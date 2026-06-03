package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.DialogLine;
import com.example.village.model.VillagerState;
import com.example.village.model.VillagerJob;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Dialog- und Nachrichtensystem für Villager.
 */
public class DialogueSystem {
    private final VillagePlugin plugin;
    private final Map<String, List<DialogLine>> dialogues;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public DialogueSystem(VillagePlugin plugin) {
        this.plugin = plugin;
        this.dialogues = new HashMap<>();
        loadDialoguesFromConfig();
    }

    /**
     * Lädt Dialoge aus messages.yml/config.yml
     */
    private void loadDialoguesFromConfig() {
        ConfigurationSection dialogSection = plugin.getConfig().getConfigurationSection("messages.dialogues");
        if (dialogSection == null) {
            plugin.getLogger().warning("Keine Dialoge in config.yml definiert!");
            return;
        }

        for (String context : dialogSection.getKeys(false)) {
            List<DialogLine> contextDialogues = new ArrayList<>();
            ConfigurationSection contextSection = dialogSection.getConfigurationSection(context);
            
            if (contextSection != null) {
                for (String key : contextSection.getKeys(false)) {
                    List<String> variants = contextSection.getStringList(key);
                    DialogLine line = new DialogLine(key, context);
                    variants.forEach(line::addVariant);
                    contextDialogues.add(line);
                }
            }
            
            dialogues.put(context, contextDialogues);
        }

        plugin.getLogger().info("✓ Dialoge geladen");
    }

    /**
     * Gibt eine zufällige Dialog-Zeile zurück
     */
    public String getDialogue(CustomVillager villager, String context, Player player) {
        List<DialogLine> options = dialogues.getOrDefault(context, new ArrayList<>());
        if (options.isEmpty()) {
            return "...";
        }

        // Filtern nach Reputation, Job, State
        DialogLine selected = options.stream()
                .filter(d -> villager.getRelation(player.getUniqueId()).getReputation() 
                          >= d.getMinReputation())
                .filter(d -> d.getRequiredJob() == null || d.getRequiredJob() == villager.getJob())
                .filter(d -> d.getRequiredState() == null || d.getRequiredState() == villager.getCurrentState())
                .findAny()
                .orElse(options.get(0));

        String message = selected.getRandomVariant();
        return replacePlaceholders(message, villager, player);
    }

    /**
     * Ersetzt Platzhalter in Nachrichten
     */
    private String replacePlaceholders(String message, CustomVillager villager, Player player) {
        return message
                .replace("{villager_name}", villager.getName())
                .replace("{player_name}", player.getName())
                .replace("{job}", villager.getJob().getDisplayName())
                .replace("{state}", villager.getCurrentState().getDisplayName())
                .replace("{hunger}", String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER)))
                .replace("{morale}", String.format("%.0f", villager.getMorale()))
                .replace("{reputation}", String.valueOf(villager.getRelation(player.getUniqueId()).getReputation()));
    }

    /**
     * Gibt passende Chat-Antwort für Spieler-Input
     */
    public String respondToPlayer(CustomVillager villager, Player player, String input) {
        String normalized = input.toLowerCase().trim();

        if (normalized.contains("hi") || normalized.contains("hello") || normalized.contains("hallo")) {
            return getDialogue(villager, "GREETING", player);
        } else if (normalized.contains("bye") || normalized.contains("auf wiedersehen")) {
            return getDialogue(villager, "GOODBYE", player);
        } else if (normalized.contains("help") || normalized.contains("hilfe")) {
            return getDialogue(villager, "HELP", player);
        } else if (normalized.contains("quest")) {
            return getDialogue(villager, "QUEST_OFFER", player);
        } else {
            return getDialogue(villager, "IDLE_CHAT", player);
        }
    }

    /**
     * NPC reagiert auf Spieler-Nähe mit Sätzen
     */
    public String getGreetingForNearbyPlayer(CustomVillager villager, Player player) {
        // Nur wenn der Villager nicht beschäftigt ist
        if (villager.getCurrentState() == VillagerState.WORKING ||
            villager.getCurrentState() == VillagerState.SLEEPING) {
            return null;
        }

        return getDialogue(villager, "GREETING", player);
    }

    /**
     * Gibt Nachrichten für spezielle Situationen
     */
    public String getContextDialogue(CustomVillager villager, Player player, VillagerState state) {
        return switch (state) {
            case EATING -> getDialogue(villager, "HUNGRY", player);
            case SLEEPING -> getDialogue(villager, "SLEEPING", player);
            case WORKING -> getDialogue(villager, "WORKING", player);
            case FLEEING -> getDialogue(villager, "FLEEING", player);
            case TRADING -> getDialogue(villager, "TRADING", player);
            default -> getDialogue(villager, "IDLE_CHAT", player);
        };
    }

    public String getDefaultGreeting(CustomVillager villager, Player player) {
        return getDialogue(villager, "GREETING", player);
    }

    public String getQuestOffer(CustomVillager villager, Player player) {
        return getDialogue(villager, "QUEST_OFFER", player);
    }

    public String getComplaintDialogue(CustomVillager villager, Player player) {
        return getDialogue(villager, "COMPLAINT", player);
    }

    public String getHappyDialogue(CustomVillager villager, Player player) {
        return getDialogue(villager, "HAPPY", player);
    }

    // Konstanten für Context
    public static final String CONTEXT_GREETING = "GREETING";
    public static final String CONTEXT_GOODBYE = "GOODBYE";
    public static final String CONTEXT_HUNGRY = "HUNGRY";
    public static final String CONTEXT_WORKING = "WORKING";
    public static final String CONTEXT_SLEEPING = "SLEEPING";
    public static final String CONTEXT_QUEST_OFFER = "QUEST_OFFER";
    public static final String CONTEXT_IDLE = "IDLE_CHAT";
}
