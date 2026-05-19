package me.lovelace.advancedclaims.manager;

import me.lovelace.advancedclaims.AdvancedClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigManager {
    private final AdvancedClaims plugin;
    private FileConfiguration config;
    private FileConfiguration lang;
    private FileConfiguration anchors;
    private final MiniMessage mm = MiniMessage.miniMessage();
    
    // Кэшированные настройки
    private boolean debugMode;
    private boolean checkUpdates;
    private boolean metrics;
    private String language;

    public ConfigManager(AdvancedClaims plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Загрузка настроек
        this.debugMode = config.getBoolean("misc.debug", false);
        this.checkUpdates = config.getBoolean("misc.check-updates", true);
        this.metrics = config.getBoolean("misc.metrics", true);
        this.language = config.getString("misc.language", "ru");

        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) plugin.saveResource("lang.yml", false);
        lang = YamlConfiguration.loadConfiguration(langFile);

        File anchorsFile = new File(plugin.getDataFolder(), "anchors.yml");
        if (!anchorsFile.exists()) plugin.saveResource("anchors.yml", false);
        anchors = YamlConfiguration.loadConfiguration(anchorsFile);
        
        // Логирование настроек
        if (debugMode) {
            plugin.getLogger().info("Debug mode enabled");
        }
        plugin.getLogger().info("Language: " + language);
        plugin.getLogger().info("Check updates: " + checkUpdates);
        plugin.getLogger().info("Metrics: " + metrics);
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getAnchors() { return anchors; }
    public FileConfiguration getLang() { return lang; }
    
    // ===== ГЕТТЕРЫ НАСТРОЕК =====
    
    /**
     * Проверить, включён ли режим отладки.
     * @return true если debug включён
     */
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Проверить, включена ли проверка обновлений.
     * @return true если проверка включена
     */
    public boolean isCheckUpdates() {
        return checkUpdates;
    }
    
    /**
     * Проверить, включены ли метрики.
     * @return true если метрики включены
     */
    public boolean isMetrics() {
        return metrics;
    }
    
    /**
     * Получить текущий язык.
     * @return код языка (ru, en, и т.д.)
     */
    public String getLanguage() {
        return language;
    }
    
    public boolean isUseEntityBlocks() {
        return config.getBoolean("border.use-entity-blocks", true);
    }

    public Material getBorderMaterial() {
        String matName = config.getString("border.material", "BARRIER");
        Material mat = Material.getMaterial(matName.toUpperCase());
        return mat != null ? mat : Material.BARRIER;
    }

    public boolean isBorderGlowing() {
        return config.getBoolean("border.glowing", true);
    }

    /**
     * Логирование в режиме отладки.
     * @param message Сообщение для логирования
     */
    public void debug(String message) {
        if (debugMode) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
    
    /**
     * Логирование в режиме отладки с предупреждением.
     * @param message Сообщение для логирования
     */
    public void debugWarn(String message) {
        if (debugMode) {
            plugin.getLogger().warning("[DEBUG] " + message);
        }
    }

    private Optional<Sound> getSound(String soundKey) {
        String soundName = config.getString("sounds." + soundKey, "NONE");
        if (soundName.equalsIgnoreCase("NONE") || soundName.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Registry.SOUNDS.get(NamespacedKey.minecraft(soundName.toLowerCase())));
    }

    /**
     * Воспроизвести звук игроку.
     * Если звук установлен в "NONE" - ничего не происходит.
     * 
     * @param player Игрок
     * @param soundKey Ключ звука в конфиге (например, "gui-click")
     */
    public void playSound(Player player, String soundKey) {
        getSound(soundKey).ifPresent(sound -> player.playSound(player.getLocation(), sound, 1f, 1f));
    }

    /**
     * Воспроизвести звук с настройками.
     * Если звук установлен в "NONE" - ничего не происходит.
     * 
     * @param player Игрок
     * @param soundKey Ключ звука в конфиге
     * @param volume Громкость (0.0-1.0)
     * @param pitch Высота (0.5-2.0)
     */
    public void playSound(Player player, String soundKey, float volume, float pitch) {
        getSound(soundKey).ifPresent(sound -> player.playSound(player.getLocation(), sound, volume, pitch));
    }

    /**
     * Воспроизвести звук для всех игроков в радиусе.
     * Если звук установлен в "NONE" - ничего не происходит.
     * 
     * @param location Место воспроизведения
     * @param soundKey Ключ звука в конфиге
     * @param radius Радиус (в блоках)
     */
    public void playSoundForNearby(org.bukkit.Location location, String soundKey, double radius) {
        getSound(soundKey).ifPresent(sound -> {
            if (location.getWorld() != null) {
                location.getWorld().playSound(location, sound, 1f, 1f);
            }
        });
    }

    /**
     * Проверить, включён ли звук.
     * @param soundKey Ключ звука
     * @return true если звук не "NONE" и не пустой
     */
    public boolean isSoundEnabled(String soundKey) {
        String soundName = config.getString("sounds." + soundKey, "NONE");
        return !soundName.equalsIgnoreCase("NONE") && !soundName.trim().isEmpty();
    }

    // Умный конвертер старых цветов в теги MiniMessage (Фикс краша!)
    private String convertLegacyToMiniMessage(String text) {
        if (text == null) return "";
        return text.replace("&0", "<black>").replace("§0", "<black>")
                .replace("&1", "<dark_blue>").replace("§1", "<dark_blue>")
                .replace("&2", "<dark_green>").replace("§2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("§3", "<dark_aqua>")
                .replace("&4", "<dark_red>").replace("§4", "<dark_red>")
                .replace("&5", "<dark_purple>").replace("§5", "<dark_purple>")
                .replace("&6", "<gold>").replace("§6", "<gold>")
                .replace("&7", "<gray>").replace("§7", "<gray>")
                .replace("&8", "<dark_gray>").replace("§8", "<dark_gray>")
                .replace("&9", "<blue>").replace("§9", "<blue>")
                .replace("&a", "<green>").replace("§a", "<green>")
                .replace("&b", "<aqua>").replace("§b", "<aqua>")
                .replace("&c", "<red>").replace("§c", "<red>")
                .replace("&d", "<light_purple>").replace("§d", "<light_purple>")
                .replace("&e", "<yellow>").replace("§e", "<yellow>")
                .replace("&f", "<white>").replace("§f", "<white>")
                .replace("&k", "<obfuscated>").replace("§k", "<obfuscated>")
                .replace("&l", "<bold>").replace("§l", "<bold>")
                .replace("&m", "<strikethrough>").replace("§m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("§n", "<underlined>")
                .replace("&o", "<italic>").replace("§o", "<italic>")
                .replace("&r", "<reset>").replace("§r", "<reset>");
    }

    public Component getMessage(String path, String... placeholders) {
        String prefix = lang.getString("prefix", "<dark_gray>[ <gold>AC <dark_gray>] <white> ");
        
        // Пробуем получить как список, если нет - как одну строку
        List<String> list = lang.getStringList(path);
        String msg;
        if (list.isEmpty()) {
            msg = lang.getString(path, "<red>Error: " + path);
        } else {
            msg = String.join("\n", list);
        }
        
        // Проверка на NONE - не выводить сообщение
        if (msg.equalsIgnoreCase("NONE") || msg.equalsIgnoreCase("none") || msg.trim().isEmpty()) {
            return Component.empty();
        }
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        return mm.deserialize(convertLegacyToMiniMessage(prefix + msg));
    }

    public String getGuiText(String path, String... placeholders) {
        // Пробуем получить как список, если нет - как одну строку
        List<String> list = lang.getStringList(path);
        String text;
        if (list.isEmpty()) {
            text = lang.getString(path, "<red>Error: " + path);
        } else {
            text = String.join("\n", list);
        }
        
        // Проверка на NONE - не выводить текст
        if (text.equalsIgnoreCase("NONE") || text.equalsIgnoreCase("none") || text.trim().isEmpty()) {
            return "";
        }
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        Component comp = mm.deserialize(convertLegacyToMiniMessage(text));
        return LegacyComponentSerializer.legacySection().serialize(comp);
    }

    public List<String> getGuiLore(String path, String... placeholders) {
        List<String> list = lang.getStringList(path);
        List<String> result = new ArrayList<>();
        for (String line : list) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    line = line.replace("{" + placeholders[i] + "}", placeholders[i+1]);
                }
            }
            Component comp = mm.deserialize(convertLegacyToMiniMessage(line));
            result.add(LegacyComponentSerializer.legacySection().serialize(comp));
        }
        return result;
    }

    public List<Component> getHelpMessage(String path, String... placeholders) {
        List<String> list = lang.getStringList(path);
        List<Component> result = new ArrayList<>();
        for (String line : list) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    line = line.replace("{" + placeholders[i] + "}", placeholders[i+1]);
                }
            }
            result.add(mm.deserialize(convertLegacyToMiniMessage(line)));
        }
        return result;
    }

    public boolean isInsideSpawnClaim(org.bukkit.Location loc) {
        if (!config.getBoolean("spawn-claim.enabled", false)) return false;
        String worldName = config.getString("spawn-claim.world", "world");
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;

        int cx = config.getInt("spawn-claim.x", 0);
        int cz = config.getInt("spawn-claim.z", 0);
        int radius = config.getInt("spawn-claim.radius", 300);

        int dx = loc.getBlockX() - cx;
        int dz = loc.getBlockZ() - cz;
        return Math.abs(dx) <= radius && Math.abs(dz) <= radius;
    }

    public boolean getSpawnFlag(String flag) {
        return config.getBoolean("spawn-claim.flags." + flag, false);
    }

    public String getString(String path, String... placeholders) {
        // Пробуем получить как список, если нет - как одну строку
        List<String> list = lang.getStringList(path);
        String text;
        if (list.isEmpty()) {
            text = lang.getString(path, "<red>Error: " + path);
        } else {
            text = String.join("\n", list);
        }
        
        // Проверка на NONE - не выводить текст
        if (text.equalsIgnoreCase("NONE") || text.equalsIgnoreCase("none") || text.trim().isEmpty()) {
            return "";
        }
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        Component comp = mm.deserialize(convertLegacyToMiniMessage(text));
        return LegacyComponentSerializer.legacySection().serialize(comp);
    }

    public Component getComponent(String path, String... placeholders) {
        // Пробуем получить как список, если нет - как одну строку
        List<String> list = lang.getStringList(path);
        String text;
        if (list.isEmpty()) {
            text = lang.getString(path, "<red>Error: " + path);
        } else {
            text = String.join("\n", list);
        }
        
        // Проверка на NONE - не выводить компонент
        if (text.equalsIgnoreCase("NONE") || text.equalsIgnoreCase("none") || text.trim().isEmpty()) {
            return Component.empty();
        }
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        return mm.deserialize(convertLegacyToMiniMessage(text));
    }
}