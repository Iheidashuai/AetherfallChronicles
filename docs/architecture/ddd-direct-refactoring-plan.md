# AetherfallChronicles DDD 直接重构方案

## 📋 文档信息

| 项目 | 信息 |
|------|------|
| **文档版本** | v1.0 |
| **创建日期** | 2026-06-07 |
| **项目类型** | 个人单机项目 |
| **重构策略** | 全量重构，一步到位 |
| **预计时间** | 2-3 周 |

---

## 🎯 重构目标

### 从单体到 DDD 多模块

**当前架构**:
```
backend/
└── src/main/java/com/mythicrealm/backend/
    ├── player/
    ├── inventory/    # 混合了背包+装备+强化
    ├── market/       # 混合了市场+机器人
    └── ...
```

**目标架构**:
```
aetherfall-chronicles/
├── pom.xml                           # 父 POM
├── mythic-realm-common/              # 公共模块
├── mythic-realm-infrastructure/      # 基础设施
├── mythic-realm-domain-player/       # 角色领域
├── mythic-realm-domain-equipment/    # 装备领域
├── mythic-realm-domain-inventory/    # 背包领域
├── mythic-realm-domain-enhancement/  # 强化领域
├── mythic-realm-domain-dungeon/      # 副本领域
├── mythic-realm-domain-market/       # 市场领域
├── mythic-realm-domain-quest/        # 任务领域
├── mythic-realm-domain-robot/        # 机器人领域
├── mythic-realm-domain-chat/         # 聊天领域
├── mythic-realm-domain-leaderboard/  # 榜单领域
├── mythic-realm-domain-announcement/ # 通告领域
├── mythic-realm-api/                 # API 网关
└── mythic-realm-starter/             # 启动模块
```

---

## 📅 重构时间表

### Week 1: 基础架构 + 核心领域

| Day | 任务 | 产出 |
|-----|------|------|
| Day 1 | 搭建 Maven 多模块骨架 | 所有模块目录、POM 配置 |
| Day 2 | 实现 common + infrastructure 模块 | 领域事件、仓储基类、配置 |
| Day 3 | 重构角色领域 | Player 聚合根完整实现 |
| Day 4 | 重构装备领域 | Equipment 聚合根完整实现 |
| Day 5 | 重构背包领域 | Inventory 聚合根完整实现 |

### Week 2: 业务领域

| Day | 任务 | 产出 |
|-----|------|------|
| Day 1 | 重构强化领域 | Enhancement 聚合根 + 策略模式 |
| Day 2 | 重构副本领域 | Dungeon 聚合根 + 战斗逻辑 |
| Day 3 | 重构市场领域 | MarketListing 聚合根 (移除机器人逻辑) |
| Day 4 | 重构任务领域 | Quest 聚合根 + 事件监听 |
| Day 5 | 重构机器人领域 | Robot 聚合根 + 性格策略 |

### Week 3: 辅助领域 + 集成测试

| Day | 任务 | 产出 |
|-----|------|------|
| Day 1 | 重构聊天、榜单、通告领域 | 三个简单领域 |
| Day 2 | 实现 API 网关 + 全局异常处理 | mythic-realm-api 模块 |
| Day 3 | 实现启动模块 + 配置文件 | mythic-realm-starter 模块 |
| Day 4 | 集成测试 + 修复 Bug | 所有功能测试通过 |
| Day 5 | 性能优化 + 文档完善 | README、API 文档 |

---

## 🏗️ 详细实施步骤

## 第一步: 创建 Maven 多模块结构

### 1.1 创建父 POM

在项目根目录创建新的结构:

```bash
cd /Users/heidashuai/IdeaProjects/AetherfallChronicles
mkdir -p aetherfall-backend
cd aetherfall-backend
```

创建 `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.mythicrealm</groupId>
    <artifactId>aetherfall-backend</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Aetherfall Chronicles Backend</name>
    <description>Web 游戏后端 - DDD 架构</description>

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
        <module>mythic-realm-domain-announcement</module>
        <module>mythic-realm-api</module>
        <module>mythic-realm-starter</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        
        <spring-boot.version>3.3.0</spring-boot.version>
        <mysql.version>8.4.0</mysql.version>
        <flyway.version>10.15.0</flyway.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 内部模块 -->
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
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-player</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-equipment</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-inventory</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-enhancement</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-dungeon</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-market</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-quest</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-robot</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-chat</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-leaderboard</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-domain-announcement</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mythicrealm</groupId>
                <artifactId>mythic-realm-api</artifactId>
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

### 1.2 创建所有子模块目录

```bash
# 创建所有模块目录
mkdir -p mythic-realm-common/src/{main,test}/java/com/mythicrealm/common
mkdir -p mythic-realm-infrastructure/src/{main,test}/java/com/mythicrealm/infrastructure
mkdir -p mythic-realm-domain-player/src/{main,test}/java/com/mythicrealm/domain/player
mkdir -p mythic-realm-domain-equipment/src/{main,test}/java/com/mythicrealm/domain/equipment
mkdir -p mythic-realm-domain-inventory/src/{main,test}/java/com/mythicrealm/domain/inventory
mkdir -p mythic-realm-domain-enhancement/src/{main,test}/java/com/mythicrealm/domain/enhancement
mkdir -p mythic-realm-domain-dungeon/src/{main,test}/java/com/mythicrealm/domain/dungeon
mkdir -p mythic-realm-domain-market/src/{main,test}/java/com/mythicrealm/domain/market
mkdir -p mythic-realm-domain-quest/src/{main,test}/java/com/mythicrealm/domain/quest
mkdir -p mythic-realm-domain-robot/src/{main,test}/java/com/mythicrealm/domain/robot
mkdir -p mythic-realm-domain-chat/src/{main,test}/java/com/mythicrealm/domain/chat
mkdir -p mythic-realm-domain-leaderboard/src/{main,test}/java/com/mythicrealm/domain/leaderboard
mkdir -p mythic-realm-domain-announcement/src/{main,test}/java/com/mythicrealm/domain/announcement
mkdir -p mythic-realm-api/src/{main,test}/java/com/mythicrealm/api
mkdir -p mythic-realm-starter/src/{main,test}/{java/com/mythicrealm,resources}

# 创建 resources 目录
mkdir -p mythic-realm-infrastructure/src/main/resources
mkdir -p mythic-realm-starter/src/main/resources/db/migration
```

---

## 第二步: 实现公共模块

### 2.1 mythic-realm-common

#### 目录结构
```
mythic-realm-common/
├── pom.xml
└── src/main/java/com/mythicrealm/common/
    ├── domain/
    │   ├── AggregateRoot.java
    │   ├── Entity.java
    │   ├── ValueObject.java
    │   ├── DomainEvent.java
    │   └── Repository.java
    ├── exception/
    │   ├── DomainException.java
    │   ├── BusinessException.java
    │   └── ResourceNotFoundException.java
    ├── event/
    │   ├── EventPublisher.java
    │   └── EventBus.java
    └── util/
        ├── IdGenerator.java
        └── Assert.java
```

#### pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-backend</artifactId>
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
    </dependencies>
</project>
```

#### 核心代码

**AggregateRoot.java**:
```java
package com.mythicrealm.common.domain;

/**
 * 聚合根标记接口
 */
public interface AggregateRoot {
    // 标记接口，表示这是一个聚合根
}
```

**Entity.java**:
```java
package com.mythicrealm.common.domain;

/**
 * 实体标记接口
 */
public interface Entity {
    // 实体有唯一标识
}
```

**ValueObject.java**:
```java
package com.mythicrealm.common.domain;

/**
 * 值对象标记接口
 * 值对象是不可变的，相等性由属性决定
 */
public interface ValueObject {
    // 标记接口
}
```

**DomainEvent.java**:
```java
package com.mythicrealm.common.domain;

import java.time.Instant;

/**
 * 领域事件基类
 */
public interface DomainEvent {
    /**
     * 事件发生时间
     */
    default Instant occurredOn() {
        return Instant.now();
    }
}
```

**EventPublisher.java**:
```java
package com.mythicrealm.common.event;

import com.mythicrealm.common.domain.DomainEvent;

/**
 * 事件发布器接口
 */
public interface EventPublisher {
    void publish(DomainEvent event);
}
```

**DomainException.java**:
```java
package com.mythicrealm.common.exception;

/**
 * 领域异常
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**BusinessException.java**:
```java
package com.mythicrealm.common.exception;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String message) {
        this("BUSINESS_ERROR", message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

**IdGenerator.java**:
```java
package com.mythicrealm.common.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ID 生成器（简单实现，生产环境建议用雪花算法）
 */
public class IdGenerator {
    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis());

    public static long nextId() {
        return counter.incrementAndGet();
    }
}
```

### 2.2 mythic-realm-infrastructure

#### 目录结构
```
mythic-realm-infrastructure/
├── pom.xml
└── src/main/java/com/mythicrealm/infrastructure/
    ├── config/
    │   ├── DataSourceConfig.java
    │   ├── RedisConfig.java
    │   └── EventBusConfig.java
    ├── event/
    │   └── SpringEventPublisher.java
    └── persistence/
        └── BaseRepository.java
```

#### pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-backend</artifactId>
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

        <!-- MySQL -->
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

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### 核心代码

**SpringEventPublisher.java**:
```java
package com.mythicrealm.infrastructure.event;

import com.mythicrealm.common.domain.DomainEvent;
import com.mythicrealm.common.event.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring 的事件发布器
 */
@Component
public class SpringEventPublisher implements EventPublisher {
    private final ApplicationEventPublisher springPublisher;

    public SpringEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        springPublisher.publishEvent(event);
    }
}
```

**DataSourceConfig.java**:
```java
package com.mythicrealm.infrastructure.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

---

## 第三步: 实现领域模块 (以角色领域为例)

### 3.1 mythic-realm-domain-player 完整实现

#### 目录结构
```
mythic-realm-domain-player/
├── pom.xml
└── src/main/java/com/mythicrealm/domain/player/
    ├── model/                       # 领域模型
    │   ├── Player.java              # 聚合根
    │   ├── PlayerId.java            # 值对象
    │   ├── PlayerStats.java         # 值对象
    │   ├── Profession.java          # 枚举
    │   ├── Level.java               # 值对象
    │   ├── Experience.java          # 值对象
    │   └── Gold.java                # 值对象
    ├── repository/                  # 仓储接口
    │   └── PlayerRepository.java
    ├── event/                       # 领域事件
    │   ├── PlayerCreatedEvent.java
    │   ├── PlayerLeveledUpEvent.java
    │   └── GoldChangedEvent.java
    ├── application/                 # 应用层
    │   ├── PlayerApplicationService.java
    │   └── command/
    │       ├── CreatePlayerCommand.java
    │       └── GainExpCommand.java
    ├── infrastructure/              # 基础设施层
    │   ├── persistence/
    │   │   ├── PlayerRepositoryImpl.java
    │   │   └── PlayerPO.java
    │   └── listener/
    │       └── PlayerEventListener.java
    └── api/                         # 接口层
        ├── PlayerController.java
        └── dto/
            ├── PlayerDTO.java
            └── CreatePlayerRequest.java
```

#### pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.mythicrealm</groupId>
        <artifactId>aetherfall-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>mythic-realm-domain-player</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- 内部依赖 -->
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-infrastructure</artifactId>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

#### 完整代码 (省略部分已在之前文档展示过的代码)

由于篇幅限制，详细代码请参考之前的 `ddd-maven-multimodule-refactoring.md` 文档中的示例。

关键点:
- ✅ **领域层纯 Java**，不依赖 Spring
- ✅ **应用层负责事务**，调用领域对象
- ✅ **基础设施层负责持久化**，实现仓储接口
- ✅ **接口层负责 HTTP**，转换 DTO

---

## 第四步: 快速创建脚本

为了加速创建所有模块，我提供一个 Shell 脚本:

### create-all-modules.sh

```bash
#!/bin/bash

BASE_DIR="aetherfall-backend"
GROUP_ID="com.mythicrealm"
VERSION="1.0.0-SNAPSHOT"

MODULES=(
    "mythic-realm-common:common"
    "mythic-realm-infrastructure:infrastructure"
    "mythic-realm-domain-player:domain/player"
    "mythic-realm-domain-equipment:domain/equipment"
    "mythic-realm-domain-inventory:domain/inventory"
    "mythic-realm-domain-enhancement:domain/enhancement"
    "mythic-realm-domain-dungeon:domain/dungeon"
    "mythic-realm-domain-market:domain/market"
    "mythic-realm-domain-quest:domain/quest"
    "mythic-realm-domain-robot:domain/robot"
    "mythic-realm-domain-chat:domain/chat"
    "mythic-realm-domain-leaderboard:domain/leaderboard"
    "mythic-realm-domain-announcement:domain/announcement"
    "mythic-realm-api:api"
    "mythic-realm-starter:starter"
)

echo "Creating Maven multi-module structure..."

# 创建每个模块
for module_info in "${MODULES[@]}"; do
    IFS=':' read -r module_name package_path <<< "$module_info"
    
    echo "Creating module: $module_name"
    
    # 创建目录结构
    mkdir -p "$BASE_DIR/$module_name/src/main/java/com/$GROUP_ID/$package_path"
    mkdir -p "$BASE_DIR/$module_name/src/test/java/com/$GROUP_ID/$package_path"
    mkdir -p "$BASE_DIR/$module_name/src/main/resources"
    
    # 创建 pom.xml
    cat > "$BASE_DIR/$module_name/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>$GROUP_ID</groupId>
        <artifactId>aetherfall-backend</artifactId>
        <version>$VERSION</version>
    </parent>

    <artifactId>$module_name</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- Add dependencies here -->
    </dependencies>
</project>
EOF

done

echo "Maven multi-module structure created successfully!"
echo "Next steps:"
echo "1. cd $BASE_DIR"
echo "2. mvn clean install"
```

### 使用方式

```bash
chmod +x create-all-modules.sh
./create-all-modules.sh
```

---

## 第五步: 数据迁移

### 5.1 迁移数据库脚本

将原来的 SQL 文件迁移到新位置:

```bash
# 复制 Flyway 迁移脚本
cp -r backend/src/main/resources/db/migration/* \
      aetherfall-backend/mythic-realm-starter/src/main/resources/db/migration/
```

### 5.2 迁移配置文件

**application.yml** (mythic-realm-starter/src/main/resources/):

```yaml
spring:
  application:
    name: mythic-realm

  datasource:
    url: jdbc:mysql://localhost:3306/mythic_realm?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

  redis:
    host: localhost
    port: 6379
    database: 0

  jackson:
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss

server:
  port: 8080

logging:
  level:
    com.mythicrealm: DEBUG
    org.springframework.jdbc: DEBUG

mythic:
  config:
    version: v1.0
```

---

## 第六步: 代码迁移策略

### 6.1 直接复制 + 重构

对于每个领域，采用以下步骤:

1. **复制原代码** 到新模块
2. **按 DDD 分层重构**:
   - Service → 拆分为 ApplicationService + DomainService
   - Entity → 拆分为 AggregateRoot + Entity + ValueObject
   - Repository 实现 → 移到 infrastructure 层
3. **提取领域事件**
4. **测试**

### 6.2 重构检查清单

对每个领域模块:

- [ ] ✅ 领域层不依赖 Spring
- [ ] ✅ 聚合根边界清晰
- [ ] ✅ 值对象是 record 且不可变
- [ ] ✅ 领域事件已定义
- [ ] ✅ 仓储接口在领域层，实现在基础设施层
- [ ] ✅ 应用服务有 @Transactional
- [ ] ✅ Controller 只负责转换 DTO

---

## 第七步: 测试与验证

### 7.1 编译测试

```bash
cd aetherfall-backend
mvn clean install
```

### 7.2 启动测试

```bash
cd mythic-realm-starter
mvn spring-boot:run
```

### 7.3 API 测试

```bash
# 创建角色
curl -X POST http://localhost:8080/api/player \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Hero",
    "profession": "WARRIOR"
  }'

# 获得经验
curl -X POST http://localhost:8080/api/player/1/exp \
  -H "Content-Type: application/json" \
  -d '{"exp": 150}'
```

---

## 第八步: 清理旧代码

```bash
# 删除旧的 backend 目录
rm -rf backend

# 重命名新目录
mv aetherfall-backend backend
```

---

## 📋 完整的 TODO 清单

### Day 1: 基础搭建
- [ ] 创建父 POM
- [ ] 创建所有子模块目录
- [ ] 实现 mythic-realm-common 模块
- [ ] 实现 mythic-realm-infrastructure 模块
- [ ] 验证编译通过

### Day 2-3: 核心领域
- [ ] 重构角色领域 (mythic-realm-domain-player)
  - [ ] Player 聚合根
  - [ ] PlayerId, PlayerStats 等值对象
  - [ ] PlayerRepository 接口
  - [ ] PlayerRepositoryImpl 实现
  - [ ] PlayerApplicationService
  - [ ] PlayerController
  - [ ] 测试

- [ ] 重构装备领域 (mythic-realm-domain-equipment)
  - [ ] Equipment 聚合根
  - [ ] EquipmentSlot 实体
  - [ ] CombatPowerCalculator
  - [ ] 仓储、应用服务、API

### Day 4: 背包领域
- [ ] 重构背包领域 (mythic-realm-domain-inventory)
  - [ ] Inventory 聚合根
  - [ ] InventorySortStrategy 策略
  - [ ] 完整实现

### Day 5: 强化领域
- [ ] 重构强化领域 (mythic-realm-domain-enhancement)
  - [ ] Enhancement 聚合根
  - [ ] EnhancementStrategy 策略接口
  - [ ] SafeEnhancementStrategy
  - [ ] RiskyEnhancementStrategy
  - [ ] 完整实现

### Day 6: 副本领域
- [ ] 重构副本领域 (mythic-realm-domain-dungeon)
  - [ ] Dungeon 聚合根
  - [ ] DungeonRun 实体
  - [ ] 战斗逻辑
  - [ ] 掉落计算

### Day 7: 市场领域
- [ ] 重构市场领域 (mythic-realm-domain-market)
  - [ ] MarketListing 聚合根
  - [ ] 移除机器人相关逻辑 (移到 robot 领域)
  - [ ] 价格策略

### Day 8: 任务领域
- [ ] 重构任务领域 (mythic-realm-domain-quest)
  - [ ] Quest 聚合根
  - [ ] QuestEventListener
  - [ ] 进度跟踪

### Day 9-10: 机器人领域
- [ ] 重构机器人领域 (mythic-realm-domain-robot)
  - [ ] Robot 聚合根
  - [ ] RobotPersonality 值对象
  - [ ] PersonalityStrategy 接口
  - [ ] AggressiveStrategy
  - [ ] CautiousStrategy
  - [ ] RobotOrchestrator
  - [ ] 事件监听器

### Day 11: 辅助领域
- [ ] 重构聊天领域 (mythic-realm-domain-chat)
- [ ] 重构榜单领域 (mythic-realm-domain-leaderboard)
- [ ] 重构通告领域 (mythic-realm-domain-announcement)

### Day 12: API 网关
- [ ] 实现 mythic-realm-api 模块
  - [ ] GlobalExceptionHandler
  - [ ] AuthenticationFilter
  - [ ] CORS 配置

### Day 13: 启动模块
- [ ] 实现 mythic-realm-starter 模块
  - [ ] MythicRealmApplication 主类
  - [ ] application.yml 配置
  - [ ] 迁移 Flyway 脚本

### Day 14: 测试
- [ ] 编译所有模块
- [ ] 启动应用
- [ ] API 测试
- [ ] 修复 Bug

### Day 15: 收尾
- [ ] 性能测试
- [ ] 优化
- [ ] 更新文档
- [ ] 清理旧代码

---

## 🎯 验收标准

### 编译
```bash
mvn clean install
# 输出: BUILD SUCCESS
```

### 启动
```bash
cd mythic-realm-starter
mvn spring-boot:run
# 输出: Started MythicRealmApplication in X seconds
```

### 功能
- [ ] 角色创建
- [ ] 装备穿戴
- [ ] 副本战斗
- [ ] 市场交易
- [ ] 机器人行为模拟
- [ ] 所有功能正常

### 架构
- [ ] 领域间无直接依赖
- [ ] 领域层纯 Java
- [ ] 事件驱动解耦
- [ ] 模块独立编译

---

## 📚 参考文件结构

最终的项目结构:

```
AetherfallChronicles/
├── backend/                          # 旧代码 (删除)
├── aetherfall-backend/               # 新架构
│   ├── pom.xml
│   ├── mythic-realm-common/
│   ├── mythic-realm-infrastructure/
│   ├── mythic-realm-domain-player/
│   ├── mythic-realm-domain-equipment/
│   ├── mythic-realm-domain-inventory/
│   ├── mythic-realm-domain-enhancement/
│   ├── mythic-realm-domain-dungeon/
│   ├── mythic-realm-domain-market/
│   ├── mythic-realm-domain-quest/
│   ├── mythic-realm-domain-robot/
│   ├── mythic-realm-domain-chat/
│   ├── mythic-realm-domain-leaderboard/
│   ├── mythic-realm-domain-announcement/
│   ├── mythic-realm-api/
│   └── mythic-realm-starter/
├── web/                              # H5 前端
├── docs/                             # 文档
└── README.md
```

---

## 🚀 开始重构

### 立即开始

1. **Fork 代码** (可选，备份保险)
   ```bash
   git checkout -b refactor/ddd-multimodule
   ```

2. **执行创建脚本**
   ```bash
   ./create-all-modules.sh
   ```

3. **开始迁移**
   - 从 Day 1 开始
   - 每天完成对应任务
   - 每个模块完成后立即测试

4. **持续验证**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

---

## 总结

这份方案是**一步到位的完整重构**，预计 **2-3 周**完成:

- ✅ **Week 1**: 基础架构 + 核心领域 (角色、装备、背包)
- ✅ **Week 2**: 业务领域 (强化、副本、市场、任务、机器人)
- ✅ **Week 3**: 辅助领域 + 集成测试 + 优化

重构完成后:
- 🎯 清晰的 DDD 四层架构
- 🎯 14 个独立的 Maven 模块
- 🎯 高内聚、低耦合
- 🎯 易于测试、维护、扩展

**现在就开始吧！** 🚀
