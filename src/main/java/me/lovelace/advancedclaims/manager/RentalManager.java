package me.lovelace.advancedclaims.manager;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.IndicatorType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RentalManager {
    private final AdvancedClaims plugin;

    private boolean citizensEnabled = false;
    private Object npcRegistry;
    private Method createNPCMethod;
    private Method spawnMethod;
    private Method destroyMethod;
    private Method getIdMethod;

    private final Map<String, UUID> plots = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> npcCache = new ConcurrentHashMap<>();

    public RentalManager(AdvancedClaims plugin) {
        this.plugin = plugin;
        initCitizensReflection();
    }

    public void loadPlots(Collection<Claim> claims) {
        plots.clear();
        for (Claim claim : claims) {
            if (claim.isRentalPlot()) {
                plots.put(claim.getName().toLowerCase(), claim.getId());
            }
        }
        plugin.getLogger().info("Loaded " + plots.size() + " rental plots from database.");
    }

    private void initCitizensReflection() {
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                Class<?> citizensApi = Class.forName("net.citizensnpcs.api.CitizensAPI");
                npcRegistry = citizensApi.getMethod("getNPCRegistry").invoke(null);
                Class<?> registryClass = npcRegistry.getClass();
                Class<?> entityTypeClass = org.bukkit.entity.EntityType.class;
                createNPCMethod = registryClass.getMethod("createNPC", entityTypeClass, String.class);

                Class<?> npcClass = Class.forName("net.citizensnpcs.api.npc.NPC");
                spawnMethod = npcClass.getMethod("spawn", Location.class);
                destroyMethod = npcClass.getMethod("destroy");
                getIdMethod = npcClass.getMethod("getId");

                citizensEnabled = true;
                plugin.getLogger().info("Citizens API hooked for Landlord & Taxer NPCs!");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to hook Citizens: " + e.getMessage());
            }
        }
    }

    public void registerPlotName(String name, UUID id) { plots.put(name.toLowerCase(), id); }
    public void unregisterPlotName(String name) { plots.remove(name.toLowerCase()); }
    public UUID getPlotIdByName(String name) { return plots.get(name.toLowerCase()); }

    public void spawnTaxer(Location loc) {
        if (!citizensEnabled) return;
        try {
            String npcName = plugin.getConfigManager().getString("taxer-npc-name", "§eСборщик налогов");
            Object npc = createNPCMethod.invoke(npcRegistry, org.bukkit.entity.EntityType.PLAYER, npcName);
            Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            Object skinTrait = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, skinTraitClass);
            skinTraitClass.getMethod("setSkinName", String.class).invoke(skinTrait, "LastHumanINmars");
            spawnMethod.invoke(npc, loc);
            taxerNpcId = (int) getIdMethod.invoke(npc);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn Taxer NPC: " + e.getMessage());
        }
    }

    public void removeLandlord(Claim claim) {
        if (claim.getHologramId() != null && claim.getHologramId().startsWith("landlord:")) {
            String[] parts = claim.getHologramId().replace("landlord:", "").split(";");
            if (parts.length >= 6) {
                World w = Bukkit.getWorld(parts[0]);
                if (w != null) {
                    Location loc = new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    Block b = loc.getBlock();
                    if (b.getType().name().endsWith("SIGN")) b.setType(Material.AIR);
                }
            }
        }
        if (citizensEnabled && npcCache.containsKey(claim.getId())) {
            try {
                Object npc = npcRegistry.getClass().getMethod("getById", int.class).invoke(npcRegistry, npcCache.get(claim.getId()));
                if (npc != null) destroyMethod.invoke(npc);
            } catch (Exception ignored) {}
            npcCache.remove(claim.getId());
        }

        claim.setHologramId(null);
        claim.setIndicatorType(IndicatorType.NONE);
        plugin.getStorage().saveClaimAsync(claim);
    }

    private Integer taxerNpcId = null;

    public void removeTaxer() {
        if (!citizensEnabled) return;
        if (taxerNpcId != null) {
            try {
                Object npc = npcRegistry.getClass().getMethod("getById", int.class).invoke(npcRegistry, taxerNpcId);
                if (npc != null) destroyMethod.invoke(npc);
            } catch (Exception ignored) {}
            taxerNpcId = null;
        }
    }

    public void updateIndicator(Claim claim) {
        plugin.getStorage().saveClaimAsync(claim);

        String holoId = claim.getHologramId();
        if (holoId == null || !holoId.startsWith("landlord:")) return;

        String[] parts = holoId.replace("landlord:", "").split(";");
        if (parts.length < 6) return;

        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return;
        Location loc = new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));

        boolean isRented = claim.isRented();
        IndicatorType type = claim.getIndicatorType();

        Block b = loc.getBlock();
        if (b.getType().name().endsWith("SIGN")) b.setType(Material.AIR);

        if (citizensEnabled && npcCache.containsKey(claim.getId())) {
            try {
                Object npc = npcRegistry.getClass().getMethod("getById", int.class).invoke(npcRegistry, npcCache.get(claim.getId()));
                if (npc != null) destroyMethod.invoke(npc);
            } catch (Exception ignored) {}
            npcCache.remove(claim.getId());
        }

        if (isRented || type == IndicatorType.NONE) return;

        if (type == IndicatorType.SIGN) {
            b.setType(Material.OAK_SIGN);
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Sign signData) {
                org.bukkit.block.BlockFace face = getClosestFace(loc.getYaw());
                signData.setRotation(face);
                b.setBlockData(signData);
            }
            if (b.getState() instanceof org.bukkit.block.Sign sign) {
                sign.line(0, plugin.getConfigManager().getComponent("rental-sign-header"));
                sign.line(1, net.kyori.adventure.text.Component.text("§e" + claim.getName()));
                sign.line(2, plugin.getConfigManager().getComponent("rental-sign-price", "price", String.valueOf(claim.getRentalPrice())));
                sign.line(3, plugin.getConfigManager().getComponent("rental-sign-click"));
                sign.update();
            }
        }

        if (type == IndicatorType.NPC && citizensEnabled) {
            try {
                String header = plugin.getConfigManager().getString("rental-sign-header", "§b[Аренда]");
                Object npc = createNPCMethod.invoke(npcRegistry, org.bukkit.entity.EntityType.PLAYER, header + " §e" + claim.getName());
                Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
                Object skinTrait = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, skinTraitClass);
                skinTraitClass.getMethod("setSkinName", String.class).invoke(skinTrait, "xJosueGutt");
                spawnMethod.invoke(npc, loc);
                int id = (int) getIdMethod.invoke(npc);
                npcCache.put(claim.getId(), id);
            } catch (Exception ignored) {}
        }
    }

    private org.bukkit.block.BlockFace getClosestFace(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        int direction = (int) ((yaw + 11.25) / 22.5);
        return switch (direction) {
            case 1 -> org.bukkit.block.BlockFace.SOUTH_SOUTH_WEST;
            case 2 -> org.bukkit.block.BlockFace.SOUTH_WEST;
            case 3 -> org.bukkit.block.BlockFace.WEST_SOUTH_WEST;
            case 4 -> org.bukkit.block.BlockFace.WEST;
            case 5 -> org.bukkit.block.BlockFace.WEST_NORTH_WEST;
            case 6 -> org.bukkit.block.BlockFace.NORTH_WEST;
            case 7 -> org.bukkit.block.BlockFace.NORTH_NORTH_WEST;
            case 8 -> org.bukkit.block.BlockFace.NORTH;
            case 9 -> org.bukkit.block.BlockFace.NORTH_NORTH_EAST;
            case 10 -> org.bukkit.block.BlockFace.NORTH_EAST;
            case 11 -> org.bukkit.block.BlockFace.EAST_NORTH_EAST;
            case 12 -> org.bukkit.block.BlockFace.EAST;
            case 13 -> org.bukkit.block.BlockFace.EAST_SOUTH_EAST;
            case 14 -> org.bukkit.block.BlockFace.SOUTH_EAST;
            case 15 -> org.bukkit.block.BlockFace.SOUTH_SOUTH_EAST;
            default -> org.bukkit.block.BlockFace.SOUTH;
        };
    }

    public double getTaxPercentage() { return plugin.getConfigManager().getConfig().getDouble("rental.tax-percentage", 5.0); }
    public int getTaxDays() { return plugin.getConfigManager().getConfig().getInt("rental.tax-days", 3); }
}