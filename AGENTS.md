# EastApp Backend Rules

## Execution

- Answer, review, explain and diagnose requests are read-only. Do not create a branch, commit or PR unless code/file changes are requested.
- A request to fix, change or implement authorises the complete delivery workflow; do not pause for separate push approval:
  **fetch latest `main` → inspect only what is needed → create or reuse one task branch → make the smallest complete change → review the focused diff → bump the version once → commit once → push → open or update the PR → stop**.
- “All above” means only the requested items in the current conversation. It does not authorise a repository-wide review or unrelated improvements.
- Do not run tests, Maven, builds, the app, Docker or a database unless explicitly requested. Do not retry unavailable tooling.
- Do not create plans, subagents, ZIPs, documentation or other artefacts unless required or explicitly requested.
- Ask only when missing information would materially change behaviour. Otherwise complete obvious details without extra confirmation.

## Branch and PR control

- GitHub `main` is the source of truth. Fetch it before editing and never use an old ZIP, stale branch, previous-chat code or memory as code truth.
- Check the existing PR state before creating a branch.
- If an open PR already covers the same unfinished task, continue that branch and PR. Do not create another branch, PR or version bump.
- If that PR is merged or closed, always create a new branch from the latest `main` and open a new PR. Never reuse its old branch.
- A different task gets one new branch and one PR. Never create branches or commits per file, attempt or minor correction.
- Default delivery is a feature branch plus PR. Never push directly to `main`, merge, deploy or reset a database unless explicitly requested.

## Scope

- Change only requested backend files. Preserve unrelated user changes.
- Do not inspect the frontend unless the API contract requires it or cross-repository work is requested.
- Use focused searches and bounded reads. Avoid unrelated refactors, reformatting, modernisation and optimisation.
- Follow the existing architecture and reuse existing services, repositories, DTOs, validation, security, caching and error handling.
- Keep list/search APIs lazy-loaded and queries focused.
- Frontend and backend versions are independent.
- Reset a development database only when clearly requested or required. Never reset production, shared or unidentified databases.

## Version, commit and PR

- Every new PR, including a documentation-only PR, increments `pom.xml` exactly once.
- Use `0.0.NNN-SNAPSHOT`. Select one above the highest backend version on latest `main` or any open backend PR, whichever is higher.
- Further changes to the same open PR do not increment the version again.
- Finish and review the requested change before committing. Prefer one commit; do not commit each file or attempt separately.
- Commit and PR title: `backend vNNN: concise description`. Keep it one line and at most 72 characters.
- Keep the PR body short: requested changes plus whether checks were run. Do not add long narratives or code dumps.
- The title version must match `pom.xml`.
- Use the configured assistant/service Git identity, never the user’s personal identity.
- Final response: PR link, branch, version, brief changes and checks not run.

## ZIP delivery — explicit fallback only

- Git/PR is the default. Create a ZIP only when explicitly requested; do not provide both unless requested.
- ZIP delivery does not create a branch, commit or PR unless explicitly requested.
- Name it `east_app_vNNN_src.zip`.
- The ZIP is extracted at the project root. Do not add a wrapper directory inside it.
- Include every changed top-level folder as its complete final tree so macOS Finder Replace does not remove unchanged files. Include required changed root files individually.
- Omit unchanged root files, generated files, caches and unrelated content.
- Verify the archive root paths and integrity once.
