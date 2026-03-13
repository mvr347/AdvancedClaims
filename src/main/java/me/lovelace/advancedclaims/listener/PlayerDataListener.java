package me.lovelace.advancedclaims.listener;
import me.lovelace.advancedclaims.AdvancedClaims;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerDataListener implements Listener {
    private final AdvancedClaims plugin;

    public PlayerDataListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getStorage().loadUserData(player.getUniqueId())
                .thenAccept(userData -> {
                    plugin.getQuestManager().getUserData(player.getUniqueId()).loadFrom(userData);
                });

        if (!player.hasPlayedBefore()) {
            ItemStack starterAnchor = plugin.getAnchorManager().createAnchorItem("tier-1");
            if (starterAnchor != null) {
                player.getInventory().addItem(starterAnchor);
                player.sendMessage(plugin.getConfigManager().getMessage("anchor-received"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();

        // 1. Асинхронное сохранение данных при выходе
        me.lovelace.advancedclaims.model.UserData data = plugin.getQuestManager().getUserData(uuid);
        plugin.getStorage().saveUserDataAsync(data);
        plugin.getQuestManager().unloadUser(uuid);

        // 2. Очистка кэшей, тасков и утечек памяти (Memory Leaks)
        plugin.getAnchorListener().cleanupPlayer(player);
        plugin.getChatListener().cleanupPlayer(player);
        me.lovelace.advancedclaims.task.BorderDisplayTask.hideBorder(player);

        // Очистка новых найденных утечек
        me.lovelace.advancedclaims.gui.MainClaimGUI.removeCooldown(uuid);
        plugin.getClaimManager().removeInvite(uuid);
    }
}