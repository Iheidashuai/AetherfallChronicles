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
  RiftBattleEvent,
  RiftRunResult,
  RiftSimulationResult,
  RiftSnapshot,
  RiftTierPreview,
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

export function EndgameRiftScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [selectedTier, setSelectedTier] = useState<number | null>(null);
  const [result, setResult] = useState<RiftRunResult | null>(null);
  const [replaySpeed, setReplaySpeed] = useState(1);
  const [visibleEventCount, setVisibleEventCount] = useState(0);
  const { data, isLoading, error } = useQuery({
    queryKey: ['rifts', token],
    queryFn: () => gameApi.rifts(token),
    staleTime: 0,
    refetchOnMount: 'always',
  });
  const runMutation = useMutation({
    mutationFn: (tier: number) => gameApi.runRift(token, tier),
    onSuccess: async (runResult) => {
      setResult(runResult);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const weeklyMutation = useMutation({
    mutationFn: () => gameApi.claimRiftWeeklyReward(token),
    onSuccess: async (weekly) => {
      await invalidateGameQueries(queryClient, token);
      setResult((current) => current ? { ...current, materials: weekly.materials } : current);
    },
  });

  useEffect(() => {
    setVisibleEventCount(result?.events.length ? 1 : 0);
  }, [result?.runId, result?.events.length]);

  useEffect(() => {
    if (!result || visibleEventCount >= result.events.length) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setVisibleEventCount((current) => Math.min(result.events.length, current + 1));
    }, replaySpeed === 4 ? 150 : replaySpeed === 2 ? 340 : 820);
    return () => window.clearTimeout(timeout);
  }, [replaySpeed, result, visibleEventCount]);

  if (isLoading) {
    return <LoadingScreen title="校准深渊裂隙" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '深渊裂隙加载失败'} />;
  }

  const activeTier = selectedTier ?? data.nextTier ?? data.challengeTiers.at(-1) ?? 1;
  const activeTierPreview = tierPreviewFor(data, activeTier);
  const activeModifiers = activeTierPreview.modifiers;
  const activeRewardPreview = activeTierPreview.rewardPreview;
  const activeTierState = tierStateLabel(activeTier, data);
  const canRun = data.unlocked
    && activeTier > 0
    && activeTier <= data.bestTier + 1
    && data.stamina.current >= data.staminaCost;
  const progressPercent = Math.min(100, Math.round((data.bestTier / Math.max(1, data.nextTier)) * 100));

  return (
    <section className="screen rift-screen">
      <TopBar title="深渊裂隙" onBack={() => setScreen('home')} />
      <div className="status-strip rift-status-strip">
        <div className="battle-brief">
          <Skull size={20} />
          <div>
            <span className="eyebrow">每周单人爬层</span>
            <strong>{data.unlocked ? `下一目标 T${data.nextTier}` : '未解锁'}</strong>
          </div>
        </div>
        <Metric label="历史最高" value={data.bestTier > 0 ? `T${data.bestTier}` : '未通关'} />
        <Metric label="本周最高" value={data.weeklyBestTier > 0 ? `T${data.weeklyBestTier}` : '未上榜'} />
        <Metric label="推荐战力" value={formatNumber(activeTierPreview.recommendedPower)} />
        <Metric label="门槛战力" value={formatNumber(activeTierPreview.minimumPower)} />
        <Metric label="疲劳消耗" value={`${data.staminaCost}`} />
      </div>

      {!data.unlocked ? (
        <div className="rift-locked-panel">
          <Skull size={42} />
          <h2>深渊裂隙尚未稳定</h2>
          <p>{data.unlockHint}</p>
          <div className="rift-lock-metrics">
            <Metric label="等级要求" value={`Lv.${data.requiredLevel}`} />
            <Metric label="前置副本" value="星陨深渊·王座" />
          </div>
        </div>
      ) : (
        <div className="desktop-workbench rift-workbench">
          <aside className="filter-rail rift-tier-rail">
            <div className="panel-head-row">
              <SectionTitle icon={<Skull size={18} />} title="推进轨道" />
              <strong>{progressPercent}%</strong>
            </div>
            <div className="rift-progress-card">
              <span>本周目标</span>
              <strong>T{data.nextTier}</strong>
              <p>{data.bestTier > 0 ? `已打穿 T${data.bestTier}，本周从下一层刷新榜单。` : '先打穿 T1，开启本周深渊记录。'}</p>
              <div className="rift-progress-bar"><span style={{ width: `${progressPercent}%` }} /></div>
            </div>
            <div className="rift-tier-list" aria-label="深渊层数">
              {data.challengeTiers.map((tier) => {
                const state = tierStateLabel(tier, data);
                return (
                  <button
                    key={tier}
                    className={`${tier === activeTier ? 'active' : ''} ${state}`}
                    onClick={() => setSelectedTier(tier)}
                    aria-pressed={tier === activeTier}
                  >
                    <span>T{tier}</span>
                    <small>{tier === data.nextTier ? '下一层' : tier <= data.bestTier ? '已通关' : '可挑战'}</small>
                  </button>
                );
              })}
            </div>
            <div className="filter-result-card">
              <span>深渊材料</span>
              <strong>{data.materials.essence}</strong>
              <small>精华 / {data.materials.shards} 碎片 / {data.materials.orbs} 宝珠</small>
            </div>
          </aside>

          <section className="main-panel rift-main-panel">
            <div className="rift-hero-panel">
              <div>
                <span className="eyebrow">挑战层数 T{activeTier} · {activeTierState === 'cleared' ? '已通关复战' : '冲击新层'}</span>
                <h2>击败{riftBossName(activeTier)}，刷新本周成绩</h2>
                <p>{activeTier === data.nextTier ? `通关后解锁 T${activeTier + 1}，榜单按最高层、评分、剩余生命和行动数结算。` : '已通关层可以重打刷材料，但周榜只保留本周最佳成绩。'}</p>
              </div>
              <button className="primary-action" disabled={!canRun || runMutation.isPending} onClick={() => runMutation.mutate(activeTier)}>
                {runMutation.isPending ? '挑战中...' : activeTier === data.nextTier ? `冲击 T${activeTier}` : `复战 T${activeTier}`}
              </button>
            </div>
            <div className="rift-objective-grid">
              <Metric label="本层门槛" value={formatNumber(activeTierPreview.minimumPower)} />
              <Metric label="推荐战力" value={formatNumber(activeTierPreview.recommendedPower)} />
              <Metric label="Boss" value={riftBossName(activeTier)} />
              <Metric label="结算口径" value="层数 / 评分 / 生命 / 行动" />
            </div>
            <div className="rift-modifier-grid">
              {activeModifiers.map((modifier) => (
                <article key={modifier.id} className="rift-modifier-card">
                  <span>{modifier.name}</span>
                  <strong>+{formatPercent(modifier.rewardBonus)}</strong>
                  <p>{modifier.description}</p>
                </article>
              ))}
            </div>
            <div className="rift-reward-preview">
              <Metric label="深渊精华" value={`+${activeRewardPreview.essence}`} />
              <Metric label="淬炼碎片" value={`+${activeRewardPreview.shards}`} />
              <Metric label="重铸宝珠" value={`+${activeRewardPreview.orbs}`} />
              <Metric label="奖励倍率" value={`${activeRewardPreview.multiplier.toFixed(2)}x`} />
            </div>
            <RiftCombatReplay
              activeTier={activeTier}
              result={result}
              replaySpeed={replaySpeed}
              visibleEventCount={visibleEventCount}
              onSpeedChange={setReplaySpeed}
              onSkip={() => setVisibleEventCount(result?.events.length ?? 0)}
            />
          </section>

          <aside className="detail-rail rift-detail-rail">
            <SectionTitle icon={<Trophy size={18} />} title="深渊周榜" />
            <div className="rift-weekly-card">
              <span>周奖励</span>
              <strong>{data.weeklyRewardAvailable ? '可领取' : data.weeklyBestTier > 0 ? '已领取' : '未就绪'}</strong>
              <small>{data.weeklyBestTier > 0 ? `本周最好 T${data.weeklyBestTier}` : '本周通关后解锁周箱'}</small>
              <button className="mini-action" disabled={!data.weeklyRewardAvailable || weeklyMutation.isPending} onClick={() => weeklyMutation.mutate()}>
                {weeklyMutation.isPending ? '领取中...' : '领取周箱'}
              </button>
            </div>
            <div className="rift-leaderboard-list">
              {data.leaderboard.length === 0 && <EmptyState text="还没有深渊通关记录。" />}
              {data.leaderboard.map((entry) => (
                <article key={entry.playerId} className={`rift-rank-row ${entry.self ? 'self' : ''}`}>
                  <strong>#{entry.rank}</strong>
                  <div>
                    <span>{entry.playerName}</span>
                    <small>T{entry.tier} · {entry.rating} · {formatNumber(entry.score)} 分</small>
                    <small>剩余 {Math.max(0, entry.playerFinalHp)}/{Math.max(1, entry.playerMaxHp)} · {entry.turnsTaken} 行动</small>
                  </div>
                  <b>{entry.self ? '你' : entry.controllerType === 'robot' ? '机器人' : '玩家'}</b>
                </article>
              ))}
            </div>
          </aside>
        </div>
      )}

      {runMutation.error && (
        <FeedbackDialog
          variant="error"
          title="深渊挑战失败"
          message={runMutation.error.message}
          onClose={() => runMutation.reset()}
        />
      )}
      {weeklyMutation.error && (
        <FeedbackDialog
          variant="error"
          title="周奖励领取失败"
          message={weeklyMutation.error.message}
          onClose={() => weeklyMutation.reset()}
        />
      )}
    </section>
  );
}

function tierPreviewFor(data: RiftSnapshot, tier: number): RiftTierPreview {
  return data.tierPreviews.find((preview) => preview.tier === tier) ?? {
    tier,
    recommendedPower: data.recommendedPower,
    minimumPower: data.minimumPower,
    modifiers: data.modifiers,
    rewardPreview: data.rewardPreview,
  };
}

function tierStateLabel(tier: number, data: RiftSnapshot) {
  if (tier <= data.bestTier) {
    return 'cleared';
  }
  if (tier === data.nextTier) {
    return 'next';
  }
  return 'open';
}

function riftBossName(tier: number) {
  return `深渊领主 T${tier}`;
}

function riftPlayerName(result: RiftRunResult) {
  return result.events.find((event) => event.eventType === 'start' && event.actorName)?.actorName
    ?? result.events.find((event) => event.actorName)?.actorName
    ?? '挑战者';
}

function riftEventLabel(event?: RiftBattleEvent) {
  if (!event) {
    return '备战';
  }
  if (event.eventType === 'start') {
    return '开战';
  }
  if (event.eventType === 'room') {
    return '推进';
  }
  if (event.eventType === 'phase') {
    return '阶段';
  }
  if (event.eventType === 'death') {
    return '击败';
  }
  if (event.eventType === 'clear') {
    return '通关';
  }
  if (event.eventType === 'fail') {
    return '失败';
  }
  if (event.eventType === 'recover' || event.tone === 'heal') {
    return '恢复';
  }
  return event.tone === 'danger' || event.tone === 'enemy' ? '敌方行动' : '我方行动';
}

function riftRoomLabel(event?: RiftBattleEvent) {
  if (!event || event.roomIndex <= 0) {
    return '裂隙入口';
  }
  return `房间 ${event.roomIndex}`;
}

function riftCurrentEnemy(result: RiftRunResult, event: RiftBattleEvent | undefined, playerName: string) {
  const eventEnemy = [event?.targetName, event?.actorName].find((name) => name && name !== playerName);
  if (eventEnemy) {
    return eventEnemy;
  }
  const lastEnemy = [...result.events]
    .reverse()
    .flatMap((entry) => [entry.targetName, entry.actorName])
    .find((name) => name && name !== playerName);
  return lastEnemy ?? riftBossName(result.tier);
}

function maxHpForName(events: RiftBattleEvent[], name: string) {
  const hpValues = events.flatMap((event) => [
    event.actorName === name && event.eventType !== 'death' ? event.actorHp : 0,
    event.targetName === name ? event.targetHp : 0,
  ]).filter((hp) => hp > 0);
  return Math.max(1, ...hpValues);
}

function latestHpForName(events: RiftBattleEvent[], name: string, fallback: number) {
  return events.reduce((hp, event) => {
    if (event.actorName === name && event.eventType !== 'death') {
      return Math.max(0, event.actorHp);
    }
    if (event.targetName === name) {
      return Math.max(0, event.targetHp);
    }
    return hp;
  }, fallback);
}

function RiftCombatReplay({
  activeTier,
  result,
  replaySpeed,
  visibleEventCount,
  onSpeedChange,
  onSkip,
}: {
  activeTier: number;
  result: RiftRunResult | null;
  replaySpeed: number;
  visibleEventCount: number;
  onSpeedChange: (speed: number) => void;
  onSkip: () => void;
}) {
  const visibleEvents = result ? result.events.slice(0, Math.max(1, visibleEventCount)) : [];
  const currentEvent = visibleEvents.at(-1);
  const playerName = result ? riftPlayerName(result) : '挑战者';
  const enemyName = result ? riftCurrentEnemy(result, currentEvent, playerName) : riftBossName(activeTier);
  const enemyMaxHp = result ? maxHpForName(result.events, enemyName) : 1;
  const playerMaxHp = result?.playerMaxHp ?? 1;
  const playerHp = result ? latestHpForName(visibleEvents, playerName, playerMaxHp) : playerMaxHp;
  const enemyHp = result ? latestHpForName(visibleEvents, enemyName, enemyMaxHp) : enemyMaxHp;
  const enemyHpText = result
    ? enemyMaxHp > 1 ? `${Math.max(0, enemyHp)}/${enemyMaxHp}` : enemyHp <= 0 ? '已击败' : '目标锁定'
    : 'Boss 待命';
  const complete = result ? visibleEventCount >= result.events.length : false;

  return (
    <section className={`rift-combat-replay ${result ? result.success ? 'success' : 'failed' : 'idle'}`}>
      <div className="panel-head-row">
        <SectionTitle icon={<Swords size={18} />} title={result ? `T${result.tier} 自动战斗` : '深渊战场'} />
        {result && (
          <div className="rift-replay-controls">
            <button className={replaySpeed === 1 ? 'active' : ''} onClick={() => onSpeedChange(1)}>1x</button>
            <button className={replaySpeed === 2 ? 'active' : ''} onClick={() => onSpeedChange(2)}>2x</button>
            <button className={replaySpeed === 4 ? 'active' : ''} onClick={() => onSpeedChange(4)}><FastForward size={14} />4x</button>
            <button onClick={onSkip}><SkipForward size={14} />全部</button>
          </div>
        )}
      </div>

      <div className="rift-combat-stage">
        <article className="rift-combatant player">
          <div>
            <span className="eyebrow">我方</span>
            <strong>{playerName}</strong>
          </div>
          <HpBar value={playerHp} max={playerMaxHp} />
          <small>{result ? `${Math.max(0, playerHp)}/${playerMaxHp}` : '等待挑战'}</small>
        </article>

        <div className="rift-clash-column">
          <span className={`rift-outcome-badge ${result ? result.success ? 'success' : 'failed' : ''}`}>
            {result ? complete ? result.success ? '通关' : '失败' : '交战中' : '未开战'}
          </span>
          <strong>{riftEventLabel(currentEvent)}</strong>
          <p>{currentEvent?.text ?? `目标：击败${riftBossName(activeTier)}。`}</p>
        </div>

        <article className="rift-combatant enemy">
          <div>
            <span className="eyebrow">{riftRoomLabel(currentEvent)}</span>
            <strong>{enemyName}</strong>
          </div>
          <HpBar value={enemyHp} max={enemyMaxHp} />
          <small>{enemyHpText}</small>
        </article>
      </div>

      {result && (
        <div className="rift-combat-metrics">
          <Metric label="评价" value={`${result.rating} · ${formatNumber(result.score)}`} />
          <Metric label="行动数" value={`${result.turnsTaken}`} />
          <Metric label="击杀" value={`${result.monstersKilled}`} />
          <Metric label="材料" value={`+${result.rewards.essence}/${result.rewards.shards}/${result.rewards.orbs}`} />
        </div>
      )}

      <div className="rift-event-log replay">
        {!result && <EmptyState text="裂隙尚未开战，暂无战斗记录。" />}
        {visibleEvents.map((event) => (
          <div key={event.index} className={`rift-event-line ${event.tone}`}>
            <span>{riftRoomLabel(event)}</span>
            <p>{event.text}</p>
          </div>
        ))}
      </div>
      {result && (
        <div className="rift-material-footer">
          当前库存：{result.materials.essence} 精华 / {result.materials.shards} 碎片 / {result.materials.orbs} 宝珠
        </div>
      )}
    </section>
  );
}
