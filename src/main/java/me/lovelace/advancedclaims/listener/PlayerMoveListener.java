package me.lovelace.advancedclaims.listener;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.ClaimFlag;
import me.lovelace.advancedclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerMoveListener implements Listener {
    private final AdvancedClaims plugin;
    private final Map<UUID, UUID> lastClaim = new ConcurrentHashMap<>();

    public PlayerMoveListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Оптимизация: игнорируем движения головой, проверяем только смену блока
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        handleMovement(event.getPlayer(), from, to, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handleMovement(event.getPlayer(), event.getFrom(), event.getTo(), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClaim.remove(event.getPlayer().getUniqueId());
    }

    private void handleMovement(Player player, Location from, Location to, PlayerMoveEvent event) {
        UUID playerId = player.getUniqueId();
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(to);
        UUID currentClaimId = claimOpt.map(Claim::getId).orElse(null);
        UUID previousClaimId = lastClaim.get(playerId);

        // Игрок зашел в новый приват
        if (currentClaimId != null && !currentClaimId.equals(previousClaimId)) {
            Claim claim = claimOpt.get();

            // Проверка флага запрета на вход (только для игроков без прав)
            if (claim.getFlag(ClaimFlag.DENY_ENTRY) && claim.getTrust(playerId) == TrustLevel.NONE && !player.hasPermission("advancedclaims.bypass")) {
                // Проверяем, был ли игрок уже в этом привате (чтобы не телепортировать своих)
                if (previousClaimId == null || !previousClaimId.equals(currentClaimId)) {
                    if (event != null) {
                        event.setCancelled(true);
                    } else {
                        player.teleport(from); // Если это был телепорт - возвращаем назад
                    }
                    player.sendActionBar(plugin.getConfigManager().getMessage("deny-entry"));
                    return;
                }
            }

            lastClaim.put(playerId, currentClaimId);
            sendClaimMessage(player, claim, true);

        }
        else if (currentClaimId == null && previousClaimId != null) {
            lastClaim.remove(playerId);
            plugin.getClaimManager().getClaimById(previousClaimId).ifPresent(claim -> {
                sendClaimMessage(player, claim, false);
            });
        }
    }

    private void sendClaimMessage(Player player, Claim claim, boolean enter) {
        String ownerName;
        String claimDisplayName;

        if (claim.isClanTerritory()) {
            // Для клановых территорий используем имя, установленное через API, или дефолтное из конфига
            claimDisplayName = claim.getName() != null ? claim.getName() : plugin.getConfigManager().getString("misc.clan-claim-display-name", "Клановая Территория");
            ownerName = plugin.getConfigManager().getString("misc.clan-owner-display-name", "Клан");
        } else {
            // Логика для обычных приватов
            ownerName = "Сервер";
            if (claim.getOwnerUuid() != null) {
                String fetchedName = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
                if (fetchedName != null) ownerName = fetchedName;
            }
            claimDisplayName = claim.getName() != null ? claim.getName() : "Участок";
        }

        if (claim.getFlag(ClaimFlag.MSG_SCREEN)) {
            Component titleComp = enter ?
                    plugin.getConfigManager().getComponent("title-enter", "name", claimDisplayName, "owner", ownerName) :
                    plugin.getConfigManager().getComponent("title-leave", "name", claimDisplayName, "owner", ownerName);

            Title title = Title.title(titleComp, Component.empty(), Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250)));
            player.showTitle(title);
        }

        if (claim.getFlag(ClaimFlag.MSG_ACTIONBAR)) {
            Component actionbarComp = enter ?
                    plugin.getConfigManager().getComponent("actionbar-enter", "name", claimDisplayName, "owner", ownerName) :
                    plugin.getConfigManager().getComponent("actionbar-leave", "name", claimDisplayName, "owner", ownerName);
            player.sendActionBar(actionbarComp);
        }
    }
}