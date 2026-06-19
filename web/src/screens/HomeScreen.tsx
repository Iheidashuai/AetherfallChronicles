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

export function HomeScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const logout = useAppStore((state) => state.logout);
  const [selectedEquipment, setSelectedEquipment] = useState<Item | null>(null);
  const { data, error, isLoading } = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
  });

  if (isLoading) {
    return <LoadingScreen title="同步角色档案" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '主页加载失败'} />;
  }

  return (
    <section className="screen home-screen">
      <div className="home-header">
        <div className="hero-status">
          <div>
            <span className="eyebrow">Lv.{data.player.level} {professionName(data.player.profession)}</span>
            <h1>{data.player.name}</h1>
          </div>
          <button className="icon-button" onClick={logout} aria-label="退出登录">
            <LogOut size={18} />
          </button>
        </div>

        <div className="stat-grid">
          <Metric label="战力" value={data.combatPower.toString()} />
          <Metric label="金币" value={formatNumber(data.player.gold)} />
          <Metric label="余额" value={`${formatNumber(data.player.realMoney)} 元`} />
          <Metric label="背包" value={`${data.inventoryCount}/${data.inventoryCapacity}`} />
        </div>
        <StaminaPanel stamina={data.stamina} />
      </div>

      <div className="home-workbench">
        <aside className="home-column">
          <CombatStatsPanel stats={data.derivedStats} equipmentStats={data.equipmentStats} compact />
          <HomeEquipmentOverview
            home={data}
            onSelect={setSelectedEquipment}
            onManage={() => setScreen('inventory')}
            onProfile={() => setScreen('character')}
          />
        </aside>
        <section className="home-column">
          <div className="nav-grid">
            <NavTile icon={<Swords size={20} />} title="副本" detail={`${data.config.dungeonCount} 个副本`} onClick={() => setScreen('dungeons')} />
            <NavTile icon={<Skull size={20} />} title="深渊" detail="Endgame 裂隙" onClick={() => setScreen('endgame')} />
            <NavTile icon={<Shield size={20} />} title="竞技场" detail="异步 PVP" onClick={() => setScreen('arena')} />
            <NavTile icon={<Trophy size={20} />} title="公会" detail="社交 · 公会Boss" onClick={() => setScreen('guild')} />
            <NavTile icon={<Backpack size={20} />} title="背包" detail="穿戴 · 出售 · 强化" onClick={() => setScreen('inventory')} />
            <NavTile icon={<Boxes size={20} />} title="物品" detail={`${data.config.itemCount} 种图鉴`} onClick={() => setScreen('item-catalog')} />
            <NavTile icon={<Sparkles size={20} />} title="技能" detail={`技能战力 ${data.powerBreakdown.skillPower ?? 0}`} onClick={() => setScreen('skills')} />
            <NavTile icon={<Hammer size={20} />} title="铁匠铺" detail="强化 · 转移" onClick={() => setScreen('blacksmith')} />
            <NavTile icon={<ScrollText size={20} />} title="任务" detail="主线 · 日常 · 成就" onClick={() => setScreen('quests')} />
            <NavTile icon={<ShoppingBag size={20} />} title="市场" detail="寄售 · 购买" onClick={() => setScreen('market')} />
            <NavTile icon={<Package size={20} />} title="商店" detail="余额补给" onClick={() => setScreen('shop')} />
            <NavTile icon={<Filter size={20} />} title="构筑" detail="流派配置" onClick={() => setScreen('builds')} />
            <NavTile icon={<Coins size={20} />} title="充值" detail={data.player.wealthTier} onClick={() => setScreen('recharge')} />
            <NavTile icon={<MessageCircle size={20} />} title="聊天" detail="世界频道" onClick={() => setScreen('chat')} />
            <NavTile icon={<Trophy size={20} />} title="榜单" detail="战力排名" onClick={() => setScreen('leaderboard')} />
            <NavTile icon={<Gauge size={20} />} title="动态" detail="机器人后台" onClick={() => setScreen('robots')} />
            <NavTile icon={<UserRound size={20} />} title="角色" detail="属性档案" onClick={() => setScreen('character')} />
          </div>
        </section>
      </div>
      {selectedEquipment && <ItemDetail item={toEquipmentDetail(selectedEquipment)} onClose={() => setSelectedEquipment(null)} />}
    </section>
  );
}

