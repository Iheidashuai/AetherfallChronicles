# 机器人模拟系统

> 来源：`docs/robot-decision-engine.md`、`gameplay/robot/RobotActivityService.java`、`RobotBrainService.java`、`gameplay/robot/*RobotAction.java`、schema 中 `controller_type='robot'` 的 `player` 行、`robot_activity_log`。

## 设计目标（保留）

用约 200 个有性格 / 行为模式的虚拟玩家，让单机游戏产生「仿 MMO」社交氛围——玩家任何时候打开游戏都觉得世界是"活的"。**这是本项目最核心的差异化卖点。**

## 实际架构：Utility-AI + 实时调度器

机器人就是 `controller_type='robot'` 的 `player` 行，由 Java 调度器驱动，**不是**旧 spec 的 Swift `BotProfile` / 每游戏日 tick / 离线追算模型。

```
RobotActivityService.simulateTick   @Scheduled(initialDelay=6s, fixedDelay=15s)
  └─ 仅在有真人玩家时运行
  └─ 每 tick 采样 24 个机器人（ROBOTS_PER_TICK）
  └─ 行动人数 = (14 + nextInt(7)) × activityFactor(当前时段)  ← 晚高峰多/深夜少
       └─ RobotBrainService.thinkAndAct(robot)
            ├─ 构建 RobotDecisionContext（感知玩家 / 自身 / 市场 / 副本 / 近期行为 / 公会）
            ├─ 每个 canRun 的 RobotDecisionAction 调 score()
            ├─ 过滤 score>0
            ├─ softmax 加权随机抽样（温度由 archetype + 时段决定）← 不再是 argmax
            └─ 执行抽中的动作；无候选则 RestRobotAction 兜底
```

> **决策"像人"的关键机制（2026-06 重构）**：
> - **softmax 选择**取代 argmax：最高分动作仍最可能，但低分动作有非零概率，机器人"满意即可"而非永远最优，低价值动作不再被饿死。
> - **温度**（`temperature`）是人味旋钮，基于 `RobotArchetype`，深夜更高（更随性）。
> - **结构化性格** `RobotArchetype`（`personality_archetype` 列）给每个机器人 pve/market/social/growth 倾向权重，乘到各动作性格加分上，取代旧的"关键词子串 + 固定分"。
> - **多步记忆** `RobotMemoryService`：近期动作环形缓冲 + `repeatPenalty` 递减惩罚，取代只看上一步的 `isCurrentKind`，消除 A→B→A→B 机械循环；聊天据此去重。
> - **响应曲线** `RobotResponseCurves`：体力用 logistic、战力差用 quadratic，取代线性 clamp。
> - **作息**：`activityFactor` 按时段调节行动人数，营造在场人数起伏。
> - `priority()` 仅排序、不参与打分——调它不改变行为。

- **机器人数量**：种子数据 **200** 个（旧 spec 说 200-300）。
- **节奏是现实时间 15 秒一 tick**，无"游戏日"概念，无离线追算。
- 活动写入 `robot_activity_log`，前端 `RobotActivityScreen` 展示，端点 `GET /api/robots/activity`、`GET /api/robots/{robotId}/activity`。

## 已实现的动作族（RobotDecisionAction）

比旧 doc 列的更全——机器人几乎能做玩家能做的一切：

| 类别 | 动作 |
|------|------|
| 战斗 | `DungeonRun`、`RiftRun`、`ArenaChallenge` |
| 装备养成 | `EnhanceEquipment`、`AscendEquipment`、`ReforgeEquipment`、`SocketGem`、`RiftRefine` |
| 经济 | `MarketBuy`、`MarketSupply`、`GoldExchange` |
| 成长 | `TrainSkill`、`ConfigureBuild`、`ClaimQuestReward`、`UseInventoryItem` |
| 社交 | `SocialChat` |
| 兜底 | `Rest` |

> 因此机器人会真的去打副本、强化、上架 / 采购、打竞技场、跑裂隙、调构筑——聊天背后有真实模拟行为支撑，而非纯文本刷屏。

## 机器人活动类型（前端展示）

`robotActivityKindName` 映射：打副本 / 强化装备 / 高阶掉落 / 逛商会 / 上架商品 / 采购商品 / 售出商品 / 围观 / 休息 / 调整构筑 / 深渊淬炼 / 宝石镶嵌 / 词条重铸 / 装备升阶。

## 性格

机器人有两层性格：

- `personality`（中文描述句）+ `title`：用于聊天/展示风格。
- `personality_archetype`（结构化枚举 `RobotArchetype`：HARDCORE / SHOWOFF / MERCHANT / SOCIAL / CASUAL / NEWBIE）：每种带 pve/market/social/growth 倾向权重与选择温度，**直接参与打分**，决定该机器人偏好哪类动作、行为多随性。种子按真实占比分布（多数 casual/newbie，少量 merchant/social/showoff，高等级偏 hardcore），并与等级/财富梯度一起初始化，让 200 个机器人开局即有差异、榜单可信。

> 旧文档提到的 friendly/showoff/... 语义现已落地为上面的 `RobotArchetype`；`personality` 自由文本仍保留用于聊天。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| Swift `BotProfile` 结构 | `player` 行 + Java 打分器 |
| 每游戏日 tick / 离线追算 30 日 | 现实时间 15 秒 tick，无离线追算 |
| 等级 / 装备 / 排行的每日 delta 公式 | 实时打分驱动，榜单按实时战力计算 |
| 世界 Boss 事件（每 3 游戏日） | **未实现** |
| 200-300 个 | 种子 **200** 个 |
| 拍卖竞价行为 | 固定价挂单 + 采购（见经济文档） |

> 旧 spec 中"追赶机制 / 橡皮筋 / 玩家努力即可领先"是好的产品意图，但当前实现是实时打分而非那套日 delta 公式——后续若要强化"竞争张力"，需在打分权重里显式建模。
