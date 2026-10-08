package net.nehaverse.pynner.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.protocol.Frames;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public final class EventBridge implements Listener {
  private final PynnerPlugin plugin;
  private final BlockingQueue<Queued> queue;
  private final BlockingQueue<Queued> observations;
  private final AtomicLong queuedBytes = new AtomicLong();
  private final AtomicLong dropped = new AtomicLong();
  private final Map<String, Long> rateTimes = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Object>> chatSnapshots = new ConcurrentHashMap<>();
  private final Map<UUID, Set<String>> chatPermissions = new ConcurrentHashMap<>();
  private volatile Map<String, List<Map<String, Object>>> subscriptions = Map.of();

  private record Queued(long generation, Map<String, Object> invocation, int bytes) {}

  public EventBridge(PynnerPlugin plugin) {
    this.plugin = plugin;
    queue = new ArrayBlockingQueue<>(Math.max(1, plugin.getConfig().getInt("queues.events", 4096)));
    observations =
        new ArrayBlockingQueue<>(Math.max(1, plugin.getConfig().getInt("queues.events", 4096)));
    register(PlayerJoinEvent.class, "player_join", PlayerJoinEvent::getPlayer, e -> Map.of());
    register(PlayerQuitEvent.class, "player_quit", PlayerQuitEvent::getPlayer, e -> Map.of());
    register(
        PlayerMoveEvent.class,
        "player_move",
        PlayerMoveEvent::getPlayer,
        e ->
            Map.of(
                "from",
                Snapshots.location(e.getFrom()),
                "to",
                Snapshots.location(Objects.requireNonNull(e.getTo()))));
    register(
        PlayerInteractEvent.class,
        "player_interact",
        PlayerInteractEvent::getPlayer,
        e -> {
          var data = new LinkedHashMap<String, Object>();
          data.put("action", e.getAction().name());
          data.put("hand", e.getHand() == null ? "" : e.getHand().name());
          if (e.getClickedBlock() != null)
            data.put("material", e.getClickedBlock().getType().name());
          return data;
        });
    register(
        EntityDamageEvent.class,
        "entity_damage",
        EntityDamageEvent::getEntity,
        e -> Map.of("damage", e.getFinalDamage(), "cause", e.getCause().name()));
    register(
        EntityDamageByEntityEvent.class,
        "entity_damage_by_entity",
        EntityDamageByEntityEvent::getEntity,
        e ->
            Map.of(
                "damage",
                e.getFinalDamage(),
                "target",
                Snapshots.entity(e.getEntity()),
                "attacker",
                Snapshots.entity(attacker(e.getDamager()))));
    register(
        EntityDeathEvent.class,
        "entity_death",
        EntityDeathEvent::getEntity,
        e -> Map.of("experience", e.getDroppedExp()));
    register(EntitySpawnEvent.class, "entity_spawn", EntitySpawnEvent::getEntity, e -> Map.of());
    register(
        BlockBreakEvent.class,
        "block_break",
        BlockBreakEvent::getPlayer,
        e ->
            Map.of(
                "material",
                e.getBlock().getType().name(),
                "block",
                Snapshots.location(e.getBlock().getLocation())));
    register(
        BlockPlaceEvent.class,
        "block_place",
        BlockPlaceEvent::getPlayer,
        e ->
            Map.of(
                "material",
                e.getBlockPlaced().getType().name(),
                "block",
                Snapshots.location(e.getBlockPlaced().getLocation())));
    register(
        InventoryClickEvent.class,
        "inventory_click",
        e -> e.getWhoClicked(),
        e -> plugin.menus().click(e));
    register(
        ProjectileHitEvent.class,
        "projectile_hit",
        ProjectileHitEvent::getEntity,
        e -> {
          var data = new LinkedHashMap<String, Object>();
          if (e.getHitEntity() != null) data.put("target", Snapshots.entity(e.getHitEntity()));
          if (e.getHitBlock() != null)
            data.put("block", Snapshots.location(e.getHitBlock().getLocation()));
          return data;
        });
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  public static Entity attacker(Entity entity) {
    return entity instanceof Projectile projectile
            && projectile.getShooter() instanceof Entity shooter
        ? shooter
        : entity;
  }

  private <E extends Event> void register(
      Class<E> type,
      String name,
      Function<E, Entity> subject,
      Function<E, Map<String, Object>> extra) {
    plugin
        .getServer()
        .getPluginManager()
        .registerEvent(
            type,
            this,
            EventPriority.NORMAL,
            (listener, event) -> {
              if (!type.isInstance(event)
                  || !(event instanceof Cancellable cancellable)
                  || subscriptions.getOrDefault(name, List.of()).stream()
                      .noneMatch(h -> Frames.flag(h, "cancel", false))) return;
              E typed = type.cast(event);
              Entity entity = subject.apply(typed);
              Map<String, Object> payload = Snapshots.event(name, entity);
              payload.putAll(extra.apply(typed));
              for (var handler : subscriptions.getOrDefault(name, List.of()))
                if (Frames.flag(handler, "cancel", false) && matches(handler, payload, entity))
                  cancellable.setCancelled(true);
            },
            plugin,
            false);
    plugin
        .getServer()
        .getPluginManager()
        .registerEvent(
            type,
            this,
            EventPriority.MONITOR,
            (listener, event) -> {
              if (!type.isInstance(event) || !subscriptions.containsKey(name)) return;
              E typed = type.cast(event);
              Entity entity = subject.apply(typed);
              Map<String, Object> payload = Snapshots.event(name, entity);
              payload.putAll(extra.apply(typed));
              payload.put("cancelled", event instanceof Cancellable c && c.isCancelled());
              publish(name, payload, entity);
            },
            plugin,
            false);
  }

  public synchronized void install(Collection<Map<String, Object>> handlers) {
    var index = new HashMap<String, List<Map<String, Object>>>();
    for (var handler : handlers)
      if ("event".equals(handler.get("kind")))
        index
            .computeIfAbsent(handler.get("event").toString(), ignored -> new ArrayList<>())
            .add(handler);
    subscriptions = Map.copyOf(index);
    rateTimes.clear();
    queue.clear();
    observations.clear();
    queuedBytes.set(0);
    chatSnapshots.clear();
    chatPermissions.clear();
    refreshChatSnapshots();
  }

  private boolean matches(Map<String, Object> handler, Map<String, Object> payload, Entity entity) {
    if (!ChatMessageFilter.matches(handler, payload)) return false;
    if (handler.get("world") != null && !handler.get("world").equals(payload.get("world")))
      return false;
    if (handler.get("material") != null && !handler.get("material").equals(payload.get("material")))
      return false;
    var snapshot = Frames.map(payload.get("entity"));
    if (handler.get("entity_type") != null
        && !handler.get("entity_type").equals(snapshot.get("type"))) return false;
    if (handler.get("permission") != null) {
      String permission = handler.get("permission").toString();
      if (entity != null) {
        if (!(entity instanceof Player player) || !player.hasPermission(permission)) return false;
      } else if (!chatPermissions
          .getOrDefault(UUID.fromString(snapshot.get("uuid").toString()), Set.of())
          .contains(permission)) return false;
    }
    double distance = Frames.number(handler, "min_distance", 0);
    if (distance > 0
        && payload.get("from") instanceof Map<?, ?>
        && payload.get("to") instanceof Map<?, ?>) {
      var from = Frames.map(payload.get("from"));
      var to = Frames.map(payload.get("to"));
      if (Objects.equals(from.get("world"), to.get("world"))) {
        double dx = Frames.number(to, "x", 0) - Frames.number(from, "x", 0),
            dy = Frames.number(to, "y", 0) - Frames.number(from, "y", 0),
            dz = Frames.number(to, "z", 0) - Frames.number(from, "z", 0);
        if (dx * dx + dy * dy + dz * dz < distance * distance) return false;
      }
    }
    return true;
  }

  private void publish(String name, Map<String, Object> payload, Entity subject) {
    for (var handler : subscriptions.getOrDefault(name, List.of())) {
      if (Frames.flag(payload, "cancelled", false)
          && !Frames.flag(handler, "include_cancelled", false)
          && !Frames.flag(handler, "cancel", false)) continue;
      if (!matches(handler, payload, subject)) continue;
      double limit = Frames.number(handler, "rate_limit", 0);
      if (limit > 0) {
        String key = handler.get("id") + ":" + Frames.map(payload.get("entity")).get("uuid");
        long now = System.nanoTime();
        Long previous = rateTimes.get(key);
        if (previous != null && now - previous < 1_000_000_000.0 / limit) continue;
        if (rateTimes.size() > 65536) rateTimes.clear();
        rateTimes.put(key, now);
      }
      invoke(handler.get("id").toString(), Map.of("event", payload));
    }
  }

  public synchronized boolean invoke(String handler, Map<String, Object> fields) {
    var session = plugin.supervisor() == null ? null : plugin.supervisor().active();
    if (session == null || !session.active) return false;
    var invocation = new LinkedHashMap<String, Object>(fields);
    invocation.put("handler", handler);
    boolean observation =
        fields.get("event") instanceof Map<?, ?> payload
            && Set.of("player_move", "mob_move", "mob_tick").contains(payload.get("event"));
    if (observation && session.pythonQueue > 768) {
      dropped.incrementAndGet();
      return false;
    }
    int size;
    try {
      size = Frames.encode(invocation).length;
    } catch (IOException exception) {
      dropped.incrementAndGet();
      return false;
    }
    // Bound individual invocations so a batch remains below the protocol cap.
    if (size > 512_000 || queuedBytes.addAndGet(size) > 8 * 1024 * 1024) {
      if (size <= 512_000) queuedBytes.addAndGet(-size);
      dropped.incrementAndGet();
      return false;
    }
    if (!(observation ? observations : queue)
        .offer(new Queued(session.generation, invocation, size))) {
      queuedBytes.addAndGet(-size);
      dropped.incrementAndGet();
      return false;
    }
    return true;
  }

  public synchronized void flush() {
    var session = plugin.supervisor().active();
    if (session == null || !session.active) {
      queue.clear();
      observations.clear();
      queuedBytes.set(0);
      return;
    }
    int count =
        Math.max(1, Math.min(256, plugin.getConfig().getInt("queues.event-batch-size", 128)));
    var batch = new ArrayList<Map<String, Object>>();
    int bytes = 0;
    for (int index = 0; index < count; index++) {
      BlockingQueue<Queued> lane = queue.isEmpty() ? observations : queue;
      Queued invocation = lane.peek();
      if (invocation == null || bytes + invocation.bytes > 900_000) break;
      invocation = lane.poll();
      if (invocation == null) break;
      queuedBytes.addAndGet(-invocation.bytes);
      if (invocation.generation != session.generation) continue;
      batch.add(invocation.invocation);
      bytes += invocation.bytes;
    }
    if (!batch.isEmpty() && !session.send("events", "events", Map.of("invocations", batch))) {
      dropped.addAndGet(batch.size());
      for (var invocation : batch)
        plugin.definitions().taskDone(invocation.get("handler").toString(), session.generation);
    }
  }

  public long dropped() {
    return dropped.get();
  }

  public int queued() {
    return queue.size() + observations.size();
  }

  public void refreshChatSnapshots() {
    if (!subscriptions.containsKey("async_chat")) return;
    Set<String> permissions = new HashSet<>();
    for (var handler : subscriptions.get("async_chat"))
      if (handler.get("permission") != null) permissions.add(handler.get("permission").toString());
    for (Player player : Bukkit.getOnlinePlayers()) {
      chatSnapshots.put(player.getUniqueId(), Snapshots.entity(player));
      var granted = new HashSet<String>();
      for (String permission : permissions)
        if (player.hasPermission(permission)) granted.add(permission);
      chatPermissions.put(player.getUniqueId(), granted);
    }
    chatSnapshots.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    chatPermissions.keySet().retainAll(chatSnapshots.keySet());
  }

  private Map<String, Object> chatPayload(AsyncChatEvent event) {
    Map<String, Object> snapshot = chatSnapshots.get(event.getPlayer().getUniqueId());
    if (snapshot == null) return null;
    var payload = new LinkedHashMap<String, Object>();
    payload.put("event", "async_chat");
    payload.put("entity", snapshot);
    payload.put("player", snapshot);
    payload.put("world", Frames.map(snapshot.get("location")).get("world"));
    payload.put("message", PlainTextComponentSerializer.plainText().serialize(event.message()));
    payload.put("cancelled", event.isCancelled());
    return payload;
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void chatRule(AsyncChatEvent event) {
    if (!subscriptions.containsKey("async_chat")) return;
    var payload = chatPayload(event);
    if (payload == null) return;
    for (var handler : subscriptions.get("async_chat"))
      if (Frames.flag(handler, "cancel", false) && matches(handler, payload, null))
        event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void chatNotice(AsyncChatEvent event) {
    if (!subscriptions.containsKey("async_chat")) return;
    var payload = chatPayload(event);
    if (payload != null) publish("async_chat", payload, null);
  }
}
