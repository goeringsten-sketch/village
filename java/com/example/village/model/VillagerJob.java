package com.example.village.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Zentrale Job-Definition für Dorfbewohner.
 *
 * Die eigentliche Persistenz läuft über die profession key Strings aus der Config.
 */
public enum VillagerJob {
    FARMER("farmer", "§aBauer", "§e[Bauer]", Material.WHEAT,
            Set.of("Anbau", "Ernte", "Effizienz"),
            Set.of("farmer", "bauer", "farm", "acker", "composter", "beet", "tierzucht", "fischerei"),
            Set.of("acker", "beet", "tierzucht", "fischerei")),
    MINER("miner", "§aBergarbeiter", "§e[Bergarbeiter]", Material.IRON_PICKAXE,
            Set.of("Abbau", "Ausdauer", "Ressourcen"),
            Set.of("miner", "bergarbeiter", "bergbau", "mine", "quarry"),
            Set.of("schmelze", "steinmetz")),
    LUMBERJACK("lumberjack", "§aHolzfäller", "§e[Holzfäller]", Material.IRON_AXE,
            Set.of("Fällen", "Transport", "Sammeln"),
            Set.of("lumberjack", "holzfaeller", "holzfäller", "forst", "forest", "woodcutter"),
            Set.of("forst", "holzfaellerhuette", "verarbeitungslager", "jagdhuette", "wildhof")),
    CARPENTER("carpenter", "§aTischler", "§e[Tischler]", Material.CRAFTING_TABLE,
            Set.of("Handwerk", "Holzschnitt", "Präzision"),
            Set.of("carpenter", "tischler", "schreiner", "woodworker"),
            Set.of("holzverarbeitung", "verarbeitungslager", "werkstatt")),
    BLACKSMITH("blacksmith", "§aSchmied", "§e[Schmied]", Material.ANVIL,
            Set.of("Schmieden", "Härtung", "Werkzeuge"),
            Set.of("blacksmith", "schmied", "smith", "smithy"),
            Set.of("schmiede", "grobschmied", "feinschmied", "schmelze")),
    MASON("mason", "§aSteinmetz", "§e[Steinmetz]", Material.STONECUTTER,
            Set.of("Steinschnitt", "Deko", "Baukunst"),
            Set.of("mason", "steinmetz", "stonecutter", "steinschneider"),
            Set.of("steinmetz", "schmelze")),
    BEEKEEPER("beekeeper", "§aImker", "§e[Imker]", Material.HONEYCOMB,
            Set.of("Honig", "Waben", "Bestäubung"),
            Set.of("beekeeper", "imker", "honig", "bee", "biene"),
            Set.of("imkerei", "imker")),
    BAKER("baker", "§aBäcker", "§e[Bäcker]", Material.BREAD,
            Set.of("Teig", "Ofen", "Versorgung"),
            Set.of("baker", "baecker", "bäcker", "backer", "backhaus"),
            Set.of("taverne", "wildhof", "smoker", "furnace", "bakery")),
    BREWER("brewer", "§aBrauer", "§e[Brauer]", Material.BREWING_STAND,
            Set.of("Brauung", "Mischung", "Tränke"),
            Set.of("brewer", "brauer", "brauerei", "brew"),
            Set.of("taverne", "medikus", "apotheke")),
    FISHER("fisher", "§aFischer", "§e[Fischer]", Material.FISHING_ROD,
            Set.of("Fischen", "Netze", "Wasser"),
            Set.of("fisher", "fischer", "fisch", "angler"),
            Set.of("fischerei", "bootshaus")),
    HUNTER("hunter", "§aJäger", "§e[Jäger]", Material.BOW,
            Set.of("Jagdkunst", "Wild", "Erschließung"),
            Set.of("hunter", "jaeger", "jäger", "fletcher", "jagd"),
            Set.of("jagdhuette", "wildhof", "forst")),
    GUARD("guard", "§aWache", "§e[Wache]", Material.IRON_SWORD,
            Set.of("Kampf", "Reichweite", "Reflexe"),
            Set.of("guard", "wache", "waechter", "wachter", "wächter"),
            Set.of("watchtower", "wachturm", "barracks", "kaserne", "bell")),
    MERCHANT("merchant", "§aHändler", "§e[Händler]", Material.EMERALD,
            Set.of("Verhandlung", "Preise", "Bestand"),
            Set.of("merchant", "haendler", "händler", "markt", "market", "shop", "marketplace", "marktplatz"),
            Set.of("marketplace", "shop", "marktplatz")),
    SCHOLAR("scholar", "§aGelehrter", "§e[Gelehrter]", Material.BOOK,
            Set.of("Wissen", "Forschung", "Speicherung"),
            Set.of("scholar", "gelehrter", "bibliothekar", "librarian", "library", "schule", "bibliothek", "priest", "priester", "tavern"),
            Set.of("schule", "bibliothek", "universitaet")),
    CARTOGRAPHER("cartographer", "§aKartograph", "§e[Kartograph]", Material.CARTOGRAPHY_TABLE,
            Set.of("Karten", "Erkundung", "Orientierung"),
            Set.of("cartographer", "kartograph", "kartographie", "cartography", "mapmaker"),
            Set.of("kartograph")),
    MEDIC("medic", "§aMedikus", "§e[Medikus]", Material.GOLDEN_APPLE,
            Set.of("Heilung", "Pflege", "Medizin"),
            Set.of("medic", "medikus", "heiler", "doctor", "arzt"),
            Set.of("medikus", "taverne", "apotheke")),
    COURIER("courier", "§aKurier", "§e[Kurier]", Material.HOPPER,
            Set.of("Transport", "Sortierung", "Logistik"),
            Set.of("courier", "kurier", "hauler", "transporter", "logistik"),
            Set.of("storage", "markt", "marketplace")),
    LABORER("none", "§aArbeiter", "§e[Arbeiter]", Material.OAK_LOG,
            Set.of("Tragkraft", "Geschwindigkeit", "Ausdauer"),
            Set.of("laborer", "arbeiter", "worker", "unassigned", "none"),
            Set.of());

    private final String professionKey;
    private final String displayName;
    private final String prefix;
    private final Material icon;
    private final Set<String> skillTrees;
    private final Set<String> aliases;
    private final Set<String> preferredBuildingKeys;

    VillagerJob(String professionKey,
                String displayName,
                String prefix,
                Material icon,
                Set<String> skillTrees,
                Set<String> aliases,
                Set<String> preferredBuildingKeys) {
        this.professionKey = professionKey;
        this.displayName = displayName;
        this.prefix = prefix;
        this.icon = icon;
        this.skillTrees = Collections.unmodifiableSet(new LinkedHashSet<>(skillTrees));
        this.aliases = Collections.unmodifiableSet(new LinkedHashSet<>(aliases));
        this.preferredBuildingKeys = Collections.unmodifiableSet(new LinkedHashSet<>(preferredBuildingKeys));
    }

    public String getProfessionKey() {
        return professionKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public Material getIcon() {
        return icon;
    }

    public Set<String> getSkillTrees() {
        return skillTrees;
    }

    public Set<String> getAliases() {
        return aliases;
    }

    public Set<String> getPreferredBuildingKeys() {
        return preferredBuildingKeys;
    }

    public String getWorkBuildingKey() {
        return switch (this) {
            case FARMER -> "acker";
            case MINER -> "schmelze";
            case LUMBERJACK -> "forst";
            case CARPENTER -> "holzverarbeitung";
            case BLACKSMITH -> "schmiede";
            case MASON -> "steinmetz";
            case BEEKEEPER -> "imkerei";
            case BAKER -> "taverne";
            case BREWER -> "taverne";
            case FISHER -> "fischerei";
            case HUNTER -> "jagdhuette";
            case GUARD -> "watchtower";
            case MERCHANT -> "marketplace";
            case SCHOLAR -> "bibliothek";
            case CARTOGRAPHER -> "kartograph";
            case MEDIC -> "medikus";
            case COURIER -> "storage";
            case LABORER -> "storage";
        };
    }

    public boolean isSelectable() {
        return this != LABORER;
    }

    public boolean matches(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return false;
        }
        String normalized = normalize(rawValue);
        if (normalize(name()).equals(normalized)) return true;
        if (normalize(professionKey).equals(normalized)) return true;
        if (normalize(displayName).equals(normalized)) return true;

        for (String alias : aliases) {
            String normalizedAlias = normalize(alias);
            if (normalizedAlias.equals(normalized)
                    || normalized.contains(normalizedAlias)
                    || normalizedAlias.contains(normalized)) {
                return true;
            }
        }
        for (String buildingKey : preferredBuildingKeys) {
            String normalizedBuilding = normalize(buildingKey);
            if (normalizedBuilding.equals(normalized)
                    || normalized.contains(normalizedBuilding)
                    || normalizedBuilding.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static VillagerJob fromString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return LABORER;
        }
        for (VillagerJob job : values()) {
            if (job.matches(rawValue)) {
                return job;
            }
        }
        return LABORER;
    }

    public static List<VillagerJob> selectableJobs() {
        List<VillagerJob> jobs = new ArrayList<>();
        for (VillagerJob job : values()) {
            if (job.isSelectable()) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    public static boolean anyMatches(Collection<String> values, VillagerJob job) {
        if (values == null || job == null) return false;
        for (String value : values) {
            if (job.matches(value)) return true;
        }
        return false;
    }

    private static String normalize(String input) {
        return input == null ? "" : input
                .replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "");
    }
}
