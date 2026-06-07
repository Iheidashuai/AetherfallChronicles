# MythicRealm Constitution

Version: 1.0.0
Ratified: 2026-06-06
Scope: iOS-to-H5 migration, Java backend, game economy, long-term agent maintenance

## Core Principles

### 1. Server-Authoritative Progression

Experience, gold, item generation, enhancement, market settlement, quest rewards, dungeon completion, leaderboard power, and anti-cheat decisions MUST be computed or verified by the Java backend. The H5 client MAY render predicted UI and replay logs, but it MUST NOT be the source of truth for player progression or economy changes.

### 2. Config-Driven Game Domain

Items, monsters, dungeons, quests, drop tables, bot profiles, economy constants, and UI-facing labels MUST be versioned configuration. Runtime code should consume typed config objects instead of embedding balance numbers in controllers or views. Every config change that can affect rewards or markets MUST carry a version and rollback path.

### 3. Incremental Vertical Migration

The project MUST migrate by playable vertical slices, not by rewriting every iOS file into web code. Each slice must include frontend UI, backend API, persistence, tests, and a manual verification note. The old iOS Swift files remain design references until the H5 feature reaches parity.

### 4. Transactional Economy

Any operation that moves currency, items, market listings, rewards, or equipment state MUST be idempotent, auditable, and protected against concurrent double-spend. MySQL transactions are the persistence boundary; Redis locks or idempotency keys may support them but cannot replace durable database constraints.

### 5. Responsive Web H5 First

The primary client target is responsive Web H5 with desktop-first horizontal workbench layouts and narrow-screen fallback. Pages MUST be fast to load, efficient with mouse/keyboard and touch, and tolerant of weak networks. Core actions should stay visible in each workspace instead of being buried below long page scroll. Canvas or Phaser scenes should be lazy-loaded and isolated from ordinary menus such as home, inventory, quest, market, chat, and leaderboard.

### 6. Observable and Testable by Default

Core domain rules MUST have unit tests. API behavior MUST have integration tests. Frontend critical flows MUST have Playwright coverage once the H5 app exists. Production-like backend behavior MUST expose structured logs, metrics, trace IDs, and audit records for player-affecting operations.

### 7. Documentation Is Runtime Context

Long-running decisions MUST be recorded in `.specify/memory` or the active `specs/NNN-*` folder. Agents and maintainers MUST read the constitution and active spec before implementation. If code diverges from specs, update the spec or file a task to fix the code.

## Governance

- New large features start with `spec.md`, then `plan.md`, then `tasks.md`.
- Any stack change affecting Java version, Spring Boot major version, database version, frontend framework, or game authority boundary needs an ADR section in the active plan or a new spec.
- Every implementation task must map back to a requirement or migration phase.
- Tests may be intentionally deferred only when the task records the residual risk and a follow-up task.

## Current Decision Baseline

- H5 shell: TypeScript, React, Vite, desktop-first responsive workbench UI.
- Optional action-combat renderer: Phaser, loaded only for battle scenes when needed.
- Backend: Java + Spring Boot modular monolith.
- Database: MySQL for durable state, Redis for cache, locks, sessions, ranked views, and recent message buffers.
- Local development services: use locally installed MySQL and Redis; do not use Docker for this project unless this constitution is amended.
- Architecture style: server-authoritative, config-driven, transactional modular monolith before microservices.
