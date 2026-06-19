package com.mythicrealm.domain.arena.port;

import com.mythicrealm.domain.arena.model.ArenaModels.ArenaBattleResult;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaFighter;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaPlayer;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class ArenaPorts {
    private ArenaPorts() {
    }

    public interface ArenaPlayerPort {
        ArenaPlayer requirePlayer(long playerId);

        Optional<ArenaPlayer> findPlayer(long playerId);

        List<ArenaPlayer> activePlayers(int limit);
    }

    public interface ArenaLoadoutPort {
        ArenaFighter fighter(long playerId);
    }

    public interface ArenaCombatPort {
        ArenaBattleResult fight(ArenaFighter attacker, ArenaFighter defender, Random random);
    }

    public interface ArenaRewardPort {
        List<String> grantItem(long playerId, String itemTemplateId, int quantity, Random random);
    }

    public interface ArenaActivityPort {
        void recordActivity(long playerId, String kind, String text);

        void recordQuestEvent(long playerId, String type, int amount);
    }
}
