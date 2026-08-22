WITH canonical_sops AS (
    SELECT DISTINCT ON (tenant_id, link_group_id)
        tenant_id,
        link_group_id,
        tag_id,
        title,
        expected_outcome,
        description
    FROM knowledge_sops
    ORDER BY
        tenant_id,
        link_group_id,
        CASE WHEN language = 'ENGLISH' THEN 0 ELSE 1 END,
        created_at,
        id
)
UPDATE knowledge_sops AS target
SET tag_id = canonical.tag_id,
    title = canonical.title,
    expected_outcome = canonical.expected_outcome,
    description = canonical.description,
    updated_at = CURRENT_TIMESTAMP
FROM canonical_sops AS canonical
WHERE target.tenant_id = canonical.tenant_id
  AND target.link_group_id = canonical.link_group_id
  AND (
      target.tag_id IS DISTINCT FROM canonical.tag_id
      OR target.title IS DISTINCT FROM canonical.title
      OR target.expected_outcome IS DISTINCT FROM canonical.expected_outcome
      OR target.description IS DISTINCT FROM canonical.description
  );
