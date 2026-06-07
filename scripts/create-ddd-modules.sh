#!/bin/bash

# ========================================
# DDD Maven 多模块架构自动创建脚本
# ========================================

set -e

BASE_DIR="backend-ddd"
GROUP_ID="com.mythicrealm"
VERSION="1.0.0-SNAPSHOT"
PARENT_VERSION="3.3.0"

echo "🚀 开始创建 DDD Maven 多模块架构..."

# 创建基础目录
echo "📁 创建基础目录..."
mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

# ========================================
# 1. 创建父 POM
# ========================================
echo "📝 创建父 POM..."
cat > pom.xml <<'EOF'
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
    </properties>

    <dependencyManagement>
        <dependencies>
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
EOF

# ========================================
# 2. 创建模块函数
# ========================================
create_module() {
    local module_name=$1
    local package_path=$2

    echo "  📦 创建模块: $module_name"

    # 创建目录结构
    mkdir -p "$module_name/src/main/java/com/mythicrealm/$package_path"
    mkdir -p "$module_name/src/test/java/com/mythicrealm/$package_path"
    mkdir -p "$module_name/src/main/resources"

    # 创建基础 POM
    cat > "$module_name/pom.xml" <<EOF
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
        <!-- Dependencies will be added later -->
    </dependencies>
</project>
EOF
}

# ========================================
# 3. 创建所有模块
# ========================================
echo "📦 创建所有子模块..."

create_module "mythic-realm-common" "common"
create_module "mythic-realm-infrastructure" "infrastructure"
create_module "mythic-realm-domain-player" "domain/player"
create_module "mythic-realm-domain-equipment" "domain/equipment"
create_module "mythic-realm-domain-inventory" "domain/inventory"
create_module "mythic-realm-domain-enhancement" "domain/enhancement"
create_module "mythic-realm-domain-dungeon" "domain/dungeon"
create_module "mythic-realm-domain-market" "domain/market"
create_module "mythic-realm-domain-quest" "domain/quest"
create_module "mythic-realm-domain-robot" "domain/robot"
create_module "mythic-realm-domain-chat" "domain/chat"
create_module "mythic-realm-domain-leaderboard" "domain/leaderboard"
create_module "mythic-realm-domain-announcement" "domain/announcement"
create_module "mythic-realm-api" "api"
create_module "mythic-realm-starter" "starter"

# ========================================
# 4. 为 common 模块创建基础类
# ========================================
echo "🔨 创建 common 模块基础类..."

COMMON_BASE="mythic-realm-common/src/main/java/com/mythicrealm/common"

mkdir -p "$COMMON_BASE/domain"
mkdir -p "$COMMON_BASE/exception"
mkdir -p "$COMMON_BASE/event"
mkdir -p "$COMMON_BASE/util"

# AggregateRoot
cat > "$COMMON_BASE/domain/AggregateRoot.java" <<'EOF'
package com.mythicrealm.common.domain;

/**
 * 聚合根标记接口
 */
public interface AggregateRoot {
}
EOF

# Entity
cat > "$COMMON_BASE/domain/Entity.java" <<'EOF'
package com.mythicrealm.common.domain;

/**
 * 实体标记接口
 */
public interface Entity {
}
EOF

# ValueObject
cat > "$COMMON_BASE/domain/ValueObject.java" <<'EOF'
package com.mythicrealm.common.domain;

/**
 * 值对象标记接口
 */
public interface ValueObject {
}
EOF

# DomainEvent
cat > "$COMMON_BASE/domain/DomainEvent.java" <<'EOF'
package com.mythicrealm.common.domain;

import java.time.Instant;

/**
 * 领域事件
 */
public interface DomainEvent {
    default Instant occurredOn() {
        return Instant.now();
    }
}
EOF

# DomainException
cat > "$COMMON_BASE/exception/DomainException.java" <<'EOF'
package com.mythicrealm.common.exception;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
EOF

# BusinessException
cat > "$COMMON_BASE/exception/BusinessException.java" <<'EOF'
package com.mythicrealm.common.exception;

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
EOF

# EventPublisher
cat > "$COMMON_BASE/event/EventPublisher.java" <<'EOF'
package com.mythicrealm.common.event;

import com.mythicrealm.common.domain.DomainEvent;

public interface EventPublisher {
    void publish(DomainEvent event);
}
EOF

# IdGenerator
cat > "$COMMON_BASE/util/IdGenerator.java" <<'EOF'
package com.mythicrealm.common.util;

import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis());

    public static long nextId() {
        return counter.incrementAndGet();
    }
}
EOF

# 更新 common 模块 POM
cat > "mythic-realm-common/pom.xml" <<'EOF'
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
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
    </dependencies>
</project>
EOF

# ========================================
# 5. 为 infrastructure 模块创建基础类
# ========================================
echo "🔨 创建 infrastructure 模块基础类..."

INFRA_BASE="mythic-realm-infrastructure/src/main/java/com/mythicrealm/infrastructure"

mkdir -p "$INFRA_BASE/config"
mkdir -p "$INFRA_BASE/event"

# SpringEventPublisher
cat > "$INFRA_BASE/event/SpringEventPublisher.java" <<'EOF'
package com.mythicrealm.infrastructure.event;

import com.mythicrealm.common.domain.DomainEvent;
import com.mythicrealm.common.event.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
EOF

# DataSourceConfig
cat > "$INFRA_BASE/config/DataSourceConfig.java" <<'EOF'
package com.mythicrealm.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
EOF

# 更新 infrastructure 模块 POM
cat > "mythic-realm-infrastructure/pom.xml" <<'EOF'
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
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-common</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
EOF

# ========================================
# 6. 创建 starter 模块
# ========================================
echo "🔨 创建 starter 模块..."

STARTER_BASE="mythic-realm-starter/src/main/java/com/mythicrealm"

# 主启动类
cat > "$STARTER_BASE/MythicRealmApplication.java" <<'EOF'
package com.mythicrealm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.mythicrealm")
@EnableScheduling
public class MythicRealmApplication {
    public static void main(String[] args) {
        SpringApplication.run(MythicRealmApplication.class, args);
    }
}
EOF

# application.yml
cat > "mythic-realm-starter/src/main/resources/application.yml" <<'EOF'
spring:
  application:
    name: mythic-realm

  datasource:
    url: jdbc:mysql://localhost:3306/mythic_realm?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password:
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

mythic:
  config:
    version: v1.0
EOF

# 创建 db/migration 目录
mkdir -p "mythic-realm-starter/src/main/resources/db/migration"

# 更新 starter 模块 POM
cat > "mythic-realm-starter/pom.xml" <<'EOF'
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

    <artifactId>mythic-realm-starter</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-api</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
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
EOF

# ========================================
# 7. 创建 API 模块
# ========================================
echo "🔨 创建 API 模块..."

API_BASE="mythic-realm-api/src/main/java/com/mythicrealm/api"

mkdir -p "$API_BASE/exception"
mkdir -p "$API_BASE/config"

# GlobalExceptionHandler
cat > "$API_BASE/exception/GlobalExceptionHandler.java" <<'EOF'
package com.mythicrealm.api.exception;

import com.mythicrealm.common.exception.BusinessException;
import com.mythicrealm.common.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(DomainException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "DOMAIN_ERROR",
            "message", e.getMessage(),
            "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", e.getCode(),
            "message", e.getMessage(),
            "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error", "INTERNAL_ERROR",
            "message", e.getMessage(),
            "timestamp", Instant.now()
        ));
    }
}
EOF

# ApiResponse
cat > "$API_BASE/ApiResponse.java" <<'EOF'
package com.mythicrealm.api;

public record ApiResponse<T>(
    boolean success,
    T data,
    String message
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
EOF

# 更新 API 模块 POM
cat > "mythic-realm-api/pom.xml" <<'EOF'
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

    <artifactId>mythic-realm-api</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.mythicrealm</groupId>
            <artifactId>mythic-realm-common</artifactId>
        </dependency>

        <!-- 依赖所有领域模块 -->
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
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
EOF

# ========================================
# 8. 创建 README
# ========================================
cat > "README.md" <<'EOF'
# Aetherfall Chronicles Backend - DDD 架构

## 项目结构

```
aetherfall-backend/
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

## 快速开始

### 编译
```bash
mvn clean install
```

### 运行
```bash
cd mythic-realm-starter
mvn spring-boot:run
```

### 访问
```
http://localhost:8080
```

## DDD 架构

每个领域模块包含四层:

1. **领域层** (model/)
   - 聚合根、实体、值对象
   - 领域服务
   - 领域事件

2. **应用层** (application/)
   - 应用服务
   - 命令对象

3. **基础设施层** (infrastructure/)
   - 仓储实现
   - 事件监听器

4. **接口层** (api/)
   - REST Controller
   - DTO

## 依赖原则

- 领域层不依赖任何框架
- 领域模块之间通过事件通信
- 所有模块依赖 common 和 infrastructure
EOF

echo ""
echo "✅ DDD Maven 多模块架构创建完成！"
echo ""
echo "📂 项目位置: $BASE_DIR"
echo ""
echo "🚀 下一步:"
echo "  1. cd $BASE_DIR"
echo "  2. mvn clean install"
echo "  3. 开始迁移各领域代码"
echo ""
