package com.eastapp.backend.knowledge;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "knowledge_sop_watch_sessions")
public class KnowledgeSopWatchSession {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sop_id", nullable = false, updatable = false)
    private KnowledgeSop sop;

    @Column(name = "played_seconds", nullable = false)
    private long playedSeconds;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeSopWatchSession() {
    }

    public KnowledgeSopWatchSession(
            UUID id,
            Tenant tenant,
            UserAccount user,
            KnowledgeSop sop,
            long playedSeconds,
            Instant capturedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.sop = Objects.requireNonNull(sop, "sop must not be null");
        this.playedSeconds = requirePlayedSeconds(playedSeconds);
        this.lastHeartbeatAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public UserAccount getUser() { return user; }
    public KnowledgeSop getSop() { return sop; }
    public long getPlayedSeconds() { return playedSeconds; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void recordCumulativePlayedSeconds(long value, Instant capturedAt) {
        long candidate = requirePlayedSeconds(value);
        if (candidate <= playedSeconds) {
            return;
        }
        playedSeconds = candidate;
        lastHeartbeatAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    private static long requirePlayedSeconds(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("playedSeconds must not be negative");
        }
        return value;
    }
}
