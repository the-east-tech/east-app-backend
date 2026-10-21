package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.PushDevice;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FirebasePushGatewayTests {
    @Test
    void classifiesPermanentTokenFailuresAsInvalid() {
        assertThat(FirebasePushGateway.isInvalidToken(MessagingErrorCode.UNREGISTERED)).isTrue();
        assertThat(FirebasePushGateway.isInvalidToken(MessagingErrorCode.INVALID_ARGUMENT)).isTrue();
        assertThat(FirebasePushGateway.isInvalidToken(MessagingErrorCode.SENDER_ID_MISMATCH)).isTrue();
    }

    @Test
    void reportsSenderMismatchAndTransientFirebaseFailures() {
        assertThat(FirebasePushGateway.shouldReport(MessagingErrorCode.SENDER_ID_MISMATCH)).isTrue();
        assertThat(FirebasePushGateway.shouldReport(MessagingErrorCode.INVALID_ARGUMENT)).isTrue();
        assertThat(FirebasePushGateway.shouldReport(MessagingErrorCode.INTERNAL)).isTrue();
        assertThat(FirebasePushGateway.shouldReport(MessagingErrorCode.UNAVAILABLE)).isTrue();
        assertThat(FirebasePushGateway.shouldReport(MessagingErrorCode.UNREGISTERED)).isFalse();
    }

    @Test
    void keepsTransientFirebaseFailuresRetryable() {
        assertThat(FirebasePushGateway.isInvalidToken(MessagingErrorCode.INTERNAL)).isFalse();
        assertThat(FirebasePushGateway.isInvalidToken(MessagingErrorCode.UNAVAILABLE)).isFalse();
    }

    @Test
    void includesDeviceAndFlowVersionInTheErrorSource() {
        UUID userId = UUID.randomUUID();
        PushDevice device = new PushDevice(
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                "token",
                "android",
                "1.0.0+3",
                Instant.now()
        );

        assertThat(FirebasePushGateway.pushDeliverySource(
                device,
                MessagingErrorCode.SENDER_ID_MISMATCH
        ))
                .contains("sender ID mismatch")
                .contains("Flow 1.0.0+3")
                .contains("ANDROID")
                .contains("userId=" + userId);
    }
}
