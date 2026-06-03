package com.example.village.command;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.Village;
import com.example.village.service.CurrencyService;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalMoneyCommand implements CommandExecutor, TabCompleter {
    private final VillageConfigManager config;
    private final VillageManager villageManager;
    private final CurrencyService currencyService;
    private final Map<UUID, PendingGlobalTransfer> pending = new ConcurrentHashMap<>();

    public GlobalMoneyCommand(VillageConfigManager config, VillageManager villageManager, CurrencyService currencyService) {
        this.config = config;
        this.villageManager = villageManager;
        this.currencyService = currencyService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        String cmd = command.getName().toLowerCase();
        if ("sendmoney".equals(cmd)) return handleSendMoney(player, args);
        if ("balance".equals(cmd)) return handleBalance(player, args);
        if ("balances".equals(cmd)) return handleBalances(player, args);
        return true;
    }

    private boolean handleSendMoney(Player player, String[] args) {
        if (args.length == 1 && ("ja".equalsIgnoreCase(args[0]) || "yes".equalsIgnoreCase(args[0]) || "nein".equalsIgnoreCase(args[0]) || "no".equalsIgnoreCase(args[0]))) {
            PendingGlobalTransfer p = pending.remove(player.getUniqueId());
            if (p == null) {
                msg(player, "&cKeine ausstehende Ueberweisung.");
                return true;
            }
            if (!args[0].equalsIgnoreCase("ja") && !args[0].equalsIgnoreCase("yes")) {
                msg(player, "&eUeberweisung abgebrochen.");
                return true;
            }
            boolean ok = currencyService.transfer(player.getUniqueId(), p.targetId(), currencyService.getGlobalCurrencyId(), p.amount());
            if (!ok) {
                msg(player, "&cNicht genug Guthaben.");
                return true;
            }
            OfflinePlayer off = Bukkit.getOfflinePlayer(p.targetId());
            msg(player, "&aGesendet: " + p.amount() + " an " + (off.getName() != null ? off.getName() : p.targetId()));
            if (off.isOnline() && off.getPlayer() != null) {
                msg(off.getPlayer(), "&aDu hast " + p.amount() + " globale Coins erhalten.");
            }
            currencyService.save();
            return true;
        }
        if (args.length < 2) {
            msg(player, "&cVerwendung: /sendmoney <Spieler> <Betrag>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            msg(player, "&cSpieler nicht online.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            msg(player, "&cUngueltiger Betrag.");
            return true;
        }
        if (amount <= 0) {
            msg(player, "&cBetrag muss positiv sein.");
            return true;
        }
        if (currencyService.getBalance(player.getUniqueId(), currencyService.getGlobalCurrencyId()) < amount) {
            msg(player, "&cNicht genug globales Guthaben.");
            return true;
        }
        pending.put(player.getUniqueId(), new PendingGlobalTransfer(target.getUniqueId(), amount));
        MessageUtil.sendYesNoRunCommand(player, config.getPrefix(), "&e" + amount + " global an " + target.getName() + " senden?",
                "/sendmoney ja", "/sendmoney nein");
        return true;
    }

    private boolean handleBalance(Player player, String[] args) {
        UUID targetId = player.getUniqueId();
        if (args.length >= 1) {
            if (!player.hasPermission("village.admin")) {
                msg(player, "&cKeine Berechtigung.");
                return true;
            }
            targetId = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
        }
        final UUID balanceTargetId = targetId;
        double bal = currencyService.getBalance(balanceTargetId, currencyService.getGlobalCurrencyId());
        String name = Bukkit.getOfflinePlayer(balanceTargetId).getName();
        msg(player, "&7Globales Guthaben von &e" + (name != null ? name : balanceTargetId) + "&7: &a" + fmt(bal));

        Set<UUID> shownVillageIds = new HashSet<>();
        if (balanceTargetId.equals(player.getUniqueId())) {
            villageManager.getVillageAtLocation(player.getLocation()).ifPresent(village -> {
                String currencyId = currencyService.getVillageCurrencyId(village);
                double villageBalance = currencyService.getBalance(balanceTargetId, currencyId);
                String currencyName = currencyService.getVillageCurrencyDisplayName(village);
                msg(player, "&7Dorf-Guthaben (&e" + village.getName() + "&7, " + currencyName + "): &a" + fmt(villageBalance));
                shownVillageIds.add(village.getId());
            });
        }

        for (Village village : villageManager.getAllVillages()) {
            if (shownVillageIds.contains(village.getId())) continue;
            String currencyId = currencyService.getVillageCurrencyId(village);
            double villageBalance = currencyService.getBalance(balanceTargetId, currencyId);
            if (villageBalance == 0.0) continue;
            String currencyName = currencyService.getVillageCurrencyDisplayName(village);
            msg(player, "&7Dorf-Guthaben (&e" + village.getName() + "&7, " + currencyName + "): &a" + fmt(villageBalance));
        }
        return true;
    }

    private boolean handleBalances(Player player, String[] args) {
        if (!player.hasPermission("village.admin")) {
            msg(player, "&cKeine Berechtigung.");
            return true;
        }
        msg(player, "&6=== Globale Guthaben ===");
        for (Map.Entry<UUID, Double> e : currencyService.getBalancesForCurrency(currencyService.getGlobalCurrencyId()).entrySet()) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            msg(player, "&e" + (name != null ? name : e.getKey()) + "&7: &a" + fmt(e.getValue()));
        }
        return true;
    }

    private String fmt(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private void msg(Player p, String m) {
        MessageUtil.send(p, config.getPrefix(), m);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        if ("sendmoney".equals(cmd) && args.length == 1) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return out;
        }
        if ("balance".equals(cmd) && args.length == 1 && sender.hasPermission("village.admin")) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return out;
        }
        return List.of();
    }

    private record PendingGlobalTransfer(UUID targetId, double amount) {}
}
