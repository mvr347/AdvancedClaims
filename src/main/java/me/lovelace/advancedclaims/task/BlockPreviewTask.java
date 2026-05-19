package me.lovelace.advancedclaims.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.ClaimTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Задача для предпросмотра зоны привата перед созданием.
 * Использует Paper API sendMultiBlockChange для максимальной производительности.
 * Или ItemDisplay entities если use-glass-blocks: false.
 */
public class BlockPreviewTask {
    private final AdvancedClaims plugin;
    private final Player player;
    private final BoundingBox box;
    private final ClaimTier tier;
    private final Runnable onCancel;

    private final Map<Location, BlockData> originalBlocks = new HashMap<>();
    private final Map<Location, BlockData> fakeBlocks = new HashMap<>();
    private final List<ItemDisplay> displayedEntities = new ArrayList<>();
    private ScheduledTask task;

    // Текстура для голов (зеленое стекло)
    private static final String GREEN_GLASS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ5NjAxMTMwNzNiNTIwNDRkNzA3OTk2NTQxOTYyMTkyMjMxOGE5ZTk5ZDc2NzE3MGIxNDI4ZGVkNDhjN2NlNSJ9fX0=";

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
        int maxX = (int) box.getMaxX() - 1;
        int maxY = (int) box.getMaxY() - 1;
        int maxZ = (int) box.getMaxZ() - 1;

        boolean useGlassBlocks = plugin.getConfigManager().getConfig()
                .getBoolean("border.use-glass-blocks", false);
        boolean glowing = plugin.getConfigManager().getConfig()
                .getBoolean("border.glowing", true);

        if (useGlassBlocks) {
            // Используем обычные блоки стекла
            BlockData glassData = Material.GREEN_STAINED_GLASS.createBlockData();
            BlockData glowData = Material.VERDANT_FROGLIGHT.createBlockData();

            // 8 вершин параллелепипеда (светящиеся блоки)
            addBlock(minX, minY, minZ, glowData);
            addBlock(maxX, minY, minZ, glowData);
            addBlock(minX, maxY, minZ, glowData);
            addBlock(maxX, maxY, minZ, glowData);
            addBlock(minX, minY, maxZ, glowData);
            addBlock(maxX, minY, maxZ, glowData);
            addBlock(minX, maxY, maxZ, glowData);
            addBlock(maxX, maxY, maxZ, glowData);

            // 4 горизонтальных ребра по Y (нижние и верхние)
            for (int x = minX; x <= maxX; x++) {
                addBlock(x, minY, minZ, glassData);
                addBlock(x, minY, maxZ, glassData);
                addBlock(x, maxY, minZ, glassData);
                addBlock(x, maxY, maxZ, glassData);
            }
            // 4 вертикальных ребра по углам
            for (int y = minY; y <= maxY; y++) {
                addBlock(minX, y, minZ, glassData);
                addBlock(maxX, y, minZ, glassData);
                addBlock(minX, y, maxZ, glassData);
                addBlock(maxX, y, maxZ, glassData);
            }
            // 4 горизонтальных ребра по Z (передние и задние)
            for (int z = minZ; z <= maxZ; z++) {
                addBlock(minX, minY, z, glassData);
                addBlock(maxX, minY, z, glassData);
                addBlock(minX, maxY, z, glassData);
                addBlock(maxX, maxY, z, glassData);
            }
        } else {
            // Используем ItemDisplay entities (как при /ac show)
            List<Location> borderLocations = new ArrayList<>();

            // Нижняя грань
            for (int x = minX; x <= maxX; x++) {
                borderLocations.add(new Location(player.getWorld(), x + 0.5, minY, minZ + 0.5));
                borderLocations.add(new Location(player.getWorld(), x + 0.5, minY, maxZ + 0.5));
            }
            for (int z = minZ; z <= maxZ; z++) {
                borderLocations.add(new Location(player.getWorld(), minX + 0.5, minY, z + 0.5));
                borderLocations.add(new Location(player.getWorld(), maxX + 0.5, minY, z + 0.5));
            }

            // Верхняя грань
            for (int x = minX; x <= maxX; x++) {
                borderLocations.add(new Location(player.getWorld(), x + 0.5, maxY, minZ + 0.5));
                borderLocations.add(new Location(player.getWorld(), x + 0.5, maxY, maxZ + 0.5));
            }
            for (int z = minZ; z <= maxZ; z++) {
                borderLocations.add(new Location(player.getWorld(), minX + 0.5, maxY, z + 0.5));
                borderLocations.add(new Location(player.getWorld(), maxX + 0.5, maxY, z + 0.5));
            }

            // Вертикальные углы
            for (int y = minY; y <= maxY; y++) {
                borderLocations.add(new Location(player.getWorld(), minX + 0.5, y, minZ + 0.5));
                borderLocations.add(new Location(player.getWorld(), maxX + 0.5, y, minZ + 0.5));
                borderLocations.add(new Location(player.getWorld(), minX + 0.5, y, maxZ + 0.5));
                borderLocations.add(new Location(player.getWorld(), maxX + 0.5, y, maxZ + 0.5));
            }

            // Создаем ItemDisplay entities
            for (Location loc : borderLocations) {
                ItemDisplay display = player.getWorld().spawn(loc, ItemDisplay.class);
                if (display != null) {
                    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                    setHeadTexture(display, GREEN_GLASS_TEXTURE);
                    display.setBillboard(Display.Billboard.FIXED);

                    Transformation transform = display.getTransformation();
                    transform.getLeftRotation().set(0, 0, 0, 1);
                    transform.getRightRotation().set(0, 0, 0, 1);
                    display.setTransformation(transform);

                    if (glowing) {
                        display.setGlowing(true);
                        display.setBrightness(new Display.Brightness(15, 15));
                    }

                    Transformation finalTransform = display.getTransformation();
                    finalTransform.getScale().set(0.8f, 0.8f, 0.8f);
                    display.setTransformation(finalTransform);

                    display.setInterpolationDuration(1);
                    display.setTeleportDuration(1);

                    displayedEntities.add(display);
                }
            }
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
        boolean useGlassBlocks = plugin.getConfigManager().getConfig()
                .getBoolean("border.use-glass-blocks", false);
        
        if (useGlassBlocks) {
            // Отправляем фейковые блоки одним пакетом (самый быстрый способ в Paper)
            player.sendMultiBlockChange(fakeBlocks);
        }
        // Для ItemDisplay entities не нужно ничего делать - они уже созданы в prepareBlocks()
    }

    public void revert() {
        boolean useGlassBlocks = plugin.getConfigManager().getConfig()
                .getBoolean("border.use-glass-blocks", false);
        
        if (useGlassBlocks) {
            if (player.isOnline()) {
                // Восстанавливаем оригинальные блоки
                player.sendMultiBlockChange(originalBlocks);
            }
        } else {
            // Удаляем ItemDisplay entities
            for (ItemDisplay display : displayedEntities) {
                if (display != null && display.isValid() && !display.isDead()) {
                    display.remove();
                }
            }
            displayedEntities.clear();
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

    /**
     * Установить текстуру головы для ItemDisplay.
     * @param display ItemDisplay
     * @param textureBase64 Base64 текстуры
     */
    private void setHeadTexture(ItemDisplay display, String textureBase64) {
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            if (meta != null) {
                java.util.UUID uuid = java.util.UUID.randomUUID();
                com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(uuid, "Border");
                com.destroystokyo.paper.profile.ProfileProperty textureProperty =
                        new com.destroystokyo.paper.profile.ProfileProperty("textures", textureBase64);
                profile.setProperty(textureProperty);
                meta.setOwnerProfile(profile);

                head.setItemMeta(meta);

                display.setItemStack(head);
            }
        } catch (NoClassDefFoundError | Exception e) {
            display.setItemStack(new ItemStack(Material.LIME_STAINED_GLASS));
        }
    }
}