package me.lovelace.advancedclaims.gui;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.Quest;
import me.lovelace.advancedclaims.model.UserData;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class QuestsGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public QuestsGUI(AdvancedClaims plugin, Player viewer, Claim claim) {
        super(36, plugin.getConfigManager().getComponent("quests.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        UserData data = plugin.getQuestManager().getUserData(viewer.getUniqueId());
        int slot = 0;

        int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
        me.lovelace.advancedclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
        String currentTierId = currentTier != null ? currentTier.id() : "all";

        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            if (!quest.tier().equals("all") && !quest.tier().equals(currentTierId)) {
                continue; // Пропускаем квесты, которые не предназначены для текущего тира привата
            }

            boolean completed = data.isQuestCompleted(quest.id());
            int progress = Math.min(data.getQuestProgress(quest.id()), quest.targetAmount());

            List<Component> lore = new ArrayList<>();
            if (completed) {
                lore.add(Component.empty());
                lore.add(plugin.getConfigManager().getComponent("quests.completed"));
                if (quest.repeatable()) {
                    lore.add(Component.empty());
                    lore.add(Component.text("§7Повторяемый: §aДа"));
                }
            } else {
                lore.add(Component.text(quest.description().replace("&", "§")));
                lore.add(plugin.getConfigManager().getComponent("quests.target", "amount", String.valueOf(quest.targetAmount()), "target", quest.targetName()));
                lore.add(plugin.getConfigManager().getComponent("quests.progress", "progress", String.valueOf(progress), "amount", String.valueOf(quest.targetAmount())));
                lore.add(Component.empty());
                
                // Показываем тип квеста
                if (quest.daily()) {
                    lore.add(Component.text("§b§lЕжедневный квест"));
                } else if (quest.repeatable()) {
                    lore.add(Component.text("§7Повторяемый: §aДа"));
                }
                
                lore.add(Component.empty());
                lore.add(plugin.getConfigManager().getComponent("quests.reward"));

                boolean hasRewards = false;
                if (quest.rewardSlots() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-slots", "slots", String.valueOf(quest.rewardSlots()))); hasRewards = true; }
                if (quest.rewardBlocks() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-blocks", "blocks", String.valueOf(quest.rewardBlocks()))); hasRewards = true; }
                if (quest.rewardExpansionBlocks() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-points", "points", String.valueOf(quest.rewardExpansionBlocks()))); hasRewards = true; }
                if (quest.rewardMembers() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-members", "members", String.valueOf(quest.rewardMembers()))); hasRewards = true; }

                // ВЫВОД БАФФА
                String rBuff = quest.rewardBuff();
                if (rBuff != null && !rBuff.trim().isEmpty()) {
                    String flagKey = rBuff.replace("PERK_", "").toLowerCase(); // haste
                    String buffTranslate = plugin.getConfigManager().getString("settings.flags." + flagKey);

                    if (buffTranslate.contains("Error")) {
                        buffTranslate = rBuff;
                    }

                    lore.add(plugin.getConfigManager().getComponent("quests.reward-buff", "buff", buffTranslate));
                    hasRewards = true;
                }

                if (!hasRewards) lore.add(plugin.getConfigManager().getComponent("quests.no-reward"));
            }

            inventory.setItem(slot++, createHead(HEAD_QUEST, plugin.getConfigManager().getComponent("quests.quest-name", "name", quest.name()), lore));
        }

        inventory.setItem(31, createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null));
        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == 31) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
        }
    }

    @Override
    protected void fillEmptySlots() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            glass.setItemMeta(meta);
        }
        for (int i = 27; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                inventory.setItem(i, glass);
            }
        }
    }
}