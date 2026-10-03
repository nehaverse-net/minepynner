package net.nehaverse.pynner.commands;

import java.util.*;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.events.Snapshots;
import net.nehaverse.pynner.protocol.Frames;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/** Public CommandMap adapter permits registration changes during Python reload. */
public final class PythonCommands {
  private final PynnerPlugin plugin;
  private final List<Command> commands = new ArrayList<>();

  public PythonCommands(PynnerPlugin plugin) {
    this.plugin = plugin;
  }

  public void validate(List<?> handlers) {
    var labels = new HashSet<String>();
    for (Object value : handlers) {
      var handler = Frames.map(value);
      if (!"command".equals(handler.get("kind"))) continue;
      var names = new ArrayList<String>();
      names.add(handler.get("name").toString());
      for (Object alias : (List<?>) handler.get("aliases")) names.add(alias.toString());
      for (String name : names) {
        if (!name.matches("[a-z][a-z0-9_-]*") || !labels.add(name))
          throw new IllegalArgumentException("Duplicate or invalid command: " + name);
        Command existing = Bukkit.getCommandMap().getCommand(name);
        if (existing != null && !commands.contains(existing))
          throw new IllegalArgumentException("Command is already owned: " + name);
      }
    }
  }

  public void install(Collection<Map<String, Object>> handlers) {
    clear();
    for (var handler : handlers)
      if ("command".equals(handler.get("kind"))) {
        Command command = new PythonCommand(handler);
        if (!Bukkit.getCommandMap().register("pynner", command))
          throw new IllegalArgumentException("Command registration failed: " + command.getName());
        commands.add(command);
      }
    Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
  }

  public void clear() {
    var commandMap = Bukkit.getCommandMap();
    // Paper's forwarding map supports remove(key), but not iterator.remove().
    var known = commandMap.getKnownCommands();
    var owned =
        known.entrySet().stream()
            .filter(entry -> commands.contains(entry.getValue()))
            .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
            .toList();
    owned.forEach(entry -> known.remove(entry.getKey(), entry.getValue()));
    commands.forEach(command -> command.unregister(commandMap));
    commands.clear();
    Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
  }

  private final class PythonCommand extends Command {
    private final Map<String, Object> handler;

    PythonCommand(Map<String, Object> handler) {
      super(handler.get("name").toString());
      this.handler = handler;
      setAliases(((List<?>) handler.get("aliases")).stream().map(Object::toString).toList());
      if (handler.get("permission") != null) setPermission(handler.get("permission").toString());
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
      if (!testPermission(sender)) return true;
      if (Frames.flag(handler, "player_only", false) && !(sender instanceof Player)) {
        sender.sendMessage("This command requires a player.");
        return true;
      }
      var specs = (List<?>) handler.get("arguments");
      if (args.length > specs.size()) {
        sender.sendMessage("Too many arguments.");
        return true;
      }
      var arguments = new ArrayList<Object>();
      try {
        for (int index = 0; index < specs.size(); index++) {
          var spec = Frames.map(specs.get(index));
          if (index >= args.length) {
            if (Frames.flag(spec, "required", true))
              throw new IllegalArgumentException("Missing " + spec.get("name"));
            arguments.add(spec.get("default"));
            continue;
          }
          String value = args[index];
          arguments.add(
              switch (Frames.text(spec, "type", "str")) {
                case "int" -> Integer.parseInt(value);
                case "float" -> {
                  double parsed = Double.parseDouble(value);
                  if (!Double.isFinite(parsed))
                    throw new IllegalArgumentException("Invalid number");
                  yield parsed;
                }
                case "bool" -> {
                  if (!List.of("true", "false").contains(value.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Use true or false");
                  yield Boolean.parseBoolean(value);
                }
                case "Player" -> {
                  Player player = Bukkit.getPlayerExact(value);
                  if (player == null)
                    throw new IllegalArgumentException("Player is not online: " + value);
                  yield Snapshots.entity(player);
                }
                default -> value;
              });
        }
        var context = new LinkedHashMap<String, Object>();
        context.put(
            "sender",
            sender instanceof Player player ? player.getUniqueId().toString() : "console");
        context.put("sender_name", sender.getName());
        context.put("args", List.of(args));
        if (sender instanceof Player player) context.put("player", Snapshots.entity(player));
        if (!plugin
            .events()
            .invoke(
                handler.get("id").toString(), Map.of("context", context, "arguments", arguments)))
          sender.sendMessage("Python is unavailable or its queue is full.");
      } catch (IllegalArgumentException exception) {
        sender.sendMessage(exception.getMessage());
      }
      return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
      if (getPermission() != null && !sender.hasPermission(getPermission())) return List.of();
      if (Frames.flag(handler, "player_only", false) && !(sender instanceof Player))
        return List.of();
      var specs = (List<?>) handler.get("arguments");
      int index = args.length - 1;
      if (index < 0 || index >= specs.size()) return List.of();
      var spec = Frames.map(specs.get(index));
      var completions = Frames.map(handler.get("completions"));
      List<String> choices;
      if (completions.get(spec.get("name")) instanceof List<?> values)
        choices = values.stream().map(Object::toString).toList();
      else if ("Player".equals(spec.get("type")))
        choices = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
      else if ("bool".equals(spec.get("type"))) choices = List.of("true", "false");
      else choices = List.of();
      String prefix = args[index].toLowerCase(Locale.ROOT);
      return choices.stream()
          .filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix))
          .toList();
    }
  }
}
