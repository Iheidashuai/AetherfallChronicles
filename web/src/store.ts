import { create } from 'zustand';

export type Screen = 'auth' | 'create-player' | 'home' | 'character' | 'inventory' | 'item-catalog' | 'skills' | 'blacksmith' | 'quests' | 'market' | 'shop' | 'builds' | 'endgame' | 'arena' | 'arena-result' | 'guild' | 'chat' | 'leaderboard' | 'robots' | 'ai-usage' | 'recharge' | 'dungeons' | 'result' | 'rift-result';

export type PendingWorldEventAction = {
  label: string;
  targetScreen: Screen;
  targetId?: string;
  params?: Record<string, string | number | boolean>;
};

type AppState = {
  token: string | null;
  username: string | null;
  admin: boolean;
  screen: Screen;
  pendingWorldEventAction: PendingWorldEventAction | null;
  setSession: (token: string, username: string, hasPlayer: boolean, admin: boolean) => void;
  setScreen: (screen: Screen) => void;
  setPendingWorldEventAction: (action: PendingWorldEventAction) => void;
  clearPendingWorldEventAction: () => void;
  logout: () => void;
};

const storedToken = localStorage.getItem('mythic.token');
const storedUsername = localStorage.getItem('mythic.username');
const storedHasPlayer = localStorage.getItem('mythic.hasPlayer');
const storedAdmin = localStorage.getItem('mythic.admin');

export const useAppStore = create<AppState>((set) => ({
  token: storedToken,
  username: storedUsername,
  admin: storedAdmin === 'true',
  screen: storedToken ? (storedHasPlayer === 'false' ? 'create-player' : 'home') : 'auth',
  pendingWorldEventAction: null,
  setSession: (token, username, hasPlayer, admin) => {
    localStorage.setItem('mythic.token', token);
    localStorage.setItem('mythic.username', username);
    localStorage.setItem('mythic.hasPlayer', String(hasPlayer));
    localStorage.setItem('mythic.admin', String(admin));
    set({ token, username, admin, screen: hasPlayer ? 'home' : 'create-player' });
  },
  setScreen: (screen) => set({ screen }),
  setPendingWorldEventAction: (action) => set({ pendingWorldEventAction: action }),
  clearPendingWorldEventAction: () => set({ pendingWorldEventAction: null }),
  logout: () => {
    localStorage.removeItem('mythic.token');
    localStorage.removeItem('mythic.username');
    localStorage.removeItem('mythic.hasPlayer');
    localStorage.removeItem('mythic.admin');
    set({ token: null, username: null, admin: false, screen: 'auth', pendingWorldEventAction: null });
  },
}));
