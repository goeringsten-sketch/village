package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.hook.VaultHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.Quest;
import com.example.village.model.Village;
import com.example.village.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Verwaltet Quests für Villager.
 */
public class QuestManager {
    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final VaultHook vaultHook;
    private final CurrencyService currencyService;
    private final Map<String, Quest> questTemplates = new HashMap<>();
    private final Map<UUID, Map<String, Quest>> activePlayerQuests = new HashMap<>();
    private final Map<UUID, Set<String>> completedQuestIds = new HashMap<>();
    private final Map<UUID, Integer> villagerFeedProgress = new HashMap<>();
    private final Map<UUID, Integer> tradeProgress = new HashMap<>();
    private final Map<UUID, Integer> recruitProgress = new HashMap<>();

    private final File questDataFile;
    private LocalDate lastDailyReset = LocalDate.now();

    public QuestManager(VillagePlugin plugin, VillageManager villageManager, VaultHook vaultHook, CurrencyService currencyService) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.vaultHook = vaultHook;
        this.currencyService = currencyService;
        this.questDataFile = new File(plugin.getDataFolder(), "quest-progress.yml");
        loadQuestsFromConfig();
        loadPlayerQuestData();
    }

    private String msg(String key, String fallback) {
        return plugin.getConfigManager().text("messages." + key, fallback);
    }

    private String msg(String key, String fallback, Map<String, String> placeholders) {
        String text = msg(key, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return text;
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
            quest.setObjectiveType(config.getString("objective-type", ""));
            quest.setObjectiveCount(config.getInt("objective-count", 1));
            quest.setPriority(config.getInt("priority", 3));

            ConfigurationSection itemsSection = config.getConfigurationSection("reward.items");
            if (itemsSection != null) {
                for (String material : itemsSection.getKeys(false)) {
                    quest.addRewardItem(material, itemsSection.getInt(material));
                }
            }
            ConfigurationSection reqItemsSection = config.getConfigurationSection("required-items");
            if (reqItemsSection != null) {
                for (String material : reqItemsSection.getKeys(false)) {
                    quest.addRequiredItem(material, reqItemsSection.getInt(material));
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

        for (Map.Entry<UUID, Set<String>> e : completedQuestIds.entrySet()) {
            cfg.set("completed." + e.getKey().toString(), new ArrayList<>(e.getValue()));
        }

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

    private void checkDailyReset() {
        LocalDate today = LocalDate.now();
        if (today.equals(lastDailyReset)) {
            return;
        }
        for (Set<String> completed : completedQuestIds.values()) {
            completed.removeIf(id -> id.endsWith(":today"));
        }
        lastDailyReset = today;
        savePlayerQuestData();
    }

    // ===================== QUEST-VERGABE =====================

    public boolean assignQuestToPlayer(Player player, CustomVillager villager, String questId)
            throws QuestException {
        checkDailyReset();

        Quest template = questTemplates.get(questId);
        if (template == null) throw new QuestException("Quest nicht gefunden: " + questId);

        Village village = resolveVillage(villager, player);
        if (village == null) throw new QuestException("Kein Dorf gefunden");

        if (village.getLevel() < template.getRequiredVillageLevel()) {
            throw new QuestException("Dorf-Level " + template.getRequiredVillageLevel() + " erforderlich");
        }

        int playerRep = villager.getRelation(player.getUniqueId()).getReputation();
        if (playerRep < template.getMinReputation()) {
            throw new QuestException("Reputation zu niedrig (" + playerRep + " < " + template.getMinReputation() + ")");
        }

        Map<String, Quest> playerActive = activePlayerQuests.get(player.getUniqueId());
        if (playerActive != null && playerActive.containsKey(questId)) {
            throw new QuestException("Diese Quest ist bereits aktiv");
        }

        if (template.isDailyQuest()) {
            Set<String> completed = completedQuestIds.getOrDefault(player.getUniqueId(), Collections.emptySet());
            if (completed.contains(questId + ":today")) {
                throw new QuestException("Diese Tagesquest wurde heute schon absolviert");
            }
        } else {
            Set<String> completed = completedQuestIds.getOrDefault(player.getUniqueId(), Collections.emptySet());
            if (completed.contains(questId)) {
                throw new QuestException("Diese Quest wurde bereits abgeschlossen");
            }
        }

        Set<String> completed = completedQuestIds.getOrDefault(player.getUniqueId(), Collections.emptySet());
        for (String prereq : template.getPrerequisites()) {
            if (!completed.contains(prereq)) {
                Quest prereqQuest = questTemplates.get(prereq);
                String prereqName = prereqQuest != null ? prereqQuest.getTitle() : prereq;
                throw new QuestException("Voraussetzung fehlt: " + prereqName);
            }
        }

        Quest instance = cloneQuest(template);
        instance.setGiverVillagerId(villager.getId());
        instance.setAssignedPlayerId(player.getUniqueId());

        activePlayerQuests.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(questId, instance);

        savePlayerQuestData();
        return true;
    }

    // ===================== QUEST-ABSCHLUSS =====================

    public void completeQuest(Player player, Quest quest, CustomVillager villager, Village village)
            throws QuestException {
        checkDailyReset();

        if (!quest.getAssignedPlayerId().equals(player.getUniqueId())) {
            throw new QuestException("Diese Quest gehört nicht dir!");
        }

        Map<String, Quest> playerActive = activePlayerQuests.get(player.getUniqueId());
        if (playerActive == null || !playerActive.containsKey(quest.getQuestId())) {
            throw new QuestException("Quest ist nicht aktiv");
        }

        canCompleteQuest(player, quest);

        if ("collect-items".equals(quest.getObjectiveType())) {
            for (Map.Entry<String, Integer> entry : quest.getRequiredItems().entrySet()) {
                Material mat = Material.matchMaterial(entry.getKey().toUpperCase());
                if (mat == null) {
                    throw new QuestException("Ungültiges Quest-Item: " + entry.getKey());
                }
                if (!QuestItemHelper.removeMaterial(player.getInventory(), mat, entry.getValue())) {
                    throw new QuestException("Items konnten nicht abgezogen werden: " + mat.name());
                }
            }
        }

        if (quest.getRewardVillagePoints() > 0) {
            village.setPoints(village.getPoints() + quest.getRewardVillagePoints());
            villageManager.saveVillage(village);
        }

        if (quest.getRewardGlobalMoney() > 0) {
            if (vaultHook != null && vaultHook.isAvailable()) {
                vaultHook.deposit(player, quest.getRewardGlobalMoney());
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        msg("quest-reward-money-vault", "&a+%amount% erhalten!",
                                Map.of("amount", vaultHook.format(quest.getRewardGlobalMoney()))));
            } else {
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        msg("quest-reward-money-fallback", "&a+%amount% Münzen erhalten!",
                                Map.of("amount", String.format("%.0f", quest.getRewardGlobalMoney()))));
            }
        }
        if (quest.getRewardLocalMoney() > 0) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            currencyService.addBalance(player.getUniqueId(), localCurrencyId, quest.getRewardLocalMoney());
            MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                    msg("quest-reward-local-money", "&a+%amount% %currency% erhalten!",
                            Map.of("amount", String.format("%.2f", quest.getRewardLocalMoney()),
                                    "currency", currencyService.getVillageCurrencyDisplayName(village))));
        }

        for (Map.Entry<String, Integer> entry : quest.getRewardItems().entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey().toUpperCase());
            if (mat != null) {
                ItemStack reward = new ItemStack(mat, entry.getValue());
                player.getInventory().addItem(reward);
            }
        }

        villager.getRelation(player.getUniqueId()).addReputation(10);
        villager.getRelation(player.getUniqueId()).completeQuest();
        if (quest.getRewardVillagerXp() > 0) {
            villager.addXp(quest.getRewardVillagerXp());
        }

        playerActive.remove(quest.getQuestId());
        if (playerActive.isEmpty()) activePlayerQuests.remove(player.getUniqueId());

        Set<String> playerCompleted = completedQuestIds.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (quest.isDailyQuest()) {
            playerCompleted.add(quest.getQuestId() + ":today");
        } else {
            playerCompleted.add(quest.getQuestId());
        }

        resetProgressForQuest(player.getUniqueId(), quest);

        savePlayerQuestData();
        if (villager.getParentVillageId() != null) {
            villageManager.getVillage(villager.getParentVillageId()).ifPresent(villageManager::saveVillage);
        }
        plugin.getLogger().info(player.getName() + " hat Quest \"" + quest.getTitle() + "\" abgeschlossen!");
    }

    private void resetProgressForQuest(UUID playerId, Quest quest) {
        String type = quest.getObjectiveType();
        if ("feed-villagers".equals(type)) {
            villagerFeedProgress.remove(playerId);
        } else if ("trade".equals(type)) {
            tradeProgress.remove(playerId);
        } else if ("recruit-villager".equals(type)) {
            recruitProgress.remove(playerId);
        }
    }

    // ===================== FORTSCHRITT =====================

    public void recordVillagerFeed(Player player, CustomVillager villager) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        int count = villagerFeedProgress.merge(playerId, 1, Integer::sum);
        notifyProgress(player, "feed-villagers", count);
    }

    public void recordTrade(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        int count = tradeProgress.merge(playerId, 1, Integer::sum);
        notifyProgress(player, "trade", count);
    }

    public void recordRecruitment(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        int count = recruitProgress.merge(playerId, 1, Integer::sum);
        notifyProgress(player, "recruit-villager", count);
    }

    private void notifyProgress(Player player, String objectiveType, int count) {
        Map<String, Quest> active = activePlayerQuests.get(player.getUniqueId());
        if (active == null) return;
        for (Quest quest : active.values()) {
            if (!objectiveType.equals(quest.getObjectiveType())) continue;
            if (count >= quest.getObjectiveCount()) {
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        msg("quest-goal-reached", "&aQuest-Ziel erreicht: &f%title% &7(%current%/%total%)",
                                Map.of("title", quest.getTitle(),
                                        "current", String.valueOf(count),
                                        "total", String.valueOf(quest.getObjectiveCount()))));
            } else {
                String key = switch (objectiveType) {
                    case "feed-villagers" -> "quest-feed-progress";
                    case "trade" -> "quest-trade-progress";
                    case "recruit-villager" -> "quest-recruit-progress";
                    default -> "quest-feed-progress";
                };
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        msg(key, "&7Fortschritt: &e%current%&7/&e%total%",
                                Map.of("current", String.valueOf(count),
                                        "total", String.valueOf(quest.getObjectiveCount()))));
            }
        }
    }

    public int getVillagerFeedProgress(UUID playerId) {
        return villagerFeedProgress.getOrDefault(playerId, 0);
    }

    public int getTradeProgress(UUID playerId) {
        return tradeProgress.getOrDefault(playerId, 0);
    }

    public int getRecruitProgress(UUID playerId) {
        return recruitProgress.getOrDefault(playerId, 0);
    }

    // ===================== ABFRAGEN =====================

    public List<Quest> getAvailableQuests(Player player, CustomVillager villager, Village village) {
        checkDailyReset();
        Set<String> completed = completedQuestIds.getOrDefault(player.getUniqueId(), Collections.emptySet());
        Map<String, Quest> active = activePlayerQuests.getOrDefault(player.getUniqueId(), Collections.emptyMap());
        int rep = villager.getRelation(player.getUniqueId()).getReputation();

        return questTemplates.values().stream()
                .filter(q -> village.getLevel() >= q.getRequiredVillageLevel())
                .filter(q -> rep >= q.getMinReputation())
                .filter(q -> !active.containsKey(q.getQuestId()))
                .filter(q -> {
                    if (q.isDailyQuest()) {
                        return !completed.contains(q.getQuestId() + ":today");
                    }
                    return !completed.contains(q.getQuestId());
                })
                .filter(q -> q.getPrerequisites().stream().allMatch(completed::contains))
                .collect(Collectors.toList());
    }

    public List<Quest> getActiveQuestsForVillager(UUID playerId, UUID villagerId) {
        return activePlayerQuests.getOrDefault(playerId, Collections.emptyMap()).values().stream()
                .filter(q -> villagerId.equals(q.getGiverVillagerId()))
                .collect(Collectors.toList());
    }

    public Map<String, Quest> getActivePlayerQuests(UUID playerId) {
        return Collections.unmodifiableMap(
                activePlayerQuests.getOrDefault(playerId, Collections.emptyMap()));
    }

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

    public boolean canCompleteFeedQuest(Player player, Quest quest) {
        if (!"feed-villagers".equals(quest.getObjectiveType())) {
            return true;
        }
        return getVillagerFeedProgress(player.getUniqueId()) >= quest.getObjectiveCount();
    }

    public void canCompleteQuest(Player player, Quest quest) throws QuestException {
        String type = quest.getObjectiveType();
        if (type == null || type.isBlank()) {
            throw new QuestException(msg("quest-unknown-objective",
                    "&cDiese Quest hat keinen gültigen Objective-Typ."));
        }

        UUID playerId = player.getUniqueId();
        switch (type) {
            case "feed-villagers" -> {
                int count = getVillagerFeedProgress(playerId);
                if (count < quest.getObjectiveCount()) {
                    throw new QuestException(msg("quest-goal-not-reached",
                            "&cQuest-Ziel noch nicht erreicht: %current%/%total% Fütterungen",
                            Map.of("current", String.valueOf(count),
                                    "total", String.valueOf(quest.getObjectiveCount()))));
                }
            }
            case "collect-items" -> {
                for (Map.Entry<String, Integer> entry : quest.getRequiredItems().entrySet()) {
                    Material mat = Material.matchMaterial(entry.getKey().toUpperCase());
                    if (mat == null) {
                        throw new QuestException("Ungültiges Quest-Item: " + entry.getKey());
                    }
                    int count = QuestItemHelper.countMaterial(player.getInventory(), mat);
                    if (count < entry.getValue()) {
                        throw new QuestException(msg("quest-collect-items-missing",
                                "&cDu benötigst %amount%x %material% (besitzt: %has%)",
                                Map.of("amount", String.valueOf(entry.getValue()),
                                        "material", mat.name(),
                                        "has", String.valueOf(count))));
                    }
                }
            }
            case "trade" -> {
                int count = getTradeProgress(playerId);
                if (count < quest.getObjectiveCount()) {
                    throw new QuestException(msg("quest-trade-not-reached",
                            "&cQuest-Ziel noch nicht erreicht: %current%/%total% Handelsvorgänge",
                            Map.of("current", String.valueOf(count),
                                    "total", String.valueOf(quest.getObjectiveCount()))));
                }
            }
            case "recruit-villager" -> {
                int count = getRecruitProgress(playerId);
                if (count < quest.getObjectiveCount()) {
                    throw new QuestException(msg("quest-recruit-not-reached",
                            "&cQuest-Ziel noch nicht erreicht: %current%/%total% Rekrutierungen",
                            Map.of("current", String.valueOf(count),
                                    "total", String.valueOf(quest.getObjectiveCount()))));
                }
            }
            default -> throw new QuestException(msg("quest-unknown-objective",
                    "&cUnbekannter Quest-Typ: %type%", Map.of("type", type)));
        }
    }

    public Quest getQuestTemplate(String questId) {
        return questTemplates.get(questId);
    }

    public Quest getActiveQuest(UUID playerId, String questId) {
        Map<String, Quest> active = activePlayerQuests.get(playerId);
        if (active == null) return null;
        return active.get(questId);
    }

    // ===================== HILFSMETHODEN =====================

    private Village resolveVillage(CustomVillager villager, Player player) {
        if (villager.getParentVillageId() != null) {
            return villageManager.getVillage(villager.getParentVillageId()).orElse(null);
        }
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
        instance.setObjectiveType(template.getObjectiveType());
        instance.setObjectiveCount(template.getObjectiveCount());
        instance.setPriority(template.getPriority());
        instance.getRewardItems().putAll(template.getRewardItems());
        instance.getRequiredItems().putAll(template.getRequiredItems());
        template.getPrerequisites().forEach(instance::addPrerequisite);
        return instance;
    }

    public static class QuestException extends Exception {
        public QuestException(String message) {
            super(message);
        }
    }
}
