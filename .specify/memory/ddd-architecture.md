---
name: ddd-architecture
description: DDD 多模块架构设计、分层原则、领域边界划分
metadata:
  type: project
  created: 2026-06-07
---

# DDD 架构设计

## 架构原则

### 1. 领域驱动设计 (DDD)
- 以业务领域为中心组织代码
- 聚合根管理聚合内部一致性
- 值对象不可变
- 领域事件记录业务变更

### 2. 四层架构

```
┌─────────────────────────────────────────────────────────┐
│  Interface Layer (接口层)                                │
│  - REST Controller                                      │
│  - DTO                                                   │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│  Application Layer (应用层)                              │
│  - Application Service (@Transactional)                 │
│  - Command Objects                                      │
│  - Event Listeners                                      │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│  Domain Layer (领域层) ★核心★                            │
│  - Aggregate Root                                       │
│  - Entity                                               │
│  - Value Object                                         │
│  - Domain Service                                       │
│  - Domain Event                                         │
│  - Repository Interface                                 │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│  Infrastructure Layer (基础设施层)                       │
│  - Repository Implementation (JdbcTemplate)             │
│  - Persistent Object (PO)                               │
│  - External Service Integration                         │
└─────────────────────────────────────────────────────────┘
```

### 3. 依赖规则

#### ✅ 允许的依赖
- Interface → Application → Domain
- Infrastructure → Domain
- 任何层 → Common

#### ❌ 禁止的依赖
- Domain → Application (领域层不能依赖应用层)
- Domain → Infrastructure (领域层不能依赖基础设施)
- Domain Module A → Domain Module B (领域模块之间不能直接依赖)

#### 跨领域通信
通过**领域事件** (Domain Event) 通信：

```java
// 装备领域发布事件
eventPublisher.publish(new EquipmentEquippedEvent(playerId, itemId));

// 任务领域监听事件
@EventListener
void onEquipmentEquipped(EquipmentEquippedEvent event) {
    questService.recordEvent(event.playerId(), "equipItem");
}
```

---

## 领域划分

### 核心领域 (Core Domain)

**特征**: 游戏的核心玩法，差异化竞争力

| 领域 | 聚合根 | 值对象 | 领域服务 |
|------|--------|--------|----------|
| **Player** | Player | PlayerId, PlayerStats, Level, Experience, Gold, Profession | PlayerLevelUpService |
| **Equipment** | Equipment | EquipmentStats, CombatPower, SlotType | CombatPowerCalculator |
| **Enhancement** | Enhancement | EnhancementLevel, EnhancementLuck, EnhancementResult | EnhancementStrategy (Safe/Normal/Risky), EnhancementCostCalculator |
| **Dungeon** | Dungeon, DungeonRun | BattleFrame, DungeonRating | CombatEngine, LootCalculator, PressureCalculator |

### 支撑领域 (Supporting Domain)

| 领域 | 聚合根 | 值对象 | 领域服务 |
|------|--------|--------|----------|
| **Inventory** | Inventory | Item, InventorySlot | InventorySortStrategy (Quality/Level/Type) |
| **Market** | MarketListing | ItemSnapshot, Price, ListingStatus | PricingStrategy, MarketTaxCalculator |
| **Quest** | Quest, QuestProgress | QuestObjective, QuestReward, QuestStatus | QuestMatcher |

### 通用领域 (Generic Domain)

| 领域 | 聚合根 | 说明 |
|------|--------|------|
| **Chat** | ChatMessage | 世界聊天消息 |
| **Leaderboard** | LeaderboardEntry (值对象) | 排行榜查询 |
| **Announcement** | Announcement | 全服通告 |

### 特殊领域

| 领域 | 聚合根 | 特点 |
|------|--------|------|
| **Robot** | Robot | 性格策略 (Aggressive/Cautious/Balanced) + 行为策略 (Dungeon/Enhance/Market/Chat) |

---

## 技术决策

### 为什么用 JdbcTemplate 而不是 JPA?

| 因素 | JdbcTemplate | JPA/Hibernate |
|------|--------------|---------------|
| **性能** | ✅ 极致性能，无 ORM 开销 | ❌ N+1 查询、懒加载陷阱 |
| **控制力** | ✅ 精确控制 SQL | ❌ 复杂查询性能差 |
| **简单性** | ✅ 学习成本低 | ❌ 学习曲线陡峭 |
| **灵活性** | ✅ 支持原生 SQL | ❌ HQL/JPQL 限制多 |

**结论**: 游戏场景追求极致性能，JdbcTemplate 是最佳选择。

### 为什么用领域事件而不是直接调用?

| 方式 | 优点 | 缺点 |
|------|------|------|
| **直接调用** | 简单直接 | 模块间强耦合，循环依赖 |
| **领域事件** | 解耦，易扩展 | 需要事件总线 |

**结论**: 领域事件实现高内聚低耦合，支持未来异步化。

---

## 代码规范

### 领域层

```java
// ✅ 正确：纯 Java，不依赖框架
public class Player implements AggregateRoot {
    private final PlayerId id;
    private PlayerStats stats;
    
    public List<DomainEvent> gainExperience(int exp) {
        // 业务逻辑
        return List.of(new PlayerLeveledUpEvent(...));
    }
}

// ❌ 错误：领域层依赖 Spring
public class Player {
    @Autowired
    private PlayerRepository repo; // ❌ 不能在领域层注入 Spring Bean
}
```

### 应用层

```java
// ✅ 正确：协调领域对象，发布事件
@Service
public class PlayerApplicationService {
    private final PlayerRepository repo;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public Player gainExperience(GainExpCommand cmd) {
        Player player = repo.findById(cmd.playerId());
        List<DomainEvent> events = player.gainExperience(cmd.exp());
        repo.save(player);
        events.forEach(eventPublisher::publish);
        return player;
    }
}

// ❌ 错误：应用层包含业务逻辑
@Service
public class PlayerApplicationService {
    public void gainExperience(...) {
        // ❌ 业务逻辑应该在 Player 聚合根中
        if (exp > 100) { level++; }
    }
}
```

### 基础设施层

```java
// ✅ 正确：实现仓储接口
@Repository
public class PlayerRepositoryImpl implements PlayerRepository {
    private final JdbcTemplate jdbc;
    
    public Optional<Player> findById(PlayerId id) {
        // JDBC 查询
        PlayerPO po = ...;
        return Optional.of(toDomain(po));
    }
    
    private Player toDomain(PlayerPO po) {
        // PO → Domain 转换
    }
}
```

### 接口层

```java
// ✅ 正确：只做 DTO 转换
@RestController
@RequestMapping("/api/players")
public class PlayerController {
    private final PlayerApplicationService service;
    
    @PostMapping("/{id}/exp")
    public ApiResponse<PlayerDTO> gainExp(@PathVariable long id, @RequestBody GainExpRequest req) {
        Player player = service.gainExperience(new GainExpCommand(id, req.exp()));
        return ApiResponse.success(PlayerDTO.from(player));
    }
}

// ❌ 错误：Controller 包含业务逻辑
@RestController
public class PlayerController {
    @PostMapping("/exp")
    public void gainExp(...) {
        // ❌ 业务逻辑应该在应用层或领域层
        player.setLevel(player.getLevel() + 1);
    }
}
```

---

## 事件流示例

### 场景: 玩家完成副本

```
1. DungeonApplicationService.runDungeon()
   ↓
2. Dungeon.complete() → 返回 DungeonCompletedEvent
   ↓
3. EventPublisher.publish(DungeonCompletedEvent)
   ↓
4. QuestEventListener 监听到事件
   ↓
5. QuestApplicationService.recordEvent()
   ↓
6. Quest.updateProgress() → 检查是否完成
   ↓
7. 如果完成 → QuestCompletedEvent
```

### 场景: 机器人打副本掉落装备

```
1. RobotOrchestrator.tick()
   ↓
2. Robot.decideNextAction() → 返回 DungeonAction
   ↓
3. EventPublisher.publish(RobotWantsToDungeonEvent)
   ↓
4. RobotEventListener.onRobotWantsToDungeon()
   ↓
5. DungeonApplicationService.runDungeonForRobot()
   ↓
6. 掉落装备 → ItemDroppedEvent
   ↓
7. RobotEventListener.onItemDropped()
   ↓
8. Robot.decideEquipment() → EquipmentDecision
   ↓
9a. 如果决定穿戴 → EquipmentApplicationService.equip()
9b. 如果决定卖 → MarketApplicationService.listItem()
```

---

## 模块依赖关系

```
mythic-realm-starter
  ↓ 依赖
mythic-realm-api
  ↓ 依赖
所有领域模块 (player/equipment/inventory/...)
  ↓ 依赖
mythic-realm-infrastructure
  ↓ 依赖
mythic-realm-common
```

**关键**: 领域模块之间**不能直接依赖**，只能通过事件通信。

---

## 未来演进

### Phase 1: DDD 多模块 ✅ (当前)
- 清晰的领域边界
- 事件驱动架构
- 易于测试和维护

### Phase 2: CQRS (命令查询分离)
- 读写分离
- 榜单、市场查询走只读副本
- 提升查询性能

### Phase 3: Event Sourcing (事件溯源)
- 保存所有领域事件
- 可回溯历史状态
- 支持审计和调试

### Phase 4: Microservices (微服务)
- 按领域拆分微服务
- 独立部署、扩缩容
- 服务间通过消息队列通信

---

## Why DDD?

| 维度 | 传统分层架构 | DDD 架构 |
|------|-------------|----------|
| **业务理解** | 技术术语主导 | 业务语言主导 |
| **代码组织** | 按技术分层 | 按业务领域 |
| **可维护性** | 业务逻辑分散 | 业务逻辑集中在领域层 |
| **可扩展性** | 修改影响面大 | 修改局限在单个领域 |
| **团队协作** | 需要了解全局 | 可独立开发各领域 |
| **测试** | 依赖数据库 | 领域层纯 Java 易测试 |

**结论**: DDD 让代码更贴近业务，更易理解、维护、扩展。
