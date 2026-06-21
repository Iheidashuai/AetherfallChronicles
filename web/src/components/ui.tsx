import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
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
  emptyRobotFilters, enhanceBaseChance, enhanceChance, enhanceCost, enhanceLuckBonus, enhancedCritValue, enhancedStatValue,
  enhancementBadgeText, enhancementEffectClass, enhancementStage, enhancementStonesForItem, 
  equipmentCompactText, equipmentDisplayName, equipmentOriginText, equipmentSlotOrder, 
  equipmentSlotPairs, filterCatalogItems, filterMarketListings, filterNumber, filterRobots, 
  formatChatTime, formatDropRate, formatMarketActivityTime, formatNumber, formatPercent, 
  formatRelativeTime, formatSigned, formatStaminaTime, formatStatValue, gemEffectText, 
  gemInventoryGroups, homeSlotShortName, inventoryTemplateQuantity, isEquipmentItem,
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
  statusName, strategyName, targetEquipSlot, toEquipmentDetail, dropToEquipmentDetail, toggleTalent, triggerName,
  typeName, uniqueDropTypes, updateDraftSkill, withinRange, writeSeenAnnouncementKeys
} from '../lib/helpers';


export function GlobalTicker({ announcements }: { announcements: GlobalAnnouncement[] }) {
  const [seenKeys, setSeenKeys] = useState<Set<string>>(() => readSeenAnnouncementKeys());
  const [displayItems, setDisplayItems] = useState<GlobalAnnouncement[]>([]);
  const uniqueAnnouncements = useMemo(
    () =>
      announcements.filter((announcement, index, list) =>
        list.findIndex((item) => item.kind === announcement.kind && item.text === announcement.text) === index
      ),
    [announcements]
  );
  const uniqueAnnouncementKeys = uniqueAnnouncements.map(announcementSeenKey).join('|');
  const displayItemKeys = displayItems.map(announcementSeenKey).join('|');
  const tickerTextLength = displayItems.reduce(
    (total, item) => total + announcementKindName(item.kind).length + item.text.length,
    0
  );
  const displayDuration =
    displayItems.length === 0 ? 8000 : Math.min(60000, Math.max(16000, tickerTextLength * 260));

  useEffect(() => {
    const activeKeys = new Set(displayItems.map(announcementSeenKey));
    const nextItems = uniqueAnnouncements.filter((announcement) => {
      const key = announcementSeenKey(announcement);
      return !seenKeys.has(key) && !activeKeys.has(key);
    });
    if (nextItems.length === 0) {
      return;
    }
    const keysToMark = nextItems.map(announcementSeenKey);
    setDisplayItems((currentItems) => {
      const currentKeys = new Set(currentItems.map(announcementSeenKey));
      const itemsToAppend = nextItems.filter((announcement) => !currentKeys.has(announcementSeenKey(announcement)));
      return [...currentItems, ...itemsToAppend];
    });
    setSeenKeys((currentSeenKeys) => {
      const nextSeenKeys = new Set([...currentSeenKeys, ...keysToMark]);
      writeSeenAnnouncementKeys(nextSeenKeys);
      return nextSeenKeys;
    });
  }, [displayItemKeys, seenKeys, uniqueAnnouncementKeys, uniqueAnnouncements]);

  useEffect(() => {
    if (!displayItemKeys) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setDisplayItems([]);
    }, displayDuration);
    return () => window.clearTimeout(timeout);
  }, [displayDuration, displayItemKeys]);

  const hasUnread = displayItems.length > 0;
  const trackClassName = `global-ticker-track${displayItems.length > 0 ? ' is-animated' : ' is-static'}`;
  const trackStyle = { '--ticker-duration': `${displayDuration}ms` } as CSSProperties;
  return (
    <div className={`global-ticker${hasUnread ? ' has-new' : ''}${displayItems.length === 0 ? ' is-empty' : ''}`}>
      <div className="global-ticker-label">
        <Bell size={16} />
        <strong>全服通告</strong>
      </div>
      <div className="global-ticker-window">
        {displayItems.length > 0 ? (
          <div className={trackClassName} style={trackStyle}>
            {displayItems.map((item) => (
              <span key={announcementSeenKey(item)}>
                <b>{announcementKindName(item.kind)}</b>
                {item.text}
              </span>
            ))}
          </div>
        ) : (
          <div className="global-ticker-track is-static">
            <span>暂无新通告，世界正在安静地冒险。</span>
          </div>
        )}
      </div>
    </div>
  );
}

export function ProfessionGlyph({ profession, size = 24 }: { profession: PlayableProfession; size?: number }) {
  if (profession === 'warrior') {
    return <Swords size={size} />;
  }
  if (profession === 'ranger') {
    return <Gauge size={size} />;
  }
  return <Sparkles size={size} />;
}

export function InfoPanel({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="create-info-panel">
      <strong>{title}</strong>
      <ul>
        {items.map((item) => (
          <li key={item}>
            <CircleCheck size={14} />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function SkillCard({ skill, busy, onTrain }: { skill: SkillView; busy: boolean; onTrain: (action: 'learn' | 'upgrade') => void }) {
  const locked = !skill.unlocked;
  const capped = skill.learned && skill.rank >= skill.rankCap;
  const action = !skill.learned ? 'learn' : 'upgrade';
  const actionCost = !skill.learned ? skill.learnCost : skill.nextRankCost;
  const canTrain = !busy && (skill.learned ? skill.canUpgrade : skill.canLearn);
  const actionLabel = locked
    ? `Lv.${skill.unlockLevel} 解锁`
    : !skill.learned
      ? `学习 ${formatNumber(actionCost)} 金`
      : capped
        ? `Lv.${skill.rankCap} 上限`
        : `升级 ${formatNumber(actionCost)} 金`;

  return (
    <article className={`skill-card ${skill.learned ? 'learned' : ''} ${locked ? 'locked' : ''}`}>
      <div className="skill-card-main">
        <div className={`skill-icon ${skill.effectType}`}>
          {skill.effectType === 'heal' ? <HeartPulse size={20} /> : skill.effectType === 'shield' ? <Shield size={20} /> : <Sparkles size={20} />}
        </div>
        <div>
          <div className="skill-title-row">
            <h2>{skill.name}</h2>
            <span>{skillCategoryName(skill)}</span>
          </div>
          <p>{skill.description}</p>
        </div>
      </div>
      <div className="skill-stat-row">
        <Metric label="阶级" value={skill.learned ? `${skill.rank}/${skill.rankCap}` : `0/${skill.rankCap}`} />
        <Metric label="效果" value={skillValueLabel(skill.currentValue, skill)} />
        <Metric label="下一阶" value={skill.rank < skill.rankCap ? skillValueLabel(skill.nextValue, skill) : '已达上限'} />
        <Metric label="冷却" value={`${skill.cooldown} 回合`} />
      </div>
      <div className="skill-foot">
        <span>{skillTriggerName(skill.triggerKind)} · 消耗 {skill.mpCostBase + Math.max(0, skill.rank - 1) * skill.mpCostGrowth} MP</span>
        <button className="mini-action" disabled={!canTrain} onClick={() => onTrain(action)}>
          {actionLabel}
        </button>
      </div>
    </article>
  );
}

export function SpecialDungeonPanel({ dungeons, selectedDungeon, combatPower, playerLevel, loading, onSelect, onRun }: {
  dungeons: Dungeon[];
  selectedDungeon: Dungeon | null;
  combatPower: number;
  playerLevel: number;
  loading: boolean;
  onSelect: (dungeonId: string) => void;
  onRun: (dungeonId: string) => void;
}) {
  if (dungeons.length === 0 || !selectedDungeon) {
    return <EmptyState text="特殊副本尚未开放。" />;
  }
  const sortedDungeons = [...dungeons].sort((left, right) => left.recommendedLevel - right.recommendedLevel);
  const highDrops = selectedDungeon.drops.filter((drop) => drop.quality === 'legendary' || drop.quality === 'immortal');
  const legendaryChance = combinedDropChance(highDrops.filter((drop) => drop.quality === 'legendary'));
  const immortalChance = combinedDropChance(highDrops.filter((drop) => drop.quality === 'immortal'));
  const risk = dungeonRisk(combatPower, selectedDungeon.minimumPower, playerLevel, selectedDungeon.minimumLevel, selectedDungeon.gate);

  return (
    <div className="special-dungeon-panel">
      <div className="bloodmoon-hero">
        <div>
          <span className="eyebrow">特殊副本 · 血月裂隙</span>
          <h2>传说与不朽装备唯一来源</h2>
          <p>不开放扫荡，成功通关后进入高阶掉落结算。传说 15 次未出保底，不朽 60 次后软保底。</p>
        </div>
        <strong className={`risk-pill ${risk.level}`}>{risk.label}</strong>
      </div>
      <div className="bloodmoon-tier-row">
        {sortedDungeons.map((dungeon, index) => (
          <button
            key={dungeon.id}
            className={dungeon.id === selectedDungeon.id ? 'active' : ''}
            onClick={() => onSelect(dungeon.id)}
          >
            <span>阶位 {index + 1}</span>
            <strong>Lv.{dungeon.minimumLevel}</strong>
            <small>{dungeon.minimumPower}</small>
          </button>
        ))}
      </div>
      <div className="bloodmoon-stats">
        <Metric label="门槛战力" value={formatNumber(selectedDungeon.minimumPower)} />
        <Metric label="传说期望" value={formatDropRate(legendaryChance)} />
        <Metric label="不朽期望" value={formatDropRate(immortalChance)} />
      </div>
      <DungeonCard
        dungeon={selectedDungeon}
        combatPower={combatPower}
        playerLevel={playerLevel}
        loading={loading}
        sweepLoading={false}
        special
        onRun={() => onRun(selectedDungeon.id)}
        onSweep={() => undefined}
      />
    </div>
  );
}

export function EquipmentProcessingPanel({
  mode,
  snapshot,
  focusedItemId,
  selectedGemId,
  lockedAffixIndexes,
  useProtector,
  busy,
  onFocusItem,
  onSelectGem,
  onToggleAffixLock,
  onUseProtectorChange,
  onUnlockSocket,
  onSocketGem,
  onUnsocketGem,
  onUpgradeGems,
  onUpgradeGemBatches,
  onReforge,
  onAscend,
}: {
  mode: Extract<ForgeView, 'socket' | 'gem' | 'reforge' | 'ascend'>;
  snapshot: EquipmentProcessingSnapshot;
  focusedItemId: number | null;
  selectedGemId: number | null;
  lockedAffixIndexes: number[];
  useProtector: boolean;
  busy: boolean;
  onFocusItem: (itemId: number) => void;
  onSelectGem: (gemId: number | null) => void;
  onToggleAffixLock: (index: number) => void;
  onUseProtectorChange: (value: boolean) => void;
  onUnlockSocket: (itemId: number) => void;
  onSocketGem: (itemId: number, socketIndex: number, gemItemId: number) => void;
  onUnsocketGem: (itemId: number, socketIndex: number) => void;
  onUpgradeGems: (gemIds: number[]) => void;
  onUpgradeGemBatches?: (gemIdBatches: number[][]) => void;
  onReforge: (itemId: number, locked: number[]) => void;
  onAscend: (itemId: number, useProtector: boolean) => void;
}) {
  const selected = snapshot.equipment.find((entry) => entry.item.id === focusedItemId) ?? snapshot.equipment[0] ?? null;
  const item = selected?.item ?? null;
  const materials = snapshot.materials;
  const selectedGem = snapshot.gems.find((gem) => gem.id === selectedGemId) ?? null;
  const gemGroups = useMemo(() => gemInventoryGroups(snapshot.gems, materials), [snapshot.gems, materials]);
  const socketReason = selected ? socketBlockReason(selected, materials, snapshot.inventory.gold) : '请选择装备。';
  const reforgeReason = selected ? reforgeBlockReason(selected, lockedAffixIndexes, materials, snapshot.inventory.gold) : '请选择装备。';
  const ascendReason = selected ? ascendBlockReason(selected, materials, snapshot.inventory.gold, useProtector) : '请选择装备。';
  const sockets = selected ? socketSlotsFor(selected) : [];
  const affixes = item?.affixes ?? [];

  if (mode === 'gem') {
    const craftableGroups = gemGroups.filter((group) => group.craftableCount > 0);
    return (
      <div className="forge-gem-craft-view">
        <section className="forge-list-panel gem-craft-list-panel">
          <div className="inventory-main-title">
            <SectionTitle icon={<Sparkles size={18} />} title="宝石合成库存" />
            <strong>{gemGroups.length} 类宝石</strong>
          </div>
          <div className="gem-group-list">
            {gemGroups.length === 0 && <EmptyState text="背包中没有可合成宝石。" />}
            {gemGroups.map((group) => {
              const oneBatch = group.gems.slice(0, 3).map((gem) => gem.id);
              const allBatches = Array.from({ length: group.craftableCount }, (_, index) =>
                group.gems.slice(index * 3, index * 3 + 3).map((gem) => gem.id),
              );
              const disabledReason = group.rank >= 9
                ? '已满级'
                : group.craftableByCount <= 0
                  ? '数量不足'
                  : group.craftableByDust <= 0
                    ? '宝石尘不足'
                    : null;
              return (
                <article key={group.templateId} className={`gem-group-card ${group.craftableCount > 0 ? 'craftable' : ''}`}>
                  <div>
                    <strong className={`quality ${group.representative.quality}`}>{group.representative.name}</strong>
                    <small>{gemEffectText(group.representative)}</small>
                  </div>
                  <div className="gem-group-meta">
                    <span>x{group.quantity}</span>
                    <span>R{group.rank}</span>
                    <span>{group.dustCostPerCraft} 尘/次</span>
                  </div>
                  <div className="gem-group-actions">
                    <button
                      className="mini-action"
                      disabled={busy || group.craftableCount <= 0}
                      title={disabledReason ?? '合成 1 颗高一级宝石'}
                      onClick={() => oneBatch.length === 3 && onUpgradeGems(oneBatch)}
                    >
                      合成 1 次
                    </button>
                    <button
                      className="primary-action compact"
                      disabled={busy || group.craftableCount <= 0 || !onUpgradeGemBatches}
                      title={disabledReason ?? `连续合成 ${group.craftableCount} 次`}
                      onClick={() => onUpgradeGemBatches?.(allBatches)}
                    >
                      一键 {group.craftableCount} 次
                    </button>
                  </div>
                  {disabledReason && <small className="gem-group-reason">{disabledReason}</small>}
                </article>
              );
            })}
          </div>
        </section>
        <section className="gem-craft-summary-panel">
          <SectionTitle icon={<Gem size={18} />} title="合成结算" />
          <div className="forge-stat-grid">
            <Metric label="宝石尘" value={(materials.mat_gem_dust ?? 0).toString()} />
            <Metric label="可合成种类" value={craftableGroups.length.toString()} />
            <Metric label="预计次数" value={craftableGroups.reduce((total, group) => total + group.craftableCount, 0).toString()} />
            <Metric label="最高等级" value="R9" />
          </div>
          <div className="modal-notice">
            <strong>三合一规则</strong>
            <span>同类型、同等级的 3 颗宝石会合成 1 颗高一级宝石；一键合成会按当前库存和宝石尘自动拆分批次。</span>
          </div>
          {snapshot.recentLogs.length > 0 && (
            <div className="processing-log-list">
              <SectionTitle icon={<Clock3 size={18} />} title="近期合成" />
              {snapshot.recentLogs.slice(0, 5).map((log, index) => (
                <div key={`${log.createdAt}-${index}`} className={`processing-log ${log.success ? 'success' : 'failed'}`}>
                  <strong>{processingActionName(log.actionType)}</strong>
                  <span>{log.summary}</span>
                  <small>{formatNumber(log.powerBefore)} → {formatNumber(log.powerAfter)}</small>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    );
  }

  return (
    <div className="forge-processing-view">
      <section className="forge-list-panel">
        <div className="inventory-main-title">
          <SectionTitle
            icon={mode === 'socket' ? <Gem size={18} /> : mode === 'reforge' ? <ScrollText size={18} /> : <Shield size={18} />}
            title={mode === 'socket' ? '后期加工装备' : mode === 'reforge' ? '词条目标' : '升阶目标'}
          />
          <strong>{snapshot.equipment.length} 件装备</strong>
        </div>
        <div className="processing-equipment-list">
          {snapshot.equipment.map((entry) => (
            <button
              key={entry.item.id}
              className={`processing-equipment-card ${entry.item.id === item?.id ? 'active' : ''}`}
              onClick={() => onFocusItem(entry.item.id)}
            >
              <span className="eyebrow">{qualityName(entry.item.quality)} · {typeName(entry.item.itemType)} · Lv.{entry.item.requiredLevel}</span>
              <strong className={`quality ${entry.item.quality}`}>{equipmentDisplayName(entry.item)}</strong>
              <small>
                孔 {entry.unlockedSocketCount}/{entry.socketLimit} · 词条 {entry.affixCount}/{entry.affixLimit} · 阶 {entry.item.ascensionLevel ?? 0}/5
              </small>
            </button>
          ))}
        </div>
      </section>

      <section className="processing-main-panel">
        {!selected || !item ? (
          <EmptyState text="请选择一件装备。" />
        ) : (
          <>
            <CompareCard title="加工目标" item={item} highlight emptyTitle="未选择" emptyText="选择装备后开始加工。" emptyMeta="等待选择" />
            {mode === 'socket' && (
              <>
                <div className="socket-grid">
                  {sockets.map((socket) => (
                    <div key={socket.socketIndex} className={`socket-cell ${socket.gemItemId ? 'filled' : socket.unlocked ? 'open' : 'locked'}`}>
                      <span>孔位 {socket.socketIndex + 1}</span>
                      <strong>{socket.gemName ?? (socket.unlocked ? '空孔位' : '未开启')}</strong>
                      <small>{socket.gemName ? `${statName(socket.statKey)} +${formatStatValue(socket.statKey, socket.statValue ?? 0)}` : socket.unlocked ? '选择宝石后可镶嵌' : '需要打孔核心开启'}</small>
                      {socket.gemItemId ? (
                        <button className="mini-action subtle" disabled={busy} onClick={() => onUnsocketGem(item.id, socket.socketIndex)}>取下</button>
                      ) : socket.unlocked ? (
                        <button
                          className="mini-action"
                          disabled={busy || !selectedGem}
                          title={selectedGem ? '镶嵌选中的宝石' : '请先在右侧选择宝石'}
                          onClick={() => selectedGem && onSocketGem(item.id, socket.socketIndex, selectedGem.id)}
                        >
                          镶嵌
                        </button>
                      ) : null}
                    </div>
                  ))}
                </div>
                <div className="processing-risk-line">
                  <span>开孔成功率 {formatPercent(selected.nextSocketCost.chance)}</span>
                  <span>失败只消耗材料，不会降级</span>
                </div>
              </>
            )}
            {mode === 'reforge' && (
              <>
                <div className="affix-list">
                  {selected.affixLimit <= 0 && <EmptyState text="当前品质未解锁后期词条，先升阶或更换史诗以上装备。" />}
                  {Array.from({ length: Math.max(selected.affixLimit, affixes.length) }).map((_, index) => {
                    const affix = affixes.find((entry) => entry.affixIndex === index);
                    const locked = lockedAffixIndexes.includes(index) || affix?.locked;
                    return (
                      <button key={index} className={`affix-row ${locked ? 'locked' : ''}`} disabled={busy || selected.affixLimit <= 0} onClick={() => onToggleAffixLock(index)}>
                        <span>词条 {index + 1}</span>
                        <strong>{affix ? `${statName(affix.statKey)} +${formatStatValue(affix.statKey, affix.statValue)}` : '未生成'}</strong>
                        <small>{affix ? `T${affix.tier} · ${locked ? '已锁定' : '参与重铸'}` : '首次重铸后生成'}</small>
                      </button>
                    );
                  })}
                </div>
                <div className="processing-risk-line danger">
                  <span>成功率 {formatPercent(selected.reforgeCost.chance)}</span>
                  <span>失败会降低未锁定词条档位</span>
                </div>
              </>
            )}
            {mode === 'ascend' && (
              <>
                <div className="ascension-meter">
                  {Array.from({ length: 5 }).map((_, index) => (
                    <span key={index} className={index < (item.ascensionLevel ?? 0) ? 'filled' : ''}>阶 {index + 1}</span>
                  ))}
                </div>
                <label className="processing-check">
                  <input type="checkbox" checked={useProtector} onChange={(event) => onUseProtectorChange(event.target.checked)} />
                  <span>使用护阶符保护 +3 以上失败不掉阶</span>
                </label>
                <div className="processing-risk-line danger">
                  <span>升阶成功率 {formatPercent(selected.ascensionCost.chance)}</span>
                  <span>+3 以上失败可能掉 1 阶</span>
                </div>
              </>
            )}
          </>
        )}
      </section>

      <aside className="forge-detail-panel processing-detail-panel">
        {mode === 'socket' && selected && item && (
          <>
            <SectionTitle icon={<Gem size={18} />} title="宝石与开孔" />
            <div className="forge-stat-grid">
              <Metric label="打孔核心" value={`${materials.mat_socket_core ?? 0}/${selected.nextSocketCost.socketCores}`} />
              <Metric label="宝石尘" value={`${materials.mat_gem_dust ?? 0}/${selected.nextSocketCost.gemDust}`} />
              <Metric label="深渊精华" value={`${materials.mat_abyss_essence ?? 0}/${selected.nextSocketCost.essence}`} />
              <Metric label="金币" value={`${formatNumber(snapshot.inventory.gold)}/${formatNumber(selected.nextSocketCost.goldCost)}`} />
            </div>
            {socketReason && <div className="modal-warning">{socketReason}</div>}
            <button className="primary-action" disabled={busy || Boolean(socketReason)} onClick={() => onUnlockSocket(item.id)}>
              {selected.unlockedSocketCount >= selected.socketLimit ? '孔位已满' : '开启孔位'}
            </button>
            <div className="gem-pick-list">
              {gemGroups.length === 0 && <EmptyState text="背包中没有可镶嵌宝石。" />}
              {gemGroups.map((group) => {
                const active = selectedGem?.templateId === group.templateId;
                return (
                  <button
                    key={group.templateId}
                    className={`gem-pick ${active ? 'active' : ''}`}
                    onClick={() => onSelectGem(active ? null : group.representative.id)}
                  >
                    <strong className={`quality ${group.representative.quality}`}>{group.representative.name}</strong>
                    <small>{gemEffectText(group.representative)} · x{group.quantity}</small>
                  </button>
                );
              })}
            </div>
          </>
        )}
        {mode === 'reforge' && selected && item && (
          <>
            <SectionTitle icon={<ScrollText size={18} />} title="重铸消耗" />
            <div className="forge-stat-grid">
              <Metric label="重铸宝珠" value={`${materials.mat_reforge_orb ?? 0}/${selected.reforgeCost.orbs + lockedAffixIndexes.length}`} />
              <Metric label="深渊精华" value={`${materials.mat_abyss_essence ?? 0}/${selected.reforgeCost.essence + lockedAffixIndexes.length * 10}`} />
              <Metric label="锁词石" value={`${materials.mat_affix_lock ?? 0}/${lockedAffixIndexes.length}`} />
              <Metric label="金币" value={`${formatNumber(snapshot.inventory.gold)}/${formatNumber(selected.reforgeCost.goldCost)}`} />
            </div>
            {reforgeReason && <div className="modal-warning">{reforgeReason}</div>}
            <button className="primary-action" disabled={busy || Boolean(reforgeReason)} onClick={() => onReforge(item.id, lockedAffixIndexes)}>
              开始重铸
            </button>
          </>
        )}
        {mode === 'ascend' && selected && item && (
          <>
            <SectionTitle icon={<Shield size={18} />} title="升阶消耗" />
            <div className="forge-stat-grid">
              <Metric label="升阶核心" value={`${materials.mat_ascension_core ?? 0}/${selected.ascensionCost.ascensionCores}`} />
              <Metric label="深渊精华" value={`${materials.mat_abyss_essence ?? 0}/${selected.ascensionCost.essence}`} />
              <Metric label="淬炼碎片" value={`${materials.mat_tempering_shard ?? 0}/${selected.ascensionCost.shards}`} />
              <Metric label="护阶符" value={`${materials.mat_ascension_guard ?? 0}/${useProtector && (item.ascensionLevel ?? 0) >= 3 ? 1 : 0}`} />
            </div>
            {ascendReason && <div className="modal-warning">{ascendReason}</div>}
            <button className="primary-action" disabled={busy || Boolean(ascendReason)} onClick={() => onAscend(item.id, useProtector)}>
              {(item.ascensionLevel ?? 0) >= 5 ? '已达上限' : '开始升阶'}
            </button>
          </>
        )}
        {snapshot.recentLogs.length > 0 && (
          <div className="processing-log-list">
            <SectionTitle icon={<Clock3 size={18} />} title="近期加工" />
            {snapshot.recentLogs.slice(0, 4).map((log, index) => (
              <div key={`${log.createdAt}-${index}`} className={`processing-log ${log.success ? 'success' : 'failed'}`}>
                <strong>{processingActionName(log.actionType)}</strong>
                <span>{log.summary}</span>
                <small>{formatNumber(log.powerBefore)} → {formatNumber(log.powerAfter)}</small>
              </div>
            ))}
          </div>
        )}
      </aside>
    </div>
  );
}

export function RiftResultPanel({ result, snapshot }: { result: RiftRunResult; snapshot: RiftSnapshot }) {
  const hpText = `${Math.max(0, result.playerFinalHp)}/${Math.max(1, result.playerMaxHp)}`;
  return (
    <section className={`rift-result-panel ${result.success ? 'success' : 'failed'}`}>
      <div className="panel-head-row">
        <SectionTitle icon={<Sparkles size={18} />} title={`T${result.tier} ${result.success ? '通关' : '失败'}`} />
        <strong>{result.rating} · {result.score}</strong>
      </div>
      <div className="rift-reward-preview compact">
        <Metric label="剩余生命" value={hpText} />
        <Metric label="行动数" value={result.turnsTaken.toString()} />
        <Metric label="击杀" value={result.monstersKilled.toString()} />
        <Metric label="材料" value={`+${result.rewards.essence}/${result.rewards.shards}/${result.rewards.orbs}`} />
      </div>
      <div className="rift-event-log">
        {result.events.slice(-18).map((event) => (
          <div key={event.index} className={`rift-event-line ${event.tone}`}>
            <span>{event.roomIndex > 0 ? `房间 ${event.roomIndex}` : '裂隙'}</span>
            <p>{event.text}</p>
          </div>
        ))}
      </div>
      <div className="rift-material-footer">
        当前库存：{result.materials.essence || snapshot.materials.essence} 精华 / {result.materials.shards || snapshot.materials.shards} 碎片 / {result.materials.orbs || snapshot.materials.orbs} 宝珠
      </div>
    </section>
  );
}

export function ArenaProfileCard({ profile, featured = false }: { profile: ArenaProfile; featured?: boolean }) {
  return (
    <article className={`arena-profile-card ${featured ? 'featured' : ''}`}>
      <div>
        <span className="eyebrow">Lv.{profile.level} {professionName(profile.profession)}</span>
        <h2>{profile.name}</h2>
        <p>{profile.controllerType === 'robot' ? '冒险者' : '玩家角色'} · {profile.tier}</p>
      </div>
      <div className="arena-profile-stats">
        <Metric label="战力" value={formatNumber(profile.combatPower)} />
        <Metric label="胜负" value={`${profile.wins}/${profile.losses}`} />
      </div>
    </article>
  );
}

export function ArenaOpponentCard({ opponent, loading, onChallenge }: { opponent: ArenaOpponent; loading: boolean; onChallenge: () => void }) {
  const disabled = loading || !opponent.challengeable;
  return (
    <article className="arena-opponent-card">
      <div className="arena-opponent-head">
        <div>
          <span className="eyebrow">#{opponent.rank || '-'} · {opponent.tier}</span>
          <h3>{opponent.name}</h3>
        </div>
        <strong>{opponent.challengeHint}</strong>
      </div>
      <div className="arena-opponent-metrics">
        <Metric label="战力" value={formatNumber(opponent.combatPower)} />
        <Metric label="积分" value={opponent.rating.toString()} />
        <Metric label="职业" value={professionName(opponent.profession)} />
      </div>
      <button className="primary-action" disabled={disabled} title={opponent.disabledReason ?? undefined} onClick={onChallenge}>
        {loading ? '结算中...' : opponent.disabledReason ?? '发起挑战'}
      </button>
    </article>
  );
}

export function ArenaShopCard({ offer, loading, onBuy }: { offer: ArenaShopOffer; loading: boolean; onBuy: () => void }) {
  const disabled = loading || !offer.affordable || !offer.unlocked;
  return (
    <article className="arena-shop-card">
      <div>
        <strong>{offer.name}</strong>
        <p>{offer.description}</p>
      </div>
      <div className="arena-shop-footer">
        <span>{offer.itemQuantity} 件 · {offer.priceCoins} 币</span>
        <button className="mini-action" disabled={disabled} title={offer.disabledReason ?? undefined} onClick={onBuy}>
          {offer.disabledReason ?? '兑换'}
        </button>
      </div>
    </article>
  );
}

export function ArenaMatchPanel({ match }: { match: ArenaMatchDetail }) {
  return (
    <section className={`arena-match-panel ${match.summary.attackerWon ? 'victory' : 'defeat'}`}>
      <div className="panel-head-row">
        <SectionTitle icon={<Trophy size={18} />} title={match.summary.attackerWon ? '进攻胜利' : '进攻失败'} />
        <strong>{formatSigned(match.summary.attackerRatingChange)} 积分 · +{match.summary.arenaCoins} 币</strong>
      </div>
      <div className="arena-fighter-compare">
        <ArenaFighterCard fighter={match.attacker} label="进攻方" />
        <ArenaFighterCard fighter={match.defender} label="防守方" />
      </div>
      <div className="arena-event-log">
        {match.events.slice(0, 20).map((event) => (
          <div key={event.sequenceNo} className={`arena-event-line ${event.tone}`}>
            <span>{event.actor === 'defender' ? '防守' : event.actor === 'player' ? '进攻' : '系统'}</span>
            <p>{event.text}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export function ArenaFighterCard({ fighter, label }: { fighter: ArenaMatchDetail['attacker']; label: string }) {
  return (
    <article className="arena-fighter-card">
      <span className="eyebrow">{label} · {fighter.buildName}</span>
      <h3>{fighter.name}</h3>
      <div className="arena-opponent-metrics">
        <Metric label="战力" value={formatNumber(fighter.combatPower)} />
        <Metric label="生命" value={fighter.maxHp.toString()} />
        <Metric label="攻击" value={fighter.attackPower.toString()} />
      </div>
      <p>{fighter.skills.length ? fighter.skills.join(' / ') : '职业默认出招'}</p>
    </article>
  );
}

export function ArenaRankRow({ profile }: { profile: ArenaProfile }) {
  return (
    <article className="arena-rank-row">
      <strong>#{profile.rank || '-'}</strong>
      <div>
        <span>{profile.name}</span>
        <small>{profile.tier} · {formatNumber(profile.combatPower)} 战力</small>
      </div>
      <b>{profile.rating}</b>
    </article>
  );
}

export function BuildScorePanel({ score, compact = false }: { score: BuildScore; compact?: boolean }) {
  const rows: Array<[string, string, number]> = [
    ['damage', '输出', score.damage],
    ['defense', '承伤', score.defense],
    ['sustain', '续航', score.sustain],
    ['speed', '速度', score.speed],
    ['rift', '深渊', score.rift],
    ['completion', '完成度', score.completion],
  ];
  return (
    <section className={`build-score-panel ${compact ? 'compact' : ''}`}>
      {!compact && <SectionTitle icon={<Gauge size={18} />} title="构筑评分" />}
      <div className="build-score-bars">
        {rows.map(([key, label, value]) => (
          <div key={key} className="build-score-row">
            <span>{label}</span>
            <div><i style={{ width: `${value}%` }} /></div>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}

export function BuildSimulationPanel({ result }: { result: RiftSimulationResult }) {
  const hpText = `${Math.max(0, result.playerFinalHp)}/${Math.max(1, result.playerMaxHp)}`;
  return (
    <section className={`build-sim-result ${result.success ? 'success' : 'failed'}`}>
      <div className="panel-head-row">
        <SectionTitle icon={<Sparkles size={18} />} title={`模拟 T${result.tier} ${result.success ? '通关' : '失败'}`} />
        <strong>{result.rating} · {result.score}</strong>
      </div>
      <div className="rift-reward-preview compact">
        <Metric label="剩余生命" value={hpText} />
        <Metric label="行动数" value={result.turnsTaken.toString()} />
        <Metric label="击杀" value={result.monstersKilled.toString()} />
        <Metric label="战力" value={formatNumber(result.combatPower)} />
      </div>
      <div className="rift-event-log build-sim-log">
        {result.events.slice(-18).map((event) => (
          <div key={event.index} className={`rift-event-line ${event.tone}`}>
            <span>{event.roomIndex > 0 ? `房间 ${event.roomIndex}` : '模拟'}</span>
            <p>{event.text}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export function WealthTierCard({ tier }: { tier: WealthTierStat }) {
  const averageRealMoney = Math.round(tier.realMoneyTotal / Math.max(1, tier.playerCount));
  return (
    <article className="wealth-tier-card">
      <div>
        <span>L{tier.wealthTierLevel.toString().padStart(2, '0')}</span>
        <strong>{tier.wealthTier}</strong>
      </div>
      <p>每轮 {formatNumber(tier.minIncome)}-{formatNumber(tier.maxIncome)} 元</p>
      <small>{tier.playerCount} 人 · 人均 {formatNumber(averageRealMoney)} 元 · 总 {formatNumber(tier.realMoneyTotal)} 元</small>
      <small>金币总量 {formatNumber(tier.goldTotal)} 金</small>
    </article>
  );
}

export function RobotRechargeCard({ row }: { row: RobotRechargeRow }) {
  return (
    <article className="robot-recharge-card">
      <div>
        <span>{formatRelativeTime(row.createdAt)} · L{row.wealthTierLevel.toString().padStart(2, '0')} {row.wealthTier}</span>
        <strong>{row.playerName}</strong>
      </div>
      <p>{rechargeReasonName(row.sourceAction)} · {formatNumber(row.rmbAmount)} 元换 {formatNumber(row.goldAmount)} 金</p>
      <small>余额 {formatNumber(row.currentRealMoney)} 元 · 金币 {formatNumber(row.currentGold)} 金</small>
    </article>
  );
}

export function CashIncomeCard({ row }: { row: CashIncomeRow }) {
  return (
    <article className="cash-income-card">
      <span>{formatRelativeTime(row.createdAt)} · L{row.wealthTierLevel.toString().padStart(2, '0')} {row.wealthTier}</span>
      <strong>{row.playerName}</strong>
      <p>到账 {formatNumber(row.rmbAmount)} 元</p>
    </article>
  );
}

export function RobotRangeFilter({ label, minValue, maxValue, onMinChange, onMaxChange }: {
  label: string;
  minValue: string;
  maxValue: string;
  onMinChange: (value: string) => void;
  onMaxChange: (value: string) => void;
}) {
  return (
    <div className="robot-range-filter">
      <span>{label}</span>
      <input inputMode="numeric" value={minValue} onChange={(event) => onMinChange(event.target.value)} placeholder="最小" />
      <input inputMode="numeric" value={maxValue} onChange={(event) => onMaxChange(event.target.value)} placeholder="最大" />
    </div>
  );
}

export function RobotActivityCard({ robot, onSelect }: { robot: RobotActivityView; onSelect: () => void }) {
  return (
    <button type="button" className={`robot-card ${robot.currentActivityKind}`} onClick={onSelect}>
      <div>
        <span className="eyebrow">{robot.title} · L{robot.wealthTierLevel.toString().padStart(2, '0')} {robot.wealthTier}</span>
        <h2>{robot.name}</h2>
      </div>
      <p>{robot.currentActivityText}</p>
      <div className="robot-meta">
        <span>{robotActivityKindName(robot.currentActivityKind)}</span>
        <span>战力 {formatNumber(robot.power)}</span>
        <span>{formatNumber(robot.gold)} 金</span>
        <span>{formatNumber(robot.realMoney)} 元</span>
        <span>{formatRelativeTime(robot.currentActivityAt)}</span>
      </div>
      <div className="robot-progress-row">
        <small>副本 {robot.dungeonClears}</small>
        <small>强化峰值 +{robot.peakEnhancement}</small>
        <small>累计充值 {formatNumber(robot.rechargeRmb)} 元</small>
      </div>
    </button>
  );
}

export function RobotEventCard({ event }: { event: RobotActivityEvent }) {
  return (
    <article className={`robot-event ${event.kind}`}>
      <span>{formatRelativeTime(event.createdAt)} · {robotActivityKindName(event.kind)}</span>
      <strong>{event.actorName}</strong>
      <small>{event.actorTitle}</small>
      <p>{event.text}</p>
    </article>
  );
}

export function RobotActivityDetailModal({ token, robot: fallbackRobot, onClose }: {
  token: string;
  robot: RobotActivityView;
  onClose: () => void;
}) {
  const historyRef = useRef<HTMLDivElement | null>(null);
  const [selectedEquipment, setSelectedEquipment] = useState<LeaderboardEquipment | null>(null);
  const { data, isLoading, error } = useQuery<RobotActivityDetail>({
    queryKey: ['robot-activity-detail', token, fallbackRobot.id],
    queryFn: () => gameApi.robotActivityDetail(token, fallbackRobot.id),
    refetchInterval: 3_000,
  });
  const robot = data?.robot ?? fallbackRobot;
  const events = data?.events ?? [];
  const equipment = data?.equipment ?? [];
  const latestEventKey = events[0] ? `${events[0].createdAt}-${events[0].text}` : '';

  useEffect(() => {
    if (historyRef.current) {
      historyRef.current.scrollTop = 0;
    }
  }, [latestEventKey]);

  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className="robot-detail-modal" onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <span className="eyebrow">{robot.title} · {professionName(robot.profession)} · {robotActivityKindName(robot.currentActivityKind)}</span>
            <h2>{robot.name}</h2>
            <p>Lv.{robot.level} · {robot.wealthTier} · 战力 {formatNumber(robot.power)} · 金币 {formatNumber(robot.gold)} 金</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="robot-detail-layout">
          <div className="robot-detail-profile">
            <div className="result-modal-metrics">
              <Metric label="副本次数" value={formatNumber(robot.dungeonClears)} />
              <Metric label="强化峰值" value={`+${robot.peakEnhancement}`} />
              <Metric label="传说获得" value={formatNumber(robot.legendaryLootCount)} />
              <Metric label="真实余额" value={`${formatNumber(robot.realMoney)} 元`} />
              <Metric label="累计充值" value={`${formatNumber(robot.rechargeRmb)} 元`} />
              <Metric label="最后活动" value={formatRelativeTime(robot.currentActivityAt)} />
            </div>
            <div className={`robot-current-action ${robot.currentActivityKind}`}>
              <span>{robotActivityKindName(robot.currentActivityKind)}</span>
              <strong>{robot.currentActivityText}</strong>
            </div>
            <SectionTitle icon={<Shield size={18} />} title={`装备档案 ${equipment.length}/9`} />
            <div className="robot-equipment-grid">
              {isLoading && equipment.length === 0 && <EmptyState text="正在读取装备档案。" />}
              {error && <EmptyState text="冒险者详情加载失败，稍后会自动重试。" />}
              {!isLoading && !error && equipment.length === 0 && <EmptyState text="暂无装备记录。" />}
              {equipment.map((item) => (
                <button
                  key={`${robot.id}-${item.slot}`}
                  className={`robot-equipment-card ${item.quality} ${enhancementEffectClass(item)}`}
                  onClick={() => setSelectedEquipment(item)}
                >
                  <div className="item-meta-line">
                    <span>{item.slotName} · Lv.{item.level} · +{item.enhancementLevel}</span>
                    <EnhancementBadge level={item.enhancementLevel} />
                  </div>
                  <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
                  <p>{leaderboardEquipmentBonusText(item)}</p>
                  <small>战力 {formatNumber(item.power)} · {equipmentOriginText(item)}</small>
                </button>
              ))}
            </div>
          </div>
          <aside className="robot-history-panel">
            <SectionTitle icon={<Clock3 size={18} />} title="实时历史动态" />
            <div className="robot-history-list" ref={historyRef}>
              {events.length === 0 && <EmptyState text="这个冒险者还没有留下历史动态。" />}
              {events.map((event) => (
                <RobotEventCard key={`${event.robotId}-${event.createdAt}-${event.text}`} event={event} />
              ))}
            </div>
          </aside>
        </div>
        {selectedEquipment && (
          <ItemDetail item={leaderboardEquipmentToDetail(selectedEquipment)} onClose={() => setSelectedEquipment(null)} />
        )}
      </section>
    </div>
  );
}

export function ChatMessageBubble({ message, onSelectSpeaker }: { message: ChatMessage; onSelectSpeaker: (speaker: ChatSpeaker) => void }) {
  const speaker = message.speaker;
  const isSystem = message.kind === 'system' || speaker.kind === 'system';
  const isMine = message.kind === 'player' && speaker.kind === 'player';
  const displayName = speaker.name || message.senderName;
  if (isSystem) {
    return (
      <article className="chat-message-row system">
        <div className="chat-system-pill">
          <span>{message.text}</span>
          <time dateTime={message.createdAt}>{formatChatTime(message.createdAt)}</time>
        </div>
      </article>
    );
  }
  return (
    <article className={`chat-message-row ${isMine ? 'mine' : 'other'} ${message.kind}`}>
      {!isMine && (
        <button className="chat-avatar" onClick={() => onSelectSpeaker(speaker)} aria-label={displayName}>
          {chatAvatarLabel(displayName)}
        </button>
      )}
      <div className="chat-bubble-stack">
        <div className="chat-message-head">
          <button className="speaker-button" onClick={() => onSelectSpeaker(speaker)}>
            {isMine ? '你' : displayName}
          </button>
          <span>{speaker.title}</span>
          <small>Lv.{speaker.level} · 战力 {formatNumber(speaker.power)}</small>
          <time dateTime={message.createdAt}>
            <Clock3 size={12} />
            {formatChatTime(message.createdAt)}
          </time>
        </div>
        <div className="chat-bubble">
          <p>{message.text}</p>
        </div>
      </div>
    </article>
  );
}

export function SpeakerDetailModal({ speaker, onClose }: { speaker: ChatSpeaker; onClose: () => void }) {
  const equippedCount = speaker.equipment.length;
  const equipmentPower = speaker.equipmentPower ?? speaker.equipment.reduce((sum, item) => sum + item.power, 0);
  const [selectedEquipment, setSelectedEquipment] = useState<LeaderboardEquipment | null>(null);
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className="speaker-modal" onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <span className="eyebrow">{speaker.title} · {professionName(speaker.profession)}</span>
            <h2>{speaker.name}</h2>
            <p>Lv.{speaker.level} · 战力 {speaker.power} · 已穿戴 {equippedCount}/9</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="speaker-stat-grid">
          <Metric label="经验" value={formatNumber(speaker.experience)} />
          <Metric label="金币" value={formatNumber(speaker.gold)} />
          <Metric label="自由点" value={formatNumber(speaker.freePoints)} />
          <Metric label="力量" value={formatNumber(speaker.strength)} />
          <Metric label="敏捷" value={formatNumber(speaker.agility)} />
          <Metric label="体质" value={formatNumber(speaker.constitution)} />
          <Metric label="智力" value={formatNumber(speaker.intelligence)} />
          <Metric label="精神" value={formatNumber(speaker.spirit)} />
          <Metric label="战力" value={formatNumber(speaker.power)} />
          <Metric label="装备贡献" value={formatNumber(equipmentPower)} />
        </div>
        {speaker.derivedStats && (
          <CombatStatsPanel stats={speaker.derivedStats} compact />
        )}
        <SectionTitle icon={<Shield size={18} />} title="装备栏" />
        <div className="speaker-equipment-grid">
          {speaker.equipment.length === 0 && <EmptyState text="暂无可查看装备。" />}
          {speaker.equipment.map((item) => (
            <button
              key={`${speaker.name}-${item.slot}`}
              className={`speaker-equipment-card ${item.quality} ${enhancementEffectClass(item)}`}
              onClick={() => setSelectedEquipment(item)}
            >
              <div className="item-meta-line">
                <span>{item.slotName} · Lv.{item.level} · +{item.enhancementLevel}</span>
                <EnhancementBadge level={item.enhancementLevel} />
              </div>
              <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
              <p>{leaderboardEquipmentBonusText(item)}</p>
              <small>战力 {formatNumber(item.power)} · {equipmentOriginText(item)}</small>
            </button>
          ))}
        </div>
        {selectedEquipment && (
          <ItemDetail item={leaderboardEquipmentToDetail(selectedEquipment)} onClose={() => setSelectedEquipment(null)} />
        )}
      </section>
    </div>
  );
}

export function DungeonCard({ dungeon, combatPower, playerLevel = 0, loading, sweepLoading, special = false, onRun, onSweep }: {
  dungeon: Dungeon;
  combatPower: number;
  playerLevel?: number;
  loading: boolean;
  sweepLoading: boolean;
  special?: boolean;
  onRun: () => void;
  onSweep: () => void;
}) {
  const risk = dungeonRisk(combatPower, dungeon.minimumPower, playerLevel, dungeon.minimumLevel, dungeon.gate);
  const drops = dungeon.drops ?? [];
  const dropTypes = uniqueDropTypes(drops);
  const specialDungeon = special || isSpecialDungeon(dungeon);
  const highestQuality = drops.reduce((best, drop) => qualityRank(drop.quality) > qualityRank(best) ? drop.quality : best, 'common');
  const highDropChance = combinedDropChance(drops.filter((drop) => drop.quality === 'legendary' || drop.quality === 'immortal'));
  const eligible = dungeon.gate?.eligible ?? true;
  const stamina = dungeon.stamina;
  const canRunWithStamina = !stamina || stamina.current >= 1;
  const canSweepWithStamina = !stamina || stamina.current >= 10;
  const previewDrops = drops.slice(0, specialDungeon ? 5 : 3);
  const hiddenDropCount = Math.max(0, drops.length - previewDrops.length);
  const normalDropLabel = drops.length > 0 ? `${drops.length} 件可掉落` : '暂无掉落';
  const [showDrops, setShowDrops] = useState(false);
  const [detailDrop, setDetailDrop] = useState<DropPreview | null>(null);
  return (
    <article className={`dungeon-card ${risk.level} ${specialDungeon ? 'special' : ''} ${eligible ? '' : 'locked'}`}>
      <div className="dungeon-card-head">
        <div>
          <span className="eyebrow">{specialDungeon ? '特殊副本' : dungeon.difficulty} · 门槛 Lv.{dungeon.minimumLevel}</span>
          <h2>{dungeon.name}</h2>
          <p>{dungeon.description}</p>
        </div>
        <strong className={`risk-pill ${risk.level}`}>{risk.label}</strong>
      </div>
      <div className="dungeon-meta">
        <span>门槛 {formatNumber(dungeon.minimumPower)}</span>
        <span>推荐 {formatNumber(dungeon.recommendedPower)}</span>
        {specialDungeon && <span>Boss {bossArchetypeName(dungeon.bossArchetype)}</span>}
        {specialDungeon && <span>预计 {dungeon.expectedRounds} 回合</span>}
        <span className={dungeon.cleared ? 'clear-state cleared' : 'clear-state'}>{dungeon.cleared ? '已通过' : '未通过'}</span>
        {!eligible && !specialDungeon && <span className="gate-state">{dungeon.gate.label}</span>}
        {specialDungeon && <strong>{qualityName(highestQuality)}上限 · {formatDropRate(highDropChance)}</strong>}
      </div>
      {specialDungeon && !eligible && <p className="gate-warning">{dungeon.gate.label}</p>}
      {stamina && stamina.current <= 0 && <p className="gate-warning">疲劳不足，可在背包使用疲劳药水。</p>}
      {specialDungeon ? (
        <div className="drop-preview-section">
          <span>当前副本可掉落</span>
          {dropTypes.length > 0 && (
            <div className="drop-slot-row">
              {dropTypes.map((type) => <small key={type}>{typeName(type)}</small>)}
            </div>
          )}
          <div className="drop-preview-grid">
            {previewDrops.map((drop) => (
              <DropPreviewCard key={drop.templateId} drop={drop} onSelect={() => setDetailDrop(drop)} />
            ))}
            {hiddenDropCount > 0 && (
              <button type="button" className="drop-more" onClick={() => setShowDrops(true)} title="查看全部掉落">
                +{hiddenDropCount}
              </button>
            )}
          </div>
        </div>
      ) : (
        <div className="drop-quick-row">
          <span>{normalDropLabel}</span>
          <button type="button" className="text-button drop-view-button" disabled={drops.length === 0} onClick={() => setShowDrops(true)}>
            <Package size={16} />
            查看掉落
          </button>
        </div>
      )}
      <div className="dungeon-action-row">
        <button className="compact-action" disabled={loading || !eligible || !canRunWithStamina} onClick={onRun}>
          {loading ? '战斗中' : !canRunWithStamina ? '疲劳不足' : eligible ? specialDungeon ? '挑战裂隙' : '进入' : '未达标'}
        </button>
        {specialDungeon ? (
          <button className="compact-action sweep-compact locked" disabled>不可扫荡</button>
        ) : (
          <button className="compact-action sweep-compact" disabled={loading || sweepLoading || !dungeon.cleared || !eligible || !canSweepWithStamina} onClick={onSweep}>
            {sweepLoading ? '扫荡中' : !canSweepWithStamina ? '疲劳不足' : '扫荡 10 次'}
          </button>
        )}
      </div>
      {showDrops && createPortal(
        <DungeonDropsModal dungeon={dungeon} special={specialDungeon} onClose={() => setShowDrops(false)} />,
        document.body
      )}
      {detailDrop && createPortal(
        <ItemDetail item={dropToEquipmentDetail(detailDrop)} onClose={() => setDetailDrop(null)} />,
        document.body
      )}
    </article>
  );
}

export function DungeonDropsModal({ dungeon, special = false, onClose }: {
  dungeon: Dungeon;
  special?: boolean;
  onClose: () => void;
}) {
  const drops = [...(dungeon.drops ?? [])].sort((a, b) => qualityRank(b.quality) - qualityRank(a.quality) || b.dropRate - a.dropRate);
  const dropTypes = uniqueDropTypes(drops);
  const specialDungeon = special || isSpecialDungeon(dungeon);
  const [detailDrop, setDetailDrop] = useState<DropPreview | null>(null);
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className="dungeon-drops-modal" onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <span className="eyebrow">{specialDungeon ? '特殊副本' : dungeon.difficulty} · 门槛 Lv.{dungeon.minimumLevel}</span>
            <h2>{dungeon.name} · 掉落详情</h2>
            <p>共 {drops.length} 件可掉落 · 推荐战力 {formatNumber(dungeon.recommendedPower)}{specialDungeon ? ' · 评分越高碎片与爆装越多' : ''}</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        {dropTypes.length > 0 && (
          <div className="drop-slot-row">
            {dropTypes.map((type) => <small key={type}>{typeName(type)}</small>)}
          </div>
        )}
        <div className="drop-preview-grid dungeon-drops-modal-grid">
          {drops.map((drop) => (
            <DropPreviewCard key={drop.templateId} drop={drop} onSelect={() => setDetailDrop(drop)} />
          ))}
        </div>
      </section>
      {detailDrop && createPortal(
        <ItemDetail item={dropToEquipmentDetail(detailDrop)} onClose={() => setDetailDrop(null)} />,
        document.body
      )}
    </div>
  );
}

export function DropPreviewCard({ drop, onSelect }: { drop: DropPreview; onSelect?: () => void }) {
  const chance = formatDropRate(drop.dropRate);
  return (
    <div
      className={`drop-preview-card ${drop.quality}${onSelect ? ' selectable' : ''}`}
      onClick={onSelect}
      role={onSelect ? 'button' : undefined}
    >
      <div>
        <Gem size={16} />
        <span>{qualityName(drop.quality)} · {typeName(drop.itemType)} · Lv.{drop.requiredLevel}</span>
      </div>
      <strong className={`quality ${drop.quality}`}>{drop.name}</strong>
      <p>{dropBonusText(drop)}</p>
      <small>掉率 {chance} · 售价 {drop.sellPrice}</small>
    </div>
  );
}

export function HomeEquipmentOverview({ home, onSelect, onManage, onProfile }: {
  home: HomeSnapshot;
  onSelect: (item: Item) => void;
  onManage: () => void;
  onProfile: () => void;
}) {
  const slots = useMemo(() => equipmentSlotPairs(home.equippedItems), [home.equippedItems]);
  const equippedItems = slots.map(([, item]) => item).filter((item): item is Item => Boolean(item));
  const equippedCount = equippedItems.length;
  const topEquipment = [...equippedItems]
    .sort((left, right) => itemPower(right) - itemPower(left))
    .slice(0, 3);
  const highestQuality = equippedItems.reduce(
    (best, item) => qualityRank(item.quality) > qualityRank(best) ? item.quality : best,
    'common',
  );
  const highTierCount = equippedItems.filter((item) => qualityRank(item.quality) >= qualityRank('epic')).length;
  const missingSlots = slots.filter(([, item]) => !item).map(([slot]) => slotName(slot));
  const completion = `${equippedCount}/${slots.length}`;
  const readiness = equippedCount === slots.length ? '全槽就绪' : `缺 ${slots.length - equippedCount} 个槽位`;

  return (
    <div className="panel home-equipment-overview">
      <div className="home-equipment-head">
        <div>
          <span className="eyebrow">装备概览</span>
          <h2>{readiness}</h2>
        </div>
        <button className="mini-action subtle" onClick={onManage}>
          <Backpack size={15} />
          管理装备
        </button>
      </div>

      <div className="home-equipment-summary">
        <div>
          <span>装备战力</span>
          <strong>{formatNumber(home.equipmentPower)}</strong>
        </div>
        <div>
          <span>穿戴</span>
          <strong>{completion}</strong>
        </div>
        <div>
          <span>最高品质</span>
          <strong className={`quality ${highestQuality}`}>{equippedItems.length ? qualityName(highestQuality) : '无'}</strong>
        </div>
      </div>

      <div className="home-slot-strip" aria-label="装备槽位状态">
        {slots.map(([slot, item]) => (
          <button
            key={slot}
            className={`home-slot-dot ${item ? `filled ${item.quality}` : 'empty'}`}
            disabled={!item}
            title={item ? `${slotName(slot)} · ${equipmentDisplayName(item)}` : `${slotName(slot)}未穿戴`}
            onClick={() => item && onSelect(item)}
          >
            <span>{homeSlotShortName(slot)}</span>
          </button>
        ))}
      </div>

      <div className="home-core-equipment">
        <div className="home-equipment-subhead">
          <span>核心装备</span>
          <small>{highTierCount} 件史诗以上</small>
        </div>
        {topEquipment.length === 0 ? (
          <EmptyState text="暂无装备，先去副本获取第一件战利品。" />
        ) : (
          <div className="home-core-list">
            {topEquipment.map((item) => (
              <button key={item.id} className={`home-core-item ${item.quality}`} onClick={() => onSelect(item)}>
                <Gem size={16} />
                <span>{typeName(item.itemType)}</span>
                <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
                <small>{formatNumber(itemPower(item))}</small>
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="home-equipment-foot">
        <small>{missingSlots.length ? `待补：${missingSlots.slice(0, 3).join(' / ')}${missingSlots.length > 3 ? '…' : ''}` : '装备栏已完整，优先强化和替换高品质部位。'}</small>
        <button className="mini-action subtle" onClick={onProfile}>角色详情</button>
      </div>
    </div>
  );
}

export function EquipmentPanel({ home, onSelect, compact = false }: { home: HomeSnapshot; onSelect?: (item: Item) => void; compact?: boolean }) {
  const slots = useMemo(() => equipmentSlotOrder().map((slot) => [slot, home.equippedItems[slot] ?? null] as const), [home.equippedItems]);
  const equippedCount = slots.filter(([, item]) => Boolean(item)).length;
  return (
    <div className={`panel equipment-panel${compact ? ' compact' : ''}`}>
      <div className="equipment-panel-head">
        <div>
          <span className="eyebrow">装备栏</span>
          <h2>已穿戴 {equippedCount}/{slots.length}</h2>
        </div>
        <Shield size={18} />
      </div>
      <div className="equipment-grid">
        {slots.map(([slot, item]) => (
          <button
            key={slot}
            className={`equipment-cell slot-button ${item ? 'filled' : 'empty'} ${enhancementEffectClass(item)}`}
            disabled={!item}
            onClick={() => item && onSelect?.(item)}
          >
            <div className="slot-label-row">
              <span>{slotName(slot)}</span>
              {item && <EnhancementBadge level={item.enhancementLevel} />}
            </div>
            {item ? (
              <>
                <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
                <small>{compact ? equipmentCompactText(item) : bonusText(item)}</small>
              </>
            ) : (
              <>
                <strong>未穿戴</strong>
                <small>等待装备</small>
              </>
            )}
          </button>
        ))}
      </div>
    </div>
  );
}

export function PowerBreakdownPanel({ slices, total }: { slices: PowerBreakdownSlice[]; total: number }) {
  return (
    <div className="power-breakdown-list">
      {slices.map((slice) => {
        const percent = total > 0 ? Math.max(3, Math.min(100, Math.round((slice.value / total) * 100))) : 0;
        return (
          <div key={slice.key} className="power-breakdown-row">
            <div>
              <span>{slice.label}</span>
              <strong>{formatNumber(slice.value)}</strong>
            </div>
            <div className="power-breakdown-bar">
              <span style={{ width: `${percent}%` }} />
            </div>
            <small>{slice.detail}</small>
          </div>
        );
      })}
    </div>
  );
}

export function CombatStatsPanel({ stats, equipmentStats, compact = false }: { stats: DerivedStats; equipmentStats?: DerivedStats; compact?: boolean }) {
  return (
    <section className={`combat-stats-panel ${compact ? 'compact' : ''}`}>
      <div className="equipment-panel-head">
        <div>
          <span className="eyebrow">最终战斗属性</span>
          <h2>装备已计入</h2>
        </div>
        <Gauge size={18} />
      </div>
      <div className="combat-stat-grid">
        <CombatStatCell label="生命" value={formatNumber(stats.maxHp)} bonus={equipmentStats?.maxHp} />
        <CombatStatCell label="法力" value={formatNumber(stats.maxMp)} bonus={equipmentStats?.maxMp} />
        <CombatStatCell label="攻击" value={formatNumber(stats.attackPower)} bonus={equipmentStats?.attackPower} />
        <CombatStatCell label="护甲" value={formatNumber(stats.armor)} bonus={equipmentStats?.armor} />
        <CombatStatCell label="抗性" value={formatNumber(stats.resistance)} bonus={equipmentStats?.resistance} />
        <CombatStatCell label="速度" value={formatNumber(stats.speed)} />
        <CombatStatCell label="命中" value={formatPercent(stats.accuracy)} bonus={equipmentStats ? equipmentStats.accuracy : undefined} percent />
        <CombatStatCell label="闪避" value={formatPercent(stats.evasion)} bonus={equipmentStats ? equipmentStats.evasion : undefined} percent />
        <CombatStatCell label="暴击" value={formatPercent(stats.critChance)} bonus={equipmentStats ? equipmentStats.critChance : undefined} percent />
      </div>
    </section>
  );
}

export function StatContributionGrid({ stats, baseStats, equipmentStats }: { stats: DerivedStats; baseStats: DerivedStats; equipmentStats: DerivedStats }) {
  return (
    <div className="stat-contribution-grid">
      <StatContributionCell label="生命" total={formatNumber(stats.maxHp)} base={formatNumber(baseStats.maxHp)} equipment={formatNumber(equipmentStats.maxHp)} />
      <StatContributionCell label="法力" total={formatNumber(stats.maxMp)} base={formatNumber(baseStats.maxMp)} equipment={formatNumber(equipmentStats.maxMp)} />
      <StatContributionCell label="攻击" total={formatNumber(stats.attackPower)} base={formatNumber(baseStats.attackPower)} equipment={formatNumber(equipmentStats.attackPower)} />
      <StatContributionCell label="护甲" total={formatNumber(stats.armor)} base={formatNumber(baseStats.armor)} equipment={formatNumber(equipmentStats.armor)} />
      <StatContributionCell label="抗性" total={formatNumber(stats.resistance)} base={formatNumber(baseStats.resistance)} equipment={formatNumber(equipmentStats.resistance)} />
      <StatContributionCell label="速度" total={formatNumber(stats.speed)} base={formatNumber(baseStats.speed)} equipment="0" />
      <StatContributionCell label="命中" total={formatPercent(stats.accuracy)} base={formatPercent(baseStats.accuracy)} equipment={formatPercent(equipmentStats.accuracy)} />
      <StatContributionCell label="闪避" total={formatPercent(stats.evasion)} base={formatPercent(baseStats.evasion)} equipment={formatPercent(equipmentStats.evasion)} />
      <StatContributionCell label="暴击" total={formatPercent(stats.critChance)} base={formatPercent(baseStats.critChance)} equipment={formatPercent(equipmentStats.critChance)} />
    </div>
  );
}

export function CombatStatCell({ label, value, bonus, percent = false }: { label: string; value: string; bonus?: number; percent?: boolean }) {
  const hasBonus = typeof bonus === 'number' && bonus > 0;
  const bonusText = hasBonus ? (percent ? `装备 +${formatPercent(bonus)}` : `装备 +${formatNumber(bonus)}`) : '基础';
  return (
    <div className="combat-stat-cell">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{bonusText}</small>
    </div>
  );
}

export function StatContributionCell({ label, total, base, equipment }: { label: string; total: string; base: string; equipment: string }) {
  return (
    <div className="stat-contribution-cell">
      <span>{label}</span>
      <strong>{total}</strong>
      <small>基础 {base} · 装备 +{equipment}</small>
    </div>
  );
}

export function QuestCard({ quest, selected = false, loading, onSelect, onClaim, onNavigate }: {
  quest: QuestRow;
  selected?: boolean;
  loading: boolean;
  onSelect: () => void;
  onClaim: () => void;
  onNavigate: () => void;
}) {
  const progress = Math.min(100, quest.progressPercent ?? Math.round((quest.currentValue / Math.max(1, quest.targetValue)) * 100));
  return (
    <article className={`quest-card ${quest.status} ${selected ? 'selected' : ''}`} onClick={onSelect}>
      <div className="quest-heading">
        <div>
          <span className="eyebrow">{categoryName(quest.category)} · {statusName(quest.status)}</span>
          <h2>{quest.title}</h2>
        </div>
        <strong>{quest.currentValue}/{quest.targetValue}</strong>
      </div>
      <p>{quest.description}</p>
      {quest.conditions?.length > 1 && (
        <div className="quest-condition-mini">
          {quest.conditions.slice(0, 2).map((condition) => (
            <span key={condition.conditionId}>{condition.currentValue}/{condition.targetValue}</span>
          ))}
        </div>
      )}
      <div className="progress-bar" aria-label="任务进度">
        <span style={{ width: `${progress}%` }} />
      </div>
      <div className="reward-row">
        {quest.rewards.map((reward, index) => (
          <span key={`${reward.type}-${reward.targetId ?? index}`} className={reward.quality ?? ''}>{rewardName(reward)}</span>
        ))}
      </div>
      <div className="action-row">
        {quest.claimable && <button className="mini-action" disabled={loading} onClick={(event) => {
          event.stopPropagation();
          onClaim();
        }}>领取</button>}
        {quest.status === 'active' && <button className="mini-action subtle" onClick={(event) => {
          event.stopPropagation();
          onNavigate();
        }}>前往</button>}
      </div>
    </article>
  );
}

export function QuestDetailPanel({ quest, loading, onClaim, onNavigate }: {
  quest: QuestRow | null;
  loading: boolean;
  onClaim: (questId: string) => void;
  onNavigate: (target?: string) => void;
}) {
  if (!quest) {
    return (
      <aside className="quest-detail-panel detail-rail">
        <EmptyState text="选择一个任务查看详情。" />
      </aside>
    );
  }
  const progress = Math.min(100, quest.progressPercent ?? 0);
  return (
    <aside className={`quest-detail-panel detail-rail ${quest.status}`}>
      <div>
        <span className="eyebrow">{categoryName(quest.category)} · {statusName(quest.status)} · {quest.resetPeriod}</span>
        <h2>{quest.title}</h2>
        <p>{quest.lore || quest.description}</p>
      </div>
      <div className="progress-bar" aria-label="任务详情进度">
        <span style={{ width: `${progress}%` }} />
      </div>
      <div className="quest-condition-list">
        {(quest.conditions?.length ? quest.conditions : [{
          conditionId: quest.id,
          conditionType: quest.navigationTarget ?? 'progress',
          currentValue: quest.currentValue,
          targetValue: quest.targetValue,
          completed: quest.status === 'completed' || quest.status === 'claimed',
        }]).map((condition) => (
          <div key={condition.conditionId} className={condition.completed ? 'complete' : ''}>
            <span>{conditionName(condition.conditionType)}</span>
            <strong>{condition.currentValue}/{condition.targetValue}</strong>
          </div>
        ))}
      </div>
      <div className="quest-reward-grid">
        {quest.rewards.map((reward, index) => (
          <div key={`${reward.type}-${reward.targetId ?? index}`} className={`reward-tile ${reward.quality ?? ''}`}>
            <span>{reward.itemCategory ? itemCategoryLabel({ itemCategory: reward.itemCategory, itemType: reward.itemCategory }) : reward.type}</span>
            <strong>{reward.itemName ?? rewardName(reward)}</strong>
            <small>x{reward.amount}</small>
          </div>
        ))}
      </div>
      <div className="result-modal-actions">
        {quest.claimable && <button className="primary-action" disabled={loading} onClick={() => onClaim(quest.id)}>领取奖励</button>}
        {quest.status === 'active' && <button className="mini-action subtle" onClick={() => onNavigate(quest.navigationTarget)}>前往</button>}
      </div>
    </aside>
  );
}

export function EnhancementBadge({ level }: { level?: number }) {
  const text = enhancementBadgeText(level ?? 0);
  if (!text) {
    return null;
  }
  return <span className={`enhance-badge enhance-${enhancementStage(level ?? 0)}`}>{text}</span>;
}

export function ItemCard({
  item,
  label,
  children,
  powerIncrease = false,
  showEffectText = true,
  className = '',
  onSelect,
}: {
  item: Item;
  label?: string;
  children?: ReactNode;
  powerIncrease?: boolean;
  showEffectText?: boolean;
  className?: string;
  onSelect?: () => void;
}) {
  return (
    <article className={`item-card ${className} ${enhancementEffectClass(item)} item-category-${item.itemCategory ?? 'equipment'} ${onSelect ? 'selectable' : ''}`} onClick={onSelect}>
      {powerIncrease && (
        <span className="power-up-indicator" aria-label="穿戴后战力提升" title="穿戴后战力提升">
          <ArrowUp size={16} strokeWidth={3} />
        </span>
      )}
      <div className="item-main">
        <Package size={18} />
        <div>
          <div className="item-meta-line">
            <span className="eyebrow">
              {label ?? `${itemCategoryLabel(item)} · ${typeName(item.itemType)}`} · Lv.{item.requiredLevel}
              {item.stackable && item.quantity > 1 ? ` · x${item.quantity}` : ''}
            </span>
            <EnhancementBadge level={item.enhancementLevel} />
          </div>
          <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
          {showEffectText && <p>{itemEffectText(item)}</p>}
        </div>
      </div>
      {children && <div className="inline-actions">{children}</div>}
    </article>
  );
}

export function TransferItemOption({ item, selected, disabled = false, onSelect }: {
  item: Item;
  selected: boolean;
  disabled?: boolean;
  onSelect: () => void;
}) {
  return (
    <button className={`transfer-item-option ${selected ? 'selected' : ''}`} disabled={disabled} onClick={onSelect}>
      <div className="item-meta-line">
        <span className="eyebrow">{qualityName(item.quality)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
        <EnhancementBadge level={item.enhancementLevel} />
      </div>
      <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
      <small>{bonusText(item)}</small>
    </button>
  );
}

export function ItemDetail({ item, onClose }: { item: EquipmentDetailData; onClose: () => void }) {
  return (
    <div className="detail-backdrop" onClick={onClose}>
      <section className={`item-detail ${item.quality} ${enhancementEffectClass(item)}`} onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <button className="text-button close-button" onClick={onClose}>关闭</button>
        <div className="detail-gem">
          <Gem size={30} />
        </div>
        <div className="item-meta-line detail-meta-line">
          <span className="eyebrow">{qualityName(item.quality)} · {itemCategoryLabel(item)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
          <EnhancementBadge level={item.enhancementLevel} />
        </div>
        <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
        {item.description && <p className="detail-description">{item.description}</p>}
        <p>{itemEffectText(item)}</p>
        <div className="detail-stat-grid">
          <Metric label="攻击" value={formatNumber(item.attackBonus)} />
          <Metric label="防御" value={formatNumber(item.defenseBonus)} />
          <Metric label="抗性" value={formatNumber(item.resistanceBonus)} />
          <Metric label="生命" value={formatNumber(item.hpBonus)} />
          <Metric label="法力" value={formatNumber(item.mpBonus)} />
          <Metric label="暴击" value={`${Math.round((item.critBonus ?? 0) * 1000) / 10}%`} />
          <Metric label="强化" value={`+${item.enhancementLevel}`} />
          <Metric label="幸运值" value={(item.enhancementLuck ?? 0).toString()} />
          <Metric label="升阶" value={`阶${item.ascensionLevel ?? 0}/5`} />
          <Metric label="升阶幸运" value={(item.ascensionLuck ?? 0).toString()} />
          <Metric label="宝石加成" value={processingBonusSummary(item, 'socket')} />
          <Metric label="词条加成" value={processingBonusSummary(item, 'affix')} />
          <Metric label="售价" value={formatNumber(item.sellPrice)} />
          <Metric label="战力估算" value={formatNumber(item.power ?? itemPower(item))} />
        </div>
        <div className="detail-source">
          <span>装备出处</span>
          <strong>{item.origin ?? originForItem(item.templateId)}</strong>
          <small>{typeName(item.itemType)}</small>
        </div>
      </section>
    </div>
  );
}

export function EquipConfirmModal({ item, currentItem, targetSlot, currentPower, loading, onClose, onConfirm }: {
  item: Item;
  currentItem: Item | null;
  targetSlot: string;
  currentPower: number;
  loading: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const delta = itemPower(item) - (currentItem ? itemPower(currentItem) : 0);
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className="decision-modal" onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <span className="eyebrow">穿戴确认 · {slotName(targetSlot)}</span>
            <h2>{equipmentDisplayName(item)}</h2>
            <p>比较新旧装备属性，再决定是否替换。</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="compare-grid">
          <CompareCard title="当前装备" item={currentItem} />
          <CompareCard title="准备穿戴" item={item} highlight />
        </div>
        <div className="power-delta">
          <Metric label="当前战力" value={currentPower.toString()} />
          <Metric label="装备差值" value={`${delta >= 0 ? '+' : ''}${delta}`} />
          <Metric label="预计战力" value={(currentPower + delta).toString()} />
        </div>
        <div className="result-modal-actions">
          <button className="mini-action subtle" onClick={onClose}>再想想</button>
          <button className="primary-action" disabled={loading} onClick={onConfirm}>{loading ? '穿戴中...' : '确认穿戴'}</button>
        </div>
      </section>
    </div>
  );
}

export function CompareCard({ title, item, highlight = false, emptyTitle = '空槽位', emptyText = '穿戴后将直接补齐该部位。', emptyMeta = '无来源' }: {
  title: string;
  item: Item | null;
  highlight?: boolean;
  emptyTitle?: string;
  emptyText?: string;
  emptyMeta?: string;
}) {
  return (
    <article className={`compare-card ${enhancementEffectClass(item)} ${highlight ? 'highlight' : ''}`}>
      <div className="item-meta-line">
        <span>{title}</span>
        {item && <EnhancementBadge level={item.enhancementLevel} />}
      </div>
      {item ? (
        <>
          <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
          <p>{bonusText(item)}</p>
          <small>{originForItem(item.templateId)}</small>
        </>
      ) : (
        <>
          <h2>{emptyTitle}</h2>
          <p>{emptyText}</p>
          <small>{emptyMeta}</small>
        </>
      )}
    </article>
  );
}

function stoneStock(stone: Item) {
  return Math.max(1, stone.quantity ?? 1);
}

function compareEnhancementStones(left: Item, right: Item) {
  return right.enhanceBonusRate - left.enhanceBonusRate
    || qualityRank(right.quality) - qualityRank(left.quality)
    || right.requiredLevel - left.requiredLevel
    || right.id - left.id;
}

export function EnhanceModal({
  item,
  gold,
  loading,
  stones = [],
  selectedStoneIds = [],
  autoEnhanceRunning = false,
  onAddStone,
  onRemoveStone,
  onReplaceStones,
  onClose,
  onEnhance,
  onAutoEnhance,
  onStopAutoEnhance,
}: {
  item: Item;
  gold: number;
  loading: boolean;
  stones?: Item[];
  selectedStoneIds?: number[];
  autoEnhanceRunning?: boolean;
  onAddStone?: (stoneId: number) => void;
  onRemoveStone?: (index: number) => void;
  onReplaceStones?: (stoneIds: number[]) => void;
  onClose: () => void;
  onEnhance: () => void;
  onAutoEnhance?: (options: { targetLevel: number; useBestStones: boolean }) => void;
  onStopAutoEnhance?: () => void;
}) {
  const [stonePickerOpen, setStonePickerOpen] = useState(false);
  const [autoTargetLevel, setAutoTargetLevel] = useState(Math.min(15, item.enhancementLevel + 1));
  const [autoUseBestStones, setAutoUseBestStones] = useState(true);
  const isMaxEnhancement = item.enhancementLevel >= 15;
  const nextLevel = isMaxEnhancement ? item.enhancementLevel : item.enhancementLevel + 1;
  const cost = enhanceCost(item);
  const baseChance = enhanceBaseChance(item);
  const luckBonus = enhanceLuckBonus(item);
  const stoneBonus = selectedStoneBonus(stones, selectedStoneIds);
  const chance = isMaxEnhancement ? 0 : Math.min(0.95, enhanceChance(item) + stoneBonus);
  const uncappedChance = baseChance + luckBonus + stoneBonus;
  const cappedByLimit = !isMaxEnhancement && uncappedChance > chance;
  const luckTargetBonus = Math.max(0, 0.95 - baseChance);
  const luckProgress = luckTargetBonus <= 0 ? 1 : Math.min(1, luckBonus / luckTargetBonus);
  const sortedStones = useMemo(() => [...stones].sort(compareEnhancementStones), [stones]);
  const bestStoneIds = useMemo(() => sortedStones
    .flatMap((stone) => Array.from({ length: stoneStock(stone) }, () => stone.id))
    .slice(0, 3), [sortedStones]);
  const selectedStones = selectedStoneIds.map((stoneId) => stones.find((stone) => stone.id === stoneId) ?? null);
  const canAddStone = !loading && !isMaxEnhancement && selectedStoneIds.length < 3;
  const canAutoFill = !loading && !isMaxEnhancement && bestStoneIds.length > 0;
  const hasSelectedStones = selectedStoneIds.length > 0;
  const minAutoTarget = Math.min(15, item.enhancementLevel + 1);
  const canEnhanceOnce = !isMaxEnhancement && gold >= cost;
  const selectStone = (stoneId: number) => {
    if (!canAddStone) {
      return;
    }
    onAddStone?.(stoneId);
    setStonePickerOpen(false);
  };
  useEffect(() => {
    if (isMaxEnhancement) {
      setStonePickerOpen(false);
      if (selectedStoneIds.length > 0) {
        onReplaceStones?.([]);
      }
      return;
    }
    setAutoTargetLevel((current) => Math.min(15, Math.max(minAutoTarget, current)));
  }, [isMaxEnhancement, minAutoTarget, onReplaceStones, selectedStoneIds.length]);
  const stonePicker = stonePickerOpen && !isMaxEnhancement ? (
    <div className="stone-picker-scrim" onClick={() => setStonePickerOpen(false)}>
      <section className="stone-picker-sheet" onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="stone-picker-head">
          <div>
            <strong>选择强化石</strong>
            <span>{selectedStoneIds.length}/3 已放入</span>
          </div>
          <button type="button" className="text-button close-button" onClick={() => setStonePickerOpen(false)}>关闭</button>
        </div>
        <div className="stone-option-list stone-picker-list">
          {sortedStones.length === 0 && <EmptyState text="当前背包没有适用于下一强化等级的强化石。" />}
          {sortedStones.map((stone) => {
            const selectedCount = selectedStoneCount(selectedStoneIds, stone.id);
            const stock = stoneStock(stone);
            return (
              <button
                key={stone.id}
                type="button"
                className={`stone-option ${stone.quality}`}
                disabled={loading || selectedStoneIds.length >= 3 || selectedCount >= stock}
                onClick={() => selectStone(stone.id)}
              >
                <strong>{equipmentDisplayName(stone)}</strong>
                <span>+{formatPercent(stone.enhanceBonusRate)} · {selectedCount}/{stock}</span>
              </button>
            );
          })}
        </div>
      </section>
    </div>
  ) : null;
  return (
    <div className="detail-backdrop result-modal-backdrop enhance-modal-backdrop" onClick={onClose}>
      <section className={`decision-modal enhance-modal ${item.quality} ${enhancementEffectClass(item)}`} onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <div className="item-meta-line">
              <span className="eyebrow">{isMaxEnhancement ? `装备强化 · +${item.enhancementLevel}` : `装备强化 · +${item.enhancementLevel} → +${nextLevel}`}</span>
              <EnhancementBadge level={item.enhancementLevel} />
            </div>
            <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
            <p>{bonusText(item)}</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="result-modal-metrics">
          <Metric label="强化费用" value={isMaxEnhancement ? '已达上限' : `${cost} 金`} />
          <Metric label="最终成功率" value={isMaxEnhancement ? '已达上限' : formatPercent(chance)} />
          <Metric label="当前金币" value={`${gold} 金`} />
          <Metric label="基础成功率" value={isMaxEnhancement ? '--' : formatPercent(baseChance)} />
          <Metric label="祝福加成" value={isMaxEnhancement ? '--' : `+${formatPercent(luckBonus)}`} />
          <Metric label="石头加成" value={`+${formatPercent(stoneBonus)}`} />
        </div>
        <div className="enhance-progress-panel">
          <div className="enhance-progress-row">
            <span>成功率进度</span>
            <strong>{isMaxEnhancement ? '已达上限' : `${formatPercent(chance)}${cappedByLimit ? ' · 上限 95%' : ''}`}</strong>
          </div>
          <div className="enhance-progress-track">
            <i style={{ width: `${isMaxEnhancement ? 100 : Math.round(chance * 100)}%` }} />
          </div>
          <div className="enhance-progress-row">
            <span>祝福值进度</span>
            <strong>{isMaxEnhancement ? '已达上限' : `${item.enhancementLuck ?? 0} 层 · +${formatPercent(luckBonus)}`}</strong>
          </div>
          <div className="enhance-progress-track blessing">
            <i style={{ width: `${Math.round((isMaxEnhancement ? 1 : luckProgress) * 100)}%` }} />
          </div>
        </div>
        <div className="enhance-stone-panel">
          <div className="enhance-stone-head">
            <div>
              <strong>强化石槽</strong>
              <span>{selectedStoneIds.length}/3 已放入</span>
            </div>
            <div className="enhance-stone-actions">
              {hasSelectedStones && (
                <button type="button" className="mini-action subtle" disabled={loading || isMaxEnhancement} onClick={() => onReplaceStones?.([])}>
                  清空
                </button>
              )}
              <button type="button" className="mini-action" disabled={!canAutoFill} onClick={() => onReplaceStones?.(bestStoneIds)}>
                自动放入最佳
              </button>
            </div>
          </div>
          <div className="stone-slot-row">
            {[0, 1, 2].map((slotIndex) => {
              const stone = selectedStones[slotIndex];
              return (
                <button
                  key={slotIndex}
                  type="button"
                  className={`stone-slot ${stone ? stone.quality : 'empty'}`}
                  disabled={loading || isMaxEnhancement || (!stone && !canAddStone)}
                  onClick={() => {
                    if (stone) {
                      onRemoveStone?.(slotIndex);
                    } else if (canAddStone) {
                      setStonePickerOpen(true);
                    }
                  }}
                >
                  {stone ? (
                    <>
                      <strong>{equipmentDisplayName(stone)}</strong>
                      <span>+{formatPercent(stone.enhanceBonusRate)}</span>
                    </>
                  ) : (
                    <>
                      <strong>空槽</strong>
                      <span>{isMaxEnhancement ? '已达上限' : canAddStone ? '选择强化石' : '已满'}</span>
                    </>
                  )}
                </button>
              );
            })}
          </div>
        </div>
        <div className="detail-source">
          <span>失败规则</span>
          <strong>+7 后失败可能降级，幸运值会提高下一次成功率。</strong>
        </div>
        <div className="enhance-auto-panel">
          <div className="enhance-auto-fields">
            <label>
              <span>自动强化目标</span>
              <input
                type="number"
                min={minAutoTarget}
                max={15}
                value={autoTargetLevel}
                disabled={loading || isMaxEnhancement}
                onChange={(event) => setAutoTargetLevel(Math.min(15, Math.max(minAutoTarget, Number(event.target.value) || minAutoTarget)))}
              />
            </label>
            <label className="toggle-row">
              <input
                type="checkbox"
                checked={autoUseBestStones}
                disabled={loading || isMaxEnhancement}
                onChange={(event) => setAutoUseBestStones(event.target.checked)}
              />
              <span>自动使用最优强化石</span>
            </label>
          </div>
          <div className="enhance-auto-actions">
            {autoEnhanceRunning ? (
              <button type="button" className="mini-action danger" onClick={onStopAutoEnhance}>停止自动强化</button>
            ) : (
              <button
                type="button"
                className="mini-action"
                disabled={!onAutoEnhance || loading || isMaxEnhancement || autoTargetLevel <= item.enhancementLevel}
                onClick={() => onAutoEnhance?.({ targetLevel: autoTargetLevel, useBestStones: autoUseBestStones })}
              >
                开始自动强化
              </button>
            )}
          </div>
        </div>
        <div className="result-modal-actions">
          <button className="mini-action subtle" onClick={onClose}>结束强化</button>
          <button className="primary-action" disabled={loading || !canEnhanceOnce} onClick={onEnhance}>
            {loading ? autoEnhanceRunning ? '自动强化中...' : '强化中...' : isMaxEnhancement ? '已达上限' : gold < cost ? '金币不足' : '强化一次'}
          </button>
        </div>
        {stonePicker && createPortal(stonePicker, document.body)}
      </section>
    </div>
  );
}

export function SellConfirmModal({ item, loading, onClose, onConfirm }: {
  item: Item;
  loading: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className={`decision-modal ${enhancementEffectClass(item)}`} onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <div className="item-meta-line">
              <span className="eyebrow">出售确认 · {qualityName(item.quality)}</span>
              <EnhancementBadge level={item.enhancementLevel} />
            </div>
            <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
            <p>{bonusText(item)}</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="result-modal-metrics">
          <Metric label="出售获得" value={`${item.sellPrice} 金`} />
          <Metric label="强化等级" value={`+${item.enhancementLevel}`} />
          <Metric label="装备出处" value={originForItem(item.templateId).replace('副本掉落 · ', '')} />
        </div>
        <div className="modal-warning">出售后装备会永久移出背包。</div>
        <div className="result-modal-actions">
          <button className="mini-action subtle" onClick={onClose}>取消</button>
          <button className="mini-action danger" disabled={loading} onClick={onConfirm}>{loading ? '出售中...' : '确认出售'}</button>
        </div>
      </section>
    </div>
  );
}

export function CombatantCard({ icon, label, title, detail, hp, maxHp, enemy = false }: {
  icon: ReactNode;
  label: string;
  title: string;
  detail: string;
  hp: number;
  maxHp: number;
  enemy?: boolean;
}) {
  const hasHp = maxHp > 0;
  return (
    <article className={`combatant-card ${enemy ? 'enemy' : 'player'}`}>
      <div className="combatant-heading">
        <div className="combatant-icon">{icon}</div>
        <div>
          <span className="eyebrow">{label}</span>
          <h2>{title}</h2>
        </div>
      </div>
      <p>{detail}</p>
      <HpBar value={hasHp ? hp : 0} max={hasHp ? maxHp : 1} />
      <strong>{hasHp ? `${Math.max(0, hp)}/${maxHp}` : '无目标'}</strong>
    </article>
  );
}

export function HpBar({ value, max }: { value: number; max: number }) {
  const percent = Math.max(0, Math.min(100, Math.round((value / Math.max(1, max)) * 100)));
  return (
    <div className="hp-bar" aria-label="生命值">
      <span style={{ width: `${percent}%` }} />
    </div>
  );
}

export function ResultSummaryModal({ result, onClose, onReturn, onRetry, retrying = false, onSelectLoot }: {
  result: DungeonRunResult;
  onClose: () => void;
  onReturn: () => void | Promise<void>;
  onRetry?: () => void;
  retrying?: boolean;
  onSelectLoot: (item: Item) => void;
}) {
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className={`result-modal ${result.success ? 'win' : 'lose'}`} onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <span className="eyebrow">{result.success ? '副本通关' : '副本撤退'} · 评价 {result.rating}</span>
            <h2>{result.dungeonName}</h2>
            <p>击败 {result.monstersKilled} 只魔物，获得 {result.expGained} 经验 / {result.goldGained} 金。</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="result-modal-metrics">
          <Metric label="最终生命" value={`${Math.max(0, result.playerFinalHp)}/${result.playerMaxHp}`} />
          <Metric label="当前战力" value={result.combatPower.toString()} />
          <Metric label="掉落物品" value={result.loot.length.toString()} />
          <Metric label="剩余疲劳" value={`${result.stamina.current}/${result.stamina.max}`} />
        </div>
        <SectionTitle icon={<Gem size={18} />} title="掉落明细" />
        <div className="result-loot-detail-grid">
          {result.loot.length === 0 && <EmptyState text="这次没有物品掉落，可以换高掉率副本继续刷。" />}
          {result.loot.map((item, index) => (
            <button key={`${item.id}-${index}`} className={`loot-detail-card ${item.quality}`} onClick={() => onSelectLoot(item)}>
              <span>{qualityName(item.quality)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
              <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
              <p>{itemEffectText(item)}</p>
              <small>售价 {item.sellPrice} 金</small>
            </button>
          ))}
        </div>
        <div className="result-modal-actions">
          <button className="mini-action subtle" onClick={onClose}>继续看战报</button>
          {onRetry && <button className="primary-action" disabled={retrying} onClick={onRetry}>{retrying ? '进入中...' : '再来一次'}</button>}
          <button className="mini-action subtle" onClick={() => void onReturn()}>回到副本大厅</button>
        </div>
      </section>
    </div>
  );
}

export function SweepSummaryModal({ result, onClose, onSelectLoot }: {
  result: DungeonSweepResult;
  onClose: () => void;
  onSelectLoot: (item: Item) => void;
}) {
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className="result-modal sweep-summary win" onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <span className="eyebrow">扫荡完成 · {result.times} 次</span>
            <h2>{result.dungeonName}</h2>
            <p>共击败 {result.monstersKilled} 只魔物，获得 {result.expGained} 经验 / {result.goldGained} 金。</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="result-modal-metrics">
          <Metric label="扫荡次数" value={result.times.toString()} />
          <Metric label="当前战力" value={result.combatPower.toString()} />
          <Metric label="掉落物品" value={result.loot.length.toString()} />
          <Metric label="剩余疲劳" value={`${result.stamina.current}/${result.stamina.max}`} />
        </div>
        <div className="sweep-log">
          {result.logs.map((log) => <p key={log}>{log}</p>)}
        </div>
        <SectionTitle icon={<Gem size={18} />} title="扫荡掉落" />
        <div className="result-loot-detail-grid">
          {result.loot.length === 0 && <EmptyState text="十次扫荡没有物品掉落，下一轮可能会转运。" />}
          {result.loot.map((item, index) => (
            <button key={`${item.id}-${index}`} className={`loot-detail-card ${item.quality}`} onClick={() => onSelectLoot(item)}>
              <span>{qualityName(item.quality)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
              <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
              <p>{itemEffectText(item)}</p>
              <small>售价 {item.sellPrice} 金</small>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}

export function MarketSaleCard({ sale, onInspect }: { sale: MarketSale; onInspect: () => void }) {
  return (
    <button className={`market-sale-card ${sale.item.quality} ${enhancementEffectClass(sale.item)}`} onClick={onInspect}>
      <div className="item-meta-line">
        <span>{formatRelativeTime(sale.soldAt)} · {sale.buyerName} · {marketCategoryName(sale.item.marketCategory)}</span>
        {sale.item.enhancementLevel > 0 && <EnhancementBadge level={sale.item.enhancementLevel} />}
      </div>
      <strong className={`quality ${sale.item.quality}`}>{marketDisplayName(sale.item)}</strong>
      <p>成交 {sale.price} 金 · 到账 {sale.netGold} 金{(sale.item.quantity ?? 1) > 1 ? ` · ${sale.item.quantity} 件` : ''}</p>
    </button>
  );
}

export function MarketListedCard({ listing, loading, onCancel, onInspect }: {
  listing: MarketListing;
  loading: boolean;
  onCancel: () => void;
  onInspect: () => void;
}) {
  return (
    <article className={`market-sale-card listed ${listing.item.quality} ${enhancementEffectClass(listing.item)} selectable`} onClick={onInspect}>
      <div className="item-meta-line">
        <span>{formatRelativeTime(listing.listedAt)} · {listing.marketTag} · {marketCategoryName(listing.item.marketCategory)}</span>
        {listing.item.enhancementLevel > 0 && <EnhancementBadge level={listing.item.enhancementLevel} />}
      </div>
      <strong className={`quality ${listing.item.quality}`}>{marketDisplayName(listing.item)}</strong>
      <p>总价 {listing.price} 金 · 单价 {listing.unitPrice} 金 · 成交 {listing.dealChance}%</p>
      <button className="mini-action subtle" disabled={loading} onClick={(event) => {
        event.stopPropagation();
        onCancel();
      }}>撤回</button>
    </article>
  );
}

export function ListingCard({ listing, own, loading, onBuy, onCancel, onInspect }: {
  listing: MarketListing;
  own: boolean;
  loading: boolean;
  onBuy: () => void;
  onCancel: () => void;
  onInspect: () => void;
}) {
  return (
    <article className={`item-card listing-card selectable ${enhancementEffectClass(listing.item)}`} onClick={onInspect}>
      <div className="item-main">
        <Coins size={18} />
        <div>
          <div className="item-meta-line">
            <span className="eyebrow">{listing.sellerName} · {listing.sellerType} · {marketCategoryName(listing.item.marketCategory)} · Lv.{listing.item.requiredLevel}</span>
            {listing.item.enhancementLevel > 0 && <EnhancementBadge level={listing.item.enhancementLevel} />}
          </div>
          <h2 className={`quality ${listing.item.quality}`}>{marketDisplayName(listing.item)}</h2>
          <p>{marketItemSummary(listing.item)}</p>
          <div className="market-tags">
            <span>{listing.marketTag}</span>
            <span>估值 {listing.recommendedPrice}/件</span>
            <span>数量 {listing.quantity}</span>
            <span>成交 {listing.dealChance}%</span>
            <span>{formatRelativeTime(listing.listedAt)}</span>
          </div>
        </div>
      </div>
      <div className="inline-actions">
        <strong className="price-tag">{listing.price} 金</strong>
        {listing.quantity > 1 && <span className="unit-price-tag">{listing.unitPrice} 金/件</span>}
        {own ? (
          <button className="mini-action subtle" disabled={loading} onClick={(event) => {
            event.stopPropagation();
            onCancel();
          }}>撤回</button>
        ) : (
          <button className="mini-action" disabled={loading} onClick={(event) => {
            event.stopPropagation();
            onBuy();
          }}>购买</button>
        )}
      </div>
    </article>
  );
}

export function LeaderboardCard({ entry, metric, onSelectSpeaker }: { entry: LeaderboardEntry; metric: LeaderboardMetric; onSelectSpeaker: () => void }) {
  const secondaryStats = leaderboardSecondaryStats(entry, metric);
  return (
    <article className={`leaderboard-card ${entry.player ? 'self' : ''}`}>
      <div className="rank-block">
        <strong className="rank">#{entry.rank}</strong>
        {entry.rank <= 3 && <span>银冠</span>}
      </div>
      <div className="leaderboard-profile">
        <span className="eyebrow">{entry.title}</span>
        <button className="leaderboard-name" onClick={onSelectSpeaker}>{entry.name}</button>
        <p>
          <span className={`profession-badge ${entry.profession}`}>{professionName(entry.profession)}</span>
          <span>{entry.player ? '玩家角色' : '冒险者'}</span>
        </p>
      </div>
      <div className="leaderboard-insight">
        <div className="leaderboard-stat-strip">
          {secondaryStats.map((stat) => (
            <span key={stat.label}>
              <small>{stat.label}</small>
              <strong>{stat.value}</strong>
            </span>
          ))}
        </div>
      </div>
      <div className="leaderboard-power">
        <span>{leaderboardMetricName(metric)}</span>
        <strong>{leaderboardScoreText(entry, metric)}</strong>
        <small>点击查看档案</small>
      </div>
    </article>
  );
}

export function CatalogFilterGroup({ title, value, options, onChange }: {
  title: string;
  value: string;
  options: [string, string][];
  onChange: (value: string) => void;
}) {
  return (
    <div className="catalog-filter-group">
      <strong>{title}</strong>
      <div>
        {options.map(([optionValue, label]) => (
          <button
            key={optionValue}
            className={value === optionValue ? 'active' : ''}
            onClick={() => onChange(optionValue)}
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}

export function CatalogDetailPanel({ item }: { item: ItemCatalogItem | null }) {
  if (!item) {
    return (
      <aside className="catalog-detail-panel detail-rail">
        <EmptyState text="选择一个物品查看详细属性。" />
      </aside>
    );
  }

  const detail = catalogItemToDetail(item);
  const statRows = catalogStatRows(item);
  return (
    <aside className={`catalog-detail-panel detail-rail ${item.quality}`}>
      <div className="catalog-detail-head">
        <div className="detail-gem">
          <Gem size={28} />
        </div>
        <div>
          <span className="eyebrow">{qualityName(item.quality)} · {itemCategoryLabel(item)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
          <h2 className={`quality ${item.quality}`}>{item.name}</h2>
          <p>{item.description || itemEffectText(detail)}</p>
        </div>
      </div>

      <div className="catalog-detail-metrics">
        <Metric label="售卖价" value={formatNumber(item.sellPrice)} />
        <Metric label="堆叠" value={item.stackable ? `最多 ${item.maxStack}` : '不可堆叠'} />
        <Metric label="模板" value={item.templateId} />
        <Metric label="战力估算" value={isEquipmentItem(item) ? formatNumber(catalogItemPower(item)) : '-'} />
      </div>

      <div className="catalog-effect-box">
        <span>物品效果</span>
        <strong>{itemEffectText(detail)}</strong>
        {item.effectValueJson && <small>{catalogEffectDetail(item)}</small>}
      </div>

      {statRows.length > 0 && (
        <div className="catalog-stat-list">
          {statRows.map((row) => (
            <div key={row.label}>
              <span>{row.label}</span>
              <strong>{row.value}</strong>
            </div>
          ))}
        </div>
      )}

      <div className="catalog-source-box">
        <span>获取方向</span>
        <strong>{catalogSourceHint(item)}</strong>
        <small>{catalogUsageHint(item)}</small>
      </div>
    </aside>
  );
}

export function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export function StaminaPanel({ stamina, compact = false }: { stamina?: HomeSnapshot['stamina'] | Dungeon['stamina']; compact?: boolean }) {
  const queryClient = useQueryClient();
  const [snapshotObservedAt, setSnapshotObservedAt] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  const refreshKeyRef = useRef('');
  const snapshotKey = stamina
    ? `${stamina.current}:${stamina.max}:${stamina.secondsUntilNext}:${stamina.secondsUntilFull}:${stamina.updatedAt}`
    : 'empty';

  useEffect(() => {
    const nextNow = Date.now();
    setSnapshotObservedAt(nextNow);
    setNow(nextNow);
    refreshKeyRef.current = '';
  }, [snapshotKey]);

  useEffect(() => {
    if (!stamina || stamina.current >= stamina.max) {
      return undefined;
    }
    const interval = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(interval);
  }, [stamina]);

  const liveStamina = useMemo(() => {
    if (!stamina) {
      return null;
    }
    return liveStaminaSnapshot(stamina, Math.floor((now - snapshotObservedAt) / 1000));
  }, [stamina, now, snapshotObservedAt]);

  useEffect(() => {
    if (!stamina || !liveStamina || stamina.current >= stamina.max) {
      return;
    }
    if (liveStamina.current <= stamina.current) {
      return;
    }
    const refreshKey = `${snapshotKey}:${liveStamina.current}`;
    if (refreshKeyRef.current === refreshKey) {
      return;
    }
    refreshKeyRef.current = refreshKey;
    void queryClient.invalidateQueries({ queryKey: ['home'] });
    void queryClient.invalidateQueries({ queryKey: ['dungeons'] });
  }, [liveStamina, queryClient, snapshotKey, stamina]);

  if (!liveStamina) {
    return null;
  }
  const percent = Math.max(0, Math.min(100, Math.round((liveStamina.current / Math.max(1, liveStamina.max)) * 100)));
  return (
    <div className={`stamina-panel ${compact ? 'compact' : ''}`}>
      <div className="stamina-panel-head">
        <span>疲劳</span>
        <strong>{liveStamina.current}/{liveStamina.max}</strong>
      </div>
      <div className="progress-bar stamina-bar" aria-label="疲劳值">
        <span style={{ width: `${percent}%` }} />
      </div>
      <small>{liveStamina.current >= liveStamina.max ? '已满' : `回满 ${formatStaminaTime(liveStamina.secondsUntilFull)}`}</small>
    </div>
  );
}

export function NavTile({ icon, title, detail, onClick }: {
  icon: ReactNode;
  title: string;
  detail: string;
  onClick: () => void;
}) {
  return (
    <button className="nav-button" onClick={onClick}>
      {icon}
      <span>
        <strong>{title}</strong>
        <small>{detail}</small>
      </span>
      <ChevronRight size={18} />
    </button>
  );
}

export function SectionTitle({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <div className="section-title">
      {icon}
      <h2>{title}</h2>
    </div>
  );
}

export function EmptyState({ text }: { text: string }) {
  return <div className="empty-state">{text}</div>;
}

export function ToastNotice({ variant, title, message }: {
  variant: FeedbackVariant | 'warning';
  title: string;
  message: string;
}) {
  const Icon = variant === 'success' ? CircleCheck : CircleAlert;
  return (
    <div className={`toast-notice ${variant}`} role="status" aria-live="polite">
      <Icon size={20} />
      <div>
        <strong>{title}</strong>
        <p>{message}</p>
      </div>
    </div>
  );
}

export function FeedbackDialog({ variant, title, message, onClose }: {
  variant: FeedbackVariant;
  title: string;
  message: string;
  onClose: () => void;
}) {
  const Icon = variant === 'error' ? CircleAlert : CircleCheck;
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className={`feedback-modal ${variant}`} onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="feedback-modal-head">
          <div className="feedback-icon">
            <Icon size={24} />
          </div>
          <div>
            <span className="eyebrow">{variant === 'error' ? '需要处理' : '操作反馈'}</span>
            <h2>{title}</h2>
            <p>{message}</p>
          </div>
          <button className="text-button" onClick={onClose}>关闭</button>
        </div>
        <div className="result-modal-actions">
          <button className={variant === 'error' ? 'mini-action danger' : 'primary-action'} onClick={onClose}>知道了</button>
        </div>
      </section>
    </div>
  );
}

export function ConfirmDialog({ title, message, confirmLabel, cancelLabel, danger = false, onConfirm, onCancel }: {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onCancel}>
      <section className="feedback-modal warning" onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="feedback-modal-head">
          <div className="feedback-icon">
            <CircleAlert size={24} />
          </div>
          <div>
            <span className="eyebrow">需要确认</span>
            <h2>{title}</h2>
            <p>{message}</p>
          </div>
          <button className="text-button" onClick={onCancel}>关闭</button>
        </div>
        <div className="result-modal-actions">
          <button className="mini-action subtle" onClick={onCancel}>{cancelLabel}</button>
          <button className={danger ? 'mini-action danger' : 'primary-action'} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </section>
    </div>
  );
}

export function TopBar({ title, onBack }: { title: string; onBack?: () => void }) {
  return (
    <header className="top-bar">
      {onBack ? <button className="text-button" onClick={onBack}>返回</button> : <span />}
      <strong>{title}</strong>
      <span />
    </header>
  );
}

export function LoadingScreen({ title }: { title: string }) {
  return (
    <section className="screen center-screen">
      <div className="loader" />
      <p>{title}</p>
    </section>
  );
}

export function ErrorScreen({ message }: { message: string }) {
  const logout = useAppStore((state) => state.logout);
  return (
    <section className="screen center-screen">
      <p className="error">{message}</p>
      <button className="primary-action" onClick={logout}>重新登录</button>
    </section>
  );
}

export async function invalidateGameQueries(queryClient: ReturnType<typeof useQueryClient>, token: string) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['home', token] }),
    queryClient.invalidateQueries({ queryKey: ['skills', token] }),
    queryClient.invalidateQueries({ queryKey: ['dungeons', token] }),
    queryClient.invalidateQueries({ queryKey: ['rifts', token] }),
    queryClient.invalidateQueries({ queryKey: ['arena', token] }),
    queryClient.invalidateQueries({ queryKey: ['inventory', token] }),
    queryClient.invalidateQueries({ queryKey: ['builds', token] }),
    queryClient.invalidateQueries({ queryKey: ['quests', token] }),
    queryClient.invalidateQueries({ queryKey: ['leaderboard', token] }),
    queryClient.invalidateQueries({ queryKey: ['recharge-dashboard', token] }),
  ]);
}
