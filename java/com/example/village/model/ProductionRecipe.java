package com.example.village.model;

import org.bukkit.Material;
import java.util.*;

/** Definiert eine Produktionskette für Arbeitsgebäude. */
public final class ProductionRecipe {
    private final String id;
    private final Map<Material, Integer> inputs;
    private final Map<Material, Integer> outputs;
    private final int durationSeconds;
    private final String requiredVillagerJob;
    private final String skillBonusKey;
    private final int requiredBuildingLevel;

    public ProductionRecipe(String id, Map<Material, Integer> inputs, Map<Material, Integer> outputs,
                            int durationSeconds, String requiredVillagerJob, String skillBonusKey,
                            int requiredBuildingLevel) {
        this.id                    = id;
        this.inputs                = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        this.outputs               = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        this.durationSeconds       = durationSeconds;
        this.requiredVillagerJob   = requiredVillagerJob;
        this.skillBonusKey         = skillBonusKey;
        this.requiredBuildingLevel = requiredBuildingLevel;
    }

    public String getId()                           { return id; }
    public Map<Material, Integer> getInputs()       { return inputs; }
    public Map<Material, Integer> getOutputs()      { return outputs; }
    public int getDurationSeconds()                 { return durationSeconds; }
    public String getRequiredVillagerJob()          { return requiredVillagerJob; }
    public String getSkillBonusKey()                { return skillBonusKey; }
    public int getRequiredBuildingLevel()           { return requiredBuildingLevel; }

    public boolean canProduce(Map<Material, Integer> available) {
        for (Map.Entry<Material, Integer> req : inputs.entrySet())
            if (available.getOrDefault(req.getKey(), 0) < req.getValue()) return false;
        return true;
    }

    public Map<Material, Integer> consumeInputs(Map<Material, Integer> available) {
        Map<Material, Integer> result = new LinkedHashMap<>(available);
        for (Map.Entry<Material, Integer> req : inputs.entrySet()) {
            int rem = result.getOrDefault(req.getKey(), 0) - req.getValue();
            if (rem <= 0) result.remove(req.getKey());
            else result.put(req.getKey(), rem);
        }
        return result;
    }
}
