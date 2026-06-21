import { describe, expect, it } from 'vitest';
import type { Item, ItemCatalogItem } from '../api';
import {
  bestEnhancementStoneIds,
  catalogItemToDetail,
  catalogSourceHint,
  enhanceBaseChance,
  enhanceChance,
  enhanceCost,
  enhanceLuckBonus,
  formatNumber,
  formatStaminaTime,
  gemInventoryGroups,
  gemUpgradeBlockReason,
  isMarketableInventoryItem,
  itemEffectText,
  liveStaminaSnapshot,
  pad2,
  professionName,
  qualityName,
  qualityRank,
  typeName,
} from './helpers';

// Minimal Item fixture — enhanceCost/enhanceChance only read a few fields.
const item = (over: Partial<Item>): Item =>
  ({ requiredLevel: 1, enhancementLevel: 0, enhancementLuck: 0, ...over }) as unknown as Item;

const gem = (id: number, templateId: string, rank: number): Item =>
  item({
    id,
    templateId,
    effectType: 'gem',
    effectValueJson: JSON.stringify({ kind: 'ruby', rank, stat: 'attack' }),
  });

const catalogItem = (over: Partial<ItemCatalogItem>): ItemCatalogItem => ({
  templateId: 'shop_level_boost',
  name: '历练追赶礼盒',
  itemType: 'boost',
  itemCategory: 'consumable',
  quality: 'legendary',
  requiredLevel: 1,
  attackBonus: 0,
  defenseBonus: 0,
  resistanceBonus: 0,
  hpBonus: 0,
  mpBonus: 0,
  critBonus: 0,
  randomRange: 0,
  description: '追赶道具',
  sellPrice: 1,
  stackable: true,
  maxStack: 1,
  enhanceBonusRate: 0,
  minEnhanceLevel: 1,
  maxEnhanceLevel: 15,
  ...over,
});

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

  it('exposes base chance and blessing bonus separately', () => {
    const target = item({ enhancementLevel: 9, enhancementLuck: 3 });

    expect(enhanceBaseChance(target)).toBeCloseTo(0.4);
    expect(enhanceLuckBonus(target)).toBeCloseTo(0.15);
  });
});

describe('bestEnhancementStoneIds', () => {
  it('chooses strongest applicable stones with stack quantity', () => {
    const target = item({ enhancementLevel: 9 });
    const stones = [
      item({ id: 1, effectType: 'enhancementStone', enhanceBonusRate: 0.05, quality: 'uncommon', minEnhanceLevel: 1, maxEnhanceLevel: 15, quantity: 1 }),
      item({ id: 2, effectType: 'enhancementStone', enhanceBonusRate: 0.22, quality: 'immortal', minEnhanceLevel: 10, maxEnhanceLevel: 15, quantity: 2 }),
      item({ id: 3, effectType: 'enhancementStone', enhanceBonusRate: 0.16, quality: 'legendary', minEnhanceLevel: 7, maxEnhanceLevel: 15, quantity: 1 }),
      item({ id: 4, effectType: 'enhancementStone', enhanceBonusRate: 0.5, quality: 'immortal', minEnhanceLevel: 13, maxEnhanceLevel: 15, quantity: 1 }),
    ];

    expect(bestEnhancementStoneIds(stones, target)).toEqual([2, 2, 3]);
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

  it('shows unequipped ring items as neutral rings instead of the first ring slot', () => {
    expect(typeName('ring')).toBe('戒指');
  });
});

describe('gemUpgradeBlockReason', () => {
  it('scales dust cost by current gem rank', () => {
    const gems = [gem(1, 'gem_ruby_8', 8), gem(2, 'gem_ruby_8', 8), gem(3, 'gem_ruby_8', 8)];

    expect(gemUpgradeBlockReason(gems, [1, 2, 3], { mat_gem_dust: 63 })).toContain('需要 64');
    expect(gemUpgradeBlockReason(gems, [1, 2, 3], { mat_gem_dust: 64 })).toBeNull();
  });

  it('blocks rank 9 gems as max rank', () => {
    const gems = [gem(1, 'gem_ruby_9', 9), gem(2, 'gem_ruby_9', 9), gem(3, 'gem_ruby_9', 9)];

    expect(gemUpgradeBlockReason(gems, [1, 2, 3], { mat_gem_dust: 999 })).toBe('该宝石已达到最高阶。');
  });
});

describe('gemInventoryGroups', () => {
  it('counts stack quantity when grouping gems for crafting', () => {
    const groups = gemInventoryGroups([
      item({
        id: 1,
        templateId: 'gem_ruby_1',
        name: '裂纹红宝石 I',
        quality: 'rare',
        quantity: 9,
        effectType: 'gem',
        effectValueJson: JSON.stringify({ kind: 'ruby', rank: 1, stat: 'attack' }),
      }),
    ], { mat_gem_dust: 24 });

    expect(groups[0]).toMatchObject({
      quantity: 9,
      craftableByCount: 3,
      craftableByDust: 3,
      craftableCount: 3,
    });
    expect(groups[0].gems.slice(0, 3).map((entry) => entry.id)).toEqual([1, 1, 1]);
  });
});

describe('itemEffectText', () => {
  it('describes catch-up set chests and progress boosters', () => {
    expect(itemEffectText(item({
      effectType: 'equipmentSetChest',
      itemCategory: 'chest',
      requiredLevel: 1,
      effectValueJson: JSON.stringify({ equipmentLevel: 90, equipmentQuality: 'legendary' }),
    }))).toBe('开启获得 Lv.90 传说九件套');

    expect(itemEffectText(item({
      effectType: 'equipmentProgressBoost',
      effectValueJson: JSON.stringify({ progression: 'enhancement', targetLevel: 15 }),
    }))).toBe('全部装备强化至 +15');

    expect(itemEffectText(item({
      effectType: 'equipmentProgressBoost',
      effectValueJson: JSON.stringify({ progression: 'ascension', targetLevel: 5 }),
    }))).toBe('全部装备升阶至 5 阶');
  });

  it('keeps shop-only catch-up items out of market listing choices', () => {
    expect(isMarketableInventoryItem(item({
      itemCategory: 'chest',
      itemType: 'chest',
      effectType: 'equipmentSetChest',
      effectValueJson: JSON.stringify({ dropPolicy: 'shopOnly' }),
    }))).toBe(false);
  });
});

describe('catalog detail helpers', () => {
  it('builds catalog item details without recursively resolving origin', () => {
    const target = catalogItem({
      effectType: 'levelBoost',
      effectValueJson: JSON.stringify({ targetLevel: 60, shopPurchaseLimit: 1 }),
      shopPurchaseLimit: 1,
    });

    expect(catalogSourceHint(target)).toBe('冒险者商店限购');
    expect(catalogItemToDetail(target)).toMatchObject({
      templateId: 'shop_level_boost',
      origin: '冒险者商店限购',
    });
  });
});
