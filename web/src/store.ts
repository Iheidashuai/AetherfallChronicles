import { create } from 'zustand';

type Screen = 'auth' | 'create-player' | 'home' | 'character' | 'inventory' | 'quests' | 'market' | 'chat' | 'leaderboard' | 'robots' | 'dungeons' | 'result';

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

export const useAppStore = create<AppState>((set) => ({
  token: storedToken,
  username: storedUsername,
  screen: storedToken ? 'home' : 'auth',
  setSession: (token, username, hasPlayer) => {
    localStorage.setItem('mythic.token', token);
    localStorage.setItem('mythic.username', username);
    set({ token, username, screen: hasPlayer ? 'home' : 'create-player' });
  },
  setScreen: (screen) => set({ screen }),
  logout: () => {
    localStorage.removeItem('mythic.token');
    localStorage.removeItem('mythic.username');
    set({ token: null, username: null, screen: 'auth' });
  },
}));
