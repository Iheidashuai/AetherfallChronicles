# 装备领域架构文档

## 架构概览

装备领域遵循 DDD (领域驱动设计) 分层架构,将代码组织为以下层次:

```
┌─────────────────────────────────────────┐
│        接口层 (Interfaces)              │
│  EquipmentController, EquipmentDTO      │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│        应用层 (Application)             │
│  EquipmentApplicationService            │
│  EquipCommand, UnequipCommand           │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│        领域层 (Domain)                  │
│  Equipment (聚合根)                     │
│  EquipmentSlot (实体)                   │
│  EquipmentStats, CombatPower (值对象)   │
│  CombatPowerCalculator (领域服务)       │
│  EquipmentRepository (仓储接口)         │
│  领域事件                               │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      基础设施层 (Infrastructure)        │
│  EquipmentRepositoryImpl                │
│  EquipmentSlotPO                        │
└─────────────────────────────────────────┘
```

## 文件清单

### 1. 领域层 (Domain Layer)

#### 聚合根
- `Equipment.java` - 装备聚合根,管理玩家的装备槽位

#### 实体
- `EquipmentSlot.java` - 装备槽位实体

#### 值对象
- `SlotType.java` - 装备槽位类型枚举
- `EquipmentStats.java` - 装备属性值对象
- `CombatPower.java` - 战斗力值对象

#### 领域服务
- `service/CombatPowerCalculator.java` - 战斗力计算服务

#### 仓储接口
- `repository/EquipmentRepository.java` - 装备仓储接口

#### 领域事件
- `event/EquipmentEquippedEvent.java` - 装备已装备事件
- `event/EquipmentUnequippedEvent.java` - 装备已卸下事件
- `event/CombatPowerChangedEvent.java` - 战斗力变化事件

### 2. 应用层 (Application Layer)

- `application/EquipmentApplicationService.java` - 装备应用服务
- `application/command/EquipCommand.java` - 装备命令
- `application/command/UnequipCommand.java` - 卸下装备命令

### 3. 基础设施层 (Infrastructure Layer)

- `infrastructure/persistence/EquipmentRepositoryImpl.java` - 仓储实现
- `infrastructure/persistence/EquipmentSlotPO.java` - 持久化对象

### 4. 接口层 (Interfaces Layer)

- `interfaces/EquipmentController.java` - REST API 控制器
- `interfaces/dto/EquipmentDTO.java` - 数据传输对象

### 5. 配置层 (Configuration)

- `config/EquipmentConfiguration.java` - Spring 配置类

## 核心业务流程

### 装备物品流程

```
1. 用户请求装备物品
   ↓
2. EquipmentController 接收请求
   ↓
3. 创建 EquipCommand
   ↓
4. EquipmentApplicationService.equip()
   - 验证物品所有权
   - 获取物品类型
   - 获取/创建 Equipment 聚合
   - 执行 equipment.equip()
   - 保存聚合
   ↓
5. Equipment.equip() (领域逻辑)
   - 确定目标槽位
   - 替换旧装备
   - 发布装备事件
   ↓
6. 返回结果给用户
```

### 卸下装备流程

```
1. 用户请求卸下装备
   ↓
2. EquipmentController 接收请求
   ↓
3. 创建 UnequipCommand
   ↓
4. EquipmentApplicationService.unequip()
   - 获取 Equipment 聚合
   - 验证物品已装备
   - 执行 equipment.unequip()
   - 保存聚合
   ↓
5. Equipment.unequip() (领域逻辑)
   - 查找装备槽位
   - 卸下装备
   - 发布卸下事件
   ↓
6. 返回结果给用户
```

### 战斗力计算流程

```
1. 请求计算战斗力
   ↓
2. EquipmentApplicationService.calculateCombatPower()
   - 获取所有装备属性
   - 调用 CombatPowerCalculator.calculate()
   ↓
3. CombatPowerCalculator.calculate()
   - 汇总装备属性
   - 计算各项得分
   - 应用套装加成
   - 返回总战斗力
```

## DDD 战术模式应用

### 聚合 (Aggregate)

- **Equipment** 是聚合根,负责维护装备槽位的一致性
- 通过聚合根访问 EquipmentSlot 实体
- 聚合边界内的修改都通过聚合根进行

### 实体 (Entity)

- **EquipmentSlot** 是实体,有唯一标识 (槽位类型)
- 封装了装备/卸下的业务逻辑

### 值对象 (Value Object)

- **EquipmentStats** - 不可变,可替换
- **CombatPower** - 不可变,可比较
- **SlotType** - 枚举类型

### 领域服务 (Domain Service)

- **CombatPowerCalculator** - 战斗力计算是跨多个实体的复杂逻辑,适合放在领域服务中

### 仓储 (Repository)

- **EquipmentRepository** - 提供聚合的持久化和重建
- 接口在领域层,实现在基础设施层

### 领域事件 (Domain Event)

- 装备/卸下操作发布领域事件
- 解耦领域对象之间的依赖
- 支持事件溯源和审计

### 应用服务 (Application Service)

- **EquipmentApplicationService** - 协调领域对象完成用例
- 管理事务边界
- 不包含业务逻辑

## 数据库表映射

### equipment_slot 表

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| player_id | BIGINT | 玩家ID |
| slot_name | VARCHAR | 槽位名称 |
| item_id | BIGINT | 物品ID |

### item_instance 表 (共享)

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| player_id | BIGINT | 玩家ID |
| item_type | VARCHAR | 物品类型 |
| attack_bonus | INT | 攻击加成 |
| defense_bonus | INT | 防御加成 |
| hp_bonus | INT | 生命加成 |
| mp_bonus | INT | 法力加成 |
| crit_bonus | DECIMAL | 暴击加成 |
| enhancement_level | INT | 强化等级 |

## 与其他领域的集成

### 依赖关系

- **inventory 领域**: 装备/卸下时需要与背包交互 (移动物品)
- **player 领域**: 战斗力计算需要玩家属性
- **enhancement 领域**: 装备属性受强化等级影响

### 事件发布

装备领域发布的事件可以被其他领域订阅:

- `EquipmentEquippedEvent` → 背包领域移除物品
- `EquipmentUnequippedEvent` → 背包领域添加物品
- `CombatPowerChangedEvent` → 更新玩家战力排行榜

## 未来扩展点

1. **装备套装系统**: 可在 Equipment 聚合中添加套装识别和加成计算
2. **装备耐久度**: 在 EquipmentSlot 实体中添加耐久度管理
3. **装备锁定**: 防止误操作卸下重要装备
4. **装备方案**: 支持保存和切换不同的装备配置
5. **装备升级**: 装备进阶系统
6. **装备镶嵌**: 宝石镶嵌系统

## 测试建议

### 单元测试

- Equipment 聚合根的装备/卸下逻辑
- EquipmentStats 强化加成计算
- CombatPowerCalculator 战斗力计算公式
- SlotType 槽位选择逻辑

### 集成测试

- EquipmentApplicationService 完整用例
- EquipmentRepositoryImpl 数据持久化
- EquipmentController API 接口

### 领域不变量测试

- 每个槽位只能有一件装备
- 戒指槽位的特殊逻辑
- 强化等级范围限制
- 战斗力计算结果非负
