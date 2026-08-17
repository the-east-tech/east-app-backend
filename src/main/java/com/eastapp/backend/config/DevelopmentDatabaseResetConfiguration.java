package com.eastapp.backend.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

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
     * Destructive database reset requires TWO independent approvals:
     *
     * 1. This source-code gate must be true.
     * 2. EASTAPP_DATABASE_RESET_ON_START must be true.
     *
     * If either side is false, Flyway clean is never executed.
     */
    private static final boolean DATABASE_RESET_ALLOWED_BY_CODE = true;

    private static final Set<String> LEGACY_MIGRATION_VERSIONS = Set.of("1", "2", "3", "4");

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
            @Value("${eastapp.database.reset-on-start:false}") boolean resetOnStart,
            @Value("${RAILWAY_ENVIRONMENT:}") String railwayEnvironment
    ) {
        return flyway -> {
            boolean runningOnRailway = !railwayEnvironment.isBlank();
            boolean resetApproved = databaseResetApproved(resetOnStart);

            if (!resetApproved) {
                log.info(
                        "Database reset disabled (codeGate={}, EASTAPP_DATABASE_RESET_ON_START={}); "
                                + "preserving existing data and applying pending Flyway migrations only",
                        DATABASE_RESET_ALLOWED_BY_CODE,
                        resetOnStart
                );
                flyway.migrate();
                return;
            }

            if (runningOnRailway) {
                if (hasExactLegacyMigrationHistory(flyway)) {
                    log.warn(
                            "Database reset approved by BOTH code gate and EASTAPP_DATABASE_RESET_ON_START; "
                                    + "legacy V1-V4 history detected on Railway. Performing the one-time "
                                    + "schema reset before applying the consolidated V1."
                    );
                    flyway.clean();
                } else {
                    log.error(
                            "Database reset was approved by both gates on Railway, but the database is not "
                                    + "the exact legacy V1-V4 baseline. Destructive reset refused; existing "
                                    + "data will be preserved."
                    );
                }
            } else {
                log.warn(
                        "Local database reset approved by BOTH code gate and EASTAPP_DATABASE_RESET_ON_START; "
                                + "deleting local database objects before Flyway migration."
                );
                flyway.clean();
            }

            flyway.migrate();
        };
    }

    private boolean databaseResetApproved(boolean resetOnStart) {
        return DATABASE_RESET_ALLOWED_BY_CODE && resetOnStart;
    }

    private boolean hasExactLegacyMigrationHistory(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            if (!schemaHistoryTableExists(connection)) {
                return false;
            }

            Set<String> versions = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT version FROM public.flyway_schema_history "
                            + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank"
            ); ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    versions.add(resultSet.getString(1));
                }
            }

            return versions.equals(LEGACY_MIGRATION_VERSIONS);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to verify the existing Flyway history before the one-time baseline reset",
                    exception
            );
        }
    }

    private boolean schemaHistoryTableExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL"
        ); ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }
}
