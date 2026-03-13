package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class TaxerGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;

    public TaxerGUI(AdvancedClaims plugin, Player viewer) {
        super(InventoryType.DISPENSER, plugin.getConfigManager().getComponent("taxer-npc-name"));
        this.plugin = plugin;
        this.viewer = viewer;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        List<Claim> ownedPlots = plugin.getClaimManager().getAllClaims().stream()
                .filter(Claim::isRentalPlot)
                .filter(Claim::isRented)
                .filter(c -> c.getOwnerUuid() != null && c.getOwnerUuid().equals(viewer.getUniqueId()))
                .toList();

        if (ownedPlots.isEmpty()) {
            inventory.setItem(4, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("rental-no-rented"), List.of(
                    Component.text("§7Арендуйте участок, чтобы"),
                    Component.text("§7оплачивать здесь налоги.")
            )));
        } else {
            int slot = 0;
            NamespacedKey key = new NamespacedKey(plugin, "plot_id");
            for (Claim plot : ownedPlots) {
                if (slot == 6 || slot == 7 || slot == 8) {
                    break;
                }

                long taxAmount = Math.round(plot.getRentalPrice() * (plugin.getRentalManager().getTaxPercentage() / 100.0));
                long timeLeft = (plot.getRentalEndTime() - System.currentTimeMillis()) / 1000;
                long days = Math.max(0, timeLeft / 86400);
                long hours = Math.max(0, (timeLeft % 86400) / 3600);

                ItemStack item = createHead(HEAD_INFO,
                        plugin.getConfigManager().getComponent("rental-list.plot-name", "name", plot.getName()),
                        List.of(
                                plugin.getConfigManager().getComponent("msg-rental-list-entry", "role", "", "name", "", "days", String.valueOf(days), "hours", String.valueOf(hours)),
                                plugin.getConfigManager().getComponent("rental-player.tax-amount", "amount", String.valueOf(taxAmount)),
                                Component.empty(),
                                plugin.getConfigManager().getComponent("rental-list.plot-lore-click")
                        ));

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, plot.getId().toString());
                    item.setItemMeta(meta);
                }

                inventory.setItem(slot++, item);
            }
        }

        inventory.setItem(7, createHead(HEAD_DELETE_NO, Component.text("§cЗакрыть"), List.of()));

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            glass.setItemMeta(meta);
        }
        inventory.setItem(6, glass);
        inventory.setItem(8, glass);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == 7) {
            viewer.closeInventory();
            return;
        }
        
        if (event.getCurrentItem().getItemMeta() == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "plot_id");
        String idStr = event.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (idStr != null) {
            UUID plotId = UUID.fromString(idStr);
            plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                long taxAmount = Math.round(plot.getRentalPrice() * (plugin.getRentalManager().getTaxPercentage() / 100.0));
                
                if (plugin.getCurrencyManager().hasEnough(viewer, taxAmount)) {
                    if (plugin.getCurrencyManager().takeCurrency(viewer, taxAmount)) {
                        long extendTime = plugin.getRentalManager().getTaxDays() * 86400000L;
                        plot.setRentalEndTime(plot.getRentalEndTime() + extendTime);
                        plugin.getStorage().saveClaimAsync(plot);
                        plugin.getRentalManager().updateIndicator(plot);
                        
                        viewer.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-success", "days", String.valueOf(plugin.getRentalManager().getTaxDays())));
                        plugin.getConfigManager().playSound(viewer, "tax-paid");
                        setMenuItems(); // Обновляем GUI
                    }
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-needed", "needed", plugin.getCurrencyManager().getNeededCoinsString(taxAmount)));
                    plugin.getConfigManager().playSound(viewer, "anchor-error");
                    viewer.closeInventory();
                }
            });
        }
    }
}
