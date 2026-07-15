package com.example.village;

import com.example.village.VillagePlugin;
import com.example.village.model.Quest;
import com.example.village.service.QuestManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuestManagerTest {
    @Test
    void rejectsUnknownObjectiveType() {
        VillagePlugin plugin = Mockito.mock(VillagePlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(new java.io.File("/tmp/village-test"));
        Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("village-test"));
        com.example.village.config.VillageConfigManager cfg = Mockito.mock(com.example.village.config.VillageConfigManager.class);
        Mockito.when(cfg.text(Mockito.anyString(), Mockito.any())).thenReturn("x");
        Mockito.when(plugin.getConfigManager()).thenReturn(cfg);

        QuestManager questManager = new QuestManager(plugin, null, null, null);
        Quest quest = new Quest("q", "Q");
        quest.setObjectiveType("");

        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        assertThrows(QuestManager.QuestException.class, () -> questManager.canCompleteQuest(player, quest));
    }
}
