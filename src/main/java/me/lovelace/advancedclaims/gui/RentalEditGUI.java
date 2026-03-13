package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.IndicatorType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class RentalEditGUI extends AbstractGUI {
    private final Claim plot;
    protected final AdvancedClaims plugin;

    public RentalEditGUI(AdvancedClaims plugin, Claim plot) {
        super(27, plugin.getConfigManager().getComponent("rental-edit.title", "name", plot.getName()));
        this.plugin = plugin;
        this.plot = plot;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        String ownerName = "Сервер";
        if (plot.getOwnerUuid() != null) {
            String fetchedName = org.bukkit.Bukkit.getOfflinePlayer(plot.getOwnerUuid()).getName();
            if (fetchedName != null) ownerName = fetchedName;
        }

        String statusKey = plot.isRented() ? "rental-edit.info-lore-status-rented" : "rental-edit.info-lore-status-free";

        // Слот 13: Информация
        ItemStack info = createHead(HEAD_INFO, plugin.getConfigManager().getComponent("rental-edit.info-name"), List.of(
                plugin.getConfigManager().getComponent("rental-edit.info-lore-owner", "owner", ownerName),
                plugin.getConfigManager().getComponent("rental-edit.info-lore-price", "price", String.valueOf(plot.getRentalPrice())),
                plugin.getConfigManager().getComponent(statusKey),
                Component.empty(),
                plugin.getConfigManager().getComponent("rental-list.plot-lore-click")
        ));
        inventory.setItem(13, info);

        // Слот 11: Изменить цену
        ItemStack price = createItem(Material.GOLD_INGOT, plugin.getConfigManager().getComponent("rental-edit.price-name"), plugin.getConfigManager().getHelpMessage("rental-edit.price-lore"));
        inventory.setItem(11, price);

        // Слот 15: КНОПКА LANDLORD (Переключатель NPC / SIGN)
        String typeStr = switch (plot.getIndicatorType()) {
            case NPC -> "NPC (Житель)";
            case SIGN -> "Табличка";
            default -> "Выключено";
        };
        ItemStack landlordBtn = createHead(HEAD_SETTINGS, Component.text("§bВид Арендодателя (Landlord)"), List.of(
                Component.text("§7Текущий вид: §e" + typeStr),
                Component.text(""),
                Component.text("§aКликните, чтобы переключить")
        ));
        inventory.setItem(15, landlordBtn);

        // Слот 26: Удалить плот
        ItemStack delete = createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("rental-edit.delete-name"), List.of(
                plugin.getConfigManager().getComponent("rental-edit.delete-lore"),
                Component.empty(),
                plugin.getConfigManager().getComponent("rental-edit.delete-lore-warning")
        ));
        inventory.setItem(26, delete);

        // Слот 22: Назад
        ItemStack back = createHead(HEAD_BACK, plugin.getConfigManager().getComponent("rental-edit.back-name"), null);
        inventory.setItem(22, back);

        fillEmptySlots();
    }

    private ItemStack createItem(Material m, Component name, List<Component> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.displayName(name);
            if (lore != null) mt.lore(lore);
            i.setItemMeta(mt);
        }
        return i;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 22) {
            event.getWhoClicked().openInventory(new RentalAdminListGUI(plugin).getInventory());
        } else if (slot == 26) {
            plugin.getStorage().deleteClaimAsync(plot.getId());
            plugin.getClaimManager().removeClaimFromCache(plot.getId());
            plugin.getRentalManager().unregisterPlotName(plot.getName());
            event.getWhoClicked().sendMessage(plugin.getConfigManager().getMessage("rental-deleted"));
            event.getWhoClicked().openInventory(new RentalAdminListGUI(plugin).getInventory());
        } else if (slot == 11) {
            plugin.getChatListener().setPendingPrice(event.getWhoClicked().getUniqueId(), plot);
            event.getWhoClicked().sendMessage(plugin.getConfigManager().getComponent("rental-edit.enter-price"));
            event.getWhoClicked().closeInventory();
        } else if (slot == 15) {
            IndicatorType current = plot.getIndicatorType();
            IndicatorType next = switch (current) {
                case NONE -> IndicatorType.SIGN;
                case SIGN -> IndicatorType.NPC;
                case NPC -> IndicatorType.NONE;
            };
            plot.setIndicatorType(next);
            plugin.getStorage().saveClaimAsync(plot);
            plugin.getRentalManager().updateIndicator(plot);
            plugin.getConfigManager().playSound((org.bukkit.entity.Player) event.getWhoClicked(), "gui-click");
            setMenuItems();
        }
    }
}