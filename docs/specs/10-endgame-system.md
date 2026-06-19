# 终局系统：竞技场 / 深渊裂隙 / 构筑 / 排行榜

> **新增文档**：旧 specs 完全没有覆盖这几个已实现的终局系统。
> 来源：`gameplay/arena/*` + `domain-arena`、`gameplay/endgame/EndgameRiftController.java` + `endgame/combat/EndgameCombatEngine.java`、`gameplay/build/*`、`gameplay/leaderboard/LeaderboardService.java`，schema `arena_*` / `rift_*` / `build_*`、前端 `ArenaScreen` / `EndgameRiftScreen` / `BuildsScreen` / `LeaderboardScreen`。

当前满级（90）后的留存由四个支柱承担：**竞技场 PvP、深渊裂隙、构筑模拟、战力榜**。

---

## 1. 竞技场（Arena，PvP）

异步 PvP，**用与副本相同的回合制 `CombatEngine.fight()` 在服务端裁决**（`ArenaAdapters.ExistingCombatEngineArenaAdapter`，30 回合上限）。

| 操作 | 端点 |
|------|------|
| 查看档案 / 状态 | `GET /api/arena` |
| 发起挑战 | `POST /api/arena/challenge/{targetId}` |
| 查看对局 | `GET /api/arena/matches/{matchId}` |
| 排名 | `GET /api/arena/rankings` |
| 竞技场商店购买 | `POST /api/arena/shop/{offerId}/buy` |

- `arena_profile`：Elo 式 `rating`（默认 1000）、`arena_coins`、胜 / 负 / 连胜、每日挑战次数上限。
- 完整对局 + 参与者 + 事件日志：`arena_match*`。
- 竞技场币商店：`arena_shop_offer`。
- 前端：`ArenaScreen` + `ArenaProfileCard` / `OpponentCard` / `ShopCard` / `MatchPanel` / `FighterCard` / `RankRow`。

## 2. 深渊裂隙（Endgame Rift）

分层（tier）递进的终局刷取，独立战斗引擎 `EndgameCombatEngine`。

| 操作 | 端点 |
|------|------|
| 裂隙总览 | `GET /api/endgame/rifts` |
| 运行某层 | `POST /api/endgame/rifts/{tier}/runs` |
| 领周常奖励 | `POST /api/endgame/rifts/weekly-reward` |

- 解锁门槛（`riftUnlocked`）；`rift_modifier_config`（难度分 + 奖励加成词条）。
- 玩家进度 `player_rift_progress`（最佳层 / 分数 / 评价）；运行与事件日志 `rift_run` / `rift_run_event`。
- **三种裂隙材料 / 货币**：精华（essence）、碎片（shards）、源质（orbs）。
- **周常奖励**（`player_rift_weekly_reward`，按周 key 发最佳层宝箱）是目前最接近"赛季"的机制。
- 前端：`EndgameRiftScreen` + `RiftResultPanel`。

## 3. 构筑（Builds）

命名装配方案，支持模拟试跑。

| 操作 | 端点 |
|------|------|
| 列表 | `GET /api/builds` |
| 新建 | `POST /api/builds` |
| 复制预设 | `POST /api/builds/presets/{id}/copy` |
| 更新 | `PUT /api/builds/{buildId}` |
| 激活 | `POST /api/builds/{buildId}/activate` |
| **模拟试跑** | `POST /api/builds/{buildId}/simulate` |

- `build_preset*` / `player_build*`：装备槽 + 技能槽 + 天赋 + 策略 / 精炼侧重。
- 模拟（`simulate`）= 对某裂隙层做一次干跑，结果存 `build_simulation_run`，前端 `BuildSimulationPanel` 展示评分。
- 前端：`BuildsScreen` + `BuildScorePanel` / `BuildSimulationPanel`，本地用 `BuildDraft` 草稿模型编辑。

## 4. 排行榜（Leaderboard）

- 端点：`GET /api/leaderboard/power`（**目前仅战力榜对外暴露**）。
- `LeaderboardService` 混合真人 + 机器人，按装备战力计算排名。
- 充值富豪榜是独立的（`/api/recharge/dashboard`，见经济文档）。
- 前端：`LeaderboardScreen` + `LeaderboardCard`。

> ⚠️ 旧 spec 提到的等级 / 通关速度 / 财富等多榜单未作为独立端点暴露；竞技场有自己的 `rankings`。

---

## 终局设计现状与缺口（产品提醒）

**已具备**：PvP（竞技场）、无限分层刷取（裂隙）、构筑试错（builds + simulate）、周常奖励、战力 / 竞技场 / 充值三类排名。

**缺口**（后续可补，供产品决策）：
- **真正的赛季循环**（重置 / 赛季奖励 / 天梯快照）仅有 rift 周常雏形，未成体系。
- **竞争张力**：机器人由实时打分驱动且整体可被玩家超越，榜单缺少"会输"的真实压力。
- **裂隙的无限缩放上限 / paragon 类成长**未定义——满级后的长期数值目标仍偏薄。

> 针对以上缺口的完整设计方案见：[终局 & 赛季循环 设计提案](../design/endgame-and-seasons-proposal.md)。
