package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.hook.VaultHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.Quest;
import com.example.village.model.Village;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Verwaltet Quests für Villager.
 * Behebt: Village-Lookup via parentVillageId, Vault-Integration, Persistenz der Spieler-Quests.
 */
public class QuestManager {
    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final VaultHook vaultHook;
    private final CurrencyService currencyService;
    private final Map<String, Quest> questTemplates = new HashMap<>();
    // UUID -> Quests (aktiv + abgeschlossen werden getrennt gespeichert)
    private final Map<UUID, Map<String, Quest>> activePlayerQuests = new HashMap<>();
    private final Map<UUID, Set<String>> completedQuestIds = new HashMap<>();

    private final File questDataFile;

    public QuestManager(VillagePlugin plugin, VillageManager villageManager, VaultHook vaultHook, CurrencyService currencyService) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.vaultHook = vaultHook;
        this.currencyService = currencyService;
        this.questDataFile = new File(plugin.getDataFolder(), "quest-progress.yml");
        loadQuestsFromConfig();
        loadPlayerQuestData();
    }

    // ===================== LADEN =====================

    private void loadQuestsFromConfig() {
        ConfigurationSection questSection = null;
        try {
            File questFile = new File(plugin.getDataFolder(), "config/quests-and-villagers.yml");
            if (!questFile.exists()) {
                File oldFile = new File(plugin.getDataFolder(), "quests-and-villagers.yml");
                if (oldFile.exists()) questFile = oldFile;
            }
            if (questFile.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(questFile);
                questSection = cfg.getConfigurationSection("quests");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Laden der quests-and-villagers.yml: " + e.getMessage());
        }

        if (questSection == null) {
            plugin.getLogger().warning("Keine Quests definiert!");
            return;
        }

        for (String questId : questSection.getKeys(false)) {
            ConfigurationSection config = questSection.getConfigurationSection(questId);
            if (config == null) continue;

            Quest quest = new Quest(questId, config.getString("title", questId));
            quest.setDescription(config.getString("description", ""));
            quest.setObjective(config.getString("objective", ""));
            quest.setRequiredVillageLevel(config.getInt("required-village-level", 1));
            quest.setMinReputation(config.getInt("min-reputation", -100));
            quest.setRewardVillagePoints(config.getInt("reward.village-points", 10));
            double rewardGlobal = config.contains("reward.money.global")
                    ? config.getDouble("reward.money.global", 0)
                    : config.getDouble("reward.money", 0);
            double rewardLocal = config.getDouble("reward.money.local", 0);
            quest.setRewardGlobalMoney(rewardGlobal);
            quest.setRewardLocalMoney(rewardLocal);
            quest.setRewardVillagerXp(config.getInt("reward.villager-xp", 5));
            quest.setDailyQuest(config.getBoolean("is-daily", false));

            ConfigurationSection itemsSection = config.getConfigurationSection("reward.items");
            if (itemsSection != null) {
                for (String material : itemsSection.getKeys(false)) {
                    quest.addRewardItem(material, itemsSection.getInt(material));
                }
            }
            config.getStringList("prerequisites").forEach(quest::addPrerequisite);
            questTemplates.put(questId, quest);
        }
        plugin.getLogger().info("✓ " + questTemplates.size() + " Quests geladen");
    }

    private void loadPlayerQuestData() {
        if (!questDataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(questDataFile);

        // Abgeschlossene Quests laden
        ConfigurationSection completedSec = cfg.getConfigurationSection("completed");
        if (completedSec != null) {
            for (String uuidStr : completedSec.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    List<String> ids = completedSec.getStringList(uuidStr);
                    completedQuestIds.put(playerId, new HashSet<>(ids));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Aktive Quests laden
        ConfigurationSection activeSec = cfg.getConfigurationSection("active");
        if (activeSec != null) {
            for (String uuidStr : activeSec.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    ConfigurationSection playerSec = activeSec.getConfigurationSection(uuidStr);
                    if (playerSec == null) continue;
                    Map<String, Quest> playerActive = new HashMap<>();
                    for (String questId : playerSec.getKeys(false)) {
                        Quest template = questTemplates.get(questId);
                        if (template == null) continue;
                        Quest instance = cloneQuest(template);
                        ConfigurationSection qs = playerSec.getConfigurationSection(questId);
                        if (qs != null) {
                            String giver = qs.getString("giver");
                            if (giver != null) instance.setGiverVillagerId(UUID.fromString(giver));
                            instance.setAssignedPlayerId(playerId);
                        }
                        playerActive.put(questId, instance);
                    }
                    if (!playerActive.isEmpty()) activePlayerQuests.put(playerId, playerActive);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info("✓ Quest-Fortschritte geladen");
    }

    public void savePlayerQuestData() {
        YamlConfiguration cfg = new YamlConfiguration();

        // Abgeschlossene Quests
        for (Map.Entry<UUID, Set<String>> e : completedQuestIds.entrySet()) {
            cfg.set("completed." + e.getKey().toString(), new ArrayList<>(e.getValue()));
        }

        // Aktive Quests
        for (Map.Entry<UUID, Map<String, Quest>> e : activePlayerQuests.entrySet()) {
            String base = "active." + e.getKey();
            for (Map.Entry<String, Quest> qe : e.getValue().entrySet()) {
                Quest q = qe.getValue();
                cfg.set(base + "." + q.getQuestId() + ".title", q.getTitle());
                if (q.getGiverVillagerId() != null) {
                    cfg.set(base + "." + q.getQuestId() + ".giver", q.getGiverVillagerId().toString());
                }
            }
        }

        try {
            cfg.save(questDataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Fehler beim Speichern der Quest-Fortschritte: " + e.getMessage());
        }
    }

    // ===================== QUEST-VERGABE =====================

    /**
     * Gibt einem Spieler eine Quest - mit vollständigem Village-Lookup und allen Prüfungen.
     */
    public boolean assignQuestToPlayer(Player player, CustomVillager villager, String questId)
            throws QuestException {

        Quest template = questTemplates.get(questId);
        if (template == null) throw new QuestException("Quest nicht gefunden: " + questId);

        // Village-Lookup über parentVillageId des Villagers
        Village village = resolveVillage(villager, player);
        if (village == null) throw new QuestException("Kein Dorf gefunden");

        // Voraussetzungen prüfen
        if (village.getLevel() < template.getRequiredVillageLevel()) {
            throw new QuestException("Dorf-Level " + template.getRequiredVillageLevel() + " erforderlich");
        }

        int playerRep = villager.getRelation(player.getUniqueId()).getReputation();
        if (playerRep < template.getMinReputation()) {
            throw new QuestException("Reputation zu niedrig (" + playerRep + " < " + template.getMinReputation() + ")");
        }

        // Schon aktiv?
        Map<String, Quest> playerActive = activePlayerQuests.get(player.getUniqueId());
        if (playerActive != null && playerActive.containsKey(questId)) {
            throw new QuestException("Diese Quest ist bereits aktiv");
        }

        // Täglliche Quests: Cooldown prüfen
        if (template.isDailyQuest()) {
            Set<String> completed = completedQuestIds.getOrDefault(player.getUniqueId(), Collections.emptySet());
            if (completed.contains(questId + ":today")) {
                throw new QuestException("Diese Tagesquest wurde heute schon absolviert");
            }
        }

        // Voraussetzungen (Prerequisites) prüfen
        Set<String> completed = completedQuestIds.getOrDefault(player.getUniqueId(), Collections.emptySet());
        for (String prereq : template.getPrerequisites()) {
            if (!completed.contains(prereq)) {
                Quest prereqQuest = questTemplates.get(prereq);
                String prereqName = prereqQuest != null ? prereqQuest.getTitle() : prereq;
                throw new QuestException("Voraussetzung fehlt: " + prereqName);
            }
        }

        // Quest-Instanz anlegen
        Quest instance = cloneQuest(template);
        instance.setGiverVillagerId(villager.getId());
        instance.setAssignedPlayerId(player.getUniqueId());

        activePlayerQuests.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(questId, instance);

        savePlayerQuestData();
        return true;
    }

    // ===================== QUEST-ABSCHLUSS =====================

    /**
     * Schließt eine Quest ab und vergibt alle Belohnungen.
     */
    public void completeQuest(Player player, Quest quest, CustomVillager villager, Village village)
            throws QuestException {

        if (!quest.getAssignedPlayerId().equals(player.getUniqueId())) {
            throw new QuestException("Diese Quest gehört nicht dir!");
        }

        Map<String, Quest> playerActive = activePlayerQuests.get(player.getUniqueId());
        if (playerActive == null || !playerActive.containsKey(quest.getQuestId())) {
            throw new QuestException("Quest ist nicht aktiv");
        }

        // === BELOHNUNGEN ===

        // 1. Dorfpunkte
        if (quest.getRewardVillagePoints() > 0) {
            village.setPoints(village.getPoints() + quest.getRewardVillagePoints());
        }

        // 2. Geld via Vault
        if (quest.getRewardGlobalMoney() > 0) {
            if (vaultHook != null && vaultHook.isAvailable()) {
                vaultHook.deposit(player, quest.getRewardGlobalMoney());
                player.sendMessage("§a+" + vaultHook.format(quest.getRewardGlobalMoney()) + " erhalten!");
            } else {
                player.sendMessage("§a+" + String.format("%.0f", quest.getRewardGlobalMoney()) + " Münzen erhalten!");
            }
        }
        if (quest.getRewardLocalMoney() > 0) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            currencyService.addBalance(player.getUniqueId(), localCurrencyId, quest.getRewardLocalMoney());
            player.sendMessage("§a+" + String.format("%.2f", quest.getRewardLocalMoney()) + " " +
                    currencyService.getVillageCurrencyDisplayName(village) + " erhalten!");
        }

        // 3. Item-Belohnungen
        for (Map.Entry<String, Integer> entry : quest.getRewardItems().entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey().toUpperCase());
            if (mat != null) {
                ItemStack reward = new ItemStack(mat, entry.getValue());
                player.getInventory().addItem(reward);
            }
        }

        // 4. Villager-Beziehung & XP
        villager.getRelation(player.getUniqueId()).addReputation(10);
        villager.getRelation(player.getUniqueId()).completeQuest();
        if (quest.getRewardVillagerXp() > 0) {
            villager.addXp(quest.getRewardVillagerXp());
        }

        // Quest als abgeschlossen markieren
        playerActive.remove(quest.getQuestId());
        if (playerActive.isEmpty()) activePlayerQuests.remove(player.getUniqueId());

        completedQuestIds.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>())
                .add(quest.getQuestId());

        savePlayerQuestData();
        plugin.getLogger().info(player.getName() + " hat Quest \"" + quest.getTitle() + "\" abgeschlossen!");
    }

    // ===================== ABFRAGEN =====================

    /**
     * Verfügbare Quests für einen Spieler bei einem bestimmten Villager.
     */
    public List<Quest> getAvailableQuests(Player player, CustomVillager villager, Village village) {
        Set<String> completed = completedQuestIds.getOrDefault(player.getUniqueId(), Collections.emptySet());
        Map<String, Quest> active = activePlayerQuests.getOrDefault(player.getUniqueId(), Collections.emptyMap());
        int rep = villager.getRelation(player.getUniqueId()).getReputation();

        return questTemplates.values().stream()
                .filter(q -> village.getLevel() >= q.getRequiredVillageLevel())
                .filter(q -> rep >= q.getMinReputation())
                .filter(q -> !active.containsKey(q.getQuestId()))   // nicht bereits aktiv
                .filter(q -> !completed.contains(q.getQuestId()) || q.isDailyQuest()) // nicht bereits dauerhaft erledigt
                .filter(q -> q.getPrerequisites().stream().allMatch(completed::contains))
                .collect(Collectors.toList());
    }

    public Map<String, Quest> getActivePlayerQuests(UUID playerId) {
        return Collections.unmodifiableMap(
                activePlayerQuests.getOrDefault(playerId, Collections.emptyMap()));
    }

    /** Rückwärtskompatibilität – liefert die aktiven Quests als Set */
    public Set<Quest> getPlayerQuests(UUID playerId) {
        return new HashSet<>(activePlayerQuests
                .getOrDefault(playerId, Collections.emptyMap()).values());
    }

    public Set<String> getCompletedQuestIds(UUID playerId) {
        return Collections.unmodifiableSet(
                completedQuestIds.getOrDefault(playerId, Collections.emptySet()));
    }

    public Collection<Quest> getQuestTemplates() {
        return Collections.unmodifiableCollection(questTemplates.values());
    }

    public Quest getQuestTemplate(String questId) {
        return questTemplates.get(questId);
    }

    // ===================== HILFSMETHODEN =====================

    /**
     * Löst das Dorf eines Villagers auf – zuerst via parentVillageId, dann via Spieler-Dorf.
     */
    private Village resolveVillage(CustomVillager villager, Player player) {
        // Primär: direkte Village-Referenz des Villagers
        if (villager.getParentVillageId() != null) {
            return villageManager.getVillage(villager.getParentVillageId()).orElse(null);
        }
        // Fallback: Dorf des anfragenden Spielers
        return villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
    }

    private Quest cloneQuest(Quest template) {
        Quest instance = new Quest(template.getQuestId(), template.getTitle());
        instance.setDescription(template.getDescription());
        instance.setObjective(template.getObjective());
        instance.setRequiredVillageLevel(template.getRequiredVillageLevel());
        instance.setMinReputation(template.getMinReputation());
        instance.setRewardVillagePoints(template.getRewardVillagePoints());
        instance.setRewardGlobalMoney(template.getRewardGlobalMoney());
        instance.setRewardLocalMoney(template.getRewardLocalMoney());
        instance.setRewardVillagerXp(template.getRewardVillagerXp());
        instance.setDailyQuest(template.isDailyQuest());
        instance.getRewardItems().putAll(template.getRewardItems());
        template.getPrerequisites().forEach(instance::addPrerequisite);
        return instance;
    }

    public static class QuestException extends Exception {
        public QuestException(String message) {
            super(message);
        }
    }
}
