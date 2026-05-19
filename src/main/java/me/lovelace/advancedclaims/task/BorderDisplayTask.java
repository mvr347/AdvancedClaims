package me.lovelace.advancedclaims.task;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Задача для отображения границ привата.
 * Поддерживает как BlockDisplay entities (по умолчанию), так и sendMultiBlockChange (как запасной вариант).
 */
public class BorderDisplayTask {
    private static final Map<UUID, BorderSession> activeBorders = new ConcurrentHashMap<>();

    record BorderSession(Map<Location, BlockData> originalBlocks,
                         List<BlockDisplay> entities,
                         ScheduledTask endTask,
                         ScheduledTask refreshTask) {}

    public static void hideBorder(Player player) {
        BorderSession session = activeBorders.remove(player.getUniqueId());
        if (session != null) {
            if (session.endTask() != null && !session.endTask().isCancelled()) {
                session.endTask().cancel();
            }
            if (session.refreshTask() != null && !session.refreshTask().isCancelled()) {
                session.refreshTask().cancel();
            }
            if (session.entities() != null) {
                for (BlockDisplay entity : session.entities()) {
                    if (entity.isValid()) {
                        entity.remove();
                    }
                }
            }
            if (session.originalBlocks() != null && !session.originalBlocks().isEmpty() && player.isOnline()) {
                player.sendMultiBlockChange(session.originalBlocks());
            }
        }
    }

    public static void showBorder(AdvancedClaims plugin, Player player, BoundingBox box, long durationTicks) {
        showBorder(plugin, player, box, durationTicks, null);
    }

    public static void showBorder(AdvancedClaims plugin, Player player, BoundingBox box, long durationTicks, UUID claimId) {
        hideBorder(player);

        boolean useEntity = plugin.getConfigManager().isUseEntityBlocks();
        Material borderMaterial = plugin.getConfigManager().getBorderMaterial();

        if (claimId != null) {
            Optional<Claim> claimOpt = plugin.getClaimManager().getClaimById(claimId);
            if (claimOpt.isPresent() && claimOpt.get().isClanTerritory()) {
                borderMaterial = Material.RED_STAINED_GLASS; // Для клановых территорий можно использовать другой цвет
            }
        }

        BlockData borderData = borderMaterial.createBlockData();

        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX()) - 1;
        int maxY = (int) Math.floor(box.getMaxY()) - 1;
        int maxZ = (int) Math.floor(box.getMaxZ()) - 1;

        Map<Location, BlockData> originalBlocks = new HashMap<>();
        Map<Location, BlockData> fakeBlocks = new HashMap<>();
        List<BlockDisplay> entities = new ArrayList<>();

        int count = 0;
        int maxBlocks = plugin.getConfigManager().getConfig().getInt("border.max-blocks", 5000);

        // Нижние и верхние грани (по оси X)
        for (int x = minX; x <= maxX; x++) {
            count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, x, minY, minZ, borderData, useEntity);
            count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, x, minY, maxZ, borderData, useEntity);
            count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, x, maxY, minZ, borderData, useEntity);
            count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, x, maxY, maxZ, borderData, useEntity);
            if (count > maxBlocks) break;
        }

        // Вертикальные грани (по оси Y) - чтобы не дублировать углы, начинаем с minY+1 и заканчиваем на maxY-1
        if (count <= maxBlocks) {
            for (int y = minY + 1; y < maxY; y++) {
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, minX, y, minZ, borderData, useEntity);
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, maxX, y, minZ, borderData, useEntity);
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, minX, y, maxZ, borderData, useEntity);
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, maxX, y, maxZ, borderData, useEntity);
                if (count > maxBlocks) break;
            }
        }

        // Нижние и верхние грани (по оси Z) - чтобы не дублировать углы, начинаем с minZ+1 и заканчиваем на maxZ-1
        if (count <= maxBlocks) {
            for (int z = minZ + 1; z < maxZ; z++) {
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, minX, minY, z, borderData, useEntity);
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, maxX, minY, z, borderData, useEntity);
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, minX, maxY, z, borderData, useEntity);
                count += tryAddBlock(plugin, player, originalBlocks, fakeBlocks, entities, maxX, maxY, z, borderData, useEntity);
                if (count > maxBlocks) break;
            }
        }

        ScheduledTask refreshTask = null;
        if (!useEntity) {
            player.sendMultiBlockChange(fakeBlocks);
            refreshTask = player.getScheduler().runAtFixedRate(plugin, task -> {
                if (player.isOnline() && activeBorders.containsKey(player.getUniqueId())) {
                    player.sendMultiBlockChange(fakeBlocks);
                } else {
                    task.cancel();
                }
            }, null, 20L, 20L);
        }

        ScheduledTask endTask = player.getScheduler().runDelayed(plugin, task -> {
            hideBorder(player);
        }, null, durationTicks);

        activeBorders.put(player.getUniqueId(), new BorderSession(originalBlocks, entities, endTask, refreshTask));
    }

    private static int tryAddBlock(AdvancedClaims plugin, Player player, Map<Location, BlockData> orig,
                                 Map<Location, BlockData> fake, List<BlockDisplay> entities, 
                                 int x, int y, int z, BlockData borderData, boolean useEntity) {
        Location loc = new Location(player.getWorld(), x, y, z);
        if (!useEntity) {
            if (!orig.containsKey(loc)) {
                orig.put(loc, loc.getBlock().getBlockData());
                fake.put(loc, borderData);
                return 1;
            }
        } else {
            // Спавним BlockDisplay entity и делаем её видимой только для конкретного игрока
            BlockDisplay display = player.getWorld().spawn(loc, BlockDisplay.class, entity -> {
                entity.setBlock(borderData);
                // Делаем невидимым для всех по умолчанию (Paper API)
                entity.setVisibleByDefault(false);
                // Показываем только нужному игроку
                player.showEntity(plugin, entity);
                
                if (plugin.getConfigManager().isBorderGlowing()) {
                    entity.setGlowing(true);
                }
            });
            entities.add(display);
            return 1;
        }
        return 0;
    }
}