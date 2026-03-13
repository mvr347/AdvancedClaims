package me.lovelace.advancedclaims;

import me.lovelace.advancedclaims.command.MainCommand;
import me.lovelace.advancedclaims.command.RentalCommand;
import me.lovelace.advancedclaims.hook.PAPIExpansion;
import me.lovelace.advancedclaims.listener.ChatListener;
import me.lovelace.advancedclaims.listener.AnchorListener;
import me.lovelace.advancedclaims.listener.GuiListener;
import me.lovelace.advancedclaims.listener.PlayerDataListener;
import me.lovelace.advancedclaims.listener.ProtectionListener;
import me.lovelace.advancedclaims.listener.QuestTrackerListener;
import me.lovelace.advancedclaims.listener.PlayerMoveListener;
import me.lovelace.advancedclaims.listener.RentalInteractListener;
import me.lovelace.advancedclaims.manager.ConfigManager;
import me.lovelace.advancedclaims.manager.ClaimManager;
import me.lovelace.advancedclaims.manager.AnchorManager;
import me.lovelace.advancedclaims.manager.QuestManager;
import me.lovelace.advancedclaims.manager.RentalManager;
import me.lovelace.advancedclaims.manager.ItemCurrencyManager;
import me.lovelace.advancedclaims.task.AutoDeleteTask;
import me.lovelace.advancedclaims.task.GlobalTickManager;
import me.lovelace.advancedclaims.task.RentalExpirationTask;
import me.lovelace.advancedclaims.storage.SQLiteStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdvancedClaims extends JavaPlugin {
    private static AdvancedClaims instance;
    private SQLiteStorage storage;
    private ConfigManager configManager;
    private ClaimManager claimManager;
    private AnchorManager anchorManager;
    private QuestManager questManager;
    private RentalManager rentalManager;
    private ItemCurrencyManager currencyManager;
    private ChatListener chatListener;
    private AnchorListener anchorListener;
    private RentalExpirationTask rentalExpirationTask;
    private AutoDeleteTask autoDeleteTask;
    private GlobalTickManager globalTickManager;
    private org.bukkit.configuration.file.FileConfiguration questsConfig;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("AdvancedClaims loading...");

        try {
            // 1. Загрузка конфигурации
            this.configManager = new ConfigManager(this);
            this.configManager.loadAll();
            getLogger().info("Config loaded successfully!");
            
            // Загрузка quests.yml
            this.saveResource("quests.yml", false);
            this.questsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File(getDataFolder(), "quests.yml"));
            getLogger().info("Quests loaded successfully!");

            // 2. Инициализация БД
            this.storage = new SQLiteStorage(this);
            this.storage.initDatabase().join();
            getLogger().info("SQLite initialized successfully!");

            // 3. Создание менеджеров
            this.chatListener = new ChatListener(this);
            this.anchorManager = new AnchorManager(this);
            this.claimManager = new ClaimManager(this);
            this.questManager = new QuestManager(this);
            this.rentalManager = new RentalManager(this);
            this.currencyManager = new ItemCurrencyManager(this);
            
            // 4. Инициализация API
            me.lovelace.advancedclaims.api.AdvancedClaimsAPI.init(this);
            getLogger().info("API initialized!");

            // 5. Загрузка приватов из базы данных
            this.storage.loadAllClaims()
                    .thenAccept(claims -> {
                        claimManager.loadClaims(claims);
                        rentalManager.loadPlots(claims);
                        getLogger().info("Loaded " + claims.size() + " claims from database.");
                    })
                    .join();

            // 6. Регистрация команд
            getCommand("advancedclaims").setExecutor(new MainCommand(this));
            getCommand("advancedclaims").setTabCompleter(new MainCommand(this));
            getCommand("rental").setExecutor(new RentalCommand(this));
            getCommand("rental").setTabCompleter(new RentalCommand(this));
            getLogger().info("Commands registered!");

            // 6. Регистрация слушателей
            PluginManager pm = getServer().getPluginManager();
            getServer().getPluginManager().registerEvents(new me.lovelace.advancedclaims.listener.LandlordListener(this), this);
            this.anchorListener = new AnchorListener(this);

            pm.registerEvents(this.anchorListener, this);
            pm.registerEvents(this.chatListener, this);
            pm.registerEvents(new ProtectionListener(this), this);
            pm.registerEvents(new GuiListener(this), this);
            pm.registerEvents(new PlayerDataListener(this), this);
            pm.registerEvents(new QuestTrackerListener(this), this);
            pm.registerEvents(new PlayerMoveListener(this), this);
            pm.registerEvents(new RentalInteractListener(this), this);
            getLogger().info("Listeners registered!");

            // 7. Проверка и регистрация PAPI
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new PAPIExpansion(this).register();
                getLogger().info("PlaceholderAPI integration enabled!");
            }

            // 8. Старт фоновых задач
            this.globalTickManager = new GlobalTickManager(this);
            this.globalTickManager.start();

            autoDeleteTask = new AutoDeleteTask(this);
            autoDeleteTask.start();

            this.rentalExpirationTask = new RentalExpirationTask(this);
            this.rentalExpirationTask.start();
            
            // 9. Проверка обновлений (если включена)
            if (configManager.isCheckUpdates()) {
                checkForUpdates();
            }

            // 10. Инициализация метрик (если включены)
            if (configManager.isMetrics()) {
                initializeMetrics();
            }

            getLogger().info("AdvancedClaims enabled successfully!");

        } catch (Exception e) {
            getLogger().severe("Failed to enable AdvancedClaims! " + e.getMessage());
            e.printStackTrace();
            this.setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling AdvancedClaims...");

        try {
            // 1. Остановка всех задач
            if (globalTickManager != null) globalTickManager.cancel();
            if (rentalExpirationTask != null) rentalExpirationTask.cancel();
            if (autoDeleteTask != null && !autoDeleteTask.isCancelled()) autoDeleteTask.cancel();
            getLogger().info("Tasks stopped!");

            // 2. СИНХРОННОЕ Сохранение всех данных пользователей (ИСПРАВЛЕН БАГ ПОТЕРИ ДАННЫХ)
            if (questManager != null) {
                questManager.saveUsersSync();
                getLogger().info("User data saved synchronously!");
            }
            
            // 3. Очистка кэшей Caffeine
            if (claimManager != null) {
                claimManager.invalidateAllCaches();
                getLogger().info("Caffeine caches cleared!");
            }

            // 4. Сохранение всех изменённых приватов синхронно
            if (claimManager != null && storage != null) {
                storage.saveAllClaimsSync(claimManager.getAllClaims());
                getLogger().info("Claims saved synchronously!");
            }

            // 5. Только потом закрываем хранилище и убиваем пулы потоков
            if (storage != null) {
                storage.close();
                getLogger().info("Database closed safely!");
            }

        } catch (Exception e) {
            getLogger().severe("Error during disable: " + e.getMessage());
            e.printStackTrace();
        } finally {
            getLogger().info("AdvancedClaims disabled successfully!");
            instance = null;
        }
    }

    public static AdvancedClaims getInstance() { return instance; }

    // Getters
    public SQLiteStorage getStorage() { return storage; }
    public ConfigManager getConfigManager() { return configManager; }
    public ClaimManager getClaimManager() { return claimManager; }
    public AnchorManager getAnchorManager() { return anchorManager; }
    public QuestManager getQuestManager() { return questManager; }
    public RentalManager getRentalManager() { return rentalManager; }
    public ItemCurrencyManager getCurrencyManager() { return currencyManager; }
    public ChatListener getChatListener() { return chatListener; }
    public AnchorListener getAnchorListener() { return anchorListener; }
    public org.bukkit.configuration.file.FileConfiguration getQuestsConfig() { return questsConfig; }
    
    /**
     * Проверка обновлений плагина.
     */
    private void checkForUpdates() {
        getServer().getAsyncScheduler().runNow(this, task -> {
            try {
                // Здесь будет логика проверки обновлений
                // Например, запрос к GitHub API или SpigotMC
                getLogger().info("Checking for updates...");
                // TODO: Реализовать проверку обновлений
            } catch (Exception e) {
                if (configManager.isDebugMode()) {
                    configManager.debugWarn("Failed to check for updates: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Инициализация метрик bStats.
     */
    private void initializeMetrics() {
        // Здесь будет инициализация bStats
        // int pluginId = XXXX; // ID плагина на bStats
        // new Metrics(this, pluginId);
        getLogger().info("Metrics enabled");
        // TODO: Добавить bStats при публикации
    }
}