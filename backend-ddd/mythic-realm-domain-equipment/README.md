# 装备领域 (Equipment Domain)

## 概述

装备领域负责管理玩家的装备系统,包括装备穿戴、卸下、战斗力计算等核心功能。

## 领域模型

### 聚合根

- **Equipment**: 装备聚合根,管理玩家的所有装备槽位

### 实体

- **EquipmentSlot**: 装备槽位实体,表示单个装备槽位

### 值对象

- **EquipmentStats**: 装备属性值对象,包含攻击、防御、生命、法力、暴击等属性
- **CombatPower**: 战斗力值对象
- **SlotType**: 装备槽位类型枚举

### 领域服务

- **CombatPowerCalculator**: 战斗力计算服务

### 领域事件

- **EquipmentEquippedEvent**: 装备已装备事件
- **EquipmentUnequippedEvent**: 装备已卸下事件
- **CombatPowerChangedEvent**: 战斗力变化事件

## 目录结构

```
src/main/java/com/mythicrealm/domain/equipment/
├── Equipment.java                          # 聚合根
├── EquipmentSlot.java                      # 实体
├── SlotType.java                           # 枚举
├── EquipmentStats.java                     # 值对象
├── CombatPower.java                        # 值对象
├── event/                                  # 领域事件
│   ├── EquipmentEquippedEvent.java
│   ├── EquipmentUnequippedEvent.java
│   └── CombatPowerChangedEvent.java
├── service/                                # 领域服务
│   └── CombatPowerCalculator.java
├── repository/                             # 仓储接口
│   └── EquipmentRepository.java
├── application/                            # 应用层
│   ├── EquipmentApplicationService.java
│   └── command/
│       ├── EquipCommand.java
│       └── UnequipCommand.java
├── infrastructure/                         # 基础设施层
│   └── persistence/
│       ├── EquipmentRepositoryImpl.java
│       └── EquipmentSlotPO.java
├── interfaces/                             # 接口层
│   ├── EquipmentController.java
│   └── dto/
│       └── EquipmentDTO.java
└── config/                                 # 配置
    └── EquipmentConfiguration.java
```

## 核心业务规则

### 装备槽位

支持以下装备槽位:
- WEAPON (武器)
- HELMET (头盔)
- ARMOR (护甲)
- LEGS (腿部)
- BOOTS (靴子)
- GLOVES (手套)
- NECKLACE (项链)
- RING1 (戒指1)
- RING2 (戒指2)

### 装备规则

1. 每个槽位只能装备一件物品
2. 装备新物品时,如果槽位已有装备,旧装备会被替换下来
3. 戒指有两个槽位,装备时优先选择空闲槽位
4. 只能装备属于自己的物品

### 战斗力计算

战斗力由以下因素决定:
- 装备攻击加成
- 装备防御加成
- 装备生命加成
- 装备法力加成
- 装备暴击加成
- 玩家等级
- 装备槽位数量 (套装加成)

计算公式:
```
offenseScore = attack * 12
defenseScore = defense * 8
healthScore = sqrt(max(1, hp)) * 26
manaScore = sqrt(max(1, mp)) * 12
critScore = offenseScore * critRate * 0.8
levelScore = level * 45.0
slotSetBonus = 1 + min(0.10, equippedSlots * 0.008)

combatPower = (offenseScore + defenseScore + healthScore + manaScore + critScore + levelScore) * slotSetBonus
```

## API 接口

### 装备物品

```
POST /api/equipment/equip
Request: { "itemId": 123 }
Response: {
  "success": true,
  "message": "装备成功",
  "replacedItemId": 456,
  "equippedItems": { "weapon": 123, ... }
}
```

### 卸下装备

```
POST /api/equipment/unequip
Request: { "itemId": 123 }
Response: {
  "success": true,
  "message": "卸下装备成功",
  "slotName": "weapon",
  "equippedItems": { ... }
}
```

### 获取已装备物品

```
GET /api/equipment/equipped?level=10&attack=50&defense=30&maxHp=500&maxMp=100&agility=20
Response: {
  "equippedItems": { "weapon": 123, "helmet": 456, ... },
  "combatPower": 1234
}
```

## 从 InventoryService 拆分的功能

以下功能已从 `InventoryService` 拆分到装备领域:

1. **equip()** - 装备物品
2. **unequip()** - 卸下装备
3. **equippedItems()** - 获取已装备物品
4. **combatPower()** - 计算战斗力

## 依赖关系

- 依赖 `mythic-realm-common` 模块提供的领域基础接口
- 使用 Spring JDBC 进行数据持久化
- 使用 `item_instance` 表获取物品属性
- 使用 `equipment_slot` 表存储装备槽位数据

## 使用示例

```java
// 装备物品
EquipCommand command = new EquipCommand(playerId, itemId);
EquipResult result = equipmentApplicationService.equip(command);

// 卸下装备
UnequipCommand command = new UnequipCommand(playerId, itemId);
UnequipResult result = equipmentApplicationService.unequip(command);

// 获取已装备物品
Map<SlotType, Long> equippedItems = equipmentApplicationService.getEquippedItems(playerId);

// 计算战斗力
PlayerStats playerStats = new PlayerStats(level, attack, defense, maxHp, maxMp, agility);
CombatPower power = equipmentApplicationService.calculateCombatPower(playerId, playerStats);
```
