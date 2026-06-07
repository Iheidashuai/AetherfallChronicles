# AetherfallChronicles DDD Maven 多模块架构重构技术方案

## 📋 文档信息

| 项目 | 信息 |
|------|------|
| **文档版本** | v1.0 |
| **创建日期** | 2026-06-07 |
| **作者** | 架构组 |
| **项目名称** | AetherfallChronicles |
| **技术栈** | Spring Boot 3.x + Maven + MySQL + Redis |

---

## 📚 目录

1. [背景与目标](#1-背景与目标)
2. [DDD 架构设计](#2-ddd-架构设计)
3. [Maven 多模块结构](#3-maven-多模块结构)
4. [分层架构详解](#4-分层架构详解)
5. [模块依赖关系](#5-模块依赖关系)
6. [领域边界划分](#6-领域边界划分)
7. [技术选型](#7-技术选型)
8. [迁移路径](#8-迁移路径)
9. [工程实施](#9-工程实施)
10. [风险与应对](#10-风险与应对)
11. [验收标准](#11-验收标准)

---

## 1. 背景与目标

### 1.1 当前架构问题

#### 🔴 单体架构问题
```
backend/
└── src/main/java/com/mythicrealm/backend/
    ├── player/          # 角色
    ├── inventory/       # 背包+装备+强化 (混在一起)
    ├── market/          # 市场+机器人逻辑 (混在一起)
    ├── dungeon/         # 副本
    └── ...
```

**存在的问题**:
1. ❌ 所有代码在一个模块,边界模糊
2. ❌ `InventoryService` 570 行,承担 3 个领域职责
3. ❌ 跨领域依赖混乱,存在循环依赖
4. ❌ 无法独立编译、测试、部署各领域
5. ❌ 新人难以理解业务边界

### 1.2 重构目标

#### ✅ 战略目标
- **业务目标**: 建立清晰的领域边界,支撑游戏业务快速迭代
- **技术目标**: 采用 DDD + Maven 多模块,实现高内聚、低耦合
- **团队目标**: 不同团队可以独立开发、测试各自的领域模块

#### ✅ 战术目标
- **模块化**: 每个领域独立为一个 Maven 模块
- **分层清晰**: 严格遵循 DDD 四层架构 (接口层、应用层、领域层、基础设施层)
- **可测试性**: 领域层可独立单元测试,不依赖数据库
- **可扩展性**: 新增功能只需修改对应领域模块

---

## 2. DDD 架构设计

### 2.1 DDD 四层架构

```
┌─────────────────────────────────────────────────────────┐
│  Interface Layer (接口层/用户界面层)                      │
│  - REST API (Controller)                                │
│  - DTO (Data Transfer Object)                           │
│  - Request/Response 转换                                 │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│  Application Layer (应用层)                              │
│  - 应用服务 (Application Service)                        │
│  - 事件发布与订阅                                         │
│  - 事务协调                                               │
│  - 权限校验                                               │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│  Domain Layer (领域层) ★核心★                            │
│  - 聚合根 (Aggregate Root)                               │
│  - 实体 (Entity)                                         │
│  - 值对象 (Value Object)                                 │
│  - 领域服务 (Domain Service)                             │
│  - 领域事件 (Domain Event)                               │
│  - 仓储接口 (Repository Interface)                       │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│  Infrastructure Layer (基础设施层)                       │
│  - 仓储实现 (Repository Implementation)                  │
│  - 数据库访问 (JPA/MyBatis/JdbcTemplate)                 │
│  - 外部服务调用                                           │
│  - 消息队列、缓存                                         │
└─────────────────────────────────────────────────────────┘
```

### 2.2 DDD 核心概念在项目中的应用

#### 聚合根 (Aggregate Root)
| 领域 | 聚合根 | 职责 |
|------|--------|------|
| 角色领域 | `Player` | 管理角色属性、等级、职业、金币 |
| 装备领域 | `Equipment` | 管理装备槽位、战力计算 |
| 背包领域 | `Inventory` | 管理背包槽位、物品存储 |
| 强化领域 | `Enhancement` | 管理强化等级、成功率、失败惩罚 |
| 副本领域 | `Dungeon` | 管理副本进度、战斗流程 |
| 市场领域 | `MarketListing` | 管理寄售订单、价格、交易 |
| 任务领域 | `Quest` | 管理任务进度、完成条件 |
| 机器人领域 | `Robot` | 管理机器人性格、决策、行为 |

#### 值对象 (Value Object)
```java
// 装备属性 (值对象)
public record EquipmentStats(
    int attack,
    int defense,
    int hp,
    int mp,
    double critRate
) {
    // 值对象是不可变的
    public EquipmentStats withAttack(int newAttack) {
        return new EquipmentStats(newAttack, defense, hp, mp, critRate);
    }
}

// 强化结果 (值对象)
public record EnhancementResult(
    int newLevel,
    int newLuck,
    boolean success
) {}

// 副本评分 (值对象)
public enum DungeonRating {
    S, A, B, C, F
}
```

#### 领域事件 (Domain Event)
```java
// 装备穿戴事件
public record EquipmentEquippedEvent(
    long playerId,
    String slotName,
    ItemId itemId,
    int powerChange
) implements DomainEvent {}

// 强化成功事件
public record EnhancementSucceededEvent(
    long playerId,
    ItemId itemId,
    int fromLevel,
    int toLevel
) implements DomainEvent {}

// 副本通关事件
public record DungeonClearedEvent(
    long playerId,
    String dungeonId,
    DungeonRating rating,
    List<ItemDrop> loot
) implements DomainEvent {}
```

---

## 3. Maven 多模块结构

### 3.1 顶层模块结构

```
aetherfall-chronicles/
├── pom.xml                          # 父 POM
├── mythic-realm-starter/            # 启动模块
├── mythic-realm-common/             # 公共模块
├── mythic-realm-domain-player/      # 角色领域
├── mythic-realm-domain-equipment/   # 装备领域
├── mythic-realm-domain-inventory/   # 背包领域
├── mythic-realm-domain-enhancement/ # 强化领域
├── mythic-realm-domain-dungeon/     # 副本领域
├── mythic-realm-domain-market/      # 市场领域
├── mythic-realm-domain-quest/       # 任务领域
├── mythic-realm-domain-robot/       # 机器人领域
├── mythic-realm-domain-chat/        # 聊天领域
├── mythic-realm-domain-leaderboard/ # 榜单领域
├── mythic-realm-infrastructure/     # 基础设施模块
└── mythic-realm-api/                # API 网关模块
```

### 3.2 根 POM 配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.mythicrealm</groupId>
    <artifactId>aetherfall-chronicles</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Aetherfall Chronicles</name>
    <description>Web 游戏 - DDD 多模块架构</description>

    <modules>
        <module>mythic-realm-common</module>
        <module>mythic-realm-infrastructure</module>
        <module>mythic-realm-domain-player</module>
        <module>mythic-realm-domain-equipment</module>
        <module>mythic-realm-domain-inventory</module>
        <module>mythic-realm-domain-enhancement</module>
        <module>mythic-realm-domain-dungeon</module>
        <module>mythic-realm-domain-market</module>
        <module>mythic-realm-domain-quest</module>
        <module>mythic-realm-domain-robot</module>
        <module>mythic-realm-domain-chat</module>
        <module>mythic-realm-domain-leaderboard</module>
        <module>mythic-realm-api</module>
        <module>mythic-realm-starter</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.3.0</spring-boot.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 内部模块版本管理 -->
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-infrastructure</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### 3.3 各模块详细结构

#### 3.3.1 公共模块 (mythic-realm-common)

```
mythic-realm-common/
├── pom.xml
└── src/main/java/com/mythicrealm/common/
    ├── domain/
    │   ├── AggregateRoot.java       # 聚合根标记接口
    │   ├── Entity.java               # 实体标记接口
    │   ├── ValueObject.java          # 值对象标记接口
    │   ├── DomainEvent.java          # 领域事件接口
    │   └── Repository.java           # 仓储接口
    ├── exception/
    │   ├── DomainException.java      # 领域异常基类
    │   ├── BusinessException.java    # 业务异常
    │   └── ResourceNotFoundException.java
    ├── event/
    │   ├── EventBus.java             # 事件总线
    │   └── EventPublisher.java       # 事件发布器
    └── util/
        ├── IdGenerator.java          # ID 生成器
        └── AssertUtils.java          # 断言工具类
```

**pom.xml**:
```xml
<project>
    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-chronicles</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mythic-realm-common</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- 只依赖最基础的库 -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### 3.3.2 基础设施模块 (mythic-realm-infrastructure)

```
mythic-realm-infrastructure/
├── pom.xml
└── src/main/java/com/mythicrealm/infrastructure/
    ├── config/
    │   ├── DataSourceConfig.java       # 数据源配置
    │   ├── RedisConfig.java            # Redis 配置
    │   └── EventBusConfig.java         # 事件总线配置
    ├── persistence/
    │   ├── BaseJdbcRepository.java     # JDBC 仓储基类
    │   └── BaseJpaRepository.java      # JPA 仓储基类
    ├── event/
    │   ├── SpringEventBus.java         # Spring 事件总线实现
    │   └── EventPublisherImpl.java     # 事件发布器实现
    ├── cache/
    │   └── RedisCacheService.java      # Redis 缓存服务
    └── security/
        └── SessionManager.java         # 会话管理
```

**pom.xml**:
```xml
<project>
    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-chronicles</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mythic-realm-infrastructure</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- 依赖公共模块 -->
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-common</artifactId>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- 数据库驱动 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- Flyway -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### 3.3.3 领域模块示例 - 角色领域 (mythic-realm-domain-player)

```
mythic-realm-domain-player/
├── pom.xml
└── src/main/java/com/mythicrealm/domain/player/
    ├── model/                          # 领域模型
    │   ├── Player.java                 # 聚合根
    │   ├── PlayerId.java               # 值对象: 角色ID
    │   ├── PlayerStats.java            # 值对象: 角色属性
    │   ├── Profession.java             # 枚举: 职业
    │   └── Experience.java             # 值对象: 经验值
    ├── repository/                     # 仓储接口 (领域层定义)
    │   └── PlayerRepository.java       
    ├── service/                        # 领域服务
    │   └── PlayerLevelUpService.java   # 升级逻辑
    ├── event/                          # 领域事件
    │   ├── PlayerCreatedEvent.java
    │   ├── PlayerLeveledUpEvent.java
    │   └── PlayerGoldChangedEvent.java
    ├── application/                    # 应用层
    │   ├── PlayerApplicationService.java
    │   ├── command/                    # 命令对象
    │   │   ├── CreatePlayerCommand.java
    │   │   └── AddExpCommand.java
    │   └── query/                      # 查询对象
    │       └── PlayerQuery.java
    ├── infrastructure/                 # 基础设施层
    │   ├── persistence/
    │   │   ├── PlayerRepositoryImpl.java
    │   │   ├── PlayerPO.java           # 持久化对象
    │   │   └── PlayerMapper.java       # MyBatis Mapper
    │   └── event/
    │       └── PlayerEventListener.java
    └── api/                            # 接口层
        ├── PlayerController.java       # REST API
        └── dto/                        # DTO
            ├── PlayerDTO.java
            ├── CreatePlayerRequest.java
            └── CreatePlayerResponse.java
```

**pom.xml**:
```xml
<project>
    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-chronicles</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mythic-realm-domain-player</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- 依赖公共模块 -->
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-common</artifactId>
        </dependency>

        <!-- 依赖基础设施 -->
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-infrastructure</artifactId>
        </dependency>

        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

#### 3.3.4 装备领域模块 (mythic-realm-domain-equipment)

```
mythic-realm-domain-equipment/
├── pom.xml
└── src/main/java/com/mythicrealm/domain/equipment/
    ├── model/
    │   ├── Equipment.java              # 聚合根
    │   ├── EquipmentSlot.java          # 实体: 装备槽
    │   ├── EquipmentStats.java         # 值对象: 装备属性
    │   ├── SlotType.java               # 枚举: 槽位类型
    │   └── CombatPower.java            # 值对象: 战力
    ├── repository/
    │   └── EquipmentRepository.java
    ├── service/
    │   ├── EquipmentDomainService.java # 装备领域服务
    │   └── CombatPowerCalculator.java  # 战力计算服务
    ├── event/
    │   ├── EquipmentEquippedEvent.java
    │   ├── EquipmentUnequippedEvent.java
    │   └── CombatPowerChangedEvent.java
    ├── application/
    │   ├── EquipmentApplicationService.java
    │   └── command/
    │       ├── EquipCommand.java
    │       └── UnequipCommand.java
    ├── infrastructure/
    │   └── persistence/
    │       ├── EquipmentRepositoryImpl.java
    │       └── EquipmentSlotPO.java
    └── api/
        └── EquipmentController.java
```

#### 3.3.5 强化领域模块 (mythic-realm-domain-enhancement)

```
mythic-realm-domain-enhancement/
├── pom.xml
└── src/main/java/com/mythicrealm/domain/enhancement/
    ├── model/
    │   ├── Enhancement.java            # 聚合根
    │   ├── EnhancementLevel.java       # 值对象: 强化等级
    │   ├── EnhancementLuck.java        # 值对象: 强化幸运值
    │   └── EnhancementResult.java      # 值对象: 强化结果
    ├── repository/
    │   └── EnhancementRepository.java
    ├── service/
    │   ├── EnhancementStrategy.java    # 强化策略接口
    │   ├── SafeEnhancementStrategy.java
    │   ├── RiskyEnhancementStrategy.java
    │   └── EnhancementCostCalculator.java
    ├── event/
    │   ├── EnhancementAttemptedEvent.java
    │   ├── EnhancementSucceededEvent.java
    │   └── EnhancementFailedEvent.java
    ├── application/
    │   └── EnhancementApplicationService.java
    └── api/
        └── EnhancementController.java
```

#### 3.3.6 机器人领域模块 (mythic-realm-domain-robot)

```
mythic-realm-domain-robot/
├── pom.xml
└── src/main/java/com/mythicrealm/domain/robot/
    ├── model/
    │   ├── Robot.java                  # 聚合根
    │   ├── RobotId.java                # 值对象
    │   ├── RobotPersonality.java       # 值对象: 性格
    │   ├── RobotStats.java             # 值对象: 统计数据
    │   └── RobotAction.java            # 值对象: 行为决策
    ├── repository/
    │   └── RobotRepository.java
    ├── service/
    │   ├── personality/                # 性格策略
    │   │   ├── PersonalityStrategy.java
    │   │   ├── AggressiveStrategy.java
    │   │   ├── CautiousStrategy.java
    │   │   └── BalancedStrategy.java
    │   ├── behavior/                   # 行为策略
    │   │   ├── DungeonBehavior.java
    │   │   ├── MarketBehavior.java
    │   │   ├── EnhanceBehavior.java
    │   │   └── ChatBehavior.java
    │   └── RobotDecisionMaker.java     # 决策引擎
    ├── event/
    │   ├── RobotWantsToDungeonEvent.java
    │   ├── RobotWantsToEnhanceEvent.java
    │   └── RobotWantsToTradeEvent.java
    ├── application/
    │   ├── RobotOrchestrator.java      # 机器人编排器
    │   └── RobotActivityScheduler.java # 定时调度器
    └── infrastructure/
        ├── eventhandler/               # 事件处理器
        │   ├── DungeonEventHandler.java
        │   ├── MarketEventHandler.java
        │   └── EnhanceEventHandler.java
        └── persistence/
            └── RobotRepositoryImpl.java
```

#### 3.3.7 API 网关模块 (mythic-realm-api)

```
mythic-realm-api/
├── pom.xml
└── src/main/java/com/mythicrealm/api/
    ├── config/
    │   ├── WebConfig.java              # Web 配置
    │   ├── SecurityConfig.java         # 安全配置
    │   └── CorsConfig.java             # CORS 配置
    ├── filter/
    │   ├── AuthenticationFilter.java   # 认证过滤器
    │   └── LoggingFilter.java          # 日志过滤器
    ├── interceptor/
    │   └── SessionInterceptor.java     # 会话拦截器
    └── exception/
        ├── GlobalExceptionHandler.java # 全局异常处理
        └── ApiErrorResponse.java       # 错误响应
```

**pom.xml**:
```xml
<project>
    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-chronicles</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mythic-realm-api</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- 依赖所有领域模块 -->
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-domain-player</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-domain-equipment</artifactId>
        </dependency>
        <!-- ... 其他领域模块 ... -->
    </dependencies>
</project>
```

#### 3.3.8 启动模块 (mythic-realm-starter)

```
mythic-realm-starter/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/mythicrealm/
    │   │   └── MythicRealmApplication.java  # 主启动类
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/              # Flyway 迁移脚本
    │           ├── V1__initial_schema.sql
    │           └── V2__add_enhancement.sql
    └── test/
        └── java/
            └── MythicRealmApplicationTests.java
```

**pom.xml**:
```xml
<project>
    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-chronicles</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mythic-realm-starter</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- 依赖 API 网关模块 -->
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-api</artifactId>
        </dependency>

        <!-- Spring Boot Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot 打包插件 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. 分层架构详解

### 4.1 接口层 (Interface Layer)

**职责**: 
- 处理 HTTP 请求/响应
- DTO 转换
- 参数校验
- API 文档

**示例代码**:
```java
package com.mythicrealm.domain.equipment.api;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {
    private final EquipmentApplicationService equipmentService;
    
    @PostMapping("/equip")
    public ApiResponse<EquipmentDTO> equip(@RequestBody EquipRequest request) {
        EquipCommand command = new EquipCommand(
            request.playerId(),
            request.itemId()
        );
        
        Equipment equipment = equipmentService.equip(command);
        
        return ApiResponse.success(EquipmentDTO.from(equipment));
    }
}

// DTO
public record EquipRequest(
    @NotNull Long playerId,
    @NotNull Long itemId
) {}

public record EquipmentDTO(
    Map<String, ItemDTO> slots,
    int combatPower
) {
    public static EquipmentDTO from(Equipment equipment) {
        return new EquipmentDTO(
            equipment.getSlots().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> ItemDTO.from(e.getValue())
                )),
            equipment.getCombatPower().value()
        );
    }
}
```

### 4.2 应用层 (Application Layer)

**职责**:
- 协调领域对象完成业务用例
- 发布领域事件
- 事务管理
- 权限校验

**示例代码**:
```java
package com.mythicrealm.domain.equipment.application;

@Service
@RequiredArgsConstructor
public class EquipmentApplicationService {
    private final EquipmentRepository equipmentRepository;
    private final InventoryRepository inventoryRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public Equipment equip(EquipCommand command) {
        // 1. 加载聚合根
        Equipment equipment = equipmentRepository.findByPlayerId(command.playerId())
            .orElseGet(() -> new Equipment(command.playerId()));
        
        Inventory inventory = inventoryRepository.findByPlayerId(command.playerId())
            .orElseThrow(() -> new DomainException("背包不存在"));
        
        // 2. 验证装备在背包中
        Item item = inventory.getItem(command.itemId())
            .orElseThrow(() -> new BusinessException("装备不在背包中"));
        
        // 3. 执行领域逻辑
        EquipmentChangedEvent event = equipment.equip(item);
        
        // 4. 从背包移除
        inventory.removeItem(command.itemId());
        
        // 5. 持久化
        equipmentRepository.save(equipment);
        inventoryRepository.save(inventory);
        
        // 6. 发布事件
        eventPublisher.publish(event);
        
        return equipment;
    }
}

// 命令对象
public record EquipCommand(long playerId, long itemId) {}
```

### 4.3 领域层 (Domain Layer)

**职责**:
- 核心业务逻辑
- 业务规则验证
- 领域模型设计

**示例代码**:
```java
package com.mythicrealm.domain.equipment.model;

/**
 * 装备聚合根
 */
public class Equipment implements AggregateRoot {
    private final long playerId;
    private final Map<SlotType, EquipmentSlot> slots;
    private CombatPower combatPower;
    
    public Equipment(long playerId) {
        this.playerId = playerId;
        this.slots = initializeSlots();
        this.combatPower = CombatPower.ZERO;
    }
    
    /**
     * 穿戴装备 - 核心领域逻辑
     */
    public EquipmentChangedEvent equip(Item item) {
        // 1. 验证装备是否可穿戴
        SlotType slotType = determineSlot(item);
        
        // 2. 获取当前槽位
        EquipmentSlot slot = slots.get(slotType);
        
        // 3. 卸下旧装备 (如果有)
        Optional<Item> oldItem = slot.getItem();
        
        // 4. 穿戴新装备
        slot.equip(item);
        
        // 5. 重新计算战力
        CombatPower oldPower = this.combatPower;
        this.combatPower = calculateCombatPower();
        
        // 6. 返回领域事件
        return new EquipmentChangedEvent(
            playerId,
            slotType,
            item.getId(),
            this.combatPower.value() - oldPower.value(),
            oldItem.map(Item::getId).orElse(null)
        );
    }
    
    /**
     * 卸下装备
     */
    public Item unequip(SlotType slotType) {
        EquipmentSlot slot = slots.get(slotType);
        Item item = slot.unequip()
            .orElseThrow(() -> new DomainException("该槽位没有装备"));
        
        this.combatPower = calculateCombatPower();
        
        return item;
    }
    
    /**
     * 计算战力 - 领域服务
     */
    private CombatPower calculateCombatPower() {
        EquipmentStats totalStats = slots.values().stream()
            .map(EquipmentSlot::getStats)
            .reduce(EquipmentStats.ZERO, EquipmentStats::add);
        
        return CombatPowerCalculator.calculate(totalStats);
    }
    
    private SlotType determineSlot(Item item) {
        return switch (item.getType()) {
            case WEAPON -> SlotType.WEAPON;
            case HELMET -> SlotType.HELMET;
            case ARMOR -> SlotType.ARMOR;
            case RING -> findAvailableRingSlot();
            default -> throw new DomainException("无法穿戴该类型物品");
        };
    }
    
    private SlotType findAvailableRingSlot() {
        if (slots.get(SlotType.RING1).isEmpty()) return SlotType.RING1;
        if (slots.get(SlotType.RING2).isEmpty()) return SlotType.RING2;
        return SlotType.RING1; // 默认替换第一个戒指
    }
    
    private Map<SlotType, EquipmentSlot> initializeSlots() {
        return Arrays.stream(SlotType.values())
            .collect(Collectors.toMap(
                type -> type,
                EquipmentSlot::new
            ));
    }
}

/**
 * 装备槽实体
 */
public class EquipmentSlot implements Entity {
    private final SlotType type;
    private Item item;
    
    public EquipmentSlot(SlotType type) {
        this.type = type;
    }
    
    public void equip(Item item) {
        validateItem(item);
        this.item = item;
    }
    
    public Optional<Item> unequip() {
        Item old = this.item;
        this.item = null;
        return Optional.ofNullable(old);
    }
    
    public boolean isEmpty() {
        return item == null;
    }
    
    public Optional<Item> getItem() {
        return Optional.ofNullable(item);
    }
    
    public EquipmentStats getStats() {
        return item != null ? item.getStats() : EquipmentStats.ZERO;
    }
    
    private void validateItem(Item item) {
        if (!type.canEquip(item.getType())) {
            throw new DomainException("该物品不能装备到" + type + "槽位");
        }
    }
}

/**
 * 战力值对象
 */
public record CombatPower(int value) implements ValueObject {
    public static final CombatPower ZERO = new CombatPower(0);
    
    public CombatPower {
        if (value < 0) {
            throw new IllegalArgumentException("战力不能为负数");
        }
    }
}
```

### 4.4 基础设施层 (Infrastructure Layer)

**职责**:
- 数据库访问
- 缓存操作
- 消息队列
- 第三方服务调用

**示例代码**:
```java
package com.mythicrealm.domain.equipment.infrastructure.persistence;

@Repository
@RequiredArgsConstructor
public class EquipmentRepositoryImpl implements EquipmentRepository {
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public Optional<Equipment> findByPlayerId(long playerId) {
        List<EquipmentSlotPO> slots = jdbcTemplate.query(
            "SELECT * FROM equipment_slot WHERE player_id = ?",
            this::mapSlotPO,
            playerId
        );
        
        if (slots.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of(toDomain(playerId, slots));
    }
    
    @Override
    public void save(Equipment equipment) {
        // 删除旧数据
        jdbcTemplate.update(
            "DELETE FROM equipment_slot WHERE player_id = ?",
            equipment.getPlayerId()
        );
        
        // 插入新数据
        for (var entry : equipment.getSlots().entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            
            jdbcTemplate.update(
                "INSERT INTO equipment_slot (player_id, slot_type, item_id) VALUES (?, ?, ?)",
                equipment.getPlayerId(),
                entry.getKey().name(),
                entry.getValue().getItem().get().getId()
            );
        }
    }
    
    private Equipment toDomain(long playerId, List<EquipmentSlotPO> slotPOs) {
        // PO -> Domain 转换逻辑
        // ...
    }
}

/**
 * 持久化对象
 */
class EquipmentSlotPO {
    private long playerId;
    private String slotType;
    private long itemId;
    // getters/setters
}
```

---

## 5. 模块依赖关系

### 5.1 依赖层次图

```
┌─────────────────────────────────────┐
│  mythic-realm-starter               │  ← 启动模块
│  (Spring Boot 主程序)                │
└──────────────┬──────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  mythic-realm-api                    │  ← API 网关
│  (全局异常处理、过滤器、拦截器)       │
└──────────────┬───────────────────────┘
               │
      ┌────────┴────────┐
      ▼                 ▼
┌──────────┐      ┌──────────┐
│  领域模块 │      │  领域模块 │  ← 各领域模块
│  player  │      │equipment │     (互不依赖)
└────┬─────┘      └────┬─────┘
     │                 │
     └────────┬────────┘
              ▼
    ┌─────────────────────┐
    │ mythic-realm-       │  ← 基础设施模块
    │ infrastructure      │
    └──────────┬──────────┘
               │
               ▼
    ┌─────────────────────┐
    │  mythic-realm-      │  ← 公共模块
    │  common             │
    └─────────────────────┘
```

### 5.2 依赖原则

#### ✅ 允许的依赖
1. **向下依赖**: 上层可以依赖下层
   - 应用层 → 领域层
   - 基础设施层 → 领域层
   - 接口层 → 应用层

2. **同层依赖**: 领域模块可以依赖公共模块
   - 任何模块 → mythic-realm-common
   - 任何模块 → mythic-realm-infrastructure

#### ❌ 禁止的依赖
1. **向上依赖**: 下层不能依赖上层
   - 领域层 ❌ 应用层
   - 领域层 ❌ 接口层

2. **领域间直接依赖**: 领域模块之间不能直接依赖
   - mythic-realm-domain-player ❌ mythic-realm-domain-equipment
   - 通过**领域事件**解耦

### 5.3 领域间通信 - 领域事件

```java
// 装备领域发布事件
@Service
public class EquipmentApplicationService {
    private final EventPublisher eventPublisher;
    
    public void equip(EquipCommand command) {
        // ... 装备逻辑
        
        // 发布事件
        eventPublisher.publish(new EquipmentChangedEvent(
            playerId,
            slotType,
            itemId,
            powerChange
        ));
    }
}

// 任务领域监听事件
@Service
public class QuestEventListener {
    private final QuestService questService;
    
    @EventListener
    public void onEquipmentChanged(EquipmentChangedEvent event) {
        // 检查是否有相关任务
        questService.checkProgress(event.playerId(), "equipItem");
    }
}

// 机器人领域监听事件
@Service
public class RobotEventListener {
    private final RobotRepository robotRepository;
    
    @EventListener
    public void onEquipmentChanged(EquipmentChangedEvent event) {
        if (!isRobot(event.playerId())) return;
        
        // 记录机器人活动
        robotRepository.logActivity(event.playerId(), "equip", "穿戴装备");
    }
}
```

---

## 6. 领域边界划分

### 6.1 核心领域 (Core Domain)

**特征**: 游戏的核心玩法,差异化竞争力

| 领域 | 模块名 | 边界描述 |
|------|--------|----------|
| **角色领域** | mythic-realm-domain-player | 管理角色的属性、等级、职业、经验、金币 |
| **装备领域** | mythic-realm-domain-equipment | 管理装备槽位、穿戴、卸下、战力计算 |
| **强化领域** | mythic-realm-domain-enhancement | 管理装备强化、成功率、幸运值、失败惩罚 |
| **副本领域** | mythic-realm-domain-dungeon | 管理副本进度、战斗流程、掉落计算 |

### 6.2 支撑领域 (Supporting Domain)

**特征**: 支撑核心玩法,但不是差异化优势

| 领域 | 模块名 | 边界描述 |
|------|--------|----------|
| **背包领域** | mythic-realm-domain-inventory | 管理背包槽位、物品存储、整理排序 |
| **市场领域** | mythic-realm-domain-market | 管理寄售订单、价格评估、交易流程 |
| **任务领域** | mythic-realm-domain-quest | 管理任务进度、完成条件、奖励发放 |

### 6.3 通用领域 (Generic Domain)

**特征**: 通用功能,可以用第三方解决方案替代

| 领域 | 模块名 | 边界描述 |
|------|--------|----------|
| **聊天领域** | mythic-realm-domain-chat | 管理聊天消息、频道、过滤 |
| **榜单领域** | mythic-realm-domain-leaderboard | 管理排行榜查询、缓存 |
| **通告领域** | mythic-realm-domain-announcement | 管理全服通告、优先级 |

### 6.4 特殊领域

| 领域 | 模块名 | 边界描述 |
|------|--------|----------|
| **机器人领域** | mythic-realm-domain-robot | 管理机器人性格、决策、行为编排 |

---

## 7. 技术选型

### 7.1 技术栈

| 分层 | 技术选型 | 说明 |
|------|----------|------|
| **接口层** | Spring MVC, Validation API | REST API, 参数校验 |
| **应用层** | Spring Transaction, Spring Events | 事务管理, 事件发布 |
| **领域层** | Pure Java, Records | 纯领域逻辑, 无框架依赖 |
| **基础设施层** | Spring JDBC Template, Flyway | 数据访问, 数据库迁移 |
| **缓存** | Spring Data Redis | 榜单、配置缓存 |
| **数据库** | MySQL 8.0 | 持久化存储 |
| **构建工具** | Maven 3.9+ | 依赖管理, 多模块构建 |
| **测试** | JUnit 5, Mockito, Testcontainers | 单元测试, 集成测试 |

### 7.2 为什么选择 JdbcTemplate 而不是 JPA?

**选择 JdbcTemplate 的理由**:
1. ✅ **性能**: 游戏场景需要极致性能,避免 ORM 额外开销
2. ✅ **控制力**: 可以精确控制 SQL,优化复杂查询
3. ✅ **简单**: 当前已使用 JdbcTemplate,迁移成本低
4. ✅ **灵活**: 支持原生 SQL,方便优化

**JPA 的劣势** (在游戏场景):
1. ❌ N+1 查询问题
2. ❌ 懒加载陷阱
3. ❌ 复杂查询性能差
4. ❌ 学习曲线陡峭

---

## 8. 迁移路径

### 8.1 迁移策略: 绞杀者模式 (Strangler Fig Pattern)

**原理**: 逐步用新架构替换旧代码,新旧并存,平滑过渡。

```
┌─────────────────────────────────────────────────┐
│  Phase 1: 新老并存                               │
│  ┌──────────┐         ┌──────────┐             │
│  │ 旧模块    │         │ 新模块    │             │
│  │ backend/ │  共存   │ domain-  │             │
│  │ player/  │ ◄────► │ player/  │             │
│  └──────────┘         └──────────┘             │
└─────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│  Phase 2: 逐步迁移                               │
│  ┌──────────┐         ┌──────────┐             │
│  │ 旧模块    │         │ 新模块    │             │
│  │ (减少)   │         │ (增加)   │             │
│  └──────────┘         └──────────┘             │
└─────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│  Phase 3: 完全迁移                               │
│                   ┌──────────┐                  │
│                   │ 新模块    │                  │
│                   │ (完整)   │                  │
│                   └──────────┘                  │
└─────────────────────────────────────────────────┘
```

### 8.2 迁移步骤

#### 阶段 1: 基础设施搭建 (1 周)

**目标**: 搭建多模块骨架,不影响现有功能

**任务清单**:
- [ ] 创建父 POM (aetherfall-chronicles)
- [ ] 创建 mythic-realm-common 模块
- [ ] 创建 mythic-realm-infrastructure 模块
- [ ] 创建 mythic-realm-starter 模块
- [ ] 配置 Maven 多模块构建
- [ ] 迁移数据库配置到 infrastructure
- [ ] 验证编译、启动无问题

**验收标准**:
```bash
# 能够成功编译
mvn clean install

# 能够启动
cd mythic-realm-starter
mvn spring-boot:run

# 现有功能正常
curl http://localhost:8080/api/health
```

#### 阶段 2: 角色领域迁移 (1 周)

**目标**: 完整迁移角色领域,作为示范

**任务清单**:
- [ ] 创建 mythic-realm-domain-player 模块
- [ ] 定义 Player 聚合根 (领域层)
  ```java
  // 新位置: mythic-realm-domain-player/src/main/java/com/mythicrealm/domain/player/model/Player.java
  public class Player implements AggregateRoot {
      private final PlayerId id;
      private PlayerStats stats;
      private Experience experience;
      // ...
  }
  ```
- [ ] 定义 PlayerRepository 接口 (领域层)
- [ ] 实现 PlayerRepositoryImpl (基础设施层)
- [ ] 实现 PlayerApplicationService (应用层)
- [ ] 实现 PlayerController (接口层)
- [ ] 定义领域事件: PlayerCreatedEvent, PlayerLeveledUpEvent
- [ ] 编写单元测试 (领域层)
- [ ] 编写集成测试 (API 层)
- [ ] 逐步切换流量到新模块
  ```java
  // 过渡期: 两个实现共存
  @Primary // 标记新实现为主实现
  @Service("newPlayerService")
  public class PlayerApplicationService { ... }
  
  @Service("oldPlayerService")
  public class OldPlayerService { ... }
  ```

**验收标准**:
- ✅ 新 API 通过所有测试
- ✅ 与旧实现行为一致
- ✅ 性能无明显下降

#### 阶段 3: 装备+背包领域迁移 (1.5 周)

**目标**: 拆分原 InventoryService,分离装备和背包

**任务清单**:
- [ ] 创建 mythic-realm-domain-equipment 模块
  - [ ] Equipment 聚合根
  - [ ] EquipmentSlot 实体
  - [ ] CombatPowerCalculator 领域服务
- [ ] 创建 mythic-realm-domain-inventory 模块
  - [ ] Inventory 聚合根
  - [ ] InventorySortStrategy 策略接口
- [ ] 定义领域事件
  - [ ] EquipmentChangedEvent
  - [ ] ItemAddedToInventoryEvent
- [ ] 实现应用服务
- [ ] 实现 REST API
- [ ] 迁移原 InventoryService 逻辑
- [ ] 测试

**关键重构**:
```java
// 旧代码 (一个 Service 干三件事)
@Service
public class InventoryService {
    public void equip(...) { ... }        // 装备
    public void addItem(...) { ... }      // 背包
    public void enhance(...) { ... }      // 强化
}

// 新代码 (三个独立领域)
@Service
public class EquipmentApplicationService {
    public Equipment equip(EquipCommand cmd) { ... }
}

@Service
public class InventoryApplicationService {
    public void addItem(AddItemCommand cmd) { ... }
}

@Service
public class EnhancementApplicationService {
    public EnhanceResult enhance(EnhanceCommand cmd) { ... }
}
```

#### 阶段 4: 强化领域迁移 (1 周)

**任务清单**:
- [ ] 创建 mythic-realm-domain-enhancement 模块
- [ ] 定义 Enhancement 聚合根
- [ ] 实现强化策略模式
  ```java
  interface EnhancementStrategy {
      EnhancementResult attempt(Enhancement enhancement);
  }
  
  class SafeEnhancementStrategy implements EnhancementStrategy { ... }
  class RiskyEnhancementStrategy implements EnhancementStrategy { ... }
  ```
- [ ] 实现应用服务
- [ ] 测试

#### 阶段 5: 副本领域迁移 (1 周)

**任务清单**:
- [ ] 创建 mythic-realm-domain-dungeon 模块
- [ ] 定义 Dungeon 聚合根
- [ ] 定义 DungeonRun 实体
- [ ] 实现战斗逻辑 (领域服务)
- [ ] 实现掉落计算 (领域服务)
- [ ] 定义 DungeonCompletedEvent
- [ ] 测试

#### 阶段 6: 市场领域迁移 (1 周)

**任务清单**:
- [ ] 创建 mythic-realm-domain-market 模块
- [ ] 定义 MarketListing 聚合根
- [ ] 定义 PricingStrategy 策略
- [ ] **关键**: 移除机器人相关逻辑
  ```java
  // 旧代码: MarketService 包含机器人逻辑
  public class MarketService {
      public void ensureRobotListings() { ... }  // ❌ 删除
      public void robotBuyListing() { ... }      // ❌ 删除
  }
  
  // 新代码: 纯市场逻辑
  public class MarketApplicationService {
      public MarketListing listItem(ListItemCommand cmd) { ... }
      public void buy(BuyCommand cmd) { ... }
  }
  ```
- [ ] 测试

#### 阶段 7: 机器人领域重构 (1.5 周)

**目标**: 建立完整的机器人领域模型

**任务清单**:
- [ ] 创建 mythic-realm-domain-robot 模块
- [ ] 定义 Robot 聚合根
  ```java
  public class Robot {
      private RobotId id;
      private RobotPersonality personality;
      private RobotStats stats;
      
      public RobotAction decideNextAction(GameContext ctx) {
          return personality.decide(this, ctx);
      }
  }
  ```
- [ ] 实现性格策略
  ```java
  interface PersonalityStrategy {
      RobotAction decide(Robot robot, GameContext ctx);
  }
  
  class AggressiveStrategy implements PersonalityStrategy { ... }
  class CautiousStrategy implements PersonalityStrategy { ... }
  ```
- [ ] 实现行为策略
  ```java
  sealed interface RobotAction permits DungeonAction, MarketAction, EnhanceAction {
      void execute(Robot robot, GameContext ctx);
  }
  ```
- [ ] 实现 RobotOrchestrator (定时调度)
- [ ] 实现事件监听器
  ```java
  @EventListener
  public void onItemDropped(ItemDroppedEvent event) {
      if (!isRobot(event.playerId())) return;
      
      Robot robot = robotRepository.find(event.playerId());
      EquipmentDecision decision = robot.decideEquipment(event.item());
      
      if (decision.shouldEquip()) {
          eventPublisher.publish(new RobotWantsToEquipEvent(...));
      }
  }
  ```
- [ ] 测试

#### 阶段 8: 其他领域迁移 (1 周)

**任务清单**:
- [ ] 迁移任务领域 (mythic-realm-domain-quest)
- [ ] 迁移聊天领域 (mythic-realm-domain-chat)
- [ ] 迁移榜单领域 (mythic-realm-domain-leaderboard)
- [ ] 迁移通告领域 (mythic-realm-domain-announcement)

#### 阶段 9: 清理与优化 (0.5 周)

**任务清单**:
- [ ] 删除旧代码 (backend/src/main/java/...)
- [ ] 更新文档
- [ ] 性能优化
- [ ] 代码审查
- [ ] 发布 v1.0

### 8.3 迁移时间线

```
Week 1:  [■■■■■■■] 基础设施搭建
Week 2:  [■■■■■■■] 角色领域迁移
Week 3:  [■■■■■■■] 装备+背包领域迁移
Week 4:  [■■■■□□□] 装备+背包领域迁移 (续)
Week 5:  [■■■■■■■] 强化领域迁移
Week 6:  [■■■■■■■] 副本领域迁移
Week 7:  [■■■■■■■] 市场领域迁移
Week 8:  [■■■■■■■] 机器人领域重构
Week 9:  [■■■■□□□] 机器人领域重构 (续)
Week 10: [■■■■■■■] 其他领域迁移
Week 11: [■■■■□□□] 清理与优化
```

**总计**: 约 **11 周** (2.5 个月)

---

## 9. 工程实施

### 9.1 代码示例 - 完整的领域模块

以角色领域为例,展示完整的实现:

#### 9.1.1 领域层

**Player.java** (聚合根):
```java
package com.mythicrealm.domain.player.model;

public class Player implements AggregateRoot {
    private final PlayerId id;
    private String name;
    private Profession profession;
    private PlayerStats stats;
    private Experience experience;
    private Gold gold;
    
    // 构造函数
    public Player(PlayerId id, String name, Profession profession) {
        this.id = id;
        this.name = validateName(name);
        this.profession = profession;
        this.stats = profession.getInitialStats();
        this.experience = Experience.ZERO;
        this.gold = Gold.of(100); // 初始金币
    }
    
    /**
     * 获得经验 - 核心领域逻辑
     */
    public List<DomainEvent> gainExperience(int exp) {
        List<DomainEvent> events = new ArrayList<>();
        
        // 添加经验
        Experience oldExp = this.experience;
        this.experience = this.experience.add(exp);
        
        // 检查是否升级
        while (canLevelUp()) {
            Level oldLevel = stats.getLevel();
            levelUp();
            Level newLevel = stats.getLevel();
            
            events.add(new PlayerLeveledUpEvent(
                id.value(),
                name,
                oldLevel.value(),
                newLevel.value(),
                stats.getFreePoints()
            ));
        }
        
        return events;
    }
    
    /**
     * 升级
     */
    private void levelUp() {
        int requiredExp = experience.getRequiredForNextLevel(stats.getLevel());
        this.experience = this.experience.subtract(requiredExp);
        this.stats = this.stats.levelUp(profession);
    }
    
    private boolean canLevelUp() {
        if (stats.getLevel().isMax()) return false;
        int required = experience.getRequiredForNextLevel(stats.getLevel());
        return experience.current() >= required;
    }
    
    /**
     * 获得金币
     */
    public void earnGold(int amount) {
        this.gold = this.gold.add(amount);
    }
    
    /**
     * 消费金币
     */
    public void spendGold(int amount) {
        if (!gold.canAfford(amount)) {
            throw new DomainException("金币不足");
        }
        this.gold = this.gold.subtract(amount);
    }
    
    private String validateName(String name) {
        if (name == null || name.trim().length() < 2 || name.trim().length() > 16) {
            throw new DomainException("角色名需要 2-16 个字符");
        }
        return name.trim();
    }
    
    // Getters
    public PlayerId getId() { return id; }
    public String getName() { return name; }
    public Profession getProfession() { return profession; }
    public PlayerStats getStats() { return stats; }
    public Experience getExperience() { return experience; }
    public Gold getGold() { return gold; }
}
```

**PlayerStats.java** (值对象):
```java
package com.mythicrealm.domain.player.model;

public record PlayerStats(
    Level level,
    int strength,
    int agility,
    int constitution,
    int intelligence,
    int spirit,
    int freePoints
) implements ValueObject {
    
    public PlayerStats levelUp(Profession profession) {
        return new PlayerStats(
            level.increment(),
            strength + 1 + profession.getStrengthBonus(),
            agility + 1 + profession.getAgilityBonus(),
            constitution + 1 + profession.getConstitutionBonus(),
            intelligence + 1 + profession.getIntelligenceBonus(),
            spirit + 1 + profession.getSpiritBonus(),
            freePoints + 3
        );
    }
    
    public int attack() {
        return strength * 2 + agility;
    }
    
    public int defense() {
        return constitution * 2 + strength;
    }
    
    public int maxHp() {
        return constitution * 20 + level.value() * 10;
    }
    
    public int maxMp() {
        return (intelligence + spirit) * 10;
    }
}
```

**Experience.java** (值对象):
```java
package com.mythicrealm.domain.player.model;

public record Experience(int current) implements ValueObject {
    public static final Experience ZERO = new Experience(0);
    
    public Experience {
        if (current < 0) {
            throw new IllegalArgumentException("经验值不能为负数");
        }
    }
    
    public Experience add(int amount) {
        return new Experience(current + Math.max(0, amount));
    }
    
    public Experience subtract(int amount) {
        return new Experience(Math.max(0, current - amount));
    }
    
    public int getRequiredForNextLevel(Level level) {
        if (level.isMax()) return 0;
        return (int) (100 * Math.pow(level.value(), 1.8));
    }
}
```

**PlayerRepository.java** (仓储接口):
```java
package com.mythicrealm.domain.player.repository;

public interface PlayerRepository {
    Optional<Player> findById(PlayerId id);
    Optional<Player> findByName(String name);
    void save(Player player);
    boolean existsByName(String name);
}
```

#### 9.1.2 应用层

**PlayerApplicationService.java**:
```java
package com.mythicrealm.domain.player.application;

@Service
@RequiredArgsConstructor
public class PlayerApplicationService {
    private final PlayerRepository playerRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public Player createPlayer(CreatePlayerCommand command) {
        // 1. 验证角色名唯一
        if (playerRepository.existsByName(command.name())) {
            throw new BusinessException("角色名已被占用");
        }
        
        // 2. 创建领域对象
        Player player = new Player(
            PlayerId.generate(),
            command.name(),
            Profession.valueOf(command.profession())
        );
        
        // 3. 持久化
        playerRepository.save(player);
        
        // 4. 发布事件
        eventPublisher.publish(new PlayerCreatedEvent(
            player.getId().value(),
            player.getName(),
            player.getProfession().name()
        ));
        
        return player;
    }
    
    @Transactional
    public Player gainExperience(GainExpCommand command) {
        // 1. 加载聚合根
        Player player = playerRepository.findById(command.playerId())
            .orElseThrow(() -> new ResourceNotFoundException("角色不存在"));
        
        // 2. 执行领域逻辑
        List<DomainEvent> events = player.gainExperience(command.exp());
        
        // 3. 持久化
        playerRepository.save(player);
        
        // 4. 发布事件
        events.forEach(eventPublisher::publish);
        
        return player;
    }
}
```

**Command 对象**:
```java
public record CreatePlayerCommand(String name, String profession) {}
public record GainExpCommand(long playerId, int exp) {}
```

#### 9.1.3 基础设施层

**PlayerRepositoryImpl.java**:
```java
package com.mythicrealm.domain.player.infrastructure.persistence;

@Repository
@RequiredArgsConstructor
public class PlayerRepositoryImpl implements PlayerRepository {
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public Optional<Player> findById(PlayerId id) {
        List<PlayerPO> pos = jdbcTemplate.query(
            "SELECT * FROM player WHERE id = ?",
            this::mapPO,
            id.value()
        );
        
        return pos.stream().findFirst().map(this::toDomain);
    }
    
    @Override
    public void save(Player player) {
        PlayerPO po = fromDomain(player);
        
        int updated = jdbcTemplate.update(
            "UPDATE player SET name=?, profession=?, level=?, experience=?, gold=?, " +
            "strength=?, agility=?, constitution=?, intelligence=?, spirit=?, free_points=? " +
            "WHERE id=?",
            po.getName(), po.getProfession(), po.getLevel(), po.getExperience(), po.getGold(),
            po.getStrength(), po.getAgility(), po.getConstitution(), 
            po.getIntelligence(), po.getSpirit(), po.getFreePoints(),
            po.getId()
        );
        
        if (updated == 0) {
            jdbcTemplate.update(
                "INSERT INTO player (id, name, profession, level, experience, gold, " +
                "strength, agility, constitution, intelligence, spirit, free_points) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                po.getId(), po.getName(), po.getProfession(), po.getLevel(), po.getExperience(),
                po.getGold(), po.getStrength(), po.getAgility(), po.getConstitution(),
                po.getIntelligence(), po.getSpirit(), po.getFreePoints()
            );
        }
    }
    
    private Player toDomain(PlayerPO po) {
        // PO -> Domain 转换
        Player player = new Player(
            PlayerId.of(po.getId()),
            po.getName(),
            Profession.valueOf(po.getProfession())
        );
        
        // 使用反射或Builder模式恢复内部状态
        // (实际项目中可能需要在 Player 中提供 reconstitute 方法)
        
        return player;
    }
    
    private PlayerPO fromDomain(Player player) {
        return new PlayerPO(
            player.getId().value(),
            player.getName(),
            player.getProfession().name(),
            player.getStats().level().value(),
            player.getExperience().current(),
            player.getGold().value(),
            player.getStats().strength(),
            player.getStats().agility(),
            player.getStats().constitution(),
            player.getStats().intelligence(),
            player.getStats().spirit(),
            player.getStats().freePoints()
        );
    }
}
```

#### 9.1.4 接口层

**PlayerController.java**:
```java
package com.mythicrealm.domain.player.api;

@RestController
@RequestMapping("/api/player")
@RequiredArgsConstructor
public class PlayerController {
    private final PlayerApplicationService playerService;
    
    @PostMapping
    public ApiResponse<PlayerDTO> createPlayer(@RequestBody @Valid CreatePlayerRequest request) {
        CreatePlayerCommand command = new CreatePlayerCommand(
            request.name(),
            request.profession()
        );
        
        Player player = playerService.createPlayer(command);
        
        return ApiResponse.success(PlayerDTO.from(player));
    }
    
    @PostMapping("/{playerId}/exp")
    public ApiResponse<PlayerDTO> gainExp(
        @PathVariable long playerId,
        @RequestBody @Valid GainExpRequest request
    ) {
        GainExpCommand command = new GainExpCommand(playerId, request.exp());
        Player player = playerService.gainExperience(command);
        return ApiResponse.success(PlayerDTO.from(player));
    }
}
```

**DTO**:
```java
public record CreatePlayerRequest(
    @NotBlank String name,
    @NotBlank String profession
) {}

public record PlayerDTO(
    long id,
    String name,
    String profession,
    int level,
    int experience,
    int gold,
    PlayerStatsDTO stats
) {
    public static PlayerDTO from(Player player) {
        return new PlayerDTO(
            player.getId().value(),
            player.getName(),
            player.getProfession().name(),
            player.getStats().level().value(),
            player.getExperience().current(),
            player.getGold().value(),
            PlayerStatsDTO.from(player.getStats())
        );
    }
}
```

### 9.2 Maven 编译与打包

```bash
# 编译所有模块
mvn clean install

# 只编译某个领域模块
cd mythic-realm-domain-player
mvn clean install

# 跳过测试编译
mvn clean install -DskipTests

# 打包可执行 JAR
cd mythic-realm-starter
mvn clean package

# 运行
java -jar target/mythic-realm-starter-1.0.0-SNAPSHOT.jar
```

### 9.3 测试策略

#### 9.3.1 领域层单元测试 (不依赖数据库)

```java
package com.mythicrealm.domain.player.model;

class PlayerTest {
    @Test
    void should_level_up_when_gain_enough_experience() {
        // Given
        Player player = new Player(
            PlayerId.generate(),
            "TestPlayer",
            Profession.WARRIOR
        );
        
        // When
        List<DomainEvent> events = player.gainExperience(150);
        
        // Then
        assertThat(player.getStats().level().value()).isEqualTo(2);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PlayerLeveledUpEvent.class);
    }
    
    @Test
    void should_throw_exception_when_spend_more_gold_than_owned() {
        // Given
        Player player = new Player(...);
        player.earnGold(50);
        
        // When & Then
        assertThatThrownBy(() -> player.spendGold(100))
            .isInstanceOf(DomainException.class)
            .hasMessage("金币不足");
    }
}
```

#### 9.3.2 应用层集成测试 (使用 Testcontainers)

```java
@SpringBootTest
@Testcontainers
class PlayerApplicationServiceIntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb");
    
    @Autowired
    private PlayerApplicationService playerService;
    
    @Test
    void should_create_player_successfully() {
        // Given
        CreatePlayerCommand command = new CreatePlayerCommand("Hero", "WARRIOR");
        
        // When
        Player player = playerService.createPlayer(command);
        
        // Then
        assertThat(player.getName()).isEqualTo("Hero");
        assertThat(player.getProfession()).isEqualTo(Profession.WARRIOR);
    }
}
```

#### 9.3.3 接口层 API 测试

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PlayerControllerTest {
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void should_create_player_via_api() {
        // Given
        CreatePlayerRequest request = new CreatePlayerRequest("Hero", "WARRIOR");
        
        // When
        ResponseEntity<ApiResponse<PlayerDTO>> response = restTemplate.postForEntity(
            "/api/player",
            request,
            new ParameterizedTypeReference<>() {}
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().name()).isEqualTo("Hero");
    }
}
```

---

## 10. 风险与应对

### 10.1 技术风险

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|----------|
| **循环依赖** | 中 | 高 | 严格禁止领域间直接依赖,使用领域事件通信 |
| **事件风暴** | 低 | 中 | 控制事件粒度,合并相似事件 |
| **性能下降** | 低 | 高 | 性能测试,优化关键路径 |
| **数据一致性** | 中 | 高 | 使用事务,保证最终一致性 |

### 10.2 组织风险

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|----------|
| **团队不熟悉 DDD** | 高 | 中 | 培训 + Code Review + 结对编程 |
| **迁移周期长** | 中 | 中 | 采用绞杀者模式,逐步迁移 |
| **业务需求变更** | 高 | 低 | 领域边界清晰,局部修改不影响全局 |

### 10.3 业务风险

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|----------|
| **功能回归** | 低 | 高 | 充分测试,灰度发布 |
| **用户体验下降** | 低 | 高 | 性能监控,及时回滚 |

---

## 11. 验收标准

### 11.1 架构验收

- [ ] ✅ 所有领域模块独立编译、测试
- [ ] ✅ 领域层不依赖任何框架 (Pure Java)
- [ ] ✅ 领域间无直接依赖,通过事件通信
- [ ] ✅ 每个聚合根有清晰的边界
- [ ] ✅ 所有业务规则在领域层实现

### 11.2 代码质量验收

- [ ] ✅ 单元测试覆盖率 ≥ 80% (领域层)
- [ ] ✅ 集成测试覆盖所有核心用例
- [ ] ✅ 代码通过 SonarQube 扫描 (0 Critical Issues)
- [ ] ✅ 所有 Public API 有文档注释

### 11.3 性能验收

- [ ] ✅ API 响应时间 P95 ≤ 200ms
- [ ] ✅ 数据库查询 P95 ≤ 50ms
- [ ] ✅ 并发 1000 用户无性能劣化

### 11.4 功能验收

- [ ] ✅ 所有现有功能正常
- [ ] ✅ 通过 E2E 测试
- [ ] ✅ 前端 H5 集成无问题

---

## 12. 总结

### 12.1 预期收益

#### 架构收益
- ✅ **清晰的边界**: 每个领域独立,职责明确
- ✅ **高内聚、低耦合**: 领域内高内聚,领域间低耦合
- ✅ **可测试性**: 领域层纯 Java,易于单元测试
- ✅ **可维护性**: 修改局部不影响全局

#### 业务收益
- ✅ **开发效率**: 新功能只需修改对应领域模块
- ✅ **团队协作**: 不同团队独立开发各自领域
- ✅ **快速迭代**: 领域边界清晰,迭代更快

#### 技术收益
- ✅ **独立部署**: 未来可按领域拆分微服务
- ✅ **性能优化**: 可针对单个领域优化
- ✅ **技术演进**: 可独立升级某个领域的技术栈

### 12.2 投入产出比

| 项目 | 投入 | 产出 |
|------|------|------|
| **时间** | 11 周 | 长期架构清晰,开发效率提升 30% |
| **人力** | 2-3 人 | 支撑 10+ 人团队独立开发 |
| **风险** | 中 | 通过绞杀者模式降低风险 |

### 12.3 后续演进路径

```
┌─────────────────┐
│ Phase 1: DDD    │  ← 当前方案
│ 多模块架构       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Phase 2: CQRS   │  ← 读写分离
│ 命令查询分离     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Phase 3:        │  ← 事件溯源
│ Event Sourcing  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Phase 4:        │  ← 微服务
│ Microservices   │
└─────────────────┘
```

---

## 附录

### 附录 A: 术语表

| 术语 | 英文 | 说明 |
|------|------|------|
| 聚合根 | Aggregate Root | 聚合的入口对象,保证聚合内部的一致性 |
| 实体 | Entity | 有唯一标识的领域对象 |
| 值对象 | Value Object | 没有唯一标识,由属性决定相等性的对象 |
| 领域服务 | Domain Service | 不属于任何实体或值对象的领域逻辑 |
| 领域事件 | Domain Event | 领域中发生的重要事件 |
| 仓储 | Repository | 负责聚合根的持久化和检索 |
| 应用服务 | Application Service | 协调领域对象完成业务用例 |
| 绞杀者模式 | Strangler Fig Pattern | 逐步替换旧系统的迁移策略 |

### 附录 B: 参考资料

- 《领域驱动设计》- Eric Evans
- 《实现领域驱动设计》- Vaughn Vernon
- 《微服务架构设计模式》- Chris Richardson
- Spring Boot 官方文档
- Maven 多模块最佳实践

---

**文档结束**
