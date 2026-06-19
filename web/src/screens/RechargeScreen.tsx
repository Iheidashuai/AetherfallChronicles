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

export function RechargeScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState('100');
  const { data, isLoading, error } = useQuery({
    queryKey: ['recharge-dashboard', token],
    queryFn: () => gameApi.rechargeDashboard(token),
    refetchInterval: 5_000,
  });
  const rechargeMutation = useMutation({
    mutationFn: (rmbAmount: number) => gameApi.recharge(token, rmbAmount),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
      await queryClient.invalidateQueries({ queryKey: ['recharge-dashboard', token] });
      await queryClient.invalidateQueries({ queryKey: ['robot-activity', token] });
      await queryClient.invalidateQueries({ queryKey: ['market', token] });
    },
  });

  if (isLoading) {
    return <LoadingScreen title="读取充值经济" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '充值后台加载失败'} />;
  }

  const rmbAmount = Math.max(0, Math.floor(Number(amount) || 0));
  const canRecharge = rmbAmount > 0 && rmbAmount <= data.wallet.realMoney;
  const quickAmounts = [1, 10, 100, 1000, 10000, 100000];
  const robotRecharges = data.robotRecharges.slice(0, 80);
  const incomeEvents = data.incomeEvents.slice(0, 60);

  return (
    <section className="screen recharge-screen">
      <TopBar title="充值经济" onBack={() => setScreen('home')} />
      <div className="stat-grid recharge-stats status-strip">
        <Metric label="我的余额" value={`${formatNumber(data.wallet.realMoney)} 元`} />
        <Metric label="我的金币" value={`${formatNumber(data.wallet.gold)} 金`} />
        <Metric label="财富标签" value={`${data.wallet.wealthTierCode} ${data.wallet.wealthTier}`} />
        <Metric label="兑换比例" value={`1:${formatNumber(data.wallet.exchangeRate)}`} />
        <Metric label="总人民币" value={`${formatNumber(data.totals.totalRmb)} 元`} />
        <Metric label="总兑换金币" value={`${formatNumber(data.totals.totalGold)} 金`} />
        <Metric label="市场挂单金币" value={`${formatNumber(data.totals.marketListedGold)} 金`} />
        <Metric label="机器人余额" value={`${formatNumber(data.totals.robotRealMoney)} 元`} />
      </div>

      <div className="recharge-workbench">
        <section className="recharge-wallet-panel">
          <SectionTitle icon={<Coins size={18} />} title="我的充值" />
          <div className="recharge-wallet-hero">
            <div>
              <span className="eyebrow">{data.wallet.wealthTier}</span>
              <h1>{formatNumber(data.wallet.realMoney)} 元</h1>
              <p>每 3 分钟到账 {formatNumber(data.wallet.minIncome)}-{formatNumber(data.wallet.maxIncome)} 元</p>
            </div>
            <strong>{formatNumber(data.wallet.gold)} 金</strong>
          </div>
          <div className="recharge-form">
            <input
              inputMode="numeric"
              value={amount}
              onChange={(event) => setAmount(event.target.value.replace(/[^\d]/g, ''))}
            />
            <button
              className="primary-action"
              disabled={!canRecharge || rechargeMutation.isPending}
              onClick={() => rechargeMutation.mutate(rmbAmount)}
            >
              充值
            </button>
          </div>
          <div className="recharge-quick-row">
            {quickAmounts.map((value) => (
              <button
                key={value}
                className={rmbAmount === value ? 'active' : ''}
                disabled={value > data.wallet.realMoney}
                onClick={() => setAmount(String(value))}
              >
                {formatNumber(value)}
              </button>
            ))}
          </div>
          <div className="recharge-preview">
            <span>可兑换</span>
            <strong>{formatNumber(rmbAmount * data.wallet.exchangeRate)} 金</strong>
          </div>
        </section>

        <section className="recharge-tier-panel">
          <SectionTitle icon={<Gauge size={18} />} title="财富金字塔" />
          <div className="recharge-tier-list">
            {data.tierStats.map((tier) => (
              <WealthTierCard key={`${tier.wealthTierLevel}-${tier.wealthTier}`} tier={tier} />
            ))}
          </div>
        </section>

        <section className="recharge-ledger-panel">
          <SectionTitle icon={<ScrollText size={18} />} title="机器人充值明细" />
          <div className="recharge-ledger-list">
            {robotRecharges.length === 0 && <EmptyState text="暂时没有机器人充值记录。" />}
            {robotRecharges.map((row) => (
              <RobotRechargeCard key={row.id} row={row} />
            ))}
          </div>
        </section>

        <aside className="recharge-income-panel">
          <SectionTitle icon={<Clock3 size={18} />} title="发钱记录" />
          <div className="recharge-income-list">
            {incomeEvents.length === 0 && <EmptyState text="等待下一轮 3 分钟发钱。" />}
            {incomeEvents.map((row) => (
              <CashIncomeCard key={row.id} row={row} />
            ))}
          </div>
        </aside>
      </div>

      {rechargeMutation.data && (
        <FeedbackDialog
          variant="success"
          title="充值成功"
          message={`花费 ${formatNumber(rechargeMutation.data.rmbAmount)} 元，获得 ${formatNumber(rechargeMutation.data.goldAmount)} 金。`}
          onClose={() => rechargeMutation.reset()}
        />
      )}
      {rechargeMutation.error && (
        <FeedbackDialog
          variant="error"
          title="充值失败"
          message={rechargeMutation.error.message}
          onClose={() => rechargeMutation.reset()}
        />
      )}
    </section>
  );
}

