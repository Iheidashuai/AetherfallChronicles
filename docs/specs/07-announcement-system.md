# 全服通告系统

> 来源：`gameplay/announcement/*`、schema `global_announcement`、前端 `GlobalTicker`（App.tsx）、`announcementKindName`。

## 设计目标（保留）

用全服通告制造社交压力与成就感，驱动玩家追求目标；机器人也会触发通告，让世界显得热闹。

## 实际实现：顶部滚动条（GlobalTicker）

通告以**屏幕顶部横向滚动条**呈现（`GlobalTicker`），登录后、非 auth 屏均显示。**没有**旧 spec 的三级展示（系统频道 / 横幅 / 全屏粒子特效）。

- 数据表 `global_announcement`：`kind`、`actor_name`、`text`、`priority`、`created_at`。
- 端点：`GET /api/announcements`。
- 前端按 `priority` + 时间排序滚动；本地用 localStorage 记录已读 key（`announcementSeenKey` / `readSeenAnnouncementKeys` / `writeSeenAnnouncementKeys`）做"新通告"提示。

## 通告类型（kind，共 4 种）

`announcementKindName` 映射：

| kind | 显示名 | 典型触发 |
|------|--------|----------|
| `loot` | 高阶掉落 | 玩家 / 机器人获得高品质（传说 / 不朽）装备 |
| `enhance` | 强化突破 | 强化达到高等级里程碑 |
| `level` | 等级提升 | 等级里程碑 |
| `system` | 世界通告 | 系统级公告（兜底） |

> 具体触发阈值由后端发布逻辑决定；玩家与机器人共用同一套通告流，所以滚动条里会混着真人和机器人的"大事"。

## 机器人对通告 / 事件的反应

机器人通过 `SocialChat` 动作在世界频道（SSE 聊天）做出反应，按性格选模板，例如对"某玩家强化到 +10"：

```
[friendly] "牛啊 {player}！+10 了，什么时候带我打副本"
[showoff]  "我的也快 +10 了，等我两天"
[casual]   "有钱人...我 +7 都升不上去"
[newbie]   "强化石都是哪刷的啊，我都不够用"
[hardcore] "+10 之后性价比就低了，建议攒着搞下一件"
[merchant] "需要强化石吗，我这有存货"
```

聊天细节见 [机器人模拟](06-bot-simulation.md) 与下方"社交频道"。

## 社交频道（chat，SSE）

虽不属严格"通告"，但与通告共同构成社交氛围：

- 端点：`GET /api/chat/messages`（历史）、`GET /api/chat/stream`（**SSE 实时推送**，`SseEmitter`）、`POST /api/chat/messages`（玩家发言）。
- 玩家发言后触发模板化的机器人 / 系统回复，消息 `kind` 为 `player` / `robot` / `system`。
- 前端 `ChatScreen` 用 `EventSource` 订阅，是全应用唯一的实时推送通道（其余皆轮询）。

## 与旧 iOS spec 的差异（已修正）

| 旧 spec | 实际 |
|---------|------|
| 三级展示（频道 / 横幅 / 全屏特效 + 烟花） | 单一顶部滚动条 |
| 强化 +7/+10/+12/+15 分级特效 | 通告类型仅 loot/enhance/level/system |
| 集齐套装通告 | 无套装系统 |
| 世界 Boss 击杀通告 | 无世界 Boss |
| 排行榜登顶全屏特效 | 按 kind 进滚动条 |
