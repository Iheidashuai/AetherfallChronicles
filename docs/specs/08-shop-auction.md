# 商店 & 商会（市场）系统

> 来源：`gameplay/shop/*`、`gameplay/market/MarketService.java`、schema `shop_offer` / `shop_purchase_log` / `market_listing` / `market_sale`、前端 `ShopScreen` / `MarketScreen`。

## 商店（shop）

单一 **RMB 商品目录**，不是旧 spec 的多类 NPC 商店。

- 数据表 `shop_offer`：分类、商品（物品或金币包）、等级门槛、RMB 价格。
- 端点：`GET /api/shop`、`POST /api/shop/offers/{id}/buy`，购买写入 `shop_purchase_log`。
- 前端 `ShopScreen` 按分类（`shopCategoryName`）展示，购买用 `real_money`。

⚠️ **未实现**：武器店 / 药水店 / 材料店 / 神秘商人、每日限购、药水冷却 / 限时 buff。药水是普通可用物品（`/api/inventory/{itemId}/use`），不是独立商店。

## 商会 / 市场（market）

玩家与机器人共用的**固定价挂单市场**（非拍卖竞价）。

### 玩家功能

| 操作 | 端点 |
|------|------|
| 浏览挂单 | `GET /api/market/listings` |
| 上架 | `POST /api/market/listings` |
| 购买 | `POST /api/market/listings/{id}/buy` |
| 取消挂单 | `POST /api/market/listings/{id}/cancel` |

- **固定单价**，无竞价、无倒计时、无自动下架（手动取消；机器人会持续买卖搅动市场）。
- **成交税 8%**（仅卖方），无上架费。
- **限价**：挂单价 ≤ 估值 ×3。
- 上架后该物品有冷却（`market_lock_until`）。

### 前端筛选（MarketScreen）

按类型 / 品质 / 分类 / 价格筛选 + 排序（`MarketFilters`、`filterMarketListings`、`marketCategoryName`）。卖出记录在 `market_sale`（前端 `MarketSaleCard` / `MarketListedCard` 展示成交与自己的挂单）。

### 机器人流动性

机器人通过 `MarketSupply`（每 5 秒调度补货，目标挂单数随等级增长）与 `MarketBuy` 持续供需，使商会显得繁忙——代价是流动性由系统保证（详见 [经济系统](05-economy-system.md) 对该设计的取舍说明）。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| 武器 / 药水 / 材料店 + 神秘商人 | 单一 RMB `shop_offer` 目录 |
| 药水冷却 / 限时 buff | 药水为普通可用物品 |
| 竞价 + 一口价 | 仅固定价挂单 |
| 上架 5% + 成交 10% | 成交 8%，无上架费 |
| 3 游戏日自动下架 | 手动取消；机器人搅动 |
| 职业适用筛选 | 无服务端职业筛选 |
