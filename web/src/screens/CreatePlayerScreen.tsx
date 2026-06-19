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

export function CreatePlayerScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [name, setName] = useState('灰烬行者');
  const [profession, setProfession] = useState<PlayableProfession>('warrior');
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const selectedProfession = CREATE_PROFESSIONS.find((option) => option.id === profession) ?? CREATE_PROFESSIONS[0];

  const mutation = useMutation({
    mutationFn: () => gameApi.createPlayer(token, name.trim(), profession),
    onSuccess: async () => {
      window.localStorage.setItem('mythic.hasPlayer', 'true');
      await queryClient.invalidateQueries({ queryKey: ['home', token] });
      setScreen('home');
    },
    onError: (err: Error) => setError(err.message),
  });
  const canCreate = name.trim().length > 0 && !mutation.isPending;

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (name.trim().length === 0 || mutation.isPending) {
      return;
    }
    mutation.mutate();
  };

  return (
    <section className="screen create-player-screen">
      <TopBar title="创建角色" />
      <div className="create-player-layout">
        <aside className={`create-hero-panel ${selectedProfession.id}`}>
          <div className="create-hero-head">
            <span className="profession-emblem">
              <ProfessionGlyph profession={selectedProfession.id} size={30} />
            </span>
            <div>
              <span className="eyebrow">职业档案</span>
              <h1>{selectedProfession.name}</h1>
              <p>{selectedProfession.title}</p>
            </div>
          </div>

          <p className="create-hero-summary">{selectedProfession.summary}</p>

          <div className="create-key-facts">
            <Metric label="定位" value={selectedProfession.role} />
            <Metric label="难度" value={selectedProfession.difficulty} />
            <Metric label="生存" value={selectedProfession.survival} />
            <Metric label="节奏" value={selectedProfession.tempo} />
          </div>

          <div className="attribute-board">
            <div className="section-title">
              <div>
                <span className="eyebrow">初始属性</span>
                <h2>创建后真实写入角色档案</h2>
              </div>
              <strong>{selectedProfession.signature}</strong>
            </div>
            <div className="attribute-meter-list">
              {ATTRIBUTE_LABELS.map((attribute) => {
                const value = selectedProfession.attributes[attribute.key];
                const meterStyle = { '--attribute-value': `${value * 10}%` } as CSSProperties;
                return (
                  <div className="attribute-meter" key={attribute.key}>
                    <div>
                      <strong>{attribute.label}</strong>
                      <span>{attribute.meaning}</span>
                    </div>
                    <div className="attribute-meter-track" aria-hidden="true">
                      <span style={meterStyle} />
                    </div>
                    <b>{value}</b>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="growth-note">
            <Sparkles size={18} />
            <span>{selectedProfession.growth[0]}；每级还会获得 3 点自由属性。</span>
          </div>
        </aside>

        <section className="create-choice-panel">
          <div className="create-choice-head">
            <div>
              <span className="eyebrow">选择战斗风格</span>
              <h2>先决定你想怎样开荒</h2>
            </div>
            <p>三种职业都能完整体验副本、装备、强化和市场，只是成长曲线与容错空间不同。</p>
          </div>

          <div className="profession-card-grid">
            {CREATE_PROFESSIONS.map((option) => (
              <button
                type="button"
                key={option.id}
                className={`create-profession-card ${option.id}${option.id === profession ? ' active' : ''}`}
                aria-pressed={option.id === profession}
                onClick={() => {
                  setProfession(option.id);
                  setError(null);
                }}
              >
                <span className="profession-card-icon">
                  <ProfessionGlyph profession={option.id} size={24} />
                </span>
                <span>
                  <strong>{option.name}</strong>
                  <small>{option.role}</small>
                </span>
                <b>{option.difficulty}</b>
              </button>
            ))}
          </div>

          <div className="create-detail-grid">
            <InfoPanel title="战斗方式" items={selectedProfession.combat} />
            <InfoPanel title="适合你如果" items={selectedProfession.bestFor} />
            <InfoPanel title="成长重点" items={selectedProfession.growth} />
            <div className="create-warning-panel">
              <CircleAlert size={18} />
              <div>
                <strong>开荒提醒</strong>
                <p>{selectedProfession.caution}</p>
              </div>
            </div>
          </div>
        </section>
      </div>

      <form className="create-footer" onSubmit={handleSubmit}>
        <label className="create-name-field">
          角色名
          <input
            value={name}
            maxLength={16}
            placeholder="输入 2-16 个字符"
            onChange={(event) => {
              setName(event.target.value);
              setError(null);
            }}
          />
        </label>
        <div className="create-submit-copy">
          <strong>{selectedProfession.name} · {selectedProfession.role}</strong>
          <span>创建后写入 MySQL，服务端自动发放初始装备。</span>
        </div>
        <button className="primary-action" disabled={!canCreate} type="submit">
          {mutation.isPending ? '创建中...' : '开始冒险'}
          <ChevronRight size={18} />
        </button>
      </form>
      {error && <FeedbackDialog variant="error" title="创建失败" message={error} onClose={() => setError(null)} />}
    </section>
  );
}

