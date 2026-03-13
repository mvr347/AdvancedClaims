package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.ClaimFlag;
import me.lovelace.advancedclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SettingsGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public SettingsGUI(AdvancedClaims plugin, Player viewer, Claim claim) {
        super(45, Component.text(plugin.getConfigManager().getGuiText("settings.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;
        boolean canEditSettings = isOwner || isManager;

        if (canEditSettings) {
            inventory.setItem(10, createToggle(HEAD_HIDE_ANCHOR, "hide", ClaimFlag.HIDE_ANCHOR));
            inventory.setItem(11, createToggle(HEAD_PVP, "pvp", ClaimFlag.PVP));
            inventory.setItem(19, createToggle(HEAD_DENY_ENTRY, "deny", ClaimFlag.DENY_ENTRY));
        } else {
            inventory.setItem(10, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(11, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(19, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
        }

        String msgState = plugin.getConfigManager().getGuiText("settings.states.disabled");
        boolean screen = claim.getFlag(ClaimFlag.MSG_SCREEN);
        boolean actionbar = claim.getFlag(ClaimFlag.MSG_ACTIONBAR);
        if (screen && actionbar) msgState = plugin.getConfigManager().getGuiText("settings.states.msg-all");
        else if (screen) msgState = plugin.getConfigManager().getGuiText("settings.states.msg-screen");
        else if (actionbar) msgState = plugin.getConfigManager().getGuiText("settings.states.msg-actionbar");

        java.util.List<Component> mLore = new ArrayList<>();
        mLore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.status") + msgState));
        mLore.add(Component.text(" "));
        mLore.add(Component.text(plugin.getConfigManager().getGuiText("settings.flags.msg-desc")));

        String msgName = plugin.getConfigManager().getGuiText("settings.flags.msg");
        inventory.setItem(20, createHead(HEAD_MSG, Component.text("§e" + msgName), mLore));

        if (canEditSettings) {
            inventory.setItem(15, createToggle(HEAD_HASTE, "haste", ClaimFlag.PERK_HASTE));
            inventory.setItem(16, createToggle(HEAD_REGEN, "regen", ClaimFlag.PERK_REGEN));
            inventory.setItem(24, createToggle(HEAD_SATURATION, "saturation", ClaimFlag.PERK_SATURATION));
            inventory.setItem(25, createToggle(HEAD_CROP_GROWTH, "crop_growth", ClaimFlag.PERK_CROP_GROWTH));
        } else {
            inventory.setItem(15, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(16, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(24, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(25, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
        }

        if (canEditSettings) {
            java.util.List<Component> dLore = new ArrayList<>();
            for (String s : plugin.getConfigManager().getGuiLore("main.desc-lore")) dLore.add(Component.text(s));
            inventory.setItem(40, createHead(HEAD_DESC, Component.text(plugin.getConfigManager().getGuiText("main.desc-name")), dLore));
        } else {
            inventory.setItem(40, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
        }

        inventory.setItem(39, createHead(HEAD_BACK, Component.text(plugin.getConfigManager().getGuiText("common.back")), null));

        if (viewer.getUniqueId().equals(claim.getOwnerUuid())) {
            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
            me.lovelace.advancedclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);

            boolean hasBypassExpand = viewer.hasPermission("advancedclaims.bypass.expand");
            int maxAllowedSize = currentTier != null ? currentTier.maxRadius() * 2 + 1 : currentSize;
            boolean canExpand = currentSize < maxAllowedSize || hasBypassExpand;
            String tierName = currentTier != null ? plugin.getConfigManager().getGuiText("settings.expand.tier-name", "name", currentTier.id()) : "Unknown";

            me.lovelace.advancedclaims.model.UserData userData = plugin.getQuestManager().getUserData(viewer.getUniqueId());
            int points = userData.getExpansionBlocks();
            boolean hasPoints = points > 0 || hasBypassExpand;

            java.util.List<Component> lore = new ArrayList<>();
            lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-current", "size", String.valueOf(currentSize))));
            lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-max", "tier", tierName, "max", String.valueOf(maxAllowedSize))));
            lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-points", "points", String.valueOf(points))));
            lore.add(Component.text(" "));

            if (!canExpand) {
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-max-reach")));
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-req-tier", "next_tier", "?")));
            } else if (!hasPoints) {
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-req-points")));
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-do-quests")));
            } else {
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-click")));
            }

            inventory.setItem(22, createHead((canExpand && hasPoints) ? HEAD_EXPAND : HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText((canExpand && hasPoints) ? "settings.expand.can" : "settings.expand.cannot")), lore));
            inventory.setItem(41, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("settings.delete")), null));
        }
        fillEmptySlots();
    }

    // ИСПРАВЛЕНИЕ: Убрали аргумент fallbackMat (он всегда был null)
    private ItemStack createToggle(String headBase64, String flagKey, ClaimFlag flag) {
        boolean isLockedByQuest = false;
        if (flag.name().startsWith("PERK_")) {
            me.lovelace.advancedclaims.model.UserData data = plugin.getQuestManager().getUserData(viewer.getUniqueId());
            isLockedByQuest = !data.hasBuffUnlocked(flag.name()) && !viewer.hasPermission("advancedclaims.bypass.flags");
        }

        boolean isLocked = isLockedByQuest;
        String name = plugin.getConfigManager().getGuiText("settings.flags." + flagKey);
        String lockAdd = isLockedByQuest ? plugin.getConfigManager().getGuiText("settings.states.locked") : " ";
        Component compName = Component.text((isLocked ? "§c" : "§e") + name + lockAdd);

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("§7" + plugin.getConfigManager().getGuiText("settings.flags." + flagKey + "-desc")));

        boolean state = claim.getFlag(flag);
        String stateStr = state ? plugin.getConfigManager().getGuiText("settings.states.enabled") : plugin.getConfigManager().getGuiText("settings.states.disabled");

        lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.status") + stateStr));
        lore.add(Component.text(" "));

        if (isLockedByQuest) lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.locked-desc")));
        else lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.click-toggle")));

        if (isLocked) return createHead(HEAD_BARRIER, compName, lore);
        if (headBase64 != null) return createHead(headBase64, compName, lore);

        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(compName);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;
        boolean canEditSettings = isOwner || isManager;

        if (slot == 39) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
            return;
        }
        if (slot == 41 && isOwner) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new DeleteConfirmGUI(plugin, viewer, claim).getInventory());
            return;
        }
        if (slot == 40 && canEditSettings) {
            viewer.closeInventory();
            plugin.getConfigManager().playSound(viewer, "gui-click");

            // ИСПРАВЛЕНИЕ: Используем новый метод ChatListener'а
            plugin.getChatListener().setPendingDesc(viewer.getUniqueId(), claim.getId());
            viewer.sendMessage(Component.text("§eВведите новое описание в чат (или 'отмена' для выхода):"));

            return;
        }

        if (slot == 22 && isOwner) {
            boolean hasBypassExpand = viewer.hasPermission("advancedclaims.bypass.expand");
            me.lovelace.advancedclaims.model.UserData userData = plugin.getQuestManager().getUserData(viewer.getUniqueId());
            if (!hasBypassExpand && userData.getExpansionBlocks() < 1) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
            me.lovelace.advancedclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
            int maxAllowedSize = currentTier != null ? currentTier.maxRadius() * 2 + 1 : currentSize;

            if (!hasBypassExpand && currentSize >= maxAllowedSize) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            org.bukkit.util.BoundingBox newBox = claim.getBoundingBox().clone().expand(0.5, 0.5, 0.5);
            if (plugin.getClaimManager().checkOverlap(claim.getWorld(), newBox, claim.getId())) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            if (!hasBypassExpand) {
                userData.removeExpansionBlocks(1);
                plugin.getStorage().saveUserDataAsync(userData);
            }
            claim.setBoundingBox(newBox);
            plugin.getStorage().saveClaimAsync(claim);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> plugin.getQuestManager().saveUsers());

            plugin.getConfigManager().playSound(viewer, "anchor-place");
            me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, viewer, claim.getBoundingBox(), 100);
            setMenuItems();
            return;
        }

        if (slot == 20 && canEditSettings) {
            boolean screen = claim.getFlag(ClaimFlag.MSG_SCREEN);
            boolean actionbar = claim.getFlag(ClaimFlag.MSG_ACTIONBAR);

            // ИСПРАВЛЕНИЕ: Убрали лишние redundant-условия (IDE больше не будет ругаться)
            if (screen && actionbar) {
                claim.setFlag(ClaimFlag.MSG_SCREEN, true);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, false);
            } else if (screen) {
                claim.setFlag(ClaimFlag.MSG_SCREEN, false);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, true);
            } else if (actionbar) {
                claim.setFlag(ClaimFlag.MSG_SCREEN, false);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, false);
            } else {
                claim.setFlag(ClaimFlag.MSG_SCREEN, true);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, true);
            }

            plugin.getStorage().saveFlagAsync(claim.getId(), ClaimFlag.MSG_SCREEN, claim.getFlag(ClaimFlag.MSG_SCREEN));
            plugin.getStorage().saveFlagAsync(claim.getId(), ClaimFlag.MSG_ACTIONBAR, claim.getFlag(ClaimFlag.MSG_ACTIONBAR));
            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();
            return;
        }

        ClaimFlag targetFlag = switch (slot) {
            case 10 -> ClaimFlag.HIDE_ANCHOR;
            case 11 -> ClaimFlag.PVP;
            case 19 -> ClaimFlag.DENY_ENTRY;
            case 15 -> ClaimFlag.PERK_HASTE;
            case 16 -> ClaimFlag.PERK_REGEN;
            case 24 -> ClaimFlag.PERK_SATURATION;
            case 25 -> ClaimFlag.PERK_CROP_GROWTH;
            default -> null;
        };

        if (targetFlag != null && canEditSettings) {
            if (targetFlag.name().startsWith("PERK_")) {
                me.lovelace.advancedclaims.model.UserData data = plugin.getQuestManager().getUserData(viewer.getUniqueId());
                if (!data.hasBuffUnlocked(targetFlag.name()) && !viewer.hasPermission("advancedclaims.bypass.flags")) {
                    plugin.getConfigManager().playSound(viewer, "gui-error");
                    return;
                }
            }

            boolean newState = !claim.getFlag(targetFlag);
            claim.setFlag(targetFlag, newState);
            plugin.getStorage().saveFlagAsync(claim.getId(), targetFlag, newState);
            if (targetFlag == ClaimFlag.HIDE_ANCHOR) {
                if (newState) {
                    claim.getAnchorLocation().getBlock().setType(Material.AIR);
                } else {
                    int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
                    me.lovelace.advancedclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
                    claim.getAnchorLocation().getBlock().setType(currentTier != null ? currentTier.material() : Material.CAMPFIRE);
                }
            }
            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();
        }
    }
}