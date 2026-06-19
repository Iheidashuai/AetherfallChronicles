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

export function BuildsScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [selectedBuildId, setSelectedBuildId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<BuildTab>('overview');
  const [draft, setDraft] = useState<BuildDraft | null>(null);
  const [simulationTier, setSimulationTier] = useState('1');
  const [simulationResult, setSimulationResult] = useState<RiftSimulationResult | null>(null);
  const [activationResult, setActivationResult] = useState<BuildActivationResult | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['builds', token],
    queryFn: () => gameApi.builds(token),
    staleTime: 0,
    refetchOnMount: 'always',
  });
  const copyMutation = useMutation({
    mutationFn: (presetId: string) => gameApi.copyBuildPreset(token, presetId),
    onSuccess: async (snapshot) => {
      await queryClient.setQueryData(['builds', token], snapshot);
      setSelectedBuildId(snapshot.builds[0]?.id ?? null);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const createMutation = useMutation({
    mutationFn: () => gameApi.createBuild(token, { name: '空白构筑', strategy: 'balanced', refineFocus: 'balanced' }),
    onSuccess: async (snapshot) => {
      await queryClient.setQueryData(['builds', token], snapshot);
      setSelectedBuildId(snapshot.builds[0]?.id ?? null);
    },
  });
  const saveMutation = useMutation({
    mutationFn: ({ buildId, request }: { buildId: number; request: BuildMutationRequest }) => gameApi.updateBuild(token, buildId, request),
    onSuccess: async (snapshot) => {
      await queryClient.setQueryData(['builds', token], snapshot);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const activateMutation = useMutation({
    mutationFn: (buildId: number) => gameApi.activateBuild(token, buildId),
    onSuccess: async (result) => {
      setActivationResult(result);
      setSimulationResult(null);
      await queryClient.setQueryData(['builds', token], result.snapshot);
      await invalidateGameQueries(queryClient, token);
    },
  });
  const simulateMutation = useMutation({
    mutationFn: ({ buildId, tier }: { buildId: number; tier: number }) => gameApi.simulateBuild(token, buildId, tier),
    onSuccess: (result) => setSimulationResult(result),
  });

  const selectedBuild = useMemo(() => {
    if (!data) {
      return null;
    }
    return data.builds.find((build) => build.id === selectedBuildId)
      ?? data.activeBuild
      ?? data.builds[0]
      ?? null;
  }, [data, selectedBuildId]);
  const sourcePreset = useMemo(() => (
    selectedBuild?.sourcePresetId && data
      ? data.presets.find((preset) => preset.id === selectedBuild.sourcePresetId) ?? null
      : null
  ), [data, selectedBuild]);

  useEffect(() => {
    if (!data) {
      return;
    }
    const nextBuild = data.builds.find((build) => build.id === selectedBuildId)
      ?? data.activeBuild
      ?? data.builds[0]
      ?? null;
    if (nextBuild && nextBuild.id !== selectedBuildId) {
      setSelectedBuildId(nextBuild.id);
    }
    if (!nextBuild) {
      setDraft(null);
      return;
    }
    setDraft(buildToDraft(nextBuild));
    if (simulationTier === '1') {
      setSimulationTier(String(data.suggestedTier));
    }
  }, [data, selectedBuildId, simulationTier]);

  if (isLoading) {
    return <LoadingScreen title="读取构筑档案" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '构筑系统加载失败'} />;
  }

  const warnings = selectedBuild && draft ? buildGapWarnings(selectedBuild, draft, data, sourcePreset) : [];
  const canSave = Boolean(selectedBuild && draft) && !saveMutation.isPending;
  const canActivate = Boolean(selectedBuild) && !activateMutation.isPending;
  const canSimulate = Boolean(selectedBuild) && !simulateMutation.isPending;
  const tier = Math.max(1, Math.floor(Number(simulationTier) || data.suggestedTier || 1));

  function mutateDraft(mutator: (current: BuildDraft) => BuildDraft) {
    setDraft((current) => current ? mutator(current) : current);
  }

  function saveDraft() {
    if (!selectedBuild || !draft) {
      return;
    }
    saveMutation.mutate({ buildId: selectedBuild.id, request: draftToRequest(draft) });
  }

  return (
    <section className="screen builds-screen">
      <TopBar title="构筑系统" onBack={() => setScreen('home')} />
      <div className="status-strip builds-status-strip">
        <div className="battle-brief">
          <Filter size={20} />
          <div>
            <span className="eyebrow">Build Lab</span>
            <strong>{data.activeBuild?.name ?? '未启用构筑'}</strong>
          </div>
        </div>
        <Metric label="职业" value={professionName(data.player.profession)} />
        <Metric label="构筑数" value={`${data.builds.length}/${data.presets.length + data.builds.length}`} />
        <Metric label="建议模拟" value={`T${data.suggestedTier}`} />
        <Metric label="门槛战力" value={formatNumber(data.suggestedMinimumPower)} />
      </div>

      <div className="desktop-workbench builds-workbench">
        <aside className="filter-rail builds-rail">
          <div className="panel-head-row">
            <SectionTitle icon={<Sparkles size={18} />} title="职业预设" />
            <span>{data.presets.length}</span>
          </div>
          <div className="build-list">
            {data.presets.map((preset) => (
              <article key={preset.id} className="build-list-card preset">
                <div>
                  <span className="eyebrow">{strategyName(preset.strategy)} · {refineFocusName(preset.refineFocus)}</span>
                  <strong>{preset.name}</strong>
                  <p>{preset.description}</p>
                </div>
                <button className="mini-action" disabled={copyMutation.isPending} onClick={() => copyMutation.mutate(preset.id)}>
                  复制
                </button>
              </article>
            ))}
          </div>

          <div className="panel-head-row build-owned-head">
            <SectionTitle icon={<Shield size={18} />} title="我的构筑" />
            <button className="mini-action subtle" disabled={createMutation.isPending} onClick={() => createMutation.mutate()}>新建</button>
          </div>
          <div className="build-list owned">
            {data.builds.length === 0 && <EmptyState text="先复制一个职业预设，或新建空白构筑。" />}
            {data.builds.map((build) => (
              <button
                key={build.id}
                className={`build-list-card owned ${build.id === selectedBuild?.id ? 'active' : ''}`}
                onClick={() => {
                  setSelectedBuildId(build.id);
                  setActiveTab('overview');
                  setSimulationResult(null);
                }}
              >
                <span>{build.active ? '已启用' : strategyName(build.strategy)}</span>
                <strong>{build.name}</strong>
                <small>完成度 {build.score.completion} · 深渊 {build.score.rift}</small>
              </button>
            ))}
          </div>
        </aside>

        <section className="main-panel builds-main-panel">
          {!selectedBuild || !draft ? (
            <EmptyState text="选择或创建一个构筑开始配置。" />
          ) : (
            <>
              <div className="build-editor-head">
                <div>
                  <span className="eyebrow">{sourcePreset ? `源自 ${sourcePreset.name}` : '玩家自定义'}</span>
                  <input value={draft.name} onChange={(event) => mutateDraft((current) => ({ ...current, name: event.target.value }))} />
                </div>
                <div className="build-mode-row">
                  {['balanced', 'aggressive', 'survival', 'speed'].map((strategy) => (
                    <button key={strategy} className={draft.strategy === strategy ? 'active' : ''} onClick={() => mutateDraft((current) => ({ ...current, strategy }))}>
                      {strategyName(strategy)}
                    </button>
                  ))}
                </div>
              </div>

              <div className="build-tab-row">
                {[
                  ['overview', '总览'],
                  ['equipment', '装备'],
                  ['skills', '技能轮转'],
                  ['talents', '天赋'],
                  ['simulate', '模拟'],
                ].map(([key, label]) => (
                  <button key={key} className={activeTab === key ? 'active' : ''} onClick={() => setActiveTab(key as BuildTab)}>{label}</button>
                ))}
              </div>

              {activeTab === 'overview' && (
                <div className="build-overview-grid">
                  <BuildScorePanel score={selectedBuild.score} />
                  <section className="build-summary-panel">
                    <SectionTitle icon={<Gauge size={18} />} title="定位摘要" />
                    <p>{sourcePreset?.description ?? '自定义构筑不会绑定固定流派，可以自由组合装备、技能和轻量天赋。'}</p>
                    <div className="build-chip-row">
                      <span>{strategyName(draft.strategy)}</span>
                      <span>{refineFocusName(draft.refineFocus)}</span>
                      <span>{draft.skills.filter((slot) => slot.skillId).length} 个技能</span>
                      <span>{Object.values(draft.equipment).filter(Boolean).length} 件装备</span>
                      <span>{draft.talents.length}/5 天赋</span>
                    </div>
                  </section>
                  <section className="build-warning-panel">
                    <SectionTitle icon={<CircleAlert size={18} />} title="缺口提示" />
                    {warnings.length === 0 ? <EmptyState text="当前配置没有明显缺口。" /> : warnings.map((item) => <p key={item}>{item}</p>)}
                  </section>
                </div>
              )}

              {activeTab === 'equipment' && (
                <div className="build-equipment-layout">
                  <div className="build-slot-grid">
                    {equipmentSlotOrder().map((slot) => {
                      const item = data.availableEquipment.find((candidate) => candidate.id === draft.equipment[slot]);
                      return (
                        <button
                          key={slot}
                          className={`build-slot-card ${item ? item.quality : 'empty'}`}
                          onClick={() => mutateDraft((current) => ({ ...current, equipment: { ...current.equipment, [slot]: null } }))}
                        >
                          <span>{slotName(slot)}</span>
                          <strong>{item ? equipmentDisplayName(item) : '未配置'}</strong>
                          <small>{item ? `战力 ${formatNumber(itemPower(item))}` : '点击已配置槽可清空'}</small>
                        </button>
                      );
                    })}
                  </div>
                  <div className="build-equipment-pool">
                    {data.availableEquipment.length === 0 && <EmptyState text="当前没有可配置装备。" />}
                    {data.availableEquipment.map((item) => {
                      const slot = buildSlotForItem(item, draft);
                      const selected = Object.values(draft.equipment).includes(item.id);
                      return (
                        <button
                          key={item.id}
                          className={`build-equipment-card ${item.quality} ${selected ? 'selected' : ''}`}
                          onClick={() => mutateDraft((current) => ({ ...current, equipment: assignBuildEquipment(current.equipment, slot, item.id) }))}
                        >
                          <span>{typeName(item.itemType)} · {slotName(slot)}</span>
                          <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
                          <small>{buildEquipmentStatLine(item)}</small>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              {activeTab === 'skills' && (
                <div className="build-skill-layout">
                  <div className="build-skill-slots">
                    {draft.skills.map((slot) => {
                      const skill = data.availableSkills.find((item) => item.id === slot.skillId);
                      return (
                        <article key={slot.slotIndex} className={`build-skill-slot ${skill?.learned ? 'learned' : 'missing'}`}>
                          <div>
                            <span>{triggerName(slot.triggerKind)}</span>
                            <strong>{skill?.name ?? '未配置技能'}</strong>
                            <small>{skill ? `Rank ${skill.rank}/${skill.maxRank} · ${skill.learned ? '已学习' : `Lv.${skill.unlockLevel} 解锁`}` : '选择右侧技能填入'}</small>
                          </div>
                          <div className="build-trigger-row">
                            {['opener', 'default', 'burst', 'execute', 'defensive', 'heal'].map((trigger) => (
                              <button key={trigger} className={slot.triggerKind === trigger ? 'active' : ''} onClick={() => mutateDraft((current) => updateDraftSkill(current, slot.slotIndex, { triggerKind: trigger }))}>
                                {triggerName(trigger)}
                              </button>
                            ))}
                          </div>
                        </article>
                      );
                    })}
                  </div>
                  <div className="build-skill-pool">
                    {data.availableSkills.map((skill) => (
                      <button
                        key={skill.id}
                        className={`build-skill-card ${skill.learned ? 'learned' : 'locked'}`}
                        onClick={() => mutateDraft((current) => assignSkillToFirstSlot(current, skill.id))}
                      >
                        <span>{skill.category} · {skill.archetype}</span>
                        <strong>{skill.name}</strong>
                        <small>{skill.learned ? `Rank ${skill.rank}` : `未学习 · Lv.${skill.unlockLevel}`}</small>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {activeTab === 'talents' && (
                <div className="build-talent-layout">
                  {(sourcePreset?.talents ?? []).length === 0 && <EmptyState text="空白构筑暂无预设天赋，复制职业流派可获得天赋树。" />}
                  {(sourcePreset?.talents ?? []).map((talent) => {
                    const active = draft.talents.includes(talent.nodeId);
                    const disabled = !active && draft.talents.length >= 5;
                    return (
                      <button
                        key={talent.nodeId}
                        className={`build-talent-card ${active ? 'active' : ''}`}
                        disabled={disabled}
                        onClick={() => mutateDraft((current) => toggleTalent(current, talent))}
                      >
                        <span>{talent.statKey} +{talent.statValue}</span>
                        <strong>{talent.name}</strong>
                        <small>{talent.description}</small>
                      </button>
                    );
                  })}
                </div>
              )}

              {activeTab === 'simulate' && (
                <div className="build-simulation-layout">
                  <section className="build-sim-control">
                    <SectionTitle icon={<Skull size={18} />} title="深渊模拟" />
                    <div className="recharge-form">
                      <input inputMode="numeric" value={simulationTier} onChange={(event) => setSimulationTier(event.target.value.replace(/[^\d]/g, ''))} />
                      <button className="primary-action" disabled={!canSimulate} onClick={() => simulateMutation.mutate({ buildId: selectedBuild.id, tier })}>
                        {simulateMutation.isPending ? '模拟中...' : `模拟 T${tier}`}
                      </button>
                    </div>
                    <p>模拟不消耗疲劳、不发奖励、不更新深渊进度，只验证当前构筑战斗计划。</p>
                  </section>
                  {simulationResult ? (
                    <BuildSimulationPanel result={simulationResult} />
                  ) : (
                    <EmptyState text="运行一次模拟后查看战斗事件与评分。" />
                  )}
                </div>
              )}
            </>
          )}
        </section>

        <aside className="detail-rail builds-detail-rail">
          <SectionTitle icon={<Trophy size={18} />} title="评分与应用" />
          {selectedBuild ? (
            <>
              <BuildScorePanel score={selectedBuild.score} compact />
              <div className="build-action-stack">
                <button className="primary-action" disabled={!canSave} onClick={saveDraft}>
                  {saveMutation.isPending ? '保存中...' : '保存构筑'}
                </button>
                <button className="mini-action" disabled={!canActivate} onClick={() => activateMutation.mutate(selectedBuild.id)}>
                  {activateMutation.isPending ? '启用中...' : '启用构筑'}
                </button>
              </div>
              <div className="build-warning-list">
                {warnings.length === 0 ? <EmptyState text="可直接启用。" /> : warnings.map((item) => <p key={item}>{item}</p>)}
              </div>
            </>
          ) : (
            <EmptyState text="暂无可应用构筑。" />
          )}
        </aside>
      </div>

      {(copyMutation.error || createMutation.error || saveMutation.error || activateMutation.error || simulateMutation.error) && (
        <FeedbackDialog
          variant="error"
          title="构筑操作失败"
          message={(copyMutation.error ?? createMutation.error ?? saveMutation.error ?? activateMutation.error ?? simulateMutation.error)?.message ?? '请稍后重试'}
          onClose={() => {
            copyMutation.reset();
            createMutation.reset();
            saveMutation.reset();
            activateMutation.reset();
            simulateMutation.reset();
          }}
        />
      )}
      {activationResult && (
        <FeedbackDialog
          variant="success"
          title="构筑已启用"
          message={`${activationResult.buildName}：装备 ${activationResult.appliedEquipmentCount} 件，技能 ${activationResult.configuredSkillCount} 个，战力 ${formatNumber(activationResult.beforePower)} -> ${formatNumber(activationResult.afterPower)}${activationResult.warnings.length ? `。提示：${activationResult.warnings.join('；')}` : ''}`}
          onClose={() => {
            setActivationResult(null);
            activateMutation.reset();
          }}
        />
      )}
    </section>
  );
}

