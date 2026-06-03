package com.example.village.command;

import com.example.village.currency.CurrencyType;
import com.example.village.integration.CurrencyIntegrationManager;
import com.example.village.listener.InventoryClickEventListener;
import com.example.village.model.TradeOffer;
import com.example.village.model.VillagerInventory;
import com.example.village.trading.MarketplaceUI;
import com.example.village.trading.PlayerToPlayerTradeUI;
import com.example.village.trading.VillagerExternalTradeUI;
import com.example.village.trading.VillagerLocalTradeUI;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final CurrencyIntegrationManager currencySystem;
    private final InventoryClickEventListener inventoryClickEventListener;
    private final Logger logger;

    private final Map<UUID, Map<UUID, PendingRequest>> pendingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blockedPlayers = new ConcurrentHashMap<>();

    public TradeCommand(JavaPlugin plugin,
                        CurrencyIntegrationManager currencySystem,
                        InventoryClickEventListener inventoryClickEventListener,
                        Logger logger) {
        this.plugin = Objects.requireNonNull(plugin);
        this.currencySystem = Objects.requireNonNull(currencySystem);
        this.inventoryClickEventListener = Objects.requireNonNull(inventoryClickEventListener);
        this.logger = Objects.requireNonNull(logger);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Command nutzen!");
            return true;
        }
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "balance" -> showBalance(player);
            case "local" -> startLocalTrade(player, args);
            case "external" -> startExternalTrade(player, args);
            case "player" -> handlePlayerSub(player, args);
            case "blocked" -> showBlocked(player);
            case "shop" -> openMarketplace(player, args);
            case "history" -> showHistory(player);
            default -> showHelp(player);
        }
        return true;
    }

    private void handlePlayerSub(Player player, String[] args) {
        if (!currencySystem.getConfigManager().isPlayerToPlayerTradingEnabled()) {
            player.sendMessage(ChatColor.RED + "Spieler-zu-Spieler Handel ist deaktiviert.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Verwendung: /trade player <spieler>");
            return;
        }

        if ("accept".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Verwendung: /trade player accept <spieler>");
                return;
            }
            Player requester = Bukkit.getPlayerExact(args[2]);
            if (requester == null) {
                player.sendMessage(ChatColor.RED + "Spieler ist nicht online.");
                return;
            }
            acceptRequestFrom(player, requester);
            return;
        }
        if ("decline".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Verwendung: /trade player decline <spieler>");
                return;
            }
            declineByName(player, args[2]);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Spieler ist nicht online.");
            return;
        }

        if (args.length >= 3) {
            if ("block".equalsIgnoreCase(args[2])) {
                blockPlayer(player, target);
                return;
            }
            if ("unblock".equalsIgnoreCase(args[2])) {
                unblockPlayer(player, target);
                return;
            }
        }

        sendTradeRequest(player, target);
    }

    public boolean hasPendingRequest(Player requester, Player target) {
        Map<UUID, PendingRequest> byRequester = pendingByTarget.get(target.getUniqueId());
        return byRequester != null && byRequester.containsKey(requester.getUniqueId());
    }

    public void sendTradeRequest(Player requester, Player target) {
        if (requester == null || target == null) {
            return;
        }
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + "Du kannst nicht mit dir selbst handeln.");
            return;
        }
        if (isBlocked(target.getUniqueId(), requester.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + "Dieser Spieler blockiert deine Anfragen.");
            return;
        }
        if (!isDistanceAllowed(requester, target)) {
            requester.sendMessage(ChatColor.RED + "Spieler ist zu weit entfernt für Handel.");
            return;
        }

        Map<UUID, PendingRequest> byRequester = pendingByTarget.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>());
        if (byRequester.containsKey(requester.getUniqueId())) {
            requester.sendMessage(ChatColor.YELLOW + "Anfrage bereits gesendet.");
            return;
        }

        int maxPending = Math.max(1, currencySystem.getConfigManager().getPlayerTradeMaxPendingRequests());
        if (byRequester.size() >= maxPending) {
            requester.sendMessage(ChatColor.RED + "Zu viele offene Anfragen beim Zielspieler.");
            return;
        }

        PendingRequest request = new PendingRequest(requester.getUniqueId(), target.getUniqueId());
        if (!hasBypassTradePermission(requester)) {
            int timeoutSeconds = Math.max(5, currencySystem.getConfigManager().getPlayerTradeRequestTimeoutSeconds());
            request.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> expireRequest(request), timeoutSeconds * 20L);
        }
        byRequester.put(requester.getUniqueId(), request);

        requester.sendMessage(ChatColor.GREEN + "Handelsanfrage an " + target.getName() + " gesendet.");
        target.sendMessage(ChatColor.GOLD + requester.getName() + ChatColor.YELLOW + " möchte mit dir handeln.");
        sendClickableRequestMessage(target, requester.getName());
    }

    public void acceptRequestFrom(Player target, Player requester) {
        PendingRequest request = removeRequest(target.getUniqueId(), requester.getUniqueId());
        if (request == null) {
            target.sendMessage(ChatColor.RED + "Keine offene Anfrage von " + requester.getName() + ".");
            return;
        }
        removeRequest(requester.getUniqueId(), target.getUniqueId());
        if (!isDistanceAllowed(target, requester)) {
            target.sendMessage(ChatColor.RED + "Spieler ist zu weit entfernt für Handel.");
            requester.sendMessage(ChatColor.RED + "Handel abgelehnt: zu große Entfernung.");
            return;
        }

        PlayerToPlayerTradeUI ui = new PlayerToPlayerTradeUI(
                currencySystem.getGlobalCurrencyManager(),
                logger,
                plugin.getDataFolder());
        ui.startTrade(requester, target);
        inventoryClickEventListener.registerTrading(requester.getUniqueId(), ui);
        inventoryClickEventListener.registerTrading(target.getUniqueId(), ui);

        requester.sendMessage(ChatColor.GREEN + target.getName() + " hat angenommen.");
        target.sendMessage(ChatColor.GREEN + "Handel gestartet.");
    }

    private boolean isDistanceAllowed(Player a, Player b) {
        if (hasBypassTradePermission(a) || hasBypassTradePermission(b)) {
            return true;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            return false;
        }
        double maxDist = Math.max(1.0, currencySystem.getConfigManager().getPlayerTradeMaxDistance());
        return a.getLocation().distanceSquared(b.getLocation()) <= (maxDist * maxDist);
    }

    private boolean hasBypassTradePermission(Player player) {
        return player.hasPermission("village.admin.trade") || player.hasPermission("village.admin");
    }

    private void sendClickableRequestMessage(Player target, String requesterName) {
        TextComponent accept = new TextComponent(ChatColor.GREEN + "[handel beginnen]");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade player accept " + requesterName));
        accept.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Klicke zum Annehmen").create()));

        TextComponent spacer = new TextComponent(" ");

        TextComponent decline = new TextComponent(ChatColor.RED + "[handel ablehnen]");
        decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade player decline " + requesterName));
        decline.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Klicke zum Ablehnen").create()));

        target.spigot().sendMessage(accept, spacer, decline);
    }

    private void declineByName(Player target, String requesterName) {
        Player requester = Bukkit.getPlayerExact(requesterName);
        if (requester == null) {
            target.sendMessage(ChatColor.RED + "Spieler ist nicht online.");
            return;
        }
        PendingRequest request = removeRequest(target.getUniqueId(), requester.getUniqueId());
        if (request == null) {
            target.sendMessage(ChatColor.RED + "Keine offene Anfrage von " + requester.getName() + ".");
            return;
        }
        requester.sendMessage(ChatColor.RED + target.getName() + " hat abgelehnt.");
        target.sendMessage(ChatColor.YELLOW + "Anfrage abgelehnt.");
    }

    private void blockPlayer(Player player, Player target) {
        blockedPlayers.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " wurde blockiert.");
    }

    private void unblockPlayer(Player player, Player target) {
        Set<UUID> blocked = blockedPlayers.get(player.getUniqueId());
        if (blocked == null || !blocked.remove(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " war nicht blockiert.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + target.getName() + " wurde entblockt.");
    }

    private void showBlocked(Player player) {
        Set<UUID> blocked = blockedPlayers.getOrDefault(player.getUniqueId(), Set.of());
        if (blocked.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Keine blockierten Spieler.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Blockierte Spieler:");
        for (UUID uuid : blocked) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            player.sendMessage(ChatColor.GRAY + "- " + (op.getName() != null ? op.getName() : uuid));
        }
    }

    private boolean isBlocked(UUID blocker, UUID requester) {
        return blockedPlayers.getOrDefault(blocker, Set.of()).contains(requester);
    }

    private void expireRequest(PendingRequest request) {
        Map<UUID, PendingRequest> map = pendingByTarget.get(request.target);
        if (map == null || map.remove(request.requester) == null) {
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester);
        if (requester != null) {
            requester.sendMessage(ChatColor.RED + "Handelsanfrage abgelaufen.");
        }
    }

    private PendingRequest removeRequest(UUID target, UUID requester) {
        Map<UUID, PendingRequest> map = pendingByTarget.get(target);
        if (map == null) {
            return null;
        }
        PendingRequest req = map.remove(requester);
        if (req != null && req.timeoutTask != null) {
            req.timeoutTask.cancel();
        }
        if (map.isEmpty()) {
            pendingByTarget.remove(target);
        }
        return req;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("balance", "local", "external", "player", "blocked", "shop", "history");
        }
        if (args.length == 2 && "player".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<>(List.of("accept", "decline"));
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            return out;
        }
        if (args.length == 3 && "player".equalsIgnoreCase(args[0])) {
            if ("accept".equalsIgnoreCase(args[1]) || "decline".equalsIgnoreCase(args[1])) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            return List.of("block", "unblock");
        }
        return List.of();
    }

    private void showBalance(Player player) {
        try {
            String villageId = "Startersdorf";
            var localManager = currencySystem.getLocalCurrencyManager(villageId);
            if (localManager == null) {
                localManager = currencySystem.createVillageManager(villageId);
            }
            double localBalance = localManager.getBalance(player.getUniqueId());
            double globalBalance = currencySystem.getGlobalCurrencyManager().getBalance(player.getUniqueId());
            player.sendMessage(ChatColor.GOLD + "=== Guthaben ===");
            player.sendMessage(ChatColor.YELLOW + "Lokal: " + ChatColor.GREEN + String.format(java.util.Locale.US, "%.2f", localBalance));
            player.sendMessage(ChatColor.YELLOW + "Global: " + ChatColor.GREEN + String.format(java.util.Locale.US, "%.2f", globalBalance));
        } catch (Exception e) {
            logger.warning("Error showing balance: " + e.getMessage());
        }
    }

    private void startLocalTrade(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Verwendung: /trade local <villager>");
            return;
        }
        String villagerName = args[1];
        String villagerUUID = UUID.nameUUIDFromBytes(villagerName.getBytes()).toString();
        String villageId = "Startersdorf";
        VillagerInventory inventory = new VillagerInventory(currencySystem.getConfigManager().getVillagerStorageSlots());
        inventory.addForSale(new TradeOffer("OAK_LOG", "Eichenholz", 1, 25, CurrencyType.LOCAL, 64));
        inventory.addForSale(new TradeOffer("WHEAT", "Weizen", 1, 15, CurrencyType.LOCAL, 32));
        inventoryClickEventListener.cacheVillagerInventory(UUID.fromString(villagerUUID), inventory);
        VillagerLocalTradeUI ui = currencySystem.createVillagerLocalTradeUI(villagerUUID, villagerName, villageId, inventory);
        inventoryClickEventListener.registerTrading(player.getUniqueId(), ui);
        ui.openTradingUI(player);
    }

    private void startExternalTrade(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Verwendung: /trade external <villager>");
            return;
        }
        String villagerName = args[1];
        String villagerUUID = UUID.nameUUIDFromBytes(villagerName.getBytes()).toString();
        String villageId = "Startersdorf";
        VillagerInventory inventory = new VillagerInventory(currencySystem.getConfigManager().getVillagerStorageSlots());
        inventory.addForSale(new TradeOffer("IRON_INGOT", "Eisenbarren", 1, 75, CurrencyType.GLOBAL, 32));
        inventory.addForSale(new TradeOffer("GOLD_INGOT", "Goldbarren", 1, 150, CurrencyType.GLOBAL, 16));
        inventoryClickEventListener.cacheVillagerInventory(UUID.fromString(villagerUUID), inventory);
        VillagerExternalTradeUI ui = currencySystem.createVillagerExternalTradeUI(villagerUUID, villagerName, villageId, inventory);
        inventoryClickEventListener.registerTrading(player.getUniqueId(), ui);
        ui.openTradingUI(player);
    }

    private void openMarketplace(Player player, String[] args) {
        if (!currencySystem.getConfigManager().isMarketplaceEnabled()) {
            player.sendMessage(ChatColor.RED + "Marktplatz deaktiviert.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Verwendung: /trade shop <owner> oder /trade shop list");
            return;
        }
        if (args[1].equalsIgnoreCase("list")) {
            Collection<MarketplaceUI> shops = currencySystem.getMarketplaceManager().getAllShops();
            shops.forEach(shop -> player.sendMessage(ChatColor.YELLOW + shop.getShopName()));
            return;
        }
        String ownerName = args[1];
        String ownerUUID = UUID.nameUUIDFromBytes(ownerName.getBytes()).toString();
        MarketplaceUI marketplace = currencySystem.getMarketplaceManager().getShop(ownerUUID).orElseGet(() -> {
            MarketplaceUI newShop = currencySystem.createMarketplace(ownerUUID, ownerName + " Shop", player.getLocation());
            newShop.addOffer(new TradeOffer("APPLE", "Apfel", 1, 5, CurrencyType.LOCAL, 50));
            return newShop;
        });
        inventoryClickEventListener.registerTrading(player.getUniqueId(), marketplace);
        marketplace.openTradingUI(player);
    }

    private void showHistory(Player player) {
        player.sendMessage(ChatColor.GRAY + "Nutze /trade history im nächsten Schritt, wenn gewünscht.");
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "/trade player <spieler>");
        player.sendMessage(ChatColor.GOLD + "/trade player accept <spieler>");
        player.sendMessage(ChatColor.GOLD + "/trade player decline <spieler>");
        player.sendMessage(ChatColor.GOLD + "/trade player <spieler> block|unblock");
        player.sendMessage(ChatColor.GOLD + "/trade blocked");
    }

    private static final class PendingRequest {
        private final UUID requester;
        private final UUID target;
        private BukkitTask timeoutTask;

        private PendingRequest(UUID requester, UUID target) {
            this.requester = requester;
            this.target = target;
        }
    }
}
