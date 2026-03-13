package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class RentalAdminListGUI extends AbstractGUI {
    protected final AdvancedClaims plugin;

    public RentalAdminListGUI(AdvancedClaims plugin) {
        super(54, plugin.getConfigManager().getComponent("rental-list.title"));
        this.plugin = plugin;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        List<Claim> plots = plugin.getClaimManager().getAllClaims().stream()
                .filter(Claim::isRentalPlot)
                .toList();

        int slot = 0;
        NamespacedKey key = new NamespacedKey(plugin, "plot_id");

        for (Claim plot : plots) {
            if (slot >= 45) break;

            String ownerName = "Сервер";
            if (plot.getOwnerUuid() != null) {
                String fetchedName = org.bukkit.Bukkit.getOfflinePlayer(plot.getOwnerUuid()).getName();
                if (fetchedName != null) ownerName = fetchedName;
            }

            String statusKey = plot.isRented() ? "rental-list.plot-lore-status-rented" : "rental-list.plot-lore-status-free";

            ItemStack item = createHead(HEAD_INFO,
                    plugin.getConfigManager().getComponent("rental-list.plot-name", "name", plot.getName()),
                    List.of(
                            plugin.getConfigManager().getComponent("rental-list.plot-lore-owner", "owner", ownerName),
                            plugin.getConfigManager().getComponent("rental-list.plot-lore-price", "price", String.valueOf(plot.getRentalPrice())),
                            plugin.getConfigManager().getComponent(statusKey),
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
        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "plot_id");
        String idStr = event.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (idStr != null) {
            UUID plotId = UUID.fromString(idStr);
            plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                event.getWhoClicked().openInventory(new RentalEditGUI(plugin, plot).getInventory());
            });
        }
    }
}