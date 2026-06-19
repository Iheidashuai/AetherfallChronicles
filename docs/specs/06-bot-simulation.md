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
  └─ 其中 14~20 个执行动作（MIN_ACTIONS_PER_TICK=14 + nextInt(7)）
       └─ RobotBrainService.thinkAndAct(robot)
            ├─ 构建 RobotDecisionContext（感知玩家 / 自身 / 市场 / 副本状态）
            ├─ 每个 canRun 的 RobotDecisionAction 调 score()（+ 随机抖动 ×6）
            ├─ 过滤 score>0，按最终分排序、再按 priority
            └─ 执行最高分动作；无候选则 RestRobotAction 兜底
```

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

机器人带 `personality` 字段（影响聊天风格）与 `title`。性格类型沿用旧设计的语义（friendly / showoff / casual / hardcore / newbie / merchant 等），用于聊天模板选择（见 [通告系统](07-announcement-system.md) 的机器人反应）。

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
