# MythicRealm / Aetherfall Chronicles — 游戏总览

> 本文档描述**当前实际产品**：一个浏览器端、服务端权威的西方魔幻菜单 RPG。
> 历史上曾有 iOS/Swift/SpriteKit 原型，**已废弃并从仓库移除**；本 specs 目录已按 Web 现实重写。

## 项目定位

**单机 Web RPG**，西方魔幻题材。核心卖点：用约 200 个有性格的 AI 机器人（聊天 / 商会交易 / 排行榜 / 全服通告）伪造出「类 MMO」的热闹氛围，让单机体验也显得"服务器很活"。

战斗是**服务端回合制模拟**（非即时动作），客户端用 Phaser 把服务端算好的战斗帧"回放"成动画。一切数值与判定都由后端裁决（server-authoritative）。

## 核心特色

- **刷装驱动**：装备掉落 → 穿戴 → 强化 / 镶嵌 / 重铸 / 升阶 → 变强 → 打更深副本
- **类 MMO 氛围**：200 个有状态机器人跑 Utility-AI，世界频道（SSE 实时）、商会买卖、战力榜、全服滚动通告
- **多终局支柱**：竞技场 PvP、深渊裂隙（rift）、构筑（build）模拟、周常奖励
- **付费幻想层**：RMB 充值 + 财富等级 + 机器人富豪榜，模拟充值竞争氛围

## 技术栈（实际）

| 层 | 选型 | 说明 |
|----|------|------|
| 前端 | Vite + React 19 + TypeScript（strict） | H5 客户端，桌面优先响应式 |
| 战斗演出 | Phaser 4 | 仅做战斗帧动画回放，不参与逻辑 |
| 前端状态 | TanStack Query（服务端态）+ Zustand（会话/路由 UI 态） | 轮询 + 聊天 SSE |
| 后端 | Spring Boot 3.3 + Java 21 + Maven 多模块 | 见架构说明 |
| 持久化 | MySQL + Spring `JdbcTemplate`（手写 SQL，无 ORM） | Redis 依赖已声明但当前未使用 |
| 数据库管理 | 无 Flyway。单一 `latest_schema.sql`，校验和不符时本地重建 | 单人学习项目策略，见 `AGENTS.md` |
| 认证 | 自实现：不透明 Bearer Token + `session_token` 表（14 天） | 无 Spring Security |

> 详见 [DDD 架构分析](../architecture/ddd-architecture-analysis.md)。模块现状：保留 4 个有真实代码的领域模块（`domain-player` / `domain-enhancement` / `domain-equipment` / `domain-arena`，采用 model/repository/application/infrastructure 分层）；原先 8 个空壳 `domain-*` 模块（announcement/chat/dungeon/inventory/leaderboard/market/quest/robot）已删除。其余系统的逻辑按**功能包**组织在 `mythic-realm-api/gameplay/<feature>/`（事务脚本式 Controller + Service）。即一个"按包分的模块化单体 + 4 个真 DDD 领域模块"。

## 系统架构（实际）

```
┌──────────────────────── web/ (React + Phaser) ────────────────────────┐
│  20 个屏幕（screen 路由） · TanStack Query 轮询 · 聊天 SSE             │
└───────────────────────────────┬───────────────────────────────────────┘
                                 │ REST（约 62 个端点，Bearer Token）
┌───────────────────────────────▼───────────────────────────────────────┐
│                    mythic-realm-api / gameplay/*                        │
│  auth player dungeon combat inventory equipment enhancement            │
│  shop market recharge arena endgame(rift) build skill quest            │
│  chat announcement leaderboard robot                                    │
├────────────────────────────────────────────────────────────────────────┤
│  RobotActivityService（@Scheduled 15s）→ RobotBrainService（Utility-AI）│
│  RechargeService（@Scheduled 180s 机器人现金事件）                      │
├────────────────────────────────────────────────────────────────────────┤
│  JdbcTemplate + 60 张表（MySQL）                                        │
└────────────────────────────────────────────────────────────────────────┘
```

## 时间与节奏（实际）

⚠️ 旧版"10 分钟 = 1 游戏日 + 离线追算"的虚拟时间系统**未实现**。当前一切以**现实时间**驱动：

- 机器人世界由 `@Scheduled` 调度器推进：每 **15 秒**一个 tick，仅在有真人玩家时运行，每 tick 采样 24 个机器人、其中 14-20 个执行动作。
- 充值/财富氛围由独立调度（每 180 秒）生成机器人现金事件。
- 体力（stamina）按现实时间恢复（默认上限 1000，每 3 分钟自动回满），是副本次数的主要节流闸。

## 玩家核心循环

```
选副本（受等级/战力门槛 + 体力限制）
   → 服务端回合制战斗（Phaser 回放）
   → 掉落 / 穿戴 / 卖钱
   → 金币投入：强化 / 镶嵌 / 重铸 / 升阶 / 精炼
   → 商会买卖（固定价挂单，8% 税，机器人提供流动性）
   → 看世界频道 / 战力榜 / 全服通告（社交氛围）
   → 终局：竞技场 PvP / 深渊裂隙 / 构筑模拟
循环
```

## 当前内容规模（实际）

- 职业：3（战士 / 游侠 / 法师）；等级上限 **90**
- 副本：**81 个**（78 个线性编号副本 + 3 个血月特殊副本）
- 物品模板：约 2900 条；装备槽 9 个；品质 6 档（含最高的「不朽 immortal」）
- 机器人：种子数据 **200** 个
- 数据库表：60 张

## 文档入口

- [角色系统](01-character-system.md)
- [装备 / 强化 / 加工系统](02-equipment-system.md)
- [战斗系统](03-combat-system.md)
- [副本系统](04-dungeon-system.md)
- [经济系统](05-economy-system.md)
- [机器人模拟](06-bot-simulation.md)
- [全服通告系统](07-announcement-system.md)
- [商店与商会系统](08-shop-auction.md)
- [UI 设计规范（Web）](09-ui-design-guidelines.md)
- [终局系统：竞技场 / 深渊裂隙 / 构筑 / 排行榜](10-endgame-system.md)

> 历史遗留：`docs/progress/ROADMAP.md`、`CHANGELOG.md` 仍含 iOS 阶段描述，待后续对齐。
