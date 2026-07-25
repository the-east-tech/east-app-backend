# EastApp Railway deployment (disposable development database)

EastApp currently treats its development database as disposable. The backend uses one Flyway V1 and automatically cleans the database before migrating while `EASTAPP_DATABASE_RESET_ON_START=true`.

## Railway backend variables

```text
RAILPACK_JDK_VERSION=25
SPRING_DOCKER_COMPOSE_ENABLED=false
EASTAPP_DATABASE_RESET_ON_START=true
EASTAPP_BOOTSTRAP_ENABLED=true
EASTAPP_BOOTSTRAP_PHONE_E164=<sealed Railway variable>
EASTAPP_BOOTSTRAP_PASSWORD=1111
```

Datasource references:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
```

The Flyway strategy is always registered. When reset is enabled it performs `clean()` before `migrate()`, so an older V1 checksum cannot block startup. When production data exists, set `EASTAPP_DATABASE_RESET_ON_START=false`; the same strategy then preserves the database and runs normal Flyway migration only.

## Deploy

Replace the backend source with this package, commit, and push to the GitHub branch connected to Railway. Confirm the Railway deployment uses that commit. The startup log must contain:

```text
EASTAPP_DATABASE_RESET_ON_START=true: deleting all database objects and recreating the schema from Flyway V1
```

If that line is absent, Railway is deploying an older commit or overriding the variable with `false`.

## Local fresh run

```bash
./scripts/run-fresh-local.sh
```
