import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowUp,
  Backpack,
  Bell,
  Boxes,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Clock3,
  Coins,
  FastForward,
  Filter,
  Gem,
  Gauge,
  Hammer,
  HeartPulse,
  LogOut,
  MessageCircle,
  Package,
  Repeat2,
  ScrollText,
  Send,
  Shield,
  Search,
  Skull,
  ShoppingBag,
  SkipForward,
  Sparkles,
  Swords,
  Trophy,
  UserRound,
} from 'lucide-react';
import { authApi, gameApi } from '../api';
import type {
  BattleFrame,
  ArenaMatchDetail,
  ArenaOpponent,
  ArenaProfile,
  ArenaShopOffer,
  BuildActivationResult,
  BuildMutationRequest,
  BuildPreset,
  BuildScore,
  BuildSnapshot,
  BuildTalent,
  PlayerBuild,
  ChatMessage,
  ChatSpeaker,
  Dungeon,
  DungeonRunResult,
  DungeonSweepResult,
  DropPreview,
  EquipmentProcessingResult,
  EquipmentProcessingSnapshot,
  GlobalAnnouncement,
  HomeSnapshot,
  InventorySnapshot,
  Item,
  ItemCatalogItem,
  DerivedStats,
  LeaderboardEntry,
  LeaderboardEquipment,
  MarketListing,
  MarketSale,
  QuestRow,
  CashIncomeRow,
  RobotRechargeRow,
  RobotActivityDetail,
  RobotActivityEvent,
  RobotActivityView,
  RiftRunResult,
  RiftSimulationResult,
  RiftSnapshot,
  ShopOffer,
  SkillSnapshot,
  SkillView,
  WealthTierStat,
} from '../api';
import { useAppStore } from '../store';
import type {
  InventoryAction,
  FeedbackVariant,
  DungeonMode,
  DungeonClearFilter,
  DungeonRiskFilter,
  DungeonLevelFilter,
  QuestCategoryFilter,
  LeaderboardMetric,
  ProfessionFilter,
  MarketSortKey,
  CatalogCategoryFilter,
  CatalogQualityFilter,
  CatalogLevelFilter,
  StaminaView,
  CatalogSortKey,
  MarketItemTypeFilter,
  MarketCategoryFilter,
  MarketQualityFilter,
  MarketLedgerTab,
  ShopCategoryFilter,
  RobotFilterKey,
  ForgeView,
  BuildTab,
  BuildDraft,
  PowerBreakdownSlice,
  RobotFilters,
  PlayableProfession,
  AttributeKey,
  MarketFilters,
  EquipmentDetailData,
} from '../types';
import {
  STAMINA_RECOVERY_SECONDS,
  ANNOUNCEMENT_SEEN_STORAGE_KEY,
  ATTRIBUTE_LABELS,
  CREATE_PROFESSIONS,
} from '../lib/constants';
import {
  announcementKindName, announcementSeenKey, ascendBlockReason, assignBuildEquipment, 
  assignSkillToFirstSlot, attributeName, battleEventName, battleFramesForResult, 
  battleLogTone, bonusText, bossArchetypeName, buildEquipmentStatLine, buildGapWarnings, 
  buildSlotForItem, buildToDraft, canRefine, catalogCardText, catalogEffectDetail, 
  catalogIconForItem, catalogItemPower, catalogItemToDetail, catalogMatchesLevel, 
  catalogSourceHint, catalogStatRows, catalogSummary, catalogUsageHint, categoryName, 
  categoryNameForInventory, chatAvatarLabel, combinedDropChance, conditionName, 
  defaultTriggerForIndex, draftToRequest, dropBonusText, dropTypeRank, 
  dungeonMatchesLevelFilter, dungeonRisk, effectNumber, emptyMarketFilters, 
  emptyRobotFilters, enhanceChance, enhanceCost, enhancedCritValue, enhancedStatValue, 
  enhancementBadgeText, enhancementEffectClass, enhancementStage, enhancementStonesForItem, 
  equipmentCompactText, equipmentDisplayName, equipmentOriginText, equipmentSlotOrder, 
  equipmentSlotPairs, filterCatalogItems, filterMarketListings, filterNumber, filterRobots, 
  formatChatTime, formatDropRate, formatMarketActivityTime, formatNumber, formatPercent, 
  formatRelativeTime, formatSigned, formatStaminaTime, formatStatValue, gemEffectText, 
  gemUpgradeBlockReason, homeSlotShortName, inventoryTemplateQuantity, isEquipmentItem, 
  isEquipmentUpgrade, isMarketableInventoryItem, isSpecialDungeon, itemCategoryLabel, 
  itemEffectText, itemLocationLabel, itemMatchesCategory, itemPower, itemTypesForCategory, 
  leaderboardEntryToSpeaker, leaderboardEquipmentBonusText, leaderboardEquipmentToDetail, 
  leaderboardMainAttribute, leaderboardMetricName, leaderboardMetricValue, 
  leaderboardScoreText, leaderboardSecondaryStats, liveStaminaSnapshot, marketCategoryName, 
  marketDisplayName, marketItemSnapshotToDetail, marketItemSummary, marketItemToDetail, 
  marketPriceEstimate, materialCount, materialName, missingMaterialParts, orderedEquipment, 
  originForItem, pad2, parseEffectValue, powerBreakdownForHome, processedCritValue, 
  processedStatValue, processingActionName, processingBonusSummary, professionName, 
  qualityName, qualityRank, questSortScore, rankLeaderboardEntries, 
  readSeenAnnouncementKeys, rechargeReasonName, refineBlockReason, refineCost, 
  refineFocusName, reforgeBlockReason, rewardName, robotActivityKindName, 
  screenForQuestTarget, selectedStoneBonus, selectedStoneCount, shopCategoryName, 
  shopOfferRewardText, shopPurchaseNotice, skillCategoryName, skillTriggerName, 
  skillValueLabel, slotName, socketBlockReason, socketSlotsFor, sortItems, statName, 
  statusName, strategyName, targetEquipSlot, toEquipmentDetail, toggleTalent, triggerName, 
  typeName, uniqueDropTypes, updateDraftSkill, withinRange, writeSeenAnnouncementKeys
} from '../lib/helpers';


import {
  GlobalTicker, ProfessionGlyph, InfoPanel, SkillCard, SpecialDungeonPanel, EquipmentProcessingPanel, RiftResultPanel, ArenaProfileCard, ArenaOpponentCard, ArenaShopCard, ArenaMatchPanel, ArenaFighterCard, ArenaRankRow, BuildScorePanel, BuildSimulationPanel, WealthTierCard, RobotRechargeCard, CashIncomeCard, RobotRangeFilter, RobotActivityCard, RobotEventCard, RobotActivityDetailModal, ChatMessageBubble, SpeakerDetailModal, DungeonCard, DropPreviewCard, HomeEquipmentOverview, EquipmentPanel, PowerBreakdownPanel, CombatStatsPanel, StatContributionGrid, CombatStatCell, StatContributionCell, QuestCard, QuestDetailPanel, EnhancementBadge, ItemCard, TransferItemOption, ItemDetail, EquipConfirmModal, CompareCard, EnhanceModal, SellConfirmModal, CombatantCard, HpBar, ResultSummaryModal, SweepSummaryModal, MarketSaleCard, MarketListedCard, ListingCard, LeaderboardCard, CatalogFilterGroup, CatalogDetailPanel, Metric, StaminaPanel, NavTile, SectionTitle, EmptyState, FeedbackDialog, ConfirmDialog, TopBar, LoadingScreen, ErrorScreen,
  invalidateGameQueries,
} from '../components/ui';

export function CharacterScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [selectedEquipment, setSelectedEquipment] = useState<Item | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
  });

  if (isLoading) {
    return <LoadingScreen title="读取角色属性" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '角色加载失败'} />;
  }

  const player = data.player;
  const equippedItems = Object.values(data.equippedItems).filter(Boolean);
  const powerBreakdown = powerBreakdownForHome(data);
  const topEquipment = [...equippedItems]
    .sort((left, right) => itemPower(right) - itemPower(left))
    .slice(0, 3);

  return (
    <section className="screen character-screen profile-screen">
      <TopBar title="角色档案" onBack={() => setScreen('home')} />
      <div className="profile-overview">
        <div className="profile-identity">
          <div className="profile-avatar">
            <ProfessionGlyph profession={player.profession as PlayableProfession} size={30} />
          </div>
          <div>
            <span className="eyebrow">Lv.{player.level} · {professionName(player.profession)}</span>
            <h1>{player.name}</h1>
            <p>已穿戴 {equippedItems.length}/{equipmentSlotOrder().length} · 金币 {formatNumber(player.gold)}</p>
          </div>
        </div>
        <div className="profile-power-total">
          <span>总战力</span>
          <strong>{formatNumber(data.combatPower)}</strong>
        </div>
      </div>

      <div className="profile-layout">
        <section className="profile-stat-panel">
          <div className="profile-panel-head">
            <SectionTitle icon={<UserRound size={18} />} title="基础属性" />
            <span>{player.freePoints} 自由点</span>
          </div>
          <div className="profile-metric-grid">
            <Metric label="经验" value={formatNumber(player.experience)} />
            <Metric label="生命" value={formatNumber(data.maxHp)} />
            <Metric label="法力" value={formatNumber(data.maxMp)} />
            <Metric label="力量" value={player.strength.toString()} />
            <Metric label="敏捷" value={player.agility.toString()} />
            <Metric label="体质" value={player.constitution.toString()} />
            <Metric label="智力" value={player.intelligence.toString()} />
            <Metric label="精神" value={player.spirit.toString()} />
          </div>
          <div className="profile-panel-head compact">
            <SectionTitle icon={<Gauge size={18} />} title="战斗属性" />
          </div>
          <StatContributionGrid stats={data.derivedStats} baseStats={data.baseStats} equipmentStats={data.equipmentStats} />
        </section>

        <aside className="profile-power-panel">
          <div className="profile-panel-head">
            <SectionTitle icon={<Gauge size={18} />} title="战力来源" />
            <span>{formatNumber(data.combatPower)}</span>
          </div>
          <PowerBreakdownPanel slices={powerBreakdown} total={data.combatPower} />
          <div className="profile-equipment-source">
            <span>装备估值</span>
            <strong>{formatNumber(data.equipmentPower)}</strong>
            <small>{topEquipment.map((item) => `${typeName(item.itemType)} ${formatNumber(itemPower(item))}`).join(' · ') || '暂无装备'}</small>
          </div>
        </aside>
      </div>

      <EquipmentPanel home={data} onSelect={setSelectedEquipment} />
      {selectedEquipment && <ItemDetail item={toEquipmentDetail(selectedEquipment)} onClose={() => setSelectedEquipment(null)} />}
    </section>
  );
}

