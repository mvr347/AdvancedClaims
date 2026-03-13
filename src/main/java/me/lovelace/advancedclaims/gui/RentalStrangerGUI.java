package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

public class RentalStrangerGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Claim plot;

    public RentalStrangerGUI(AdvancedClaims plugin, Claim plot) {
        super(9, plugin.getConfigManager().getComponent("rental-player.title", "name", plot.getName()));
        this.plugin = plugin;
        this.plot = plot;
        this.inventory = Bukkit.createInventory(this, InventoryType.HOPPER, plugin.getConfigManager().getComponent("rental-player.title", "name", plot.getName()));
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        String ownerName = "Сервер (Свободен)";
        if (plot.getOwnerUuid() != null && plot.isRented()) {
            String fetchedName = Bukkit.getOfflinePlayer(plot.getOwnerUuid()).getName();
            if (fetchedName != null) ownerName = fetchedName;
        }

        int sizeX = (int) Math.round(plot.getBoundingBox().getMaxX() - plot.getBoundingBox().getMinX());
        int sizeZ = (int) Math.round(plot.getBoundingBox().getMaxZ() - plot.getBoundingBox().getMinZ());

        // Инфа по центру (Слот 2)
        inventory.setItem(2, createHead(HEAD_INFO,
                plugin.getConfigManager().getComponent("rental-edit.info-name"),
                plugin.getConfigManager().getHelpMessage("main.info-lore", "owner", ownerName, "size", String.valueOf(sizeX), "points", "0")));
        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getSlot() == 2) {
            org.bukkit.entity.Player viewer = (org.bukkit.entity.Player) event.getWhoClicked();
            if (event.isLeftClick()) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
                double x = (plot.getBoundingBox().getMinX() + plot.getBoundingBox().getMaxX()) / 2.0;
                double z = (plot.getBoundingBox().getMinZ() + plot.getBoundingBox().getMaxZ()) / 2.0;
                double y = viewer.getWorld().getHighestBlockYAt((int)x, (int)z) + 1;
                viewer.teleport(new org.bukkit.Location(viewer.getWorld(), x, y, z));
            } else if (event.isRightClick()) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
                me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, viewer, plot.getBoundingBox(), 200L);
            }
        }
    }
}