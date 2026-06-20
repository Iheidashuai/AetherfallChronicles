import { useEffect, useRef, useState } from 'react';
import { FastForward, SkipForward, Swords } from 'lucide-react';
import type { RiftBattleEvent, RiftRunResult } from '../api';
import { useAppStore } from '../store';
import { formatNumber } from '../lib/helpers';
import { ConfirmDialog, EmptyState, HpBar, Metric, SectionTitle, TopBar } from '../components/ui';

export function RiftResultScreen({ result }: { result: RiftRunResult }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [replaySpeed, setReplaySpeed] = useState(1);
  const [visibleEventCount, setVisibleEventCount] = useState(result.events.length ? 1 : 0);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);

  useEffect(() => {
    setReplaySpeed(1);
    setVisibleEventCount(result.events.length ? 1 : 0);
    setShowLeaveConfirm(false);
  }, [result.runId, result.events.length]);

  useEffect(() => {
    if (visibleEventCount >= result.events.length) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setVisibleEventCount((current) => Math.min(result.events.length, current + 1));
    }, replaySpeed === 4 ? 150 : replaySpeed === 2 ? 340 : 820);
    return () => window.clearTimeout(timeout);
  }, [replaySpeed, result.events.length, visibleEventCount]);

  const complete = result.events.length === 0 || visibleEventCount >= result.events.length;
  const battlePlaying = result.events.length > 0 && !complete;

  function returnToRifts() {
    setScreen('endgame');
  }

  function handleBack() {
    if (battlePlaying) {
      setShowLeaveConfirm(true);
      return;
    }
    returnToRifts();
  }

  return (
    <section className="screen result-screen rift-battle-screen combat-screen">
      <TopBar title="深渊战报" onBack={handleBack} />

      <aside className="combat-side rift-battle-side">
        <div className={`result-banner ${result.success ? 'win' : 'lose'}`}>
          <span>{result.success ? '通关' : '失败'} · 评价 {result.rating}</span>
          <h1>深渊裂隙 T{result.tier}</h1>
          <p>得分 {formatNumber(result.score)}，击杀 {result.monstersKilled}，消耗 {result.turnsTaken} 行动</p>
        </div>

        <div className="speed-bar">
          <button className={replaySpeed === 1 ? 'active' : ''} onClick={() => setReplaySpeed(1)}>1x</button>
          <button className={replaySpeed === 2 ? 'active' : ''} onClick={() => setReplaySpeed(2)}>2x</button>
          <button className={replaySpeed === 4 ? 'active' : ''} onClick={() => setReplaySpeed(4)}><FastForward size={15} />4x</button>
          <button onClick={() => setVisibleEventCount(result.events.length)}><SkipForward size={15} />全部</button>
        </div>

        <div className="combat-reward-panel rift-battle-summary-grid">
          <Metric label="推荐战力" value={formatNumber(result.recommendedPower)} />
          <Metric label="当前战力" value={formatNumber(result.combatPower)} />
          <Metric label="剩余生命" value={`${Math.max(0, result.playerFinalHp)}/${Math.max(1, result.playerMaxHp)}`} />
        </div>

        <div className="rift-reward-preview compact rift-battle-materials">
          <Metric label="深渊精华" value={`+${result.rewards.essence}`} />
          <Metric label="淬炼碎片" value={`+${result.rewards.shards}`} />
          <Metric label="重铸宝珠" value={`+${result.rewards.orbs}`} />
          <Metric label="战报进度" value={`${Math.min(visibleEventCount, result.events.length)}/${result.events.length}`} />
        </div>

        <div className="rift-material-footer">
          当前库存：{result.materials.essence} 精华 / {result.materials.shards} 碎片 / {result.materials.orbs} 宝珠
        </div>

        <button className="primary-action rift-battle-return" onClick={returnToRifts}>回到深渊裂隙</button>
      </aside>

      <div className="combat-main rift-battle-main">
        <RiftCombatReplay
          result={result}
          visibleEventCount={visibleEventCount}
          complete={complete}
        />
      </div>

      {showLeaveConfirm && (
        <ConfirmDialog
          title="战斗仍在展示"
          message="现在返回会跳过剩余深渊战报，确定回到深渊裂隙吗？"
          confirmLabel="返回裂隙"
          cancelLabel="继续查看"
          danger
          onCancel={() => setShowLeaveConfirm(false)}
          onConfirm={() => {
            setShowLeaveConfirm(false);
            returnToRifts();
          }}
        />
      )}
    </section>
  );
}

function RiftCombatReplay({
  result,
  visibleEventCount,
  complete,
}: {
  result: RiftRunResult;
  visibleEventCount: number;
  complete: boolean;
}) {
  const logRef = useRef<HTMLDivElement | null>(null);
  const visibleEvents = result.events.slice(0, Math.max(1, visibleEventCount));
  const currentEvent = visibleEvents.at(-1);
  const playerName = riftPlayerName(result);
  const enemyName = riftCurrentEnemy(result, currentEvent, playerName);
  const enemyMaxHp = maxHpForName(result.events, enemyName);
  const playerMaxHp = result.playerMaxHp || 1;
  const playerHp = latestHpForName(visibleEvents, playerName, playerMaxHp);
  const enemyHp = latestHpForName(visibleEvents, enemyName, enemyMaxHp);
  const enemyHpText = enemyMaxHp > 1 ? `${Math.max(0, enemyHp)}/${enemyMaxHp}` : enemyHp <= 0 ? '已击败' : '目标锁定';

  useEffect(() => {
    const node = logRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [visibleEventCount]);

  return (
    <section className={`rift-combat-replay rift-battle-replay ${result.success ? 'success' : 'failed'}`}>
      <div className="panel-head-row">
        <SectionTitle icon={<Swords size={18} />} title={`T${result.tier} 自动战斗`} />
        <strong>{complete ? '战斗结束' : '交战中'}</strong>
      </div>

      <div className="rift-combat-stage">
        <article className="rift-combatant player">
          <div>
            <span className="eyebrow">我方</span>
            <strong>{playerName}</strong>
          </div>
          <HpBar value={playerHp} max={playerMaxHp} />
          <small>{Math.max(0, playerHp)}/{playerMaxHp}</small>
        </article>

        <div className="rift-clash-column">
          <span className={`rift-outcome-badge ${complete ? result.success ? 'success' : 'failed' : ''}`}>
            {complete ? result.success ? '通关' : '失败' : '交战中'}
          </span>
          <strong>{riftEventLabel(currentEvent)}</strong>
          <p>{currentEvent?.text ?? `目标：击败${riftBossName(result.tier)}。`}</p>
        </div>

        <article className="rift-combatant enemy">
          <div>
            <span className="eyebrow">{riftRoomLabel(currentEvent)}</span>
            <strong>{enemyName}</strong>
          </div>
          <HpBar value={enemyHp} max={enemyMaxHp} />
          <small>{enemyHpText}</small>
        </article>
      </div>

      <div className="rift-combat-metrics">
        <Metric label="评价" value={`${result.rating} · ${formatNumber(result.score)}`} />
        <Metric label="行动数" value={`${result.turnsTaken}`} />
        <Metric label="击杀" value={`${result.monstersKilled}`} />
        <Metric label="材料" value={`+${result.rewards.essence}/${result.rewards.shards}/${result.rewards.orbs}`} />
      </div>

      <div className="rift-event-log replay rift-battle-log" ref={logRef}>
        {visibleEvents.length === 0 && <EmptyState text="本场没有战斗记录。" />}
        {visibleEvents.map((event) => (
          <div key={event.index} className={`rift-event-line ${event.tone}`}>
            <span>{riftRoomLabel(event)}</span>
            <p>{event.text}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function riftBossName(tier: number) {
  return `深渊领主 T${tier}`;
}

function riftPlayerName(result: RiftRunResult) {
  return result.events.find((event) => event.eventType === 'start' && event.actorName)?.actorName
    ?? result.events.find((event) => event.actorName)?.actorName
    ?? '挑战者';
}

function riftEventLabel(event?: RiftBattleEvent) {
  if (!event) {
    return '备战';
  }
  if (event.eventType === 'start') {
    return '开战';
  }
  if (event.eventType === 'room') {
    return '推进';
  }
  if (event.eventType === 'phase') {
    return '阶段';
  }
  if (event.eventType === 'death') {
    return '击败';
  }
  if (event.eventType === 'clear') {
    return '通关';
  }
  if (event.eventType === 'fail') {
    return '失败';
  }
  if (event.eventType === 'recover' || event.tone === 'heal') {
    return '恢复';
  }
  return event.tone === 'danger' || event.tone === 'enemy' ? '敌方行动' : '我方行动';
}

function riftRoomLabel(event?: RiftBattleEvent) {
  if (!event || event.roomIndex <= 0) {
    return '裂隙入口';
  }
  return `房间 ${event.roomIndex}`;
}

function riftCurrentEnemy(result: RiftRunResult, event: RiftBattleEvent | undefined, playerName: string) {
  const eventEnemy = [event?.targetName, event?.actorName].find((name) => name && name !== playerName);
  if (eventEnemy) {
    return eventEnemy;
  }
  const lastEnemy = [...result.events]
    .reverse()
    .flatMap((entry) => [entry.targetName, entry.actorName])
    .find((name) => name && name !== playerName);
  return lastEnemy ?? riftBossName(result.tier);
}

function maxHpForName(events: RiftBattleEvent[], name: string) {
  const hpValues = events.flatMap((event) => [
    event.actorName === name && event.eventType !== 'death' ? event.actorHp : 0,
    event.targetName === name ? event.targetHp : 0,
  ]).filter((hp) => hp > 0);
  return Math.max(1, ...hpValues);
}

function latestHpForName(events: RiftBattleEvent[], name: string, fallback: number) {
  return events.reduce((hp, event) => {
    if (event.actorName === name && event.eventType !== 'death') {
      return Math.max(0, event.actorHp);
    }
    if (event.targetName === name) {
      return Math.max(0, event.targetHp);
    }
    return hp;
  }, fallback);
}
