package me.lovelace.advancedclaims.listener;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.ClaimFlag;
import me.lovelace.advancedclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.Iterator;
import java.util.Optional;

public class ProtectionListener implements Listener {
    private final AdvancedClaims plugin;

    public ProtectionListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    private void deny(Player player, Claim claim, Component message) {
        if (claim == null || !claim.getFlag(ClaimFlag.SILENT_DENY)) {
            String str = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(message);
            player.sendActionBar(str);
        }
    }

    // --- ЗАЩИТА ОТ ВЗРЫВОВ (КРИПЕРЫ / TNT) ---
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(java.util.List<org.bukkit.block.Block> blocks) {
        Iterator<org.bukkit.block.Block> it = blocks.iterator();
        while (it.hasNext()) {
            org.bukkit.block.Block block = it.next();
            Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(block.getLocation());

            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();

                // 1. ЗАЩИТА ЯКОРЯ: Якорь нельзя взорвать никогда (даже если флаг EXPLOSIONS включен)
                org.bukkit.Location anchor = claim.getAnchorLocation();
                if (anchor.getBlockX() == block.getX() && anchor.getBlockY() == block.getY() && anchor.getBlockZ() == block.getZ()) {
                    it.remove();
                    continue;
                }

                // 2. ЗАЩИТА ТЕРРИТОРИИ: Зависит от флага привата
                if (!claim.getFlag(ClaimFlag.EXPLOSIONS)) {
                    it.remove();
                }
            } else if (isSpawnProtected(block.getLocation())) {
                // Защита спавна от взрывов
                if (!plugin.getConfigManager().getSpawnFlag("explosions")) {
                    it.remove();
                }
            }
        }
    }
    // -----------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;
        if (isSpawnProtected(event.getBlock().getLocation())) {
            if (!plugin.getConfigManager().getSpawnFlag("break-blocks")) {
                event.setCancelled(true);
                if (plugin.getConfigManager().getConfig().getBoolean("spawn-claim.say-break-message", false)) {
                    deny(player, null, plugin.getConfigManager().getMessage("deny-break"));
                }
            }
            return;
        }
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        claimOpt.ifPresent(claim -> {
            if (claim.getTrust(player.getUniqueId()).ordinal() < TrustLevel.BUILD.ordinal()) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-break"));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;
        if (isSpawnProtected(event.getBlock().getLocation())) {
            if (!plugin.getConfigManager().getSpawnFlag("place-blocks")) {
                event.setCancelled(true);
                if (plugin.getConfigManager().getConfig().getBoolean("spawn-claim.say-break-message", false)) {
                    deny(player, null, plugin.getConfigManager().getMessage("deny-place"));
                }
            }
            return;
        }
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        claimOpt.ifPresent(claim -> {
            if (claim.getTrust(player.getUniqueId()).ordinal() < TrustLevel.BUILD.ordinal()) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-place"));
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlOrChorus(PlayerTeleportEvent event) {
        if (!hasBypass(event.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        if (isSpawnProtected(event.getTo())) {
            if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && !plugin.getConfigManager().getSpawnFlag("enderpearl")) {
                event.setCancelled(true);
                deny(event.getPlayer(), null, plugin.getConfigManager().getMessage("deny-pearl"));
            } else if (cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT && !plugin.getConfigManager().getSpawnFlag("chorus")) {
                event.setCancelled(true);
                deny(event.getPlayer(), null, plugin.getConfigManager().getMessage("deny-chorus"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        if (hasBypass(event.getPlayer())) return;
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) return;
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(block.getLocation());
        claimOpt.ifPresent(claim -> {
            Material type = block.getType();
            if (isContainerBlock(type)) {
                if (claim.getTrust(event.getPlayer().getUniqueId()).ordinal() < TrustLevel.BUILD.ordinal()) {
                    event.setCancelled(true);
                    deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-interact"));
                    return;
                }
            }
            if (isDoorOrButtonBlock(type)) {
                if (claim.getTrust(event.getPlayer().getUniqueId()).ordinal() < TrustLevel.ACCESS.ordinal()) {
                    event.setCancelled(true);
                    deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-interact"));
                    return;
                }
            }
            if (claim.getTrust(event.getPlayer().getUniqueId()).ordinal() < TrustLevel.BUILD.ordinal()) {
                event.setCancelled(true);
                deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-interact"));
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onElytra(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) return;
        if (hasBypass(player)) return;
        if (isSpawnProtected(player.getLocation())) {
            if (!plugin.getConfigManager().getSpawnFlag("elytra")) {
                event.setCancelled(true);
                deny(player, null, plugin.getConfigManager().getMessage("deny-elytra"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        if (attacker.hasPermission("advancedclaims.bypass") || attacker.hasPermission("advancedclaims.admin")) return;
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(victim.getLocation());
        if (claimOpt.isPresent() && !claimOpt.get().getFlag(ClaimFlag.PVP)) {
            event.setCancelled(true);
        } else if (isSpawnProtected(victim.getLocation()) && !plugin.getConfigManager().getSpawnFlag("pvp")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        claimOpt.ifPresent(claim -> {
            if (claim.getFlag(ClaimFlag.PERK_CROP_GROWTH)) {
                org.bukkit.block.data.BlockData data = event.getNewState().getBlockData();
                if (data instanceof org.bukkit.block.data.Ageable ageable) {
                    if (ageable.getAge() < ageable.getMaximumAge() && Math.random() < 0.5) {
                        ageable.setAge(Math.min(ageable.getAge() + 1, ageable.getMaximumAge()));
                        event.getNewState().setBlockData(ageable);
                    }
                }
            }
        });
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("advancedclaims.bypass")
                || player.hasPermission("advancedclaims.admin")
                || player.hasPermission("advancedclaims.moderator");
    }

    private boolean isSpawnProtected(org.bukkit.Location loc) {
        return plugin.getConfigManager().isInsideSpawnClaim(loc);
    }

    private boolean isContainerBlock(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST ||
                type == Material.FURNACE || type == Material.BLAST_FURNACE ||
                type == Material.SMOKER || type == Material.BARREL ||
                type.name().contains("SHULKER_BOX") || type == Material.DISPENSER ||
                type == Material.DROPPER || type == Material.HOPPER;
    }

    private boolean isDoorOrButtonBlock(Material type) {
        return type.name().contains("DOOR") || type.name().contains("TRAPDOOR") ||
                type.name().contains("BUTTON") || type == Material.LEVER;
    }
}