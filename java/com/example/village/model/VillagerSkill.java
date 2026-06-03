package com.example.village.model;

/**
 * Einzelne Fähigkeit eines Villagers.
 */
public class VillagerSkill {
    private final String skillName;
    private int level;
    private double xp;
    private static final double XP_PER_LEVEL = 100.0;

    public VillagerSkill(String skillName) {
        this.skillName = skillName;
        this.level = 1;
        this.xp = 0;
    }

    public String getSkillName() {
        return skillName;
    }

    public int getLevel() {
        return level;
    }

    public double getXp() {
        return xp;
    }

    public double getXpProgress() {
        return (xp % XP_PER_LEVEL) / XP_PER_LEVEL;
    }

    public void addXp(double amount) {
        this.xp += amount;
        while (xp >= XP_PER_LEVEL) {
            xp -= XP_PER_LEVEL;
            level++;
        }
    }

    public int getXpToNextLevel() {
        return (int) (XP_PER_LEVEL - (xp % XP_PER_LEVEL));
    }
}
