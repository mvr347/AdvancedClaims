package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class MemberActionGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final UUID targetId;

    public MemberActionGUI(AdvancedClaims plugin, Player viewer, Claim claim, UUID targetId) {
        super(27, Component.text("Управление участником"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        this.targetId = targetId;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetId);
        TrustLevel currentRole = claim.getTrust(targetId);
        String roleName = getColoredRoleName(currentRole);

        // 1. Повысить (Слот 11)
        boolean canPromote = currentRole.ordinal() < TrustLevel.MANAGER.ordinal();
        String promoteLoreKey = canPromote ? "member-action.promote-lore-yes" : "member-action.promote-lore-no";
        inventory.setItem(11, createHead(canPromote ? HEAD_EXPAND : HEAD_BARRIER, 
            plugin.getConfigManager().getComponent("member-action.promote-name"), 
            java.util.List.of(plugin.getConfigManager().getComponent(promoteLoreKey))));

        // 2. Голова (Слот 13 - По центру. Выгоняет!)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta headMeta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(target);
            headMeta.displayName(Component.text("§e" + (target.getName() != null ? target.getName() : "Неизвестный")));
            headMeta.lore(java.util.List.of(
                    plugin.getConfigManager().getComponent("member-action.kick-lore-1", "role", roleName),
                    Component.empty(),
                    plugin.getConfigManager().getComponent("member-action.kick-lore-2")
            ));
            head.setItemMeta(headMeta);
        }
        inventory.setItem(13, head);

        // 3. Понизить (Слот 15)
        boolean canDemote = currentRole.ordinal() > TrustLevel.ACCESS.ordinal();
        String demoteLoreKey = canDemote ? "member-action.demote-lore-yes" : "member-action.demote-lore-no";
        inventory.setItem(15, createHead(canDemote ? HEAD_DEMOTE : HEAD_BARRIER, 
            plugin.getConfigManager().getComponent("member-action.demote-name"), 
            java.util.List.of(plugin.getConfigManager().getComponent(demoteLoreKey))));

        // 4. Назад (Слот 22)
        inventory.setItem(22, createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null));

        fillEmptySlots();
    }
    
    private String getColoredRoleName(TrustLevel level) {
        return plugin.getConfigManager().getString(switch (level) {
            case OWNER -> "members.role-owner";
            case MANAGER -> "members.role-manager";
            case BUILD -> "members.role-build";
            case CONTAINER -> "members.role-container";
            case ACCESS -> "members.role-access";
            case NONE -> "members.role-none";
        });
    }

    private void openCorrectMembersGUI() {
        if (claim.isRentalPlot()) {
            viewer.openInventory(new RentalMembersGUI(plugin, viewer, claim).getInventory());
        } else {
            viewer.openInventory(new MembersGUI(plugin, viewer, claim).getInventory());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        TrustLevel currentRole = claim.getTrust(targetId);

        if (slot == 22) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            openCorrectMembersGUI();
            return;
        }

        // Клик по голове по центру (Слот 13) - ВЫГОНЯЕТ ИГРОКА
        if (slot == 13) {
            claim.getMembers().remove(targetId);
            plugin.getStorage().removeMemberAsync(claim.getId(), targetId);
            claim.setModified(true);

            viewer.sendMessage(plugin.getConfigManager().getMessage("member-removed"));
            plugin.getConfigManager().playSound(viewer, "anchor-break");

            Player onlineTarget = Bukkit.getPlayer(targetId);
            if (onlineTarget != null) {
                onlineTarget.sendMessage(plugin.getConfigManager().getComponent("chat-you-kicked", "player", viewer.getName()));
            }
            openCorrectMembersGUI();
            return;
        }

        if (slot == 11) {
            if (currentRole.ordinal() >= TrustLevel.MANAGER.ordinal()) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }
            changeRole(TrustLevel.values()[currentRole.ordinal() + 1]);
        }

        if (slot == 15) {
            if (currentRole.ordinal() <= TrustLevel.ACCESS.ordinal()) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }
            changeRole(TrustLevel.values()[currentRole.ordinal() - 1]);
        }
    }

    private void changeRole(TrustLevel newRole) {
        claim.setTrust(targetId, newRole);
        plugin.getStorage().saveMemberAsync(claim.getId(), targetId, newRole);
        claim.setModified(true);
        plugin.getConfigManager().playSound(viewer, "gui-click");

        Player onlineTarget = Bukkit.getPlayer(targetId);
        String roleName = plugin.getConfigManager().getConfig().getString("claim.roles." + newRole.name(), newRole.name());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.getConfigManager().getComponent("chat-role-changed", "player", viewer.getName(), "role", roleName));
        }
        setMenuItems();
    }
}