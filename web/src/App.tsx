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
  ChatMessage,
  ChatSpeaker,
  Dungeon,
  DungeonRunResult,
  DungeonSweepResult,
  DropPreview,
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
  WealthTierStat,
} from './api';
import { useAppStore } from './store';

type InventoryAction = 'equip' | 'sell' | 'enhance';
type FeedbackVariant = 'success' | 'error';
type DungeonMode = 'normal' | 'special';
type DungeonClearFilter = 'all' | 'uncleared' | 'cleared';
type DungeonRiskFilter = 'all' | 'safe' | 'normal' | 'risky' | 'deadly';
type DungeonLevelFilter = 'all' | '1-30' | '31-60' | '61-90';
type QuestCategoryFilter = 'all' | 'main' | 'daily' | 'achievement';
type LeaderboardMetric = 'power' | 'gold' | 'level';
type ProfessionFilter = 'all' | 'warrior' | 'mage' | 'ranger';
type MarketSortKey = 'listedAt' | 'level' | 'quality';
type CatalogCategoryFilter = 'all' | 'equipment' | 'consumable' | 'material' | 'chest';
type CatalogQualityFilter = 'all' | 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'immortal';
type CatalogLevelFilter = 'all' | '1-10' | '11-30' | '31-60' | '61-90';
type CatalogSortKey = 'quality' | 'level' | 'type';
type MarketItemTypeFilter = 'all' | 'weapon' | 'helmet' | 'armor' | 'legs' | 'boots' | 'gloves' | 'necklace' | 'ring';
type MarketQualityFilter = 'all' | 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'immortal';
type MarketLedgerTab = 'listed' | 'sold';
type RobotFilterKey = 'name' | 'minGold' | 'maxGold' | 'minPower' | 'maxPower' | 'minLevel' | 'maxLevel';
type ForgeView = 'enhance' | 'transfer' | 'socket' | 'refine' | 'craft';
const ANNOUNCEMENT_SEEN_STORAGE_KEY = 'mythic.announcements.seen';

type PowerBreakdownSlice = {
  key: string;
  label: string;
  value: number;
  detail: string;
};

type RobotFilters = Record<RobotFilterKey, string>;
type PlayableProfession = 'warrior' | 'ranger' | 'mage';
type AttributeKey = 'strength' | 'agility' | 'constitution' | 'intelligence' | 'spirit';

type MarketFilters = {
  minLevel: string;
  maxLevel: string;
  itemType: MarketItemTypeFilter;
  quality: MarketQualityFilter;
  sort: MarketSortKey;
};

type EquipmentDetailData = {
  id?: number;
  templateId: string;
  name: string;
  displayName?: string;
  itemType: string;
  itemCategory?: string;
  quality: string;
  requiredLevel: number;
  attackBonus: number;
  defenseBonus: number;
  resistanceBonus: number;
  hpBonus: number;
  mpBonus: number;
  critBonus?: number;
  sellPrice: number;
  quantity?: number;
  stackable?: boolean;
  effectType?: string;
  effectValueJson?: string;
  enhanceBonusRate?: number;
  minEnhanceLevel?: number;
  maxEnhanceLevel?: number;
  enhancementLevel: number;
  enhancementLuck?: number;
  origin?: string;
  power?: number;
};

const ATTRIBUTE_LABELS: { key: AttributeKey; label: string; meaning: string }[] = [
  { key: 'strength', label: '力量', meaning: '物理攻击' },
  { key: 'agility', label: '敏捷', meaning: '暴击与速度' },
  { key: 'constitution', label: '体质', meaning: '生命与防御' },
  { key: 'intelligence', label: '智力', meaning: '法力与法伤' },
  { key: 'spirit', label: '精神', meaning: '续航与技能' },
];

const CREATE_PROFESSIONS: {
  id: PlayableProfession;
  name: string;
  title: string;
  role: string;
  difficulty: string;
  tempo: string;
  survival: string;
  summary: string;
  signature: string;
  attributes: Record<AttributeKey, number>;
  growth: string[];
  combat: string[];
  bestFor: string[];
  caution: string;
}[] = [
  {
    id: 'warrior',
    name: '战士',
    title: '近战守线者',
    role: '近战 / 坦克',
    difficulty: '稳健',
    tempo: '稳步推进',
    survival: '高',
    summary: '抗压最强，容错高，适合先熟悉副本节奏。',
    signature: '力量 + 体质成长',
    attributes: { strength: 10, agility: 5, constitution: 8, intelligence: 3, spirit: 4 },
    growth: ['升级额外获得力量与体质', '生命和防御成长更厚', '初期装备容错最高'],
    combat: ['站得住，适合连续刷普通副本', '面对高压怪物时失误成本低', '输出节奏稳定，爆发不是最高'],
    bestFor: ['第一次玩，想稳稳推进', '喜欢抗伤害和正面硬碰硬', '想少看攻略也能开荒'],
    caution: '清怪速度不如爆发职业，需要靠武器和强化补输出。',
  },
  {
    id: 'ranger',
    name: '射手',
    title: '远程游击手',
    role: '远程物理 / 暴击',
    difficulty: '灵活',
    tempo: '快节奏',
    survival: '中',
    summary: '敏捷最高，暴击成长好，适合刷本和追求效率。',
    signature: '敏捷 + 力量成长',
    attributes: { strength: 6, agility: 10, constitution: 5, intelligence: 4, spirit: 5 },
    growth: ['升级额外获得敏捷与力量', '暴击率随敏捷自然抬升', '更依赖武器和饰品收益'],
    combat: ['打低风险副本效率好', '适合追求掉落和市场周转', '高压副本要留意推荐战力'],
    bestFor: ['喜欢快节奏和暴击数字', '愿意比较装备收益', '想兼顾刷本和市场玩法'],
    caution: '身板较薄，越级挑战时比战士更吃装备。',
  },
  {
    id: 'mage',
    name: '法师',
    title: '奥术爆发者',
    role: '远程魔法 / 爆发',
    difficulty: '进阶',
    tempo: '爆发窗口',
    survival: '低-中',
    summary: '智力和精神最高，伤害上限高，但前期容错最低。',
    signature: '智力 + 精神成长',
    attributes: { strength: 3, agility: 4, constitution: 4, intelligence: 10, spirit: 9 },
    growth: ['升级额外获得智力与精神', '法力值和技能续航更强', '后期爆发和范围能力突出'],
    combat: ['适合愿意经营资源的玩家', '装备成型后清场能力强', '前期需要避免硬吃伤害'],
    bestFor: ['喜欢高爆发和技能流', '愿意研究装备与资源', '能接受前期更脆的开荒'],
    caution: '生命和防御起点低，初期副本更需要看推荐战力。',
  },
];

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
      <div className={`phone-frame${token && screen !== 'auth' ? ' has-global-ticker' : ''}`}>
        {token && screen !== 'auth' && <GlobalTicker announcements={announcementsQuery.data ?? []} />}
        <div className="screen-host">
          {screen === 'auth' && <AuthScreen />}
          {screen === 'create-player' && token && <CreatePlayerScreen token={token} />}
          {screen === 'home' && token && <HomeScreen token={token} />}
          {screen === 'character' && token && <CharacterScreen token={token} />}
          {screen === 'inventory' && token && <InventoryScreen token={token} />}
          {screen === 'item-catalog' && token && <ItemCatalogScreen token={token} />}
          {screen === 'blacksmith' && token && <BlacksmithScreen token={token} />}
          {screen === 'quests' && token && <QuestScreen token={token} />}
          {screen === 'market' && token && <MarketScreen token={token} />}
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

function AuthScreen() {
  const setSession = useAppStore((state) => state.setSession);
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('hero');
  const [password, setPassword] = useState('mythic123');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => (mode === 'login' ? authApi.login(username, password) : authApi.register(username, password)),
    onSuccess: (data) => {
      setError(null);
      setSession(data.token, data.username, data.hasPlayer);
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <section className="screen auth-screen">
      <div className="brand-block">
        <span className="eyebrow">Aetherfall Chronicles</span>
        <h1>MythicRealm</h1>
        <p>服务端权威 H5 迁移版</p>
      </div>

      <div className="segmented">
        <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>登录</button>
        <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>注册</button>
      </div>

      <label>
        账号
        <input value={username} onChange={(event) => setUsername(event.target.value)} />
      </label>
      <label>
        密码
        <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
      </label>

      {error && (
        <FeedbackDialog
          variant="error"
          title={mode === 'login' ? '登录失败' : '注册失败'}
          message={error}
          onClose={() => setError(null)}
        />
      )}

      <button className="primary-action" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
        {mutation.isPending ? '处理中...' : mode === 'login' ? '进入游戏' : '创建账号'}
        <ChevronRight size={18} />
      </button>
    </section>
  );
}

function GlobalTicker({ announcements }: { announcements: GlobalAnnouncement[] }) {
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

function announcementSeenKey(announcement: GlobalAnnouncement) {
  return `${announcement.id}:${announcement.createdAt}`;
}

function readSeenAnnouncementKeys() {
  try {
    const raw = window.localStorage.getItem(ANNOUNCEMENT_SEEN_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return new Set(Array.isArray(parsed) ? parsed.filter((value): value is string => typeof value === 'string') : []);
  } catch {
    return new Set<string>();
  }
}

function writeSeenAnnouncementKeys(keys: Set<string>) {
  try {
    window.localStorage.setItem(ANNOUNCEMENT_SEEN_STORAGE_KEY, JSON.stringify(Array.from(keys).slice(-160)));
  } catch {
    // Ignore storage quota/private mode failures; the ticker still works for the current render.
  }
}

function CreatePlayerScreen({ token }: { token: string }) {
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

function ProfessionGlyph({ profession, size = 24 }: { profession: PlayableProfession; size?: number }) {
  if (profession === 'warrior') {
    return <Swords size={size} />;
  }
  if (profession === 'ranger') {
    return <Gauge size={size} />;
  }
  return <Sparkles size={size} />;
}

function InfoPanel({ title, items }: { title: string; items: string[] }) {
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

function HomeScreen({ token }: { token: string }) {
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
          <EquipmentPanel home={data} onSelect={setSelectedEquipment} compact />
        </aside>
        <section className="home-column">
          <div className="nav-grid">
            <NavTile icon={<Swords size={20} />} title="副本" detail={`${data.config.dungeonCount} 个副本`} onClick={() => setScreen('dungeons')} />
            <NavTile icon={<Backpack size={20} />} title="背包" detail="穿戴 · 出售 · 强化" onClick={() => setScreen('inventory')} />
            <NavTile icon={<Boxes size={20} />} title="物品" detail={`${data.config.itemCount} 种图鉴`} onClick={() => setScreen('item-catalog')} />
            <NavTile icon={<Hammer size={20} />} title="铁匠铺" detail="强化 · 转移" onClick={() => setScreen('blacksmith')} />
            <NavTile icon={<ScrollText size={20} />} title="任务" detail="主线 · 日常 · 成就" onClick={() => setScreen('quests')} />
            <NavTile icon={<ShoppingBag size={20} />} title="市场" detail="寄售 · 购买" onClick={() => setScreen('market')} />
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

function CharacterScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [selectedEquipment, setSelectedEquipment] = useState<Item | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
  });

  if (isLoading) {
    return <LoadingScreen title="读取角色属性" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '角色加载失败'} />;
  }

  const player = data.player;
  const equippedItems = Object.values(data.equippedItems).filter(Boolean);
  const powerBreakdown = powerBreakdownForHome(data);
  const topEquipment = [...equippedItems]
    .sort((left, right) => itemPower(right) - itemPower(left))
    .slice(0, 3);

  return (
    <section className="screen character-screen profile-screen">
      <TopBar title="角色档案" onBack={() => setScreen('home')} />
      <div className="profile-overview">
        <div className="profile-identity">
          <div className="profile-avatar">
            <ProfessionGlyph profession={player.profession as PlayableProfession} size={30} />
          </div>
          <div>
            <span className="eyebrow">Lv.{player.level} · {professionName(player.profession)}</span>
            <h1>{player.name}</h1>
            <p>已穿戴 {equippedItems.length}/{equipmentSlotOrder().length} · 金币 {formatNumber(player.gold)}</p>
          </div>
        </div>
        <div className="profile-power-total">
          <span>总战力</span>
          <strong>{formatNumber(data.combatPower)}</strong>
        </div>
      </div>

      <div className="profile-layout">
        <section className="profile-stat-panel">
          <div className="profile-panel-head">
            <SectionTitle icon={<UserRound size={18} />} title="基础属性" />
            <span>{player.freePoints} 自由点</span>
          </div>
          <div className="profile-metric-grid">
            <Metric label="经验" value={formatNumber(player.experience)} />
            <Metric label="生命" value={formatNumber(data.maxHp)} />
            <Metric label="法力" value={formatNumber(data.maxMp)} />
            <Metric label="力量" value={player.strength.toString()} />
            <Metric label="敏捷" value={player.agility.toString()} />
            <Metric label="体质" value={player.constitution.toString()} />
            <Metric label="智力" value={player.intelligence.toString()} />
            <Metric label="精神" value={player.spirit.toString()} />
          </div>
          <div className="profile-panel-head compact">
            <SectionTitle icon={<Gauge size={18} />} title="战斗属性" />
          </div>
          <StatContributionGrid stats={data.derivedStats} baseStats={data.baseStats} equipmentStats={data.equipmentStats} />
        </section>

        <aside className="profile-power-panel">
          <div className="profile-panel-head">
            <SectionTitle icon={<Gauge size={18} />} title="战力来源" />
            <span>{formatNumber(data.combatPower)}</span>
          </div>
          <PowerBreakdownPanel slices={powerBreakdown} total={data.combatPower} />
          <div className="profile-equipment-source">
            <span>装备估值</span>
            <strong>{formatNumber(data.equipmentPower)}</strong>
            <small>{topEquipment.map((item) => `${typeName(item.itemType)} ${formatNumber(itemPower(item))}`).join(' · ') || '暂无装备'}</small>
          </div>
        </aside>
      </div>

      <EquipmentPanel home={data} onSelect={setSelectedEquipment} />
      {selectedEquipment && <ItemDetail item={toEquipmentDetail(selectedEquipment)} onClose={() => setSelectedEquipment(null)} />}
    </section>
  );
}

function ItemCatalogScreen({ token }: { token: string }) {
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
      <div className="catalog-summary-strip">
        <Metric label="全部" value={summary.total.toString()} />
        <Metric label="装备" value={summary.equipment.toString()} />
        <Metric label="消耗品" value={summary.consumable.toString()} />
        <Metric label="材料" value={summary.material.toString()} />
        <Metric label="宝箱" value={summary.chest.toString()} />
        <Metric label="最高等级" value={`Lv.${summary.maxLevel}`} />
      </div>

      <div className="item-catalog-workbench">
        <aside className="catalog-filter-panel">
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

        <section className="catalog-list-panel">
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

function DungeonScreen({ token, onResult }: { token: string; onResult: (result: DungeonRunResult) => void }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<DungeonMode>('normal');
  const [clearFilter, setClearFilter] = useState<DungeonClearFilter>('all');
  const [riskFilter, setRiskFilter] = useState<DungeonRiskFilter>('all');
  const [levelFilter, setLevelFilter] = useState<DungeonLevelFilter>('all');
  const [selectedSpecialId, setSelectedSpecialId] = useState<string | null>(null);
  const [sweepResult, setSweepResult] = useState<DungeonSweepResult | null>(null);
  const [selectedLoot, setSelectedLoot] = useState<Item | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['dungeons', token],
    queryFn: () => gameApi.dungeons(token),
    staleTime: 0,
    refetchOnMount: 'always',
  });
  const homeQuery = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
    staleTime: 0,
    refetchOnMount: 'always',
  });
  const mutation = useMutation({
    mutationFn: (dungeonId: string) => gameApi.runDungeon(token, dungeonId),
    onSuccess: async (result) => {
      await invalidateGameQueries(queryClient, token);
      onResult(result);
    },
  });
  const sweepMutation = useMutation({
    mutationFn: (dungeonId: string) => gameApi.sweepDungeon(token, dungeonId, 10),
    onSuccess: async (result) => {
      setSweepResult(result);
      await invalidateGameQueries(queryClient, token);
    },
  });

  function refreshDungeonLobby() {
    void queryClient.invalidateQueries({ queryKey: ['dungeons', token] });
    void queryClient.invalidateQueries({ queryKey: ['home', token] });
  }

  function switchDungeonMode(nextMode: DungeonMode) {
    setMode(nextMode);
    refreshDungeonLobby();
  }

  if (isLoading) {
    return <LoadingScreen title="读取副本情报" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '副本加载失败'} />;
  }
  const combatPower = homeQuery.data?.combatPower ?? 0;
  const playerLevel = homeQuery.data?.player.level ?? 0;
  const stamina = homeQuery.data?.stamina ?? data.find((dungeon) => dungeon.stamina)?.stamina;
  const normalDungeons = data.filter((dungeon) => !isSpecialDungeon(dungeon));
  const specialDungeons = data.filter(isSpecialDungeon);
  const selectedSpecialDungeon = specialDungeons.find((dungeon) => dungeon.id === selectedSpecialId) ?? specialDungeons[0] ?? null;
  const visibleDungeons = normalDungeons.filter((dungeon) => {
    const risk = dungeonRisk(combatPower, dungeon.minimumPower, playerLevel, dungeon.minimumLevel, dungeon.gate).level as DungeonRiskFilter;
    const matchesClear = clearFilter === 'all' || (clearFilter === 'cleared' ? dungeon.cleared : !dungeon.cleared);
    const matchesRisk = riskFilter === 'all' || risk === riskFilter;
    const matchesLevel = dungeonMatchesLevelFilter(dungeon, levelFilter);
    return matchesClear && matchesRisk && matchesLevel;
  });

  return (
    <section className="screen dungeon-screen">
      <TopBar title="副本大厅" onBack={() => setScreen('home')} />
      {homeQuery.data && (
        <div className="battle-brief">
          <Gauge size={20} />
          <div>
            <span className="eyebrow">当前战力</span>
            <strong>{homeQuery.data.combatPower}</strong>
          </div>
        </div>
      )}
      <StaminaPanel stamina={stamina} />
      <div className="dungeon-mode-tabs">
        <button className={mode === 'normal' ? 'active' : ''} onClick={() => switchDungeonMode('normal')}>
          <Swords size={16} />
          正常副本
          <small>{normalDungeons.length}</small>
        </button>
        <button className={mode === 'special' ? 'active bloodmoon' : 'bloodmoon'} onClick={() => switchDungeonMode('special')}>
          <Sparkles size={16} />
          特殊副本
          <small>{specialDungeons.length}</small>
        </button>
      </div>
      {mode === 'normal' ? (
        <>
          <div className="dungeon-filter-panel">
            <div className="segmented three">
              {[
                ['all', '全部'],
                ['uncleared', '未通过'],
                ['cleared', '已通过'],
              ].map(([value, label]) => (
                <button key={value} className={clearFilter === value ? 'active' : ''} onClick={() => {
                  setClearFilter(value as DungeonClearFilter);
                  refreshDungeonLobby();
                }}>
                  {label}
                </button>
              ))}
            </div>
            <div className="risk-filter-row">
              {[
                ['all', '全部风险'],
                ['safe', '碾压'],
                ['normal', '稳妥'],
                ['risky', '危险'],
                ['deadly', '极危'],
              ].map(([value, label]) => (
                <button key={value} className={riskFilter === value ? `active ${value}` : value} onClick={() => {
                  setRiskFilter(value as DungeonRiskFilter);
                  refreshDungeonLobby();
                }}>
                  {label}
                </button>
              ))}
            </div>
            <div className="level-filter-row">
              {[
                ['all', '全部等级'],
                ['1-30', 'Lv.1-30'],
                ['31-60', 'Lv.31-60'],
                ['61-90', 'Lv.61-90'],
              ].map(([value, label]) => (
                <button key={value} className={levelFilter === value ? 'active' : ''} onClick={() => {
                  setLevelFilter(value as DungeonLevelFilter);
                  refreshDungeonLobby();
                }}>
                  {label}
                </button>
              ))}
            </div>
            <strong>{visibleDungeons.length}/{normalDungeons.length} 个副本</strong>
          </div>
          <div className="dungeon-list">
            {visibleDungeons.length === 0 && <EmptyState text="当前筛选下没有副本，换个风险档再看。" />}
            {visibleDungeons.map((dungeon) => (
              <DungeonCard
                key={dungeon.id}
                dungeon={dungeon}
                combatPower={combatPower}
                playerLevel={playerLevel}
                loading={mutation.isPending}
                sweepLoading={sweepMutation.isPending}
                onRun={() => mutation.mutate(dungeon.id)}
                onSweep={() => sweepMutation.mutate(dungeon.id)}
              />
            ))}
          </div>
        </>
      ) : (
        <SpecialDungeonPanel
          dungeons={specialDungeons}
          selectedDungeon={selectedSpecialDungeon}
          combatPower={combatPower}
          playerLevel={playerLevel}
          loading={mutation.isPending}
          onSelect={setSelectedSpecialId}
          onRun={(dungeonId) => mutation.mutate(dungeonId)}
        />
      )}
      {mutation.error && (
        <FeedbackDialog
          variant="error"
          title="进入副本失败"
          message={mutation.error.message}
          onClose={() => mutation.reset()}
        />
      )}
      {sweepMutation.error && (
        <FeedbackDialog
          variant="error"
          title="扫荡失败"
          message={sweepMutation.error.message}
          onClose={() => sweepMutation.reset()}
        />
      )}
      {sweepResult && <SweepSummaryModal result={sweepResult} onClose={() => setSweepResult(null)} onSelectLoot={setSelectedLoot} />}
      {selectedLoot && <ItemDetail item={toEquipmentDetail(selectedLoot)} onClose={() => setSelectedLoot(null)} />}
    </section>
  );
}

function SpecialDungeonPanel({ dungeons, selectedDungeon, combatPower, playerLevel, loading, onSelect, onRun }: {
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

function ResultScreen({ result, onResult }: { result: DungeonRunResult; onResult: (result: DungeonRunResult) => void }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const token = useAppStore((state) => state.token);
  const [speed, setSpeed] = useState(1);
  const frames = useMemo(() => battleFramesForResult(result), [result]);
  const [visibleFrames, setVisibleFrames] = useState(1);
  const [selectedLoot, setSelectedLoot] = useState<Item | null>(null);
  const [showSummary, setShowSummary] = useState(false);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const battleStageRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setVisibleFrames(1);
    setSelectedLoot(null);
    setShowSummary(false);
    setShowLeaveConfirm(false);
  }, [result]);

  useEffect(() => {
    if (visibleFrames >= frames.length) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setVisibleFrames((current) => Math.min(frames.length, current + 1));
    }, speed === 4 ? 160 : speed === 2 ? 320 : 620);
    return () => window.clearTimeout(timeout);
  }, [frames.length, speed, visibleFrames]);

  useEffect(() => {
    const node = battleStageRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [visibleFrames]);

  useEffect(() => {
    if (visibleFrames >= frames.length) {
      setShowSummary(true);
    }
  }, [frames.length, visibleFrames]);

  const battlePlaying = visibleFrames < frames.length;

  async function returnToDungeons() {
    if (token) {
      await invalidateGameQueries(queryClient, token);
    }
    setScreen('dungeons');
  }

  function handleBack() {
    if (battlePlaying) {
      setShowLeaveConfirm(true);
      return;
    }
    void returnToDungeons();
  }

  const retryMutation = useMutation({
    mutationFn: () => {
      if (!token) {
        throw new Error('登录已失效，请重新登录');
      }
      return gameApi.runDungeon(token, result.dungeonId);
    },
    onSuccess: async (nextResult) => {
      onResult(nextResult);
      if (token) {
        await invalidateGameQueries(queryClient, token);
      }
    },
  });

  const currentFrame = frames[Math.min(visibleFrames - 1, frames.length - 1)];
  const currentEnemy = currentFrame.enemyName ?? (result.success ? '区域已肃清' : '推进中断');

  return (
    <section className="screen result-screen combat-screen">
      <TopBar title="副本战报" onBack={handleBack} />

      <aside className="combat-side">
        <div className={`result-banner ${result.success ? 'win' : 'lose'}`}>
          <span>{result.success ? '通关' : '撤退'} · 评价 {result.rating}</span>
          <h1>{result.dungeonName}</h1>
          <p>击败 {result.monstersKilled} 只魔物，获得 {result.expGained} 经验 / {result.goldGained} 金</p>
        </div>

        <div className="combat-hud">
          <CombatantCard
            icon={<HeartPulse size={18} />}
            label="我方"
            title={result.player.name}
            detail={`Lv.${result.player.level} ${professionName(result.player.profession)} · 战力 ${result.combatPower}`}
            hp={currentFrame.playerHp}
            maxHp={currentFrame.playerMaxHp || result.playerMaxHp}
          />
          <CombatantCard
            icon={<Skull size={18} />}
            label={currentFrame.roomLabel ?? '当前目标'}
            title={currentEnemy}
            detail={currentFrame.enemyMaxHp > 0 ? `剩余 ${currentFrame.enemyHp}/${currentFrame.enemyMaxHp}` : '等待下一波'}
            hp={currentFrame.enemyHp}
            maxHp={currentFrame.enemyMaxHp}
            enemy
          />
        </div>

        <div className="speed-bar">
          <button className={speed === 1 ? 'active' : ''} onClick={() => setSpeed(1)}>1x</button>
          <button className={speed === 2 ? 'active' : ''} onClick={() => setSpeed(2)}>2x</button>
          <button className={speed === 4 ? 'active' : ''} onClick={() => setSpeed(4)}><FastForward size={15} />4x</button>
          <button onClick={() => setVisibleFrames(frames.length)}><SkipForward size={15} />全部</button>
        </div>

        <div className="combat-reward-panel">
          <Metric label="推荐战力" value={(result.recommendedPower || 0).toString()} />
          <Metric label="战报进度" value={`${visibleFrames}/${frames.length}`} />
          <Metric label="掉落装备" value={result.loot.length.toString()} />
        </div>

        <div className="loot-grid compact-loot">
          {result.loot.length === 0 && <EmptyState text="本次没有获得装备。" />}
          {result.loot.slice(0, 4).map((item) => (
            <button key={item.id} className={`loot-card ${item.quality}`} onClick={() => setSelectedLoot(item)}>
              <Gem size={18} />
              <span>{qualityName(item.quality)} · {typeName(item.itemType)}</span>
              <strong>{equipmentDisplayName(item)}</strong>
            </button>
          ))}
        </div>
      </aside>

      <div className="battle-stage" ref={battleStageRef}>
        <div className="battle-stage-header">
          <span>{currentFrame.roomLabel ?? '战斗记录'}</span>
          <strong>{currentEnemy}</strong>
        </div>
        {frames.slice(0, visibleFrames).map((frame) => (
          <div key={`${frame.index}-${frame.text}`} className={`battle-line ${frame.tone || battleLogTone(frame.text)} ${frame.eventType ?? ''}`}>
            <span>#{frame.index}</span>
            <small>{battleEventName(frame)}</small>
            <p>{frame.text}</p>
          </div>
        ))}
      </div>
      {showSummary && (
        <ResultSummaryModal
          result={result}
          onClose={() => setShowSummary(false)}
          onReturn={returnToDungeons}
          onRetry={!result.success ? () => retryMutation.mutate() : undefined}
          retrying={retryMutation.isPending}
          onSelectLoot={setSelectedLoot}
        />
      )}
      {showLeaveConfirm && (
        <ConfirmDialog
          title="战斗仍在展示"
          message="现在返回会跳过剩余战报和掉落复盘，确定回到副本大厅吗？"
          confirmLabel="返回大厅"
          cancelLabel="继续查看"
          danger
          onCancel={() => setShowLeaveConfirm(false)}
          onConfirm={() => {
            setShowLeaveConfirm(false);
            void returnToDungeons();
          }}
        />
      )}
      {selectedLoot && <ItemDetail item={toEquipmentDetail(selectedLoot)} onClose={() => setSelectedLoot(null)} />}
      {retryMutation.error && (
        <FeedbackDialog
          variant="error"
          title="重试失败"
          message={retryMutation.error.message}
          onClose={() => retryMutation.reset()}
        />
      )}
    </section>
  );
}

function InventoryScreen({ token }: { token: string }) {
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

      <div className="inventory-workbench">
        <aside className="inventory-side">
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

        <section className="inventory-main">
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

        <aside className="inventory-tools-panel">
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

function BlacksmithScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [activeView, setActiveView] = useState<ForgeView>('enhance');
  const [focusedItemId, setFocusedItemId] = useState<number | null>(null);
  const [detailItem, setDetailItem] = useState<Item | null>(null);
  const [enhanceItem, setEnhanceItem] = useState<Item | null>(null);
  const [enhanceMessage, setEnhanceMessage] = useState<string | null>(null);
  const [selectedStoneIds, setSelectedStoneIds] = useState<number[]>([]);
  const [sourceItemId, setSourceItemId] = useState<number | null>(null);
  const [targetItemId, setTargetItemId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['inventory', token],
    queryFn: () => gameApi.inventory(token),
  });
  const allEquipment = useMemo(() => {
    if (!data) {
      return [];
    }
    return sortItems([...Object.values(data.equippedItems), ...data.inventory].filter(isEquipmentItem), 'quality');
  }, [data]);
  const sourceItems = useMemo(() => allEquipment.filter((item) => item.enhancementLevel > 0), [allEquipment]);
  const targetItems = useMemo(() => allEquipment.filter((item) => item.id !== sourceItemId), [allEquipment, sourceItemId]);
  const selectedSource = allEquipment.find((item) => item.id === sourceItemId) ?? null;
  const selectedTarget = targetItems.find((item) => item.id === targetItemId) ?? null;
  const focusedItem = focusedItemId ? allEquipment.find((item) => item.id === focusedItemId) ?? null : null;
  const canTransfer = Boolean(
    selectedSource
    && selectedTarget
    && selectedSource.id !== selectedTarget.id
    && selectedSource.enhancementLevel > selectedTarget.enhancementLevel,
  );

  useEffect(() => {
    if (!data) {
      return;
    }
    if (sourceItemId && !sourceItems.some((item) => item.id === sourceItemId)) {
      setSourceItemId(null);
    }
    if (targetItemId && !targetItems.some((item) => item.id === targetItemId)) {
      setTargetItemId(null);
    }
    if (focusedItemId && !allEquipment.some((item) => item.id === focusedItemId)) {
      setFocusedItemId(null);
    }
    if (detailItem && !allEquipment.some((item) => item.id === detailItem.id)) {
      setDetailItem(null);
    }
  }, [allEquipment, data, detailItem, focusedItemId, sourceItemId, sourceItems, targetItemId, targetItems]);

  const enhanceMutation = useMutation({
    mutationFn: ({ itemId, stoneItemIds }: { itemId: number; stoneItemIds: number[] }) => gameApi.enhance(token, itemId, stoneItemIds),
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setSelectedStoneIds([]);
      setEnhanceMessage(result.success ? '强化成功，装备属性已提升。' : '强化失败，幸运值提升，下次成功率提高。');
      const refreshed = [...result.inventory.inventory, ...Object.values(result.inventory.equippedItems)].find((item) => item.id === enhanceItem?.id);
      if (refreshed) {
        setEnhanceItem(refreshed);
        setFocusedItemId(refreshed.id);
        if (detailItem?.id === refreshed.id) {
          setDetailItem(refreshed);
        }
      }
      await invalidateGameQueries(queryClient, token);
    },
  });
  const transferMutation = useMutation({
    mutationFn: () => {
      if (sourceItemId == null || targetItemId == null) {
        throw new Error('请选择来源装备和目标装备');
      }
      if (sourceItemId === targetItemId) {
        throw new Error('来源装备和目标装备不能相同');
      }
      if (!selectedSource || !selectedTarget) {
        throw new Error('请选择有效的来源装备和目标装备');
      }
      if (selectedTarget.enhancementLevel >= selectedSource.enhancementLevel) {
        throw new Error('目标装备强化等级必须低于来源装备');
      }
      return gameApi.transferEnhancement(token, sourceItemId, targetItemId);
    },
    onMutate: () => {
      setNotice(null);
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(['inventory', token], result.inventory);
      setSourceItemId(null);
      setTargetItemId(result.targetItem.id);
      setNotice(`已继承到 ${equipmentDisplayName(result.targetItem)}`);
      await invalidateGameQueries(queryClient, token);
    },
  });

  if (isLoading) {
    return <LoadingScreen title="点燃锻炉" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '铁匠铺加载失败'} />;
  }

  const busy = enhanceMutation.isPending || transferMutation.isPending;
  const blacksmithError = enhanceMutation.error?.message ?? transferMutation.error?.message;
  const feedback = blacksmithError
    ? { variant: 'error' as const, title: '操作失败', message: blacksmithError }
    : notice
      ? { variant: 'success' as const, title: '操作完成', message: notice }
      : null;

  function closeFeedback() {
    setNotice(null);
    enhanceMutation.reset();
    transferMutation.reset();
  }

  function selectTransferSource(item: Item) {
    setSourceItemId(item.id);
    setNotice(null);
    const currentTarget = allEquipment.find((equipment) => equipment.id === targetItemId);
    if (!currentTarget || currentTarget.id === item.id || currentTarget.enhancementLevel >= item.enhancementLevel) {
      setTargetItemId(null);
    }
  }

  const enhanceableCount = allEquipment.filter((item) => item.enhancementLevel < 15).length;
  const transferTargetCount = selectedSource
    ? targetItems.filter((item) => item.enhancementLevel < selectedSource.enhancementLevel).length
    : targetItems.length;
  const forgeViews: Array<{ id: ForgeView; title: string; detail: string; icon: ReactNode; badge: string }> = [
    { id: 'enhance', title: '装备强化', detail: '提升基础属性与战力', icon: <Hammer size={18} />, badge: `${enhanceableCount}` },
    { id: 'transfer', title: '强化转移', detail: '把高强化继承到低强化装备', icon: <Repeat2 size={18} />, badge: `${sourceItems.length}` },
    { id: 'socket', title: '宝石镶嵌', detail: '预留功能位', icon: <Gem size={18} />, badge: 'soon' },
    { id: 'refine', title: '属性洗练', detail: '预留功能位', icon: <Sparkles size={18} />, badge: 'soon' },
    { id: 'craft', title: '装备打造', detail: '预留功能位', icon: <Package size={18} />, badge: 'soon' },
  ];
  const activeForge = forgeViews.find((view) => view.id === activeView) ?? forgeViews[0];

  return (
    <section className="screen blacksmith-screen forge-screen">
      <TopBar title="铁匠铺" onBack={() => setScreen('home')} />
      <div className="forge-status-strip">
        <div className="forge-status-main">
          <div className="forge-emblem">
            <Hammer size={24} />
          </div>
          <div>
            <span className="eyebrow">工坊状态</span>
            <h1>装备工坊</h1>
          </div>
        </div>
        <Metric label="金币" value={formatNumber(data.gold)} />
        <Metric label="装备" value={allEquipment.length.toString()} />
        <Metric label="可转移" value={sourceItems.length.toString()} />
      </div>

      <div className="blacksmith-workbench forge-workbench">
        <aside className="forge-sidebar">
          <div className="forge-master">
            <span className="eyebrow">功能台</span>
            <strong>{activeForge.title}</strong>
          </div>
          <nav className="forge-nav">
            {forgeViews.map((view) => (
              <button
                key={view.id}
                className={`forge-nav-button ${activeView === view.id ? 'active' : ''}`}
                onClick={() => setActiveView(view.id)}
              >
                <span className="forge-nav-icon">{view.icon}</span>
                <span>
                  <strong>{view.title}</strong>
                  <small>{view.detail}</small>
                </span>
                <em>{view.badge}</em>
              </button>
            ))}
          </nav>
        </aside>

        <section className="forge-workspace">
          <div className="forge-workspace-head">
            <div>
              <span className="eyebrow">当前界面</span>
              <h2>{activeForge.title}</h2>
            </div>
            <div className="forge-pill-row">
              <span>{formatNumber(data.gold)} 金</span>
              {activeView === 'transfer' && <span>{transferTargetCount} 个目标</span>}
            </div>
          </div>

          {activeView === 'enhance' && (
            <div className="forge-enhance-view">
              <section className="forge-list-panel">
                <div className="inventory-main-title">
                  <SectionTitle icon={<Hammer size={18} />} title="强化清单" />
                  <strong>{enhanceableCount} 件可强化</strong>
                </div>
                <div className="item-grid blacksmith-item-grid forge-equipment-grid">
                  {allEquipment.length === 0 && <EmptyState text="当前没有可强化装备。" />}
                  {allEquipment.map((item) => (
                    <ItemCard
                      key={item.id}
                      item={item}
                      label={itemLocationLabel(item, data.equippedItems)}
                      onSelect={() => setFocusedItemId(item.id)}
                    >
                      <button className="mini-action" disabled={busy || item.enhancementLevel >= 15} onClick={(event) => {
                        event.stopPropagation();
                        setFocusedItemId(item.id);
                        setEnhanceMessage(null);
                        setSelectedStoneIds([]);
                        setEnhanceItem(item);
                      }}>
                        {item.enhancementLevel >= 15 ? '满级' : '强化'}
                      </button>
                    </ItemCard>
                  ))}
                </div>
              </section>
              <aside className="forge-detail-panel">
                <CompareCard
                  title="当前选择"
                  item={focusedItem}
                  highlight
                  emptyTitle="未选择"
                  emptyText="从左侧装备清单选择一件装备。"
                  emptyMeta="等待选择"
                />
                {focusedItem ? (
                  <>
                    <div className="forge-stat-grid">
                      <Metric label="强化等级" value={`+${focusedItem.enhancementLevel}`} />
                      <Metric label="成功率" value={`${Math.round(enhanceChance(focusedItem) * 100)}%`} />
                      <Metric label="强化费用" value={`${formatNumber(enhanceCost(focusedItem))} 金`} />
                      <Metric label="幸运值" value={(focusedItem.enhancementLuck ?? 0).toString()} />
                    </div>
                    <div className="forge-detail-actions">
                      <button
                        className="mini-action subtle"
                        onClick={() => setDetailItem(focusedItem)}
                      >
                        查看详情
                      </button>
                      <button
                        className="primary-action"
                        disabled={busy || focusedItem.enhancementLevel >= 15}
                        onClick={() => {
                          setEnhanceMessage(null);
                          setSelectedStoneIds([]);
                          setEnhanceItem(focusedItem);
                        }}
                      >
                        {focusedItem.enhancementLevel >= 15 ? '已达上限' : '开始强化'}
                      </button>
                    </div>
                  </>
                ) : (
                  <EmptyState text="未选择装备。" />
                )}
              </aside>
            </div>
          )}

          {activeView === 'transfer' && (
            <div className="forge-transfer-view">
              <section className="transfer-column">
                <div className="transfer-column-head">
                  <h3>来源装备</h3>
                  <strong>{sourceItems.length}</strong>
                </div>
                <div className="transfer-list">
                  {sourceItems.length === 0 && <EmptyState text="暂无带强化等级的装备。" />}
                  {sourceItems.map((item) => (
                    <TransferItemOption
                      key={item.id}
                      item={item}
                      selected={item.id === sourceItemId}
                      onSelect={() => selectTransferSource(item)}
                    />
                  ))}
                </div>
              </section>
              <section className="transfer-column">
                <div className="transfer-column-head">
                  <h3>目标装备</h3>
                  <strong>{transferTargetCount}</strong>
                </div>
                <div className="transfer-list">
                  {!selectedSource && <EmptyState text="先选择来源装备。" />}
                  {selectedSource && targetItems.length === 0 && <EmptyState text="暂无可继承的目标装备。" />}
                  {selectedSource && targetItems.map((item) => (
                    <TransferItemOption
                      key={item.id}
                      item={item}
                      selected={item.id === targetItemId}
                      disabled={item.id === sourceItemId || item.enhancementLevel >= selectedSource.enhancementLevel}
                      onSelect={() => setTargetItemId(item.id)}
                    />
                  ))}
                </div>
              </section>
              <aside className="forge-transfer-preview">
                <div className="transfer-preview">
                  <CompareCard
                    title="来源装备"
                    item={selectedSource}
                    emptyTitle="未选择"
                    emptyText="选择带强化等级的装备作为来源。"
                    emptyMeta="无来源"
                  />
                  <CompareCard
                    title="继承目标"
                    item={selectedTarget}
                    highlight
                    emptyTitle="未选择"
                    emptyText="目标装备不能与来源装备相同。"
                    emptyMeta="无目标"
                  />
                </div>
                {selectedSource && selectedTarget && !canTransfer && <div className="modal-warning">目标装备强化等级必须低于来源装备。</div>}
                {sourceItemId != null && targetItemId != null && sourceItemId === targetItemId && <div className="modal-warning">来源装备和目标装备不能相同。</div>}
                <div className="result-modal-actions">
                  <button className="primary-action" disabled={busy || !canTransfer} onClick={() => transferMutation.mutate()}>
                    {transferMutation.isPending ? '转移中...' : '开始转移'}
                  </button>
                </div>
              </aside>
            </div>
          )}

          {activeView !== 'enhance' && activeView !== 'transfer' && (
            <div className="forge-coming-soon">
              <div className="forge-emblem large">
                {activeForge.icon}
              </div>
              <h2>{activeForge.title}</h2>
              <p>即将开放</p>
            </div>
          )}
        </section>
      </div>
      {detailItem && <ItemDetail item={toEquipmentDetail(detailItem)} onClose={() => setDetailItem(null)} />}
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
      {feedback && (
        <FeedbackDialog
          variant={feedback.variant}
          title={feedback.title}
          message={feedback.message}
          onClose={closeFeedback}
        />
      )}
    </section>
  );
}

function QuestScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [category, setCategory] = useState<QuestCategoryFilter>('all');
  const [selectedQuestId, setSelectedQuestId] = useState<string | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['quests', token],
    queryFn: () => gameApi.quests(token),
  });
  const claimMutation = useMutation({
    mutationFn: (questId: string) => gameApi.claimQuest(token, questId),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
    },
  });
  const sortedQuests = useMemo(() => {
    if (!data) {
      return [];
    }
    return [...data].sort((left, right) => questSortScore(right) - questSortScore(left));
  }, [data]);
  const filteredQuests = useMemo(
    () => sortedQuests.filter((quest) => category === 'all' || quest.category === category),
    [category, sortedQuests],
  );
  const selectedQuest = filteredQuests.find((quest) => quest.id === selectedQuestId) ?? filteredQuests[0] ?? null;

  if (isLoading) {
    return <LoadingScreen title="读取任务档案" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '任务加载失败'} />;
  }

  return (
    <section className="screen quest-screen">
      <TopBar title="任务" onBack={() => setScreen('home')} />
      <div className="quest-tabs">
        <Metric label="可领取" value={data.filter((quest) => quest.status === 'completed').length.toString()} />
        <Metric label="进行中" value={data.filter((quest) => quest.status === 'active').length.toString()} />
        <Metric label="已领取" value={data.filter((quest) => quest.status === 'claimed').length.toString()} />
      </div>
      <div className="quest-workbench">
        <aside className="quest-category-panel">
          {(['all', 'main', 'daily', 'achievement'] as QuestCategoryFilter[]).map((value) => {
            const count = value === 'all' ? data.length : data.filter((quest) => quest.category === value).length;
            return (
              <button key={value} className={category === value ? 'active' : ''} onClick={() => setCategory(value)}>
                <span>{value === 'all' ? '全部' : categoryName(value)}</span>
                <strong>{count}</strong>
              </button>
            );
          })}
        </aside>
        <section className="quest-list-panel">
          <div className="inventory-main-title">
            <SectionTitle icon={<ScrollText size={18} />} title={`${category === 'all' ? '全部' : categoryName(category)}任务`} />
            <strong>{filteredQuests.length}</strong>
          </div>
          <div className="quest-list">
            {filteredQuests.length === 0 && <EmptyState text="当前分类没有任务。" />}
            {filteredQuests.map((quest) => (
              <QuestCard
                key={quest.id}
                quest={quest}
                selected={selectedQuest?.id === quest.id}
                loading={claimMutation.isPending}
                onSelect={() => setSelectedQuestId(quest.id)}
                onClaim={() => claimMutation.mutate(quest.id)}
                onNavigate={() => setScreen(screenForQuestTarget(quest.navigationTarget))}
              />
            ))}
          </div>
        </section>
        <QuestDetailPanel
          quest={selectedQuest}
          loading={claimMutation.isPending}
          onClaim={(questId) => claimMutation.mutate(questId)}
          onNavigate={(target) => setScreen(screenForQuestTarget(target))}
        />
      </div>
      {claimMutation.error && (
        <FeedbackDialog
          variant="error"
          title="领取失败"
          message={claimMutation.error.message}
          onClose={() => claimMutation.reset()}
        />
      )}
    </section>
  );
}

function MarketScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [priceByItem, setPriceByItem] = useState<Record<number, string>>({});
  const [selectedMarketItem, setSelectedMarketItem] = useState<EquipmentDetailData | null>(null);
  const [marketFilters, setMarketFilters] = useState<MarketFilters>(emptyMarketFilters());
  const [marketLedgerTab, setMarketLedgerTab] = useState<MarketLedgerTab>('listed');
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

  const listMutation = useMutation({
    mutationFn: ({ item, price }: { item: Item; price: number }) => gameApi.listItem(token, item.id, price),
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
  const inventoryItems = inventoryQuery.data.inventory.filter(isEquipmentItem).slice(0, 10);
  const marketActionError = listMutation.error?.message ?? buyMutation.error?.message ?? cancelMutation.error?.message;
  const hasMarketFilters = marketFilters.minLevel.trim().length > 0
    || marketFilters.maxLevel.trim().length > 0
    || marketFilters.itemType !== 'all'
    || marketFilters.quality !== 'all'
    || marketFilters.sort !== 'listedAt';
  return (
    <section className="screen market-screen">
      <TopBar title="冒险者商会" onBack={() => setScreen('home')} />
      <div className="stat-grid market-stats">
        <Metric label="金币" value={homeQuery.data.player.gold.toString()} />
        <Metric label="在线商贩" value={market.onlineTraders.toString()} />
        <Metric label="机器人挂单" value={market.robotListings.toString()} />
        <Metric label="近时成交" value={market.soldRecently.toString()} />
        <Metric label="我的寄售" value={market.playerListings.toString()} />
        <Metric label="均价" value={`${market.averagePrice} 金`} />
        <Metric label="背包" value={inventoryQuery.data.inventory.length.toString()} />
      </div>
      <div className="market-workbench">
        <aside className="market-sell-panel">
          <SectionTitle icon={<Package size={18} />} title="我的可寄售装备" />
          <div className="market-rule-card">
            <strong>商会规则</strong>
            <p>{market.rules.antiExploit}</p>
          </div>
          <div className="market-sales-panel">
            <div className="market-sales-head">
              <SectionTitle icon={<Coins size={18} />} title="我的寄售" />
              <strong>{marketLedgerTab === 'listed' ? playerActiveListings.length : playerSales.length} 单</strong>
            </div>
            <div className="market-ledger-tabs">
              {[
                ['listed', '已上架'],
                ['sold', '已成交'],
              ].map(([value, label]) => (
                <button key={value} className={marketLedgerTab === value ? 'active' : ''} onClick={() => setMarketLedgerTab(value as MarketLedgerTab)}>
                  {label}
                </button>
              ))}
            </div>
            <div className="market-sale-list">
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
          <div className="market-inventory-list">
            {inventoryItems.length === 0 && <EmptyState text="背包没有可寄售装备。" />}
            {inventoryItems.map((item) => {
              const defaultPrice = marketPriceEstimate(item);
              const price = Number(priceByItem[item.id] || defaultPrice);
              return (
                <ItemCard key={item.id} item={item} onSelect={() => setSelectedMarketItem(toEquipmentDetail(item))}>
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
                      listMutation.mutate({ item, price });
                    }}
                  >
                    上架
                  </button>
                </ItemCard>
              );
            })}
          </div>
        </aside>

        <section className="market-board">
          <div className="market-board-head">
            <SectionTitle icon={<ShoppingBag size={18} />} title="商会看板" />
            <strong>{visibleMarketListings.length}/{market.listings.length} 件</strong>
          </div>
          <div className="market-filter-panel">
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
              <ListingCard
                key={listing.id}
                listing={listing}
                own={listing.playerListing && listing.sellerName === playerName}
                loading={buyMutation.isPending || cancelMutation.isPending}
                onBuy={() => buyMutation.mutate(listing.id)}
                onCancel={() => cancelMutation.mutate(listing.id)}
                onInspect={() => setSelectedMarketItem(marketItemToDetail(listing))}
              />
            ))}
          </div>
        </section>

        <aside className="market-activity-panel">
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

function RobotActivityScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [selectedRobot, setSelectedRobot] = useState<RobotActivityView | null>(null);
  const [filters, setFilters] = useState<RobotFilters>({
    name: '',
    minGold: '',
    maxGold: '',
    minPower: '',
    maxPower: '',
    minLevel: '',
    maxLevel: '',
  });
  const { data, isLoading, error } = useQuery({
    queryKey: ['robot-activity', token],
    queryFn: () => gameApi.robotActivity(token),
    refetchInterval: 5_000,
  });
  const robots = data?.robots ?? [];
  const filteredRobots = useMemo(() => filterRobots(robots, filters), [robots, filters]);
  const hasRobotFilters = Object.values(filters).some((value) => value.trim().length > 0);

  if (isLoading) {
    return <LoadingScreen title="读取机器人后台" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '机器人动态加载失败'} />;
  }

  const updateFilter = (key: RobotFilterKey, value: string) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };
  const activeCount = filteredRobots.filter((robot) => robot.currentActivityKind !== 'rest').length;
  const marketCount = filteredRobots.filter((robot) => robot.currentActivityKind.startsWith('market')).length;
  const totalGold = filteredRobots.reduce((sum, robot) => sum + robot.gold, 0);
  const totalRealMoney = filteredRobots.reduce((sum, robot) => sum + robot.realMoney, 0);
  const totalRecharge = filteredRobots.reduce((sum, robot) => sum + robot.rechargeRmb, 0);
  const peakPower = Math.max(...filteredRobots.map((robot) => robot.power), 0);

  return (
    <section className="screen robot-screen">
      <TopBar title="机器人后台" onBack={() => setScreen('home')} />
      <div className="stat-grid robot-stats">
        <Metric label="机器人" value={hasRobotFilters ? `${filteredRobots.length}/${data.robots.length}` : data.robots.length.toString()} />
        <Metric label="正在行动" value={activeCount.toString()} />
        <Metric label="商会相关" value={marketCount.toString()} />
        <Metric label="机器人金币" value={`${formatNumber(totalGold)} 金`} />
        <Metric label="真实余额" value={`${formatNumber(totalRealMoney)} 元`} />
        <Metric label="累计充值" value={`${formatNumber(totalRecharge)} 元`} />
        <Metric label="最高战力" value={formatNumber(peakPower)} />
      </div>
      <div className="robot-workbench">
        <section className="robot-roster">
          <div className="robot-list-head">
            <SectionTitle icon={<Gauge size={18} />} title="机器人列表" />
            <span>点击机器人查看档案和个人历史动态</span>
          </div>
          <div className="robot-filter-panel">
            <label className="robot-filter-field name">
              <span>名称</span>
              <input value={filters.name} onChange={(event) => updateFilter('name', event.target.value)} placeholder="机器人名称" />
            </label>
            <RobotRangeFilter
              label="金币"
              minValue={filters.minGold}
              maxValue={filters.maxGold}
              onMinChange={(value) => updateFilter('minGold', value)}
              onMaxChange={(value) => updateFilter('maxGold', value)}
            />
            <RobotRangeFilter
              label="战力"
              minValue={filters.minPower}
              maxValue={filters.maxPower}
              onMinChange={(value) => updateFilter('minPower', value)}
              onMaxChange={(value) => updateFilter('maxPower', value)}
            />
            <RobotRangeFilter
              label="等级"
              minValue={filters.minLevel}
              maxValue={filters.maxLevel}
              onMinChange={(value) => updateFilter('minLevel', value)}
              onMaxChange={(value) => updateFilter('maxLevel', value)}
            />
            {hasRobotFilters && (
              <button className="mini-action subtle" onClick={() => setFilters(emptyRobotFilters())}>重置</button>
            )}
          </div>
          <div className="robot-grid">
            {filteredRobots.length === 0 && <EmptyState text="当前筛选下没有机器人。" />}
            {filteredRobots.map((robot) => (
              <RobotActivityCard key={robot.id} robot={robot} onSelect={() => setSelectedRobot(robot)} />
            ))}
          </div>
        </section>
      </div>
      {selectedRobot && <RobotActivityDetailModal token={token} robot={selectedRobot} onClose={() => setSelectedRobot(null)} />}
    </section>
  );
}

function RechargeScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState('100');
  const { data, isLoading, error } = useQuery({
    queryKey: ['recharge-dashboard', token],
    queryFn: () => gameApi.rechargeDashboard(token),
    refetchInterval: 5_000,
  });
  const rechargeMutation = useMutation({
    mutationFn: (rmbAmount: number) => gameApi.recharge(token, rmbAmount),
    onSuccess: async () => {
      await invalidateGameQueries(queryClient, token);
      await queryClient.invalidateQueries({ queryKey: ['recharge-dashboard', token] });
      await queryClient.invalidateQueries({ queryKey: ['robot-activity', token] });
      await queryClient.invalidateQueries({ queryKey: ['market', token] });
    },
  });

  if (isLoading) {
    return <LoadingScreen title="读取充值经济" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '充值后台加载失败'} />;
  }

  const rmbAmount = Math.max(0, Math.floor(Number(amount) || 0));
  const canRecharge = rmbAmount > 0 && rmbAmount <= data.wallet.realMoney;
  const quickAmounts = [1, 10, 100, 1000, 10000, 100000];
  const robotRecharges = data.robotRecharges.slice(0, 80);
  const incomeEvents = data.incomeEvents.slice(0, 60);

  return (
    <section className="screen recharge-screen">
      <TopBar title="充值经济" onBack={() => setScreen('home')} />
      <div className="stat-grid recharge-stats">
        <Metric label="我的余额" value={`${formatNumber(data.wallet.realMoney)} 元`} />
        <Metric label="我的金币" value={`${formatNumber(data.wallet.gold)} 金`} />
        <Metric label="财富标签" value={`${data.wallet.wealthTierCode} ${data.wallet.wealthTier}`} />
        <Metric label="兑换比例" value={`1:${formatNumber(data.wallet.exchangeRate)}`} />
        <Metric label="总人民币" value={`${formatNumber(data.totals.totalRmb)} 元`} />
        <Metric label="总兑换金币" value={`${formatNumber(data.totals.totalGold)} 金`} />
        <Metric label="市场挂单金币" value={`${formatNumber(data.totals.marketListedGold)} 金`} />
        <Metric label="机器人余额" value={`${formatNumber(data.totals.robotRealMoney)} 元`} />
      </div>

      <div className="recharge-workbench">
        <section className="recharge-wallet-panel">
          <SectionTitle icon={<Coins size={18} />} title="我的充值" />
          <div className="recharge-wallet-hero">
            <div>
              <span className="eyebrow">{data.wallet.wealthTier}</span>
              <h1>{formatNumber(data.wallet.realMoney)} 元</h1>
              <p>每 3 分钟到账 {formatNumber(data.wallet.minIncome)}-{formatNumber(data.wallet.maxIncome)} 元</p>
            </div>
            <strong>{formatNumber(data.wallet.gold)} 金</strong>
          </div>
          <div className="recharge-form">
            <input
              inputMode="numeric"
              value={amount}
              onChange={(event) => setAmount(event.target.value.replace(/[^\d]/g, ''))}
            />
            <button
              className="primary-action"
              disabled={!canRecharge || rechargeMutation.isPending}
              onClick={() => rechargeMutation.mutate(rmbAmount)}
            >
              充值
            </button>
          </div>
          <div className="recharge-quick-row">
            {quickAmounts.map((value) => (
              <button
                key={value}
                className={rmbAmount === value ? 'active' : ''}
                disabled={value > data.wallet.realMoney}
                onClick={() => setAmount(String(value))}
              >
                {formatNumber(value)}
              </button>
            ))}
          </div>
          <div className="recharge-preview">
            <span>可兑换</span>
            <strong>{formatNumber(rmbAmount * data.wallet.exchangeRate)} 金</strong>
          </div>
        </section>

        <section className="recharge-tier-panel">
          <SectionTitle icon={<Gauge size={18} />} title="财富金字塔" />
          <div className="recharge-tier-list">
            {data.tierStats.map((tier) => (
              <WealthTierCard key={`${tier.wealthTierLevel}-${tier.wealthTier}`} tier={tier} />
            ))}
          </div>
        </section>

        <section className="recharge-ledger-panel">
          <SectionTitle icon={<ScrollText size={18} />} title="机器人充值明细" />
          <div className="recharge-ledger-list">
            {robotRecharges.length === 0 && <EmptyState text="暂时没有机器人充值记录。" />}
            {robotRecharges.map((row) => (
              <RobotRechargeCard key={row.id} row={row} />
            ))}
          </div>
        </section>

        <aside className="recharge-income-panel">
          <SectionTitle icon={<Clock3 size={18} />} title="发钱记录" />
          <div className="recharge-income-list">
            {incomeEvents.length === 0 && <EmptyState text="等待下一轮 3 分钟发钱。" />}
            {incomeEvents.map((row) => (
              <CashIncomeCard key={row.id} row={row} />
            ))}
          </div>
        </aside>
      </div>

      {rechargeMutation.data && (
        <FeedbackDialog
          variant="success"
          title="充值成功"
          message={`花费 ${formatNumber(rechargeMutation.data.rmbAmount)} 元，获得 ${formatNumber(rechargeMutation.data.goldAmount)} 金。`}
          onClose={() => rechargeMutation.reset()}
        />
      )}
      {rechargeMutation.error && (
        <FeedbackDialog
          variant="error"
          title="充值失败"
          message={rechargeMutation.error.message}
          onClose={() => rechargeMutation.reset()}
        />
      )}
    </section>
  );
}

function WealthTierCard({ tier }: { tier: WealthTierStat }) {
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

function RobotRechargeCard({ row }: { row: RobotRechargeRow }) {
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

function CashIncomeCard({ row }: { row: CashIncomeRow }) {
  return (
    <article className="cash-income-card">
      <span>{formatRelativeTime(row.createdAt)} · L{row.wealthTierLevel.toString().padStart(2, '0')} {row.wealthTier}</span>
      <strong>{row.playerName}</strong>
      <p>到账 {formatNumber(row.rmbAmount)} 元</p>
    </article>
  );
}

function RobotRangeFilter({ label, minValue, maxValue, onMinChange, onMaxChange }: {
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

function RobotActivityCard({ robot, onSelect }: { robot: RobotActivityView; onSelect: () => void }) {
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

function RobotEventCard({ event }: { event: RobotActivityEvent }) {
  return (
    <article className={`robot-event ${event.kind}`}>
      <span>{formatRelativeTime(event.createdAt)} · {robotActivityKindName(event.kind)}</span>
      <strong>{event.actorName}</strong>
      <small>{event.actorTitle}</small>
      <p>{event.text}</p>
    </article>
  );
}

function RobotActivityDetailModal({ token, robot: fallbackRobot, onClose }: {
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
              {error && <EmptyState text="机器人详情加载失败，稍后会自动重试。" />}
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
              {events.length === 0 && <EmptyState text="这个机器人还没有留下历史动态。" />}
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

function ChatScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const [selectedSpeaker, setSelectedSpeaker] = useState<ChatSpeaker | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streamConnected, setStreamConnected] = useState(false);
  const chatListRef = useRef<HTMLDivElement | null>(null);
  const lastMessageIdRef = useRef(0);
  const { data, isLoading, error } = useQuery({
    queryKey: ['chat', token],
    queryFn: () => gameApi.chatMessages(token),
  });
  const sendMutation = useMutation({
    mutationFn: (messageText: string) => gameApi.sendChat(token, messageText),
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
    setMessages((previous) => {
      if (previous.some((item) => item.id === message.id)) {
        return previous;
      }
      const next = [...previous, message].sort((left, right) => left.id - right.id).slice(-120);
      lastMessageIdRef.current = next.at(-1)?.id ?? lastMessageIdRef.current;
      return next;
    });
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
    const source = new EventSource(gameApi.chatStreamUrl(token, lastMessageIdRef.current));
    source.onopen = () => {
      if (!closed) {
        setStreamConnected(true);
      }
    };
    source.onerror = () => {
      if (!closed) {
        setStreamConnected(false);
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

  return (
    <section className="screen chat-screen">
      <TopBar title="世界聊天" onBack={() => setScreen('home')} />
      <div className="channel-strip chat-channel-strip">
        <span>世界</span>
        <strong>{messages.filter((message) => message.kind === 'robot').length}+ 在线发言</strong>
        <i className={`stream-indicator ${streamConnected ? 'online' : ''}`} />
      </div>
      <div className="chat-list" ref={chatListRef}>
        {messages.map((message) => (
          <ChatMessageBubble key={message.id} message={message} onSelectSpeaker={setSelectedSpeaker} />
        ))}
      </div>
      <form className="chat-form" onSubmit={submit}>
        <input value={text} maxLength={120} onChange={(event) => setText(event.target.value)} />
        <button className="icon-button send-button" disabled={sendMutation.isPending} aria-label="发送">
          <Send size={18} />
        </button>
      </form>
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

function ChatMessageBubble({ message, onSelectSpeaker }: { message: ChatMessage; onSelectSpeaker: (speaker: ChatSpeaker) => void }) {
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

function chatAvatarLabel(value: string) {
  const trimmed = value.trim();
  return (trimmed[0] ?? '?').toUpperCase();
}

function SpeakerDetailModal({ speaker, onClose }: { speaker: ChatSpeaker; onClose: () => void }) {
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

function LeaderboardScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [selectedSpeaker, setSelectedSpeaker] = useState<ChatSpeaker | null>(null);
  const [metric, setMetric] = useState<LeaderboardMetric>('power');
  const [profession, setProfession] = useState<ProfessionFilter>('all');
  const { data, isLoading, error } = useQuery({
    queryKey: ['leaderboard', token],
    queryFn: () => gameApi.leaderboard(token),
    refetchInterval: 8_000,
  });
  const rankedEntries = useMemo(() => rankLeaderboardEntries(data ?? [], metric, profession), [data, metric, profession]);
  const ownRank = rankedEntries.find((entry) => entry.player)?.rank;

  if (isLoading) {
    return <LoadingScreen title="读取银冠榜单" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '排行榜加载失败'} />;
  }

  return (
    <section className="screen leaderboard-screen">
      <TopBar title={`${leaderboardMetricName(metric)}榜`} onBack={() => setScreen('home')} />
      <div className="rank-summary">
        <Metric label="榜单人数" value={`${rankedEntries.length}/${data.length}`} />
        <Metric label="我的排名" value={(ownRank ?? '-').toString()} />
        <Metric label={`榜首${leaderboardMetricName(metric)}`} value={leaderboardScoreText(rankedEntries[0], metric)} />
      </div>
      <div className="leaderboard-filter-panel">
        <div className="segmented three">
          {[
            ['power', '战力榜'],
            ['gold', '金币榜'],
            ['level', '等级榜'],
          ].map(([value, label]) => (
            <button key={value} className={metric === value ? 'active' : ''} onClick={() => setMetric(value as LeaderboardMetric)}>
              {label}
            </button>
          ))}
        </div>
        <div className="profession-filter-row">
          {[
            ['all', '全部职业'],
            ['warrior', '战士'],
            ['mage', '法师'],
            ['ranger', '射手'],
          ].map(([value, label]) => (
            <button key={value} className={profession === value ? 'active' : ''} onClick={() => setProfession(value as ProfessionFilter)}>
              {label}
            </button>
          ))}
        </div>
      </div>
      <div className="leaderboard-list">
        {rankedEntries.length === 0 && <EmptyState text="当前职业筛选下没有榜单角色。" />}
        {rankedEntries.map((entry) => (
          <LeaderboardCard
            key={`${metric}-${profession}-${entry.rank}-${entry.name}`}
            entry={entry}
            metric={metric}
            onSelectSpeaker={() => setSelectedSpeaker(leaderboardEntryToSpeaker(entry))}
          />
        ))}
      </div>
      {selectedSpeaker && <SpeakerDetailModal speaker={selectedSpeaker} onClose={() => setSelectedSpeaker(null)} />}
    </section>
  );
}

function DungeonCard({ dungeon, combatPower, playerLevel = 0, loading, sweepLoading, special = false, onRun, onSweep }: {
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
        <span>Boss {bossArchetypeName(dungeon.bossArchetype)}</span>
        <span>预计 {dungeon.expectedRounds} 回合</span>
        <span className={dungeon.cleared ? 'clear-state cleared' : 'clear-state'}>{dungeon.cleared ? '已通过' : '未通过'}</span>
        <strong>{specialDungeon ? `${qualityName(highestQuality)}上限 · ${formatDropRate(highDropChance)}` : `${drops.length} 件可掉落`}</strong>
      </div>
      {!eligible && <p className="gate-warning">{dungeon.gate.label}</p>}
      {stamina && stamina.current <= 0 && <p className="gate-warning">疲劳不足，可在背包使用疲劳药水。</p>}
      <div className="drop-preview-section">
        <span>当前副本可掉落</span>
        {dropTypes.length > 0 && (
          <div className="drop-slot-row">
            {dropTypes.map((type) => <small key={type}>{typeName(type)}</small>)}
          </div>
        )}
        <div className="drop-preview-grid">
          {drops.slice(0, 5).map((drop) => (
            <DropPreviewCard key={drop.templateId} drop={drop} />
          ))}
          {drops.length > 5 && <div className="drop-more">+{drops.length - 5}</div>}
        </div>
      </div>
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
    </article>
  );
}

function DropPreviewCard({ drop }: { drop: DropPreview }) {
  const chance = formatDropRate(drop.dropRate);
  return (
    <div className={`drop-preview-card ${drop.quality}`}>
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

function EquipmentPanel({ home, onSelect, compact = false }: { home: HomeSnapshot; onSelect?: (item: Item) => void; compact?: boolean }) {
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

function PowerBreakdownPanel({ slices, total }: { slices: PowerBreakdownSlice[]; total: number }) {
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

function CombatStatsPanel({ stats, equipmentStats, compact = false }: { stats: DerivedStats; equipmentStats?: DerivedStats; compact?: boolean }) {
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

function StatContributionGrid({ stats, baseStats, equipmentStats }: { stats: DerivedStats; baseStats: DerivedStats; equipmentStats: DerivedStats }) {
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

function CombatStatCell({ label, value, bonus, percent = false }: { label: string; value: string; bonus?: number; percent?: boolean }) {
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

function StatContributionCell({ label, total, base, equipment }: { label: string; total: string; base: string; equipment: string }) {
  return (
    <div className="stat-contribution-cell">
      <span>{label}</span>
      <strong>{total}</strong>
      <small>基础 {base} · 装备 +{equipment}</small>
    </div>
  );
}

function QuestCard({ quest, selected = false, loading, onSelect, onClaim, onNavigate }: {
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

function QuestDetailPanel({ quest, loading, onClaim, onNavigate }: {
  quest: QuestRow | null;
  loading: boolean;
  onClaim: (questId: string) => void;
  onNavigate: (target?: string) => void;
}) {
  if (!quest) {
    return (
      <aside className="quest-detail-panel">
        <EmptyState text="选择一个任务查看详情。" />
      </aside>
    );
  }
  const progress = Math.min(100, quest.progressPercent ?? 0);
  return (
    <aside className={`quest-detail-panel ${quest.status}`}>
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

function equipmentDisplayName(item: { name: string; displayName?: string; enhancementLevel?: number }) {
  const baseName = (item.displayName?.trim() || item.name).replace(/\s\+\d+$/, '');
  const level = item.enhancementLevel ?? 0;
  return level > 0 ? `${baseName} +${level}` : baseName;
}

function equipmentCompactText(item: Item) {
  return `Lv.${item.requiredLevel} · 战力 ${formatNumber(itemPower(item))}`;
}

function enhancementStage(level = 0) {
  if (level >= 15) {
    return 'max';
  }
  if (level >= 12) {
    return 'radiant';
  }
  if (level >= 9) {
    return 'awaken';
  }
  return null;
}

function enhancementEffectClass(item?: { enhancementLevel?: number } | null) {
  const stage = enhancementStage(item?.enhancementLevel ?? 0);
  return stage ? `enhance-effect enhance-${stage}` : '';
}

function enhancementBadgeText(level: number) {
  const stage = enhancementStage(level);
  if (stage === 'max') {
    return `+${level} MAX`;
  }
  if (stage === 'radiant') {
    return `+${level} 辉光`;
  }
  if (stage === 'awaken') {
    return `+${level} 觉醒`;
  }
  return '';
}

function EnhancementBadge({ level }: { level?: number }) {
  const text = enhancementBadgeText(level ?? 0);
  if (!text) {
    return null;
  }
  return <span className={`enhance-badge enhance-${enhancementStage(level ?? 0)}`}>{text}</span>;
}

function ItemCard({ item, label, children, powerIncrease = false, onSelect }: { item: Item; label?: string; children?: ReactNode; powerIncrease?: boolean; onSelect?: () => void }) {
  return (
    <article className={`item-card ${enhancementEffectClass(item)} item-category-${item.itemCategory ?? 'equipment'} ${onSelect ? 'selectable' : ''}`} onClick={onSelect}>
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
          <p>{itemEffectText(item)}</p>
        </div>
      </div>
      {children && <div className="inline-actions">{children}</div>}
    </article>
  );
}

function TransferItemOption({ item, selected, disabled = false, onSelect }: {
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

function ItemDetail({ item, onClose }: { item: EquipmentDetailData; onClose: () => void }) {
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

function EquipConfirmModal({ item, currentItem, targetSlot, currentPower, loading, onClose, onConfirm }: {
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

function CompareCard({ title, item, highlight = false, emptyTitle = '空槽位', emptyText = '穿戴后将直接补齐该部位。', emptyMeta = '无来源' }: {
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

function EnhanceModal({ item, gold, message, loading, stones = [], selectedStoneIds = [], onAddStone, onRemoveStone, onClose, onEnhance }: {
  item: Item;
  gold: number;
  message: string | null;
  loading: boolean;
  stones?: Item[];
  selectedStoneIds?: number[];
  onAddStone?: (stoneId: number) => void;
  onRemoveStone?: (index: number) => void;
  onClose: () => void;
  onEnhance: () => void;
}) {
  const nextLevel = item.enhancementLevel + 1;
  const cost = enhanceCost(item);
  const stoneBonus = selectedStoneBonus(stones, selectedStoneIds);
  const chance = Math.min(0.95, enhanceChance(item) + stoneBonus);
  const selectedStones = selectedStoneIds.map((stoneId) => stones.find((stone) => stone.id === stoneId) ?? null);
  return (
    <div className="detail-backdrop result-modal-backdrop" onClick={onClose}>
      <section className={`decision-modal ${item.quality} ${enhancementEffectClass(item)}`} onClick={(event: MouseEvent<HTMLElement>) => event.stopPropagation()}>
        <div className="result-modal-head">
          <div>
            <div className="item-meta-line">
              <span className="eyebrow">装备强化 · +{item.enhancementLevel} → +{nextLevel}</span>
              <EnhancementBadge level={item.enhancementLevel} />
            </div>
            <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
            <p>{bonusText(item)}</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="result-modal-metrics">
          <Metric label="强化费用" value={`${cost} 金`} />
          <Metric label="成功率" value={`${Math.round(chance * 100)}%`} />
          <Metric label="当前金币" value={`${gold} 金`} />
          <Metric label="强化石" value={`${selectedStoneIds.length}/3`} />
          <Metric label="石头加成" value={`+${formatPercent(stoneBonus)}`} />
        </div>
        {message && <div className="modal-notice">{message}</div>}
        <div className="enhance-stone-panel">
          <div className="stone-slot-row">
            {[0, 1, 2].map((slotIndex) => {
              const stone = selectedStones[slotIndex];
              return (
                <button
                  key={slotIndex}
                  className={`stone-slot ${stone ? stone.quality : ''}`}
                  disabled={!stone}
                  onClick={() => onRemoveStone?.(slotIndex)}
                >
                  {stone ? (
                    <>
                      <strong>{equipmentDisplayName(stone)}</strong>
                      <span>+{formatPercent(stone.enhanceBonusRate)}</span>
                    </>
                  ) : (
                    <>
                      <strong>空槽</strong>
                      <span>可放强化石</span>
                    </>
                  )}
                </button>
              );
            })}
          </div>
          <div className="stone-option-list">
            {stones.length === 0 && <EmptyState text="当前背包没有适用于下一强化等级的强化石。" />}
            {stones.map((stone) => {
              const selectedCount = selectedStoneCount(selectedStoneIds, stone.id);
              const stock = Math.max(1, stone.quantity);
              return (
                <button
                  key={stone.id}
                  className={`stone-option ${stone.quality}`}
                  disabled={loading || selectedStoneIds.length >= 3 || selectedCount >= stock}
                  onClick={() => onAddStone?.(stone.id)}
                >
                  <strong>{equipmentDisplayName(stone)}</strong>
                  <span>+{formatPercent(stone.enhanceBonusRate)} · {selectedCount}/{stock}</span>
                </button>
              );
            })}
          </div>
        </div>
        <div className="detail-source">
          <span>失败规则</span>
          <strong>+7 后失败可能降级，幸运值会提高下一次成功率。</strong>
        </div>
        <div className="result-modal-actions">
          <button className="mini-action subtle" onClick={onClose}>结束强化</button>
          <button className="primary-action" disabled={loading || gold < cost || item.enhancementLevel >= 15} onClick={onEnhance}>
            {loading ? '强化中...' : item.enhancementLevel >= 15 ? '已达上限' : '强化一次'}
          </button>
        </div>
      </section>
    </div>
  );
}

function SellConfirmModal({ item, loading, onClose, onConfirm }: {
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

function CombatantCard({ icon, label, title, detail, hp, maxHp, enemy = false }: {
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

function HpBar({ value, max }: { value: number; max: number }) {
  const percent = Math.max(0, Math.min(100, Math.round((value / Math.max(1, max)) * 100)));
  return (
    <div className="hp-bar" aria-label="生命值">
      <span style={{ width: `${percent}%` }} />
    </div>
  );
}

function ResultSummaryModal({ result, onClose, onReturn, onRetry, retrying = false, onSelectLoot }: {
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
          {onRetry && <button className="mini-action" disabled={retrying} onClick={onRetry}>{retrying ? '进入中...' : '重试'}</button>}
          <button className="primary-action" onClick={() => void onReturn()}>回到副本大厅</button>
        </div>
      </section>
    </div>
  );
}

function SweepSummaryModal({ result, onClose, onSelectLoot }: {
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

function MarketSaleCard({ sale, onInspect }: { sale: MarketSale; onInspect: () => void }) {
  return (
    <button className={`market-sale-card ${sale.item.quality} ${enhancementEffectClass(sale.item)}`} onClick={onInspect}>
      <div className="item-meta-line">
        <span>{formatRelativeTime(sale.soldAt)} · {sale.buyerName}</span>
        <EnhancementBadge level={sale.item.enhancementLevel} />
      </div>
      <strong className={`quality ${sale.item.quality}`}>{equipmentDisplayName(sale.item)}</strong>
      <p>成交 {sale.price} 金 · 到账 {sale.netGold} 金</p>
    </button>
  );
}

function MarketListedCard({ listing, loading, onCancel, onInspect }: {
  listing: MarketListing;
  loading: boolean;
  onCancel: () => void;
  onInspect: () => void;
}) {
  return (
    <article className={`market-sale-card listed ${listing.item.quality} ${enhancementEffectClass(listing.item)} selectable`} onClick={onInspect}>
      <div className="item-meta-line">
        <span>{formatRelativeTime(listing.listedAt)} · {listing.marketTag}</span>
        <EnhancementBadge level={listing.item.enhancementLevel} />
      </div>
      <strong className={`quality ${listing.item.quality}`}>{equipmentDisplayName(listing.item)}</strong>
      <p>挂价 {listing.price} 金 · 估值 {listing.recommendedPrice} 金 · 成交 {listing.dealChance}%</p>
      <button className="mini-action subtle" disabled={loading} onClick={(event) => {
        event.stopPropagation();
        onCancel();
      }}>撤回</button>
    </article>
  );
}

function ListingCard({ listing, own, loading, onBuy, onCancel, onInspect }: {
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
            <span className="eyebrow">{listing.sellerName} · {listing.sellerType} · Lv.{listing.item.requiredLevel}</span>
            <EnhancementBadge level={listing.item.enhancementLevel} />
          </div>
          <h2 className={`quality ${listing.item.quality}`}>{equipmentDisplayName(listing.item)}</h2>
                <p>{listing.item.attackBonus > 0 ? `攻击 +${listing.item.attackBonus}` : ''} {listing.item.defenseBonus > 0 ? `防御 +${listing.item.defenseBonus}` : ''} {listing.item.resistanceBonus > 0 ? `抗性 +${listing.item.resistanceBonus}` : ''} {listing.item.hpBonus > 0 ? `生命 +${listing.item.hpBonus}` : ''}</p>
          <div className="market-tags">
            <span>{listing.marketTag}</span>
            <span>估值 {listing.recommendedPrice}</span>
            <span>成交 {listing.dealChance}%</span>
            <span>{formatRelativeTime(listing.listedAt)}</span>
          </div>
        </div>
      </div>
      <div className="inline-actions">
        <strong className="price-tag">{listing.price} 金</strong>
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

function LeaderboardCard({ entry, metric, onSelectSpeaker }: { entry: LeaderboardEntry; metric: LeaderboardMetric; onSelectSpeaker: () => void }) {
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
          <span>{entry.player ? '玩家角色' : '后台机器人'}</span>
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

function CatalogFilterGroup({ title, value, options, onChange }: {
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

function CatalogDetailPanel({ item }: { item: ItemCatalogItem | null }) {
  if (!item) {
    return (
      <aside className="catalog-detail-panel">
        <EmptyState text="选择一个物品查看详细属性。" />
      </aside>
    );
  }

  const detail = catalogItemToDetail(item);
  const statRows = catalogStatRows(item);
  return (
    <aside className={`catalog-detail-panel ${item.quality}`}>
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

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StaminaPanel({ stamina, compact = false }: { stamina?: HomeSnapshot['stamina'] | Dungeon['stamina']; compact?: boolean }) {
  if (!stamina) {
    return null;
  }
  const percent = Math.max(0, Math.min(100, Math.round((stamina.current / Math.max(1, stamina.max)) * 100)));
  return (
    <div className={`stamina-panel ${compact ? 'compact' : ''}`}>
      <div className="stamina-panel-head">
        <span>疲劳</span>
        <strong>{stamina.current}/{stamina.max}</strong>
      </div>
      <div className="progress-bar stamina-bar" aria-label="疲劳值">
        <span style={{ width: `${percent}%` }} />
      </div>
      <small>{stamina.current >= stamina.max ? '已满' : `下次恢复 ${formatStaminaTime(stamina.secondsUntilNext)} · 回满 ${formatStaminaTime(stamina.secondsUntilFull)}`}</small>
    </div>
  );
}

function NavTile({ icon, title, detail, onClick }: {
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

function SectionTitle({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <div className="section-title">
      {icon}
      <h2>{title}</h2>
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return <div className="empty-state">{text}</div>;
}

function FeedbackDialog({ variant, title, message, onClose }: {
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

function ConfirmDialog({ title, message, confirmLabel, cancelLabel, danger = false, onConfirm, onCancel }: {
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

function TopBar({ title, onBack }: { title: string; onBack?: () => void }) {
  return (
    <header className="top-bar">
      {onBack ? <button className="text-button" onClick={onBack}>返回</button> : <span />}
      <strong>{title}</strong>
      <span />
    </header>
  );
}

function LoadingScreen({ title }: { title: string }) {
  return (
    <section className="screen center-screen">
      <div className="loader" />
      <p>{title}</p>
    </section>
  );
}

function ErrorScreen({ message }: { message: string }) {
  const logout = useAppStore((state) => state.logout);
  return (
    <section className="screen center-screen">
      <p className="error">{message}</p>
      <button className="primary-action" onClick={logout}>重新登录</button>
    </section>
  );
}

async function invalidateGameQueries(queryClient: ReturnType<typeof useQueryClient>, token: string) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['home', token] }),
    queryClient.invalidateQueries({ queryKey: ['dungeons', token] }),
    queryClient.invalidateQueries({ queryKey: ['inventory', token] }),
    queryClient.invalidateQueries({ queryKey: ['quests', token] }),
    queryClient.invalidateQueries({ queryKey: ['leaderboard', token] }),
    queryClient.invalidateQueries({ queryKey: ['recharge-dashboard', token] }),
  ]);
}

function orderedEquipment(items: Record<string, Item>) {
  return equipmentSlotOrder()
    .filter((slot) => items[slot])
    .map((slot) => [slot, items[slot]] as [string, Item]);
}

function equipmentSlotPairs(items: Record<string, Item>) {
  return equipmentSlotOrder().map((slot) => [slot, items[slot] ?? null] as [string, Item | null]);
}

function equipmentSlotOrder() {
  return ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring1', 'ring2'];
}

function battleFramesForResult(result: DungeonRunResult): BattleFrame[] {
  if (result.frames?.length) {
    return result.frames;
  }
  const maxHp = result.playerMaxHp || result.playerFinalHp || 1;
  return (result.logs?.length ? result.logs : ['没有战斗记录']).map((log, index) => {
    const tone = battleLogTone(log);
    return {
      index: index + 1,
      text: log,
      tone,
      roomLabel: '战斗记录',
      enemyName: undefined,
      playerHp: result.playerFinalHp || maxHp,
      playerMaxHp: maxHp,
      enemyHp: 0,
      enemyMaxHp: 0,
      actor: 'system' as const,
      eventType: log.includes('恢复') ? 'heal' as const : tone === 'hit' ? 'hit' as const : 'phase' as const,
      damage: 0,
      critical: log.includes('暴击'),
      missed: log.includes('闪避') || log.includes('落空'),
    };
  });
}

function professionName(profession: string) {
  return profession === 'warrior' ? '战士' : profession === 'ranger' ? '射手' : profession === 'mage' ? '法师' : profession;
}

function leaderboardMetricName(metric: LeaderboardMetric) {
  return metric === 'gold' ? '金币' : metric === 'level' ? '等级' : '战力';
}

function leaderboardMetricValue(entry: LeaderboardEntry, metric: LeaderboardMetric) {
  if (metric === 'gold') {
    return entry.gold;
  }
  if (metric === 'level') {
    return entry.level;
  }
  return entry.power;
}

function leaderboardScoreText(entry: LeaderboardEntry | undefined, metric: LeaderboardMetric) {
  if (!entry) {
    return '-';
  }
  if (metric === 'gold') {
    return `${formatNumber(entry.gold)} 金`;
  }
  if (metric === 'level') {
    return `Lv.${entry.level}`;
  }
  return formatNumber(entry.power);
}

function leaderboardSecondaryStats(entry: LeaderboardEntry, metric: LeaderboardMetric) {
  const stats = [
    { key: 'level', label: '等级', value: `Lv.${entry.level}` },
    { key: 'power', label: '战力', value: formatNumber(entry.power) },
    { key: 'gold', label: '金币', value: `${formatNumber(entry.gold)} 金` },
  ];
  return [
    ...stats.filter((stat) => stat.key !== metric),
    { key: 'mainAttribute', label: '主属性', value: leaderboardMainAttribute(entry) },
  ];
}

function leaderboardMainAttribute(entry: LeaderboardEntry) {
  const attributes = [
    ['力量', entry.strength],
    ['敏捷', entry.agility],
    ['体质', entry.constitution],
    ['智力', entry.intelligence],
    ['精神', entry.spirit],
  ] as const;
  const [label, value] = attributes.reduce((best, current) => current[1] > best[1] ? current : best);
  return `${label} ${value}`;
}

function rankLeaderboardEntries(entries: LeaderboardEntry[], metric: LeaderboardMetric, profession: ProfessionFilter) {
  return entries
    .filter((entry) => profession === 'all' || entry.profession === profession)
    .sort((left, right) => {
      const metricDelta = leaderboardMetricValue(right, metric) - leaderboardMetricValue(left, metric);
      if (metricDelta !== 0) {
        return metricDelta;
      }
      return right.power - left.power
        || right.gold - left.gold
        || right.level - left.level
        || left.name.localeCompare(right.name);
    })
    .map((entry, index) => ({ ...entry, rank: index + 1 }));
}

function slotName(slot: string) {
  const names: Record<string, string> = {
    weapon: '武器',
    helmet: '头盔',
    armor: '护甲',
    legs: '护腿',
    boots: '靴子',
    gloves: '护手',
    necklace: '项链',
    ring1: '戒指一',
    ring2: '戒指二',
  };
  return names[slot] ?? slot;
}

function typeName(type: string) {
  if (type === 'enhancementStone') {
    return '强化石';
  }
  if (type === 'staminaPotion') {
    return '疲劳药水';
  }
  if (type === 'attributePotion') {
    return '属性药水';
  }
  if (type === 'fragment') {
    return '碎片';
  }
  if (type === 'chest') {
    return '宝箱';
  }
  return slotName(type === 'ring' ? 'ring1' : type);
}

function isEquipmentItem(item: { itemCategory?: string; itemType: string }) {
  return (item.itemCategory ?? 'equipment') === 'equipment'
    || ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring'].includes(item.itemType);
}

function itemCategoryLabel(item: { itemCategory?: string; itemType: string }) {
  const category = item.itemCategory ?? (isEquipmentItem(item) ? 'equipment' : 'material');
  const names: Record<string, string> = {
    equipment: '装备',
    consumable: '消耗品',
    material: '材料',
    chest: '宝箱',
  };
  return names[category] ?? typeName(item.itemType);
}

function itemEffectText(item: Item | EquipmentDetailData) {
  if (item.effectType === 'staminaPotion') {
    return `恢复疲劳 +${effectNumber(item, 'amount')}`;
  }
  if (item.effectType === 'attributePotion') {
    const effect = parseEffectValue(item);
    return `${attributeName(String(effect.attribute ?? ''))} +${Number(effect.amount ?? 0)}`;
  }
  if (item.effectType === 'enhancementStone') {
    return `成功率 +${formatPercent(item.enhanceBonusRate ?? 0)} · +${item.minEnhanceLevel ?? 1}-${item.maxEnhanceLevel ?? 15}`;
  }
  if (item.effectType === 'fragment') {
    return '合成装备宝箱材料';
  }
  if (item.effectType === 'chest' || item.itemCategory === 'chest') {
    return '开启后随机获得奖励';
  }
  return bonusText(item);
}

function parseEffectValue(item: Item | EquipmentDetailData) {
  if (!item.effectValueJson) {
    return {} as Record<string, string | number>;
  }
  try {
    return JSON.parse(item.effectValueJson) as Record<string, string | number>;
  } catch {
    return {} as Record<string, string | number>;
  }
}

function effectNumber(item: Item | EquipmentDetailData, key: string) {
  const value = parseEffectValue(item)[key];
  return typeof value === 'number' ? value : Number(value ?? 0);
}

function inventoryTemplateQuantity(items: Item[], templateId: string) {
  return items
    .filter((item) => item.templateId === templateId)
    .reduce((total, item) => total + Math.max(1, item.quantity ?? 1), 0);
}

function enhancementStonesForItem(items: Item[], item: Item | null) {
  if (!item) {
    return [];
  }
  const nextLevel = item.enhancementLevel + 1;
  return items
    .filter((stone) => stone.effectType === 'enhancementStone')
    .filter((stone) => stone.minEnhanceLevel <= nextLevel && stone.maxEnhanceLevel >= nextLevel)
    .sort((left, right) => right.enhanceBonusRate - left.enhanceBonusRate || qualityRank(right.quality) - qualityRank(left.quality) || left.id - right.id);
}

function selectedStoneBonus(stones: Item[], selectedStoneIds: number[]) {
  return selectedStoneIds.reduce((total, stoneId) => {
    const stone = stones.find((candidate) => candidate.id === stoneId);
    return total + (stone?.enhanceBonusRate ?? 0);
  }, 0);
}

function selectedStoneCount(selectedStoneIds: number[], stoneId: number) {
  return selectedStoneIds.filter((id) => id === stoneId).length;
}

function attributeName(attribute: string) {
  const names: Record<string, string> = {
    strength: '力量',
    agility: '敏捷',
    constitution: '体质',
    intelligence: '智力',
    spirit: '精神',
  };
  return names[attribute] ?? attribute;
}

function formatStaminaTime(seconds: number) {
  if (seconds <= 0) {
    return '已满';
  }
  const minutes = Math.floor(seconds / 60);
  const remain = seconds % 60;
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60);
    const extraMinutes = minutes % 60;
    return `${hours}h ${extraMinutes}m`;
  }
  return `${minutes}:${remain.toString().padStart(2, '0')}`;
}

function bossArchetypeName(archetype: string) {
  const names: Record<string, string> = {
    boss: '首领',
    brute: '重击',
    skirmisher: '迅捷',
    caster: '施法',
    guardian: '守卫',
  };
  return names[archetype] ?? archetype;
}

function uniqueDropTypes(drops: DropPreview[]) {
  return Array.from(new Set(drops.map((drop) => drop.itemType)))
    .sort((left, right) => dropTypeRank(left) - dropTypeRank(right));
}

function combinedDropChance(drops: DropPreview[]) {
  return 1 - drops.reduce((missChance, drop) => missChance * (1 - Math.max(0, Math.min(1, drop.dropRate))), 1);
}

function dropTypeRank(type: string) {
  const order = ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring'];
  const index = order.indexOf(type);
  return index === -1 ? order.length : index;
}

function formatDropRate(rate: number) {
  const percent = rate * 100;
  if (percent > 0 && percent < 1) {
    return `${percent.toFixed(1)}%`;
  }
  return `${Math.round(percent)}%`;
}

function formatPercent(rate: number) {
  return `${Math.round(rate * 1000) / 10}%`;
}

function powerBreakdownForHome(home: HomeSnapshot): PowerBreakdownSlice[] {
  return [
    {
      key: 'base',
      label: '角色基础',
      value: home.powerBreakdown.basePower,
      detail: `Lv.${home.player.level} · 基础属性转化`,
    },
    {
      key: 'equipment',
      label: '装备贡献',
      value: home.powerBreakdown.equipmentPower,
      detail: `${Object.values(home.equippedItems).filter(Boolean).length}/9 件已穿戴`,
    },
    {
      key: 'synergy',
      label: '属性协同',
      value: home.powerBreakdown.synergyPower,
      detail: '装备带来的输出和生存效率',
    },
  ];
}

function categoryName(category: string) {
  const names: Record<string, string> = {
    main: '主线',
    daily: '日常',
    achievement: '成就',
  };
  return names[category] ?? category;
}

function statusName(status: string) {
  const names: Record<string, string> = {
    active: '进行中',
    completed: '可领取',
    claimed: '已领取',
    locked: '未解锁',
  };
  return names[status] ?? status;
}

function rewardName(reward: { type: string; amount: number; itemName?: string; targetId?: string }) {
  const names: Record<string, string> = {
    gold: '金币',
    experience: '经验',
    itemTemplate: reward.itemName ?? '物品',
  };
  return `${names[reward.type] ?? reward.itemName ?? reward.targetId ?? reward.type} +${reward.amount}`;
}

function conditionName(type: string) {
  const names: Record<string, string> = {
    dungeonCompleted: '通关副本',
    monsterKills: '击败怪物',
    equipmentEquipped: '穿戴装备',
    enhancementAttempts: '强化尝试',
    enhancementSuccesses: '强化成功',
    enhancementStoneUsed: '使用强化石',
    enhancementLevelReached: '强化等级',
    combatPowerReached: '战力达成',
    staminaSpent: '消耗疲劳',
    itemUsed: '使用道具',
    chestOpened: '开启宝箱',
    fragmentCrafted: '碎片合成',
    worldChatSent: '世界聊天',
    marketVisited: '访问市场',
    leaderboardViewed: '查看榜单',
  };
  return names[type] ?? type;
}

function questSortScore(quest: QuestRow) {
  let score = 0;
  if (quest.claimable || quest.status === 'completed') {
    score += 10000;
  }
  if (quest.recommended) {
    score += 1000;
  }
  if (quest.status === 'active') {
    score += 500;
  }
  score += Math.min(100, quest.progressPercent ?? 0);
  if (quest.category === 'main') {
    score += 20;
  }
  if (quest.category === 'daily') {
    score += 10;
  }
  return score;
}

function screenForQuestTarget(target?: string) {
  if (target === 'dungeonList') {
    return 'dungeons';
  }
  if (target === 'worldChat') {
    return 'chat';
  }
  if (target === 'market' || target === 'leaderboard' || target === 'inventory') {
    return target;
  }
  return 'home';
}

function dungeonRisk(
  combatPower: number,
  minimumPower: number,
  playerLevel = 0,
  minimumLevel = 0,
  gate?: Dungeon['gate'],
) {
  if (!combatPower) {
    return { label: '读取中', level: 'unknown' };
  }
  if (gate && !gate.eligible) {
    if (gate.missingLevel > 0 && gate.missingPower > 0) {
      return { label: '未达标', level: 'deadly' };
    }
    if (gate.missingLevel > 0) {
      return { label: `差 ${gate.missingLevel} 级`, level: 'deadly' };
    }
    return { label: `差 ${formatNumber(gate.missingPower)}`, level: 'risky' };
  }
  const ratio = combatPower / Math.max(1, minimumPower);
  const levelGap = playerLevel > 0 && minimumLevel > 0 ? minimumLevel - playerLevel : 0;
  if (levelGap > 0) {
    return { label: `差 ${levelGap} 级`, level: 'deadly' };
  }
  if (ratio >= 1.2) {
    return { label: '碾压', level: 'safe' };
  }
  if (ratio >= 1.0) {
    return { label: '稳妥', level: 'normal' };
  }
  return { label: '危险', level: 'risky' };
}

function dungeonMatchesLevelFilter(dungeon: Dungeon, filter: DungeonLevelFilter) {
  if (filter === 'all') {
    return true;
  }
  const [minLevel, maxLevel] = filter.split('-').map(Number);
  return dungeon.minimumLevel >= minLevel && dungeon.minimumLevel <= maxLevel;
}

function isSpecialDungeon(dungeon: Dungeon) {
  return dungeon.id.startsWith('special_');
}

function qualityName(quality: string) {
  const names: Record<string, string> = {
    common: '普通',
    uncommon: '优秀',
    rare: '稀有',
    epic: '史诗',
    legendary: '传说',
    immortal: '不朽',
  };
  return names[quality] ?? quality;
}

function battleLogTone(log: string) {
  if (log.includes('倒下') || log.includes('中止') || log.includes('危险')) {
    return 'danger';
  }
  if (log.includes('不朽')) {
    return 'immortal-loot';
  }
  if (log.includes('传说') || log.includes('获得装备')) {
    return 'loot';
  }
  if (log.includes('击败') || log.includes('暴击')) {
    return 'hit';
  }
  return '';
}

function battleEventName(frame: BattleFrame) {
  if (frame.critical) {
    return '暴击';
  }
  if (frame.missed) {
    return '闪避';
  }
  const names: Record<string, string> = {
    hit: frame.actor === 'enemy' ? '受击' : '命中',
    miss: '闪避',
    crit: '暴击',
    phase: '机制',
    heal: '恢复',
    death: '击败',
  };
  return names[frame.eventType] ?? '记录';
}

function itemMatchesCategory(item: Item, category: string) {
  if (category === 'all') {
    return true;
  }
  if (category === 'equipment') {
    return isEquipmentItem(item);
  }
  if (['consumable', 'material', 'chest'].includes(category)) {
    return (item.itemCategory ?? '') === category;
  }
  return itemTypesForCategory(category).includes(item.itemType);
}

function itemTypesForCategory(category: string) {
  const map: Record<string, string[]> = {
    equipment: ['weapon', 'helmet', 'armor', 'legs', 'boots', 'gloves', 'necklace', 'ring'],
    weapon: ['weapon'],
    armor: ['helmet', 'armor', 'legs', 'boots', 'gloves'],
    accessory: ['necklace', 'ring'],
  };
  return map[category] ?? [];
}

function categoryNameForInventory(category: string) {
  const names: Record<string, string> = {
    all: '全部',
    equipment: '装备',
    consumable: '消耗品',
    material: '材料',
    chest: '宝箱',
    weapon: '武器',
    armor: '防具',
    accessory: '饰品',
  };
  return names[category] ?? '全部';
}

function catalogSummary(items: ItemCatalogItem[]) {
  return {
    total: items.length,
    equipment: items.filter((item) => isEquipmentItem(item)).length,
    consumable: items.filter((item) => item.itemCategory === 'consumable').length,
    material: items.filter((item) => item.itemCategory === 'material').length,
    chest: items.filter((item) => item.itemCategory === 'chest').length,
    maxLevel: items.reduce((max, item) => Math.max(max, item.requiredLevel), 1),
  };
}

function filterCatalogItems(items: ItemCatalogItem[], filters: {
  category: CatalogCategoryFilter;
  quality: CatalogQualityFilter;
  level: CatalogLevelFilter;
  sort: CatalogSortKey;
  search: string;
}) {
  const search = filters.search.trim().toLowerCase();
  return [...items]
    .filter((item) => filters.category === 'all' || (filters.category === 'equipment' ? isEquipmentItem(item) : item.itemCategory === filters.category))
    .filter((item) => filters.quality === 'all' || item.quality === filters.quality)
    .filter((item) => catalogMatchesLevel(item, filters.level))
    .filter((item) => {
      if (!search) {
        return true;
      }
      return [
        item.name,
        item.templateId,
        item.itemType,
        item.itemCategory,
        qualityName(item.quality),
        typeName(item.itemType),
        item.description,
        catalogCardText(item),
      ].join(' ').toLowerCase().includes(search);
    })
    .sort((left, right) => {
      if (filters.sort === 'level') {
        return right.requiredLevel - left.requiredLevel
          || qualityRank(right.quality) - qualityRank(left.quality)
          || left.itemType.localeCompare(right.itemType)
          || left.name.localeCompare(right.name);
      }
      if (filters.sort === 'type') {
        return left.itemCategory.localeCompare(right.itemCategory)
          || left.itemType.localeCompare(right.itemType)
          || right.requiredLevel - left.requiredLevel
          || qualityRank(right.quality) - qualityRank(left.quality)
          || left.name.localeCompare(right.name);
      }
      return qualityRank(right.quality) - qualityRank(left.quality)
        || right.requiredLevel - left.requiredLevel
        || left.itemType.localeCompare(right.itemType)
        || left.name.localeCompare(right.name);
    });
}

function catalogMatchesLevel(item: ItemCatalogItem, filter: CatalogLevelFilter) {
  if (filter === 'all') {
    return true;
  }
  const [minLevel, maxLevel] = filter.split('-').map(Number);
  return item.requiredLevel >= minLevel && item.requiredLevel <= maxLevel;
}

function catalogItemToDetail(item: ItemCatalogItem): EquipmentDetailData {
  return {
    templateId: item.templateId,
    name: item.name,
    displayName: item.name,
    itemType: item.itemType,
    itemCategory: item.itemCategory,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus ?? 0,
    sellPrice: item.sellPrice,
    quantity: item.stackable ? 1 : undefined,
    stackable: item.stackable,
    effectType: item.effectType ?? undefined,
    effectValueJson: item.effectValueJson ?? undefined,
    enhanceBonusRate: item.enhanceBonusRate,
    minEnhanceLevel: item.minEnhanceLevel,
    maxEnhanceLevel: item.maxEnhanceLevel,
    enhancementLevel: 0,
    enhancementLuck: 0,
    origin: catalogSourceHint(item),
    power: isEquipmentItem(item) ? catalogItemPower(item) : 0,
  };
}

function catalogItemPower(item: ItemCatalogItem) {
  return itemPower({
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus ?? 0,
    enhancementLevel: 0,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
  });
}

function catalogCardText(item: ItemCatalogItem) {
  if (isEquipmentItem(item)) {
    return bonusText(catalogItemToDetail(item));
  }
  return itemEffectText(catalogItemToDetail(item));
}

function catalogIconForItem(item: ItemCatalogItem) {
  if (isEquipmentItem(item)) {
    return <Shield size={18} />;
  }
  if (item.itemCategory === 'consumable') {
    return <HeartPulse size={18} />;
  }
  if (item.itemCategory === 'material') {
    return <Gem size={18} />;
  }
  return <Package size={18} />;
}

function catalogStatRows(item: ItemCatalogItem) {
  const rows = [
    ['攻击', item.attackBonus],
    ['防御', item.defenseBonus],
    ['抗性', item.resistanceBonus],
    ['生命', item.hpBonus],
    ['法力', item.mpBonus],
    ['暴击', item.critBonus ? `${Math.round(item.critBonus * 1000) / 10}%` : ''],
    ['随机浮动', item.randomRange > 0 ? `±${item.randomRange}` : ''],
  ] as const;
  return rows
    .filter(([, value]) => value !== 0 && value !== '')
    .map(([label, value]) => ({ label, value: typeof value === 'number' ? `+${formatNumber(value)}` : value }));
}

function catalogEffectDetail(item: ItemCatalogItem) {
  const effect = parseEffectValue(catalogItemToDetail(item));
  const entries = Object.entries(effect);
  if (entries.length === 0) {
    return '';
  }
  return entries
    .map(([key, value]) => {
      if (key === 'attribute') {
        return `属性：${attributeName(String(value))}`;
      }
      if (key === 'amount') {
        return `数值：${value}`;
      }
      if (key === 'maxLevel') {
        return `等级上限：Lv.${value}`;
      }
      return `${key}：${value}`;
    })
    .join(' · ');
}

function catalogSourceHint(item: ItemCatalogItem) {
  if (item.itemCategory === 'chest') {
    return item.quality === 'legendary' || item.quality === 'immortal' ? '碎片合成与高难任务' : '任务奖励与副本掉落';
  }
  if (item.effectType === 'enhancementStone') {
    return '副本掉落、宝箱和任务奖励';
  }
  if (item.effectType === 'fragment') {
    return '日常活跃、宝箱与高阶副本';
  }
  if (item.itemCategory === 'consumable') {
    return '任务奖励、宝箱和冒险补给';
  }
  return item.quality === 'immortal' ? '血月裂隙等特殊副本' : '普通副本、宝箱和市场流转';
}

function catalogUsageHint(item: ItemCatalogItem) {
  if (item.effectType === 'enhancementStone') {
    return `强化 +${item.minEnhanceLevel} 至 +${item.maxEnhanceLevel} 时可用，成功率 +${formatPercent(item.enhanceBonusRate)}`;
  }
  if (item.effectType === 'staminaPotion') {
    return '疲劳不足时使用，恢复值不会超过 200。';
  }
  if (item.effectType === 'attributePotion') {
    return '使用后永久增加角色属性，适合优先补主属性。';
  }
  if (item.effectType === 'fragment') {
    return '积攒到配方数量后可合成传说或不朽装备宝箱。';
  }
  if (item.itemCategory === 'chest') {
    return '开启后按权重产出装备、材料或药水。';
  }
  return '用于提升角色战斗力，品质和等级越高基础价值越高。';
}

function sortItems(items: Item[], sort: string) {
  const comparators: Record<string, (a: Item, b: Item) => number> = {
    level: (a, b) => b.requiredLevel - a.requiredLevel || qualityRank(b.quality) - qualityRank(a.quality) || a.id - b.id,
    type: (a, b) => a.itemType.localeCompare(b.itemType) || qualityRank(b.quality) - qualityRank(a.quality) || a.id - b.id,
    quality: (a, b) => qualityRank(b.quality) - qualityRank(a.quality) || b.requiredLevel - a.requiredLevel || a.id - b.id,
  };
  return [...items].sort(comparators[sort] ?? comparators.quality);
}

function qualityRank(quality: string) {
  const ranks: Record<string, number> = {
    immortal: 6,
    legendary: 5,
    epic: 4,
    rare: 3,
    uncommon: 2,
    common: 1,
  };
  return ranks[quality] ?? 0;
}

function formatNumber(value: number) {
  return Number.isInteger(value) ? value.toString() : Math.round(value).toString();
}

function formatChatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
}

function pad2(value: number) {
  return value.toString().padStart(2, '0');
}

function formatRelativeTime(value: string) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 8) {
    return '刚刚';
  }
  if (seconds < 60) {
    return `${seconds} 秒前`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} 分钟前`;
  }
  return `${Math.floor(minutes / 60)} 小时前`;
}

function formatMarketActivityTime(activity: { createdAt?: string; minutesAgo?: number }) {
  if (activity.createdAt) {
    return formatRelativeTime(activity.createdAt);
  }
  if (typeof activity.minutesAgo === 'number') {
    return activity.minutesAgo <= 0 ? '刚刚' : `${activity.minutesAgo} 分钟前`;
  }
  return '刚刚';
}

function announcementKindName(kind: string) {
  const names: Record<string, string> = {
    loot: '高阶掉落',
    enhance: '强化突破',
    level: '等级提升',
    system: '世界通告',
  };
  return names[kind] ?? '世界通告';
}

function robotActivityKindName(kind: string) {
  const names: Record<string, string> = {
    dungeon: '打副本',
    enhance: '强化装备',
    legendary: '高阶掉落',
    market_watch: '逛商会',
    market_list: '上架装备',
    market_buy: '购买装备',
    market_sell: '售出装备',
    watch: '围观',
    rest: '休息',
  };
  return names[kind] ?? '后台活动';
}

function rechargeReasonName(sourceAction: string) {
  const names: Record<string, string> = {
    player_wallet: '手动充值',
    robot_enhance: '强化补金',
    robot_market_buy: '市场补金',
  };
  return names[sourceAction] ?? '充值';
}

function emptyMarketFilters(): MarketFilters {
  return {
    minLevel: '',
    maxLevel: '',
    itemType: 'all',
    quality: 'all',
    sort: 'listedAt',
  };
}

function filterMarketListings(listings: MarketListing[], filters: MarketFilters) {
  const minLevel = filterNumber(filters.minLevel);
  const maxLevel = filterNumber(filters.maxLevel);
  return listings
    .filter((listing) => {
      const item = listing.item;
      return withinRange(item.requiredLevel, minLevel, maxLevel)
        && (filters.itemType === 'all' || item.itemType === filters.itemType)
        && (filters.quality === 'all' || item.quality === filters.quality);
    })
    .sort((left, right) => {
      if (filters.sort === 'level') {
        return right.item.requiredLevel - left.item.requiredLevel
          || qualityRank(right.item.quality) - qualityRank(left.item.quality)
          || Date.parse(right.listedAt) - Date.parse(left.listedAt)
          || right.id - left.id;
      }
      if (filters.sort === 'quality') {
        return qualityRank(right.item.quality) - qualityRank(left.item.quality)
          || right.item.requiredLevel - left.item.requiredLevel
          || Date.parse(right.listedAt) - Date.parse(left.listedAt)
          || right.id - left.id;
      }
      return Date.parse(right.listedAt) - Date.parse(left.listedAt) || right.id - left.id;
    });
}

function emptyRobotFilters(): RobotFilters {
  return {
    name: '',
    minGold: '',
    maxGold: '',
    minPower: '',
    maxPower: '',
    minLevel: '',
    maxLevel: '',
  };
}

function filterRobots(robots: RobotActivityView[], filters: RobotFilters) {
  const name = filters.name.trim().toLowerCase();
  const minGold = filterNumber(filters.minGold);
  const maxGold = filterNumber(filters.maxGold);
  const minPower = filterNumber(filters.minPower);
  const maxPower = filterNumber(filters.maxPower);
  const minLevel = filterNumber(filters.minLevel);
  const maxLevel = filterNumber(filters.maxLevel);
  return robots.filter((robot) => {
    if (name && !`${robot.name} ${robot.title} ${professionName(robot.profession)}`.toLowerCase().includes(name)) {
      return false;
    }
    return withinRange(robot.gold, minGold, maxGold)
      && withinRange(robot.power, minPower, maxPower)
      && withinRange(robot.level, minLevel, maxLevel);
  });
}

function filterNumber(value: string) {
  const normalized = value.replace(/[^\d]/g, '');
  return normalized ? Number(normalized) : null;
}

function withinRange(value: number, min: number | null, max: number | null) {
  return (min === null || value >= min) && (max === null || value <= max);
}

function targetEquipSlot(item: Item, equippedItems: Record<string, Item>) {
  if (item.itemType !== 'ring') {
    return item.itemType;
  }
  if (!equippedItems.ring1) {
    return 'ring1';
  }
  if (!equippedItems.ring2) {
    return 'ring2';
  }
  return 'ring1';
}

function itemLocationLabel(item: Item, equippedItems: Record<string, Item>) {
  const equippedSlot = Object.entries(equippedItems).find(([, equippedItem]) => equippedItem.id === item.id)?.[0];
  return equippedSlot ? `已穿戴 · ${slotName(equippedSlot)}` : typeName(item.itemType);
}

function isEquipmentUpgrade(item: Item, equippedItems: Record<string, Item>) {
  const targetSlot = targetEquipSlot(item, equippedItems);
  const currentItem = equippedItems[targetSlot];
  return itemPower(item) > (currentItem ? itemPower(currentItem) : 0);
}

function toEquipmentDetail(item: Item): EquipmentDetailData {
  return {
    id: item.id,
    templateId: item.templateId,
    name: item.name,
    displayName: item.displayName,
    itemType: item.itemType,
    itemCategory: item.itemCategory,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus ?? 0,
    sellPrice: item.sellPrice,
    quantity: item.quantity,
    stackable: item.stackable,
    effectType: item.effectType,
    effectValueJson: item.effectValueJson,
    enhanceBonusRate: item.enhanceBonusRate,
    minEnhanceLevel: item.minEnhanceLevel,
    maxEnhanceLevel: item.maxEnhanceLevel,
    enhancementLevel: item.enhancementLevel,
    enhancementLuck: item.enhancementLuck,
    origin: originForItem(item.templateId),
    power: itemPower(item),
  };
}

function leaderboardEquipmentToDetail(equipment: LeaderboardEquipment): EquipmentDetailData {
  return {
    templateId: equipment.templateId,
    name: equipment.name,
    displayName: equipment.name,
    itemType: equipment.slot.startsWith('ring') ? 'ring' : equipment.slot,
    quality: equipment.quality,
    requiredLevel: equipment.level,
    attackBonus: equipment.attackBonus,
    defenseBonus: equipment.defenseBonus,
    resistanceBonus: equipment.resistanceBonus,
    hpBonus: equipment.hpBonus,
    mpBonus: equipment.mpBonus,
    critBonus: equipment.critBonus,
    sellPrice: equipment.sellPrice,
    enhancementLevel: equipment.enhancementLevel,
    enhancementLuck: equipment.enhancementLuck,
    origin: equipmentOriginText(equipment),
    power: equipment.power,
  };
}

function leaderboardEntryToSpeaker(entry: LeaderboardEntry): ChatSpeaker {
  return {
    playerId: entry.player ? 0 : null,
    name: entry.name,
    title: entry.title,
    kind: entry.player ? 'player' : 'robot',
    profession: entry.profession,
    level: entry.level,
    power: entry.power,
    experience: entry.experience,
    gold: entry.gold,
    strength: entry.strength,
    agility: entry.agility,
    constitution: entry.constitution,
    intelligence: entry.intelligence,
    spirit: entry.spirit,
    freePoints: entry.freePoints,
    derivedStats: entry.derivedStats,
    equipmentPower: entry.equipmentPower,
    equipment: entry.equipment ?? [],
  };
}

function leaderboardEquipmentBonusText(item: LeaderboardEquipment) {
  const parts = [];
  if (item.attackBonus > 0) {
    parts.push(`攻击 +${item.attackBonus}`);
  }
  if (item.defenseBonus > 0) {
    parts.push(`防御 +${item.defenseBonus}`);
  }
  if (item.resistanceBonus > 0) {
    parts.push(`抗性 +${item.resistanceBonus}`);
  }
  if (item.hpBonus > 0) {
    parts.push(`生命 +${item.hpBonus}`);
  }
  if (item.mpBonus > 0) {
    parts.push(`法力 +${item.mpBonus}`);
  }
  return parts.join(' · ') || '基础装备';
}

function marketItemSnapshotToDetail(item: MarketListing['item']): EquipmentDetailData {
  return {
    templateId: item.templateId,
    name: item.name,
    displayName: item.enhancementLevel > 0 ? `${item.name} +${item.enhancementLevel}` : item.name,
    itemType: item.itemType,
    quality: item.quality,
    requiredLevel: item.requiredLevel,
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    resistanceBonus: item.resistanceBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus,
    sellPrice: item.sellPrice,
    enhancementLevel: item.enhancementLevel,
    enhancementLuck: item.enhancementLuck,
    origin: item.origin,
    power: itemPower(item),
  };
}

function marketItemToDetail(listing: MarketListing): EquipmentDetailData {
  return marketItemSnapshotToDetail(listing.item);
}

function originForItem(templateId: string) {
  const tierMatch = templateId.match(/eq_t(\d+)/);
  const tier = tierMatch ? Number(tierMatch[1]) : 0;
  if (tier >= 37) {
    return '副本掉落 · 龙眠王庭';
  }
  if (tier >= 31) {
    return '副本掉落 · 星陨荒原';
  }
  if (tier >= 25) {
    return '副本掉落 · 黑曜山脉';
  }
  if (tier >= 19) {
    return '副本掉落 · 古堡回廊';
  }
  if (tier >= 7) {
    return '副本掉落 · 腐沼边境';
  }
  if (tier >= 1) {
    return '副本掉落 · 蛛影林地';
  }
  return '冒险者商会流通';
}

function equipmentOriginText(item: Pick<LeaderboardEquipment, 'origin' | 'templateId'>) {
  if (!item.origin || item.origin.includes(item.templateId) || item.origin.includes('eq_')) {
    return originForItem(item.templateId);
  }
  return item.origin;
}

function itemPower(item: Pick<EquipmentDetailData, 'attackBonus' | 'defenseBonus' | 'resistanceBonus' | 'hpBonus' | 'mpBonus' | 'critBonus' | 'enhancementLevel' | 'quality' | 'requiredLevel'>) {
  const level = item.enhancementLevel ?? 0;
  return Math.max(
    1,
    Math.round(
      enhancedStatValue(item.attackBonus, level) * 45
      + enhancedStatValue(item.defenseBonus, level) * 30
      + enhancedStatValue(item.resistanceBonus, level) * 30
      + enhancedStatValue(item.hpBonus, level) * 4
      + enhancedStatValue(item.mpBonus, level) * 2
      + enhancedCritValue(item.critBonus ?? 0, level) * 3000
      + level * 100
      + Math.max(1, item.requiredLevel) * 20
      + qualityRank(item.quality) * 60,
    ),
  );
}

function enhancedStatValue(value: number, level: number) {
  if (value <= 0) {
    return 0;
  }
  let result = Math.round(value * (1 + level * 0.03));
  if (level >= 5) {
    result += Math.max(1, Math.floor(value / 10));
  }
  if (level >= 10) {
    result += Math.max(1, Math.floor(value / 8));
  }
  if (level >= 15) {
    result += Math.max(1, Math.floor(value / 5));
  }
  return result;
}

function enhancedCritValue(value: number, level: number) {
  let result = value * (1 + level * 0.03);
  if (level >= 10) {
    result += 0.01;
  }
  if (level >= 15) {
    result += 0.02;
  }
  return result;
}

function marketPriceEstimate(item: Pick<EquipmentDetailData, 'attackBonus' | 'defenseBonus' | 'resistanceBonus' | 'hpBonus' | 'mpBonus' | 'critBonus' | 'enhancementLevel' | 'quality' | 'sellPrice' | 'requiredLevel'>) {
  const statScore = item.attackBonus * 16
    + item.defenseBonus * 12
    + item.resistanceBonus * 10
    + item.hpBonus / 2
    + item.mpBonus / 2
    + (item.critBonus ?? 0) * 1200
    + item.enhancementLevel * 80;
  const rank = qualityRank(item.quality);
  return Math.max(30, Math.round(item.sellPrice * 8 + statScore * 2 + item.requiredLevel * 35 + rank * rank * 55));
}

function enhanceCost(item: Item) {
  const nextLevel = item.enhancementLevel + 1;
  return Math.max(1, item.requiredLevel) * Math.max(1, item.requiredLevel) * nextLevel * 10;
}

function enhanceChance(item: Item) {
  const nextLevel = item.enhancementLevel + 1;
  const base = nextLevel <= 3 ? 1 : nextLevel <= 6 ? 0.8 : nextLevel <= 9 ? 0.6 : nextLevel <= 12 ? 0.4 : 0.2;
  return Math.min(0.95, base + item.enhancementLuck * 0.05);
}

function bonusText(item: Item | EquipmentDetailData) {
  const parts = [
    item.attackBonus > 0 ? `攻击 +${item.attackBonus}` : '',
    item.defenseBonus > 0 ? `防御 +${item.defenseBonus}` : '',
    item.resistanceBonus > 0 ? `抗性 +${item.resistanceBonus}` : '',
    item.hpBonus > 0 ? `生命 +${item.hpBonus}` : '',
    item.mpBonus > 0 ? `法力 +${item.mpBonus}` : '',
    item.enhancementLevel > 0 ? `强化 +${item.enhancementLevel}` : '',
  ].filter(Boolean);
  return parts.join(' · ') || '基础装备';
}

function dropBonusText(drop: DropPreview) {
  const parts = [
    drop.attackBonus > 0 ? `攻击 +${drop.attackBonus}` : '',
    drop.defenseBonus > 0 ? `防御 +${drop.defenseBonus}` : '',
    drop.resistanceBonus > 0 ? `抗性 +${drop.resistanceBonus}` : '',
    drop.hpBonus > 0 ? `生命 +${drop.hpBonus}` : '',
    drop.mpBonus > 0 ? `法力 +${drop.mpBonus}` : '',
  ].filter(Boolean);
  return parts.join(' · ') || '基础装备';
}
