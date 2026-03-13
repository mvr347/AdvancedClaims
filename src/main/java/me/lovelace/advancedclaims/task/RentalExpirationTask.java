package me.lovelace.advancedclaims.task;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.concurrent.TimeUnit;

public class RentalExpirationTask {
    private final AdvancedClaims plugin;
    private ScheduledTask task;

    public RentalExpirationTask(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduler -> {
            long now = System.currentTimeMillis();
            long taxInterval = plugin.getRentalManager().getTaxDays() * 86400000L;

            for (Claim claim : plugin.getClaimManager().getAllClaims()) {
                if (!claim.isRentalPlot()) continue;

                // 1. Check expiration
                if (claim.isRented() && claim.getRentalEndTime() < now) {
                    Bukkit.getScheduler().runTask(plugin, () -> terminateRent(claim));
                    continue;
                }

                // 2. Check taxes
                if (claim.isRented()) {
                    if (claim.getLastTaxTime() == 0) {
                        claim.setLastTaxTime(now);
                        plugin.getStorage().saveClaimAsync(claim);
                    }

                    if (now - claim.getLastTaxTime() >= taxInterval) {
                        long taxAmount = Math.round(claim.getRentalPrice() * (plugin.getRentalManager().getTaxPercentage() / 100.0));
                        Player renter = Bukkit.getPlayer(claim.getOwnerUuid());

                        if (renter != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (plugin.getCurrencyManager().hasEnough(renter, taxAmount)) {
                                    if (plugin.getCurrencyManager().takeCurrency(renter, taxAmount)) {
                                        claim.setLastTaxTime(now);
                                        plugin.getStorage().saveClaimAsync(claim);
                                        renter.sendMessage(plugin.getConfigManager().getMessage("rental-tax-paid", "amount", String.valueOf(taxAmount)));
                                    }
                                } else {
                                    terminateRent(claim);
                                    renter.sendMessage(plugin.getConfigManager().getMessage("rental-tax-failed", "amount", String.valueOf(taxAmount)));
                                }
                            });
                        }
                    }
                }
            }
        }, 1L, 1L, TimeUnit.MINUTES);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }

    private void terminateRent(Claim claim) {
        claim.setRentalEndTime(0);
        claim.setOwnerUuid(claim.getParentClaimId());
        plugin.getStorage().saveClaimAsync(claim);
        plugin.getRentalManager().updateIndicator(claim);
    }
}