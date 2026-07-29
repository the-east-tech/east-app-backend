# Google Maps setup

EastApp uses one hardcoded Google Maps server key during development.

Open:

```text
src/main/java/com/eastapp/backend/places/config/GooglePlacesProperties.java
```

Replace:

```java
private static final String HARDCODED_API_KEY = "PASTE_GOOGLE_MAPS_API_KEY_HERE";
```

with the real key.

Enable these APIs for the same Google Cloud project and key:

- **Places API (New)** — business search, business coordinates and Google rating
- **Geocoding API** — converts captured attendance coordinates into a readable address

No `.env.local`, shell export, Railway variable, or Google Business Profile OAuth is required by this development package.

Google Business Location remains compulsory for Initial Setup and every tenant. The saved business coordinates are an attendance reference point only. Staff can Clock In/Out from any distance. EastApp stores the captured address, GPS accuracy, business-location snapshot and calculated distance from the office.

This package keeps one clean Flyway `V1`; recreate the database when applying schema changes.
