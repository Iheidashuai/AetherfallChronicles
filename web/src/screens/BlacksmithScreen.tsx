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
  battleLogTone, bestEnhancementStoneIds, bonusText, bossArchetypeName, buildEquipmentStatLine, buildGapWarnings,
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
  homeSlotShortName, inventoryTemplateQuantity, isEquipmentItem,
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
  GlobalTicker, ProfessionGlyph, InfoPanel, SkillCard, SpecialDungeonPanel, EquipmentProcessingPanel, RiftResultPanel, ArenaProfileCard, ArenaOpponentCard, ArenaShopCard, ArenaMatchPanel, ArenaFighterCard, ArenaRankRow, BuildScorePanel, BuildSimulationPanel, WealthTierCard, RobotRechargeCard, CashIncomeCard, RobotRangeFilter, RobotActivityCard, RobotEventCard, RobotActivityDetailModal, ChatMessageBubble, SpeakerDetailModal, DungeonCard, DropPreviewCard, HomeEquipmentOverview, EquipmentPanel, PowerBreakdownPanel, CombatStatsPanel, StatContributionGrid, CombatStatCell, StatContributionCell, QuestCard, QuestDetailPanel, EnhancementBadge, ItemCard, TransferItemOption, ItemDetail, EquipConfirmModal, CompareCard, EnhanceModal, SellConfirmModal, CombatantCard, HpBar, ResultSummaryModal, SweepSummaryModal, MarketSaleCard, MarketListedCard, ListingCard, LeaderboardCard, CatalogFilterGroup, CatalogDetailPanel, Metric, StaminaPanel, NavTile, SectionTitle, EmptyState, ToastNotice, FeedbackDialog, ConfirmDialog, TopBar, LoadingScreen, ErrorScreen,
  invalidateGameQueries,
} from '../components/ui';

export function BlacksmithScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [activeView, setActiveView] = useState<ForgeView>('enhance');
  const [focusedItemId, setFocusedItemId] = useState<number | null>(null);
  const [detailItem, setDetailItem] = useState<Item | null>(null);
  const [enhanceItem, setEnhanceItem] = useState<Item | null>(null);
  const [enhanceToast, setEnhanceToast] = useState<{ variant: FeedbackVariant | 'warning'; title: string; message: string } | null>(null);
  const [selectedStoneIds, setSelectedStoneIds] = useState<number[]>([]);
  const [autoEnhanceRunning, setAutoEnhanceRunning] = useState(false);
  const autoEnhanceStopRef = useRef(false);
  const autoEnhanceClosedRef = useRef(false);
  const [sourceItemId, setSourceItemId] = useState<number | null>(null);
  const [targetItemId, setTargetItemId] = useState<number | null>(null);
  const [refineFocus, setRefineFocus] = useState('balanced');
  const [selectedGemId, setSelectedGemId] = useState<number | null>(null);
  const [lockedAffixIndexes, setLockedAffixIndexes] = useState<number[]>([]);
  const [useAscensionProtector, setUseAscensionProtector] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['inventory', token],
    queryFn: () => gameApi.inventory(token),
  });
  const {
    data: processingData,
    isLoading: processingLoading,
    error: processingError,
  } = useQuery<EquipmentProcessingSnapshot>({
    queryKey: ['equipmentProcessing', token],
    queryFn: () => gameApi.equipmentProcessing(token),
  });
  const {
    data: homeData,
    isLoading: homeLoading,
    error: homeError,
  } = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
  });

  useEffect(() => {
    if (!enhanceToast) {
      return;
    }
    const timeout = window.setTimeout(() => setEnhanceToast(null), 2400);
    return () => window.clearTimeout(timeout);
  }, [enhanceToast]);

  const allEquipment = useMemo(() => {
    if (!data) {
      return [];
    }
    return sortItems([...Object.values(data.equippedItems), ...data.inventory].filter(isEquipmentItem), 'quality');
  }, [data]);
  const sourceItems = useMemo(() => allEquipment.filter((item) => item.enhancementLevel > 0), [allEquipment]);
  const targetItems = useMemo(() => allEquipment.filter((item) => item.id !== sourceItemId), [allEquipment, sourceItemId]);
  const selectedSource = allEquipment.find((item) => item.id === sourceItemId) ?? null;
  const selectableTransferTargets = useMemo(() => {
    if (!selectedSource) {
      return [];
    }
    return allEquipment.filter((item) =>
      item.id !== selectedSource.id
      && item.itemType === selectedSource.itemType
      && item.enhancementLevel < selectedSource.enhancementLevel,
    );
  }, [allEquipment, selectedSource]);
  const selectedTarget = selectableTransferTargets.find((item) => item.id === targetItemId) ?? null;
  const focusedItem = focusedItemId ? allEquipment.find((item) => item.id === focusedItemId) ?? null : null;
  const canTransfer = Boolean(
    selectedSource
    && selectedTarget
    && selectedSource.id !== selectedTarget.id
    && selectedSource.itemType === selectedTarget.itemType
    && selectedSource.enhancementLevel > selectedTarget.enhancementLevel,
  );

  useEffect(() => {
    if (!data) {
      return;
    }
    if (sourceItemId && !sourceItems.some((item) => item.id === sourceItemId)) {
      setSourceItemId(null);
    }
    if (targetItemId && !selectableTransferTargets.some((item) => item.id === targetItemId)) {
      setTargetItemId(null);
    }
    if (focusedItemId && !allEquipment.some((item) => item.id === focusedItemId)) {
      setFocusedItemId(null);
    }
    if (detailItem && !allEquipment.some((item) => item.id === detailItem.id)) {
      setDetailItem(null);
    }
  }, [allEquipment, data, detailItem, focusedItemId, selectableTransferTargets, sourceItemId, sourceItems, targetItemId]);

  const enhanceMutation = useMutation({
    mutationFn: ({ itemId, stoneItemIds }: { itemId: number; stoneItemIds: number[] }) => gameApi.enhance(token, itemId, stoneItemIds),
    onMutate: () => {
      setNotice(null);
      setEnhanceToast(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setSelectedStoneIds([]);
      setEnhanceToast(result.success
        ? { variant: 'success', title: '强化成功', message: '装备属性已提升。' }
        : { variant: 'warning', title: '强化失败', message: '幸运值提升，下次成功率提高。' });
      const refreshed = [...result.inventory.inventory, ...Object.values(result.inventory.equippedItems)].find((item) => item.id === enhanceItem?.id);
      if (refreshed) {
        setEnhanceItem(refreshed);
        setFocusedItemId(refreshed.id);
        if (detailItem?.id === refreshed.id) {
          setDetailItem(refreshed);
        }
      }
      await invalidateGameQueries(queryClient, token);
    },
    onError: (error) => {
      setEnhanceToast({ variant: 'error', title: '强化失败', message: error.message });
    },
  });

  function itemFromInventorySnapshot(snapshot: InventorySnapshot, itemId: number) {
    return [...snapshot.inventory, ...Object.values(snapshot.equippedItems)].find((item) => item.id === itemId) ?? null;
  }

  function closeEnhanceModal() {
    autoEnhanceStopRef.current = true;
    autoEnhanceClosedRef.current = true;
    setEnhanceItem(null);
    setEnhanceToast(null);
    setSelectedStoneIds([]);
  }

  function stopAutoEnhance() {
    autoEnhanceStopRef.current = true;
    setEnhanceToast({ variant: 'warning', title: '停止中', message: '当前强化完成后会停止。' });
  }

  async function runAutoEnhance({ targetLevel, useBestStones }: { targetLevel: number; useBestStones: boolean }) {
    if (!enhanceItem || !data || autoEnhanceRunning) {
      return;
    }
    autoEnhanceStopRef.current = false;
    autoEnhanceClosedRef.current = false;
    setAutoEnhanceRunning(true);
    setSelectedStoneIds([]);
    setNotice(null);
    setEnhanceToast(null);
    let currentItem = enhanceItem;
    let currentSnapshot = data;
    let attempts = 0;
    let successes = 0;
    try {
      while (
        !autoEnhanceStopRef.current
        && currentItem.enhancementLevel < Math.min(15, targetLevel)
        && currentItem.enhancementLevel < 15
      ) {
        const nextCost = enhanceCost(currentItem);
        if (currentSnapshot.gold < nextCost) {
          throw new Error(`金币不足，需要 ${formatNumber(nextCost)} 金`);
        }
        const stoneItemIds = useBestStones ? bestEnhancementStoneIds(currentSnapshot.inventory, currentItem) : [];
        const result = await gameApi.enhance(token, currentItem.id, stoneItemIds);
        attempts++;
        if (result.success) {
          successes++;
        }
        currentSnapshot = result.inventory;
        queryClient.setQueryData(['inventory', token], result.inventory);
        setSelectedStoneIds([]);
        const refreshed = itemFromInventorySnapshot(result.inventory, currentItem.id);
        if (!refreshed) {
          throw new Error('强化后未找到目标装备');
        }
        currentItem = refreshed;
        if (!autoEnhanceClosedRef.current) {
          setEnhanceItem(refreshed);
          setFocusedItemId(refreshed.id);
          if (detailItem?.id === refreshed.id) {
            setDetailItem(refreshed);
          }
        }
        await new Promise((resolve) => window.setTimeout(resolve, 220));
      }
      if (!autoEnhanceClosedRef.current) {
        const stopped = autoEnhanceStopRef.current && currentItem.enhancementLevel < Math.min(15, targetLevel);
        setEnhanceToast(stopped
          ? { variant: 'warning', title: '自动强化已停止', message: `已尝试 ${attempts} 次，成功 ${successes} 次。` }
          : { variant: 'success', title: '自动强化完成', message: `已强化到 +${currentItem.enhancementLevel}，尝试 ${attempts} 次，成功 ${successes} 次。` });
      }
    } catch (error) {
      if (!autoEnhanceClosedRef.current) {
        setEnhanceToast({ variant: 'error', title: '自动强化中断', message: (error as Error).message });
      }
    } finally {
      setAutoEnhanceRunning(false);
      await invalidateGameQueries(queryClient, token);
    }
  }

  const transferMutation = useMutation({
    mutationFn: () => {
      if (sourceItemId == null || targetItemId == null) {
        throw new Error('请选择来源装备和目标装备');
      }
      if (sourceItemId === targetItemId) {
        throw new Error('来源装备和目标装备不能相同');
      }
      if (!selectedSource || !selectedTarget) {
        throw new Error('请选择有效的来源装备和目标装备');
      }
      if (selectedSource.itemType !== selectedTarget.itemType) {
        throw new Error('强化转移只能转移到相同部位装备');
      }
      if (selectedTarget.enhancementLevel >= selectedSource.enhancementLevel) {
        throw new Error('目标装备强化等级必须低于来源装备');
      }
      return gameApi.transferEnhancement(token, sourceItemId, targetItemId);
    },
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setSourceItemId(null);
      setTargetItemId(result.targetItem.id);
      setNotice(`已继承到 ${equipmentDisplayName(result.targetItem)}`);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const refineMutation = useMutation({
    mutationFn: ({ itemId, focus }: { itemId: number; focus: string }) => gameApi.refine(token, itemId, focus),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setFocusedItemId(result.item.id);
      setNotice(`已将 ${equipmentDisplayName(result.item)} 淬炼到 ${result.refineLevel} 阶`);
      await invalidateGameQueries(queryClient, token);
    },
  });
  async function handleProcessingSuccess(result: EquipmentProcessingResult) {
    queryClient.setQueryData(['equipmentProcessing', token], result.snapshot);
    queryClient.setQueryData(['inventory', token], result.snapshot.inventory);
    setFocusedItemId(result.item.id);
    setSelectedGemId(null);
    setNotice(`${result.message}，角色战力 ${formatNumber(result.powerBefore)} → ${formatNumber(result.powerAfter)}`);
    await invalidateGameQueries(queryClient, token);
  }

  const unlockSocketMutation = useMutation({
    mutationFn: (itemId: number) => gameApi.unlockSocket(token, itemId),
    onMutate: () => setNotice(null),
    onSuccess: handleProcessingSuccess,
  });
  const socketGemMutation = useMutation({
    mutationFn: ({ itemId, socketIndex, gemItemId }: { itemId: number; socketIndex: number; gemItemId: number }) =>
      gameApi.socketGem(token, itemId, socketIndex, gemItemId),
    onMutate: () => setNotice(null),
    onSuccess: handleProcessingSuccess,
  });
  const unsocketGemMutation = useMutation({
    mutationFn: ({ itemId, socketIndex }: { itemId: number; socketIndex: number }) => gameApi.unsocketGem(token, itemId, socketIndex),
    onMutate: () => setNotice(null),
    onSuccess: handleProcessingSuccess,
  });
  const upgradeGemsMutation = useMutation({
    mutationFn: (gemItemIds: number[]) => gameApi.upgradeGems(token, gemItemIds),
    onMutate: () => setNotice(null),
    onSuccess: handleProcessingSuccess,
  });
  const upgradeGemBatchesMutation = useMutation({
    mutationFn: (gemItemIdBatches: number[][]) => gameApi.upgradeGemBatches(token, gemItemIdBatches),
    onMutate: () => setNotice(null),
    onSuccess: handleProcessingSuccess,
  });
  const reforgeMutation = useMutation({
    mutationFn: ({ itemId, locked }: { itemId: number; locked: number[] }) => gameApi.reforgeEquipment(token, itemId, locked),
    onMutate: () => setNotice(null),
    onSuccess: handleProcessingSuccess,
  });
  const ascendMutation = useMutation({
    mutationFn: ({ itemId, useProtector }: { itemId: number; useProtector: boolean }) => gameApi.ascendEquipment(token, itemId, useProtector),
    onMutate: () => setNotice(null),
    onSuccess: handleProcessingSuccess,
  });

  if (isLoading || processingLoading || homeLoading) {
    return <LoadingScreen title="点燃锻炉" />;
  }
  if (error || processingError || homeError || !data || !processingData || !homeData) {
    return <ErrorScreen message={(error as Error)?.message ?? (processingError as Error)?.message ?? (homeError as Error)?.message ?? '铁匠铺加载失败'} />;
  }

  const processingBusy = unlockSocketMutation.isPending
    || socketGemMutation.isPending
    || unsocketGemMutation.isPending
    || upgradeGemsMutation.isPending
    || upgradeGemBatchesMutation.isPending
    || reforgeMutation.isPending
    || ascendMutation.isPending;
  const busy = enhanceMutation.isPending || autoEnhanceRunning || transferMutation.isPending || refineMutation.isPending || processingBusy;
  const blacksmithError = transferMutation.error?.message
    ?? refineMutation.error?.message
    ?? unlockSocketMutation.error?.message
    ?? socketGemMutation.error?.message
    ?? unsocketGemMutation.error?.message
    ?? upgradeGemsMutation.error?.message
    ?? upgradeGemBatchesMutation.error?.message
    ?? reforgeMutation.error?.message
    ?? ascendMutation.error?.message;
  const feedback = blacksmithError
    ? { variant: 'error' as const, title: '操作失败', message: blacksmithError }
    : notice
      ? { variant: 'success' as const, title: '操作完成', message: notice }
      : null;

  function closeFeedback() {
    setNotice(null);
    enhanceMutation.reset();
    transferMutation.reset();
    refineMutation.reset();
    unlockSocketMutation.reset();
    socketGemMutation.reset();
    unsocketGemMutation.reset();
    upgradeGemsMutation.reset();
    upgradeGemBatchesMutation.reset();
    reforgeMutation.reset();
    ascendMutation.reset();
  }

  function selectTransferSource(item: Item) {
    setSourceItemId(item.id);
    setNotice(null);
    const currentTarget = allEquipment.find((equipment) =>
      equipment.id === targetItemId
      && equipment.id !== item.id
      && equipment.itemType === item.itemType
      && equipment.enhancementLevel < item.enhancementLevel,
    );
    if (!currentTarget) {
      setTargetItemId(null);
    }
  }

  const enhanceableCount = allEquipment.filter((item) => item.enhancementLevel < 15).length;
  const transferTargetCount = selectedSource
    ? selectableTransferTargets.length
    : targetItems.length;
  const riftEssence = materialCount(data.inventory, 'mat_abyss_essence');
  const riftShards = materialCount(data.inventory, 'mat_tempering_shard');
  const riftOrbs = materialCount(data.inventory, 'mat_reforge_orb');
  const refineableCount = allEquipment.filter((item) => (item.refineLevel ?? 0) < 5).length;
  const socketableCount = processingData.equipment.filter((entry) => entry.unlockedSocketCount < entry.socketLimit).length;
  const gemCount = processingData.gems.reduce((total, gem) => total + Math.max(1, gem.quantity ?? 1), 0);
  const reforgeableCount = processingData.equipment.filter((entry) => entry.affixLimit > 0).length;
  const ascendableCount = processingData.equipment.filter((entry) => (entry.item.ascensionLevel ?? 0) < 5).length;
  const forgeViews: Array<{ id: ForgeView; title: string; detail: string; icon: ReactNode; badge: string }> = [
    { id: 'enhance', title: '装备强化', detail: '提升基础属性与战力', icon: <Hammer size={18} />, badge: `${enhanceableCount}` },
    { id: 'transfer', title: '强化转移', detail: '把高强化继承到低强化装备', icon: <Repeat2 size={18} />, badge: `${sourceItems.length}` },
    { id: 'refine', title: '深渊淬炼', detail: '消耗深渊材料定向强化', icon: <Sparkles size={18} />, badge: `${refineableCount}` },
    { id: 'socket', title: '宝石镶嵌', detail: '开孔 · 镶嵌', icon: <Gem size={18} />, badge: `${socketableCount}` },
    { id: 'gem', title: '宝石合成', detail: '三合一 · 一键合成', icon: <Sparkles size={18} />, badge: `${gemCount}` },
    { id: 'reforge', title: '词条重铸', detail: '锁词 · 高风险洗练', icon: <ScrollText size={18} />, badge: `${reforgeableCount}` },
    { id: 'ascend', title: '装备升阶', detail: '突破加工上限', icon: <Shield size={18} />, badge: `${ascendableCount}` },
  ];
  const activeForge = forgeViews.find((view) => view.id === activeView) ?? forgeViews[0];
  const currentCombatPower = data.combatPower;
  const currentLevel = homeData.player.level;

  return (
    <section className="screen blacksmith-screen forge-screen">
      <TopBar title="铁匠铺" onBack={() => setScreen('home')} />
      <div className="forge-status-strip">
        <div className="forge-status-main">
          <div className="forge-emblem">
            <Hammer size={24} />
          </div>
          <div>
            <span className="eyebrow">工坊状态</span>
            <h1>装备工坊</h1>
          </div>
        </div>
        <Metric label="战力" value={formatNumber(currentCombatPower)} />
        <Metric label="等级" value={`Lv.${currentLevel}`} />
        <Metric label="金币" value={formatNumber(data.gold)} />
        <Metric label="装备" value={allEquipment.length.toString()} />
        <Metric label="可转移" value={sourceItems.length.toString()} />
      </div>

      <div className="blacksmith-workbench forge-workbench">
        <aside className="forge-sidebar">
          <div className="forge-master">
            <span className="eyebrow">功能台</span>
            <strong>{activeForge.title}</strong>
          </div>
          <nav className="forge-nav">
            {forgeViews.map((view) => (
              <button
                key={view.id}
                className={`forge-nav-button ${activeView === view.id ? 'active' : ''}`}
                onClick={() => setActiveView(view.id)}
              >
                <span className="forge-nav-icon">{view.icon}</span>
                <span>
                  <strong>{view.title}</strong>
                  <small>{view.detail}</small>
                </span>
                <em>{view.badge}</em>
              </button>
            ))}
          </nav>
        </aside>

        <section className="forge-workspace">
          <div className="forge-workspace-head">
            <div>
              <span className="eyebrow">当前界面</span>
              <h2>{activeForge.title}</h2>
            </div>
            <div className="forge-pill-row">
              <span>战力 {formatNumber(currentCombatPower)}</span>
              <span>Lv.{currentLevel}</span>
              <span>{formatNumber(data.gold)} 金</span>
              {activeView === 'transfer' && <span>{transferTargetCount} 个目标</span>}
            </div>
          </div>

          {activeView === 'enhance' && (
            <div className="forge-enhance-view">
              <section className="forge-list-panel">
                <div className="inventory-main-title">
                  <SectionTitle icon={<Hammer size={18} />} title="强化清单" />
                  <strong>{enhanceableCount} 件可强化</strong>
                </div>
                <div className="item-grid blacksmith-item-grid forge-equipment-grid forge-enhancement-list-grid">
                  {allEquipment.length === 0 && <EmptyState text="当前没有可强化装备。" />}
                  {allEquipment.map((item) => (
                    <ItemCard
                      key={item.id}
                      item={item}
                      label={itemLocationLabel(item, data.equippedItems)}
                      className="forge-equipment-card"
                      showEffectText={false}
                      onSelect={() => setFocusedItemId(item.id)}
                    >
                      {item.enhancementLevel < 15 && (
                        <button className="mini-action forge-equipment-action" disabled={busy} onClick={(event) => {
                          event.stopPropagation();
                          setFocusedItemId(item.id);
                          setEnhanceToast(null);
                          setSelectedStoneIds([]);
                          setEnhanceItem(item);
                        }}>
                          强化
                        </button>
                      )}
                    </ItemCard>
                  ))}
                </div>
              </section>
              <aside className="forge-detail-panel">
                <CompareCard
                  title="当前选择"
                  item={focusedItem}
                  highlight
                  emptyTitle="未选择"
                  emptyText="从左侧装备清单选择一件装备。"
                  emptyMeta="等待选择"
                />
                {focusedItem ? (
                  <>
                    <div className="forge-stat-grid">
                      <Metric label="强化等级" value={`+${focusedItem.enhancementLevel}`} />
                      <Metric label="成功率" value={`${Math.round(enhanceChance(focusedItem) * 100)}%`} />
                      <Metric label="强化费用" value={`${formatNumber(enhanceCost(focusedItem))} 金`} />
                      <Metric label="幸运值" value={(focusedItem.enhancementLuck ?? 0).toString()} />
                    </div>
                    <div className="forge-detail-actions">
                      <button
                        className="mini-action subtle"
                        onClick={() => setDetailItem(focusedItem)}
                      >
                        查看详情
                      </button>
                      <button
                        className="primary-action"
                        disabled={busy || focusedItem.enhancementLevel >= 15}
                        onClick={() => {
                          setEnhanceToast(null);
                          setSelectedStoneIds([]);
                          setEnhanceItem(focusedItem);
                        }}
                      >
                        {focusedItem.enhancementLevel >= 15 ? '已达上限' : '开始强化'}
                      </button>
                    </div>
                  </>
                ) : (
                  <EmptyState text="未选择装备。" />
                )}
              </aside>
            </div>
          )}

          {activeView === 'transfer' && (
            <div className="forge-transfer-view">
              <section className="transfer-column">
                <div className="transfer-column-head">
                  <h3>来源装备</h3>
                  <strong>{sourceItems.length}</strong>
                </div>
                <div className="transfer-list">
                  {sourceItems.length === 0 && <EmptyState text="暂无带强化等级的装备。" />}
                  {sourceItems.map((item) => (
                    <TransferItemOption
                      key={item.id}
                      item={item}
                      selected={item.id === sourceItemId}
                      onSelect={() => selectTransferSource(item)}
                    />
                  ))}
                </div>
              </section>
              <section className="transfer-column">
                <div className="transfer-column-head">
                  <h3>目标装备</h3>
                  <strong>{transferTargetCount}</strong>
                </div>
                <div className="transfer-list">
                  {!selectedSource && <EmptyState text="先选择来源装备。" />}
                  {selectedSource && selectableTransferTargets.length === 0 && <EmptyState text="暂无同部位、低强化的可继承目标。" />}
                  {selectedSource && selectableTransferTargets.map((item) => (
                    <TransferItemOption
                      key={item.id}
                      item={item}
                      selected={item.id === targetItemId}
                      onSelect={() => setTargetItemId(item.id)}
                    />
                  ))}
                </div>
              </section>
              <aside className="forge-transfer-preview">
                <div className="transfer-preview">
                  <CompareCard
                    title="来源装备"
                    item={selectedSource}
                    emptyTitle="未选择"
                    emptyText="选择带强化等级的装备作为来源。"
                    emptyMeta="无来源"
                  />
                  <CompareCard
                    title="继承目标"
                    item={selectedTarget}
                    highlight
                    emptyTitle="未选择"
                    emptyText="目标装备必须同部位，且强化等级低于来源。"
                    emptyMeta="无目标"
                  />
                </div>
                {selectedSource && selectedTarget && !canTransfer && <div className="modal-warning">目标装备强化等级必须低于来源装备。</div>}
                {sourceItemId != null && targetItemId != null && sourceItemId === targetItemId && <div className="modal-warning">来源装备和目标装备不能相同。</div>}
                <div className="result-modal-actions">
                  <button className="primary-action" disabled={busy || !canTransfer} onClick={() => transferMutation.mutate()}>
                    {transferMutation.isPending ? '转移中...' : '开始转移'}
                  </button>
                </div>
              </aside>
            </div>
          )}

          {activeView === 'refine' && (
            <div className="forge-enhance-view">
              <section className="forge-list-panel">
                <div className="inventory-main-title">
                  <SectionTitle icon={<Sparkles size={18} />} title="深渊淬炼清单" />
                  <strong>{refineableCount} 件可淬炼</strong>
                </div>
                <div className="item-grid blacksmith-item-grid forge-equipment-grid forge-enhancement-list-grid">
                  {allEquipment.length === 0 && <EmptyState text="当前没有可淬炼装备。" />}
                  {allEquipment.map((item) => (
                    <ItemCard
                      key={item.id}
                      item={item}
                      label={`${itemLocationLabel(item, data.equippedItems)} · 淬${item.refineLevel ?? 0}/5`}
                      className="forge-equipment-card"
                      showEffectText={false}
                      onSelect={() => setFocusedItemId(item.id)}
                    >
                      <button className="mini-action" disabled={busy || (item.refineLevel ?? 0) >= 5} onClick={(event) => {
                        event.stopPropagation();
                        setFocusedItemId(item.id);
                      }}>
                        {(item.refineLevel ?? 0) >= 5 ? '满阶' : '选择'}
                      </button>
                    </ItemCard>
                  ))}
                </div>
              </section>
              <aside className="forge-detail-panel">
                <CompareCard
                  title="淬炼目标"
                  item={focusedItem}
                  highlight
                  emptyTitle="未选择"
                  emptyText="从左侧选择一件装备。"
                  emptyMeta="等待选择"
                />
                {focusedItem ? (
                  <>
                    <div className="forge-refine-focus">
                      {[
                        ['balanced', '均衡'],
                        ['attack', '攻击'],
                        ['defense', '护甲'],
                        ['resistance', '抗性'],
                        ['hp', '生命'],
                        ['mp', '法力'],
                        ['crit', '暴击'],
                      ].map(([value, label]) => (
                        <button key={value} className={refineFocus === value ? 'active' : ''} onClick={() => setRefineFocus(value)}>
                          {label}
                        </button>
                      ))}
                    </div>
                    <div className="forge-stat-grid">
                      <Metric label="当前淬炼" value={`淬${focusedItem.refineLevel ?? 0}/5`} />
                      <Metric label="深渊精华" value={`${riftEssence}/${refineCost(focusedItem).essence}`} />
                      <Metric label="淬炼碎片" value={`${riftShards}/${refineCost(focusedItem).shards}`} />
                      <Metric label="重铸宝珠" value={`${riftOrbs}/${refineCost(focusedItem).orbs}`} />
                    </div>
                    {refineBlockReason(focusedItem, data.gold, riftEssence, riftShards, riftOrbs) && (
                      <div className="modal-warning">
                        {refineBlockReason(focusedItem, data.gold, riftEssence, riftShards, riftOrbs)}
                      </div>
                    )}
                    <div className="forge-detail-actions">
                      <button className="mini-action subtle" onClick={() => setDetailItem(focusedItem)}>查看详情</button>
                      <button
                        className="primary-action"
                        disabled={busy || !canRefine(focusedItem, data.gold, riftEssence, riftShards, riftOrbs)}
                        title={refineBlockReason(focusedItem, data.gold, riftEssence, riftShards, riftOrbs) ?? '消耗深渊材料淬炼当前装备'}
                        onClick={() => refineMutation.mutate({ itemId: focusedItem.id, focus: refineFocus })}
                      >
                        {refineMutation.isPending ? '淬炼中...' : (focusedItem.refineLevel ?? 0) >= 5 ? '已达上限' : '开始淬炼'}
                      </button>
                    </div>
                  </>
                ) : (
                  <EmptyState text="未选择装备。" />
                )}
              </aside>
            </div>
          )}

          {(activeView === 'socket' || activeView === 'gem' || activeView === 'reforge' || activeView === 'ascend') && (
            <EquipmentProcessingPanel
              mode={activeView}
              snapshot={processingData}
              focusedItemId={focusedItemId}
              selectedGemId={selectedGemId}
              lockedAffixIndexes={lockedAffixIndexes}
              useProtector={useAscensionProtector}
              busy={busy}
              onFocusItem={(itemId) => {
                setFocusedItemId(itemId);
                setLockedAffixIndexes([]);
              }}
              onSelectGem={setSelectedGemId}
              onToggleAffixLock={(index) => setLockedAffixIndexes((current) =>
                current.includes(index) ? current.filter((value) => value !== index) : [...current, index],
              )}
              onUseProtectorChange={setUseAscensionProtector}
              onUnlockSocket={(itemId) => unlockSocketMutation.mutate(itemId)}
              onSocketGem={(itemId, socketIndex, gemItemId) => socketGemMutation.mutate({ itemId, socketIndex, gemItemId })}
              onUnsocketGem={(itemId, socketIndex) => unsocketGemMutation.mutate({ itemId, socketIndex })}
              onUpgradeGems={(gemIds) => upgradeGemsMutation.mutate(gemIds)}
              onUpgradeGemBatches={(gemIdBatches) => upgradeGemBatchesMutation.mutate(gemIdBatches)}
              onReforge={(itemId, locked) => reforgeMutation.mutate({ itemId, locked })}
              onAscend={(itemId, useProtector) => ascendMutation.mutate({ itemId, useProtector })}
            />
          )}
        </section>
      </div>
      {detailItem && <ItemDetail item={toEquipmentDetail(detailItem)} onClose={() => setDetailItem(null)} />}
      {enhanceItem && (
        <EnhanceModal
          item={enhanceItem}
          gold={data.gold}
          loading={enhanceMutation.isPending || autoEnhanceRunning}
          autoEnhanceRunning={autoEnhanceRunning}
          onClose={closeEnhanceModal}
          stones={enhancementStonesForItem(data.inventory, enhanceItem)}
          selectedStoneIds={selectedStoneIds}
          onAddStone={(stoneId) => setSelectedStoneIds((current) => current.length >= 3 ? current : [...current, stoneId])}
          onRemoveStone={(index) => setSelectedStoneIds((current) => current.filter((_, currentIndex) => currentIndex !== index))}
          onReplaceStones={setSelectedStoneIds}
          onEnhance={() => enhanceMutation.mutate({ itemId: enhanceItem.id, stoneItemIds: selectedStoneIds })}
          onAutoEnhance={runAutoEnhance}
          onStopAutoEnhance={stopAutoEnhance}
        />
      )}
      {enhanceToast && <ToastNotice variant={enhanceToast.variant} title={enhanceToast.title} message={enhanceToast.message} />}
      {feedback && (
        <FeedbackDialog
          variant={feedback.variant}
          title={feedback.title}
          message={feedback.message}
          onClose={closeFeedback}
        />
      )}
    </section>
  );
}
