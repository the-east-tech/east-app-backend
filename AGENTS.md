# EastApp Backend Rules

## Default execution mode

- For requests such as “fix/change/implement all above and send/give me code”, use this exact workflow: **read only what is needed → make the requested change → review the focused diff → increment the backend version once → commit to a feature branch → open a pull request → stop**.
- “All above” means every requested item in the current conversation. It does not authorise a repository-wide audit or extra improvements.
- Make the smallest complete change. Do not inspect, refactor, clean up, modernise, optimise, or reformat unrelated code.
- Do not run tests, Maven compile/build, the app, Docker, or a database unless the user explicitly asks. Do not retry environment failures.
- Do not create a ZIP, release bundle, report, documentation, or other artefact unless explicitly requested.
- Do not use plans, subagents, web research, or broad Git-history analysis unless required by the requested change or explicitly requested.
- If a necessary expansion would materially change scope or behaviour, ask first. Otherwise complete obvious implementation details without back-and-forth.

## Source and scope

- GitHub `main` is the source of truth. Fetch the latest `main` for the repository being changed before creating the feature branch.
- Do not use old ZIPs, previous-chat code, or memory as code truth.
- Do not fetch or inspect the frontend unless the backend change genuinely depends on its current contract or the user requests cross-repository work.
- Use focused searches and bounded reads. Open only relevant files or relevant sections of large files.
- Follow the existing architecture and reuse existing services, repositories, DTOs, validation, security, caching, and error-handling patterns.
- Keep list/search APIs lazy-loaded and queries focused.
- Frontend and backend versions are independent; never force them to match.
- Development database resets and clean baseline replacement are allowed only when clearly requested or required. Never reset production, shared, or unidentified databases.

## Git, versioning and delivery

- Default delivery is **feature branch + pull request**. Never push commits directly to `main` unless the user explicitly asks for a direct `main` push.
- Avoid unnecessary intermediate or throwaway commits on `main`. Keep branch history purposeful; prefer one complete versioned commit for a finished change when practical.
- **Every pull request must increment the backend version exactly once**, including documentation-only or process-only PRs. The backend version source of truth is `pom.xml`.
- Backend version format is `0.0.NNN-SNAPSHOT`. Increment `NNN` by one from the latest `main` version for each new PR.
- Commit and PR titles must be descriptive and versioned: `backend vNNN: concise description`. Example: `backend v110: clean Flyway to single V1`.
- The version number in the commit/PR title must match the version in `pom.xml`.
- Commit only the requested changes. Preserve unrelated existing changes.
- Use the assistant/service Git identity supplied by the environment. Never configure or use the user’s personal name or email as commit author.
- Do not merge the PR, deploy, or reset a database unless the user explicitly requests it.
- Final response: provide the PR link, branch name, version, and a brief list of requested changes; state that tests/builds were not run when they were not requested.

## ZIP rules — only when explicitly requested

- Name it `east_app_vNNN_src.zip`.
- Include every changed top-level folder as its complete resulting tree after additions and deletions.
- Include required changed root files individually; omit unchanged or unnecessary root files.
- Make the archive macOS Finder Replace-safe and verify its paths and integrity once.
