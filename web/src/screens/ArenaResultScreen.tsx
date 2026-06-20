import { useEffect, useRef, useState } from 'react';
import { FastForward, SkipForward, Swords } from 'lucide-react';
import type { ArenaBattleEvent, ArenaMatchDetail } from '../api';
import { useAppStore } from '../store';
import { formatNumber, formatSigned, professionName } from '../lib/helpers';
import { ArenaFighterCard, ConfirmDialog, EmptyState, HpBar, Metric, SectionTitle, TopBar } from '../components/ui';

export function ArenaResultScreen({ match }: { match: ArenaMatchDetail }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [speed, setSpeed] = useState(1);
  const [visibleEventCount, setVisibleEventCount] = useState(match.events.length ? 1 : 0);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const logRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setSpeed(1);
    setVisibleEventCount(match.events.length ? 1 : 0);
    setShowLeaveConfirm(false);
  }, [match.summary.matchId, match.events.length]);

  useEffect(() => {
    if (visibleEventCount >= match.events.length) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setVisibleEventCount((current) => Math.min(match.events.length, current + 1));
    }, speed === 4 ? 150 : speed === 2 ? 340 : 820);
    return () => window.clearTimeout(timeout);
  }, [match.events.length, speed, visibleEventCount]);

  useEffect(() => {
    const node = logRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [visibleEventCount]);

  const visibleEvents = match.events.slice(0, Math.max(1, visibleEventCount));
  const currentEvent = visibleEvents.at(-1);
  const attackerHp = Math.max(0, currentEvent?.attackerHp ?? match.attacker.maxHp);
  const defenderHp = Math.max(0, currentEvent?.defenderHp ?? match.defender.maxHp);
  const complete = match.events.length === 0 || visibleEventCount >= match.events.length;
  const battlePlaying = match.events.length > 0 && !complete;

  function returnToArena() {
    setScreen('arena');
  }

  function handleBack() {
    if (battlePlaying) {
      setShowLeaveConfirm(true);
      return;
    }
    returnToArena();
  }

  return (
    <section className="screen result-screen arena-battle-screen combat-screen">
      <TopBar title="竞技场战报" onBack={handleBack} />

      <aside className="combat-side arena-battle-side">
        <div className={`result-banner ${match.summary.attackerWon ? 'win' : 'lose'}`}>
          <span>{match.summary.attackerWon ? '进攻胜利' : '进攻失败'} · {match.profile.tier}</span>
          <h1>{match.attacker.name}</h1>
          <p>挑战 {match.defender.name}，{formatSigned(match.summary.attackerRatingChange)} 积分 / +{match.summary.arenaCoins} 竞技币</p>
        </div>

        <div className="speed-bar">
          <button className={speed === 1 ? 'active' : ''} onClick={() => setSpeed(1)}>1x</button>
          <button className={speed === 2 ? 'active' : ''} onClick={() => setSpeed(2)}>2x</button>
          <button className={speed === 4 ? 'active' : ''} onClick={() => setSpeed(4)}><FastForward size={15} />4x</button>
          <button onClick={() => setVisibleEventCount(match.events.length)}><SkipForward size={15} />全部</button>
        </div>

        <div className="combat-reward-panel arena-battle-summary-grid">
          <Metric label="积分变化" value={formatSigned(match.summary.attackerRatingChange)} />
          <Metric label="竞技币" value={`+${match.summary.arenaCoins}`} />
          <Metric label="战报进度" value={`${Math.min(visibleEventCount, match.events.length)}/${match.events.length}`} />
        </div>

        <div className="arena-battle-fighter-stack">
          <ArenaFighterCard fighter={match.attacker} label="进攻方" />
          <ArenaFighterCard fighter={match.defender} label="防守方" />
        </div>

        <button className="primary-action arena-battle-return" onClick={returnToArena}>回到竞技场</button>
      </aside>

      <div className="combat-main arena-battle-main">
        <section className={`arena-duel-stage ${match.summary.attackerWon ? 'victory' : 'defeat'}`}>
          <div className="panel-head-row">
            <SectionTitle icon={<Swords size={18} />} title={`第 ${match.summary.matchId} 场自动战斗`} />
            <strong>{complete ? '战斗结束' : '交战中'}</strong>
          </div>
          <div className="arena-duel-fighters">
            <article className="arena-combatant player">
              <div>
                <span className="eyebrow">进攻方 · {professionName(match.attacker.profession)}</span>
                <strong>{match.attacker.name}</strong>
              </div>
              <HpBar value={attackerHp} max={match.attacker.maxHp} />
              <small>{attackerHp}/{match.attacker.maxHp} · 战力 {formatNumber(match.attacker.combatPower)}</small>
            </article>

            <div className="arena-clash-column">
              <span className={`rift-outcome-badge ${complete ? match.summary.attackerWon ? 'success' : 'failed' : ''}`}>
                {complete ? match.summary.attackerWon ? '胜利' : '失败' : '交战中'}
              </span>
              <strong>{arenaEventLabel(currentEvent)}</strong>
              <p>{currentEvent?.text ?? `${match.attacker.name} 向 ${match.defender.name} 发起挑战。`}</p>
            </div>

            <article className="arena-combatant enemy">
              <div>
                <span className="eyebrow">防守方 · {professionName(match.defender.profession)}</span>
                <strong>{match.defender.name}</strong>
              </div>
              <HpBar value={defenderHp} max={match.defender.maxHp} />
              <small>{defenderHp}/{match.defender.maxHp} · 战力 {formatNumber(match.defender.combatPower)}</small>
            </article>
          </div>
        </section>

        <div className="arena-event-log arena-battle-log" ref={logRef}>
          {visibleEvents.length === 0 && <EmptyState text="本场没有战斗记录。" />}
          {visibleEvents.map((event) => (
            <div key={event.sequenceNo} className={`arena-event-line ${event.tone} ${event.actor === 'defender' ? 'defender' : 'player'}`}>
              <span>{arenaActorLabel(event)}</span>
              <p>{event.text}</p>
            </div>
          ))}
        </div>
      </div>

      {showLeaveConfirm && (
        <ConfirmDialog
          title="战斗仍在展示"
          message="现在返回会跳过剩余竞技场战报，确定回到竞技场吗？"
          confirmLabel="返回竞技场"
          cancelLabel="继续查看"
          danger
          onCancel={() => setShowLeaveConfirm(false)}
          onConfirm={() => {
            setShowLeaveConfirm(false);
            returnToArena();
          }}
        />
      )}
    </section>
  );
}

function arenaEventLabel(event?: ArenaBattleEvent) {
  if (!event) {
    return '备战';
  }
  if (event.skillName) {
    return event.skillName;
  }
  if (event.critical) {
    return '暴击';
  }
  if (event.missed) {
    return '闪避';
  }
  const labels: Record<string, string> = {
    hit: '命中',
    miss: '闪避',
    crit: '暴击',
    heal: '恢复',
    shield: '护盾',
    death: '击败',
    phase: '阶段',
  };
  return labels[event.eventType] ?? '行动';
}

function arenaActorLabel(event: ArenaBattleEvent) {
  if (event.actor === 'defender') {
    return '防守';
  }
  if (event.actor === 'player' || event.actor === 'attacker') {
    return '进攻';
  }
  return '系统';
}
