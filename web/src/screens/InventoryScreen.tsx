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

export function InventoryScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string | null>(null);
  const [category, setCategory] = useState('all');
  const [sort, setSort] = useState('quality');
  const [selectedItem, setSelectedItem] = useState<Item | null>(null);
  const [equipCandidate, setEquipCandidate] = useState<Item | null>(null);
  const [enhanceItem, setEnhanceItem] = useState<Item | null>(null);
  const [sellItem, setSellItem] = useState<Item | null>(null);
  const [enhanceMessage, setEnhanceMessage] = useState<string | null>(null);
  const [selectedStoneIds, setSelectedStoneIds] = useState<number[]>([]);
  const { data, isLoading, error } = useQuery({
    queryKey: ['inventory', token],
    queryFn: () => gameApi.inventory(token),
  });
  const visibleInventory = useMemo(() => {
    if (!data) {
      return [];
    }
    return sortItems(data.inventory.filter((item) => itemMatchesCategory(item, category)), sort);
  }, [category, data, sort]);

  const equipMutation = useMutation({
    mutationFn: (itemId: number) => gameApi.equip(token, itemId),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (snapshot) => {
      queryClient.setQueryData(['inventory', token], snapshot);
      setEquipCandidate(null);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const equipBestMutation = useMutation({
    mutationFn: () => gameApi.equipBest(token),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (snapshot) => {
      queryClient.setQueryData(['inventory', token], snapshot);
      setNotice('已穿戴当前最高战力装备');
      await invalidateGameQueries(queryClient, token);
    },
  });
  const unequipMutation = useMutation({
    mutationFn: (itemId: number) => gameApi.unequip(token, itemId),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (snapshot) => {
      queryClient.setQueryData(['inventory', token], snapshot);
      setNotice('装备已下架到背包');
      await invalidateGameQueries(queryClient, token);
    },
  });
  const sellMutation = useMutation({
    mutationFn: (itemId: number) => gameApi.sell(token, itemId),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (snapshot) => {
      queryClient.setQueryData(['inventory', token], snapshot);
      setSellItem(null);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const enhanceMutation = useMutation({
    mutationFn: ({ itemId, stoneItemIds }: { itemId: number; stoneItemIds: number[] }) => gameApi.enhance(token, itemId, stoneItemIds),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setEnhanceMessage(result.success ? '强化成功，装备属性已提升。' : '强化失败，幸运值提升，下次成功率提高。');
      const refreshed = [...result.inventory.inventory, ...Object.values(result.inventory.equippedItems)].find((item) => item.id === enhanceItem?.id);
      if (refreshed) {
        setEnhanceItem(refreshed);
      }
      await invalidateGameQueries(queryClient, token);
    },
  });
  const useItemMutation = useMutation({
    mutationFn: (item: Item) => gameApi.useItem(token, item.id),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setSelectedStoneIds([]);
      const rewardText = result.rewards.length > 0
        ? `，获得 ${result.rewards.map((item) => equipmentDisplayName(item)).join('、')}`
        : '';
      const staminaText = result.stamina ? `，疲劳 ${result.stamina.current}/${result.stamina.max}` : '';
      setNotice(`已使用 ${result.itemName}${rewardText}${staminaText}`);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const craftMutation = useMutation({
    mutationFn: (recipeId: string) => gameApi.craftRecipe(token, recipeId),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setNotice(`已合成 ${result.recipeName}`);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const bulkSellMutation = useMutation({
    mutationFn: ({ qualities, itemTypes }: { qualities: string[]; itemTypes: string[] }) => gameApi.bulkSell(token, qualities, itemTypes),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setNotice(`出售 ${result.soldCount} 件，获得 ${result.goldGained} 金`);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const organizeMutation = useMutation({
    mutationFn: (nextSort: string) => gameApi.organizeInventory(token, nextSort),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (snapshot) => {
      queryClient.setQueryData(['inventory', token], snapshot);
      await invalidateGameQueries(queryClient, token);
    },
  });

  if (isLoading) {
    return <LoadingScreen title="整理背包" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '背包加载失败'} />;
  }

  const equipped = equipmentSlotPairs(data.equippedItems);
  const equippedCount = equipped.filter(([, item]) => Boolean(item)).length;
  const activeTypes = itemTypesForCategory(category === 'all' ? 'equipment' : category);
  const legendaryFragments = inventoryTemplateQuantity(data.inventory, 'mat_fragment_legendary');
  const immortalFragments = inventoryTemplateQuantity(data.inventory, 'mat_fragment_immortal');
  const busy = equipMutation.isPending || equipBestMutation.isPending || unequipMutation.isPending || sellMutation.isPending || enhanceMutation.isPending || useItemMutation.isPending || craftMutation.isPending || bulkSellMutation.isPending || organizeMutation.isPending;
  const inventoryActionError =
    equipMutation.error?.message ??
    equipBestMutation.error?.message ??
    unequipMutation.error?.message ??
    sellMutation.error?.message ??
    enhanceMutation.error?.message ??
    useItemMutation.error?.message ??
    craftMutation.error?.message ??
    bulkSellMutation.error?.message ??
    organizeMutation.error?.message;
  const inventoryFeedback = inventoryActionError
    ? { variant: 'error' as const, title: '操作失败', message: inventoryActionError }
    : notice
      ? { variant: 'success' as const, title: '操作完成', message: notice }
      : null;

  function closeInventoryFeedback() {
    setNotice(null);
    equipMutation.reset();
    equipBestMutation.reset();
    unequipMutation.reset();
    sellMutation.reset();
    enhanceMutation.reset();
    useItemMutation.reset();
    craftMutation.reset();
    bulkSellMutation.reset();
    organizeMutation.reset();
  }

  return (
    <section className="screen inventory-screen">
      <TopBar title="背包装备" onBack={() => setScreen('home')} />

      <div className="inventory-workbench desktop-workbench">
        <aside className="inventory-side filter-rail">
          <div className="inventory-hero">
            <div>
              <span className="eyebrow">背包容量</span>
              <h1>{data.inventory.length}/{data.capacity}</h1>
            </div>
            <div>
              <span>战力 {data.combatPower}</span>
              <strong>{data.gold} 金</strong>
            </div>
          </div>
          <div className="stat-grid compact">
            <Metric label="战力" value={data.combatPower.toString()} />
            <Metric label="金币" value={data.gold.toString()} />
            <Metric label="背包" value={`${data.inventory.length}/${data.capacity}`} />
            <Metric label="已穿戴" value={equippedCount.toString()} />
          </div>
          <SectionTitle icon={<Shield size={18} />} title="已穿戴" />
          <div className="equipment-grid inventory-slot-grid">
            {equipped.map(([slot, item]) => (
              <article
                key={slot}
                className={`equipment-cell equipped-slot-card ${item ? 'filled' : 'empty'} ${enhancementEffectClass(item)}`}
              >
                <button
                  className="slot-inspect-button"
                  disabled={!item}
                  onClick={() => item && setSelectedItem(item)}
                >
                  <div className="slot-label-row">
                    <span>{slotName(slot)}</span>
                    {item && <EnhancementBadge level={item.enhancementLevel} />}
                  </div>
                  {item ? (
                    <>
                      <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
                      <small>{bonusText(item)}</small>
                    </>
                  ) : (
                    <>
                      <strong>未穿戴</strong>
                      <small>等待装备</small>
                    </>
                  )}
                </button>
                {item ? (
                  <div className="slot-action-row">
                    <button
                      className="mini-action subtle"
                      disabled={busy}
                      onClick={(event) => {
                        event.stopPropagation();
                        unequipMutation.mutate(item.id);
                      }}
                    >
                      下架
                    </button>
                    <button
                      className="mini-action"
                      disabled={busy}
                      onClick={(event) => {
                        event.stopPropagation();
                        setEnhanceMessage(null);
                        setSelectedStoneIds([]);
                        setEnhanceItem(item);
                      }}
                    >
                      强化
                    </button>
                  </div>
                ) : null}
              </article>
            ))}
          </div>
        </aside>

        <section className="inventory-main main-panel">
          <div className="inventory-main-title">
            <SectionTitle icon={<Sparkles size={18} />} title={`${categoryNameForInventory(category)}物品`} />
            <strong>{visibleInventory.length} 件</strong>
          </div>
          <div className="item-grid">
            {visibleInventory.length === 0 && <EmptyState text="当前分类没有可操作装备。" />}
            {visibleInventory.map((item) => (
              <ItemCard
                key={item.id}
                item={item}
                powerIncrease={isEquipmentItem(item) && isEquipmentUpgrade(item, data.equippedItems)}
                onSelect={() => setSelectedItem(item)}
              >
                {isEquipmentItem(item) ? (
                  <>
                    <button className="mini-action" disabled={busy} onClick={(event) => {
                      event.stopPropagation();
                      setEquipCandidate(item);
                    }}>穿戴</button>
                    <button className="mini-action" disabled={busy} onClick={(event) => {
                      event.stopPropagation();
                      setEnhanceMessage(null);
                      setSelectedStoneIds([]);
                      setEnhanceItem(item);
                    }}>强化</button>
                  </>
                ) : (
                  <button
                    className="mini-action"
                    disabled={busy || !['staminaPotion', 'attributePotion', 'chest'].includes(item.effectType ?? '')}
                    onClick={(event) => {
                      event.stopPropagation();
                      useItemMutation.mutate(item);
                    }}
                  >
                    {item.effectType === 'chest' ? '开启' : '使用'}
                  </button>
                )}
                <button className="mini-action danger" disabled={busy} onClick={(event) => {
                  event.stopPropagation();
                  setSellItem(item);
                }}>出售</button>
              </ItemCard>
            ))}
          </div>
        </section>

        <aside className="inventory-tools-panel detail-rail">
          <SectionTitle icon={<Backpack size={18} />} title="背包工具" />
          <button className="mini-action tool-action-wide" disabled={busy || data.inventory.length === 0} onClick={() => equipBestMutation.mutate()}>
            <ArrowUp size={16} />
            {equipBestMutation.isPending ? '穿戴中...' : '一键穿戴最高战力'}
          </button>
          <div className="segmented filter-tabs">
            {[
              ['all', '全部'],
              ['equipment', '装备'],
              ['consumable', '消耗品'],
              ['material', '材料'],
              ['chest', '宝箱'],
              ['weapon', '武器'],
              ['armor', '防具'],
              ['accessory', '饰品'],
            ].map(([value, label]) => (
              <button key={value} className={category === value ? 'active' : ''} onClick={() => setCategory(value)}>
                {label}
              </button>
            ))}
          </div>
          <div className="tool-row">
            <button className={sort === 'quality' ? 'mini-action' : 'mini-action subtle'} disabled={busy} onClick={() => {
              setSort('quality');
              organizeMutation.mutate('quality');
            }}>品质整理</button>
            <button className={sort === 'level' ? 'mini-action' : 'mini-action subtle'} disabled={busy} onClick={() => {
              setSort('level');
              organizeMutation.mutate('level');
            }}>等级整理</button>
            <button className={sort === 'type' ? 'mini-action' : 'mini-action subtle'} disabled={busy} onClick={() => {
              setSort('type');
              organizeMutation.mutate('type');
            }}>类型整理</button>
          </div>
          <SectionTitle icon={<Package size={18} />} title="碎片合成" />
          <div className="craft-recipe-list">
            <button className="recipe-button" disabled={busy || legendaryFragments < 20} onClick={() => craftMutation.mutate('recipe_legendary_cache')}>
              <strong>传说装备宝箱</strong>
              <span>{legendaryFragments}/20 传说碎片</span>
            </button>
            <button className="recipe-button" disabled={busy || immortalFragments < 30} onClick={() => craftMutation.mutate('recipe_immortal_cache')}>
              <strong>不朽装备宝箱</strong>
              <span>{immortalFragments}/30 不朽碎片</span>
            </button>
          </div>
          <SectionTitle icon={<Coins size={18} />} title="按品质卖出" />
          <div className="quality-sell-grid">
            {[
              ['common', '普通'],
              ['uncommon', '优秀'],
              ['rare', '稀有'],
              ['epic', '史诗'],
              ['legendary', '传说'],
              ['immortal', '不朽'],
            ].map(([quality, label]) => (
              <button
                key={quality}
                className={`quality-sell ${quality}`}
                disabled={busy || activeTypes.length === 0}
                onClick={() => bulkSellMutation.mutate({ qualities: [quality], itemTypes: activeTypes })}
              >
                卖{label}
              </button>
            ))}
          </div>
        </aside>
      </div>
      {selectedItem && <ItemDetail item={toEquipmentDetail(selectedItem)} onClose={() => setSelectedItem(null)} />}
      {equipCandidate && (
        <EquipConfirmModal
          item={equipCandidate}
          currentItem={data.equippedItems[targetEquipSlot(equipCandidate, data.equippedItems)] ?? null}
          targetSlot={targetEquipSlot(equipCandidate, data.equippedItems)}
          currentPower={data.combatPower}
          loading={equipMutation.isPending}
          onClose={() => setEquipCandidate(null)}
          onConfirm={() => equipMutation.mutate(equipCandidate.id)}
        />
      )}
      {enhanceItem && (
        <EnhanceModal
          item={enhanceItem}
          gold={data.gold}
          message={enhanceMessage}
          loading={enhanceMutation.isPending}
          onClose={() => {
            setEnhanceItem(null);
            setEnhanceMessage(null);
            setSelectedStoneIds([]);
          }}
          stones={enhancementStonesForItem(data.inventory, enhanceItem)}
          selectedStoneIds={selectedStoneIds}
          onAddStone={(stoneId) => setSelectedStoneIds((current) => current.length >= 3 ? current : [...current, stoneId])}
          onRemoveStone={(index) => setSelectedStoneIds((current) => current.filter((_, currentIndex) => currentIndex !== index))}
          onEnhance={() => enhanceMutation.mutate({ itemId: enhanceItem.id, stoneItemIds: selectedStoneIds })}
        />
      )}
      {sellItem && (
        <SellConfirmModal
          item={sellItem}
          loading={sellMutation.isPending}
          onClose={() => setSellItem(null)}
          onConfirm={() => sellMutation.mutate(sellItem.id)}
        />
      )}
      {inventoryFeedback && (
        <FeedbackDialog
          variant={inventoryFeedback.variant}
          title={inventoryFeedback.title}
          message={inventoryFeedback.message}
          onClose={closeInventoryFeedback}
        />
      )}
    </section>
  );
}

