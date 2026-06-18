package com.mythicrealm.api.gameplay.combat;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import org.springframework.stereotype.Service;

@Service
public class EncounterGate {
    public GateStatus evaluate(PlayerRecord player, int combatPower, DungeonConfig dungeon) {
        int missingLevel = Math.max(0, dungeon.minimumLevel() - player.level());
        int missingPower = Math.max(0, dungeon.minimumPower() - combatPower);
        boolean eligible = missingLevel == 0 && missingPower == 0;
        String label;
        if (eligible) {
            label = "可挑战";
        } else if (missingLevel > 0 && missingPower > 0) {
            label = "还差 " + missingLevel + " 级 / " + missingPower + " 战力";
        } else if (missingLevel > 0) {
            label = "还差 " + missingLevel + " 级";
        } else {
            label = "还差 " + missingPower + " 战力";
        }
        return new GateStatus(eligible, missingLevel, missingPower, label);
    }

    public record GateStatus(boolean eligible, int missingLevel, int missingPower, String label) {
    }
}
