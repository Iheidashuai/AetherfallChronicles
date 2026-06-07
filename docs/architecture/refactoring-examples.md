# 重构示例代码

本文档提供具体的重构代码示例,展示如何将当前架构改造为符合 DDD 原则的架构。

---

## 示例 1: 拆分 InventoryService

### 当前问题
`InventoryService` 承担了三个领域的职责,违反单一职责原则:
```java
@Service
public class InventoryService {
    // 背包管理
    public List<ItemRecord> inventoryItems(long playerId) { ... }
    public int nextFreeSlot(long playerId) { ... }
    public InventorySnapshot organize(PlayerRecord player, String sort) { ... }
    
    // 装备管理
    public InventorySnapshot equip(PlayerRecord player, long itemId) { ... }
    public InventorySnapshot unequip(PlayerRecord player, long itemId) { ... }
    public Map<String, ItemRecord> equippedItems(long playerId) { ... }
    public int combatPower(PlayerRecord player) { ... }
    
    // 强化系统
    public EnhanceResult enhance(PlayerRecord player, long itemId) { ... }
    private double baseSuccessRate(int targetLevel) { ... }
}
```

### 重构后

#### 1. 背包领域服务
```java
package com.mythicrealm.backend.inventory;

@Service
public class InventoryService {
    private final JdbcTemplate jdbcTemplate;
    private final ItemRepository itemRepository;
    
    /**
     * 获取背包物品列表
     */
    public List<ItemRecord> getInventoryItems(long playerId) {
        return jdbcTemplate.query(
            "SELECT ii.* FROM inventory_slot s " +
            "JOIN item_instance ii ON ii.id = s.item_id " +
            "WHERE s.player_id = ? ORDER BY s.slot_index",
            this::mapItem,
            playerId
        );
    }
    
    /**
     * 添加物品到背包
     */
    public void addItem(long playerId, long itemId) {
        int slot = findNextFreeSlot(playerId);
        jdbcTemplate.update(
            "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
            playerId, slot, itemId
        );
    }
    
    /**
     * 从背包移除物品
     */
    public void removeItem(long playerId, long itemId) {
        jdbcTemplate.update(
            "DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?",
            playerId, itemId
        );
    }
    
    /**
     * 整理背包
     */
    public void organize(long playerId, InventorySortStrategy strategy) {
        List<ItemRecord> items = getInventoryItems(playerId);
        List<ItemRecord> sorted = strategy.sort(items);
        
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ?", playerId);
        for (int i = 0; i < sorted.size(); i++) {
            jdbcTemplate.update(
                "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
                playerId, i, sorted.get(i).id()
            );
        }
    }
    
    private int findNextFreeSlot(long playerId) {
        Set<Integer> occupied = jdbcTemplate.queryForList(
            "SELECT slot_index FROM inventory_slot WHERE player_id = ?",
            Integer.class,
            playerId
        ).stream().collect(Collectors.toSet());
        
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!occupied.contains(i)) return i;
        }
        throw ApiException.badRequest("背包已满");
    }
}

/**
 * 背包排序策略接口
 */
interface InventorySortStrategy {
    List<ItemRecord> sort(List<ItemRecord> items);
}

/**
 * 按品质排序
 */
class QualitySortStrategy implements InventorySortStrategy {
    @Override
    public List<ItemRecord> sort(List<ItemRecord> items) {
        return items.stream()
            .sorted(Comparator.comparingInt(this::qualityRank).reversed()
                .thenComparingInt(ItemRecord::requiredLevel).reversed())
            .toList();
    }
    
    private int qualityRank(ItemRecord item) {
        return switch (item.quality()) {
            case "legendary" -> 5;
            case "epic" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            default -> 1;
        };
    }
}
```

#### 2. 装备领域服务
```java
package com.mythicrealm.backend.equipment;

@Service
public class EquipmentService {
    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 穿戴装备
     */
    @Transactional
    public EquipmentSnapshot equip(PlayerRecord player, long itemId) {
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        String targetSlot = determineSlot(item);
        
        // 检查是否在背包中
        inventoryService.getInventoryItems(player.id()).stream()
            .filter(i -> i.id() == itemId)
            .findFirst()
            .orElseThrow(() -> ApiException.badRequest("只能穿戴背包中的装备"));
        
        // 获取旧装备
        Optional<ItemRecord> oldItem = getEquippedItem(player.id(), targetSlot);
        
        // 卸下旧装备到背包
        if (oldItem.isPresent()) {
            unequipToInventory(player.id(), targetSlot);
        }
        
        // 从背包移除新装备
        inventoryService.removeItem(player.id(), itemId);
        
        // 穿戴新装备
        jdbcTemplate.update(
            "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
            player.id(), targetSlot, itemId
        );
        
        // 计算战力变化
        int oldPower = calculateCombatPower(player);
        EquipmentSnapshot snapshot = getSnapshot(player);
        int newPower = snapshot.combatPower();
        
        // 发布领域事件
        eventPublisher.publishEvent(new EquipmentChangedEvent(
            player.id(),
            targetSlot,
            item,
            newPower - oldPower
        ));
        
        return snapshot;
    }
    
    /**
     * 卸下装备
     */
    @Transactional
    public EquipmentSnapshot unequip(PlayerRecord player, long itemId) {
        String slot = findEquippedSlot(player.id(), itemId)
            .orElseThrow(() -> ApiException.badRequest("该装备未穿戴"));
        
        // 移除装备槽
        jdbcTemplate.update(
            "DELETE FROM equipment_slot WHERE player_id = ? AND item_id = ?",
            player.id(), itemId
        );
        
        // 添加到背包
        inventoryService.addItem(player.id(), itemId);
        
        return getSnapshot(player);
    }
    
    /**
     * 获取所有已穿戴装备
     */
    public Map<String, ItemRecord> getEquippedItems(long playerId) {
        return jdbcTemplate.query(
            "SELECT es.slot_name, ii.* FROM equipment_slot es " +
            "JOIN item_instance ii ON ii.id = es.item_id " +
            "WHERE es.player_id = ?",
            (rs, rowNum) -> Map.entry(rs.getString("slot_name"), mapItem(rs)),
            playerId
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    /**
     * 计算战力
     */
    public int calculateCombatPower(PlayerRecord player) {
        var equipment = getEquippedItems(player.id()).values();
        
        int equipAttack = equipment.stream().mapToInt(ItemRecord::enhancedAttackBonus).sum();
        int equipDefense = equipment.stream().mapToInt(ItemRecord::enhancedDefenseBonus).sum();
        int equipHp = equipment.stream().mapToInt(ItemRecord::enhancedHpBonus).sum();
        int equipMp = equipment.stream().mapToInt(ItemRecord::enhancedMpBonus).sum();
        double equipCrit = equipment.stream().mapToDouble(ItemRecord::enhancedCritBonus).sum();
        
        double attack = player.attack() + equipAttack;
        double defense = player.defense() + equipDefense;
        double hp = player.maxHp() + equipHp;
        double mp = player.maxMp() + equipMp;
        double critRate = Math.min(0.45, player.agility() * 0.001 + equipCrit);
        
        double offenseScore = attack * 12;
        double defenseScore = defense * 8;
        double healthScore = Math.sqrt(Math.max(1, hp)) * 26;
        double manaScore = Math.sqrt(Math.max(1, mp)) * 12;
        double critScore = offenseScore * critRate * 0.8;
        double levelScore = player.level() * 45.0;
        double slotBonus = 1 + Math.min(0.10, equipment.size() * 0.008);
        
        return (int) ((offenseScore + defenseScore + healthScore + manaScore + critScore + levelScore) * slotBonus);
    }
    
    private String determineSlot(ItemRecord item) {
        return switch (item.itemType()) {
            case "weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace" -> item.itemType();
            case "ring" -> findAvailableRingSlot(item.playerId());
            default -> throw ApiException.badRequest("该物品不可穿戴");
        };
    }
    
    private String findAvailableRingSlot(long playerId) {
        Map<String, ItemRecord> equipped = getEquippedItems(playerId);
        if (!equipped.containsKey("ring1")) return "ring1";
        if (!equipped.containsKey("ring2")) return "ring2";
        return "ring1"; // 默认替换第一个戒指槽
    }
}

/**
 * 装备变化事件
 */
public record EquipmentChangedEvent(
    long playerId,
    String slotName,
    ItemRecord newItem,
    int powerChange
) {}
```

#### 3. 强化领域服务
```java
package com.mythicrealm.backend.enhancement;

@Service
public class EnhancementService {
    private final JdbcTemplate jdbcTemplate;
    private final ItemRepository itemRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 强化装备
     */
    @Transactional
    public EnhanceResult enhance(PlayerRecord player, long itemId) {
        ItemRecord item = itemRepository.requireOwnedItem(player.id(), itemId);
        
        // 验证强化条件
        int currentLevel = item.enhancementLevel();
        if (currentLevel >= MAX_ENHANCEMENT_LEVEL) {
            throw ApiException.badRequest("装备已强化到上限");
        }
        
        int targetLevel = currentLevel + 1;
        int cost = calculateCost(item, targetLevel);
        
        if (player.gold() < cost) {
            throw ApiException.badRequest("金币不足，需要 " + cost + " 金");
        }
        
        // 计算成功率
        EnhancementStrategy strategy = EnhancementStrategyFactory.getStrategy(targetLevel);
        boolean success = strategy.attempt(item, new Random(System.nanoTime() + itemId));
        
        // 执行强化
        EnhancementResult result = success 
            ? strategy.onSuccess(item)
            : strategy.onFailure(item);
        
        // 扣除金币
        jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", cost, player.id());
        
        // 更新装备
        jdbcTemplate.update(
            "UPDATE item_instance SET enhancement_level = ?, enhancement_luck = ? WHERE id = ?",
            result.newLevel(), result.newLuck(), itemId
        );
        
        // 发布事件
        eventPublisher.publishEvent(new EnhancementCompletedEvent(
            player.id(),
            itemId,
            currentLevel,
            result.newLevel(),
            success,
            cost
        ));
        
        return new EnhanceResult(
            success,
            cost,
            strategy.calculateChance(item),
            result.newLevel()
        );
    }
    
    private int calculateCost(ItemRecord item, int targetLevel) {
        int baseCost = Math.max(1, item.requiredLevel()) * Math.max(1, item.requiredLevel());
        return baseCost * targetLevel * 10;
    }
}

/**
 * 强化策略接口
 */
interface EnhancementStrategy {
    boolean attempt(ItemRecord item, Random random);
    double calculateChance(ItemRecord item);
    EnhancementResult onSuccess(ItemRecord item);
    EnhancementResult onFailure(ItemRecord item);
}

/**
 * 低级强化策略 (1-3级, 100%成功)
 */
class SafeEnhancementStrategy implements EnhancementStrategy {
    @Override
    public boolean attempt(ItemRecord item, Random random) {
        return true; // 必定成功
    }
    
    @Override
    public double calculateChance(ItemRecord item) {
        return 1.0;
    }
    
    @Override
    public EnhancementResult onSuccess(ItemRecord item) {
        return new EnhancementResult(item.enhancementLevel() + 1, 0);
    }
    
    @Override
    public EnhancementResult onFailure(ItemRecord item) {
        throw new IllegalStateException("低级强化不应失败");
    }
}

/**
 * 高级强化策略 (13-15级, 失败掉2级)
 */
class RiskyEnhancementStrategy implements EnhancementStrategy {
    @Override
    public boolean attempt(ItemRecord item, Random random) {
        double chance = 0.2 + item.enhancementLuck() * 0.05;
        return random.nextDouble() <= chance;
    }
    
    @Override
    public double calculateChance(ItemRecord item) {
        return Math.min(1.0, 0.2 + item.enhancementLuck() * 0.05);
    }
    
    @Override
    public EnhancementResult onSuccess(ItemRecord item) {
        return new EnhancementResult(item.enhancementLevel() + 1, 0);
    }
    
    @Override
    public EnhancementResult onFailure(ItemRecord item) {
        int newLevel = Math.max(0, item.enhancementLevel() - 2);
        int newLuck = item.enhancementLuck() + 1;
        return new EnhancementResult(newLevel, newLuck);
    }
}

record EnhancementResult(int newLevel, int newLuck) {}

/**
 * 强化完成事件
 */
public record EnhancementCompletedEvent(
    long playerId,
    long itemId,
    int fromLevel,
    int toLevel,
    boolean success,
    int costGold
) {}
```

---

## 示例 2: 引入领域事件解耦

### 当前问题
`DungeonService` 直接依赖 `QuestService`:
```java
@Service
public class DungeonService {
    private final QuestService questService; // 直接依赖
    
    public DungeonRunResult runDungeon(...) {
        // ... 副本逻辑
        
        // 直接调用任务系统
        questService.recordEvent(playerId, new QuestEvent("dungeonCompleted", dungeonId, 1));
        questService.recordEvent(playerId, new QuestEvent("monsterKills", null, monstersKilled));
        
        return result;
    }
}
```

### 重构后

#### 1. 定义领域事件
```java
package com.mythicrealm.backend.dungeon.event;

/**
 * 副本完成事件
 */
public record DungeonCompletedEvent(
    long playerId,
    String playerName,
    String dungeonId,
    String dungeonName,
    boolean success,
    String rating,
    int monstersKilled,
    int expGained,
    int goldGained,
    List<ItemRecord> loot
) {
    public static DungeonCompletedEvent from(PlayerRecord player, DungeonRunResult result) {
        return new DungeonCompletedEvent(
            player.id(),
            player.name(),
            result.dungeonId(),
            result.dungeonName(),
            result.success(),
            result.rating(),
            result.monstersKilled(),
            result.expGained(),
            result.goldGained(),
            result.loot()
        );
    }
}

/**
 * 装备掉落事件
 */
public record ItemDroppedEvent(
    long playerId,
    ItemRecord item,
    String source,
    String quality
) {}
```

#### 2. 副本服务发布事件
```java
@Service
public class DungeonService {
    private final ApplicationEventPublisher eventPublisher;
    // 移除 QuestService 依赖
    
    @Transactional
    public DungeonRunResult runDungeon(AuthenticatedAccount account, String dungeonId) {
        PlayerRecord player = playerService.requireByAccount(account);
        
        // ... 副本战斗逻辑
        
        DungeonRunResult result = new DungeonRunResult(...);
        
        // 发布事件而不是直接调用
        eventPublisher.publishEvent(DungeonCompletedEvent.from(player, result));
        
        // 发布装备掉落事件
        for (ItemRecord item : result.loot()) {
            eventPublisher.publishEvent(new ItemDroppedEvent(
                player.id(),
                item,
                "dungeon:" + dungeonId,
                item.quality()
            ));
        }
        
        return result;
    }
}
```

#### 3. 任务系统监听事件
```java
@Service
public class QuestEventListener {
    private final QuestService questService;
    
    @EventListener
    @Transactional
    public void onDungeonCompleted(DungeonCompletedEvent event) {
        if (event.success()) {
            questService.recordEvent(
                event.playerId(),
                new QuestEvent("dungeonCompleted", event.dungeonId(), 1)
            );
        }
        
        if (event.monstersKilled() > 0) {
            questService.recordEvent(
                event.playerId(),
                new QuestEvent("monsterKills", null, event.monstersKilled())
            );
        }
    }
    
    @EventListener
    @Transactional
    public void onItemDropped(ItemDroppedEvent event) {
        if (isRareOrBetter(event.quality())) {
            questService.recordEvent(
                event.playerId(),
                new QuestEvent("itemQualityObtained", "rare", 1)
            );
        }
    }
    
    private boolean isRareOrBetter(String quality) {
        return Set.of("rare", "epic", "legendary").contains(quality);
    }
}
```

#### 4. 通告系统监听事件
```java
@Service
public class AnnouncementEventListener {
    private final AnnouncementService announcementService;
    
    @EventListener
    public void onItemDropped(ItemDroppedEvent event) {
        if ("legendary".equals(event.quality())) {
            announcementService.publish(
                "loot",
                event.playerId(),
                String.format("%s 获得传说装备【%s】", 
                    event.playerName(), event.item().name()),
                3
            );
        }
    }
    
    @EventListener
    public void onEnhancementCompleted(EnhancementCompletedEvent event) {
        if (event.success() && event.toLevel() >= 9) {
            announcementService.publish(
                "enhance",
                event.playerId(),
                String.format("强化成功 +%d", event.toLevel()),
                2
            );
        }
    }
}
```

---

## 示例 3: 机器人系统重构

### 当前问题
机器人逻辑分散在 `RobotActivityService` 和 `MarketService` 中,缺少领域模型抽象。

### 重构后

#### 1. 机器人聚合根
```java
package com.mythicrealm.backend.robot.domain;

/**
 * 机器人聚合根
 */
public class Robot {
    private final long id;
    private final String name;
    private final RobotPersonality personality;
    private final RobotStats stats;
    
    public Robot(long id, String name, RobotPersonality personality, RobotStats stats) {
        this.id = id;
        this.name = name;
        this.personality = personality;
        this.stats = stats;
    }
    
    /**
     * 决定下一步行为
     */
    public RobotAction decideNextAction(GameContext context) {
        return personality.decideAction(this, context);
    }
    
    /**
     * 决定是否穿戴装备
     */
    public EquipmentDecision decideEquipment(ItemRecord droppedItem, ItemRecord currentEquipment) {
        return personality.decideEquipment(this, droppedItem, currentEquipment);
    }
    
    /**
     * 决定市场寄售价格
     */
    public int decideListingPrice(ItemRecord item, int recommendedPrice) {
        return personality.decidePricing(this, item, recommendedPrice);
    }
    
    public long getId() { return id; }
    public String getName() { return name; }
    public RobotPersonality getPersonality() { return personality; }
    public RobotStats getStats() { return stats; }
}

/**
 * 机器人属性值对象
 */
public record RobotStats(
    int level,
    int power,
    int gold,
    int dungeonClears,
    int peakEnhancement,
    int legendaryLootCount
) {}
```

#### 2. 机器人性格策略
```java
/**
 * 机器人性格接口
 */
public interface RobotPersonality {
    RobotAction decideAction(Robot robot, GameContext context);
    EquipmentDecision decideEquipment(Robot robot, ItemRecord dropped, ItemRecord current);
    int decidePricing(Robot robot, ItemRecord item, int recommendedPrice);
}

/**
 * 激进型性格
 */
public class AggressivePersonality implements RobotPersonality {
    @Override
    public RobotAction decideAction(Robot robot, GameContext context) {
        // 70% 打副本, 20% 强化, 10% 闲聊
        int roll = context.random().nextInt(100);
        if (roll < 70) return new DungeonAction();
        if (roll < 90) return new EnhanceAction();
        return new ChatAction();
    }
    
    @Override
    public EquipmentDecision decideEquipment(Robot robot, ItemRecord dropped, ItemRecord current) {
        // 只要属性更好就换
        if (current == null) return EquipmentDecision.equip(dropped);
        
        int droppedScore = scoreItem(dropped);
        int currentScore = scoreItem(current);
        
        return droppedScore > currentScore 
            ? EquipmentDecision.equip(dropped)
            : EquipmentDecision.sell(dropped);
    }
    
    @Override
    public int decidePricing(Robot robot, ItemRecord item, int recommendedPrice) {
        // 激进定价: 低于推荐价,快速出售
        Random random = new Random();
        return recommendedPrice * (70 + random.nextInt(20)) / 100;
    }
    
    private int scoreItem(ItemRecord item) {
        return item.enhancedAttackBonus() + item.enhancedDefenseBonus();
    }
}

/**
 * 谨慎型性格
 */
public class CautiousPersonality implements RobotPersonality {
    @Override
    public RobotAction decideAction(Robot robot, GameContext context) {
        // 50% 打副本, 10% 强化, 40% 市场/闲聊
        int roll = context.random().nextInt(100);
        if (roll < 50) return new DungeonAction();
        if (roll < 60) return new EnhanceAction();
        if (roll < 80) return new MarketAction();
        return new ChatAction();
    }
    
    @Override
    public EquipmentDecision decideEquipment(Robot robot, ItemRecord dropped, ItemRecord current) {
        // 需要明显更好才换 (20% 提升)
        if (current == null) return EquipmentDecision.equip(dropped);
        
        int droppedScore = scoreItem(dropped);
        int currentScore = scoreItem(current);
        
        return droppedScore > currentScore * 1.2
            ? EquipmentDecision.equip(dropped)
            : EquipmentDecision.sell(dropped);
    }
    
    @Override
    public int decidePricing(Robot robot, ItemRecord item, int recommendedPrice) {
        // 谨慎定价: 接近推荐价
        Random random = new Random();
        return recommendedPrice * (90 + random.nextInt(20)) / 100;
    }
    
    private int scoreItem(ItemRecord item) {
        return item.enhancedAttackBonus() + item.enhancedDefenseBonus();
    }
}
```

#### 3. 机器人行为
```java
/**
 * 机器人行为接口
 */
public sealed interface RobotAction permits DungeonAction, EnhanceAction, MarketAction, ChatAction {
    void execute(Robot robot, GameContext context);
}

/**
 * 打副本行为
 */
public final class DungeonAction implements RobotAction {
    @Override
    public void execute(Robot robot, GameContext context) {
        context.eventPublisher().publishEvent(new RobotWantsToDungeonEvent(
            robot.getId(),
            robot.getStats().level()
        ));
    }
}

/**
 * 强化行为
 */
public final class EnhanceAction implements RobotAction {
    @Override
    public void execute(Robot robot, GameContext context) {
        context.eventPublisher().publishEvent(new RobotWantsToEnhanceEvent(
            robot.getId()
        ));
    }
}
```

#### 4. 机器人编排器
```java
@Service
public class RobotOrchestrator {
    private final RobotRepository robotRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    @Scheduled(initialDelay = 6_000, fixedDelay = 30_000)
    public void tick() {
        List<Robot> robots = robotRepository.findRandomRobots(7);
        GameContext context = new GameContext(new Random(), eventPublisher);
        
        for (Robot robot : robots) {
            RobotAction action = robot.decideNextAction(context);
            action.execute(robot, context);
        }
    }
}

/**
 * 游戏上下文
 */
public record GameContext(
    Random random,
    ApplicationEventPublisher eventPublisher
) {}
```

#### 5. 业务系统监听机器人事件
```java
/**
 * 副本系统监听机器人副本请求
 */
@Service
public class RobotDungeonHandler {
    private final DungeonService dungeonService;
    private final RobotRepository robotRepository;
    
    @EventListener
    @Transactional
    public void onRobotWantsToDungeon(RobotWantsToDungeonEvent event) {
        Robot robot = robotRepository.findById(event.robotId());
        PlayerRecord player = toPlayer(robot);
        
        try {
            DungeonRunResult result = dungeonService.runDungeonForPlayer(
                player,
                chooseDungeon(robot),
                null
            );
            
            // 发布副本完成事件
            eventPublisher.publishEvent(DungeonCompletedEvent.from(player, result));
            
        } catch (Exception e) {
            // 记录日志
        }
    }
}

/**
 * 装备系统监听装备掉落事件
 */
@Service
public class RobotEquipmentHandler {
    private final RobotRepository robotRepository;
    private final EquipmentService equipmentService;
    
    @EventListener
    @Transactional
    public void onItemDropped(ItemDroppedEvent event) {
        if (!isRobot(event.playerId())) return;
        
        Robot robot = robotRepository.findById(event.playerId());
        ItemRecord current = getCurrentEquipment(robot, event.item());
        
        EquipmentDecision decision = robot.decideEquipment(event.item(), current);
        
        if (decision.shouldEquip()) {
            equipmentService.equip(toPlayer(robot), event.item().id());
        } else if (decision.shouldSell()) {
            eventPublisher.publishEvent(new RobotWantsToSellEvent(
                robot.getId(),
                event.item(),
                robot.decideListingPrice(event.item(), decision.recommendedPrice())
            ));
        }
    }
}
```

---

## 总结

通过以上重构:

1. **InventoryService 拆分** → 三个独立领域服务,各司其职
2. **引入领域事件** → 解耦 DungeonService ↔ QuestService / AnnouncementService
3. **机器人系统重构** → 引入 Robot 聚合根 + 性格策略,业务逻辑统一管理

**重构后的好处**:
- ✅ 单一职责原则
- ✅ 依赖倒置 (通过事件解耦)
- ✅ 开闭原则 (新增机器人性格无需修改现有代码)
- ✅ 可测试性 (各模块独立测试)
- ✅ 可扩展性 (新增功能不影响现有模块)
