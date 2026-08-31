# EastApp Backend Rules

- Fetch the latest `main` from both repositories and state the exact independent frontend/backend versions.
- GitHub `main` is the only source of truth. Do not use old ZIPs or previous-chat memory.
- Ask before coding if anything is unclear. Work only on the requested scope.
- Follow the existing architecture, reuse existing code, and design list/search APIs for lazy loading.
- Development database reset and migration-baseline replacement are allowed. Never reset production, shared, or unidentified databases.
- Run Maven compile once and only relevant tests. Do not start the app, Docker, or database. Do not retry environment failures.
- Never force the backend version to match the frontend version.
- Packaging rule: changed folder = include whole folder; changed root file = include that file; unchanged = omit. No huge outputs.
- Do not commit or push unless explicitly requested.
