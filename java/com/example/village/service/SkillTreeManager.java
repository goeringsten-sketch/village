package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.hook.VaultHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerSkill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Verwaltet Skill Tree und Progression.
 */
public class SkillTreeManager {
    private final VillagePlugin plugin;
    private final Map<String, SkillLevelReq> skillRequirements;
    private final VaultHook vaultHook;

    public SkillTreeManager(VillagePlugin plugin, VaultHook vaultHook) {
        this.plugin = plugin;
        this.vaultHook = vaultHook;
        this.skillRequirements = new HashMap<>();
        loadSkillRequirements();
    }

    /**
     * Lädt Skill-Level-Anforderungen aus Config
     */
    private void loadSkillRequirements() {
        ConfigurationSection skillsSection = null;
        File skillFile = new File(plugin.getDataFolder(), "config/quests-and-villagers.yml");
        if (skillFile.exists()) {
            skillsSection = YamlConfiguration.loadConfiguration(skillFile).getConfigurationSection("skills");
        }
        if (skillsSection == null) {
            skillsSection = plugin.getConfig().getConfigurationSection("skills");
        }
        if (skillsSection == null) return;

        for (String skillName : skillsSection.getKeys(false)) {
            ConfigurationSection skill = skillsSection.getConfigurationSection(skillName);
            if (skill == null) continue;

            int maxLevel = skill.getInt("max-level", 20);
            double baseCost = skill.contains("base-cost.local")
                    ? skill.getDouble("base-cost.local", 100)
                    : skill.getDouble("base-cost", 100);
            skillRequirements.put(skillName, new SkillLevelReq(skillName, maxLevel, baseCost));
        }

        plugin.getLogger().info("✓ " + skillRequirements.size() + " Skills geladen");
    }

    /**
     * Upgradet einen Skill um ein Level
     */
    public boolean upgradeSkill(CustomVillager villager, String skillName) throws SkillUpgradeException {
        VillagerSkill skill = villager.getSkill(skillName);
        if (skill == null) {
            throw new SkillUpgradeException("Skill nicht gefunden: " + skillName);
        }

        SkillLevelReq req = skillRequirements.get(skillName);
        if (req == null) {
            throw new SkillUpgradeException("Skill-Anforderungen nicht definiert: " + skillName);
        }

        // Max Level prüfen
        if (skill.getLevel() >= req.maxLevel) {
            throw new SkillUpgradeException("Max Level " + req.maxLevel + " erreicht!");
        }

        // Kosten berechnen (exponentiell)
        double cost = req.baseCost * Math.pow(1.1, skill.getLevel());
        
        // Geld prüfen
        if (villager.getWallet() < cost) {
            throw new SkillUpgradeException(
                "Nicht genug Geld. Benötigt: " + String.format("%.0f", cost)
            );
        }

        // Upgrade durchführen
        villager.addMoney(-cost);
        skill.addXp(skill.getXpToNextLevel());  // Auf nächstes Level

        return true;
    }

    /**
     * Gibt passive Boni des Villagers zurück
     */
    public SkillBonus getSkillBonus(CustomVillager villager) {
        SkillBonus bonus = new SkillBonus();

        for (VillagerSkill skill : villager.getSkills().values()) {
            double levelBonus = skill.getLevel() * 0.05;  // 5% pro Level

            switch (skill.getSkillName()) {
                case "Anbau" -> bonus.productionBonus += levelBonus;
                case "Ernte" -> bonus.harvestBonus += levelBonus;
                case "Effizienz" -> bonus.efficiencyBonus += levelBonus;
                case "Verhandlung" -> bonus.tradeBonus += levelBonus;
                case "Preise" -> bonus.priceBonus += levelBonus;
                case "Bestand" -> bonus.inventoryBonus += levelBonus;
                case "Kampf" -> bonus.combatBonus += levelBonus;
                case "Reichweite" -> bonus.rangeBonus += levelBonus;
                case "Reflexe" -> bonus.reflexBonus += levelBonus;
                case "Wissen" -> bonus.knowledgeBonus += levelBonus;
                case "Forschung" -> bonus.researchBonus += levelBonus;
                case "Speicherung" -> bonus.storageBonus += levelBonus;
            }
        }

        return bonus;
    }

    /**
     * Gibt Skill-Beschreibung aus
     */
    public String getSkillDescription(String skillName) {
        return switch (skillName) {
            case "Anbau" -> "§eErhöht Anbau-Effizienzen.";
            case "Ernte" -> "§eErhöht Ernte-Ausbeute.";
            case "Effizienz" -> "§eReduziert Bearbeitungszeit.";
            case "Verhandlung" -> "§eVerbessert NPC-Handel.";
            case "Preise" -> "§eGesamt bessere Preise im Handel.";
            case "Bestand" -> "§eErhöht Handels-Bestand.";
            case "Kampf" -> "§eErhöht Kampf-Schaden.";
            case "Reichweite" -> "§eErhöht Kampf-Reichweite.";
            case "Reflexe" -> "§eErhöht Ausweichfähigkeit.";
            case "Wissen" -> "§eErhöht Forschungs-Tempo.";
            case "Forschung" -> "§eFreischaft neue Upgrades.";
            case "Speicherung" -> "§eErhöht Lager-Kapazität.";
            default -> "§7Unbekannter Skill";
        };
    }

    /**
     * Datenklasse für Skill-Anforderungen
     */
    public static class SkillLevelReq {
        public String skillName;
        public int maxLevel;
        public double baseCost;

        public SkillLevelReq(String skillName, int maxLevel, double baseCost) {
            this.skillName = skillName;
            this.maxLevel = maxLevel;
            this.baseCost = baseCost;
        }
    }

    /**
     * Passive Boni-Berechnung
     */
    public static class SkillBonus {
        public double productionBonus = 0;
        public double harvestBonus = 0;
        public double efficiencyBonus = 0;
        public double tradeBonus = 0;
        public double priceBonus = 0;
        public double inventoryBonus = 0;
        public double combatBonus = 0;
        public double rangeBonus = 0;
        public double reflexBonus = 0;
        public double knowledgeBonus = 0;
        public double researchBonus = 0;
        public double storageBonus = 0;
    }

    public static class SkillUpgradeException extends Exception {
        public SkillUpgradeException(String message) {
            super(message);
        }
    }
}
