package net.nehaverse.pynner.bridge;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.protocol.Frames;

/** All socket, process and filesystem work lives outside the tick thread. */
public final class RuntimeSupervisor implements AutoCloseable {
  private final PynnerPlugin plugin;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final ServerSocket server;
  private final Map<Long, RuntimeSession> sessions = new ConcurrentHashMap<>();
  private final AtomicLong generation = new AtomicLong();
  private volatile RuntimeSession active;
  private volatile RuntimeSession candidate;
  private volatile boolean stopped;
  private int restarts;
  private long restartAfter;

  public RuntimeSupervisor(PynnerPlugin plugin) throws IOException {
    this.plugin = plugin;
    server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
    executor.submit(this::accept);
    if (plugin.getConfig().getBoolean("runtime.auto-reload")) executor.submit(this::watch);
  }

  public RuntimeSession active() {
    return active;
  }

  public synchronized void reload() {
    restarts = 0;
    startRuntime();
  }

  private synchronized void startRuntime() {
    if (stopped || candidate != null) return;
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    RuntimeSession session =
        new RuntimeSession(
            generation.incrementAndGet(), HexFormat.of().formatHex(secret), executor);
    sessions.put(session.generation, session);
    candidate = session;
    String executable = plugin.getConfig().getString("python.executable", "python");
    executor.submit(
        () -> {
          try {
            Path root = plugin.getDataFolder().toPath().toAbsolutePath();
            var builder =
                new ProcessBuilder(
                    executable,
                    "-u",
                    "-m",
                    "pynner_runtime",
                    "--port",
                    Integer.toString(server.getLocalPort()),
                    "--generation",
                    Long.toString(session.generation),
                    "--scripts",
                    root.resolve("scripts").toString(),
                    "--logs",
                    root.resolve("logs").toString());
            builder.directory(root.toFile());
            builder.environment().put("PYNNER_TOKEN", session.token);
            builder.environment().put("PYTHONUTF8", "1");
            builder.redirectErrorStream(true);
            session.process = builder.start();
            if (session.closed || stopped) {
              session.close();
              session.process.destroyForcibly();
              return;
            }
            Path log = root.resolve("logs/runtime-" + session.generation + ".log");
            try (var reader = session.process.inputReader(StandardCharsets.UTF_8);
                var output = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
              String line;
              while ((line = reader.readLine()) != null) {
                output.write(line);
                output.newLine();
                output.flush();
                plugin.getLogger().info("Python: " + line);
              }
            }
          } catch (Exception exception) {
            plugin.getLogger().severe("Python runtime failed: " + exception.getMessage());
            session.close();
          }
        });
  }

  private void accept() {
    while (!stopped) {
      try {
        Socket socket = server.accept();
        executor.submit(() -> serve(socket));
      } catch (IOException exception) {
        if (!stopped) plugin.getLogger().warning("IPC accept failed: " + exception.getMessage());
      }
    }
  }

  private void serve(Socket socket) {
    try (socket) {
      socket.setSoTimeout(10_000);
      socket.setTcpNoDelay(true);
      Map<String, Object> hello = Frames.read(socket.getInputStream());
      if (!hello.get("version").equals(1L) || !"hello".equals(hello.get("type")))
        throw new IOException("Bad handshake");
      long id = ((Number) hello.get("generation")).longValue();
      RuntimeSession session = sessions.get(id);
      Map<String, Object> payload = Frames.map(hello.get("payload"));
      if (session == null
          || !MessageDigest.isEqual(
              session.token.getBytes(StandardCharsets.UTF_8),
              Frames.text(payload, "token", "").getBytes(StandardCharsets.UTF_8)))
        throw new IOException("IPC authentication failed");
      String channel = Frames.text(payload, "channel", "");
      session.attach(channel, socket);
      socket.setSoTimeout(0);
      while (!session.closed && !stopped) {
        Map<String, Object> envelope = Frames.read(socket.getInputStream());
        if (!envelope.get("version").equals(1L)
            || ((Number) envelope.get("generation")).longValue() != id)
          throw new IOException("Bad envelope");
        receive(session, Frames.text(envelope, "type", ""), Frames.map(envelope.get("payload")));
      }
    } catch (Exception exception) {
      if (!stopped && !(exception instanceof EOFException))
        plugin.getLogger().fine("IPC closed: " + exception.getMessage());
    }
  }

  private void receive(RuntimeSession session, String type, Map<String, Object> payload) {
    switch (type) {
      case "heartbeat" -> {
        session.heartbeat = System.nanoTime();
        long completed = (long) Frames.number(payload, "completed", 0);
        if (completed != session.completed || Frames.number(payload, "queue", 0) == 0)
          session.progressAt = System.nanoTime();
        session.completed = completed;
        session.pythonQueue = (int) Frames.number(payload, "queue", 0);
      }
      case "register" -> {
        if (session != candidate || !session.connected()) {
          session.close();
          break;
        }
        if (!plugin.enqueue(() -> activate(session, payload))) session.close();
      }
      case "load_error" -> {
        plugin
            .getLogger()
            .severe(
                "Python load failed; previous generation retained:\n"
                    + Frames.text(payload, "message", ""));
        session.close();
      }
      case "log" -> plugin.getLogger().warning(Frames.text(payload, "message", "Python Error"));
      case "operation" -> {
        long request = (long) Frames.number(payload, "request_id", 0);
        long deadline =
            System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(
                    Math.min(5000, Math.max(1, (long) Frames.number(payload, "timeout_ms", 5000))));
        if (session != active || !session.active)
          plugin.operations().error(session, request, "STALE_GENERATION", "Runtime is not active");
        else if (!plugin.enqueue(() -> plugin.operations().execute(session, payload, deadline)))
          plugin.operations().error(session, request, "QUEUE_FULL", "Operation queue is full");
      }
      case "task_done" ->
          plugin.enqueue(
              () ->
                  plugin
                      .definitions()
                      .taskDone(Frames.text(payload, "id", ""), session.generation));
      case "ready" ->
          plugin.getLogger().info("Python generation " + session.generation + " enabled");
      default -> throw new IllegalArgumentException("Unknown message: " + type);
    }
  }

  private void activate(RuntimeSession session, Map<String, Object> manifest) {
    if (session != candidate || session.closed) return;
    try {
      plugin.definitions().validate(manifest);
      RuntimeSession previous = active;
      Map<String, Object> previousManifest = previous == null ? null : previous.manifest;
      plugin.definitions().install(manifest, session.generation);
      active = session;
      session.manifest = manifest;
      session.active = true;
      candidate = null;
      session.heartbeat = System.nanoTime();
      if (!session.send("control", "activate", Map.of())) {
        active = previous;
        if (previousManifest != null)
          plugin.definitions().install(previousManifest, previous.generation);
        throw new IOException("Activation channel full");
      }
      if (previous != null) {
        previous.retire();
        sessions.remove(previous.generation);
      }
      plugin.getLogger().info("Activated Python generation " + session.generation);
    } catch (Exception exception) {
      plugin.getLogger().severe("Registration failed: " + exception.getMessage());
      session.close();
      candidate = null;
    }
  }

  public synchronized void tick() {
    long now = System.nanoTime();
    if (candidate != null
        && (candidate.closed
            || elapsed(candidate.heartbeat, now)
                > plugin.getConfig().getInt("runtime.startup-timeout-seconds", 30))) {
      candidate.close();
      sessions.remove(candidate.generation);
      candidate = null;
      if (active == null)
        restartAfter =
            now
                + TimeUnit.SECONDS.toNanos(
                    plugin.getConfig().getInt("runtime.restart-delay-seconds", 5));
      plugin.getLogger().warning("Candidate runtime stopped; current generation retained");
    }
    if (active != null
        && (active.closed
            || active.process != null && !active.process.isAlive()
            || elapsed(active.heartbeat, now)
                > plugin.getConfig().getInt("runtime.heartbeat-timeout-seconds", 15)
            || elapsed(active.progressAt, now) > 30 && active.pythonQueue > 0)) {
      active.close();
      sessions.remove(active.generation);
      active = null;
      plugin.definitions().stopTasks();
      restartAfter =
          now
              + TimeUnit.SECONDS.toNanos(
                  plugin.getConfig().getInt("runtime.restart-delay-seconds", 5));
      plugin.getLogger().warning("Python runtime unavailable; Paper continues running");
    }
    if (active == null
        && candidate == null
        && now >= restartAfter
        && restarts < plugin.getConfig().getInt("runtime.restart-limit", 3)) {
      restarts++;
      startRuntime();
    }
  }

  private static long elapsed(long start, long now) {
    return TimeUnit.NANOSECONDS.toSeconds(now - start);
  }

  private void watch() {
    String previous = "";
    String pending = "";
    long changedAt = 0;
    while (!stopped) {
      try {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        var digest = MessageDigest.getInstance("SHA-256");
        try (var files = Files.walk(scripts)) {
          for (Path path : files.filter(p -> p.toString().endsWith(".py")).sorted().toList()) {
            digest.update(scripts.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(path));
          }
        }
        String signature = HexFormat.of().formatHex(digest.digest());
        if (previous.isEmpty()) previous = signature;
        if (!signature.equals(pending)) {
          pending = signature;
          changedAt = System.nanoTime();
        }
        if (!signature.equals(previous)
            && System.nanoTime() - changedAt > TimeUnit.MILLISECONDS.toNanos(750)) {
          previous = signature;
          plugin.enqueue(this::reload);
        }
        Thread.sleep(500);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception exception) {
        plugin.getLogger().warning("File watch: " + exception.getMessage());
        return;
      }
    }
  }

  @Override
  public void close() {
    stopped = true;
    RuntimeSession current = active;
    if (current != null) {
      current.send("control", "shutdown", Map.of());
      try {
        if (current.process != null) current.process.waitFor(2, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
    sessions.values().forEach(RuntimeSession::close);
    try {
      server.close();
    } catch (IOException ignored) {
      /* Already closing. */
    }
    executor.shutdownNow();
  }
}
