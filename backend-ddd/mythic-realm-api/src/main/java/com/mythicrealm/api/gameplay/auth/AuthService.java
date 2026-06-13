package com.mythicrealm.api.gameplay.auth;

import com.mythicrealm.api.gameplay.common.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final SessionService sessionService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher, SessionService sessionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
    }

    @Transactional
    public AuthResponse register(String username, String password) {
        validate(username, password);
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO account (username, password_hash) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, username.trim());
                ps.setString(2, passwordHasher.hash(password));
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException error) {
            throw ApiException.badRequest("用户名已存在");
        }
        long accountId = keyHolder.getKey().longValue();
        return new AuthResponse(sessionService.createSession(accountId), username.trim(), false);
    }

    @Transactional
    public AuthResponse login(String username, String password) {
        AccountCredential credential = findCredential(username.trim())
            .orElseThrow(() -> ApiException.unauthorized("用户名或密码错误"));
        if (!passwordHasher.matches(password, credential.passwordHash())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        boolean hasPlayer = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player WHERE account_id = ?",
            Integer.class,
            credential.accountId()
        ) > 0;
        return new AuthResponse(sessionService.createSession(credential.accountId()), credential.username(), hasPlayer);
    }

    private Optional<AccountCredential> findCredential(String username) {
        var rows = jdbcTemplate.query(
            "SELECT id, username, password_hash FROM account WHERE username = ?",
            (rs, rowNum) -> new AccountCredential(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash")
            ),
            username
        );
        return rows.stream().findFirst();
    }

    private void validate(String username, String password) {
        if (username == null || username.trim().length() < 2) {
            throw ApiException.badRequest("用户名至少需要 2 个字符");
        }
        if (password == null || password.length() < 6) {
            throw ApiException.badRequest("密码至少需要 6 个字符");
        }
    }

    private record AccountCredential(long accountId, String username, String passwordHash) {
    }

    public record AuthResponse(String token, String username, boolean hasPlayer) {
    }
}
