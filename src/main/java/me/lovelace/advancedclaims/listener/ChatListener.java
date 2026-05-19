package me.lovelace.advancedclaims.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private final AdvancedClaims plugin;
    private final Map<UUID, UUID> pendingDesc = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingMember = new ConcurrentHashMap<>();
    private final Map<UUID, Claim> pendingPrice = new ConcurrentHashMap<>();

    public ChatListener(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    public void setPendingDesc(UUID player, UUID claimId) { pendingDesc.put(player, claimId); }
    public void setPendingMember(UUID player, UUID claimId) { pendingMember.put(player, claimId); }
    public void setPendingPrice(UUID player, Claim claim) { pendingPrice.put(player, claim); }

    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        pendingDesc.remove(uuid);
        pendingMember.remove(uuid);
        pendingPrice.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Если игрок ничего не вводит для плагина — пропускаем
        if (!pendingDesc.containsKey(uuid) && !pendingMember.containsKey(uuid) && !pendingPrice.containsKey(uuid)) {
            return;
        }

        // 1. Отменяем ивент, чтобы текст не попал в глобальный чат
        event.setCancelled(true);

        // 2. Получаем чистый текст из компонента Paper 1.21+
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
            cleanupPlayer(player);
            player.sendMessage(plugin.getConfigManager().getMessage("chat-cancel"));
            return;
        }

        // --- Изменение описания ---
        if (pendingDesc.containsKey(uuid)) {
            UUID claimId = pendingDesc.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> { // Выполняем синхронно
                Optional<Claim> claimOpt = plugin.getClaimManager().getClaimById(claimId);
                if (claimOpt.isPresent()) {
                    Claim claim = claimOpt.get();
                    claim.setDescription(text);
                    plugin.getStorage().saveClaimAsync(claim);
                    player.sendMessage(plugin.getConfigManager().getMessage("chat-desc-changed", "desc", text));
                    player.openInventory(new me.lovelace.advancedclaims.gui.SettingsGUI(plugin, player, claim).getInventory());
                }
            });
            return;
        }

        // --- Добавление участника (Инвайт через GUI) ---
        if (pendingMember.containsKey(uuid)) {
            UUID claimId = pendingMember.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> { // Выполняем синхронно
                Optional<Claim> claimOpt = plugin.getClaimManager().getClaimById(claimId);
                if (claimOpt.isEmpty()) return;
                Claim claim = claimOpt.get();

                OfflinePlayer target = Bukkit.getOfflinePlayer(text);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    player.sendMessage(plugin.getConfigManager().getMessage("member-not-found"));
                    if (claim.isRentalPlot()) {
                        player.openInventory(new me.lovelace.advancedclaims.gui.RentalMembersGUI(plugin, player, claim).getInventory());
                    } else {
                        player.openInventory(new me.lovelace.advancedclaims.gui.MembersGUI(plugin, player, claim).getInventory());
                    }
                    return;
                }

                if (claim.getTrust(target.getUniqueId()) != TrustLevel.NONE) {
                    player.sendMessage(plugin.getConfigManager().getMessage("member-already-exists"));
                    if (claim.isRentalPlot()) {
                        player.openInventory(new me.lovelace.advancedclaims.gui.RentalMembersGUI(plugin, player, claim).getInventory());
                    } else {
                        player.openInventory(new me.lovelace.advancedclaims.gui.MembersGUI(plugin, player, claim).getInventory());
                    }
                    return;
                }

                // ПРОВЕРКА ЛИМИТОВ: Не более 5 чужих приватов и не более 5 чужих плотов
                int memberCount = 0;
                for (Claim c : plugin.getClaimManager().getAllClaims()) {
                    if (c.isRentalPlot() == claim.isRentalPlot() && c.getTrust(target.getUniqueId()) != TrustLevel.NONE && (c.getOwnerUuid() == null || !c.getOwnerUuid().equals(target.getUniqueId()))) {
                        memberCount++;
                    }
                }
                
                if (memberCount >= 5) {
                    player.sendMessage(Component.text("§cЭтот игрок уже состоит в максимальном количестве " + (claim.isRentalPlot() ? "чужих плотов (5)!" : "чужих приватов (5)!")));
                    return;
                }

                int defaultLimit = plugin.getConfigManager().getConfig().getInt("claim.members.default-limit", 5);
                int absoluteLimit = plugin.getConfigManager().getConfig().getInt("claim.members.absolute-limit", 24);
                int maxMembers = claim.isRentalPlot() ? 10 : Math.min(absoluteLimit, defaultLimit);

                if (claim.getMembers().size() >= maxMembers) {
                    player.sendMessage(plugin.getConfigManager().getMessage("limit-members"));
                    return;
                }

                plugin.getClaimManager().addInvite(target.getUniqueId(), claimId);
                player.sendMessage(plugin.getConfigManager().getMessage("invite-sent", "player", target.getName()));
                plugin.getConfigManager().playSound(player, "gui-click");

                if (target.isOnline() && target.getPlayer() != null) {
                    Component msg = plugin.getConfigManager().getMessage("invite-received", "player", player.getName())
                            .append(plugin.getConfigManager().getMessage("invite-accept-btn")
                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ac invite confirm"))
                                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(plugin.getConfigManager().getMessage("invite-accept-hover"))));
                    target.getPlayer().sendMessage(msg);
                    plugin.getConfigManager().playSound(target.getPlayer(), "anchor-place");
                }
            });
            return;
        }

        // --- Изменение цены аренды ---
        if (pendingPrice.containsKey(uuid)) {
            Claim claim = pendingPrice.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> { // Выполняем синхронно
                try {
                    long price = Long.parseLong(text);
                    if (price < 0) throw new NumberFormatException();
                    claim.setRentalPrice(price);
                    plugin.getStorage().saveClaimAsync(claim);
                    plugin.getRentalManager().updateIndicator(claim);
                    player.sendMessage(Component.text("§aЦена аренды установлена: §e" + price));
                    player.openInventory(new me.lovelace.advancedclaims.gui.RentalEditGUI(plugin, claim).getInventory());
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getConfigManager().getMessage("msg-price-number"));
                    player.openInventory(new me.lovelace.advancedclaims.gui.RentalEditGUI(plugin, claim).getInventory());
                }
            });
        }
    }
}