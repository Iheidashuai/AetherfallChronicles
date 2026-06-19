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

export function ItemCatalogScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [category, setCategory] = useState<CatalogCategoryFilter>('all');
  const [quality, setQuality] = useState<CatalogQualityFilter>('all');
  const [level, setLevel] = useState<CatalogLevelFilter>('all');
  const [sort, setSort] = useState<CatalogSortKey>('quality');
  const [search, setSearch] = useState('');
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['item-catalog', token],
    queryFn: () => gameApi.itemCatalog(token),
  });

  const items = data ?? [];
  const filteredItems = useMemo(
    () => filterCatalogItems(items, { category, quality, level, sort, search }),
    [items, category, quality, level, sort, search],
  );
  const selectedItem = filteredItems.find((item) => item.templateId === selectedTemplateId) ?? filteredItems[0] ?? null;
  const summary = useMemo(() => catalogSummary(items), [items]);

  useEffect(() => {
    if (filteredItems.length === 0) {
      if (selectedTemplateId !== null) {
        setSelectedTemplateId(null);
      }
      return;
    }
    if (!filteredItems.some((item) => item.templateId === selectedTemplateId)) {
      setSelectedTemplateId(filteredItems[0].templateId);
    }
  }, [filteredItems, selectedTemplateId]);

  if (isLoading) {
    return <LoadingScreen title="整理物品图鉴" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '物品图鉴加载失败'} />;
  }

  return (
    <section className="screen item-catalog-screen">
      <TopBar title="物品图鉴" onBack={() => setScreen('home')} />
      <div className="catalog-summary-strip status-strip">
        <Metric label="全部" value={summary.total.toString()} />
        <Metric label="装备" value={summary.equipment.toString()} />
        <Metric label="消耗品" value={summary.consumable.toString()} />
        <Metric label="材料" value={summary.material.toString()} />
        <Metric label="宝箱" value={summary.chest.toString()} />
        <Metric label="最高等级" value={`Lv.${summary.maxLevel}`} />
      </div>

      <div className="item-catalog-workbench desktop-workbench">
        <aside className="catalog-filter-panel filter-rail">
          <SectionTitle icon={<Filter size={18} />} title="筛选" />
          <div className="catalog-search">
            <Search size={16} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="搜索名称 / 类型 / 效果"
              aria-label="搜索物品"
            />
          </div>

          <CatalogFilterGroup
            title="类型"
            value={category}
            options={[
              ['all', '全部'],
              ['equipment', '装备'],
              ['consumable', '消耗品'],
              ['material', '材料'],
              ['chest', '宝箱'],
            ]}
            onChange={(next) => setCategory(next as CatalogCategoryFilter)}
          />
          <CatalogFilterGroup
            title="品质"
            value={quality}
            options={[
              ['all', '全部'],
              ['common', '普通'],
              ['uncommon', '优秀'],
              ['rare', '稀有'],
              ['epic', '史诗'],
              ['legendary', '传说'],
              ['immortal', '不朽'],
            ]}
            onChange={(next) => setQuality(next as CatalogQualityFilter)}
          />
          <CatalogFilterGroup
            title="等级段"
            value={level}
            options={[
              ['all', '全部'],
              ['1-10', 'Lv.1-10'],
              ['11-30', 'Lv.11-30'],
              ['31-60', 'Lv.31-60'],
              ['61-90', 'Lv.61-90'],
            ]}
            onChange={(next) => setLevel(next as CatalogLevelFilter)}
          />
          <CatalogFilterGroup
            title="排序"
            value={sort}
            options={[
              ['quality', '品质'],
              ['level', '等级'],
              ['type', '类型'],
            ]}
            onChange={(next) => setSort(next as CatalogSortKey)}
          />
        </aside>

        <section className="catalog-list-panel main-panel">
          <div className="inventory-main-title">
            <SectionTitle icon={<Boxes size={18} />} title={`${categoryNameForInventory(category)}列表`} />
            <strong>{filteredItems.length} 种</strong>
          </div>
          <div className="catalog-item-grid">
            {filteredItems.length === 0 && <EmptyState text="当前筛选下没有物品。" />}
            {filteredItems.map((item) => (
              <button
                key={item.templateId}
                className={`catalog-item-card ${item.quality} ${selectedItem?.templateId === item.templateId ? 'selected' : ''}`}
                onClick={() => setSelectedTemplateId(item.templateId)}
              >
                <div className="catalog-item-icon">
                  {catalogIconForItem(item)}
                </div>
                <span className="eyebrow">{qualityName(item.quality)} · {itemCategoryLabel(item)} · Lv.{item.requiredLevel}</span>
                <strong className={`quality ${item.quality}`}>{item.name}</strong>
                <small>{catalogCardText(item)}</small>
              </button>
            ))}
          </div>
        </section>

        <CatalogDetailPanel item={selectedItem} />
      </div>
    </section>
  );
}

