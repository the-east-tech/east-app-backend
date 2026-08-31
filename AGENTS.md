# EastApp Backend Rules

- Source: GitHub `main` only. Fetch the backend; read only frontend `pubspec.yaml` unless its source is needed. State both independent versions; ignore old ZIPs and previous-chat code.
- Scope: ask if unclear, make only the smallest complete requested change, reuse the existing architecture, and keep list/search APIs lazy-loaded.
- Database: development resets and baseline replacement are allowed; never reset production, shared, or unidentified databases.
- Verify: run Maven compile once plus only relevant tests. Never start the app, Docker, or database, or retry environment failures.
- Version: never force the backend version to match the frontend version.
- Release ZIP: name it `east_app_vNNN_src.zip` and make it macOS Finder Replace-safe. Include every changed top-level folder as its complete resulting tree after additions/deletions; include changed root files individually; omit unchanged root files. Verify ZIP paths against `main`; do not deliver an unverifiable ZIP.
- Efficiency: minimise tokens with focused searches, bounded reads, and concise non-repetitive updates. Avoid full logs/files/diffs. Do not use plans, subagents, or web research unless required or requested.
- Git: do not commit or push unless explicitly requested