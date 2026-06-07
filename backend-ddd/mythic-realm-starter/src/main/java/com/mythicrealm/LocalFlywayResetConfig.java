package com.mythicrealm;

import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalFlywayResetConfig {
    private static final Logger log = LoggerFactory.getLogger(LocalFlywayResetConfig.class);

    @Bean
    public FlywayMigrationStrategy destructiveLocalFlywayMigrationStrategy(
        @Value("${mythic.database.destructive-reset-on-migration-error:true}") boolean resetOnMigrationError
    ) {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayException error) {
                if (!resetOnMigrationError) {
                    throw error;
                }
                log.warn("Flyway migration validation failed; cleaning local learning database and rebuilding latest schema.", error);
                flyway.clean();
                flyway.migrate();
            }
        };
    }
}
