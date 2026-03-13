package me.lovelace.advancedclaims.command;
import me.lovelace.advancedclaims.AdvancedClaims;
import me.lovelace.advancedclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;
import java.util.List;

public class MainCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final AdvancedClaims plugin;

    public MainCommand(AdvancedClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            List<Claim> accessibleClaims = plugin.getClaimManager().getAllClaims().stream()
                    .filter(c -> !c.isRentalPlot())
                    .filter(c -> c.getTrust(player.getUniqueId()) != me.lovelace.advancedclaims.model.TrustLevel.NONE)
                    .toList();

            if (accessibleClaims.isEmpty()) {
                player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
                return true;
            }

            if (accessibleClaims.size() == 1) {
                player.openInventory(new me.lovelace.advancedclaims.gui.MainClaimGUI(plugin, player, accessibleClaims.get(0)).getInventory());
                return true;
            }

            player.sendMessage(plugin.getConfigManager().getMessage("rental-multiple-claims"));
            for (Claim c : accessibleClaims) {
                String ownerName = Bukkit.getOfflinePlayer(c.getOwnerUuid()).getName();
                if (ownerName == null) ownerName = "Неизвестно";
                player.sendMessage(plugin.getConfigManager().getComponent("msg-invite-list-entry", "player", ownerName));
            }
            player.sendMessage(plugin.getConfigManager().getMessage("rental-multiple-claims-hint"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> {
                for (Component line : plugin.getConfigManager().getHelpMessage("command-help")) {
                    player.sendMessage(line);
                }
                return true;
            }
            case "home" -> {
                Claim targetClaim = null;
                for (Claim c : plugin.getClaimManager().getAllClaims()) {
                    if (!c.isRentalPlot() && c.getOwnerUuid().equals(player.getUniqueId())) {
                        targetClaim = c;
                        break;
                    }
                }
                if (targetClaim == null) {
                    for (Claim c : plugin.getClaimManager().getAllClaims()) {
                        if (!c.isRentalPlot() && c.getTrust(player.getUniqueId()) != me.lovelace.advancedclaims.model.TrustLevel.NONE) {
                            targetClaim = c;
                            break;
                        }
                    }
                }
                if (targetClaim != null) {
                    player.teleportAsync(targetClaim.getHomeLocation());
                    player.sendMessage(plugin.getConfigManager().getMessage("teleport-home"));
                    plugin.getConfigManager().playSound(player, "anchor-place");
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
                    plugin.getConfigManager().playSound(player, "anchor-error");
                }
                return true;
            }

            case "invite" -> {
                if (args.length >= 2) {
                    if (args[0].equalsIgnoreCase("invite")) {
                        // Подтверждение инвайта
                        if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
                            java.util.UUID inviteClaimId = plugin.getClaimManager().getInvite(player.getUniqueId());
                            if (inviteClaimId == null) {
                                player.sendMessage(net.kyori.adventure.text.Component.text("§cУ вас нет активных приглашений."));
                                return true;
                            }
                            java.util.Optional<me.lovelace.advancedclaims.model.Claim> claimOpt = plugin.getClaimManager().getClaimById(inviteClaimId);
                            if (claimOpt.isPresent()) {
                                me.lovelace.advancedclaims.model.Claim claim = claimOpt.get();
                                
                                int memberCount = 0;
                                for (me.lovelace.advancedclaims.model.Claim c : plugin.getClaimManager().getAllClaims()) {
                                    if (c.isRentalPlot() == claim.isRentalPlot() && c.getTrust(player.getUniqueId()) != me.lovelace.advancedclaims.model.TrustLevel.NONE && (c.getOwnerUuid() == null || !c.getOwnerUuid().equals(player.getUniqueId()))) {
                                        memberCount++;
                                    }
                                }
                                
                                if (memberCount >= 5) {
                                    player.sendMessage(net.kyori.adventure.text.Component.text("§cВы уже состоите в максимальном количестве " + (claim.isRentalPlot() ? "плотов!" : "приватов!")));
                                    plugin.getClaimManager().removeInvite(player.getUniqueId());
                                    return true;
                                }

                                claim.setTrust(player.getUniqueId(), me.lovelace.advancedclaims.model.TrustLevel.ACCESS);
                                plugin.getStorage().saveMemberAsync(claim.getId(), player.getUniqueId(), me.lovelace.advancedclaims.model.TrustLevel.ACCESS);
                                player.sendMessage(net.kyori.adventure.text.Component.text("§aВы успешно присоединились к привату!"));
                                plugin.getClaimManager().removeInvite(player.getUniqueId());
                                player.openInventory(new me.lovelace.advancedclaims.gui.MembersGUI(plugin, player, claim).getInventory());
                            } else {
                                player.sendMessage(net.kyori.adventure.text.Component.text("§cЭтот приват больше не существует."));
                                plugin.getClaimManager().removeInvite(player.getUniqueId());
                            }
                            return true;
                        }
                        if (args.length == 2) {
                            String targetName = args[1];
                            java.util.Optional<me.lovelace.advancedclaims.model.Claim> currentOpt = plugin.getClaimManager().getClaimAt(player.getLocation());

                            if (currentOpt.isEmpty()) {
                                player.sendMessage(plugin.getConfigManager().getMessage("not-in-your-claim"));
                                return true;
                            }

                            me.lovelace.advancedclaims.model.Claim claim = currentOpt.get();
                            if (claim.getTrust(player.getUniqueId()).ordinal() < me.lovelace.advancedclaims.model.TrustLevel.MANAGER.ordinal()) {
                                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                                return true;
                            }

                            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                                player.sendMessage(plugin.getConfigManager().getMessage("member-not-found"));
                                return true;
                            }

                            if (claim.getTrust(target.getUniqueId()) != me.lovelace.advancedclaims.model.TrustLevel.NONE) {
                                player.sendMessage(net.kyori.adventure.text.Component.text("§cИгрок уже является участником привата!"));
                                return true;
                            }

                            plugin.getClaimManager().addInvite(target.getUniqueId(), claim.getId());
                            player.sendMessage(plugin.getConfigManager().getMessage("invite-sent", "player", target.getName()));
                            plugin.getConfigManager().playSound(player, "gui-click");

                            if (target.isOnline() && target.getPlayer() != null) {
                                net.kyori.adventure.text.Component msg = plugin.getConfigManager().getMessage("invite-received", "player", player.getName())
                                        .append(plugin.getConfigManager().getMessage("invite-accept-btn")
                                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ac invite confirm"))
                                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(plugin.getConfigManager().getMessage("invite-accept-hover"))));
                                target.getPlayer().sendMessage(msg);
                                plugin.getConfigManager().playSound(target.getPlayer(), "anchor-place");
                            }
                        } else {
                            player.sendMessage(plugin.getConfigManager().getComponent("msg-invite-usage"));
                        }
                        return true;
                    }
                }
                player.sendMessage(plugin.getConfigManager().getComponent("msg-invite-usage"));
                return true;
            }
            case "confirm" -> {
                me.lovelace.advancedclaims.listener.AnchorListener.PendingClaim pending = plugin.getAnchorListener().getPendingClaims().remove(player.getUniqueId());
                if (pending != null) {
                    pending.previewTask().revert();
                    Claim newClaim = new Claim(java.util.UUID.randomUUID(), pending.location().getWorld(), pending.previewTask().getBox(), player.getUniqueId(), pending.location());
                    plugin.getClaimManager().addClaimToCache(newClaim);
                    plugin.getStorage().saveClaimAsync(newClaim);

                    pending.location().getBlock().setType(pending.tier().material());
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    player.sendMessage(plugin.getConfigManager().getMessage("claim-created"));
                    plugin.getConfigManager().playSound(player, "anchor-place");
                }
                return true;
            }
            case "show" -> {
                Optional<Claim> currentClaimOpt = plugin.getClaimManager().getClaimAt(player.getLocation());
                if (currentClaimOpt.isEmpty()) {
                    boolean hasAnyClaim = plugin.getClaimManager().getAllClaims().stream()
                            .anyMatch(c -> c.getTrust(player.getUniqueId()) != me.lovelace.advancedclaims.model.TrustLevel.NONE);
                    if (hasAnyClaim) {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-in-your-claim"));
                    } else {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
                    }
                    return true;
                }
                me.lovelace.advancedclaims.task.BorderDisplayTask.hideBorder(player);
                me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, player, currentClaimOpt.get().getBoundingBox(), 200L);
                return true;
            }
            case "move" -> {
                Optional<Claim> opt = plugin.getClaimManager().getClaimAt(player.getLocation());
                if (opt.isEmpty() || opt.get().isRentalPlot()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("not-in-your-claim"));
                    return true;
                }
                Claim claim = opt.get();
                if (!claim.getOwnerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-move-deny"));
                    return true;
                }

                int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
                me.lovelace.advancedclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);

                int baseSize = currentTier != null ? currentTier.radiusX() * 2 : 16;
                int expansions = (currentSize - baseSize);
                if (expansions > 0) {
                    me.lovelace.advancedclaims.model.UserData data = plugin.getQuestManager().getUserData(player.getUniqueId());
                    data.addExpansionBlocks(expansions);
                    plugin.getStorage().saveUserDataAsync(data);
                }

                org.bukkit.inventory.ItemStack anchor = plugin.getAnchorManager().createAnchorItem(currentTier != null ? currentTier.id() : "tier-1");
                if (anchor != null) {
                    player.getInventory().addItem(anchor).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                }

                plugin.getClaimManager().removeClaimFromCache(claim.getId());
                plugin.getStorage().deleteClaimAsync(claim.getId());
                claim.getAnchorLocation().getBlock().setType(org.bukkit.Material.AIR);

                player.sendMessage(plugin.getConfigManager().getMessage("rental-move-success"));
                plugin.getConfigManager().playSound(player, "anchor-break");
                return true;
            }
            case "admin" -> {
                if (!player.hasPermission("advancedclaims.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("reload")) {
                        plugin.getConfigManager().loadAll();
                        plugin.getAnchorManager().loadTiers();
                        plugin.getQuestManager().loadQuests();
                        player.sendMessage(plugin.getConfigManager().getMessage("admin-reloaded"));
                        return true;
                    } else if (args[1].equalsIgnoreCase("give") && args.length >= 4) {
                        Player target = Bukkit.getPlayer(args[2]);
                        org.bukkit.inventory.ItemStack anchor = plugin.getAnchorManager().createAnchorItem(args[3]);
                        if (target != null && anchor != null) {
                            target.getInventory().addItem(anchor);
                            player.sendMessage(plugin.getConfigManager().getMessage("admin-give-success", "player", target.getName()));
                        } else {
                            player.sendMessage(plugin.getConfigManager().getMessage("admin-give-fail"));
                        }
                        return true;
                    } else if (args[1].equalsIgnoreCase("expand") && args.length >= 4) {
                        Player owner = Bukkit.getPlayer(args[2]);
                        if (owner == null) return true;
                        int amount = Integer.parseInt(args[3]);
                        Optional<Claim> opt = plugin.getClaimManager().getClaimAt(player.getLocation());
                        if (opt.isEmpty()) {
                            player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-expand-fail"));
                            return true;
                        }
                        Claim claim = opt.get();
                        if (!claim.getOwnerUuid().equals(owner.getUniqueId())) {
                            player.sendMessage(plugin.getConfigManager().getMessage("admin-expand-not-owner", "player", owner.getName()));
                            return true;
                        }
                        claim.setBoundingBox(claim.getBoundingBox().expand(amount, amount, amount));
                        plugin.getStorage().saveClaimAsync(claim);
                        player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-expand-success", "amount", String.valueOf(amount)));
                        me.lovelace.advancedclaims.task.BorderDisplayTask.showBorder(plugin, player, claim.getBoundingBox(), 100);
                        return true;
                    } else if (args[1].equalsIgnoreCase("addmembers") && args.length >= 4) {
                        Player target = Bukkit.getPlayer(args[2]);
                        if (target != null) {
                            int amount = Integer.parseInt(args[3]);
                            me.lovelace.advancedclaims.model.UserData data = plugin.getQuestManager().getUserData(target.getUniqueId());
                            data.addBonusMemberLimit(amount);
                            plugin.getStorage().saveUserDataAsync(data);
                            player.sendMessage(plugin.getConfigManager().getMessage("admin-limit-success", "player", target.getName(), "amount", String.valueOf(amount)));
                        }
                        return true;
                    } else if (args[1].equalsIgnoreCase("claim") && args.length >= 5 && args[2].equalsIgnoreCase("limit")) {
                        String action = args[3];
                        Player target = Bukkit.getPlayer(args[4]);
                        int count = args.length >= 6 ? Integer.parseInt(args[5]) : 1;
                        if (target != null) {
                            me.lovelace.advancedclaims.model.UserData data = plugin.getQuestManager().getUserData(target.getUniqueId());
                            if (args[0].equalsIgnoreCase("leave")) {
                                if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
                                    java.util.Optional<me.lovelace.advancedclaims.model.Claim> currentOpt = plugin.getClaimManager().getClaimAt(player.getLocation());
                                    if (currentOpt.isPresent() && !currentOpt.get().isRentalPlot() && currentOpt.get().getMembers().containsKey(player.getUniqueId())) {
                                        me.lovelace.advancedclaims.model.Claim claim = currentOpt.get();
                                        claim.getMembers().remove(player.getUniqueId());
                                        plugin.getStorage().removeMemberAsync(claim.getId(), player.getUniqueId());
                                        player.sendMessage(net.kyori.adventure.text.Component.text("§aВы успешно покинули приват!"));
                                    } else {
                                        player.sendMessage(net.kyori.adventure.text.Component.text("§cВы должны находиться в привате, который хотите покинуть."));
                                    }
                                } else {
                                    java.util.Optional<me.lovelace.advancedclaims.model.Claim> currentOpt = plugin.getClaimManager().getClaimAt(player.getLocation());
                                    if (currentOpt.isEmpty() || currentOpt.get().isRentalPlot() || !currentOpt.get().getMembers().containsKey(player.getUniqueId())) {
                                        player.sendMessage(net.kyori.adventure.text.Component.text("§cВстаньте на территорию привата (где вы участник), чтобы покинуть его."));
                                        return true;
                                    }
                                    net.kyori.adventure.text.Component msg = net.kyori.adventure.text.Component.text("§eВы уверены, что хотите покинуть этот приват? ")
                                            .append(net.kyori.adventure.text.Component.text("§a[ПОДТВЕРДИТЬ]")
                                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ac leave confirm"))
                                                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("§7Нажмите для выхода из привата"))));
                                    player.sendMessage(msg);
                                }
                                return true;
                            }

                            if (action.equalsIgnoreCase("add")) {
                                data.addExpansionBlocks(count);
                                player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-limit-add", "count", String.valueOf(count), "player", target.getName()));
                            } else if (action.equalsIgnoreCase("remove")) {
                                if (data.getExpansionBlocks() >= count) {
                                    data.removeExpansionBlocks(count);
                                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-limit-remove", "count", String.valueOf(count), "player", target.getName()));
                                } else {
                                    player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-limit-insufficient"));
                                }
                            }
                            plugin.getStorage().saveUserDataAsync(data);
                        } else {
                            player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-player-not-found"));
                        }
                        return true;
                    }
                }
                for (Component line : plugin.getConfigManager().getHelpMessage("admin-help")) {
                    player.sendMessage(line);
                }
                return true;
            }
            default -> {
                if (args.length == 1) {
                    String targetName = args[0];
                    plugin.getClaimManager().getAllClaims().stream()
                            .filter(c -> !c.isRentalPlot())
                            .filter(c -> {
                                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(c.getOwnerUuid());
                                return op.getName() != null && op.getName().equalsIgnoreCase(targetName);
                            })
                            .findFirst()
                            .ifPresentOrElse(
                                    c -> player.openInventory(new me.lovelace.advancedclaims.gui.MainClaimGUI(plugin, player, c).getInventory()),
                                    () -> player.sendMessage(plugin.getConfigManager().getMessage("rental-claim-not-found", "player", targetName))
                            );
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();
        if (args.length == 1) {
            completions.addAll(java.util.List.of("home", "show", "confirm", "invite", "move", "admin", "help"));
            if (sender instanceof Player player) {
                plugin.getClaimManager().getAllClaims().stream()
                        .filter(c -> !c.isRentalPlot())
                        .filter(c -> c.getTrust(player.getUniqueId()) != me.lovelace.advancedclaims.model.TrustLevel.NONE)
                        .forEach(c -> {
                            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(c.getOwnerUuid());
                            if (op.getName() != null) {
                                completions.add(op.getName());
                            }
                        });
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            completions.addAll(java.util.List.of("reload", "give", "expand", "addmembers", "claim"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("claim")) {
            completions.add("limit");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("claim") && args[2].equalsIgnoreCase("limit")) {
            completions.addAll(java.util.List.of("add", "remove"));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("claim") && args[2].equalsIgnoreCase("limit")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            completions.add("confirm");
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("expand") || args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("addmembers"))) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
            completions.addAll(java.util.List.of("tier-1", "tier-2", "tier-3"));
        }
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
    }
}