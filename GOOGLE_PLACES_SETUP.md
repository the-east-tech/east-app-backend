# Google Maps setup

EastApp uses one hardcoded Google Maps server key during development.

Open:

```text
src/main/java/com/eastapp/backend/places/config/GooglePlacesProperties.java
```

The development key is already configured in this file.
Do not replace it with a placeholder when repackaging the source.

Enable these APIs for the same Google Cloud project and key:

- **Places API (New)** — business search, business coordinates and Google rating
- **Geocoding API** — converts captured attendance coordinates into a readable address

No `.env.local`, shell export, Railway variable, or Google Business Profile OAuth is required by this development package.

Google Business Location remains compulsory for Initial Setup and every tenant. The saved business coordinates are an attendance reference point only. Staff can Clock In/Out from any distance. EastApp stores the captured address, GPS accuracy, business-location snapshot and calculated distance from the office.

EastApp v052 is a deliberate clean Flyway baseline. Reset the old development database once, then keep the new V1 immutable and add each later schema change as V2+.
