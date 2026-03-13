package me.lovelace.advancedclaims.listener;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.gui.TaxerGUI;
import me.lovelace.advancedclaims.model.Claim;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class LandlordListener implements Listener {
    private final AdvancedClaims plugin;

    public LandlordListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block b = e.getClickedBlock();
            if (b != null && b.getType().name().endsWith("SIGN")) {
                checkLandlordClick(e.getPlayer(), b.getLocation());
            }
        }
    }

    @EventHandler
    public void onEntityClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = e.getRightClicked();

        if (clicked.hasMetadata("NPC")) {
            String name = clicked.getCustomName() != null ? clicked.getCustomName() : clicked.getName();
            String strippedName = name != null ? ChatColor.stripColor(name) : "";

            String taxerNpcName = ChatColor.stripColor(plugin.getConfigManager().getString("taxer-npc-name", "Сборщик налогов"));
            if (strippedName.contains(taxerNpcName)) {
                e.setCancelled(true);
                plugin.getConfigManager().playSound(e.getPlayer(), "gui-click");
                e.getPlayer().openInventory(new TaxerGUI(plugin, e.getPlayer()).getInventory());
                return;
            }

            checkLandlordClick(e.getPlayer(), clicked.getLocation());
        }
    }

    private void checkLandlordClick(org.bukkit.entity.Player player, Location loc) {
        for (Claim c : plugin.getClaimManager().getAllClaims()) {
            if (c.isRentalPlot() && !c.isRented() && c.getHologramId() != null && c.getHologramId().startsWith("landlord:")) {
                String[] parts = c.getHologramId().replace("landlord:", "").split(";");
                if (parts.length >= 4) {
                    double bx = Double.parseDouble(parts[1]);
                    double by = Double.parseDouble(parts[2]);
                    double bz = Double.parseDouble(parts[3]);

                    if (loc.getWorld().getName().equals(parts[0]) && loc.distanceSquared(new Location(loc.getWorld(), bx, by, bz)) < 4.0) {

                        // Проверяем лимиты перед открытием (чтобы игрок не купил 2 участка)
                        long ownedCount = plugin.getClaimManager().getAllClaims().stream()
                                .filter(Claim::isRentalPlot)
                                .filter(plot -> plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId()))
                                .count();

                        if (ownedCount >= 1 && !player.hasPermission("advancedclaims.rental.bypasslimit")) {
                            player.sendMessage(plugin.getConfigManager().getMessage("rental-limit-reached"));
                            return;
                        }

                        // Открываем GUI напрямую (игнорируя проверку позиции)
                        player.openInventory(new me.lovelace.advancedclaims.gui.RentalPaymentGUI(plugin, c).getInventory());
                        return;
                    }
                }
            }
        }
    }
}