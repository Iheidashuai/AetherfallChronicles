# 机器人模拟系统

## 设计目标

通过 200-300 个有性格/行为模式的虚拟玩家，让单机游戏产生「仿 MMO」的社交氛围。
玩家在任何时间打开游戏，都能感受到世界是「活的」。

## 系统架构

```
BotManager (全局调度器，每游戏日 tick 一次)
├── BotProfile[]          ← 200-300 个预生成角色
├── BotBehaviorEngine     ← 决定每个 bot 今天做什么
├── ChatGenerator         ← 生成世界频道消息
├── AuctionAgent          ← 管理拍卖场上架/购买
├── RankingSimulator      ← 更新排行榜数据
└── EventReactor          ← 响应游戏事件生成内容
```

## 机器人角色模型

```swift
struct BotProfile {
    let id: UUID
    let name: String              // 预设名字池随机组合
    let profession: Profession    // 战士/射手/法师
    let personality: Personality  // 影响聊天风格
    let activityLevel: Float      // 0.1~1.0 活跃度
    let growthRate: Float         // 0.8~1.2 成长速度系数
    let wealthLevel: Float        // 0.5~1.5 财富倾向
    let chatFrequency: Float      // 发言频率系数
    var currentLevel: Int         // 当前等级（每日更新）
    var currentGear: GearScore    // 装备评分（每日更新）
    var joinDay: Int              // 第几天「加入」游戏
}
```

### 性格类型

| 性格 | 聊天风格 | 占比 |
|------|----------|------|
| friendly | 友善，喜欢帮助、赞美 | 25% |
| showoff | 炫耀，晒装备、晒排名 | 15% |
| casual | 休闲，聊日常、吐槽 | 25% |
| hardcore | 硬核，讨论数值、攻略 | 15% |
| newbie | 萌新，问问题、犯错误 | 10% |
| merchant | 商人，总在聊交易 | 10% |

### 名字生成规则

- 西方风格：「ShadowBlade」「IceQueen_77」「xXDarkKnightXx」
- 混合中英：「剑圣Leon」「小法师Mia」
- 搞笑类：「我不是挂」「别抢我Boss」「强化必成」

## 每日行为调度

当玩家打开游戏时，BotManager 根据距上次登录经过的游戏天数，批量计算所有机器人变化：

### 1. 等级增长

```
newLevel = currentLevel + dailyGrowth(activityLevel, growthRate)
dailyGrowth = round(activityLevel × growthRate × 0.5)

高活跃 bot: +0.5~0.6 级/游戏日
低活跃 bot: +0.1~0.2 级/游戏日
上限：不超过玩家等级 +3（保证玩家努力就能领先）
```

### 2. 装备评分更新

```
gearScore += random(5~20) × activityLevel × (currentLevel / 10)
偶尔大幅跳跃（模拟「出了好装备」）→ 触发全服通告
```

### 3. 拍卖场行为

```
上架概率: random() < 0.1 × activityLevel → 上架 1-3 件物品
购买概率: random() < 0.05 × wealthLevel → 购买拍卖场物品
```

### 4. 离线追算

- 玩家关闭游戏再打开时，计算经过了多少个游戏日
- 批量追算所有 bot 的等级/装备/排行榜变化
- 生成离线期间的聊天记录（可翻看）
- **上限**：离线最多追算 30 游戏日（5 小时现实时间），防止计算量过大

---

## 世界频道聊天系统

### 消息频率

每 30-120 秒一条（10 分钟 1 游戏日内约 5-15 条消息）

### 消息模板库（按性格分类）

```
[friendly]
- "有人需要帮忙打{dungeon_name}吗？我{level}级{profession}"
- "{player_name} 厉害了，排名又上去了"
- "今天日常做完了吗，记得去领奖励"

[showoff]
- "嘿嘿，刚从{dungeon_name}出了{item_quality}{item_type}！"
- "我的{weapon_name}终于+{enhance_level}了，花了我{gold_amount}金"
- "战力排行第{rank}，还行吧"

[casual]
- "今天这个神秘商人卖的东西也太贵了"
- "{profession}真的需要削弱了，太离谱"
- "有没有人觉得{dungeon_name}的Boss太难了"

[hardcore]
- "{profession}走{build}流的话，{stat}堆到{value}就够了"
- "{dungeon_name}地狱难度第二阶段注意躲{skill}就行"
- "计算了一下，+{enhance_level}性价比最高，再往上不值"

[newbie]
- "请问{dungeon_name}怎么去啊"
- "为什么我的装备不能穿啊，是不是bug"
- "大佬们一般怎么赚金币的"

[merchant]
- "收{item_type}{quality}以上的，价格好商量"
- "出{item_name}，+{enhance_level}，{gem_count}孔，私聊"
- "拍卖场那个{item_name}被谁买走了，我还没来得及出价"
```

### 动态触发规则

| 触发条件 | 生成的消息类型 |
|----------|---------------|
| 玩家排名上升 | friendly bot @玩家恭喜 |
| 有 bot 等级突破整十 | showoff 类消息 |
| 新副本解锁 | 多个 bot 讨论副本 |
| 拍卖场出现橙装 | 多人惊叹 + 商人出价 |
| 世界 Boss 出现 | 大量组队/兴奋消息 |
| 玩家长时间未登录再回来 | "xxx 好久不见了！" |
| 全服通告出现 | 1-3 个 bot 在 15-30 秒内做出反应 |

---

## 拍卖场机器人行为

### 上架逻辑

```
物品选择：从「等级匹配的物品模板池」随机选
物品等级：bot.currentLevel + random(-3, +3)
品质分布：绿 50%, 蓝 35%, 紫 13%, 橙 2%
定价：itemBaseValue × random(0.7, 1.3)
超值物品(5%)：定价 × random(0.3, 0.5) ← 制造捡漏感
```

**每日上架节奏（基于 10 分钟游戏日）：**
- 第 1 分钟（早晨）：8-12 件
- 第 5 分钟（傍晚）：10-15 件
- 随机时段：3-5 件

### 购买逻辑（买走玩家物品）

```
每分钟检查玩家上架物品：
1. 价格合理性：玩家定价 ≤ 物品价值 × 1.2
2. 购买概率 = 0.3 × (物品价值 / 定价)
3. 上架超过 5 分钟：购买概率 +0.2
4. 紫/橙品质：购买概率 +0.1

效果：合理定价的物品约 3-5 分钟（现实时间）能卖出
```

---

## 排行榜竞争逻辑

### 设计目标

玩家每天玩 1-2 小时 → 稳定 Top 5-15
特别肝 → 能冲进 Top 3

### 排行榜类型

- 等级排行
- 战力排行
- 副本通关速度排行
- 财富排行

### 更新规则（每游戏日）

```
等级排行：
  Top 1 bot 增速 = 玩家增速 × 0.9
  Top 2-5 bot 增速 = 玩家增速 × 0.7~0.85
  Top 6-20 = 正态分布

战力排行：
  战力 = 等级基础 + 装备评分 + 强化加成
  bot 战力增长有随机波动

通关速度：
  bot 通关时间 = 理论最优 × random(1.1, 2.0)
  Top 1 bot 时间 = 玩家最佳 × random(0.95, 1.05)
```

### 「追赶」机制

```
if 玩家排名上升:
    附近 3 名 bot 下一日 growthRate × 1.1（微加速）

if 玩家排名稳定 3 天:
    下方 bot 逐渐追上（缩小差距到 5%）→「不进则退」

if 玩家连续 3 天未登录:
    bot 增速 × 0.5（不让回来的玩家绝望）
    世界频道："xxx 最近没上线啊，不会弃坑了吧"
```

---

## 世界 Boss 事件

每 3 游戏日（30 分钟现实时间）出现一次：

1. 出现前 1 游戏日（10分钟前）：频道公告预告
2. 出现时：全服公告 + bot 发言 "来了来了！"
3. 持续当前游戏日的 3 分钟（现实时间）
4. 玩家可参与打（即时战斗，独立副本）
5. 结算时：
   - 玩家伤害 vs bot 伤害（预计算）
   - 全服公告 Top 1 奖励
   - bot 频道讨论结果

---

## 最终体验感知

| 时间节点 | 玩家感知 |
|----------|----------|
| 每次打开游戏 | 世界频道有聊天记录可翻看 |
| 每个游戏日（10分钟） | 拍卖场有新物品，排行榜有变化 |
| 完成副本 | 可能被 bot @恭喜 |
| 上架物品 | 几分钟后可能被买走 |
| 排名上升 | bot 议论"xxx好猛" |
| 3 天没玩（现实） | 回来看到"你终于回来了" |
| 获得稀有装备 | 全服通告，bot 羡慕 |
