// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { gameApi, type RobotActivityDetail, type RobotActivitySnapshot, type RobotActivityView } from '../api';
import { useAppStore } from '../store';
import { RobotActivityScreen } from './RobotActivityScreen';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const robot: RobotActivityView = {
  id: 7,
  name: 'Dorian Thornwick',
  title: '掉落记录员',
  profession: 'archer',
  level: 18,
  power: 62_327,
  gold: 291_596,
  realMoney: 640_688,
  wealthTierLevel: 6,
  wealthTier: '平民',
  rechargeRmb: 956,
  rechargeGold: 9_560,
  dungeonClears: 76,
  peakEnhancement: 7,
  legendaryLootCount: 0,
  currentActivityKind: 'dungeon',
  currentActivityText: '刚花 1490 金升级技能',
  currentActivityAt: '2026-06-20T00:00:00Z',
  lastActivityAt: '2026-06-20T00:00:00Z',
};

const snapshot: RobotActivitySnapshot = {
  robots: [robot],
  events: [],
};

const detail: RobotActivityDetail = {
  robot,
  events: [],
  equipment: [],
};

let root: Root | null = null;
let container: HTMLDivElement | null = null;
let queryClient: QueryClient | null = null;

beforeEach(() => {
  vi.spyOn(gameApi, 'robotActivity').mockResolvedValue(snapshot);
  vi.spyOn(gameApi, 'robotActivityDetail').mockResolvedValue(detail);
  useAppStore.setState({
    token: 'test-token',
    username: 'tester',
    screen: 'robots',
    pendingWorldEventAction: {
      label: '查看冒险者',
      targetScreen: 'robots',
      targetId: String(robot.id),
      params: { robotId: robot.id },
    },
  });
  container = document.createElement('div');
  document.body.appendChild(container);
  queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
  root = createRoot(container);
});

afterEach(async () => {
  if (root) {
    await act(async () => root?.unmount());
  }
  container?.remove();
  queryClient?.clear();
  vi.restoreAllMocks();
  useAppStore.setState({
    token: null,
    username: null,
    screen: 'auth',
    pendingWorldEventAction: null,
  });
  root = null;
  container = null;
  queryClient = null;
});

describe('RobotActivityScreen world-event detail modal', () => {
  it('does not reopen the highlighted robot modal after closing it', async () => {
    await act(async () => {
      root?.render(
        <QueryClientProvider client={queryClient!}>
          <RobotActivityScreen token="test-token" />
        </QueryClientProvider>,
      );
    });

    await waitFor(() => {
      expect(container?.textContent).toContain('实时历史动态');
    });

    const closeButton = Array.from(container!.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === '关闭');
    expect(closeButton).toBeTruthy();

    await act(async () => {
      closeButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    await waitFor(() => {
      expect(container?.textContent).not.toContain('实时历史动态');
    });
  });
});

async function waitFor(assertion: () => void, timeoutMs = 1000) {
  const startedAt = Date.now();
  let lastError: unknown;
  while (Date.now() - startedAt < timeoutMs) {
    try {
      assertion();
      return;
    } catch (error) {
      lastError = error;
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
    }
  }
  throw lastError;
}
