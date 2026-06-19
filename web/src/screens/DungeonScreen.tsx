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

export function DungeonScreen({ token, onResult }: { token: string; onResult: (result: DungeonRunResult) => void }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<DungeonMode>('normal');
  const [clearFilter, setClearFilter] = useState<DungeonClearFilter>('all');
  const [riskFilter, setRiskFilter] = useState<DungeonRiskFilter>('all');
  const [levelFilter, setLevelFilter] = useState<DungeonLevelFilter>('all');
  const [selectedSpecialId, setSelectedSpecialId] = useState<string | null>(null);
  const [sweepResult, setSweepResult] = useState<DungeonSweepResult | null>(null);
  const [selectedLoot, setSelectedLoot] = useState<Item | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['dungeons', token],
    queryFn: () => gameApi.dungeons(token),
    staleTime: 0,
    refetchOnMount: 'always',
  });
  const homeQuery = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
    staleTime: 0,
    refetchOnMount: 'always',
  });
  const mutation = useMutation({
    mutationFn: (dungeonId: string) => gameApi.runDungeon(token, dungeonId),
    onSuccess: async (result) => {
      await invalidateGameQueries(queryClient, token);
      onResult(result);
    },
  });
  const sweepMutation = useMutation({
    mutationFn: (dungeonId: string) => gameApi.sweepDungeon(token, dungeonId, 10),
    onSuccess: async (result) => {
      setSweepResult(result);
      await invalidateGameQueries(queryClient, token);
    },
  });

  function refreshDungeonLobby() {
    void queryClient.invalidateQueries({ queryKey: ['dungeons', token] });
    void queryClient.invalidateQueries({ queryKey: ['home', token] });
  }

  function switchDungeonMode(nextMode: DungeonMode) {
    setMode(nextMode);
    refreshDungeonLobby();
  }

  if (isLoading) {
    return <LoadingScreen title="读取副本情报" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '副本加载失败'} />;
  }
  const combatPower = homeQuery.data?.combatPower ?? 0;
  const playerLevel = homeQuery.data?.player.level ?? 0;
  const stamina = homeQuery.data?.stamina ?? data.find((dungeon) => dungeon.stamina)?.stamina;
  const normalDungeons = data.filter((dungeon) => !isSpecialDungeon(dungeon));
  const specialDungeons = data.filter(isSpecialDungeon);
  const selectedSpecialDungeon = specialDungeons.find((dungeon) => dungeon.id === selectedSpecialId) ?? specialDungeons[0] ?? null;
  const visibleDungeons = normalDungeons.filter((dungeon) => {
    const risk = dungeonRisk(combatPower, dungeon.minimumPower, playerLevel, dungeon.minimumLevel, dungeon.gate).level as DungeonRiskFilter;
    const matchesClear = clearFilter === 'all' || (clearFilter === 'cleared' ? dungeon.cleared : !dungeon.cleared);
    const matchesRisk = riskFilter === 'all' || risk === riskFilter;
    const matchesLevel = dungeonMatchesLevelFilter(dungeon, levelFilter);
    return matchesClear && matchesRisk && matchesLevel;
  });

  return (
    <section className="screen dungeon-screen">
      <TopBar title="副本大厅" onBack={() => setScreen('home')} />
      <div className="status-strip dungeon-status-strip">
        {homeQuery.data && (
          <div className="battle-brief">
            <Gauge size={20} />
            <div>
              <span className="eyebrow">当前战力</span>
              <strong>{homeQuery.data.combatPower}</strong>
            </div>
          </div>
        )}
        <StaminaPanel stamina={stamina} compact />
        <Metric label="正常副本" value={normalDungeons.length.toString()} />
        <Metric label="特殊副本" value={specialDungeons.length.toString()} />
        <Metric label="当前结果" value={mode === 'normal' ? `${visibleDungeons.length}/${normalDungeons.length}` : specialDungeons.length.toString()} />
      </div>
      <div className="desktop-workbench dungeon-workbench">
        <aside className="filter-rail dungeon-filter-panel dungeon-filter-rail">
          <SectionTitle icon={<Filter size={18} />} title="副本筛选" />
          <div className="dungeon-mode-tabs">
            <button className={mode === 'normal' ? 'active' : ''} onClick={() => switchDungeonMode('normal')}>
              <Swords size={16} />
              正常副本
              <small>{normalDungeons.length}</small>
            </button>
            <button className={mode === 'special' ? 'active bloodmoon' : 'bloodmoon'} onClick={() => switchDungeonMode('special')}>
              <Sparkles size={16} />
              特殊副本
              <small>{specialDungeons.length}</small>
            </button>
          </div>
          {mode === 'normal' ? (
            <>
              <div className="filter-rail-group">
                <strong>通关状态</strong>
                <div className="segmented three">
                  {[
                    ['all', '全部'],
                    ['uncleared', '未通过'],
                    ['cleared', '已通过'],
                  ].map(([value, label]) => (
                    <button key={value} className={clearFilter === value ? 'active' : ''} onClick={() => {
                      setClearFilter(value as DungeonClearFilter);
                      refreshDungeonLobby();
                    }}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="filter-rail-group">
                <strong>风险区间</strong>
                <div className="risk-filter-row">
                  {[
                    ['all', '全部风险'],
                    ['safe', '碾压'],
                    ['normal', '稳妥'],
                    ['risky', '危险'],
                    ['deadly', '极危'],
                  ].map(([value, label]) => (
                    <button key={value} className={riskFilter === value ? `active ${value}` : value} onClick={() => {
                      setRiskFilter(value as DungeonRiskFilter);
                      refreshDungeonLobby();
                    }}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="filter-rail-group">
                <strong>等级段</strong>
                <div className="level-filter-row">
                  {[
                    ['all', '全部等级'],
                    ['1-30', 'Lv.1-30'],
                    ['31-60', 'Lv.31-60'],
                    ['61-90', 'Lv.61-90'],
                  ].map(([value, label]) => (
                    <button key={value} className={levelFilter === value ? 'active' : ''} onClick={() => {
                      setLevelFilter(value as DungeonLevelFilter);
                      refreshDungeonLobby();
                    }}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="filter-result-card">
                <span>当前匹配</span>
                <strong>{visibleDungeons.length}/{normalDungeons.length}</strong>
                <small>副本列表按门槛等级展示</small>
              </div>
            </>
          ) : (
            <div className="filter-result-card bloodmoon">
              <span>特殊副本</span>
              <strong>{specialDungeons.length}</strong>
              <small>传说与不朽装备核心来源，不开放扫荡。</small>
            </div>
          )}
        </aside>
        <section className="main-panel dungeon-main-panel">
          {mode === 'normal' ? (
            <>
              <div className="panel-head-row">
                <SectionTitle icon={<Swords size={18} />} title="正常副本列表" />
                <strong>{visibleDungeons.length}/{normalDungeons.length} 个副本</strong>
              </div>
              <div className="dungeon-list">
                {visibleDungeons.length === 0 && <EmptyState text="当前筛选下没有副本，换个风险档再看。" />}
                {visibleDungeons.map((dungeon) => (
                  <DungeonCard
                    key={dungeon.id}
                    dungeon={dungeon}
                    combatPower={combatPower}
                    playerLevel={playerLevel}
                    loading={mutation.isPending}
                    sweepLoading={sweepMutation.isPending}
                    onRun={() => mutation.mutate(dungeon.id)}
                    onSweep={() => sweepMutation.mutate(dungeon.id)}
                  />
                ))}
              </div>
            </>
          ) : (
            <SpecialDungeonPanel
              dungeons={specialDungeons}
              selectedDungeon={selectedSpecialDungeon}
              combatPower={combatPower}
              playerLevel={playerLevel}
              loading={mutation.isPending}
              onSelect={setSelectedSpecialId}
              onRun={(dungeonId) => mutation.mutate(dungeonId)}
            />
          )}
        </section>
      </div>
      {mutation.error && (
        <FeedbackDialog
          variant="error"
          title="进入副本失败"
          message={mutation.error.message}
          onClose={() => mutation.reset()}
        />
      )}
      {sweepMutation.error && (
        <FeedbackDialog
          variant="error"
          title="扫荡失败"
          message={sweepMutation.error.message}
          onClose={() => sweepMutation.reset()}
        />
      )}
      {sweepResult && <SweepSummaryModal result={sweepResult} onClose={() => setSweepResult(null)} onSelectLoot={setSelectedLoot} />}
      {selectedLoot && <ItemDetail item={toEquipmentDetail(selectedLoot)} onClose={() => setSelectedLoot(null)} />}
    </section>
  );
}

