package net.nehaverse.pynner.events;

import io.papermc.paper.event.entity.EntityMoveEvent;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.protocol.Frames;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class NativeHooks implements Listener {
  private final PynnerPlugin plugin;
  private final Map<String, Long> cooldowns = new HashMap<>();
  private final Map<UUID, String> equipped = new HashMap<>();
  private final Map<UUID, Attack> lastWeaponAttack = new HashMap<>();

  private record Attack(UUID attacker, String weapon, long expires) {}

  public NativeHooks(PynnerPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  private String weapon(LivingEntity entity) {
    return entity.getEquipment() == null
        ? null
        : plugin.items().weaponId(entity.getEquipment().getItemInMainHand());
  }

  private Map<String, Object> combat(String name, Entity entity, Entity target, double damage) {
    var payload = Snapshots.event(name, entity);
    if (target != null) payload.put("target", Snapshots.entity(target));
    payload.put("damage", damage);
    return payload;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void cooldown(EntityDamageByEntityEvent event) {
    Entity attacker = EventBridge.attacker(event.getDamager());
    if (!(attacker instanceof LivingEntity living) || event.getDamager() instanceof Projectile)
      return;
    String id = weapon(living);
    var spec = plugin.definitions().weapons().get(id);
    if (spec == null) return;
    double cooldown = Frames.number(spec, "cooldown", 0);
    if (cooldown <= 0) return;
    String key = living.getUniqueId() + ":" + id;
    long now = System.nanoTime();
    if (cooldowns.getOrDefault(key, 0L) > now) {
      event.setCancelled(true);
      return;
    }
    cooldowns.put(key, now + (long) (cooldown * 1_000_000_000));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void hit(EntityDamageByEntityEvent event) {
    Entity attacker = EventBridge.attacker(event.getDamager());
    if (attacker instanceof LivingEntity living && !(event.getDamager() instanceof Projectile)) {
      String id = weapon(living);
      if (id != null && event.getFinalDamage() > 0) {
        lastWeaponAttack.put(
            event.getEntity().getUniqueId(),
            new Attack(living.getUniqueId(), id, System.nanoTime() + 10_000_000_000L));
        plugin
            .definitions()
            .hook(
                "weapon",
                id,
                "on_hit",
                combat("weapon_hit", living, event.getEntity(), event.getFinalDamage()));
        use(living, id);
      }
    }
    String mob = plugin.definitions().mobId(attacker);
    if (mob != null)
      plugin
          .definitions()
          .hook(
              "mob",
              mob,
              "on_attack",
              combat("mob_attack", attacker, event.getEntity(), event.getFinalDamage()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void damage(EntityDamageEvent event) {
    String mob = plugin.definitions().mobId(event.getEntity());
    if (mob != null)
      plugin
          .definitions()
          .hook(
              "mob",
              mob,
              "on_damage",
              combat("mob_damage", event.getEntity(), event.getEntity(), event.getFinalDamage()));
    if (event.getEntity() instanceof LivingEntity living) {
      String weapon = weapon(living);
      if (weapon != null)
        plugin
            .definitions()
            .hook(
                "weapon",
                weapon,
                "on_damage",
                combat("weapon_damage", living, living, event.getFinalDamage()));
    }
  }

  private void use(LivingEntity living, String id) {
    ItemStack item = Objects.requireNonNull(living.getEquipment()).getItemInMainHand();
    var meta = item.getItemMeta();
    Integer remaining =
        meta.getPersistentDataContainer().get(plugin.items().usesKey, PersistentDataType.INTEGER);
    if (remaining == null) return;
    if (remaining <= 1) {
      plugin.definitions().hook("weapon", id, "on_break", Snapshots.event("weapon_break", living));
      item.setAmount(Math.max(0, item.getAmount() - 1));
    } else {
      meta.getPersistentDataContainer()
          .set(plugin.items().usesKey, PersistentDataType.INTEGER, remaining - 1);
      item.setItemMeta(meta);
    }
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void drops(EntityDeathEvent event) {
    String mob = plugin.definitions().mobId(event.getEntity());
    var spec = plugin.definitions().mobs().get(mob);
    if (spec == null) return;
    if (spec.get("experience") != null)
      event.setDroppedExp((int) Frames.number(spec, "experience", 0));
    if (spec.get("drops") instanceof List<?> drops) {
      event.getDrops().clear();
      for (Object value : drops) {
        var drop = Frames.map(value);
        if (ThreadLocalRandom.current().nextDouble() < Frames.number(drop, "chance", 1))
          event
              .getDrops()
              .add(plugin.items().build(drop.get("item"), (int) Frames.number(drop, "amount", 1)));
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void death(EntityDeathEvent event) {
    String mob = plugin.definitions().mobId(event.getEntity());
    if (mob != null)
      plugin
          .definitions()
          .hook("mob", mob, "on_death", Snapshots.event("mob_death", event.getEntity()));
    Attack attack = lastWeaponAttack.remove(event.getEntity().getUniqueId());
    Entity cause = event.getDamageSource().getCausingEntity();
    if (attack != null
        && attack.expires > System.nanoTime()
        && cause != null
        && attack.attacker.equals(cause.getUniqueId()))
      plugin
          .definitions()
          .hook(
              "weapon",
              attack.weapon,
              "on_kill",
              combat("weapon_kill", cause, event.getEntity(), 0));
    plugin.definitions().forgetMob(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void interact(PlayerInteractEvent event) {
    if (event.useItemInHand() == Event.Result.DENY) return;
    if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
    String id = plugin.items().weaponId(event.getItem());
    if (id == null) return;
    String hook =
        event.getAction().isLeftClick()
            ? "on_left_click"
            : event.getAction().isRightClick() ? "on_right_click" : null;
    if (hook != null)
      plugin
          .definitions()
          .hook("weapon", id, hook, Snapshots.event("weapon_interact", event.getPlayer()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void broken(PlayerItemBreakEvent event) {
    String id = plugin.items().weaponId(event.getBrokenItem());
    if (id != null)
      plugin
          .definitions()
          .hook("weapon", id, "on_break", Snapshots.event("weapon_break", event.getPlayer()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void target(EntityTargetEvent event) {
    String mob = plugin.definitions().mobId(event.getEntity());
    if (mob != null)
      plugin
          .definitions()
          .hook(
              "mob",
              mob,
              "on_target",
              combat("mob_target", event.getEntity(), event.getTarget(), 0));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void move(EntityMoveEvent event) {
    String mob = plugin.definitions().mobId(event.getEntity());
    if (mob != null && plugin.definitions().hasHook("mob", mob, "on_move"))
      plugin
          .definitions()
          .hook("mob", mob, "on_move", Snapshots.event("mob_move", event.getEntity()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void interactMob(PlayerInteractEntityEvent event) {
    String mob = plugin.definitions().mobId(event.getRightClicked());
    if (mob != null) {
      var payload = Snapshots.event("mob_interact", event.getRightClicked());
      payload.put("player", Snapshots.entity(event.getPlayer()));
      plugin.definitions().hook("mob", mob, "on_interact", payload);
    }
  }

  @EventHandler
  public void load(EntitiesLoadEvent event) {
    for (Entity entity : event.getEntities())
      if (entity instanceof LivingEntity living) plugin.definitions().trackMob(living);
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    equipped.remove(event.getPlayer().getUniqueId());
    cooldowns.keySet().removeIf(key -> key.startsWith(event.getPlayer().getUniqueId().toString()));
  }

  public void tick() {
    long now = System.nanoTime();
    cooldowns.entrySet().removeIf(entry -> entry.getValue() < now);
    lastWeaponAttack.values().removeIf(attack -> attack.expires < now);
    for (Player player : Bukkit.getOnlinePlayers()) {
      String next = weapon(player), previous = equipped.get(player.getUniqueId());
      if (Objects.equals(next, previous)) continue;
      if (previous != null)
        plugin
            .definitions()
            .hook("weapon", previous, "on_unequip", Snapshots.event("weapon_unequip", player));
      if (next != null) {
        equipped.put(player.getUniqueId(), next);
        plugin
            .definitions()
            .hook("weapon", next, "on_equip", Snapshots.event("weapon_equip", player));
      } else equipped.remove(player.getUniqueId());
    }
  }
}
