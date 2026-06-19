package com.mythicrealm.api.gameplay.build;

import com.mythicrealm.api.gameplay.endgame.combat.BuildCombatPlan;
import com.mythicrealm.api.gameplay.endgame.combat.BuildCombatPlan.PlannedSkill;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BuildCombatPlanService {
    private final JdbcTemplate jdbcTemplate;

    public BuildCombatPlanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BuildCombatPlan activePlan(PlayerRecord player) {
        Long buildId = jdbcTemplate.query(
            "SELECT id FROM player_build WHERE player_id = ? AND active = TRUE ORDER BY updated_at DESC LIMIT 1",
            (rs, rowNum) -> rs.getLong("id"),
            player.id()
        ).stream().findFirst().orElse(null);
        return buildId == null ? BuildCombatPlan.empty() : planForBuild(player, buildId);
    }

    public BuildCombatPlan planForBuild(PlayerRecord player, long buildId) {
        return jdbcTemplate.query(
            "SELECT id, name, strategy FROM player_build WHERE player_id = ? AND id = ?",
            (rs, rowNum) -> new BuildCombatPlan(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("strategy"),
                skillsForBuild(player.id(), rs.getLong("id")),
                talentBonuses(rs.getLong("id"))
            ),
            player.id(),
            buildId
        ).stream().findFirst().orElse(BuildCombatPlan.empty());
    }

    private List<PlannedSkill> skillsForBuild(long playerId, long buildId) {
        return jdbcTemplate.query(
            """
            SELECT bs.trigger_kind, st.*, ps.skill_rank
            FROM player_build_skill_slot bs
            JOIN skill_template st ON st.id = bs.skill_id
            JOIN player_skill ps ON ps.player_id = ? AND ps.skill_id = st.id
            WHERE bs.build_id = ?
            ORDER BY bs.slot_index
            """,
            (rs, rowNum) -> {
                int rank = Math.max(1, rs.getInt("skill_rank"));
                return new PlannedSkill(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("trigger_kind"),
                    rs.getString("damage_type"),
                    rs.getDouble("base_multiplier") + rs.getDouble("rank_multiplier_growth") * (rank - 1),
                    rs.getInt("cooldown"),
                    rs.getInt("mp_cost_base") + rs.getInt("mp_cost_growth") * (rank - 1),
                    rs.getString("effect_type"),
                    rs.getDouble("effect_power_base") + rs.getDouble("effect_power_growth") * (rank - 1),
                    rs.getInt("duration_rounds"),
                    rs.getInt("priority")
                );
            },
            playerId,
            buildId
        );
    }

    private Map<String, Double> talentBonuses(long buildId) {
        List<Map.Entry<String, Double>> rows = jdbcTemplate.query(
            """
            SELECT pt.stat_key, pt.stat_value
            FROM player_build_talent bt
            JOIN player_build b ON b.id = bt.build_id
            JOIN build_preset_talent pt ON pt.preset_id = b.source_preset_id AND pt.node_id = bt.node_id
            WHERE bt.build_id = ?
            """,
            (rs, rowNum) -> Map.entry(rs.getString("stat_key"), rs.getDouble("stat_value")),
            buildId
        );
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Double> row : rows) {
            result.merge(row.getKey(), row.getValue(), Double::sum);
        }
        return result;
    }
}
