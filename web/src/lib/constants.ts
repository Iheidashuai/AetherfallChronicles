import type { AttributeKey, PlayableProfession } from '../types';

export const STAMINA_RECOVERY_SECONDS = 600;
export const ANNOUNCEMENT_SEEN_STORAGE_KEY = 'mythic.announcements.seen';

export const ATTRIBUTE_LABELS: { key: AttributeKey; label: string; meaning: string }[] = [
  { key: 'strength', label: '力量', meaning: '物理攻击' },
  { key: 'agility', label: '敏捷', meaning: '暴击与速度' },
  { key: 'constitution', label: '体质', meaning: '生命与防御' },
  { key: 'intelligence', label: '智力', meaning: '法力与法伤' },
  { key: 'spirit', label: '精神', meaning: '续航与技能' },
];

export const CREATE_PROFESSIONS: {
  id: PlayableProfession;
  name: string;
  title: string;
  role: string;
  difficulty: string;
  tempo: string;
  survival: string;
  summary: string;
  signature: string;
  attributes: Record<AttributeKey, number>;
  growth: string[];
  combat: string[];
  bestFor: string[];
  caution: string;
}[] = [
  {
    id: 'warrior',
    name: '战士',
    title: '近战守线者',
    role: '近战 / 坦克',
    difficulty: '稳健',
    tempo: '稳步推进',
    survival: '高',
    summary: '抗压最强，容错高，适合先熟悉副本节奏。',
    signature: '力量 + 体质成长',
    attributes: { strength: 10, agility: 5, constitution: 8, intelligence: 3, spirit: 4 },
    growth: ['升级额外获得力量与体质', '生命和防御成长更厚', '初期装备容错最高'],
    combat: ['站得住，适合连续刷普通副本', '面对高压怪物时失误成本低', '输出节奏稳定，爆发不是最高'],
    bestFor: ['第一次玩，想稳稳推进', '喜欢抗伤害和正面硬碰硬', '想少看攻略也能开荒'],
    caution: '清怪速度不如爆发职业，需要靠武器和强化补输出。',
  },
  {
    id: 'ranger',
    name: '射手',
    title: '远程游击手',
    role: '远程物理 / 暴击',
    difficulty: '灵活',
    tempo: '快节奏',
    survival: '中',
    summary: '敏捷最高，暴击成长好，适合刷本和追求效率。',
    signature: '敏捷 + 力量成长',
    attributes: { strength: 6, agility: 10, constitution: 5, intelligence: 4, spirit: 5 },
    growth: ['升级额外获得敏捷与力量', '暴击率随敏捷自然抬升', '更依赖武器和饰品收益'],
    combat: ['打低风险副本效率好', '适合追求掉落和市场周转', '高压副本要留意推荐战力'],
    bestFor: ['喜欢快节奏和暴击数字', '愿意比较装备收益', '想兼顾刷本和市场玩法'],
    caution: '身板较薄，越级挑战时比战士更吃装备。',
  },
  {
    id: 'mage',
    name: '法师',
    title: '奥术爆发者',
    role: '远程魔法 / 爆发',
    difficulty: '进阶',
    tempo: '爆发窗口',
    survival: '低-中',
    summary: '智力和精神最高，伤害上限高，但前期容错最低。',
    signature: '智力 + 精神成长',
    attributes: { strength: 3, agility: 4, constitution: 4, intelligence: 10, spirit: 9 },
    growth: ['升级额外获得智力与精神', '法力值和技能续航更强', '后期爆发和范围能力突出'],
    combat: ['适合愿意经营资源的玩家', '装备成型后清场能力强', '前期需要避免硬吃伤害'],
    bestFor: ['喜欢高爆发和技能流', '愿意研究装备与资源', '能接受前期更脆的开荒'],
    caution: '生命和防御起点低，初期副本更需要看推荐战力。',
  },
];
