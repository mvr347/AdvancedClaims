package me.lovelace.advancedclaims.listener;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.gui.AbstractGUI;
import me.lovelace.advancedclaims.gui.RentalPaymentGUI;
import me.lovelace.advancedclaims.gui.RentalPlayerGUI;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.TrustLevel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiListener implements Listener {
    private final AdvancedClaims plugin;

    public GuiListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof AbstractGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null) return;
            if (event.getClickedInventory().equals(event.getView().getBottomInventory())) return;

            gui.handleClick(event);
        } else if (event.getInventory().getHolder() instanceof RentalPaymentGUI gui) {
            if (event.getClickedInventory() == null) return;

            if (event.getSlot() == 22 && event.getClickedInventory().equals(event.getInventory())) {
                event.setCancelled(true);
                handleRentalPayment(event, gui);
            } else if (event.getSlot() == 4 && event.getClickedInventory().equals(event.getInventory())) {
                event.setCancelled(true);
            }
        } else if (event.getInventory().getHolder() instanceof RentalPlayerGUI gui) {
            if (event.getClickedInventory() == null) return;
            if (event.getClickedInventory().equals(event.getView().getBottomInventory())) return;

            gui.handleClick(event);
        }
    }

    private void handleRentalPayment(InventoryClickEvent event, RentalPaymentGUI gui) {
        Player player = (Player) event.getWhoClicked();
        Claim claim = gui.getClaim();
        if (claim == null) return;

        long ownedPlots = plugin.getClaimManager().getAllClaims().stream()
                .filter(clm -> clm.isRentalPlot())
                .filter(clm -> clm.getOwnerUuid() != null && clm.getOwnerUuid().equals(player.getUniqueId()))
                .count();

        if (ownedPlots >= 1) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-limit-reached"));
            return;
        }

        long value = plugin.getCurrencyManager().calculateValue(event.getInventory());
        if (value >= claim.getRentalPrice()) {
            event.getInventory().clear();
            player.closeInventory();

            claim.setRentalEndTime(System.currentTimeMillis() + 86400000L);
            claim.setTrust(player.getUniqueId(), TrustLevel.OWNER);
            plugin.getStorage().saveClaimAsync(claim);
            plugin.getRentalManager().updateIndicator(claim);

            player.sendMessage(plugin.getConfigManager().getMessage("rental-payment-success"));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-insufficient-funds", "price", String.valueOf(claim.getRentalPrice())));
        }
    }
}