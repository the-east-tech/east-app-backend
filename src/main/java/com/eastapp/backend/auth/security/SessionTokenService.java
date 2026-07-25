package com.eastapp.backend.auth.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SessionTokenService {

    private static final String TOKEN_PREFIX = "eas_";
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedSessionToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        String rawToken = TOKEN_PREFIX + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        return new GeneratedSessionToken(rawToken, hash(rawToken));
    }

    public byte[] hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Session token must not be blank");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record GeneratedSessionToken(String rawToken, byte[] tokenHash) {
    }
}
