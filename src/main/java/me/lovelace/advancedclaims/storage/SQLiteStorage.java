package me.lovelace.advancedclaims.storage;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.ClaimFlag;
import me.lovelace.advancedclaims.model.IndicatorType;
import me.lovelace.advancedclaims.model.TrustLevel;
import me.lovelace.advancedclaims.model.UserData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

public class SQLiteStorage {
    private final AdvancedClaims plugin;
    private Connection claimsConnection;
    private Connection rentalsConnection;
    private final ExecutorService dbExecutor;

    private static final String CLAIMS_UPSERT =
            "INSERT INTO claims (id, world, min_x, min_y, min_z, max_x, max_y, max_z, owner_uuid, name, description, anchor_x, anchor_y, anchor_z, created_at, last_active, home_x, home_y, home_z) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(id) DO UPDATE SET " +
                    "world=excluded.world, min_x=excluded.min_x, min_y=excluded.min_y, min_z=excluded.min_z, " +
                    "max_x=excluded.max_x, max_y=excluded.max_y, max_z=excluded.max_z, " +
                    "owner_uuid=excluded.owner_uuid, name=excluded.name, description=excluded.description, " +
                    "anchor_x=excluded.anchor_x, anchor_y=excluded.anchor_y, anchor_z=excluded.anchor_z, " +
                    "last_active=excluded.last_active, home_x=excluded.home_x, home_y=excluded.home_y, home_z=excluded.home_z";

    private static final String RENTALS_UPSERT =
            "INSERT INTO rentals (id, world, min_x, min_y, min_z, max_x, max_y, max_z, owner_uuid, name, description, anchor_x, anchor_y, anchor_z, created_at, last_active, home_x, home_y, home_z, rental_price, rental_end_time, parent_claim_id, indicator_type, hologram_id, last_tax_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(id) DO UPDATE SET " +
                    "world=excluded.world, min_x=excluded.min_x, min_y=excluded.min_y, min_z=excluded.min_z, " +
                    "max_x=excluded.max_x, max_y=excluded.max_y, max_z=excluded.max_z, " +
                    "owner_uuid=excluded.owner_uuid, name=excluded.name, description=excluded.description, " +
                    "anchor_x=excluded.anchor_x, anchor_y=excluded.anchor_y, anchor_z=excluded.anchor_z, " +
                    "last_active=excluded.last_active, home_x=excluded.home_x, home_y=excluded.home_y, home_z=excluded.home_z, " +
                    "rental_price=excluded.rental_price, rental_end_time=excluded.rental_end_time, parent_claim_id=excluded.parent_claim_id, indicator_type=excluded.indicator_type, hologram_id=excluded.hologram_id, last_tax_time=excluded.last_tax_time";

    public SQLiteStorage(AdvancedClaims plugin) {
        this.plugin = plugin;
        this.dbExecutor = Executors.newCachedThreadPool();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public CompletableFuture<Void> initDatabase() {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Инициализация ClaimsSQLITE.db
                File claimsFile = new File(plugin.getDataFolder(), "storage/ClaimsSQLITE.db");
                if (!claimsFile.exists()) {
                    claimsFile.getParentFile().mkdirs();
                }
                claimsConnection = DriverManager.getConnection("jdbc:sqlite:" + claimsFile.getAbsolutePath());
                configureConnection(claimsConnection);
                initClaimsTables(claimsConnection);

                // 2. Инициализация RentalsSQLITE.db
                File rentalsFile = new File(plugin.getDataFolder(), "storage/RentalsSQLITE.db");
                if (!rentalsFile.exists()) {
                    rentalsFile.getParentFile().mkdirs();
                }
                rentalsConnection = DriverManager.getConnection("jdbc:sqlite:" + rentalsFile.getAbsolutePath());
                configureConnection(rentalsConnection);
                initRentalsTables(rentalsConnection);

            } catch (SQLException e) {
                throw new RuntimeException("DB Init failed", e);
            }
        }, dbExecutor);
    }

    private void configureConnection(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA temp_store = MEMORY;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    private void initClaimsTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS claims (id VARCHAR(36) PRIMARY KEY, world VARCHAR(64), min_x INT, min_y INT, min_z INT, max_x INT, max_y INT, max_z INT, owner_uuid VARCHAR(36), name VARCHAR(128), description TEXT, anchor_x INT, anchor_y INT, anchor_z INT, created_at BIGINT, last_active BIGINT, home_x INT, home_y INT, home_z INT);");
            stmt.execute("CREATE TABLE IF NOT EXISTS claim_members (claim_id VARCHAR(36), player_uuid VARCHAR(36), trust_level VARCHAR(32), PRIMARY KEY (claim_id, player_uuid), FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS claim_flags (claim_id VARCHAR(36), flag_name VARCHAR(64), state BOOLEAN, PRIMARY KEY (claim_id, flag_name), FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE);");
            
            // Таблицы пользователей (только в ClaimsDB)
            stmt.execute("CREATE TABLE IF NOT EXISTS users (uuid VARCHAR(36) PRIMARY KEY, expansion_blocks INT, bonus_members INT, bonus_slots INT, bonus_blocks INT);");
            stmt.execute("CREATE TABLE IF NOT EXISTS user_quests (uuid VARCHAR(36), quest_id VARCHAR(64), progress INT, completed BOOLEAN, PRIMARY KEY (uuid, quest_id), FOREIGN KEY(uuid) REFERENCES users(uuid) ON DELETE CASCADE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS user_buffs (uuid VARCHAR(36), buff_name VARCHAR(64), PRIMARY KEY (uuid, buff_name), FOREIGN KEY(uuid) REFERENCES users(uuid) ON DELETE CASCADE);");
        }
    }

    private void initRentalsTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS rentals (id VARCHAR(36) PRIMARY KEY, world VARCHAR(64), min_x INT, min_y INT, min_z INT, max_x INT, max_y INT, max_z INT, owner_uuid VARCHAR(36), name VARCHAR(128), description TEXT, anchor_x INT, anchor_y INT, anchor_z INT, created_at BIGINT, last_active BIGINT, home_x INT, home_y INT, home_z INT, rental_price BIGINT DEFAULT 0, rental_end_time BIGINT DEFAULT 0, parent_claim_id VARCHAR(36), indicator_type VARCHAR(32) DEFAULT 'NONE', hologram_id VARCHAR(64), last_tax_time BIGINT DEFAULT 0);");
            stmt.execute("CREATE TABLE IF NOT EXISTS rental_members (rental_id VARCHAR(36), player_uuid VARCHAR(36), trust_level VARCHAR(32), PRIMARY KEY (rental_id, player_uuid), FOREIGN KEY(rental_id) REFERENCES rentals(id) ON DELETE CASCADE);");
        }
    }

    public CompletableFuture<List<Claim>> loadAllClaims() {
        return CompletableFuture.supplyAsync(() -> {
            List<Claim> allClaims = new ArrayList<>();
            allClaims.addAll(loadClaimsFromDB(claimsConnection, "claims", false));
            allClaims.addAll(loadClaimsFromDB(rentalsConnection, "rentals", true));
            return allClaims;
        }, dbExecutor);
    }

    private List<Claim> loadClaimsFromDB(Connection conn, String tableName, boolean isRental) {
        Map<UUID, Claim> claimsMap = new HashMap<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {
            while (rs.next()) {
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) continue;

                UUID id = UUID.fromString(rs.getString("id"));
                BoundingBox box = new BoundingBox(rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"), rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));
                String ownerUuidStr = rs.getString("owner_uuid");
                UUID ownerUuid = ownerUuidStr != null ? UUID.fromString(ownerUuidStr) : null;

                Claim claim = new Claim(id, world, box, ownerUuid, new Location(world, rs.getInt("anchor_x"), rs.getInt("anchor_y"), rs.getInt("anchor_z")));

                String name = rs.getString("name"); if (name != null) claim.setName(name);
                String desc = rs.getString("description"); if (desc != null) claim.setDescription(desc);

                int hx = rs.getInt("home_x");
                if (!rs.wasNull()) claim.setHomeLocation(new Location(world, hx, rs.getInt("home_y"), rs.getInt("home_z")));

                if (isRental) {
                    claim.setRentalPrice(rs.getLong("rental_price"));
                    claim.setRentalEndTime(rs.getLong("rental_end_time"));
                    String parentId = rs.getString("parent_claim_id");
                    if (parentId != null) claim.setParentClaimId(UUID.fromString(parentId));
                    claim.setIndicatorType(IndicatorType.valueOf(rs.getString("indicator_type")));
                    claim.setHologramId(rs.getString("hologram_id"));
                    claim.setLastTaxTime(rs.getLong("last_tax_time"));
                }

                claimsMap.put(id, claim);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading from " + tableName + ": " + e.getMessage());
        }

        String memberTable = isRental ? "rental_members" : "claim_members";
        String idColumn = isRental ? "rental_id" : "claim_id";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM " + memberTable)) {
            while (rs.next()) {
                Claim claim = claimsMap.get(UUID.fromString(rs.getString(idColumn)));
                if (claim != null) {
                    claim.getMembers().put(UUID.fromString(rs.getString("player_uuid")), TrustLevel.valueOf(rs.getString("trust_level")));
                }
            }
        } catch (SQLException e) { plugin.getLogger().severe("Error loading members (" + tableName + "): " + e.getMessage()); }

        if (!isRental) {
            String flagTable = "claim_flags";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM " + flagTable)) {
                while (rs.next()) {
                    Claim claim = claimsMap.get(UUID.fromString(rs.getString(idColumn)));
                    if (claim != null) {
                        try { claim.setFlag(ClaimFlag.valueOf(rs.getString("flag_name")), rs.getInt("state") == 1); } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("Error loading flags (" + tableName + "): " + e.getMessage()); }
        }

        return new ArrayList<>(claimsMap.values());
    }

    public void saveClaimAsync(Claim claim) {
        CompletableFuture.runAsync(() -> {
            boolean isRental = claim.isRentalPlot();
            Connection conn = isRental ? rentalsConnection : claimsConnection;
            String sql = isRental ? RENTALS_UPSERT : CLAIMS_UPSERT;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                prepareClaimStatement(ps, claim, isRental);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving claim: " + e.getMessage());
            }
        }, dbExecutor);
    }

    public void batchSaveAllAsync(java.util.Collection<Claim> claims) {
        dbExecutor.execute(() -> {
            saveBatch(claimsConnection, CLAIMS_UPSERT, claims.stream().filter(c -> !c.isRentalPlot()).toList(), false);
            saveBatch(rentalsConnection, RENTALS_UPSERT, claims.stream().filter(Claim::isRentalPlot).toList(), true);
        });
    }

    private void saveBatch(Connection conn, String sql, List<Claim> claims, boolean isRental) {
        if (claims.isEmpty() || conn == null) return;
        try {
            if (conn.isClosed()) return;
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0;
                for (Claim claim : claims) {
                    if (!claim.isModified() || claim.getWorld() == null) continue;
                    prepareClaimStatement(ps, claim, isRental);
                    ps.addBatch();
                    claim.setModified(false);
                    count++;
                }
                if (count > 0) ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Batch save error: " + e.getMessage());
        } finally {
            try { if (conn != null) conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void saveAllClaimsSync(Collection<Claim> claims) {
        saveBatchSync(claimsConnection, CLAIMS_UPSERT, claims.stream().filter(c -> !c.isRentalPlot()).toList(), false);
        saveBatchSync(rentalsConnection, RENTALS_UPSERT, claims.stream().filter(Claim::isRentalPlot).toList(), true);
    }

    private void saveBatchSync(Connection conn, String sql, List<Claim> claims, boolean isRental) {
        if (claims.isEmpty() || conn == null) return;
        try {
            if (conn.isClosed()) return;
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Claim claim : claims) {
                    if (claim.getWorld() == null) continue;
                    prepareClaimStatement(ps, claim, isRental);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().severe("Sync save error: " + e.getMessage());
        }
    }

    private void prepareClaimStatement(PreparedStatement ps, Claim claim, boolean isRental) throws SQLException {
        ps.setString(1, claim.getId().toString());
        ps.setString(2, claim.getWorld().getName());
        ps.setInt(3, (int) claim.getBoundingBox().getMinX());
        ps.setInt(4, (int) claim.getBoundingBox().getMinY());
        ps.setInt(5, (int) claim.getBoundingBox().getMinZ());
        ps.setInt(6, (int) claim.getBoundingBox().getMaxX());
        ps.setInt(7, (int) claim.getBoundingBox().getMaxY());
        ps.setInt(8, (int) claim.getBoundingBox().getMaxZ());
        ps.setString(9, claim.getOwnerUuid() != null ? claim.getOwnerUuid().toString() : null);
        ps.setString(10, claim.getName());
        ps.setString(11, claim.getDescription());
        ps.setInt(12, claim.getAnchorLocation().getBlockX());
        ps.setInt(13, claim.getAnchorLocation().getBlockY());
        ps.setInt(14, claim.getAnchorLocation().getBlockZ());
        ps.setLong(15, System.currentTimeMillis()); // CreatedAt (не храним в памяти, но в базе нужно)
        ps.setLong(16, claim.getLastActive());
        
        Location home = claim.getHomeLocation();
        if (home != null) {
            ps.setInt(17, home.getBlockX());
            ps.setInt(18, home.getBlockY());
            ps.setInt(19, home.getBlockZ());
        } else {
            ps.setNull(17, Types.INTEGER);
            ps.setNull(18, Types.INTEGER);
            ps.setNull(19, Types.INTEGER);
        }

        if (isRental) {
            ps.setLong(20, claim.getRentalPrice());
            ps.setLong(21, claim.getRentalEndTime());
            ps.setString(22, claim.getParentClaimId() != null ? claim.getParentClaimId().toString() : null);
            ps.setString(23, claim.getIndicatorType().name());
            ps.setString(24, claim.getHologramId());
            ps.setLong(25, claim.getLastTaxTime());
        }
    }

    public void saveMemberAsync(UUID claimId, UUID playerUuid, TrustLevel level) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        Connection conn = isRental ? rentalsConnection : claimsConnection;
        String table = isRental ? "rental_members" : "claim_members";
        String idCol = isRental ? "rental_id" : "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO " + table + " (" + idCol + ", player_uuid, trust_level) VALUES (?, ?, ?)")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, playerUuid.toString());
                ps.setString(3, level.name());
                ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        }, dbExecutor);
    }

    public void removeMemberAsync(UUID claimId, UUID playerUuid) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        Connection conn = isRental ? rentalsConnection : claimsConnection;
        String table = isRental ? "rental_members" : "claim_members";
        String idCol = isRental ? "rental_id" : "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE " + idCol + " = ? AND player_uuid = ?")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        }, dbExecutor);
    }

    public void saveFlagAsync(UUID claimId, ClaimFlag flag, boolean state) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        if (isRental) return;
        
        Connection conn = claimsConnection;
        String table = "claim_flags";
        String idCol = "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO " + table + " (" + idCol + ", flag_name, state) VALUES (?, ?, ?)")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, flag.name());
                ps.setInt(3, state ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        }, dbExecutor);
    }

    // Удаление привата (БЕЗ сохранения флагов, если не переезд)
    // Если нужно сохранить флаги при move, это делается отдельным копированием перед удалением.
    // Здесь мы просто чистим всё.
    public void deleteClaimAsync(UUID claimId) {
        boolean isRental = plugin.getClaimManager().getClaimById(claimId).map(Claim::isRentalPlot).orElse(false);
        Connection conn = isRental ? rentalsConnection : claimsConnection;
        String mainTable = isRental ? "rentals" : "claims";
        String memberTable = isRental ? "rental_members" : "claim_members";
        String idCol = isRental ? "rental_id" : "claim_id";

        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + memberTable + " WHERE " + idCol + " = ?")) {
                ps.setString(1, claimId.toString());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
            
            if (!isRental) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM claim_flags WHERE claim_id = ?")) {
                    ps.setString(1, claimId.toString());
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
            }
            
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + mainTable + " WHERE id = ?")) {
                ps.setString(1, claimId.toString());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public CompletableFuture<UserData> loadUserData(UUID uuid) {
        // Данные игроков храним ТОЛЬКО в ClaimsSQLITE.db
        return CompletableFuture.supplyAsync(() -> {
            UserData data = new UserData(uuid);
            try (PreparedStatement ps = claimsConnection.prepareStatement("SELECT * FROM users WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setExpansionBlocks(rs.getInt("expansion_blocks"));
                        data.setBonusMemberLimit(rs.getInt("bonus_members"));
                        data.setBonusClaimSlots(rs.getInt("bonus_slots"));
                        data.setBonusBlocks(rs.getInt("bonus_blocks"));
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
            try (PreparedStatement ps = claimsConnection.prepareStatement("SELECT * FROM user_quests WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String qId = rs.getString("quest_id");
                        data.setQuestProgress(qId, rs.getInt("progress"));
                        data.setQuestCompleted(qId, rs.getBoolean("completed"));
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
            try (PreparedStatement ps = claimsConnection.prepareStatement("SELECT * FROM user_buffs WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        data.unlockBuff(rs.getString("buff_name"));
                    }
                }
            } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
            return data;
        }, dbExecutor);
    }

    public void saveUserDataAsync(UserData data) {
        boolean useSync = false;
        try {
            if (dbExecutor.isShutdown() || claimsConnection == null || claimsConnection.isClosed()) {
                useSync = true;
            }
        } catch (SQLException e) {
            useSync = true;
        }

        if (useSync) {
            saveUserDataSync(data);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                if (claimsConnection == null || claimsConnection.isClosed()) return;
            } catch (SQLException e) {
                return;
            }

            try (PreparedStatement ps = claimsConnection.prepareStatement("INSERT OR REPLACE INTO users (uuid, expansion_blocks, bonus_members, bonus_slots, bonus_blocks) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, data.getUuid().toString());
                ps.setInt(2, data.getExpansionBlocks());
                ps.setInt(3, data.getBonusMemberLimit());
                ps.setInt(4, data.getBonusClaimSlots());
                ps.setInt(5, data.getBonusBlocks());
                ps.executeUpdate();
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().contains("closed")) plugin.getLogger().severe("SQL Error: " + e.getMessage());
            }

            try (PreparedStatement ps = claimsConnection.prepareStatement("INSERT OR REPLACE INTO user_quests (uuid, quest_id, progress, completed) VALUES (?, ?, ?, ?) ")) {
                for (Map.Entry<String, Integer> entry : data.getProgressMap().entrySet()) {
                    ps.setString(1, data.getUuid().toString());
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, entry.getValue());
                    ps.setBoolean(4, data.isQuestCompleted(entry.getKey()));
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().contains("closed")) plugin.getLogger().severe("SQL Error: " + e.getMessage());
            }
        }, dbExecutor);
    }

    public void saveUserDataSync(UserData data) {
        try {
            if (claimsConnection == null || claimsConnection.isClosed()) return;
        } catch (SQLException e) {
            return;
        }

        try (PreparedStatement ps = claimsConnection.prepareStatement("INSERT OR REPLACE INTO users (uuid, expansion_blocks, bonus_members, bonus_slots, bonus_blocks) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, data.getUuid().toString());
            ps.setInt(2, data.getExpansionBlocks());
            ps.setInt(3, data.getBonusMemberLimit());
            ps.setInt(4, data.getBonusClaimSlots());
            ps.setInt(5, data.getBonusBlocks());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        try (PreparedStatement ps = claimsConnection.prepareStatement("INSERT OR REPLACE INTO user_quests (uuid, quest_id, progress, completed) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<String, Integer> entry : data.getProgressMap().entrySet()) {
                ps.setString(1, data.getUuid().toString());
                ps.setString(2, entry.getKey());
                ps.setInt(3, entry.getValue());
                ps.setBoolean(4, data.isQuestCompleted(entry.getKey()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
        try (PreparedStatement ps = claimsConnection.prepareStatement("INSERT OR IGNORE INTO user_buffs (uuid, buff_name) VALUES (?, ?)")) {
            for (String buff : data.getUnlockedBuffs()) {
                ps.setString(1, data.getUuid().toString());
                ps.setString(2, buff);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) { plugin.getLogger().severe("SQL Error: " + e.getMessage()); }
    }

    public void saveAllUserDataSync(java.util.Collection<me.lovelace.advancedclaims.model.UserData> allData) {
        if (claimsConnection == null) return;

        try {
            claimsConnection.setAutoCommit(false);
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to start transaction: " + ex.getMessage());
            return;
        }

        try (PreparedStatement psUser = claimsConnection.prepareStatement("INSERT OR REPLACE INTO users (uuid, expansion_blocks, bonus_members, bonus_slots, bonus_blocks) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement psQuest = claimsConnection.prepareStatement("INSERT OR REPLACE INTO user_quests (uuid, quest_id, progress, completed) VALUES (?, ?, ?, ?)");
             PreparedStatement psBuff = claimsConnection.prepareStatement("INSERT OR IGNORE INTO user_buffs (uuid, buff_name) VALUES (?, ?)")) {

            for (me.lovelace.advancedclaims.model.UserData data : allData) {
                psUser.setString(1, data.getUuid().toString());
                psUser.setInt(2, data.getExpansionBlocks());
                psUser.setInt(3, data.getBonusMemberLimit());
                psUser.setInt(4, data.getBonusClaimSlots());
                psUser.setInt(5, data.getBonusBlocks());
                psUser.addBatch();

                for (java.util.Map.Entry<String, Integer> entry : data.getProgressMap().entrySet()) {
                    psQuest.setString(1, data.getUuid().toString());
                    psQuest.setString(2, entry.getKey());
                    psQuest.setInt(3, entry.getValue());
                    psQuest.setBoolean(4, data.isQuestCompleted(entry.getKey()));
                    psQuest.addBatch();
                }

                for (String buff : data.getUnlockedBuffs()) {
                    psBuff.setString(1, data.getUuid().toString());
                    psBuff.setString(2, buff);
                    psBuff.addBatch();
                }
            }

            psUser.executeBatch();
            psQuest.executeBatch();
            psBuff.executeBatch();
            claimsConnection.commit();

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save user data synchronously! " + e.getMessage());
            try {
                claimsConnection.rollback();
            } catch (SQLException rollbackEx) {
                plugin.getLogger().severe("Rollback failed: " + rollbackEx.getMessage());
            }
        } finally {
            try {
                claimsConnection.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                plugin.getLogger().severe("AutoCommit reset failed: " + autoCommitEx.getMessage());
            }
        }
    }

    public void close() {
        try { if (claimsConnection != null) claimsConnection.close(); } catch (SQLException ignored) {}
        try { if (rentalsConnection != null) rentalsConnection.close(); } catch (SQLException ignored) {}
        dbExecutor.shutdownNow();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Database executor did not terminate in time!");
            }
        } catch (InterruptedException e) {
            plugin.getLogger().severe("Interrupted waiting for DB shutdown");
        }
    }
}