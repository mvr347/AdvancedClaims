package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

public class RentalPlayerGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final Claim plot;

    public RentalPlayerGUI(AdvancedClaims plugin, Player viewer, Claim plot) {
        super(9, plugin.getConfigManager().getComponent("rental-player.title", "name", plot.getName()));
        this.plugin = plugin;
        this.viewer = viewer;
        this.plot = plot;
        this.inventory = Bukkit.createInventory(this, InventoryType.HOPPER, plugin.getConfigManager().getComponent("rental-player.title", "name", plot.getName()));
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        String ownerName = "Сервер";
        if (plot.getOwnerUuid() != null) {
            String fetchedName = Bukkit.getOfflinePlayer(plot.getOwnerUuid()).getName();
            if (fetchedName != null) ownerName = fetchedName;
        }

        long timeLeft = (plot.getRentalEndTime() - System.currentTimeMillis()) / 1000;
        long days = Math.max(0, timeLeft / 86400);
        long hours = Math.max(0, (timeLeft % 86400) / 3600);

        // Слот 0: Инфо
        inventory.setItem(0, createHead(HEAD_INFO,
                plugin.getConfigManager().getComponent("rental-edit.info-name"),
                plugin.getConfigManager().getHelpMessage("rental-player.info-lore", "owner", ownerName, "days", String.valueOf(days), "hours", String.valueOf(hours))));

        // Слот 2: Участники
        inventory.setItem(2, createHead(HEAD_MEMBERS,
                plugin.getConfigManager().getComponent("main.members-name"),
                plugin.getConfigManager().getHelpMessage("rental-player.members-lore", "count", String.valueOf(plot.getMembers().size()), "max", "10")));

        // Слот 4: Отказаться (Открывает подтверждение)
        if (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(viewer.getUniqueId())) {
            inventory.setItem(4, createHead(HEAD_BARRIER,
                    plugin.getConfigManager().getComponent("rental-player.refuse-btn"),
                    plugin.getConfigManager().getHelpMessage("rental-player.refuse-lore")));
        }

        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 0) {
            if (event.isLeftClick()) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
                double x = (plot.getBoundingBox().getMinX() + plot.getBoundingBox().getMaxX()) / 2.0;
                double z = (plot.getBoundingBox().getMinZ() + plot.getBoundingBox().getMaxZ()) / 2.0;
                // Получаем мир из якоря привата
                org.bukkit.World targetWorld = plot.getAnchorLocation().getWorld();
                if (targetWorld != null) {
                    double y = targetWorld.getHighestBlockYAt((int)x, (int)z) + 1;
                    viewer.teleport(new org.bukkit.Location(targetWorld, x, y, z));
                }
            } else if (event.isRightClick()) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
                me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, viewer, plot.getBoundingBox(), 200L);
            }
        }
        if (slot == 2) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            // ИСПРАВЛЕНИЕ: Открываем RentalMembersGUI вместо обычного MembersGUI
            viewer.openInventory(new RentalMembersGUI(plugin, viewer, plot).getInventory());
        }
        if (slot == 4 && plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(viewer.getUniqueId())) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new RentalAbandonConfirmGUI(plugin, plot).getInventory());
        }
    }
}