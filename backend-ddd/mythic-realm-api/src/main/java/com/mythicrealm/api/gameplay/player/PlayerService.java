package com.mythicrealm.api.gameplay.player;

import com.mythicrealm.api.gameplay.auth.AuthenticatedAccount;
import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.recharge.WealthTierService;
import com.mythicrealm.api.gameplay.recharge.WealthTierService.WealthTier;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {
    public static final int MAX_LEVEL = 90;

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final AnnouncementService announcementService;
    private final WealthTierService wealthTierService;

    public PlayerService(
        JdbcTemplate jdbcTemplate,
        @Lazy InventoryService inventoryService,
        AnnouncementService announcementService,
        WealthTierService wealthTierService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.announcementService = announcementService;
        this.wealthTierService = wealthTierService;
    }

    @Transactional
    public PlayerRecord createPlayer(AuthenticatedAccount account, String name, String profession) {
        validateName(name);
        ProfessionStats stats = ProfessionStats.forName(profession);
        if (findByAccountId(account.accountId()).isPresent()) {
            throw ApiException.badRequest("该账号已经创建角色");
        }

        WealthTier tier = wealthTierService.topTier();
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO player
                    (account_id, name, profession, real_money, wealth_tier_level, wealth_tier,
                     strength, agility, constitution, intelligence, spirit)
                    VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setLong(1, account.accountId());
                ps.setString(2, name.trim());
                ps.setString(3, stats.profession());
                ps.setInt(4, tier.level());
                ps.setString(5, tier.name());
                ps.setInt(6, stats.strength());
                ps.setInt(7, stats.agility());
                ps.setInt(8, stats.constitution());
                ps.setInt(9, stats.intelligence());
                ps.setInt(10, stats.spirit());
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException error) {
            throw ApiException.badRequest("角色名已被占用");
        }

        PlayerRecord player = requireById(keyHolder.getKey().longValue());
        inventoryService.grantStarterEquipment(player.id());
        return requireById(player.id());
    }

    public PlayerRecord requireByAccount(AuthenticatedAccount account) {
        return findByAccountId(account.accountId()).orElseThrow(() -> ApiException.notFound("请先创建角色"));
    }

    public PlayerRecord requireById(long playerId) {
        return findById(playerId).orElseThrow(() -> ApiException.notFound("角色不存在"));
    }

    public Optional<PlayerRecord> findByAccountId(long accountId) {
        var players = jdbcTemplate.query(
            "SELECT * FROM player WHERE account_id = ?",
            (rs, rowNum) -> mapPlayer(rs),
            accountId
        );
        return players.stream().findFirst();
    }

    public Optional<PlayerRecord> findById(long playerId) {
        var players = jdbcTemplate.query(
            "SELECT * FROM player WHERE id = ?",
            (rs, rowNum) -> mapPlayer(rs),
            playerId
        );
        return players.stream().findFirst();
    }

    public PlayerRecord applyRewards(long playerId, int expGained, int goldGained) {
        PlayerRecord player = requireById(playerId);
        int level = player.level();
        int experience = player.experience() + Math.max(0, expGained);
        int strength = player.strength();
        int agility = player.agility();
        int constitution = player.constitution();
        int intelligence = player.intelligence();
        int spirit = player.spirit();
        int freePoints = player.freePoints();

        while (level < MAX_LEVEL) {
            int needed = experienceRequired(level);
            if (needed <= 0 || experience < needed) {
                break;
            }
            experience -= needed;
            level++;
            strength++;
            agility++;
            constitution++;
            intelligence++;
            spirit++;
            freePoints += 3;
            switch (player.profession()) {
                case "warrior" -> {
                    strength++;
                    constitution++;
                }
                case "ranger" -> {
                    agility++;
                    strength++;
                }
                case "mage" -> {
                    intelligence++;
                    spirit++;
                }
                default -> {
                }
            }
        }
        if (level >= MAX_LEVEL) {
            experience = 0;
        }

        jdbcTemplate.update(
            """
            UPDATE player
            SET level = ?, experience = ?, gold = gold + ?, strength = ?, agility = ?,
                constitution = ?, intelligence = ?, spirit = ?, free_points = ?
            WHERE id = ?
            """,
            level,
            experience,
            Math.max(0, goldGained),
            strength,
            agility,
            constitution,
            intelligence,
            spirit,
            freePoints,
            playerId
        );
        PlayerRecord updatedPlayer = requireById(playerId);
        announcementService.publishLevelMilestones(updatedPlayer.name(), player.level(), updatedPlayer.level());
        return updatedPlayer;
    }

    public static int experienceRequired(int level) {
        if (level >= MAX_LEVEL) {
            return 0;
        }
        return (int) (100 * Math.pow(level, 1.8));
    }

    private PlayerRecord mapPlayer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlayerRecord(
            rs.getLong("id"),
            rs.getLong("account_id"),
            rs.getString("name"),
            rs.getString("profession"),
            rs.getInt("level"),
            rs.getInt("experience"),
            rs.getLong("gold"),
            rs.getLong("real_money"),
            rs.getInt("wealth_tier_level"),
            rs.getString("wealth_tier"),
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
    }

    private void validateName(String name) {
        if (name == null || name.trim().length() < 2 || name.trim().length() > 16) {
            throw ApiException.badRequest("角色名需要 2-16 个字符");
        }
    }

    private record ProfessionStats(
        String profession,
        int strength,
        int agility,
        int constitution,
        int intelligence,
        int spirit
    ) {
        static ProfessionStats forName(String rawProfession) {
            String profession = rawProfession == null ? "warrior" : rawProfession.toLowerCase(Locale.ROOT);
            return switch (profession) {
                case "warrior" -> new ProfessionStats("warrior", 10, 5, 8, 3, 4);
                case "ranger" -> new ProfessionStats("ranger", 6, 10, 5, 4, 5);
                case "mage" -> new ProfessionStats("mage", 3, 4, 4, 10, 9);
                default -> throw ApiException.badRequest("暂不支持该职业");
            };
        }
    }
}
