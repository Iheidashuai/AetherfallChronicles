# 机器人社交生态：公会系统 设计

> 状态：**P1-P4 已实现**（社交外壳 / 公会 Boss / 养成双轨 / 周榜+结算），经一轮 grilling 确定方案。代码见 `gameplay/guild/` 与 `screens/GuildScreen.tsx`；v2（阵营 / 挖人戏剧）仍为提案。
> 北极星：**让玩家觉得自己活在一个有人的世界里**。这是本项目最差异化的方向。
> 依赖现状：机器人是 `controller_type='robot'` 的 `player` 行（200 个），由 `RobotBrainService`（Utility-AI，`@Scheduled` 15s 采样 24 个）驱动；SSE 聊天；JdbcTemplate + MySQL；无 DB 行锁（单 JVM `synchronized`）。

---

## 一、产品设计（已敲定）

| 决策点 | 结论 |
|--------|------|
| 玩家角色 | **公会成员**（和 ~19 个机器人同会，不是会长） |
| 结构 | **单层公会**：~10 个公会 × ~20 成员。"小队"= 打 Boss 的临时队（轻量）；"阵营"= v2 赛季宏观层 |
| 核心循环（tentpole） | **每周公会 Boss 共斗**（共享血池） |
| Boss 模型 | **共享 HP 血池**：全公会打同一根血条，可实时看到队友的伤害推动血条下降；低血时机器人让路保证玩家参与；按个人伤害分奖，公会总伤入榜 |
| Boss 节奏 | **每周刷新 + 周结算**；血量 ≈ 够打一周；提前杀 → 刷更强的"荧耀一圈"Boss |
| 进度/经济 | **双轨**：① 公会等级（贡献累积→解锁全员被动增益）② 个人公会币（公会商店买宝石/材料/称号）。**每日捐献**= 真实动作 + 金币黑洞 |
| 入会 | **浏览公会列表自选**（显示等级/排名/人数/会长机器人口吻的招募语），换会有冷却 |
| 机器人"有人感"行为（v1） | **事件驱动公会聊天** + **机器人捐献（日志可见）** + **内部贡献内卷（争周 MVP，橡皮筋）**。挖人/跳槽/鄙视链 → v2 |
| 聊天深度 | **事件驱动为主**：机器人对真实公会事件（Boss 刷新/血量阶段/捐献/MVP 易主/升级/被反超）在 15-30s 内反应，辅以少量氛围闲聊。**事件锚定 = 杀死"空洞感"的关键** |
| 公会间排名 | **真实可升可降**：对手公会机器人真的在卷，懈怠就掉名次；周结算给前列公会发奖 + 全服公告"本周第一公会" |

### 核心循环图

```
每周一：各公会 Boss 刷新（世界钟事件）
   ↓
全周：你 + ~19 机器人 共斗本会 Boss（共享血池，可视化）
      + 每日捐献（金币/物品 → 公会资金 + 个人贡献）
   ↓ 产出
   贡献分 →┬→ 公会等级（全员被动增益，机器人的贡献也惠及你）
           ├→ 个人公会币（公会商店）
           ├→ 个人周贡献榜（和机器人争 MVP，橡皮筋）
           └→ 公会总贡献（公会间周榜，真实可升可降）
   ↓
全程：事件驱动公会聊天（Boss/捐献/MVP/反超 都有机器人反应）
   ↓
每周日：周结算 → 公会榜发奖 + 全服第一公会公告（世界钟事件）
```

---

## 二、技术设计（已敲定）

### 2.1 核心原则：单一决策大脑

机器人**所有能做的事都在同一个决策系统里**（`RobotBrainService`）。每个机器人每个 tick 像人一样**只做一件事**，从全部候选动作里按自身情况打分选出最高分。公会 Boss/捐献/聊天**不是独立子系统**，而是这套 Utility-AI 的新增候选动作。

> 关键区分：**机器人"动作"进统一大脑；Boss"生命周期"（刷新/结算）是世界事件，不是机器人决策**——没有机器人会"决定"刷 Boss，由世界钟负责。

新增 `RobotDecisionAction` 评分器（注册进 `RobotBrainService`，与 dungeon/market/arena/rest 同台竞争）：
- `GuildBossRobotAction` — 机器人所在公会 Boss 存活 + 有体力/资格时，可选去打 Boss。**伤害用便宜公式算出（见 2.2.1），不跑完整战斗**，扣共享血池、写贡献。低血时降低评分（让路给玩家）。
- `GuildDonateRobotAction` — 有富余金币/物品时，可选捐献（写捐献日志 + 贡献 + 公会资金）。
- `GuildChatRobotAction` — 在事件触发或闲时，向本会聊天频道发一条（事件驱动优先）。

> 满足 `AGENTS.md` 规则：新增玩法必须更新机器人决策引擎。

### 2.2 机器人保真度：全逐人真实模拟

**200 个机器人全部一等公民**——公会总伤 = 成员真实伤害汇总，对手公会名次 = 其真实机器人贡献之和。

可行性：现有 15s/24 个采样 ≈ 5760 次激活/小时，足够把**每周**的 Boss 贡献平摊到全 200 个机器人。Boss 伤害"随时间印象式累积"，但每一笔都是真实逐人产出。

#### 2.2.1 机器人伤害走公式，不跑完整战斗（采纳，关键决策）

调度负载的根源是"每个动作变重"——若机器人打 Boss 跑完整 `CombatEngine.fight()`（最多 30 回合），同一 tick 要多算好几场战斗。**对策：机器人对 Boss 的单次伤害用便宜公式估算**，几乎零 CPU，同时**保留"全 200 个机器人真实逐笔贡献"**（每一笔仍写进 `guild_boss_contribution`，按机器人战力区分强弱）：

```
botDamage ≈ botCombatPower × BOSS_DMG_COEFF × variance(0.9 ~ 1.1)
```

- `botCombatPower` 复用现有战力计算 → 强机器人贡献更高，名次"凭实力"，符合真实感。
- `BOSS_DMG_COEFF` 与 `guild_boss.hp_max`（随成员数缩放）一起调，使 Boss ≈ 够打一周。
- `variance` 用机器人 id + tick 等派生的**确定性**扰动（调度/脚本环境避免不可复现的随机源）。

**玩家与机器人非对称（有意为之）**：
- **玩家**点"攻击 Boss" → 跑**真实** `CombatEngine.fight()` vs Boss（Phaser 展示战斗演出，伤害反映 build/技能/强化）——这是玩家的乐趣与可视化所在。
- **机器人** → 走上面的公式（只产出一个数字 + 触发聊天）。

这样玩家依旧"有真打的战斗动画"，而 200 个机器人的逐笔贡献便宜到可忽略，**调度负载基本消失**。

> 仍保留一个观测点：上线后看公会动作是否把副本/市场活动挤太狠（**争用**问题，非 CPU 问题）；若是，再给公会动作设独立 tick 配额。

### 2.3 竞争平衡：轻度玩家感知橡皮筋

90% 自主决策（机器人按自身情况，像人）；10% 全局微调只抑制极端：你公会落后太多 → 队友机器人略加劲（托底）；对手领先太多 → 略放缓。正常区间**真实可升可降**，保证 1-2h/天玩家也摸得到前列，但**确实会输**。

### 2.4 并发：单 JVM `synchronized`（沿用现有风格）

共享血池由玩家 + 多个机器人并发扣减。沿用现有 `InventoryService.slotAllocationMonitor` / `MarketService.robotListingMonitor` 模式：

```java
Object monitor = guildBossMonitors.computeIfAbsent(guildId, k -> new Object());
synchronized (monitor) {
    // read hp_current → 扣减 → write；同时累加 contribution
}
```

> 注意（与全代码库同一约束）：**仅单实例安全**，不支持横向扩展。学习项目可接受。

### 2.5 聊天：扩展现有 `ChatService`

`chat_message` 加 `channel` 列（`'world'` | `'guild:<id>'`）。机器人 `GuildChatRobotAction` 发到 `guild:<id>`；玩家 SSE 流过滤为 `world + 自己公会`。因每会只有 1 个真人，无需真正的多订阅扇出。复用 `/api/chat/stream`。

### 2.6 世界钟：同步 ISO 周

新增轻量 `GuildSeasonService`（`@Scheduled`，每几分钟检查是否跨周）：
- `week_key`（如 `2026-W25`）复用 `player_rift_weekly_reward` 的周键模式。
- **周一**：结算上周（排名、发奖、`guild_weekly_result` 快照）→ 刷新各公会 Boss → 全服公告第一公会。
- **Boss 击杀**（事件，在 `synchronized` 扣血路径里触发）：标记 killed、发奖、刷"荧耀一圈"更强 Boss。

### 2.7 数据模型（新表 + 改动）

```sql
-- 公会本体
CREATE TABLE guild (
  id BIGINT PK AUTO_INCREMENT,
  name VARCHAR(64) UNIQUE, leader_robot_id BIGINT,
  level INT DEFAULT 1, fund BIGINT DEFAULT 0,
  total_contribution BIGINT DEFAULT 0,     -- 累积（用于公会等级）
  recruiting_blurb VARCHAR(160),           -- 会长口吻招募语
  created_at TIMESTAMP);

-- 成员（玩家与机器人共用，player_id 指向 player 表）
CREATE TABLE guild_member (
  guild_id BIGINT, player_id BIGINT PK,
  role VARCHAR(16) DEFAULT 'member',       -- leader/officer/member
  weekly_contribution BIGINT DEFAULT 0,    -- 本周（周结算清零）
  total_contribution BIGINT DEFAULT 0,
  joined_at TIMESTAMP, KEY idx_guild (guild_id));

-- 每公会每周一只 Boss（共享血池）
CREATE TABLE guild_boss (
  id BIGINT PK AUTO_INCREMENT, guild_id BIGINT, week_key VARCHAR(12),
  boss_template_id VARCHAR(64), tier INT DEFAULT 1,   -- 荧耀圈递增
  hp_max BIGINT, hp_current BIGINT,
  status VARCHAR(12) DEFAULT 'alive',      -- alive/killed/expired
  spawned_at TIMESTAMP, killed_at TIMESTAMP,
  UNIQUE KEY uk_guild_week_tier (guild_id, week_key, tier));

-- 个人对 Boss 的伤害贡献（UPSERT 累加）
CREATE TABLE guild_boss_contribution (
  guild_id BIGINT, week_key VARCHAR(12), player_id BIGINT,
  damage BIGINT DEFAULT 0, attempts INT DEFAULT 0,
  PRIMARY KEY (guild_id, week_key, player_id));

-- 捐献日志（可视化 + 贡献来源 + 金币黑洞）
CREATE TABLE guild_donation_log (
  id BIGINT PK AUTO_INCREMENT, guild_id BIGINT, player_id BIGINT,
  kind VARCHAR(12), amount BIGINT, contribution_gain BIGINT,
  created_at TIMESTAMP, KEY idx_guild_time (guild_id, created_at));

-- 周结算快照（公会间排名）
CREATE TABLE guild_weekly_result (
  week_key VARCHAR(12), guild_id BIGINT, rank INT,
  total_contribution BIGINT, reward_claimed BOOLEAN DEFAULT FALSE,
  PRIMARY KEY (week_key, guild_id));

-- 公会商店 & 等级配置
CREATE TABLE guild_shop_offer (id BIGINT PK, cost_guild_coin INT, reward_kind VARCHAR(24), reward_ref VARCHAR(64), ...);
CREATE TABLE guild_level_config (level INT PK, required_contribution BIGINT, perk_kind VARCHAR(24), perk_value INT);

-- 改动现有表
ALTER TABLE player ADD COLUMN guild_coin BIGINT NOT NULL DEFAULT 0;   -- 与 gold/real_money 同列
ALTER TABLE chat_message ADD COLUMN channel VARCHAR(24) NOT NULL DEFAULT 'world';
```

> schema 仍合并进单一 `latest_schema.sql`（遵守 `AGENTS.md`，无 Flyway）。

### 2.8 后端落点（沿用 `gameplay/<feature>/` 包结构，不建空壳模块）

新增 `mythic-realm-api/src/main/java/.../gameplay/guild/`：
- `GuildController`（`/api/guild`）：`GET /guilds`（浏览列表）、`POST /guilds/{id}/join`、`POST /leave`、`GET /`（我的公会快照：Boss/成员榜/聊天/资金）、`POST /boss/attack`、`POST /donate`、`GET /ranking`（公会周榜）、`GET /shop`、`POST /shop/{id}/buy`。
- `GuildService`（入会/换会/快照）、`GuildBossService`（共享血池扣减 + 贡献 + 击杀/荧耀圈）、`GuildDonationService`、`GuildRankingService`、`GuildShopService`、`GuildSeasonService`（世界钟）。
- 机器人三动作类放 `gameplay/robot/`（与现有 `*RobotAction` 同处），注册进 `RobotBrainService`。

集成点：
- **公会等级增益**：在副本结算金币 / 战力计算处叠加 `guild_level_config` 的 perk（如 +x% 金币、+x% Boss 伤害、+体力上限）。
- **公告**：新增 kind `guild_boss`（公会击杀）、`guild_first`（周第一公会），进现有滚动条。
- **体力**：Boss 攻击消耗体力或每日 Boss 次数（复用 `StaminaService`）。

### 2.9 前端落点（沿用拆分后的 `screens/` 结构）

- 新增 `screens/GuildScreen.tsx`（+ 必要的卡片组件入 `components/ui.tsx`）；主页 `NavTile` 加"公会"入口。
- 标签页：**公会大厅**（Boss 血条实时下降 + 成员周贡献榜 + 公会聊天 + 捐献按钮）/ **公会周榜** / **公会商店** / **浏览公会**（未入会时）。
- 数据走轮询（与现有一致）；公会聊天走 SSE（复用 `chatStreamUrl`，过滤 channel）。
- `api.ts` 加 `guildApi.*`；类型进 `types/`。

---

## 三、分期落地

| 阶段 | 内容 | 产出价值 |
|------|------|----------|
| **P1 社交外壳** | guild/guild_member 表、浏览/入会/换会、公会大厅页、公会聊天频道（channel）、机器人 `GuildChatRobotAction`（事件驱动雏形） | 立刻"有家、有人说话" |
| **P2 公会 Boss** | guild_boss/contribution 表、共享血池攻击（synchronized）、`GuildBossRobotAction`、世界钟刷新/击杀/荧耀圈、Boss 血条可视化 | tentpole 共斗体验 |
| **P3 养成双轨** | 捐献 + `GuildDonateRobotAction`、公会等级 + 全员增益、公会币 + 公会商店 | 金币黑洞 + 个人/集体回报 |
| **P4 真实竞争** | 公会间周榜、周结算发奖、全服第一公会公告、轻度橡皮筋调参、内部 MVP 内卷 | "真实可输赢"的张力 |
| **v2** | 阵营宏观层、挖人/跳槽/鄙视链涌现戏剧、小队深化 | 生态二期 |

## 四、风险与对策

1. **模板疲劳（最大风险）** → 聊天**事件驱动**为主，每个大事件 15-30s 内有反应；机器人个性（`personality`）决定语气；池子足够大并随版本扩充。
2. **调度负载（全逐人模拟）** → **已采纳对策：机器人 Boss 伤害走便宜公式（2.2.1），不跑完整战斗**，保留全 200 逐笔真实贡献的同时几乎消除 CPU 负载。残留的只是"争用"（公会动作 vs 其它动作抢 tick），靠调分数 / 必要时设独立配额解决；玩家攻击仍是真实战斗。
3. **并发正确性** → 共享血池只用 `synchronized` + 单条原子语义；仅单实例安全（与全库同约束）。
4. **节奏失衡**（Boss 太快被机器人打死） → 血量随成员数缩放 + 低血机器人让路 + 荧耀圈兜底。
5. **"与我无关"** → 机器人贡献惠及**全员增益**，让玩家乐见队友活跃；事件驱动聊天 @ 玩家制造关联。
```
