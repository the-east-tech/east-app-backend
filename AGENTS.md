# EastApp Backend Rules

- Fetch the latest `main` from both repositories and state the exact independent frontend/backend versions.
- GitHub `main` is the only source of truth. Do not use old ZIPs or previous-chat memory.
- Ask before coding if anything is unclear. Work only on the requested scope.
- Follow the existing architecture, reuse existing code, and design list/search APIs for lazy loading.
- Development database reset and migration-baseline replacement are allowed. Never reset production, shared, or unidentified databases.
- Run Maven compile once and only relevant tests. Do not start the app, Docker, or database. Do not retry environment failures.
- Never force the backend version to match the frontend version.
- Release ZIP naming: `east_app_vNNN_src.zip`.
- Release ZIPs must be macOS Finder Replace-safe. If any folder is included in a release ZIP, include the ENTIRE resulting folder from the base version after applying the requested changes: every unchanged existing file, every changed file, and every newly added file. Never package a partial folder under a path the user replaces wholesale.
- In particular, if `src/` is included, the ZIP must contain the COMPLETE resulting `src/` tree. If `scripts/` is included, the ZIP must contain the COMPLETE resulting `scripts/` tree. A user replacing either folder in macOS Finder must not lose unrelated files.
- Changed root files are included individually. Unchanged root files are omitted.
- Before delivery, verify every included folder's complete file/path set against the GitHub `main` base tree plus intended additions/deletions. Do not deliver the ZIP if replacement safety cannot be verified.
- Do not commit or push unless explicitly requested.
