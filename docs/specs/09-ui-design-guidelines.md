# UI 设计规范（Web）

> 本文档定义当前 **React + Vite Web 客户端** 的 UI 规范。旧版 SwiftUI / iPhone 竖屏规范已废弃。
> 来源：`web/src/styles.css`、`web/src/App.tsx`、`AGENTS.md` 的桌面布局规则。

## 设计目标

- **桌面优先、响应式**：主目标是桌面浏览器的"工作台（workbench）"布局，移动端为收窄回退。
- **信息优先**：系统化 RPG 界面，不做营销式大 Hero；优先展示角色状态、数值、列表与操作。
- **暗色魔幻风**：背景深色（`#101217` / `#14171b`），卡片用低透明白色叠层，金色描边（`rgba(213,193,122,*)`）作魔幻点缀。
- **可扫读**：列表卡、详情、背包、结算都要让玩家快速比较等级、战力、品质、风险、奖励。

## 布局架构（重要）

应用外壳是 `.app-shell` → `.phone-frame` → `.screen-host` → `.screen`。其中 `.phone-frame` 有**两套定义，靠层叠顺序生效**：

1. 基础（styles.css 早段）：`width: min(100%, 460px)` 手机竖屏。
2. 无条件覆盖（styles.css 中段）：`width: min(calc(100vw - 32px), 1480px); height: min(calc(100vh - 32px), 940px)` —— **桌面工作台，后定义胜出，成为默认**。
3. `@media (max-width: 900px)`：回退为全宽手机布局（`width:100%`、`border-radius:0`）。

> ⚠️ **已知技术债**：同名 `.phone-frame` 双定义 + 1480px 桌面布局仍叫 "phone-frame"，是误导且脆弱。重构 CSS 时应改名（如 `.app-frame`）并改为显式断点，而非靠覆盖顺序。详见前端拆分方案的"后续"。

工作台页面（home / dungeon / market / blacksmith / builds / endgame / arena / chat / leaderboard / robots / recharge 等）使用 `*-workbench` 网格布局：多为 `grid-template-columns: minmax(主栏) 1fr` 的两栏 / 三栏。

## 滚动规则（来自 AGENTS.md，强制）

桌面工作台**绝不允许内容溢出陷阱**。任何可能超出视口的面板 / 列表 / 日志，必须有可计算高度的显式滚动容器：

- 用 `height: 100%` / `flex: 1` / `minmax(0, 1fr)` + `min-height: 0` + `overflow: auto`。
- 新增或修改桌面页时，必须在 **1366×768 和 1920×1080** 两个尺寸验证主内容区与左右侧栏的滚动行为。
- 重点页面：dungeon、market、blacksmith、builds、endgame、arena、chat、leaderboard、robots。

## 组件规范

### 卡片
- 背景 `rgba(255,255,255,0.04~0.065)`，圆角 8~12px，重要卡可加同色系描边。
- 不嵌套装饰性卡片；分组用 section 标题 + 单层卡。

### 按钮
- 主操作按钮高度约 44~58px；并排按钮共享剩余宽度，不用固定宽。
- 危险 / 挑战用红、确认 / 继续用绿、背包 / 信息用蓝、角色 / 稀有用紫。
- `:disabled` 统一 `opacity: 0.55` + `cursor: not-allowed`。

### 列表与网格
- 物品 / 掉落格用自适应网格（`repeat(auto-fit, minmax(…))`），避免固定列数溢出。
- 副本用纵向卡片列表，展示名称、难度、推荐等级 / 战力、风险、主要掉落。

### 详情与弹窗
- 详情结构：顶部栏（`TopBar`）→ 摘要 → 风险提示 → 信息 section → 底部主操作。
- 弹窗（`ItemDetail` / `EnhanceModal` / `ConfirmDialog` / `FeedbackDialog`）居中遮罩，不遮挡可滚内容。
- 风险提示不阻断操作时用橙色；真正禁用 / 失败才用红色。

## 色彩语义

- 黄 / 金：战力、金币、评分、重点奖励
- 红：挑战、危险、伤害、低生命
- 蓝：背包、信息、MP
- 绿：继续、可用、恢复
- 紫：角色、Boss、稀有 / 特殊
- 灰：说明文本、次级状态、未装备槽

装备品质颜色统一取代码中的品质色（含最高的 immortal），页面不重复定义。

## 字体

- 全局 `Inter` 字体栈；标题 30~40px，主数值大字，卡片标题加粗，说明 13px，辅助标签更小。
- 文本必须允许换行 / 截断，不允许撑破卡片宽度。

## 已知 UI 技术债（待重构）

1. **单文件 7900 行全局 CSS**，590 个全局类、无 BEM / 无 CSS Modules → 碰撞与死类风险。
2. **`.phone-frame` 双定义** + 桌面布局沿用"phone"命名。
3. **仅 4 个 `@media`**，响应式覆盖偏薄。
4. 建议随前端拆分（见 `docs/architecture/frontend-split-plan.md`）按屏幕边界切成 `screens/X.module.css`。

## 验收标准（每次 UI 改动）

1. `npm run typecheck`（`tsc -b`）通过、`vite build` 成功。
2. 桌面 1366×768 与 1920×1080：无横向裁切、无按钮遮挡、主内容区与侧栏可滚。
3. 移动 ≤900px：回退布局可用，无裁切。
4. 核心路径可点完成：登录 → 主页入口 → 返回 → 主操作。
5. 内容为空 / 内容很多 / 玩家战力不足三类状态不崩溃且有可读反馈。
