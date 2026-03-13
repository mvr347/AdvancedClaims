package me.lovelace.advancedclaims.manager;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Quest;
import me.lovelace.advancedclaims.model.UserData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер квестов - управление прогрессом, наградами и событиями.
 */
public class QuestManager {
    private final AdvancedClaims plugin;
    private final Map<String, Quest> quests = new ConcurrentHashMap<>();
    private final Map<UUID, UserData> users = new ConcurrentHashMap<>();
    
    // Кэш ежедневных квестов (сбрасывается каждый день)
    private final Map<UUID, Set<String>> completedDailyQuests = new ConcurrentHashMap<>();
    
    // Слушатели прогресса
    private final List<QuestProgressListener> progressListeners = new ArrayList<>();

    public QuestManager(AdvancedClaims plugin) {
        this.plugin = plugin;
        loadQuests();
    }

    /**
     * Получить все квесты.
     */
    public Collection<Quest> getAllQuests() {
        return Collections.unmodifiableCollection(quests.values());
    }

    /**
     * Получить квест по ID.
     */
    public Quest getQuestById(String id) {
        return quests.get(id);
    }

    /**
     * Получить квесты по тиру.
     */
    public List<Quest> getQuestsByTier(String tier) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.tier().equals(tier) || quest.tier().equals("all")) {
                result.add(quest);
            }
        }
        return result;
    }

    /**
     * Получить квесты по категории.
     */
    public List<Quest> getQuestsByCategory(String category) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.category().equals(category)) {
                result.add(quest);
            }
        }
        return result;
    }

    /**
     * Получить квесты по сложности.
     */
    public List<Quest> getQuestsByDifficulty(Quest.Difficulty difficulty) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.difficulty() == difficulty) {
                result.add(quest);
            }
        }
        return result;
    }

    /**
     * Получить ежедневные квесты.
     */
    public List<Quest> getDailyQuests() {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.daily()) {
                result.add(quest);
            }
        }
        return result;
    }

    /**
     * Загрузить квесты из quests.yml.
     */
    public void loadQuests() {
        quests.clear();
        FileConfiguration questConfig = plugin.getQuestsConfig();
        if (questConfig == null) {
            plugin.getLogger().warning("Quests config is null!");
            return;
        }
        
        ConfigurationSection section = questConfig.getConfigurationSection("quests");
        if (section == null) {
            plugin.getLogger().warning("No quests section in config!");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                String questName = section.getString(key + ".name", "Unknown Quest");
                String questDesc = section.getString(key + ".description", "");
                String typeStr = section.getString(key + ".type", "MINE_BLOCKS");
                String targetName = section.getString(key + ".target-name", "Block");
                int targetAmount = section.getInt(key + ".target-amount", 100);
                int rewardSlots = section.getInt(key + ".reward-slots", 0);
                int rewardBlocks = section.getInt(key + ".reward-blocks", 0);
                int rewardExpansion = section.getInt(key + ".reward-expansion-blocks", 0);
                int rewardMembers = section.getInt(key + ".reward-members", 0);
                String rewardBuff = section.getString(key + ".reward-buff", "NONE");
                String tier = section.getString(key + ".tier", "all");
                boolean repeatable = section.getBoolean(key + ".repeatable", false);
                boolean daily = section.getBoolean(key + ".daily", false);
                String difficultyStr = section.getString(key + ".difficulty", "EASY");
                String category = section.getString(key + ".category", "GENERAL");
                boolean eventOnly = section.getBoolean(key + ".event-only", false);
                String eventDate = section.getString(key + ".event-date", "");
                int minPlayers = section.getInt(key + ".min-players", 1);
                List<String> commands = section.getStringList(key + ".commands");

                Quest.QuestType questType;
                try {
                    questType = Quest.QuestType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    questType = Quest.QuestType.MINE_BLOCKS;
                }

                Quest.Difficulty difficulty;
                try {
                    difficulty = Quest.Difficulty.valueOf(difficultyStr);
                } catch (IllegalArgumentException e) {
                    difficulty = Quest.Difficulty.EASY;
                }

                quests.put(key, new Quest(
                    key,
                    questName,
                    questDesc,
                    questType,
                    targetName,
                    targetAmount,
                    rewardSlots,
                    rewardBlocks,
                    rewardExpansion,
                    rewardMembers,
                    rewardBuff,
                    tier,
                    repeatable,
                    daily,
                    difficulty,
                    category,
                    eventOnly,
                    eventDate,
                    minPlayers,
                    commands
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load quest: " + key + " - " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Loaded " + quests.size() + " quests!");
    }

    /**
     * Получить данные пользователя.
     * Сначала проверяет кэш, затем загружает из БД.
     */
    public UserData getUserData(UUID uuid) {
        // Проверяем кэш Caffeine
        UserData cached = plugin.getClaimManager().getCachedUserData(uuid);
        if (cached != null) {
            return cached;
        }
        
        // Если нет в кэше, загружаем из менеджера
        UserData data = users.computeIfAbsent(uuid, k -> new UserData(k));
        
        // Кэшируем в Caffeine
        plugin.getClaimManager().cacheUserData(uuid, data);
        
        return data;
    }

    /**
     * Загрузить данные пользователей.
     */
    public void loadUsers() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            plugin.getStorage().loadUserData(p.getUniqueId()).thenAccept(data -> {
                getUserData(p.getUniqueId()).loadFrom(data);
            });
        }
    }

    /**
     * Сохранить всех пользователей (асинхронно).
     */
    public void saveUsers() {
        for (UserData data : users.values()) {
            plugin.getStorage().saveUserDataAsync(data);
        }
    }

    /**
     * Сохранить всех пользователей (синхронно).
     */
    public void saveUsersSync() {
        plugin.getStorage().saveAllUserDataSync(users.values());
    }

    /**
     * Выгрузить пользователя.
     */
    public void unloadUser(UUID uuid) {
        users.remove(uuid);
        completedDailyQuests.remove(uuid);
    }

    /**
     * Добавить прогресс квеста.
     */
    public void addProgress(UUID uuid, Quest.QuestType type, String targetName, int amount) {
        UserData data = getUserData(uuid);
        
        for (Quest quest : quests.values()) {
            if (quest.type() != type) continue;
            
            // Проверка категории "MONSTER" для всех враждебных мобов
            if (type == Quest.QuestType.KILL_MOBS && quest.targetName().equals("MONSTER")) {
                if (!isHostileMob(targetName)) continue;
            }
            
            // Проверка категории "ANIMAL" для всех животных
            if (type == Quest.QuestType.BREED_ANIMALS && quest.targetName().equals("ANIMAL")) {
                if (!isAnimal(targetName)) continue;
            }
            
            // Проверка категории "FISH" для всех рыб
            if (type == Quest.QuestType.FISH && quest.targetName().equals("FISH")) {
                if (!isFish(targetName)) continue;
            }
            
            // Проверка категории "ANY" для любых предметов
            if (quest.targetName().equals("ANY") || quest.targetName().equalsIgnoreCase(targetName)) {
                if (!data.isQuestCompleted(quest.id()) || quest.repeatable()) {
                    int progress = data.getQuestProgress(quest.id()) + amount;
                    data.setQuestProgress(quest.id(), progress);
                    
                    // Обновляем кэш прогресса
                    plugin.getClaimManager().updateQuestProgress(uuid, quest.id(), progress);

                    if (progress >= quest.targetAmount()) {
                        data.setQuestCompleted(quest.id(), true);
                        applyRewards(quest, uuid);
                        
                        // Уведомить слушателей
                        for (QuestProgressListener listener : progressListeners) {
                            listener.onQuestComplete(uuid, quest);
                        }
                    }
                    
                    // Уведомить слушателей о прогрессе
                    for (QuestProgressListener listener : progressListeners) {
                        listener.onQuestProgress(uuid, quest, progress, quest.targetAmount());
                    }
                }
            }
        }
    }

    /**
     * Применить награды квеста.
     */
    private void applyRewards(Quest quest, UUID uuid) {
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        UserData data = getUserData(uuid);

        // Награда слотов привата
        if (quest.rewardSlots() > 0) {
            data.addBonusMemberLimit(quest.rewardSlots());
        }

        // Награда блоков расширения
        if (quest.rewardBlocks() > 0) {
            data.addExpansionBlocks(quest.rewardBlocks());
        }

        // Награда очков расширения
        if (quest.rewardExpansionBlocks() > 0) {
            data.addExpansionBlocks(quest.rewardExpansionBlocks());
        }

        // Награда участников
        if (quest.rewardMembers() > 0) {
            data.addBonusMemberLimit(quest.rewardMembers());
        }

        // Награда баффом
        if (quest.rewardBuff() != null && !quest.rewardBuff().equals("NONE")) {
            data.unlockBuff(quest.rewardBuff());
        }

        // Сохранение
        plugin.getStorage().saveUserDataAsync(data);

        // Уведомление игрока
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getMessage("quest-completed", "quest", quest.name()));
            plugin.getConfigManager().playSound(player, "tax-paid");
        }
    }

    /**
     * Сбросить ежедневные квесты.
     */
    public void resetDailyQuests() {
        completedDailyQuests.clear();
        for (UserData data : users.values()) {
            for (Quest quest : getDailyQuests()) {
                data.setQuestCompleted(quest.id(), false);
                data.setQuestProgress(quest.id(), 0);
            }
        }
    }

    /**
     * Проверить, завершен ли ежедневный квест.
     */
    public boolean isDailyQuestCompleted(UUID uuid, String questId) {
        Set<String> completed = completedDailyQuests.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        return completed.contains(questId);
    }

    /**
     * Отметить ежедневный квест как завершенный.
     */
    public void markDailyQuestComplete(UUID uuid, String questId) {
        Set<String> completed = completedDailyQuests.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        completed.add(questId);
    }

    /**
     * Добавить слушателя прогресса.
     */
    public void addProgressListener(QuestProgressListener listener) {
        progressListeners.add(listener);
    }

    /**
     * Удалить слушателя прогресса.
     */
    public void removeProgressListener(QuestProgressListener listener) {
        progressListeners.remove(listener);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private boolean isHostileMob(String mobName) {
        return mobName.equals("ZOMBIE") || mobName.equals("SKELETON") || mobName.equals("CREEPER") ||
               mobName.equals("SPIDER") || mobName.equals("WITCH") || mobName.equals("PILLAGER") ||
               mobName.equals("VINDICATOR") || mobName.equals("EVOKER") || mobName.equals("RAVAGER") ||
               mobName.equals("DROWNED") || mobName.equals("HUSK") || mobName.equals("STRAY") ||
               mobName.equals("CAVE_SPIDER") || mobName.equals("SILVERFISH") || mobName.equals("ENDERMITE") ||
               mobName.equals("SLIME") || mobName.equals("MAGMA_CUBE") || mobName.equals("GHAST") ||
               mobName.equals("BLAZE") || mobName.equals("WITHER_SKELETON") || mobName.equals("HOGLIN") ||
               mobName.equals("PIGLIN_BRUTE") || mobName.equals("ZOGLIN") || mobName.equals("PHANTOM") ||
               mobName.equals("WARDEN") || mobName.equals("BREEZE");
    }

    private boolean isAnimal(String mobName) {
        return mobName.equals("COW") || mobName.equals("PIG") || mobName.equals("SHEEP") ||
               mobName.equals("CHICKEN") || mobName.equals("HORSE") || mobName.equals("DONKEY") ||
               mobName.equals("MULE") || mobName.equals("LLAMA") || mobName.equals("TRADER_LLAMA") ||
               mobName.equals("RABBIT") || mobName.equals("CAT") || mobName.equals("WOLF") ||
               mobName.equals("FOX") || mobName.equals("PANDA") || mobName.equals("TURTLE") ||
               mobName.equals("DOLPHIN") || mobName.equals("GOAT") || mobName.equals("AXOLOTL") ||
               mobName.equals("FROG") || mobName.equals("ALLAY") || mobName.equals("CAMEL") ||
               mobName.equals("SNIFFER");
    }

    private boolean isFish(String itemName) {
        return itemName.equals("COD") || itemName.equals("SALMON") || itemName.equals("TROPICAL_FISH") ||
               itemName.equals("PUFFERFISH");
    }

    /**
     * Интерфейс слушателя прогресса квестов.
     */
    public interface QuestProgressListener {
        void onQuestProgress(UUID player, Quest quest, int progress, int target);
        void onQuestComplete(UUID player, Quest quest);
    }
}
