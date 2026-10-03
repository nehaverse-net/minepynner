package net.nehaverse.pynner.debug;

import com.google.gson.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebugClient implements ClientModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger("PynnerDebug");
  public static final Gson JSON = new Gson();
  public static DebugClient instance;
  private ControlServer control;
  public volatile JsonObject session = new JsonObject();
  public volatile String endpoint = "";

  @Override
  public void onInitializeClient() {
    instance = this;
    var inspect =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.pynner_debug.inspect",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyBinding.Category.MISC));
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          while (inspect.wasPressed()) client.setScreen(new DebugScreen());
        });
    try {
      control = new ControlServer(this);
    } catch (Exception error) {
      LOG.error("Cannot start local debug control", error);
    }
    ClientLifecycleEvents.CLIENT_STOPPING.register(
        client -> {
          if (control != null) control.close();
        });
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registry) ->
            dispatcher.register(
                ClientCommandManager.literal("pynnerclient")
                    .executes(
                        context -> {
                          MinecraftClient.getInstance().setScreen(new DebugScreen());
                          return 1;
                        })));
    HudRenderCallback.EVENT.register(
        (draw, tick) -> {
          var client = MinecraftClient.getInstance();
          if (client.world == null || !isDebugConnection()) return;
          String state = string(session, "state", "connected");
          draw.drawTextWithShadow(client.textRenderer, "Pynner Debug: " + state, 8, 8, 0xFF80FFB0);
          String error = string(session, "error", "");
          if (!error.isBlank())
            draw.drawTextWithShadow(
                client.textRenderer,
                client.textRenderer.trimToWidth(error, draw.getScaledWindowWidth() - 16),
                8,
                21,
                0xFFFF7070);
        });
  }

  public JsonObject status() {
    var client = MinecraftClient.getInstance();
    var result = new JsonObject();
    result.addProperty("minecraft", "1.21.11");
    result.addProperty("username", client.getSession().getUsername());
    result.addProperty("in_world", client.world != null);
    result.addProperty(
        "busy",
        client.world != null
            || client.getNetworkHandler() != null
            || client.currentScreen instanceof ConnectScreen);
    result.addProperty("debug_connected", isDebugConnection());
    result.addProperty("endpoint", endpoint);
    result.add("session", session.deepCopy());
    return result;
  }

  public boolean isDebugConnection() {
    var client = MinecraftClient.getInstance();
    return !endpoint.isBlank()
        && client.getCurrentServerEntry() != null
        && endpoint.equals(client.getCurrentServerEntry().address);
  }

  public JsonObject connect(JsonObject request) {
    var client = MinecraftClient.getInstance();
    if (status().get("busy").getAsBoolean())
      throw new IllegalStateException("Return to the title screen first.");
    int port = request.get("port").getAsInt();
    if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid local port");
    endpoint = "127.0.0.1:" + port;
    session = request.deepCopy();
    session.addProperty("state", "connecting");
    var info = new ServerInfo("Pynner Debug", endpoint, ServerInfo.ServerType.OTHER);
    info.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.DISABLED);
    ConnectScreen.connect(
        new TitleScreen(), client, ServerAddress.parse(endpoint), info, false, null);
    return status();
  }

  public JsonObject update(JsonObject request) {
    if (!string(session, "id", "").equals(string(request, "id", "")))
      throw new IllegalStateException("Another debug session owns this client.");
    session = request.deepCopy();
    return status();
  }

  public JsonObject disconnect(JsonObject request) {
    if (!string(session, "id", "").equals(string(request, "id", "")))
      throw new IllegalStateException("Another debug session owns this client.");
    var client = MinecraftClient.getInstance();
    if (isDebugConnection()) {
      client.disconnect(new TitleScreen(), false);
    }
    endpoint = "";
    session = new JsonObject();
    return status();
  }

  public JsonObject command(JsonObject request) {
    var client = MinecraftClient.getInstance();
    if (!isDebugConnection() || client.world == null || client.getNetworkHandler() == null)
      throw new IllegalStateException("No active local debug world.");
    String command = string(request, "command", "");
    if (command.isBlank()
        || command.length() > 1024
        || command.contains("\n")
        || command.contains("\r")) throw new IllegalArgumentException("Invalid command");
    client
        .getNetworkHandler()
        .sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
    var result = new JsonObject();
    result.addProperty("submitted", true);
    return result;
  }

  public static String string(JsonObject object, String key, String fallback) {
    return object.has(key) && !object.get(key).isJsonNull()
        ? object.get(key).getAsString()
        : fallback;
  }
}
