import { FormEvent, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Coins, LogOut, MessageCircle, Send, Shield, Trophy, UserRound } from 'lucide-react';
import { gameApi } from '../api';
import type { GuildChatMessage, GuildSummary, GuildView } from '../api';
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
