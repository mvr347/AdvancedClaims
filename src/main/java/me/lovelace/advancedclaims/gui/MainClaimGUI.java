package me.lovelace.advancedclaims.gui;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainClaimGUI extends AbstractGUI {
    private final AdvancedClaims plugin;
    private final Player viewer;
    private final Claim claim;

    // cooldown для показа границ (оптимизация от лагов)
    private static final Map<UUID, Long> borderCooldown = new ConcurrentHashMap<>();

    public MainClaimGUI(AdvancedClaims plugin, Player viewer, Claim claim) {
        super(27, plugin.getConfigManager().getComponent("main.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
        me.lovelace.advancedclaims.model.UserData userData = plugin.getQuestManager().getUserData(viewer.getUniqueId());
        String ownerName = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
        if (ownerName == null) ownerName = "???";

        inventory.setItem(4, createHead(HEAD_INFO,
                plugin.getConfigManager().getComponent("main.info-name"),
                plugin.getConfigManager().getHelpMessage("main.info-lore", "owner", ownerName, "size", String.valueOf(currentSize), "points", String.valueOf(userData.getExpansionBlocks()))));

        inventory.setItem(11, createHead(HEAD_SETTINGS,
                plugin.getConfigManager().getComponent("main.settings-name"),
                plugin.getConfigManager().getHelpMessage("main.settings-lore")));

        inventory.setItem(13, createHead(HEAD_MEMBERS,
                plugin.getConfigManager().getComponent("main.members-name"),
                plugin.getConfigManager().getHelpMessage("main.members-lore")));

        inventory.setItem(15, createHead(HEAD_QUEST,
                plugin.getConfigManager().getComponent("main.quests-name"),
                plugin.getConfigManager().getHelpMessage("main.quests-lore")));

        inventory.setItem(22, createHead(HEAD_BARRIER,
                plugin.getConfigManager().getComponent("common.close"), null));

        fillEmptySlots();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case 4 -> {
                if (event.isLeftClick()) {
                    plugin.getConfigManager().playSound(viewer, "gui-click");
                    viewer.closeInventory();
                    org.bukkit.Location loc = claim.getAnchorLocation();
                    if (loc == null) {
                        double x = (claim.getBoundingBox().getMinX() + claim.getBoundingBox().getMaxX()) / 2.0;
                        double z = (claim.getBoundingBox().getMinZ() + claim.getBoundingBox().getMaxZ()) / 2.0;
                        double y = viewer.getWorld().getHighestBlockYAt((int)x, (int)z) + 1;
                        loc = new org.bukkit.Location(viewer.getWorld(), x, y, z);
                    } else {
                        loc = loc.clone().add(0.5, 1, 0.5);
                    }
                    viewer.teleport(loc);
                    viewer.sendMessage(plugin.getConfigManager().getMessage("teleport-home"));
                } else if (event.isRightClick()) {
                    long lastTime = borderCooldown.getOrDefault(viewer.getUniqueId(), 0L);
                    if (System.currentTimeMillis() - lastTime > 15000L) {
                        plugin.getConfigManager().playSound(viewer, "gui-click");
                        viewer.closeInventory();
                        me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, viewer, claim.getBoundingBox(), 200L);
                        borderCooldown.put(viewer.getUniqueId(), System.currentTimeMillis());
                    } else {
                        plugin.getConfigManager().playSound(viewer, "gui-error");
                    }
                }
            }
            case 11 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new SettingsGUI(plugin, viewer, claim).getInventory());
            }
            case 13 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new MembersGUI(plugin, viewer, claim).getInventory());
            }
            case 15 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new QuestsGUI(plugin, viewer, claim).getInventory());
            }
            case 22 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
            }
        }
    }

    // Добавлено для устранения утечек памяти
    public static void removeCooldown(UUID uuid) {
        borderCooldown.remove(uuid);
    }
}