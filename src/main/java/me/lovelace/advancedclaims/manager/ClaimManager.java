package me.lovelace.advancedclaims.manager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Caffeine cache imports
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

public class ClaimManager {
    private final AdvancedClaims plugin;
    private final Map<UUID, Claim> claimsById = new ConcurrentHashMap<>();
    private final Map<UUID, Long2ObjectOpenHashMap<List<Claim>>> worldCaches = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // ===== ПОСТОЯННЫЕ КЭШИ (ConcurrentHashMap + CopyOnWriteArrayList) =====
    // Кэш: UUID владельца → Список его приватов
    private final Map<UUID, List<Claim>> claimsByOwner = new ConcurrentHashMap<>();
    // Кэш: UUID игрока → Список приватов где есть доступ
    private final Map<UUID, List<Claim>> claimsByPlayer = new ConcurrentHashMap<>();
    // Кэш арендных приватов
    private final List<Claim> rentalPlotsCache = new CopyOnWriteArrayList<>();
    
    // ===== ВРЕМЕННЫЕ КЭШИ (Caffeine) =====
    // Кэш профилей UserData (истекает через 10 минут после последнего доступа)
    private final Cache<UUID, me.lovelace.advancedclaims.model.UserData> userDataCache;
    // Кэш прогресса квестов (истекает через 5 минут)
    private final Cache<UUID, Map<String, Integer>> questProgressCache;
    // Кэш кулдаунов команд (истекает через 1 минуту)
    private final Cache<UUID, Long> commandCooldownsCache;
    // Кэш откатов действий (истекает через 15 минут)
    private final Cache<UUID, List<Claim>> rollbackCache;
    
    public ClaimManager(AdvancedClaims plugin) {
        this.plugin = plugin;
        
        // Инициализация Caffeine кэшей
        this.userDataCache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .recordStats()
            .build();
        
        this.questProgressCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(50000)
            .recordStats()
            .build();
        
        this.commandCooldownsCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .maximumSize(100000)
            .recordStats()
            .build();
        
        this.rollbackCache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(1000)
            .recordStats()
            .build();
            
        plugin.getLogger().info("Caffeine caches initialized!");
    }

    public void loadClaims(Collection<Claim> loadedClaims) {
        lock.writeLock().lock();
        try {
            claimsById.clear();
            worldCaches.clear();
            claimsByOwner.clear();
            claimsByPlayer.clear();
            rentalPlotsCache.clear();
            
            for (Claim claim : loadedClaims) {
                addClaimToCacheInternal(claim);
            }
            
            // Ребилд кэша игроков после загрузки всех приватов
            rebuildPlayerClaimsCache();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addClaimToCache(Claim claim) {
        lock.writeLock().lock();
        try {
            addClaimToCacheInternal(claim);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addClaimToCacheInternal(Claim claim) {
        if (claim.getWorld() == null) return;
        claimsById.put(claim.getId(), claim);
        
        // Обновляем кэш владельца
        if (claim.getOwnerUuid() != null) {
            claimsByOwner.computeIfAbsent(claim.getOwnerUuid(), k -> new CopyOnWriteArrayList<>()).add(claim);
        }
        
        // Обновляем кэш арендных приватов
        if (claim.isRentalPlot()) {
            rentalPlotsCache.add(claim);
        }
        
        // Ребилд кэша игроков (медленно, но надежно)
        rebuildPlayerClaimsCache();

        Long2ObjectOpenHashMap<List<Claim>> chunkMap = worldCaches.computeIfAbsent(
                claim.getWorld().getUID(),
                k -> new Long2ObjectOpenHashMap<>()
        );

        BoundingBox box = claim.getBoundingBox();
        int minCX = (int) Math.floor(box.getMinX()) >> 4;
        int maxCX = (int) Math.floor(box.getMaxX()) >> 4;
        int minCZ = (int) Math.floor(box.getMinZ()) >> 4;
        int maxCZ = (int) Math.floor(box.getMaxZ()) >> 4;

        for (int x = minCX; x <= maxCX; x++) {
            for (int z = minCZ; z <= maxCZ; z++) {
                long chunkKey = getChunkKey(x, z);
                chunkMap.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(claim);
            }
        }
    }
    
    /**
     * Перестроить кэш приватов по игрокам.
     * Вызывается при загрузке всех приватов.
     */
    private void rebuildPlayerClaimsCache() {
        claimsByPlayer.clear();
        for (Claim claim : claimsById.values()) {
            // Добавляем владельца
            if (claim.getOwnerUuid() != null) {
                claimsByPlayer.computeIfAbsent(claim.getOwnerUuid(), k -> new CopyOnWriteArrayList<>()).add(claim);
            }
            // Добавляем участников
            for (UUID memberId : claim.getMembers().keySet()) {
                claimsByPlayer.computeIfAbsent(memberId, k -> new CopyOnWriteArrayList<>()).add(claim);
            }
        }
    }

    public void removeClaimFromCache(UUID claimId) {
        lock.writeLock().lock();
        try {
            Claim claim = claimsById.remove(claimId);
            if (claim == null || claim.getWorld() == null) return;

            // ===== ИСПРАВЛЕНИЕ: Удаляем из всех кэшей =====
            // Удаляем из кэша владельца
            if (claim.getOwnerUuid() != null) {
                List<Claim> ownerClaims = claimsByOwner.get(claim.getOwnerUuid());
                if (ownerClaims != null) {
                    ownerClaims.remove(claim);
                    if (ownerClaims.isEmpty()) {
                        claimsByOwner.remove(claim.getOwnerUuid());
                    }
                }
            }

            // Удаляем из кэша игроков (владелец + все участники)
            updatePlayerClaimsCache(claim.getOwnerUuid());
            for (UUID memberId : claim.getMembers().keySet()) {
                updatePlayerClaimsCache(memberId);
            }

            // Удаляем из кэша арендных приватов
            if (claim.isRentalPlot()) {
                rentalPlotsCache.remove(claim);
            }
            // ===== КОНЕЦ ИСПРАВЛЕНИЯ =====

            Long2ObjectOpenHashMap<List<Claim>> chunkMap = worldCaches.get(claim.getWorld().getUID());
            if (chunkMap == null) return;

            BoundingBox box = claim.getBoundingBox();
            int minCX = (int) Math.floor(box.getMinX()) >> 4;
            int maxCX = (int) Math.floor(box.getMaxX()) >> 4;
            int minCZ = (int) Math.floor(box.getMinZ()) >> 4;
            int maxCZ = (int) Math.floor(box.getMaxZ()) >> 4;

            for (int x = minCX; x <= maxCX; x++) {
                for (int z = minCZ; z <= maxCZ; z++) {
                    long chunkKey = getChunkKey(x, z);
                    List<Claim> list = chunkMap.get(chunkKey);
                    if (list != null) {
                        list.remove(claim);
                        if (list.isEmpty()) {
                            chunkMap.remove(chunkKey);
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Collection<Claim> getAllClaims() {
        return Collections.unmodifiableCollection(claimsById.values());
    }

    public Optional<Claim> getClaimAt(Location loc) {
        World world = loc.getWorld();
        if (world == null) return Optional.empty();
        long chunkKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);

        lock.readLock().lock();
        try {
            Long2ObjectOpenHashMap<List<Claim>> chunkMap = worldCaches.get(world.getUID());
            if (chunkMap == null) return Optional.empty();

            List<Claim> claimsInChunk = chunkMap.get(chunkKey);
            if (claimsInChunk != null) {
                Claim foundParent = null;
                for (Claim claim : claimsInChunk) {
                    // Используем координаты напрямую вместо toVector()
                    if (claim.getBoundingBox().contains(loc.getX(), loc.getY(), loc.getZ())) {
                        if (claim.isRentalPlot()) {
                            return Optional.of(claim);
                        } else {
                            foundParent = claim;
                        }
                    }
                }
                if (foundParent != null) return Optional.of(foundParent);
            }
        } finally {
            lock.readLock().unlock();
        }
        return Optional.empty();
    }

    public Optional<Claim> getClaimById(UUID id) {
        return Optional.ofNullable(claimsById.get(id));
    }

    public boolean checkOverlap(World world, BoundingBox box) {
        return checkOverlap(world, box, null);
    }

    public boolean checkOverlap(World world, BoundingBox box, UUID ignoreClaimId) {
        if (world == null) return false;
        int minCX = (int) Math.floor(box.getMinX()) >> 4;
        int maxCX = (int) Math.floor(box.getMaxX()) >> 4;
        int minCZ = (int) Math.floor(box.getMinZ()) >> 4;
        int maxCZ = (int) Math.floor(box.getMaxZ()) >> 4;

        lock.readLock().lock();
        try {
            Long2ObjectOpenHashMap<List<Claim>> chunkMap = worldCaches.get(world.getUID());
            if (chunkMap == null) return false;

            for (int x = minCX; x <= maxCX; x++) {
                for (int z = minCZ; z <= maxCZ; z++) {
                    long chunkKey = getChunkKey(x, z);
                    List<Claim> claimsInChunk = chunkMap.get(chunkKey);
                    if (claimsInChunk != null) {
                        for (Claim claim : claimsInChunk) {
                            if (!Objects.equals(claim.getId(), ignoreClaimId) && claim.getBoundingBox().overlaps(box)) {
                                return true;
                            }
                        }
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return false;
    }

    private long getChunkKey(int x, int z) {
        return ((long) x & 0xffffffffL) | (((long) z & 0xffffffffL) << 32);
    }

    // ===== МЕТОДЫ ДЛЯ КЭШЕЙ (Производительность O(1)) =====
    
    /**
     * Получить все приваты владельца.
     * @param ownerUuid UUID владельца
     * @return Список приватов (пустой если нет)
     */
    public List<Claim> getClaimsByOwner(UUID ownerUuid) {
        if (ownerUuid == null) return Collections.emptyList();
        return Collections.unmodifiableList(claimsByOwner.getOrDefault(ownerUuid, Collections.emptyList()));
    }
    
    /**
     * Получить все приваты где игрок имеет доступ.
     * @param playerUuid UUID игрока
     * @return Список приватов (пустой если нет)
     */
    public List<Claim> getClaimsByPlayer(UUID playerUuid) {
        if (playerUuid == null) return Collections.emptyList();
        return Collections.unmodifiableList(claimsByPlayer.getOrDefault(playerUuid, Collections.emptyList()));
    }
    
    /**
     * Получить все арендные плоты.
     * @return Список арендных приватов
     */
    public List<Claim> getAllRentalPlots() {
        return Collections.unmodifiableList(rentalPlotsCache);
    }
    
    /**
     * Обновить кэш игрока (при изменении прав доступа).
     * @param playerUuid UUID игрока
     */
    public void updatePlayerClaimsCache(UUID playerUuid) {
        claimsByPlayer.remove(playerUuid);
        List<Claim> playerClaims = new ArrayList<>();
        for (Claim claim : claimsById.values()) {
            if (claim.getOwnerUuid() != null && claim.getOwnerUuid().equals(playerUuid)) {
                playerClaims.add(claim);
            } else if (claim.getMembers().containsKey(playerUuid)) {
                playerClaims.add(claim);
            }
        }
        if (!playerClaims.isEmpty()) {
            claimsByPlayer.put(playerUuid, new CopyOnWriteArrayList<>(playerClaims));
        }
    }

    // ===== МЕТОДЫ ДЛЯ CAFFEINE КЭШЕЙ =====
    
    /**
     * Получить UserData из кэша.
     * @param uuid UUID игрока
     * @return UserData или null
     */
    public me.lovelace.advancedclaims.model.UserData getCachedUserData(UUID uuid) {
        return userDataCache.getIfPresent(uuid);
    }
    
    /**
     * Кэшировать UserData.
     * @param uuid UUID игрока
     * @param data UserData
     */
    public void cacheUserData(UUID uuid, me.lovelace.advancedclaims.model.UserData data) {
        if (data != null) {
            userDataCache.put(uuid, data);
        }
    }
    
    /**
     * Получить прогресс квеста из кэша.
     * @param uuid UUID игрока
     * @param questId ID квеста
     * @return Прогресс или 0
     */
    public int getCachedQuestProgress(UUID uuid, String questId) {
        Map<String, Integer> progress = questProgressCache.getIfPresent(uuid);
        return progress != null ? progress.getOrDefault(questId, 0) : 0;
    }
    
    /**
     * Обновить прогресс квеста в кэше.
     * @param uuid UUID игрока
     * @param questId ID квеста
     * @param progress Прогресс
     */
    public void updateQuestProgress(UUID uuid, String questId, int progress) {
        Map<String, Integer> playerProgress = questProgressCache.get(uuid, k -> new ConcurrentHashMap<>());
        playerProgress.put(questId, progress);
        questProgressCache.put(uuid, playerProgress);
    }
    
    /**
     * Проверить кулдаун команды.
     * @param uuid UUID игрока
     * @param command Команда
     * @param cooldownMs Кулдаун в мс
     * @return true если на кулдауне
     */
    public boolean isOnCooldown(UUID uuid, String command, long cooldownMs) {
        Long lastUse = commandCooldownsCache.getIfPresent(uuid);
        if (lastUse == null) return false;
        
        long elapsed = System.currentTimeMillis() - lastUse;
        return elapsed < cooldownMs;
    }
    
    /**
     * Установить кулдаун команды.
     * @param uuid UUID игрока
     * @param command Команда
     */
    public void setCooldown(UUID uuid, String command) {
        commandCooldownsCache.put(uuid, System.currentTimeMillis());
    }
    
    /**
     * Получить данные для отката из кэша.
     * @param uuid UUID игрока
     * @return Список приватов для отката
     */
    public List<Claim> getRollbackData(UUID uuid) {
        return rollbackCache.getIfPresent(uuid);
    }
    
    /**
     * Установить данные для отката.
     * @param uuid UUID игрока
     * @param claims Список приватов
     */
    public void setRollbackData(UUID uuid, List<Claim> claims) {
        if (claims != null && !claims.isEmpty()) {
            rollbackCache.put(uuid, new CopyOnWriteArrayList<>(claims));
        }
    }
    
    /**
     * Очистить все кэши Caffeine.
     */
    public void invalidateAllCaches() {
        userDataCache.invalidateAll();
        questProgressCache.invalidateAll();
        commandCooldownsCache.invalidateAll();
        rollbackCache.invalidateAll();
    }
    
    /**
     * Получить статистику кэшей.
     * @return Строка со статистикой
     */
    public String getCacheStats() {
        return String.format(
            "UserData: %d | Quests: %d | Cooldowns: %d | Rollback: %d",
            userDataCache.estimatedSize(),
            questProgressCache.estimatedSize(),
            commandCooldownsCache.estimatedSize(),
            rollbackCache.estimatedSize()
        );
    }

    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

    public void addInvite(UUID player, UUID claimId) {
        pendingInvites.put(player, claimId);
    }

    public UUID getInvite(UUID player) {
        return pendingInvites.get(player);
    }

    public void removeInvite(UUID player) {
        pendingInvites.remove(player);
    }
}