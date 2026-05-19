package me.lovelace.advancedclaims.listener;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.gui.DeleteConfirmGUI;
import me.lovelace.advancedclaims.gui.MainClaimGUI;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.ClaimTier;
import me.lovelace.advancedclaims.model.TrustLevel;
import me.lovelace.advancedclaims.task.BlockPreviewTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик событий якорей: установка, удаление, подтверждение создания.
 */
public class AnchorListener implements Listener {
    private final AdvancedClaims plugin;
    private final ConcurrentHashMap<UUID, PendingClaim> pendingClaims = new ConcurrentHashMap<>();

    public record PendingClaim(Location location, ClaimTier tier, BlockPreviewTask previewTask, long createdAt) {}

    public ConcurrentHashMap<UUID, PendingClaim> getPendingClaims() {
        return pendingClaims;
    }

    public AnchorListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnchorBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(loc);

        if (claimOpt.isEmpty()) return;
        Claim claim = claimOpt.get();
        Location anchorLoc = claim.getAnchorLocation();

        if (loc.getWorld().equals(anchorLoc.getWorld()) &&
                loc.getBlockX() == anchorLoc.getBlockX() &&
                loc.getBlockZ() == anchorLoc.getBlockZ() &&
                (loc.getBlockY() == anchorLoc.getBlockY() || loc.getBlockY() == anchorLoc.getBlockY() - 1)) {

            event.setCancelled(true);

            Player player = event.getPlayer();
            if (claim.getOwnerUuid().equals(player.getUniqueId())) {
                player.openInventory(new DeleteConfirmGUI(plugin, player, claim).getInventory());
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("anchor-cannot-break"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Если есть ожидающий приват, то не обрабатываем обычный клик по якорю
        if (pendingClaims.containsKey(player.getUniqueId())) return;

        Optional<Claim> existingClaimOpt = plugin.getClaimManager().getClaimAt(clickedBlock.getLocation());
        if (existingClaimOpt.isPresent()) {
            Claim claim = existingClaimOpt.get();
            Location anchorLoc = claim.getAnchorLocation();

            if (clickedBlock.getX() == anchorLoc.getBlockX() &&
                    clickedBlock.getY() == anchorLoc.getBlockY() &&
                    clickedBlock.getZ() == anchorLoc.getBlockZ()) {

                // Если приват в режиме осады и игрок НЕ является лидером клана, не открываем GUI
                if (claim.isUnderSiege() && !player.getUniqueId().equals(claim.getOwnerUuid())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("siege-interact-deny"));
                    return;
                }

                if (claim.getTrust(player.getUniqueId()).ordinal() >= TrustLevel.MANAGER.ordinal()) {
                    event.setCancelled(true);
                    player.openInventory(new MainClaimGUI(plugin, player, claim).getInventory());
                }
                return;
            }
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        Optional<ClaimTier> tierOpt = plugin.getAnchorManager().getTierFromItem(item);
        if (tierOpt.isEmpty()) return;

        event.setCancelled(true);
        ClaimTier tier = tierOpt.get();
        Location targetLoc = clickedBlock.getRelative(event.getBlockFace()).getLocation();

        BoundingBox newBox = BoundingBox.of(
                targetLoc.clone().add(-tier.radiusX(), -tier.radiusY(), -tier.radiusZ()),
                targetLoc.clone().add(tier.radiusX(), tier.radiusY(), tier.radiusZ())
        );

        // Проверка на пересечение с клановыми территориями
        for (Claim existingClaim : plugin.getClaimManager().getAllClaims()) {
            if (existingClaim.isClanTerritory() && newBox.overlaps(existingClaim.getBoundingBox())) {
                player.sendMessage(plugin.getConfigManager().getMessage("clan-overlap-deny"));
                return;
            }
        }

        String worldName = targetLoc.getWorld().getName();
        boolean isWorldAllowed = plugin.getConfigManager().getConfig()
                .getBoolean("limits.allowed-worlds." + worldName + ".enabled", true);

        if (!isWorldAllowed) {
            player.sendMessage(plugin.getConfigManager().getMessage("claim-disabled-world"));
            return;
        }

        int currentClaims = 0;
        for (Claim claim : plugin.getClaimManager().getAllClaims()) {
            if (!claim.isRentalPlot() && claim.getOwnerUuid() != null && claim.getOwnerUuid().equals(player.getUniqueId())) {
                currentClaims++;
            }
        }

        int maxClaims = 1;

        if (plugin.getQuestManager() != null) {
            me.lovelace.advancedclaims.model.UserData userData = plugin.getQuestManager().getUserData(player.getUniqueId());
            maxClaims += userData.getBonusClaimSlots();
        }

        if (currentClaims >= maxClaims) {
            player.sendMessage(plugin.getConfigManager().getMessage("claim-limit-reached"));
            return;
        }

        PendingClaim oldPending = pendingClaims.remove(player.getUniqueId());
        if (oldPending != null) oldPending.previewTask().revert();

        BlockPreviewTask previewTask = new BlockPreviewTask(plugin, player, newBox, tier, () -> pendingClaims.remove(player.getUniqueId()));

        pendingClaims.put(player.getUniqueId(), new PendingClaim(targetLoc, tier, previewTask, System.currentTimeMillis()));

        String titleTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(plugin.getConfigManager().getMessage("claim-preview-title"));
        String titleSub = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(plugin.getConfigManager().getMessage("claim-preview-subtitle"));
        
        player.sendTitle(titleTitle, titleSub, 10, 70, 20);
        player.sendMessage(plugin.getConfigManager().getMessage("claim-create-hint"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemHold(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        if (item == null || plugin.getAnchorManager().getTierFromItem(item).isEmpty()) {
            PendingClaim pending = pendingClaims.remove(player.getUniqueId());
            if (pending != null) {
                pending.previewTask().revert();
                player.sendMessage(plugin.getConfigManager().getMessage("anchor-cancel"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConfirmInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction().name().contains("RIGHT_CLICK") && pendingClaims.containsKey(player.getUniqueId())) {
            PendingClaim pending = pendingClaims.get(player.getUniqueId());
            if (System.currentTimeMillis() - pending.createdAt() < 500) return;

            event.setCancelled(true);
            Bukkit.dispatchCommand(player, "ac confirm");
        }
    }

    public void cleanupPlayer(org.bukkit.entity.Player player) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        if (pending != null && pending.previewTask() != null) {
            pending.previewTask().revert();
        }
    }
}