# Project Context

Last reviewed: 2026-06-08

## Project Maintenance Mode

This is a single-player learning game project. Keep the project in the latest
playable state, and prefer simple direct changes over production-style
compatibility work.

- Destructive upgrades are acceptable. It is fine to stop the app, clean local
  data, rewrite schema/config/seed logic, and restart.
- Do not maintain long database migration chains or backward-compatible local
  upgrade paths unless the user explicitly asks.
- Do not optimize for production rollout concerns such as blue/green deploys,
  historical save-data preservation, or rollback plans.
- The active database schema is the latest mutable Flyway file:
  `backend-ddd/mythic-realm-starter/src/main/resources/db/migration/V1__latest_schema.sql`.
- Startup may clean and rebuild the local database when Flyway history does not
  match the latest schema.

## Current Repository Shape

The repository contains:
- **Backend**: DDD Maven multi-module architecture (15 modules) at `backend-ddd/`
- **Frontend**: H5 Vite + React app at `web/`
- **Legacy**: the old iOS/Swift MVP has been removed from the repository
- **Docs**: Architecture docs at `docs/architecture/`, specs at `docs/specs/`

## Observed Implementation Reality

The README now describes the current H5 + Java implementation. Older specs may still mention Swift + SpriteKit + SQLite + ECS and real-time 2D action combat as historical migration context.

The active product should prioritize a networked menu RPG with server-authoritative simulation. A Phaser battle scene is useful later if the game returns to real-time action combat.

## Current Architecture: DDD Maven Multi-Module (2026-06-07)

The repository now uses a complete DDD (Domain-Driven Design) architecture:

**Backend** (`backend-ddd/`):
- **架构**: DDD 四层架构 (Domain/Application/Infrastructure/Interface)
- **技术**: Spring Boot 3.3.0, Java 21, Maven multi-module
- **数据层**: JdbcTemplate (not JPA), single latest Flyway schema for local rebuilds
- **事件驱动**: Spring Events for cross-domain communication
- **模块数**: 15 个独立模块
  - `mythic-realm-common` - 公共模块 (领域事件、异常)
  - `mythic-realm-infrastructure` - 基础设施 (数据源、Redis、事件总线)
  - `mythic-realm-domain-player` - 角色领域
  - `mythic-realm-domain-equipment` - 装备领域
  - `mythic-realm-domain-inventory` - 背包领域
  - `mythic-realm-domain-enhancement` - 强化领域
  - `mythic-realm-domain-dungeon` - 副本领域
  - `mythic-realm-domain-market` - 市场领域
  - `mythic-realm-domain-quest` - 任务领域
  - `mythic-realm-domain-robot` - 机器人领域
  - `mythic-realm-domain-chat` - 聊天领域
  - `mythic-realm-domain-leaderboard` - 榜单领域
  - `mythic-realm-domain-announcement` - 通告领域
  - `mythic-realm-api` - API 网关 (全局异常处理)
  - `mythic-realm-starter` - 启动模块

**Frontend** (`web/`):
- **技术**: Vite + React + TypeScript
- **状态管理**: Zustand
- **UI**: Tailwind CSS + Lucide Icons

**数据服务**:
- MySQL 8.4 on `127.0.0.1:3307`
- Redis on `127.0.0.1:6379`
- No Docker (local services)

Implemented gameplay surface:

- Register/login, one active character per account, player home.
- Character stats, combat power, equipment slots, inventory slots.
- Item instances, loot rolls, equip, sell, enhance, bulk sell by selected quality, organize by quality/level/type.
- Dungeon selection and server-authoritative round battle simulation.
- Battle result playback with speed controls and loot detail UI.
- Quest progress and reward claims from dungeon, inventory, market, enhancement, chat, and leaderboard events.
- Market listing, cancel, robot/player purchase.
- World chat with durable robot/system/player messages.
- Leaderboard with 200 robot profiles and the current player inserted into rank order.

## Durable Product Decisions

- The H5 target is now a responsive desktop-first web game, not a phone-shaped vertical shell. It must still degrade gracefully on narrow viewports, but primary Web UI should use horizontal workbench layouts.
- Long lists should live in internal scroll containers. Avoid making the user scroll the whole page to reach core actions.
- Chat must open at the latest messages with the input visible and fixed at the bottom of the chat workspace. History is reached by scrolling upward.
- Inventory must expose bulk sell by specific quality: common, uncommon, rare, epic, legendary. Category filters can scope which item types are affected.
- Dungeon difficulty must respect combat power. Low-power players should fail or partially progress in high-power dungeons; rewards should reflect actual kills and success.
- World activity should feel populated. Maintain 200 robot profiles for leaderboard/social surfaces unless the design explicitly changes.
- Future gameplay features should consider the robot decision engine. If a feature should affect robot leveling, dungeon runs, equipment, enhancement, market behavior, recharge simulation, or chat, update robot context/actions/logging as part of the feature.
- Product Design direction: quiet, dense, game-operations web UI with clear panels, quality color coding, compact action controls, and horizontal comparison space.

## Long-Term Maintenance Rule

Do not treat chat history as durable project memory. When a decision matters beyond the current session, update `.specify/memory` or the active spec folder.
