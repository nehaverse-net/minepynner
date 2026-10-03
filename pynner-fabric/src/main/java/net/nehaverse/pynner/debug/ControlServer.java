package net.nehaverse.pynner.debug;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.*;
import net.minecraft.client.MinecraftClient;

/** Authenticated, loopback-only control; all Minecraft access runs on its client thread. */
public final class ControlServer implements AutoCloseable {
  private final HttpServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(2);
  private final String token = UUID.randomUUID().toString() + UUID.randomUUID();
  private final Path descriptor;
  private final DebugClient client;

  public ControlServer(DebugClient client) throws IOException {
    this.client = client;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
    Path directory = Path.of(System.getProperty("user.home"), ".pynner", "debug", "clients");
    Files.createDirectories(directory);
    descriptor =
        directory.resolve(ProcessHandle.current().pid() + "-" + UUID.randomUUID() + ".json");
    var details = new JsonObject();
    details.addProperty("port", server.getAddress().getPort());
    details.addProperty("token", token);
    details.addProperty("minecraft", "1.21.11");
    details.addProperty("pid", ProcessHandle.current().pid());
    Files.writeString(descriptor, DebugClient.JSON.toJson(details));
    if (Files.getFileStore(descriptor).supportsFileAttributeView("posix"))
      Files.setPosixFilePermissions(
          descriptor, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    DebugClient.LOG.info(
        "Pynner debug client ready, control port {}", server.getAddress().getPort());
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      String authorization = exchange.getRequestHeaders().getFirst("Authorization");
      if (authorization == null
          || !MessageDigest.isEqual(
              authorization.getBytes(StandardCharsets.UTF_8),
              ("Bearer " + token).getBytes(StandardCharsets.UTF_8))) {
        respond(exchange, 401, "Unauthorized");
        return;
      }
      String path = exchange.getRequestURI().getPath();
      boolean status = path.equals("/status") && exchange.getRequestMethod().equals("GET");
      if (!status && !exchange.getRequestMethod().equals("POST")) {
        respond(exchange, 405, "Method not allowed");
        return;
      }
      byte[] body = exchange.getRequestBody().readNBytes(65537);
      if (body.length > 65536) {
        respond(exchange, 413, "Request too large");
        return;
      }
      var request =
          body.length == 0
              ? new JsonObject()
              : JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
      JsonObject result =
          MinecraftClient.getInstance()
              .submit(
                  () ->
                      switch (path) {
                        case "/status" -> client.status();
                        case "/connect" -> client.connect(request);
                        case "/session" -> client.update(request);
                        case "/disconnect" -> client.disconnect(request);
                        case "/command" -> client.command(request);
                        case "/inspect" -> {
                          MinecraftClient.getInstance().setScreen(new DebugScreen());
                          yield client.status();
                        }
                        default -> throw new IllegalArgumentException("Unknown endpoint");
                      })
              .get(5, TimeUnit.SECONDS);
      byte[] output = DebugClient.JSON.toJson(result).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      exchange.sendResponseHeaders(200, output.length);
      exchange.getResponseBody().write(output);
    } catch (Exception error) {
      Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
      respond(
          exchange,
          409,
          cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
    } finally {
      exchange.close();
    }
  }

  private void respond(HttpExchange exchange, int code, String message) throws IOException {
    var error = new JsonObject();
    error.addProperty("error", message);
    byte[] output = DebugClient.JSON.toJson(error).getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(code, output.length);
    exchange.getResponseBody().write(output);
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
    try {
      Files.deleteIfExists(descriptor);
    } catch (IOException error) {
      DebugClient.LOG.warn("Cannot remove descriptor", error);
    }
  }
}
