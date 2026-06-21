// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { gameApi, type DerivedStats, type EquipmentProcessingSnapshot, type HomeSnapshot, type InventorySnapshot, type Item } from '../api';
import { useAppStore } from '../store';
import { BlacksmithScreen } from './BlacksmithScreen';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const equipment = (overrides: Partial<Item> = {}): Item => ({
  id: 101,
  playerId: 1,
  templateId: 'warrior_legacy_weapon_60',
  name: '战士传承·陨星·裁决之刃',
  itemType: 'weapon',
  itemCategory: 'equipment',
  quality: 'immortal',
  requiredLevel: 60,
  attackBonus: 371,
  defenseBonus: 23,
  resistanceBonus: 16,
  hpBonus: 270,
  mpBonus: 56,
  critBonus: 0.05,
  sellPrice: 1000,
  quantity: 1,
  stackable: false,
  enhanceBonusRate: 0,
  minEnhanceLevel: 1,
  maxEnhanceLevel: 15,
  enhancementLevel: 15,
  enhancementLuck: 0,
  refineLevel: 0,
  ascensionLevel: 0,
  sockets: [],
  affixes: [],
  ...overrides,
});

const rubyGem = (id: number, rank = 1): Item => ({
  ...equipment({
    id,
    templateId: `gem_ruby_${rank}`,
    name: `裂纹红宝石 ${rank}`,
    itemType: 'ruby',
    itemCategory: 'gem',
    quality: 'rare',
    requiredLevel: 1,
    attackBonus: 0,
    defenseBonus: 0,
    resistanceBonus: 0,
    hpBonus: 0,
    mpBonus: 0,
    sellPrice: 10,
    effectType: 'gem',
    effectValueJson: JSON.stringify({ rank, stat: 'attack', value: rank * 10 }),
    enhancementLevel: 0,
    displayName: `裂纹红宝石 ${rank}`,
  }),
});

const stats = (): DerivedStats => ({
  maxHp: 1000,
  maxMp: 500,
  attackPower: 100,
  armor: 50,
  resistance: 40,
  speed: 20,
  accuracy: 0.9,
  evasion: 0.05,
  critChance: 0.1,
  critDamage: 1.5,
});

const makeInventory = (item = equipment()): InventorySnapshot => ({
  inventory: [],
  equippedItems: { weapon: item },
  combatPower: 88_000,
  gold: 1_000_000,
  capacity: 120,
});

const makeProcessing = (
  inventory: InventorySnapshot,
  overrides: Partial<EquipmentProcessingSnapshot> = {},
): EquipmentProcessingSnapshot => ({
  equipment: Object.values(inventory.equippedItems).map((item) => ({
    item,
    socketLimit: 0,
    affixLimit: 0,
    unlockedSocketCount: 0,
    affixCount: 0,
    nextSocketCost: { goldCost: 0, socketCores: 0, gemDust: 0, essence: 0, chance: 1 },
    reforgeCost: { goldCost: 0, orbs: 0, essence: 0, lockStones: 0, chance: 1 },
    ascensionCost: { goldCost: 0, ascensionCores: 0, essence: 0, shards: 0, guards: 0, chance: 1 },
  })),
  gems: [],
  materials: {},
  recentLogs: [],
  inventory,
  ...overrides,
});

const makeHome = (inventory: InventorySnapshot): HomeSnapshot => ({
  player: {
    id: 1,
    name: 'AI_Tester',
    profession: 'warrior',
    level: 60,
    experience: 0,
    gold: inventory.gold,
    realMoney: 0,
    wealthTierLevel: 1,
    wealthTier: '平民',
    strength: 10,
    agility: 10,
    constitution: 10,
    intelligence: 10,
    spirit: 10,
    freePoints: 0,
  },
  combatPower: inventory.combatPower,
  maxHp: 1000,
  maxMp: 500,
  derivedStats: stats(),
  baseStats: stats(),
  equipmentStats: stats(),
  equipmentPower: inventory.combatPower,
  powerBreakdown: {
    basePower: 1000,
    equipmentPower: inventory.combatPower,
    skillPower: 0,
    synergyPower: 0,
    totalPower: inventory.combatPower,
  },
  equippedItems: inventory.equippedItems,
  inventoryCount: 1,
  inventoryCapacity: inventory.capacity,
  config: {
    version: 'test',
    checksum: 'test',
    itemCount: 1,
    monsterCount: 0,
    dungeonCount: 0,
    questCount: 0,
  },
  stamina: {
    current: 100,
    max: 100,
    secondsUntilNext: 0,
    secondsUntilFull: 0,
    updatedAt: '2026-06-21T00:00:00Z',
  },
});

let root: Root | null = null;
let container: HTMLDivElement | null = null;
let queryClient: QueryClient | null = null;

beforeEach(() => {
  const inventory = makeInventory();
  vi.spyOn(gameApi, 'inventory').mockResolvedValue(inventory);
  vi.spyOn(gameApi, 'equipmentProcessing').mockResolvedValue(makeProcessing(inventory));
  vi.spyOn(gameApi, 'home').mockResolvedValue(makeHome(inventory));
  useAppStore.setState({
    token: 'test-token',
    username: 'tester',
    screen: 'blacksmith',
    pendingWorldEventAction: null,
  });
  container = document.createElement('div');
  document.body.appendChild(container);
  queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
  root = createRoot(container);
});

afterEach(async () => {
  if (root) {
    await act(async () => root?.unmount());
  }
  container?.remove();
  queryClient?.clear();
  vi.restoreAllMocks();
  useAppStore.setState({
    token: null,
    username: null,
    screen: 'auth',
    pendingWorldEventAction: null,
  });
  root = null;
  container = null;
  queryClient = null;
});

describe('BlacksmithScreen enhance list', () => {
  it('uses compact equipment cards and keeps item detail behind an explicit action', async () => {
    await act(async () => {
      root?.render(
        <QueryClientProvider client={queryClient!}>
          <BlacksmithScreen token="test-token" />
        </QueryClientProvider>,
      );
    });

    await waitFor(() => {
      expect(container?.textContent).toContain('强化清单');
    });

    const card = container!.querySelector<HTMLElement>('.forge-equipment-card');
    expect(card).toBeTruthy();
    expect(card!.textContent).toContain('战士传承·陨星·裁决之刃 +15');
    expect(card!.textContent).toContain('+15 MAX');
    expect(card!.textContent).not.toContain('满级');
    expect(card!.querySelector('.inline-actions')).toBeNull();
    expect(card!.textContent).not.toContain('攻击 +371');
    expect(card!.textContent).not.toContain('防御 +23');

    await act(async () => {
      card!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(container!.querySelector('.item-detail')).toBeNull();
    const detailPanel = container!.querySelector<HTMLElement>('.forge-detail-panel');
    expect(detailPanel?.textContent).toContain('当前选择');
    expect(detailPanel?.textContent).toContain('查看详情');

    const detailButton = Array.from(detailPanel!.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.trim() === '查看详情');
    expect(detailButton).toBeTruthy();

    await act(async () => {
      detailButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    await waitFor(() => {
      expect(container!.querySelector('.item-detail')).toBeTruthy();
    });
  });
});

describe('BlacksmithScreen equipment transfer', () => {
  it('shows same-slot targets regardless of their current enhancement after selecting a source item', async () => {
    const source = equipment({
      id: 201,
      templateId: 'source_legs',
      name: '来源高强护腿',
      itemType: 'legs',
      enhancementLevel: 15,
    });
    const eligibleTarget = equipment({
      id: 202,
      templateId: 'target_legs_low',
      name: '可选低强护腿',
      itemType: 'legs',
      enhancementLevel: 3,
    });
    const highTarget = equipment({
      id: 203,
      templateId: 'target_legs_high',
      name: '可选高强护腿',
      itemType: 'legs',
      enhancementLevel: 15,
    });
    const wrongSlotTarget = equipment({
      id: 204,
      templateId: 'target_weapon_low',
      name: '不可选低强武器',
      itemType: 'weapon',
      enhancementLevel: 0,
    });
    const inventory: InventorySnapshot = {
      inventory: [eligibleTarget, highTarget, wrongSlotTarget],
      equippedItems: { legs: source },
      combatPower: 88_000,
      gold: 1_000_000,
      capacity: 120,
    };
    vi.mocked(gameApi.inventory).mockResolvedValue(inventory);
    vi.mocked(gameApi.equipmentProcessing).mockResolvedValue(makeProcessing(inventory));
    vi.mocked(gameApi.home).mockResolvedValue(makeHome(inventory));

    await act(async () => {
      root?.render(
        <QueryClientProvider client={queryClient!}>
          <BlacksmithScreen token="test-token" />
        </QueryClientProvider>,
      );
    });

    await waitFor(() => {
      expect(container?.textContent).toContain('强化清单');
    });

    const transferNav = Array.from(container!.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('装备转移'));
    expect(transferNav).toBeTruthy();

    await act(async () => {
      transferNav!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    await waitFor(() => {
      expect(container?.textContent).toContain('来源装备');
    });

    const sourceButton = Array.from(container!.querySelectorAll<HTMLButtonElement>('.transfer-item-option'))
      .find((button) => button.textContent?.includes('来源高强护腿'));
    expect(sourceButton).toBeTruthy();

    await act(async () => {
      sourceButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    const targetColumn = container!.querySelectorAll<HTMLElement>('.transfer-column')[1];
    expect(targetColumn.textContent).toContain('可选低强护腿');
    expect(targetColumn.textContent).toContain('可选高强护腿');
    expect(targetColumn.textContent).not.toContain('不可选低强武器');
    expect(targetColumn.textContent).not.toContain('来源高强护腿');
  });
});

describe('BlacksmithScreen gem crafting', () => {
  it('uses one real batch request for one-click gem crafting', async () => {
    const inventory = makeInventory();
    const gems = [1, 2, 3, 4, 5, 6].map((id) => rubyGem(id));
    vi.mocked(gameApi.equipmentProcessing).mockResolvedValue(makeProcessing(inventory, {
      gems,
      materials: { mat_gem_dust: 999 },
    }));
    const upgradeGems = vi.spyOn(gameApi, 'upgradeGems').mockRejectedValue(new Error('single upgrade should not be called'));
    const upgradeGemBatches = vi.spyOn(gameApi, 'upgradeGemBatches').mockResolvedValue({
      actionType: 'gem_upgrade',
      success: true,
      message: '批量合成 2 次宝石，最后获得 裂纹红宝石 2',
      item: rubyGem(99, 2),
      items: [rubyGem(98, 2), rubyGem(99, 2)],
      consumed: [{ templateId: 'mat_gem_dust', quantity: 16 }],
      processedCount: 2,
      powerBefore: 88_000,
      powerAfter: 88_000,
      snapshot: makeProcessing(inventory, {
        gems: [rubyGem(98, 2), rubyGem(99, 2)],
        materials: { mat_gem_dust: 983 },
      }),
    });

    await act(async () => {
      root?.render(
        <QueryClientProvider client={queryClient!}>
          <BlacksmithScreen token="test-token" />
        </QueryClientProvider>,
      );
    });

    await waitFor(() => {
      expect(container?.textContent).toContain('强化清单');
    });

    const gemCraftNav = Array.from(container!.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('宝石合成'));
    expect(gemCraftNav).toBeTruthy();

    await act(async () => {
      gemCraftNav!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    await waitFor(() => {
      expect(container?.textContent).toContain('宝石合成库存');
      expect(container?.textContent).toContain('一键 2 次');
    });

    const batchButton = Array.from(container!.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.trim() === '一键 2 次');
    expect(batchButton).toBeTruthy();

    await act(async () => {
      batchButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    await waitFor(() => {
      expect(upgradeGemBatches).toHaveBeenCalledTimes(1);
    });
    expect(upgradeGemBatches).toHaveBeenCalledWith('test-token', [[1, 2, 3], [4, 5, 6]]);
    expect(upgradeGems).not.toHaveBeenCalled();
  });
});

async function waitFor(assertion: () => void, timeoutMs = 1000) {
  const startedAt = Date.now();
  let lastError: unknown;
  while (Date.now() - startedAt < timeoutMs) {
    try {
      assertion();
      return;
    } catch (error) {
      lastError = error;
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
    }
  }
  throw lastError;
}
