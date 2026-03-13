package me.lovelace.advancedclaims.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основная модель привата с полной поддержкой Paper API 1.21.11.
 * Все геттеры/setтеры имеют правильную обработку модифицированного состояния.
 */
public class Claim {
    private final UUID id;
    private final World world;
    private BoundingBox boundingBox;
    private UUID ownerUuid;
    private final Location anchorLocation;
    private String name;
    private String description;
    private Location homeLocation;
    private long lastActive;
    private final Map<UUID, TrustLevel> members = new ConcurrentHashMap<>();
    private final Map<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);

    // Rental fields
    private long rentalPrice = 0;
    private long rentalEndTime = 0;
    private UUID parentClaimId = null;
    private IndicatorType indicatorType = IndicatorType.NONE;
    private String hologramId = null;
    private long lastTaxTime = 0;

    private transient boolean modified = true;

    public Claim(UUID id, World world, BoundingBox boundingBox, UUID ownerUuid, Location anchorLocation) {
        this.id = id;
        this.world = world;
        this.boundingBox = boundingBox;
        this.ownerUuid = ownerUuid;
        this.anchorLocation = anchorLocation;
        this.lastActive = System.currentTimeMillis();

        if (ownerUuid != null) {
            try {
                this.name = "Приват " + org.bukkit.Bukkit.getOfflinePlayer(ownerUuid).getName();
            } catch (Exception e) {
                this.name = "Приват неизвестного";
            }
        } else {
            this.name = "Аренда";
        }

        for (ClaimFlag flag : ClaimFlag.values()) {
            flags.put(flag, flag.getDefaultState());
        }
    }

    // Getters
    public UUID getId() { return id; }
    public World getWorld() { return world; }
    public BoundingBox getBoundingBox() { return boundingBox; }

    public void setBoundingBox(BoundingBox box) {
        this.boundingBox = box;
        this.modified = true;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        this.modified = true;
    }

    public Location getAnchorLocation() { return anchorLocation; }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.modified = true;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        this.modified = true;
    }

    public Location getHomeLocation() {
        return homeLocation == null ? anchorLocation : homeLocation;
    }

    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
        this.modified = true;
    }

    public long getLastActive() { return lastActive; }
    public void updateLastActive() {
        this.lastActive = System.currentTimeMillis();
        this.modified = true;
    }

    public void setTrust(UUID player, TrustLevel level) {
        members.put(player, level);
        this.modified = true;
    }

    public TrustLevel getTrust(UUID player) {
        if (player.equals(ownerUuid)) return TrustLevel.OWNER;
        return members.getOrDefault(player, TrustLevel.NONE);
    }

    public Map<UUID, TrustLevel> getMembers() { return members; }

    public boolean getFlag(ClaimFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultState());
    }

    public void setFlag(ClaimFlag flag, boolean state) {
        flags.put(flag, state);
        this.modified = true;
    }

    public Map<ClaimFlag, Boolean> getFlags() { return flags; }

    public boolean overlaps(BoundingBox other) {
        return this.boundingBox.overlaps(other);
    }

    public boolean isModified() { return modified; }
    public void setModified(boolean modified) { this.modified = modified; }

    // Rental Getters/Setters
    public long getRentalPrice() { return rentalPrice; }
    public void setRentalPrice(long rentalPrice) {
        this.rentalPrice = rentalPrice;
        this.modified = true;
    }

    public long getRentalEndTime() { return rentalEndTime; }
    public void setRentalEndTime(long rentalEndTime) {
        this.rentalEndTime = rentalEndTime;
        this.modified = true;
    }

    public UUID getParentClaimId() { return parentClaimId; }
    public void setParentClaimId(UUID parentClaimId) {
        this.parentClaimId = parentClaimId;
        this.modified = true;
    }

    public IndicatorType getIndicatorType() { return indicatorType; }
    public void setIndicatorType(IndicatorType indicatorType) {
        this.indicatorType = indicatorType;
        this.modified = true;
    }

    public String getHologramId() { return hologramId; }
    public void setHologramId(String hologramId) {
        this.hologramId = hologramId;
        this.modified = true;
    }

    public boolean isRentalPlot() { return parentClaimId != null; }
    public boolean isRented() {
        return rentalEndTime > System.currentTimeMillis() && rentalEndTime > 0;
    }

    // Rental Tax Methods
    public long getLastTaxTime() { return lastTaxTime; }
    public void setLastTaxTime(long lastTaxTime) {
        this.lastTaxTime = lastTaxTime;
        this.modified = true;
    }
}