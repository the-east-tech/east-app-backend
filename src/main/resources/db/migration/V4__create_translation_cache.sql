CREATE TABLE translation_cache (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    source_language VARCHAR(16) NOT NULL,
    target_language VARCHAR(16) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    source_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_translation_cache_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uq_translation_cache_lookup
        UNIQUE (tenant_id, source_language, target_language, source_hash),
    CONSTRAINT ck_translation_cache_source_language
        CHECK (source_language IN ('ENGLISH', 'CHINESE', 'MYANMAR')),
    CONSTRAINT ck_translation_cache_target_language
        CHECK (target_language IN ('ENGLISH', 'CHINESE', 'MYANMAR')),
    CONSTRAINT ck_translation_cache_direction
        CHECK (source_language <> target_language),
    CONSTRAINT ck_translation_cache_source_hash
        CHECK (source_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_translation_cache_source_text
        CHECK (btrim(source_text) <> ''),
    CONSTRAINT ck_translation_cache_translated_text
        CHECK (btrim(translated_text) <> ''),
    CONSTRAINT ck_translation_cache_provider
        CHECK (btrim(provider) <> '')
);

CREATE INDEX ix_translation_cache_tenant_pair
    ON translation_cache (tenant_id, source_language, target_language);
