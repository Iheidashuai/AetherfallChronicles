# Agent Context

This repository is a single-player learning game project.

## Maintenance Mode

- Keep the project in the latest playable state.
- Destructive upgrades are acceptable. It is fine to stop the app, clean local data, rewrite schema, and restart.
- Do not spend effort on production-style migration chains, backward-compatible database upgrades, rollback plans, blue/green deployment, or preserving old local save data unless the user explicitly asks.
- Database schema should stay consolidated in the latest Flyway schema file: `backend-ddd/mythic-realm-starter/src/main/resources/db/migration/V1__latest_schema.sql`.
- If schema history conflicts with the latest schema, local startup may clean and rebuild the database.

## Gameplay AI Rule

When adding new gameplay, check whether robot decisions should perceive or use it. Update the robot decision engine when the feature should affect robot leveling, dungeon runs, equipment, enhancement, market behavior, recharge simulation, or chat.

## Current Product Shape

- Backend: Spring Boot DDD Maven multi-module project under `backend-ddd/`.
- Frontend: Vite + React H5 client under `web/`.
- Primary target: desktop-first responsive web menu RPG.
- Robot population target: 200 active fantasy-named robots.
