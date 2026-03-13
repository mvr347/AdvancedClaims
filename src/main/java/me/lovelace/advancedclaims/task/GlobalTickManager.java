package me.lovelace.advancedclaims.task;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.ClaimFlag;
import me.lovelace.advancedclaims.model.TrustLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 Глобальный менеджер тиков для обработки бафов и автосохранения.
 Использует Paper Regionized Scheduler для лучшей производительности.
 */
public class GlobalTickManager {
    private final AdvancedClaims plugin;
    private long tickCounter = 0;
    private ScheduledTask task;

    public GlobalTickManager(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduler -> {
            tickCounter++;

// 1. БАФЫ ПРИВАТА: Проверяем каждую секунду (20 тиков = 1000ms)
            if (tickCounter % 20 == 0) {
                // Передаем данные в главный поток для безопасного применения эффектов
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Location loc = player.getLocation();
                        plugin.getClaimManager().getClaimAt(loc).ifPresent(claim -> {
                            if (claim.getTrust(player.getUniqueId()) == TrustLevel.NONE) return;

// Бафы (обновляем каждые 3 секунды = 60 тиков)
                            if (claim.getFlag(ClaimFlag.PERK_HASTE)) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 1, true, false, true));
                            }
                            if (claim.getFlag(ClaimFlag.PERK_REGEN)) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, true));
                            }
                            if (claim.getFlag(ClaimFlag.PERK_SATURATION)) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 60, 0, true, false, true));
                            }
                        });
                    }
                });
            }

// 2. АВТОСОХРАНЕНИЕ В БАЗУ ДАННЫХ: Каждые 5 минут (6000 тиков = 300 секунд)
            if (tickCounter % 6000 == 0) {
                plugin.getStorage().batchSaveAllAsync(plugin.getClaimManager().getAllClaims());
            }

// 3. РОСТ РАСТЕНИЙ: Каждые 3 секунды (60 тиков)
            if (tickCounter % 60 == 0) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Location loc = player.getLocation();
                        plugin.getClaimManager().getClaimAt(loc).ifPresent(claim -> {
                            if (claim.getFlag(ClaimFlag.PERK_CROP_GROWTH)) {
                                growCropsNearPlayer(player, loc);
                            }
                        });
                    }
                });
            }

        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    /**
     Ускоряет рост растений в радиусе 5 блоков от игрока.
     */
    private void growCropsNearPlayer(Player player, Location loc) {
        World world = player.getWorld();
        if (world == null) return;
// Ограничиваем количество проверок для производительности
        for (int i = 0; i < 10; i++) {
            int dx = java.util.concurrent.ThreadLocalRandom.current().nextInt(-5, 6);
            int dy = java.util.concurrent.ThreadLocalRandom.current().nextInt(-2, 3);
            int dz = java.util.concurrent.ThreadLocalRandom.current().nextInt(-5, 6);
            org.bukkit.block.Block b = loc.clone().add(dx, dy, dz).getBlock();
            if (b.getType().isAir()) continue;

            if (b.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() < ageable.getMaximumAge()) {
                    ageable.setAge(ageable.getAge() + 1);
                    b.setBlockData(ageable);
                    world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                            b.getLocation().add(0.5, 0.5, 0.5), 3);
                }
            }
        }
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}