import { create } from 'zustand';

type Screen = 'auth' | 'create-player' | 'home' | 'character' | 'inventory' | 'item-catalog' | 'blacksmith' | 'quests' | 'market' | 'chat' | 'leaderboard' | 'robots' | 'recharge' | 'dungeons' | 'result';

type AppState = {
  token: string | null;
  username: string | null;
  screen: Screen;
  setSession: (token: string, username: string, hasPlayer: boolean) => void;
  setScreen: (screen: Screen) => void;
  logout: () => void;
};

const storedToken = localStorage.getItem('mythic.token');
const storedUsername = localStorage.getItem('mythic.username');
const storedHasPlayer = localStorage.getItem('mythic.hasPlayer');

export const useAppStore = create<AppState>((set) => ({
  token: storedToken,
  username: storedUsername,
  screen: storedToken ? (storedHasPlayer === 'false' ? 'create-player' : 'home') : 'auth',
  setSession: (token, username, hasPlayer) => {
    localStorage.setItem('mythic.token', token);
    localStorage.setItem('mythic.username', username);
    localStorage.setItem('mythic.hasPlayer', String(hasPlayer));
    set({ token, username, screen: hasPlayer ? 'home' : 'create-player' });
  },
  setScreen: (screen) => set({ screen }),
  logout: () => {
    localStorage.removeItem('mythic.token');
    localStorage.removeItem('mythic.username');
    localStorage.removeItem('mythic.hasPlayer');
    set({ token: null, username: null, screen: 'auth' });
  },
}));
