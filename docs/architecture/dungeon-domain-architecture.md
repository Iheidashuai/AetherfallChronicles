# Dungeon Domain Architecture

## Overview

The Dungeon domain implements a complete DDD (Domain-Driven Design) architecture for managing dungeon runs, combat simulation, loot drops, and player progression in the mythic realm game.

## Domain Structure

### 1. Domain Layer (`domain/`)

#### 1.1 Aggregates & Entities

**Dungeon (Aggregate Root)**
- Location: `domain/model/Dungeon.java`
- Responsibilities:
  - Manages dungeon configuration and metadata
  - Orchestrates dungeon run creation
  - Publishes domain events for completed runs
  - Tracks domain events (DungeonStartedEvent, DungeonCompletedEvent, etc.)

**DungeonRun (Entity)**
- Location: `domain/model/DungeonRun.java`
- Responsibilities:
  - Represents a single dungeon execution
  - Tracks combat progress (monsters killed, exp/gold gained)
  - Accumulates loot drops
  - Records battle logs and frames
  - Calculates rare loot count

#### 1.2 Value Objects

**BattleFrame**
- Location: `domain/model/BattleFrame.java`
- Immutable snapshot of a combat moment
- Contains: player/enemy HP, action text, tone, room context

**DungeonRating (Enum)**
- Location: `domain/model/DungeonRating.java`
- Values: S, A, B, C, F
- Calculation logic based on success, power ratio, and HP remaining

#### 1.3 Domain Services

**CombatEngine**
- Location: `domain/service/CombatEngine.java`
- Core combat simulation logic:
  - Turn-based combat (max 18 rounds)
  - Damage calculation with variance and criticals
  - Monster HP/attack scaling based on pressure
  - Room recovery mechanics
  - Attrition damage for prolonged battles
  - Battle frame generation

**LootCalculator**
- Location: `domain/service/LootCalculator.java`
- Loot drop calculation:
  - Processes monster loot tables
  - Applies drop rate probabilities
  - Creates inventory items via InventoryService
  - Generates loot battle frames

**PressureCalculator**
- Location: `domain/service/PressureCalculator.java`
- Difficulty scaling based on player power vs recommended:
  - 1.2x+ power ratio → 0.85 pressure (easier)
  - 1.0-1.2x → 1.0 pressure (standard)
  - 0.8-1.0x → 1.25 pressure (harder)
  - 0.6-0.8x → 1.75 pressure (very hard)
  - <0.6x → 2.55 pressure (extreme)

#### 1.4 Domain Events

**DungeonStartedEvent**
- Fired when a dungeon run begins
- Contains: playerId, dungeonId, dungeonName, requestId

**DungeonCompletedEvent**
- Fired when a dungeon run finishes
- Contains: success status, rating, rewards, statistics

**MonsterKilledEvent**
- Fired for monster kills
- Contains: playerId, dungeonId, monster count

**ItemDroppedEvent**
- Fired for each item drop
- Contains: playerId, dungeonId, itemId, quality
- Helper method: `isRareOrBetter()`

#### 1.5 Repository Interfaces

**DungeonRepository**
- Location: `domain/repository/DungeonRepository.java`
- Methods:
  - `findById(String dungeonId)`
  - `requireById(String dungeonId)`
  - `findAll()`
  - `hasCleared(long playerId, String dungeonId)`
  - `getClearedDungeonIds(long playerId)`

**DungeonRunRepository**
- Location: `domain/repository/DungeonRunRepository.java`
- Methods:
  - `save(DungeonRun)`
  - `findByPlayerAndRequest(long playerId, String requestId)`
  - `exists(long playerId, String requestId)`

### 2. Application Layer (`application/`)

#### 2.1 Application Service

**DungeonApplicationService**
- Location: `application/DungeonApplicationService.java`
- Use cases:
  - `runDungeon(RunDungeonCommand)` - Execute dungeon run with full combat
  - `sweepDungeon(SweepDungeonCommand)` - Fast-forward multiple runs
- Responsibilities:
  - Command validation and idempotency
  - Orchestrates domain objects and services
  - Transaction management
  - Event publishing
  - Quest event integration

#### 2.2 Commands

**RunDungeonCommand**
- Fields: playerId, dungeonId, requestId
- Purpose: Request a single dungeon run

**SweepDungeonCommand**
- Fields: playerId, dungeonId, times (1-10), requestId
- Purpose: Request multiple fast-forwarded runs

### 3. Infrastructure Layer (`infrastructure/`)

#### 3.1 Persistence

**DungeonRepositoryImpl**
- Location: `infrastructure/persistence/DungeonRepositoryImpl.java`
- Uses GameConfigService for dungeon configs
- Uses JdbcTemplate for cleared status queries

**DungeonRunRepositoryImpl**
- Location: `infrastructure/persistence/DungeonRunRepositoryImpl.java`
- Persists runs to `dungeon_run` table
- Creates economy audit events
- JSON serialization/deserialization via ObjectMapper
- Stores: loot_json, result_json for history replay

**DungeonRunPO**
- Location: `infrastructure/persistence/DungeonRunPO.java`
- Maps to `dungeon_run` database table
- Fields: id, playerId, dungeonId, configVersion, requestId, success, rating, rewards, JSON columns

#### 3.2 Configuration

**DungeonDomainConfig**
- Location: `infrastructure/config/DungeonDomainConfig.java`
- Spring beans for domain services:
  - PressureCalculator
  - LootCalculator
  - CombatEngine

### 4. Interface Layer (`interfaces/`)

#### 4.1 REST Controller

**DungeonController**
- Location: `interfaces/DungeonController.java`
- Endpoints:
  - `GET /api/dungeons` - List dungeons with cleared status
  - `POST /api/dungeons/{dungeonId}/run` - Run dungeon
  - `POST /api/dungeons/{dungeonId}/sweep?times=N` - Sweep dungeon

#### 4.2 DTOs

**DungeonResultDTO**
- Full run result with logs, frames, loot, player state

**DungeonSweepResultDTO**
- Sweep result with aggregated rewards and summary logs

**DungeonProgressPreviewDTO**
- Dungeon metadata with cleared status

## Combat Mechanics

### Turn Flow
1. Player attacks with damage variance and crit chance
2. If monster survives, it counter-attacks
3. Repeat for max 18 rounds
4. If timeout, apply attrition damage

### Damage Formula
```
damage = max(1, (attack * critMultiplier - defense * 0.42) * variance)
- variance: 0.88 to 1.12
- critMultiplier: 1.75 if crit, else 1.0
```

### Monster Scaling
```
monsterHp = baseHp * pressure
monsterAttack = baseAttack * (0.9 + pressure * 0.45)
```

### Player Stats
- HP: player.maxHp + equipment bonuses
- Attack: player.attack + equipment bonuses
- Defense: player.defense + equipment bonuses
- Crit Rate: min(0.45, player.agility * 0.001 + equipment crit bonuses)

### Recovery
- Between rooms: recover max(6, playerMaxHp / 8) HP

## Event Flow

### Dungeon Run
1. DungeonStartedEvent published
2. Combat simulation executes
3. MonsterKilledEvent for each monster
4. ItemDroppedEvent for each loot drop
5. DungeonCompletedEvent on finish
6. Quest events triggered:
   - dungeonCompleted
   - monsterKills
   - itemQualityObtained (for rare+)
   - combatPowerReached

### Dungeon Sweep
1. No combat simulation (instant calculation)
2. Direct reward accumulation
3. Quest events triggered with multiplied counts

## Idempotency

- Uses `requestId` for duplicate detection
- Checks `DungeonRunRepository.findByPlayerAndRequest()` before execution
- Returns cached result if found

## Integration Points

- **PlayerService**: Load player stats, apply rewards
- **InventoryService**: Load equipment, calculate combat power, add loot
- **GameConfigService**: Load dungeon configs, monster configs, item templates
- **QuestService**: Record quest events for progression tracking

## Migration Notes

The original `DungeonService.java` has been refactored into:
- **Domain logic** → CombatEngine, LootCalculator, PressureCalculator
- **Business orchestration** → DungeonApplicationService
- **Data access** → DungeonRepositoryImpl, DungeonRunRepositoryImpl
- **API** → DungeonController with DTOs

Original combat logic, rating system, and loot calculation are **fully preserved** in the domain services.

## Files Created

### Domain Layer (8 files)
- `domain/model/Dungeon.java`
- `domain/model/DungeonRun.java`
- `domain/model/BattleFrame.java`
- `domain/model/DungeonRating.java`
- `domain/service/CombatEngine.java`
- `domain/service/LootCalculator.java`
- `domain/service/PressureCalculator.java`
- `domain/event/DungeonStartedEvent.java`
- `domain/event/DungeonCompletedEvent.java`
- `domain/event/MonsterKilledEvent.java`
- `domain/event/ItemDroppedEvent.java`
- `domain/repository/DungeonRepository.java`
- `domain/repository/DungeonRunRepository.java`

### Application Layer (3 files)
- `application/DungeonApplicationService.java`
- `application/command/RunDungeonCommand.java`
- `application/command/SweepDungeonCommand.java`

### Infrastructure Layer (4 files)
- `infrastructure/persistence/DungeonRepositoryImpl.java`
- `infrastructure/persistence/DungeonRunRepositoryImpl.java`
- `infrastructure/persistence/DungeonRunPO.java`
- `infrastructure/config/DungeonDomainConfig.java`

### Interface Layer (4 files)
- `interfaces/DungeonController.java`
- `interfaces/dto/DungeonResultDTO.java`
- `interfaces/dto/DungeonSweepResultDTO.java`
- `interfaces/dto/DungeonProgressPreviewDTO.java`

**Total: 23 new files**

## Next Steps

1. Update existing `DungeonService` and `DungeonController` to delegate to new architecture
2. Add unit tests for domain services
3. Add integration tests for application service
4. Consider event bus for domain events
5. Add domain event handlers for announcements (rare drops, first clears, etc.)
