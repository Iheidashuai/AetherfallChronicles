# Project Context

Last reviewed: 2026-06-06

## Current Repository Shape

The repository currently contains an iOS MVP under `MythicRealm/` and design documents under `docs/specs/`.

Important local files:

- `MythicRealm/MythicRealm/App/MythicRealmApp.swift`: app entry, global `GameState`, screen routing.
- `MythicRealm/MythicRealm/Data/AccountManager.swift`: local account, character, and save persistence using JSON files and `UserDefaults`.
- `MythicRealm/MythicRealm/Data/ConfigLoader.swift`: JSON config loading for items, monsters, dungeons, and quests.
- `MythicRealm/MythicRealm/Game/Combat/TextBattleEngine.swift`: current battle loop and battle log generation.
- `MythicRealm/MythicRealm/Game/Equipment/*`: item model, inventory, loot, enhancement, combat power.
- `MythicRealm/MythicRealm/Game/Market/*`: player listing, robot listing, trade simulation, pricing.
- `MythicRealm/MythicRealm/Game/Leaderboard/*`: robot adventure simulation and ranking.
- `MythicRealm/MythicRealm/Game/Chat/*`: simulated world chat, quick phrases, system events.
- `MythicRealm/MythicRealm/Game/Quest/*`: quest progress, daily reset, reward claim.
- `MythicRealm/MythicRealm/Resources/*.json`: source config data.

## Observed Implementation Reality

The README and older specs describe Swift + SpriteKit + SQLite + ECS and real-time 2D action combat. The current codebase is closer to:

- SwiftUI-driven menus and state.
- Text/log-based auto battle.
- Local JSON account/save persistence.
- JSON config files.
- Rich systems for market, bot leaderboard, chat, quest, inventory, and loot.

This means the first H5 version should prioritize a networked menu RPG with server-authoritative simulation. A Phaser battle scene is useful later if the game returns to real-time action combat.

## Current H5 + Java Implementation Snapshot

The repository now includes a playable H5 + Java vertical slice:

- `backend/`: Spring Boot 3.x modular monolith on Java 21, Maven, Flyway, MySQL, Redis-backed sessions.
- `web/`: Vite + React + TypeScript, Zustand for local screen state, TanStack Query for server state.
- Local data services: project-local MySQL 8.4 on `127.0.0.1:3307`, Redis on `127.0.0.1:6379`; Docker is intentionally not used.
- Game config source of truth at runtime: MySQL tables. JSON files in backend resources are seed inputs only.
- Current seeded config scale: item templates, monsters, dungeons, quests, robot profiles, chat messages.

Implemented gameplay surface:

- Register/login, one active character per account, player home.
- Character stats, combat power, equipment slots, inventory slots.
- Item instances, loot rolls, equip, sell, enhance, bulk sell by selected quality, organize by quality/level/type.
- Dungeon selection and server-authoritative round battle simulation.
- Battle result playback with speed controls and loot detail UI.
- Quest progress and reward claims from dungeon, inventory, market, enhancement, chat, and leaderboard events.
- Market listing, cancel, robot/player purchase.
- World chat with durable robot/system/player messages.
- Leaderboard with 100+ robot profiles and the current player inserted into rank order.

## Migration Boundary Map

| Current Swift Area | Target Frontend | Target Backend |
| --- | --- | --- |
| `GameState` screen routing | React routes and Zustand client store | Session snapshot APIs |
| `AccountManager` | Login/register screens | Auth, account, session services |
| `PlayerData` | Character panel display | Player aggregate, level/stat domain service |
| `Item`, `InventorySystem`, `LootSystem` | Inventory/equipment UI | Item instance, inventory, loot, enhancement services |
| `TextBattleEngine` | Battle log playback, optional Phaser renderer | Dungeon run and battle result service |
| `QuestSystem` | Quest board and reward actions | Quest progress and reward service |
| `MarketSystem` | Market listing and purchase UI | Market service with transactions and locks |
| `RobotLeaderboardSystem` | Leaderboard UI | Simulation scheduler and Redis/MySQL ranking |
| `WorldChatSystem` | Chat UI, unread state | Chat service, bot/system message generation |
| `Resources/*.json` | Read-only config payload cache | Versioned config service and startup validation |

## Durable Product Decisions

- The H5 target is now a responsive desktop-first web game, not a phone-shaped vertical shell. It must still degrade gracefully on narrow viewports, but primary Web UI should use horizontal workbench layouts.
- Long lists should live in internal scroll containers. Avoid making the user scroll the whole page to reach core actions.
- Chat must open at the latest messages with the input visible and fixed at the bottom of the chat workspace. History is reached by scrolling upward.
- Inventory must expose bulk sell by specific quality: common, uncommon, rare, epic, legendary. Category filters can scope which item types are affected.
- Dungeon difficulty must respect combat power. Low-power players should fail or partially progress in high-power dungeons; rewards should reflect actual kills and success.
- World activity should feel populated. Maintain at least 100 robot profiles for leaderboard/social surfaces unless the design explicitly changes.
- Product Design direction: quiet, dense, game-operations web UI with clear panels, quality color coding, compact action controls, and horizontal comparison space.

## Long-Term Maintenance Rule

Do not treat chat history as durable project memory. When a decision matters beyond the current session, update `.specify/memory` or the active spec folder.
