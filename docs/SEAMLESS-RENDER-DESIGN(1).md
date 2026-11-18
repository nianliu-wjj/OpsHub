# LiveMD 无感渲染系统设计文档

> **定位**：基于 `docs/REA-MARKDOWN-RENDER-TIMING.md` 的逆向结论（Typora / nomo / MDmeow 三方证据）为 LiveMD 设计「用户更新文档后无感渲染」的完整系统方案。自包含、可直接交由其他系统/开发者实施。
> **设计基线**：问题十二（光标区域级标记显示）落地后的 main 工作区（含问题六/八/十/十二全部已固化修复）。
> **整理日期**：2026-11-17

---

## 1. 目标与非目标

### 1.1 用户体验目标——「四零承诺」

| # | 承诺 | 反例（现状风险点） |
|---|---|---|
| Z1 | **打字零渲染争帧** | Mermaid/KaTeX 大图渲染与键入同帧执行 → 输入卡顿 |
| Z2 | **结构变化零空窗** | 手术替换瞬间的白块/布局跳变 |
| Z3 | **光标零漂移** | 粘贴/拖拽/块手术后焦点落到意外位置 |
| Z4 | **程序更新零假编辑** | 自动保存回写、外部 watcher 回写被误判为用户编辑 → 脏标记误亮、保存回环、内容"刷新一下" |

### 1.2 非目标与硬约束

- **不引入**新编辑器框架、不重建视图树（REA 公理 A：PM ViewDesc 增量能力已免费获得，LiveMD 已同构达标）；
- **不违**「WKWebView 幽灵态纪律」（问题六/八实证）：任何设计不得触碰 text wrapper 重建路径、不得恢复 `content-visibility` 对末级块的覆盖、不得让光标移动路径产生 inline decoration 变化；
- 不追求打字延迟改造（PM 原生即达标），聚焦**渲染周边治理**（REA 结论：三个软件的差距不在增量渲染本身，而在周边治理）；
- worker 化已达标项（`utils/lintWorkerClient.ts`：单 worker 串行 + 超时熔断 + 切文档抢占 + 主线程降级）**不重复建设**，仅复用其模式。

---

## 2. 现状盘点矩阵（证据编号沿用 REA 报告）

**主矩阵**：体验承诺（Z，用户痛点视角）× REA 公理（A-G，机制视角）× 分项现状 × 证据 × 已固化修复 × 差距 × 设计项 × 验收断言。半达标公理（E/F）展开为分项子行。

| 承诺 | 公理 | 分项现状 | 证据（文件 / 位置） | 已固化修复 | 差距 | 设计项 | 验收断言（render-smoke） |
|---|---|---|---|---|---|---|---|
| — | A 视图树不重建 | ✓ `restoreDoc` 单事务合并（内容+光标+书签）；外部回写分支同走单事务 | editor.ts `restoreDoc` | 问题九收尾（三连事务 → 单事务） | 无 | 写为回归基线：P2 强制走 restoreDoc，禁 setMarkdown 整挂重建 | 打开/回写流程中 `ed.view.dom` 根节点引用相等（identity 断言） |
| Z1/Z2 | B 打字零渲染管 | ✓ PM 原生增量；问题十二后行内标记显示走 head 下自管 style（零 PM mutation 通道） | caret-style.ts | 问题六/八（幽灵态纪律）、问题十二（自管 style 通道） | 无 | — | 复用问题十二冒烟用例 8：光标往返零 childList/非类 attr 突变 |
| Z2 | C 变更粒度=块 | ✓ PM ViewDesc 对位复用（node.eq 原地 patch） | prosemirror-view 内核 | — | 无 | — | 结构变化流程的 mutation 序列中，非目标块零变化 |
| Z3 | D 光标=坐标（外部恢复面） | ✓ `restoreDoc` 携 `cursorOffset` 钳制恢复 | editor.ts `restoreDoc` 参数 | 问题九 | 无 | — | 回写后 `selection.head` 与恢复前相等 |
| Z3 | D 光标=坐标（手术命令面） | ✗ 自研手术命令（粘贴表格化 / 图片拖入 / 块菜单操作 / 表格行列命令等）无统一选区恢复规范，存在"焦点靠 DOM 惯性"的隐式依赖 | core/commands、keymap 各命令 | 问题五（列表 Enter 光标安全化，仅覆盖一处） | 缺统一三律协议 | **P3** 手术三律 + 命令清单化 | 每命令扩展选区回归用例（enter-marker-safety 套件），断言术后 selection 落点等于规范期望坐标 |
| Z1 | E-1 重计算 worker 化 | ✓ lint 全链 worker：单 worker 串行 + 超时熔断 + 切文档抢占 + 主线程降级 | utils/lintWorkerClient.ts 头注释 | — | 无 | 复用其模式为 P1 的取消/抢占设计参考 | — |
| Z1 | E-2 Mermaid 执行模型 | ◐ 全局串行队列 + SVG 结果缓存 + 模块懒加载 | code-block.ts L110-124（mermaidRenderQueue / mermaidSvgCache / loadMermaidModule） | 问题二（optimizeDeps 预构建，解决动态加载 404 家族） | 队列只管并发、不管时机 | **P1**：队列改经 renderScheduler 入队（background 档） | 打字期间 Mermaid 长任务（>50ms）计数不随键入增加 |
| Z1 | E-3 busy 抑制 | ✗ 打字期间昂贵渲染照常执行 | —— | — | 缺 Typora 式 busy 语义 | **P1**：`notifyTyping` 单行埋点 + idle 泄洪（300ms 静默窗） | 打字段 vs 静默段长任务计数对比为零差 |
| Z2 | E-4 渲染遮罩 | ✗ 手术/重渲空窗直接可视（白块闪动） | —— | — | 缺 `ty-before-first-render` 同构物 | **P1**：`livemd-render-pending` 类（visibility + min-height 锁高） | 粘贴 Mermaid 块流程 mutation 序列无「内容清空再填充」突变 |
| Z2 | E-5 范围化渲染 | ✗ 全文档扫渲入口无受影响块过滤 | code-block.ts 挂载逻辑 | — | 缺范围化 | **P1**：复用 `getAffectedBlockRanges`（±1 块）作入队范围 | 静止编辑单块时，其它块 nodeview update 回调计数不增 |
| — | F-1 程序更新守卫 | ◐ `isSourceViewToggling || isHydrating` 双守卫，仅覆盖 hydrate / 源码切换两源 | LivemdEditor.vue L815-830（change 消费段） | — | watcher 回写 / 检查修复写回无守卫 | **P2**：哨兵 `runProgrammatic` 包裹全写入路径 | watcher 回写全流程 change 事件计数为 0 |
| Z4 | F-2 通知单义性 | ✗ 无内容基线，防抖通知尾（outline 150ms / 保存定时）无法甄别真假编辑 | LivemdEditor.vue change / outline 链 | — | 缺 MDmeow 式双基线（doc.eq + 序列化串） | **P2**：`isRealEdit` 首行短路判定 | 程序更新后注入一次内容不变的噪声 change → 脏标记不亮、tab.content 不回写 |
| — | G 渲染结果可校验 | ✗ 未建设（自研解析器内闭环） | —— | — | 暂缓 | 接入不可信内容源时立项（预留 MDmeow maskFragments 模式） | — |

**承诺 → 公理覆盖映射**（自查，确保无孤立设计项）：Z1＝E-1/E-2/E-3；Z2＝B/C/E-4/E-5；Z3＝D（外部恢复面 + 手术命令面）；Z4＝F-2（F-1 为前置）。

---

## 3. 总体架构

### 3.1 四路径分层（目标态）

```
┌─ 输入路径（打字） ──────────────── PM doc transaction → ViewDesc 原位 patch
│                                    ⚠ 与以下三条路径物理隔离（Z1 守卫）
│
├─ 变更路径（回车/粘贴/块命令） ─── PM step → 记录受影响块集合（±边界）
│                                    → 触发"廉价"渲染立即完成
│                                    → "昂贵"渲染入 P1 调度队列（含遮罩）
│
├─ 外部路径（保存回写/watcher/修复）→ restoreDoc 单事务 + P2 哨兵抑制通知
│
└─ 通知路径（应用层） ───────────── change/outline/tab 回写仅接受「真实用户编辑」
```

### 3.2 新增/改造组件

| 组件 | 性质 | 所在层 |
|---|---|---|
| **渲染调度器 `core/scheduler/render-scheduler.ts`** | 新增（核心，P1） | core 引擎域 |
| **渲染遮罩样式类 `livemd-render-pending`** | 新增（CSS，P1） | core/styles |
| **程序更新哨兵 `renderer/hooks/useProgrammaticGuard.ts`** | 新增（P2） | 渲染层 |
| 自研命令的选区三律改造 | 改造既有命令（P3） | core/commands、keymap |
| render-smoke 验证基建 | 扩展现有 caret-smoke（P5） | 项目根调试页 |

---

## 4. 分模块设计

### 4.1 P1 昂贵渲染调度器（对应 REA 公理 E / Typora busy 机制）

**问题**：Mermaid、KaTeX 大公式、代码高亮重建属百毫秒级任务，当前与打字同帧执行；大文档下连续渲染会吃满输入帧。

**设计**：把现有 `code-block.ts` 内嵌的 mermaid 专用队列**泛化为全局渲染调度器**，三件套：

1. **busy 协议**：

```typescript
// core/scheduler/render-scheduler.ts（接口签名）
export interface RenderTask {
  /** 任务归属块 cid（用 PM pos 派生即可），用于范围化与取消 */
  blockKey: string;
  /** 优先级：interactive（光标所在块，用户在看）> background（视口外/非活跃块） */
  kind: "interactive" | "background";
  run: (signal: AbortSignal) => Promise<void>;
}

export const renderScheduler = {
  /** 输入开始（keymap 事务含 docChanged 时）置忙，静默窗口（idle 300ms）后泄洪 */
  notifyTyping(): void;
  /** 入队：interactive 任务仅延迟到当前帧后；background 任务在 busy 期间挂起 */
  enqueue(task: RenderTask): void;
  /** 文档切换/块删除时按 blockKey 取消孤儿任务（防旧渲染回写已删除节点） */
  cancelBlock(blockKey: string): void;
};
```

busy 判定不新增事件链：`editor.ts` 的 `dispatchTransaction` 末尾在 `tr.docChanged` 时调用 `renderScheduler.notifyTyping()`（单行埋点，无新听感路径）。

2. **范围化**：与问题十二的区域计算同源——只有**受影响块**（syntax-detector 的 `getAffectedBlockRanges` 已算出 ±1 块范围）入队重渲；Mermaid 全文档扫渲的入口改为按块登记。

3. **渲染遮罩类（防手术空窗，Typora `ty-before-first-render` 同构）**：

```css
/* core/styles/livemd.css —— 渲染进行中的块被"按住"不闪：内容不可见但几何恒定 */
.livemd-render-pending {
  /* 不用 display:none（会造成布局塌陷闪烁），用内容不可见 + 保持估高 */
  visibility: hidden;
  contain: paint;
  /* 由 nodeview 在入队时同步写下 min-height（取该块当前 getBoundingClientRect） */
}
```

入队时记录块高度写 `style.min-height`，渲染完成摘类——**先按住、算完放开**，用户看到的是"块原样直到新内容替换"，而非白块闪动。

4. **接点改造**（最小侵入两处）：
   - `code-block.ts` 的 mermaid 队列改经 `renderScheduler.enqueue`，`background` 档位；
   - `math-block.ts` / `decorations` 的 KaTeX 挂载走 `interactive` 档位。

**验收（P1）**：在 400 行 Mermaid 文档中连打 10 个字符，主线程长任务（>50ms）计数不随打字增加； 任何时刻渲染中的块 `getComputedStyle` 不出现白块空窗（render-smoke 断言）。

### 4.2 P2 外部更新哨兵（对应 REA 公理 F / MDmeow D-2 六式）

**问题**：`isHydrating` 双守卫只覆盖 hydrate 与源码切换；文件 watcher 回写、检查修复写回、主题应用等程序化写入若漏挂守卫 → change 消费端误判脏、tab.content 回写改变滚动/焦点（用户视角即"文档自己刷新了一下"）。此外 PM 无 debounced change，但**渲染层存在异步通知尾**（outline 150ms 防抖、保存 2s 防抖）需要内容基线兜底。

**设计**（哨兵协议，MDMeow `programmaticDocumentUpdate` 泛化）：

```typescript
// renderer/hooks/useProgrammaticGuard.ts（形状）
export function useProgrammaticGuard() {
  let suppressing = 0;
  let baselineDoc: unknown = null;        // 上次"真实编辑"后的 doc 引用
  let baselineMarkdown = "";              // 同上序列化值（doc.eq 双保险）

  /** 包裹一次程序化写入：期间 change/outline 全部忽略，结束后重建基线 */
  async function runProgrammatic<T>(fn: () => Promise<T> | T): Promise<T>;
  /** change 消费端调用：基线命中（eq + 字符串相等）返回 false，视为假编辑 */
  function isRealEdit(doc: unknown, markdown: string): boolean;
}
```

1. **接管点**：LivemdEditor.vue 的 `editor.on("change")` 首行与 outline/批注联动入口，替换裸的 `isHydrating` 判定为 `isRealEdit(doc, markdown)`（`doc.eq(baselineDoc) && markdown === baselineMarkdown` 命中 → 静默短路，任何人忘挂守卫都不会误报）;
2. **写入路径清单化**（挂在 `runProgrammatic` 内逐一核销）：hydrate、源码切换、**外部 watcher 回写重载分支（file:Change → restoreDoc）**、检查修复写回、图片上传后的插入回执；
3. **守卫收编**：`isSourceViewToggling`/`isHydrating` 保留原变量但注释上指向哨兵，不再各自为政。

**验收（P2）**：自动保存触发一次写盘 → 脏标记不闪亮、视口不滚动、tab.content 与编辑区内容引用稳定；watcher 模拟外部改文件弹回 → 弹恢复流程期间 `editor.on("change")` 零次回调（render-smoke 断言计数）。

### 4.3 P3 手术三律（对应 REA 公理 D / Typora selection.restoreSelection）

**凡自研命令改块结构处**，强制三步协议（Typora 的 `restoreSelection(node, {start,end})` 的 PM 坐标译）：

```typescript
// ① pre：保存坐标而非 DOM 句柄
const anchor = saveCursorAnchor(state);   // { blockPos: number; offset: number } —— $cursor.parentOffset + $cursor.before(depth)
try {
  // ② 手术：一次性 tr 完成变更（严禁拆多个 dispatch 中间可见）
  dispatch(tr);
  // ③ post：按新坐标系映射回位置并显式落回
  const mapped = mapAnchorThrough(tr, anchor);   // 用 tr.mapping.map 前向映射
  if (mapped) dispatch(setTrSelection(mapped));   // 永不依赖"dom 焦点恰好还在"
} catch { restoreAnchor(anchor); }
```

**命令清单**（将 patch 检核）：粘贴图片路径替换、拖拽插入、块菜单操作（上移/下移/代码块折叠回插）、列表升降级边界命令、表格行列命令、`splitListItem` 快捷键族之外的表格 Enter。每处改造以 `enter-marker-safety.test.mjs` 同套件扩展**选区回归用例**（RED→GREEN 一次一命令）。

### 4.4 P4 大文档视口治理（复核性课题，无新建设）

守住问题八结论：`content-visibility: auto` 只保留**顶层块**选择器，永不为 `li`/行内元素重新启用；同时 P1 的遮罩类使用 `contain: paint`（不参与 content-visibility 的 relevant 判定路径）；render-smoke 挂一条**结构不变量断言**：`getComputedStyle(任一 li).contentVisibility === "visible"`。

### 4.5 P5 验证与指标体系（render-smoke 基建）

扩展现有 `caret-smoke.mjs` / `debug-dom` 页（不被回删）新增四类断言（构建在既有 CDP 通道上）：

1. **打字帧狩猎**：长任务 PerformanceObserver（longtask）计数注入对比（打字 vs 静默）；
2. **手术空窗监视**：页面级 `MutationObserver` 在测试creating的 paste/命令流程中记录"块内容从存在到存在"的空帧（断言 = 父块变化序列中无"内容清空再填充"突变）；
3. **假编辑计数**：自动保存/回写流程后读取 change 事件计数；
4. **幽灵态红线**：复用问题十二用例 8（零 childList/非类 attr 突变）持续在列。

**性能预算（长期指标）**：打字路径 javaScript 帧预算 ≤ 4ms（不含 PM 分摊）；Mermaid 队列 drain ≤ 1 任务/帧；上述断言全部进 render-smoke 基线，任何回归即卡点。

---

## 5. 三场景时序（目标态）

```
【打字】key → transaction → ViewDesc 原位 patch →（同事务）renderScheduler.notifyTyping
        → 队列挂起 background 任务，idle 泄洪 → 用户全程只见文字出现
【粘贴含 Mermaid 块】paste 规则解析 → transaction 替换块 → 廉价渲染即时
        → Mermaid 挂 livemd-render-pending（min-height 锁高）入 interactive 队列
        → 当前帧后渲染 → 摘类 → 视觉效果 = 内容一次到位、无白块
【外部回写】file:Change → restoreDoc 单事务（内容+光标+书签）→ runProgrammatic 包裹
        → change/outline 在哨兵水位下 → 基线重建 → 脏标记不亮、视口零滚动
```

---

## 6. 实施阶段与文件清单

| 阶段 | 内容 | 新改文件 | 规模 | 回滚面 |
|---|---|---|---|---|
| S1 | P2 程序更新哨兵（用户痛点最直接：「文档自己刷新」） | `src/renderer/hooks/useProgrammaticGuard.ts`（新）、LivemdEditor.vue（接点 ~20 行）、useAutoSave 关联处 | 小 | 单 commit，守卫默认闭=旧行为 |
| S2 | P1 渲染调度器三件套 | `src/core/scheduler/render-scheduler.ts`（新 ~150 行）、livemd.css（遮罩类 10 行）、code-block.ts / math-block.ts 改造两处、editor.ts 单行埋点 | 中 | 队列旁路开关 `LIVEMD_SCHEDULER=off` 降级回直渲 |
| S3 | P3 手术三律逐命令 | core/commands（多文件小改）+ test 扩展 | 渐进 | 按命令独立 commit |
| S4 | P4/P5 监控固化 | render-smoke.mjs、debug-dom 页扩展 | 小 | 纯基建 |

依赖关系：S1、S2 可并行；S3 依赖 P3 规范评审后小步快跑；P5 随各阶段增量落地。

### 6.1 落地记录（2026-11-17：S1 + S2 已实现并通过验证）

| 阶段 | 状态 | 实际落点（与 §6 计划的差异注明） |
|---|---|---|
| **S1（P2 哨兵）** | ✅ 完成 | `src/hooks/useProgrammaticGuard.ts`（runProgrammatic 深度计数 + doc.eq/序列化双基线 isRealEdit，快通道：字符串不等不进 O(n) 比较）。接点：`LivemdEditor.vue` change 消费端首行短路；hydrate 注入 / 外部回写重载 / 源码切换 / defineExpose 写入四条路径全部 runProgrammatic 包裹；**原 isHydrating / isSourceViewToggling 双孤旗移除**（哨兵语义收编）。测试：`test/programmatic-guard.test.mjs`（8 用例） |
| **S2（P1 调度器）** | ✅ 完成 | `src/core/scheduler/render-scheduler.ts`（落点即计划位置）：notifyTyping 埋点在 editor.ts dispatchTransaction 用户事务单行；Mermaid 走 background 档（原模块级串行队列收编，串行语义由调度器保证）+ 同块去重 + destroy 时 cancelBlock；KaTeX 块走 interactive 档（缓存命中保持同步直渲，快通道零延迟；未命中顺延一帧且**旧内容保留至新结果就绪，无空窗**）；遮罩 `.livemd-render-pending`（visibility + contain:paint，min-height 内联锁高）用于 Mermaid「有旧图先遮后换」场景；泄洪上限（background 等待 ≥1.5s 强制放行）防「该渲不渲」；`LIVEMD_SCHEDULER=off` 全局旁路直渲为回滚面（与计划一致）。测试：`test/render-scheduler.test.mjs`（7 用例，含泄洪上限回归——首版实现曾暴露 wakeup 0ms 热循环缺陷，已修复） |
| S3（P3 手术三律） | ⏳ 未开始 | 待规范评审后按命令小步快跑（enter-marker-safety 套件扩展选区回归用例） |
| S4（P4/P5） | ◐ 部分 | P4 结构不变量断言（li 的 content-visibility）与 P5 render-smoke CDP 基建未建设；本轮以 Node 单测覆盖调度器/哨兵的可测子集，全量 321 测试通过 |

验证基线：`pnpm test` 321 全绿（+15 新增）；`pnpm lint` 通过；`pnpm build:web` 通过。既有基线问题（非本轮引入）：tsc 对 `caret-style.ts`/`catalog.ts` 的历史类型错误、tsconfig `baseUrl` 弃用告警。

已知取舍：
- KaTeX 遮罩：块级公式未命中缓存时**不挂遮罩类**，而是「旧内容保留到新结果写回」——比遮罩更快且天然无空窗；遮罩类专用于 Mermaid 换图空窗（两处需求不同，见 code-block.ts 注释）。
- interactive 档带 8ms 同帧批量预算：大文档打开时的公式爆发在数帧内出清，避免逐帧弹出数十帧。

---

## 7. 风险与对照

| 风险 | 缓解 |
|---|---|
| 调度器挂起导致"该渲不渲"的视觉滞后 | interactive 档不阻塞（只顺延到帧后）；background 空转上限 300ms 泄洪；队列空转超时兜底日志 |
| 遮罩类遇见 WKWebView 新重绘怪癖（问题七/八家族） | 遮罩仅 `visibility + contain: paint`，不触碰 content-visibility 体系；万一如现即摘除（回滚面单点） |
| 哨兵基线缓存（doc 引用 + 序列化串）内存成本 | 只存引用与上次序列化值各一份；文档切换时随 tab 重建 |
| 手术三律 map 复杂命令（表格操作）点位漂移 | 每命令 RED 试例先行（映射错直接红），修复不含糊 |
| P1 队列与问题二 mermaid optimizeDeps 的历史交叠 | 队列不动模块加载（懒加载与调试路径保持原样），只调度执行 |

**交叉引用**：`docs/BUGFIX-CARET-REGION.md`（问题十二·自管 style 通道，即本设计 B 公理达标证据）、`docs/REA-MARKDOWN-RENDER-TIMING.md`（公理来源与 Typora/MDmeow 证据）、仓库内已固化的历史修复（问题六/八幽灵态纪律、问题九 restoreDoc 单事务、问题十一布局重构）。

---

## 8. 总则

本系统不新增渲染能力——PM 已给我们增量渲染（REA 公理 A-D 达标），本设计补齐的是**渲染周边治理**（E/F）与**验证自证力**（P5）。判断实现是否成功的唯一标准是用户视角：**编辑的每一秒里，屏幕上发生的一切都源于他自己的动作**。
