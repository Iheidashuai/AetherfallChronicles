import { FormEvent, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Coins, LogOut, MessageCircle, Send, Shield, Skull, Swords, Trophy, UserRound } from 'lucide-react';
import { gameApi } from '../api';
import type { ChatMessage, GuildBossView, GuildSummary, GuildView } from '../api';
import { useAppStore } from '../store';
import { EmptyState, ErrorScreen, LoadingScreen, SectionTitle, TopBar } from '../components/ui';

const PROFESSION_NAMES: Record<string, string> = { warrior: '战士', ranger: '游侠', mage: '法师' };

export function GuildScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const pendingWorldEventAction = useAppStore((state) => state.pendingWorldEventAction);
  const clearPendingWorldEventAction = useAppStore((state) => state.clearPendingWorldEventAction);
  const queryClient = useQueryClient();
  const [focusBoss, setFocusBoss] = useState(false);
  const homeQuery = useQuery({ queryKey: ['guild', 'home'], queryFn: () => gameApi.myGuild(token) });
  const guild = homeQuery.data?.guild ?? null;

  useEffect(() => {
    if (pendingWorldEventAction?.targetScreen !== 'guild') {
      return;
    }
    setFocusBoss(pendingWorldEventAction.params?.focus === 'boss');
    clearPendingWorldEventAction();
  }, [pendingWorldEventAction, clearPendingWorldEventAction]);

  if (homeQuery.isLoading) {
    return <LoadingScreen title="公会" />;
  }
  if (homeQuery.isError) {
    return <ErrorScreen message={(homeQuery.error as Error)?.message ?? '公会数据加载失败'} />;
  }

  return (
    <div className="screen guild-screen">
      <TopBar title="公会" onBack={() => setScreen('home')} />
      {guild ? (
        <GuildHall token={token} view={guild} focusBoss={focusBoss} onChanged={() => queryClient.invalidateQueries({ queryKey: ['guild'] })} />
      ) : (
        <GuildBrowser token={token} onJoined={() => queryClient.invalidateQueries({ queryKey: ['guild'] })} />
      )}
    </div>
  );
}

function GuildBrowser({ token, onJoined }: { token: string; onJoined: () => void }) {
  const listQuery = useQuery({ queryKey: ['guild', 'browse'], queryFn: () => gameApi.guildBrowse(token) });
  const joinMutation = useMutation({
    mutationFn: (guildId: number) => gameApi.joinGuild(token, guildId),
    onSuccess: onJoined,
  });

  if (listQuery.isLoading) {
    return <LoadingScreen title="公会列表" />;
  }
  const guilds = listQuery.data ?? [];

  return (
    <div className="guild-browse">
      <SectionTitle icon={<Shield size={16} />} title="加入一个公会" />
      <p className="guild-hint">和约 20 名公会成员一起捐献、刷公会 Boss、冲公会榜。选一个加入吧。</p>
      <div className="guild-list">
        {guilds.map((entry: GuildSummary) => (
          <div key={entry.id} className="guild-card">
            <div className="guild-card-main">
              <div className="guild-card-head">
                <strong>{entry.name}</strong>
                <span className="guild-rank">服排 #{entry.rank}</span>
              </div>
              <div className="guild-card-meta">
                <span>Lv.{entry.level}</span>
                <span>{entry.memberCount} 人</span>
                <span>会长 {entry.leaderName}</span>
              </div>
              <p className="guild-blurb">“{entry.recruitingBlurb}”</p>
            </div>
            <button
              className="guild-join-btn"
              disabled={joinMutation.isPending}
              onClick={() => joinMutation.mutate(entry.id)}
            >
              加入 <ChevronRight size={16} />
            </button>
          </div>
        ))}
      </div>
      {joinMutation.isError ? (
        <p className="guild-error">{(joinMutation.error as Error)?.message ?? '加入失败'}</p>
      ) : null}
    </div>
  );
}

function GuildHall({ token, view, focusBoss, onChanged }: { token: string; view: GuildView; focusBoss: boolean; onChanged: () => void }) {
  const { guild, members } = view;
  const leaveMutation = useMutation({ mutationFn: () => gameApi.leaveGuild(token), onSuccess: onChanged });
  const donateMutation = useMutation({
    mutationFn: (amount: number) => gameApi.donateGuild(token, amount),
    onSuccess: onChanged,
  });

  return (
    <div className="guild-hall">
      <div className="guild-banner">
        <div className="guild-banner-head">
          <h1>{guild.name}</h1>
          <button className="guild-leave-btn" disabled={leaveMutation.isPending} onClick={() => leaveMutation.mutate()}>
            <LogOut size={15} /> 退出公会
          </button>
        </div>
        <div className="guild-banner-stats">
          <Metric icon={<Trophy size={15} />} label="公会等级" value={`Lv.${guild.level}`} />
          <Metric icon={<Shield size={15} />} label="服务器排名" value={`#${guild.rank}`} />
          <Metric icon={<Coins size={15} />} label="我的公会币" value={`${view.myGuildCoin.toLocaleString()}`} />
          <Metric icon={<UserRound size={15} />} label="公会资金" value={`${view.fund.toLocaleString()}`} />
        </div>
        <div className="guild-perks">
          {view.perks.map((perk) => (
            <span key={perk} className="guild-perk-chip">{perk}</span>
          ))}
        </div>
        <div className="guild-donate-row">
          <span className="guild-blurb">“{guild.recruitingBlurb}”</span>
          <div className="guild-donate-actions">
            {[10000, 50000].map((amt) => (
              <button
                key={amt}
                className="guild-donate-btn"
                disabled={donateMutation.isPending}
                onClick={() => donateMutation.mutate(amt)}
              >
                捐献 {amt.toLocaleString()} 金
              </button>
            ))}
          </div>
        </div>
      </div>

      <GuildBoss token={token} focus={focusBoss} />

      <GuildRanking token={token} />

      <GuildShop token={token} />

      <div className="guild-hall-body">
        <div className="guild-members">
          <SectionTitle icon={<UserRound size={16} />} title="公会成员" />
          <div className="guild-member-list">
            {members.map((member) => (
              <div key={member.playerId} className={`guild-member-row ${member.kind}`}>
                <span className="guild-member-name">
                  {member.name}
                  {member.role === 'leader' ? <span className="guild-tag-leader">会长</span> : null}
                  {member.kind === 'player' ? <span className="guild-tag-you">你</span> : null}
                </span>
                <span className="guild-member-meta">
                  {PROFESSION_NAMES[member.profession] ?? member.profession} · Lv.{member.level}
                </span>
                <span className="guild-member-contrib">周贡献 {member.weeklyContribution}</span>
              </div>
            ))}
          </div>
        </div>

        <GuildChat token={token} />
      </div>
    </div>
  );
}

function GuildBoss({ token, focus }: { token: string; focus: boolean }) {
  const queryClient = useQueryClient();
  const [feedback, setFeedback] = useState<string | null>(null);
  const bossQuery = useQuery({
    queryKey: ['guild', 'boss'],
    queryFn: () => gameApi.guildBoss(token),
    refetchInterval: 5000,
  });
  const attackMutation = useMutation({
    mutationFn: () => gameApi.attackGuildBoss(token),
    onSuccess: (result) => {
      queryClient.setQueryData(['guild', 'boss'], result.view);
      setFeedback(
        result.killed
          ? `击杀！造成 ${result.damage.toLocaleString()} 伤害，更强的 T${result.spawnedTier} Boss 已降临！`
          : `造成 ${result.damage.toLocaleString()} 伤害！`,
      );
    },
    onError: (error) => setFeedback((error as Error)?.message ?? '攻击失败'),
  });

  if (bossQuery.isLoading) {
    return <div className="guild-boss"><SectionTitle icon={<Skull size={16} />} title="公会 Boss" /></div>;
  }
  const view: GuildBossView | undefined = bossQuery.data;
  if (!view) {
    return null;
  }
  const { boss, topContributors, myDamage, myRank } = view;
  const pct = boss.hpMax > 0 ? Math.max(0, Math.min(100, (boss.hpCurrent / boss.hpMax) * 100)) : 0;

  return (
    <div className={`guild-boss ${focus ? 'world-event-focus' : ''}`}>
      <div className="guild-boss-head">
        <SectionTitle icon={<Skull size={16} />} title={`公会 Boss · ${boss.name}${boss.tier > 1 ? ` (T${boss.tier})` : ''}`} />
        <span className="guild-boss-week">{boss.weekKey}</span>
      </div>
      <div className="guild-boss-bar">
        <div className="guild-boss-fill" style={{ width: `${pct}%` }} />
        <span className="guild-boss-hp">
          {boss.hpCurrent.toLocaleString()} / {boss.hpMax.toLocaleString()}
        </span>
      </div>
      <div className="guild-boss-actions">
        <button className="guild-boss-attack" disabled={attackMutation.isPending} onClick={() => attackMutation.mutate()}>
          <Swords size={16} /> 挑战 Boss（消耗 1 体力）
        </button>
        <span className="guild-boss-mine">
          我的贡献 {myDamage.toLocaleString()}
          {myRank > 0 ? ` · 第 ${myRank} 名` : ''}
        </span>
      </div>
      {feedback ? <p className="guild-boss-feedback">{feedback}</p> : null}
      <div className="guild-boss-ranks">
        {topContributors.length === 0 ? (
          <EmptyState text="还没人出手，第一刀就是你！" />
        ) : (
          topContributors.map((c) => (
            <div key={c.playerId} className="guild-boss-rank-row">
              <span className="guild-boss-rank-no">#{c.rank}</span>
              <span className="guild-boss-rank-name">{c.name}</span>
              <span className="guild-boss-rank-dmg">{c.damage.toLocaleString()}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function GuildRanking({ token }: { token: string }) {
  const rankQuery = useQuery({
    queryKey: ['guild', 'ranking'],
    queryFn: () => gameApi.guildRanking(token),
    refetchInterval: 8000,
  });
  const rows = rankQuery.data ?? [];
  return (
    <div className="guild-ranking">
      <SectionTitle icon={<Trophy size={16} />} title="公会周榜 · 本周贡献" />
      <div className="guild-rank-list">
        {rows.map((r) => (
          <div key={r.id} className={`guild-rank-row ${r.mine ? 'mine' : ''}`}>
            <span className="guild-rank-no">#{r.rank}</span>
            <span className="guild-rank-name">
              {r.name}
              <span className="guild-rank-lv"> Lv.{r.level}</span>
              {r.mine ? <span className="guild-tag-you">我的公会</span> : null}
            </span>
            <span className="guild-rank-val">{r.weeklyContribution.toLocaleString()}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function GuildShop({ token }: { token: string }) {
  const queryClient = useQueryClient();
  const [msg, setMsg] = useState<string | null>(null);
  const shopQuery = useQuery({ queryKey: ['guild', 'shop'], queryFn: () => gameApi.guildShop(token) });
  const buyMutation = useMutation({
    mutationFn: (offerId: string) => gameApi.buyGuildShop(token, offerId),
    onSuccess: (data) => {
      queryClient.setQueryData(['guild', 'shop'], data);
      queryClient.invalidateQueries({ queryKey: ['guild', 'home'] });
      setMsg('兑换成功！');
    },
    onError: (error) => setMsg((error as Error)?.message ?? '兑换失败'),
  });
  const data = shopQuery.data;
  if (!data) {
    return null;
  }
  return (
    <div className="guild-shop">
      <div className="guild-shop-head">
        <SectionTitle icon={<Coins size={16} />} title="公会商店" />
        <span className="guild-shop-coin">公会币 {data.guildCoin.toLocaleString()}</span>
      </div>
      {msg ? <p className="guild-shop-msg">{msg}</p> : null}
      <div className="guild-shop-list">
        {data.offers.map((o) => (
          <div key={o.id} className="guild-shop-card">
            <div className="guild-shop-info">
              <strong>{o.name}</strong>
              <small>{o.description}</small>
            </div>
            <button
              className="guild-shop-buy"
              disabled={buyMutation.isPending || data.guildCoin < o.costGuildCoin}
              onClick={() => buyMutation.mutate(o.id)}
            >
              {o.costGuildCoin} 币
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function GuildChat({ token }: { token: string }) {
  const [text, setText] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streamConnected, setStreamConnected] = useState(false);
  const chatLogRef = useRef<HTMLDivElement | null>(null);
  const lastMessageIdRef = useRef(0);
  const chatQuery = useQuery({
    queryKey: ['guild', 'chat', token],
    queryFn: () => gameApi.chatMessages(token, 'guild'),
  });
  const sendMutation = useMutation({
    mutationFn: (message: string) => gameApi.sendChat(token, message, 'guild'),
    onSuccess: (message) => {
      appendIncomingMessage(message);
      setText('');
    },
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = text.trim();
    if (!trimmed || sendMutation.isPending) {
      return;
    }
    sendMutation.mutate(trimmed);
  };

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
    if (!chatQuery.data) {
      return;
    }
    const initialMessages = [...chatQuery.data].sort((left, right) => left.id - right.id);
    setMessages(initialMessages);
    lastMessageIdRef.current = initialMessages.at(-1)?.id ?? 0;
  }, [chatQuery.data]);

  useEffect(() => {
    if (!chatQuery.data) {
      return undefined;
    }
    let closed = false;
    const source = new EventSource(gameApi.chatStreamUrl(token, lastMessageIdRef.current, 'guild'));
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
  }, [token, Boolean(chatQuery.data)]);

  useEffect(() => {
    const node = chatLogRef.current;
    if (!node || messages.length === 0) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [messages]);

  return (
    <div className="guild-chat">
      <div className="guild-chat-title">
        <SectionTitle icon={<MessageCircle size={16} />} title="公会频道" />
        <i className={`stream-indicator ${streamConnected ? 'online' : ''}`} />
      </div>
      <div className="guild-chat-log" ref={chatLogRef}>
        {messages.length === 0 ? (
          <EmptyState text="公会频道还很安静，发条消息热场吧。" />
        ) : (
          messages.map((message: ChatMessage) => (
            <div key={message.id} className={`guild-chat-line ${message.kind}`}>
              <span className="guild-chat-sender">{message.senderName}</span>
              <span className="guild-chat-text">{message.text}</span>
            </div>
          ))
        )}
      </div>
      <form className="guild-chat-input" onSubmit={submit}>
        <input
          value={text}
          maxLength={120}
          placeholder="对公会说点什么…"
          onChange={(event) => setText(event.target.value)}
        />
        <button type="submit" disabled={sendMutation.isPending || !text.trim()}>
          <Send size={16} />
        </button>
      </form>
    </div>
  );
}

function Metric({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="guild-metric">
      <span className="guild-metric-label">
        {icon} {label}
      </span>
      <strong className="guild-metric-value">{value}</strong>
    </div>
  );
}
