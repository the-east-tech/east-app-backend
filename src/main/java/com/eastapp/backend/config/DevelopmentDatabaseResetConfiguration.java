package com.eastapp.backend.config;

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

    @Bean
    FlywayConfigurationCustomizer eastAppFlywayConfigurationCustomizer(
            @Value("${eastapp.database.reset-on-start:true}") boolean resetOnStart
    ) {
        return configuration -> {
            if (resetOnStart) {
                configuration.cleanDisabled(false);
            }
        };
    }

    @Bean
    FlywayMigrationStrategy eastAppFlywayMigrationStrategy(
            @Value("${eastapp.database.reset-on-start:true}") boolean resetOnStart
    ) {
        return flyway -> {
            if (resetOnStart) {
                log.warn(
                        "EASTAPP_DATABASE_RESET_ON_START=true: deleting all database objects "
                                + "and recreating the schema from Flyway V1"
                );
                flyway.clean();
            } else {
                log.info(
                        "EASTAPP_DATABASE_RESET_ON_START=false: preserving existing data "
                                + "and applying pending Flyway migrations only"
                );
            }

            flyway.migrate();
        };
    }
}
