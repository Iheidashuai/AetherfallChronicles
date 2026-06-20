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

export function MarketScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const pendingWorldEventAction = useAppStore((state) => state.pendingWorldEventAction);
  const clearPendingWorldEventAction = useAppStore((state) => state.clearPendingWorldEventAction);
  const queryClient = useQueryClient();
  const [priceByItem, setPriceByItem] = useState<Record<number, string>>({});
  const [quantityByItem, setQuantityByItem] = useState<Record<number, string>>({});
  const [selectedMarketItem, setSelectedMarketItem] = useState<EquipmentDetailData | null>(null);
  const [marketFilters, setMarketFilters] = useState<MarketFilters>(emptyMarketFilters());
  const [marketLedgerTab, setMarketLedgerTab] = useState<MarketLedgerTab>('listed');
  const [highlightListingId, setHighlightListingId] = useState<number | null>(null);
  const homeQuery = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
  });
  const inventoryQuery = useQuery({
    queryKey: ['inventory', token],
    queryFn: () => gameApi.inventory(token),
  });
  const listingsQuery = useQuery({
    queryKey: ['market', token],
    queryFn: () => gameApi.marketListings(token),
    refetchInterval: 5_000,
  });
  const visibleMarketListings = useMemo(
    () => filterMarketListings(listingsQuery.data?.listings ?? [], marketFilters),
    [listingsQuery.data?.listings, marketFilters],
  );

  useEffect(() => {
    if (pendingWorldEventAction?.targetScreen !== 'market') {
      return;
    }
    const listingId = Number(pendingWorldEventAction.params?.listingId ?? pendingWorldEventAction.targetId ?? 0);
    if (listingId > 0) {
      setHighlightListingId(listingId);
    }
    setMarketFilters((current) => ({
      ...current,
      category: 'equipment',
      sort: 'quality',
    }));
    clearPendingWorldEventAction();
  }, [pendingWorldEventAction, clearPendingWorldEventAction]);

  const listMutation = useMutation({
    mutationFn: ({ item, quantity, unitPrice }: { item: Item; quantity: number; unitPrice: number }) => gameApi.listItem(token, item.id, quantity, unitPrice),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
      await queryClient.invalidateQueries({ queryKey: ['market', token] });
    },
  });
  const buyMutation = useMutation({
    mutationFn: (listingId: number) => gameApi.buyListing(token, listingId),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
      await queryClient.invalidateQueries({ queryKey: ['market', token] });
    },
  });
  const cancelMutation = useMutation({
    mutationFn: (listingId: number) => gameApi.cancelListing(token, listingId),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
      await queryClient.invalidateQueries({ queryKey: ['market', token] });
    },
  });

  if (homeQuery.isLoading || inventoryQuery.isLoading || listingsQuery.isLoading) {
    return <LoadingScreen title="刷新商会看板" />;
  }
  if (homeQuery.error || inventoryQuery.error || listingsQuery.error || !homeQuery.data || !inventoryQuery.data || !listingsQuery.data) {
    return <ErrorScreen message={(homeQuery.error as Error)?.message ?? (inventoryQuery.error as Error)?.message ?? (listingsQuery.error as Error)?.message ?? '市场加载失败'} />;
  }

  const playerName = homeQuery.data.player.name;
  const market = listingsQuery.data;
  const playerSales = [...(market.playerSales ?? [])].sort((left, right) => Date.parse(right.soldAt) - Date.parse(left.soldAt) || right.id - left.id);
  const playerActiveListings = market.listings
    .filter((listing) => listing.playerListing && listing.sellerName === playerName)
    .sort((left, right) => Date.parse(right.listedAt) - Date.parse(left.listedAt) || right.id - left.id);
  const inventoryItems = inventoryQuery.data.inventory.filter(isMarketableInventoryItem).slice(0, 24);
  const ledgerCount = marketLedgerTab === 'listed'
    ? playerActiveListings.length
    : marketLedgerTab === 'unlisted'
      ? inventoryItems.length
      : playerSales.length;
  const ledgerCountUnit = marketLedgerTab === 'unlisted' ? '件' : '单';
  const ledgerTitle = marketLedgerTab === 'listed'
    ? '已上架'
    : marketLedgerTab === 'unlisted'
      ? '未上架'
      : '已成交';
  const marketActionError = listMutation.error?.message ?? buyMutation.error?.message ?? cancelMutation.error?.message;
  const hasMarketFilters = marketFilters.minLevel.trim().length > 0
    || marketFilters.maxLevel.trim().length > 0
    || marketFilters.category !== 'all'
    || marketFilters.itemType !== 'all'
    || marketFilters.quality !== 'all'
    || marketFilters.sort !== 'listedAt';
  return (
    <section className="screen market-screen">
      <TopBar title="冒险者商会" onBack={() => setScreen('home')} />
      <div className="stat-grid market-stats status-strip">
        <Metric label="金币" value={homeQuery.data.player.gold.toString()} />
        <Metric label="在线商贩" value={market.onlineTraders.toString()} />
        <Metric label="冒险者挂单" value={market.robotListings.toString()} />
        <Metric label="近时成交" value={market.soldRecently.toString()} />
        <Metric label="我的上架" value={playerActiveListings.length.toString()} />
        <Metric label="均价" value={`${market.averagePrice} 金`} />
        <Metric label="背包" value={inventoryQuery.data.inventory.length.toString()} />
      </div>
      <div className="market-workbench desktop-workbench">
        <aside className="market-sell-panel filter-rail">
          <SectionTitle icon={<Package size={18} />} title="我的寄售" />
          <div className="market-rule-card">
            <strong>商会规则</strong>
            <p>{market.rules.antiExploit}</p>
          </div>
          <div className="market-sales-panel">
            <div className="market-sales-head">
              <SectionTitle icon={<Coins size={18} />} title={ledgerTitle} />
              <strong>{ledgerCount} {ledgerCountUnit}</strong>
            </div>
            <div className="market-ledger-tabs">
              {[
                ['listed', '已上架'],
                ['unlisted', '未上架'],
                ['sold', '已成交'],
              ].map(([value, label]) => (
                <button key={value} className={marketLedgerTab === value ? 'active' : ''} onClick={() => setMarketLedgerTab(value as MarketLedgerTab)}>
                  {label}
                </button>
              ))}
            </div>
            <div className={`market-sale-list ${marketLedgerTab === 'unlisted' ? 'market-inventory-list' : ''}`}>
              {marketLedgerTab === 'listed' && playerActiveListings.length === 0 && <EmptyState text="当前没有已上架的寄售。" />}
              {marketLedgerTab === 'listed' && playerActiveListings.map((listing) => (
                <MarketListedCard
                  key={listing.id}
                  listing={listing}
                  loading={cancelMutation.isPending}
                  onCancel={() => cancelMutation.mutate(listing.id)}
                  onInspect={() => setSelectedMarketItem(marketItemToDetail(listing))}
                />
              ))}
              {marketLedgerTab === 'unlisted' && inventoryItems.length === 0 && <EmptyState text="背包没有可寄售物品。" />}
              {marketLedgerTab === 'unlisted' && inventoryItems.map((item) => {
                const defaultPrice = marketPriceEstimate(item);
                const unitPrice = Number(priceByItem[item.id] || defaultPrice);
                const quantity = item.stackable ? Math.min(item.quantity ?? 1, Math.max(1, Number(quantityByItem[item.id] || 1))) : 1;
                const totalPrice = Math.max(1, unitPrice) * Math.max(1, quantity);
                return (
                  <ItemCard key={item.id} item={item} onSelect={() => setSelectedMarketItem(toEquipmentDetail(item))}>
                    {item.stackable && (
                      <input
                        className="price-input quantity-input"
                        inputMode="numeric"
                        value={quantityByItem[item.id] ?? '1'}
                        onClick={(event) => event.stopPropagation()}
                        onChange={(event) => setQuantityByItem((current) => ({ ...current, [item.id]: event.target.value }))}
                      />
                    )}
                    <input
                      className="price-input"
                      inputMode="numeric"
                      value={priceByItem[item.id] ?? String(defaultPrice)}
                      onClick={(event) => event.stopPropagation()}
                      onChange={(event) => setPriceByItem((current) => ({ ...current, [item.id]: event.target.value }))}
                    />
                    <button
                      className="mini-action"
                      disabled={listMutation.isPending}
                      onClick={(event) => {
                        event.stopPropagation();
                        listMutation.mutate({ item, quantity, unitPrice });
                      }}
                    >
                      上架 {item.stackable ? `${totalPrice}金` : ''}
                    </button>
                  </ItemCard>
                );
              })}
              {marketLedgerTab === 'sold' && playerSales.length === 0 && <EmptyState text="暂时还没有寄售成交。" />}
              {marketLedgerTab === 'sold' && playerSales.map((sale) => (
                <MarketSaleCard
                  key={sale.id}
                  sale={sale}
                  onInspect={() => setSelectedMarketItem(marketItemSnapshotToDetail(sale.item))}
                />
              ))}
            </div>
          </div>
        </aside>

        <section className="market-board main-panel">
          <div className="market-board-head">
            <SectionTitle icon={<ShoppingBag size={18} />} title="商会看板" />
            <strong>{visibleMarketListings.length}/{market.listings.length} 件</strong>
          </div>
          <div className="market-filter-panel">
            <div className="market-choice-row">
              <span>分类</span>
              {[
                ['all', '全部'],
                ['equipment', '装备'],
                ['gem', '宝石'],
                ['material', '材料'],
                ['consumable', '消耗品'],
                ['chest', '宝箱'],
              ].map(([value, label]) => (
                <button key={value} className={marketFilters.category === value ? 'active' : ''} onClick={() => setMarketFilters((current) => ({ ...current, category: value as MarketCategoryFilter, itemType: value === 'equipment' ? current.itemType : 'all' }))}>{label}</button>
              ))}
            </div>
            <div className="market-level-filter">
              <span>等级</span>
              <input inputMode="numeric" value={marketFilters.minLevel} onChange={(event) => setMarketFilters((current) => ({ ...current, minLevel: event.target.value }))} placeholder="最小" />
              <input inputMode="numeric" value={marketFilters.maxLevel} onChange={(event) => setMarketFilters((current) => ({ ...current, maxLevel: event.target.value }))} placeholder="最大" />
            </div>
            <div className="market-choice-row">
              <span>部位</span>
              {[
                ['all', '全部'],
                ['weapon', '武器'],
                ['helmet', '头盔'],
                ['armor', '护甲'],
                ['legs', '护腿'],
                ['boots', '靴子'],
                ['gloves', '护手'],
                ['necklace', '项链'],
                ['ring', '戒指'],
              ].map(([value, label]) => (
                <button key={value} className={marketFilters.itemType === value ? 'active' : ''} onClick={() => setMarketFilters((current) => ({ ...current, itemType: value as MarketItemTypeFilter }))}>{label}</button>
              ))}
            </div>
            <div className="market-choice-row">
              <span>品质</span>
              {[
                ['all', '全部'],
                ['common', '普通'],
                ['uncommon', '优秀'],
                ['rare', '稀有'],
                ['epic', '史诗'],
                ['legendary', '传说'],
              ].map(([value, label]) => (
                <button key={value} className={marketFilters.quality === value ? 'active' : ''} onClick={() => setMarketFilters((current) => ({ ...current, quality: value as MarketQualityFilter }))}>{label}</button>
              ))}
            </div>
            <div className="market-choice-row sort">
              <span>排序</span>
              {[
                ['listedAt', '最新上架'],
                ['unitPrice', '单价低'],
                ['price', '总价低'],
                ['level', '等级排序'],
                ['quality', '品质排序'],
              ].map(([value, label]) => (
                <button key={value} className={marketFilters.sort === value ? 'active' : ''} onClick={() => setMarketFilters((current) => ({ ...current, sort: value as MarketSortKey }))}>{label}</button>
              ))}
              {hasMarketFilters && <button className="reset" onClick={() => setMarketFilters(emptyMarketFilters())}>重置</button>}
            </div>
          </div>
          <div className="market-listing-grid">
            {visibleMarketListings.length === 0 && <EmptyState text="当前筛选下没有商会商品。" />}
            {visibleMarketListings.map((listing) => (
              <div key={listing.id} className={listing.id === highlightListingId ? 'world-event-highlight' : undefined}>
                <ListingCard
                  listing={listing}
                  own={listing.playerListing && listing.sellerName === playerName}
                  loading={buyMutation.isPending || cancelMutation.isPending}
                  onBuy={() => buyMutation.mutate(listing.id)}
                  onCancel={() => cancelMutation.mutate(listing.id)}
                  onInspect={() => setSelectedMarketItem(marketItemToDetail(listing))}
                />
              </div>
            ))}
          </div>
        </section>

        <aside className="market-activity-panel detail-rail">
          <SectionTitle icon={<MessageCircle size={18} />} title="商会动态" />
          <div className="market-activity-list">
            {market.activities.map((activity, index) => (
              <article key={`${activity.actorName}-${activity.createdAt}-${activity.kind}-${activity.text}-${index}`} className={`market-activity ${activity.kind}`}>
                <span>{formatMarketActivityTime(activity)}</span>
                <strong>{activity.actorName}</strong>
                <small>{activity.actorTitle}</small>
                <p>{activity.text}</p>
              </article>
            ))}
          </div>
        </aside>
      </div>
      {selectedMarketItem && <ItemDetail item={selectedMarketItem} onClose={() => setSelectedMarketItem(null)} />}
      {marketActionError && (
        <FeedbackDialog
          variant="error"
          title="商会操作失败"
          message={marketActionError}
          onClose={() => {
            listMutation.reset();
            buyMutation.reset();
            cancelMutation.reset();
          }}
        />
      )}
    </section>
  );
}
