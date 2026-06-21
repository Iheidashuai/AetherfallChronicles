// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { gameApi, type InventorySnapshot, type Item } from '../api';
import { useAppStore } from '../store';
import { InventoryScreen } from './InventoryScreen';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const item = (overrides: Partial<Item>): Item => ({
  id: 1,
  playerId: 1,
  templateId: 'item',
  name: '物品',
  itemType: 'material',
  itemCategory: 'material',
  quality: 'common',
  requiredLevel: 1,
  attackBonus: 0,
  defenseBonus: 0,
  resistanceBonus: 0,
  hpBonus: 0,
  mpBonus: 0,
  critBonus: 0,
  sellPrice: 1,
  quantity: 1,
  stackable: true,
  enhanceBonusRate: 0,
  minEnhanceLevel: 1,
  maxEnhanceLevel: 15,
  enhancementLevel: 0,
  enhancementLuck: 0,
  ...overrides,
});

const inventorySnapshot = (): InventorySnapshot => ({
  inventory: [
    item({ id: 10, templateId: 'mat_fragment_legendary', name: '传说碎片', quantity: 40 }),
    item({ id: 11, templateId: 'mat_fragment_immortal', name: '不朽碎片', quality: 'immortal', quantity: 1572 }),
    item({
      id: 20,
      templateId: 'chest_immortal_cache',
      name: '不朽装备宝箱',
      itemType: 'chest',
      itemCategory: 'chest',
      quality: 'immortal',
      quantity: 3,
      effectType: 'chest',
      usable: true,
      actionLabel: '开启',
    }),
  ],
  equippedItems: {},
  combatPower: 1000,
  gold: 100,
  capacity: 1000,
});

let root: Root | null = null;
let container: HTMLDivElement | null = null;
let queryClient: QueryClient | null = null;

beforeEach(() => {
  const snapshot = inventorySnapshot();
  vi.spyOn(gameApi, 'inventory').mockResolvedValue(snapshot);
  vi.spyOn(gameApi, 'craftRecipe').mockResolvedValue({
    recipeId: 'recipe_immortal_cache',
    recipeName: '合成不朽装备宝箱',
    rewards: [],
    inventory: snapshot,
    quantity: 52,
  });
  vi.spyOn(gameApi, 'useItem').mockResolvedValue({
    itemName: '不朽装备宝箱',
    effectType: 'chest',
    message: '已使用 不朽装备宝箱 x3',
    rewards: [],
    inventory: snapshot,
    quantity: 3,
  });
  useAppStore.setState({
    token: 'test-token',
    username: 'tester',
    screen: 'inventory',
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
  });
  root = null;
  container = null;
  queryClient = null;
});

describe('InventoryScreen bulk actions', () => {
  it('crafts all possible fragment recipes from the recipe card', async () => {
    await renderInventory();
    await waitFor(() => {
      expect(container?.textContent).toContain('可合成 52');
    });

    const immortalRecipe = recipeCard('不朽装备宝箱');
    const allButton = Array.from(immortalRecipe.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === '全部');
    expect(allButton).toBeTruthy();

    await act(async () => {
      allButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(gameApi.craftRecipe).toHaveBeenCalledWith('test-token', 'recipe_immortal_cache', 52);
  });

  it('opens an entire chest stack from the item card', async () => {
    await renderInventory();
    await waitFor(() => {
      expect(container?.textContent).toContain('不朽装备宝箱');
    });

    const chestCard = itemCard('不朽装备宝箱');
    const openAllButton = Array.from(chestCard.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === '开启全部');
    expect(openAllButton).toBeTruthy();

    await act(async () => {
      openAllButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(gameApi.useItem).toHaveBeenCalledWith('test-token', 20, 3);
  });
});

async function renderInventory() {
  await act(async () => {
    root?.render(
      <QueryClientProvider client={queryClient!}>
        <InventoryScreen token="test-token" />
      </QueryClientProvider>,
    );
  });
}

function recipeCard(text: string) {
  const card = Array.from(container!.querySelectorAll('.recipe-card'))
    .find((element) => element.textContent?.includes(text));
  expect(card).toBeTruthy();
  return card as HTMLElement;
}

function itemCard(text: string) {
  const card = Array.from(container!.querySelectorAll('.item-card'))
    .find((element) => element.textContent?.includes(text));
  expect(card).toBeTruthy();
  return card as HTMLElement;
}

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
