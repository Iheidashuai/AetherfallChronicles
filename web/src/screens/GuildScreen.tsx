import { FormEvent, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Coins, LogOut, MessageCircle, Send, Shield, Skull, Swords, Trophy, UserRound } from 'lucide-react';
import { gameApi } from '../api';
import type { GuildBossView, GuildChatMessage, GuildSummary, GuildView } from '../api';
import { useAppStore } from '../store';
import { EmptyState, ErrorScreen, LoadingScreen, SectionTitle, TopBar } from '../components/ui';

const PROFESSION_NAMES: Record<string, string> = { warrior: '战士', ranger: '游侠', mage: '法师' };

export function GuildScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const queryClient = useQueryClient();
  const homeQuery = useQuery({ queryKey: ['guild', 'home'], queryFn: () => gameApi.myGuild(token) });
  const guild = homeQuery.data?.guild ?? null;

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
        <GuildHall token={token} view={guild} onChanged={() => queryClient.invalidateQueries({ queryKey: ['guild'] })} />
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

function GuildHall({ token, view, onChanged }: { token: string; view: GuildView; onChanged: () => void }) {
  const { guild, members } = view;
  const leaveMutation = useMutation({ mutationFn: () => gameApi.leaveGuild(token), onSuccess: onChanged });

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
          <Metric icon={<UserRound size={15} />} label="成员" value={`${guild.memberCount}`} />
          <Metric icon={<Coins size={15} />} label="我的身份" value={view.myRole === 'leader' ? '会长' : '成员'} />
        </div>
        <p className="guild-blurb">“{guild.recruitingBlurb}”</p>
      </div>

      <GuildBoss token={token} />

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

function GuildBoss({ token }: { token: string }) {
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
    <div className="guild-boss">
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

function GuildChat({ token }: { token: string }) {
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const chatQuery = useQuery({
    queryKey: ['guild', 'chat'],
    queryFn: () => gameApi.guildChat(token),
    refetchInterval: 5000,
  });
  const sendMutation = useMutation({
    mutationFn: (message: string) => gameApi.sendGuildChat(token, message),
    onSuccess: (messages) => {
      queryClient.setQueryData(['guild', 'chat'], messages);
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

  const messages = chatQuery.data ?? [];

  return (
    <div className="guild-chat">
      <SectionTitle icon={<MessageCircle size={16} />} title="公会频道" />
      <div className="guild-chat-log">
        {messages.length === 0 ? (
          <EmptyState text="公会频道还很安静，发条消息热场吧。" />
        ) : (
          messages.map((message: GuildChatMessage) => (
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
