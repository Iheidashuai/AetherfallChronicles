// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Item } from '../api';
import { EnhanceModal } from './ui';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root | null = null;
let container: HTMLDivElement | null = null;

const equipment = (overrides: Partial<Item> = {}): Item => ({
  id: 10,
  playerId: 1,
  templateId: 'eq_test_ring',
  name: '测试指轮',
  itemType: 'ring',
  itemCategory: 'equipment',
  quality: 'legendary',
  requiredLevel: 60,
  attackBonus: 100,
  defenseBonus: 40,
  resistanceBonus: 40,
  hpBonus: 500,
  mpBonus: 300,
  critBonus: 0.02,
  sellPrice: 1000,
  quantity: 1,
  stackable: false,
  enhanceBonusRate: 0,
  minEnhanceLevel: 1,
  maxEnhanceLevel: 15,
  enhancementLevel: 0,
  enhancementLuck: 0,
  ...overrides,
});

const stone = (overrides: Partial<Item> = {}): Item => ({
  ...equipment({
    id: 20,
    templateId: 'stone_test',
    name: '测试强化石',
    itemType: 'enhancementStone',
    itemCategory: 'material',
    quality: 'immortal',
    effectType: 'enhancementStone',
    enhanceBonusRate: 0.22,
    minEnhanceLevel: 1,
    maxEnhanceLevel: 15,
    stackable: true,
    quantity: 2,
    ...overrides,
  }),
});

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(async () => {
  if (root) {
    await act(async () => root?.unmount());
  }
  container?.remove();
  document.body.querySelectorAll('.stone-picker-scrim').forEach((node) => node.remove());
  vi.restoreAllMocks();
  root = null;
  container = null;
});

describe('EnhanceModal', () => {
  it('locks stone slots and enhance action at max level', async () => {
    await act(async () => {
      root?.render(
        <EnhanceModal
          item={equipment({ enhancementLevel: 15, enhancementLuck: 5 })}
          gold={1_000_000}
          loading={false}
          stones={[stone()]}
          onClose={() => undefined}
          onEnhance={() => undefined}
        />,
      );
    });

    expect(container?.textContent).toContain('祝福值进度');
    expect(container?.textContent).toContain('已达上限');
    const slots = Array.from(container!.querySelectorAll<HTMLButtonElement>('.stone-slot.empty'));
    expect(slots).toHaveLength(3);
    expect(slots.every((slot) => slot.disabled)).toBe(true);

    await act(async () => {
      slots[0].dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(document.body.querySelector('.stone-picker-sheet')).toBeNull();
    const maxButton = Array.from(container!.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.trim() === '已达上限');
    expect(maxButton?.disabled).toBe(true);
  });

  it('renders the stone picker through a body portal', async () => {
    await act(async () => {
      root?.render(
        <EnhanceModal
          item={equipment({ enhancementLevel: 9 })}
          gold={1_000_000}
          loading={false}
          stones={[stone()]}
          onClose={() => undefined}
          onEnhance={() => undefined}
        />,
      );
    });

    const firstSlot = container!.querySelector<HTMLButtonElement>('.stone-slot.empty');
    expect(firstSlot).toBeTruthy();

    await act(async () => {
      firstSlot!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    const picker = document.body.querySelector('.stone-picker-sheet');
    expect(picker).toBeTruthy();
    expect(container!.contains(picker)).toBe(false);
  });
});
