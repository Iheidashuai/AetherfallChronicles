# 装备强化领域 - DDD 架构详解

## 总览

本文档详细说明装备强化领域模块的 DDD 架构设计和实现。

## 架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Interface Layer                             │
│  ┌───────────────────────────┐      ┌─────────────────────────┐    │
│  │  EnhancementController    │      │   EnhanceResultDTO      │    │
│  │  - enhance()              │─────▶│   - success             │    │
│  └───────────────────────────┘      │   - previousLevel       │    │
│                                      │   - currentLevel        │    │
│                                      │   - cost                │    │
│                                      └─────────────────────────┘    │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Application Layer                            │
│  ┌───────────────────────────────────────────────────────────┐     │
│  │         EnhancementApplicationService                     │     │
│  │  - enhance(EnhanceCommand, itemLevel, gold)              │     │
│  │  - selectStrategy(targetLevel)                            │     │
│  └────┬──────────────────────────────────────────────────┬───┘     │
│       │                                                   │         │
│       │ Command                                    Events │         │
│       ▼                                                   ▼         │
│  ┌──────────────┐                              ┌──────────────────┐│
│  │EnhanceCommand│                              │ Domain Events    ││
│  │- playerId    │                              │ - Attempted      ││
│  │- itemId      │                              │ - Succeeded      ││
│  └──────────────┘                              │ - Failed         ││
│                                                 └──────────────────┘│
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          Domain Layer                                │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │              Aggregate Root: Enhancement                   │    │
│  │  - itemId                                                  │    │
│  │  - playerId                                                │    │
│  │  - level: EnhancementLevel                                 │    │
│  │  - luck: EnhancementLuck                                   │    │
│  │                                                             │    │
│  │  + attemptEnhancement(success, cost, rate)                 │    │
│  │  + calculateFailureLevelPenalty(currentLevel)              │    │
│  └────────────┬───────────────────────────────────────────────┘    │
│               │                                                     │
│               │ uses                                                │
│               ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                    Value Objects                             │  │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │  │
│  │  │EnhancementLevel  │  │EnhancementLuck   │  │Enhancement │ │  │
│  │  │ (0-15)           │  │ (0-∞)            │  │Result      │ │  │
│  │  │                  │  │                  │  │            │ │  │
│  │  │+ increment()     │  │+ increment()     │  │+ success   │ │  │
│  │  │+ decrement()     │  │+ reset()         │  │+ cost      │ │  │
│  │  │+ isSafeZone()    │  │+ calculateBonus()│  │+ rate      │ │  │
│  │  │+ isNormalZone()  │  │                  │  │            │ │  │
│  │  │+ isRiskyZone()   │  │                  │  │            │ │  │
│  │  └──────────────────┘  └──────────────────┘  └────────────┘ │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                  Domain Services                             │  │
│  │  ┌──────────────────────────────────────────────────────┐   │  │
│  │  │  EnhancementStrategy (interface)                     │   │  │
│  │  │  + calculateBaseSuccessRate(targetLevel)             │   │  │
│  │  │  + supports(targetLevel)                             │   │  │
│  │  │  + calculateFinalSuccessRate(enhancement)            │   │  │
│  │  └────────┬─────────────────┬─────────────────┬─────────┘   │  │
│  │           │                 │                 │             │  │
│  │  ┌────────▼──────┐ ┌───────▼─────┐ ┌─────────▼─────────┐   │  │
│  │  │Safe (1-6)     │ │Normal(7-12) │ │Risky (13-15)      │   │  │
│  │  │- 100%/80%     │ │- 60%/40%    │ │- 20%              │   │  │
│  │  │- 不掉级       │ │- 掉1级      │ │- 掉2级            │   │  │
│  │  └───────────────┘ └─────────────┘ └───────────────────┘   │  │
│  │                                                              │  │
│  │  ┌──────────────────────────────────────────────────────┐   │  │
│  │  │  EnhancementCostCalculator                           │   │  │
│  │  │  + calculate(itemLevel, targetLevel)                 │   │  │
│  │  │    = itemLevel² × targetLevel × 10                   │   │  │
│  │  └──────────────────────────────────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                  Repository Interface                        │  │
│  │  ┌──────────────────────────────────────────────────────┐   │  │
│  │  │  EnhancementRepository                               │   │  │
│  │  │  + findByItemId(itemId): Optional<Enhancement>       │   │  │
│  │  │  + save(enhancement): void                           │   │  │
│  │  │  + delete(itemId): void                              │   │  │
│  │  └──────────────────────────────────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Infrastructure Layer                            │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │         EnhancementRepositoryImpl                           │   │
│  │  - jdbcTemplate: JdbcTemplate                              │   │
│  │  + findByItemId(itemId)                                    │   │
│  │  + save(enhancement)                                       │   │
│  │  + delete(itemId)                                          │   │
│  │                                                             │   │
│  │  ↓ SQL                                                      │   │
│  │  UPDATE item_instance SET enhancement_level=?, luck=?      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              ▼                                       │
│                      ┌───────────────┐                               │
│                      │ item_instance │                               │
│                      │  - id         │                               │
│                      │  - player_id  │                               │
│                      │  - enh_level  │                               │
│                      │  - enh_luck   │                               │
│                      └───────────────┘                               │
└─────────────────────────────────────────────────────────────────────┘
```

## 核心设计原则

### 1. 聚合根 (Aggregate Root)

**Enhancement** 是强化的聚合根，它：
- 封装强化的核心业务规则
- 维护内部一致性
- 控制对内部对象的访问
- 发布领域事件

```java
public class Enhancement implements AggregateRoot {
    // 聚合标识
    private final long itemId;
    private final long playerId;
    
    // 聚合状态（使用值对象）
    private EnhancementLevel level;
    private EnhancementLuck luck;
    
    // 业务方法
    public EnhancementResult attemptEnhancement(...) {
        // 业务逻辑
    }
}
```

### 2. 值对象 (Value Objects)

所有领域概念都被建模为不可变值对象：

#### EnhancementLevel
- 封装等级范围验证（0-15）
- 提供等级操作（增加、减少）
- 判断所在区间（安全/普通/危险）

#### EnhancementLuck
- 封装幸运值逻辑
- 计算成功率加成
- 不可变且可替换

#### EnhancementResult
- 封装强化结果的所有信息
- 提供便捷的查询方法

### 3. 策略模式 (Strategy Pattern)

不同强化等级使用不同策略：

```
1-6级   → SafeEnhancementStrategy   (安全区)
7-12级  → NormalEnhancementStrategy (普通区)
13-15级 → RiskyEnhancementStrategy  (危险区)
```

每个策略封装：
- 基础成功率计算
- 支持的等级范围
- 最终成功率计算（基础 + 幸运加成）

### 4. 仓储模式 (Repository Pattern)

**EnhancementRepository** 接口定义在领域层，实现在基础设施层，实现关注点分离：

```java
// 领域层 - 接口
public interface EnhancementRepository {
    Optional<Enhancement> findByItemId(long itemId);
    void save(Enhancement enhancement);
    void delete(long itemId);
}

// 基础设施层 - 实现
@Repository
public class EnhancementRepositoryImpl implements EnhancementRepository {
    // JDBC 实现
}
```

### 5. 领域事件 (Domain Events)

强化过程发布三类事件：

1. **EnhancementAttemptedEvent**: 开始强化时
2. **EnhancementSucceededEvent**: 强化成功时
3. **EnhancementFailedEvent**: 强化失败时

事件驱动架构优势：
- 解耦业务逻辑
- 支持异步处理
- 便于扩展功能（如通知、统计、排行榜）

### 6. 应用服务 (Application Service)

**EnhancementApplicationService** 协调领域对象完成用例：

```java
@Transactional
public EnhancementResult enhance(
    EnhanceCommand command,
    int itemRequiredLevel,
    int currentGold
) {
    // 1. 获取/创建聚合
    // 2. 验证前置条件
    // 3. 计算成本
    // 4. 选择策略
    // 5. 发布尝试事件
    // 6. 执行强化
    // 7. 保存结果
    // 8. 发布结果事件
}
```

## 强化业务流程

```
用户请求强化
    │
    ▼
[验证权限和物品]
    │
    ▼
[计算成本]
    │
    ▼
[检查金币]
    │
    ▼
[选择策略] ──┬── 1-6级: SafeStrategy (100%/80%)
             ├── 7-12级: NormalStrategy (60%/40%)
             └── 13-15级: RiskyStrategy (20%)
    │
    ▼
[计算最终成功率] = 基础成功率 + (幸运值 × 5%)
    │
    ▼
[随机判定] ──┬── 成功 ──┬── 等级 +1
             │          └── 幸运值清零
             │
             └── 失败 ──┬── 1-6级: 不掉级
                        ├── 7-12级: 掉1级
                        ├── 13-15级: 掉2级
                        └── 幸运值 +1
    │
    ▼
[发布领域事件]
    │
    ▼
[返回结果]
```

## 关键业务规则

### 成功率计算

| 目标等级 | 基础成功率 | 幸运值加成 | 最终成功率 |
|---------|-----------|-----------|-----------|
| 1-3     | 100%      | +5%/点    | min(100%, base + luck×5%) |
| 4-6     | 80%       | +5%/点    | min(100%, base + luck×5%) |
| 7-9     | 60%       | +5%/点    | min(100%, base + luck×5%) |
| 10-12   | 40%       | +5%/点    | min(100%, base + luck×5%) |
| 13-15   | 20%       | +5%/点    | min(100%, base + luck×5%) |

### 失败惩罚

```java
private EnhancementLevel calculateFailureLevelPenalty(EnhancementLevel currentLevel) {
    int targetLevel = currentLevel.value() + 1;
    
    if (targetLevel <= 6) {
        return currentLevel;           // 不掉级
    }
    
    if (targetLevel <= 12) {
        return currentLevel.decrement();  // 掉1级
    }
    
    return currentLevel.decrementBy(2);   // 掉2级
}
```

### 成本公式

```
成本 = 装备等级² × 目标强化等级 × 10
```

示例：
- 10级装备 +1: 10² × 1 × 10 = 1,000 金币
- 10级装备 +5: 10² × 5 × 10 = 5,000 金币
- 20级装备 +10: 20² × 10 × 10 = 40,000 金币
- 30级装备 +15: 30² × 15 × 10 = 135,000 金币

## 数据库映射

强化信息存储在 `item_instance` 表：

```sql
CREATE TABLE item_instance (
    id BIGINT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    template_id VARCHAR(64),
    name VARCHAR(128),
    -- ... 其他装备属性 ...
    enhancement_level INT DEFAULT 0,    -- 强化等级 (0-15)
    enhancement_luck INT DEFAULT 0      -- 幸运值
);
```

仓储实现直接更新这两个字段：

```java
public void save(Enhancement enhancement) {
    jdbcTemplate.update(
        "UPDATE item_instance SET enhancement_level = ?, enhancement_luck = ? WHERE id = ?",
        enhancement.getLevelValue(),
        enhancement.getLuckValue(),
        enhancement.getItemId()
    );
}
```

## 测试策略

### 单元测试

1. **值对象测试**
   - 验证不可变性
   - 验证业务规则
   - 验证边界条件

2. **聚合根测试**
   - 测试强化成功场景
   - 测试不同区间的失败惩罚
   - 测试幸运值累积

3. **策略测试**
   - 验证成功率计算
   - 验证幸运值加成
   - 验证策略选择

4. **成本计算器测试**
   - 验证成本公式
   - 验证边界条件

### 集成测试

- 测试完整强化流程
- 测试事件发布
- 测试数据库持久化

## 扩展点

### 1. 新增强化道具

```java
public interface EnhancementItem {
    double getSuccessRateBonus();  // 成功率加成
    boolean preventsDowngrade();    // 防止掉级
}
```

### 2. 强化保护符

```java
public class ProtectedEnhancement extends Enhancement {
    private final boolean hasProtection;
    
    @Override
    protected EnhancementLevel calculateFailureLevelPenalty(...) {
        if (hasProtection) {
            return currentLevel; // 保护符防止掉级
        }
        return super.calculateFailureLevelPenalty(...);
    }
}
```

### 3. 强化历史记录

```java
public interface EnhancementHistoryRepository {
    void recordAttempt(EnhancementAttemptedEvent event);
    List<EnhancementHistory> findByPlayerId(long playerId);
}
```

### 4. 强化成就系统

监听领域事件实现：

```java
@EventListener
public void onEnhancementSucceeded(EnhancementSucceededEvent event) {
    if (event.newLevel() == 15) {
        achievementService.unlock(event.playerId(), "ENHANCEMENT_MASTER");
    }
}
```

## 与原实现对比

### 原 InventoryService.enhance()

```java
@Transactional
public EnhanceResult enhance(PlayerRecord player, long itemId) {
    // 所有逻辑混在一起
    // - 验证
    // - 计算
    // - 更新数据库
    // - 返回结果
}
```

问题：
- 业务逻辑与数据访问混合
- 难以测试
- 难以扩展
- 职责不清晰

### 新 DDD 架构

```java
// 领域层 - 纯业务逻辑
Enhancement.attemptEnhancement(...)

// 应用层 - 协调
EnhancementApplicationService.enhance(...)

// 基础设施层 - 数据访问
EnhancementRepositoryImpl.save(...)
```

优势：
- 关注点分离
- 易于测试
- 易于扩展
- 职责清晰

## 总结

本强化领域模块完整实现了 DDD 架构：

✅ **领域层**: 纯业务逻辑，无技术依赖
✅ **应用层**: 协调领域对象，实现用例
✅ **基础设施层**: 技术实现细节
✅ **接口层**: 对外暴露服务

✅ **聚合根**: Enhancement 封装强化逻辑
✅ **值对象**: 不可变、可替换
✅ **策略模式**: 灵活的成功率计算
✅ **仓储模式**: 持久化抽象
✅ **领域事件**: 解耦与扩展

代码质量：
- 清晰的职责划分
- 完整的单元测试
- 详细的文档说明
- 易于理解和维护
