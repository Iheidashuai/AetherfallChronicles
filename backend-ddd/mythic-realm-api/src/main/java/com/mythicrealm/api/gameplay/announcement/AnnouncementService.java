package com.mythicrealm.api.gameplay.announcement;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {
    private static final List<Integer> LEVEL_MILESTONES = List.of(45, 60, 75, 90);

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void publishLegendaryLoot(String actorName, String dungeonName, String itemName) {
        publish(
            "loot",
            actorName,
            actorName + " 在【" + dungeonName + "】获得传说装备【" + itemName + "】。",
            3
        );
    }

    @Transactional
    public void publishImmortalLoot(String actorName, String dungeonName, String itemName) {
        publish(
            "loot",
            actorName,
            actorName + " 在【" + dungeonName + "】获得不朽装备【" + itemName + "】。",
            4
        );
    }

    @Transactional
    public void publishEnhancementMilestone(String actorName, String itemName, int enhancementLevel) {
        if (enhancementLevel < 10) {
            return;
        }
        publish(
            "enhance",
            actorName,
            actorName + " 的【" + itemName + "】强化突破 +" + enhancementLevel + "。",
            2
        );
    }

    @Transactional
    public void publishLevelMilestones(String actorName, int previousLevel, int currentLevel) {
        for (int milestone : LEVEL_MILESTONES) {
            if (previousLevel < milestone && currentLevel >= milestone) {
                publish(
                    "level",
                    actorName,
                    actorName + " 等级升到 " + milestone + "，新的远征阶段已经开启。",
                    1
                );
            }
        }
    }

    private void publish(String kind, String actorName, String text, int priority) {
        int inserted = jdbcTemplate.update(
            """
            INSERT INTO global_announcement (kind, actor_name, text, priority)
            SELECT ?, ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM global_announcement WHERE kind = ? AND text = ?
            )
            """,
            kind,
            actorName,
            text,
            priority,
            kind,
            text
        );
        if (inserted == 0) {
            return;
        }
        jdbcTemplate.update(
            "DELETE FROM global_announcement WHERE id NOT IN (SELECT id FROM (SELECT id FROM global_announcement ORDER BY created_at DESC, id DESC LIMIT 80) recent)"
        );
    }

    public List<AnnouncementView> latest() {
        List<AnnouncementView> rows = jdbcTemplate.query(
            """
            SELECT id, kind, actor_name, text, priority, created_at
            FROM global_announcement
            ORDER BY created_at DESC, priority DESC, id DESC
            LIMIT 80
            """,
            (rs, rowNum) -> new AnnouncementView(
                rs.getLong("id"),
                rs.getString("kind"),
                rs.getString("actor_name"),
                rs.getString("text"),
                rs.getInt("priority"),
                rs.getTimestamp("created_at").toInstant()
            )
        );
        Set<String> seenEvents = new HashSet<>();
        return rows.stream()
            .filter(row -> seenEvents.add(row.kind() + "\n" + row.text()))
            .limit(18)
            .toList();
    }

    public record AnnouncementView(
        long id,
        String kind,
        String actorName,
        String text,
        int priority,
        Instant createdAt
    ) {
    }
}
