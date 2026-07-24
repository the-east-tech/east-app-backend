# EastApp backend v005

Based on v004.

## Added

- Attendance event persistence with Flyway `V2__create_attendance_events.sql`.
- `POST /api/v1/attendance/events` for authenticated clock-in/clock-out submissions.
- `GET /api/v1/attendance/today` for the signed-in employee.
- Head-only `GET /api/v1/attendance/audit` with DAY, WEEK, MONTH and YEAR views.
- Server-side geofence distance calculation using the configured work location.
- Immutable capture metadata: server time, device capture time, GPS, accuracy, face count/box/angles, camera/QR validation, platform, OS and app version.
- Idempotent `clientEventId` handling to prevent duplicate submissions.
- Railway Railpack configuration, PostgreSQL 18 setup guide and CLI deployment guide.

## Privacy

- No attendance photo column exists.
- No attendance photo is uploaded or stored.
- The API response explicitly returns `photoStored: false`.

## Unchanged

- Identity/authentication/session design.
- User and Role APIs.
- Existing V1 schema and existing local data.
- Password minimum remains four characters.
