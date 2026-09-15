package com.eastapp.backend.activity.service;

import com.eastapp.backend.activity.ActivityEvent;
import com.eastapp.backend.activity.UserNotification;
import com.eastapp.backend.activity.config.NotificationProperties;
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

    private static final String APP_NAME = "eastapp-notifications";

    private final NotificationProperties properties;
    private volatile FirebaseMessaging messaging;

    public FirebasePushGateway(NotificationProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.canQueuePush();
    }

    public Result send(UserNotification userNotification, String token) {
        if (!isEnabled()) return Result.RETRYABLE_FAILURE;
        ActivityEvent event = userNotification.getActivityEvent();
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle("EastApp · " + event.getModule())
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
            if (code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.INVALID_ARGUMENT) {
                return Result.INVALID_TOKEN;
            }
            return Result.RETRYABLE_FAILURE;
        } catch (RuntimeException | IOException exception) {
            return Result.RETRYABLE_FAILURE;
        }
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
