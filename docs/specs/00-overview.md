# MythicRealm - 游戏总览

## 项目定位

iOS 单机 RPG 游戏，西方魔幻题材，2D 像素风格，即时动作战斗。
核心卖点：通过中度模拟的机器人系统，让玩家在单机体验中感受到「仿 MMO」的社交氛围。

## 核心特色

- 即时动作战斗，操作感强
- 装备强化/镶嵌/套装，刷装驱动力
- 200-300 个有性格的机器人模拟多人在线
- 世界频道聊天、拍卖场交易、排行榜竞争，单机也热闹

## 技术选型

| 项目 | 选择 | 理由 |
|------|------|------|
| 语言 | Swift | Apple 原生，性能最优 |
| 游戏框架 | SpriteKit | 2D 像素游戏最佳选择，零外部依赖 |
| UI 框架 | SwiftUI + SpriteKit 混合 | SwiftUI 做菜单/背包/商店等 UI，SpriteKit 做战斗场景 |
| 数据存储 | SQLite (GRDB.swift) | 结构化数据存储，未来可扩展联网 |
| 架构模式 | ECS (Entity-Component-System) | 游戏开发标准架构，适合复杂系统组合 |
| 资源管线 | Aseprite 导出 → Texture Atlas | 像素美术标准工作流 |

## 时间系统

**10 分钟现实时间 = 1 游戏日**

| 现实时间 | 游戏时间 |
|----------|----------|
| 10 分钟 | 1 天 |
| 1 小时 | 6 天 |
| 2.5 小时 | 15 天 |
| 约 17 小时 | 100 天（一个赛季） |

## 游戏日内时段划分（10 分钟内）

```
0:00 ~ 1:00 (第1分钟)   → 「早晨」：日常任务刷新、商店补货
1:00 ~ 3:00 (第1~3分钟) → 「白天」：拍卖场第一波上新
3:00 ~ 5:00 (第3~5分钟) → 「午间」：神秘商人可能出现
5:00 ~ 7:00 (第5~7分钟) → 「傍晚」：拍卖场第二波上新
7:00 ~ 9:00 (第7~9分钟) → 「夜晚」：聊天频道最活跃
9:00 ~ 10:00(第9~10分钟)→ 「深夜」：排行榜结算、bot 数据更新
```

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│                    Game Manager                       │
├──────────┬──────────┬──────────┬──────────────────────┤
│  Scene   │  Entity  │   Data   │     Simulation      │
│  Manager │  System  │  Layer   │     Engine          │
├──────────┼──────────┼──────────┼──────────────────────┤
│ Town     │ Player   │ SQLite   │ Bot Manager         │
│ Dungeon  │ Monster  │ Save/Load│ Auction Scheduler   │
│ World Map│ NPC      │ Config   │ Chat Generator      │
│ Battle   │ Item     │ Tables   │ Ranking Simulator   │
└──────────┴──────────┴──────────┴──────────────────────┘
```

## 项目结构

```
MythicRealm/
├── MythicRealm.xcodeproj
├── MythicRealm/
│   ├── App/                    # App 入口
│   ├── Core/                   # 核心引擎
│   │   ├── ECS/               # Entity-Component-System
│   │   ├── Scene/             # 场景管理
│   │   └── Physics/           # 碰撞检测
│   ├── Game/                   # 游戏逻辑
│   │   ├── Character/         # 角色系统
│   │   ├── Combat/            # 战斗系统
│   │   ├── Equipment/         # 装备系统
│   │   ├── Dungeon/           # 副本系统
│   │   ├── Quest/             # 任务系统
│   │   ├── Shop/              # 商店系统
│   │   └── Auction/           # 拍卖场
│   ├── Simulation/            # 模拟在线系统
│   │   ├── BotManager/        # 机器人管理
│   │   ├── ChatSimulator/     # 聊天模拟
│   │   ├── RankingEngine/     # 排行榜引擎
│   │   └── EventScheduler/    # 事件调度
│   ├── Data/                   # 数据层
│   │   ├── Database/          # SQLite 操作
│   │   ├── Models/            # 数据模型
│   │   └── Config/            # JSON 配置
│   ├── UI/                     # SwiftUI 界面
│   │   ├── HUD/              # 战斗 HUD
│   │   ├── Inventory/        # 背包界面
│   │   ├── CharacterPanel/   # 角色面板
│   │   └── Menus/            # 各种菜单
│   └── Resources/             # 资源文件
│       ├── Sprites/           # 精灵图集
│       ├── Audio/             # 音频
│       ├── Particles/         # 粒子特效
│       └── Data/              # 配置 JSON
└── MythicRealmTests/
```

## 美术资源方案

纯程序员开发，无美术人员：

1. **角色/怪物**：购买像素素材包（itch.io，$5-$30/包）
2. **地图 Tileset**：购买/免费 tileset，16×16 或 32×32 像素
3. **UI**：像素 UI 素材包 + 代码自定义
4. **特效**：SpriteKit 粒子系统生成
5. **AI 辅助**：AI 生成像素风概念图，手动微调
