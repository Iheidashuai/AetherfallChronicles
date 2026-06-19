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
import { authApi, gameApi } from './api';
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
} from './api';
import { useAppStore } from './store';
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
} from './types';
import {
  STAMINA_RECOVERY_SECONDS,
  ANNOUNCEMENT_SEEN_STORAGE_KEY,
  ATTRIBUTE_LABELS,
  CREATE_PROFESSIONS,
} from './lib/constants';
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
} from './lib/helpers';


import {
  GlobalTicker, ProfessionGlyph, InfoPanel, SkillCard, SpecialDungeonPanel, EquipmentProcessingPanel, RiftResultPanel, ArenaProfileCard, ArenaOpponentCard, ArenaShopCard, ArenaMatchPanel, ArenaFighterCard, ArenaRankRow, BuildScorePanel, BuildSimulationPanel, WealthTierCard, RobotRechargeCard, CashIncomeCard, RobotRangeFilter, RobotActivityCard, RobotEventCard, RobotActivityDetailModal, ChatMessageBubble, SpeakerDetailModal, DungeonCard, DropPreviewCard, HomeEquipmentOverview, EquipmentPanel, PowerBreakdownPanel, CombatStatsPanel, StatContributionGrid, CombatStatCell, StatContributionCell, QuestCard, QuestDetailPanel, EnhancementBadge, ItemCard, TransferItemOption, ItemDetail, EquipConfirmModal, CompareCard, EnhanceModal, SellConfirmModal, CombatantCard, HpBar, ResultSummaryModal, SweepSummaryModal, MarketSaleCard, MarketListedCard, ListingCard, LeaderboardCard, CatalogFilterGroup, CatalogDetailPanel, Metric, StaminaPanel, NavTile, SectionTitle, EmptyState, FeedbackDialog, ConfirmDialog, TopBar, LoadingScreen, ErrorScreen,
} from './components/ui';
import { AuthScreen } from './screens/AuthScreen';
import { CreatePlayerScreen } from './screens/CreatePlayerScreen';
import { HomeScreen } from './screens/HomeScreen';
import { CharacterScreen } from './screens/CharacterScreen';
import { SkillsScreen } from './screens/SkillsScreen';
import { ItemCatalogScreen } from './screens/ItemCatalogScreen';
import { DungeonScreen } from './screens/DungeonScreen';
import { ResultScreen } from './screens/ResultScreen';
import { InventoryScreen } from './screens/InventoryScreen';
import { BlacksmithScreen } from './screens/BlacksmithScreen';
import { QuestScreen } from './screens/QuestScreen';
import { MarketScreen } from './screens/MarketScreen';
import { RobotActivityScreen } from './screens/RobotActivityScreen';
import { ShopScreen } from './screens/ShopScreen';
import { BuildsScreen } from './screens/BuildsScreen';
import { EndgameRiftScreen } from './screens/EndgameRiftScreen';
import { ArenaScreen } from './screens/ArenaScreen';
import { RechargeScreen } from './screens/RechargeScreen';
import { ChatScreen } from './screens/ChatScreen';
import { LeaderboardScreen } from './screens/LeaderboardScreen';

export function App() {
  const token = useAppStore((state) => state.token);
  const screen = useAppStore((state) => state.screen);
  const setScreen = useAppStore((state) => state.setScreen);
  const [lastResult, setLastResult] = useState<DungeonRunResult | null>(null);
  const announcementsQuery = useQuery({
    queryKey: ['announcements', token],
    queryFn: () => gameApi.announcements(token!),
    enabled: Boolean(token) && screen !== 'auth',
    refetchInterval: 10_000,
  });

  return (
    <main className="app-shell">
      <div className={`phone-frame game-frame${token && screen !== 'auth' ? ' has-global-ticker' : ''}`}>
        {token && screen !== 'auth' && <GlobalTicker announcements={announcementsQuery.data ?? []} />}
        <div className="screen-host">
          {screen === 'auth' && <AuthScreen />}
          {screen === 'create-player' && token && <CreatePlayerScreen token={token} />}
          {screen === 'home' && token && <HomeScreen token={token} />}
          {screen === 'character' && token && <CharacterScreen token={token} />}
          {screen === 'inventory' && token && <InventoryScreen token={token} />}
          {screen === 'item-catalog' && token && <ItemCatalogScreen token={token} />}
          {screen === 'skills' && token && <SkillsScreen token={token} />}
          {screen === 'blacksmith' && token && <BlacksmithScreen token={token} />}
          {screen === 'quests' && token && <QuestScreen token={token} />}
          {screen === 'market' && token && <MarketScreen token={token} />}
          {screen === 'shop' && token && <ShopScreen token={token} />}
          {screen === 'builds' && token && <BuildsScreen token={token} />}
          {screen === 'endgame' && token && <EndgameRiftScreen token={token} />}
          {screen === 'arena' && token && <ArenaScreen token={token} />}
          {screen === 'chat' && token && <ChatScreen token={token} />}
          {screen === 'leaderboard' && token && <LeaderboardScreen token={token} />}
          {screen === 'robots' && token && <RobotActivityScreen token={token} />}
          {screen === 'recharge' && token && <RechargeScreen token={token} />}
          {screen === 'dungeons' && token && (
            <DungeonScreen
              token={token}
              onResult={(result) => {
                setLastResult(result);
                setScreen('result');
              }}
            />
          )}
          {screen === 'result' && lastResult && <ResultScreen result={lastResult} onResult={setLastResult} />}
        </div>
      </div>
    </main>
  );
}

