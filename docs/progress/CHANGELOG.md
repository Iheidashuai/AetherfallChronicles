# 开发进度日志

## 当前阶段：Phase 1 完成 ✓

---

### 2026-05-31 - 副本大厅与 UI 规范补充

**完成内容：**
- [x] 新增副本大厅流程：主页「进入副本」进入副本列表，不再直接挑战固定副本
- [x] 首版内置 5 个副本：毒蛛巢穴、腐朽沼泽、精灵遗迹、暗影树心、月光猎场
- [x] 新增副本详情页：展示推荐等级、推荐战力、玩家战力、风险提示、怪物信息、可能掉落
- [x] 战斗入口改为使用玩家选中的副本，结算页显示对应副本名
- [x] 修复结算页、背包页在 iPhone 17 Pro 竖屏下的横向裁切和遮挡问题
- [x] 新增 UI 设计规范文档，后续页面默认遵循响应式暗色 RPG 系统界面规则

**技术细节：**
- 副本配置仍内置在 `ConfigLoader.swift`
- 掉落详情由副本房间怪物的 `lootTable` 聚合生成
- 等级或战力不足仅展示风险提示，不阻止挑战
- UI 规范文档：`docs/specs/09-ui-design-guidelines.md`

---

### 2026-05-30 - Phase 1 核心可玩完成

**完成内容：**
- [x] Xcode 项目搭建（xcodegen + SpriteKit + SwiftUI）
- [x] ECS 架构（Entity/Component/System）
- [x] 虚拟摇杆 + 技能按钮输入系统
- [x] 战士职业：旋风斩 + 猛击两个技能
- [x] 即时战斗系统：自动普攻、技能CD、闪避无敌帧
- [x] 伤害计算 + 暴击 + 击中反馈（帧冻结/击退/屏震/伤害数字）
- [x] 怪物 AI（巡逻/追击/攻击状态机）
- [x] Boss AI（腐败树人王：2 阶段，挥击/根须/召唤小怪）
- [x] 副本系统：4 房间（3 普通 + 1 Boss），通关评价 S/A/B/C
- [x] 装备系统：20+ 件装备，白/绿/蓝三品质，穿戴即时生效
- [x] 掉落系统：怪物掉率表驱动
- [x] 背包系统：40 格，装备/出售
- [x] 全套 UI：主菜单、战斗 HUD、背包、角色面板、副本结算
- [x] 存档系统：JSON 持久化（SQLite 暂用 JSON 替代）
- [x] 编译通过 + 模拟器运行成功

**技术细节：**
- 项目路径：`MythicRealm/`
- 构建方式：`xcodegen generate && xcodebuild -scheme MythicRealm -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- 运行方式：`xcrun simctl install "iPhone 17 Pro" <path>/MythicRealm.app && xcrun simctl launch "iPhone 17 Pro" com.mythicrealm.game`

---

### 2026-05-30 - 项目设计完成

**完成内容：**
- [x] 确定技术选型：Swift + SpriteKit + SwiftUI + SQLite
- [x] 确定画面风格：2D 像素风
- [x] 确定战斗方式：即时动作
- [x] 确定时间系统：10 分钟 = 1 游戏日
- [x] 完成角色系统设计（3 职业、属性体系、技能树）
- [x] 完成装备系统设计（强化/镶嵌/套装/品质）
- [x] 完成战斗系统设计（操作方式/元素克制/Boss 机制）
- [x] 完成副本系统设计（5 章结构/难度体系/奖励）
- [x] 完成经济系统设计（金币产出消耗/数值公式/通胀控制）
- [x] 完成机器人模拟系统设计（角色模型/行为调度/聊天/排行榜）
- [x] 完成全服通告系统设计（触发规则/展示层次/bot 反应）
- [x] 完成商店 & 拍卖场设计

**设计文档：**
- `docs/specs/00-overview.md` - 游戏总览 & 技术选型
- `docs/specs/01-character-system.md` - 角色系统
- `docs/specs/02-equipment-system.md` - 装备系统
- `docs/specs/03-combat-system.md` - 战斗系统
- `docs/specs/04-dungeon-system.md` - 副本 & 任务系统
- `docs/specs/05-economy-system.md` - 经济系统
- `docs/specs/06-bot-simulation.md` - 机器人模拟系统
- `docs/specs/07-announcement-system.md` - 全服通告系统
- `docs/specs/08-shop-auction.md` - 商店 & 拍卖场
