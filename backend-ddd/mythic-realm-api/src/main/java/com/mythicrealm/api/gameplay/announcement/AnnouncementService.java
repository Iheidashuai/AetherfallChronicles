package com.mythicrealm.api.gameplay.announcement;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {
    private final JdbcTemplate jdbcTemplate;

    public AnnouncementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void publish(String kind, String actorName, String text, int priority) {
        jdbcTemplate.update(
            "INSERT INTO global_announcement (kind, actor_name, text, priority) VALUES (?, ?, ?, ?)",
            kind,
            actorName,
            text,
            priority
        );
        jdbcTemplate.update(
            "DELETE FROM global_announcement WHERE id NOT IN (SELECT id FROM (SELECT id FROM global_announcement ORDER BY created_at DESC, id DESC LIMIT 80) recent)"
        );
    }

    public List<AnnouncementView> latest() {
        return jdbcTemplate.query(
            """
            SELECT id, kind, actor_name, text, priority, created_at
            FROM global_announcement
            WHERE kind <> 'level'
            ORDER BY created_at DESC, priority DESC, id DESC
            LIMIT 18
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
