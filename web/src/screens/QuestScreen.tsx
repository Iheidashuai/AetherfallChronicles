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

export function QuestScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [category, setCategory] = useState<QuestCategoryFilter>('all');
  const [selectedQuestId, setSelectedQuestId] = useState<string | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['quests', token],
    queryFn: () => gameApi.quests(token),
  });
  const claimMutation = useMutation({
    mutationFn: (questId: string) => gameApi.claimQuest(token, questId),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
    },
  });
  const sortedQuests = useMemo(() => {
    if (!data) {
      return [];
    }
    return [...data].sort((left, right) => questSortScore(right) - questSortScore(left));
  }, [data]);
  const filteredQuests = useMemo(
    () => sortedQuests.filter((quest) => category === 'all' || quest.category === category),
    [category, sortedQuests],
  );
  const selectedQuest = filteredQuests.find((quest) => quest.id === selectedQuestId) ?? filteredQuests[0] ?? null;

  if (isLoading) {
    return <LoadingScreen title="读取任务档案" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '任务加载失败'} />;
  }

  return (
    <section className="screen quest-screen">
      <TopBar title="任务" onBack={() => setScreen('home')} />
      <div className="quest-tabs status-strip">
        <Metric label="可领取" value={data.filter((quest) => quest.status === 'completed').length.toString()} />
        <Metric label="进行中" value={data.filter((quest) => quest.status === 'active').length.toString()} />
        <Metric label="已领取" value={data.filter((quest) => quest.status === 'claimed').length.toString()} />
      </div>
      <div className="quest-workbench desktop-workbench">
        <aside className="quest-category-panel filter-rail">
          {(['all', 'main', 'daily', 'achievement'] as QuestCategoryFilter[]).map((value) => {
            const count = value === 'all' ? data.length : data.filter((quest) => quest.category === value).length;
            return (
              <button key={value} className={category === value ? 'active' : ''} onClick={() => setCategory(value)}>
                <span>{value === 'all' ? '全部' : categoryName(value)}</span>
                <strong>{count}</strong>
              </button>
            );
          })}
        </aside>
        <section className="quest-list-panel main-panel">
          <div className="inventory-main-title">
            <SectionTitle icon={<ScrollText size={18} />} title={`${category === 'all' ? '全部' : categoryName(category)}任务`} />
            <strong>{filteredQuests.length}</strong>
          </div>
          <div className="quest-list">
            {filteredQuests.length === 0 && <EmptyState text="当前分类没有任务。" />}
            {filteredQuests.map((quest) => (
              <QuestCard
                key={quest.id}
                quest={quest}
                selected={selectedQuest?.id === quest.id}
                loading={claimMutation.isPending}
                onSelect={() => setSelectedQuestId(quest.id)}
                onClaim={() => claimMutation.mutate(quest.id)}
                onNavigate={() => setScreen(screenForQuestTarget(quest.navigationTarget))}
              />
            ))}
          </div>
        </section>
        <QuestDetailPanel
          quest={selectedQuest}
          loading={claimMutation.isPending}
          onClaim={(questId) => claimMutation.mutate(questId)}
          onNavigate={(target) => setScreen(screenForQuestTarget(target))}
        />
      </div>
      {claimMutation.error && (
        <FeedbackDialog
          variant="error"
          title="领取失败"
          message={claimMutation.error.message}
          onClose={() => claimMutation.reset()}
        />
      )}
    </section>
  );
}

