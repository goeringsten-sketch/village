package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.data.VillageDatabaseManager;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillagerJob;
import com.example.village.model.VillagerContract;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VillagerContractService {

    /** Mindestbestand, ab dem ein Kurier einen Ueberschuss als Transportauftrag erkennt. */
    private static final int LOGISTICS_SURPLUS_THRESHOLD = 8;
    /** Maximale Menge, die ein einzelner Logistikauftrag transportiert. */
    private static final int LOGISTICS_MAX_AMOUNT = 32;

    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final VillageDatabaseManager databaseManager;

    public VillagerContractService(VillagePlugin plugin,
                                   VillageManager villageManager,
                                   VillageDatabaseManager databaseManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.databaseManager = databaseManager;
    }

    public VillagerContract createContract(Village village,
                                           VillagerContract.Type type,
                                           UUID requesterVillagerId,
                                           UUID supplierVillagerId,
                                           Material material,
                                           int amount,
                                           long deadlineAt,
                                           String note) {
        if (village == null || requesterVillagerId == null || supplierVillagerId == null || material == null || amount <= 0) {
            return null;
        }
        VillagerContract contract = VillagerContract.create(type, requesterVillagerId, supplierVillagerId, material, amount, deadlineAt, note);
        village.addContract(contract);
        databaseManager.saveVillage(village);
        return contract;
    }

    /**
     * Nimmt einen offenen Auftrag an (offen -> aktiv). Erst danach wird er
     * von {@link #processVillage(Village)} tatsaechlich abgewickelt.
     */
    public boolean acceptContract(Village village, UUID contractId) {
        VillagerContract contract = village != null ? village.getContract(contractId) : null;
        if (contract == null || contract.getStatus() != VillagerContract.Status.OPEN) {
            return false;
        }
        contract.setStatus(VillagerContract.Status.ACTIVE);
        databaseManager.saveVillage(village);
        return true;
    }

    /**
     * Erzeugt einen Vorschlags-Auftrag fuer den Beruf des angegebenen Dorfbewohners.
     * Der Auftrag startet im Status OPEN ("offen") und muss erst ueber
     * {@link #acceptContract(Village, UUID)} angenommen werden, bevor er ausgefuehrt wird.
     */
    public VillagerContract createSuggestedContract(Village village, CustomVillager requester) {
        if (village == null || requester == null || requester.getJob() == null) {
            return null;
        }

        if (requester.getJob() == VillagerJob.COURIER) {
            return createLogisticsContract(village, requester);
        }

        ContractTemplate template = resolveTemplate(requester.getJob());
        if (template == null) {
            return null;
        }

        CustomVillager supplier = findBestSupplier(village, requester.getId(), template.preferredSupplierJobs(), template.material());
        if (supplier == null) {
            return null;
        }

        VillagerContract contract = VillagerContract.create(
                template.type(),
                requester.getId(),
                supplier.getId(),
                template.material(),
                template.amount(),
                0L,
                template.note()
        );
        village.addContract(contract);
        databaseManager.saveVillage(village);
        return contract;
    }

    /**
     * Logistikauftrag fuer Kurier/Hauler: findet einen Dorfbewohner mit Ueberschuss
     * eines Materials und erstellt einen Transportauftrag ins oeffentliche Dorflager,
     * statt - wie ein normaler Auftrag - die Ware in das persoenliche Lager des
     * Anfragenden zu liefern (siehe Sonderbehandlung in {@link #processVillage(Village)}).
     */
    private VillagerContract createLogisticsContract(Village village, CustomVillager courier) {
        CustomVillager bestSupplier = null;
        Material bestMaterial = null;
        int bestAmount = 0;

        for (CustomVillager villager : village.getVillagers()) {
            if (villager == null || villager.getId().equals(courier.getId())) continue;
            if (villager.getJob() == VillagerJob.COURIER) continue;
            for (Map.Entry<Material, Integer> entry : villager.getInventory().entrySet()) {
                if (entry.getValue() >= LOGISTICS_SURPLUS_THRESHOLD && entry.getValue() > bestAmount) {
                    bestAmount = entry.getValue();
                    bestMaterial = entry.getKey();
                    bestSupplier = villager;
                }
            }
        }

        if (bestSupplier == null || bestMaterial == null) {
            return null;
        }

        int amount = Math.min(bestAmount, LOGISTICS_MAX_AMOUNT);
        VillagerContract contract = VillagerContract.create(
                VillagerContract.Type.LOGISTICS,
                courier.getId(),
                bestSupplier.getId(),
                bestMaterial,
                amount,
                0L,
                "Transport von " + bestMaterial.name() + " ins Dorflager"
        );
        village.addContract(contract);
        databaseManager.saveVillage(village);
        return contract;
    }

    public boolean cancelContract(Village village, UUID contractId) {
        VillagerContract contract = village != null ? village.getContract(contractId) : null;
        if (contract == null) {
            return false;
        }
        contract.setStatus(VillagerContract.Status.CANCELLED);
        databaseManager.saveVillage(village);
        return true;
    }

    /**
     * Wertet alle aktiven Auftraege eines Dorfes aus. Liefert der Lieferant genug Ware,
     * wird der Auftrag abgeschlossen - je nach Typ mit Sonderbehandlung:
     * <ul>
     *     <li>LOGISTICS: Ware geht in ein oeffentliches Dorflager statt ins Lager des Kuriers.</li>
     *     <li>RESEARCH: beide Seiten erhalten zusaetzlich eine kleine Wissens-Bonus-XP.</li>
     *     <li>alle anderen: normale Lieferung, blockiert durch das Job-Lager-Limit des Empfaengers.</li>
     * </ul>
     */
    public int processVillage(Village village) {
        if (village == null || village.getContracts().isEmpty()) {
            return 0;
        }
        VillageConfigManager configManager = plugin.getConfigManager();
        BuildingChestManager chestManager = plugin.getBuildingChestManager();

        int completed = 0;
        boolean changed = false;
        for (VillagerContract contract : village.getContracts()) {
            if (contract.getStatus() != VillagerContract.Status.ACTIVE) continue;
            if (contract.isExpired()) {
                contract.setStatus(VillagerContract.Status.CANCELLED);
                changed = true;
                continue;
            }

            CustomVillager supplier = findVillager(village, contract.getSupplierVillagerId());
            CustomVillager requester = findVillager(village, contract.getRequesterVillagerId());
            Material material = contract.getMaterial();
            if (supplier == null || requester == null || material == null) {
                continue;
            }

            int stored = supplier.getInventory().getOrDefault(material, 0);
            if (stored < contract.getAmount()) {
                continue;
            }

            if (contract.getType() == VillagerContract.Type.LOGISTICS) {
                // Kurier transportiert die Ware in ein oeffentliches Dorflager statt sie
                // persoenlich zu behalten - genau wie im Ausbauplan fuer "Logistikauftrag" beschrieben.
                if (chestManager == null
                        || !chestManager.depositToAnyPublicChest(village, Map.of(material, contract.getAmount()))) {
                    continue; // kein Platz im Dorflager - im naechsten Tick erneut versuchen
                }
                supplier.removeItem(material, contract.getAmount());
                requester.addMoney(2.0 + contract.getAmount() * 0.1);
                requester.addXp(1.0);
            } else {
                if (configManager != null && !configManager.hasJobStorageCapacity(village, requester, material)) {
                    continue; // Job-Lager des Empfaengers ist voll - im naechsten Tick erneut versuchen
                }
                supplier.removeItem(material, contract.getAmount());
                requester.addItem(material, contract.getAmount());

                if (contract.getType() == VillagerContract.Type.RESEARCH) {
                    // Wissensauftrag: Forschung schaltet kleine Berufsboni (XP) fuer beide Seiten frei.
                    requester.addXp(3.0);
                    supplier.addXp(2.0);
                }
            }

            contract.setStatus(VillagerContract.Status.COMPLETED);
            completed++;
            changed = true;
        }

        if (changed) {
            databaseManager.saveVillage(village);
        }
        return completed;
    }

    private CustomVillager findVillager(Village village, UUID villagerId) {
        if (village == null || villagerId == null) return null;
        for (CustomVillager villager : village.getVillagers()) {
            if (villagerId.equals(villager.getId())) {
                return villager;
            }
        }
        return null;
    }

    private CustomVillager findBestSupplier(Village village,
                                            UUID requesterId,
                                            List<VillagerJob> preferredJobs,
                                            Material material) {
        CustomVillager fallback = null;
        for (CustomVillager villager : village.getVillagers()) {
            if (villager == null || villager.getId().equals(requesterId)) continue;
            if (villager.getJob() == null || villager.getJob() == VillagerJob.LABORER) continue;
            if (preferredJobs != null && preferredJobs.contains(villager.getJob())) {
                if (material != null && villager.getInventory().getOrDefault(material, 0) >= 1) {
                    return villager;
                }
                if (fallback == null) {
                    fallback = villager;
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
        for (CustomVillager villager : village.getVillagers()) {
            if (villager == null || villager.getId().equals(requesterId)) continue;
            if (material != null && villager.getInventory().getOrDefault(material, 0) >= 1) {
                return villager;
            }
        }
        return null;
    }

    private ContractTemplate resolveTemplate(VillagerJob job) {
        return switch (job) {
            case CARPENTER -> new ContractTemplate(
                    VillagerContract.Type.PRODUCTION,
                    Material.OAK_LOG,
                    16,
                    List.of(VillagerJob.LUMBERJACK),
                    "Holzlieferung für Tischlerei"
            );
            case BAKER -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.WHEAT,
                    32,
                    List.of(VillagerJob.FARMER),
                    "Weizen für Bäckerei"
            );
            case BLACKSMITH -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.IRON_INGOT,
                    16,
                    List.of(VillagerJob.MINER, VillagerJob.BLACKSMITH),
                    "Metalllieferung für Schmiede"
            );
            case MASON -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.STONE,
                    32,
                    List.of(VillagerJob.MINER),
                    "Steinlieferung für Steinmetz"
            );
            case BREWER -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.SUGAR,
                    16,
                    List.of(VillagerJob.FARMER, VillagerJob.MERCHANT),
                    "Zutaten für Brauerei"
            );
            case FISHER -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.COD,
                    16,
                    List.of(VillagerJob.FISHER),
                    "Fisch für Versorgung"
            );
            case BEEKEEPER -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.HONEYCOMB,
                    8,
                    List.of(VillagerJob.FARMER, VillagerJob.BEEKEEPER),
                    "Wachs für Imkerei"
            );
            case MEDIC -> new ContractTemplate(
                    VillagerContract.Type.RESEARCH,
                    Material.GOLDEN_APPLE,
                    4,
                    List.of(VillagerJob.MERCHANT, VillagerJob.SCHOLAR),
                    "Medizinische Versorgung"
            );
            case CARTOGRAPHER -> new ContractTemplate(
                    VillagerContract.Type.RESEARCH,
                    Material.PAPER,
                    32,
                    List.of(VillagerJob.FARMER, VillagerJob.MERCHANT),
                    "Papier für Kartographie"
            );
            case MERCHANT -> new ContractTemplate(
                    VillagerContract.Type.TRADE,
                    Material.EMERALD,
                    8,
                    List.of(VillagerJob.FARMER, VillagerJob.LUMBERJACK, VillagerJob.MINER, VillagerJob.BAKER),
                    "Handelsware für Markt"
            );
            case LUMBERJACK -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.IRON_AXE,
                    1,
                    List.of(VillagerJob.BLACKSMITH),
                    "Neue Axt für den Holzfäller"
            );
            case MINER -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.BREAD,
                    16,
                    List.of(VillagerJob.BAKER, VillagerJob.FARMER),
                    "Verpflegung für den Bergarbeiter"
            );
            case GUARD -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.IRON_SWORD,
                    1,
                    List.of(VillagerJob.BLACKSMITH),
                    "Bewaffnung für die Wache"
            );
            case SCHOLAR -> new ContractTemplate(
                    VillagerContract.Type.RESEARCH,
                    Material.PAPER,
                    32,
                    List.of(VillagerJob.FARMER, VillagerJob.MERCHANT, VillagerJob.CARTOGRAPHER),
                    "Papier für neue Forschungsergebnisse"
            );
            case HUNTER -> new ContractTemplate(
                    VillagerContract.Type.MATERIAL,
                    Material.IRON_INGOT,
                    8,
                    List.of(VillagerJob.MINER, VillagerJob.BLACKSMITH),
                    "Pfeilspitzen und Messer für die Jagd"
            );
            default -> null;
        };
    }

    private record ContractTemplate(VillagerContract.Type type,
                                    Material material,
                                    int amount,
                                    List<VillagerJob> preferredSupplierJobs,
                                    String note) {}

    public VillageManager getVillageManager() {
        return villageManager;
    }

    public VillagePlugin getPlugin() {
        return plugin;
    }
}
