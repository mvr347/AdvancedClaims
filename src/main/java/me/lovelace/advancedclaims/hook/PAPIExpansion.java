package me.lovelace.advancedclaims.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.ClaimFlag;
import me.lovelace.advancedclaims.model.TrustLevel;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class PAPIExpansion extends PlaceholderExpansion {
    private final AdvancedClaims plugin;

    public PAPIExpansion(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "advancedclaims";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return " ";

        // --- 1. Глобальная статистика ---
        if (params.equalsIgnoreCase("claims")) {
            long count = plugin.getClaimManager().getAllClaims().stream()
                    .filter(c -> c.getOwnerUuid() != null && c.getOwnerUuid().equals(offlinePlayer.getUniqueId()))
                    .count();
            return String.valueOf(count);
        }

        if (params.equalsIgnoreCase("claims_max")) {
            int max = plugin.getConfigManager().getConfig().getInt("limits.default-max-claims", 3);
            return String.valueOf(max);
        }

        if (params.equalsIgnoreCase("blocks")) {
            double totalVolume = plugin.getClaimManager().getAllClaims().stream()
                    .filter(c -> c.getOwnerUuid() != null && c.getOwnerUuid().equals(offlinePlayer.getUniqueId()))
                    .mapToDouble(c -> c.getBoundingBox().getVolume())
                    .sum();
            return String.valueOf((int) totalVolume);
        }

        if (params.equalsIgnoreCase("blocks_max")) {
            int maxBlocks = plugin.getConfigManager().getConfig().getInt("limits.default-max-blocks", 10000);
            return String.valueOf(maxBlocks);
        }

        // --- 2. Контекстная статистика ---
        if (offlinePlayer.isOnline()) {
            Player player = offlinePlayer.getPlayer();
            Optional<Claim> currentClaimOpt = plugin.getClaimManager().getClaimAt(player.getLocation());

            if (currentClaimOpt.isPresent()) {
                Claim claim = currentClaimOpt.get();

                if (params.equalsIgnoreCase("current_name")) {
                    return claim.getName() != null ? claim.getName() : "Без названия";
                }

                if (params.equalsIgnoreCase("current_owner")) {
                    return org.bukkit.Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
                }

                if (params.equalsIgnoreCase("current_role")) {
                    return claim.getTrust(player.getUniqueId()).name();
                }

                if (params.equalsIgnoreCase("is_pvp")) {
                    return claim.getFlag(ClaimFlag.PVP) ? " &cВключено" : " &aВыключено";
                }
            } else {
                if (params.startsWith("current_ ")) return "Свободная территория";
                if (params.equalsIgnoreCase("is_pvp")) return " &cВключено";
            }
        }

        // --- 3. Аренда ---
        if (params.equalsIgnoreCase("rented_days")) {
            return "0";
        }

        if (params.equalsIgnoreCase("quests_completed")) {
            return "0";
        }

        return null;
    }
}