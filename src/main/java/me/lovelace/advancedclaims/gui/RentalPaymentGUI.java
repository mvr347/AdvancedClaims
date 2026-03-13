package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

public class RentalPaymentGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Claim plot;

    public RentalPaymentGUI(AdvancedClaims plugin, Claim plot) {
        super(9, plugin.getConfigManager().getComponent("rental-payment.title-short"));
        this.plugin = plugin;
        this.plot = plot;
        this.inventory = Bukkit.createInventory(this, InventoryType.HOPPER, plugin.getConfigManager().getComponent("rental-payment.title", "name", plot.getName()));
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        inventory.setItem(0, createHead(HEAD_DELETE_YES,
                plugin.getConfigManager().getComponent("rental-payment.confirm-name"),
                plugin.getConfigManager().getHelpMessage("rental-payment.confirm-lore", "price", String.valueOf(plot.getRentalPrice()))));

        inventory.setItem(2, createHead(HEAD_INFO,
                plugin.getConfigManager().getComponent("rental-payment.info-name"),
                plugin.getConfigManager().getHelpMessage("rental-payment.info-lore", "name", plot.getName(), "price", String.valueOf(plot.getRentalPrice()))));

        inventory.setItem(4, createHead(HEAD_DELETE_NO,
                plugin.getConfigManager().getComponent("common.cancel"),
                plugin.getConfigManager().getHelpMessage("common.cancel-lore")));

        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == 0) {
            player.closeInventory();
            plugin.getConfigManager().playSound(player, "gui-click");
        } else if (event.getSlot() == 4) {
            long ownedCount = plugin.getClaimManager().getAllClaims().stream()
                    .filter(Claim::isRentalPlot)
                    .filter(c -> c.getOwnerUuid() != null && c.getOwnerUuid().equals(player.getUniqueId()))
                    .count();

            if (ownedCount >= 1 && !player.hasPermission("advancedclaims.rental.bypasslimit")) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-limit-reached"));
                plugin.getConfigManager().playSound(player, "anchor-error");
                player.closeInventory();
                return;
            }

            long price = plot.getRentalPrice();
            if (plugin.getCurrencyManager().hasEnough(player, price)) {
                if (plugin.getCurrencyManager().takeCurrency(player, price)) {
                    plot.setOwnerUuid(player.getUniqueId());
                    plot.setRentalEndTime(System.currentTimeMillis() + plugin.getRentalManager().getTaxDays() * 86400000L);
                    plot.setTrust(player.getUniqueId(), TrustLevel.OWNER);
                    plugin.getStorage().saveClaimAsync(plot);
                    plugin.getRentalManager().updateIndicator(plot);

                    player.closeInventory();
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-buy-success", "name", plot.getName()));
                    plugin.getConfigManager().playSound(player, "tax-paid");
                }
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-needed", "needed", plugin.getCurrencyManager().getNeededCoinsString(price)));
                plugin.getConfigManager().playSound(player, "anchor-error");
                player.closeInventory();
            }
        }
    }

    public Claim getClaim() {
        return this.plot;
    }
}