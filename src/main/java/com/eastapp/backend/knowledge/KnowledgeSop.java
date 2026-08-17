package com.eastapp.backend.knowledge;

import com.eastapp.backend.organisation.Tenant;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.stock.StockTag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "knowledge_sops")
public class KnowledgeSop {
    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false, updatable = false)
    private StockTag tag;

    @Column(name = "youtube_url", nullable = false, length = 500)
    private String youtubeUrl;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "expected_outcome", nullable = false, length = 1000)
    private String expectedOutcome;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeSop() {
    }

    public KnowledgeSop(
            Tenant tenant,
            StockTag tag,
            String youtubeUrl,
            String title,
            String expectedOutcome,
            String description,
            UserAccount createdBy
    ) {
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.tag = Objects.requireNonNull(tag, "tag must not be null");
        this.youtubeUrl = requireText(youtubeUrl, "youtubeUrl");
        this.title = requireText(title, "title");
        this.expectedOutcome = requireText(expectedOutcome, "expectedOutcome");
        this.description = requireText(description, "description");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public StockTag getTag() { return tag; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public String getTitle() { return title; }
    public String getExpectedOutcome() { return expectedOutcome; }
    public String getDescription() { return description; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        String result = Objects.requireNonNull(value, field + " must not be null").trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }
}
