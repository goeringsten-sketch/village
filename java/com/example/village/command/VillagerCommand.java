package com.example.village.command;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerJob;
import com.example.village.model.Village;
import com.example.village.service.QuestManager;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerManager;
import com.example.village.util.MessageUtil;
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

        String jobName = args[1];
        VillagerJob job = VillagerJob.fromString(jobName);
        if (job == VillagerJob.LABORER && !"none".equalsIgnoreCase(jobName) && !"laborer".equalsIgnoreCase(jobName)) {
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
            player.sendMessage(prefix + "§cNutzung: /villager quest <list|accept|complete> [QuestId] [VillagerName]");
            return false;
        }

        String action = args[1].toLowerCase();

        if ("list".equals(action)) {
            var quests = questManager.getPlayerQuests(player.getUniqueId());
            player.sendMessage(prefix + "§eDeine aktiven Quests:");
            if (quests.isEmpty()) {
                player.sendMessage("§7  (keine aktiven Quests)");
            }
            for (com.example.village.model.Quest quest : quests) {
                player.sendMessage("§7  - " + quest.getTitle() + " §8(" + quest.getQuestId() + ", "
                        + quest.getRewardVillagePoints() + " Punkte)");
            }
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(prefix + "§cNutzung: /villager quest " + action + " <QuestId> [VillagerName]");
            return false;
        }

        String questId = args[2];

        if ("accept".equals(action)) {
            CustomVillager villager = resolveQuestVillager(player, args.length > 3 ? args[3] : null);
            if (villager == null) {
                player.sendMessage(prefix + "§cKein Villager gefunden. Interagiere zuerst mit einem Villager oder gib den Namen an.");
                return false;
            }
            try {
                questManager.assignQuestToPlayer(player, villager, questId);
                var template = questManager.getQuestTemplate(questId);
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        plugin.getConfigManager().text("messages.quest-accepted", "&aQuest angenommen: &e%title%")
                                .replace("%title%", template != null ? template.getTitle() : questId));
            } catch (QuestManager.QuestException e) {
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(), e.getMessage());
            }
            return true;
        }

        if ("complete".equals(action)) {
            com.example.village.model.Quest quest = questManager.getActiveQuest(player.getUniqueId(), questId);
            if (quest == null) {
                player.sendMessage(prefix + "§cDiese Quest ist nicht aktiv.");
                return false;
            }
            CustomVillager villager = null;
            if (quest.getGiverVillagerId() != null) {
                villager = advancedVillagerManager.getVillager(quest.getGiverVillagerId());
            }
            if (villager == null) {
                villager = resolveQuestVillager(player, args.length > 3 ? args[3] : null);
            }
            if (villager == null) {
                player.sendMessage(prefix + "§cQuestgeber-Villager nicht gefunden.");
                return false;
            }
            Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
            if (village == null && villager.getParentVillageId() != null) {
                village = villageManager.getVillage(villager.getParentVillageId()).orElse(null);
            }
            if (village == null) {
                player.sendMessage(prefix + "§cKein Dorf gefunden.");
                return false;
            }
            try {
                questManager.completeQuest(player, quest, villager, village);
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        plugin.getConfigManager().text("messages.quest-completed", "&aQuest abgeschlossen: &e%title%")
                                .replace("%title%", quest.getTitle()));
            } catch (QuestManager.QuestException e) {
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(), e.getMessage());
            }
            return true;
        }

        player.sendMessage(prefix + "§cUnbekannte Quest-Aktion!");
        return false;
    }

    private CustomVillager resolveQuestVillager(Player player, String villagerName) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            return null;
        }
        if (villagerName != null && !villagerName.isBlank()) {
            return village.getVillagers().stream()
                    .filter(v -> v.getName().equalsIgnoreCase(villagerName))
                    .findFirst()
                    .orElse(null);
        }
        if (plugin.getVillagerGuiClickListener() != null) {
            UUID villagerId = plugin.getVillagerGuiClickListener().getLastInteractedVillager(player.getUniqueId());
            if (villagerId != null) {
                CustomVillager villager = advancedVillagerManager.getVillager(villagerId);
                if (villager != null) {
                    return villager;
                }
            }
        }
        return village.getVillagers().stream().findFirst().orElse(null);
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
        sender.sendMessage("§7Jobs: " + String.join(", ", VillagerJob.selectableJobs().stream()
                .map(job -> job.getProfessionKey().toUpperCase(java.util.Locale.ROOT))
                .toList()));
        sender.sendMessage("§e/villager list §7- Listet Dorfbewohner auf");
        sender.sendMessage("§e/villager info <Name> §7- Info anzeigen");
        sender.sendMessage("§e/villager remove <Name> §7- Verjagt einen Villager");
        sender.sendMessage("§e/villager quest <list|accept|complete> [QuestId] [VillagerName] §7- Quest-Verwaltung");
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
            return VillagerJob.selectableJobs().stream()
                    .map(job -> job.getProfessionKey().toUpperCase(java.util.Locale.ROOT))
                    .toList();
        }

        if (args.length == 2 && "quest".equals(args[0])) {
            return Arrays.asList("list", "accept", "complete");
        }

        if (args.length == 3 && "quest".equals(args[0])) {
            if ("accept".equals(args[1]) || "complete".equals(args[1])) {
                return questManager.getQuestTemplates().stream()
                        .map(q -> q.getQuestId())
                        .toList();
            }
        }

        return List.of();
    }
}
