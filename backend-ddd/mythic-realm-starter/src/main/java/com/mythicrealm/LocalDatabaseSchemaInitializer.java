package com.mythicrealm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component("localDatabaseSchemaInitializer")
public class LocalDatabaseSchemaInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(LocalDatabaseSchemaInitializer.class);
    private static final String STATE_TABLE = "local_schema_state";
    private static final String CHECKSUM_KEY = "latest_schema";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;
    private final String schemaLocation;
    private final boolean rebuildOnStart;

    public LocalDatabaseSchemaInitializer(
        DataSource dataSource,
        JdbcTemplate jdbcTemplate,
        ResourceLoader resourceLoader,
        @Value("${mythic.database.schema-location:classpath:db/latest_schema.sql}") String schemaLocation,
        @Value("${mythic.database.rebuild-on-start:false}") boolean rebuildOnStart
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.resourceLoader = resourceLoader;
        this.schemaLocation = schemaLocation;
        this.rebuildOnStart = rebuildOnStart;
    }

    @Override
    public void afterPropertiesSet() {
        Resource schema = resourceLoader.getResource(schemaLocation);
        String checksum = checksum(schema);
        String recordedChecksum = recordedChecksum();
        if (!rebuildOnStart && checksum.equals(recordedChecksum) && tableExists("config_bundle")) {
            log.info("Local database schema is current: {}", shortChecksum(checksum));
            return;
        }

        log.warn(
            "Rebuilding local learning database from {}. recorded={}, latest={}, force={}",
            schemaLocation,
            shortChecksum(recordedChecksum),
            shortChecksum(checksum),
            rebuildOnStart
        );
        rebuild(schema);
        recordChecksum(checksum);
        log.info("Local database schema rebuilt from latest schema: {}", shortChecksum(checksum));
    }

    private String recordedChecksum() {
        if (!tableExists(STATE_TABLE)) {
            return "";
        }
        return jdbcTemplate.query(
            "SELECT checksum FROM " + STATE_TABLE + " WHERE schema_key = ?",
            (rs, rowNum) -> rs.getString("checksum"),
            CHECKSUM_KEY
        ).stream().findFirst().orElse("");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND table_type = 'BASE TABLE'
            """,
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private void rebuild(Resource schema) {
        dropAllTables();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(schema, StandardCharsets.UTF_8));
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to rebuild local database schema.", error);
        }
    }

    private void dropAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_type = 'BASE TABLE'
            """,
            String.class
        );
        if (tables.isEmpty()) {
            return;
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : tables) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + quoteIdentifier(table));
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void recordChecksum(String checksum) {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS " + STATE_TABLE + " (" +
                "schema_key VARCHAR(64) PRIMARY KEY, " +
                "checksum VARCHAR(64) NOT NULL, " +
                "rebuilt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        );
        jdbcTemplate.update(
            "INSERT INTO " + STATE_TABLE + " (schema_key, checksum) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE checksum = VALUES(checksum), rebuilt_at = CURRENT_TIMESTAMP",
            CHECKSUM_KEY,
            checksum
        );
    }

    private String checksum(Resource schema) {
        try {
            byte[] bytes = schema.getContentAsByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Failed to read local database schema: " + schemaLocation, error);
        }
    }

    private String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "none";
        }
        return checksum.substring(0, Math.min(12, checksum.length()));
    }

    private String quoteIdentifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }
}
