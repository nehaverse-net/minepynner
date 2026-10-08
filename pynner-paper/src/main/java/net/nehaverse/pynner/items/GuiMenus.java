package net.nehaverse.pynner.items;

import java.util.*;
import net.nehaverse.pynner.PynnerPlugin;
import net.nehaverse.pynner.events.Snapshots;
import net.nehaverse.pynner.protocol.Frames;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Read-only Python menus, and guarded sorting of an already-open chest. */
public final class GuiMenus implements Listener {
  private final PynnerPlugin plugin;
  private final Map<UUID, ViewState> views = new HashMap<>();

  private record ViewState(InventoryView view, String id) {}

  private static final class Menu implements InventoryHolder {
    final String id;
    Inventory inventory;

    Menu(String id) {
      this.id = id;
    }

    public Inventory getInventory() {
      return inventory;
    }
  }

  public GuiMenus(PynnerPlugin plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  public String open(Player player, Map<String, Object> args) {
    int size = (int) Frames.number(args, "size", 9);
    String id = Frames.text(args, "gui_id", "");
    if (size < 9 || size > 54 || size % 9 != 0 || id.isBlank())
      throw new IllegalArgumentException("Invalid GUI size or ID");
    var holder = new Menu(id);
    holder.inventory =
        Bukkit.createInventory(holder, size, Items.text(Frames.text(args, "title", "")));
    for (var entry : Frames.map(args.get("items")).entrySet()) {
      int slot = Integer.parseInt(entry.getKey());
      if (slot < 0 || slot >= size)
        throw new IllegalArgumentException("GUI slot outside inventory");
      holder.inventory.setItem(slot, plugin.items().build(entry.getValue(), 1));
    }
    player.openInventory(holder.inventory);
    return state(player).id();
  }

  private InventoryView requireMenu(Player player, String id) {
    var view = requireView(player, id);
    if (!(view.getTopInventory().getHolder() instanceof Menu))
      throw new IllegalArgumentException("Only Pynner menus can be updated or switched");
    return view;
  }

  public boolean update(Player player, Map<String, Object> args) {
    var top = requireMenu(player, Frames.text(args, "view_id", "")).getTopInventory();
    var contents =
        Frames.flag(args, "clear", false) ? new ItemStack[top.getSize()] : top.getContents();
    // Build every replacement before touching the live inventory.
    for (var entry : Frames.map(args.get("items")).entrySet()) {
      int slot = Integer.parseInt(entry.getKey());
      if (slot < 0 || slot >= contents.length)
        throw new IllegalArgumentException("GUI slot outside inventory");
      contents[slot] = entry.getValue() == null ? null : plugin.items().build(entry.getValue(), 1);
    }
    top.setContents(contents);
    return true;
  }

  public String switchMenu(Player player, Map<String, Object> args) {
    requireMenu(player, Frames.text(args, "view_id", ""));
    return open(player, args);
  }

  private ViewState state(Player player) {
    var current = player.getOpenInventory();
    var previous = views.get(player.getUniqueId());
    if (previous == null || previous.view() != current) {
      previous = new ViewState(current, UUID.randomUUID().toString());
      views.put(player.getUniqueId(), previous);
    }
    return previous;
  }

  public InventoryView requireView(Player player, String id) {
    var state = state(player);
    if (!state.id().equals(id))
      throw new IllegalArgumentException("Inventory view changed; ignoring stale click");
    return state.view();
  }

  public Map<String, Object> click(InventoryClickEvent event) {
    var player = (Player) event.getWhoClicked();
    var view = event.getView();
    var data = new LinkedHashMap<String, Object>();
    data.put("slot", event.getRawSlot());
    data.put("click", event.getClick().name());
    data.put("view_id", state(player).id());
    data.put("inventory_type", view.getTopInventory().getType().name());
    data.put("top_size", view.getTopInventory().getSize());
    data.put(
        "in_top", event.getRawSlot() >= 0 && event.getRawSlot() < view.getTopInventory().getSize());
    data.put("empty", empty(event.getCurrentItem()));
    data.put("cursor_empty", empty(event.getCursor()));
    data.put("item", Snapshots.item(event.getCurrentItem()));
    data.put("gui_id", view.getTopInventory().getHolder() instanceof Menu menu ? menu.id : "");
    return data;
  }

  public boolean sort(Player player, String id) {
    var view = requireView(player, id);
    var top = view.getTopInventory();
    if (top.getType() != InventoryType.CHEST || top.getHolder() instanceof Menu)
      throw new IllegalArgumentException("Only ordinary open chests can be sorted");
    if (!empty(player.getItemOnCursor()))
      throw new IllegalArgumentException("Empty the cursor before sorting");
    top.setContents(sorted(top.getContents()));
    return true;
  }

  public static ItemStack[] sorted(ItemStack[] source) {
    var stacks = new ArrayList<ItemStack>();
    for (var original : source) {
      if (empty(original)) continue;
      var remaining = original.clone();
      for (var stack : stacks) {
        if (!stack.isSimilar(remaining)) continue;
        int transfer =
            Math.min(
                remaining.getAmount(), Math.max(0, stack.getMaxStackSize() - stack.getAmount()));
        stack.setAmount(stack.getAmount() + transfer);
        remaining.setAmount(remaining.getAmount() - transfer);
        if (remaining.getAmount() == 0) break;
      }
      if (remaining.getAmount() > 0) stacks.add(remaining);
    }
    stacks.sort(Comparator.comparing(stack -> stack.getType().name()));
    return stacks.toArray(new ItemStack[source.length]);
  }

  private static boolean empty(ItemStack item) {
    return item == null || item.getType().isAir();
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void protectClick(InventoryClickEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void protectDrag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
  }

  @EventHandler
  public void close(InventoryCloseEvent event) {
    views.remove(event.getPlayer().getUniqueId());
  }

  public void shutdown() {
    for (var player : Bukkit.getOnlinePlayers())
      if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)
        player.closeInventory();
    views.clear();
  }

  public void closeMenus() {
    for (var player : Bukkit.getOnlinePlayers()) {
      var inventory = player.getOpenInventory().getTopInventory();
      if (inventory.getHolder() instanceof Menu)
        plugin
            .router()
            .entity(
                player,
                () -> {
                  if (player.getOpenInventory().getTopInventory().equals(inventory))
                    player.closeInventory();
                },
                () -> {});
    }
  }
}
