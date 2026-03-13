package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class DeleteConfirmGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public DeleteConfirmGUI(AdvancedClaims plugin, Player viewer, Claim claim) {
        super(27, Component.text(plugin.getConfigManager().getGuiText("delete-confirm.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        // ИЗМЕНЕНО: Используем ключи confirm и cancel
        inventory.setItem(11, createHead(HEAD_DELETE_YES, Component.text(plugin.getConfigManager().getGuiText("delete-confirm.confirm")), null));
        inventory.setItem(15, createHead(HEAD_DELETE_NO, Component.text(plugin.getConfigManager().getGuiText("delete-confirm.cancel")), null));

        inventory.setItem(22, createHead(HEAD_BACK, Component.text(plugin.getConfigManager().getGuiText("common.back")), null));

        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == 11) {
            viewer.closeInventory();
            if (claim.getAnchorLocation().getBlock().getType() != Material.AIR) {
                claim.getAnchorLocation().getBlock().setType(Material.AIR);
            }
            plugin.getStorage().deleteClaimAsync(claim.getId());
            plugin.getClaimManager().removeClaimFromCache(claim.getId());

            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
            me.lovelace.advancedclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
            String tierId = currentTier != null ? currentTier.id() : "tier-1";

            ItemStack anchor = plugin.getAnchorManager().createAnchorItem(tierId);
            if (anchor != null) viewer.getInventory().addItem(anchor);

            viewer.sendMessage(plugin.getConfigManager().getComponent("delete-confirm.deleted"));
        } else if (event.getSlot() == 15 || event.getSlot() == 22) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new SettingsGUI(plugin, viewer, claim).getInventory());
        }
    }
}