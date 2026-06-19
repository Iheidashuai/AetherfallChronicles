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

export function RobotActivityScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [selectedRobot, setSelectedRobot] = useState<RobotActivityView | null>(null);
  const [filters, setFilters] = useState<RobotFilters>({
    name: '',
    minGold: '',
    maxGold: '',
    minPower: '',
    maxPower: '',
    minLevel: '',
    maxLevel: '',
  });
  const { data, isLoading, error } = useQuery({
    queryKey: ['robot-activity', token],
    queryFn: () => gameApi.robotActivity(token),
    refetchInterval: 5_000,
  });
  const robots = data?.robots ?? [];
  const filteredRobots = useMemo(() => filterRobots(robots, filters), [robots, filters]);
  const hasRobotFilters = Object.values(filters).some((value) => value.trim().length > 0);

  if (isLoading) {
    return <LoadingScreen title="读取机器人后台" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '机器人动态加载失败'} />;
  }

  const updateFilter = (key: RobotFilterKey, value: string) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };
  const activeCount = filteredRobots.filter((robot) => robot.currentActivityKind !== 'rest').length;
  const marketCount = filteredRobots.filter((robot) => robot.currentActivityKind.startsWith('market')).length;
  const totalGold = filteredRobots.reduce((sum, robot) => sum + robot.gold, 0);
  const totalRealMoney = filteredRobots.reduce((sum, robot) => sum + robot.realMoney, 0);
  const totalRecharge = filteredRobots.reduce((sum, robot) => sum + robot.rechargeRmb, 0);
  const peakPower = Math.max(...filteredRobots.map((robot) => robot.power), 0);

  return (
    <section className="screen robot-screen">
      <TopBar title="机器人后台" onBack={() => setScreen('home')} />
      <div className="stat-grid robot-stats status-strip">
        <Metric label="机器人" value={hasRobotFilters ? `${filteredRobots.length}/${data.robots.length}` : data.robots.length.toString()} />
        <Metric label="正在行动" value={activeCount.toString()} />
        <Metric label="商会相关" value={marketCount.toString()} />
        <Metric label="机器人金币" value={`${formatNumber(totalGold)} 金`} />
        <Metric label="真实余额" value={`${formatNumber(totalRealMoney)} 元`} />
        <Metric label="累计充值" value={`${formatNumber(totalRecharge)} 元`} />
        <Metric label="最高战力" value={formatNumber(peakPower)} />
      </div>
      <div className="robot-workbench desktop-workbench">
        <section className="robot-roster main-panel">
          <div className="robot-list-head">
            <SectionTitle icon={<Gauge size={18} />} title="机器人列表" />
            <span>点击机器人查看档案和个人历史动态</span>
          </div>
          <div className="robot-filter-panel">
            <label className="robot-filter-field name">
              <span>名称</span>
              <input value={filters.name} onChange={(event) => updateFilter('name', event.target.value)} placeholder="机器人名称" />
            </label>
            <RobotRangeFilter
              label="金币"
              minValue={filters.minGold}
              maxValue={filters.maxGold}
              onMinChange={(value) => updateFilter('minGold', value)}
              onMaxChange={(value) => updateFilter('maxGold', value)}
            />
            <RobotRangeFilter
              label="战力"
              minValue={filters.minPower}
              maxValue={filters.maxPower}
              onMinChange={(value) => updateFilter('minPower', value)}
              onMaxChange={(value) => updateFilter('maxPower', value)}
            />
            <RobotRangeFilter
              label="等级"
              minValue={filters.minLevel}
              maxValue={filters.maxLevel}
              onMinChange={(value) => updateFilter('minLevel', value)}
              onMaxChange={(value) => updateFilter('maxLevel', value)}
            />
            {hasRobotFilters && (
              <button className="mini-action subtle" onClick={() => setFilters(emptyRobotFilters())}>重置</button>
            )}
          </div>
          <div className="robot-grid">
            {filteredRobots.length === 0 && <EmptyState text="当前筛选下没有机器人。" />}
            {filteredRobots.map((robot) => (
              <RobotActivityCard key={robot.id} robot={robot} onSelect={() => setSelectedRobot(robot)} />
            ))}
          </div>
        </section>
      </div>
      {selectedRobot && <RobotActivityDetailModal token={token} robot={selectedRobot} onClose={() => setSelectedRobot(null)} />}
    </section>
  );
}

