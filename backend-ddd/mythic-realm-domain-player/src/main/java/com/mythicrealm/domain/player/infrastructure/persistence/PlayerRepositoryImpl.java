package com.mythicrealm.domain.player.infrastructure.persistence;

import com.mythicrealm.domain.player.model.*;
import com.mythicrealm.domain.player.repository.PlayerRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * 玩家仓储实现
 */
@Repository
public class PlayerRepositoryImpl implements PlayerRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlayerRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Player save(Player player) {
        if (player.getId() == null) {
            return insert(player);
        } else {
            return update(player);
        }
    }

    @Override
    public Optional<Player> findById(PlayerId playerId) {
        String sql = "SELECT * FROM player WHERE id = ?";
        try {
            PlayerPO po = jdbcTemplate.queryForObject(sql, new PlayerRowMapper(), playerId.value());
            return Optional.of(toDomain(po));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Player> findByAccountId(Long accountId) {
        String sql = "SELECT * FROM player WHERE account_id = ?";
        try {
            PlayerPO po = jdbcTemplate.queryForObject(sql, new PlayerRowMapper(), accountId);
            return Optional.of(toDomain(po));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM player WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name.trim());
        return count != null && count > 0;
    }

    private Player insert(Player player) {
        String sql = """
            INSERT INTO player
            (account_id, name, profession, level, experience, gold,
             strength, agility, constitution, intelligence, spirit, free_points)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, player.getAccountId());
            ps.setString(2, player.getName());
            ps.setString(3, player.getProfession().getCode());
            ps.setInt(4, player.getLevel().value());
            ps.setInt(5, player.getExperience().value());
            ps.setInt(6, player.getGold().value());
            ps.setInt(7, player.getStats().strength());
            ps.setInt(8, player.getStats().agility());
            ps.setInt(9, player.getStats().constitution());
            ps.setInt(10, player.getStats().intelligence());
            ps.setInt(11, player.getStats().spirit());
            ps.setInt(12, player.getStats().freePoints());
            return ps;
        }, keyHolder);

        Long generatedId = keyHolder.getKey().longValue();
        player.setId(PlayerId.of(generatedId));
        return player;
    }

    private Player update(Player player) {
        String sql = """
            UPDATE player
            SET level = ?, experience = ?, gold = ?,
                strength = ?, agility = ?, constitution = ?,
                intelligence = ?, spirit = ?, free_points = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
            player.getLevel().value(),
            player.getExperience().value(),
            player.getGold().value(),
            player.getStats().strength(),
            player.getStats().agility(),
            player.getStats().constitution(),
            player.getStats().intelligence(),
            player.getStats().spirit(),
            player.getStats().freePoints(),
            player.getId().value()
        );

        return player;
    }

    private Player toDomain(PlayerPO po) {
        PlayerId playerId = PlayerId.of(po.getId());
        Profession profession = Profession.fromCode(po.getProfession());
        Level level = Level.of(po.getLevel());
        Experience experience = Experience.of(po.getExperience());
        Gold gold = Gold.of(po.getGold());
        PlayerStats stats = new PlayerStats(
            po.getStrength(),
            po.getAgility(),
            po.getConstitution(),
            po.getIntelligence(),
            po.getSpirit(),
            po.getFreePoints()
        );

        return new Player(playerId, po.getAccountId(), po.getName(),
                         profession, level, experience, gold, stats);
    }

    private static class PlayerRowMapper implements RowMapper<PlayerPO> {
        @Override
        public PlayerPO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PlayerPO(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("name"),
                rs.getString("profession"),
                rs.getInt("level"),
                rs.getInt("experience"),
                rs.getInt("gold"),
                rs.getInt("strength"),
                rs.getInt("agility"),
                rs.getInt("constitution"),
                rs.getInt("intelligence"),
                rs.getInt("spirit"),
                rs.getInt("free_points")
            );
        }
    }
}
