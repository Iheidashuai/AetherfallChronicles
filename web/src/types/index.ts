import type { Dungeon, HomeSnapshot } from '../api';

export type InventoryAction = 'equip' | 'sell' | 'enhance';
export type FeedbackVariant = 'success' | 'error';
export type DungeonMode = 'normal' | 'special';
export type DungeonClearFilter = 'all' | 'uncleared' | 'locked' | 'cleared';
export type DungeonRiskFilter = 'all' | 'safe' | 'normal' | 'risky' | 'deadly';
export type DungeonLevelFilter = 'all' | '1-30' | '31-60' | '61-90';
export type QuestCategoryFilter = 'all' | 'main' | 'daily' | 'achievement';
export type LeaderboardMetric = 'power' | 'gold' | 'level';
export type ProfessionFilter = 'all' | 'warrior' | 'mage' | 'ranger';
export type MarketSortKey = 'listedAt' | 'unitPrice' | 'price' | 'level' | 'quality';
export type CatalogCategoryFilter = 'all' | 'equipment' | 'consumable' | 'material' | 'chest';
export type CatalogQualityFilter = 'all' | 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'immortal';
export type CatalogLevelFilter = 'all' | '1-10' | '11-30' | '31-60' | '61-90';
export type StaminaView = NonNullable<HomeSnapshot['stamina'] | Dungeon['stamina']>;
export type CatalogSortKey = 'quality' | 'level' | 'type';
export type MarketItemTypeFilter = 'all' | 'weapon' | 'helmet' | 'armor' | 'legs' | 'boots' | 'gloves' | 'necklace' | 'ring';
export type MarketCategoryFilter = 'all' | 'equipment' | 'gem' | 'material' | 'consumable' | 'chest';
export type MarketQualityFilter = 'all' | 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'immortal';
export type MarketLedgerTab = 'listed' | 'sold';
export type ShopCategoryFilter = 'all' | 'gold' | 'stamina' | 'enhancement' | 'growth' | 'chest';
export type RobotFilterKey = 'name' | 'minGold' | 'maxGold' | 'minPower' | 'maxPower' | 'minLevel' | 'maxLevel';
export type ForgeView = 'enhance' | 'transfer' | 'refine' | 'socket' | 'reforge' | 'ascend';
export type BuildTab = 'overview' | 'equipment' | 'skills' | 'talents' | 'simulate';

export type BuildDraft = {
  name: string;
  strategy: string;
  refineFocus: string;
  equipment: Record<string, number | null>;
  skills: { slotIndex: number; triggerKind: string; skillId: string }[];
  talents: string[];
};

export type PowerBreakdownSlice = {
  key: string;
  label: string;
  value: number;
  detail: string;
};

export type RobotFilters = Record<RobotFilterKey, string>;
export type PlayableProfession = 'warrior' | 'ranger' | 'mage';
export type AttributeKey = 'strength' | 'agility' | 'constitution' | 'intelligence' | 'spirit';

export type MarketFilters = {
  minLevel: string;
  maxLevel: string;
  category: MarketCategoryFilter;
  itemType: MarketItemTypeFilter;
  quality: MarketQualityFilter;
  sort: MarketSortKey;
};

export type EquipmentDetailData = {
  id?: number;
  templateId: string;
  name: string;
  displayName?: string;
  itemType: string;
  itemCategory?: string;
  quality: string;
  requiredLevel: number;
  attackBonus: number;
  defenseBonus: number;
  resistanceBonus: number;
  hpBonus: number;
  mpBonus: number;
  critBonus?: number;
  sellPrice: number;
  quantity?: number;
  stackable?: boolean;
  effectType?: string;
  effectValueJson?: string;
  enhanceBonusRate?: number;
  minEnhanceLevel?: number;
  maxEnhanceLevel?: number;
  enhancementLevel: number;
  enhancementLuck?: number;
  refineLevel?: number;
  refineFocus?: string;
  ascensionLevel?: number;
  ascensionLuck?: number;
  socketAttackBonus?: number;
  socketDefenseBonus?: number;
  socketResistanceBonus?: number;
  socketHpBonus?: number;
  socketMpBonus?: number;
  socketCritBonus?: number;
  affixAttackBonus?: number;
  affixDefenseBonus?: number;
  affixResistanceBonus?: number;
  affixHpBonus?: number;
  affixMpBonus?: number;
  affixCritBonus?: number;
  origin?: string;
  power?: number;
  description?: string;
};
