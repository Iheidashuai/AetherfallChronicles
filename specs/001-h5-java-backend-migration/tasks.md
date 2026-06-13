# Tasks: H5 + Java Backend Migration

Feature: 001-h5-java-backend-migration
Status: Draft
Created: 2026-06-06

## Phase 0: Documentation and Governance

- [x] T001 Create Spec Kit-compatible project constitution.
- [x] T002 Record current iOS project context and migration boundary map.
- [x] T003 Create migration feature spec.
- [x] T004 Create migration technical plan.
- [x] T005 Create implementation task breakdown.
- [x] T008 Create durable desktop Web UI design guidance.
- [ ] T006 Decide exact backend version baseline and record ADR.
- [ ] T007 Decide frontend router/styling choices and record ADR.

## Phase 1: Repository Scaffold

- [x] T101 Create `backend/` Spring Boot modular monolith.
- [x] T102 Create `web/` Vite + React + TypeScript H5 app.
- [x] T103 Add local MySQL and Redis setup script; do not use Docker.
- [x] T104 Add Flyway baseline migration.
- [ ] T105 Add backend integration-test profile using local MySQL and Redis; do not use Docker/Testcontainers by default.
- [x] T106 Add frontend typecheck and build scripts.
- [ ] T107 Add CI workflow for backend tests and frontend checks.

## Phase 2: Config Port

- [x] T201 Copy current item, monster, dungeon, and quest JSON into backend resources.
- [x] T202 Define typed Java config models.
- [x] T203 Add config checksum and version loader.
- [x] T204 Add startup validation for references between dungeon, monster, loot, and quest configs.
- [x] T205 Expose `GET /api/config/bootstrap`.
- [ ] T206 Add golden tests for known config examples.

## Phase 3: Auth and Player Home

- [x] T301 Implement account schema and credential hashing.
- [ ] T302 Implement register, login, refresh, logout/session APIs. Current slice has register/login/session token; refresh/logout remain.
- [x] T303 Implement player creation and one-active-character rule.
- [x] T304 Implement home snapshot API.
- [x] T305 Build H5 login/register/create-character screens.
- [x] T306 Build H5 home screen with character, resources, equipment summary, and navigation.

## Phase 4: Inventory and Equipment

- [x] T401 Implement item instance schema and repository.
- [x] T402 Implement inventory and equipment slot schemas.
- [x] T403 Implement combat power calculation.
- [x] T404 Implement equip, sell, bulk sell by quality, organize, and enhance APIs. Unequip remains later.
- [ ] T405 Add transaction and concurrency tests for item operations.
- [x] T406 Build H5 inventory page with desktop three-column layout.
- [x] T407 Build H5 character/equipment panel.

## Phase 5: Dungeon, Battle, and Rewards

- [x] T501 Port battle and reward formulas into backend round simulation. Current slice supports server-side combat power pressure, failure, partial rewards, and result logs.
- [x] T502 Implement dungeon list API.
- [x] T503 Implement dungeon run API with idempotency key.
- [ ] T504 Implement sweep API for completed dungeons.
- [ ] T505 Persist dungeon run audit data.
- [ ] T506 Add tests for reward duplication prevention.
- [x] T507 Build H5 dungeon list/result screens. Detail remains later.
- [x] T508 Build battle log playback component.

## Phase 6: Quest

- [x] T601 Implement quest progress schema.
- [x] T602 Implement quest event dispatcher.
- [ ] T603 Implement daily quest reset logic with clock rollback protection.
- [x] T604 Implement reward claim API. Claim-all remains later.
- [ ] T605 Add tests for quest progress and reward transactions.
- [x] T606 Build H5 quest page.

## Phase 7: Market

- [x] T701 Implement market listing schema.
- [x] T702 Implement list, cancel, buy, and browse APIs. Expiry/retrieve remains later.
- [x] T703 Implement initial robot market pricing rules.
- [ ] T704 Add Redisson/MySQL protection for purchase concurrency.
- [ ] T705 Add tests for double-spend and duplicate-purchase attempts.
- [x] T706 Build H5 market page.

## Phase 8: Chat, Leaderboard, and Simulation

- [x] T801 Implement chat durable storage. Redis recent list remains later.
- [x] T802 Implement player chat send. Rate limit and moderation placeholder remain later.
- [x] T803 Port robot chat templates and system event generation.
- [ ] T804 Implement player power leaderboard with Redis sorted set. Current slice ranks from MySQL plus current player.
- [x] T805 Implement robot profile persistence with 100+ robots.
- [ ] T806 Implement bounded simulation scheduler.
- [x] T807 Build H5 chat page with latest-message default and fixed input.
- [x] T808 Build H5 leaderboard page.
- [x] T809 Build desktop-first horizontal workbench layouts for home, inventory, dungeon, battle result, chat, and leaderboard.

## Phase 9: Quality, Ops, and Release

- [ ] T901 Add OpenAPI documentation.
- [ ] T902 Add structured logging and trace IDs.
- [ ] T903 Add economy audit inspection query.
- [ ] T904 Add backup/restore notes for MySQL and Redis.
- [ ] T905 Add Playwright smoke test for register -> dungeon -> equip flow.
- [ ] T906 Run mobile viewport visual QA.
- [ ] T907 Create first H5 release checklist.

## Parallelization Notes

- T201-T204 can run while frontend scaffold tasks T102/T106 are in progress.
- H5 static pages can be built against mocked APIs before backend endpoints are ready.
- Backend domain tests should be written before API controllers for economy-sensitive modules.
- Market and dungeon transaction tests should not be postponed; they protect the highest-risk systems.
