# MythicRealm

H5 + Java 后端的西方魔幻题材菜单 RPG。

项目通过机器人、商会、世界频道、榜单和全服通告模拟 MMO 氛围，当前主线是浏览器可玩的服务端权威版本。

## 技术栈

- 前端：Vite + React + TypeScript
- 后端：Spring Boot 3.3 + Java 21 + Maven 多模块
- 数据：MySQL + Redis
- 架构：DDD 分层、多领域模块、Flyway 数据迁移

## 本地开发

- `backend-ddd/`：Spring Boot DDD 多模块后端
- `web/`：Vite + React H5 客户端
- [本地启动说明](docs/dev/local-h5-java-setup.md)

## 文档入口

- [游戏总览](docs/specs/00-overview.md)
- [角色系统](docs/specs/01-character-system.md)
- [装备 / 强化 / 加工系统](docs/specs/02-equipment-system.md)
- [战斗系统](docs/specs/03-combat-system.md)
- [副本 & 任务](docs/specs/04-dungeon-system.md)
- [经济系统](docs/specs/05-economy-system.md)
- [机器人模拟](docs/specs/06-bot-simulation.md)
- [全服通告](docs/specs/07-announcement-system.md)
- [商店 & 商会系统](docs/specs/08-shop-auction.md)
- [UI 设计规范（Web）](docs/specs/09-ui-design-guidelines.md)
- [终局系统：竞技场 / 裂隙 / 构筑 / 排行榜](docs/specs/10-endgame-system.md)

> specs 已按当前 Web（React/Phaser + Java）产品重写；Swift 本地版工程已从仓库移除。
> 前端工程改造参考：[App.tsx 拆分方案](docs/architecture/frontend-split-plan.md)。

## Spec Kit 上下文

当前仓库已引入 GitHub Spec Kit 风格的长期维护文档：

- [项目宪章](.specify/memory/constitution.md)
- [项目上下文](.specify/memory/project-context.md)
- [AI 代理上下文](AGENTS.md)
- [H5 + Java 后端迁移 Spec](specs/001-h5-java-backend-migration/spec.md)
- [H5 + Java 后端迁移技术方案](specs/001-h5-java-backend-migration/plan.md)
- [H5 + Java 后端迁移任务拆解](specs/001-h5-java-backend-migration/tasks.md)

## 进度

- [路线图](docs/progress/ROADMAP.md)
- [变更日志](docs/progress/CHANGELOG.md)
