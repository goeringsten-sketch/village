package com.example.village.model;

import java.util.Set;

/**
 * Berufe für Villager mit zugehörigen Gebäudetypen und Fähigkeiten.
 */
public enum VillagerJob {
    FARMER("§fBauer", "§e[Bauer]", "farm", 
            Set.of("Anbau", "Ernte", "Effizienz")),
    MERCHANT("§fHändler", "§e[Merchant]", "marketplace",
            Set.of("Verhandlung", "Preise", "Bestand")),
    GUARD("§fWächter", "§e[Guard]", "watchtower",
            Set.of("Kampf", "Reichweite", "Reflexe")),
    LIBRARIAN("§fBuchhalter", "§e[Librarian]", "library",
            Set.of("Wissen", "Forschung", "Speicherung")),
    MILLER("§fMüller", "§e[Miller]", "mill",
            Set.of("Produktion", "Geschwindigkeit", "Qualität")),
    PRIEST("§fPriester", "§e[Priest]", "tavern",
            Set.of("Heilung", "Moral", "Zeremonien")),
    LABORER("§fArbeiter", "§e[Laborer]", "storage",
            Set.of("Tragkraft", "Geschwindigkeit", "Ausdauer"));

    private final String displayName;
    private final String prefix;
    private final String workBuildingKey;
    private final Set<String> skillTrees;

    VillagerJob(String displayName, String prefix, String workBuildingKey, Set<String> skillTrees) {
        this.displayName = displayName;
        this.prefix = prefix;
        this.workBuildingKey = workBuildingKey;
        this.skillTrees = skillTrees;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getWorkBuildingKey() {
        return workBuildingKey;
    }

    public Set<String> getSkillTrees() {
        return skillTrees;
    }
}
