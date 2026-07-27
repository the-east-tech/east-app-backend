# Google Places setup

EastApp uses one hardcoded Google Maps server key during development.

Open:

```text
src/main/java/com/eastapp/backend/places/config/GooglePlacesProperties.java
```

Replace:

```java
private static final String HARDCODED_API_KEY = "PASTE_GOOGLE_MAPS_API_KEY_HERE";
```

with the real key. Enable **Places API (New)** for that key.

No `.env.local`, shell export, Railway variable, or Google Business Profile OAuth is required by this development package.

Google Business Location remains compulsory for Initial Setup and every tenant. This package keeps one clean Flyway `V1`; recreate the database when applying schema changes.
