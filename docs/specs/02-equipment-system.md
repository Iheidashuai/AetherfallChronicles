# 装备 / 强化 / 加工系统

> 来源：`domain-equipment/SlotType.java`、`domain-enhancement/*`、`gameplay/inventory/EquipmentProcessingController.java`、schema `item_template` / `item_instance` / `equipment_slot` / `equipment_socket` / `equipment_affix` / `gem_template`、前端 `InventoryScreen` / `BlacksmithScreen`。

## 装备槽（9 个）

`SlotType`：weapon（武器）、helmet（头盔）、armor（护甲）、legs（护腿）、boots（靴子）、gloves（护手）、necklace（项链）、ring1、ring2（双戒指）。

> 旧 spec 写"8 部位"；实际两个戒指槽独立计数 → **9 槽**。

## 品质（6 档）

字符串编码，贯穿掉落 / 词缀 / 孔位逻辑：
`common`（普通）、`uncommon`（优秀 / 绿）、`rare`（稀有 / 蓝）、`epic`（史诗 / 紫）、`legendary`（传说 / 橙）、**`immortal`（不朽，最高档）**。

> 旧 spec 以传说封顶；实际代码在传说之上还有 **immortal** 档（特殊副本保底产出，见副本文档）。装备品质颜色统一取代码中的品质色，页面不重复定义。

## 装备属性

模板 / 实例上的属性字段：`attack_bonus`、`defense_bonus`、`resistance_bonus`、`hp_bonus`、`mp_bonus`、`crit_bonus`、`required_level`、`sell_price`。

实例额外成长字段：`enhancement_level`、`enhancement_luck`、`refine_level` / `refine_focus`、`ascension_level` / `ascension_luck`、`market_lock_until`（上架冷却）。

种子数据约 **2900** 条 `item_template`。

## 词缀（affix）

`equipment_affix`：带索引的 `stat_key` / `value`、`tier`（档次）、可锁定（lockable）。即可随机 roll、可锁定保留的词缀系统。通过加工中的"重铸"重新 roll（见下）。

## 宝石与孔位（socket / gem）

- `equipment_socket`：每件装备的带索引孔位，可解锁，存放一个宝石物品 id。
- `gem_template`：`gem_kind`、`rank_level`、`stat_key` / `value`、`next_template_id`（升级链）。
- 孔位通过"加工"系统解锁 / 镶嵌 / 取下；宝石通过"宝石升级"沿 `next_template_id` 升阶（材料门槛）。

## 套装

⚠️ **未实现**。schema 与代码中**没有套装表或套装加成逻辑**。旧 spec 的"6 件套 2/4/6 效果"目前不存在——若要做需新增数据与结算。

---

## 强化系统（+0 → +15）

来源：`domain-enhancement`，端点 `POST /api/enhancement/enhance`（前端 `BlacksmithScreen`）。

- **上限 +15**（`EnhancementLevel.MAX_LEVEL = 15`）。
- **三档强化区，按目标等级自动选择**（非玩家手选"安全/普通/激进"）：
  - 安全区 +1~+6（`SafeEnhancementStrategy`）
  - 普通区 +7~+12（`NormalEnhancementStrategy`）
  - 激进区 +13~+15（`RiskyEnhancementStrategy`）：基础成功率 **20%**，失败 **掉 2 级**。
- **幸运值**：`enhancement_luck` 随尝试累积（pity 机制）。
- **费用公式**：`requiredLevel² × targetEnhancementLevel × 10`。
  - 例：30 级装备 +9→+10 = `30² × 10 × 10 = 90,000` 金。

> 旧 spec 的"100/80/60/40/20 成功率表 + 祝福石 50 钻石"中，仅激进区 20% / 掉 2 级在代码中可验证；安全 / 普通区的具体成功率以策略类为准。保护机制是**幸运值累积**而非钻石道具（无钻石货币）。

---

## 装备加工系统（equipment-processing）

端点前缀 `/api/equipment-processing`，前端 `BlacksmithScreen` 内 `EquipmentProcessingPanel`。所有操作写入 `equipment_processing_log`。

| 操作 | 端点 | 说明 |
|------|------|------|
| 解锁孔位 | `POST /{itemId}/sockets/unlock` | 解锁一个宝石孔 |
| 镶嵌宝石 | `POST /{itemId}/sockets/{i}/socket` | 往第 i 孔放宝石 |
| 取下宝石 | `POST /{itemId}/sockets/{i}/unsocket` | 取出宝石 |
| 宝石升级 | `POST /gems/upgrade` | 沿 `next_template_id` 升阶 |
| 重铸 | `POST /{itemId}/reforge` | 重新 roll 词缀 |
| 升阶 | `POST /{itemId}/ascend` | 提升 `ascension_level`（带 luck） |

另外，背包控制器（`/api/inventory`）提供两个相关操作：
- **精炼** `POST /{itemId}/refine`（提升 `refine_level` / 设定 `refine_focus`）
- **强化转移** `POST /transfer-enhancement`（把 +等级从一件装备转到另一件）

各操作均有材料 / 金币门槛，前端用 `*BlockReason` 系列函数给出不可执行原因。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| 8 部位 | **9 槽**（双戒指） |
| 传说封顶 | 新增 **immortal** 档 |
| 6 件套套装 | **未实现** |
| 祝福石（钻石） | 幸运值累积，无钻石 |
| 手选安全/激进 | 按目标等级自动分区 |
| 取出宝石 = 等级×1000 金 | 取下 / 镶嵌走加工系统，材料门槛 |
| — | 新增：重铸 / 升阶 / 精炼 / 强化转移 |
