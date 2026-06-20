# 经济系统

> 来源：`gameplay/market/MarketService.java`、`gameplay/shop/*`、`gameplay/recharge/RechargeService.java`、`gameplay/inventory/*`、`domain-enhancement`、schema `market_listing` / `shop_offer` / `recharge_order` / `cash_income_event` / `economy_audit_event`。

## 设计哲学（保留）

单机经济的核心矛盾：既不能通胀（金币泛滥无意义），也不能太紧缩（刷不动）。原则：**略微入不敷出**，让玩家始终有赚钱动力。所有经济变动写入 `economy_audit_event` 审计。

## 货币体系（实际：2 种）

| 货币 | 字段 | 定位 | 获取 | 用途 |
|------|------|------|------|------|
| 金币 | `gold` | 主流通货币 | 打怪 / 卖装 / 任务 / 商会出售 | 强化、加工、商会、兑换 |
| 真实货币 | `real_money` | RMB 充值货币 | 充值（recharge） | 商店购买、充值竞争氛围、财富等级 |

⚠️ **没有钻石、没有荣誉点**。旧 spec 的三货币模型未实现。"高端层"由 **RMB 充值 + 财富等级（`wealth_tier`）** 承担。

## 金币消耗去向（gold sinks）

| 消耗 | 公式 / 说明 |
|------|------------|
| **强化装备** | `requiredLevel² × targetLevel × 10`（最大黑洞，见装备文档） |
| 加工操作 | 解锁孔位 / 镶嵌 / 重铸 / 升阶 / 精炼，材料 + 金币门槛 |
| 商会交易税 | 成交时抽 **8%**（仅卖方，永久消失） |
| 商店 | RMB 商品（`shop_offer`） |
| 金币兑换 | `GoldExchange`（机器人也用，玩家端有兑换入口） |

> 强化费用曲线示例（30 级装备强到 +10，不含失败重试）：`+1=9,000 … +10=90,000`，累计约 **495,000 金**，是核心 grind 引擎。

## 商会 / 市场（market）

端点 `/api/market`，前端 `MarketScreen`：

| 操作 | 端点 |
|------|------|
| 浏览挂单 | `GET /listings` |
| 上架 | `POST /listings` |
| 购买 | `POST /listings/{id}/buy` |
| 取消 | `POST /listings/{id}/cancel` |

- **固定单价挂单**，无竞价 / 无拍卖倒计时（旧 spec 的竞价系统未实现）。
- **税率 8%**（`MARKET_TAX_RATE = 8`，仅成交时从卖方扣，无上架费）。
- **限价 3 倍**：挂单价 ≤ 估值 ×3（`PRICE_CAP_MULTIPLIER`）。
- **机器人提供流动性**：`ensureRobotListings`（每 5 秒调度，目标挂单数随等级增长）持续补货；机器人也会买玩家 / 其它机器人的挂单。这是"商会很活"的来源，但也意味着流动性是系统保证的。

## 充值与财富氛围（recharge）

端点 `/api/recharge`，前端 `RechargeScreen`：

- `GET /dashboard`：玩家信息 + **机器人充值富豪榜**。
- `POST /api/recharge`：充值 `real_money`，设定财富等级（`wealth_tier`）。
- `RechargeService` 每 **180 秒**调度，生成机器人现金收入事件（`cash_income_event`），制造"大佬在充值"的氛围。

## 体力（stamina）

体力是副本次数的主要节流（默认上限 1000，每 3 分钟按现实时间自动回满）。它取代了旧 spec 的"每日副本收益上限"。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| 三货币（金币 / 钻石 / 荣誉点） | 金币 + RMB（real_money），无钻石 / 荣誉 |
| 拍卖费 上架 5% + 成交 10% / 抽 15% | **成交 8%**，无上架费 |
| 装备修理费 / 复活费 / 传送费 | 未实现（无耐久 / 无复活） |
| 神秘商人 | 未实现，商店是 RMB `shop_offer` |
| 每日副本收益上限 | 体力节流 |
| 竞价拍卖 | 固定价挂单 + 取消 |
| — | 新增：RMB 充值 + 财富等级 + 机器人富豪榜 |
