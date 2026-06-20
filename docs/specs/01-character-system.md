# 角色系统

> 来源：`gameplay/player/PlayerService.java`、`domain-player`、schema `player` 表、`gameplay/skill/*`、前端 `CreatePlayerScreen`。

## 职业（3 种）

由 `ProfessionStats.forName` 硬编码，其它取值会被拒绝。

| 职业 | key | 主属性 | 起始属性（力/敏/体/智/灵） | 定位 |
|------|-----|--------|----------------------------|------|
| 战士 | `warrior` | 力量 / 体质 | 10 / 5 / 8 / 3 / 4 | 高血量近战 |
| 游侠 | `ranger` | 敏捷 / 力量 | 6 / 10 / 5 / 4 / 5 | 高命中暴击 |
| 法师 | `mage` | 智力 / 灵力 | 3 / 4 / 4 / 10 / 9 | 魔法爆发 |

## 五大基础属性

`strength`（力量）、`agility`（敏捷）、`constitution`（体质）、`intelligence`（智力）、`spirit`（灵力）。

基础属性经 `CombatStatsService` 派生为战斗属性（攻击、防御、抗性、暴击、命中、闪避、血量等），供战斗引擎使用。**注意**：战斗为回合制，**没有"攻击速度"属性**；伤害类型只有物理 / 魔法两类（见 [战斗系统](03-combat-system.md)）。具体派生系数以 `CombatStats` 代码为准，本文不固化数值。

## 等级与经验

- **等级上限：90**（`MAX_LEVEL = 90`）。
- **升级所需经验**：`experienceRequired(level) = 100 × level^1.8`。
- **每级自动成长**：五属性各 +1，对应职业的两条主属性额外 +1（战士→力+体，游侠→敏+力，法师→智+灵）。
- **每级 +3 自由属性点**（`free_points` 列），玩家自行分配。

> 自由点当前无软上限 / 边际递减，是后续平衡可深化点。

## 玩家持久化字段（player 表节选）

- 资源：`gold`（默认 100）、`real_money`（RMB 充值货币）、`stamina_current`（默认 1000）
- 财富氛围：`wealth_tier_level` / `wealth_tier`（虚荣财富等级，用于充值榜）
- 身份：`controller_type`（`player` | `robot`，机器人复用同表）、`title`、`personality`
- 冗余统计：`dungeon_clears`、`peak_enhancement`、`legendary_loot_count`、`current_activity_*`

## 技能系统

- 端点：`GET /api/skills`、`POST /api/skills/{skillId}/learn`、`POST /api/skills/{skillId}/upgrade`，数据在 `skill_template`。
- 技能按 trigger 类型参与战斗回合（见战斗文档的技能触发）。
- ⚠️ 旧版"每职业 3 条分支天赋树 + 火/冰/雷元素切换"**未实现**；当前是可学习 / 升级的技能列表，无分支树、无元素切换。

## 角色创建流程

1. 前端 `CreatePlayerScreen` 选职业（常量 `CREATE_PROFESSIONS` 展示属性 / 成长 / 适合人群）。
2. `POST /api/players` → 校验角色名 2-16 字符，插入 `player` 行，发放新手装备。
3. 一账号一角色（`uk_player_account` 唯一约束）。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| 等级上限 60 | **90** |
| 攻速属性、`防御=体质×2` 等固定换算 | 以 `CombatStats` 为准，无攻速 |
| 3 分支天赋树 + 元素切换 | 普通技能列表，物理 / 魔法二元 |
