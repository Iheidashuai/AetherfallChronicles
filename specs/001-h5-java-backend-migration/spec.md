# Feature Spec: H5 + Java Backend Migration

Feature: 001-h5-java-backend-migration
Status: Draft
Created: 2026-06-06
Owner: MythicRealm maintainers

## Summary

Migrate MythicRealm from a local iOS MVP into a responsive desktop-first H5 web game backed by a Java service, MySQL, and Redis. The first production target is a server-authoritative menu RPG with playable core loops: account, character, inventory, equipment, dungeon battle result playback, quests, market, leaderboard, and simulated world chat.

The migration should preserve the current fantasy RPG identity and config-driven gameplay while moving player progression and economy state out of local JSON files and into durable backend services.

## Goals

- Let players access the game from modern web browsers without installing an iOS app.
- Move account, character, inventory, quest, market, chat, leaderboard, and dungeon progression to backend-managed state.
- Preserve current gameplay systems as domain concepts, not necessarily as one-to-one Swift-to-Java ports.
- Establish a Spec Kit-compatible documentation structure for long-term planning and agent handoffs.
- Keep the first migration small enough to ship as a vertical slice.

## Non-Goals

- Full real-time multiplayer combat.
- Full microservice decomposition.
- Direct iOS save-file import for public release.
- Full admin console in the first playable H5 milestone.
- Full SpriteKit-equivalent action battle in the first milestone.

## User Stories

### US1: Returning Player Opens H5 Game

As a returning player, I can open the H5 game in a mobile browser, log in, and see my character home screen with level, combat power, gold, equipment, and quick navigation.

Acceptance criteria:

- Login restores server-side player state.
- Home screen shows character, resources, equipment summary, quest summary, market/chat indicators, and leaderboard entry point.
- Refreshing the browser does not lose progress.

### US2: Player Runs a Dungeon

As a player, I can select a dungeon, start a run, watch battle logs or a simple battle presentation, and receive server-calculated rewards.

Acceptance criteria:

- Backend validates player eligibility and current equipment snapshot.
- Backend returns deterministic run result, logs, rewards, and quest events.
- Rewards are committed once and cannot be duplicated by refresh or retry.

### US3: Player Manages Equipment

As a player, I can view inventory, equip items, sell or enhance equipment, and see combat power update.

Acceptance criteria:

- Equipment operations are persisted in MySQL.
- Concurrent requests cannot duplicate, lose, or double-equip an item.
- Combat power calculation is shared or mirrored with backend authority.

### US4: Player Uses Market

As a player, I can list equipment, buy robot/player listings, cancel listings, and see trade records.

Acceptance criteria:

- Listing, purchase, cancel, expiry, and settlement are transactional.
- Gold and item ownership never diverge.
- Market actions are rate-limited and idempotent.

### US5: Player Sees Simulated MMO Activity

As a player, I can view world chat, robot leaderboard, market activity, and system notices that make the game feel alive.

Acceptance criteria:

- Recent chat loads quickly from Redis.
- Durable chat and system messages are stored in MySQL.
- Robot simulation can catch up after downtime within bounded limits.

## Functional Requirements

- FR-001: The system MUST provide register, login, logout, token refresh, and current-session APIs.
- FR-002: The system MUST support one or more characters per account, with the first milestone allowing one active character.
- FR-003: The system MUST load item, monster, dungeon, and quest configs from versioned backend config bundles.
- FR-004: The system MUST persist item instances separately from item templates.
- FR-005: The system MUST persist inventory slots and equipped slots with ownership constraints.
- FR-006: The system MUST calculate dungeon rewards on the backend.
- FR-007: The system MUST record dungeon runs with request id, config version, reward summary, and result.
- FR-008: The system MUST support quest progress updates from dungeon, inventory, market, enhancement, and leaderboard events.
- FR-009: The system MUST support market listing, purchase, cancel, expiry, and trade records.
- FR-010: The system MUST store player-related economy operations in an audit table or event log.
- FR-011: The system MUST expose leaderboard data sorted by combat power and rank.
- FR-012: The system MUST support bot simulation ticks for market, leaderboard, and chat activity.
- FR-013: The H5 client MUST use desktop-first horizontal workbench layouts on wide screens and remain usable on narrow screens.
- FR-014: The H5 client MUST handle API retries without duplicating rewards or transactions.
- FR-015: The project MUST keep migration docs under `.specify` and `specs/001-h5-java-backend-migration/`.
- FR-016: Dungeon combat MUST be backend-authoritative and must allow failure or partial progress when combat power is far below recommended power.
- FR-017: Inventory bulk sell MUST support explicit quality selection: common, uncommon, rare, epic, and legendary.
- FR-018: World chat MUST open at the newest messages with the input visible; historical messages are reached by scrolling upward.
- FR-019: Leaderboard and social surfaces MUST include at least 100 robot profiles in the current milestone.

## Key Entities

- Account
- Session
- Player
- PlayerStats
- CurrencyWallet
- ItemTemplate
- ItemInstance
- InventorySlot
- EquipmentSlot
- DungeonConfig
- DungeonRun
- QuestConfig
- QuestProgress
- MarketListing
- TradeRecord
- ChatMessage
- RobotProfile
- RobotSnapshot
- ConfigBundle
- EconomyAuditEvent

## Success Metrics

- A new player can register, create a character, complete an appropriate dungeon, fail an overpowered dungeon, receive loot, equip an item, and see updated combat power in H5.
- Core economy operations pass concurrent request tests.
- Backend domain tests cover combat, loot, quest reward, equipment, and market settlement.
- H5 first contentful render stays under 2.5 seconds in the local/dev test environment target.
- No feature implementation starts without referencing the active spec and plan.

## Assumptions

- The initial public target is responsive Web H5 with desktop-first layout and narrow-screen fallback.
- The first H5 release can use battle log playback instead of real-time action controls.
- The existing Swift code is a domain reference, not a source-compatible migration target.
- MySQL and Redis are acceptable infrastructure dependencies for the first backend.
