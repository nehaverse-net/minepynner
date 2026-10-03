package net.nehaverse.pynner.scheduling;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Scheduler boundary; Folia certification requires separate integration tests. */
public final class ExecutionRouter {
  private final Plugin plugin;

  public ExecutionRouter(Plugin plugin) {
    this.plugin = plugin;
  }

  public boolean entity(Entity entity, Runnable operation, Runnable retired) {
    return entity.getScheduler().execute(plugin, operation, retired, 1);
  }

  public void region(Location location, Runnable operation) {
    plugin.getServer().getRegionScheduler().execute(plugin, location, operation);
  }

  public void global(Runnable operation) {
    plugin.getServer().getGlobalRegionScheduler().execute(plugin, operation);
  }

  public void async(Runnable operation) {
    plugin.getServer().getAsyncScheduler().runNow(plugin, task -> operation.run());
  }

  public ScheduledTask timer(Runnable operation, long delayTicks, boolean repeating) {
    var scheduler = plugin.getServer().getGlobalRegionScheduler();
    return repeating
        ? scheduler.runAtFixedRate(plugin, task -> operation.run(), delayTicks, delayTicks)
        : scheduler.runDelayed(plugin, task -> operation.run(), delayTicks);
  }
}
