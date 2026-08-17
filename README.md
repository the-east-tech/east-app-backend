# EastApp Backend

Backend API for **EastApp**, a multi-business operations application covering identity, access control, attendance, stock, knowledge, points, tenants and Google business locations.

## Current development model

- `v069` uses the consolidated Flyway V1 baseline; phone-login removal does not require a schema change
- After this reset, `V1__create_eastapp_schema.sql` is immutable and every later schema change must use `V2+`
- No seeded tenants or users
- Initial Setup creates the first tenant and first `OWNER`
- Each tenant represents one independent business location
- Business codes are globally unique
- Every tenant must have a Google business location and attendance geofence

## Technology stack

- Java 25
- Spring Boot 4.1
- Spring MVC
- Spring Data JPA
- Spring Security
- PostgreSQL 18
- Flyway
- Maven Wrapper
- Docker Compose
- Railway Railpack
- Google Places API (New)

## Main capabilities

### Identity and access

- Login using company code, employee ID and password; phone number remains part of the employee profile but is not a login credential
- Opaque bearer-session authentication
- Password hashing through Spring Security
- Tenant-aware access control
- Owner-only business context switching

Built-in roles:

```text
OWNER
HEAD
MANAGER
SUPERVISOR
STAFF_1
STAFF_2
```

### Tenants

- Create and update business tenants
- Globally unique company codes
- Tenant-specific employee ID prefixes
- Compulsory Google business location
- Tenant-specific attendance geofence
- Every Owner can access every tenant

### People

- Users
- Fixed roles only: Owner, Head, Manager, Supervisor, Staff1 and Staff2
- Password reset
- Tenant-specific employee IDs
- Attendance audit and reporting
- Tenant-scoped point assignment and deduction by Owners and Heads; Owner accounts are excluded from employee points and leaderboard ranking
- Immutable point adjustment history with compulsory reasons
- Active-user leaderboard ranked by accumulated total points

### Attendance

- Clock In and Clock Out events
- Device-captured timestamp and location
- Tenant-specific business-location reference for attendance distance calculation
- GPS coordinates, reverse-geocoded address and distance-from-business metadata
- Action-specific Check In / Check Out attendance QR codes, reusable by multiple employees in the same tenant for 30 minutes
- Daily, monthly and yearly audit views
- Behaviour and employee-level reporting

Attendance no longer stores face-detection or face-photo data. Attendance events require a backend-validated QR code plus GPS evidence.

### Stock

- SKUs
- Tags
- Suppliers
- Daily stock counts
- Receiving records
- Media metadata
- Approval and rejection workflow
- Audit trail
- Cross-business SKU copy for Owners

Cross-business copying duplicates the selected SKUs together with their tags and suppliers. The copied records are independent from the source tenant.

### Knowledge

- Tenant-scoped SOP storage
- Mandatory YouTube video URL
- Mandatory Stock tag, title, expected outcome and description
- Embedded-video metadata through a validated YouTube video ID
- SOP creation restricted to Owners and Heads
- SOP viewing available to authenticated users in the active business

### Home data

- Five latest Stock Audit Trail records performed by the current logged-in user
- Current logged-in user's real accumulated point total
- Current-business leaderboard
- Today's combined Daily Count and Receiving review summary
- `Pending Review` count
- `Done` count, where both Approved and Rejected records are considered done
- Approvals opens Stock → Review and returns to Home on Back

### Google Places

- Business-location autocomplete
- Place details and coordinates
- Google Maps URL
- Google rating and review count
- Tenant-specific rating display in EastApp
- Configurable rating cache

The current development shortcut keeps the Google Places server key in one Java constant:

```text
src/main/java/com/eastapp/backend/places/config/GooglePlacesProperties.java
```

The development key is already configured in `GooglePlacesProperties.java`.
Do not replace it with a placeholder when repackaging the source.

Enable **Places API (New)** for that key.

> This hardcoded-key approach is intentionally simple for current development. Before production, move the key to a backend secret and restrict its API access and quota.

## Prerequisites

- JDK 25
- Docker Desktop
- Git

Maven does not need to be installed separately because the repository includes `mvnw`.

## Run locally while preserving the database

From the repository root:

```bash
./scripts/run-local.sh
```

The script:

1. Preserves the existing PostgreSQL volume
2. Starts PostgreSQL 18
3. Applies pending Flyway migrations
4. Starts Spring Boot
5. Prints a one-time Initial Setup code only when setup has not been completed

A destructive reset remains available through `scripts/run-fresh-local.sh`, but it now requires the explicit environment variable `EASTAPP_CONFIRM_DATABASE_RESET=YES`.

The setup code is valid for 1 hour.

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Initial Setup

On an empty database:

1. Start the backend
2. Read the setup code from the backend terminal
3. Open EastApp Flutter
4. Complete Initial Setup
5. Select the tenant's Google business location
6. Create the first Owner account

Initial Setup creates:

- The first tenant
- Built-in roles
- The first Owner
- The first tenant-specific employee ID

No tenant or user is seeded automatically.

The same flow can be tested through `requests.http`:

```http
GET /api/v1/setup/status
POST /api/v1/setup/owner
```

## Normal local startup without deleting data

```bash
./mvnw spring-boot:run
```

By default, Spring Boot Docker Compose support starts the PostgreSQL service when required.

## API testing

Use the IntelliJ HTTP Client file:

```text
requests.http
```

It contains requests for:

- Initial Setup
- Login and logout
- Business context switching
- Tenants
- Users and roles
- Attendance
- Stock
- Google Places
- Home Stock activity and review summary
- Knowledge SOPs

The Login request stores `eastappToken` automatically for authenticated requests.

## Authentication

Authenticated endpoints use:

```http
Authorization: Bearer <opaque-session-token>
```

EastApp currently uses opaque session tokens rather than JWT.

## Access summary

| Capability | Owner | Head | Manager | Supervisor / Staff |
|---|---:|---:|---:|---:|
| Switch business context | Yes | No | No | No |
| View/manage tenants | Yes | No | No | No |
| Create tenant | Yes | No | No | No |
| Access People → User | Yes | Yes | Yes | No |
| Create/assign Owner | Yes | No | No | No |
| Create/assign Head | Yes | Yes | No | No |
| Create/assign Manager | Yes | Yes | No | No |
| Create/assign Supervisor | Yes | Yes | Yes | No |
| Create/assign Staff1 / Staff2 | Yes | Yes | Yes | No |
| Stock Audit Trail screen | Yes | Yes | No | No |
| Home own Stock activity | Yes | Yes | Yes | Yes |
| Home today's review summary | Yes | Yes | Yes | No |
| Create Knowledge SOP | Yes | Yes | No | No |
| View Knowledge SOP | Yes | Yes | Yes | Yes |
| Cross-business SKU copy | Yes | No | No | No |

User visibility follows the fixed hierarchy: Owner sees all users, Head cannot see Owner users, and Manager cannot see Owner or Head users. Supervisor and Staff roles cannot access user management. Owners share access across all tenants; all other roles remain in their active tenant and cannot access tenant management.

## Caching strategy

No Redis or general backend data cache is added at this stage.

- Tenant, context, role, tag and SOP datasets are currently small
- Correct cache invalidation would add complexity without evidence of a backend bottleneck
- Google rating remains the only backend-cached external value
- Flutter caches tenant and authentication-context lists in memory for five minutes and invalidates them after tenant, user, login/logout or context changes
- Consider Caffeine first when repeated backend computation becomes measurable
- Consider Redis only when EastApp runs multiple backend instances or requires shared distributed cache/session behaviour

## Database and Flyway

EastApp `v069` protects destructive database reset with two independent gates. `flyway.clean()` can run only when **both** are true:

1. Code gate: `DATABASE_RESET_ALLOWED_BY_CODE`
2. Environment gate: `EASTAPP_DATABASE_RESET_ON_START`

Current code gate in this package:

```text
DATABASE_RESET_ALLOWED_BY_CODE=true
```

Truth table:

| Code gate | EASTAPP_DATABASE_RESET_ON_START | Reset |
|---|---|---|
| false | false | No |
| false | true | No |
| true | false | No |
| true | true | Yes |

### Local commands

Normal local start, preserving all database data:

```bash
./scripts/run-local.sh
```

`run-local.sh` explicitly sets:

```text
EASTAPP_DATABASE_RESET_ON_START=false
```

Fresh local reset request:

```bash
EASTAPP_CONFIRM_DATABASE_RESET=YES ./scripts/run-fresh-local.sh
```

`run-fresh-local.sh` no longer runs `docker compose down -v` and no longer deletes the PostgreSQL Docker volume directly. It sets `EASTAPP_DATABASE_RESET_ON_START=true`, starts PostgreSQL, and lets the backend's two-gate protection decide whether `flyway.clean()` is allowed.

Therefore a local reset occurs only when the code gate is also `true`.

### Railway

Keep the existing Railway variable:

```text
EASTAPP_DATABASE_RESET_ON_START=false
```

For a deliberate reset, both the source-code gate and Railway variable must be deliberately changed to `true`. On Railway, the current consolidated-baseline transition also retains the additional legacy-history safety check.

The consolidated `V1__create_eastapp_schema.sql` is the clean baseline containing the former V1-V4 schema. Once established, V1 must remain immutable and all future schema changes must use new append-only V2+ migrations.


## Railway deployment

Railway uses Railpack and checks:

```text
/actuator/health
```

Core Railway settings:

```text
RAILPACK_JDK_VERSION=25
SPRING_DOCKER_COMPOSE_ENABLED=false
EASTAPP_DATABASE_RESET_ON_START=false
```

Datasource variables should reference the Railway PostgreSQL service:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
```


Detailed deployment guidance should live in:

```text
docs/RAILWAY_DEPLOYMENT.md
```

## Project structure

```text
src/main/java/com/eastapp/backend/
├── attendance/
├── auth/
├── common/
├── config/
├── knowledge/
├── organisation/
├── people/
├── places/
├── points/
├── setup/
└── stock/

src/main/resources/
├── application.yaml
└── db/migration/
    └── V1__create_eastapp_schema.sql

scripts/
├── run-local.sh
└── run-fresh-local.sh

requests.http
compose.yaml
railway.json
```

## Development workflow

For every backend change:

1. Apply the change locally
2. Start Spring Boot locally
3. Let Flyway validate the existing schema and apply pending migrations
4. Verify Hibernate validation
5. Test affected endpoints in `requests.http`
6. Keep all changed and newly added endpoints represented in `requests.http`
7. Commit and push only after local success

Use `./mvnw spring-boot:run` for normal development. Run the full test suite for schema, security, risky changes and release preparation.

## Documentation

Keep detailed guides separate from this README:

- [Railway deployment](docs/RAILWAY_DEPLOYMENT.md)
- [Google Places setup](docs/GOOGLE_PLACES_SETUP.md)

Remove obsolete bootstrap documentation and scripts. Initial Setup is now the only supported first-user creation flow.


## Business Report module

The clean V1 baseline includes the tenant-scoped reporting workflow used by the five Flutter Report cards: Sales, Inventory Intelligence, Waste, Daily Photos and Complaints.

- Sales is one report per business/day. Cash Total, Food Delivery Sales, eWallet Total, Cash Received By and Staff on Duty are compulsory. Total Sales, payment mix, Void Total, Void Exposure and Sales per Staff are derived. Void Bills are optional evidence added before submission. A submitted report is read-only unless Owner or Head rejects it.
- Void Bills are append-only evidence entries with a compulsory photo, bill number, reason and amount. Bill numbers are unique per Sales report without case sensitivity.
- Inventory Intelligence is calculated from active SKU balances, limits and price ranges; no duplicate inventory form is stored.
- Waste records include photo evidence and estimated loss, then enter the approval workflow.
- Every active Manager, Supervisor, Staff 1 and Staff 2 user is expected to submit at least five Daily Photos. Owner and Head are excluded from that requirement.
- Complaints track customer profile, action, compensation and Open/Resolved status without a separate approval step.
- Owner, Head, Manager and Supervisor can view business analytics. Only Owner, Head or Manager can approve reports; Manager cannot approve their own submission.
- Report evidence media can only be attached by the user account that uploaded it.

Configuration:

```yaml
eastapp:
  reports:
    time-zone: Asia/Kuala_Lumpur
    daily-photo-minimum: 5
```

## V4 attendance evidence and atomic Daily Count review


Daily Count bulk review now uses one transactional endpoint, `PATCH /api/v1/stock/counts/bulk-review`. The service validates every selected record before changing any of them, so an invalid or previously reviewed record rolls back the whole batch.
