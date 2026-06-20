package com.mythicrealm.api.gameplay.skill;

import com.mythicrealm.api.gameplay.combat.CombatSkill;
import com.mythicrealm.api.gameplay.combat.Combatant;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {
    private static final int RANK_LEVEL_STEP = 5;

    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;

    public SkillService(JdbcTemplate jdbcTemplate, PlayerService playerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerService = playerService;
    }

    @Transactional
    public SkillSnapshot snapshot(PlayerRecord player) {
        ensureStarterSkills(player);
        PlayerRecord current = playerService.requireById(player.id());
        List<SkillView> skills = playerTemplates(current).stream()
            .map(template -> view(current, template, learnedRanks(current.id()).get(template.id())))
            .sorted(Comparator
                .comparingInt(SkillView::unlockLevel)
                .thenComparing(SkillView::category)
                .thenComparing(SkillView::id))
            .toList();
        int skillPower = skillPower(current);
        int learnedCount = (int) skills.stream().filter(SkillView::learned).count();
        int affordableCount = (int) skills.stream()
            .filter(skill -> skill.canLearn() || skill.canUpgrade())
            .count();
        return new SkillSnapshot(current, skills, skillPower, learnedCount, affordableCount, rankLevelStep());
    }

    @Transactional
    public SkillSnapshot learn(PlayerRecord player, String skillId) {
        PlayerRecord current = playerService.requireById(player.id());
        SkillTemplate template = requireTemplate(skillId);
        validatePlayerTemplate(current, template);
        Integer existingRank = learnedRanks(current.id()).get(template.id());
        if (existingRank != null && existingRank > 0) {
            throw ApiException.badRequest("已经学会该技能");
        }
        if (current.level() < template.unlockLevel()) {
            throw ApiException.badRequest("等级不足，" + template.unlockLevel() + " 级后可学习");
        }
        int cost = learnCost(template);
        spendGold(current, cost);
        jdbcTemplate.update(
            """
            INSERT INTO player_skill (player_id, skill_id, skill_rank)
            VALUES (?, ?, 1)
            ON DUPLICATE KEY UPDATE skill_rank = GREATEST(skill_rank, 1), updated_at = CURRENT_TIMESTAMP
            """,
            current.id(),
            template.id()
        );
        return snapshot(playerService.requireById(current.id()));
    }

    @Transactional
    public SkillSnapshot upgrade(PlayerRecord player, String skillId) {
        PlayerRecord current = playerService.requireById(player.id());
        SkillTemplate template = requireTemplate(skillId);
        validatePlayerTemplate(current, template);
        int currentRank = learnedRanks(current.id()).getOrDefault(template.id(), 0);
        if (currentRank <= 0) {
            throw ApiException.badRequest("请先学习该技能");
        }
        int cap = rankCap(current, template);
        if (currentRank >= cap) {
            throw ApiException.badRequest("当前等级最多升级到 " + cap + " 阶");
        }
        int nextRank = currentRank + 1;
        int cost = upgradeCost(template, nextRank);
        spendGold(current, cost);
        jdbcTemplate.update(
            "UPDATE player_skill SET skill_rank = ?, updated_at = CURRENT_TIMESTAMP WHERE player_id = ? AND skill_id = ?",
            nextRank,
            current.id(),
            template.id()
        );
        return snapshot(playerService.requireById(current.id()));
    }

    @Transactional
    public TrainingOption trainBestAffordable(PlayerRecord player) {
        TrainingOption option = bestTrainingOption(player);
        if (option == null || !option.affordable()) {
            return null;
        }
        if (option.learn()) {
            learn(player, option.skillId());
        } else {
            upgrade(player, option.skillId());
        }
        return option;
    }

    public TrainingOption bestTrainingOption(PlayerRecord player) {
        ensureStarterSkills(player);
        PlayerRecord current = playerService.requireById(player.id());
        Map<String, Integer> ranks = learnedRanks(current.id());
        return playerTemplates(current).stream()
            .map(template -> trainingOption(current, template, ranks.getOrDefault(template.id(), 0)))
            .filter(option -> option != null && option.available())
            .sorted(Comparator
                .comparing(TrainingOption::affordable)
                .thenComparingDouble(TrainingOption::score)
                .reversed())
            .findFirst()
            .orElse(null);
    }

    public List<CombatSkill> playerCombatSkills(PlayerRecord player) {
        ensureStarterSkills(player);
        Map<String, Integer> ranks = learnedRanks(player.id());
        return playerTemplates(player).stream()
            .filter(template -> !"passive".equals(template.category()))
            .filter(template -> player.level() >= template.unlockLevel())
            .map(template -> {
                int rank = ranks.getOrDefault(template.id(), 0);
                return rank > 0 ? template.toCombatSkill(rank) : null;
            })
            .filter(skill -> skill != null)
            .sorted(Comparator.comparingInt(CombatSkill::priority).reversed())
            .toList();
    }

    public List<CombatSkill> monsterCombatSkills(Combatant monster) {
        return templates("monster").stream()
            .filter(template -> monster.stats().level() >= template.unlockLevel())
            .filter(template -> "any".equals(template.archetype()) || template.archetype().equals(monster.archetype()) || (monster.boss() && "boss".equals(template.archetype())))
            .filter(template -> !"passive".equals(template.category()))
            .map(template -> {
                int rank = monsterRank(monster.stats().level(), template);
                return template.toCombatSkill(rank);
            })
            .sorted(Comparator.comparingInt(CombatSkill::priority).reversed())
            .limit(monster.boss() ? 4 : 2)
            .toList();
    }

    public int skillPower(PlayerRecord player) {
        ensureStarterSkills(player);
        Map<String, Integer> ranks = learnedRanks(player.id());
        return skillPower(player, playerTemplates(player), ranks);
    }

    public Map<Long, Integer> skillPowerForPlayers(List<PlayerRecord> players) {
        if (players.isEmpty()) {
            return Map.of();
        }
        List<SkillTemplate> templates = templates("player");
        Map<Long, Map<String, Integer>> ranksByPlayer = learnedRanks(players.stream().map(PlayerRecord::id).toList());
        Map<Long, Integer> result = new HashMap<>();
        for (PlayerRecord player : players) {
            List<SkillTemplate> playerTemplates = templates.stream()
                .filter(template -> "any".equals(template.profession()) || normalize(player.profession()).equals(template.profession()))
                .toList();
            result.put(player.id(), skillPower(player, playerTemplates, ranksByPlayer.getOrDefault(player.id(), Map.of())));
        }
        return result;
    }

    private int skillPower(PlayerRecord player, List<SkillTemplate> playerTemplates, Map<String, Integer> ranks) {
        int raw = playerTemplates.stream()
            .mapToInt(template -> {
                int rank = ranks.getOrDefault(template.id(), template.unlockLevel() <= 1 ? 1 : 0);
                if (rank <= 0) {
                    return 0;
                }
                double value = (36 + template.unlockLevel() * 5 + rank * 34) * template.tierCoef();
                if ("support".equals(template.category())) {
                    value *= 0.82;
                }
                if ("passive".equals(template.category())) {
                    value *= 0.62;
                }
                return Math.max(0, (int) Math.round(value));
            })
            .sum();
        int softCap = Math.max(120, player.level() * 210 + player.wealthTierLevel() * 16);
        return Math.min(raw, softCap);
    }

    public int rankCap(PlayerRecord player, SkillTemplate template) {
        if (player.level() < template.unlockLevel()) {
            return 0;
        }
        return Math.min(template.maxRank(), 1 + Math.max(0, player.level() - template.unlockLevel()) / RANK_LEVEL_STEP);
    }

    public int learnCost(SkillTemplate template) {
        if (template.unlockLevel() <= 1) {
            return 0;
        }
        return roundToTen((int) Math.round((80 + template.unlockLevel() * 18) * template.tierCoef()));
    }

    public int upgradeCost(SkillTemplate template, int nextRank) {
        return roundToTen((int) Math.round((90 + template.unlockLevel() * 22) * Math.pow(nextRank, 1.48) * template.tierCoef()));
    }

    public int rankLevelStep() {
        return RANK_LEVEL_STEP;
    }

    private void ensureStarterSkills(PlayerRecord player) {
        List<String> starterIds = playerTemplates(player).stream()
            .filter(template -> template.unlockLevel() <= 1)
            .map(SkillTemplate::id)
            .toList();
        for (String skillId : starterIds) {
            jdbcTemplate.update(
                """
                INSERT INTO player_skill (player_id, skill_id, skill_rank)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE skill_rank = GREATEST(skill_rank, 1)
                """,
                player.id(),
                skillId
            );
        }
    }

    private SkillTemplate requireTemplate(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw ApiException.badRequest("技能不存在");
        }
        return jdbcTemplate.query(
                "SELECT * FROM skill_template WHERE id = ?",
                (rs, rowNum) -> mapTemplate(rs),
                skillId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("技能不存在"));
    }

    private List<SkillTemplate> playerTemplates(PlayerRecord player) {
        String profession = normalize(player.profession());
        return jdbcTemplate.query(
            """
            SELECT *
            FROM skill_template
            WHERE owner_scope = 'player' AND (profession = ? OR profession = 'any')
            ORDER BY unlock_level, priority DESC, id
            """,
            (rs, rowNum) -> mapTemplate(rs),
            profession
        );
    }

    private List<SkillTemplate> templates(String ownerScope) {
        return jdbcTemplate.query(
            "SELECT * FROM skill_template WHERE owner_scope = ? ORDER BY unlock_level, priority DESC, id",
            (rs, rowNum) -> mapTemplate(rs),
            ownerScope
        );
    }

    private Map<String, Integer> learnedRanks(long playerId) {
        List<Map.Entry<String, Integer>> rows = jdbcTemplate.query(
            "SELECT skill_id, skill_rank FROM player_skill WHERE player_id = ?",
            (rs, rowNum) -> Map.entry(rs.getString("skill_id"), rs.getInt("skill_rank")),
            playerId
        );
        Map<String, Integer> ranks = new HashMap<>();
        for (Map.Entry<String, Integer> row : rows) {
            ranks.put(row.getKey(), row.getValue());
        }
        return ranks;
    }

    private Map<Long, Map<String, Integer>> learnedRanks(List<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(playerIds.size(), "?"));
        Map<Long, Map<String, Integer>> ranks = new HashMap<>();
        jdbcTemplate.query(
            "SELECT player_id, skill_id, skill_rank FROM player_skill WHERE player_id IN (" + placeholders + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                long playerId = rs.getLong("player_id");
                ranks.computeIfAbsent(playerId, ignored -> new HashMap<>())
                    .put(rs.getString("skill_id"), rs.getInt("skill_rank"));
            },
            playerIds.toArray()
        );
        return ranks;
    }

    private SkillView view(PlayerRecord player, SkillTemplate template, Integer rankValue) {
        int rank = rankValue == null ? 0 : Math.max(0, rankValue);
        int cap = rankCap(player, template);
        boolean learned = rank > 0;
        boolean unlocked = player.level() >= template.unlockLevel();
        int learnCost = learnCost(template);
        int nextRankCost = learned && rank < cap ? upgradeCost(template, rank + 1) : 0;
        return new SkillView(
            template.id(),
            template.name(),
            template.ownerScope(),
            template.profession(),
            template.archetype(),
            template.unlockLevel(),
            template.maxRank(),
            cap,
            rank,
            template.category(),
            template.targetType(),
            template.damageType(),
            template.baseMultiplier(),
            template.rankMultiplierGrowth(),
            template.cooldown(),
            template.mpCostBase(),
            template.mpCostGrowth(),
            template.effectType(),
            template.effectPowerBase(),
            template.effectPowerGrowth(),
            template.durationRounds(),
            template.triggerKind(),
            template.priority(),
            template.visualKey(),
            template.description(),
            template.tierCoef(),
            learned,
            unlocked,
            unlocked && !learned && player.gold() >= learnCost,
            learned && rank < cap && player.gold() >= nextRankCost,
            learnCost,
            nextRankCost,
            projectedValue(template, Math.max(1, rank)),
            rank < cap ? projectedValue(template, Math.max(1, rank + 1)) : projectedValue(template, Math.max(1, rank))
        );
    }

    private TrainingOption trainingOption(PlayerRecord player, SkillTemplate template, int rank) {
        int cap = rankCap(player, template);
        if (cap <= 0) {
            return null;
        }
        boolean learn = rank <= 0;
        if (!learn && rank >= cap) {
            return null;
        }
        int nextRank = learn ? 1 : rank + 1;
        int cost = learn ? learnCost(template) : upgradeCost(template, nextRank);
        double score = 18 + template.priority() * 0.75 + template.unlockLevel() * 0.25;
        score += Math.max(0, cap - rank) * 5.0;
        score += "damage".equals(template.effectType()) ? 8 : 4;
        if (player.level() >= template.unlockLevel() + 10) {
            score += 6;
        }
        return new TrainingOption(
            template.id(),
            template.name(),
            learn,
            rank,
            nextRank,
            cap,
            cost,
            player.gold() >= cost,
            true,
            score,
            "学习或升级【" + template.name() + "】能直接提升自动战斗表现"
        );
    }

    private double projectedValue(SkillTemplate template, int rank) {
        if ("heal".equals(template.effectType()) || "shield".equals(template.effectType())) {
            return template.effectPowerBase() + template.effectPowerGrowth() * (rank - 1);
        }
        return template.baseMultiplier() + template.rankMultiplierGrowth() * (rank - 1);
    }

    private void validatePlayerTemplate(PlayerRecord player, SkillTemplate template) {
        if (!"player".equals(template.ownerScope())) {
            throw ApiException.badRequest("该技能不能由角色学习");
        }
        if (!"any".equals(template.profession()) && !normalize(player.profession()).equals(template.profession())) {
            throw ApiException.badRequest("职业不符合该技能要求");
        }
    }

    private void spendGold(PlayerRecord player, int cost) {
        if (cost <= 0) {
            return;
        }
        int changed = jdbcTemplate.update(
            "UPDATE player SET gold = gold - ? WHERE id = ? AND gold >= ?",
            cost,
            player.id(),
            cost
        );
        if (changed == 0) {
            throw ApiException.badRequest("金币不足，需要 " + cost + " 金");
        }
    }

    private int monsterRank(int monsterLevel, SkillTemplate template) {
        int byLevel = 1 + Math.max(0, monsterLevel - template.unlockLevel()) / 12;
        return Math.max(1, Math.min(template.maxRank(), byLevel));
    }

    private int roundToTen(int value) {
        return Math.max(0, ((value + 9) / 10) * 10);
    }

    private String normalize(String value) {
        return value == null ? "any" : value.toLowerCase(Locale.ROOT);
    }

    private SkillTemplate mapTemplate(ResultSet rs) throws SQLException {
        return new SkillTemplate(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("owner_scope"),
            rs.getString("profession"),
            rs.getString("archetype"),
            rs.getInt("unlock_level"),
            rs.getInt("max_rank"),
            rs.getString("category"),
            rs.getString("target_type"),
            rs.getString("damage_type"),
            rs.getDouble("base_multiplier"),
            rs.getDouble("rank_multiplier_growth"),
            rs.getInt("cooldown"),
            rs.getInt("mp_cost_base"),
            rs.getInt("mp_cost_growth"),
            rs.getString("effect_type"),
            rs.getDouble("effect_power_base"),
            rs.getDouble("effect_power_growth"),
            rs.getInt("duration_rounds"),
            rs.getString("trigger_kind"),
            rs.getInt("priority"),
            rs.getString("visual_key"),
            rs.getString("description"),
            rs.getDouble("tier_coef")
        );
    }

    public record SkillSnapshot(
        PlayerRecord player,
        List<SkillView> skills,
        int skillPower,
        int learnedCount,
        int affordableCount,
        int rankLevelStep
    ) {
    }

    public record SkillView(
        String id,
        String name,
        String ownerScope,
        String profession,
        String archetype,
        int unlockLevel,
        int maxRank,
        int rankCap,
        int rank,
        String category,
        String targetType,
        String damageType,
        double baseMultiplier,
        double rankMultiplierGrowth,
        int cooldown,
        int mpCostBase,
        int mpCostGrowth,
        String effectType,
        double effectPowerBase,
        double effectPowerGrowth,
        int durationRounds,
        String triggerKind,
        int priority,
        String visualKey,
        String description,
        double tierCoef,
        boolean learned,
        boolean unlocked,
        boolean canLearn,
        boolean canUpgrade,
        int learnCost,
        int nextRankCost,
        double currentValue,
        double nextValue
    ) {
    }

    public record TrainingOption(
        String skillId,
        String skillName,
        boolean learn,
        int currentRank,
        int nextRank,
        int rankCap,
        int cost,
        boolean affordable,
        boolean available,
        double score,
        String reason
    ) {
    }
}
