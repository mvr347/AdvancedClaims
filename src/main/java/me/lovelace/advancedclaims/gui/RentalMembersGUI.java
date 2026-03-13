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

import java.util.Map;
import java.util.UUID;

public class RentalMembersGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public RentalMembersGUI(AdvancedClaims plugin, Player viewer, Claim claim) {
        super(36, plugin.getConfigManager().getComponent("members.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        
        // Для аренды используем фиксированный лимит или дефолтный, без бонусов игрока (или можно оставить как есть)
        // Обычно в арендах лимит фиксирован, например 10
        int maxMembers = 10; 

        boolean isManagerOrOwner = viewer.getUniqueId().equals(claim.getOwnerUuid()) ||
                claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;

        // Кнопка НАЗАД ведет в RentalPlayerGUI
        inventory.setItem(27, createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null));

        if (claim.getMembers().isEmpty()) {
            java.util.List<Component> lore = plugin.getConfigManager().getHelpMessage("members.empty-lore", "limit", String.valueOf(maxMembers));

            if (isManagerOrOwner) {
                inventory.setItem(13, createHead(HEAD_ADD_MEMBER, plugin.getConfigManager().getComponent("members.add-name"), lore));
            } else {
                inventory.setItem(13, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("members.no-perm-barrier"), lore));
            }
        } else {
            if (claim.getMembers().size() < maxMembers && isManagerOrOwner) {
                java.util.List<Component> lore = plugin.getConfigManager().getHelpMessage("members.add-lore", "free", String.valueOf(maxMembers - claim.getMembers().size()));
                inventory.setItem(35, createHead(HEAD_ADD_MEMBER, plugin.getConfigManager().getComponent("members.add-name"), lore));
            } else if (!isManagerOrOwner) {
                java.util.List<Component> lore = plugin.getConfigManager().getHelpMessage("members.empty-lore", "limit", String.valueOf(maxMembers));
                inventory.setItem(35, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("members.no-perm-barrier"), lore));
            }

            int slot = 0;
            for (Map.Entry<UUID, TrustLevel> entry : claim.getMembers().entrySet()) {
                if (slot == 27) slot = 28;
                if (slot >= 35) break;

                OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(target);
                    String name = target.getName() != null ? target.getName() : "Неизвестный";
                    String roleName = plugin.getConfigManager().getConfig().getString("claim.roles." + entry.getValue().name(), entry.getValue().name());

                    if (roleName.equals(entry.getValue().name())) {
                        roleName = getColoredRoleName(entry.getValue());
                    }

                    meta.displayName(plugin.getConfigManager().getComponent("members.member-name", "player", name));
                    meta.lore(plugin.getConfigManager().getHelpMessage("members.member-lore", "role", roleName));
                    head.setItemMeta(meta);
                }
                inventory.setItem(slot++, head);
            }
        }
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

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        
        // НАЗАД -> RentalPlayerGUI
        if (slot == 27) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new RentalPlayerGUI(plugin, viewer, claim).getInventory());
            return;
        }

        boolean isManagerOrOwner = viewer.getUniqueId().equals(claim.getOwnerUuid()) ||
                claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;

        if ((claim.getMembers().isEmpty() && slot == 13) || (!claim.getMembers().isEmpty() && slot == 35)) {
            if (!isManagerOrOwner) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            viewer.closeInventory();
            plugin.getConfigManager().playSound(viewer, "gui-click");

            plugin.getChatListener().setPendingMember(viewer.getUniqueId(), claim.getId());
            viewer.sendMessage(plugin.getConfigManager().getComponent("chat-enter-member"));

            return;
        }

        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
            if (meta == null || meta.getOwningPlayer() == null) return;
            UUID targetId = meta.getOwningPlayer().getUniqueId();

            if (targetId.equals(viewer.getUniqueId()) && claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("manager-role-error"));
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            if (isManagerOrOwner) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                // Используем тот же MemberActionGUI, но он должен знать, куда возвращаться
                viewer.openInventory(new MemberActionGUI(plugin, viewer, claim, targetId).getInventory());
            }
        }
    }
}