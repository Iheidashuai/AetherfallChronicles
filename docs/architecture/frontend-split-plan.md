# 前端 App.tsx 拆分方案

> 目标：把 `web/src/App.tsx`（8026 行 / 84 个组件 / 145 个 helper）拆成可维护、可懒加载、AI 友好的多文件结构，**不改变任何运行时行为**。

## ✅ 执行状态（已落地）

本方案已执行，`tsc -b` 与 `vite build` 均通过，`vite preview` 返回 200、入口包正常、Phaser 已懒加载。最终落地结构（与下文计划略有取舍）：

- `src/types/index.ts` — 全部共享类型。
- `src/lib/constants.ts` — `STAMINA_RECOVERY_SECONDS` / `ANNOUNCEMENT_SEEN_STORAGE_KEY` / `ATTRIBUTE_LABELS` / `CREATE_PROFESSIONS`。
- `src/lib/helpers.tsx` — 全部 145 个纯 helper（**单模块**，规避跨文件依赖；可后续再按主题拆，且不需改调用方）。
- `src/components/ui.tsx` — 全部 63 个非屏幕组件（卡片 / 弹窗 / 面板 / 徽章 / 滚动条等）。同文件内互相引用，零 import 成本。
- `src/screens/<Name>.tsx` — 20 个屏幕各一个文件，从 `../components/ui` 与 `../lib/helpers` 引入依赖。
- `src/App.tsx` — 瘦身到 **233 行**：导入 + 路由阶梯。
- `src/screens/ResultScreen.tsx` — `BattleStagePhaser` 改为 `React.lazy` + `Suspense`。

**收益**：`App.tsx` 8026 → 233 行；主 JS 包 **2128 KB → 452 KB**（gzip 514 → 131 KB，约 −75%），Phaser 拆成按需加载的独立 chunk（1674 KB）。

> 取舍说明：计划里"shared 组件进 `components/`、cluster 组件就近放屏幕文件"为降低风险改成了"所有非屏幕组件统一进 `components/ui.tsx`"。因 `noUnusedLocals` 关闭，屏幕文件使用统一 kitchen-sink import header，编译期零未用告警。后续可把 `ui.tsx` / `helpers.tsx` 再按主题细分（纯机械、无需改调用方）。

---


## 1. 现状与拆分前提

| 事实 | 拆分含义 |
|------|----------|
| 84 个组件全是顶层 `function X()`，**无嵌套组件**、无 `memo`/箭头组件 | 每个组件都能整体平移到独立文件，零重构风险 |
| 145 个顶层 helper（formatter / 名称映射 / 计算 / 类型守卫 / 排序 / 转换器 / 构筑变更） | 全部是纯函数，天然属于 `lib/` |
| `screen` 路由是 `App()`（260-310 行）里一条 `{screen === 'x' && <XScreen/>}` 阶梯 | 每个屏幕 1:1 对应一个文件，拆分边界清晰 |
| 跨屏共享类型在 `App.tsx` 内声明（`EquipmentDetailData` 等，见 §4） | **必须先抽出共享类型，否则会循环依赖**——这是第一步 |
| `BattleStagePhaser`（Phaser 4）只在 `ResultScreen` 用一次，却静态打进主包（2.0MB） | 拆分时顺手改成 `React.lazy`，单独成 chunk |
| 零测试 | 拆分本身无法靠测试兜底 → 用 `tsc --noEmit` + 逐文件平移 + 人工冒烟保证不回归 |

**核心原则**：本方案是**机械搬运**，不是重写。每一步只剪切+粘贴+加 import/export，`tsc` 必须始终绿。任何"顺手优化逻辑"的冲动都推迟到拆分完成后单独提交。

## 2. 目标目录结构

```
web/src/
├── main.tsx                      # 不动
├── api.ts                        # 不动（后续可选拆分，见 §6）
├── store.ts                      # 不动
├── App.tsx                       # 瘦身到 ~90 行：Providers + 路由阶梯
├── BattleStagePhaser.tsx         # 不动内容，改为被 lazy import
│
├── types/
│   └── index.ts                  # 所有跨屏共享的本地类型（见 §4）
│
├── lib/
│   ├── constants.ts              # CREATE_PROFESSIONS, ATTRIBUTE_LABELS, STAMINA_RECOVERY_SECONDS, ANNOUNCEMENT_SEEN_STORAGE_KEY
│   ├── format.ts                 # formatNumber/formatChatTime/pad2/formatRelativeTime/formatStaminaTime/formatSigned/formatPercent/formatDropRate/formatStatValue/formatMarketActivityTime
│   ├── names.ts                  # 所有 *Name / *Label i18n 映射（professionName, slotName, qualityName, statName, announcementKindName, robotActivityKindName, …约 30 个）
│   ├── quality.ts                # enhancementStage/enhancementEffectClass/enhancementBadgeText/battleLogTone/battleEventName/catalogIconForItem/qualityRank/dropTypeRank
│   ├── power.ts                  # itemPower/catalogItemPower/processedStatValue/enhancedStatValue/enhancedCritValue/processedCritValue/powerBreakdownForHome/socketSlotsFor/combinedDropChance/marketPriceEstimate/enhanceCost/enhanceChance/refineCost
│   ├── guards.ts                 # isEquipmentItem/isSpecialDungeon/isMarketableInventoryItem/isEquipmentUpgrade + 所有 *BlockReason（refine/socket/reforge/ascend/gemUpgrade）+ canRefine/missingMaterialParts/withinRange
│   ├── sort.ts                   # sortItems/filterCatalogItems/filterMarketListings/filterRobots/rankLeaderboardEntries/leaderboard* 取值函数/uniqueDropTypes/itemTypesForCategory
│   ├── detail.ts                 # 文案与详情构造：equipmentDisplayName/itemEffectText/bonusText/gemEffectText/catalog* 文案 + 所有 *ToDetail 转换器（catalogItemToDetail/toEquipmentDetail/marketItemToDetail/leaderboardEquipmentToDetail/leaderboardEntryToSpeaker）
│   ├── build.ts                  # buildToDraft/draftToRequest/buildGapWarnings/assignBuildEquipment/updateDraftSkill/assignSkillToFirstSlot/toggleTalent/buildSlotForItem
│   └── announcements.ts          # announcementSeenKey/readSeenAnnouncementKeys/writeSeenAnnouncementKeys
│
├── components/                   # 跨屏复用的 UI 原子/分子
│   ├── layout/
│   │   ├── TopBar.tsx            # TopBar
│   │   ├── GlobalTicker.tsx      # GlobalTicker
│   │   ├── StateScreens.tsx      # LoadingScreen + ErrorScreen + EmptyState
│   │   └── primitives.tsx        # SectionTitle + Metric
│   ├── feedback/
│   │   └── Dialogs.tsx           # FeedbackDialog + ConfirmDialog
│   ├── item/
│   │   ├── ItemCard.tsx          # ItemCard + EnhancementBadge
│   │   ├── ItemDetail.tsx        # ItemDetail
│   │   ├── CompareCard.tsx       # CompareCard
│   │   └── ItemModals.tsx        # EquipConfirmModal + EnhanceModal + SellConfirmModal
│   └── stamina/
│       └── StaminaPanel.tsx      # StaminaPanel
│
└── screens/                      # 每个路由一个文件夹，cluster-only 组件就近放置
    ├── AuthScreen.tsx
    ├── CreatePlayerScreen.tsx            (+ ProfessionGlyph)
    ├── HomeScreen.tsx                    (+ HomeEquipmentOverview, NavTile, EquipmentPanel)
    ├── CharacterScreen.tsx               (+ CombatStatsPanel, StatContributionGrid, CombatStatCell, StatContributionCell, PowerBreakdownPanel)
    ├── SkillsScreen.tsx                  (+ SkillCard)
    ├── ItemCatalogScreen.tsx            (+ CatalogFilterGroup, CatalogDetailPanel)
    ├── DungeonScreen.tsx                 (+ DungeonCard, SpecialDungeonPanel, DropPreviewCard)
    ├── ResultScreen.tsx                  (+ CombatantCard, HpBar, ResultSummaryModal, SweepSummaryModal, battleFramesForResult；lazy 引 BattleStagePhaser)
    ├── InventoryScreen.tsx               (+ TransferItemOption)
    ├── BlacksmithScreen.tsx             (+ EquipmentProcessingPanel)
    ├── QuestScreen.tsx                   (+ QuestCard, QuestDetailPanel)
    ├── MarketScreen.tsx                  (+ MarketSaleCard, MarketListedCard, ListingCard)
    ├── RobotActivityScreen.tsx          (+ RobotActivityCard, RobotEventCard, RobotActivityDetailModal, RobotRangeFilter)
    ├── ShopScreen.tsx
    ├── BuildsScreen.tsx                  (+ BuildScorePanel, BuildSimulationPanel)
    ├── EndgameRiftScreen.tsx            (+ RiftResultPanel)
    ├── ArenaScreen.tsx                   (+ ArenaProfileCard/OpponentCard/ShopCard/MatchPanel/FighterCard/RankRow)
    ├── RechargeScreen.tsx               (+ WealthTierCard, RobotRechargeCard, CashIncomeCard)
    ├── ChatScreen.tsx                    (+ ChatMessageBubble, SpeakerDetailModal)
    └── LeaderboardScreen.tsx            (+ LeaderboardCard)
```

> **就近原则**：只被单个屏幕使用的组件/类型/helper，放进该屏幕文件或同目录；被 ≥2 个屏幕使用的，上提到 `components/` 或 `lib/`。§5 的"组件归属表"给出每个组件的去向。

## 3. 迁移顺序（7 个阶段，每阶段结束 `tsc` 必须绿）

按依赖方向"从叶子到根"搬，保证每一步都能独立编译、独立提交、独立回滚。

### 阶段 0 — 抽共享类型（必须最先做）
把 `App.tsx` 第 85-141 行的本地类型（见 §4）剪到 `types/index.ts` 并 `export`，在 `App.tsx` 顶部 `import type { … } from './types'`。
**为什么先做**：这些类型被 lib 和多个屏幕共享；先有它们，后面每个文件才能各自 import，不会出现"组件搬走了但类型还在 App.tsx"的循环依赖。
验收：`tsc` 绿，运行时零变化。

### 阶段 1 — 抽纯函数库 `lib/`
按 §2 的分组把 145 个 helper + 4 个常量剪到 `lib/*.ts` 并 `export`。lib 内部互相依赖用相对 import；lib 依赖 `types/` 和 `api.ts` 的类型。
**风险点**：少数 helper 互相调用（如 `power.ts` 用到 `quality.ts` 的 `qualityRank`）——用 import 连起来即可，无环（纯函数单向依赖）。
验收：`tsc` 绿；`App.tsx` 顶部新增一批 `import { … } from './lib/…'`。

### 阶段 2 — 抽共享 UI 组件 `components/`
搬 §2 列出的跨屏组件（TopBar/GlobalTicker/状态屏/对话框/Item 系列/StaminaPanel）。这些组件依赖 `lib/` 和 `types/`，已就绪。
验收：`tsc` 绿；首页、背包、副本三条路径手动冒烟。

### 阶段 3-6 — 逐屏搬迁（每阶段搬一组 cluster，4 个阶段搬完 20 屏）
建议分组（耦合内聚、互不依赖，可并行）：
- **3a 基础链路**：Auth、CreatePlayer、Home、Character、Skills
- **3b 物品链路**：Inventory、Blacksmith、ItemCatalog、Quest
- **3c 战斗链路**：Dungeon、Result（含 Phaser lazy 化）
- **3d 经济+社交+终局**：Market、Shop、Recharge、Chat、Leaderboard、RobotActivity、Builds、EndgameRift、Arena

每搬一个屏幕：剪屏幕组件 + 其 cluster-only 子组件 + cluster-only 类型/helper → `screens/XScreen.tsx`，`export function XScreen`，在 `App.tsx` 改成 `import { XScreen } from './screens/XScreen'`。
验收：每组搬完 `tsc` 绿 + 该组路径点一遍。

### 阶段 7 — 瘦身 App.tsx + Phaser 懒加载
`App.tsx` 只剩：providers、`GlobalTicker`、`lastResult` 状态、路由阶梯（全是 import 进来的屏幕）。约 90 行。
`ResultScreen` 里 `const BattleStagePhaser = lazy(() => import('../BattleStagePhaser'))`，用 `<Suspense fallback={…}>` 包裹。
验收：`vite build` 后确认 Phaser 进入独立 chunk、主包显著变小；结算页动画正常。

## 4. 必须先抽出的共享类型（阶段 0 清单）

这些在 `App.tsx`（行号见括号）内声明、被多处引用，是拆分的"地基"：

| 类型 | 行 | 被谁用 |
|------|----|--------|
| `EquipmentDetailData` | 141 | **最跨切**：ItemDetail、CatalogDetailPanel、所有 `*ToDetail` 转换器 |
| `StaminaView` | 98 | StaminaPanel、liveStaminaSnapshot |
| `BuildDraft` | 112 | BuildsScreen + 6 个 build 变更函数 |
| `PowerBreakdownSlice` | 121 | PowerBreakdownPanel、powerBreakdownForHome |
| `MarketFilters` | 132 | MarketScreen、filterMarketListings、emptyMarketFilters |
| `RobotFilters` / `RobotFilterKey` | 128 / 107 | RobotActivity/Recharge、filterRobots |
| `PlayableProfession` / `AttributeKey` | 129 / 130 | CreatePlayer、ProfessionGlyph、常量表 |
| 各 union 过滤类型 | 85-109 | `InventoryAction/DungeonMode/...BuildTab` 等，多数屏幕私有，但 `LeaderboardMetric`、`ProfessionFilter` 跨用 → 进 `types/` |

> 屏幕私有的 union 类型可以跟随屏幕走；但为简单起见，阶段 0 可把全部 union 类型统一放 `types/index.ts`，零判断成本。

## 5. 组件归属总表（84 → 去向）

- **共享层 `components/`**（13）：GlobalTicker, TopBar, LoadingScreen, ErrorScreen, EmptyState, SectionTitle, Metric, FeedbackDialog, ConfirmDialog, ItemCard, ItemDetail, EnhancementBadge, CompareCard, EquipConfirmModal, EnhanceModal, SellConfirmModal, StaminaPanel
- **屏幕及其 cluster-only 子组件 `screens/`**（其余）：按 §2 括号内归属就近放置。例如 Arena 的 6 个卡片组件全部进 `screens/ArenaScreen.tsx`（或 `screens/arena/` 子目录，若单文件过大）。

> 经验阈值：拆完后单个屏幕文件若仍 >500 行（Blacksmith ~580、Inventory ~420、Builds ~400），可在该屏幕目录内再分 `XScreen.tsx` + `components.tsx` + `logic.ts`。优先保证"一个路由一个目录"。

## 6. 拆分完成后的可选后续（不属于本次机械搬运）

1. **引入 React Router**：把 §1 的 `screen ===` 阶梯换成真实路由，解锁 URL/深链/前进后退/按路由 code-split。
2. **`api.ts` 拆分**（1292 行 / 85 个类型）：按 feature 拆 `api/auth.ts`、`api/dungeon.ts`…，类型进 `types/`。
3. **`styles.css` 模块化**（7895 行 / 590 全局类）：先按 §2 的屏幕边界切成 `screens/X.module.css`，消除 `.phone-frame` 双定义（见 UI 设计文档）。
4. **`token` 改为从 store/context 读**，去掉 20 个屏幕的 prop 钻取。
5. **补关键路径测试**：充值/强化/拍卖/认证。

## 7. 风险与回滚

- **唯一真实风险**：搬动时漏掉一个 import 或类型，`tsc` 会立刻报错——这是好事，编译器即测试。
- **每阶段一个 commit**，出问题 `git revert` 单个阶段即可。
- **不要在搬运 commit 里夹带逻辑修改**；逻辑优化另开 commit，便于 review 与二分定位。
- 全程保持运行时行为字节级不变；唯一预期的运行时变化是阶段 7 的 Phaser 懒加载（首屏更快）。
