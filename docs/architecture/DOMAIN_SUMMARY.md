# DDD 领域架构总结

本文档总结了当前聊天、榜单和通告的实现位置。项目已经收敛为个人单机学习游戏，不再维护旧 `backend/` 包路径。

## 1. 聊天领域 (Chat Domain)

### 目录结构
```
backend-ddd/mythic-realm-api/src/main/java/com/mythicrealm/api/gameplay/chat/
├── ChatController.java
└── ChatService.java
```

### 核心职责
- **ChatMessage (聚合根)**: 管理聊天消息的生命周期,包括消息类型(玩家/机器人/系统)、内容验证和事件发布
- **ChatApplicationService**: 协调聊天消息的发送、接收,管理开场消息和环境消息生成
- **ChatMessageRepository**: 持久化聊天消息,支持按时间查询和统计

### API 端点
- `GET /api/chat/messages` - 获取最近的聊天消息
- `POST /api/chat/messages` - 发送聊天消息

---

## 2. 榜单领域 (Leaderboard Domain)

### 目录结构
```
backend-ddd/mythic-realm-api/src/main/java/com/mythicrealm/api/gameplay/leaderboard/
├── LeaderboardController.java
└── LeaderboardService.java
```

### 核心职责
- **LeaderboardEntry (值对象)**: 表示榜单中的一个条目,包含玩家信息、战力、装备等
- **LeaderboardDomainService**: 从数据库构建榜单,计算排名,生成装备摘要
- **LeaderboardApplicationService**: 协调榜单查询,转换为 DTO

### API 端点
- `GET /api/leaderboard` - 获取战力榜单

### 特点
- 无需持久化仓库,直接从数据库查询构建
- 使用领域服务处理复杂的排名和战力计算逻辑
- 支持玩家和机器人的装备信息展示

---

## 3. 通告领域 (Announcement Domain)

### 目录结构
```
backend-ddd/mythic-realm-api/src/main/java/com/mythicrealm/api/gameplay/announcement/
├── AnnouncementController.java
└── AnnouncementService.java
```

### 核心职责
- **Announcement (聚合根)**: 管理全局通告的创建和优先级计算
- **AnnouncementApplicationService**: 发布各类通告(装备、强化、副本、升级)
- **AnnouncementEventListener**: 监听其他领域的事件,自动生成通告
- **AnnouncementRepository**: 持久化通告,支持清理旧通告

### API 端点
- `GET /api/announcements` - 获取最新通告

### 事件监听
监听以下跨领域事件并生成通告:
- `EquipmentEnhancedEvent` - 装备强化成功 (5级以上)
- `EquipmentEquippedEvent` - 穿戴高品质装备 (稀有及以上)
- `DungeonClearedEvent` - 通关危险副本
- `PlayerLevelUpEvent` - 玩家升级

### 通告优先级
根据事件重要性自动计算优先级:
- 强化: +10级=5, +7级=4, +5级=3, 其他=2
- 装备: 传说=5, 史诗=4, 稀有=3, 其他=2
- 副本: 极危=5, 很危=4, 危险=3, 其他=2
- 升级: 统一为1

---

## DDD 架构总结

### 分层职责

1. **Domain Layer (领域层)**
   - Model: 聚合根、实体、值对象
   - Repository: 仓库接口
   - Event: 领域事件
   - Service: 领域服务(用于无状态的领域逻辑)

2. **Application Layer (应用层)**
   - ApplicationService: 编排用例,协调领域对象
   - EventListener: 监听和处理领域事件

3. **Infrastructure Layer (基础设施层)**
   - Persistence: 仓库实现(JDBC/JPA)
   - 依赖外部系统的适配器

4. **Interfaces Layer (接口层)**
   - Controller: REST API 控制器
   - DTO: 数据传输对象

### 设计原则

- **单一职责**: 每个类只负责一个明确的职责
- **依赖倒置**: 应用层和领域层依赖抽象(Repository接口),基础设施层实现具体
- **分层隔离**: 严格的分层依赖关系(Interface → Application → Domain ← Infrastructure)
- **领域事件**: 使用事件解耦不同限界上下文之间的依赖
- **充血模型**: 领域模型包含业务逻辑,而不仅仅是数据容器

---

## 下一步

这三个领域的 DDD 架构已完成。可以继续迁移其他复杂领域:
- Quest (任务领域)
- Dungeon (副本领域)
- Market (市场领域)
- Robot (机器人领域)
