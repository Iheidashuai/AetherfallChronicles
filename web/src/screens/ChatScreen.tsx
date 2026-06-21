import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AtSign,
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

export function ChatScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const [selectedSpeaker, setSelectedSpeaker] = useState<ChatSpeaker | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streamConnected, setStreamConnected] = useState(false);
  const chatListRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const lastMessageIdRef = useRef(0);
  const streamConnectedRef = useRef(false);
  const { data, isLoading, error } = useQuery({
    queryKey: ['chat', token, 'world'],
    queryFn: () => gameApi.chatMessages(token, 'world'),
  });
  const sendMutation = useMutation({
    mutationFn: (messageText: string) => gameApi.sendChat(token, messageText, 'world'),
    onSuccess: async (message) => {
      appendIncomingMessage(message);
      setText('');
      await invalidateGameQueries(queryClient, token);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!text.trim()) {
      return;
    }
    sendMutation.mutate(text.trim());
  }

  function appendIncomingMessage(message: ChatMessage) {
    if (!message || !Number.isFinite(message.id)) {
      return;
    }
    appendIncomingMessages([message]);
  }

  function appendIncomingMessages(incoming: ChatMessage[]) {
    if (incoming.length === 0) {
      return;
    }
    setMessages((previous) => {
      const byId = new Map<number, ChatMessage>();
      for (const item of previous) {
        byId.set(item.id, item);
      }
      for (const item of incoming) {
        if (item && Number.isFinite(item.id)) {
          byId.set(item.id, item);
        }
      }
      const next = [...byId.values()].sort((left, right) => left.id - right.id).slice(-120);
      lastMessageIdRef.current = next.at(-1)?.id ?? lastMessageIdRef.current;
      return next;
    });
  }

  function mentionSpeaker(speaker: ChatSpeaker) {
    const name = speaker.name?.trim();
    if (!name) {
      return;
    }
    setText((current) => {
      const prefix = `@${name} `;
      if (current.includes(prefix)) {
        return current;
      }
      return current.trim() ? `${prefix}${current}` : prefix;
    });
    window.requestAnimationFrame(() => inputRef.current?.focus());
  }

  useEffect(() => {
    if (!data) {
      return;
    }
    const initialMessages = [...data].sort((left, right) => left.id - right.id);
    setMessages(initialMessages);
    lastMessageIdRef.current = initialMessages.at(-1)?.id ?? 0;
  }, [data]);

  useEffect(() => {
    if (!data) {
      return undefined;
    }
    let closed = false;
    const source = new EventSource(gameApi.chatStreamUrl(token, lastMessageIdRef.current, 'world'));
    source.onopen = () => {
      if (!closed) {
        setStreamConnected(true);
      }
    };
    source.onerror = () => {
      if (!closed) {
        setStreamConnected(false);
        gameApi.chatMessages(token, 'world').then(appendIncomingMessages).catch(() => undefined);
      }
    };
    source.addEventListener('message', (event: MessageEvent) => {
      try {
        appendIncomingMessage(JSON.parse(event.data) as ChatMessage);
      } catch {
        // Ignore malformed stream chunks.
      }
    });
    return () => {
      closed = true;
      source.close();
      setStreamConnected(false);
    };
  }, [token, Boolean(data)]);

  useEffect(() => {
    streamConnectedRef.current = streamConnected;
  }, [streamConnected]);

  useEffect(() => {
    if (!data) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      if (!streamConnectedRef.current) {
        gameApi.chatMessages(token, 'world').then(appendIncomingMessages).catch(() => undefined);
      }
    }, 5000);
    return () => window.clearInterval(timer);
  }, [token, Boolean(data)]);

  useEffect(() => {
    const node = chatListRef.current;
    if (!node || !messages.length) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [messages]);

  if (isLoading) {
    return <LoadingScreen title="接入传讯水晶" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '聊天加载失败'} />;
  }
  const robotMessageCount = messages.filter((message) => message.kind === 'robot').length;
  const systemMessageCount = messages.filter((message) => message.kind === 'system' || message.speaker.kind === 'system').length;
  const recentSpeakers = messages
    .filter((message) => message.speaker.kind !== 'system')
    .slice(-8)
    .reverse();

  return (
    <section className="screen chat-screen">
      <TopBar title="世界聊天" onBack={() => setScreen('home')} />
      <div className="channel-strip chat-channel-strip">
        <span>世界</span>
        <strong>{robotMessageCount}+ 在线发言</strong>
        <i className={`stream-indicator ${streamConnected ? 'online' : ''}`} />
      </div>
      <div className="desktop-workbench chat-workbench">
        <section className="main-panel chat-main-panel">
          <div className="chat-list" ref={chatListRef}>
            {messages.map((message) => (
              <ChatMessageBubble key={message.id} message={message} onSelectSpeaker={setSelectedSpeaker} />
            ))}
          </div>
          <form className="chat-form" onSubmit={submit}>
            <input
              ref={inputRef}
              value={text}
              maxLength={120}
              placeholder="@名字 可以点名聊天"
              onChange={(event) => setText(event.target.value)}
            />
            <button className="icon-button send-button" disabled={sendMutation.isPending} aria-label="发送">
              <Send size={18} />
            </button>
          </form>
        </section>
        <aside className="detail-rail chat-side-panel">
          <SectionTitle icon={<MessageCircle size={18} />} title="频道情报" />
          <div className="chat-side-metrics">
            <Metric label="消息" value={messages.length.toString()} />
            <Metric label="冒险者" value={robotMessageCount.toString()} />
            <Metric label="系统" value={systemMessageCount.toString()} />
            <Metric label="连接" value={streamConnected ? '在线' : '等待'} />
          </div>
          <SectionTitle icon={<UserRound size={18} />} title="最近发言" />
          <div className="chat-speaker-list">
            {recentSpeakers.length === 0 && <EmptyState text="暂时没有发言者。" />}
            {recentSpeakers.map((message) => (
              <div
                key={`${message.id}-${message.senderName}`}
                className="chat-speaker-card"
              >
                <button className="chat-speaker-main" onClick={() => setSelectedSpeaker(message.speaker)}>
                  <span>{message.speaker.title || '冒险者'}</span>
                  <strong>{message.speaker.name || message.senderName}</strong>
                  <small>Lv.{message.speaker.level} · 战力 {formatNumber(message.speaker.power)}</small>
                </button>
                <button
                  className="chat-mention-button"
                  title={`@${message.speaker.name || message.senderName}`}
                  aria-label={`@${message.speaker.name || message.senderName}`}
                  onClick={() => mentionSpeaker(message.speaker)}
                >
                  <AtSign size={14} />
                </button>
              </div>
            ))}
          </div>
        </aside>
      </div>
      {sendMutation.error && (
        <FeedbackDialog
          variant="error"
          title="发送失败"
          message={sendMutation.error.message}
          onClose={() => sendMutation.reset()}
        />
      )}
      {selectedSpeaker && <SpeakerDetailModal speaker={selectedSpeaker} onClose={() => setSelectedSpeaker(null)} />}
    </section>
  );
}
