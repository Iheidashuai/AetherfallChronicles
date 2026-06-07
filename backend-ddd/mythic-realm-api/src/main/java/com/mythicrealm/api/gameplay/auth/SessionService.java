package com.mythicrealm.api.gameplay.auth;

import com.mythicrealm.api.gameplay.common.ApiException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();
    private final Duration sessionDuration;

    public SessionService(JdbcTemplate jdbcTemplate, @Value("${mythic.auth.session-days:14}") int sessionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionDuration = Duration.ofDays(sessionDays);
    }

    public String createSession(long accountId) {
        String token = newToken();
        jdbcTemplate.update(
            "INSERT INTO session_token (account_id, token, expires_at) VALUES (?, ?, ?)",
            accountId,
            token,
            Timestamp.from(Instant.now().plus(sessionDuration))
        );
        return token;
    }

    public AuthenticatedAccount require(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        return findByToken(token).orElseThrow(() -> ApiException.unauthorized("登录状态已失效"));
    }

    private Optional<AuthenticatedAccount> findByToken(String token) {
        var accounts = jdbcTemplate.query(
            """
            SELECT a.id, a.username
            FROM session_token st
            JOIN account a ON a.id = st.account_id
            WHERE st.token = ? AND st.expires_at > CURRENT_TIMESTAMP
            """,
            (rs, rowNum) -> new AuthenticatedAccount(rs.getLong("id"), rs.getString("username")),
            token
        );
        return accounts.stream().findFirst();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw ApiException.unauthorized("缺少登录凭证");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private String newToken() {
        byte[] bytes = new byte[36];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
