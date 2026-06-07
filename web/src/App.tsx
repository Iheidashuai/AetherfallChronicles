import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowUp,
  Backpack,
  Bell,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Clock3,
  Coins,
  FastForward,
  Gem,
  Gauge,
  HeartPulse,
  LogOut,
  MessageCircle,
  Package,
  ScrollText,
  Send,
  Shield,
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
  LeaderboardEntry,
  LeaderboardEquipment,
  MarketListing,
  MarketSale,
  QuestRow,
  RobotActivityDetail,
  RobotActivityEvent,
  RobotActivityView,
} from './api';
import { useAppStore } from './store';

type InventoryAction = 'equip' | 'sell' | 'enhance';
type FeedbackVariant = 'success' | 'error';
type DungeonClearFilter = 'all' | 'uncleared' | 'cleared';
type DungeonRiskFilter = 'all' | 'safe' | 'normal' | 'risky' | 'deadly';
type LeaderboardMetric = 'power' | 'gold' | 'level';
type ProfessionFilter = 'all' | 'warrior' | 'mage' | 'ranger';
type MarketSortKey = 'listedAt' | 'level' | 'quality';
type MarketItemTypeFilter = 'all' | 'weapon' | 'helmet' | 'armor' | 'legs' | 'boots' | 'gloves' | 'necklace' | 'ring';
type MarketQualityFilter = 'all' | 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary';
type MarketLedgerTab = 'listed' | 'sold';
type RobotFilterKey = 'name' | 'minGold' | 'maxGold' | 'minPower' | 'maxPower' | 'minLevel' | 'maxLevel';

type RobotFilters = Record<RobotFilterKey, string>;

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
  quality: string;
  requiredLevel: number;
  attackBonus: number;
  defenseBonus: number;
  hpBonus: number;
  mpBonus: number;
  critBonus?: number;
  sellPrice: number;
  enhancementLevel: number;
  enhancementLuck?: number;
  origin?: string;
  power?: number;
};

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
      <div className="phone-frame">
        {token && screen !== 'auth' && <GlobalTicker announcements={announcementsQuery.data ?? []} />}
        <div className="screen-host">
          {screen === 'auth' && <AuthScreen />}
          {screen === 'create-player' && token && <CreatePlayerScreen token={token} />}
          {screen === 'home' && token && <HomeScreen token={token} />}
          {screen === 'character' && token && <CharacterScreen token={token} />}
          {screen === 'inventory' && token && <InventoryScreen token={token} />}
          {screen === 'quests' && token && <QuestScreen token={token} />}
          {screen === 'market' && token && <MarketScreen token={token} />}
          {screen === 'chat' && token && <ChatScreen token={token} />}
          {screen === 'leaderboard' && token && <LeaderboardScreen token={token} />}
          {screen === 'robots' && token && <RobotActivityScreen token={token} />}
          {screen === 'dungeons' && token && (
            <DungeonScreen
              token={token}
              onResult={(result) => {
                setLastResult(result);
                setScreen('result');
              }}
            />
          )}
          {screen === 'result' && lastResult && <ResultScreen result={lastResult} />}
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
  const visibleAnnouncements = announcements.filter((announcement) => announcement.kind !== 'level');
  const items = visibleAnnouncements.length > 0 ? visibleAnnouncements : [{
    id: 0,
    kind: 'system',
    actorName: '银冠公会',
    text: '世界通告接入中，远征记录会在这里滚动。',
    priority: 0,
    createdAt: new Date().toISOString(),
  }];
  return (
    <div className="global-ticker">
      <div className="global-ticker-label">
        <Bell size={16} />
        <strong>全服通告</strong>
      </div>
      <div className="global-ticker-window">
        <div className="global-ticker-track">
          {[...items, ...items].map((item, index) => (
            <span key={`${item.id}-${index}`}>
              <b>{announcementKindName(item.kind)}</b>
              {item.text}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function CreatePlayerScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [name, setName] = useState('灰烬行者');
  const [profession, setProfession] = useState('warrior');
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => gameApi.createPlayer(token, name, profession),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['home', token] });
      setScreen('home');
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <section className="screen result-screen">
      <TopBar title="创建角色" />
      <div className="panel lead-panel">
        <UserRound size={28} />
        <h2>{professionName(profession)}</h2>
        <p>角色会立即写入 MySQL，并由服务端发放初始装备。</p>
      </div>
      <div className="segmented three">
        <button className={profession === 'warrior' ? 'active' : ''} onClick={() => setProfession('warrior')}>战士</button>
        <button className={profession === 'ranger' ? 'active' : ''} onClick={() => setProfession('ranger')}>射手</button>
        <button className={profession === 'mage' ? 'active' : ''} onClick={() => setProfession('mage')}>法师</button>
      </div>
      <label>
        角色名
        <input value={name} onChange={(event) => setName(event.target.value)} />
      </label>
      {error && <FeedbackDialog variant="error" title="创建失败" message={error} onClose={() => setError(null)} />}
      <button className="primary-action" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
        {mutation.isPending ? '创建中...' : '开始冒险'}
        <ChevronRight size={18} />
      </button>
    </section>
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
          <Metric label="金币" value={data.player.gold.toString()} />
          <Metric label="背包" value={`${data.inventoryCount}/${data.inventoryCapacity}`} />
          <Metric label="任务" value={`${data.config.questCount}`} />
        </div>
      </div>

      <div className="home-workbench">
        <aside className="home-column">
          <EquipmentPanel home={data} onSelect={setSelectedEquipment} />
        </aside>
        <section className="home-column">
          <div className="nav-grid">
            <NavTile icon={<Swords size={20} />} title="副本" detail={`${data.config.dungeonCount} 个副本`} onClick={() => setScreen('dungeons')} />
            <NavTile icon={<Backpack size={20} />} title="背包" detail="穿戴 · 出售 · 强化" onClick={() => setScreen('inventory')} />
            <NavTile icon={<ScrollText size={20} />} title="任务" detail="主线 · 日常 · 成就" onClick={() => setScreen('quests')} />
            <NavTile icon={<ShoppingBag size={20} />} title="市场" detail="寄售 · 购买" onClick={() => setScreen('market')} />
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
  return (
    <section className="screen character-screen">
      <TopBar title="角色档案" onBack={() => setScreen('home')} />
      <div className="profile-header">
        <UserRound size={34} />
        <div>
          <span className="eyebrow">Lv.{player.level} {professionName(player.profession)}</span>
          <h1>{player.name}</h1>
        </div>
      </div>
      <div className="stat-grid">
        <Metric label="经验" value={player.experience.toString()} />
        <Metric label="自由点" value={player.freePoints.toString()} />
        <Metric label="生命" value={formatNumber(data.maxHp)} />
        <Metric label="法力" value={formatNumber(data.maxMp)} />
      </div>
      <div className="attribute-grid">
        <Metric label="力量" value={player.strength.toString()} />
        <Metric label="敏捷" value={player.agility.toString()} />
        <Metric label="体质" value={player.constitution.toString()} />
        <Metric label="智力" value={player.intelligence.toString()} />
        <Metric label="精神" value={player.spirit.toString()} />
        <Metric label="战力" value={data.combatPower.toString()} />
      </div>
      <EquipmentPanel home={data} onSelect={setSelectedEquipment} />
      {selectedEquipment && <ItemDetail item={toEquipmentDetail(selectedEquipment)} onClose={() => setSelectedEquipment(null)} />}
    </section>
  );
}

function DungeonScreen({ token, onResult }: { token: string; onResult: (result: DungeonRunResult) => void }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [clearFilter, setClearFilter] = useState<DungeonClearFilter>('all');
  const [riskFilter, setRiskFilter] = useState<DungeonRiskFilter>('all');
  const [sweepResult, setSweepResult] = useState<DungeonSweepResult | null>(null);
  const [selectedLoot, setSelectedLoot] = useState<Item | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['dungeons', token],
    queryFn: () => gameApi.dungeons(token),
  });
  const homeQuery = useQuery({
    queryKey: ['home', token],
    queryFn: () => gameApi.home(token),
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

  if (isLoading) {
    return <LoadingScreen title="读取副本情报" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '副本加载失败'} />;
  }
  const combatPower = homeQuery.data?.combatPower ?? 0;
  const visibleDungeons = data.filter((dungeon) => {
    const risk = dungeonRisk(combatPower, dungeon.recommendedPower).level as DungeonRiskFilter;
    const matchesClear = clearFilter === 'all' || (clearFilter === 'cleared' ? dungeon.cleared : !dungeon.cleared);
    const matchesRisk = riskFilter === 'all' || risk === riskFilter;
    return matchesClear && matchesRisk;
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
      <div className="dungeon-filter-panel">
        <div className="segmented three">
          {[
            ['all', '全部'],
            ['uncleared', '未通过'],
            ['cleared', '已通过'],
          ].map(([value, label]) => (
            <button key={value} className={clearFilter === value ? 'active' : ''} onClick={() => setClearFilter(value as DungeonClearFilter)}>
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
            <button key={value} className={riskFilter === value ? `active ${value}` : value} onClick={() => setRiskFilter(value as DungeonRiskFilter)}>
              {label}
            </button>
          ))}
        </div>
        <strong>{visibleDungeons.length}/{data.length} 个副本</strong>
      </div>
      <div className="dungeon-list">
        {visibleDungeons.length === 0 && <EmptyState text="当前筛选下没有副本，换个风险档再看。" />}
        {visibleDungeons.map((dungeon) => (
          <DungeonCard
            key={dungeon.id}
            dungeon={dungeon}
            combatPower={combatPower}
            loading={mutation.isPending}
            sweepLoading={sweepMutation.isPending}
            onRun={() => mutation.mutate(dungeon.id)}
            onSweep={() => sweepMutation.mutate(dungeon.id)}
          />
        ))}
      </div>
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

function ResultScreen({ result }: { result: DungeonRunResult }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const token = useAppStore((state) => state.token);
  const [speed, setSpeed] = useState(1);
  const frames = useMemo(() => battleFramesForResult(result), [result]);
  const [visibleFrames, setVisibleFrames] = useState(1);
  const [selectedLoot, setSelectedLoot] = useState<Item | null>(null);
  const [showSummary, setShowSummary] = useState(false);
  const battleStageRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setVisibleFrames(1);
    setSelectedLoot(null);
    setShowSummary(false);
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

  async function returnHome() {
    if (token) {
      await invalidateGameQueries(queryClient, token);
    }
    setScreen('home');
  }

  const currentFrame = frames[Math.min(visibleFrames - 1, frames.length - 1)];
  const currentEnemy = currentFrame.enemyName ?? (result.success ? '区域已肃清' : '推进中断');

  return (
    <section className="screen result-screen combat-screen">
      <TopBar title="副本战报" onBack={returnHome} />

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
          <div key={`${frame.index}-${frame.text}`} className={`battle-line ${frame.tone || battleLogTone(frame.text)}`}>
            <span>#{frame.index}</span>
            <p>{frame.text}</p>
          </div>
        ))}
      </div>
      {showSummary && (
        <ResultSummaryModal
          result={result}
          onClose={() => setShowSummary(false)}
          onReturn={returnHome}
          onSelectLoot={setSelectedLoot}
        />
      )}
      {selectedLoot && <ItemDetail item={toEquipmentDetail(selectedLoot)} onClose={() => setSelectedLoot(null)} />}
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
    mutationFn: (itemId: number) => gameApi.enhance(token, itemId),
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
  const activeTypes = itemTypesForCategory(category);
  const busy = equipMutation.isPending || unequipMutation.isPending || sellMutation.isPending || enhanceMutation.isPending || bulkSellMutation.isPending || organizeMutation.isPending;
  const inventoryActionError =
    equipMutation.error?.message ??
    unequipMutation.error?.message ??
    sellMutation.error?.message ??
    enhanceMutation.error?.message ??
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
    unequipMutation.reset();
    sellMutation.reset();
    enhanceMutation.reset();
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
            <SectionTitle icon={<Sparkles size={18} />} title={`${categoryNameForInventory(category)}装备`} />
            <strong>{visibleInventory.length} 件</strong>
          </div>
          <div className="item-grid">
            {visibleInventory.length === 0 && <EmptyState text="当前分类没有可操作装备。" />}
            {visibleInventory.map((item) => (
              <ItemCard
                key={item.id}
                item={item}
                powerIncrease={isEquipmentUpgrade(item, data.equippedItems)}
                onSelect={() => setSelectedItem(item)}
              >
                <button className="mini-action" disabled={busy} onClick={(event) => {
                  event.stopPropagation();
                  setEquipCandidate(item);
                }}>穿戴</button>
                <button className="mini-action" disabled={busy} onClick={(event) => {
                  event.stopPropagation();
                  setEnhanceMessage(null);
                  setEnhanceItem(item);
                }}>强化</button>
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
          <div className="segmented filter-tabs">
            {[
              ['all', '全部'],
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
          <SectionTitle icon={<Coins size={18} />} title="按品质卖出" />
          <div className="quality-sell-grid">
            {[
              ['common', '普通'],
              ['uncommon', '优秀'],
              ['rare', '稀有'],
              ['epic', '史诗'],
              ['legendary', '传说'],
            ].map(([quality, label]) => (
              <button
                key={quality}
                className={`quality-sell ${quality}`}
                disabled={busy}
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
          }}
          onEnhance={() => enhanceMutation.mutate(enhanceItem.id)}
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

function QuestScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
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

  if (isLoading) {
    return <LoadingScreen title="读取任务档案" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '任务加载失败'} />;
  }

  return (
    <section className="screen market-screen">
      <TopBar title="任务" onBack={() => setScreen('home')} />
      <div className="quest-tabs">
        <Metric label="可领取" value={data.filter((quest) => quest.status === 'completed').length.toString()} />
        <Metric label="进行中" value={data.filter((quest) => quest.status === 'active').length.toString()} />
        <Metric label="已领取" value={data.filter((quest) => quest.status === 'claimed').length.toString()} />
      </div>
      <div className="quest-list">
        {data.map((quest) => (
          <QuestCard
            key={quest.id}
            quest={quest}
            loading={claimMutation.isPending}
            onClaim={() => claimMutation.mutate(quest.id)}
            onNavigate={() => setScreen(screenForQuestTarget(quest.navigationTarget))}
          />
        ))}
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
  const inventoryItems = inventoryQuery.data.inventory.slice(0, 10);
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
  const peakPower = Math.max(...filteredRobots.map((robot) => robot.power), 0);

  return (
    <section className="screen robot-screen">
      <TopBar title="机器人后台" onBack={() => setScreen('home')} />
      <div className="stat-grid robot-stats">
        <Metric label="机器人" value={hasRobotFilters ? `${filteredRobots.length}/${data.robots.length}` : data.robots.length.toString()} />
        <Metric label="正在行动" value={activeCount.toString()} />
        <Metric label="商会相关" value={marketCount.toString()} />
        <Metric label="机器人金币" value={`${formatNumber(totalGold)} 金`} />
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
        <span className="eyebrow">{robot.title} · Lv.{robot.level} {professionName(robot.profession)}</span>
        <h2>{robot.name}</h2>
      </div>
      <p>{robot.currentActivityText}</p>
      <div className="robot-meta">
        <span>{robotActivityKindName(robot.currentActivityKind)}</span>
        <span>战力 {formatNumber(robot.power)}</span>
        <span>{formatNumber(robot.gold)} 金</span>
        <span>{formatRelativeTime(robot.currentActivityAt)}</span>
      </div>
      <div className="robot-progress-row">
        <small>副本 {robot.dungeonClears}</small>
        <small>强化峰值 +{robot.peakEnhancement}</small>
        <small>传说 {robot.legendaryLootCount}</small>
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
            <p>Lv.{robot.level} · 战力 {formatNumber(robot.power)} · 金币 {formatNumber(robot.gold)} 金</p>
          </div>
          <button className="text-button close-button" onClick={onClose}>关闭</button>
        </div>
        <div className="robot-detail-layout">
          <div className="robot-detail-profile">
            <div className="result-modal-metrics">
              <Metric label="副本次数" value={formatNumber(robot.dungeonClears)} />
              <Metric label="强化峰值" value={`+${robot.peakEnhancement}`} />
              <Metric label="传说获得" value={formatNumber(robot.legendaryLootCount)} />
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
                <article key={`${robot.id}-${item.slot}`} className={`robot-equipment-card ${item.quality} ${enhancementEffectClass(item)}`}>
                  <div className="item-meta-line">
                    <span>{item.slotName} · Lv.{item.level} · +{item.enhancementLevel}</span>
                    <EnhancementBadge level={item.enhancementLevel} />
                  </div>
                  <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
                  <p>{leaderboardEquipmentBonusText(item)}</p>
                  <small>战力 {formatNumber(item.power)} · {item.origin}</small>
                </article>
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
      </section>
    </div>
  );
}

function ChatScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const [selectedSpeaker, setSelectedSpeaker] = useState<ChatSpeaker | null>(null);
  const chatListRef = useRef<HTMLDivElement | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['chat', token],
    queryFn: () => gameApi.chatMessages(token),
    refetchInterval: 3000,
  });
  const sendMutation = useMutation({
    mutationFn: () => gameApi.sendChat(token, text),
    onSuccess: async () => {
      setText('');
      await invalidateGameQueries(queryClient, token);
      await queryClient.invalidateQueries({ queryKey: ['chat', token] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!text.trim()) {
      return;
    }
    sendMutation.mutate();
  }

  useEffect(() => {
    const node = chatListRef.current;
    if (!node || !data) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [data]);

  if (isLoading) {
    return <LoadingScreen title="接入传讯水晶" />;
  }
  if (error || !data) {
    return <ErrorScreen message={(error as Error)?.message ?? '聊天加载失败'} />;
  }

  return (
    <section className="screen chat-screen">
      <TopBar title="世界聊天" onBack={() => setScreen('home')} />
      <div className="channel-strip">
        <span>世界</span>
        <strong>{data.filter((message) => message.kind === 'robot').length}+ 在线发言</strong>
      </div>
      <div className="chat-list" ref={chatListRef}>
        {data.map((message) => (
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
  return (
    <article className={`chat-message ${message.kind}`}>
      <div className="chat-message-head">
        <button className="speaker-button" disabled={isSystem} onClick={() => onSelectSpeaker(speaker)}>
          {speaker.name || message.senderName}
        </button>
        <span>{speaker.title}</span>
        {!isSystem && <small>Lv.{speaker.level} · 战力 {speaker.power}</small>}
        <time dateTime={message.createdAt}>
          <Clock3 size={12} />
          {formatChatTime(message.createdAt)}
        </time>
      </div>
      <p>{message.text}</p>
    </article>
  );
}

function SpeakerDetailModal({ speaker, onClose }: { speaker: ChatSpeaker; onClose: () => void }) {
  const equippedCount = speaker.equipment.length;
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
        </div>
        <SectionTitle icon={<Shield size={18} />} title="装备栏" />
        <div className="speaker-equipment-grid">
          {speaker.equipment.length === 0 && <EmptyState text="暂无可查看装备。" />}
          {speaker.equipment.map((item) => (
            <article key={`${speaker.name}-${item.slot}`} className={`speaker-equipment-card ${item.quality} ${enhancementEffectClass(item)}`}>
              <div className="item-meta-line">
                <span>{item.slotName} · Lv.{item.level} · +{item.enhancementLevel}</span>
                <EnhancementBadge level={item.enhancementLevel} />
              </div>
              <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
              <p>{leaderboardEquipmentBonusText(item)}</p>
              <small>战力 {item.power} · {item.origin}</small>
            </article>
          ))}
        </div>
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

function DungeonCard({ dungeon, combatPower, loading, sweepLoading, onRun, onSweep }: {
  dungeon: Dungeon;
  combatPower: number;
  loading: boolean;
  sweepLoading: boolean;
  onRun: () => void;
  onSweep: () => void;
}) {
  const risk = dungeonRisk(combatPower, dungeon.recommendedPower);
  const drops = dungeon.drops ?? [];
  const dropTypes = uniqueDropTypes(drops);
  return (
    <article className={`dungeon-card ${risk.level}`}>
      <div className="dungeon-card-head">
        <div>
          <span className="eyebrow">{dungeon.difficulty} · 推荐 Lv.{dungeon.recommendedLevel}</span>
          <h2>{dungeon.name}</h2>
          <p>{dungeon.description}</p>
        </div>
        <strong className={`risk-pill ${risk.level}`}>{risk.label}</strong>
      </div>
      <div className="dungeon-meta">
        <span>推荐 {dungeon.recommendedPower}</span>
        <span className={dungeon.cleared ? 'clear-state cleared' : 'clear-state'}>{dungeon.cleared ? '已通过' : '未通过'}</span>
        <strong>{drops.length} 件可掉落</strong>
      </div>
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
        <button className="compact-action" disabled={loading} onClick={onRun}>
          {loading ? '战斗中' : '进入'}
        </button>
        <button className="compact-action sweep-compact" disabled={loading || sweepLoading || !dungeon.cleared} onClick={onSweep}>
          {sweepLoading ? '扫荡中' : '扫荡 10 次'}
        </button>
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

function EquipmentPanel({ home, onSelect }: { home: HomeSnapshot; onSelect?: (item: Item) => void }) {
  const slots = useMemo(() => equipmentSlotOrder().map((slot) => [slot, home.equippedItems[slot] ?? null] as const), [home.equippedItems]);
  const equippedCount = slots.filter(([, item]) => Boolean(item)).length;
  return (
    <div className="panel equipment-panel">
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
                <small>{bonusText(item)}</small>
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

function QuestCard({ quest, loading, onClaim, onNavigate }: {
  quest: QuestRow;
  loading: boolean;
  onClaim: () => void;
  onNavigate: () => void;
}) {
  const progress = Math.min(100, Math.round((quest.currentValue / Math.max(1, quest.targetValue)) * 100));
  return (
    <article className={`quest-card ${quest.status}`}>
      <div className="quest-heading">
        <div>
          <span className="eyebrow">{categoryName(quest.category)} · {statusName(quest.status)}</span>
          <h2>{quest.title}</h2>
        </div>
        <strong>{quest.currentValue}/{quest.targetValue}</strong>
      </div>
      <p>{quest.description}</p>
      <div className="progress-bar" aria-label="任务进度">
        <span style={{ width: `${progress}%` }} />
      </div>
      <div className="reward-row">
        {quest.rewards.map((reward, index) => (
          <span key={`${reward.type}-${reward.targetId ?? index}`}>{rewardName(reward.type, reward.amount)}</span>
        ))}
      </div>
      <div className="action-row">
        {quest.status === 'completed' && <button className="mini-action" disabled={loading} onClick={onClaim}>领取</button>}
        {quest.status === 'active' && <button className="mini-action subtle" onClick={onNavigate}>前往</button>}
      </div>
    </article>
  );
}

function equipmentDisplayName(item: { name: string; displayName?: string; enhancementLevel?: number }) {
  const baseName = (item.displayName?.trim() || item.name).replace(/\s\+\d+$/, '');
  const level = item.enhancementLevel ?? 0;
  return level > 0 ? `${baseName} +${level}` : baseName;
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
    <article className={`item-card ${enhancementEffectClass(item)} ${onSelect ? 'selectable' : ''}`} onClick={onSelect}>
      {powerIncrease && (
        <span className="power-up-indicator" aria-label="穿戴后战力提升" title="穿戴后战力提升">
          <ArrowUp size={16} strokeWidth={3} />
        </span>
      )}
      <div className="item-main">
        <Package size={18} />
        <div>
          <div className="item-meta-line">
            <span className="eyebrow">{label ?? typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
            <EnhancementBadge level={item.enhancementLevel} />
          </div>
          <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
          <p>{bonusText(item)}</p>
        </div>
      </div>
      {children && <div className="inline-actions">{children}</div>}
    </article>
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
          <span className="eyebrow">{qualityName(item.quality)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
          <EnhancementBadge level={item.enhancementLevel} />
        </div>
        <h2 className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</h2>
        <div className="detail-stat-grid">
          <Metric label="攻击" value={formatNumber(item.attackBonus)} />
          <Metric label="防御" value={formatNumber(item.defenseBonus)} />
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
          <small>模板 {item.templateId}</small>
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

function CompareCard({ title, item, highlight = false }: { title: string; item: Item | null; highlight?: boolean }) {
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
          <h2>空槽位</h2>
          <p>穿戴后将直接补齐该部位。</p>
          <small>无来源</small>
        </>
      )}
    </article>
  );
}

function EnhanceModal({ item, gold, message, loading, onClose, onEnhance }: {
  item: Item;
  gold: number;
  message: string | null;
  loading: boolean;
  onClose: () => void;
  onEnhance: () => void;
}) {
  const nextLevel = item.enhancementLevel + 1;
  const cost = enhanceCost(item);
  const chance = enhanceChance(item);
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
        </div>
        {message && <div className="modal-notice">{message}</div>}
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

function ResultSummaryModal({ result, onClose, onReturn, onSelectLoot }: {
  result: DungeonRunResult;
  onClose: () => void;
  onReturn: () => void | Promise<void>;
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
          <Metric label="掉落装备" value={result.loot.length.toString()} />
        </div>
        <SectionTitle icon={<Gem size={18} />} title="掉落明细" />
        <div className="result-loot-detail-grid">
          {result.loot.length === 0 && <EmptyState text="这次没有装备掉落，可以换高掉率副本继续刷。" />}
          {result.loot.map((item) => (
            <button key={item.id} className={`loot-detail-card ${item.quality}`} onClick={() => onSelectLoot(item)}>
              <span>{qualityName(item.quality)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
              <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
              <p>{bonusText(item)}</p>
              <small>售价 {item.sellPrice} 金</small>
            </button>
          ))}
        </div>
        <div className="result-modal-actions">
          <button className="mini-action subtle" onClick={onClose}>继续看战报</button>
          <button className="primary-action" onClick={() => void onReturn()}>回到首页</button>
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
          <Metric label="掉落装备" value={result.loot.length.toString()} />
        </div>
        <div className="sweep-log">
          {result.logs.map((log) => <p key={log}>{log}</p>)}
        </div>
        <SectionTitle icon={<Gem size={18} />} title="扫荡掉落" />
        <div className="result-loot-detail-grid">
          {result.loot.length === 0 && <EmptyState text="十次扫荡没有装备掉落，下一轮可能会转运。" />}
          {result.loot.map((item) => (
            <button key={item.id} className={`loot-detail-card ${item.quality}`} onClick={() => onSelectLoot(item)}>
              <span>{qualityName(item.quality)} · {typeName(item.itemType)} · Lv.{item.requiredLevel}</span>
              <strong className={`quality ${item.quality}`}>{equipmentDisplayName(item)}</strong>
              <p>{bonusText(item)}</p>
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
          <p>{listing.item.attackBonus > 0 ? `攻击 +${listing.item.attackBonus}` : ''} {listing.item.defenseBonus > 0 ? `防御 +${listing.item.defenseBonus}` : ''} {listing.item.hpBonus > 0 ? `生命 +${listing.item.hpBonus}` : ''}</p>
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

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
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
    queryClient.invalidateQueries({ queryKey: ['inventory', token] }),
    queryClient.invalidateQueries({ queryKey: ['quests', token] }),
    queryClient.invalidateQueries({ queryKey: ['leaderboard', token] }),
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
  return (result.logs?.length ? result.logs : ['没有战斗记录']).map((log, index) => ({
    index: index + 1,
    text: log,
    tone: battleLogTone(log),
    roomLabel: '战斗记录',
    enemyName: undefined,
    playerHp: result.playerFinalHp || maxHp,
    playerMaxHp: maxHp,
    enemyHp: 0,
    enemyMaxHp: 0,
  }));
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
  return slotName(type === 'ring' ? 'ring1' : type);
}

function uniqueDropTypes(drops: DropPreview[]) {
  return Array.from(new Set(drops.map((drop) => drop.itemType)))
    .sort((left, right) => dropTypeRank(left) - dropTypeRank(right));
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

function rewardName(type: string, amount: number) {
  const names: Record<string, string> = {
    gold: '金币',
    experience: '经验',
    itemTemplate: '装备',
  };
  return `${names[type] ?? type} +${amount}`;
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

function dungeonRisk(combatPower: number, recommendedPower: number) {
  if (!combatPower) {
    return { label: '读取中', level: 'unknown' };
  }
  const ratio = combatPower / Math.max(1, recommendedPower);
  if (ratio >= 1.2) {
    return { label: '碾压', level: 'safe' };
  }
  if (ratio >= 0.95) {
    return { label: '稳妥', level: 'normal' };
  }
  if (ratio >= 0.75) {
    return { label: '危险', level: 'risky' };
  }
  return { label: '极危', level: 'deadly' };
}

function qualityName(quality: string) {
  const names: Record<string, string> = {
    common: '普通',
    uncommon: '优秀',
    rare: '稀有',
    epic: '史诗',
    legendary: '传说',
  };
  return names[quality] ?? quality;
}

function battleLogTone(log: string) {
  if (log.includes('倒下') || log.includes('中止') || log.includes('危险')) {
    return 'danger';
  }
  if (log.includes('获得装备')) {
    return 'loot';
  }
  if (log.includes('击败') || log.includes('暴击')) {
    return 'hit';
  }
  return '';
}

function itemMatchesCategory(item: Item, category: string) {
  return category === 'all' || itemTypesForCategory(category).includes(item.itemType);
}

function itemTypesForCategory(category: string) {
  const map: Record<string, string[]> = {
    weapon: ['weapon'],
    armor: ['helmet', 'armor', 'legs', 'boots', 'gloves'],
    accessory: ['necklace', 'ring'],
  };
  return map[category] ?? [];
}

function categoryNameForInventory(category: string) {
  const names: Record<string, string> = {
    all: '全部',
    weapon: '武器',
    armor: '防具',
    accessory: '饰品',
  };
  return names[category] ?? '全部';
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
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
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
    loot: '传说掉落',
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
    legendary: '传说掉落',
    market_watch: '逛商会',
    market_list: '上架装备',
    market_buy: '购买装备',
    market_sell: '售出装备',
    watch: '围观',
    rest: '休息',
  };
  return names[kind] ?? '后台活动';
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
    quality: item.quality,
    requiredLevel: item.requiredLevel,
    attackBonus: item.attackBonus,
    defenseBonus: item.defenseBonus,
    hpBonus: item.hpBonus,
    mpBonus: item.mpBonus,
    critBonus: item.critBonus ?? 0,
    sellPrice: item.sellPrice,
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
    hpBonus: equipment.hpBonus,
    mpBonus: equipment.mpBonus,
    critBonus: equipment.critBonus,
    sellPrice: equipment.sellPrice,
    enhancementLevel: equipment.enhancementLevel,
    enhancementLuck: equipment.enhancementLuck,
    origin: equipment.origin,
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

function itemPower(item: Pick<EquipmentDetailData, 'attackBonus' | 'defenseBonus' | 'hpBonus' | 'mpBonus' | 'critBonus' | 'enhancementLevel' | 'quality'>) {
  return Math.max(
    1,
    Math.round(
      item.attackBonus * 12
      + item.defenseBonus * 8
      + item.hpBonus / 2
      + item.mpBonus / 2
      + (item.critBonus ?? 0) * 900
      + item.enhancementLevel * 18
      + qualityRank(item.quality) * 12,
    ),
  );
}

function marketPriceEstimate(item: Pick<EquipmentDetailData, 'attackBonus' | 'defenseBonus' | 'hpBonus' | 'mpBonus' | 'critBonus' | 'enhancementLevel' | 'quality' | 'sellPrice' | 'requiredLevel'>) {
  const statScore = item.attackBonus * 16
    + item.defenseBonus * 12
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
  return Math.min(1, base + item.enhancementLuck * 0.05);
}

function bonusText(item: Item) {
  const parts = [
    item.attackBonus > 0 ? `攻击 +${item.attackBonus}` : '',
    item.defenseBonus > 0 ? `防御 +${item.defenseBonus}` : '',
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
    drop.hpBonus > 0 ? `生命 +${drop.hpBonus}` : '',
    drop.mpBonus > 0 ? `法力 +${drop.mpBonus}` : '',
  ].filter(Boolean);
  return parts.join(' · ') || '基础装备';
}
