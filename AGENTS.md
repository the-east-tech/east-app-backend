# EastApp Backend Rules

## Default execution mode

- For requests such as “fix/change/implement all above and send/give me code”, use this exact workflow: **read only what is needed → make the requested change → review the focused diff → commit and push → stop**.
- “All above” means every requested item in the current conversation. It does not authorise a repository-wide audit or extra improvements.
- Make the smallest complete change. Do not inspect, refactor, clean up, modernise, optimise, or reformat unrelated code.
- Do not run tests, Maven compile/build, the app, Docker, or a database unless the user explicitly asks. Do not retry environment failures.
- Do not create a ZIP, release bundle, report, documentation, or other artefact unless explicitly requested.
- Do not use plans, subagents, web research, or broad Git-history analysis unless required by the requested change or explicitly requested.
- If a necessary expansion would materially change scope or behaviour, ask first. Otherwise complete obvious implementation details without back-and-forth.

## Source and scope

- GitHub `main` is the source of truth. Fetch the latest `main` for the repository being changed.
- Do not use old ZIPs, previous-chat code, or memory as code truth.
- Do not fetch or inspect the frontend unless the backend change genuinely depends on its current contract or the user requests cross-repository work.
- Use focused searches and bounded reads. Open only relevant files or relevant sections of large files.
- Follow the existing architecture and reuse existing services, repositories, DTOs, validation, security, caching, and error-handling patterns.
- Keep list/search APIs lazy-loaded and queries focused.
- Frontend and backend versions are independent; never force them to match.
- Development database resets and clean baseline replacement are allowed only when clearly requested or required. Never reset production, shared, or unidentified databases.

## Git and delivery

- An implementation request that says “send/give me code” authorises a direct commit and push to `main`, unless the user asks for a ZIP, patch, branch, or no push.
- Commit only the requested changes. Preserve any unrelated existing changes.
- Use the assistant/service Git identity supplied by the environment. Never configure or use the user’s personal name or email as commit author.
- Final response: state the pushed commit SHA, list the requested changes briefly, and state that tests/builds were not run when they were not requested.

## ZIP rules — only when explicitly requested

- Name it `east_app_vNNN_src.zip`.
- Include every changed top-level folder as its complete resulting tree after additions and deletions.
- Include required changed root files individually; omit unchanged or unnecessary root files.
- Make the archive macOS Finder Replace-safe and verify its paths and integrity once.
