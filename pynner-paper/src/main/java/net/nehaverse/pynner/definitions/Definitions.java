package net.nehaverse.pynner.definitions;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.*;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.items.Items;
import net.nehaverse.pynner.protocol.Frames;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

public final class Definitions {
  private final PynnerPlugin plugin;
  private Map<String, Map<String, Object>> weapons = new LinkedHashMap<>();
  private Map<String, Map<String, Object>> mobs = new LinkedHashMap<>();
  private Map<String, Map<String, Object>> handlers = new LinkedHashMap<>();
  private final Map<String, ScheduledTask> tasks = new HashMap<>();
  private final Set<String> busyTasks = new HashSet<>();
  private final Set<NamespacedKey> recipes = new HashSet<>();
  private final Map<UUID, LivingEntity> trackedMobs = new HashMap<>();
  private long generation;
  private long ticks;

  public Definitions(PynnerPlugin plugin) {
    this.plugin = plugin;
  }

  public Map<String, Map<String, Object>> weapons() {
    return weapons;
  }

  public Map<String, Map<String, Object>> mobs() {
    return mobs;
  }

  public Collection<Map<String, Object>> handlers() {
    return handlers.values();
  }

  public Map<String, Object> handler(String id) {
    return handlers.get(id);
  }

  public void validate(Map<String, Object> manifest) {
    Map<String, Object> weaponMap = Frames.map(manifest.get("weapons"));
    Map<String, Object> mobMap = Frames.map(manifest.get("mobs"));
    var candidateWeapons = definitions(weaponMap);
    for (var entry : weaponMap.entrySet()) {
      id(entry.getKey());
      var spec = Frames.map(entry.getValue());
      plugin.items().buildSpec(spec, entry.getKey(), 1);
      Items.positive(spec, "cooldown", 0);
      if (spec.get("recipe") != null) {
        var recipe = plugin.items().recipe(entry.getKey(), spec);
        if (Bukkit.getRecipe(recipe.getKey()) != null && !recipes.contains(recipe.getKey()))
          throw new IllegalArgumentException("Recipe already owned: " + recipe.getKey());
      }
    }
    for (var entry : mobMap.entrySet()) {
      id(entry.getKey());
      var spec = Frames.map(entry.getValue());
      EntityType type = EntityType.valueOf(Frames.text(spec, "base", "ZOMBIE"));
      if (!type.isSpawnable()
          || !LivingEntity.class.isAssignableFrom(Objects.requireNonNull(type.getEntityClass())))
        throw new IllegalArgumentException("Mob base must be spawnable and living");
      for (String field :
          List.of(
              "health",
              "attack_damage",
              "armor",
              "movement_speed",
              "experience",
              "size",
              "tick_seconds")) Items.positive(spec, field, 1);
      if (Frames.number(spec, "health", 20) <= 0
          || Frames.number(spec, "size", 1) <= 0
          || Frames.number(spec, "tick_seconds", 1) <= 0)
        throw new IllegalArgumentException("Mob health, size and tick_seconds must be positive");
      if (spec.get("equipment") instanceof Map<?, ?> equipment)
        equipment.forEach(
            (slot, item) -> {
              EquipmentSlot.valueOf(slot.toString().toUpperCase(Locale.ROOT));
              plugin.items().build(item, 1, candidateWeapons);
            });
      if (spec.get("drops") instanceof List<?> drops)
        for (Object drop : drops) {
          var data = Frames.map(drop);
          plugin
              .items()
              .build(data.get("item"), (int) Frames.number(data, "amount", 1), candidateWeapons);
          double chance = Frames.number(data, "chance", 1);
          if (chance < 0 || chance > 1)
            throw new IllegalArgumentException("Drop chance must be 0..1");
        }
      if (spec.get("attributes") instanceof Map<?, ?> attributes)
        attributes.forEach(
            (name, value) -> {
              Items.attribute(name.toString());
              if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
                throw new IllegalArgumentException("Invalid attribute value");
            });
      World world = Bukkit.getWorlds().getFirst();
      LivingEntity template =
          world.createEntity(
              world.getSpawnLocation(),
              Objects.requireNonNull(type.getEntityClass()).asSubclass(LivingEntity.class));
      // Validate support on an unspawned entity; equipment was validated against candidate
      // definitions above.
      var attributesOnly = new LinkedHashMap<>(spec);
      attributesOnly.remove("equipment");
      applyMob(template, attributesOnly);
    }
    var seen = new HashSet<String>();
    for (Object value : (List<?>) manifest.get("handlers")) {
      var handler = Frames.map(value);
      if (!seen.add(Frames.text(handler, "id", "")))
        throw new IllegalArgumentException("Duplicate handler ID");
      if ("task".equals(handler.get("kind")) && Frames.number(handler, "seconds", 0) <= 0)
        throw new IllegalArgumentException("Invalid task interval");
    }
    plugin.commands().validate((List<?>) manifest.get("handlers"));
  }

  private static void id(String id) {
    if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid ID: " + id);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> definitions(Object value) {
    return (Map<String, Map<String, Object>>) (Map<?, ?>) value;
  }

  public void install(Map<String, Object> manifest, long generation) {
    Map<String, Object> previous =
        Map.of("weapons", weapons, "mobs", mobs, "handlers", new ArrayList<>(handlers.values()));
    long previousGeneration = this.generation;
    try {
      applyManifest(manifest, generation);
    } catch (RuntimeException failure) {
      try {
        applyManifest(previous, previousGeneration);
      } catch (RuntimeException rollback) {
        failure.addSuppressed(rollback);
        plugin.getLogger().severe("Registration rollback failed: " + rollback.getMessage());
      }
      throw failure;
    }
  }

  private void applyManifest(Map<String, Object> manifest, long generation) {
    stopTasks();
    recipes.forEach(Bukkit::removeRecipe);
    recipes.clear();
    weapons = definitions(manifest.get("weapons"));
    mobs = definitions(manifest.get("mobs"));
    var registered = new LinkedHashMap<String, Map<String, Object>>();
    for (Object value : (List<?>) manifest.get("handlers")) {
      var handler = Frames.map(value);
      registered.put(handler.get("id").toString(), handler);
    }
    handlers = registered;
    this.generation = generation;
    plugin.events().install(handlers.values());
    plugin.commands().install(handlers.values());
    trackedMobs.clear();
    for (World world : Bukkit.getWorlds())
      for (LivingEntity entity : world.getLivingEntities()) trackMob(entity);
    for (var entry : weapons.entrySet())
      if (entry.getValue().get("recipe") != null) {
        ShapedRecipe recipe = plugin.items().recipe(entry.getKey(), entry.getValue());
        if (!Bukkit.addRecipe(recipe))
          throw new IllegalArgumentException("Recipe collision: " + entry.getKey());
        recipes.add(recipe.getKey());
      }
    for (var handler : handlers.values())
      if ("task".equals(handler.get("kind"))) {
        String id = handler.get("id").toString();
        long delay = Math.max(1, Math.round(Frames.number(handler, "seconds", 1) * 20));
        Runnable invoke =
            () -> {
              if (!busyTasks.add(id)) return;
              if (!plugin.events().invoke(id, Map.of())) busyTasks.remove(id);
            };
        ScheduledTask task =
            plugin.router().timer(invoke, delay, Frames.flag(handler, "repeating", false));
        tasks.put(id, task);
      }
  }

  public void taskDone(String id, long generation) {
    if (this.generation == generation) busyTasks.remove(id);
  }

  public void cancelTask(String id, String owner) {
    if (!Objects.equals(
        Frames.text(Objects.requireNonNull(handlers.get(id), "Unknown task"), "owner", ""), owner))
      throw new IllegalArgumentException("Task belongs to another script");
    var task = tasks.remove(id);
    if (task != null) task.cancel();
    busyTasks.remove(id);
  }

  public void stopTasks() {
    tasks.values().forEach(ScheduledTask::cancel);
    tasks.clear();
    busyTasks.clear();
  }

  public String mobId(Entity entity) {
    return entity
        .getPersistentDataContainer()
        .get(plugin.items().mobKey, PersistentDataType.STRING);
  }

  public LivingEntity spawn(String id, Location location) {
    Map<String, Object> spec = Objects.requireNonNull(mobs.get(id), "Unknown mob ID");
    LivingEntity living =
        (LivingEntity)
            location
                .getWorld()
                .spawnEntity(location, EntityType.valueOf(Frames.text(spec, "base", "ZOMBIE")));
    living.getPersistentDataContainer().set(plugin.items().mobKey, PersistentDataType.STRING, id);
    applyMob(living, spec);
    trackMob(living);
    living.setHealth(Objects.requireNonNull(living.getAttribute(Attribute.MAX_HEALTH)).getValue());
    hook("mob", id, "on_spawn", net.nehaverse.pynner.events.Snapshots.event("mob_spawn", living));
    return living;
  }

  public void applyMob(LivingEntity living, Map<String, Object> spec) {
    if (spec.get("name") != null) {
      living.customName(Items.text(spec.get("name").toString()));
      living.setCustomNameVisible(true);
    }
    if (spec.get("health") != null) {
      setAttribute(living, Attribute.MAX_HEALTH, Frames.number(spec, "health", 20));
      living.setHealth(Math.min(living.getHealth(), Frames.number(spec, "health", 20)));
    }
    for (var entry :
        Map.of(
                "attack_damage",
                Attribute.ATTACK_DAMAGE,
                "armor",
                Attribute.ARMOR,
                "movement_speed",
                Attribute.MOVEMENT_SPEED,
                "size",
                Attribute.SCALE)
            .entrySet())
      if (spec.get(entry.getKey()) != null)
        setAttribute(living, entry.getValue(), Frames.number(spec, entry.getKey(), 1));
    if (spec.get("ai") != null) living.setAI(Frames.flag(spec, "ai", true));
    if (spec.get("equipment") instanceof Map<?, ?> equipment && living.getEquipment() != null)
      equipment.forEach(
          (slot, item) ->
              living
                  .getEquipment()
                  .setItem(
                      EquipmentSlot.valueOf(slot.toString().toUpperCase(Locale.ROOT)),
                      plugin.items().build(item, 1)));
    if (spec.get("attributes") instanceof Map<?, ?> attributes)
      attributes.forEach(
          (name, value) ->
              setAttribute(
                  living, Items.attribute(name.toString()), ((Number) value).doubleValue()));
    if (spec.get("pdc") instanceof Map<?, ?> pdc)
      pdc.forEach(
          (name, value) ->
              Items.setPdc(living.getPersistentDataContainer(), name.toString(), value));
  }

  private void setAttribute(LivingEntity living, Attribute attribute, double value) {
    var instance = living.getAttribute(attribute);
    if (instance == null)
      throw new IllegalArgumentException("Entity does not support " + attribute);
    instance.setBaseValue(value);
  }

  public void hook(String kind, String id, String hook, Map<String, Object> payload) {
    var definitions = kind.equals("weapon") ? weapons : mobs;
    var spec = definitions.get(id);
    if (spec == null || !(spec.get("hooks") instanceof Map<?, ?> hooks) || hooks.get(hook) == null)
      return;
    payload.put("definition_id", id);
    payload.put("hook", hook);
    plugin.events().invoke(hooks.get(hook).toString(), Map.of("event", payload));
  }

  public boolean hasHook(String kind, String id, String hook) {
    var spec = (kind.equals("weapon") ? weapons : mobs).get(id);
    return spec != null && spec.get("hooks") instanceof Map<?, ?> hooks && hooks.containsKey(hook);
  }

  public void trackMob(LivingEntity entity) {
    if (mobId(entity) != null) trackedMobs.put(entity.getUniqueId(), entity);
  }

  public void forgetMob(Entity entity) {
    trackedMobs.remove(entity.getUniqueId());
  }

  public void tickMobs() {
    ticks++;
    if (plugin.supervisor().active() == null
        || mobs.values().stream()
            .noneMatch(spec -> Frames.map(spec.get("hooks")).containsKey("on_tick"))) return;
    trackedMobs.values().removeIf(entity -> !entity.isValid());
    for (LivingEntity entity : trackedMobs.values()) {
      String id = mobId(entity);
      var spec = mobs.get(id);
      if (spec == null || !hasHook("mob", id, "on_tick")) continue;
      long interval = Math.max(1, Math.round(Frames.number(spec, "tick_seconds", 1) * 20));
      if (ticks % interval == 0)
        hook("mob", id, "on_tick", net.nehaverse.pynner.events.Snapshots.event("mob_tick", entity));
    }
  }

  public void close() {
    stopTasks();
    trackedMobs.clear();
    recipes.forEach(Bukkit::removeRecipe);
    recipes.clear();
    plugin.commands().clear();
  }
}
