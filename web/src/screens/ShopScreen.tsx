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

const MAX_SHOP_PURCHASE_QUANTITY = 999;

export function ShopScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [category, setCategory] = useState<ShopCategoryFilter>('all');
  const [selectedOfferId, setSelectedOfferId] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [notice, setNotice] = useState<string | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['shop', token],
    queryFn: () => gameApi.shop(token),
  });
  const purchaseMutation = useMutation({
    mutationFn: ({ offerId, count }: { offerId: string; count: number }) => gameApi.buyShopOffer(token, offerId, count),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setNotice(shopPurchaseNotice(result.offer, result.quantity, result.goldGained, result.rewards));
      await invalidateGameQueries(queryClient, token);
      await queryClient.invalidateQueries({ queryKey: ['shop', token] });
      await queryClient.invalidateQueries({ queryKey: ['recharge-dashboard', token] });
      await queryClient.invalidateQueries({ queryKey: ['robot-activity', token] });
    },
  });

  if (isLoading) {
    return <LoadingScreen title="打开商店" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '商店加载失败'} />;
  }

  const visibleOffers = data.offers.filter((offer) => category === 'all' || offer.category === category);
  const selectedOffer = visibleOffers.find((offer) => offer.id === selectedOfferId)
    ?? visibleOffers[0]
    ?? data.offers[0]
    ?? null;
  const selectedRemaining = selectedOffer?.remainingPurchases ?? MAX_SHOP_PURCHASE_QUANTITY;
  const maxQuantity = Math.max(1, Math.min(MAX_SHOP_PURCHASE_QUANTITY, selectedRemaining <= 0 ? 1 : selectedRemaining));
  const safeQuantity = Math.max(1, Math.min(maxQuantity, quantity));
  const totalPrice = selectedOffer ? selectedOffer.priceRmb * safeQuantity : 0;
  const canBuySelected = Boolean(selectedOffer?.unlocked && !selectedOffer.soldOut && totalPrice <= data.wallet.realMoney && !purchaseMutation.isPending);
  const shopError = purchaseMutation.error?.message ?? null;
  const categoryRows: Array<[ShopCategoryFilter, string]> = [
    ['all', '全部'],
    ['sweep', '扫荡'],
    ['gold', '金币'],
    ['stamina', '疲劳'],
    ['enhancement', '强化'],
    ['gem', '宝石'],
    ['growth', '成长'],
    ['chest', '宝箱'],
  ];

  function buyOffer(offer: ShopOffer, count = 1) {
    setSelectedOfferId(offer.id);
    setQuantity(count);
    purchaseMutation.mutate({ offerId: offer.id, count });
  }

  return (
    <section className="screen shop-screen">
      <TopBar title="冒险者商店" onBack={() => setScreen('home')} />
      <div className="stat-grid shop-status-strip status-strip">
        <Metric label="余额" value={`${formatNumber(data.wallet.realMoney)} 元`} />
        <Metric label="金币" value={`${formatNumber(data.wallet.gold)} 金`} />
        <Metric label="财富标签" value={data.wallet.wealthTier} />
        <Metric label="兑换比例" value={`1:${formatNumber(data.wallet.exchangeRate)}`} />
        <Metric label="商品" value={`${visibleOffers.length}/${data.offers.length}`} />
      </div>

      <div className="shop-workbench desktop-workbench">
        <aside className="shop-filter-rail filter-rail">
          <SectionTitle icon={<ShoppingBag size={18} />} title="货架分类" />
          <div className="shop-category-list">
            {categoryRows.map(([value, label]) => {
              const count = value === 'all' ? data.offers.length : data.offers.filter((offer) => offer.category === value).length;
              return (
                <button key={value} className={category === value ? 'active' : ''} onClick={() => setCategory(value)}>
                  <span>{label}</span>
                  <strong>{count}</strong>
                </button>
              );
            })}
          </div>
          <div className="shop-wallet-card">
            <span className="eyebrow">当前钱包</span>
            <h1>{formatNumber(data.wallet.realMoney)} 元</h1>
            <p>{formatNumber(data.wallet.gold)} 金</p>
          </div>
        </aside>

        <section className="shop-main-panel main-panel">
          <div className="inventory-main-title">
            <SectionTitle icon={<Package size={18} />} title={`${shopCategoryName(category)}商品`} />
            <strong>{visibleOffers.length} 个</strong>
          </div>
          <div className="shop-offer-grid">
            {visibleOffers.length === 0 && <EmptyState text="当前分类暂无商品。" />}
            {visibleOffers.map((offer) => (
              <article
                key={offer.id}
                className={`shop-offer-card category-${offer.category} ${selectedOffer?.id === offer.id ? 'selected' : ''} ${offer.quality ?? offer.category}`}
                onClick={() => setSelectedOfferId(offer.id)}
              >
                <div className="shop-offer-head">
                  <span className={`quality ${offer.quality ?? offer.category}`}>{shopCategoryName(offer.category)}</span>
                  <strong>{offer.name}</strong>
                </div>
                <p>{offer.description}</p>
                <div className="shop-offer-reward">
                  <strong>{shopOfferRewardText(offer)}</strong>
                  <span>{formatNumber(offer.priceRmb)} 元</span>
                </div>
                <div className="shop-offer-meta">
                  <span>Lv.{offer.requiredLevel}</span>
                  <span>{offer.unlocked ? '可购买' : '等级不足'}</span>
                  <span>{offer.affordable ? '余额足够' : '余额不足'}</span>
                  {offer.purchaseLimit ? <span>{offer.soldOut ? '已限购' : `剩余 ${offer.remainingPurchases ?? offer.purchaseLimit}`}</span> : null}
                </div>
                <button
                  className="mini-action"
                  disabled={!offer.unlocked || !offer.affordable || offer.soldOut || purchaseMutation.isPending}
                  onClick={(event) => {
                    event.stopPropagation();
                    buyOffer(offer, 1);
                  }}
                >
                  购买一份
                </button>
              </article>
            ))}
          </div>
        </section>

        <aside className="shop-detail-rail detail-rail">
          <SectionTitle icon={<Coins size={18} />} title="购买结算" />
          {selectedOffer ? (
            <>
              <div className={`shop-selected-card ${selectedOffer.quality ?? selectedOffer.category}`}>
                <span className="eyebrow">{shopCategoryName(selectedOffer.category)}</span>
                <h2>{selectedOffer.name}</h2>
                <p>{selectedOffer.description}</p>
                <strong>{shopOfferRewardText(selectedOffer)}</strong>
              </div>
              <div className="shop-quantity-control">
                <button disabled={purchaseMutation.isPending || safeQuantity <= 1} onClick={() => setQuantity((current) => Math.max(1, current - 1))}>-</button>
                <input
                  inputMode="numeric"
                  value={String(safeQuantity)}
                  onChange={(event) => setQuantity(Math.max(1, Math.min(maxQuantity, Number(event.target.value.replace(/[^\d]/g, '')) || 1)))}
                />
                <button disabled={purchaseMutation.isPending || safeQuantity >= maxQuantity} onClick={() => setQuantity((current) => Math.min(maxQuantity, current + 1))}>+</button>
              </div>
              {selectedOffer.purchaseLimit ? (
                <div className="shop-limit-note">
                  限购 {selectedOffer.purchaseLimit} 份，已购 {selectedOffer.purchasedQuantity ?? 0} 份
                </div>
              ) : null}
              <div className="shop-total-card">
                <span>合计</span>
                <strong>{formatNumber(totalPrice)} 元</strong>
                <small>余额剩余 {formatNumber(Math.max(0, data.wallet.realMoney - totalPrice))} 元</small>
              </div>
              <button
                className="primary-action"
                disabled={!canBuySelected}
                onClick={() => purchaseMutation.mutate({ offerId: selectedOffer.id, count: safeQuantity })}
              >
                {selectedOffer.soldOut ? '已限购' : purchaseMutation.isPending ? '购买中...' : '确认购买'}
              </button>
            </>
          ) : (
            <EmptyState text="选择一个商品后查看结算。" />
          )}
        </aside>
      </div>

      {notice && (
        <FeedbackDialog
          variant="success"
          title="购买完成"
          message={notice}
          onClose={() => {
            setNotice(null);
            purchaseMutation.reset();
          }}
        />
      )}
      {shopError && (
        <FeedbackDialog
          variant="error"
          title="购买失败"
          message={shopError}
          onClose={() => purchaseMutation.reset()}
        />
      )}
    </section>
  );
}
