import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, BrainCircuit, Clock3, Database, FileText } from 'lucide-react';
import { gameApi } from '../api';
import type { AiModelCallDetail } from '../api';
import { useAppStore } from '../store';
import { EmptyState, ErrorScreen, LoadingScreen, Metric, SectionTitle, TopBar } from '../components/ui';

const INPUT_TOKEN_PRICE_PER_1K = 0.00075;
const OUTPUT_TOKEN_PRICE_PER_1K = 0.003;

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
        <Metric label="今日输入" value={formatNumber(summary.todayPromptTokens)} />
        <Metric label="今日输出" value={formatNumber(summary.todayCompletionTokens)} />
        <Metric label="今日总计" value={formatNumber(summary.todayTotalTokens)} />
        <Metric label="历史输入" value={formatNumber(summary.allPromptTokens)} />
        <Metric label="历史输出" value={formatNumber(summary.allCompletionTokens)} />
        <Metric label="历史总计" value={formatNumber(summary.allTotalTokens)} />
        <Metric label="今日费用" value={formatYuan(tokenCost(summary.todayPromptTokens, summary.todayCompletionTokens))} />
        <Metric label="历史费用" value={formatYuan(tokenCost(summary.allPromptTokens, summary.allCompletionTokens))} />
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
            <div className="ai-price-chip">
              <span>输入 {formatPrice(INPUT_TOKEN_PRICE_PER_1K)} 元/1K</span>
              <span>输出 {formatPrice(OUTPUT_TOKEN_PRICE_PER_1K)} 元/1K</span>
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
                    <th>输入</th>
                    <th>输出</th>
                    <th>总计</th>
                    <th>费用</th>
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
                      <td>{formatNumber(call.promptTokens ?? 0)}</td>
                      <td>{formatNumber(call.completionTokens ?? 0)}</td>
                      <td>{formatNumber(call.totalTokens ?? 0)}</td>
                      <td>{formatYuan(tokenCost(call.promptTokens ?? 0, call.completionTokens ?? 0))}</td>
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
        <Metric label="输入 Token" value={formatNumber(detail.promptTokens ?? 0)} />
        <Metric label="输出 Token" value={formatNumber(detail.completionTokens ?? 0)} />
        <Metric label="总 Token" value={formatNumber(detail.totalTokens ?? 0)} />
        <Metric label="输入费用" value={formatYuan(inputTokenCost(detail.promptTokens ?? 0))} />
        <Metric label="输出费用" value={formatYuan(outputTokenCost(detail.completionTokens ?? 0))} />
        <Metric label="合计费用" value={formatYuan(tokenCost(detail.promptTokens ?? 0, detail.completionTokens ?? 0))} />
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

function inputTokenCost(tokens: number) {
  return (safeNumber(tokens) / 1000) * INPUT_TOKEN_PRICE_PER_1K;
}

function outputTokenCost(tokens: number) {
  return (safeNumber(tokens) / 1000) * OUTPUT_TOKEN_PRICE_PER_1K;
}

function tokenCost(inputTokens: number, outputTokens: number) {
  return inputTokenCost(inputTokens) + outputTokenCost(outputTokens);
}

function safeNumber(value: number) {
  return Number.isFinite(value) ? value : 0;
}

function formatYuan(value: number) {
  const amount = safeNumber(value);
  return `${amount >= 1 ? amount.toFixed(2) : amount.toFixed(6)} 元`;
}

function formatPrice(value: number) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 6,
  });
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
