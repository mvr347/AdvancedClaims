package me.lovelace.advancedclaims.command;

import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.gui.RentalAdminListGUI;
import me.lovelace.advancedclaims.gui.RentalEditGUI;
import me.lovelace.advancedclaims.gui.RentalPaymentGUI;
import me.lovelace.advancedclaims.gui.RentalPlayerGUI;
import me.lovelace.advancedclaims.gui.RentalStrangerGUI;
import me.lovelace.advancedclaims.model.Claim;
import me.lovelace.advancedclaims.model.IndicatorType;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RentalCommand implements CommandExecutor, TabCompleter {
    private final AdvancedClaims plugin;

    public RentalCommand(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            List<Claim> relatedPlots = plugin.getClaimManager().getAllClaims().stream()
                    .filter(Claim::isRentalPlot)
                    .filter(c -> (c.getOwnerUuid() != null && c.getOwnerUuid().equals(player.getUniqueId())) || c.getMembers().containsKey(player.getUniqueId()))
                    .toList();

            if (relatedPlots.isEmpty()) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-no-rented"));
                return true;
            }

            if (relatedPlots.size() == 1) {
                player.openInventory(new RentalPlayerGUI(plugin, player, relatedPlots.get(0)).getInventory());
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-list-header"));
                for (Claim plot : relatedPlots) {
                    long timeLeft = (plot.getRentalEndTime() - System.currentTimeMillis()) / 1000;
                    long days = Math.max(0, timeLeft / 86400);
                    long hours = Math.max(0, (timeLeft % 86400) / 3600);
                    String role = (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId())) ? plugin.getConfigManager().getString("members.role-owner") : plugin.getConfigManager().getString("members.role-member");
                    player.sendMessage(plugin.getConfigManager().getMessage("msg-rental-list-entry", "role", role, "name", plot.getName(), "days", String.valueOf(days), "hours", String.valueOf(hours)));
                }
                player.sendMessage(plugin.getConfigManager().getMessage("msg-rental-list-footer"));
            }
            return true;
        }

        if (args.length == 1 && !List.of("admin", "buy", "rent", "tp", "help", "leave", "show").contains(args[0].toLowerCase())) {
            UUID plotId = plugin.getRentalManager().getPlotIdByName(args[0]);
            if (plotId == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-claim-not-found"));
                return true;
            }
            Claim plot = plugin.getClaimManager().getClaimById(plotId).orElse(null);
            if (plot == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-claim-not-found"));
                return true;
            }

            if ((plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId())) || plot.getMembers().containsKey(player.getUniqueId())) {
                player.openInventory(new RentalPlayerGUI(plugin, player, plot).getInventory());
            } else {
                player.openInventory(new RentalStrangerGUI(plugin, plot).getInventory());
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "admin" -> handleAdminCommand(player, args);
            case "buy", "rent" -> handleBuyCommand(player);
            case "tp" -> handleTpCommand(player, args);
            case "help" -> handleHelpCommand(player);
            case "leave" -> handleLeaveCommand(player, args);
            case "show" -> handleShowCommand(player);
            case "sell" -> handleSellCommand(player, args);
            default -> player.sendMessage(plugin.getConfigManager().getMessage("rental-unknown-command"));
        }
        return true;
    }

    private void handleShowCommand(Player player) {
        if (!player.hasPermission("advancedclaims.rental.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        Claim plot = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
        if (plot == null || !plot.isRentalPlot()) {
            player.sendMessage(Component.text("§cВы должны находиться на территории арендного участка!"));
            return;
        }
        me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, player, plot.getBoundingBox(), 200L);
        player.sendMessage(Component.text("§aГраницы участка " + plot.getName() + " подсвечены на 10 секунд."));
    }

    private void handleLeaveCommand(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            Claim plot = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
            if (plot != null && plot.isRentalPlot() && plot.getMembers().containsKey(player.getUniqueId())) {
                plot.getMembers().remove(player.getUniqueId());
                plugin.getStorage().removeMemberAsync(plot.getId(), player.getUniqueId());
                player.sendMessage(plugin.getConfigManager().getMessage("rental-leave-success", "name", plot.getName()));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-leave-not-on-plot"));
            }
        } else {
            Claim plot = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
            if (plot == null || !plot.isRentalPlot() || !plot.getMembers().containsKey(player.getUniqueId())) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-leave-not-member"));
                return;
            }
            Component msg = plugin.getConfigManager().getComponent("rental-leave-confirm", "name", plot.getName())
                    .append(plugin.getConfigManager().getComponent("rental-confirm-btn")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/rental leave confirm"))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(plugin.getConfigManager().getComponent("rental-confirm-hover"))));
            player.sendMessage(msg);
        }
    }

    private void handleHelpCommand(Player player) {
        for (Component line : plugin.getConfigManager().getHelpMessage("rental-help")) {
            player.sendMessage(line);
        }
        if (player.hasPermission("advancedclaims.rental.admin")) {
            for (Component line : plugin.getConfigManager().getHelpMessage("rental-admin.help")) {
                player.sendMessage(line);
            }
        }
    }

    private void handleBuyCommand(Player player) {
        Claim targetPlot = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
        if (targetPlot == null || !targetPlot.isRentalPlot()) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-buy-not-on-plot"));
            return;
        }
        if (targetPlot.isRented()) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-buy-already-rented"));
            return;
        }

        long ownedCount = plugin.getClaimManager().getAllClaims().stream()
                .filter(Claim::isRentalPlot)
                .filter(c -> c.getOwnerUuid() != null && c.getOwnerUuid().equals(player.getUniqueId()))
                .count();

        if (ownedCount >= 1 && !player.hasPermission("advancedclaims.rental.bypasslimit")) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-limit-reached"));
            return;
        }
        player.openInventory(new RentalPaymentGUI(plugin, targetPlot).getInventory());
    }

    private void handleSellCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("msg-rental-sell-usage"));
            return;
        }

        // Проверка подтверждения
        if (args.length >= 3 && args[1].equalsIgnoreCase("confirm") && args[2].equalsIgnoreCase("confirm")) {
            Claim plot = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
            if (plot == null || !plot.isRentalPlot()) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-buy-not-on-plot"));
                return;
            }
            if (plot.getOwnerUuid() == null || !plot.getOwnerUuid().equals(player.getUniqueId())) {
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }

            // Удаляем всех участников
            for (UUID memberId : new ArrayList<>(plot.getMembers().keySet())) {
                plot.getMembers().remove(memberId);
                plugin.getStorage().removeMemberAsync(plot.getId(), memberId);
            }

            // Сбрасываем владельца
            plot.setOwnerUuid(null);
            plot.setRentalEndTime(0);
            plugin.getStorage().saveClaimAsync(plot);
            plugin.getRentalManager().updateIndicator(plot);

            player.sendMessage(plugin.getConfigManager().getMessage("rental-sold"));
            plugin.getConfigManager().playSound(player, "tax-paid");
            return;
        }

        // Отправка подтверждения
        Claim plot = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
        if (plot == null || !plot.isRentalPlot()) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-buy-not-on-plot"));
            return;
        }
        if (plot.getOwnerUuid() == null || !plot.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        String targetName = args[1];
        Component msg = plugin.getConfigManager().getComponent("msg-rental-sell-confirm", "name", targetName)
                .append(plugin.getConfigManager().getComponent("msg-rental-sell-btn")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/rental sell " + targetName + " confirm confirm"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(plugin.getConfigManager().getComponent("msg-rental-sell-btn-hover"))));
        player.sendMessage(msg);
    }

    private void handleTpCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-tp-usage", "label", "/rental"));
            return;
        }
        UUID plotId = plugin.getRentalManager().getPlotIdByName(args[1]);
        if (plotId == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-claim-not-found"));
            return;
        }
        plugin.getClaimManager().getClaimById(plotId).ifPresentOrElse(plot -> {
            if (plot.getTrust(player.getUniqueId()) == me.lovelace.advancedclaims.model.TrustLevel.NONE && !player.hasPermission("advancedclaims.admin")) {
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            Location loc = plot.getHomeLocation() != null ? plot.getHomeLocation() : plot.getAnchorLocation().clone().add(0.5, 1.0, 0.5);
            player.teleport(loc);
            player.sendMessage(plugin.getConfigManager().getMessage("rental-tp-success", "name", plot.getName()));
        }, () -> player.sendMessage(plugin.getConfigManager().getMessage("rental-not-found")));
    }

    private void handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("advancedclaims.rental.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length == 1) {
            handleHelpCommand(player);
            return;
        }

        String subCommand = args[1].toLowerCase();
        switch (subCommand) {
            case "show" -> {
                // Псевдоним для /rental show (только для админов)
                handleShowCommand(player);
            }
            case "list" -> player.openInventory(new RentalAdminListGUI(plugin).getInventory());
            case "set" -> {
                if (args.length >= 3) {
                    if (args[2].equalsIgnoreCase("landlord")) {
                        Claim targetPlot = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
                        if (targetPlot == null || !targetPlot.isRentalPlot()) {
                            player.sendMessage(plugin.getConfigManager().getMessage("rental-buy-not-on-plot"));
                            return;
                        }

                        plugin.getRentalManager().removeLandlord(targetPlot);

                        Location loc = player.getLocation();
                        String serializedLoc = loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
                        targetPlot.setHologramId("landlord:" + serializedLoc);

                        if (targetPlot.getIndicatorType() == IndicatorType.NONE) {
                            targetPlot.setIndicatorType(IndicatorType.NPC);
                        }

                        plugin.getStorage().saveClaimAsync(targetPlot);
                        plugin.getRentalManager().updateIndicator(targetPlot);
                        player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-landlord-set", "name", targetPlot.getName()));
                        plugin.getConfigManager().playSound(player, "anchor-place");
                    } else if (args[2].equalsIgnoreCase("taxer")) {
                        plugin.getRentalManager().spawnTaxer(player.getLocation());
                        player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-taxer-set"));
                        plugin.getConfigManager().playSound(player, "anchor-place");
                    } else {
                        player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-set-usage"));
                    }
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-set-usage"));
                }
            }
            case "remove" -> {
                if (args.length >= 4 && args[2].equalsIgnoreCase("landlord")) {
                    String plotName = args[3];
                    UUID plotId = plugin.getRentalManager().getPlotIdByName(plotName);
                    if (plotId == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("rental-claim-not-found"));
                        return;
                    }
                    plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                        plugin.getRentalManager().removeLandlord(plot);
                        player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-landlord-removed", "name", plot.getName()));
                    });
                } else if (args.length == 3 && args[2].equalsIgnoreCase("taxer")) {
                    plugin.getRentalManager().removeTaxer();
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-taxer-removed"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-remove-usage"));
                }
            }
            case "setowner" -> {
                if (args.length < 4) { player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-setowner-usage")); return; }
                UUID plotId = plugin.getRentalManager().getPlotIdByName(args[2]);
                if (plotId == null) { player.sendMessage(plugin.getConfigManager().getMessage("rental-claim-not-found")); return; }
                plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                    org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[3]);
                    plot.setOwnerUuid(target.getUniqueId());
                    plot.setRentalEndTime(System.currentTimeMillis() + plugin.getRentalManager().getTaxDays() * 86400000L);
                    plot.getMembers().clear();
                    plot.setTrust(target.getUniqueId(), me.lovelace.advancedclaims.model.TrustLevel.OWNER);
                    plugin.getStorage().saveClaimAsync(plot);
                    plugin.getRentalManager().updateIndicator(plot);
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-setowner-success", "target", target.getName()));
                });
            }
            case "removeowner" -> {
                if (args.length < 3) return;
                UUID plotId = plugin.getRentalManager().getPlotIdByName(args[2]);
                if (plotId != null) plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                    plot.setOwnerUuid(plot.getParentClaimId());
                    plot.setRentalEndTime(0);
                    plot.getMembers().clear();
                    plugin.getStorage().saveClaimAsync(plot);
                    plugin.getRentalManager().updateIndicator(plot);
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-removeowner-success"));
                });
            }
            case "create" -> {
                if (args.length != 9) {
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-create-usage"));
                    return;
                }
                String name = args[2];
                if (plugin.getRentalManager().getPlotIdByName(name) != null) {
                    player.sendMessage(plugin.getConfigManager().getComponent("msg-rental-admin-exists"));
                    return;
                }
                try {
                    int x1 = parseCoordinate(args, 3, 0);
                    int y1 = parseCoordinate(args, 4, 0);
                    int z1 = parseCoordinate(args, 5, 0);
                    int x2 = parseCoordinate(args, 6, 0);
                    int y2 = parseCoordinate(args, 7, 0);
                    int z2 = parseCoordinate(args, 8, 0);

                    // Добавляем 12 блоков вверх и 1 вниз
                    int minX = Math.min(x1, x2);
                    int maxX = Math.max(x1, x2);
                    int baseY = Math.min(y1, y2);
                    int minY = baseY - 1;
                    int maxY = baseY + 12;
                    int minZ = Math.min(z1, z2);
                    int maxZ = Math.max(z1, z2);

                    BoundingBox box = new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
                    Claim rental = new Claim(UUID.randomUUID(), player.getWorld(), box, null, player.getLocation());
                    rental.setName(name);
                    rental.setParentClaimId(UUID.nameUUIDFromBytes("SERVER_RENTAL".getBytes()));
                    rental.setIndicatorType(IndicatorType.NONE);
                    rental.setRentalPrice(0);

                    plugin.getClaimManager().addClaimToCache(rental);
                    plugin.getStorage().saveClaimAsync(rental);
                    plugin.getRentalManager().registerPlotName(name, rental.getId());

                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-create-success", "name", name));

                    // Моментальное открытие GUI настроек
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.openInventory(new RentalEditGUI(plugin, rental).getInventory());
                    });
                } catch (Exception e) {
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-invalid-coords"));
                }
            }
            case "delete" -> {
                if (args.length < 3) return;
                String plotName = args[2];
                UUID plotId = plugin.getRentalManager().getPlotIdByName(plotName);
                if (plotId != null) {
                    plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                        plugin.getRentalManager().removeLandlord(plot);
                    });
                    plugin.getStorage().deleteClaimAsync(plotId);
                    plugin.getClaimManager().removeClaimFromCache(plotId);
                    plugin.getRentalManager().unregisterPlotName(plotName);
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-deleted"));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("tp", "buy", "help", "leave", "show", "sell"));
            if (sender.hasPermission("advancedclaims.rental.admin")) completions.add("admin");
            for (Claim c : plugin.getClaimManager().getAllClaims()) if (c.isRentalPlot() && c.getName() != null) completions.add(c.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            completions.add("<игрок>");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("advancedclaims.rental.admin")) {
            completions.addAll(List.of("create", "delete", "list", "setowner", "removeowner", "set", "remove", "show"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set") && sender.hasPermission("advancedclaims.rental.admin")) {
            completions.addAll(List.of("landlord", "taxer"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remove") && sender.hasPermission("advancedclaims.rental.admin")) {
            completions.addAll(List.of("landlord", "taxer"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remove") && args[2].equalsIgnoreCase("landlord") && sender.hasPermission("advancedclaims.rental.admin")) {
            for (Claim c : plugin.getClaimManager().getAllClaims()) if (c.isRentalPlot() && c.getName() != null) completions.add(c.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && List.of("setowner", "removeowner", "delete").contains(args[1].toLowerCase())) {
            for (Claim c : plugin.getClaimManager().getAllClaims()) if (c.isRentalPlot() && c.getName() != null) completions.add(c.getName());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setowner")) {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length >= 4 && args.length <= 9 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
            if (sender instanceof Player p) {
                org.bukkit.block.Block target = p.getTargetBlockExact(10);
                if (target != null) {
                    if (args.length == 4 || args.length == 7) completions.add(String.valueOf(target.getX()));
                    if (args.length == 5 || args.length == 8) completions.add(String.valueOf(target.getY()));
                    if (args.length == 6 || args.length == 9) completions.add(String.valueOf(target.getZ()));
                }
            }
            completions.add("~");
        }
        return completions.stream().filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).collect(Collectors.toList());
    }

    private int parseCoordinate(String[] args, int index, int defaultValue) {
        if (args.length <= index) return defaultValue;
        if (args[index].equals("~")) return defaultValue;
        try { return Integer.parseInt(args[index]); } catch (Exception e) { return defaultValue; }
    }
}