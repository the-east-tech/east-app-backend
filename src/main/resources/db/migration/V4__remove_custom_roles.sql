-- EastApp v062: remove the custom-role concept without changing historical migrations.
-- V1 is immutable once applied. This migration upgrades both existing and fresh databases.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users u
        JOIN roles r ON r.id = u.role_id
        WHERE r.system_key IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot remove custom roles because one or more users are still assigned to a custom role. Reassign those users to a built-in role first.';
    END IF;
END
$$;

-- Remove unused legacy custom roles, if any.
DELETE FROM roles
WHERE system_key IS NULL;

ALTER TABLE roles
    ALTER COLUMN system_key SET NOT NULL;

ALTER TABLE roles
    DROP CONSTRAINT ck_roles_system_key;

ALTER TABLE roles
    ADD CONSTRAINT ck_roles_system_key
    CHECK (system_key IN ('OWNER', 'HEAD', 'MANAGER', 'SUPERVISOR', 'STAFF_1', 'STAFF_2'));
