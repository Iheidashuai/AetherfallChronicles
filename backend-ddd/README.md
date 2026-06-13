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
source setjdk21.sh
mvn clean install
```

### 运行
```bash
./run.sh
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
