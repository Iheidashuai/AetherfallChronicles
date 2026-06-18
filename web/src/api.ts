export type Player = {
  id: number;
  name: string;
  profession: string;
  level: number;
  experience: number;
  gold: number;
  realMoney: number;
  wealthTierLevel: number;
  wealthTier: string;
  strength: number;
  agility: number;
  constitution: number;
  intelligence: number;
  spirit: number;
  freePoints: number;
};

export type Item = {
  id: number;
  playerId: number;
  templateId: string;
  name: string;
  itemType: string;
  itemCategory: string;
  quality: string;
  requiredLevel: number;
  attackBonus: number;
  defenseBonus: number;
  resistanceBonus: number;
  hpBonus: number;
  mpBonus: number;
  critBonus?: number;
  sellPrice: number;
  quantity: number;
  stackable: boolean;
  effectType?: string;
  effectValueJson?: string;
  enhanceBonusRate: number;
  minEnhanceLevel: number;
  maxEnhanceLevel: number;
  enhancementLevel: number;
  enhancementLuck: number;
  displayName?: string;
};

export type ItemCatalogItem = {
  templateId: string;
  name: string;
  itemType: string;
  itemCategory: string;
  quality: string;
  requiredLevel: number;
  attackBonus: number;
  defenseBonus: number;
  resistanceBonus: number;
  hpBonus: number;
  mpBonus: number;
  critBonus: number;
  randomRange: number;
  description: string;
  sellPrice: number;
  stackable: boolean;
  maxStack: number;
  effectType?: string | null;
  effectValueJson?: string | null;
  enhanceBonusRate: number;
  minEnhanceLevel: number;
  maxEnhanceLevel: number;
};

export type StaminaSnapshot = {
  current: number;
  max: number;
  secondsUntilNext: number;
  secondsUntilFull: number;
  updatedAt: string;
};

export type InventorySnapshot = {
  inventory: Item[];
  equippedItems: Record<string, Item>;
  combatPower: number;
  gold: number;
  capacity: number;
};

export type EnhanceResult = {
  success: boolean;
  cost: number;
  chance: number;
  usedStoneCount: number;
  stoneBonus: number;
  enhancementLevel: number;
  inventory: InventorySnapshot;
};

export type UseItemResult = {
  itemName: string;
  effectType: string;
  rewards: Item[];
  stamina?: StaminaSnapshot;
  inventory: InventorySnapshot;
};

export type CraftResult = {
  recipeId: string;
  recipeName: string;
  rewards: Item[];
  inventory: InventorySnapshot;
};

export type BulkSellResult = {
  soldCount: number;
  goldGained: number;
  inventory: InventorySnapshot;
};

export type EnhancementTransferResult = {
  sourceItem: Item;
  targetItem: Item;
  inventory: InventorySnapshot;
};

export type QuestRow = {
  id: string;
  title: string;
  category: string;
  description: string;
  lore?: string;
  navigationTarget?: string;
  status: string;
  currentValue: number;
  targetValue: number;
  conditionLogic: string;
  resetPeriod: string;
  claimable: boolean;
  progressPercent: number;
  resetAt?: string;
  recommended: boolean;
  conditions: QuestCondition[];
  rewards: QuestReward[];
};

export type QuestCondition = {
  conditionId: string;
  conditionType: string;
  targetId?: string;
  targetValue: number;
  currentValue: number;
  completed: boolean;
};

export type QuestReward = {
  type: string;
  targetId?: string;
  amount: number;
  itemName?: string;
  quality?: string;
  itemCategory?: string;
};

export type MarketListing = {
  id: number;
  playerListing: boolean;
  sellerName: string;
  itemId: number;
  item: {
    templateId: string;
    name: string;
    itemType: string;
    quality: string;
    requiredLevel: number;
    attackBonus: number;
    defenseBonus: number;
    resistanceBonus: number;
    hpBonus: number;
    mpBonus: number;
    critBonus: number;
    sellPrice: number;
    enhancementLevel: number;
    enhancementLuck: number;
    origin: string;
  };
  price: number;
  recommendedPrice: number;
  priceRatio: number;
  dealChance: number;
  status: string;
  sellerType: string;
  marketTag: string;
  listedAt: string;
};

export type MarketSnapshot = {
  listings: MarketListing[];
  activities: MarketActivity[];
  playerSales: MarketSale[];
  rules: {
    taxRate: number;
    priceCapMultiplier: number;
    refreshMinutes: number;
    antiExploit: string;
  };
  onlineTraders: number;
  robotListings: number;
  playerListings: number;
  soldRecently: number;
  averagePrice: number;
};

export type MarketActivity = {
  actorName: string;
  actorTitle: string;
  text: string;
  kind: string;
  createdAt?: string;
  minutesAgo?: number;
};

export type MarketSale = {
  id: number;
  item: MarketListing['item'];
  price: number;
  netGold: number;
  buyerName: string;
  soldAt: string;
};

export type ChatMessage = {
  id: number;
  senderName: string;
  kind: string;
  text: string;
  createdAt: string;
  speaker: ChatSpeaker;
};

export type ChatSpeaker = {
  playerId?: number | null;
  name: string;
  title: string;
  kind: string;
  profession: string;
  level: number;
  power: number;
  experience: number;
  gold: number;
  strength: number;
  agility: number;
  constitution: number;
  intelligence: number;
  spirit: number;
  freePoints: number;
  derivedStats?: DerivedStats;
  equipmentPower?: number;
  equipment: LeaderboardEquipment[];
};

export type LeaderboardEntry = {
  rank: number;
  name: string;
  title: string;
  profession: string;
  level: number;
  power: number;
  player: boolean;
  experience: number;
  gold: number;
  strength: number;
  agility: number;
  constitution: number;
  intelligence: number;
  spirit: number;
  freePoints: number;
  derivedStats: DerivedStats;
  equipmentPower: number;
  equipment: LeaderboardEquipment[];
};

export type LeaderboardEquipment = {
  templateId: string;
  slot: string;
  slotName: string;
  name: string;
  quality: string;
  level: number;
  power: number;
  attackBonus: number;
  defenseBonus: number;
  resistanceBonus: number;
  hpBonus: number;
  mpBonus: number;
  critBonus: number;
  sellPrice: number;
  enhancementLevel: number;
  enhancementLuck: number;
  origin: string;
};

export type HomeSnapshot = {
  player: Player;
  combatPower: number;
  maxHp: number;
  maxMp: number;
  derivedStats: DerivedStats;
  baseStats: DerivedStats;
  equipmentStats: DerivedStats;
  equipmentPower: number;
  powerBreakdown: PowerBreakdown;
  equippedItems: Record<string, Item>;
  inventoryCount: number;
  inventoryCapacity: number;
  config: {
    version: string;
    checksum: string;
    itemCount: number;
    monsterCount: number;
    dungeonCount: number;
    questCount: number;
  };
  stamina: StaminaSnapshot;
};

export type PowerBreakdown = {
  basePower: number;
  equipmentPower: number;
  synergyPower: number;
  totalPower: number;
};

export type DerivedStats = {
  maxHp: number;
  maxMp: number;
  attackPower: number;
  armor: number;
  resistance: number;
  speed: number;
  accuracy: number;
  evasion: number;
  critChance: number;
  critDamage: number;
};

export type Dungeon = {
  id: string;
  name: string;
  description: string;
  difficulty: string;
  recommendedLevel: number;
  recommendedPower: number;
  minimumLevel: number;
  minimumPower: number;
  bossArchetype: string;
  expectedRounds: number;
  drops: DropPreview[];
  cleared: boolean;
  gate: {
    eligible: boolean;
    missingLevel: number;
    missingPower: number;
    label: string;
  };
  stamina?: StaminaSnapshot;
};

export type DropPreview = {
  templateId: string;
  name: string;
  itemType: string;
  itemCategory: string;
  quality: string;
  requiredLevel: number;
  attackBonus: number;
  defenseBonus: number;
  resistanceBonus: number;
  hpBonus: number;
  mpBonus: number;
  sellPrice: number;
  dropRate: number;
};

export type DungeonRunResult = {
  dungeonId: string;
  dungeonName: string;
  success: boolean;
  rating: string;
  monstersKilled: number;
  expGained: number;
  goldGained: number;
  loot: Item[];
  player: Player;
  combatPower: number;
  logs: string[];
  recommendedPower: number;
  playerMaxHp: number;
  playerFinalHp: number;
  frames: BattleFrame[];
  stamina: StaminaSnapshot;
};

export type DungeonSweepResult = {
  dungeonId: string;
  dungeonName: string;
  times: number;
  monstersKilled: number;
  expGained: number;
  goldGained: number;
  loot: Item[];
  player: Player;
  combatPower: number;
  logs: string[];
  stamina: StaminaSnapshot;
};

export type BattleFrame = {
  index: number;
  text: string;
  tone: string;
  roomLabel?: string;
  enemyName?: string;
  playerHp: number;
  playerMaxHp: number;
  enemyHp: number;
  enemyMaxHp: number;
  actor: 'player' | 'enemy' | 'system';
  eventType: 'hit' | 'miss' | 'crit' | 'phase' | 'heal' | 'death';
  damage: number;
  critical: boolean;
  missed: boolean;
};

export type AuthResponse = {
  token: string;
  username: string;
  hasPlayer: boolean;
};

export type GlobalAnnouncement = {
  id: number;
  kind: string;
  actorName: string;
  text: string;
  priority: number;
  createdAt: string;
};

export type RobotActivitySnapshot = {
  robots: RobotActivityView[];
  events: RobotActivityEvent[];
};

export type RobotActivityDetail = {
  robot: RobotActivityView;
  events: RobotActivityEvent[];
  equipment: LeaderboardEquipment[];
};

export type RobotActivityView = {
  id: number;
  name: string;
  title: string;
  profession: string;
  level: number;
  power: number;
  gold: number;
  realMoney: number;
  wealthTierLevel: number;
  wealthTier: string;
  rechargeRmb: number;
  rechargeGold: number;
  dungeonClears: number;
  peakEnhancement: number;
  legendaryLootCount: number;
  currentActivityKind: string;
  currentActivityText: string;
  currentActivityAt: string;
  lastActivityAt: string;
};

export type RobotActivityEvent = {
  robotId: number;
  actorName: string;
  actorTitle: string;
  kind: string;
  text: string;
  createdAt: string;
};

export type RechargeWallet = {
  playerId: number;
  playerName: string;
  gold: number;
  realMoney: number;
  wealthTierLevel: number;
  wealthTierCode: string;
  wealthTier: string;
  minIncome: number;
  maxIncome: number;
  exchangeRate: number;
};

export type RechargeTotals = {
  totalRmb: number;
  totalGold: number;
  marketListedGold: number;
  marketSoldGold: number;
  robotGold: number;
  robotRealMoney: number;
  allPlayerGold: number;
};

export type WealthTierStat = {
  wealthTierLevel: number;
  wealthTier: string;
  playerCount: number;
  realMoneyTotal: number;
  goldTotal: number;
  minIncome: number;
  maxIncome: number;
};

export type RobotRechargeRow = {
  id: number;
  playerId: number;
  playerName: string;
  wealthTierLevel: number;
  wealthTier: string;
  rmbAmount: number;
  goldAmount: number;
  reason: string;
  sourceAction: string;
  currentGold: number;
  currentRealMoney: number;
  createdAt: string;
};

export type CashIncomeRow = {
  id: number;
  playerId: number;
  playerName: string;
  wealthTierLevel: number;
  wealthTier: string;
  rmbAmount: number;
  createdAt: string;
};

export type RechargeDashboard = {
  wallet: RechargeWallet;
  totals: RechargeTotals;
  tierStats: WealthTierStat[];
  robotRecharges: RobotRechargeRow[];
  incomeEvents: CashIncomeRow[];
};

export type RechargeResult = {
  player: Player;
  rmbAmount: number;
  goldAmount: number;
  reason: string;
  sourceAction: string;
  wallet: RechargeWallet;
};

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
  }
}

export async function api<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers ?? {}),
      },
    });
  } catch {
    throw new ApiError('无法连接服务器，请确认前端和 Java 后端服务都已启动。', 0);
  }
  if (!response.ok) {
    const serverUnavailable = response.status >= 500;
    let message = serverUnavailable ? '服务端连接失败，请确认 Java 后端已启动。' : `请求失败：${response.status}`;
    try {
      if (response.headers.get('content-type')?.includes('application/json')) {
        const body = await response.json();
        message = body.message ?? message;
      } else if (!serverUnavailable) {
        const text = await response.text();
        message = text.trim() || message;
      }
    } catch {
      // Keep fallback message.
    }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  if (!text.trim()) {
    return undefined as T;
  }
  if (response.headers.get('content-type')?.includes('application/json')) {
    return JSON.parse(text) as T;
  }
  return text as T;
}

export const authApi = {
  register: (username: string, password: string) =>
    api<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  login: (username: string, password: string) =>
    api<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
};

export const gameApi = {
  createPlayer: (token: string, name: string, profession = 'warrior') =>
    api<Player>(
      '/api/players',
      {
        method: 'POST',
        body: JSON.stringify({ name, profession }),
      },
      token,
    ),
  home: (token: string) => api<HomeSnapshot>('/api/game/home', {}, token),
  itemCatalog: (token: string) => api<ItemCatalogItem[]>('/api/config/items', {}, token),
  dungeons: (token: string) => api<Dungeon[]>('/api/dungeons', {}, token),
  runDungeon: (token: string, dungeonId: string) =>
    api<DungeonRunResult>(
      `/api/dungeons/${dungeonId}/runs`,
      {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
      },
      token,
    ),
  sweepDungeon: (token: string, dungeonId: string, times = 10) =>
    api<DungeonSweepResult>(
      `/api/dungeons/${dungeonId}/sweeps`,
      {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({ times }),
      },
      token,
    ),
  inventory: (token: string) => api<InventorySnapshot>('/api/inventory', {}, token),
  equip: (token: string, itemId: number) => api<InventorySnapshot>(`/api/inventory/${itemId}/equip`, { method: 'POST' }, token),
  equipBest: (token: string) => api<InventorySnapshot>('/api/inventory/equip-best', { method: 'POST' }, token),
  unequip: (token: string, itemId: number) => api<InventorySnapshot>(`/api/inventory/${itemId}/unequip`, { method: 'POST' }, token),
  sell: (token: string, itemId: number) => api<InventorySnapshot>(`/api/inventory/${itemId}/sell`, { method: 'POST' }, token),
  enhance: (token: string, itemId: number, stoneItemIds: number[] = []) =>
    api<EnhanceResult>(
      `/api/inventory/${itemId}/enhance`,
      { method: 'POST', body: JSON.stringify({ stoneItemIds }) },
      token,
    ),
  useItem: (token: string, itemId: number) =>
    api<UseItemResult>(`/api/inventory/${itemId}/use`, { method: 'POST' }, token),
  craftRecipe: (token: string, recipeId: string) =>
    api<CraftResult>(`/api/inventory/recipes/${recipeId}/craft`, { method: 'POST' }, token),
  transferEnhancement: (token: string, sourceItemId: number, targetItemId: number) =>
    api<EnhancementTransferResult>('/api/inventory/transfer-enhancement', { method: 'POST', body: JSON.stringify({ sourceItemId, targetItemId }) }, token),
  bulkSell: (token: string, qualities: string[], itemTypes: string[] = []) =>
    api<BulkSellResult>('/api/inventory/bulk-sell', { method: 'POST', body: JSON.stringify({ qualities, itemTypes }) }, token),
  organizeInventory: (token: string, sort: string) =>
    api<InventorySnapshot>('/api/inventory/organize', { method: 'POST', body: JSON.stringify({ sort }) }, token),
  quests: (token: string) => api<QuestRow[]>('/api/quests', {}, token),
  claimQuest: (token: string, questId: string) => api(`/api/quests/${questId}/claim`, { method: 'POST' }, token),
  marketListings: (token: string) => api<MarketSnapshot>('/api/market/listings', {}, token),
  listItem: (token: string, itemId: number, price: number) =>
    api<MarketListing>('/api/market/listings', { method: 'POST', body: JSON.stringify({ itemId, price }) }, token),
  buyListing: (token: string, listingId: number) =>
    api<MarketListing>(`/api/market/listings/${listingId}/buy`, { method: 'POST' }, token),
  cancelListing: (token: string, listingId: number) =>
    api<void>(`/api/market/listings/${listingId}/cancel`, { method: 'POST' }, token),
  chatMessages: (token: string) => api<ChatMessage[]>('/api/chat/messages', {}, token),
  sendChat: (token: string, text: string) => api<ChatMessage>('/api/chat/messages', { method: 'POST', body: JSON.stringify({ text }) }, token),
  chatStreamUrl: (token: string, afterId = 0) => `/api/chat/stream?${new URLSearchParams({ token, afterId: String(afterId) }).toString()}`,
  leaderboard: (token: string) => api<LeaderboardEntry[]>('/api/leaderboard/power', {}, token),
  robotActivity: (token: string) => api<RobotActivitySnapshot>('/api/robots/activity', {}, token),
  robotActivityDetail: (token: string, robotId: number) => api<RobotActivityDetail>(`/api/robots/${robotId}/activity`, {}, token),
  rechargeDashboard: (token: string) => api<RechargeDashboard>('/api/recharge/dashboard', {}, token),
  recharge: (token: string, rmbAmount: number) =>
    api<RechargeResult>('/api/recharge', { method: 'POST', body: JSON.stringify({ rmbAmount }) }, token),
  announcements: (token: string) => api<GlobalAnnouncement[]>('/api/announcements', {}, token),
};
