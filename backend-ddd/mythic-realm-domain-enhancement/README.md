# Mythic Realm - 装备强化领域模块

## 概述

本模块实现了装备强化系统的完整 DDD 架构，从原有的 `InventoryService` 中拆分出强化相关逻辑，遵循领域驱动设计原则。

## 架构设计

### 1. 领域层 (Domain Layer)

#### 1.1 聚合根 (Aggregate Root)
- **Enhancement**: 强化聚合根，封装装备强化的核心业务逻辑

#### 1.2 值对象 (Value Objects)
- **EnhancementLevel**: 强化等级值对象 (0-15级)
- **EnhancementLuck**: 强化幸运值值对象
- **EnhancementResult**: 强化结果值对象

#### 1.3 领域服务 (Domain Services)
- **EnhancementStrategy**: 强化策略接口
  - **SafeEnhancementStrategy**: 安全强化策略 (1-6级)
  - **NormalEnhancementStrategy**: 普通强化策略 (7-12级)
  - **RiskyEnhancementStrategy**: 危险强化策略 (13-15级)
- **EnhancementCostCalculator**: 强化成本计算器

#### 1.4 仓储接口 (Repository Interface)
- **EnhancementRepository**: 强化仓储接口

#### 1.5 领域事件 (Domain Events)
- **EnhancementAttemptedEvent**: 强化尝试事件
- **EnhancementSucceededEvent**: 强化成功事件
- **EnhancementFailedEvent**: 强化失败事件

### 2. 应用层 (Application Layer)

- **EnhancementApplicationService**: 强化应用服务，协调领域对象完成业务用例
- **EnhanceCommand**: 强化命令对象

### 3. 基础设施层 (Infrastructure Layer)

- **EnhancementRepositoryImpl**: 仓储实现，使用 JDBC 操作数据库

### 4. 接口层 (Interface Layer)

- **EnhancementController**: REST 控制器，提供 HTTP 接口
- **EnhanceResultDTO**: 强化结果数据传输对象

## 强化规则

### 成功率

| 目标等级 | 基础成功率 | 策略 |
|---------|-----------|------|
| 1-3级   | 100%      | 安全 |
| 4-6级   | 80%       | 安全 |
| 7-9级   | 60%       | 普通 |
| 10-12级 | 40%       | 普通 |
| 13-15级 | 20%       | 危险 |

### 失败惩罚

| 目标等级 | 失败惩罚 |
|---------|---------|
| 1-6级   | 不掉级   |
| 7-12级  | 掉1级    |
| 13-15级 | 掉2级    |

### 幸运值机制

- 每次强化失败增加 1 点幸运值
- 每点幸运值增加 5% 成功率
- 强化成功后幸运值清零

### 成本公式

```
成本 = 装备等级² × 目标强化等级 × 10
```

例如：
- 10级装备强化到+5：10² × 5 × 10 = 5000 金币
- 20级装备强化到+10：20² × 10 × 10 = 40000 金币

## 使用示例

### 强化装备

```java
@Autowired
private EnhancementApplicationService enhancementApplicationService;

// 创建强化命令
EnhanceCommand command = new EnhanceCommand(playerId, itemId);

// 执行强化
EnhancementResult result = enhancementApplicationService.enhance(
    command,
    itemRequiredLevel,  // 装备需求等级
    currentGold         // 当前金币
);

// 检查结果
if (result.success()) {
    System.out.println("强化成功！从 +" + result.previousLevel().value() 
        + " 升级到 +" + result.currentLevel().value());
} else {
    System.out.println("强化失败！当前等级: +" + result.currentLevel().value() 
        + ", 幸运值: " + result.currentLuck().value());
}
```

### 监听领域事件

```java
@Component
public class EnhancementEventListener {

    @EventListener
    public void onEnhancementSucceeded(EnhancementSucceededEvent event) {
        // 强化成功后的处理逻辑
        // 例如：发送通知、记录日志、更新排行榜等
    }

    @EventListener
    public void onEnhancementFailed(EnhancementFailedEvent event) {
        // 强化失败后的处理逻辑
    }
}
```

## 与原 InventoryService 的对比

### 原 InventoryService.enhance() 方法

```java
@Transactional
public EnhanceResult enhance(PlayerRecord player, long itemId) {
    // 验证、计算、更新数据库
    // 所有逻辑混在一起
}
```

### 新 DDD 架构

- **领域层**: `Enhancement` 聚合根封装强化逻辑
- **策略模式**: 不同等级区间使用不同策略
- **值对象**: 强化等级、幸运值等都是不可变值对象
- **领域事件**: 发布事件，支持事件驱动架构
- **仓储模式**: 分离领域逻辑与数据访问
- **应用服务**: 协调领域对象完成用例

## 依赖

本模块依赖：
- `mythic-realm-common`: 通用领域基础设施
- `spring-boot-starter-web`: Web 支持
- `spring-boot-starter-jdbc`: 数据库访问

## 测试

TODO: 添加单元测试和集成测试

## 迁移指南

### 从 InventoryService 迁移到新架构

1. **引入依赖**
```xml
<dependency>
    <groupId>com.mythicrealm</groupId>
    <artifactId>mythic-realm-domain-enhancement</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

2. **更新调用代码**
```java
// 旧代码
EnhanceResult result = inventoryService.enhance(player, itemId);

// 新代码
EnhanceCommand command = new EnhanceCommand(playerId, itemId);
EnhancementResult result = enhancementApplicationService.enhance(
    command,
    item.requiredLevel(),
    player.gold()
);
```

3. **数据库表无需变更**
   - 继续使用 `item_instance` 表的 `enhancement_level` 和 `enhancement_luck` 字段

## 未来扩展

- [ ] 添加强化保护道具（防止掉级）
- [ ] 添加强化祝福道具（提高成功率）
- [ ] 添加强化历史记录
- [ ] 添加强化成就系统
- [ ] 添加强化排行榜
- [ ] 支持批量强化
- [ ] 添加强化动画效果配置

## 作者

Mythic Realm Development Team

## 版本

1.0.0-SNAPSHOT

## 日期

2026-06-07
