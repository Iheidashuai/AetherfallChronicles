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

export function ArenaScreen({ token, onBattle }: { token: string; onBattle: (match: ArenaMatchDetail) => void }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ['arena', token],
    queryFn: () => gameApi.arena(token),
    refetchInterval: 8_000,
  });
  const challengeMutation = useMutation({
    mutationFn: (targetId: number) => gameApi.challengeArena(token, targetId),
    onSuccess: async (match) => {
      onBattle(match);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const buyMutation = useMutation({
    mutationFn: (offerId: string) => gameApi.buyArenaOffer(token, offerId),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
    },
  });
  const matchMutation = useMutation({
    mutationFn: (matchId: number) => gameApi.arenaMatch(token, matchId),
    onSuccess: onBattle,
  });

  if (isLoading) {
    return <LoadingScreen title="接入竞技场" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '竞技场加载失败'} />;
  }

  const attemptsLeft = Math.max(0, data.profile.dailyAttempts - data.profile.todayAttemptsUsed);

  return (
    <section className="screen arena-screen">
      <TopBar title="异步竞技场" onBack={() => setScreen('home')} />
      <div className="status-strip arena-status-strip">
        <div className="battle-brief">
          <Shield size={20} />
          <div>
            <span className="eyebrow">实时防守</span>
            <strong>{data.profile.tier} #{data.profile.rank || '-'}</strong>
          </div>
        </div>
        <Metric label="竞技积分" value={data.profile.rating.toString()} />
        <Metric label="竞技币" value={data.profile.arenaCoins.toString()} />
        <Metric label="今日次数" value={`${attemptsLeft}/${data.profile.dailyAttempts}`} />
        <Metric label="连胜" value={data.profile.winStreak.toString()} />
      </div>

      <div className="desktop-workbench arena-workbench">
        <aside className="filter-rail arena-rail">
          <SectionTitle icon={<UserRound size={18} />} title="我的段位" />
          <ArenaProfileCard profile={data.profile} featured />
          <SectionTitle icon={<ShoppingBag size={18} />} title="竞技币商店" />
          <div className="arena-shop-list">
            {data.shop.map((offer) => (
              <ArenaShopCard
                key={offer.id}
                offer={offer}
                loading={buyMutation.isPending}
                onBuy={() => buyMutation.mutate(offer.id)}
              />
            ))}
          </div>
        </aside>

        <section className="main-panel arena-main-panel">
          <div className="panel-head-row">
            <SectionTitle icon={<Swords size={18} />} title="推荐对手" />
            <strong>{data.opponents.length} 名可侦测目标</strong>
          </div>
          <div className="arena-opponent-grid">
            {data.opponents.length === 0 && <EmptyState text="暂无可挑战对手。" />}
            {data.opponents.map((opponent) => (
              <ArenaOpponentCard
                key={opponent.playerId}
                opponent={opponent}
                loading={challengeMutation.isPending}
                onChallenge={() => challengeMutation.mutate(opponent.playerId)}
              />
            ))}
          </div>
        </section>

        <aside className="detail-rail arena-detail-rail">
          <SectionTitle icon={<Clock3 size={18} />} title="最近战报" />
          <div className="arena-match-list">
            {data.recentMatches.length === 0 && <EmptyState text="还没有竞技场战报。" />}
            {data.recentMatches.map((match) => (
              <button key={match.matchId} className="arena-match-row" onClick={() => matchMutation.mutate(match.matchId)}>
                <strong>{match.attackerWon ? match.attackerName : match.defenderName}</strong>
                <span>{match.resultText}</span>
                <small>{formatSigned(match.attackerRatingChange)} / {match.arenaCoins} 币</small>
              </button>
            ))}
          </div>
          <SectionTitle icon={<Trophy size={18} />} title="竞技排行" />
          <div className="arena-ranking-list">
            {data.rankings.map((profile) => (
              <ArenaRankRow key={profile.playerId} profile={profile} />
            ))}
          </div>
        </aside>
      </div>

      {challengeMutation.error && (
        <FeedbackDialog variant="error" title="挑战失败" message={challengeMutation.error.message} onClose={() => challengeMutation.reset()} />
      )}
      {buyMutation.error && (
        <FeedbackDialog variant="error" title="兑换失败" message={buyMutation.error.message} onClose={() => buyMutation.reset()} />
      )}
      {matchMutation.error && (
        <FeedbackDialog variant="error" title="战报读取失败" message={matchMutation.error.message} onClose={() => matchMutation.reset()} />
      )}
    </section>
  );
}
