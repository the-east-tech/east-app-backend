# EastApp Railway deployment — disposable development database

EastApp currently treats development data as disposable. While
`EASTAPP_DATABASE_RESET_ON_START=true`, every backend startup cleans the database,
applies the single Flyway V1, then creates development users from one private JSON list.

Flyway contains schema and non-personal reference data only. Jenssen, Nicky, and future
users all use the same bootstrap mechanism.

## 1. Create the private user list locally

Run once:

```bash
./scripts/configure-bootstrap-users.sh
```

This creates:

```text
config/bootstrap-users.local.json
```

The file is permission `600` and added to the repository's local Git exclude file.
Edit this JSON file whenever development users, credentials, roles, or business
memberships need to change.

## 2. Local fresh run

```bash
EASTAPP_DATABASE_RESET_ON_START=true ./scripts/run-local.sh
```

The backend resets local PostgreSQL only when its code gate is also enabled, applies Flyway V1, and bootstraps every user
from `config/bootstrap-users.local.json`.

## 3. Configure the same list in Railway

Generate a compact one-line copy of the local JSON:

```bash
./scripts/print-railway-bootstrap-users.sh
```

In the Railway backend service, create one sealed/private variable and paste that
complete output as its value:

```text
EASTAPP_BOOTSTRAP_USERS_JSON=<complete compact JSON>
```

Required backend variables:

```text
RAILPACK_JDK_VERSION=25
SPRING_DOCKER_COMPOSE_ENABLED=false
EASTAPP_DATABASE_RESET_ON_START=true
EASTAPP_BOOTSTRAP_ENABLED=true
EASTAPP_BOOTSTRAP_USERS_JSON=<sealed compact JSON>
```

Datasource references:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
```

The backend gives `EASTAPP_BOOTSTRAP_USERS_JSON` precedence over the local file path,
so Railway does not require a file upload.

## 4. Add or change users during development

1. Edit `config/bootstrap-users.local.json`.
2. Run locally and verify login/context switching.
3. Run `./scripts/print-railway-bootstrap-users.sh`.
4. Replace the Railway `EASTAPP_BOOTSTRAP_USERS_JSON` value.
5. Redeploy the backend.

Each user has one login identity and one or more business memberships. Passwords are
BCrypt-hashed by Spring before database storage. Raw phone numbers and passwords are
never written to Flyway, Git-tracked configuration, application logs, or changelogs.

## 5. Production transition

Before retaining production data:

```text
EASTAPP_DATABASE_RESET_ON_START=false
EASTAPP_BOOTSTRAP_ENABLED=false
```

After that point, create normal employees through People → User and use append-only
Flyway migrations instead of rewriting V1.
