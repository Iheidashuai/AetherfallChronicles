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
  WorldEvent,
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

export function HomeScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const setPendingWorldEventAction = useAppStore((state) => state.setPendingWorldEventAction);
  const logout = useAppStore((state) => state.logout);
  const [selectedEquipment, setSelectedEquipment] = useState<Item | null>(null);
  const { data, error, isLoading } = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
  });
  const worldEventsQuery = useQuery({
    queryKey: ['world-events', token],
    queryFn: () => gameApi.worldEvents(token),
    refetchInterval: 10_000,
  });

  if (isLoading) {
    return <LoadingScreen title="同步角色档案" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '主页加载失败'} />;
  }

  const primaryActions = [
    { icon: <Swords size={20} />, title: '副本', detail: `${data.config.dungeonCount} 个副本`, onClick: () => setScreen('dungeons') },
    { icon: <Hammer size={20} />, title: '铁匠铺', detail: '强化 · 转移', onClick: () => setScreen('blacksmith') },
    { icon: <ShoppingBag size={20} />, title: '市场', detail: '寄售 · 购买', onClick: () => setScreen('market') },
    { icon: <Trophy size={20} />, title: '公会', detail: 'Boss · 周榜', onClick: () => setScreen('guild') },
    { icon: <Trophy size={20} />, title: '榜单', detail: '战力排名', onClick: () => setScreen('leaderboard') },
    { icon: <Gauge size={20} />, title: '动态', detail: '冒险者动态', onClick: () => setScreen('robots') },
  ];
  const secondaryActions = [
    { icon: <Skull size={20} />, title: '深渊', detail: 'Endgame 裂隙', onClick: () => setScreen('endgame') },
    { icon: <Shield size={20} />, title: '竞技场', detail: '异步 PVP', onClick: () => setScreen('arena') },
    { icon: <Backpack size={20} />, title: '背包', detail: '穿戴 · 出售', onClick: () => setScreen('inventory') },
    { icon: <ScrollText size={20} />, title: '任务', detail: '主线 · 日常', onClick: () => setScreen('quests') },
    { icon: <Sparkles size={20} />, title: '技能', detail: `技能战力 ${data.powerBreakdown.skillPower ?? 0}`, onClick: () => setScreen('skills') },
    { icon: <Filter size={20} />, title: '构筑', detail: '流派配置', onClick: () => setScreen('builds') },
    { icon: <Coins size={20} />, title: '充值', detail: data.player.wealthTier, onClick: () => setScreen('recharge') },
    { icon: <MessageCircle size={20} />, title: '聊天', detail: '世界频道', onClick: () => setScreen('chat') },
    { icon: <Package size={20} />, title: '商店', detail: '余额补给', onClick: () => setScreen('shop') },
    { icon: <Boxes size={20} />, title: '物品', detail: `${data.config.itemCount} 种图鉴`, onClick: () => setScreen('item-catalog') },
    { icon: <UserRound size={20} />, title: '角色', detail: '属性档案', onClick: () => setScreen('character') },
  ];

  return (
    <section className="screen home-screen">
      <div className="home-command-bar">
        <div className="hero-status">
          <div>
            <span className="eyebrow">Lv.{data.player.level} {professionName(data.player.profession)}</span>
            <h1>{data.player.name}</h1>
          </div>
          <button className="icon-button" onClick={logout} aria-label="退出登录">
            <LogOut size={18} />
          </button>
        </div>

        <div className="home-status-grid">
          <Metric label="战力" value={formatNumber(data.combatPower)} />
          <Metric label="金币" value={formatNumber(data.player.gold)} />
          <Metric label="余额" value={`${formatNumber(data.player.realMoney)} 元`} />
          <Metric label="背包" value={`${data.inventoryCount}/${data.inventoryCapacity}`} />
        </div>

        <StaminaPanel stamina={data.stamina} compact />
      </div>

      <div className="home-workbench">
        <section className="home-main-column">
          <WorldEventPanel
            events={worldEventsQuery.data ?? []}
            loading={worldEventsQuery.isLoading}
            onAct={(event) => {
              setPendingWorldEventAction(event.action);
              setScreen(event.action.targetScreen);
            }}
            onFallback={() => setScreen('dungeons')}
          />
          <section className="home-action-panel">
            <div className="home-panel-head">
              <SectionTitle icon={<Swords size={18} />} title="常用行动" />
              <span>优先处理成长和世界事件</span>
            </div>
            <div className="nav-grid home-primary-nav">
              {primaryActions.map((action) => (
                <NavTile key={action.title} {...action} />
              ))}
            </div>
          </section>
        </section>

        <aside className="home-side-column">
          <HomeEquipmentOverview
            home={data}
            onSelect={setSelectedEquipment}
            onManage={() => setScreen('inventory')}
            onProfile={() => setScreen('character')}
          />
          <section className="home-action-panel compact">
            <div className="home-panel-head">
              <SectionTitle icon={<Boxes size={18} />} title="更多入口" />
            </div>
            <div className="nav-grid home-secondary-nav">
              {secondaryActions.map((action) => (
                <NavTile key={action.title} {...action} />
              ))}
            </div>
          </section>
        </aside>
      </div>
      {selectedEquipment && <ItemDetail item={toEquipmentDetail(selectedEquipment)} onClose={() => setSelectedEquipment(null)} />}
    </section>
  );
}

function WorldEventPanel({ events, loading, onAct, onFallback }: {
  events: WorldEvent[];
  loading: boolean;
  onAct: (event: WorldEvent) => void;
  onFallback: () => void;
}) {
  const primaryEvent = events[0];
  const secondaryEvents = events.slice(1, 3);
  return (
    <section className="world-event-panel">
      <div className="world-event-head">
        <SectionTitle icon={<Bell size={18} />} title="世界正在发生" />
        <span>{loading ? '同步中' : `${events.length} 条可介入事件`}</span>
      </div>
      {!primaryEvent ? (
        <div className="world-event-empty">
          <div>
            <strong>暂时没有强事件</strong>
            <p>世界很安静时，最稳的选择是继续刷当前副本、整理背包，把下一次机会准备好。</p>
          </div>
          <button onClick={onFallback}>去刷掉落</button>
        </div>
      ) : (
        <div className="world-event-layout">
          <article className={`world-event-card featured ${primaryEvent.tone}`}>
            <div className="world-event-card-main">
              <div className="world-event-card-top">
                <span className="world-event-type">{worldEventTypeName(primaryEvent.type)}</span>
                <small>{primaryEvent.freshness}</small>
              </div>
              <h2>{primaryEvent.title}</h2>
              <p>{primaryEvent.summary}</p>
              <small className="world-event-relevance">{primaryEvent.relevance}</small>
            </div>
            <div className="world-event-card-action">
              <span>{primaryEvent.rewardHint}</span>
              <button onClick={() => onAct(primaryEvent)}>
                {primaryEvent.action.label}
                <ChevronRight size={16} />
              </button>
            </div>
          </article>

          <div className="world-event-side-list">
            {secondaryEvents.map((event) => (
              <button key={event.id} className={`world-event-row ${event.tone}`} onClick={() => onAct(event)}>
                <div className="world-event-card-main">
                  <div className="world-event-card-top">
                    <span className="world-event-type">{worldEventTypeName(event.type)}</span>
                    <small>{event.freshness}</small>
                  </div>
                  <h2>{event.title}</h2>
                  <p>{event.summary}</p>
                  <small className="world-event-relevance">{event.relevance}</small>
                </div>
                <ChevronRight size={16} />
              </button>
            ))}
            {events.length > 3 && (
              <div className="world-event-more">
                另有 {events.length - 3} 条事件会继续轮询更新
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

function worldEventTypeName(type: WorldEvent['type']) {
  switch (type) {
    case 'guild_boss':
      return '公会';
    case 'market_upgrade':
      return '市场';
    case 'leaderboard_neighbor':
      return '榜单';
    case 'robot_highlight':
      return '冒险者';
    default:
      return '世界';
  }
}
