package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.ActivityEvent;
import com.eastapp.backend.activity.PushDevice;
import com.eastapp.backend.activity.UserNotification;
import com.eastapp.backend.activity.config.NotificationProperties;
import com.eastapp.backend.support.service.ErrorReportService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FirebasePushGateway {
    public enum Result { SENT, INVALID_TOKEN, RETRYABLE_FAILURE }

    private static final String APP_NAME = "flow-notifications";

    private final NotificationProperties properties;
    private final ErrorReportService errorReportService;
    private volatile FirebaseMessaging messaging;

    public FirebasePushGateway(
            NotificationProperties properties,
            ErrorReportService errorReportService
    ) {
        this.properties = properties;
        this.errorReportService = errorReportService;
    }

    public boolean isEnabled() {
        return properties.canQueuePush();
    }

    public Result send(UserNotification userNotification, PushDevice device) {
        if (!isEnabled()) return Result.RETRYABLE_FAILURE;
        ActivityEvent event = userNotification.getActivityEvent();
        Message message = Message.builder()
                .setToken(device.getToken())
                .setNotification(Notification.builder()
                        .setTitle("Flow · " + event.getModule())
                        .setBody(event.summary())
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder()
                                .setDefaultSound(true)
                                .setDefaultVibrateTimings(true)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .putData("notificationId", userNotification.getId().toString())
                .putData("eventId", event.getId().toString())
                .putData("module", event.getModule())
                .putData("action", event.getAction())
                .build();
        try {
            messaging().send(message);
            return Result.SENT;
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode code = exception.getMessagingErrorCode();
            if (code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                errorReportService.reportSystemErrorAlways(
                        pushDeliverySource(device, code),
                        exception
                );
            } else if (shouldReport(code)) {
                errorReportService.reportSystemError(pushDeliverySource(device, code), exception);
            }
            if (isInvalidToken(code)) {
                return Result.INVALID_TOKEN;
            }
            return Result.RETRYABLE_FAILURE;
        } catch (RuntimeException | IOException exception) {
            errorReportService.reportSystemError(pushDeliverySource(device, null), exception);
            return Result.RETRYABLE_FAILURE;
        }
    }

    static boolean isInvalidToken(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT
                || code == MessagingErrorCode.SENDER_ID_MISMATCH;
    }

    static boolean shouldReport(MessagingErrorCode code) {
        return code != MessagingErrorCode.UNREGISTERED;
    }

    static String pushDeliverySource(PushDevice device, MessagingErrorCode code) {
        String error = code == MessagingErrorCode.SENDER_ID_MISMATCH
                ? ": sender ID mismatch"
                : "";
        return "Firebase push delivery" + error
                + " | Flow " + device.getAppVersion()
                + " | " + device.getPlatform()
                + " | userId=" + device.getUserId()
                + " | deviceId=" + device.getId();
    }

    private FirebaseMessaging messaging() throws IOException {
        FirebaseMessaging current = messaging;
        if (current != null) return current;
        synchronized (this) {
            if (messaging != null) return messaging;
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setProjectId(properties.getFirebaseProjectId());
            String serviceAccountJson = properties.getFirebaseServiceAccountJson();
            GoogleCredentials credentials = serviceAccountJson.isBlank()
                    ? GoogleCredentials.getApplicationDefault()
                    : GoogleCredentials.fromStream(new ByteArrayInputStream(
                            serviceAccountJson.getBytes(StandardCharsets.UTF_8)
                    ));
            builder.setCredentials(credentials);
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(candidate -> APP_NAME.equals(candidate.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(builder.build(), APP_NAME));
            messaging = FirebaseMessaging.getInstance(app);
            return messaging;
        }
    }
}
