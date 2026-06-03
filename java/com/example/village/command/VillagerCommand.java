package com.example.village.command;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerJob;
import com.example.village.model.Village;
import com.example.village.service.QuestManager;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Command für Villager-Management: /villager
 */
public class VillagerCommand implements CommandExecutor, TabCompleter {
    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final VillagerManager advancedVillagerManager;
    private final QuestManager questManager;
    private final String prefix = "§8[§6Villager§8] §7";

    public VillagerCommand(VillagePlugin plugin, VillageManager villageManager,
                          VillagerManager advancedVillagerManager, QuestManager questManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.advancedVillagerManager = advancedVillagerManager;
        this.questManager = questManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + "§cNur Spieler können diesen Befehl verwenden.");
            return false;
        }

        if (args.length == 0) {
            return showHelp(sender);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "recruit":
                return handleRecruit(player, args);
            case "list":
                return handleList(player, args);
            case "info":
                return handleInfo(player, args);
            case "remove":
                return handleRemove(player, args);
            case "quest":
                return handleQuestCmd(player, args);
            case "skill":
                return handleSkillCmd(player, args);
            case "help":
                showHelp(sender);
                return true;
            default:
                sender.sendMessage(prefix + "§cUnbekannter Befehl. Nutze /villager help");
                return false;
        }
    }

    private boolean handleRecruit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(prefix + "§cNutzung: /villager recruit <JobName>");
            return false;
        }

        String jobName = args[1].toUpperCase();
        VillagerJob job;
        try {
            job = VillagerJob.valueOf(jobName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(prefix + "§cUnbekannter Job: " + jobName);
            return false;
        }

        // Spieler muss ein Dorf haben
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            player.sendMessage(prefix + "§cDu hast kein Dorf!");
            return false;
        }

        try {
            String villagerName = args.length > 2 ? args[2] : "Villager_" + System.currentTimeMillis() % 1000;
            CustomVillager villager = advancedVillagerManager.recruitVillager(
                player, village, job, villagerName,
                player.getLocation(),
                plugin.getConfig().getDouble("villager-costs.recruitment", 100)
            );

            player.sendMessage(prefix + "§aVillager §e" + villagerName + " §awurde rekrutiert!");
            return true;
        } catch (VillagerManager.RecruitmentException e) {
            player.sendMessage(prefix + "§c" + e.getMessage());
            return false;
        }
    }

    private boolean handleList(Player player, String[] args) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            player.sendMessage(prefix + "§cDu hast kein Dorf!");
            return false;
        }

        int count = 0;
        player.sendMessage(prefix + "§eDorfbewohner in &6" + village.getName() + "§e:");
        for (CustomVillager v : village.getVillagers()) {
            player.sendMessage("§7  - " + v.getName() + " §8(" + v.getJob().getDisplayName() + "§8) Lv." + v.getLevel());
            count++;
        }
        player.sendMessage(prefix + "§7Gesamt: " + count + "/" + village.getVillagers().size());
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(prefix + "§cNutzung: /villager info <VillagerName>");
            return false;
        }

        String villagerName = args[1];
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            player.sendMessage(prefix + "§cDu hast kein Dorf!");
            return false;
        }

        CustomVillager villager = village.getVillagers().stream()
                .filter(v -> v.getName().equalsIgnoreCase(villagerName))
                .findFirst()
                .orElse(null);

        if (villager == null) {
            player.sendMessage(prefix + "§cVillager nicht gefunden!");
            return false;
        }

        player.sendMessage("§b========== " + villager.getName() + " ==========");
        player.sendMessage("§7Job: " + villager.getJob().getDisplayName());
        player.sendMessage("§7Level: " + villager.getLevel() + " | XP: " + String.format("%.0f", villager.getXp()));
        player.sendMessage("§7Geld: §6" + String.format("%.0f", villager.getWallet()));
        player.sendMessage("§7Moral: §e" + String.format("%.0f", villager.getMorale()) + "%");
        player.sendMessage("§7Status: " + villager.getCurrentState().getDisplayName());
        player.sendMessage("§7Hunger: §c" + String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER)) + "%");
        player.sendMessage("§7Zufriedenheit: §a" + String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HAPPINESS)) + "%");
        player.sendMessage("§b============================");
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(prefix + "§cNutzung: /villager remove <VillagerName>");
            return false;
        }

        String villagerName = args[1];
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            player.sendMessage(prefix + "§cDu hast kein Dorf!");
            return false;
        }

        CustomVillager villager = village.getVillagers().stream()
                .filter(v -> v.getName().equalsIgnoreCase(villagerName))
                .findFirst()
                .orElse(null);

        if (villager == null) {
            player.sendMessage(prefix + "§cVillager nicht gefunden!");
            return false;
        }

        if (advancedVillagerManager.removeVillager(villager)) {
            player.sendMessage(prefix + "§cVillager §e" + villagerName + " §cwurde verjagt!");
            return true;
        }
        return false;
    }

    private boolean handleQuestCmd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(prefix + "§cNutzung: /villager quest <list|complete> [Villager]");
            return false;
        }

        String action = args[1].toLowerCase();

        if ("list".equals(action)) {
            var quests = questManager.getPlayerQuests(player.getUniqueId());
            player.sendMessage(prefix + "§eDeine Quests:");
            for (com.example.village.model.Quest quest : quests) {
                player.sendMessage("§7  - " + quest.getTitle() + " §8(" + quest.getRewardVillagePoints() + " Punkte)");
            }
            return true;
        }

        player.sendMessage(prefix + "§cUnbekannte Quest-Aktion!");
        return false;
    }

    private boolean handleSkillCmd(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(prefix + "§cNutzung: /villager skill <VillagerName> <SkillName>");
            return false;
        }

        String villagerName = args[1];
        String skillName = args[2];

        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            player.sendMessage(prefix + "§cDu hast kein Dorf!");
            return false;
        }

        CustomVillager villager = village.getVillagers().stream()
                .filter(v -> v.getName().equalsIgnoreCase(villagerName))
                .findFirst()
                .orElse(null);

        if (villager == null) {
            player.sendMessage(prefix + "§cVillager nicht gefunden!");
            return false;
        }

        try {
            var skillManager = plugin.getSkillTreeManager();
            if (skillManager.upgradeSkill(villager, skillName)) {
                player.sendMessage(prefix + "§aSkill §e" + skillName + " §awurde geupgradet!");
            }
        } catch (Exception e) {
            player.sendMessage(prefix + "§c" + e.getMessage());
            return false;
        }

        return false;
    }

    private boolean showHelp(CommandSender sender) {
        sender.sendMessage("§b========== Villager Befehle ==========");
        sender.sendMessage("§e/villager recruit <Job> [Name] §7- Rekrutiert einen Villager");
        sender.sendMessage("§e/villager list §7- Listet Dorfbewohner auf");
        sender.sendMessage("§e/villager info <Name> §7- Info anzeigen");
        sender.sendMessage("§e/villager remove <Name> §7- Verjagt einen Villager");
        sender.sendMessage("§e/villager quest <list> §7- Zeigt Quests");
        sender.sendMessage("§e/villager skill <Name> <Skill> §7- Upgrade Skill");
        sender.sendMessage("§b======================================");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("recruit", "list", "info", "remove", "quest", "skill", "help");
        }

        if (args.length == 2 && "recruit".equals(args[0])) {
            return Arrays.asList("FARMER", "MERCHANT", "GUARD", "LIBRARIAN", "MILLER", "PRIEST", "LABORER");
        }

        return List.of();
    }
}
