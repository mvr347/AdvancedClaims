package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class ClaimListGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final List<Claim> accessibleClaims = new ArrayList<>();

    public ClaimListGUI(AdvancedClaims plugin, Player viewer) {
        super(54, Component.text(plugin.getConfigManager().getGuiText("list.title")));
        this.plugin = plugin;
        this.viewer = viewer;

        for (Claim c : plugin.getClaimManager().getAllClaims()) {
            if (c.getOwnerUuid().equals(viewer.getUniqueId()) || c.getTrust(viewer.getUniqueId()) != me.lovelace.advancedclaims.model.TrustLevel.NONE) {
                accessibleClaims.add(c);
            }
        }
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        int slot = 0;
        for (Claim claim : accessibleClaims) {
            if (slot >= 45) break; // Лимит 45 приватов на страницу

            String ownerName = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
            if (ownerName == null) ownerName = "Неизвестно";

            me.lovelace.advancedclaims.model.TrustLevel role = claim.getTrust(viewer.getUniqueId());
            if (claim.getOwnerUuid().equals(viewer.getUniqueId())) role = me.lovelace.advancedclaims.model.TrustLevel.OWNER;
            String roleName = plugin.getConfigManager().getConfig().getString("claim.roles." + role.name(), role.name());

            int x = claim.getAnchorLocation().getBlockX();
            int y = claim.getAnchorLocation().getBlockY();
            int z = claim.getAnchorLocation().getBlockZ();

            List<Component> lore = new ArrayList<>();
            for (String s : plugin.getConfigManager().getGuiLore("list.claim-lore", "owner", ownerName, "role", roleName)) {
                lore.add(Component.text(s));
            }

            inventory.setItem(slot++, createHead(HEAD_INFO, Component.text(plugin.getConfigManager().getGuiText("list.claim-name", "x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z))), lore));
        }

        inventory.setItem(49, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("common.close")), null));
        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 49) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
            return;
        }

        if (slot < accessibleClaims.size()) {
            Claim target = accessibleClaims.get(slot);
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, target).getInventory());
        }
    }
}