package net.nehaverse.pynner;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import net.nehaverse.pynner.bridge.*;
import net.nehaverse.pynner.commands.PythonCommands;
import net.nehaverse.pynner.definitions.Definitions;
import net.nehaverse.pynner.events.*;
import net.nehaverse.pynner.items.Items;
import net.nehaverse.pynner.scheduling.ExecutionRouter;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PynnerPlugin extends JavaPlugin implements TabCompleter {
  private BlockingQueue<Runnable> work;
  private RuntimeSupervisor supervisor;
  private Operations operations;
  private Definitions definitions;
  private Items items;
  private net.nehaverse.pynner.items.GuiMenus menus;
  private PythonCommands commands;
  private EventBridge events;
  private NativeHooks nativeHooks;
  private ExecutionRouter router;
  private YamlConfiguration messages;
  private long ticks;
  private long debugParentPid;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    debugParentPid = getConfig().getLong("debug.parent-pid", 0);
    try {
      for (String directory :
          List.of(
              "scripts/weapons",
              "scripts/mobs",
              "scripts/events",
              "scripts/plugins",
              "logs",
              "runtime")) Files.createDirectories(getDataFolder().toPath().resolve(directory));
      if (!getDataFolder().toPath().resolve("messages.yml").toFile().exists())
        saveResource("messages.yml", false);
      messages =
          YamlConfiguration.loadConfiguration(
              getDataFolder().toPath().resolve("messages.yml").toFile());
      work = new ArrayBlockingQueue<>(Math.max(1, getConfig().getInt("queues.operations", 4096)));
      router = new ExecutionRouter(this);
      items = new Items(this);
      menus = new net.nehaverse.pynner.items.GuiMenus(this);
      commands = new PythonCommands(this);
      definitions = new Definitions(this);
      operations = new Operations(this);
      events = new EventBridge(this);
      nativeHooks = new NativeHooks(this);
      supervisor = new RuntimeSupervisor(this);
      Objects.requireNonNull(getCommand("pynner")).setExecutor(this);
      Objects.requireNonNull(getCommand("pynner")).setTabCompleter(this);
      router.timer(this::tick, 1, true);
      supervisor.reload();
    } catch (IOException exception) {
      getLogger().log(Level.SEVERE, "Cannot start Pynner", exception);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  public boolean enqueue(Runnable operation) {
    return isEnabled() && work != null && work.offer(operation);
  }

  public net.nehaverse.pynner.items.GuiMenus menus() {
    return menus;
  }

  private void tick() {
    int maximum = Math.max(1, getConfig().getInt("queues.operations-per-tick", 200));
    long stop =
        System.nanoTime()
            + (long)
                (Math.max(0.1, getConfig().getDouble("queues.operation-budget-ms", 2)) * 1_000_000);
    for (int index = 0; index < maximum && System.nanoTime() < stop; index++) {
      Runnable operation = work.poll();
      if (operation == null) break;
      try {
        operation.run();
      } catch (Exception exception) {
        getLogger().log(Level.SEVERE, "Queued operation failed", exception);
      }
    }
    if (++ticks % 20 == 0) {
      if (debugParentPid > 0
          && ProcessHandle.of(debugParentPid).map(ProcessHandle::isAlive).orElse(false) == false) {
        getLogger().info("Debug runner exited; stopping its local test server");
        debugParentPid = 0;
        getServer().shutdown();
        return;
      }
      supervisor.tick();
    }
    nativeHooks.tick();
    definitions.tickMobs();
    events.flush();
    if (ticks % 10 == 0) events.refreshChatSnapshots();
  }

  public Map<String, Object> status() {
    var active = supervisor.active();
    return Map.of(
        "active",
        active != null && active.active,
        "generation",
        active == null ? 0 : active.generation,
        "operation_queue",
        work.size(),
        "event_queue",
        events.queued(),
        "dropped_events",
        events.dropped(),
        "weapons",
        definitions.weapons().size(),
        "mobs",
        definitions.mobs().size());
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) return false;
    String action = args[0].toLowerCase(Locale.ROOT);
    if (!sender.hasPermission("pynner.admin." + action)) {
      sender.sendMessage("No permission.");
      return true;
    }
    try {
      switch (action) {
        case "reload" -> {
          supervisor.reload();
          sender.sendMessage(messages.getString("reload", "Reload requested."));
        }
        case "status" -> sender.sendMessage("[Pynner] " + status());
        case "give" -> {
          if (!(sender instanceof Player player) || args.length != 2)
            throw new IllegalArgumentException("/pynner give <weapon_id> (player only)");
          player
              .getInventory()
              .addItem(items.build(args[1], 1))
              .values()
              .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        case "spawn" -> {
          if (!(sender instanceof Player player) || args.length != 2)
            throw new IllegalArgumentException("/pynner spawn <mob_id> (player only)");
          definitions.spawn(args[1], player.getLocation());
        }
        default -> {
          return false;
        }
      }
    } catch (Exception exception) {
      sender.sendMessage("[Pynner] " + exception.getMessage());
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1)
      return List.of("reload", "status", "give", "spawn").stream()
          .filter(
              value ->
                  value.startsWith(args[0].toLowerCase(Locale.ROOT))
                      && sender.hasPermission("pynner.admin." + value))
          .toList();
    if (args.length == 2 && args[0].equals("give"))
      return definitions.weapons().keySet().stream()
          .filter(value -> value.startsWith(args[1]))
          .toList();
    if (args.length == 2 && args[0].equals("spawn"))
      return definitions.mobs().keySet().stream()
          .filter(value -> value.startsWith(args[1]))
          .toList();
    return List.of();
  }

  @Override
  public void onDisable() {
    if (menus != null) menus.shutdown();
    if (supervisor != null) supervisor.close();
    if (definitions != null) definitions.close();
    if (work != null) work.clear();
  }

  public RuntimeSupervisor supervisor() {
    return supervisor;
  }

  public Operations operations() {
    return operations;
  }

  public Definitions definitions() {
    return definitions;
  }

  public Items items() {
    return items;
  }

  public PythonCommands commands() {
    return commands;
  }

  public EventBridge events() {
    return events;
  }

  public ExecutionRouter router() {
    return router;
  }
}
