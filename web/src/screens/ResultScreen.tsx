import { FormEvent, MouseEvent, Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react';

const BattleStagePhaser = lazy(() =>
  import('../BattleStagePhaser').then((m) => ({ default: m.BattleStagePhaser })),
);
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

export function ResultScreen({ result, onResult }: { result: DungeonRunResult; onResult: (result: DungeonRunResult) => void }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const token = useAppStore((state) => state.token);
  const [speed, setSpeed] = useState(1);
  const frames = useMemo(() => battleFramesForResult(result), [result]);
  const [visibleFrames, setVisibleFrames] = useState(1);
  const [selectedLoot, setSelectedLoot] = useState<Item | null>(null);
  const [showSummary, setShowSummary] = useState(false);
  const [summaryResult, setSummaryResult] = useState(result);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const battleStageRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setSummaryResult(result);
    setVisibleFrames(1);
    setSelectedLoot(null);
    setShowSummary(false);
    setShowLeaveConfirm(false);
  }, [result]);

  useEffect(() => {
    if (visibleFrames >= frames.length) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setVisibleFrames((current) => Math.min(frames.length, current + 1));
    }, speed === 4 ? 160 : speed === 2 ? 380 : 980);
    return () => window.clearTimeout(timeout);
  }, [frames.length, speed, visibleFrames]);

  useEffect(() => {
    const node = battleStageRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [visibleFrames]);

  useEffect(() => {
    if (summaryResult !== result) {
      return;
    }
    if (visibleFrames >= frames.length) {
      setShowSummary(true);
    }
  }, [frames.length, result, summaryResult, visibleFrames]);

  const battlePlaying = visibleFrames < frames.length;

  async function returnToDungeons() {
    if (token) {
      await invalidateGameQueries(queryClient, token);
    }
    setScreen('dungeons');
  }

  function handleBack() {
    if (battlePlaying) {
      setShowLeaveConfirm(true);
      return;
    }
    void returnToDungeons();
  }

  function handleRetry() {
    if (retryMutation.isPending) {
      return;
    }
    retryMutation.mutate();
  }

  const retryMutation = useMutation({
    mutationFn: () => {
      if (!token) {
        throw new Error('登录已失效，请重新登录');
      }
      return gameApi.runDungeon(token, result.dungeonId);
    },
    onSuccess: async (nextResult) => {
      setShowSummary(false);
      onResult(nextResult);
      if (token) {
        await invalidateGameQueries(queryClient, token);
      }
    },
  });

  const currentFrame = frames[Math.min(visibleFrames - 1, frames.length - 1)];
  const currentEnemy = currentFrame.enemyName ?? (result.success ? '区域已肃清' : '推进中断');

  return (
    <section className="screen result-screen combat-screen">
      <TopBar title="副本战报" onBack={handleBack} />

      <aside className="combat-side">
        <div className={`result-banner ${result.success ? 'win' : 'lose'}`}>
          <span>{result.success ? '通关' : '撤退'} · 评价 {result.rating}</span>
          <h1>{result.dungeonName}</h1>
          <p>击败 {result.monstersKilled} 只魔物，获得 {result.expGained} 经验 / {result.goldGained} 金</p>
        </div>

        <div className="combat-hud">
          <CombatantCard
            icon={<HeartPulse size={18} />}
            label="我方"
            title={result.player.name}
            detail={`Lv.${result.player.level} ${professionName(result.player.profession)} · 战力 ${result.combatPower}`}
            hp={currentFrame.playerHp}
            maxHp={currentFrame.playerMaxHp || result.playerMaxHp}
          />
          <CombatantCard
            icon={<Skull size={18} />}
            label={currentFrame.roomLabel ?? '当前目标'}
            title={currentEnemy}
            detail={currentFrame.enemyMaxHp > 0 ? `剩余 ${currentFrame.enemyHp}/${currentFrame.enemyMaxHp}` : '等待下一波'}
            hp={currentFrame.enemyHp}
            maxHp={currentFrame.enemyMaxHp}
            enemy
          />
        </div>

        <div className="speed-bar">
          <button className={speed === 1 ? 'active' : ''} onClick={() => setSpeed(1)}>1x</button>
          <button className={speed === 2 ? 'active' : ''} onClick={() => setSpeed(2)}>2x</button>
          <button className={speed === 4 ? 'active' : ''} onClick={() => setSpeed(4)}><FastForward size={15} />4x</button>
          <button onClick={() => setVisibleFrames(frames.length)}><SkipForward size={15} />全部</button>
        </div>

        <div className="combat-reward-panel">
          <Metric label="推荐战力" value={(result.recommendedPower || 0).toString()} />
          <Metric label="战报进度" value={`${visibleFrames}/${frames.length}`} />
          <Metric label="掉落装备" value={result.loot.length.toString()} />
        </div>

        <div className="loot-grid compact-loot">
          {result.loot.length === 0 && <EmptyState text="本次没有获得装备。" />}
          {result.loot.slice(0, 4).map((item) => (
            <button key={item.id} className={`loot-card ${item.quality}`} onClick={() => setSelectedLoot(item)}>
              <Gem size={18} />
              <span>{qualityName(item.quality)} · {typeName(item.itemType)}</span>
              <strong>{equipmentDisplayName(item)}</strong>
            </button>
          ))}
        </div>
      </aside>

      <div className="combat-main">
        <div className="phaser-battle-shell">
          <Suspense fallback={<div className="phaser-battle-loading">加载战斗演出…</div>}>
            <BattleStagePhaser result={result} frame={currentFrame} />
          </Suspense>
        </div>
        <div className="battle-stage battle-transcript" ref={battleStageRef}>
          <div className="battle-stage-header">
            <span>{currentFrame.roomLabel ?? '战斗记录'}</span>
            <strong>{currentEnemy}</strong>
          </div>
          {frames.slice(0, visibleFrames).map((frame) => (
            <div key={`${frame.index}-${frame.text}`} className={`battle-line ${frame.tone || battleLogTone(frame.text)} ${frame.eventType ?? ''}`}>
              <span>#{frame.index}</span>
              <small>{battleEventName(frame)}</small>
              <p>{frame.text}</p>
            </div>
          ))}
        </div>
      </div>
      {showSummary && (
        <ResultSummaryModal
          result={result}
          onClose={() => setShowSummary(false)}
          onReturn={returnToDungeons}
          onRetry={handleRetry}
          retrying={retryMutation.isPending}
          onSelectLoot={setSelectedLoot}
        />
      )}
      {showLeaveConfirm && (
        <ConfirmDialog
          title="战斗仍在展示"
          message="现在返回会跳过剩余战报和掉落复盘，确定回到副本大厅吗？"
          confirmLabel="返回大厅"
          cancelLabel="继续查看"
          danger
          onCancel={() => setShowLeaveConfirm(false)}
          onConfirm={() => {
            setShowLeaveConfirm(false);
            void returnToDungeons();
          }}
        />
      )}
      {selectedLoot && <ItemDetail item={toEquipmentDetail(selectedLoot)} onClose={() => setSelectedLoot(null)} />}
      {retryMutation.error && (
        <FeedbackDialog
          variant="error"
          title="再来一次失败"
          message={retryMutation.error.message}
          onClose={() => retryMutation.reset()}
        />
      )}
    </section>
  );
}
