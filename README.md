# EastApp Backend

Backend API for **EastApp**, a multi-business operations application covering identity, access control, attendance, stock, knowledge, points, tenants and Google business locations.

## Current development model

- `v098` returns the one-time Initial Setup Code to the uninitialised Flutter app so it can be copied without reading backend logs
- While the disposable-development policy remains active, every schema change is merged into the single clean `V1__create_eastapp_schema.sql`; startup resets only when both reset gates are `true`
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
- Explicit English or Myanmar video language
- Optional linking to a previously created SOP video in the same tenant
- Maximum two linked video versions, enforced by the database and service layer
- English and Myanmar must use different validated YouTube video IDs
- Linked versions share the Stock tag, title, expected outcome and description; only the video and language differ
- Linked versions are returned with one shared group ID and displayed as one SOP choice
- Editing shared SOP information through either video updates both linked versions
- Deleting any selected SOP deletes every video version in its linked group transactionally
- SOP creation, editing and deletion restricted to Owners, Heads and Managers
- SOP viewing available to authenticated users in the active business

### Content translation

- Fixed Flutter interface labels use Localisation and remain independent from content translation
- User-entered English, Chinese or Myanmar text remains unchanged as the original
- The business record keeps the original value; any selected direction stores both other languages as tenant-scoped `translation_cache` rows, leaving all three language values available in PostgreSQL
- Translation cache keys include tenant, source language, target language and a SHA-256 hash of the normalised original text
- Existing translations are reused; only new or changed text calls Cloudflare Workers AI
- `POST /api/v1/translations/preview` queries PostgreSQL only and reports selected-language and companion-language cache hits, cache misses and the provider requests required if confirmed
- The preview never invokes Cloudflare and never writes translation rows
- Flutter calls the provider-backed translation endpoint only after the user taps Save and confirms the cost warning
- Navigating, reopening a page, loading cached feature data and saving normal business data never trigger Cloudflare translation calls; they only collect text for the next explicit Translate Save
- The next Translate Save considers matching content discovered across pages visited in the current signed-in session, and the preview reports that full scope before confirmation
- A missing source may require two provider requests because the selected and companion languages are both stored
- Double taps join the same in-flight preview or translation operation and do not submit a second provider-backed request
- Cloudflare credentials remain backend environment secrets and are never shipped in Flutter

Enable translation with:

```text
EASTAPP_TRANSLATION_PROVIDER_ENABLED=true
EASTAPP_CLOUDFLARE_ACCOUNT_ID=<Cloudflare account ID>
EASTAPP_CLOUDFLARE_API_TOKEN=<Workers AI token>
```

When the provider is disabled or its credentials are absent, EastApp can still reuse stored translations. A cache miss returns a provider-disabled response without calling Cloudflare.

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

## Run locally

From the repository root:

```bash
./scripts/run-local.sh
```

The script:

1. Reads `EASTAPP_DATABASE_RESET_ON_START` and defaults it to `false`
2. Starts PostgreSQL 18
3. Resets the database only when both independent reset gates are `true`
4. Starts Spring Boot
5. Generates a one-time Initial Setup code only when setup has not been completed

To request a reset, use the same script:

```bash
EASTAPP_DATABASE_RESET_ON_START=true ./scripts/run-local.sh
```

There is no repair step. When reset is not approved, Flyway checksum validation is skipped and the existing database is preserved.

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
2. Open EastApp Flutter
3. Copy the Setup Code from the one-time popup
4. Complete Initial Setup using the copied code
5. Select the tenant's Google business location
6. Create the first Owner account

`GET /api/v1/setup/status` returns the active Setup Code and expiry only while
Initial Setup remains incomplete. Its response is marked `no-store`. This is a
convenience for the current deployment model and means the code is visible to
anyone who can reach an uninitialised backend.

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
| Create/edit/delete Knowledge SOP | Yes | Yes | Yes | No |
| View Knowledge SOP | Yes | Yes | Yes | Yes |
| Cross-business SKU copy | Yes | No | No | No |

User visibility follows the fixed hierarchy: Owner sees all users, Head cannot see Owner users, and Manager cannot see Owner or Head users. Supervisor and Staff roles cannot access user management. Owners share access across all tenants; all other roles remain in their active tenant and cannot access tenant management.

## Caching strategy

No Redis or general backend data cache is added at this stage.

- Tenant, context, role, tag and SOP datasets are currently small
- Correct cache invalidation would add complexity without evidence of a backend bottleneck
- Google rating and translated user content are the only backend-cached external values
- Translation uses PostgreSQL rather than Redis so identical tenant content survives restarts and does not consume AI again
- Flutter caches tenant and authentication-context lists in memory for five minutes and invalidates them after tenant, user, login/logout or context changes
- Consider Caffeine first when repeated backend computation becomes measurable
- Consider Redis only when EastApp runs multiple backend instances or requires shared distributed cache/session behaviour

## Database and Flyway

EastApp `v098` protects destructive database reset with two independent gates. `flyway.clean()` can run only when **both** are true:

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

Without an environment value, `run-local.sh` defaults to:

```text
EASTAPP_DATABASE_RESET_ON_START=false
```

Local reset request using the same script:

```bash
EASTAPP_DATABASE_RESET_ON_START=true ./scripts/run-local.sh
```

The script never deletes the PostgreSQL Docker volume directly. The backend resets only when the code gate is also `true`. Otherwise it preserves the database and skips checksum validation. Flyway repair is never run.

### Railway

Keep the existing Railway variable:

```text
EASTAPP_DATABASE_RESET_ON_START=false
```

For a deliberate Railway reset, the source-code gate and Railway variable must both be `true`. Return the Railway variable to `false` after the reset deployment.

The current release policy deliberately resets the database for each release. Keep one clean `V1__create_eastapp_schema.sql`, merge every schema change into that file, and do not create V2+ migrations unless this policy is explicitly changed.


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
EASTAPP_TRANSLATION_PROVIDER_ENABLED=true
EASTAPP_CLOUDFLARE_ACCOUNT_ID=<Cloudflare account ID>
EASTAPP_CLOUDFLARE_API_TOKEN=<Workers AI token>
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
├── stock/
└── translation/

src/main/resources/
├── application.yaml
└── db/migration/
    └── V1__create_eastapp_schema.sql

scripts/
└── run-local.sh

requests.http
compose.yaml
railway.json
```

## Development workflow

For every backend change:

1. Apply the change locally
2. Start Spring Boot locally
3. Let Flyway reset and apply V1 when both reset gates are enabled; otherwise preserve the database
4. Verify the affected behaviour
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
