package net.nehaverse.pynner.items;

import java.util.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.protocol.Frames;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.*;

public final class Items {
  private final PynnerPlugin plugin;
  public final NamespacedKey weaponKey;
  public final NamespacedKey mobKey;
  public final NamespacedKey usesKey;
  public final NamespacedKey durabilityKey;

  public Items(PynnerPlugin plugin) {
    this.plugin = plugin;
    weaponKey = new NamespacedKey(plugin, "weapon_id");
    mobKey = new NamespacedKey(plugin, "mob_id");
    usesKey = new NamespacedKey(plugin, "uses");
    durabilityKey = new NamespacedKey(plugin, "durability");
  }

  public static net.kyori.adventure.text.Component text(String text) {
    return LegacyComponentSerializer.legacySection().deserialize(text);
  }

  public static NamespacedKey key(String name) {
    NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
    if (key == null) throw new IllegalArgumentException("Invalid namespaced key: " + name);
    return key;
  }

  public static Attribute attribute(String name) {
    Attribute attribute = Registry.ATTRIBUTE.get(key(name));
    if (attribute == null) throw new IllegalArgumentException("Unknown attribute: " + name);
    return attribute;
  }

  public static double positive(Map<String, Object> spec, String field, double fallback) {
    double value = Frames.number(spec, field, fallback);
    if (value < 0) throw new IllegalArgumentException(field + " must be non-negative");
    return value;
  }

  public ItemStack build(Object value, int amount) {
    return build(value, amount, plugin.definitions().weapons());
  }

  public ItemStack build(Object value, int amount, Map<String, Map<String, Object>> definitions) {
    if (amount < 1 || amount > 64) throw new IllegalArgumentException("amount must be 1..64");
    String ident = null;
    Map<String, Object> spec;
    if (value instanceof String name) {
      spec = definitions.get(name);
      if (spec != null) ident = name;
      else spec = Map.of("material", name);
    } else spec = Frames.map(value);
    return buildSpec(spec, ident, amount);
  }

  public ItemStack buildSpec(Map<String, Object> spec, String ident, int amount) {
    Material material = Material.matchMaterial(Frames.text(spec, "material", "STICK"));
    if (material == null || material.isAir() || !material.isItem())
      throw new IllegalArgumentException("Unknown item Material");
    ItemStack item = new ItemStack(material, amount);
    var meta = item.getItemMeta();
    if (spec.containsKey("name")) meta.displayName(text(Frames.text(spec, "name", "")));
    if (spec.get("lore") instanceof List<?> lore)
      meta.lore(lore.stream().map(line -> text(line.toString())).toList());
    if (spec.get("custom_model_data") instanceof Number model)
      meta.setCustomModelData(model.intValue());
    if (spec.get("enchantments") instanceof Map<?, ?> enchantments) {
      for (var entry : enchantments.entrySet()) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(key(entry.getKey().toString()));
        if (enchantment == null)
          throw new IllegalArgumentException("Unknown enchantment: " + entry.getKey());
        int level = ((Number) entry.getValue()).intValue();
        if (level < 1 || level > 255)
          throw new IllegalArgumentException("Enchantment level must be 1..255");
        meta.addEnchant(enchantment, level, true);
      }
    }
    if (ident != null) {
      meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, ident);
      meta.getPersistentDataContainer()
          .set(new NamespacedKey(plugin, "schema"), PersistentDataType.INTEGER, 1);
    }
    if (spec.get("uses") != null) {
      int uses = (int) positive(spec, "uses", 1);
      if (uses < 1) throw new IllegalArgumentException("uses must be positive");
      meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
    }
    if (spec.get("durability") != null) {
      int durability = (int) positive(spec, "durability", 1);
      if (durability < 1 || !(meta instanceof Damageable damageable))
        throw new IllegalArgumentException(
            "durability requires a damageable item and a positive value");
      damageable.setMaxDamage(durability);
      meta.getPersistentDataContainer().set(durabilityKey, PersistentDataType.INTEGER, durability);
    }
    if (spec.get("pdc") instanceof Map<?, ?> pdc)
      pdc.forEach((k, v) -> setPdc(meta.getPersistentDataContainer(), k.toString(), v));
    if (spec.get("damage") != null) {
      double damage = positive(spec, "damage", 1);
      meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
      meta.addAttributeModifier(
          Attribute.ATTACK_DAMAGE,
          new AttributeModifier(
              new NamespacedKey(plugin, "damage"),
              damage - 1,
              AttributeModifier.Operation.ADD_NUMBER,
              EquipmentSlotGroup.MAINHAND));
    }
    if (spec.get("attack_speed") != null) {
      double speed = positive(spec, "attack_speed", 4);
      meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
      meta.addAttributeModifier(
          Attribute.ATTACK_SPEED,
          new AttributeModifier(
              new NamespacedKey(plugin, "speed"),
              speed - 4,
              AttributeModifier.Operation.ADD_NUMBER,
              EquipmentSlotGroup.MAINHAND));
    }
    if (spec.get("attributes") instanceof List<?> attributes) {
      int index = 0;
      for (Object entry : attributes) {
        Map<String, Object> modifier = Frames.map(entry);
        String slot = Frames.text(modifier, "slot", "MAINHAND").toLowerCase(Locale.ROOT);
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(slot);
        if (group == null) throw new IllegalArgumentException("Unknown equipment slot: " + slot);
        meta.addAttributeModifier(
            attribute(Frames.text(modifier, "attribute", "")),
            new AttributeModifier(
                new NamespacedKey(plugin, "modifier_" + index++),
                Frames.number(modifier, "amount", 0),
                AttributeModifier.Operation.valueOf(
                    Frames.text(modifier, "operation", "ADD_NUMBER")),
                group));
      }
    }
    item.setItemMeta(meta);
    return item;
  }

  public String weaponId(ItemStack item) {
    return item == null || !item.hasItemMeta()
        ? null
        : item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
  }

  public static void setPdc(PersistentDataContainer pdc, String name, Object value) {
    NamespacedKey key = key(name.contains(":") ? name : "pynner:" + name);
    if (key.getNamespace().equals("pynner")
        && Set.of("weapon_id", "mob_id", "schema", "uses", "durability").contains(key.getKey()))
      throw new IllegalArgumentException("Reserved PDC key: " + key);
    if (value instanceof String text) pdc.set(key, PersistentDataType.STRING, text);
    else if (value instanceof Float || value instanceof Double)
      pdc.set(key, PersistentDataType.DOUBLE, ((Number) value).doubleValue());
    else if (value instanceof Number number)
      pdc.set(key, PersistentDataType.LONG, number.longValue());
    else throw new IllegalArgumentException("PDC values must be string, integer or float");
  }

  public ShapedRecipe recipe(String ident, Map<String, Object> spec) {
    Map<String, Object> recipe = Frames.map(spec.get("recipe"));
    var result = new ShapedRecipe(new NamespacedKey(plugin, ident), buildSpec(spec, ident, 1));
    List<?> shape = (List<?>) recipe.get("shape");
    result.shape(shape.stream().map(Object::toString).toArray(String[]::new));
    Frames.map(recipe.get("ingredients"))
        .forEach(
            (symbol, material) -> {
              if (symbol.length() != 1)
                throw new IllegalArgumentException("Recipe symbols must be single characters");
              Material ingredient = Material.matchMaterial(material.toString());
              if (ingredient == null || ingredient.isAir())
                throw new IllegalArgumentException("Unknown recipe ingredient");
              result.setIngredient(symbol.charAt(0), ingredient);
            });
    return result;
  }
}
