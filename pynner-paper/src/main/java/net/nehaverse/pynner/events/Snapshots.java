package net.nehaverse.pynner.events;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.*;
import org.bukkit.util.Vector;

public final class Snapshots {
  private Snapshots() {}

  public static Map<String, Object> location(Location location) {
    return Map.of(
        "world",
        location.getWorld().getName(),
        "x",
        location.getX(),
        "y",
        location.getY(),
        "z",
        location.getZ(),
        "yaw",
        (double) location.getYaw(),
        "pitch",
        (double) location.getPitch());
  }

  public static Map<String, Object> vector(Vector vector) {
    return Map.of("x", vector.getX(), "y", vector.getY(), "z", vector.getZ());
  }

  public static Map<String, Object> entity(Entity entity) {
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("uuid", entity.getUniqueId().toString());
    snapshot.put("type", entity.getType().name());
    snapshot.put("name", entity.getName());
    snapshot.put("location", location(entity.getLocation()));
    snapshot.put("velocity", vector(entity.getVelocity()));
    snapshot.put("pdc", pdc(entity.getPersistentDataContainer()));
    snapshot.put("fire_ticks", entity.getFireTicks());
    if (entity instanceof LivingEntity living) {
      snapshot.put("health", living.getHealth());
      var max = living.getAttribute(Attribute.MAX_HEALTH);
      if (max != null) snapshot.put("max_health", max.getValue());
    }
    if (entity instanceof Player player) {
      snapshot.put("food", player.getFoodLevel());
      snapshot.put("level", player.getLevel());
      snapshot.put(
          "inventory",
          Arrays.stream(player.getInventory().getContents()).map(Snapshots::item).toList());
    }
    return snapshot;
  }

  public static Map<String, Object> item(ItemStack item) {
    if (item == null || item.getType().isAir()) return null;
    var result = new LinkedHashMap<String, Object>();
    result.put("material", item.getType().name());
    result.put("amount", item.getAmount());
    var meta = item.getItemMeta();
    var pdc = pdc(meta.getPersistentDataContainer());
    result.put("pdc", pdc);
    if (pdc.get("pynner:weapon_id") != null) result.put("weapon_id", pdc.get("pynner:weapon_id"));
    if (meta.displayName() != null)
      result.put(
          "name",
          net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
              .serialize(meta.displayName()));
    if (meta instanceof Damageable damageable) result.put("damage", damageable.getDamage());
    return result;
  }

  public static Map<String, Object> pdc(PersistentDataContainer container) {
    var result = new LinkedHashMap<String, Object>();
    for (var key : container.getKeys()) {
      Object value = null;
      if (container.has(key, PersistentDataType.STRING))
        value = container.get(key, PersistentDataType.STRING);
      else if (container.has(key, PersistentDataType.INTEGER))
        value = container.get(key, PersistentDataType.INTEGER);
      else if (container.has(key, PersistentDataType.LONG))
        value = container.get(key, PersistentDataType.LONG);
      else if (container.has(key, PersistentDataType.DOUBLE))
        value = container.get(key, PersistentDataType.DOUBLE);
      if (value != null) result.put(key.toString(), value);
    }
    return result;
  }

  public static Map<String, Object> event(String name, Entity entity) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("event", name);
    payload.put("world", entity.getWorld().getName());
    payload.put("entity", entity(entity));
    if (entity instanceof Player) payload.put("player", payload.get("entity"));
    return payload;
  }
}
