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
  refineLevel?: number;
  refineFocus?: string;
  ascensionLevel?: number;
  ascensionLuck?: number;
  socketAttackBonus?: number;
  socketDefenseBonus?: number;
  socketResistanceBonus?: number;
  socketHpBonus?: number;
  socketMpBonus?: number;
  socketCritBonus?: number;
  affixAttackBonus?: number;
  affixDefenseBonus?: number;
  affixResistanceBonus?: number;
  affixHpBonus?: number;
  affixMpBonus?: number;
  affixCritBonus?: number;
  sockets?: EquipmentSocket[];
  affixes?: EquipmentAffix[];
  displayName?: string;
  description?: string;
};

export type EquipmentSocket = {
  socketIndex: number;
  unlocked: boolean;
  gemItemId?: number | null;
  gemTemplateId?: string | null;
  gemName?: string | null;
  gemQuality?: string | null;
  statKey?: string | null;
  statValue?: number;
  rank?: number;
};

export type EquipmentAffix = {
  affixIndex: number;
  statKey: string;
  statValue: number;
  tier: number;
  locked: boolean;
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

export type RefineResult = {
  item: Item;
  refineLevel: number;
  refineFocus: string;
  goldCost: number;
  essenceCost: number;
  shardCost: number;
  orbCost: number;
  inventory: InventorySnapshot;
};

export type MaterialCost = {
  templateId: string;
  quantity: number;
};

export type SocketUnlockCost = {
  goldCost: number;
  socketCores: number;
  gemDust: number;
  essence: number;
  chance: number;
};

export type ReforgeCost = {
  goldCost: number;
  orbs: number;
  essence: number;
  lockStones: number;
  chance: number;
};

export type AscensionCost = {
  goldCost: number;
  ascensionCores: number;
  essence: number;
  shards: number;
  guards: number;
  chance: number;
};

export type ProcessingItemView = {
  item: Item;
  socketLimit: number;
  affixLimit: number;
  unlockedSocketCount: number;
  affixCount: number;
  nextSocketCost: SocketUnlockCost;
  reforgeCost: ReforgeCost;
  ascensionCost: AscensionCost;
};

export type ProcessingLogView = {
  actionType: string;
  success: boolean;
  summary: string;
  powerBefore: number;
  powerAfter: number;
  createdAt: string;
};

export type EquipmentProcessingSnapshot = {
  equipment: ProcessingItemView[];
  gems: Item[];
  materials: Record<string, number>;
  recentLogs: ProcessingLogView[];
  inventory: InventorySnapshot;
};

export type EquipmentProcessingResult = {
  actionType: string;
  success: boolean;
  message: string;
  item: Item;
  consumed: MaterialCost[];
  powerBefore: number;
  powerAfter: number;
  snapshot: EquipmentProcessingSnapshot;
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
    itemCategory: string;
    marketCategory: string;
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
    refineLevel?: number;
    ascensionLevel?: number;
    quantity: number;
    stackable: boolean;
    description?: string;
    effectType?: string;
    effectValueJson?: string;
    processingSummary?: string;
    origin: string;
  };
  quantity: number;
  unitPrice: number;
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

export type ArenaProfile = {
  playerId: number;
  name: string;
  profession: string;
  level: number;
  controllerType: string;
  combatPower: number;
  rating: number;
  tier: string;
  rank: number;
  arenaCoins: number;
  todayAttemptsUsed: number;
  dailyAttempts: number;
  wins: number;
  losses: number;
  winStreak: number;
};

export type ArenaOpponent = {
  playerId: number;
  name: string;
  profession: string;
  level: number;
  controllerType: string;
  combatPower: number;
  rating: number;
  tier: string;
  rank: number;
  wins: number;
  losses: number;
  challengeHint: string;
  challengeable: boolean;
  disabledReason?: string | null;
};

export type ArenaMatchSummary = {
  matchId: number;
  attackerId: number;
  attackerName: string;
  defenderId: number;
  defenderName: string;
  attackerWon: boolean;
  attackerRatingChange: number;
  defenderRatingChange: number;
  arenaCoins: number;
  resultText: string;
  createdAt: string;
};

export type ArenaBattleEvent = {
  sequenceNo: number;
  actor: string;
  eventType: string;
  tone: string;
  text: string;
  attackerHp: number;
  defenderHp: number;
  damage: number;
  critical: boolean;
  missed: boolean;
  skillName?: string | null;
};

export type ArenaFighterSnapshot = {
  playerId: number;
  name: string;
  profession: string;
  level: number;
  combatPower: number;
  maxHp: number;
  attackPower: number;
  armor: number;
  resistance: number;
  buildName: string;
  strategy: string;
  equipmentSummary: string[];
  skills: string[];
};

export type ArenaMatchDetail = {
  summary: ArenaMatchSummary;
  profile: ArenaProfile;
  attacker: ArenaFighterSnapshot;
  defender: ArenaFighterSnapshot;
  events: ArenaBattleEvent[];
};

export type ArenaShopOffer = {
  id: string;
  name: string;
  description: string;
  itemTemplateId: string;
  itemQuantity: number;
  priceCoins: number;
  requiredRating: number;
  sortOrder: number;
  affordable: boolean;
  unlocked: boolean;
  disabledReason?: string | null;
};

export type ArenaShopPurchaseResult = {
  offer: ArenaShopOffer;
  profile: ArenaProfile;
  rewards: string[];
};

export type ArenaOverview = {
  profile: ArenaProfile;
  opponents: ArenaOpponent[];
  recentMatches: ArenaMatchSummary[];
  shop: ArenaShopOffer[];
  rankings: ArenaProfile[];
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

export type RiftModifier = {
  id: string;
  name: string;
  description: string;
  difficultyScore: number;
  rewardBonus: number;
  enabled: boolean;
};

export type RiftReward = {
  essence: number;
  shards: number;
  orbs: number;
  multiplier: number;
};

export type RiftMaterials = {
  essence: number;
  shards: number;
  orbs: number;
};

export type RiftBattleEvent = {
  index: number;
  turn: number;
  roomIndex: number;
  eventType: string;
  actorName?: string | null;
  targetName?: string | null;
  text: string;
  value: number;
  actorHp: number;
  targetHp: number;
  tone: string;
};

export type RiftLeaderboardEntry = {
  rank: number;
  playerId: number;
  playerName: string;
  controllerType: string;
  tier: number;
  rating: string;
  score: number;
  turnsTaken: number;
  playerFinalHp: number;
  playerMaxHp: number;
  createdAt: string;
  self: boolean;
};

export type RiftTierPreview = {
  tier: number;
  recommendedPower: number;
  minimumPower: number;
  modifiers: RiftModifier[];
  rewardPreview: RiftReward;
};

export type RiftSnapshot = {
  unlocked: boolean;
  unlockHint: string;
  requiredLevel: number;
  requiredDungeonId: string;
  bestTier: number;
  bestScore: number;
  bestRating?: string | null;
  weeklyBestTier: number;
  challengeTiers: number[];
  nextTier: number;
  staminaCost: number;
  recommendedPower: number;
  minimumPower: number;
  modifiers: RiftModifier[];
  rewardPreview: RiftReward;
  tierPreviews: RiftTierPreview[];
  materials: RiftMaterials;
  stamina: StaminaSnapshot;
  leaderboard: RiftLeaderboardEntry[];
  weeklyRewardAvailable: boolean;
};

export type RiftRunResult = {
  runId: number;
  tier: number;
  success: boolean;
  rating: string;
  score: number;
  turnsTaken: number;
  monstersKilled: number;
  combatPower: number;
  recommendedPower: number;
  modifierIds: string[];
  rewards: RiftReward;
  playerFinalHp: number;
  playerMaxHp: number;
  events: RiftBattleEvent[];
  materials: RiftMaterials;
  stamina: StaminaSnapshot;
};

export type RiftWeeklyRewardResult = {
  weekKey: string;
  bestTier: number;
  essence: number;
  shards: number;
  orbs: number;
  chest: Item;
  materials: RiftMaterials;
};

export type BuildTalent = {
  nodeId: string;
  name: string;
  description: string;
  statKey: string;
  statValue: number;
};

export type BuildSkillSlot = {
  slotIndex: number;
  triggerKind: string;
  skillId?: string | null;
  skillName?: string | null;
  learned: boolean;
};

export type BuildEquipmentSlot = {
  slotName: string;
  itemId?: number | null;
  itemName?: string | null;
  itemType?: string | null;
  quality?: string | null;
  power: number;
};

export type BuildScore = {
  damage: number;
  defense: number;
  sustain: number;
  speed: number;
  rift: number;
  completion: number;
};

export type BuildPreset = {
  id: string;
  name: string;
  profession: string;
  archetype: string;
  strategy: string;
  description: string;
  refineFocus: string;
  talents: BuildTalent[];
  skillSlots: BuildSkillSlot[];
};

export type PlayerBuild = {
  id: number;
  name: string;
  sourcePresetId?: string | null;
  profession: string;
  archetype: string;
  strategy: string;
  refineFocus: string;
  active: boolean;
  equipmentSlots: BuildEquipmentSlot[];
  skillSlots: BuildSkillSlot[];
  talents: string[];
  score: BuildScore;
};

export type BuildSnapshot = {
  player: Player;
  presets: BuildPreset[];
  builds: PlayerBuild[];
  activeBuild?: PlayerBuild | null;
  availableEquipment: Item[];
  availableSkills: SkillView[];
  suggestedTier: number;
  suggestedMinimumPower: number;
};

export type BuildMutationRequest = {
  name?: string;
  strategy?: string;
  refineFocus?: string;
  equipmentSlots?: { slotName: string; itemId?: number | null; preferredItemType?: string | null }[];
  skillSlots?: { slotIndex: number; triggerKind: string; skillId?: string | null }[];
  talents?: string[];
};

export type BuildActivationResult = {
  buildId: number;
  buildName: string;
  appliedEquipmentCount: number;
  configuredSkillCount: number;
  beforePower: number;
  afterPower: number;
  warnings: string[];
  snapshot: BuildSnapshot;
};

export type RiftSimulationResult = RiftRunResult & {
  buildId: number;
};

export type PowerBreakdown = {
  basePower: number;
  equipmentPower: number;
  skillPower: number;
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
  description?: string;
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
  eventType: 'hit' | 'miss' | 'crit' | 'phase' | 'heal' | 'shield' | 'death';
  damage: number;
  critical: boolean;
  missed: boolean;
  skillId?: string | null;
  skillName?: string | null;
  visualKey?: string | null;
  targetSide?: 'player' | 'enemy' | 'system' | string | null;
  effectValue?: number;
};

export type SkillSnapshot = {
  player: Player;
  skills: SkillView[];
  skillPower: number;
  learnedCount: number;
  affordableCount: number;
  rankLevelStep: number;
};

export type SkillView = {
  id: string;
  name: string;
  ownerScope: string;
  profession: string;
  archetype: string;
  unlockLevel: number;
  maxRank: number;
  rankCap: number;
  rank: number;
  category: string;
  targetType: string;
  damageType: string;
  baseMultiplier: number;
  rankMultiplierGrowth: number;
  cooldown: number;
  mpCostBase: number;
  mpCostGrowth: number;
  effectType: string;
  effectPowerBase: number;
  effectPowerGrowth: number;
  durationRounds: number;
  triggerKind: string;
  priority: number;
  visualKey: string;
  description: string;
  tierCoef: number;
  learned: boolean;
  unlocked: boolean;
  canLearn: boolean;
  canUpgrade: boolean;
  learnCost: number;
  nextRankCost: number;
  currentValue: number;
  nextValue: number;
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

export type ShopOffer = {
  id: string;
  name: string;
  description: string;
  category: string;
  priceRmb: number;
  itemTemplateId: string | null;
  itemName: string | null;
  itemType: string | null;
  itemCategory: string | null;
  quality: string | null;
  itemQuantity: number;
  goldAmount: number;
  requiredLevel: number;
  unlocked: boolean;
  affordable: boolean;
  sortOrder: number;
};

export type ShopSnapshot = {
  wallet: RechargeWallet;
  offers: ShopOffer[];
  categories: string[];
};

export type ShopPurchaseResult = {
  offer: ShopOffer;
  quantity: number;
  totalPriceRmb: number;
  goldGained: number;
  rewards: Item[];
  wallet: RechargeWallet;
  inventory: InventorySnapshot;
  player: Player;
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
  skills: (token: string) => api<SkillSnapshot>('/api/skills', {}, token),
  learnSkill: (token: string, skillId: string) => api<SkillSnapshot>(`/api/skills/${skillId}/learn`, { method: 'POST' }, token),
  upgradeSkill: (token: string, skillId: string) => api<SkillSnapshot>(`/api/skills/${skillId}/upgrade`, { method: 'POST' }, token),
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
  refine: (token: string, itemId: number, focus: string) =>
    api<RefineResult>(`/api/inventory/${itemId}/refine`, { method: 'POST', body: JSON.stringify({ focus }) }, token),
  equipmentProcessing: (token: string) =>
    api<EquipmentProcessingSnapshot>('/api/equipment-processing', {}, token),
  unlockSocket: (token: string, itemId: number) =>
    api<EquipmentProcessingResult>(`/api/equipment-processing/${itemId}/sockets/unlock`, { method: 'POST' }, token),
  socketGem: (token: string, itemId: number, socketIndex: number, gemItemId: number) =>
    api<EquipmentProcessingResult>(
      `/api/equipment-processing/${itemId}/sockets/${socketIndex}/socket`,
      { method: 'POST', body: JSON.stringify({ gemItemId }) },
      token,
    ),
  unsocketGem: (token: string, itemId: number, socketIndex: number) =>
    api<EquipmentProcessingResult>(`/api/equipment-processing/${itemId}/sockets/${socketIndex}/unsocket`, { method: 'POST' }, token),
  upgradeGems: (token: string, gemItemIds: number[]) =>
    api<EquipmentProcessingResult>('/api/equipment-processing/gems/upgrade', { method: 'POST', body: JSON.stringify({ gemItemIds }) }, token),
  reforgeEquipment: (token: string, itemId: number, lockedAffixIndexes: number[]) =>
    api<EquipmentProcessingResult>(
      `/api/equipment-processing/${itemId}/reforge`,
      { method: 'POST', body: JSON.stringify({ lockedAffixIndexes }) },
      token,
    ),
  ascendEquipment: (token: string, itemId: number, useProtector: boolean) =>
    api<EquipmentProcessingResult>(
      `/api/equipment-processing/${itemId}/ascend`,
      { method: 'POST', body: JSON.stringify({ useProtector }) },
      token,
    ),
  bulkSell: (token: string, qualities: string[], itemTypes: string[] = []) =>
    api<BulkSellResult>('/api/inventory/bulk-sell', { method: 'POST', body: JSON.stringify({ qualities, itemTypes }) }, token),
  organizeInventory: (token: string, sort: string) =>
    api<InventorySnapshot>('/api/inventory/organize', { method: 'POST', body: JSON.stringify({ sort }) }, token),
  quests: (token: string) => api<QuestRow[]>('/api/quests', {}, token),
  claimQuest: (token: string, questId: string) => api(`/api/quests/${questId}/claim`, { method: 'POST' }, token),
  marketListings: (token: string) => api<MarketSnapshot>('/api/market/listings', {}, token),
  listItem: (token: string, itemId: number, quantity: number, unitPrice: number) =>
    api<MarketListing>('/api/market/listings', { method: 'POST', body: JSON.stringify({ itemId, quantity, unitPrice }) }, token),
  buyListing: (token: string, listingId: number) =>
    api<MarketListing>(`/api/market/listings/${listingId}/buy`, { method: 'POST' }, token),
  cancelListing: (token: string, listingId: number) =>
    api<void>(`/api/market/listings/${listingId}/cancel`, { method: 'POST' }, token),
  shop: (token: string) => api<ShopSnapshot>('/api/shop', {}, token),
  buyShopOffer: (token: string, offerId: string, quantity: number) =>
    api<ShopPurchaseResult>(`/api/shop/offers/${offerId}/buy`, { method: 'POST', body: JSON.stringify({ quantity }) }, token),
  rifts: (token: string) => api<RiftSnapshot>('/api/endgame/rifts', {}, token),
  runRift: (token: string, tier: number) =>
    api<RiftRunResult>(
      `/api/endgame/rifts/${tier}/runs`,
      { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() } },
      token,
    ),
  claimRiftWeeklyReward: (token: string) =>
    api<RiftWeeklyRewardResult>('/api/endgame/rifts/weekly-reward', { method: 'POST' }, token),
  arena: (token: string) => api<ArenaOverview>('/api/arena', {}, token),
  challengeArena: (token: string, targetId: number) =>
    api<ArenaMatchDetail>(`/api/arena/challenge/${targetId}`, { method: 'POST' }, token),
  arenaMatch: (token: string, matchId: number) => api<ArenaMatchDetail>(`/api/arena/matches/${matchId}`, {}, token),
  arenaRankings: (token: string) => api<ArenaProfile[]>('/api/arena/rankings', {}, token),
  buyArenaOffer: (token: string, offerId: string) =>
    api<ArenaShopPurchaseResult>(`/api/arena/shop/${offerId}/buy`, { method: 'POST' }, token),
  builds: (token: string) => api<BuildSnapshot>('/api/builds', {}, token),
  copyBuildPreset: (token: string, presetId: string) =>
    api<BuildSnapshot>(`/api/builds/presets/${presetId}/copy`, { method: 'POST' }, token),
  createBuild: (token: string, request: BuildMutationRequest) =>
    api<BuildSnapshot>('/api/builds', { method: 'POST', body: JSON.stringify(request) }, token),
  updateBuild: (token: string, buildId: number, request: BuildMutationRequest) =>
    api<BuildSnapshot>(`/api/builds/${buildId}`, { method: 'PUT', body: JSON.stringify(request) }, token),
  activateBuild: (token: string, buildId: number) =>
    api<BuildActivationResult>(`/api/builds/${buildId}/activate`, { method: 'POST' }, token),
  simulateBuild: (token: string, buildId: number, tier: number) =>
    api<RiftSimulationResult>(`/api/builds/${buildId}/simulate`, { method: 'POST', body: JSON.stringify({ tier }) }, token),
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
  guildBrowse: (token: string) => api<GuildSummary[]>('/api/guild/guilds', {}, token),
  myGuild: (token: string) => api<GuildHomeResponse>('/api/guild', {}, token),
  joinGuild: (token: string, guildId: number) =>
    api<GuildHomeResponse>(`/api/guild/${guildId}/join`, { method: 'POST' }, token),
  leaveGuild: (token: string) => api<GuildHomeResponse>('/api/guild/leave', { method: 'POST' }, token),
  guildChat: (token: string) => api<GuildChatMessage[]>('/api/guild/chat', {}, token),
  sendGuildChat: (token: string, text: string) =>
    api<GuildChatMessage[]>('/api/guild/chat', { method: 'POST', body: JSON.stringify({ text }) }, token),
  guildBoss: (token: string) => api<GuildBossView>('/api/guild/boss', {}, token),
  attackGuildBoss: (token: string) => api<GuildBossAttackResult>('/api/guild/boss/attack', { method: 'POST' }, token),
  donateGuild: (token: string, amount: number) =>
    api<GuildDonateResult>('/api/guild/donate', { method: 'POST', body: JSON.stringify({ amount }) }, token),
  guildShop: (token: string) => api<GuildShopView>('/api/guild/shop', {}, token),
  buyGuildShop: (token: string, offerId: string) =>
    api<GuildShopView>(`/api/guild/shop/${offerId}/buy`, { method: 'POST' }, token),
  guildRanking: (token: string) => api<GuildRankEntry[]>('/api/guild/ranking', {}, token),
};

export type GuildSummary = {
  id: number;
  name: string;
  level: number;
  rank: number;
  memberCount: number;
  leaderName: string;
  recruitingBlurb: string;
};
export type GuildMember = {
  playerId: number;
  name: string;
  kind: string;
  profession: string;
  level: number;
  role: string;
  weeklyContribution: number;
  totalContribution: number;
};
export type GuildView = {
  guild: GuildSummary;
  myRole: string;
  members: GuildMember[];
  perks: string[];
  myGuildCoin: number;
  fund: number;
};
export type GuildDonateResult = { amount: number; coinGained: number; guildCoin: number; guild: GuildView };
export type GuildShopOffer = {
  id: string;
  name: string;
  description: string;
  costGuildCoin: number;
  rewardKind: string;
  rewardAmount: number;
};
export type GuildShopView = { guildCoin: number; offers: GuildShopOffer[] };
export type GuildRankEntry = {
  id: number;
  name: string;
  level: number;
  weeklyContribution: number;
  rank: number;
  mine: boolean;
};
export type GuildHomeResponse = { guild: GuildView | null };
export type GuildChatMessage = { id: number; senderName: string; kind: string; text: string; createdAt: string };
export type GuildBossInfo = {
  id: number;
  name: string;
  tier: number;
  hpCurrent: number;
  hpMax: number;
  status: string;
  weekKey: string;
};
export type GuildBossContributor = { playerId: number; name: string; kind: string; damage: number; rank: number };
export type GuildBossView = {
  boss: GuildBossInfo;
  topContributors: GuildBossContributor[];
  myDamage: number;
  myRank: number;
};
export type GuildBossAttackResult = { damage: number; killed: boolean; spawnedTier: number; view: GuildBossView };
