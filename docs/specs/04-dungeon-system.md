# 副本 & 任务系统

> 来源：`gameplay/dungeon/DungeonService.java` / `DungeonController.java`、`gameplay/combat/EncounterGate.java`、`StaminaService`、schema `dungeon_config` / `dungeon_room` / `dungeon_room_monster` / `dungeon_run*`、`gameplay/quest/*`、前端 `DungeonScreen` / `ResultScreen` / `QuestScreen`。

## 副本规模（实际）

**81 个副本，完全由数据库配置驱动**（`dungeon_config` + `dungeon_room` + `dungeon_room_monster`）：

- **78 个线性编号副本**：`dungeon_01_spider` … `dungeon_78_*`，主题分区（蜘蛛 / 沼泽 / 遗迹 / 根须 / 月 / … / 墓 / 魂）。
- **3 个血月特殊副本**：`special_bloodmoon_1/2/3`。

> 旧 spec 的"5 章 / 内置 5 个副本"已过时；实际是 78 段线性推进 + 3 个特殊副本。

## 进入门槛与节流（无每日次数）

- **门槛**：`EncounterGate.evaluate` 校验最低等级 + 最低战力，不达标直接拒绝（"未达到副本门槛"）。每副本有推荐 / 最低等级与战力。
- **节流闸是体力（stamina），不是每日次数**：每次运行消耗 1 体力（`StaminaService.consume`），扫荡按倍数消耗 N 体力。体力按现实时间恢复。
- ⚠️ **无副本解锁链、无"每日前 3 次"、无首通专属奖励**——旧 spec 的这些机制未实现。

## 副本运行（DungeonController）

| 操作 | 端点 | 说明 |
|------|------|------|
| 副本预览 | `GET /api/dungeons` | 返回门槛 / 体力 / 是否已通关等标记 |
| 运行 | `POST /api/dungeons/{id}/runs` | 回合制战斗模拟（见战斗文档），带 request-id 幂等（`uk_dungeon_run_request`） |
| 扫荡 | `POST /api/dungeons/{id}/sweeps` | 仅在该副本已通关后可用，1~10 倍快速结算 |

运行流程：遍历 `dungeon_room` → `dungeon_room_monster` → 逐怪 `CombatEngine.fight()`，累加经验 / 金币 / 掉落。持久化到 `dungeon_run`（运行）、`dungeon_run_loot`（掉落）、`dungeon_run_log`（文字）、`dungeon_run_frame`（动画帧）。

## 血月特殊副本（special_bloodmoon_*）

- **不可扫荡**；只结算传说 / 不朽（immortal）级掉落。
- **保底（pity）系统**（`applySpecialPity`）：
  - immortal 软保底：累计未出 ≥59 次后概率递增；硬保底 99 次必出。
  - 传说及以上软保底：≥14 次。

## 副本详情页（前端 DungeonScreen）

展示：名称 / 描述 / 难度、推荐等级 / 战力 / 玩家当前战力、风险提示（不禁止挑战）、怪物清单、聚合掉落预览（同物品只显示一次，掉率取最高）。难度为每副本配置值（简单 / 普通 / 困难 / 噩梦 / 深渊），**不是**旧 spec 的"普通/困难/地狱"三难度链。

> ⚠️ 旧 spec 的陷阱房 / 解谜房 / 隐藏岔路 / 多阶段走位 Boss **未实现**；房间即怪物遭遇，由回合制引擎结算。

## 任务系统（quest）

- 端点：`GET /api/quests`、`POST /api/quests/{questId}/claim`。
- 数据：`quest_config` / 条件 / 奖励 / 进度表；前端 `QuestScreen`（列表 + 详情，可跳转到对应系统）。
- 任务条件与奖励由配置驱动；前端 `conditionName` / `rewardName` 提供中文映射。

> 旧 spec 的"日常 5 任务刷新 / 成就钻石奖励"中，钻石货币不存在（见经济文档）；成就的具体清单待补。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| SwiftUI 原型 | React / Phaser 客户端 |
| 5 章 / 5 个副本 | 78 线性副本 + 3 血月特殊 |
| 普通 / 困难 / 地狱解锁链 | 每副本难度配置，无解锁链 |
| 每日前 3 次额外、首通奖励 | 无；改为体力节流 |
| 陷阱 / 解谜 / 走位 Boss | 房间即怪物遭遇，回合制 |
| — | 新增：扫荡、血月特殊副本 + 保底 |
