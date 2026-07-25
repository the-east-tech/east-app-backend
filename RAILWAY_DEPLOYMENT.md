# EastApp Railway deployment (development database reset)

EastApp currently treats its development database as disposable. The backend can automatically clean the Railway PostgreSQL schema and rerun the single Flyway V1 before Hibernate starts.

## Backend service variables

Keep the existing PostgreSQL reference variables and add:

```text
RAILPACK_JDK_VERSION=25
SPRING_DOCKER_COMPOSE_ENABLED=false
EASTAPP_DATABASE_RESET_ON_START=true
EASTAPP_BOOTSTRAP_PHONE_E164=<set as a Railway secret>
```

Keep the existing datasource variables. The primary Head account is created automatically as `E0001` / `Jenssen` with development password `1111`; its private phone is read only from `EASTAPP_BOOTSTRAP_PHONE_E164`.

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
```

With `EASTAPP_DATABASE_RESET_ON_START=true`, every backend start deletes all current database objects and recreates them from `V1__create_eastapp_schema.sql`. The identity bootstrap then recreates the primary Head profile in every active business context. Disable or remove the reset variable before real production data exists.

## Deploy

Push the backend to the GitHub `main` branch. Railway rebuilds and redeploys automatically. The application resets and migrates the database before JPA validation, so an old Flyway checksum cannot block startup.

Health check remains:

```text
/actuator/health
```

## Local fresh run

```bash
./scripts/run-fresh-local.sh
```
