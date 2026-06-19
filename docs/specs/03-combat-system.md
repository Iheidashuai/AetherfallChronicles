# 战斗系统

> 来源：`gameplay/combat/CombatEngine.java`、`DamageCalculator.java`、`CombatStats.java`、`gameplay/dungeon/DungeonService.java`、前端 `web/src/BattleStagePhaser.tsx`。

## 核心模型：服务端回合制模拟

战斗**不是即时动作**，而是**服务端自动回合制**。客户端不参与任何战斗判定，只把服务端算好的战斗帧回放成动画。

- `CombatEngine.fight()`：每场战斗最多 `DEFAULT_ROUND_LIMIT = 30` 回合；每回合玩家先手、敌人后手。无移动、无摇杆、无闪避按钮。
- 一次副本运行（`DungeonService.runDungeonForPlayer`）：遍历房间 → 房间内怪物 → 对每个敌人调用 `combatEngine.fight()`，累加经验 / 金币 / 掉落，房间间按 `roomRecoveryRate` 回血。
- 产出 `BattleFrame` 帧序列 + 文字日志，分别持久化到 `dungeon_run_frame` / `dungeon_run_log`。

## 伤害公式（DamageCalculator.attack）

```
raw = attackPower × skillMultiplier × mechanicMultiplier
      × (1 − reduction) × variance(0.94~1.06) × critMult
```

- **命中率** = clamp(accuracy − evasion + levelGap×0.01, 0.70 ~ 0.98)
- **暴击率** = clamp(attackerCrit − defenderCritResist, 0.02 ~ 0.42)
- **减伤** = clamp(defense / (defense + 180 + attackerLevel×22), 0.03 ~ 0.72)

## 伤害类型（仅两类）

`CombatStats.defenseFor`：来袭 `damageType == "magic"` 时用 `resistance`（魔抗），否则用 `armor`（护甲）。

⚠️ **没有火/冰/雷/光/暗五元素克制轮**。旧 spec 的元素体系未实现。

## 敌人机制（字符串键，非元素）

- `shield_phase`：血量 ≤35% 时，护甲 / 抗性 +25%，持续 3 回合。
- `enrage_50`：血量 ≤50% 时，伤害 +20%。
- `heavy_every_3`：每第 3 回合伤害 ×1.45。

技能按 trigger 类型选择：heal / shield 阈值触发，`execute`（斩杀）/ `opener`（开场）/ `burst`（爆发）。

## 通关评价（DungeonService.rating）

| 评价 | 条件 |
|------|------|
| **F** | 战斗失败 |
| **S** | powerRatio ≥ 1.25 且剩余血量 ≥ 65% |
| **A** | powerRatio ≥ 0.95 且剩余血量 ≥ 35% |
| **B** | 其余通关情况 |

⚠️ 评价由**战力比 + 剩余血量**决定，**无计时、无 C 评价、无复活**。旧 spec 的"S=3 分钟内无死亡 / C=有复活"未实现。

## 客户端战斗演出（BattleStagePhaser.tsx）

纯表现层，标签直接写着「AUTO BATTLE / 2D 自动战斗画面」：

- 双人舞台：一个矢量绘制的英雄 + 一个矢量绘制的怪物（如蜘蛛）。
- 回放服务端帧：血条、技能横幅、突进、投射 / 陨石 / 斩击 / 治疗 / 护盾特效（按 `frame.visualKey` 触发）、暴击 / 闪避浮字、屏幕震动。
- **所有数值由服务端决定**；前端只负责好看。仅在 `ResultScreen`（结算页）使用，是 Phaser 4 的唯一用途（应懒加载，见前端拆分方案）。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| 即时动作 + 虚拟摇杆 + 闪避按钮 | 服务端回合制模拟，客户端回放 |
| SpriteKit Physics 碰撞 / 前后摇 / 红圈预警 | 无 |
| 五元素克制（+50%/+30%） | 仅物理 / 魔法二元 |
| 帧冻结 / 击退 / 走位手感 | 仅屏震 + 粒子（表现层回放） |
| 精英词缀（反射 / 吸血 / 分裂） | `shield_phase` / `enrage_50` / `heavy_every_3` |
| 普通 / 困难 / 地狱难度链 | 难度为每副本配置（见副本文档） |
| 评价 S/A/B/C + 计时 + 复活 | F/B/A/S，按战力比 + 残血 |
