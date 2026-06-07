# MythicRealm

iOS 单机 RPG 游戏 — 西方魔幻题材，2D 像素风，即时动作战斗。

通过机器人模拟多人在线氛围，让单机也能感受到 MMO 的热闹。

## 技术栈

- Swift + SpriteKit（游戏引擎）
- SwiftUI（UI 系统）
- SQLite / GRDB.swift（数据存储）
- ECS 架构

## 文档

- [游戏总览](docs/specs/00-overview.md)
- [角色系统](docs/specs/01-character-system.md)
- [装备系统](docs/specs/02-equipment-system.md)
- [战斗系统](docs/specs/03-combat-system.md)
- [副本 & 任务](docs/specs/04-dungeon-system.md)
- [经济系统](docs/specs/05-economy-system.md)
- [机器人模拟](docs/specs/06-bot-simulation.md)
- [全服通告](docs/specs/07-announcement-system.md)
- [商店 & 拍卖场](docs/specs/08-shop-auction.md)
- [UI 设计规范](docs/specs/09-ui-design-guidelines.md)

## Spec Kit 迁移上下文

当前仓库已引入 GitHub Spec Kit 风格的长期维护文档：

- [项目宪章](.specify/memory/constitution.md)
- [项目上下文](.specify/memory/project-context.md)
- [H5 + Java 后端迁移 Spec](specs/001-h5-java-backend-migration/spec.md)
- [H5 + Java 后端迁移技术方案](specs/001-h5-java-backend-migration/plan.md)
- [H5 + Java 后端迁移任务拆解](specs/001-h5-java-backend-migration/tasks.md)

## H5 + Java 本地开发

迁移实现已开始落在新目录：

- `backend/`：Spring Boot 后端，使用本机 MySQL + Redis
- `web/`：Vite + React + TypeScript 移动 H5 客户端
- [本地启动说明](docs/dev/local-h5-java-setup.md)

## 进度

- [路线图](docs/progress/ROADMAP.md)
- [变更日志](docs/progress/CHANGELOG.md)

## 开发环境

- macOS 26.5 (Apple Silicon)
- Xcode 26.5
- iOS 26.5 Simulator
- 目标设备：iPhone 15+
