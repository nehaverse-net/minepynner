package net.nehaverse.pynner.bridge;

import java.util.*;
import net.kyori.adventure.title.Title;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.events.Snapshots;
import net.nehaverse.pynner.items.Items;
import net.nehaverse.pynner.protocol.Frames;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** Explicit operation allowlist; no reflection into Bukkit or arbitrary method invocation. */
public final class Operations {
  private final PynnerPlugin plugin;

  public Operations(PynnerPlugin plugin) {
    this.plugin = plugin;
  }

  public void error(RuntimeSession session, long request, String code, String message) {
    session.send(
        "control",
        "result",
        Map.of("request_id", request, "error", code, "message", message == null ? code : message));
  }

  private void result(RuntimeSession session, long request, Object value) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("request_id", request);
    payload.put("value", value);
    if (!session.send("control", "result", payload)) session.close();
  }

  public void execute(RuntimeSession session, Map<String, Object> payload, long deadline) {
    long request = (long) Frames.number(payload, "request_id", 0);
    if (!current(session, deadline, request)) return;
    String operation = Frames.text(payload, "operation", "");
    var target = Frames.map(payload.get("target"));
    var arguments = Frames.map(payload.get("arguments"));
    try {
      if (operation.startsWith("entity.") || operation.startsWith("player.")) {
        Entity entity = Bukkit.getEntity(UUID.fromString(Frames.text(target, "uuid", "")));
        if (entity == null) {
          error(session, request, "ENTITY_GONE", "Entity no longer exists");
          return;
        }
        boolean scheduled =
            plugin
                .router()
                .entity(
                    entity,
                    () -> {
                      if (!current(session, deadline, request)) return;
                      try {
                        // Dead players remain connected and can still receive chat.
                        // Keep the validity check for mutations and other entity operations.
                        boolean onlineMessageRecipient =
                            operation.equals("player.send_message")
                                && entity instanceof Player player
                                && player.isOnline();
                        if (!entity.isValid() && !onlineMessageRecipient) {
                          error(session, request, "ENTITY_GONE", "Entity retired");
                          return;
                        }
                        if (operation.equals("entity.teleport")) {
                          entity
                              .teleportAsync(location(Frames.map(arguments.get("location"))))
                              .whenComplete(
                                  (ok, exception) -> {
                                    if (exception != null)
                                      error(
                                          session,
                                          request,
                                          "EXECUTION_ERROR",
                                          exception.getMessage());
                                    else result(session, request, ok);
                                  });
                        } else
                          result(session, request, entityOperation(entity, operation, arguments));
                      } catch (Exception exception) {
                        error(session, request, "INVALID_ARGUMENT", exception.getMessage());
                      }
                    },
                    () ->
                        error(session, request, "ENTITY_GONE", "Entity retired before execution"));
        if (!scheduled) error(session, request, "ENTITY_GONE", "Entity scheduler rejected request");
      } else if (operation.equals("world.spawn_mob") || operation.equals("world.set_block")) {
        Location location = location(Frames.map(arguments.get("location")));
        plugin
            .router()
            .region(
                location,
                () -> {
                  if (!current(session, deadline, request)) return;
                  try {
                    if (operation.equals("world.spawn_mob"))
                      result(
                          session,
                          request,
                          Snapshots.entity(
                              plugin
                                  .definitions()
                                  .spawn(Frames.text(arguments, "id", ""), location)));
                    else {
                      Material material =
                          Material.matchMaterial(Frames.text(arguments, "material", ""));
                      if (material == null || !material.isBlock())
                        throw new IllegalArgumentException("Unknown block Material");
                      location.getBlock().setType(material);
                      result(session, request, true);
                    }
                  } catch (Exception exception) {
                    error(session, request, "INVALID_ARGUMENT", exception.getMessage());
                  }
                });
      } else
        result(
            session,
            request,
            globalOperation(operation, target, arguments, Frames.text(payload, "owner", "")));
    } catch (Exception exception) {
      error(session, request, "INVALID_ARGUMENT", exception.getMessage());
    }
  }

  private boolean current(RuntimeSession session, long deadline, long request) {
    if (plugin.supervisor().active() != session || !session.active) {
      error(session, request, "STALE_GENERATION", "Old runtime generation");
      return false;
    }
    if (System.nanoTime() > deadline) {
      error(session, request, "TIMEOUT", "Expired before execution");
      return false;
    }
    return true;
  }

  private Object globalOperation(
      String operation, Map<String, Object> target, Map<String, Object> args, String owner) {
    return switch (operation) {
      case "server.broadcast" -> {
        Bukkit.broadcast(Items.text(Frames.text(args, "message", "")));
        yield true;
      }
      case "world.broadcast" -> {
        World world =
            Objects.requireNonNull(
                Bukkit.getWorld(Frames.text(args, "world", "")), "Unknown world");
        world
            .getPlayers()
            .forEach(player -> player.sendMessage(Items.text(Frames.text(args, "message", ""))));
        yield true;
      }
      case "world.set_weather" -> {
        World world =
            Objects.requireNonNull(
                Bukkit.getWorld(Frames.text(args, "world", "")), "Unknown world");
        String weather = Frames.text(args, "weather", "");
        if (!Set.of("clear", "rain", "thunder").contains(weather))
          throw new IllegalArgumentException("Weather must be clear, rain, or thunder");
        int seconds = range(args, "seconds", 1, 86400);
        world.setStorm(!weather.equals("clear"));
        world.setThundering(weather.equals("thunder"));
        world.setClearWeatherDuration(weather.equals("clear") ? seconds * 20 : 0);
        world.setWeatherDuration(seconds * 20);
        world.setThunderDuration(seconds * 20);
        yield true;
      }
      case "server.online_players" ->
          Bukkit.getOnlinePlayers().stream().map(Snapshots::entity).toList();
      case "server.status" -> plugin.status();
      case "scheduler.cancel" -> {
        plugin.definitions().cancelTask(Frames.text(args, "id", ""), owner);
        yield true;
      }
      case "command.reply" -> {
        String sender = Frames.text(target, "sender", "console");
        if (sender.equals("console"))
          Bukkit.getConsoleSender().sendMessage(Items.text(Frames.text(args, "message", "")));
        else {
          Player player = Bukkit.getPlayer(UUID.fromString(sender));
          if (player == null) throw new IllegalArgumentException("Sender left the server");
          player.sendMessage(Items.text(Frames.text(args, "message", "")));
        }
        yield true;
      }
      default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
    };
  }

  private Object entityOperation(Entity entity, String operation, Map<String, Object> args) {
    switch (operation) {
      case "entity.snapshot":
        return Snapshots.entity(entity);
      case "entity.set_name":
        entity.customName(Items.text(Frames.text(args, "value", "")));
        entity.setCustomNameVisible(true);
        break;
      case "entity.kill":
        if (entity instanceof LivingEntity living) living.setHealth(0);
        else entity.remove();
        break;
      case "entity.set_health":
        {
          LivingEntity living = living(entity);
          double health = nonNegative(args, "value");
          if (health > maxHealth(living))
            throw new IllegalArgumentException("Health exceeds maximum");
          living.setHealth(health);
          break;
        }
      case "entity.damage":
        living(entity).damage(nonNegative(args, "amount"));
        break;
      case "entity.heal":
        {
          LivingEntity living = living(entity);
          living.setHealth(
              Math.min(maxHealth(living), living.getHealth() + nonNegative(args, "amount")));
          break;
        }
      case "entity.set_velocity":
        {
          var vector = Frames.map(args.get("velocity"));
          entity.setVelocity(
              new Vector(
                  Frames.number(vector, "x", 0),
                  Frames.number(vector, "y", 0),
                  Frames.number(vector, "z", 0)));
          break;
        }
      case "entity.set_fire":
        entity.setFireTicks(ticks(nonNegative(args, "seconds")));
        break;
      case "entity.add_effect":
        {
          PotionEffectType effect = effect(args);
          living(entity)
              .addPotionEffect(
                  new PotionEffect(
                      effect,
                      ticks(nonNegative(args, "seconds")),
                      range(args, "amplifier", 0, 255)));
          break;
        }
      case "entity.remove_effect":
        living(entity).removePotionEffect(effect(args));
        break;
      case "entity.lightning":
        if (Frames.flag(args, "damage", false))
          entity.getWorld().strikeLightning(entity.getLocation());
        else entity.getWorld().strikeLightningEffect(entity.getLocation());
        break;
      case "entity.spawn_particle":
        entity
            .getWorld()
            .spawnParticle(
                Particle.valueOf(Frames.text(args, "particle", "FLAME").toUpperCase(Locale.ROOT)),
                entity.getLocation(),
                range(args, "count", 0, 1000));
        break;
      case "entity.play_sound":
        entity
            .getWorld()
            .playSound(
                entity.getLocation(),
                Frames.text(args, "sound", ""),
                (float) nonNegative(args, "volume"),
                (float) nonNegative(args, "pitch"));
        break;
      case "entity.set_pdc":
        Items.setPdc(
            entity.getPersistentDataContainer(), Frames.text(args, "key", ""), args.get("value"));
        break;
      case "player.send_message":
        player(entity).sendMessage(Items.text(Frames.text(args, "message", "")));
        break;
      case "player.open_gui":
        return plugin.menus().open(player(entity), args);
      case "player.update_gui":
        return plugin.menus().update(player(entity), args);
      case "player.switch_gui":
        return plugin.menus().switchMenu(player(entity), args);
      case "player.close_inventory":
        plugin.menus().requireView(player(entity), Frames.text(args, "view_id", ""));
        player(entity).closeInventory();
        break;
      case "player.sort_inventory":
        return plugin.menus().sort(player(entity), Frames.text(args, "view_id", ""));
      case "player.send_actionbar":
        player(entity).sendActionBar(Items.text(Frames.text(args, "message", "")));
        break;
      case "player.send_title":
        player(entity)
            .showTitle(
                Title.title(
                    Items.text(Frames.text(args, "title", "")),
                    Items.text(Frames.text(args, "subtitle", ""))));
        break;
      case "player.set_food":
        player(entity).setFoodLevel(range(args, "value", 0, 20));
        break;
      case "player.set_level":
        player(entity).setLevel(range(args, "value", 0, Integer.MAX_VALUE));
        break;
      case "player.give_item":
        return player(entity)
            .getInventory()
            .addItem(plugin.items().build(args.get("item"), range(args, "amount", 1, 64)))
            .values()
            .stream()
            .map(Snapshots::item)
            .toList();
      case "player.remove_item":
        remove(player(entity), args.get("item"), range(args, "amount", 1, 64));
        break;
      default:
        throw new IllegalArgumentException("Unsupported operation: " + operation);
    }
    return true;
  }

  private void remove(Player player, Object value, int amount) {
    ItemStack wanted = plugin.items().build(value, 1);
    String id = plugin.items().weaponId(wanted);
    int available = 0;
    for (ItemStack stack : player.getInventory().getStorageContents())
      if (matches(stack, wanted, id)) available += stack.getAmount();
    if (available < amount) throw new IllegalArgumentException("Not enough matching items");
    ItemStack[] contents = player.getInventory().getStorageContents();
    for (int slot = 0; slot < contents.length && amount > 0; slot++) {
      ItemStack stack = contents[slot];
      if (!matches(stack, wanted, id)) continue;
      int take = Math.min(amount, stack.getAmount());
      amount -= take;
      stack.setAmount(stack.getAmount() - take);
      contents[slot] = stack.getAmount() == 0 ? null : stack;
    }
    player.getInventory().setStorageContents(contents);
  }

  private boolean matches(ItemStack actual, ItemStack wanted, String id) {
    return actual != null
        && (id != null ? id.equals(plugin.items().weaponId(actual)) : actual.isSimilar(wanted));
  }

  public static Location location(Map<String, Object> data) {
    World world =
        Objects.requireNonNull(Bukkit.getWorld(Frames.text(data, "world", "")), "Unknown world");
    double x = Frames.number(data, "x", 0),
        y = Frames.number(data, "y", 0),
        z = Frames.number(data, "z", 0);
    if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -2048 || y > 2048)
      throw new IllegalArgumentException("Location out of bounds");
    return new Location(
        world,
        x,
        y,
        z,
        (float) Frames.number(data, "yaw", 0),
        (float) Frames.number(data, "pitch", 0));
  }

  private static LivingEntity living(Entity entity) {
    if (entity instanceof LivingEntity living) return living;
    throw new IllegalArgumentException("Entity is not living");
  }

  private static Player player(Entity entity) {
    if (entity instanceof Player player) return player;
    throw new IllegalArgumentException("Entity is not a player");
  }

  private static double maxHealth(LivingEntity entity) {
    return Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getValue();
  }

  private static double nonNegative(Map<String, Object> args, String key) {
    return Items.positive(args, key, 0);
  }

  private static int range(Map<String, Object> args, String key, int min, int max) {
    double value = Frames.number(args, key, min);
    if (value < min || value > max || value != Math.rint(value))
      throw new IllegalArgumentException(key + " is out of range");
    return (int) value;
  }

  private static int ticks(double seconds) {
    if (seconds > Integer.MAX_VALUE / 20.0) throw new IllegalArgumentException("Duration too long");
    return (int) Math.round(seconds * 20);
  }

  private static PotionEffectType effect(Map<String, Object> args) {
    return Objects.requireNonNull(
        Registry.EFFECT.get(Items.key(Frames.text(args, "effect", ""))), "Unknown potion effect");
  }
}
