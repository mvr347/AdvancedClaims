package me.lovelace.advancedclaims.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.ClaimTier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Задача для предпросмотра зоны привата перед созданием.
 * Использует Paper API sendMultiBlockChange для максимальной производительности.
 */
public class BlockPreviewTask {
    private final AdvancedClaims plugin;
    private final Player player;
    private final BoundingBox box;
    private final ClaimTier tier;
    private final Runnable onCancel;

    private final Map<Location, BlockData> originalBlocks = new HashMap<>();
    private final Map<Location, BlockData> fakeBlocks = new HashMap<>();
    private ScheduledTask task;

    public BlockPreviewTask(AdvancedClaims plugin, Player player, BoundingBox box, ClaimTier tier, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.box = box;
        this.tier = tier;
        this.onCancel = onCancel;

        prepareBlocks();
        showPreview();
        startChecking();
    }

    private void prepareBlocks() {
        int minX = (int) box.getMinX();
        int minY = (int) box.getMinY();
        int minZ = (int) box.getMinZ();
        int maxX = (int) box.getMaxX();
        int maxY = (int) box.getMaxY();
        int maxZ = (int) box.getMaxZ();

        BlockData glassData = Material.LIME_STAINED_GLASS.createBlockData();
        BlockData glowData = Material.VERDANT_FROGLIGHT.createBlockData();

        // Вершины параллелепипеда (светящиеся блоки)
        addBlock(minX, minY, minZ, glowData);
        addBlock(maxX, minY, minZ, glowData);
        addBlock(minX, maxY, minZ, glowData);
        addBlock(maxX, maxY, minZ, glowData);
        addBlock(minX, minY, maxZ, glowData);
        addBlock(maxX, minY, maxZ, glowData);
        addBlock(minX, maxY, maxZ, glowData);
        addBlock(maxX, maxY, maxZ, glowData);

        // Вершины параллелепипеда
        for (int x = minX; x <= maxX; x++) {
            addBlock(x, minY, minZ, glassData);
            addBlock(x, maxY, minZ, glassData);
            addBlock(x, minY, maxZ, glassData);
            addBlock(x, maxY, maxZ, glassData);
        }
        for (int y = minY; y <= maxY; y++) {
            addBlock(minX, y, minZ, glassData);
            addBlock(maxX, y, minZ, glassData);
            addBlock(minX, y, maxZ, glassData);
            addBlock(maxX, y, maxZ, glassData);
        }
        for (int z = minZ; z <= maxZ; z++) {
            addBlock(minX, minY, z, glassData);
            addBlock(maxX, minY, z, glassData);
            addBlock(minX, maxY, z, glassData);
            addBlock(maxX, maxY, z, glassData);
        }
    }

    private void addBlock(int x, int y, int z, BlockData fakeData) {
        Location loc = new Location(player.getWorld(), x, y, z);
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc, loc.getBlock().getBlockData());
            fakeBlocks.put(loc, fakeData);
        }
    }

    private void showPreview() {
        // Отправляем фейковые блоки одним пакетом (самый быстрый способ в Paper)
        player.sendMultiBlockChange(fakeBlocks);
    }

    public void revert() {
        if (player.isOnline()) {
            // Восстанавливаем оригинальные блоки
            player.sendMultiBlockChange(originalBlocks);
        }
        if (task != null) task.cancel();
        if (onCancel != null) onCancel.run();
    }

    private void startChecking() {
        // Regionized Scheduler: проверяем руку каждые 10 тиков (0.5 сек)
        task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                revert();
                return;
            }

            ItemStack inHand = player.getInventory().getItemInMainHand();
            Optional<ClaimTier> tierOpt = plugin.getAnchorManager().getTierFromItem(inHand);

            if (tierOpt.isEmpty() || !tierOpt.get().id().equals(tier.id())) {
                revert();
                if (onCancel != null) onCancel.run();
                player.sendMessage(plugin.getConfigManager().getComponent("msg-anchor-cancel"));
            }
        }, null, 1L, 10L);
    }

    public BoundingBox getBox() {
        return this.box;
    }
}