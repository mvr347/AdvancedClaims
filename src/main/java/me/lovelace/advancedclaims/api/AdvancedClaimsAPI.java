package me.lovelace.advancedclaims.api;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.manager.ClaimManager;
import me.lovelace.advancedclaims.manager.QuestManager;
import me.lovelace.advancedclaims.manager.RentalManager;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.Quest;
import me.lovelace.advancedclaims.model.TrustLevel;
import me.lovelace.advancedclaims.model.UserData;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Основное API для взаимодействия с AdvancedClaims.
 * Используйте этот класс для интеграции с другими плагинами.
 * 
 * Пример использования:
 * <pre>
 * AdvancedClaimsAPI api = AdvancedClaimsAPI.getInstance();
 * Optional<Claim> claim = api.getClaimAt(player.getLocation());
 * api.addPlayerToClaim(claim.get(), player, TrustLevel.BUILD);
 * </pre>
 */
public final class AdvancedClaimsAPI {
    
    private static AdvancedClaimsAPI instance;
    private final AdvancedClaims plugin;

    private AdvancedClaimsAPI(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализировать API (вызывается внутри плагина).
     */
    public static void init(AdvancedClaims plugin) {
        if (instance == null) {
            instance = new AdvancedClaimsAPI(plugin);
        }
    }

    /**
     * Получить экземпляр API.
     * @return API экземпляр
     * @throws IllegalStateException если API не инициализирован
     */
    public static AdvancedClaimsAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AdvancedClaimsAPI not initialized!");
        }
        return instance;
    }

    /**
     * Проверить, инициализировано ли API.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    // ===== CLAIMS API =====

    /**
     * Получить приват по координатам.
     * @param location Координаты
     * @return Optional с приватом
     */
    public Optional<Claim> getClaimAt(Location location) {
        return plugin.getClaimManager().getClaimAt(location);
    }

    /**
     * Получить приват по ID.
     * @param claimId ID привата
     * @return Optional с приватом
     */
    public Optional<Claim> getClaimById(UUID claimId) {
        return plugin.getClaimManager().getClaimById(claimId);
    }

    /**
     * Получить все приваты.
     * @return Список всех приватов
     */
    public Collection<Claim> getAllClaims() {
        return plugin.getClaimManager().getAllClaims();
    }

    /**
     * Получить все приваты игрока.
     * @param player Игрок
     * @return Список приватов игрока (O(1) из кэша)
     */
    public List<Claim> getPlayerClaims(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return Collections.emptyList();
        }
        return plugin.getClaimManager().getClaimsByOwner(player.getUniqueId());
    }

    /**
     * Получить все приваты, где игрок имеет доступ.
     * @param player Игрок
     * @return Список приватов с доступом (O(1) из кэша)
     */
    public List<Claim> getPlayerAccessibleClaims(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return Collections.emptyList();
        }
        return plugin.getClaimManager().getClaimsByPlayer(player.getUniqueId());
    }

    /**
     * Создать новый приват.
     * @param world Мир
     * @param box Границы
     * @param owner Владелец
     * @param anchorLocation Место якоря
     * @return Созданный приват
     */
    public Claim createClaim(World world, BoundingBox box, UUID owner, Location anchorLocation) {
        Claim claim = new Claim(UUID.randomUUID(), world, box, owner, anchorLocation);
        plugin.getClaimManager().addClaimToCache(claim);
        plugin.getStorage().saveClaimAsync(claim);
        return claim;
    }

    /**
     * Удалить приват.
     * @param claimId ID привата
     */
    public void deleteClaim(UUID claimId) {
        plugin.getClaimManager().getClaimById(claimId).ifPresent(claim -> {
            plugin.getClaimManager().removeClaimFromCache(claimId);
            plugin.getStorage().deleteClaimAsync(claimId);
        });
    }

    /**
     * Добавить игрока в приват.
     * @param claim Приват
     * @param player Игрок
     * @param trustLevel Уровень доступа
     */
    public void addPlayerToClaim(Claim claim, OfflinePlayer player, TrustLevel trustLevel) {
        claim.setTrust(player.getUniqueId(), trustLevel);
        plugin.getStorage().saveMemberAsync(claim.getId(), player.getUniqueId(), trustLevel);
    }

    /**
     * Удалить игрока из привата.
     * @param claim Приват
     * @param player Игрок
     */
    public void removePlayerFromClaim(Claim claim, OfflinePlayer player) {
        claim.getMembers().remove(player.getUniqueId());
        plugin.getStorage().removeMemberAsync(claim.getId(), player.getUniqueId());
    }

    /**
     * Получить уровень доступа игрока к привату.
     * @param claim Приват
     * @param player Игрок
     * @return Уровень доступа
     */
    public TrustLevel getTrustLevel(Claim claim, OfflinePlayer player) {
        return claim.getTrust(player.getUniqueId());
    }

    /**
     * Проверить, имеет ли игрок доступ к привату.
     * @param claim Приват
     * @param player Игрок
     * @param requiredLevel Требуемый уровень
     * @return true если есть доступ
     */
    public boolean hasAccess(Claim claim, OfflinePlayer player, TrustLevel requiredLevel) {
        if (claim == null || player == null || requiredLevel == null) {
            return false;
        }
        // getTrust() никогда не возвращает null, только TrustLevel.NONE
        TrustLevel trust = claim.getTrust(player.getUniqueId());
        return trust != null && trust.ordinal() >= requiredLevel.ordinal();
    }

    // ===== QUESTS API =====

    /**
     * Получить все квесты.
     * @return Коллекция квестов
     */
    public Collection<Quest> getAllQuests() {
        return plugin.getQuestManager().getAllQuests();
    }

    /**
     * Получить квест по ID.
     * @param questId ID квеста
     * @return Квест или null
     */
    public Quest getQuestById(String questId) {
        return plugin.getQuestManager().getQuestById(questId);
    }

    /**
     * Получить квесты по тиру.
     * @param tier Тир
     * @return Список квестов
     */
    public List<Quest> getQuestsByTier(String tier) {
        return plugin.getQuestManager().getQuestsByTier(tier);
    }

    /**
     * Получить квесты по категории.
     * @param category Категория
     * @return Список квестов
     */
    public List<Quest> getQuestsByCategory(String category) {
        return plugin.getQuestManager().getQuestsByCategory(category);
    }

    /**
     * Получить квесты по сложности.
     * @param difficulty Сложность
     * @return Список квестов
     */
    public List<Quest> getQuestsByDifficulty(Quest.Difficulty difficulty) {
        return plugin.getQuestManager().getQuestsByDifficulty(difficulty);
    }

    /**
     * Получить ежедневные квесты.
     * @return Список ежедневных квестов
     */
    public List<Quest> getDailyQuests() {
        return plugin.getQuestManager().getDailyQuests();
    }

    /**
     * Получить прогресс квеста игрока.
     * @param player Игрок
     * @param questId ID квеста
     * @return Прогресс
     */
    public int getQuestProgress(OfflinePlayer player, String questId) {
        return plugin.getQuestManager().getUserData(player.getUniqueId()).getQuestProgress(questId);
    }

    /**
     * Проверить, завершен ли квест.
     * @param player Игрок
     * @param questId ID квеста
     * @return true если завершен
     */
    public boolean isQuestCompleted(OfflinePlayer player, String questId) {
        return plugin.getQuestManager().getUserData(player.getUniqueId()).isQuestCompleted(questId);
    }

    /**
     * Добавить прогресс квеста.
     * @param player Игрок
     * @param type Тип квеста
     * @param targetName Цель
     * @param amount Количество
     */
    public void addQuestProgress(OfflinePlayer player, Quest.QuestType type, String targetName, int amount) {
        plugin.getQuestManager().addProgress(player.getUniqueId(), type, targetName, amount);
    }

    /**
     * Добавить прогресс квеста (по ID квеста).
     * @param player Игрок
     * @param questId ID квеста
     * @param amount Количество
     */
    public void addQuestProgressById(OfflinePlayer player, String questId, int amount) {
        Quest quest = getQuestById(questId);
        if (quest != null) {
            addQuestProgress(player, quest.type(), quest.targetName(), amount);
        }
    }

    // ===== USER DATA API =====

    /**
     * Получить данные пользователя.
     * @param player Игрок
     * @return UserData или null если не загружены
     */
    public UserData getUserData(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return null;
        }
        try {
            return plugin.getQuestManager().getUserData(player.getUniqueId());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получить количество очков расширения игрока.
     * @param player Игрок
     * @return Количество очков (0 если ошибка)
     */
    public int getExpansionBlocks(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getExpansionBlocks() : 0;
    }

    /**
     * Получить лимит участников привата игрока.
     * @param player Игрок
     * @return Лимит (0 если ошибка)
     */
    public int getMemberLimit(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getBonusMemberLimit() : 0;
    }

    /**
     * Проверить, разблокирован ли бафф у игрока.
     * @param player Игрок
     * @param buffName Название баффа
     * @return true если разблокирован (false если ошибка)
     */
    public boolean hasBuffUnlocked(OfflinePlayer player, String buffName) {
        UserData data = getUserData(player);
        return data != null && data.hasBuffUnlocked(buffName);
    }

    // ===== RENTAL API =====

    /**
     * Получить все арендные плоты.
     * @return Список арендных приватов (O(1) из кэша)
     */
    public List<Claim> getAllRentalPlots() {
        return plugin.getClaimManager().getAllRentalPlots();
    }

    /**
     * Получить арендный плот по названию.
     * @param name Название
     * @return Optional с плотом
     */
    public Optional<Claim> getRentalPlotByName(String name) {
        UUID plotId = plugin.getRentalManager().getPlotIdByName(name);
        if (plotId != null) {
            return plugin.getClaimManager().getClaimById(plotId);
        }
        return Optional.empty();
    }

    /**
     * Проверить, арендован ли плот.
     * @param plot Плот
     * @return true если арендован
     */
    public boolean isRented(Claim plot) {
        return plot.isRented();
    }

    /**
     * Получить время окончания аренды.
     * @param plot Плот
     * @return Время в миллисекундах
     */
    public long getRentalEndTime(Claim plot) {
        return plot.getRentalEndTime();
    }

    /**
     * Получить цену аренды плота.
     * @param plot Плот
     * @return Цена
     */
    public long getRentalPrice(Claim plot) {
        return plot.getRentalPrice();
    }

    // ===== EVENT API =====

    /**
     * Зарегистрировать слушателя прогресса квестов.
     * @param listener Слушатель
     */
    public void registerQuestProgressListener(QuestManager.QuestProgressListener listener) {
        plugin.getQuestManager().addProgressListener(listener);
    }

    /**
     * Отменить регистрацию слушателя.
     * @param listener Слушатель
     */
    public void unregisterQuestProgressListener(QuestManager.QuestProgressListener listener) {
        plugin.getQuestManager().removeProgressListener(listener);
    }

    // ===== ASYNC API =====

    /**
     * Асинхронно получить данные пользователя из БД.
     * @param player Игрок
     * @return CompletableFuture с UserData
     */
    public CompletableFuture<UserData> loadUserDataAsync(OfflinePlayer player) {
        return plugin.getStorage().loadUserData(player.getUniqueId());
    }

    /**
     * Асинхронно сохранить данные пользователя.
     * @param player Игрок
     */
    public void saveUserDataAsync(OfflinePlayer player) {
        UserData data = getUserData(player);
        plugin.getStorage().saveUserDataAsync(data);
    }

    /**
     * Асинхронно загрузить приват из БД.
     * @param claimId ID привата
     * @return CompletableFuture с Claim (null если не найден)
     * 
     * Примечание: Метод возвращает данные из кэша.
     * Для полной загрузки из БД используйте plugin.getStorage().loadClaimAsync()
     */
    public CompletableFuture<Claim> loadClaimAsync(UUID claimId) {
        if (claimId == null) {
            return CompletableFuture.completedFuture(null);
        }
        // Возвращаем из кэша (быстро)
        return CompletableFuture.completedFuture(
            plugin.getClaimManager().getClaimById(claimId).orElse(null)
        );
    }

    /**
     * Асинхронно сохранить приват.
     * @param claim Приват
     */
    public void saveClaimAsync(Claim claim) {
        plugin.getStorage().saveClaimAsync(claim);
    }
}
