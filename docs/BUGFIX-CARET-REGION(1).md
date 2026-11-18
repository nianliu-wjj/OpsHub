# LiveMD 光标区域级语法标记显示优化方案（显示机制第三阶段）

> **基线版本**：`05c6d0b`（refactor(structure): src 目录结构参考 soybean-admin 布局重构）
> **整理日期**：2026-11-17
> **用途**：本文档自包含，可直接交由其他系统/开发者在本项目（或同构 ProseMirror WYSIWYG 编辑器）上实施同等的优化。含完整的机制调研证据、关键代码全量节选、验证数据与踩坑实录。

---

## 1. 问题与需求

上一阶段（「焦点块级显示」）将光标所在 textblock 的所有语法标记（`**`、`==`、`` ` `` 等）**整体亮起**，粒度过粗。本阶段两项需求：

1. **引用块 `> ` 前缀自始至终不显示**（即时渲染模式；源码模式不变）——此前光标进入引用块即亮起（图一现场）。
2. **行内语义标记只在光标紧邻其区域时显示**：例「从**系统模型与故障模型**出发」，光标在粗体以外位置时 `**` 不显示（图二现场）；光标处于语义内容内部或紧贴标记前后时显示。

**维持现状项**：标题 `###` 前缀继续「光标在标题块内显示」；行内数学公式沿既有光标驱动渲染机制；源码模式机制不动。

---

## 2. 前置机制背景

理解本方案的前提（LiveMD 核心机制摘要）：

1. **语法标记是真实文本**：`**`、`` ` ``、`==` 等是 ProseMirror 文档中的真实 text 节点，带 `syntax_marker` mark（`attrs.syntaxType` 标明类型）；语义样式（粗体/斜体/…）由语义 mark（strong/emphasis/…）表达。序列化器直读文本，round-trip 天然保真。
2. **schema 恒定 meta wrapper**（上一阶段已落地）：`syntax_marker` mark 的 toDOM 输出嵌套恒定 wrapper，标记文本永远住在其中，由 CSS `display:none` 静态隐藏，不参与布局与文本流：

   ```typescript
   // src/core/schema/index.ts
   toDOM(mark): DOMOutputSpec {
     return [
       "span",
       { class: "livemd-syntax", "data-syntax-type": mark.attrs.syntaxType },
       ["span", { class: "livemd-syntax-meta" }, 0],   // 标记文本永远住在这里
     ];
   }
   ```

3. **焦点块机制**（本阶段要收紧的对象）：装饰插件给光标所在 textblock 挂 `livemd-focus-block` 类（块级 `Decoration.node`），CSS 祖先选择器使全块 meta 显示：

   ```css
   /* 修改前：焦点块内全部标记显示 —— 本问题的根源 */
   .livemd-focus-block .livemd-syntax-meta { display: inline; ... }
   ```

4. **WKWebView 幽灵态纪律**（前两阶段实证的铁律，本方案不得突破）：
   - 光标移动路径上**禁止任何 inline decoration 增删/attrs 变化**（触发 text wrapper 重建 → WKWebView text run 幽灵态）；
   - **禁止外部直改 PM 渲染元素（strong/span 等）的 class/属性**——见下节源码证据。

---

## 3. 硬约束调研结论（决定方案形态的证据）

**目标行为参照 Typora**：光标所在的语义区域容器（`md-inline` span）才显示标记——但 Typora 是 jQuery 自管 DOM，直接 `addClass` 即得。ProseMirror 体系内必须先回答「为什么不能直改 DOM」。

**prosemirror-view@1.42.6 源码证据**（`node_modules/prosemirror-view/dist/index.js`）：

`registerMutation`（约 L4910）对 DOM MutationObserver 的 attr 变更分流：

```js
if (mut.type == "attributes" &&
    (desc == this.view.docView || mut.attributeName == "contenteditable" ||
        // Firefox sometimes fires spurious events for null/empty styles
        (mut.attributeName == "style" && !mut.oldValue && !mut.target.getAttribute("style"))))
    return null;
if (!desc || desc.ignoreMutation(mut))
    return null;
```

免检名单只有 `contenteditable`/特定 `style`；`class` 不在其中，命运取决于 `desc.ignoreMutation`。ViewDesc 基类（L1117）：

```js
ignoreMutation(mutation) {
    return !this.contentDOM && mutation.type != "selection";
}
```

而 **MarkViewDesc（strong/em 等语义容器）没有覆写 ignoreMutation，且全部持有 contentDOM**（mark 有 content hole）→ `!this.contentDOM` 为假 → **不忽略** → attr 变更进入 `handleDOMChange` 触发该区段 DOM 重读——正是幽灵态路径。

**结论**：PM 体系内「光标状态 → DOM 显隐」的**唯一零 mutation 写入通道**是：

- (a) **块级 `Decoration.node` 的类名/attrs patch**（焦点块先例，安全，本方案继续承担 heading 显示）；
- (b) **编辑器 DOM 之外的 `<style>` 元素**（`document.head` 下，PM 的 MutationObserver 观察不到编辑器子树之外；style 文本替换仅引发浏览器样式重算，不改 DOM 结构、不触发 wrapper 重建）。

**方案主体走通道 (b)**：每次编辑器 update 后计算「光标紧邻区域集合」→ 生成结构选择器 → 整体替换 style 文本。

---

## 4. 方案总览

```
光标状态写入通道分工
────────────────────────────────────────────
焦点块 node decoration（保留）→ 仅 heading：.livemd-focus-block .livemd-syntax[data-syntax-type="heading"] .livemd-syntax-meta { display:inline }
自管 <style> 元素（新增）     → 行内语义标记 / escape / html_entity：按光标紧邻区域生成精确选择器
CSS 静态隐藏（不变）          → 其余一切：.livemd-syntax-meta { display:none }
```

- blockquote `> ` 前缀自此**无任何即时渲染显示规则**（需求 1 达成）；
- 标记文本自身的 DOM 结构（schema toDOM 恒定 meta wrapper）全程不被触碰（幽灵活纪律）。

新增/修改的文件：

| 文件 | 动作 |
|---|---|
| `src/core/decorations/caret-style.ts` | **新增**（343 行）：`computeCaretRegions` 纯函数 + 选择器生成 + `attachCaretStyle` 生命周期 |
| `src/core/decorations/index.ts` | 装饰插件 `view()` 钩子接线 `attachCaretStyle` |
| `src/core/styles/livemd.css` | 两处改动（见 5.6） |
| `test/caret-region.test.mjs` | **新增**：11 个 Node 级回归用例 |
| `debug-dom.html` + `src/debug-dom/{main.ts,i18n-shim.ts}` | **新增**：headless Chrome 冒烟调试页（见 8.2） |
| `caret-smoke.mjs` | 冒烟驱动脚本（CDP） |

---

## 5. 关键实现代码

### 5.1 新模块 `src/core/decorations/caret-style.ts`（全量）

```typescript
/**
 * 光标紧邻区域级语法标记显示（Typora 同构 · 显示机制第三阶段）
 *
 * 在 ProseMirror 体系内落地的硬约束（见文档第 3 节源码证据）：
 * - 光标移动路径上禁止 inline decoration 增删/attrs 变化（text wrapper 重建 →
 *   WKWebView 幽灵态）；
 * - 禁止外部直改 PM 渲染元素（mark 元素全有 contentDOM，MarkViewDesc 不忽略属性
 *   mutation → 触发该区段 DOM 重读）。
 *
 * 因此光标状态的写入通道是编辑器 DOM 之外的 <style> 元素（document.head 下，
 * PM MutationObserver 观察不到，样式文本替换仅引发样式重算）：
 * 每次编辑器 update 后计算「光标紧邻区域集合」→ 生成结构选择器
 * （tag:nth-of-type 链，锚定 view.dom 的 scope class）→ 整体替换 style 文本。
 * 标记文本自身的 DOM 结构（schema toDOM 恒定 meta wrapper）完全不被触碰。
 *
 * 显示类型分派：
 * - heading：保持焦点块级显示（焦点块 CSS 限定 heading 类型，见 livemd.css）；
 * - blockquote（引用 "> "）/ list 前缀：永不入选、无任何即时渲染显示规则；
 * - math_inline：沿用既有光标驱动渲染机制，不进本通道；
 * - escape / html_entity：2-N 字符微区域（无语义外壳，锚定标记 span 自身）；
 * - 其余语义 mark（strong/emphasis/code_inline/…）：区域 = mark 连续覆盖范围
 *   （含标记文本），光标邻接（nodeBefore/nodeAfter 携带该 mark）即命中。
 */

import { EditorView } from "prosemirror-view";
// ⚠️ 注意：Mark/Node 仅作类型使用必须用 type 导入 —— 值导入会让 prosemirror-model 的
// Node 类在运行时遮蔽 DOM 全局 Node（Node.ELEMENT_NODE 变 undefined），导致下方
// nodeType 守卫恒走 no-op 分支（踩坑实录见文档坑 1）
import type { Mark, Node } from "prosemirror-model";
import type { SyntaxMarkerRegion } from "./index";

/** 参与区域级显示的语义 mark 类型 */
const SEMANTIC_MARK_TYPES = new Set([
  "strong",
  "emphasis",
  "code_inline",
  "strikethrough",
  "highlight",
  "link",
  "sub",
  "sup",
  "html_inline",
]);

/** 光标紧邻区域 */
export interface CaretRegion {
  from: number;
  to: number;
  kind: "semantic" | "escape" | "html_entity";
}

/** 结构链最大深度（防异常结构生成超长选择器） */
const SHELL_MAX_DEPTH = 8;

/**
 * 求一个 mark 实例在当前 textblock 内的连续覆盖区段（含标记文本自身）。
 * 返回覆盖光标邻域（±1 容错计数偏差）的那个区段，无则 null。
 */
function markExtentInBlock(
  parent: Node,
  parentStart: number,
  mark: Mark,
  cursorOffset: number
): { from: number; to: number } | null {
  const runs: Array<{ s: number; e: number }> = [];
  let runStart = -1;
  let runEnd = -1;
  let offset = 0;
  parent.forEach((child) => {
    const size = child.nodeSize;
    if (child.marks.some((m) => m.eq(mark))) {
      if (runStart < 0) runStart = offset;
      runEnd = offset + size;
    } else {
      if (runStart >= 0) runs.push({ s: runStart, e: runEnd });
      runStart = -1;
    }
    offset += size;
  });
  if (runStart >= 0) runs.push({ s: runStart, e: runEnd });

  for (const run of runs) {
    if (cursorOffset >= run.s - 1 && cursorOffset <= run.e + 1) {
      return { from: parentStart + run.s, to: parentStart + run.e };
    }
  }
  return null;
}

/**
 * 计算光标紧邻区域集合（纯文档模型计算，可在 Node 环境单测）。
 *
 * 语义区域：光标 nodeBefore / nodeAfter 任一携带语义 mark 即命中——这天然表达
 * 「光标在内容内部 / 紧贴标记前后」而「相隔 ≥1 字符」不亮；
 * 微区域（escape / html_entity）：光标落在 [from-1, to+1] 即命中。
 *
 * heading / blockquote / math_inline 等类型永不入选：
 * - heading 由焦点块 CSS 保持块级显示；
 * - blockquote "> " 前缀自始至终不显示（用户决策）；
 * - math_inline 沿用既有光标驱动渲染机制。
 */
export function computeCaretRegions(
  doc: Node,
  cursorPos: number,
  syntaxRegions?: SyntaxMarkerRegion[] | null
): CaretRegion[] {
  const result: CaretRegion[] = [];
  const seen = new Set<string>();

  const $cursor = doc.resolve(Math.min(Math.max(cursorPos, 0), doc.content.size));
  if (!$cursor.parent.isTextblock) return result;

  // A. 语义 mark 邻接收集（同一类型只取一次）
  const markByType = new Map<string, Mark>();
  for (const adjacent of [$cursor.nodeBefore, $cursor.nodeAfter]) {
    if (!adjacent) continue;
    for (const m of adjacent.marks) {
      if (SEMANTIC_MARK_TYPES.has(m.type.name)) markByType.set(m.type.name, m);
    }
  }
  const parentStart = $cursor.start($cursor.depth);
  for (const [typeName, mark] of markByType) {
    const extent = markExtentInBlock($cursor.parent, parentStart, mark, $cursor.parentOffset);
    if (!extent) continue;
    const key = `${typeName}:${extent.from}:${extent.to}`;
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({ ...extent, kind: "semantic" });
  }

  // B. 微区域（escape / html_entity）
  for (const region of syntaxRegions ?? []) {
    if (region.syntaxType !== "escape" && region.syntaxType !== "html_entity") continue;
    if (cursorPos < region.from - 1 || cursorPos > region.to + 1) continue;
    const key = `${region.syntaxType}:${region.from}:${region.to}`;
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({ from: region.from, to: region.to, kind: region.syntaxType });
  }

  return result;
}
```

### 5.2 DOM 定位与选择器生成（同文件）

```typescript
/** domAtPos 结果规范化为 Element（文本节点取其父元素） */
function elementFromDomAtPos(view: EditorView, pos: number, bias: -1 | 1): Element | null {
  const { node } = view.domAtPos(Math.max(0, Math.min(pos, view.state.doc.content.size)), bias);
  if (!node) return null;
  if (node.nodeType === Node.TEXT_NODE) return node.parentElement;
  return node instanceof Element ? node : node.parentElement;
}

/**
 * 标记区域 → 标记 span 自身（span.livemd-syntax，其唯一直子即 meta）。
 * 取 from+1 处向左取 DOM：落在标记文本内部，避开 PM domAtPos 在 text 边界
 * 返回块级元素的坑（踩坑实录见文档坑 3）。
 */
function resolveSyntaxSpan(view: EditorView, from: number, to: number): HTMLElement | null {
  const inside = Math.min(from + 1, to);
  let el = elementFromDomAtPos(view, inside, -1);
  let hops = 0;
  while (el && el !== view.dom && hops++ < SHELL_MAX_DEPTH) {
    if (el.classList.contains("livemd-syntax")) return el;
    el = el.parentElement;
  }
  return null;
}

/**
 * 结构选择器生成：目标元素到编辑器根每层 tag + 同 tag 兄弟序（nth-of-type 按
 * 元素计，text 节点不参与），根层锚定 scope class。深度超限放弃该区域（容错
 * 降级——宁可少亮一处，不产生脆弱选择器）。
 */
function buildSelector(view: EditorView, target: HTMLElement, scopeClass: string): string | null {
  const parts: string[] = [];
  let el: HTMLElement | null = target;
  let depth = 0;
  while (el && el !== view.dom) {
    if (++depth > SHELL_MAX_DEPTH) return null;
    const tag = el.tagName.toLowerCase();
    let nth = 1;
    let sib: Element | null = el.previousElementSibling;
    while (sib) {
      if (sib.tagName.toLowerCase() === tag) nth++;
      sib = sib.previousElementSibling;
    }
    parts.unshift(`${tag}:nth-of-type(${nth})`);
    el = el.parentElement;
  }
  if (el !== view.dom || parts.length === 0) return null;
  return `.${scopeClass} > ${parts.join(" > ")}`;
}

/** 区域规则的 meta 匹配尾巴：标记 span 的唯一直子（精确到单个 meta） */
const META_TAIL = "> span.livemd-syntax-meta";

/** meta 显示声明体：与 livemd.css 焦点块 heading 规则保持一致 */
const META_DISPLAY_DECL = `  display: inline;
  color: var(--text-color-3, #999);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 0.9em;
  user-select: auto;
  pointer-events: auto;`;
```

### 5.3 规则生成（同文件；**扁平三段式事实下的定稿策略**）

```typescript
/**
 * 区域集合 → 样式表文本（同选择器去重；entity 区域附解码 widget 隐藏规则）。
 *
 * 定位策略（冒烟三期定稿）：标记 DOM 是「扁平三段式」——开标记 span、内容外壳、
 * 闭标记 span 是块的直接子元素（不存在包裹三段的外壳祖先），因此不做外壳解析，
 * 而是语义区域在文档层展开为区间内**每个带 syntax_marker 的 text**（块内平铺
 * 遍历），逐个定位其标记 span 生成唯一路径选择器 —— 嵌套语义（strong 内 em）
 * 与转义标记的 span 各自精确命中，同块相邻的其他语义区域零泄漏。
 */
function buildCaretStylesheet(
  view: EditorView,
  regions: CaretRegion[],
  scopeClass: string
): string {
  const metaSelectors = new Set<string>();
  const entitySelectors = new Set<string>();

  const emitMarker = (from: number, to: number, isEntity: boolean) => {
    const span = resolveSyntaxSpan(view, from, to);
    if (!span) return;
    const selector = buildSelector(view, span, scopeClass);
    if (!selector) return;
    metaSelectors.add(`${selector} ${META_TAIL}`);
    if (isEntity) {
      // entity 隐藏解码 widget（原文 meta 显示时互斥）。
      // widget 挂于区域尾部 side:-1，DOM 上紧邻原文 span 之后（兄弟 + 选择器）。
      entitySelectors.add(`${selector} + .livemd-html-entity-rendered`);
    }
  };

  for (const region of regions) {
    if (region.kind === "semantic") {
      const $from = view.state.doc.resolve(region.from);
      const parent = $from.parent;
      const parentStart = $from.start($from.depth);
      let offset = parentStart;
      parent.forEach((child) => {
        if (
          child.isText &&
          offset >= region.from &&
          offset + child.nodeSize <= region.to &&
          child.marks.some((m) => m.type.name === "syntax_marker")
        ) {
          emitMarker(offset, offset + child.nodeSize, false);
        }
        offset += child.nodeSize;
      });
    } else {
      emitMarker(region.from, region.to, region.kind === "html_entity");
    }
  }

  if (metaSelectors.size === 0 && entitySelectors.size === 0) return "";

  // 注释保持 ASCII：vite-auto-i18n 会把中文字面量替换为 $deepScan(key) 运行时调用，
  // 裸 key 会污染 style 文本首条规则的 prelude（踩坑实录见文档坑 2）
  const parts: string[] = ["/* livemd caret-region (auto-generated) */"];
  if (metaSelectors.size > 0) {
    parts.push(`${Array.from(metaSelectors).join(",\n")} {\n${META_DISPLAY_DECL}\n}`);
  }
  if (entitySelectors.size > 0) {
    parts.push(`${Array.from(entitySelectors).join(",\n")} {\n  display: none;\n}`);
  }
  return parts.join("\n\n") + "\n";
}
```

### 5.4 生命周期挂接 `attachCaretStyle`（同文件）

```typescript
/** 自管 style 元素控制器 */
export interface CaretStyleController {
  /** 编辑器每次 update 后调用（源码模式自动退场；光标无邻接区域即清空） */
  refresh: () => void;
  /** 编辑器销毁时移除 style 元素与 scope class */
  dispose: () => void;
}

let scopeCounter = 0;

/**
 * 挂接光标区域显示：编辑器根挂唯一 scope class（多编辑器实例隔离），
 * document.head 下创建自管 style 元素。getter 注入装饰插件状态（避免循环 import）。
 */
export function attachCaretStyle(
  view: EditorView,
  getPluginState: () =>
    | { sourceView: boolean; cachedSyntaxRegions?: SyntaxMarkerRegion[] }
    | undefined
): CaretStyleController {
  if (!view.dom || view.dom.nodeType !== Node.ELEMENT_NODE) {
    return { refresh: () => {}, dispose: () => {} };
  }
  const scopeClass = `livemd-caret-scope-${(scopeCounter += 1)}`;
  view.dom.classList.add(scopeClass);
  const style = document.createElement("style");
  style.setAttribute("data-livemd-caret", scopeClass);
  document.head.appendChild(style);
  let lastCss: string | null = null;

  const refresh = () => {
    try {
      const pluginState = getPluginState();
      let regions: CaretRegion[] = [];
      const active = pluginState?.sourceView !== true && view.state.selection.empty;
      if (active) {
        regions = computeCaretRegions(
          view.state.doc,
          view.state.selection.head,
          pluginState?.cachedSyntaxRegions
        );
      }
      const css = active ? buildCaretStylesheet(view, regions, scopeClass) : "";
      if (import.meta.env.DEV) {
        // 调试页可见的观测口：区域集合与生成结果（仅开发构建）
        (view.dom as unknown as Record<string, unknown>).__caretDebug = {
          active,
          regions,
          css,
        };
      }
      if (css !== lastCss) {
        style.textContent = css;
        lastCss = css;
      }
    } catch (error) {
      // 区域显示失败不允许影响编辑器 update 主流程
      console.error("[livemd-caret-style]", error);
    }
  };

  const dispose = () => {
    style.remove();
    view.dom.classList?.remove(scopeClass);
  };

  return { refresh, dispose };
}
```

### 5.5 装饰插件接线 `src/core/decorations/index.ts`

模块顶部：

```typescript
import { attachCaretStyle } from "./caret-style";

export { computeCaretRegions, type CaretRegion } from "./caret-style";
```

`createDecorationPlugin` 的 `view()` 钩子（与已有 `source-view` 根类同步同处）：

```typescript
view(editorView: EditorView) {
  const syncRootClass = (view: EditorView) => {
    view.dom.classList.toggle(
      "source-view",
      decorationPluginKey.getState(view.state)?.sourceView === true
    );
  };
  syncRootClass(editorView);

  // 光标紧邻区域级标记显示（进程外 style 元素通道，见 caret-style.ts 头注释）
  const caretStyle = attachCaretStyle(editorView, () =>
    decorationPluginKey.getState(editorView.state)
  );

  return {
    update(view: EditorView) {
      syncRootClass(view);
      caretStyle.refresh();
    },
    destroy() {
      caretStyle.dispose();
      editorView?.dom.classList?.remove("source-view");
    },
  };
},
```

### 5.6 CSS `src/core/styles/livemd.css`（两处改动）

**改动 1**：焦点块显示规则从通配收紧为 heading 专属（这正是需求 1 的主要载体——blockquote 类型自此无任何显示规则）：

```diff
-/* 光标所在 textblock（焦点块 node 装饰）- 标记整体显示 */
-.livemd-focus-block .livemd-syntax-meta {
+/* 光标所在 textblock（焦点块 node 装饰）- 仅标题前缀保持块级显示。
+   行内语义标记（粗体/行内代码/高亮/链接等）不再随焦点块整体显示：改为光标
+   紧邻区域级显示（decoration 插件自管 style 元素命中区域外壳选择器，见
+   core/decorations/caret-style.ts）。引用块 "> " 前缀自此无任何即时渲染
+   显示规则 —— 自始至终不显示（源码模式规则不受影响）。 */
+.livemd-focus-block .livemd-syntax[data-syntax-type="heading"] .livemd-syntax-meta {
   display: inline;
   color: var(--text-color-3, #999);
   font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
   font-size: 0.9em;
   user-select: auto;
   pointer-events: auto;
 }
-
-/* 焦点块内隐藏 HTML entity 的解码展示（显示原文标记），保持互斥 */
-.livemd-focus-block .livemd-html-entity-rendered {
-  display: none;
-}
```

**改动 2（隐含于上面 diff）**：删除 entity 解码 widget 的焦点块通配隐藏——entity 原文显示改由区域机制承担（style 元素在命中 entity 区域时生成 `{entitySpanSelector} + .livemd-html-entity-rendered { display:none }` 与原文 meta 显示规则配套）。

源码模式规则 `.livemd-editor.source-view .livemd-syntax-meta { display:inline }` 不动。

---

## 6. 踩坑点实录（五坑，防再次出现）

> 每坑按「现象 → 根因 → 修复前后对照 → 防线」记录。冒烟期实际数据佐证（读数均为 CDP 实测原样），修复顺序即五坑被发现的真实顺序。

### 坑 1：`import { Node }` 值导入遮蔽 DOM 全局 `Node` → 机制整体 no-op

- **现象**：冒烟中 style 元素根本不出现（`styleEls: 0`）、编辑器根无 scope class，`attachCaretStyle` 像从未执行过——但服务端模块内容确认是新版。
- **根因**：文件顶部 `import { Mark, Node } from "prosemirror-model"` 是**值导入**。构建产物里 prosemirror-model 的 `Node` 类绑定在运行时**遮蔽了 DOM 全局 `Node`**，`Node.ELEMENT_NODE` 变成 `undefined`，于是守卫 `view.dom.nodeType !== Node.ELEMENT_NODE` **恒为真**，`attachCaretStyle` 每次都走 no-op 早退分支（返回空 refresh/dispose）。而编辑器照常挂载、无任何报错——静默失效极具迷惑性。
- **防线**：类型只用就写 `import type`。该文件已固化写法并加注释警示。

```typescript
// ⚠️ 错误（值导入，运行时遮蔽 DOM Node）：
import { Mark, Node } from "prosemirror-model";
// ✅ 正确（type 导入在编译期擦除，不产生运行时绑定）：
import type { Mark, Node } from "prosemirror-model";
```

- **通用教训**：PM 生态的 `Node`/`Mark`/`Fragment` 等类型极其容易与 DOM 同名全局冲突；凡是「值导入 + 与 DOM API 混用」的模块要专门审这一条。

### 坑 2：i18n 插件把中文字面量替换为运行时调用 → 裸 key 污染 CSS 规则

- **现象**：style 文本以 `"4gxmivz\n\n.livemd-caret-scope-1 > p:nth-of-type(1) …"` 开头——**首条 CSS 规则的 prelude 被一个裸字符串污染**，整条规则作废（CSS 解析把 `4gxmivz` 与后面的选择器串成一个非法 qualified rule）。
- **根因**：项目使用 vite-auto-i18n-plugin（deepScan 模式）：**模块内任何含中文的字符串字面量**都会被编译成 `$deepScan("<key>")` 运行时调用，key 形如 `"4gxmivz"`，由主应用的 lang bootstrap 提供回译。我们生成的 style 文本里经过 `$deepScan` 直通后只剩裸 key——含中文的 `/* 注释字符串 */` 字面量同样中招。

**修复前后对照**：

```typescript
// ⚠️ 修复前（模块内原代码——注释字符串含中文）：
const parts: string[] = ["/* livemd 标记区域（动态生成，勿手改） */"];

// ⚠️ vite dev 实际服务的产物（curl 模块可见，中文字面量已被替换为运行时调用）：
const parts = [$deepScan("4gxmivz")];

// ⚠️ 运行时 style.textContent 实况（$deepScan 直通后只剩裸 key）——
// CSS 解析把 "4gxmivz" 与后面的选择器串成一个非法 qualified rule，首条规则整体失效：
"4gxmivz\n\n.livemd-caret-scope-1 > p:nth-of-type(1) span.livemd-syntax-meta { ... }"

// ✅ 修复后（动态 CSS 的字面量一律 ASCII）：
const parts: string[] = ["/* livemd caret-region (auto-generated) */"];
// 修复后 style 文本实况（规则恢复解析，meta 正常点亮）：
"/* livemd caret-region (auto-generated) */\n\n.livemd-caret-scope-1 > p:nth-of-type(1) > span:nth-of-type(1) > span.livemd-syntax-meta { ... }"
```

- **防线**：
  1. **动态生成 CSS / 拼入运行时文本的字面量一律纯 ASCII**（注释写成 `/* livemd caret-region (auto-generated) */` 并注明原因）；
  2. 任何「字符串字面量在运行时被翻译插件吃掉」的诡异问题，先查产物里 `$t(` / `$deepScan(` 的注入点（`curl http://localhost:5173/src/<模块路径>` 直接看服务端产物最快）。

### 坑 3：`domAtPos` 在 text 边界返回块元素 → 外壳解析塌缩到块根

- **现象**：区域命中了、规则生成了，但规则选择器形如 `.scope > p:nth-of-type(1) span…`——**外壳层被跳过**，规则命中整块；表现为「引用块内光标在粗体上，`> ` 前缀也跟着亮」（需求 1 破防）。用例 6 实测读数：`blockquoteMeta: ["inline"]`。
- **根因**：`view.domAtPos(pos, bias)` 当 `pos` 恰在 text 边界时返回的是**块级元素**（形如 `{ node: <p>, offset: childIndex }`）而非 textNode。以区域边界 `from`/`to` 取 DOM，`elFrom.contains(elTo)` 首判即真，外壳逐级回退直接落到 p。
- **实测佐证**（CDP 探针，语义区域 `**bold**` from=3 / to=11）：

```
domAtPos(4, -1) → { node: #text "**", offset: 1 }   ← 区域内部位置：正常返回 textNode
区域边界 3 的位置：内容 "aa" 与开标记 "**" 的交界 → PM 返回块元素级锚点
```

**修复前后对照**：

```typescript
// ⚠️ 修复前（初版 resolveShell：以区域边界 from/to 取 DOM）：
function resolveShell(view: EditorView, from: number, to: number): HTMLElement | null {
  const elFrom = elementFromDomAtPos(view, from, 1);   // 边界 → DOM 返回 <p>
  const elTo = elementFromDomAtPos(view, to, -1);       // 边界 → DOM 返回 <p>
  let shell: HTMLElement | null = elFrom;
  let hops = 0;
  while (shell && shell !== view.dom && hops++ < SHELL_MAX_DEPTH) {
    if (shell.contains(elTo)) return shell;            // ← p.contains(p) 首判即真
    shell = shell.parentElement;
  }
  return null;   // 永远走不到：shell = <p> 直接返回
}
// 后果：生成的选择器缺少 strong 层，descendant 尾巴命中整块 ——
//   .livemd-caret-scope-1 > blockquote:nth-of-type(1) > p:nth-of-type(1) span.livemd-syntax-meta
//   （p 内 "> " 前缀 meta 一并被点亮：blockquoteMeta ["inline"]）

// ✅ 修复后（定位端点取区域内部，落在开/闭标记文本之内）：
function resolveShell(view: EditorView, from: number, to: number): HTMLElement | null {
  const insideFrom = Math.min(from + 1, to - 1);   // 开标记 "**" 文本内部
  const insideTo = Math.max(to - 1, insideFrom);   // 闭标记 "**" 文本内部
  const elFrom = elementFromDomAtPos(view, insideFrom, -1);  // → textNode，parent 链正常
  const elTo = elementFromDomAtPos(view, insideTo, 1);       // → 标记文本父链
  ...
}
// 该函数随后在坑 4 中被整体替换（外壳前提不成立），但「内部取值」教训被
// resolveSyntaxSpan 完整继承：
function resolveSyntaxSpan(view: EditorView, from: number, to: number): HTMLElement | null {
  const inside = Math.min(from + 1, to);   // 微区域至少 1 字符，from+1 恒在范围内
  let el = elementFromDomAtPos(view, inside, -1);
  ...
}
```

- **防线**：凡用 `domAtPos` 定位 inline 内容，**取值点必须离开位置边界**（`from+1` / `to-1` 或等价的内部偏移），再规范化为元素；边界位置返回什么由 ViewDesc 层决定，不可依赖。

### 坑 4：「外壳祖先」模型在 PM 扁平渲染下不存在 → 必须逐标记精确定位

- **现象**：坑 3 修补外壳解析（内部取值）后，外壳依然稳定在**块根 p**。泄漏复测实锤：光标在 `aa**bold**cc==hl==dd` 的 bold 内，同段落的 `==hl==` 高亮标记**也被点亮**——实测读数 `highlightMeta: ["inline", "inline"]`（应为 none）。
- **根因**：实测 DOM（这是最重要的一次证据修正）——标记渲染是**扁平三段式**：

  ```html
  <p>
    aa
    <span class="livemd-syntax" data-syntax-type="strong"><span class="livemd-syntax-meta"><strong>**</strong></span></span>
    <strong>bold</strong>
    <span class="livemd-syntax" data-syntax-type="strong"><span class="livemd-syntax-meta"><strong>**</strong></span></span>
    cc
    <span class="livemd-syntax" data-syntax-type="highlight">…==…</span>
    <mark>hl</mark>
    …
  </p>
  ```

  **开标记 span、内容外壳（strong）、闭标记 span 是同级兄弟**——不存在 Typora `md-inline` 那种包裹三段的外壳祖先。「找外壳」这条路在 PM 的 mark 渲染模型下**原理性不成立**。

**修复前后对照**：

```typescript
// ⚠️ 修复前（外壳路线——该路线在坑 4 被判定原理性不成立并整体移除）：
for (const region of regions) {
  const shell =
    region.kind === "semantic"
      ? resolveShell(view, region.from, region.to)   // 扁平 DOM 下共同祖先恒为块根 p
      : resolveSyntaxSpan(view, region.from, region.to);
  const selector = buildSelector(view, shell, scopeClass);
  metaSelectors.add(`${selector} span.livemd-syntax-meta`);   // ← 尾巴是宽泛后代匹配
}
// 后果：规则命中 p 下全部 meta（模型解读数）：
//   .livemd-caret-scope-1 > p:nth-of-type(1) span.livemd-syntax-meta { display:inline; ... }
//   → 本段 bold 亮（对） + 同段 ==hl== 亮（泄漏）：
//   strongMeta ["inline","inline"] 、 highlightMeta ["inline","inline"] ← 泄漏实锤

// ✅ 修复后（定稿：语义区域在文档层展开为区间内每个带 syntax_marker 的 text，
//    逐标记定位 span、逐个生成唯一路径选择器；微区域直接定位自身）：
for (const region of regions) {
  if (region.kind === "semantic") {
    const $from = view.state.doc.resolve(region.from);
    const parent = $from.parent;                        // paragraph inline* 平铺，无嵌套节点
    const parentStart = $from.start($from.depth);
    let offset = parentStart;
    parent.forEach((child) => {
      if (
        child.isText &&
        offset >= region.from &&
        offset + child.nodeSize <= region.to &&
        child.marks.some((m) => m.type.name === "syntax_marker")
      ) {
        emitMarker(offset, offset + child.nodeSize, false);   // 开/闭标记各一条规则
      }
      offset += child.nodeSize;
    });
  } else {
    emitMarker(region.from, region.to, region.kind === "html_entity");
  }
}

// 尾巴收紧为标记 span 的唯一直子（配合上方单体定位，规则 bind 到单个标记）：
const META_TAIL = "> span.livemd-syntax-meta";
// 修复后 style 实况——开/闭两个标记 span 各一条规则，均精确到单体：
//   .livemd-caret-scope-1 > p:nth-of-type(1) > span:nth-of-type(1) > span.livemd-syntax-meta,
//   .livemd-caret-scope-1 > p:nth-of-type(1) > span:nth-of-type(2) > span.livemd-syntax-meta { ... }
// 复测读数（光标在 bold 内）：strongMeta ["inline","inline"] ✓ 、 highlightMeta ["none","none"] ✓
```

- **防线（定稿策略）**：语义区域在**文档层**展开为区间内每个带 `syntax_marker` 的 text（paragraph 的 inline* 内容平铺，children 遍历即全覆盖，嵌套语义的标记同样被捕获——嵌套标记 span 的 DOM 虽在 strong 内部，文档层仍是同一 paragraph 的直接孩子），**逐标记**定位其 span、逐个生成唯一路径选择器。收益：零泄漏（每规则 bind 到单个标记 span）、嵌套免费、不依赖 `:has()` 等新语法。
- **调试基建教训**：本坑靠实时 DOM dump 一次定位，比源码推演省几个小时——**遇到 PM DOM 疑云先 dump `innerHTML` / `domAtPos` 实况**：

  ```js
  // CDP Runtime.evaluate：段落 innerHTML（扁平三段式的铁证）
  ed.view.dom.querySelector("p").innerHTML
  // domAtPos 语义存疑时直接探双位置对比
  ed.view.domAtPos(3, -1)   // 边界
  ed.view.domAtPos(4, -1)   // 内部
  ```

### 坑 5（附）：调试页自身的环境坑

重建 `debug-dom.html` 冒烟页时遇到的两个环境问题，本地复现型项目都会撞到：

1. **i18n 运行时缺失**：vite-auto-i18n 把模块内中文字符串替换为 `$t("key")` / `$deepScan("key")` 全局调用，主应用由 lang bootstrap 提供这组全局；裸调试页没有 bootstrap，编辑器链中**任何**含中文字面量的模块（首个是 mindmap-markdown.ts）都会当场炸掉模块装载。

   **中招现场（两条报错按时间顺序出现）**：

   ```text
   ReferenceError: $t is not defined
       at http://localhost:5174/src/core/utils/mindmap-markdown.ts:2:27
   （补上 $t shim 后报错前进到下一处）
   ReferenceError: $deepScan is not defined
       at http://localhost:5174/src/debug-dom/main.ts:7:12
   ```

   **修复前后对照**：

   ```typescript
   // ⚠️ 修复前（main.ts 直接 import editor 链——$t/$deepScan 尚未定义即开始求值模块）：
   import "../core/styles/livemd.css";
   import { LivemdEditor } from "../core/editor";
   // → ReferenceError: $t is not defined（import 提升按声明顺序执行，editor 链先于一切函数体）

   // ✅ 修复后（shim 必须是第一个 import；同顶再暴露 __TextSelection 供冒烟脚本免时序坑）：
   import "./i18n-shim";                                  // ① 必须最先
   import "../core/styles/livemd.css";                    // ②
   import { LivemdEditor } from "../core/editor";        // ③
   import { TextSelection } from "prosemirror-state";    // ④
   ```

   ```typescript
   // src/debug-dom/i18n-shim.ts（直通回译，冒烟只断言机制不校验文案）
   (() => {
     const g = globalThis as unknown as Record<string, unknown>;
     if (typeof g.$t !== "function") g.$t = (text: string) => text;
     if (typeof g.$deepScan !== "function") g.$deepScan = (text: string) => text;
   })();
   export {};
   ```

   **防线**：凡新建不经主 bootstrap 的调试/测试入口，第一步先补 i18n 全局的直通 shim，并保持其为**第一个 import**（ESM 的 import 提升按声明顺序求值，函数体内的兜底来不及）。

2. **中文内容被 i18n 替换成回译 key**：调试文档喂中文，浏览器内文档 text 全部变成 key——探针按中文子串定位光标位置全部 `text not found`。**中招实况**（CDP dump 文档 text 节点原样）：

   ```json
   ["1: \"fke1\"", "5: \"**\"", "7: \"a63fpb9\"", "14: \"**\"", "16: \"eejb2\"",
    "24: \"> \"", "26: \"gtdf2\"", "31: \"**\"", "33: \"edas2\"", "38: \"**\"",
    "40: \"hu2t2\"", "48: \"###\"", "51: \" dr706u4\""]
   ```

   **修复前后对照**：

   ```typescript
   // ⚠️ 修复前（冒烟文档喂中文，浏览器内全部变 key，探针 text not found）：
   content: "从**系统模型与故障模型**出发\n\n> 引用**内容**文本\n\n### 标题文本\n"

   // ✅ 修复后（冒烟文档一律 ASCII——断言对象是显示机制而非中文渲染）：
   content: "aa**bold**bb\n\n> quote **bold2** tail\n\n### heading-text\n"
   ```

---

## 7. 测试与验证

### 7.1 Node 级回归 `test/caret-region.test.mjs`（11 用例，全绿）

vite `ssrLoadModule` 加载 parser 与装饰模块（与项目既有测试同基建）：断言 `computeCaretRegions(doc, pos, syntaxRegions)` 的集合语义——

| 用例 | 断言 |
|---|---|
| 光标在粗体内容内部 | 恰 1 个 semantic 区域，from/to 覆盖开标记起点到闭标记终点 |
| 光标在粗体外（相隔 ≥1 字符） | 空集合 |
| 光标紧贴闭标记之后 | 命中（「语法代码前后需要展示」需求） |
| 光标紧贴开标记之前 | 命中 |
| 嵌套粗斜体 | strong 外层 + emphasis 内层双区域，外层覆盖内层 |
| 行内代码内容内 | 命中 code_inline 区域 |
| 引用块 | 光标贴 `> ` 前缀标记上→**空集**；引用普通文字→空集；引用内粗体→仅 strong 区域（`> ` 永不显示的文档层语义验证） |
| 标题块内任意位置 | 空集合（heading 由焦点块 CSS 承担） |
| 转义 `\*` | 紧邻命中 escape 微区域；远处空 |
| HTML entity | 紧邻命中 html_entity 微区域 |
| 普通文本 | 空集合 |

### 7.2 真实浏览器冒烟（headless Chrome + CDP，9 断言全绿）

调试页 `debug-dom.html` 挂真实 `LivemdEditor`（`window.__debugEditor`），`caret-smoke.mjs` 驱动光标逐位落点并读 `getComputedStyle`：

| # | 场景 | 实测 | 判定 |
|---|---|---|---|
| 1 | 光标贴开标记前 | strongMeta `[inline,inline,…]` | ✓ 前后展示 |
| 2 | 光标在粗体内容内 | `[inline,inline,…]` | ✓ |
| 3 | 光标在粗体外 | 全 `none`、style 清空 | ✓ **图二问题修复** |
| 4 | 引用普通文字 | 全 `none` | ✓ |
| 5 | 光标贴 `> ` 前缀 | blockquoteMeta `none` | ✓ **需求 1** |
| 6 | 引用块粗体内 | 引用粗体 `[…,inline,inline]`、`> ` 仍 `none` | ✓ |
| 7 | 光标在标题块 | headingMeta `inline` | ✓ 现状维持 |
| 8 | 光标往返 8 次 | `childListOrText: 0`、`nonClassAttrs: 0`（attr 全为 class：focus/scope） | ✓ **幽灵态防线** |
| 9 | 序列化 | 原文无损往返 | ✓ |
| 附 | 泄漏复测 `aa**bold**cc==hl==dd` | strong `[inline,inline]`、**highlight `[none,none]`** | ✓ 相邻语义零串亮 |

用例 8 的零结构突变断言是本方案的**红线回归项**：光标移动路径只允许块级 class 变更，任何 text 层重建都意味着幽灵态风险回归。

### 7.3 验证命令

```bash
# 全量测试（应用本方案后 306/306 通过 = 295 基线 + 11 个 caret-region 用例）
node --experimental-strip-types --test "test/**/*.test.mjs"

# Lint 与格式
npx oxlint src/core/decorations/caret-style.ts src/core/decorations/index.ts
npx oxfmt src/core/decorations/caret-style.ts src/core/decorations/index.ts

# 构建（12.9s 通过）
pnpm build:web

# 冒烟（先起 dev server，再起 headless Chrome）
node caret-smoke.mjs
```

### 7.4 实机复验清单（Tauri WKWebView）

- 大文档中移动光标/回车编辑，无幽灵态回归（对应历史问题六/八）；
- 粗体/行内代码/高亮：光标邻近点亮、移开熄灭的编辑手感；
- 引用块内任意位置 `> ` 永不可见；源码模式全部标记照常显示、切回无损；
- 行内公式进出渲染/源码切换行为不变。

---

## 8. 附录

### 8.1 生成的 style 规则形态（实测样例）

光标在 `**bold**` 内容内时，`document.head` 里的动态样式（一次生成、光标离开整体替换为空）：

```css
/* livemd caret-region (auto-generated) */

.livemd-caret-scope-1 > p:nth-of-type(1) > span:nth-of-type(1) > span.livemd-syntax-meta,
.livemd-caret-scope-1 > p:nth-of-type(1) > span:nth-of-type(2) > span.livemd-syntax-meta {
  display: inline;
  color: var(--text-color-3, #999);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 0.9em;
  user-select: auto;
  pointer-events: auto;
}
```

引用块内粗体命中时（跨层路径自动生成）：

```css
.livemd-caret-scope-1 > blockquote:nth-of-type(1) > p:nth-of-type(1) > span:nth-of-type(2) > span.livemd-syntax-meta, …
```

### 8.2 冒烟调试页基建（三件套）

- `debug-dom.html`（项目根，dev server 直访 `/debug-dom.html`）：挂载点 + module 入口；
- `src/debug-dom/main.ts`：挂真实 `LivemdEditor` 并暴露 `window.__debugEditor`、`window.__TextSelection`（冒烟脚本免动态 import 时序坑）；
- `src/debug-dom/i18n-shim.ts`：`$t` / `$deepScan` 直通 shim（见坑 5）。

### 8.3 边界与风险备忘

- **回滚面**：机制集中于新模块 + CSS 两行（diff 见 5.6）+ view() 三行接线，可独立 revert，回退即恢复焦点块全亮行为；
- **源码模式**：`refresh` 检测 `sourceView` 自行清空 style 文本退场，与 `.source-view` 全显规则零冲突；
- **多编辑器实例**（多 tab）：scope class 唯一编号隔离，style 元素随 `view.destroy` 摘除；
- **非空选区**（拖选）：style 清空、标记隐藏——与 Typora 拖选行为一致性未强求，后续可按需放开；
- **composition（输入法）窗口**：选择器基于 DOM 实况序号，输入法组合中间态可能短暂失配，下一次 update 自愈，无结构破坏；
- **性能**：每光标移动 O(邻接 mark 数) 计算 + 一次字符串比较；style 文本有变化才写（同区域移动零写）。

### 8.4 方案与历史阶段的关系

| 阶段 | 显示粒度 | 通道 | 幽灵态安全性 |
|---|---|---|---|
| 一（问题六前） | 光标区域 hidden/visible 二态翻转 | inline decoration attrs | ✗ 触发幽灵态（已废弃） |
| 二（问题六/八） | 焦点块全亮 | 块级 node deco class（CSS 祖先） | ✓ 稳定但粒度过粗 |
| 三（本方案） | 光标紧邻区域级 | 块级 deco（heading 保留）+ head 下自管 style 元素 | ✓ 零 PM mutation 通道 |

### 8.5 「光标样式切换延迟」的根因因果链

本方案下标记的点亮/熄灭存在**一帧级**的可见延迟。这不是缺陷残留，而是「零 PM mutation 幽灵态纪律」的直接代价；其中有一条子链曾是真实缺陷，已修复。完整因果链如下：

```
链 A（主链 · 一帧级切换延迟 —— 设计权衡，非缺陷）
────────────────────────────────────────────────
光标移动（键入 / 点击）
 └→ ① dispatch transaction → PM updateState 同步周期
      ├→ ② plugin.apply 重算装饰 → 焦点块 node deco diff → 块根 class patch
      │      └→ heading 等"焦点块级"显示【此步直接改编辑器 DOM，早于样式框架重算】
      ├→ ③ PM ViewDesc DOM 同步完成
      └→ ④ 插件 view.update 钩子 → refresh()
           ├→ computeCaretRegions（复用缓存，O(邻接 mark 数)）
           ├→ buildCaretStylesheet（读 DOM 实况 + nth-of-type 序号）
           ├→ css !== lastCss 判重（同值零写）
           └→ ⑤ style.textContent 替换（document.head 下自管 <style>）
                 └→ ⑥ 浏览器把 stylesheet 变化并入下一次渲染帧的样式重算与 paint

因果定性：
- 区域标记的显示态不随 selection 变化直接写入编辑器 DOM，而是经
  「view.update 钩子 → head 下 style 元素 → 帧级样式重算」两跳落地（②…⑥）；
- 因此①…⑥与焦点块级显示（②）相比，区域标记的视觉切换晚约一帧。
- 这是用「paint 延迟一帧」换「text wrapper 永不重建」的权衡结果：
  任何把区域显示提前一跳的改法（inline decoration attrs / 外部直改 mark 元素
  class）都会回流到第 3 节源码证据所列的 DOM 重读路径 —— 幽灵态回归。

链 B（初始态迟滞 —— 真实缺陷，已修复）
────────────────────────────────────────────────
编辑器挂载（EditorView 构造期调用插件 view()）
 └→ attachCaretStyle 创建 <style> 元素与 scope class
     └→ 修复前：不调用 refresh() —— 等待第一次 view.update
         └→ 首个 PM update 在用户首次编辑 / 移动光标前【不会发生】
             └→ 后果：初始光标所在区域的标记不显示，直到用户动一下光标
                 （表象：刚打开文档时"标记显示有延迟/失灵"）

修复点（src/core/decorations/caret-style.ts，attachCaretStyle 尾部）：
    // 初次刷新：插件 view() 构造期不存在 update 钩子调用…
    refresh();
验证：挂载后不做任何 dispatch 的探针 —— __caretDebug 有数据、style 元素就位、
初始光标位置（纯文本）区域空集为正确语义（见 9.1 时间线）。
```

**执行者注意**：链 A 的帧级延迟在任何移植/优化中都**不应**通过「提前直写 DOM」来消除；
若产品上不可接受，唯一安全的加速方向是把 refresh 触发点前移到 plugin.apply 之后、
DOM 同步之前的微任务段（仍不触碰文本 DOM），通常不必要——帧级延迟低于人类感知阈值
（约 16ms@60fps，且仅在跨区域移动瞬间存在）。

---

## 9. 实施会话记录与仓库状态（2026-11-17）

> 本章为一次会话内的实施纪要与完成时点的工作区快照，供其他系统对照落地时机与仓库状态；代码内容以前述章节为准。

### 9.1 实施时间线

1. 方案批准（自管 style 元素通道）→ 落地 `src/core/decorations/caret-style.ts` + 装饰插件 `view()` 接线 + `livemd.css` 两处收紧；
2. Node 回归 11 用例一次通过（`test/caret-region.test.mjs`，先于浏览器调试——区域集合语义先行固化）；
3. 冒烟调试按**坑 1 → 坑 2 → 坑 3 → 坑 4 的顺序**暴露并修复（详见第 6 章；每坑都有 CDP 实测读数佐证）；
4. 9 项浏览器断言全绿 + 泄漏复测清零 → 全量测试 306/306 → `pnpm build:web` 通过；
5. 整理「光标样式切换延迟」因果链（8.5）：定性链 A 为零 mutation 纪律的帧级权衡、
   修复链 B（attach 期初次 refresh 缺失导致的初始态迟滞）并经无 dispatch 的挂载探针验证闭洞；
6. 同会话后半执行全库注释补齐（详见 9.2）；
7. REA 逆向扩展：对素材目录 Markdown 编辑软件（Typora / nomo / MDmeow / Textus / Aropyt）分析「渲染时机与无感渲染」，
   产出 `docs/REA-MARKDOWN-RENDER-TIMING.md`（七公理 + Typora 块手术 vs PM 增量机对机对比 + 块边界两种世界）；
8. 据逆向结论产出实施设计 `docs/SEAMLESS-RENDER-DESIGN.md`（四零承诺、公理 × 分项 × 验收断言的完整现状矩阵、P1-P5 模块设计）；
9. 代码块语言下拉支持输入过滤（语言/模式共用的 `createCustomSelect` 改造为 combobox）：
   打开即聚焦过滤框、label/value 不分大小写子串过滤、 ↓/↑ 键盘高亮 + Enter 提交、
   关闭自动复位——CDP 两轮冒烟通过（35 项 → 过滤单选 → doc 语言属性与 markdown 往返闭环）。

### 9.2 完成时点的工作区状态（未提交清单）

本文档成文时，以上改动**全部在工作区、尚未提交**，执行者按下列清单核对：

| 类别 | 项 |
|---|---|
| 问题十二·新增文件（未跟踪） | `src/core/decorations/caret-style.ts`、`test/caret-region.test.mjs`、`debug-dom.html`、`src/debug-dom/{main.ts,i18n-shim.ts}`、`caret-smoke.mjs` |
| 问题十二·跟踪文件修改 | `src/core/decorations/index.ts`（view() 接线 + 模块导出转发）、`src/core/styles/livemd.css`（焦点块规则收紧 + entity 通配隐藏删除） |
| 同会话·注释批次 | 全库注释补齐约 180 文件缺口清零（hooks 24 处 + utils 25 处 + TS 散件 34 + Vue 37 + **Rust 20 模块 `//!` 与 64 条 command `///`**）——删除行均为行尾注释重写与 BOM 修复，零代码逻辑变更 |
| 同会话·语言下拉过滤 | `src/core/nodeviews/code-block.ts`（`createCustomSelect`：filter 输入框 + 键盘导航 + 空态 + 关闭复位）、`src/core/styles/livemd.css`（filter/active/empty 样式段）、`src/debug-dom/main.ts`（冒烟文档补代码块） |
| 同会话·产出文档（docs/） | `docs/BUGFIX-CARET-REGION.md`（本文档）、`docs/REA-MARKDOWN-RENDER-TIMING.md`、`docs/SEAMLESS-RENDER-DESIGN.md` |
| 环境副作用 | `lang/index.json` 被 vite-auto-i18n 插件在 dev 运行期自动重写（与本方案无关，提交与否单独决策） |
| 顺手修复 | `src-tauri/src/lib.rs` 原带 UTF-8 BOM，注释插入使 BOM 漂移到文件中部导致 rustc 编译错误——已连根清除，cargo check 回归退出码 0（仅 2 个基线既有 warning，与 `NSPrintSavePath` unused-import 相关、修复前后一致） |

当前 `git diff --shortstat`（排除 i18n 副作用文件）：**约 131 文件 +863/−21 行**（相对 9.2 首记时点新增的 +126 行来自语言下拉过滤功能与设计文档矩阵补全）。

### 9.3 姊妹文档状态与提交建议

- **`docs/BUGFIX-GUIDE.md`（历史问题一～十一合集）已丢失**：该文件在本会话中被发现从磁盘消失，且从未被 git 跟踪（无历史可恢复）。本文档因此**独立成篇**；如需重建问题一～十一合集，需按相关会话记录另行整理。
- **⚠ docs/ 目录当前被 .gitignore 忽略**：`docs/` 下三份文档（本文档、REA 报告、无感渲染系统设计）**无法被 git 收录**——这也解释了历史 BUGFIX-GUIDE.md「从未有历史可恢复」的处境。若这些文档要进版本库，需先调整 `.gitignore` 的 docs 忽略规则（或为三份文档加否定规则 `!docs/*.md`）。
- **提交切分建议**（行为改动 / 纯注释 / 新功能 / 设计文档四类分离，回滚面彼此独立）：
  1. `feat(editor): 光标紧邻区域级语法标记显示（自管 style 通道）`——caret-style.ts、decoration 接线、livemd.css 两处、回归测试、debug 冒烟基建；
  2. `docs(comments): 全库注释补齐（文件头 + 导出注释，含 Rust rustdoc）`——注释批次 ~130 文件；
  3. `feat(code-block): 语言下拉支持输入过滤与键盘导航`——code-block.ts + livemd.css 下拉样式段；
  4. `docs(render): REA 渲染时机逆向报告与无感渲染系统设计`——前提是先解除 9.3 上述的 docs 忽略问题。
- **落地到其他系统后的复验命令链**（完整验证 ~1 分钟）：

  ```bash
  node --experimental-strip-types --test "test/**/*.test.mjs"   # 306/306（或迁移后的等值基线）
  pnpm build:web                                                  # 主要链构建
  pnpm dev & node caret-smoke.mjs                                 # 9 项显示断言 + 零突变断言
  cd src-tauri && cargo check                                     # 退出码 0（既有 unused-import warning 除外）
  ```

- **实机复验**：在 Tauri WKWebView 中以大文档验证四点——粗体邻接显示手感、引用 `> ` 永不可见、标题 `###` 维持可见、编辑长会话无幽灵态回归（历史上限 34 万字符文档场景）；另验语言下拉：打开即聚焦、键词过滤、Enter 直接落语言。

---

## 10. 无感渲染实施会话记录（2026-11-17 夜：S1 哨兵 + S2 调度器）

> **本节定位**：`docs/SEAMLESS-RENDER-DESIGN.md`（P1-P5 设计）的 **S1、S2 两阶段落地实录**。按本节清单可在任何 PM 谱系（ProseMirror 内核）的镜像项目上复现同一套"类 Typora 无感渲染"效果；P1/P2 以外的 S3（手术三律）、P5（CDP 冒烟基建）仍未实施，属设计文档规划内后续。
> **对应的逆向证据**：`docs/REA-MARKDOWN-RENDER-TIMING.md` 公理 E（Typora `File.inBusyMode` busy 抑制 + `ty-before-first-render` 遮罩）与公理 F（MDmeow `programmaticDocumentUpdate` 哨兵 + 内容基线）。

### 10.1 改动文件清单（本次会话，未提交）

| 类别 | 文件 | 作用 |
|---|---|---|
| **新增（S2 核心）** | `src/core/scheduler/render-scheduler.ts`（约 270 行） | 全局渲染调度器：busy 抑制 / interactive 顺延 / background 泄洪 / 同块去重 / cancelBlock |
| 新增（S2 遮罩） | `src/core/styles/livemd.css`（尾部追加一段） | `.livemd-render-pending`：`visibility: hidden` + `contain: paint`，min-height 由 JS 内联锁高 |
| 修改（S2 埋点） | `src/core/editor.ts`（+1 import，+3 行） | `dispatchTransaction` 内 `docChanged && addToHistory !== false` 时 `renderScheduler.notifyTyping()` |
| 修改（S2 Mermaid） | `src/core/nodeviews/code-block.ts` | 串行队列收编为 background 档 + 换图遮罩 + `destroy` 时 cancelBlock |
| 修改（S2 KaTeX） | `src/core/nodeviews/math-block.ts` | 块级公式缓存未命中走 interactive 档（命中保持同步直渲）+ destroy cancelBlock |
| **新增（S1 哨兵）** | `src/hooks/useProgrammaticGuard.ts` | `runProgrammatic` 深度计数 + `isRealEdit` 双基线（doc.eq + 序列化串） |
| 修改（S1 接点） | `src/components/editor/LivemdEditor.vue` | change 消费端短路；四条写入路径包裹；**删掉 `isHydrating`/`isSourceViewToggling` 两个孤旗** |
| 新增测试 | `test/render-scheduler.test.mjs`、`test/programmatic-guard.test.mjs` | 15 用例（调度器 7 + 哨兵 8） |

统计：`git diff --shortstat` 对 5 个跟踪文件 +262/−47；4 个新文件未跟踪。

### 10.2 调度器协议（`render-scheduler.ts` 核心决策）

对外只有三个入口 + 两个遮罩助手：

```ts
renderScheduler.notifyTyping()                    // 编辑器事务埋点（无返回、无副作用，幂置忙）
renderScheduler.enqueue({ blockKey, kind, run })  // run(signal) —— signal.aborted 时跳过 DOM 回写
renderScheduler.cancelBlock(blockKey)             // 块销毁：丢排队 + abort 在途
applyRenderMask(el) / removeRenderMask(el)        // 先按住、算完放开（遮罩类 + min-height 内联）
```

**行为参数**（文件顶部可调）：

| 参数 | 默认值 | 语义 |
|---|---|---|
| `TYPING_IDLE_WINDOW_MS` | 300 | 打字静默窗：窗内 background 挂起（Typora busy 同构） |
| `BACKGROUND_MAX_WAIT_MS` | 1500 | 泄洪上限：连续打字也不让任务饿死（"该渲不渲"兜底） |
| `INTERACTIVE_FRAME_BUDGET_MS` | 8 | interactive 同帧批量预算：大文档打开的公式爆发数帧内出清 |
| `LIVEMD_SCHEDULER`（globalThis） | — | 置 `"off"` 即旁路直渲（**整条调度回滚面**，回到旧行为） |

**档位语义**：interactive 只顺延到当前帧后、不受 busy 抑制（光标块的渲染延迟会直接被用户看到）；background 在 busy 中挂起、静默后 **≤1 任务/帧** 泄洪（帧间用 rAF/node16ms 双态）；**同 blockKey 入队自动作废旧排队任务**（一个块同一时刻只有最新内容的任务有效）；串行性由"一次只在途一个任务"保证，与被收编前的 Mermaid 全局串行队列等价。

**blockKey 来源**：NodeView 自增 uid（`codeblock:<n>` / `mathblock:<n>`，构造时分配）。不用 PM pos——pos 在撤销/重排中漂移，uid 在块生命周期内稳定；块的销毁信号（`destroy()`）是唯一需要撤销任务的时机。

### 10.3 两个昂贵渲染源的接入形态（关键差异，复现时勿混用）

**Mermaid（background 档 + 遮罩）**——空窗发生在 *入队前*：`createMermaidPreview` 缓存未命中时先把旧 SVG 换成一行占位文字，大图塌陷 → 渲染后弹回。因此：

```ts
// code-block.ts createMermaidPreview：换占位符之前
if (preview.querySelector("svg")) applyRenderMask(preview);   // 旧图不可见但几何恒定 + min-height 锁高
preview.innerHTML = '<div class="livemd-mermaid-placeholder">…</div>';
// afterMermaidRendered() 与 renderMermaidError() 的第一行：removeRenderMask(preview)
```

渲染本体走 `renderScheduler.enqueue({ kind: "background", run })`，`loadMermaidModule()` 与 `mermaid.render()` 前后都要复查 `signal.aborted || token !== this.mermaidRenderToken`。

**KaTeX 块（interactive 档，不挂遮罩）**——KaTeX 有全量 LRU 缓存（`mathRenderCache`），命中即几微秒；未命中的排版是毫秒级，空窗风险不在"换占位"而在"同帧抢键入"：

```ts
// math-block.ts updatePreview：命中同步直渲（快通道零延迟），未命中顺延一帧
const cached = peekMathRender(content, true);
if (cached !== undefined) { this.writePreview(cached); return; }
const token = ++this.renderToken;
renderScheduler.enqueue({ blockKey: this.renderKey, kind: "interactive",
  run: (signal) => {
    if (signal.aborted || token !== this.renderToken) return;
    this.writePreview(renderMath(content, true));   // 旧内容保留至新结果写回 —— 天然无空窗，无需遮罩
  }});
```

**为什么 KaTeX 不套 Mermaid 的遮罩**：遮罩解决的是"内容先清空再填充"的手术空窗；KaTeX 接入刻意不清空旧内容（新结果就绪才 innerHTML 替换，单帧原子），遮罩反而会多遮一帧。两种接法对应两种空窗形态，复现到其他内核时先判断你的昂贵渲染属哪一类。

### 10.4 程序更新哨兵（`useProgrammaticGuard.ts`）

```ts
const programmaticGuard = useProgrammaticGuard(() =>
  editor ? { doc: editor.view.state.doc, markdown: editor.getMarkdown() } : null
);

programmaticGuard.runProgrammatic(() => editor.restoreDoc(...));  // 包裹任何程序化写入
editor.on("change", ({ markdown }) => {
  if (!editor || !programmaticGuard.isRealEdit(editor.view.state.doc, markdown)) return; // 消费端首行
  ...
});
```

判定序（快到慢）：`suppressDepth > 0` → 假编辑；`markdown !== baselineMarkdown`（memcmp 数量级）→ 真编辑；相等才付 `doc.eq()` O(n) 结构比较作双保险。**打字路径每次 change 只付一次字符串比较成本**（快通道保证不进树遍历），守护 Z1 的 4ms 帧预算。

基线捕获是 O(1) 的关键：`restoreDoc` 派发时 `dispatchTransaction` 已为 change 事件调过 `getMarkdown()`，`markdownCache` 已热，哨兵 finally 里的 `snapshot()` 是缓存命中。

**接管清单**（已核销四条写入路径，复现时照查）：① 两阶段挂载 `hydrateEditorContent` 的 restoreDoc；② 外部回写 `syncEditorFromTab`（watcher / 检查修复）的 restoreDoc；③ 源码模式切换 `handleSourceViewToggle` 的 toggleSourceView；④ `defineExpose` 的 `setMarkdown` / `toggleSourceView`。**原 `isHydrating`/`isSourceViewToggling` 两个布尔孤旗整体移除**——它们只各覆盖一条路径，且无法免疫"回写后 150ms 的 outline 去抖尾"（内容基线才能）。

### 10.5 坑实录（本会话真实踩过）

**坑 6（续 5 坑编号）：泄洪唤醒的 0ms 定时器热循环。** 首版实现中，唤醒定时器到期后 `drain()` 只重新判断 `isTyping()` 就继续 `armWake()`——若 `forceIn` 已为负，`Math.max(0, …)` 得 0ms，定时器即刻再触发，busy 永远成立时变成 CPU 空转（测试表现为 `awaitIdle` 4 秒超时）。**修复**：把饥饿判定烘焙进 drain 本体——`starved = now() - backgroundQueue[0].enqueuedAt >= BACKGROUND_MAX_WAIT_MS`，starved 时无视 busy 直接执行；`armWake` 只负责约定"下一次重评时刻"。回归用例：`test/render-scheduler.test.mjs` 的"连续打字也不饿死 background"（静默窗故意拉到 60s，验证只有泄洪一条放行路径）。

**坑 7：Node 测试环境的 rAF 缺失是设计约束而非测试脚手架问题。** 调度器的帧调度写成双态（`typeof requestAnimationFrame === "function" ? rAF : setTimeout(16ms)`），既让 15 个单测在纯 Node 跑通，也意味着**复现系统若在非浏览器宿主（Worker / 测试器）中使用调度器，帧节拍会退化为 16ms 宏任务**——行为正确但时序余量要按 16ms 粒度设计测试。

**坑 8：tsc 基线噪音要预先甄别，别把既有错误当新引入。** 会话验证期的 `vue-tsc` 死于 tsconfig `baseUrl`/`moduleResolution=node10` 弃用告警（TS5101/5107），`tsc --noEmit` 另出 `caret-style.ts`/`catalog.ts` 的历史错误——全部与本轮改动无关（项目 scripts 本也没挂类型检查门）。以 `pnpm test` + `pnpm lint` + `pnpm build:web` 为准门即可；但复现系统若有干净类型门，建议先把这批基线清掉再叠加本功能，否则无法分辨回归归属。

### 10.6 验证基线（复现系统的核对值）

```bash
pnpm test        # 321/321（306 基线 + 15 新增：render-scheduler 7 + programmatic-guard 8）
pnpm lint        # oxlint + oxfmt 全绿（注意：本会话曾被 oxfmt 重写过一次，提交前先 pnpm format）
pnpm build:web   # vite 构建通过（~14s）
```

**调度器单测的时序宽裕值**（Node 无 rAF 退化到 16ms 粒度下的安全余量，复现系统照抄即可稳定）：busy 挂起断言窗 40ms（静默窗 120ms）、静默泄洪轮询 10ms/次、泄洪上限 150ms（静默窗 60_000ms）、在途 abort 观察窗 60+60ms。

**专项功能验收（复现后人工核对 Z1-Z4 四承诺）**：

| 承诺 | 操作 | 期望 |
|---|---|---|
| Z1 打字零渲染争帧 | 400 行 Mermaid 大文档中连打 10 字符 | Mermaid 不在键入帧内重渲；停手 ~300ms 后 ≤1 任务/帧泄洪更新 |
| Z1/KaTeX | 多公式文档光标扫块 | 命中公式零延迟显隐；未命中公式顺延一帧、旧内容保留不闪 |
| Z2 结构变化零空窗 | 编辑已渲染的 Mermaid 源码 | 换图期间旧图被"按住"（不可见但占位不塌陷），新图就绪一次替换，无白块弹跳 |
| Z3 光标零漂移 | 打字程序中未改路径（PM 原生），无新增操作 | 回归用例全绿即视为无回归 |
| Z4 程序更新零假编辑 | 自动保存触发 / watcher 模拟外部改文件回写 | 脏标记不闪亮、tab.content 不被去抖尾误回写、视口不跳；watcher 回写流程 change 消费端计数为 0 |
| 回滚面 | 控制台执行 `globalThis.LIVEMD_SCHEDULER = "off"` | Mermaid 立刻回到入队直渲的旧行为（问题定位用，结束后务必删除） |

### 10.7 在其他 PM 谱系项目上的复现步骤（推荐顺序）

1. **先落哨兵（S1），再落调度器（S2）**——S1 只触及 change 消费端与写入路径，风险面小、可直接消灭"文档自己刷新"这一最可感知缺陷；S2 涉及昂贵渲染方，每类渲染单独接。
2. 移植 `useProgrammaticGuard.ts`（零依赖，只要 PM `Node.eq`），把你的"hydrate / 外部回写 / 视图切换"类写入路径逐条包进 `runProgrammatic`，change 消费端首行插 `isRealEdit` 短路；**删除原有的一切程序更新布尔旗**，防止双轨判定漂移。
3. 移植 `render-scheduler.ts`（零依赖）；在编辑器 `dispatchTransaction` 加 notifyTyping 埋点（注意只对用户事务：`addToHistory !== false` 或等价的 `tr.getMeta` 标记）。
4. 逐类接入昂贵渲染（先判断空窗形态，见 10.3）：**占位符换图型**（Mermaid 类）走 background + 遮罩；**原地换内容型**（KaTeX 类）走 interactive + 旧内容保留。每类渲染给 NodeView 挂 uid 作 blockKey，`destroy()` 里 cancelBlock。
5. 抄 `test/render-scheduler.test.mjs`（7 用例覆盖调度协议全部分支，纯 Node 可跑），时序常量按 10.6 的宽裕值；哨兵 8 用例直接平移。
6. 灰度手段：保留 `LIVEMD_SCHEDULER=off` 旁路开关上线，实机观察负反馈后摘除。

### 10.8 与第 9 节会话状态的关系

- 9.2 节所列"问题十二"工作区清单不变；本节清单（10.1）叠加其上，**两者都尚未提交**，如按 9.3 的切分建议提交，本节改动应独占一个 commit：`feat(render): 无感渲染调度器与程序更新哨兵（S1+S2）`。
- `docs/SEAMLESS-RENDER-DESIGN.md` §6.1 已同步标注 S1/S2 落地状态与本节互为索引：那侧记"设计如何被实现"，本节记"实现如何可复现"。
- 测试基线数字随本节推进由 306 → **321**；9.3 的复验命令链中 `--test` 一行的期望值同步以本节数字为准。
