package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.List;

public class RentalAbandonConfirmGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Claim plot;

    public RentalAbandonConfirmGUI(AdvancedClaims plugin, Claim plot) {
        super(9, plugin.getConfigManager().getComponent("delete-confirm.cancel"));
        this.plugin = plugin;
        this.plot = plot;
        this.inventory = Bukkit.createInventory(this, InventoryType.HOPPER, plugin.getConfigManager().getComponent("delete-confirm.title"));
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        // Слот 0: Отмена
        inventory.setItem(0, createHead(HEAD_BACK,
                plugin.getConfigManager().getComponent("delete-confirm.cancel"),
                List.of(plugin.getConfigManager().getComponent("common.cancel-lore"))));

        // Слот 2: Предупреждение
        inventory.setItem(2, createHead(HEAD_INFO,
                Component.text("§cВнимание!"),
                List.of(
                        Component.text("§7Вы собираетесь отказаться"),
                        Component.text("§7от участка §e" + plot.getName()),
                        Component.text("§7Деньги за аренду §cне вернутся§7!"),
                        Component.text("§7Все участники будут удалены.")
                )));

        // Слот 4: Подтвердить отказ
        inventory.setItem(4, createHead(HEAD_DELETE_YES,
                plugin.getConfigManager().getComponent("delete-confirm.confirm"),
                List.of(Component.text("§cКликните для подтверждения!"))));

        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == 0) {
            plugin.getConfigManager().playSound(player, "gui-click");
            player.openInventory(new RentalPlayerGUI(plugin, player, plot).getInventory());
        } else if (event.getSlot() == 4) {
            player.closeInventory();
            plot.setRentalEndTime(0);
            plot.setOwnerUuid(plot.getParentClaimId());
            plot.getMembers().clear();
            plugin.getStorage().saveClaimAsync(plot);
            plugin.getRentalManager().updateIndicator(plot);
            player.sendMessage(plugin.getConfigManager().getMessage("rental-refuse-success"));
            plugin.getConfigManager().playSound(player, "gui-click");
        }
    }
}