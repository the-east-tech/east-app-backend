CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    company_code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_tenants_company_code
        UNIQUE (company_code),

    CONSTRAINT ck_tenants_company_code_uppercase
        CHECK (company_code = upper(company_code)),

    CONSTRAINT ck_tenants_company_code_format
        CHECK (company_code ~ '^[A-Z0-9][A-Z0-9_-]{1,31}$'),

    CONSTRAINT ck_tenants_name_not_blank
        CHECK (btrim(name) <> '')
);


CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    tenant_id UUID NOT NULL,
    system_key VARCHAR(32),
    name VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_roles_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_roles_tenant_id_id
        UNIQUE (tenant_id, id),

    CONSTRAINT uq_roles_tenant_system_key
        UNIQUE (tenant_id, system_key),

    CONSTRAINT ck_roles_system_key
        CHECK (
            system_key IS NULL
            OR system_key IN (
                'HEAD',
                'MANAGER',
                'SUPERVISOR',
                'STAFF_1',
                'STAFF_2'
            )
        ),

    CONSTRAINT ck_roles_name_not_blank
        CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX uq_roles_tenant_name_ci
    ON roles (tenant_id, lower(name));


CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    tenant_id UUID NOT NULL,
    employee_id VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    full_name VARCHAR(120) NOT NULL,
    phone_e164 VARCHAR(16) NOT NULL,
    profile_photo_key VARCHAR(255),
    birth_date DATE,
    start_date DATE,
    end_date DATE,

    role_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_users_role_same_tenant
        FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles (tenant_id, id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_users_tenant_employee_id
        UNIQUE (tenant_id, employee_id),

    CONSTRAINT ck_users_employee_id_uppercase
        CHECK (employee_id = upper(employee_id)),

    CONSTRAINT ck_users_employee_id_format
        CHECK (employee_id ~ '^[A-Z0-9][A-Z0-9_-]{1,31}$'),

    CONSTRAINT ck_users_password_hash_not_blank
        CHECK (btrim(password_hash) <> ''),

    CONSTRAINT ck_users_full_name_not_blank
        CHECK (btrim(full_name) <> ''),

    CONSTRAINT ck_users_phone_e164_format
        CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),

    CONSTRAINT ck_users_profile_photo_key_not_blank
        CHECK (
            profile_photo_key IS NULL
            OR btrim(profile_photo_key) <> ''
        ),

    CONSTRAINT ck_users_employment_dates
        CHECK (
            end_date IS NULL
            OR start_date IS NULL
            OR end_date >= start_date
        )
);


CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    user_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT uq_user_sessions_token_hash
        UNIQUE (token_hash),

    CONSTRAINT ck_user_sessions_token_hash_length
        CHECK (octet_length(token_hash) = 32),

    CONSTRAINT ck_user_sessions_revoked_at
        CHECK (
            revoked_at IS NULL
            OR revoked_at >= created_at
        )
);

CREATE INDEX ix_user_sessions_user_id
    ON user_sessions (user_id);
