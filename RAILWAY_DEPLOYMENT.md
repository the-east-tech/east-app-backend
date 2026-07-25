# EastApp Railway deployment

This package is ready for Railway's Railpack builder. Keep the existing project-root files such as `pom.xml`, `mvnw`, `.mvn/` and `compose.yaml`; replace `src/` and add the Railway files from this package.

## 1. Create the Railway project

1. In Railway, create an **Empty Project**.
2. Add **Database → PostgreSQL** and leave its service name as `Postgres`.
3. Open the Postgres service → **Settings → Source** and set the image to `ghcr.io/railwayapp-templates/postgres-ssl:18`. EastApp uses PostgreSQL 18 `uuidv7()`, so an older database version will fail Flyway V1.
4. Create an empty backend service in the same project, or let `railway up` create it.

## 2. Install and link Railway CLI

```bash
brew install railway
railway login
cd ~/IdeaProjects/east-app
railway link
```

Choose the EastApp project and the backend service when prompted.

## 3. Backend variables

Open the backend service → **Variables** → **Raw Editor**, then add:

```text
RAILPACK_JDK_VERSION=25
SPRING_DOCKER_COMPOSE_ENABLED=false
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5

EASTAPP_BOOTSTRAP_ENABLED=true
EASTAPP_BOOTSTRAP_COMPANY_CODE=EAST
EASTAPP_BOOTSTRAP_COMPANY_NAME=The East
EASTAPP_BOOTSTRAP_EMPLOYEE_ID=E0001
EASTAPP_BOOTSTRAP_FULL_NAME=Jenssen
EASTAPP_BOOTSTRAP_PHONE_E164=+60XXXXXXXXX
EASTAPP_BOOTSTRAP_PASSWORD=XXXX

EASTAPP_ATTENDANCE_TIME_ZONE=Asia/Kuala_Lumpur
EASTAPP_ATTENDANCE_LOCATION_NAME=Secret Coffee House
EASTAPP_ATTENDANCE_LATITUDE=4.3272472
EASTAPP_ATTENDANCE_LONGITUDE=101.1329829
EASTAPP_ATTENDANCE_ALLOWED_RADIUS_METERS=100
```

Use Jenssen's real E.164 phone number and current password. The minimum password length remains four characters.

## 4. Deploy local source

```bash
cd ~/IdeaProjects/east-app
railway up
```

Railpack detects Maven from `pom.xml`, builds with Java 25 and starts the Spring Boot JAR. Flyway applies V1, V2 and V3 automatically.

## 5. Public HTTPS domain

In the backend service:

1. Open **Settings → Networking**.
2. Click **Generate Domain**.
3. Open `https://<domain>/actuator/health` and expect:

```json
{"status":"UP"}
```

## 6. First Railway users

The local Docker PostgreSQL volume and Railway PostgreSQL are separate databases.

The first deployment bootstraps Jenssen only. Sign in as Jenssen, then create Nicky Chang again with the **Head** role.

After both founders exist, set:

```text
EASTAPP_BOOTSTRAP_ENABLED=false
```

Redeploy. Existing data remains.

## 7. Build Nicky's APK

```bash
cd ~/StudioProjects/east_app
flutter build apk --release \
  --dart-define=EASTAPP_API_BASE_URL=https://<railway-domain>
```

APK path:

```text
build/app/outputs/flutter-apk/app-release.apk
```

The release APK contains the public Railway API URL. Nicky does not need ur home Wi-Fi or ur Mac to stay online.
