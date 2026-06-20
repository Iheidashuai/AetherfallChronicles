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

export function EndgameRiftScreen({ token, onResult }: { token: string; onResult: (result: RiftRunResult) => void }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [selectedTier, setSelectedTier] = useState<number | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['rifts', token],
    queryFn: () => gameApi.rifts(token),
    staleTime: 0,
    refetchOnMount: 'always',
  });
  const runMutation = useMutation({
    mutationFn: (tier: number) => gameApi.runRift(token, tier),
    onSuccess: async (runResult) => {
      onResult(runResult);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const weeklyMutation = useMutation({
    mutationFn: () => gameApi.claimRiftWeeklyReward(token),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
    },
  });

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
                  <b>{entry.self ? '你' : entry.controllerType === 'robot' ? '冒险者' : '玩家'}</b>
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
