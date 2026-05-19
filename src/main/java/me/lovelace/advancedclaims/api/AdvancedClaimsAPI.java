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
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
     * Получить все приваты (игроков и кланов).
     * @return Список всех приватов
     */
    public Collection<Claim> getAllClaims() {
        return plugin.getClaimManager().getAllClaims();
    }

    /**
     * Получить все клановые приваты.
     * @return Список клановых приватов
     */
    public List<Claim> getAllClanClaims() {
        return plugin.getClaimManager().getAllClanClaims();
    }

    /**
     * Получить клановый приват по ID клана (ownerUuid).
     * @param clanId ID клана
     * @return Optional с приватом
     */
    public Optional<Claim> getClanClaim(UUID clanId) {
        return plugin.getClaimManager().getClanClaimByClanId(clanId);
    }

    /**
     * Получить все приваты игрока.
     * @param player Игрок
     * @return Список приватов игрока
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
     * @return Список приватов с доступом
     */
    public List<Claim> getPlayerAccessibleClaims(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return Collections.emptyList();
        }
        return plugin.getClaimManager().getClaimsByPlayer(player.getUniqueId());
    }

    /**
     * Создать новый приват игрока.
     * @param world Мир
     * @param box Границы
     * @param owner Владелец
     * @param anchorLocation Место якоря
     * @return Созданный приват
     */
    public Claim createClaim(World world, BoundingBox box, UUID owner, Location anchorLocation) {
        Claim claim = new Claim(UUID.randomUUID(), world, box, owner, anchorLocation);
        claim.setClaimType(Claim.ClaimType.PLAYER); // Отмечаем как клановую территорию
        plugin.getClaimManager().addClaimToCache(claim);
        plugin.getStorage().saveClaimAsync(claim);
        return claim;
    }

    /**
     * Создать новый клановый приват.
     * @param world Мир
     * @param box Границы
     * @param clanId ID клана (будет владельцем)
     * @param anchorLocation Место якоря
     * @return Созданный клановый приват
     */
    public Claim createClanClaim(World world, BoundingBox box, UUID clanId, Location anchorLocation) {
        Claim claim = new Claim(UUID.randomUUID(), world, box, clanId, anchorLocation);
        claim.setClaimType(Claim.ClaimType.CLAN);
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
        claim.removePlayer(player.getUniqueId());
        plugin.getStorage().removeMemberAsync(claim.getId(), player.getUniqueId());
    }

    /**
     * Удалить участника клана из привата.
     * @param claimId ID привата
     * @param playerUuid UUID игрока
     */
    public void removeClanMemberFromClaim(UUID claimId, UUID playerUuid) {
        plugin.getClaimManager().getClaimById(claimId).ifPresent(claim -> {
            claim.removePlayer(playerUuid);
            plugin.getStorage().removeMemberAsync(claimId, playerUuid);
        });
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
        TrustLevel trust = claim.getTrust(player.getUniqueId());
        return trust != null && trust.ordinal() >= requiredLevel.ordinal();
    }

    /**
     * Динамическое управление ролями из других плагинов (например, Clans).
     * <p>
     * <b>Логика работы:</b>
     * <ul>
     * <li>При запрете {@link ClaimPermission#BUILD}, уровень игрока понижается до {@link TrustLevel#CONTAINER}.
     *     Это сделано для того, чтобы игрок, которому запретили строить, все еще мог пользоваться сундуками, если это разрешено.
     *     Если у игрока был уровень {@link TrustLevel#MANAGER}, он также будет понижен.</li>
     * <li>При разрешении {@link ClaimPermission#BUILD}, уровень игрока устанавливается в {@link TrustLevel#BUILD}.</li>
     * <li>При запрете {@link ClaimPermission#CONTAINER}, если у игрока нет права на строительство (уровень ниже {@link TrustLevel#BUILD}),
     *     он полностью удаляется из привата.</li>
     * <li>При разрешении {@link ClaimPermission#CONTAINER}, если у игрока еще нет доступа к контейнерам,
     *     ему выдается уровень {@link TrustLevel#CONTAINER}. Это не затронет игроков с более высокими правами (например, {@link TrustLevel#BUILD}).</li>
     * </ul>
     *
     * @param claimId        ID привата
     * @param playerUuid     UUID игрока
     * @param permission     Тип разрешения
     * @param state          Новое состояние (true - разрешить, false - запретить)
     */
    public void updatePlayerRole(UUID claimId, UUID playerUuid, ClaimPermission permission, boolean state) {
        if (claimId == null || playerUuid == null || permission == null) return;

        plugin.getClaimManager().getClaimById(claimId).ifPresent(claim -> {
            TrustLevel currentLevel = claim.getTrust(playerUuid);
            if (currentLevel == null) {
                currentLevel = TrustLevel.NONE;
            }

            if (!state && permission == ClaimPermission.BUILD) {
                // Запретили строить. Понижаем до CONTAINER.
                claim.setTrust(playerUuid, TrustLevel.CONTAINER);
                plugin.getStorage().saveMemberAsync(claim.getId(), playerUuid, TrustLevel.CONTAINER);
            } else if (state && permission == ClaimPermission.BUILD) {
                // Разрешили строить
                claim.setTrust(playerUuid, TrustLevel.BUILD);
                plugin.getStorage().saveMemberAsync(claim.getId(), playerUuid, TrustLevel.BUILD);
            } else if (!state && permission == ClaimPermission.CONTAINER) {
                // Запретили сундуки. Если строить тоже нельзя (уровень ниже BUILD) — удаляем из привата
                if (currentLevel.ordinal() < TrustLevel.BUILD.ordinal()) {
                    claim.removePlayer(playerUuid);
                    plugin.getStorage().removeMemberAsync(claim.getId(), playerUuid);
                }
            } else if (state && permission == ClaimPermission.CONTAINER) {
                // Разрешили сундуки. Если уровень ниже CONTAINER, ставим CONTAINER
                if (currentLevel.ordinal() < TrustLevel.CONTAINER.ordinal()) {
                    claim.setTrust(playerUuid, TrustLevel.CONTAINER);
                    plugin.getStorage().saveMemberAsync(claim.getId(), playerUuid, TrustLevel.CONTAINER);
                }
            }

            plugin.getStorage().saveClaimAsync(claim);
        });
    }

    /**
     * Динамическое управление ролями из других плагинов (например, Clans).
     * @deprecated Используйте {@link #updatePlayerRole(UUID, UUID, ClaimPermission, boolean)} вместо этого метода
     *
     * @param claimId        ID привата
     * @param playerUuid     UUID игрока
     * @param permissionType Тип разрешения (BUILD, CONTAINERS и т.д.)
     * @param state          Новое состояние
     */
    @Deprecated(forRemoval = true)
    public void updatePlayerRole(UUID claimId, UUID playerUuid, String permissionType, boolean state) {
        if (permissionType == null) return;

        ClaimPermission permission = null;
        try {
            permission = ClaimPermission.valueOf(permissionType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Игнорируем неверный тип разрешения
        }

        if (permission != null) {
            updatePlayerRole(claimId, playerUuid, permission, state);
        }
    }

    /**
     * Показать игроку визуальные границы вокруг BoundingBox.
     * Эта визуализация использует BlockDisplay entities и не конфликтует с блоками в мире.
     *
     * @param player Игрок, которому нужно показать границы
     * @param box BoundingBox (область), которую нужно подсветить
     * @param durationTicks Время отображения в тиках (20 тиков = 1 секунда)
     */
    public void showBorder(Player player, BoundingBox box, long durationTicks) {
        if (player == null || !player.isOnline() || box == null) return;
        me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, player, box, durationTicks);
    }

    /**
     * Принудительно скрыть визуальные границы для игрока.
     *
     * @param player Игрок
     */
    public void hideBorder(Player player) {
        if (player == null) return;
        me.lovelace.advancedclaims.task.BorderDisplayTask.hideBorder(player);
    }

    /**
     * Проверяет, пересекается ли переданный BoundingBox с каким-либо существующим приватом.
     * Это полезно для интеграции с другими плагинами (например, кланами),
     * чтобы убедиться, что их регион не накладывается на приват.
     *
     * @param world Мир
     * @param box   Границы для проверки
     * @return true если пересекается, false если свободно
     */
    public boolean checkOverlap(World world, BoundingBox box) {
        return plugin.getClaimManager().checkOverlap(world, box);
    }

    /**
     * Проверяет, пересекается ли переданный BoundingBox с каким-либо существующим приватом,
     * игнорируя указанный приват (полезно при расширении).
     *
     * @param world         Мир
     * @param box           Границы для проверки
     * @param ignoreClaimId ID привата, который нужно игнорировать
     * @return true если пересекается, false если свободно
     */
    public boolean checkOverlap(World world, BoundingBox box, UUID ignoreClaimId) {
        return plugin.getClaimManager().checkOverlap(world, box, ignoreClaimId);
    }

    /**
     * Устанавливает статус клановой территории для привата.
     * @param claimId ID привата
     * @param status true, если это клановая территория, false иначе
     */
    public void setClanTerritory(UUID claimId, boolean status) {
        plugin.getClaimManager().getClaimById(claimId).ifPresent(claim -> {
            claim.setClanTerritory(status);
            plugin.getStorage().saveClaimAsync(claim);
        });
    }

    /**
     * Устанавливает/снимает режим осады для привата.
     * @param claimId ID привата
     * @param active true, если режим осады активен, false иначе
     */
    public void setSiegeMode(UUID claimId, boolean active) {
        plugin.getClaimManager().getClaimById(claimId).ifPresent(claim -> {
            claim.setUnderSiege(active);
            plugin.getStorage().saveClaimAsync(claim);
        });
    }

    /**
     * Возвращает локацию блока-якоря (баннера) привата.
     * @param claimId ID привата
     * @return Локация якоря или null, если приват не найден
     */
    public Location getClaimAnchor(UUID claimId) {
        return plugin.getClaimManager().getClaimById(claimId)
                .map(Claim::getAnchorLocation)
                .orElse(null);
    }

    /**
     * Проверяет, находится ли указанная локация вне BoundingBox привата.
     * @param claimId ID привата
     * @param loc Локация для проверки
     * @return true, если локация вне привата, false если внутри или приват не найден
     */
    public boolean isOutsideClaim(UUID claimId, Location loc) {
        return plugin.getClaimManager().getClaimById(claimId)
                .map(claim -> !claim.getBoundingBox().contains(loc.getX(), loc.getY(), loc.getZ()))
                .orElse(true); // Если приват не найден, считаем, что локация "вне"
    }

    // ===== QUESTS API =====

    public Collection<Quest> getAllQuests() {
        return plugin.getQuestManager().getAllQuests();
    }

    public Quest getQuestById(String questId) {
        return plugin.getQuestManager().getQuestById(questId);
    }

    public List<Quest> getQuestsByTier(String tier) {
        return plugin.getQuestManager().getQuestsByTier(tier);
    }

    public List<Quest> getQuestsByCategory(String category) {
        return plugin.getQuestManager().getQuestsByCategory(category);
    }

    public List<Quest> getQuestsByDifficulty(Quest.Difficulty difficulty) {
        return plugin.getQuestManager().getQuestsByDifficulty(difficulty);
    }

    public List<Quest> getDailyQuests() {
        return plugin.getQuestManager().getDailyQuests();
    }

    public int getQuestProgress(OfflinePlayer player, String questId) {
        return plugin.getQuestManager().getUserData(player.getUniqueId()).getQuestProgress(questId);
    }

    public boolean isQuestCompleted(OfflinePlayer player, String questId) {
        return plugin.getQuestManager().getUserData(player.getUniqueId()).isQuestCompleted(questId);
    }

    public void addQuestProgress(OfflinePlayer player, Quest.QuestType type, String targetName, int amount) {
        plugin.getQuestManager().addProgress(player.getUniqueId(), type, targetName, amount);
    }

    public void addQuestProgressById(OfflinePlayer player, String questId, int amount) {
        Quest quest = getQuestById(questId);
        if (quest != null) {
            addQuestProgress(player, quest.type(), quest.targetName(), amount);
        }
    }

    // ===== USER DATA API =====

    public Optional<UserData> getUserData(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(plugin.getQuestManager().getUserData(player.getUniqueId()));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to get user data for " + player.getName(), e);
            return Optional.empty();
        }
    }

    public int getExpansionBlocks(OfflinePlayer player) {
        return getUserData(player).map(UserData::getExpansionBlocks).orElse(0);
    }

    public int getMemberLimit(OfflinePlayer player) {
        return getUserData(player).map(UserData::getBonusMemberLimit).orElse(0);
    }

    public boolean hasBuffUnlocked(OfflinePlayer player, String buffName) {
        return getUserData(player).map(data -> data.hasBuffUnlocked(buffName)).orElse(false);
    }

    // ===== RENTAL API =====

    public List<Claim> getAllRentalPlots() {
        return plugin.getClaimManager().getAllRentalPlots();
    }

    public Optional<Claim> getRentalPlotByName(String name) {
        UUID plotId = plugin.getRentalManager().getPlotIdByName(name);
        if (plotId != null) {
            return plugin.getClaimManager().getClaimById(plotId);
        }
        return Optional.empty();
    }

    public boolean isRented(Claim plot) {
        return plot.isRented();
    }

    public long getRentalEndTime(Claim plot) {
        return plot.getRentalEndTime();
    }

    public long getRentalPrice(Claim plot) {
        return plot.getRentalPrice();
    }

    // ===== EVENT API =====

    public void registerQuestProgressListener(QuestManager.QuestProgressListener listener) {
        plugin.getQuestManager().addProgressListener(listener);
    }

    public void unregisterQuestProgressListener(QuestManager.QuestProgressListener listener) {
        plugin.getQuestManager().removeProgressListener(listener);
    }

    // ===== ASYNC API =====

    public CompletableFuture<UserData> loadUserDataAsync(OfflinePlayer player) {
        return plugin.getStorage().loadUserData(player.getUniqueId());
    }

    public void saveUserDataAsync(OfflinePlayer player) {
        getUserData(player).ifPresent(data -> plugin.getStorage().saveUserDataAsync(data));
    }

    public CompletableFuture<Claim> loadClaimAsync(UUID claimId) {
        if (claimId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getStorage().loadClaimAsync(claimId);
    }

    public void saveClaimAsync(Claim claim) {
        plugin.getStorage().saveClaimAsync(claim);
    }
}