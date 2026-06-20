// Shared pure helpers extracted from App.tsx (formatters, name maps, calculators,
// type guards, sorters, detail builders, build mutators). Can be themed-split later.
import type { CSSProperties, ReactNode } from 'react';
import {
  ArrowUp, Backpack, Bell, Boxes, ChevronRight, CircleAlert, CircleCheck, Clock3,
  Coins, FastForward, Filter, Gem, Gauge, Hammer, HeartPulse, LogOut, MessageCircle,
  Package, Repeat2, ScrollText, Send, Shield, Search, Skull, ShoppingBag, SkipForward,
  Sparkles, Swords, Trophy, UserRound,
} from 'lucide-react';
import type {
  BattleFrame, ArenaMatchDetail, ArenaOpponent, ArenaProfile, ArenaShopOffer,
  BuildActivationResult, BuildMutationRequest, BuildPreset, BuildScore, BuildSnapshot,
  BuildTalent, PlayerBuild, ChatMessage, ChatSpeaker, Dungeon, DungeonRunResult,
  DungeonSweepResult, DropPreview, EquipmentProcessingResult, EquipmentProcessingSnapshot,
  GlobalAnnouncement, HomeSnapshot, InventorySnapshot, Item, ItemCatalogItem, DerivedStats,
  LeaderboardEntry, LeaderboardEquipment, MarketListing, MarketSale, QuestRow, CashIncomeRow,
  RobotRechargeRow, RobotActivityDetail, RobotActivityEvent, RobotActivityView, RiftRunResult,
  RiftSimulationResult, RiftSnapshot, ShopOffer, SkillSnapshot, SkillView, WealthTierStat,
} from '../api';
import type {
  InventoryAction, FeedbackVariant, DungeonMode, DungeonClearFilter, DungeonRiskFilter,
  DungeonLevelFilter, QuestCategoryFilter, LeaderboardMetric, ProfessionFilter, MarketSortKey,
  CatalogCategoryFilter, CatalogQualityFilter, CatalogLevelFilter, StaminaView, CatalogSortKey,
  MarketItemTypeFilter, MarketCategoryFilter, MarketQualityFilter, MarketLedgerTab,
  ShopCategoryFilter, RobotFilterKey, ForgeView, BuildTab, BuildDraft, PowerBreakdownSlice,
  RobotFilters, PlayableProfession, AttributeKey, MarketFilters, EquipmentDetailData,
} from '../types';
import {
  ANNOUNCEMENT_SEEN_STORAGE_KEY, ATTRIBUTE_LABELS, CREATE_PROFESSIONS,
} from './constants';

export function announcementSeenKey(announcement: GlobalAnnouncement) {
  return `${announcement.id}:${announcement.createdAt}`;
}

export function readSeenAnnouncementKeys() {
  try {
    const raw = window.localStorage.getItem(ANNOUNCEMENT_SEEN_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return new Set(Array.isArray(parsed) ? parsed.filter((value): value is string => typeof value === 'string') : []);
  } catch {
    return new Set<string>();
  }
}

export function writeSeenAnnouncementKeys(keys: Set<string>) {
  try {
    window.localStorage.setItem(ANNOUNCEMENT_SEEN_STORAGE_KEY, JSON.stringify(Array.from(keys).slice(-160)));
  } catch {
    // Ignore storage quota/private mode failures; the ticker still works for the current render.
  }
}


export function chatAvatarLabel(value: string) {
  const trimmed = value.trim();
  return (trimmed[0] ?? '?').toUpperCase();
}


export function equipmentDisplayName(item: { name: string; displayName?: string; enhancementLevel?: number; refineLevel?: number; ascensionLevel?: number }) {
  const baseName = (item.displayName?.trim() || item.name).replace(/\s\+\d+$/, '');
  const level = item.enhancementLevel ?? 0;
  const enhanced = level > 0 ? `${baseName} +${level}` : baseName;
  const refined = (item.refineLevel ?? 0) > 0 ? `${enhanced} · 淬${item.refineLevel}` : enhanced;
  return (item.ascensionLevel ?? 0) > 0 ? `${refined} · 阶${item.ascensionLevel}` : refined;
}

export function equipmentCompactText(item: Item) {
  return `Lv.${item.requiredLevel} · 战力 ${formatNumber(itemPower(item))}`;
}

export function enhancementStage(level = 0) {
  if (level >= 15) {
    return 'max';
  }
  if (level >= 12) {
    return 'radiant';
  }
  if (level >= 9) {
    return 'awaken';
  }
  return null;
}

export function enhancementEffectClass(item?: { enhancementLevel?: number } | null) {
  const stage = enhancementStage(item?.enhancementLevel ?? 0);
  return stage ? `enhance-effect enhance-${stage}` : '';
}

export function enhancementBadgeText(level: number) {
  const stage = enhancementStage(level);
  if (stage === 'max') {
    return `+${level} MAX`;
  }
  if (stage === 'radiant') {
    return `+${level} 辉光`;
  }
  if (stage === 'awaken') {
    return `+${level} 觉醒`;
  }
  return '';
}


export function orderedEquipment(items: Record<string, Item>) {
  return equipmentSlotOrder()
    .filter((slot) => items[slot])
    .map((slot) => [slot, items[slot]] as [string, Item]);
}

export function equipmentSlotPairs(items: Record<string, Item>) {
  return equipmentSlotOrder().map((slot) => [slot, items[slot] ?? null] as [string, Item | null]);
}

export function equipmentSlotOrder() {
  return ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring1', 'ring2'];
}

export function battleFramesForResult(result: DungeonRunResult): BattleFrame[] {
  if (result.frames?.length) {
    return result.frames;
  }
  const maxHp = result.playerMaxHp || result.playerFinalHp || 1;
  return (result.logs?.length ? result.logs : ['没有战斗记录']).map((log, index) => {
    const tone = battleLogTone(log);
    return {
      index: index + 1,
      text: log,
      tone,
      roomLabel: '战斗记录',
      enemyName: undefined,
      playerHp: result.playerFinalHp || maxHp,
      playerMaxHp: maxHp,
      enemyHp: 0,
      enemyMaxHp: 0,
      actor: 'system' as const,
      eventType: log.includes('恢复') ? 'heal' as const : tone === 'hit' ? 'hit' as const : 'phase' as const,
      damage: 0,
      critical: log.includes('暴击'),
      missed: log.includes('闪避') || log.includes('落空'),
    };
  });
}

export function professionName(profession: string) {
  return profession === 'warrior' ? '战士' : profession === 'ranger' ? '射手' : profession === 'mage' ? '法师' : profession;
}

export function skillCategoryName(skill: SkillView) {
  if (skill.effectType === 'heal') {
    return '治疗';
  }
  if (skill.effectType === 'shield') {
    return '护盾';
  }
  return skill.damageType === 'magic' ? '法术' : '攻击';
}

export function skillTriggerName(trigger: string) {
  const names: Record<string, string> = {
    default: '循环',
    burst: '爆发',
    execute: '终结',
    opener: '起手',
    defensive: '守势',
    heal: '续航',
  };
  return names[trigger] ?? trigger;
}

export function skillValueLabel(value: number, skill: SkillView) {
  if (skill.effectType === 'heal' || skill.effectType === 'shield') {
    return formatPercent(value);
  }
  return `${value.toFixed(2)}x`;
}

export function leaderboardMetricName(metric: LeaderboardMetric) {
  return metric === 'gold' ? '金币' : metric === 'level' ? '等级' : '战力';
}

export function formatSigned(value: number) {
  return value > 0 ? `+${value}` : value.toString();
}

export function leaderboardMetricValue(entry: LeaderboardEntry, metric: LeaderboardMetric) {
  if (metric === 'gold') {
    return entry.gold;
  }
  if (metric === 'level') {
    return entry.level;
  }
  return entry.power;
}

export function leaderboardScoreText(entry: LeaderboardEntry | undefined, metric: LeaderboardMetric) {
  if (!entry) {
    return '-';
  }
  if (metric === 'gold') {
    return `${formatNumber(entry.gold)} 金`;
  }
  if (metric === 'level') {
    return `Lv.${entry.level}`;
  }
  return formatNumber(entry.power);
}

export function leaderboardSecondaryStats(entry: LeaderboardEntry, metric: LeaderboardMetric) {
  const stats = [
    { key: 'level', label: '等级', value: `Lv.${entry.level}` },
    { key: 'power', label: '战力', value: formatNumber(entry.power) },
    { key: 'gold', label: '金币', value: `${formatNumber(entry.gold)} 金` },
  ];
  return [
    ...stats.filter((stat) => stat.key !== metric),
    { key: 'mainAttribute', label: '主属性', value: leaderboardMainAttribute(entry) },
  ];
}

export function leaderboardMainAttribute(entry: LeaderboardEntry) {
  const attributes = [
    ['力量', entry.strength],
    ['敏捷', entry.agility],
    ['体质', entry.constitution],
    ['智力', entry.intelligence],
    ['精神', entry.spirit],
  ] as const;
  const [label, value] = attributes.reduce((best, current) => current[1] > best[1] ? current : best);
  return `${label} ${value}`;
}

export function rankLeaderboardEntries(entries: LeaderboardEntry[], metric: LeaderboardMetric, profession: ProfessionFilter) {
  return entries
    .filter((entry) => profession === 'all' || entry.profession === profession)
    .sort((left, right) => {
      const metricDelta = leaderboardMetricValue(right, metric) - leaderboardMetricValue(left, metric);
      if (metricDelta !== 0) {
        return metricDelta;
      }
      return right.power - left.power
        || right.gold - left.gold
        || right.level - left.level
        || left.name.localeCompare(right.name);
    })
    .map((entry, index) => ({ ...entry, rank: index + 1 }));
}

export function slotName(slot: string) {
  const names: Record<string, string> = {
    weapon: '武器',
    helmet: '头盔',
    armor: '护甲',
    legs: '护腿',
    boots: '靴子',
    gloves: '护手',
    necklace: '项链',
    ring1: '戒指一',
    ring2: '戒指二',
  };
  return names[slot] ?? slot;
}

export function homeSlotShortName(slot: string) {
  const names: Record<string, string> = {
    weapon: '武',
    helmet: '头',
    armor: '甲',
    legs: '腿',
    boots: '靴',
    gloves: '手',
    necklace: '链',
    ring1: '戒1',
    ring2: '戒2',
  };
  return names[slot] ?? slotName(slot).slice(0, 1);
}

export function typeName(type: string) {
  if (type === 'enhancementStone') {
    return '强化石';
  }
  if (type === 'staminaPotion') {
    return '疲劳药水';
  }
  if (type === 'attributePotion') {
    return '属性药水';
  }
  if (type === 'levelBoost') {
    return '成长药水';
  }
  if (type === 'fragment') {
    return '碎片';
  }
  if (type === 'chest') {
    return '宝箱';
  }
  return slotName(type === 'ring' ? 'ring1' : type);
}

export function isEquipmentItem(item: { itemCategory?: string; itemType: string }) {
  return (item.itemCategory ?? 'equipment') === 'equipment'
    || ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring'].includes(item.itemType);
}

export function itemCategoryLabel(item: { itemCategory?: string; itemType: string }) {
  const category = item.itemCategory ?? (isEquipmentItem(item) ? 'equipment' : 'material');
  const names: Record<string, string> = {
    equipment: '装备',
    consumable: '消耗品',
    material: '材料',
    chest: '宝箱',
  };
  return names[category] ?? typeName(item.itemType);
}

export function itemEffectText(item: Item | EquipmentDetailData) {
  if (item.effectType === 'staminaPotion') {
    return `恢复疲劳 +${effectNumber(item, 'amount')}`;
  }
  if (item.effectType === 'attributePotion') {
    const effect = parseEffectValue(item);
    return `${attributeName(String(effect.attribute ?? ''))} +${Number(effect.amount ?? 0)}`;
  }
  if (item.effectType === 'levelBoost') {
    const effect = parseEffectValue(item);
    return `直升 Lv.${Number(effect.targetLevel ?? 60)} · 获得 9 件史诗装备`;
  }
  if (item.effectType === 'enhancementStone') {
    return `成功率 +${formatPercent(item.enhanceBonusRate ?? 0)} · +${item.minEnhanceLevel ?? 1}-${item.maxEnhanceLevel ?? 15}`;
  }
  if (item.effectType === 'fragment') {
    return '合成装备宝箱材料';
  }
  if (item.effectType === 'chest' || item.itemCategory === 'chest') {
    return '开启后随机获得奖励';
  }
  return bonusText(item);
}

export function parseEffectValue(item: Item | EquipmentDetailData) {
  if (!item.effectValueJson) {
    return {} as Record<string, string | number>;
  }
  try {
    return JSON.parse(item.effectValueJson) as Record<string, string | number>;
  } catch {
    return {} as Record<string, string | number>;
  }
}

export function effectNumber(item: Item | EquipmentDetailData, key: string) {
  const value = parseEffectValue(item)[key];
  return typeof value === 'number' ? value : Number(value ?? 0);
}

export function inventoryTemplateQuantity(items: Item[], templateId: string) {
  return items
    .filter((item) => item.templateId === templateId)
    .reduce((total, item) => total + Math.max(1, item.quantity ?? 1), 0);
}

export function enhancementStonesForItem(items: Item[], item: Item | null) {
  if (!item) {
    return [];
  }
  const nextLevel = item.enhancementLevel + 1;
  return items
    .filter((stone) => stone.effectType === 'enhancementStone')
    .filter((stone) => stone.minEnhanceLevel <= nextLevel && stone.maxEnhanceLevel >= nextLevel)
    .sort((left, right) => right.enhanceBonusRate - left.enhanceBonusRate || qualityRank(right.quality) - qualityRank(left.quality) || left.id - right.id);
}

export function selectedStoneBonus(stones: Item[], selectedStoneIds: number[]) {
  return selectedStoneIds.reduce((total, stoneId) => {
    const stone = stones.find((candidate) => candidate.id === stoneId);
    return total + (stone?.enhanceBonusRate ?? 0);
  }, 0);
}

export function selectedStoneCount(selectedStoneIds: number[], stoneId: number) {
  return selectedStoneIds.filter((id) => id === stoneId).length;
}

export function attributeName(attribute: string) {
  const names: Record<string, string> = {
    strength: '力量',
    agility: '敏捷',
    constitution: '体质',
    intelligence: '智力',
    spirit: '精神',
  };
  return names[attribute] ?? attribute;
}

export function liveStaminaSnapshot(stamina: StaminaView, elapsedSeconds: number): StaminaView {
  if (stamina.current >= stamina.max) {
    return { ...stamina, secondsUntilNext: 0, secondsUntilFull: 0 };
  }
  const elapsed = Math.max(0, elapsedSeconds);
  const secondsUntilFull = Math.max(0, stamina.secondsUntilFull - elapsed);
  if (secondsUntilFull <= 0) {
    return { ...stamina, current: stamina.max, secondsUntilNext: 0, secondsUntilFull: 0 };
  }
  return {
    ...stamina,
    secondsUntilNext: secondsUntilFull,
    secondsUntilFull,
  };
}

export function formatStaminaTime(seconds: number) {
  if (seconds <= 0) {
    return '已满';
  }
  const minutes = Math.floor(seconds / 60);
  const remain = seconds % 60;
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60);
    const extraMinutes = minutes % 60;
    return `${hours}h ${extraMinutes}m`;
  }
  return `${minutes}:${remain.toString().padStart(2, '0')}`;
}

export function bossArchetypeName(archetype: string) {
  const names: Record<string, string> = {
    boss: '首领',
    brute: '重击',
    skirmisher: '迅捷',
    caster: '施法',
    guardian: '守卫',
  };
  return names[archetype] ?? archetype;
}

export function uniqueDropTypes(drops: DropPreview[]) {
  return Array.from(new Set(drops.map((drop) => drop.itemType)))
    .sort((left, right) => dropTypeRank(left) - dropTypeRank(right));
}

export function combinedDropChance(drops: DropPreview[]) {
  return 1 - drops.reduce((missChance, drop) => missChance * (1 - Math.max(0, Math.min(1, drop.dropRate))), 1);
}

export function dropTypeRank(type: string) {
  const order = ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring'];
  const index = order.indexOf(type);
  return index === -1 ? order.length : index;
}

export function formatDropRate(rate: number) {
  const percent = rate * 100;
  if (percent > 0 && percent < 1) {
    return `${percent.toFixed(1)}%`;
  }
  return `${Math.round(percent)}%`;
}

export function formatPercent(rate: number) {
  return `${Math.round(rate * 1000) / 10}%`;
}

export function powerBreakdownForHome(home: HomeSnapshot): PowerBreakdownSlice[] {
  return [
    {
      key: 'base',
      label: '角色基础',
      value: home.powerBreakdown.basePower,
      detail: `Lv.${home.player.level} · 基础属性转化`,
    },
    {
      key: 'equipment',
      label: '装备贡献',
      value: home.powerBreakdown.equipmentPower,
      detail: `${Object.values(home.equippedItems).filter(Boolean).length}/9 件已穿戴`,
    },
    {
      key: 'skill',
      label: '技能训练',
      value: home.powerBreakdown.skillPower ?? 0,
      detail: '已学技能和技能阶级',
    },
    {
      key: 'synergy',
      label: '属性协同',
      value: home.powerBreakdown.synergyPower,
      detail: '装备带来的输出和生存效率',
    },
  ];
}

export function categoryName(category: string) {
  const names: Record<string, string> = {
    main: '主线',
    daily: '日常',
    achievement: '成就',
  };
  return names[category] ?? category;
}

export function statusName(status: string) {
  const names: Record<string, string> = {
    active: '进行中',
    completed: '可领取',
    claimed: '已领取',
    locked: '未解锁',
  };
  return names[status] ?? status;
}

export function rewardName(reward: { type: string; amount: number; itemName?: string; targetId?: string }) {
  const names: Record<string, string> = {
    gold: '金币',
    experience: '经验',
    itemTemplate: reward.itemName ?? '物品',
  };
  return `${names[reward.type] ?? reward.itemName ?? reward.targetId ?? reward.type} +${reward.amount}`;
}

export function conditionName(type: string) {
  const names: Record<string, string> = {
    dungeonCompleted: '通关副本',
    monsterKills: '击败怪物',
    equipmentEquipped: '穿戴装备',
    enhancementAttempts: '强化尝试',
    enhancementSuccesses: '强化成功',
    enhancementStoneUsed: '使用强化石',
    enhancementLevelReached: '强化等级',
    combatPowerReached: '战力达成',
    staminaSpent: '消耗疲劳',
    itemUsed: '使用道具',
    chestOpened: '开启宝箱',
    fragmentCrafted: '碎片合成',
    worldChatSent: '世界聊天',
    marketVisited: '访问市场',
    leaderboardViewed: '查看榜单',
  };
  return names[type] ?? type;
}

export function questSortScore(quest: QuestRow) {
  let score = 0;
  if (quest.claimable || quest.status === 'completed') {
    score += 10000;
  }
  if (quest.recommended) {
    score += 1000;
  }
  if (quest.status === 'active') {
    score += 500;
  }
  score += Math.min(100, quest.progressPercent ?? 0);
  if (quest.category === 'main') {
    score += 20;
  }
  if (quest.category === 'daily') {
    score += 10;
  }
  return score;
}

export function screenForQuestTarget(target?: string) {
  if (target === 'dungeonList') {
    return 'dungeons';
  }
  if (target === 'worldChat') {
    return 'chat';
  }
  if (target === 'market' || target === 'leaderboard' || target === 'inventory') {
    return target;
  }
  return 'home';
}

export function dungeonRisk(
  combatPower: number,
  minimumPower: number,
  playerLevel = 0,
  minimumLevel = 0,
  gate?: Dungeon['gate'],
) {
  if (!combatPower) {
    return { label: '读取中', level: 'unknown' };
  }
  if (gate && !gate.eligible) {
    if (gate.missingLevel > 0 && gate.missingPower > 0) {
      return { label: '未达标', level: 'deadly' };
    }
    if (gate.missingLevel > 0) {
      return { label: `差 ${gate.missingLevel} 级`, level: 'deadly' };
    }
    return { label: `差 ${formatNumber(gate.missingPower)}`, level: 'risky' };
  }
  const ratio = combatPower / Math.max(1, minimumPower);
  const levelGap = playerLevel > 0 && minimumLevel > 0 ? minimumLevel - playerLevel : 0;
  if (levelGap > 0) {
    return { label: `差 ${levelGap} 级`, level: 'deadly' };
  }
  if (ratio >= 1.2) {
    return { label: '碾压', level: 'safe' };
  }
  if (ratio >= 1.0) {
    return { label: '稳妥', level: 'normal' };
  }
  return { label: '危险', level: 'risky' };
}

export function dungeonMatchesLevelFilter(dungeon: Dungeon, filter: DungeonLevelFilter) {
  if (filter === 'all') {
    return true;
  }
  const [minLevel, maxLevel] = filter.split('-').map(Number);
  return dungeon.minimumLevel >= minLevel && dungeon.minimumLevel <= maxLevel;
}

export function isSpecialDungeon(dungeon: Dungeon) {
  return dungeon.id.startsWith('special_');
}

export function qualityName(quality: string) {
  const names: Record<string, string> = {
    common: '普通',
    uncommon: '优秀',
    rare: '稀有',
    epic: '史诗',
    legendary: '传说',
    immortal: '不朽',
  };
  return names[quality] ?? quality;
}

export function battleLogTone(log: string) {
  if (log.includes('倒下') || log.includes('中止') || log.includes('危险')) {
    return 'danger';
  }
  if (log.includes('不朽')) {
    return 'immortal-loot';
  }
  if (log.includes('传说') || log.includes('获得装备')) {
    return 'loot';
  }
  if (log.includes('击败') || log.includes('暴击')) {
    return 'hit';
  }
  return '';
}

export function battleEventName(frame: BattleFrame) {
  if (frame.skillName) {
    return frame.skillName;
  }
  if (frame.critical) {
    return '暴击';
  }
  if (frame.missed) {
    return '闪避';
  }
  const names: Record<string, string> = {
    hit: frame.actor === 'enemy' ? '受击' : '命中',
    miss: '闪避',
    crit: '暴击',
    phase: '机制',
    heal: '恢复',
    shield: '护盾',
    death: '击败',
  };
  return names[frame.eventType] ?? '记录';
}

export function itemMatchesCategory(item: Item, category: string) {
  if (category === 'all') {
    return true;
  }
  if (category === 'equipment') {
    return isEquipmentItem(item);
  }
  if (['consumable', 'material', 'chest'].includes(category)) {
    return (item.itemCategory ?? '') === category;
  }
  return itemTypesForCategory(category).includes(item.itemType);
}

export function itemTypesForCategory(category: string) {
  const map: Record<string, string[]> = {
    equipment: ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring'],
    weapon: ['weapon'],
    armor: ['helmet', 'armor', 'legs', 'boots', 'gloves'],
    accessory: ['necklace', 'ring'],
  };
  return map[category] ?? [];
}

export function categoryNameForInventory(category: string) {
  const names: Record<string, string> = {
    all: '全部',
    equipment: '装备',
    consumable: '消耗品',
    material: '材料',
    chest: '宝箱',
    weapon: '武器',
    armor: '防具',
    accessory: '饰品',
  };
  return names[category] ?? '全部';
}

export function shopCategoryName(category: string) {
  const names: Record<string, string> = {
    all: '全部',
    gold: '金币',
    stamina: '疲劳',
    enhancement: '强化',
    gem: '宝石',
    growth: '成长',
    chest: '宝箱',
  };
  return names[category] ?? category;
}

export function shopOfferRewardText(offer: ShopOffer) {
  if (offer.goldAmount > 0) {
    return `${formatNumber(offer.goldAmount)} 金`;
  }
  const quantity = offer.itemQuantity > 1 ? ` x${offer.itemQuantity}` : '';
  return `${offer.itemName ?? offer.name}${quantity}`;
}

export function shopPurchaseNotice(offer: ShopOffer, quantity: number, goldGained: number, rewards: Item[]) {
  const countText = quantity > 1 ? ` x${quantity}` : '';
  if (goldGained > 0) {
    return `购买 ${offer.name}${countText}，获得 ${formatNumber(goldGained)} 金。`;
  }
  if (offer.itemName) {
    const totalQuantity = Math.max(1, offer.itemQuantity * quantity);
    return `购买 ${offer.name}${countText}，获得 ${offer.itemName}${totalQuantity > 1 ? ` x${totalQuantity}` : ''}。`;
  }
  const rewardText = rewards.length > 0
    ? rewards.map((item) => `${equipmentDisplayName(item)}${item.quantity > 1 ? ` x${item.quantity}` : ''}`).join('、')
    : shopOfferRewardText(offer);
  return `购买 ${offer.name}${countText}，获得 ${rewardText}。`;
}

export function catalogSummary(items: ItemCatalogItem[]) {
  return {
    total: items.length,
    equipment: items.filter((item) => isEquipmentItem(item)).length,
    consumable: items.filter((item) => item.itemCategory === 'consumable').length,
    material: items.filter((item) => item.itemCategory === 'material').length,
    chest: items.filter((item) => item.itemCategory === 'chest').length,
    maxLevel: items.reduce((max, item) => Math.max(max, item.requiredLevel), 1),
  };
}

export function filterCatalogItems(items: ItemCatalogItem[], filters: {
  category: CatalogCategoryFilter;
  quality: CatalogQualityFilter;
  level: CatalogLevelFilter;
  sort: CatalogSortKey;
  search: string;
}) {
  const search = filters.search.trim().toLowerCase();
  return [...items]
    .filter((item) => filters.category === 'all' || (filters.category === 'equipment' ? isEquipmentItem(item) : item.itemCategory === filters.category))
    .filter((item) => filters.quality === 'all' || item.quality === filters.quality)
    .filter((item) => catalogMatchesLevel(item, filters.level))
    .filter((item) => {
      if (!search) {
        return true;
      }
      return [
        item.name,
        item.templateId,
        item.itemType,
        item.itemCategory,
        qualityName(item.quality),
        typeName(item.itemType),
        item.description,
        catalogCardText(item),
      ].join(' ').toLowerCase().includes(search);
    })
    .sort((left, right) => {
      if (filters.sort === 'level') {
        return right.requiredLevel - left.requiredLevel
          || qualityRank(right.quality) - qualityRank(left.quality)
          || left.itemType.localeCompare(right.itemType)
          || left.name.localeCompare(right.name);
      }
      if (filters.sort === 'type') {
        return left.itemCategory.localeCompare(right.itemCategory)
          || left.itemType.localeCompare(right.itemType)
          || right.requiredLevel - left.requiredLevel
          || qualityRank(right.quality) - qualityRank(left.quality)
          || left.name.localeCompare(right.name);
      }
      return qualityRank(right.quality) - qualityRank(left.quality)
        || right.requiredLevel - left.requiredLevel
        || left.itemType.localeCompare(right.itemType)
        || left.name.localeCompare(right.name);
    });
}

export function catalogMatchesLevel(item: ItemCatalogItem, filter: CatalogLevelFilter) {
  if (filter === 'all') {
    return true;
  }
  const [minLevel, maxLevel] = filter.split('-').map(Number);
  return item.requiredLevel >= minLevel && item.requiredLevel <= maxLevel;
}

export function catalogItemToDetail(item: ItemCatalogItem): EquipmentDetailData {
  return {
    templateId: item.templateId,
    name: item.name,
    displayName: item.name,
    itemType: item.itemType,
    itemCategory: item.itemCategory,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus ?? 0,
    sellPrice: item.sellPrice,
    quantity: item.stackable ? 1 : undefined,
    stackable: item.stackable,
    effectType: item.effectType ?? undefined,
    effectValueJson: item.effectValueJson ?? undefined,
    enhanceBonusRate: item.enhanceBonusRate,
    minEnhanceLevel: item.minEnhanceLevel,
    maxEnhanceLevel: item.maxEnhanceLevel,
    enhancementLevel: 0,
    enhancementLuck: 0,
    origin: catalogSourceHint(item),
    power: isEquipmentItem(item) ? catalogItemPower(item) : 0,
  };
}

export function catalogItemPower(item: ItemCatalogItem) {
  return itemPower({
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus ?? 0,
    enhancementLevel: 0,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
  });
}

export function catalogCardText(item: ItemCatalogItem) {
  if (isEquipmentItem(item)) {
    return bonusText(catalogItemToDetail(item));
  }
  return itemEffectText(catalogItemToDetail(item));
}

export function catalogIconForItem(item: ItemCatalogItem) {
  if (isEquipmentItem(item)) {
    return <Shield size={18} />;
  }
  if (item.itemCategory === 'consumable') {
    return <HeartPulse size={18} />;
  }
  if (item.itemCategory === 'material') {
    return <Gem size={18} />;
  }
  return <Package size={18} />;
}

export function catalogStatRows(item: ItemCatalogItem) {
  const rows = [
    ['攻击', item.attackBonus],
    ['防御', item.defenseBonus],
    ['抗性', item.resistanceBonus],
    ['生命', item.hpBonus],
    ['法力', item.mpBonus],
    ['暴击', item.critBonus ? `${Math.round(item.critBonus * 1000) / 10}%` : ''],
    ['随机浮动', item.randomRange > 0 ? `±${item.randomRange}` : ''],
  ] as const;
  return rows
    .filter(([, value]) => value !== 0 && value !== '')
    .map(([label, value]) => ({ label, value: typeof value === 'number' ? `+${formatNumber(value)}` : value }));
}

export function catalogEffectDetail(item: ItemCatalogItem) {
  const effect = parseEffectValue(catalogItemToDetail(item));
  const entries = Object.entries(effect);
  if (entries.length === 0) {
    return '';
  }
  return entries
    .map(([key, value]) => {
      if (key === 'attribute') {
        return `属性：${attributeName(String(value))}`;
      }
      if (key === 'amount') {
        return `数值：${value}`;
      }
      if (key === 'maxLevel') {
        return `等级上限：Lv.${value}`;
      }
      return `${key}：${value}`;
    })
    .join(' · ');
}

export function catalogSourceHint(item: ItemCatalogItem) {
  if (item.effectType === 'levelBoost') {
    return '冒险者商店限购一次';
  }
  if (item.itemCategory === 'chest') {
    return item.quality === 'legendary' || item.quality === 'immortal' ? '碎片合成与高难任务' : '任务奖励与副本掉落';
  }
  if (item.effectType === 'enhancementStone') {
    return '副本掉落、宝箱和任务奖励';
  }
  if (item.effectType === 'fragment') {
    return '日常活跃、宝箱与高阶副本';
  }
  if (item.itemCategory === 'consumable') {
    return '任务奖励、宝箱和冒险补给';
  }
  return item.quality === 'immortal' ? '血月裂隙等特殊副本' : '普通副本、宝箱和市场流转';
}

export function catalogUsageHint(item: ItemCatalogItem) {
  if (item.effectType === 'enhancementStone') {
    return `强化 +${item.minEnhanceLevel} 至 +${item.maxEnhanceLevel} 时可用，成功率 +${formatPercent(item.enhanceBonusRate)}`;
  }
  if (item.effectType === 'staminaPotion') {
    return '疲劳不足时使用，恢复值不会超过 1000。';
  }
  if (item.effectType === 'attributePotion') {
    return '使用后永久增加角色属性，适合优先补主属性。';
  }
  if (item.effectType === 'levelBoost') {
    return '使用后直升指定等级，并获得对应职业的整套等级装备。';
  }
  if (item.effectType === 'fragment') {
    return '积攒到配方数量后可合成传说或不朽装备宝箱。';
  }
  if (item.itemCategory === 'chest') {
    return '开启后按权重产出装备、材料或药水。';
  }
  return '用于提升角色战斗力，品质和等级越高基础价值越高。';
}

export function sortItems(items: Item[], sort: string) {
  const comparators: Record<string, (a: Item, b: Item) => number> = {
    level: (a, b) => b.requiredLevel - a.requiredLevel || qualityRank(b.quality) - qualityRank(a.quality) || a.id - b.id,
    type: (a, b) => a.itemType.localeCompare(b.itemType) || qualityRank(b.quality) - qualityRank(a.quality) || a.id - b.id,
    quality: (a, b) => qualityRank(b.quality) - qualityRank(a.quality) || b.requiredLevel - a.requiredLevel || a.id - b.id,
  };
  return [...items].sort(comparators[sort] ?? comparators.quality);
}

export function materialCount(items: Item[], templateId: string) {
  return items
    .filter((item) => item.templateId === templateId)
    .reduce((total, item) => total + Math.max(1, item.quantity ?? 1), 0);
}

export function materialName(templateId: string) {
  const names: Record<string, string> = {
    gold: '金币',
    mat_abyss_essence: '深渊精华',
    mat_tempering_shard: '淬炼碎片',
    mat_reforge_orb: '重铸宝珠',
    mat_socket_core: '打孔核心',
    mat_gem_dust: '宝石尘',
    mat_affix_lock: '锁词石',
    mat_ascension_core: '升阶核心',
    mat_ascension_guard: '护阶符',
  };
  return names[templateId] ?? templateId;
}

export function statName(stat?: string | null) {
  const names: Record<string, string> = {
    attack: '攻击',
    defense: '护甲',
    resistance: '抗性',
    hp: '生命',
    mp: '法力',
    crit: '暴击',
  };
  return stat ? names[stat] ?? stat : '属性';
}

export function formatStatValue(stat: string | null | undefined, value: number) {
  if (stat === 'crit') {
    return `${Math.round(value * 1000) / 10}%`;
  }
  return formatNumber(Math.round(value));
}

export function socketSlotsFor(entry: EquipmentProcessingSnapshot['equipment'][number]) {
  const existing = entry.item.sockets ?? [];
  return Array.from({ length: Math.max(entry.socketLimit, existing.length) }, (_, index) => {
    const socket = existing.find((item) => item.socketIndex === index);
    return socket ?? {
      socketIndex: index,
      unlocked: index < entry.unlockedSocketCount,
      gemItemId: null,
      gemTemplateId: null,
      gemName: null,
      gemQuality: null,
      statKey: null,
      statValue: 0,
      rank: 0,
    };
  });
}

export function missingMaterialParts(materials: Record<string, number>, costs: Array<{ id: string; quantity: number }>, gold = 0, goldCost = 0) {
  const missing: string[] = [];
  if (gold < goldCost) {
    missing.push(`金币缺 ${formatNumber(goldCost - gold)}`);
  }
  for (const cost of costs) {
    const owned = materials[cost.id] ?? 0;
    if (owned < cost.quantity) {
      missing.push(`${materialName(cost.id)}缺 ${cost.quantity - owned}`);
    }
  }
  return missing;
}

export function socketBlockReason(entry: EquipmentProcessingSnapshot['equipment'][number], materials: Record<string, number>, gold: number) {
  if (entry.socketLimit <= 0) {
    return '当前品质不能开孔。';
  }
  if (entry.unlockedSocketCount >= entry.socketLimit) {
    return '当前装备孔位已全部开启。';
  }
  const missing = missingMaterialParts(materials, [
    { id: 'mat_socket_core', quantity: entry.nextSocketCost.socketCores },
    { id: 'mat_gem_dust', quantity: entry.nextSocketCost.gemDust },
    { id: 'mat_abyss_essence', quantity: entry.nextSocketCost.essence },
  ], gold, entry.nextSocketCost.goldCost);
  return missing.length > 0 ? `无法开孔：${missing.join('，')}。` : null;
}

export function reforgeBlockReason(entry: EquipmentProcessingSnapshot['equipment'][number], lockedAffixIndexes: number[], materials: Record<string, number>, gold: number) {
  if (entry.affixLimit <= 0) {
    return '当前品质未解锁后期词条。';
  }
  const missing = missingMaterialParts(materials, [
    { id: 'mat_reforge_orb', quantity: entry.reforgeCost.orbs + lockedAffixIndexes.length },
    { id: 'mat_abyss_essence', quantity: entry.reforgeCost.essence + lockedAffixIndexes.length * 10 },
    { id: 'mat_affix_lock', quantity: lockedAffixIndexes.length },
  ], gold, entry.reforgeCost.goldCost);
  return missing.length > 0 ? `无法重铸：${missing.join('，')}。` : null;
}

export function ascendBlockReason(entry: EquipmentProcessingSnapshot['equipment'][number], materials: Record<string, number>, gold: number, useProtector: boolean) {
  if ((entry.item.ascensionLevel ?? 0) >= 5) {
    return '当前装备已达到升阶上限。';
  }
  const guardCost = useProtector && (entry.item.ascensionLevel ?? 0) >= 3 ? 1 : 0;
  const missing = missingMaterialParts(materials, [
    { id: 'mat_ascension_core', quantity: entry.ascensionCost.ascensionCores },
    { id: 'mat_abyss_essence', quantity: entry.ascensionCost.essence },
    { id: 'mat_tempering_shard', quantity: entry.ascensionCost.shards },
    { id: 'mat_ascension_guard', quantity: guardCost },
  ], gold, entry.ascensionCost.goldCost);
  return missing.length > 0 ? `无法升阶：${missing.join('，')}。` : null;
}

export function gemUpgradeBlockReason(gems: Item[], selectedIds: number[], materials: Record<string, number>) {
  if (selectedIds.length !== 3) {
    return '请选择 3 颗同类同阶宝石。';
  }
  const selected = selectedIds.map((id) => gems.find((gem) => gem.id === id)).filter((gem): gem is Item => Boolean(gem));
  if (selected.length !== 3 || selected.some((gem) => gem.templateId !== selected[0].templateId)) {
    return '只能选择 3 颗完全相同的宝石。';
  }
  const rank = gemRank(selected[0]);
  if (rank >= 9 || selected[0].templateId.endsWith('_9')) {
    return '该宝石已达到最高阶。';
  }
  const gemDustCost = 8 * rank;
  if ((materials.mat_gem_dust ?? 0) < gemDustCost) {
    return `无法升级：宝石尘不足，需要 ${gemDustCost}。`;
  }
  return null;
}

export function gemEffectText(gem: Item) {
  try {
    const parsed = JSON.parse(gem.effectValueJson || '{}');
    return `${statName(parsed.stat)} · ${parsed.kind ?? 'gem'} R${parsed.rank ?? 1}`;
  } catch {
    return '可镶嵌宝石';
  }
}

function gemRank(gem: Item) {
  try {
    const parsed = JSON.parse(gem.effectValueJson || '{}');
    const rank = Number(parsed.rank);
    if (Number.isFinite(rank) && rank > 0) {
      return rank;
    }
  } catch {
    // Fall through to the template suffix.
  }
  const match = gem.templateId.match(/_(\d+)$/);
  return match ? Number(match[1]) : 1;
}

export function processingActionName(actionType: string) {
  const names: Record<string, string> = {
    socket: '宝石镶嵌',
    unsocket: '取下宝石',
    gem_upgrade: '宝石升级',
    reforge: '词条重铸',
    ascend: '装备升阶',
  };
  return names[actionType] ?? actionType;
}

export function processingBonusSummary(item: EquipmentDetailData, kind: 'socket' | 'affix') {
  const prefix = kind === 'socket' ? 'socket' : 'affix';
  const parts = [
    ['攻击', item[`${prefix}AttackBonus` as keyof EquipmentDetailData]],
    ['防御', item[`${prefix}DefenseBonus` as keyof EquipmentDetailData]],
    ['抗性', item[`${prefix}ResistanceBonus` as keyof EquipmentDetailData]],
    ['生命', item[`${prefix}HpBonus` as keyof EquipmentDetailData]],
    ['法力', item[`${prefix}MpBonus` as keyof EquipmentDetailData]],
    ['暴击', item[`${prefix}CritBonus` as keyof EquipmentDetailData]],
  ].filter(([, value]) => typeof value === 'number' && value > 0)
    .map(([label, value]) => `${label}+${label === '暴击' ? formatStatValue('crit', Number(value)) : formatNumber(Number(value))}`);
  return parts.length > 0 ? parts.slice(0, 2).join(' · ') : '暂无';
}

export function refineCost(item: Pick<Item, 'requiredLevel' | 'refineLevel'>) {
  const nextLevel = (item.refineLevel ?? 0) + 1;
  return {
    gold: Math.max(1, item.requiredLevel) * 800 * nextLevel,
    essence: 12 * nextLevel * nextLevel,
    shards: Math.max(0, nextLevel - 2) * 3,
    orbs: nextLevel >= 5 ? 1 : 0,
  };
}

export function canRefine(item: Item, gold: number, essence: number, shards: number, orbs: number) {
  if ((item.refineLevel ?? 0) >= 5) {
    return false;
  }
  const cost = refineCost(item);
  return gold >= cost.gold && essence >= cost.essence && shards >= cost.shards && orbs >= cost.orbs;
}

export function refineBlockReason(item: Item, gold: number, essence: number, shards: number, orbs: number) {
  if ((item.refineLevel ?? 0) >= 5) {
    return '当前装备已达到淬炼上限。';
  }
  const cost = refineCost(item);
  const missing: string[] = [];
  if (gold < cost.gold) {
    missing.push(`金币缺 ${formatNumber(cost.gold - gold)}`);
  }
  if (essence < cost.essence) {
    missing.push(`深渊精华缺 ${cost.essence - essence}`);
  }
  if (shards < cost.shards) {
    missing.push(`淬炼碎片缺 ${cost.shards - shards}`);
  }
  if (orbs < cost.orbs) {
    missing.push(`重铸宝珠缺 ${cost.orbs - orbs}`);
  }
  return missing.length > 0 ? `无法淬炼：${missing.join('，')}。` : null;
}

export function qualityRank(quality: string) {
  const ranks: Record<string, number> = {
    immortal: 6,
    legendary: 5,
    epic: 4,
    rare: 3,
    uncommon: 2,
    common: 1,
  };
  return ranks[quality] ?? 0;
}

export function formatNumber(value: number) {
  return Number.isInteger(value) ? value.toString() : Math.round(value).toString();
}

export function formatChatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
}

export function pad2(value: number) {
  return value.toString().padStart(2, '0');
}

export function formatRelativeTime(value: string) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 8) {
    return '刚刚';
  }
  if (seconds < 60) {
    return `${seconds} 秒前`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} 分钟前`;
  }
  return `${Math.floor(minutes / 60)} 小时前`;
}

export function formatMarketActivityTime(activity: { createdAt?: string; minutesAgo?: number }) {
  if (activity.createdAt) {
    return formatRelativeTime(activity.createdAt);
  }
  if (typeof activity.minutesAgo === 'number') {
    return activity.minutesAgo <= 0 ? '刚刚' : `${activity.minutesAgo} 分钟前`;
  }
  return '刚刚';
}

export function isMarketableInventoryItem(item: Item) {
  if (item.effectType === 'levelBoost') {
    return false;
  }
  return item.itemCategory === 'equipment'
    || item.itemCategory === 'material'
    || item.itemCategory === 'consumable'
    || item.itemCategory === 'chest'
    || item.itemType === 'gem'
    || item.effectType === 'gem';
}

export function marketCategoryName(category?: string) {
  return ({
    equipment: '装备',
    gem: '宝石',
    material: '材料',
    consumable: '消耗品',
    chest: '宝箱',
  } as Record<string, string>)[category ?? ''] ?? '物品';
}

export function marketDisplayName(item: Pick<MarketListing['item'], 'name' | 'quantity' | 'enhancementLevel' | 'refineLevel' | 'ascensionLevel' | 'itemCategory'>) {
  const base = item.itemCategory === 'equipment' ? equipmentDisplayName(item) : item.name;
  return (item.quantity ?? 1) > 1 ? `${base} x${item.quantity}` : base;
}

export function marketItemSummary(item: MarketListing['item']) {
  if (item.marketCategory === 'equipment') {
    const statLine = [
      item.attackBonus > 0 ? `攻击 +${item.attackBonus}` : '',
      item.defenseBonus > 0 ? `防御 +${item.defenseBonus}` : '',
      item.resistanceBonus > 0 ? `抗性 +${item.resistanceBonus}` : '',
      item.hpBonus > 0 ? `生命 +${item.hpBonus}` : '',
      item.mpBonus > 0 ? `法力 +${item.mpBonus}` : '',
    ].filter(Boolean).join(' · ');
    return [statLine, item.processingSummary].filter(Boolean).join(' · ') || '可装备成长物品';
  }
  return item.description || item.effectType || '成长物资';
}

export function announcementKindName(kind: string) {
  const names: Record<string, string> = {
    loot: '高阶掉落',
    enhance: '强化突破',
    level: '等级提升',
    system: '世界通告',
  };
  return names[kind] ?? '世界通告';
}

export function robotActivityKindName(kind: string) {
  const names: Record<string, string> = {
    dungeon: '打副本',
    enhance: '强化装备',
    legendary: '高阶掉落',
    market_watch: '逛商会',
    market_list: '上架商品',
    market_buy: '采购商品',
    market_sell: '售出商品',
    watch: '围观',
    rest: '休息',
    build: '调整构筑',
    rift_refine: '深渊淬炼',
    processing_socket: '宝石镶嵌',
    processing_reforge: '词条重铸',
    processing_ascend: '装备升阶',
  };
  return names[kind] ?? '冒险活动';
}

export function rechargeReasonName(sourceAction: string) {
  const names: Record<string, string> = {
    player_wallet: '手动充值',
    robot_enhance: '强化补金',
    robot_market_buy: '市场补金',
  };
  return names[sourceAction] ?? '充值';
}

export function emptyMarketFilters(): MarketFilters {
  return {
    minLevel: '',
    maxLevel: '',
    category: 'all',
    itemType: 'all',
    quality: 'all',
    sort: 'listedAt',
  };
}

export function filterMarketListings(listings: MarketListing[], filters: MarketFilters) {
  const minLevel = filterNumber(filters.minLevel);
  const maxLevel = filterNumber(filters.maxLevel);
  return listings
    .filter((listing) => {
      const item = listing.item;
      return withinRange(item.requiredLevel, minLevel, maxLevel)
        && (filters.category === 'all' || item.marketCategory === filters.category)
        && (filters.itemType === 'all' || item.marketCategory !== 'equipment' || item.itemType === filters.itemType)
        && (filters.quality === 'all' || item.quality === filters.quality);
    })
    .sort((left, right) => {
      if (filters.sort === 'unitPrice') {
        return left.unitPrice - right.unitPrice
          || Date.parse(right.listedAt) - Date.parse(left.listedAt)
          || right.id - left.id;
      }
      if (filters.sort === 'price') {
        return left.price - right.price
          || Date.parse(right.listedAt) - Date.parse(left.listedAt)
          || right.id - left.id;
      }
      if (filters.sort === 'level') {
        return right.item.requiredLevel - left.item.requiredLevel
          || qualityRank(right.item.quality) - qualityRank(left.item.quality)
          || Date.parse(right.listedAt) - Date.parse(left.listedAt)
          || right.id - left.id;
      }
      if (filters.sort === 'quality') {
        return qualityRank(right.item.quality) - qualityRank(left.item.quality)
          || right.item.requiredLevel - left.item.requiredLevel
          || Date.parse(right.listedAt) - Date.parse(left.listedAt)
          || right.id - left.id;
      }
      return Date.parse(right.listedAt) - Date.parse(left.listedAt) || right.id - left.id;
    });
}

export function emptyRobotFilters(): RobotFilters {
  return {
    name: '',
    minGold: '',
    maxGold: '',
    minPower: '',
    maxPower: '',
    minLevel: '',
    maxLevel: '',
  };
}

export function filterRobots(robots: RobotActivityView[], filters: RobotFilters) {
  const name = filters.name.trim().toLowerCase();
  const minGold = filterNumber(filters.minGold);
  const maxGold = filterNumber(filters.maxGold);
  const minPower = filterNumber(filters.minPower);
  const maxPower = filterNumber(filters.maxPower);
  const minLevel = filterNumber(filters.minLevel);
  const maxLevel = filterNumber(filters.maxLevel);
  return robots.filter((robot) => {
    if (name && !`${robot.name} ${robot.title} ${professionName(robot.profession)}`.toLowerCase().includes(name)) {
      return false;
    }
    return withinRange(robot.gold, minGold, maxGold)
      && withinRange(robot.power, minPower, maxPower)
      && withinRange(robot.level, minLevel, maxLevel);
  });
}

export function filterNumber(value: string) {
  const normalized = value.replace(/[^\d]/g, '');
  return normalized ? Number(normalized) : null;
}

export function withinRange(value: number, min: number | null, max: number | null) {
  return (min === null || value >= min) && (max === null || value <= max);
}

export function buildToDraft(build: PlayerBuild): BuildDraft {
  const equipment: Record<string, number | null> = {};
  for (const slot of equipmentSlotOrder()) {
    equipment[slot] = build.equipmentSlots.find((item) => item.slotName === slot)?.itemId ?? null;
  }
  const skills = Array.from({ length: 6 }, (_, index) => {
    const slot = build.skillSlots.find((item) => item.slotIndex === index);
    return {
      slotIndex: index,
      triggerKind: slot?.triggerKind ?? defaultTriggerForIndex(index),
      skillId: slot?.skillId ?? '',
    };
  });
  return {
    name: build.name,
    strategy: build.strategy || 'balanced',
    refineFocus: build.refineFocus || 'balanced',
    equipment,
    skills,
    talents: [...build.talents],
  };
}

export function draftToRequest(draft: BuildDraft): BuildMutationRequest {
  return {
    name: draft.name,
    strategy: draft.strategy,
    refineFocus: draft.refineFocus,
    equipmentSlots: Object.entries(draft.equipment).map(([slotName, itemId]) => ({
      slotName,
      itemId,
      preferredItemType: slotName.startsWith('ring') ? 'ring' : slotName,
    })),
    skillSlots: draft.skills
      .filter((slot) => slot.skillId)
      .map((slot) => ({
        slotIndex: slot.slotIndex,
        triggerKind: slot.triggerKind,
        skillId: slot.skillId,
      })),
    talents: draft.talents,
  };
}

export function buildGapWarnings(build: PlayerBuild, draft: BuildDraft, snapshot: BuildSnapshot, preset: BuildPreset | null) {
  const warnings: string[] = [];
  const selectedEquipment = Object.entries(draft.equipment).filter(([, itemId]) => Boolean(itemId));
  if (selectedEquipment.length < 6) {
    warnings.push(`装备槽未满：已配置 ${selectedEquipment.length}/9 件，启用时只会应用已配置装备。`);
  }
  const learnedSkillIds = new Set(snapshot.availableSkills.filter((skill) => skill.learned).map((skill) => skill.id));
  const missingSkills = draft.skills
    .map((slot) => slot.skillId)
    .filter((skillId) => skillId && !learnedSkillIds.has(skillId))
    .map((skillId) => snapshot.availableSkills.find((skill) => skill.id === skillId)?.name ?? skillId);
  if (missingSkills.length > 0) {
    warnings.push(`未学技能会被战斗跳过：${missingSkills.join('、')}。`);
  }
  if (preset && draft.talents.length < 5) {
    warnings.push(`天赋还可激活 ${5 - draft.talents.length} 个节点。`);
  }
  if (!build.active && snapshot.activeBuild) {
    warnings.push(`启用后会替换当前构筑：${snapshot.activeBuild.name}。`);
  }
  return warnings;
}

export function buildSlotForItem(item: Item, draft: BuildDraft) {
  if (item.itemType !== 'ring') {
    return item.itemType;
  }
  if (draft.equipment.ring1 === item.id) {
    return 'ring1';
  }
  if (draft.equipment.ring2 === item.id) {
    return 'ring2';
  }
  return draft.equipment.ring1 ? 'ring2' : 'ring1';
}

export function assignBuildEquipment(equipment: Record<string, number | null>, slot: string, itemId: number) {
  const next: Record<string, number | null> = { ...equipment };
  for (const key of Object.keys(next)) {
    if (next[key] === itemId) {
      next[key] = null;
    }
  }
  next[slot] = itemId;
  return next;
}

export function updateDraftSkill(draft: BuildDraft, slotIndex: number, patch: Partial<BuildDraft['skills'][number]>) {
  return {
    ...draft,
    skills: draft.skills.map((slot) => slot.slotIndex === slotIndex ? { ...slot, ...patch } : slot),
  };
}

export function assignSkillToFirstSlot(draft: BuildDraft, skillId: string) {
  const existing = draft.skills.find((slot) => slot.skillId === skillId);
  if (existing) {
    return updateDraftSkill(draft, existing.slotIndex, { skillId: '' });
  }
  const target = draft.skills.find((slot) => !slot.skillId) ?? draft.skills[0];
  return updateDraftSkill(draft, target.slotIndex, { skillId });
}

export function toggleTalent(draft: BuildDraft, talent: BuildTalent) {
  if (draft.talents.includes(talent.nodeId)) {
    return { ...draft, talents: draft.talents.filter((nodeId) => nodeId !== talent.nodeId) };
  }
  if (draft.talents.length >= 5) {
    return draft;
  }
  return { ...draft, talents: [...draft.talents, talent.nodeId] };
}

export function defaultTriggerForIndex(index: number) {
  return ['opener', 'default', 'burst', 'execute', 'defensive', 'heal'][index] ?? 'default';
}

export function strategyName(strategy: string) {
  const names: Record<string, string> = {
    balanced: '均衡',
    aggressive: '进攻',
    survival: '生存',
    speed: '速刷',
  };
  return names[strategy] ?? strategy;
}

export function refineFocusName(focus: string) {
  const names: Record<string, string> = {
    balanced: '均衡词条',
    attack: '攻击词条',
    defense: '防御词条',
    resistance: '抗性词条',
    hp: '生命词条',
    mp: '法力词条',
    crit: '暴击词条',
  };
  return names[focus] ?? focus;
}

export function triggerName(trigger: string) {
  const names: Record<string, string> = {
    opener: '起手',
    default: '循环',
    burst: '爆发',
    execute: '斩杀',
    defensive: '防御',
    heal: '治疗',
  };
  return names[trigger] ?? trigger;
}

export function buildEquipmentStatLine(item: Item) {
  const parts = [];
  if (item.attackBonus > 0) {
    parts.push(`攻 +${item.attackBonus}`);
  }
  if (item.defenseBonus > 0) {
    parts.push(`防 +${item.defenseBonus}`);
  }
  if (item.resistanceBonus > 0) {
    parts.push(`抗 +${item.resistanceBonus}`);
  }
  if (item.hpBonus > 0) {
    parts.push(`命 +${item.hpBonus}`);
  }
  if ((item.critBonus ?? 0) > 0) {
    parts.push(`暴 +${Math.round((item.critBonus ?? 0) * 100)}%`);
  }
  return `${parts.join(' · ') || '基础属性'} · 战力 ${formatNumber(itemPower(item))}`;
}

export function targetEquipSlot(item: Item, equippedItems: Record<string, Item>) {
  if (item.itemType !== 'ring') {
    return item.itemType;
  }
  if (!equippedItems.ring1) {
    return 'ring1';
  }
  if (!equippedItems.ring2) {
    return 'ring2';
  }
  return 'ring1';
}

export function itemLocationLabel(item: Item, equippedItems: Record<string, Item>) {
  const equippedSlot = Object.entries(equippedItems).find(([, equippedItem]) => equippedItem.id === item.id)?.[0];
  return equippedSlot ? `已穿戴 · ${slotName(equippedSlot)}` : typeName(item.itemType);
}

export function isEquipmentUpgrade(item: Item, equippedItems: Record<string, Item>) {
  const targetSlot = targetEquipSlot(item, equippedItems);
  const currentItem = equippedItems[targetSlot];
  return itemPower(item) > (currentItem ? itemPower(currentItem) : 0);
}

export function toEquipmentDetail(item: Item): EquipmentDetailData {
  return {
    id: item.id,
    templateId: item.templateId,
    name: item.name,
    displayName: item.displayName,
    itemType: item.itemType,
    itemCategory: item.itemCategory,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus ?? 0,
    sellPrice: item.sellPrice,
    quantity: item.quantity,
    stackable: item.stackable,
    effectType: item.effectType,
    effectValueJson: item.effectValueJson,
    enhanceBonusRate: item.enhanceBonusRate,
    minEnhanceLevel: item.minEnhanceLevel,
    maxEnhanceLevel: item.maxEnhanceLevel,
    enhancementLevel: item.enhancementLevel,
    enhancementLuck: item.enhancementLuck,
    refineLevel: item.refineLevel,
    refineFocus: item.refineFocus,
    ascensionLevel: item.ascensionLevel,
    ascensionLuck: item.ascensionLuck,
    socketAttackBonus: item.socketAttackBonus,
    socketDefenseBonus: item.socketDefenseBonus,
    socketResistanceBonus: item.socketResistanceBonus,
    socketHpBonus: item.socketHpBonus,
    socketMpBonus: item.socketMpBonus,
    socketCritBonus: item.socketCritBonus,
    affixAttackBonus: item.affixAttackBonus,
    affixDefenseBonus: item.affixDefenseBonus,
    affixResistanceBonus: item.affixResistanceBonus,
    affixHpBonus: item.affixHpBonus,
    affixMpBonus: item.affixMpBonus,
    affixCritBonus: item.affixCritBonus,
    origin: originForItem(item.templateId),
    power: itemPower(item),
    description: item.description,
  };
}

export function dropToEquipmentDetail(drop: DropPreview): EquipmentDetailData {
  return {
    templateId: drop.templateId,
    name: drop.name,
    itemType: drop.itemType,
    itemCategory: drop.itemCategory,
    quality: drop.quality,
    requiredLevel: drop.requiredLevel,
    attackBonus: drop.attackBonus,
    defenseBonus: drop.defenseBonus,
    resistanceBonus: drop.resistanceBonus,
    hpBonus: drop.hpBonus,
    mpBonus: drop.mpBonus,
    sellPrice: drop.sellPrice,
    enhancementLevel: 0,
    origin: originForItem(drop.templateId),
    description: drop.description,
  };
}

export function leaderboardEquipmentToDetail(equipment: LeaderboardEquipment): EquipmentDetailData {
  return {
    templateId: equipment.templateId,
    name: equipment.name,
    displayName: equipment.name,
    itemType: equipment.slot.startsWith('ring') ? 'ring' : equipment.slot,
    quality: equipment.quality,
    requiredLevel: equipment.level,
    attackBonus: equipment.attackBonus,
    defenseBonus: equipment.defenseBonus,
    resistanceBonus: equipment.resistanceBonus,
    hpBonus: equipment.hpBonus,
    mpBonus: equipment.mpBonus,
    critBonus: equipment.critBonus,
    sellPrice: equipment.sellPrice,
    enhancementLevel: equipment.enhancementLevel,
    enhancementLuck: equipment.enhancementLuck,
    origin: equipmentOriginText(equipment),
    power: equipment.power,
  };
}

export function leaderboardEntryToSpeaker(entry: LeaderboardEntry): ChatSpeaker {
  return {
    playerId: entry.player ? 0 : null,
    name: entry.name,
    title: entry.title,
    kind: entry.player ? 'player' : 'robot',
    profession: entry.profession,
    level: entry.level,
    power: entry.power,
    experience: entry.experience,
    gold: entry.gold,
    strength: entry.strength,
    agility: entry.agility,
    constitution: entry.constitution,
    intelligence: entry.intelligence,
    spirit: entry.spirit,
    freePoints: entry.freePoints,
    derivedStats: entry.derivedStats,
    equipmentPower: entry.equipmentPower,
    equipment: entry.equipment ?? [],
  };
}

export function leaderboardEquipmentBonusText(item: LeaderboardEquipment) {
  const parts = [];
  if (item.attackBonus > 0) {
    parts.push(`攻击 +${item.attackBonus}`);
  }
  if (item.defenseBonus > 0) {
    parts.push(`防御 +${item.defenseBonus}`);
  }
  if (item.resistanceBonus > 0) {
    parts.push(`抗性 +${item.resistanceBonus}`);
  }
  if (item.hpBonus > 0) {
    parts.push(`生命 +${item.hpBonus}`);
  }
  if (item.mpBonus > 0) {
    parts.push(`法力 +${item.mpBonus}`);
  }
  return parts.join(' · ') || '基础装备';
}

export function marketItemSnapshotToDetail(item: MarketListing['item']): EquipmentDetailData {
  return {
    templateId: item.templateId,
    name: item.name,
    displayName: item.enhancementLevel > 0 ? `${item.name} +${item.enhancementLevel}` : item.name,
    itemType: item.itemType,
    itemCategory: item.itemCategory,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus,
    sellPrice: item.sellPrice,
    quantity: item.quantity,
    stackable: item.stackable,
    effectType: item.effectType,
    effectValueJson: item.effectValueJson,
    enhancementLevel: item.enhancementLevel,
    enhancementLuck: item.enhancementLuck,
    refineLevel: item.refineLevel,
    ascensionLevel: item.ascensionLevel,
    origin: item.origin,
    power: itemPower(item),
  };
}

export function marketItemToDetail(listing: MarketListing): EquipmentDetailData {
  return marketItemSnapshotToDetail(listing.item);
}

export function originForItem(templateId: string) {
  const tierMatch = templateId.match(/eq_t(\d+)/);
  const tier = tierMatch ? Number(tierMatch[1]) : 0;
  if (tier >= 37) {
    return '副本掉落 · 龙眠王庭';
  }
  if (tier >= 31) {
    return '副本掉落 · 星陨荒原';
  }
  if (tier >= 25) {
    return '副本掉落 · 黑曜山脉';
  }
  if (tier >= 19) {
    return '副本掉落 · 古堡回廊';
  }
  if (tier >= 7) {
    return '副本掉落 · 腐沼边境';
  }
  if (tier >= 1) {
    return '副本掉落 · 蛛影林地';
  }
  return '冒险者商会流通';
}

export function equipmentOriginText(item: Pick<LeaderboardEquipment, 'origin' | 'templateId'>) {
  if (!item.origin || item.origin.includes(item.templateId) || item.origin.includes('eq_')) {
    return originForItem(item.templateId);
  }
  return item.origin;
}

export function itemPower(item: Pick<EquipmentDetailData, 'attackBonus' | 'defenseBonus' | 'resistanceBonus' | 'hpBonus' | 'mpBonus' | 'critBonus' | 'enhancementLevel' | 'quality' | 'requiredLevel' | 'refineLevel' | 'ascensionLevel' | 'socketAttackBonus' | 'socketDefenseBonus' | 'socketResistanceBonus' | 'socketHpBonus' | 'socketMpBonus' | 'socketCritBonus' | 'affixAttackBonus' | 'affixDefenseBonus' | 'affixResistanceBonus' | 'affixHpBonus' | 'affixMpBonus' | 'affixCritBonus'>) {
  const level = item.enhancementLevel ?? 0;
  const refineLevel = item.refineLevel ?? 0;
  const ascensionLevel = item.ascensionLevel ?? 0;
  return Math.max(
    1,
    Math.round(
      processedStatValue(item.attackBonus, level, refineLevel, ascensionLevel, (item.socketAttackBonus ?? 0) + (item.affixAttackBonus ?? 0)) * 45
      + processedStatValue(item.defenseBonus, level, refineLevel, ascensionLevel, (item.socketDefenseBonus ?? 0) + (item.affixDefenseBonus ?? 0)) * 30
      + processedStatValue(item.resistanceBonus, level, refineLevel, ascensionLevel, (item.socketResistanceBonus ?? 0) + (item.affixResistanceBonus ?? 0)) * 30
      + processedStatValue(item.hpBonus, level, refineLevel, ascensionLevel, (item.socketHpBonus ?? 0) + (item.affixHpBonus ?? 0)) * 4
      + processedStatValue(item.mpBonus, level, refineLevel, ascensionLevel, (item.socketMpBonus ?? 0) + (item.affixMpBonus ?? 0)) * 2
      + processedCritValue(item.critBonus ?? 0, level, refineLevel, ascensionLevel, (item.socketCritBonus ?? 0) + (item.affixCritBonus ?? 0)) * 3000
      + level * 100
      + refineLevel * 130
      + ascensionLevel * 260
      + Math.max(1, item.requiredLevel) * 20
      + qualityRank(item.quality) * 60,
    ),
  );
}

export function processedStatValue(value: number, level: number, refineLevel: number, ascensionLevel: number, postBonus: number) {
  return Math.round((enhancedStatValue(value, level, refineLevel) + Math.max(0, postBonus)) * (1 + ascensionLevel * 0.02));
}

export function enhancedStatValue(value: number, level: number, refineLevel = 0) {
  if (value <= 0) {
    return 0;
  }
  let result = Math.round(value * (1 + level * 0.03 + refineLevel * 0.018));
  if (level >= 5) {
    result += Math.max(1, Math.floor(value / 10));
  }
  if (level >= 10) {
    result += Math.max(1, Math.floor(value / 8));
  }
  if (level >= 15) {
    result += Math.max(1, Math.floor(value / 5));
  }
  return result;
}

export function enhancedCritValue(value: number, level: number, refineLevel = 0) {
  let result = value * (1 + level * 0.03 + refineLevel * 0.018);
  if (level >= 10) {
    result += 0.01;
  }
  if (level >= 15) {
    result += 0.02;
  }
  return result;
}

export function processedCritValue(value: number, level: number, refineLevel: number, ascensionLevel: number, postBonus: number) {
  return (enhancedCritValue(value, level, refineLevel) + Math.max(0, postBonus)) * (1 + ascensionLevel * 0.02);
}

export function marketPriceEstimate(item: Pick<EquipmentDetailData, 'attackBonus' | 'defenseBonus' | 'resistanceBonus' | 'hpBonus' | 'mpBonus' | 'critBonus' | 'enhancementLevel' | 'quality' | 'sellPrice' | 'requiredLevel' | 'refineLevel' | 'ascensionLevel' | 'itemCategory' | 'itemType' | 'effectType'>) {
  if (item.itemCategory && item.itemCategory !== 'equipment') {
    const rank = qualityRank(item.quality);
    const category = item.itemType === 'gem' || item.effectType === 'gem' ? 'gem' : item.itemCategory;
    const multiplier = category === 'gem'
      ? 18 + rank * 5
      : category === 'material'
        ? 10 + rank * 3
        : category === 'chest'
          ? 16 + rank * 4
          : 9 + rank * 2;
    return Math.max(5, Math.round(item.sellPrice * multiplier / 2 + Math.max(1, item.requiredLevel) * Math.max(1, rank)));
  }
  const statScore = item.attackBonus * 16
    + item.defenseBonus * 12
    + item.resistanceBonus * 10
    + item.hpBonus / 2
    + item.mpBonus / 2
    + (item.critBonus ?? 0) * 1200
    + item.enhancementLevel * 80
    + (item.refineLevel ?? 0) * 140
    + (item.ascensionLevel ?? 0) * 260;
  const rank = qualityRank(item.quality);
  return Math.max(30, Math.round(item.sellPrice * 8 + statScore * 2 + item.requiredLevel * 35 + rank * rank * 55));
}

export function enhanceCost(item: Item) {
  const nextLevel = item.enhancementLevel + 1;
  return Math.max(1, item.requiredLevel) * Math.max(1, item.requiredLevel) * nextLevel * 10;
}

export function enhanceChance(item: Item) {
  const nextLevel = item.enhancementLevel + 1;
  const base = nextLevel <= 3 ? 1 : nextLevel <= 6 ? 0.8 : nextLevel <= 9 ? 0.6 : nextLevel <= 12 ? 0.4 : 0.2;
  return Math.min(0.95, base + item.enhancementLuck * 0.05);
}

export function bonusText(item: Item | EquipmentDetailData) {
  const parts = [
    item.attackBonus > 0 ? `攻击 +${item.attackBonus}` : '',
    item.defenseBonus > 0 ? `防御 +${item.defenseBonus}` : '',
    item.resistanceBonus > 0 ? `抗性 +${item.resistanceBonus}` : '',
    item.hpBonus > 0 ? `生命 +${item.hpBonus}` : '',
    item.mpBonus > 0 ? `法力 +${item.mpBonus}` : '',
    item.enhancementLevel > 0 ? `强化 +${item.enhancementLevel}` : '',
  ].filter(Boolean);
  return parts.join(' · ') || '基础装备';
}

export function dropBonusText(drop: DropPreview) {
  const parts = [
    drop.attackBonus > 0 ? `攻击 +${drop.attackBonus}` : '',
    drop.defenseBonus > 0 ? `防御 +${drop.defenseBonus}` : '',
    drop.resistanceBonus > 0 ? `抗性 +${drop.resistanceBonus}` : '',
    drop.hpBonus > 0 ? `生命 +${drop.hpBonus}` : '',
    drop.mpBonus > 0 ? `法力 +${drop.mpBonus}` : '',
  ].filter(Boolean);
  return parts.join(' · ') || '基础装备';
}
