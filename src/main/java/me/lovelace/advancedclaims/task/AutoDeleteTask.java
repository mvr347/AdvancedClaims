package me.lovelace.advancedclaims.task;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoDeleteTask {
    private final AdvancedClaims plugin;
    private final long maxInactiveMillis;
    private final boolean dropAnchor;
    private ScheduledTask task;

    public AutoDeleteTask(AdvancedClaims plugin) {
        this.plugin = plugin;
        int days = plugin.getConfigManager().getConfig().getInt("auto-delete.days-inactive", 7);
        this.maxInactiveMillis = days * 24L * 60L * 60L * 1000L;
        this.dropAnchor = plugin.getConfigManager().getConfig().getBoolean("auto-delete.drop-anchor", true);
    }

    public void start() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduler -> {
            if (!plugin.getConfigManager().getConfig().getBoolean("auto-delete.enabled", true)) return;

            long currentTime = System.currentTimeMillis();
            List<Claim> toDelete = new ArrayList<>();

            for (Claim claim : plugin.getClaimManager().getAllClaims()) {
                if (currentTime - claim.getLastActive() > maxInactiveMillis) {
                    toDelete.add(claim);
                }
            }

            for (Claim claim : toDelete) {
                plugin.getClaimManager().removeClaimFromCache(claim.getId());
                plugin.getStorage().deleteClaimAsync(claim.getId());

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    claim.getAnchorLocation().getBlock().setType(Material.AIR);

                    if (dropAnchor && claim.getWorld() != null) {
                        int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
                        me.lovelace.advancedclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
                        String tierId = currentTier != null ? currentTier.id() : "tier-1";

                        ItemStack anchor = plugin.getAnchorManager().createAnchorItem(tierId);
                        if (anchor != null) {
                            claim.getWorld().dropItemNaturally(claim.getAnchorLocation(), anchor);
                        }
                    }

                    plugin.getLogger().info("Deleted inactive claim of " + claim.getOwnerUuid());
                });
            }
        }, 5L, 5L, TimeUnit.MINUTES);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }
}