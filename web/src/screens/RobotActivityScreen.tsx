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
  RobotSpeedView,
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
  const admin = useAppStore((state) => state.admin);
  const pendingWorldEventAction = useAppStore((state) => state.pendingWorldEventAction);
  const clearPendingWorldEventAction = useAppStore((state) => state.clearPendingWorldEventAction);
  const queryClient = useQueryClient();
  const [selectedRobot, setSelectedRobot] = useState<RobotActivityView | null>(null);
  const [highlightRobotId, setHighlightRobotId] = useState<number | null>(null);
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
  const speedQuery = useQuery({
    queryKey: ['robot-speed', token],
    queryFn: () => gameApi.robotSpeed(token),
    enabled: admin,
    refetchInterval: 5_000,
  });
  const speedMutation = useMutation({
    mutationFn: (multiplier: number) => gameApi.updateRobotSpeed(token, multiplier),
    onSuccess: (speed) => {
      queryClient.setQueryData(['robot-speed', token], speed);
      queryClient.invalidateQueries({ queryKey: ['robot-activity', token] });
    },
  });
  const robots = data?.robots ?? [];
  const filteredRobots = useMemo(() => filterRobots(robots, filters), [robots, filters]);
  const hasRobotFilters = Object.values(filters).some((value) => value.trim().length > 0);

  useEffect(() => {
    if (pendingWorldEventAction?.targetScreen !== 'robots') {
      return;
    }
    const robotId = Number(pendingWorldEventAction.params?.robotId ?? pendingWorldEventAction.targetId ?? 0);
    if (robotId > 0) {
      setHighlightRobotId(robotId);
    }
    clearPendingWorldEventAction();
  }, [pendingWorldEventAction, clearPendingWorldEventAction]);

  useEffect(() => {
    if (!highlightRobotId || selectedRobot) {
      return;
    }
    const robot = robots.find((entry) => entry.id === highlightRobotId);
    if (robot) {
      setSelectedRobot(robot);
    }
  }, [highlightRobotId, robots, selectedRobot]);

  if (isLoading) {
    return <LoadingScreen title="读取冒险者动态" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '冒险者动态加载失败'} />;
  }

  const updateFilter = (key: RobotFilterKey, value: string) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };
  const closeRobotDetail = () => {
    setSelectedRobot(null);
    setHighlightRobotId(null);
  };
  const activeCount = filteredRobots.filter((robot) => robot.currentActivityKind !== 'rest').length;
  const marketCount = filteredRobots.filter((robot) => robot.currentActivityKind.startsWith('market')).length;
  const totalGold = filteredRobots.reduce((sum, robot) => sum + robot.gold, 0);
  const totalRealMoney = filteredRobots.reduce((sum, robot) => sum + robot.realMoney, 0);
  const totalRecharge = filteredRobots.reduce((sum, robot) => sum + robot.rechargeRmb, 0);
  const peakPower = Math.max(...filteredRobots.map((robot) => robot.power), 0);

  return (
    <section className="screen robot-screen">
      <TopBar title="冒险者动态" onBack={() => setScreen('home')} />
      <div className="stat-grid robot-stats status-strip">
        <Metric label="冒险者" value={hasRobotFilters ? `${filteredRobots.length}/${data.robots.length}` : data.robots.length.toString()} />
        <Metric label="正在行动" value={activeCount.toString()} />
        <Metric label="商会相关" value={marketCount.toString()} />
        <Metric label="冒险者金币" value={`${formatNumber(totalGold)} 金`} />
        <Metric label="真实余额" value={`${formatNumber(totalRealMoney)} 元`} />
        <Metric label="累计充值" value={`${formatNumber(totalRecharge)} 元`} />
        <Metric label="最高战力" value={formatNumber(peakPower)} />
      </div>
      {admin && (
        <RobotSpeedAdminPanel
          speed={speedQuery.data}
          loading={speedQuery.isLoading}
          busy={speedMutation.isPending}
          error={(speedQuery.error as Error | null)?.message ?? (speedMutation.error as Error | null)?.message}
          onSetSpeed={(multiplier) => speedMutation.mutate(multiplier)}
        />
      )}
      <div className="robot-workbench desktop-workbench">
        <section className="robot-roster main-panel">
          <div className="robot-list-head">
            <SectionTitle icon={<Gauge size={18} />} title="冒险者名册" />
            <div className="robot-list-actions">
              <span>点击冒险者查看档案和个人历史动态</span>
              {admin && (
                <button className="mini-action admin-action" onClick={() => setScreen('ai-usage')}>
                  <Gauge size={14} /> AI 用量
                </button>
              )}
            </div>
          </div>
          <div className="robot-filter-panel">
            <label className="robot-filter-field name">
              <span>名称</span>
              <input value={filters.name} onChange={(event) => updateFilter('name', event.target.value)} placeholder="冒险者名称" />
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
            {filteredRobots.length === 0 && <EmptyState text="当前筛选下没有冒险者。" />}
            {filteredRobots.map((robot) => (
              <div key={robot.id} className={robot.id === highlightRobotId ? 'world-event-highlight' : undefined}>
                <RobotActivityCard robot={robot} onSelect={() => setSelectedRobot(robot)} />
              </div>
            ))}
          </div>
        </section>
      </div>
      {selectedRobot && <RobotActivityDetailModal token={token} robot={selectedRobot} onClose={closeRobotDetail} />}
    </section>
  );
}

function RobotSpeedAdminPanel({
  speed,
  loading,
  busy,
  error,
  onSetSpeed,
}: {
  speed?: RobotSpeedView;
  loading: boolean;
  busy: boolean;
  error?: string;
  onSetSpeed: (multiplier: number) => void;
}) {
  const current = speed?.multiplier ?? 1;
  const simulation = speed?.simulation;
  const options = [1, 5, 10];
  return (
    <section className="robot-speed-admin">
      <div className="robot-speed-head">
        <SectionTitle icon={<FastForward size={18} />} title="机器人加速" />
        <div className="robot-speed-actions">
          <div className="robot-speed-segment" aria-label="机器人加速倍率">
            {options.map((multiplier) => (
              <button
                key={multiplier}
                className={current === multiplier ? 'active' : ''}
                disabled={busy || loading}
                onClick={() => onSetSpeed(multiplier)}
              >
                {multiplier}x
              </button>
            ))}
          </div>
          {current !== 1 && (
            <button className="mini-action subtle" disabled={busy || loading} onClick={() => onSetSpeed(1)}>
              恢复 1x
            </button>
          )}
        </div>
      </div>
      <div className="robot-speed-status">
        <Metric label="当前" value={`${current}x · 全员调度`} />
        <Metric label="主 tick" value={tickText(speed?.robotTick)} />
        <Metric label="市场 pulse" value={tickText(speed?.marketPulse)} />
        <Metric label="跳过" value={`${(speed?.robotTick.skippedCount ?? 0) + (speed?.marketPulse.skippedCount ?? 0)} 次`} />
        <Metric label="规划 / 执行" value={simulation ? `${formatNumber(simulation.plannedCount)} / ${formatNumber(simulation.executedCount)}` : '等待'} />
        <Metric label="延迟 / 失败" value={simulation ? `${formatNumber(simulation.deferredCount)} / ${formatNumber(simulation.failedCount)}` : '等待'} />
        <Metric label="最近耗时" value={simulation ? `${formatNumber(simulation.totalMs)} ms` : '等待'} />
        <Metric label="DB 并发" value={simulation ? `${formatNumber(simulation.executionParallelism)}` : '等待'} />
        <Metric label="降载" value={simulation?.backpressureActive ? `${Math.round(simulation.budgetFactor * 100)}%` : '未触发'} />
      </div>
      <div className="robot-speed-notes">
        <span>模式：每 5 秒最多 400 名机器人参与规划，按预算限流执行</span>
        <span>影响：机器人行为、被动成长、市场周转、机器人挂单生命周期</span>
        <span>不影响：AI 聊天、玩家恢复、充值收入、系统时间</span>
      </div>
      {error && <p className="gate-hint">{error}</p>}
    </section>
  );
}

function tickText(tick?: RobotSpeedView['robotTick']) {
  if (!tick) {
    return '等待';
  }
  if (tick.running) {
    return '运行中';
  }
  if (!tick.lastFinishedAt && tick.skippedCount === 0) {
    return '等待';
  }
  return `${formatNumber(tick.lastActionCount)} 次 / ${formatNumber(tick.lastDurationMs)} ms`;
}
