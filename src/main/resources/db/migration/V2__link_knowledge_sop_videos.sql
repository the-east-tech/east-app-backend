ALTER TABLE knowledge_sops
    ADD COLUMN language VARCHAR(20),
    ADD COLUMN link_group_id UUID;

UPDATE knowledge_sops
SET language = 'ENGLISH',
    link_group_id = id;

ALTER TABLE knowledge_sops
    ALTER COLUMN language SET NOT NULL,
    ALTER COLUMN link_group_id SET NOT NULL,
    ADD CONSTRAINT ck_knowledge_sops_language
        CHECK (language IN ('ENGLISH', 'MYANMAR')),
    ADD CONSTRAINT uq_knowledge_sops_group_language
        UNIQUE (tenant_id, link_group_id, language);

CREATE INDEX ix_knowledge_sops_tenant_link_group
    ON knowledge_sops (tenant_id, link_group_id);
