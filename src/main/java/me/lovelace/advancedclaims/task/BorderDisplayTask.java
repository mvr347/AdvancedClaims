package me.lovelace.advancedclaims.task;

import me.lovelace.advancedclaims.AdvancedClaims;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay; // Изменено с BlockDisplay
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Задача для отображения границ привата через ItemDisplay entities с эффектом Glowing.
 * Блоки статичны (не поворачиваются к игроку).
 */
public class BorderDisplayTask {
    // Изменено на ItemDisplay
    private static final ConcurrentHashMap<UUID, List<ItemDisplay>> activeBorders = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<org.bukkit.block.Block>> placedGlassBlocks = new ConcurrentHashMap<>();

    // Текстуры для голов (Base64)
    private static final String GREEN_GLASS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjMxMmUxYjkzZTM1NDRkMGVkMDFlMDQ3MTZlNWUyZjNlYThlZDc5OWFlMDI1M2U0YjE4MjRkZThiMzAwMmY2NCJ9fX0=";
    private static final String PREVIEW_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTBmNGVmN2Q2YzVkOGRhMzYxODI3NTIyM2JkY2ExNDdjOWNmYWFjNzdkMzdlOGFlODFkNzU3YTU5MzY3NmFjMiJ9fX0=";

    public static void hideBorder(Player player) {
        // Удаляем ItemDisplay entities
        List<ItemDisplay> displays = activeBorders.remove(player.getUniqueId());
        if (displays != null) {
            for (ItemDisplay display : displays) {
                if (display != null && display.isValid() && !display.isDead()) {
                    display.remove();
                }
            }
        }

        // Возвращаем обычные блоки обратно
        List<org.bukkit.block.Block> glassBlocks = placedGlassBlocks.remove(player.getUniqueId());
        if (glassBlocks != null) {
            for (org.bukkit.block.Block block : glassBlocks) {
                if (block != null && block.getType() == Material.GREEN_STAINED_GLASS) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Показать границы привата.
     */
    public static void showBorder(AdvancedClaims plugin, Player player, BoundingBox box, long durationTicks) {
        hideBorder(player);

        List<ItemDisplay> displays = new ArrayList<>();

        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX()) - 1;
        int maxY = (int) Math.floor(box.getMaxY()) - 1;
        int maxZ = (int) Math.floor(box.getMaxZ()) - 1;

        boolean useGlassBlocks = plugin.getConfigManager().getConfig()
                .getBoolean("border.use-glass-blocks", false);

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

        if (useGlassBlocks) {
            Material glassMaterial = Material.GREEN_STAINED_GLASS;
            List<org.bukkit.block.Block> glassBlocks = new ArrayList<>();

            for (Location loc : borderLocations) {
                glassBlocks.add(loc.getBlock());
                loc.getBlock().setBlockData(glassMaterial.createBlockData());
            }

            placedGlassBlocks.put(player.getUniqueId(), glassBlocks);
        } else {
            // Используем ItemDisplay entities
            for (Location loc : borderLocations) {
                ItemDisplay display = player.getWorld().spawn(loc, ItemDisplay.class);
                if (display != null) {
                    // Заставляем предмет выглядеть как установленный блок
                    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);

                    // Устанавливаем текстуру головы (она внутри вызывает setItemStack)
                    setHeadTexture(display, GREEN_GLASS_TEXTURE);

                    display.setBillboard(Display.Billboard.FIXED);

                    Transformation transform = display.getTransformation();
                    transform.getLeftRotation().set(0, 0, 0, 1);
                    transform.getRightRotation().set(0, 0, 0, 1);
                    display.setTransformation(transform);

                    display.setGlowing(true);
                    display.setBrightness(new Display.Brightness(15, 15));

                    Transformation finalTransform = display.getTransformation();
                    finalTransform.getScale().set(0.8f, 0.8f, 0.8f);
                    display.setTransformation(finalTransform);

                    display.setInterpolationDuration(1);
                    display.setTeleportDuration(1);

                    displays.add(display);
                }
            }
        }

        activeBorders.put(player.getUniqueId(), displays);
        player.getScheduler().runDelayed(plugin, task -> hideBorder(player), null, durationTicks);
    }

    /**
     * Показать границы при создании привата (превью).
     */
    public static void showPreviewBorder(AdvancedClaims plugin, Player player, BoundingBox box, long durationTicks) {
        hideBorder(player);

        List<ItemDisplay> displays = new ArrayList<>();

        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX()) - 1;
        int maxY = (int) Math.floor(box.getMaxY()) - 1;
        int maxZ = (int) Math.floor(box.getMaxZ()) - 1;

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
                setHeadTexture(display, PREVIEW_TEXTURE);

                display.setBillboard(Display.Billboard.FIXED);

                Transformation transform = display.getTransformation();
                transform.getLeftRotation().set(0, 0, 0, 1);
                transform.getRightRotation().set(0, 0, 0, 1);
                display.setTransformation(transform);

                display.setGlowing(true);
                display.setBrightness(new Display.Brightness(15, 15));

                Transformation finalTransform = display.getTransformation();
                finalTransform.getScale().set(0.8f, 0.8f, 0.8f);
                display.setTransformation(finalTransform);

                display.setInterpolationDuration(1);
                display.setTeleportDuration(1);

                displays.add(display);
            }
        }

        activeBorders.put(player.getUniqueId(), displays);
        player.getScheduler().runDelayed(plugin, task -> hideBorder(player), null, durationTicks);
    }

    /**
     * Установить текстуру головы для ItemDisplay.
     * @param display ItemDisplay
     * @param textureBase64 Base64 текстуры
     */
    private static void setHeadTexture(ItemDisplay display, String textureBase64) {
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