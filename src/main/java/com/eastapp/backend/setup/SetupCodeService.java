package com.eastapp.backend.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class SetupCodeService {

    private static final Logger log = LoggerFactory.getLogger(SetupCodeService.class);
    private static final Duration VALIDITY = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 10;

    private final JdbcTemplate jdbcTemplate;

    public SetupCodeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ActiveSetupCode ensureActiveCode() {
        SetupCodeRow row = lockSetupRow();
        Instant now = Instant.now();
        if (row.code() != null
                && row.expiresAt() != null
                && now.isBefore(row.expiresAt())) {
            return new ActiveSetupCode(row.code(), row.expiresAt());
        }

        String code = generateCode();
        Instant expiresAt = now.plus(VALIDITY);
        jdbcTemplate.update(
                """
                update application_setup
                set setup_code = ?, setup_code_expires_at = ?
                where id = 1
                """,
                code,
                Timestamp.from(expiresAt)
        );
        log.warn("EastApp initial setup code: {} (valid for 1 hour)", code);
        return new ActiveSetupCode(code, expiresAt);
    }

    @Transactional
    public boolean matches(String candidate) {
        SetupCodeRow row = lockSetupRow();
        if (row.code() == null
                || row.expiresAt() == null
                || Instant.now().isAfter(row.expiresAt())) {
            return false;
        }
        String normalised = candidate == null
                ? ""
                : candidate.trim().toUpperCase(Locale.ROOT);
        return row.code().equals(normalised);
    }

    @Transactional
    public void invalidate() {
        jdbcTemplate.update(
                """
                update application_setup
                set setup_code = null,
                    setup_code_expires_at = null
                where id = 1
                """
        );
    }

    private SetupCodeRow lockSetupRow() {
        return jdbcTemplate.queryForObject(
                """
                select setup_code, setup_code_expires_at
                from application_setup
                where id = 1
                for update
                """,
                (resultSet, rowNumber) -> {
                    Timestamp expiresAt = resultSet.getTimestamp("setup_code_expires_at");
                    return new SetupCodeRow(
                            resultSet.getString("setup_code"),
                            expiresAt == null ? null : expiresAt.toInstant()
                    );
                }
        );
    }

    private static String generateCode() {
        StringBuilder generated = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            generated.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return generated.toString();
    }

    public record ActiveSetupCode(String code, Instant expiresAt) {
    }

    private record SetupCodeRow(String code, Instant expiresAt) {
    }
}
