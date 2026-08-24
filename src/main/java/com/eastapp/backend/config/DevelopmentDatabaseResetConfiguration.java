package com.eastapp.backend.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DevelopmentDatabaseResetConfiguration {
    private static final Logger log =
            LoggerFactory.getLogger(DevelopmentDatabaseResetConfiguration.class);

    /*
     * A destructive reset is allowed only when BOTH this code gate and
     * EASTAPP_DATABASE_RESET_ON_START are true. If either is false, data is preserved.
     */
    private static final boolean DATABASE_RESET_ALLOWED_BY_CODE = false;

    @Bean
    FlywayConfigurationCustomizer eastAppFlywayConfigurationCustomizer(
            @Value("${eastapp.database.reset-on-start:false}") boolean resetOnStart
    ) {
        return configuration -> {
            if (databaseResetApproved(resetOnStart)) {
                configuration.cleanDisabled(false);
            }
        };
    }

    @Bean
    FlywayMigrationStrategy eastAppFlywayMigrationStrategy(
            @Value("${eastapp.database.reset-on-start:false}") boolean resetOnStart
    ) {
        return flyway -> {
            if (databaseResetApproved(resetOnStart)) {
                log.warn(
                        "Database reset approved by BOTH code gate and "
                                + "EASTAPP_DATABASE_RESET_ON_START; deleting all database objects."
                );
                flyway.clean();
            } else {
                log.info(
                        "Database reset disabled (codeGate={}, EASTAPP_DATABASE_RESET_ON_START={}); "
                                + "preserving data.",
                        DATABASE_RESET_ALLOWED_BY_CODE,
                        resetOnStart
                );
            }
            flyway.migrate();
        };
    }

    private boolean databaseResetApproved(boolean resetOnStart) {
        return DATABASE_RESET_ALLOWED_BY_CODE && resetOnStart;
    }
}
