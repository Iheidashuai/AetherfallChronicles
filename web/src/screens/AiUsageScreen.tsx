import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, BrainCircuit, Clock3, Database, FileText } from 'lucide-react';
import { gameApi } from '../api';
import type { AiModelCallDetail } from '../api';
import { useAppStore } from '../store';
import { EmptyState, ErrorScreen, LoadingScreen, Metric, SectionTitle, TopBar } from '../components/ui';

export function AiUsageScreen({ token }: { token: string }) {
  const setScreen = useAppStore((state) => state.setScreen);
  const [selectedCallId, setSelectedCallId] = useState<number | null>(null);
  const summaryQuery = useQuery({
    queryKey: ['ai-usage', 'summary', token],
    queryFn: () => gameApi.aiUsageSummary(token),
    refetchInterval: 10_000,
  });
  const callsQuery = useQuery({
    queryKey: ['ai-usage', 'calls', token],
    queryFn: () => gameApi.aiUsageCalls(token, 100),
    refetchInterval: 10_000,
  });
  const detailQuery = useQuery({
    queryKey: ['ai-usage', 'call', token, selectedCallId],
    queryFn: () => gameApi.aiUsageCall(token, selectedCallId!),
    enabled: selectedCallId !== null,
  });

  if (summaryQuery.isLoading || callsQuery.isLoading) {
    return <LoadingScreen title="AI 用量" />;
  }
  if (summaryQuery.isError || callsQuery.isError || !summaryQuery.data) {
    return <ErrorScreen message={(summaryQuery.error as Error)?.message ?? (callsQuery.error as Error)?.message ?? 'AI 用量加载失败'} />;
  }

  const summary = summaryQuery.data;
  const calls = callsQuery.data ?? [];
  const selected = detailQuery.data ?? null;

  return (
    <section className="screen ai-usage-screen">
      <TopBar title="AI 用量" onBack={() => setScreen('robots')} />
      <div className="stat-grid ai-usage-stats status-strip">
        <Metric label="今日 Token" value={formatNumber(summary.todayTotalTokens)} />
        <Metric label="历史 Token" value={formatNumber(summary.allTotalTokens)} />
        <Metric label="平均延迟" value={`${formatNumber(summary.avgLatencyMs)} ms`} />
        <Metric label="失败率" value={formatPercent(summary.failureRate)} />
        <Metric label="兜底" value={formatNumber(summary.fallbackCount)} />
        <Metric label="调用" value={`${formatNumber(summary.successCalls)}/${formatNumber(summary.totalCalls)}`} />
      </div>

      <div className="desktop-workbench ai-usage-workbench">
        <section className="main-panel ai-usage-main">
          <div className="ai-usage-head">
            <SectionTitle icon={<BrainCircuit size={18} />} title="模型调用" />
            <div className="ai-model-chip">
              <Database size={14} />
              <span>{summary.provider || '未调用'}</span>
              <strong>{summary.model || '未配置模型'}</strong>
            </div>
          </div>
          <div className="ai-usage-table-wrap">
            {calls.length === 0 ? (
              <EmptyState text="还没有 AI 调用记录。" />
            ) : (
              <table className="ai-usage-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>模式</th>
                    <th>模型</th>
                    <th>Token</th>
                    <th>延迟</th>
                    <th>状态</th>
                    <th>时间</th>
                  </tr>
                </thead>
                <tbody>
                  {calls.map((call) => (
                    <tr
                      key={call.id}
                      className={selectedCallId === call.id ? 'selected' : ''}
                      onClick={() => setSelectedCallId(call.id)}
                    >
                      <td>#{call.id}</td>
                      <td>{call.mode}</td>
                      <td>{call.model || call.provider}</td>
                      <td>{formatNumber(call.totalTokens ?? 0)}</td>
                      <td>{formatNumber(call.latencyMs)} ms</td>
                      <td>
                        <span className={`ai-call-status ${call.success ? 'ok' : 'fail'}`}>
                          {call.success ? '成功' : call.errorType || '失败'}
                        </span>
                      </td>
                      <td>{formatTime(call.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>

        <aside className="detail-rail ai-usage-detail">
          <SectionTitle icon={<FileText size={18} />} title="调用详情" />
          {selectedCallId === null ? (
            <EmptyState text="选择一条调用记录。" />
          ) : detailQuery.isLoading ? (
            <LoadingScreen title="详情" />
          ) : selected ? (
            <CallDetail detail={selected} />
          ) : (
            <EmptyState text="详情不可用。" />
          )}
        </aside>
      </div>
    </section>
  );
}

function CallDetail({ detail }: { detail: AiModelCallDetail }) {
  return (
    <div className="ai-call-detail-body">
      <div className="ai-call-detail-grid">
        <Metric label="Provider" value={detail.provider || '-'} />
        <Metric label="Model" value={detail.model || '-'} />
        <Metric label="Prompt" value={formatNumber(detail.promptTokens ?? 0)} />
        <Metric label="Completion" value={formatNumber(detail.completionTokens ?? 0)} />
        <Metric label="Token 来源" value={detail.tokenSource || '-'} />
        <Metric label="Latency" value={`${formatNumber(detail.latencyMs)} ms`} />
      </div>
      {!detail.success && (
        <div className="ai-call-error">
          <AlertTriangle size={16} />
          <span>{detail.errorMessage || detail.errorType || '调用失败'}</span>
        </div>
      )}
      <PayloadBlock title="Request" value={detail.requestPayload} />
      <PayloadBlock title="Prompt" value={detail.promptText} />
      <PayloadBlock title="Response" value={detail.rawResponse} />
      <PayloadBlock title="Parsed" value={detail.parsedResponse} />
      <PayloadBlock title="Validation" value={detail.validationErrors} />
    </div>
  );
}

function PayloadBlock({ title, value }: { title: string; value?: string | null }) {
  if (!value) {
    return null;
  }
  return (
    <details className="ai-payload-block" open={title === 'Validation'}>
      <summary>
        <Clock3 size={14} /> {title}
      </summary>
      <pre>{pretty(value)}</pre>
    </details>
  );
}

function pretty(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatNumber(value: number) {
  return Number.isFinite(value) ? Math.round(value).toLocaleString() : '0';
}

function formatPercent(value: number) {
  return `${Math.round((Number.isFinite(value) ? value : 0) * 100)}%`;
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function pad(value: number) {
  return value.toString().padStart(2, '0');
}
