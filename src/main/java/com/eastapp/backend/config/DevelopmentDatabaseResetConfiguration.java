package com.eastapp.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DevelopmentDatabaseResetConfiguration {
    private static final Logger log =
            LoggerFactory.getLogger(DevelopmentDatabaseResetConfiguration.class);

    @Bean
    @ConditionalOnProperty(
            prefix = "eastapp.database",
            name = "reset-on-start",
            havingValue = "true"
    )
    FlywayConfigurationCustomizer allowFlywayCleanForDevelopmentReset() {
        return configuration -> configuration.cleanDisabled(false);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "eastapp.database",
            name = "reset-on-start",
            havingValue = "true"
    )
    FlywayMigrationStrategy cleanAndMigrateDevelopmentDatabase() {
        return flyway -> {
            log.warn(
                    "EASTAPP_DATABASE_RESET_ON_START=true: deleting all database objects "
                            + "and recreating the schema from Flyway V1"
            );
            flyway.clean();
            flyway.migrate();
        };
    }
}
