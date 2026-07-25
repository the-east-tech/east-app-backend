# Private bootstrap user list

The same JSON document is used locally and on Railway.

- Local source: `config/bootstrap-users.local.json`
- Railway source: `EASTAPP_BOOTSTRAP_USERS_JSON`
- Schema version: `1`

## User fields

- `key`: private stable label used only to validate the seed list; not stored in the database.
- `fullName`: name copied into each business profile.
- `phoneE164`: international phone number, such as `+60123456789`.
- `password`: development login password; BCrypt-hashed before database storage.
- `active`: optional; defaults to `true`.
- `birthDate`: optional ISO date (`YYYY-MM-DD`).
- `memberships`: one or more business-specific employee profiles.

## Membership fields

- `businessCode`: existing Flyway-seeded business code (`EAST`, `JUNE`, or `SECRET`).
- `employeeId`: employee ID used to log in within that business.
- `roleCode`: built-in role (`HEAD`, `MANAGER`, `SUPERVISOR`, `STAFF_1`, or `STAFF_2`).
- `active`: optional; defaults to `true`.
- `startDate`: optional ISO date.
- `endDate`: optional ISO date and cannot precede `startDate`.

One user may have memberships in multiple businesses. All memberships share one login
identity and password, so the user can switch business context without logging out.

Use `config/bootstrap-users.example.json` as the editable structure. Never commit the
real local user file.
