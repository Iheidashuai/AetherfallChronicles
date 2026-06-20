import { describe, expect, it } from 'vitest';
import type { Item } from '../api';
import {
  enhanceChance,
  enhanceCost,
  formatNumber,
  formatStaminaTime,
  liveStaminaSnapshot,
  pad2,
  professionName,
  qualityName,
  qualityRank,
} from './helpers';

// Minimal Item fixture — enhanceCost/enhanceChance only read a few fields.
const item = (over: Partial<Item>): Item =>
  ({ requiredLevel: 1, enhancementLevel: 0, enhancementLuck: 0, ...over }) as unknown as Item;

describe('enhanceCost (money path)', () => {
  it('follows requiredLevel^2 * nextLevel * 10', () => {
    // 30-level item +9 -> +10 : 30*30*10*10 = 90,000 (matches economy spec)
    expect(enhanceCost(item({ requiredLevel: 30, enhancementLevel: 9 }))).toBe(90_000);
    // 10-level item +0 -> +1 : 10*10*1*10 = 1,000
    expect(enhanceCost(item({ requiredLevel: 10, enhancementLevel: 0 }))).toBe(1_000);
  });

  it('treats requiredLevel below 1 as 1', () => {
    expect(enhanceCost(item({ requiredLevel: 0, enhancementLevel: 0 }))).toBe(10);
  });
});

describe('enhanceChance', () => {
  it('uses zone base rates by target level', () => {
    // NOTE: the helper caps every displayed chance at 0.95, so even the guaranteed
    // +1..+3 tier (base 1.0) shows 0.95 in the UI, never 100%. Documenting actual behavior.
    expect(enhanceChance(item({ enhancementLevel: 0 }))).toBeCloseTo(0.95); // +1 base 1.0 -> capped 0.95
    expect(enhanceChance(item({ enhancementLevel: 6 }))).toBeCloseTo(0.6); // +7 => 60%
    expect(enhanceChance(item({ enhancementLevel: 14 }))).toBeCloseTo(0.2); // +15 => 20%
  });

  it('adds luck but caps at 0.95', () => {
    expect(enhanceChance(item({ enhancementLevel: 14, enhancementLuck: 3 }))).toBeCloseTo(0.35);
    expect(enhanceChance(item({ enhancementLevel: 0, enhancementLuck: 10 }))).toBe(0.95);
  });
});

describe('qualityRank', () => {
  it('orders qualities with immortal highest', () => {
    expect(qualityRank('immortal')).toBeGreaterThan(qualityRank('legendary'));
    expect(qualityRank('legendary')).toBeGreaterThan(qualityRank('common'));
    expect(qualityRank('unknown')).toBe(0);
  });
});

describe('formatters', () => {
  it('formatNumber rounds non-integers, passes integers', () => {
    expect(formatNumber(1200)).toBe('1200');
    expect(formatNumber(1.4)).toBe('1');
    expect(formatNumber(1.6)).toBe('2');
  });

  it('pad2 zero-pads to two digits', () => {
    expect(pad2(5)).toBe('05');
    expect(pad2(42)).toBe('42');
  });

  it('formatStaminaTime renders full / mm:ss / h m', () => {
    expect(formatStaminaTime(0)).toBe('已满');
    expect(formatStaminaTime(90)).toBe('1:30');
    expect(formatStaminaTime(3700)).toBe('1h 1m');
  });

  it('liveStaminaSnapshot refills stamina to max when the recovery window ends', () => {
    const stamina = {
      current: 420,
      max: 1000,
      secondsUntilNext: 180,
      secondsUntilFull: 180,
      updatedAt: '2026-06-20T00:00:00Z',
    };

    expect(liveStaminaSnapshot(stamina, 179)).toMatchObject({
      current: 420,
      secondsUntilNext: 1,
      secondsUntilFull: 1,
    });
    expect(liveStaminaSnapshot(stamina, 180)).toMatchObject({
      current: 1000,
      secondsUntilNext: 0,
      secondsUntilFull: 0,
    });
  });
});

describe('name maps fall back gracefully', () => {
  it('known and unknown values', () => {
    expect(professionName('warrior')).not.toBe('');
    expect(qualityName('immortal')).not.toBe('');
    expect(qualityName('totally-unknown')).toBeTypeOf('string');
  });
});
