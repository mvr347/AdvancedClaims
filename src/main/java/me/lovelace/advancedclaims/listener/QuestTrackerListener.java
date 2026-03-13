package me.lovelace.advancedclaims.listener;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Quest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;

/**
 * Отслеживание прогресса квестов игрока.
 */
public class QuestTrackerListener implements Listener {
    private final AdvancedClaims plugin;

    public QuestTrackerListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getQuestManager().addProgress(event.getPlayer().getUniqueId(), Quest.QuestType.MINE_BLOCKS, event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.getQuestManager().addProgress(event.getPlayer().getUniqueId(), Quest.QuestType.PLACE_BLOCKS, event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            plugin.getQuestManager().addProgress(event.getEntity().getKiller().getUniqueId(), Quest.QuestType.KILL_MOBS, event.getEntity().getType().name(), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();
        org.bukkit.inventory.ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();
        plugin.getQuestManager().addProgress(player.getUniqueId(), Quest.QuestType.CRAFT_ITEMS, result.getType().name(), amount);
    }
}