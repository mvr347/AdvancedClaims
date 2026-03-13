package me.lovelace.advancedclaims.manager;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.ClaimTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Менеджер якорей приватов.
 * Управляет уровнями якорей, созданием предметов и проверкой прав.
 */
public class AnchorManager {
    private final AdvancedClaims plugin;
    private final Map<String, ClaimTier> tiers = new HashMap<>();
    private final NamespacedKey anchorKey;
    private final NamespacedKey anchorOwnerKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AnchorManager(AdvancedClaims plugin) {
        this.plugin = plugin;
        this.anchorKey = new NamespacedKey(plugin, "anchor_tier");
        this.anchorOwnerKey = new NamespacedKey(plugin, "anchor_owner");
        loadTiers();
    }

    /**
     * Загрузить все тиры якорей из anchors.yml.
     */
    public void loadTiers() {
        tiers.clear();
        ConfigurationSection section = plugin.getConfigManager().getAnchors().getConfigurationSection("tiers");
        if (section == null) {
            plugin.getLogger().warning("No tiers section in anchors.yml!");
            return;
        }

        List<ClaimTier> tempTiers = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                // Чтение материала
                String materialName = section.getString(key + ".material", "CAMPFIRE");
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    plugin.getLogger().warning("Invalid material '" + materialName + "' for tier " + key + ", using CAMPFIRE");
                    material = Material.CAMPFIRE;
                }

                // Чтение названия и описания
                String name = section.getString(key + ".name", "<green>Claim Anchor");
                List<String> lore = section.getStringList(key + ".lore");

                // Чтение радиусов
                int rX = section.getInt(key + ".radius-x", 8);
                int rY = section.getInt(key + ".radius-y", 8);
                int rZ = section.getInt(key + ".radius-z", 8);
                int maxRadius = section.getInt(key + ".max-radius", rX + 1);
                int cmd = section.getInt(key + ".custom-model-data", 0);

                // Чтение дополнительных настроек
                String permission = section.getString(key + ".permission", "none");
                long createCost = section.getLong(key + ".create-cost", 0);
                
                // Чтение цвета границы
                int borderRed = 0, borderGreen = 255, borderBlue = 0;
                if (section.contains(key + ".border-color")) {
                    borderRed = section.getInt(key + ".border-color.red", 0);
                    borderGreen = section.getInt(key + ".border-color.green", 255);
                    borderBlue = section.getInt(key + ".border-color.blue", 0);
                }
                
                // Чтение звуков
                String placeSound = section.getString(key + ".place-sound", "BLOCK_BEACON_ACTIVATE");
                String breakSound = section.getString(key + ".break-sound", "BLOCK_NOTE_BLOCK_BASS");

                tempTiers.add(new ClaimTier(
                    key, material, name, lore,
                    rX, rY, rZ, maxRadius, cmd,
                    permission, createCost,
                    borderRed, borderGreen, borderBlue,
                    placeSound, breakSound
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load tier " + key + ": " + e.getMessage());
            }
        }

        // Сортировка тиров по радиусу (от меньшего к большему)
        tempTiers.sort(Comparator.comparingInt(ClaimTier::radiusX));

        // Пересчет максимальных размеров
        for (int i = 0; i < tempTiers.size(); i++) {
            ClaimTier current = tempTiers.get(i);
            
            // Если max-radius не задан, вычисляем его
            int effectiveMaxRadius = current.maxRadius();
            if (effectiveMaxRadius <= current.radiusX()) {
                if (i < tempTiers.size() - 1) {
                    // Максимум = следующий тир - 1
                    effectiveMaxRadius = tempTiers.get(i + 1).radiusX() * 2 - 1;
                } else {
                    // Для последнего тира максимум = радиус + 16
                    effectiveMaxRadius = current.radiusX() + 16;
                }
            }
            
            // Создаем финальный тир с пересчитанным максимумом
            ClaimTier finalTier = new ClaimTier(
                current.id(), current.material(), current.name(), current.lore(),
                current.radiusX(), current.radiusY(), current.radiusZ(),
                effectiveMaxRadius, current.customModelData(),
                current.permission(), current.createCost(),
                current.borderRed(), current.borderGreen(), current.borderBlue(),
                current.placeSound(), current.breakSound()
            );
            tiers.put(finalTier.id(), finalTier);
        }

        plugin.getLogger().info("Loaded " + tiers.size() + " anchor tiers.");
        
        // Вывод информации о каждом тире
        for (ClaimTier tier : tiers.values()) {
            plugin.getLogger().info("  - " + tier.id() + ": " + 
                tier.getSizeX() + "x" + tier.getSizeY() + "x" + tier.getSizeZ() + 
                " (max: " + tier.getMaxSize() + ")");
        }
    }

    /**
     * Создать предмет якоря для указанного тира.
     * @param tierId ID тира
     * @return ItemStack якоря или null если тир не найден
     */
    public ItemStack createAnchorItem(String tierId) {
        return createAnchorItem(tierId, null);
    }

    /**
     * Создать предмет якоря с владельцем.
     * @param tierId ID тира
     * @param ownerUUID UUID владельца (опционально)
     * @return ItemStack якоря или null
     */
    public ItemStack createAnchorItem(String tierId, UUID ownerUUID) {
        ClaimTier tier = tiers.get(tierId);
        if (tier == null) {
            plugin.getLogger().warning("Attempted to create anchor for unknown tier: " + tierId);
            return null;
        }

        ItemStack item = new ItemStack(tier.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Установка названия
        meta.displayName(miniMessage.deserialize(tier.name()));

        // Установка описания
        List<Component> componentLore = new ArrayList<>();
        for (String line : tier.lore()) {
            componentLore.add(miniMessage.deserialize(line));
        }
        meta.lore(componentLore);

        // CustomModelData
        if (tier.customModelData() > 0) {
            meta.setCustomModelData(tier.customModelData());
        }

        // Сохранение ID тира в NBT
        meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, tier.id());
        
        // Сохранение владельца в NBT (если указан)
        if (ownerUUID != null) {
            meta.getPersistentDataContainer().set(anchorOwnerKey, PersistentDataType.STRING, ownerUUID.toString());
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Получить тир якоря из предмета.
     * @param item ItemStack для проверки
     * @return Optional с ClaimTier или пустой
     */
    public Optional<ClaimTier> getTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();

        ItemMeta meta = item.getItemMeta();
        String tierId = meta.getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING);
        if (tierId == null) return Optional.empty();

        return Optional.ofNullable(tiers.get(tierId));
    }

    /**
     * Получить тир по ID.
     * @param id ID тира
     * @return ClaimTier или null
     */
    public ClaimTier getTier(String id) {
        return tiers.get(id);
    }

    /**
     * Получить все тиры.
     * @return Коллекция всех тиров
     */
    public Collection<ClaimTier> getAllTiers() {
        return Collections.unmodifiableCollection(tiers.values());
    }

    /**
     * Получить тир по размеру привата.
     * @param size Размер привата
     * @return Подходящий тир или null
     */
    public ClaimTier getTierBySize(int size) {
        ClaimTier bestMatch = null;
        for (ClaimTier tier : tiers.values()) {
            if (tier.getSizeX() <= size) {
                if (bestMatch == null || tier.getSizeX() > bestMatch.getSizeX()) {
                    bestMatch = tier;
                }
            }
        }
        return bestMatch;
    }

    /**
     * Получить следующий тир для текущего.
     * @param currentTier Текущий тир
     * @return Optional со следующим тиром
     */
    public Optional<ClaimTier> getNextTier(ClaimTier currentTier) {
        List<ClaimTier> sortedTiers = new ArrayList<>(tiers.values());
        sortedTiers.sort(Comparator.comparingInt(ClaimTier::radiusX));
        
        int currentIndex = sortedTiers.indexOf(currentTier);
        if (currentIndex >= 0 && currentIndex < sortedTiers.size() - 1) {
            return Optional.of(sortedTiers.get(currentIndex + 1));
        }
        return Optional.empty();
    }

    /**
     * Проверить, может ли игрок использовать этот тир.
     * @param tier Тир для проверки
     * @param playerUUID UUID игрока
     * @return true если есть доступ
     */
    public boolean canUseTier(ClaimTier tier, UUID playerUUID) {
        if (tier.isPermissionFree()) return true;
        
        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(playerUUID);
        if (player.isOnline()) {
            return player.getPlayer().hasPermission("advancedclaims.admin") ||
                   player.getPlayer().hasPermission(tier.permission());
        }
        return false;
    }

    /**
     * Получить владельца якоря из предмета.
     * @param item ItemStack якоря
     * @return Optional с UUID владельца
     */
    public Optional<UUID> getAnchorOwner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        
        ItemMeta meta = item.getItemMeta();
        String ownerStr = meta.getPersistentDataContainer().get(anchorOwnerKey, PersistentDataType.STRING);
        if (ownerStr == null || ownerStr.isEmpty()) return Optional.empty();
        
        try {
            return Optional.of(UUID.fromString(ownerStr));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Установить владельца якоря.
     * @param item ItemStack якоря
     * @param ownerUUID UUID владельца
     */
    public void setAnchorOwner(ItemStack item, UUID ownerUUID) {
        if (item == null || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(anchorOwnerKey, PersistentDataType.STRING, ownerUUID.toString());
        item.setItemMeta(meta);
    }

    /**
     * Проверить, является ли предмет якорем.
     * @param item ItemStack для проверки
     * @return true если это якорь
     */
    public boolean isAnchor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(anchorKey, PersistentDataType.STRING);
    }

    /**
     * Получить минимальный тир.
     * @return ClaimTier минимального уровня
     */
    public ClaimTier getLowestTier() {
        return tiers.values().stream()
            .min(Comparator.comparingInt(ClaimTier::radiusX))
            .orElse(null);
    }

    /**
     * Получить максимальный тир.
     * @return ClaimTier максимального уровня
     */
    public ClaimTier getHighestTier() {
        return tiers.values().stream()
            .max(Comparator.comparingInt(ClaimTier::radiusX))
            .orElse(null);
    }

    /**
     * Получить тир по материалу.
     * @param material Материал для поиска
     * @return Optional с ClaimTier
     */
    public Optional<ClaimTier> getTierByMaterial(Material material) {
        return tiers.values().stream()
            .filter(tier -> tier.material() == material)
            .findFirst();
    }
}
