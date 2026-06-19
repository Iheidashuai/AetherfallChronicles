# Agent Context

This repository is a single-player learning game project.

## Maintenance Mode

- Keep the project in the latest playable state.
- Destructive upgrades are acceptable. It is fine to stop the app, clean local data, rewrite schema, and restart.
- Do not spend effort on production-style migration chains, backward-compatible database upgrades, rollback plans, blue/green deployment, or preserving old local save data unless the user explicitly asks.
- Database schema should stay consolidated in the latest single schema file: `backend-ddd/mythic-realm-starter/src/main/resources/db/latest_schema.sql`.
- Flyway is not used. If the recorded local checksum differs from the latest schema, startup may clean and rebuild the database.

## Gameplay AI Rule

When adding new gameplay, check whether robot decisions should perceive or use it. Update the robot decision engine when the feature should affect robot leveling, dungeon runs, equipment, enhancement, market behavior, recharge simulation, or chat.

## Current Product Shape

- Backend: Spring Boot DDD Maven multi-module project under `backend-ddd/`.
- Frontend: Vite + React H5 client under `web/`.
- Primary target: desktop-first responsive web menu RPG.
- Robot population target: 200 active fantasy-named robots.

## Desktop Layout Rule

- Desktop workbench pages must never trap overflowing content. Any panel/list/log that can exceed the viewport must have an explicit scroll container with a computable height, usually via `height: 100%`/`flex: 1`/`minmax(0, 1fr)` plus `min-height: 0` and `overflow: auto`.
- When adding or changing a desktop page, verify scroll behavior for the main content area and both side rails at 1366x768 and 1920x1080. This is required for pages such as dungeon, market, blacksmith, builds, endgame, arena, chat, leaderboard, and robot activity.

## Local Tooling

- In a fresh Windows/Codex shell, load project tool wrappers before running validation commands:
  `. .\scripts\use-local-tools.ps1`
- Do not conclude `npm`, `node`, or `mvn` are unavailable until the local tool wrappers have been loaded.
- The wrappers intentionally prefer:
  - Node: Codex bundled Node under `%LOCALAPPDATA%\OpenAI\Codex\bin\...\node.exe`.
  - npm/npx: local wrappers in `tools/win/` running the existing npm CLI with the modern Node runtime.
  - Maven: `C:\Users\10050\apache-maven-3.9.10-bin\apache-maven-3.9.10` with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- The user's global PATH currently contains stale Node/Maven entries, so use the project wrappers for repeatable verification.

## Local Validation Account

- Use the local test account for browser/API verification:
  - Username: `123456`
  - Password: `123456`
  - Character: `AI_Tester`, warrior, created for this account.
- If the local database is cleaned/rebuilt and the account disappears, recreate/register this same account before browser verification.
