# REA 逆向分析：Markdown 编辑软件的渲染时机与无感渲染原理

> **素材目录**：本仓库的兄弟目录 `rea-software/software`（Typora.dmg、typora-setup-x64.exe、Markmira / Markdific dmg、及 Aropyt / MDmeow / nomo / Textus / solomd 等已解包源码）
> **研究方法**：REA（逆向工程分析）——挂载安装镜像、检索前端 bundle 中的调度与渲染原语（render/refresh/selection/debounce/busy 等窗口取证）、结合已解包开源源码逐文件阅读。
> **研究问题**：
> 1. 文档**何时**被渲染（打开、键入、结构变化、滚动各时机的分工）；
> 2. 每次更新文档后**如何**渲染（整树重建？局部手术？事务替换？）；
> 3. 实现原理，以及**如何做到用户更新文档后"无感渲染"**——不刷新、不闪屏、焦点不漂移。
> **整理日期**：2026-11-17

---

## 1. 软件形态清点（分析对象与定位）

| 软件 | 形态 | 编辑内核 | 渲染管线归类 |
|---|---|---|---|
| **Typora** | 商业闭源，原生壳 + WKWebView（mac）/ Electron 系（win） | **自研 contenteditable + jQuery** | 块粒度 DOM 手术（本文深挖对象） |
| **nomo** | Tauri 应用（与 LiveMD 同壳同内核谱系） | **ProseMirror 全家桶 + prosemirror-markdown** | PM 增量 ViewDesc diff（标准基线） |
| **MDmeow** | Web/桌面 | **@milkdown/crepe**（PM 系封装） + CodeMirror（代码块） | PM 事务 + 程序更新抑制（含大量实战防御） |
| **Textus** | Tauri 应用 | @milkdown/kit（React 绑定）+ marked | 同 milkdown 系 |
| **Aropyt** | Tauri 应用 | **CodeMirror（源码编辑）** + markdown-it + **iframe 第二渲染管线** | 阴性对照：源码 + 预览双管线，非所见即所得 |

> Markmira / Markdific 为闭源 dmg，超出本轮证据预算未逐 bundle 追；solomd 缺 package 清单，佐为辅助。

---

## 2. Typora 解构：渲染时机五分法与「块手术」原理

取证文件：`Typora.dmg` 挂载后 `Typora.app/Contents/Resources/TypeMark/appsrc/`（`window/frame.js` ≈1.6MB 为编辑器内层 bundle，`main.js` 为窗口层）。

### 2.1 渲染时机的五分法（各自独立调度）

| 时机 | 发生什么 | 证据 |
|---|---|---|
| **① 打开文档** | 解析 → 生成全树块 DOM；顺带把 `#write` 挂 `ty-before-first-render` 类作**遮罩**，首渲染完成后 `setTimeout(remove, 10)` 摘除。数学类块渲染前再次挂遮罩（`beforeRenderMath()` 中 `document.querySelector("#write").classList.add("ty-before-first-render")`） | frame.js："beforeRenderMath(){document.querySelector(\"#write\").classList.add(\"ty-before-first-render\")}… a.contains(\"ty-before-first-render\")&&setTimeout(()=>{a.remove(\"ty-before-first-render\")},10)" |
| **② 键入文字** | **什么都不渲染**。块内容是 contenteditable 的 **live DOM**（语法文件 span/加粗 strong 已在块内联呈现，光标在哪个块、哪个块就是"只显示必要标记"的展开态 md-focus）——**打字路径与渲染路径彻底分离，这就是"无感"的根基** | `$(e).closest("[cid]").addClass("md-focus")`——活动块以 `[cid]` 块属性定位、仅类名联动，无渲染调用 |
| **③ 块结构变化**（回车产生新块 / 粘贴 / 块类型切换 / 列表_patterns_） | **块粒度 DOM 手术**：对受影响块 `renderNode` / `renderHtmlWithBlockMeta` 生成新节点 → **`findElemById(cid).replaceWith(newNode)`** 精确替换，其余块零接触 | frame.js："u=v.renderHtmlWithBlockMeta(a,File.editor.brush), s.findElemById(a.id).replaceWith(u)" |
| **④ 滚动/大文档** | 视口懒渲染（IntersectionObserver 家族，监听窗口含 MutationObserver 簇） | frame.js：`USE_MUTATION_OBSERVER&&"MutationObserver" in u&&(this._domObserver=new MutationObserver(...))`（IntersectionObserver 实现库片段） |
| **⑤ 昂贵子内容**（数学公式 / 图表 diagram / 代码高亮） | **独立调度器**：`mathInline.renderAll`、`Fences.renderFencesByUpdate`（代码块失焦后按更新内容渲染）、`renderAfter/renderUnder`（只渲染某块之后的数学块，增量）；**进行中输入时置 `File.inBusyMode`，busy 期间的 forceRefresh 改走渲染下部范围**，避免打字中被昂贵渲染抢帧 | frame.js："this.mathBlock.forceRefresh():this.mathBlock.renderUnder(e,n)"；"var t=n&&!File.inBusyMode" |

**渲染方法的词汇表**（frame.js 全量出现统计）：`renderNode` / `renderAfter` / `renderUnder` / `renderIfNeed` / `forceRefreshSoon` / `tryEnterBusyModeWithDelay` / `Fences.renderFencesByUpdate`——全部是**块/范围粒度**原语，没有"整文档 rerender"的常规入口（`forceRefreshSoon` 仅兜底）。

### 2.2 焦点（光标）保持原理

光标位置**从不依赖 DOM 聚焦句柄的连续性**，而是一组显式的选择保存/恢复原语：

```text
editor.selection.restoreSelection(node, { start, end })   // 渲染/插入后按块内偏移精确落回
editor.selection.jumpIntoElemBegin(node) / jumpIntoElemEnd(node)
editor.selection.jumpToNextBlock(...)
o.curSelection / buildUndo()                                // 选区入 undo 栈，撤销后还原
```

frame.js 原文（块插入路径）：

```js
r.findElemById(b.id).before(x.toHTML()),
r.selection.restoreSelection(r.findElemById(x.id)[0], e.length)
```

即：**任何块手术后，以 [块 cid, 偏移] 二元坐标恢复选区**——DOM 换了内脏，光标按"新块 + 同偏移"落回，用户视觉无感。搜索命中移动等场景还先 `$(".md-focus").removeClass("md-focus")` 再在目标块重挂，同样是坐标式恢复。

### 2.3 小结：Typora 的无感渲染三支柱

1. **打字不渲染**：live DOM 即数据源，输入路径零渲染调用；
2. **变更即手术**：结构变化按块 cid 定位、`replaceWith` 秒级局部替换 + 选区坐标恢复；
3. **昂贵渲染降频**：busy 抑制 + 范围化（After/Under）+ 遮罩类防白闪（先挡后算，算完摘）。

---

## 3. ProseMirror / milkdown 系：事务化无感基线与实战防御

### 3.1 渲染时机（nomo 与 milkdown 系的一致模型）

- **所有文档变化都是 transaction**（打字、粘贴、程序化替换），PM 的 ViewDesc 层对前后 doc 做**结构化 diff，只 patch 受影响的 DOM 片段**——不存在"重渲染"这个概念；
- nöm 的插件面（`src/lib/editor-core/plugins/`：mathInlineDraft / inlineSourceEditing / codeHighlightDecorationPlugin 等）与 LiveMD 同构，都是**在事务后追加增量效果**（装饰、nodeview 状态），从不整体树重建；
- 昂贵的 markdown 离散任务（lint 等）走 **Web Worker**（nomo：`src/app/workers/markdownLint.worker.ts`）——重计算不占输入帧。

### 3.2 MDmeow 的「外部更新无感」六式（`src/editor.ts` 逐条取证）

打开/切换/回写等**程序化文档更新**是最容易闪屏的场景，MDmeow（milkdown/Crepe）的实践：

1. **编辑器实例永生 + 单事务替换**：`setContent` 不销毁重建 crepe，走官方动作 `this.crepe.editor.action(replaceAll(content, true))`——一次 PM transaction 全树替换，**DOM 根与编辑器实例不动**，滚视口/焦点栈不重置；
2. **程序更新抑制标志**：`this.programmaticDocumentUpdate = true` 包夹 setContent 全程，期间 `notifyActualEdit()` 直接短路——**外部内容更新绝不被误判为用户编辑**（防「自动保存回写→误触发保存→再回写」的闪烁回环）；
3. **变更通知基线（双层去抖尾）**：milkdown 的 `markdownUpdated` 是防抖的、可能在 setContent 后很久才触发——用 `lastNotifiedDoc?.eq(doc) && markdown === last 标记` 过滤，`notifyActualEdit` 只在**真实内容变化**时回调（代码注释原文：“Milkdown's markdownUpdated is debounced and may fire long after setContent(), even when no user-facing document transaction occurred”）；
4. **渲染安全探测 + 罪段掩码**：setContent 前 `maskFragments`/`isSafeToRender` 校验（milkdown 在长文档中会损坏无关粗体的已知缺陷），不可渲染时**逐段二分定位**不安全片段（候选段落 ≤100）— 掩码为保护占位符——**宁掩勿崩、逐段保真**：
   ```ts
   let prepared = maskFragments(markdown, findKnownUnsafeFragments(markdown));
   ... for (const section of candidateParagraphs(prepared).slice(0, 100)) ...
   ```
5. **BOM 摘取**：`leadingBOM` 把 UTF-8 BOM 先剥离、读取/保存时再还原（BOM 交给 Milkdown 解析会损坏无关粗体的防御之一）；
6. **DOM 附着用 rAF 防抖**：代码块行号轨等**周边构件**（不属于渲染）由 MutationObserver 观察 DOM 增删 → **`cancelAnimationFrame` + `requestAnimationFrame` 合帧**后一次附贴排版（`renderExternalCodeLineNumbers` 直接按 rect 绝对定位排轨，避免驱动重排）。

### 3.3 对比结论（系内差异）

nomo（标准 PM）的无感来自 PM 核心能力本身；MDmeow 证明**即使内核自带增量**，产品层仍必须补两层（**更新通知厘清** + **渲染结果校验降级**）才能在真实长文档上做到稳定无感。

### 3.4 机对机对比：Typora「块手术」vs nomo「PM 增量 diff」

两种内核把同一条公理（变更粒度=块、光标=坐标）交给了**不同的承担者**——Typora 是业务层手写手术，nomo 是内核层自动 diff：

| 维度 | Typora（自研 contenteditable） | nomo（ProseMirror 内核） |
|---|---|---|
| **事实源** | 渲染后的 contenteditable DOM **就是**数据（"视图即模型"） | PM doc（不可变树）为唯一事实源，DOM 只是其投影 |
| **变更的来源** | 用户操作显式触发命令链（编辑器命令、菜单、输入规则手写逻辑） | 一切变更 = transaction（按键、粘贴、程序替换统一入口） |
| **谁计算 diff** | **业务代码**：自己的块管理器决定哪块要重造 | **内核 ViewDesc**：对前后 doc 自动做结构化 diff |
| **变更的最小单元** | 块级 DOM 元素（`[cid]` 定位） | ViewDesc 片段（块或 text chunk，属性与文本可单独 patch） |
| **替换方式** | `renderHtmlWithBlockMeta` 生成新节点 → `findElemById(cid).replaceWith(newNode)`（**节点整个换新**） | 同一 DOM 节点**原地 patch**（改属性/文本），节点认同尽量保留（**node identity 稳定**） |
| **打字路径** | 直写 live DOM，**不经任何管线**（无事务概念） | 每键一个事务（含 composition 处理），doc 与 DOM 逐键同步 |
| **光标保持** | 业务手写：手术前保存 → 术后 `selection.restoreSelection(node, {start,end})` 按 [块 cid, 偏移] 落回 | 内核自动：selection 随 **step mapping** 平移到新坐标系（`TextSelection.map`），撤回/重做同理由 history 保留坐标 |
| **撤消/重做** | 自研 undo 栈（命令快照、选区快照 `buildUndo()`） | prosemirror-history 的 inverted step 流，天然精确 |
| **昂贵子内容** | busy 模式抑制 + `renderAfter/Under` 范围化 + 遮罩类防闪；代码块失焦后 `Fences.renderFencesByUpdate` | NodeView 承接（CodeMirror 嵌入）、装饰由插件事务化追加；重计算可走 worker（lint） |
| **长文档** | IntersectionObserver 视口懒渲染（视口外块不渲染） | 全树常驻（依赖现代引擎；LiveMD 在此叠加顶层 content-visibility 分层） |
| **闪烁风险面** | 手术 replaceWith 的帧内空窗（靠快速替换与遮罩类兜住）、busy 外的抢占 | 无手术空窗；风险集中在**装饰/nodeview 的错误管理**（此前 LiveMD 问题六实证：inline decoration attrs 变化即触发 wrapper 重建） |
| **Impl 成本** | 每类块变化的手术代码全部自己写（Typora 团队十余年沉淀） | 内核免费获得 diff/undo/光标映射；产品层专注 schema、nodeview、插件 |
| **不可渲染来源** | —— | MDmeow 层另补安全探测降级（见 3.2 式 4） |

**殊途同归的收敛点**：两边都对用户承诺了同一组体验事实——① 打字无感（一个不进渲染管线、一个 diff 后无事可做）；② 结构变化只动一块（一个 replaceWith、一个 patch 同一节点）；③ 光标看着 DOM 演化落回原位（一个手写坐标恢复、一个内核 step map）。

**LiveMD 的站位与启示**：LiveMD 是 PM 谱系（nomo 同构），**diff 与光标映射已经免费**；真正要向 Typora 借的是「昂贵内容调度观」——busy 抑制、范围化、遮罩类；要向 MDmeow 借的是「外部更新纪律」（3.2 六式）。三个软件的差距不在有没有增量渲染，而在**增量渲染的周边治理**。

### 3.5 深挖一维：「块边界」的两种世界——Typora 手动维护 vs PM 建模期固化

3.4 表里"谁计算 diff"一行最锋利的实战差异是**块边界**（block boundary）：一次输入结束后，"哪段内容算一块？这次改动碰没碰边界？要不要把相邻块并进来/拆出去？"——Typora 的答案散落在业务代码里，PM 的答案是"这个问题不存在"。

#### A. Typora 的痛点：边界靠命令作者手动维护

1. **边界判定没有单一事实来源**。"块"由内容级解析器（fence/嵌套列表/引用缩进……）在渲染瞬间切出来，此后边界只存在于 DOM 结构与 `[cid]` 标记里。markdown 的天然歧义（软换行 vs 硬边界、`- a\n- b` 的列表合并、代码块内空行、嵌套引用层级）都必须由**每条编辑命令的作者**自行判断——命令链要显式回答"这次操作波及哪些 cid"（证据词：`renderIfNeed`、`restructure`——frame.js 各出现 1/1 处，即"先谓词判断边界要不要重组，再决定渲染范围"的手写守卫）。
2. **手术范围 = 作者声明**。`findElemById(cid).replaceWith(...)` 之前的每一步（text 属性刷新、undo 快照、selection 保存）都依赖作者把受影响块数列举**完全**——漏判一块 → 编辑后相邻块内容未重组（视觉滞后）；误判多块 → 不相关块被换新（无谓闪烁 + 光标坐标系重建立）。
3. **边界漂移波及光标**。`restoreSelection(node, {start,end})` 的坐标系挂在"块 cid + 块内偏移"上；一旦边界判定与实际重组不一致（例：引用行删成普通段、列表项升级层级），恢复原语落到的就不是用户心智中的同一点——需要配套 `jumpToNextBlock` 等修正命令兜底（frame.js 均有实证）。
4. **每加入一种新块类型都是边界知识的再投资**（fences/math_block/html_block 各自的边界规矩散在专门模块：`Fences.renderFencesByUpdate`、`mathBlock.renderUnder`…）。

> 一句话：**Typora 把"边界"当作每个命令都必须重新回答的运行时问题**——十余年打磨换来的是覆盖完备，但每一行手术代码都在对抗歧义。

#### B. PM 的答案：不存在"运行时边界判定"，只有建模期声明 + 坐标查询

nomo（及 LiveMD）的世界里，ViewDesc 从不"判定"边界，因为边界在三个更早的层面上已经被固化：

1. **Schema 建模期声明**（边界 = doc 树，不需要猜）。content 表达式（`"block+"`、`"inline*"`）与节点 spec 的 `defining: true`（在活内容边缘必须保留节点边界的操作提示）/`isolating`（禁止跨界编辑）在建模期就写死了什么是一个块。LiveMD 的 schema 就是现成证据：`heading`、`blockquote`、`code_block` 的 spec 都带 `defining: true`（`src/core/schema/index.ts`）——**回车/删除时边界怎么切，是 schema 声明 + transform 层（ReplaceStep 的结构合法性）自动保证的产物，命令代码无权也不需要表态**。
2. **Position 全局坐标系**（边界是一个整数，不是一次推断）。文档中每个节点边界在 pm doc 里都有唯一 position；`doc.resolve(pos)` 拿到 \$from/\$to 后 `nodeAfter/nodeBefore/minDepth` 等都是**查询**而非判定。光标、装饰、选区、编辑入口共享同一坐标系——"这次输入在不在某块里" `\$cursor.parent` 一读即得。
3. **ViewDesc 是 doc 树的 1:1 投影 + 对位复用**（边界不参与匹配运算）。prosemirror-view 的同步入口 `ViewDesc.updateChildren`（本地源码证据：`node_modules/prosemirror-view/dist/index.js` 中 "Sync the content of the given DOM node with the nodes associated with the given array of view descs..."）按**儿子数组的新旧对位**更新：这对 (旧 desc, 新 node) 若 `node.eq()` 成立 → 原 DOM 节点原地更新、nodeView 复用；不成立 → 销毁重建该片段。**匹配的对象是"节点序对"，边界只作为树结构隐含在序对嵌套里**——渲染层从未拥有对边界的决定权，自然也永远不会"判错边界"。

#### C. 代价转移与哲学对照

| | Typora 手动维护 | PM 建模期固化 |
|---|---|---|
| 边界的单一事实来源 | 无（散在命令/parser/选择恢复/fences 各处，靠约定一致） | **有**：一个 schema + 一棵 doc |
| 新块类型引入成本 | 每命令都要重新回答边界问题 | 一个 NodeSpec + content 表达式即声明完毕 |
| 边界 bug 的形态 | 漏判（内容不重组）/误判（无辜块闪烁、光标漂移）——**运行时爆雷** | schema 设计错误/content 不匹配（step 应用失败的报错与自动 rebuild，可静态审） |
| 痛点转嫁去向 | 实战堆经验 | 前移到 schema 设计与 appendTransaction 的合法态维护 |

**PM 并非零成本**：边界语义混沌只是**从运行时命令里前移到了建模期的 schema**——写错 content 表达式或缺 `defining`，会得到"回车不切块/段落吞并"这类结构级缺陷。但这类缺陷是**声明式的、可静态审阅、报错可定位**的；而手写边界错误是**命令覆盖不全的运行时漏网**——这就是架构把成本移到哪一层的问题。

#### D. 与本仓库的对照

LiveMD 里残留的"手动边界维护"已经被 PM 化到最窄的两处，可作自检基线：

1. `syntax-detector` 的受影响块扫描（`getAffectedBlockRanges`：±1 块安全边距）——在 PM 坐标系上做**受控的手动范围声明**，边界由 node 结构保证，±1 只是防御相邻 textblock 的 regex 边界，不是真边界判定；
2. 自定义 NodeView 的 `ignoreMutation`（列表/表格 nodeview 只认 class/style mutation）——同样是在"判定哪些 DOM 变化不代表文档变化"，但判域已被 NodeDesc 的对位更新限定在单节点内。

**结论**：块边界的两种世界 = **"运行时推断"vs"建模期声明"**。Typora 用十年的命令代码买回了"每个动作都能精确手术"；PM 把同一个问题用 schema + position + eq 对位三个原语直接**消解**了。这也解释了 3.4 表的最后一行——为什么 nomo 的业务代码量远小于 Typora：不含边界判定的编辑命令，才可能薄。

---

## 4. 阴性对照：Aropyt（源码+预览双管线）

`apps/web/src/runtime.ts` 自述（L785）：“iframe 渲染是**第二套渲染管线**——两边各显示一半（原地渲染没覆盖的）”；markdown-it **懒加载不进首屏**。CodeMirror 是编辑层、预览是独立 iframe——两管线间天然存在内容耦合窗口，闪烁治理成本（同步滚动、双向定位、防抖节流）远高于单管线方案。**这反证了"WYSIWYG 单管线"是无感渲染的结构前提**。

---

## 5. 无感渲染设计公理（证据提炼，可迁移）

| # | 公理 | 证据来源 |
|---|---|---|
| A | **视图树永不重建**：编辑器实例只创建一次，一切文档变更（含外部长文件替换）以**单事务**流入同一实例 | MDmeow replaceAll、PM ViewDesc、Typora 块手术 |
| B | **打字路径零渲染**：输入写入 live DOM / PM doc，不触发任何渲染管线；结构变化（回车/粘贴/类型切换）才涉及渲染 | Typora 活动块 md-focus、PM input rules |
| C | **变更粒度 = 块**：以稳定块 id（cid / PM node pos）定位，`replaceWith` 式局部替换，绝不触碰兄弟块 | Typora findElemById().replaceWith |
| D | **光标是坐标，不是 DOM 句柄**：手术前保存 [块, 偏移]，术后 `restoreSelection` 精确落回；选区进 undo 栈 | Typora selection.restoreSelection |
| E | **昂贵子渲染独立调度**：busy 模式抑制 + 范围化（renderAfter/Under）+ 遮罩类（ty-before-first-render）防白闪 + worker 化重计算 | Typora busy/遮罩、nomo lint worker |
| F | **程序化更新挂抑制标志**：批量替换期间封锁"编辑通知"，用内容基线（doc.eq + 字符串）过滤去抖尾巴， distinguishing 真实用户编辑 | MDmeow programmaticDocumentUpdate / lastNotifiedDoc |
| G | **防御性渲染校验**：对外部文档先探测可渲染性，不可渲染的二分定位罪段做占位保护，保语义不保样式 | MDmeow isSafeToRender、maskFragments |

**反模式（闪烁来源）**：整树 innerHTML 重写 / 销毁重建编辑器实例 / 自动保存回写链路缺"假编辑"过滤 / 渲染与输入同帧抢占 / 渲染前未遮罩导致中间态可视。

---

## 6. 对 LiveMD 的对照与改进清单

以 LiveMD 当前实现逐条对照公理：

| 公理 | LiveMD 现状 | 评价与建议 |
|---|---|---|
| A | 编辑器实例常驻；`restoreDoc` 把「内容 + 书签 + 光标」合为**单事务**一次替换（此前的三连事务优化） | ✓ 已达标；外部回写路径必须始终坚持不重建实例 |
| B | 打字路径 PM 原生，无渲染调用；**问题十二后行内标记显示走 head 下自管 style 元素（零 PM mutation）** | ✓ 已达标 |
| C | PM ViewDesc 原生块级 diff；**遗留差异**：无「块 cid」类稳定标识（PM 用位置，等价成立） | ✓ |
| D | 程序化恢复带 `cursorOffset` 钳制（restoreDoc 参数）；编辑期光标由 PM 原生保持 | ✓；粘贴/拖拽等自研命令手术后建议补 `setSelection` 显式落回（对照 Typora 的 restoreSelection 语义） |
| E | 有大文档 `content-visibility` 分层（问题八裁剪后仅顶层块）；**缺**：昂贵资源（Mermaid/KaTeX）渲染期遮罩类、busy 抑制、worker 化 lint（nomo 的 markdownLint.worker.ts 是直接可抄的模板） | 部分；建议引入「渲染遮罩类」与 busy 语义治理 Mermaid/KaTeX 渲染争夺帧 |
| F | 自动保存回写链已有 `isHydrating` / 恢复注入防抖（hydrate 流程）；**缺**：MDmeow 式「内容基线过滤」对 `editor.on("change")` 去抖尾的免疫 | 部分；可在 LivemdEditor.vue 的 change 通知处加 `doc.eq` 基线短路 |
| G | 无（信任自家解析器） | 对自研核心可暂缓；若后续接入主题转换/外部导入的不可控内容，可引用 MDmeow 探测降级模式 |

**一句话结论**：无感渲染**不是一个功能，而是五条管线纪律**——打字不经渲染、变更按块手术、光标按坐标恢复、昂贵渲染遮罩降频、程序更新零回调。LiveMD 在 A-D 上已是同构正确，E/F 两项是下一阶段的体验增益点。

---

## 7. 证据文卷索引（相对素材目录）

| 编号 | 证据 | 位置 |
|---|---|---|
| T-1 | 块手术：`renderHtmlWithBlockMeta` + `findElemById(id).replaceWith` | Typora.dmg → appsrc/window/frame.js |
| T-2 | 光标恢复：`selection.restoreSelection(node,{start,end})` ×4 处 | 同上 |
| T-3 | 初始/数学遮罩：`ty-before-first-render` add/remove + `beforeRenderMath` | 同上 |
| T-4 | busy 抑制与范围化渲染：`File.inBusyMode`、`mathBlock.renderUnder/After`、`Fences.renderFencesByUpdate` | 同上 |
| T-5 | 活动块定位：`$(e).closest("[cid]").addClass("md-focus")` | 同上 |
| T-6 | 视口懒渲染：IntersectionObserver + MutationObserver 簇 | 同上 |
| N-1 | PM 标准增量基线与插件面 | nomo/src/lib/editor-core/（ProseMirrorEditorCore.ts、plugins/） |
| N-2 | 重任务 worker 化 | nomo/src/app/workers/markdownLint.worker.ts |
| D-1 | 单事务替换 + 实例永生 | MDmeow-main/src/editor.ts `setContent`：`replaceAll(content, true)` |
| D-2 | 程序更新抑制 + 去抖尾基线 | 同文件：`programmaticDocumentUpdate` / `lastNotifiedDoc` / `notifyActualEdit` |
| D-3 | 渲染探测降级 | 同文件：`maskFragments` / `isSafeToRender` / `candidateParagraphs` |
| D-4 | BOM 防御 | 同文件：`leadingBOM` |
| D-5 | 周边构件 rAF 附着 | 同文件：`codeLineObserver` + `scheduleExternalCodeLineNumbers` |
| AP-1 | 双管线反模式自述 | Aropyt/apps/web/src/runtime.ts L785 起 |

> 复现方法：`hdiutil attach -readonly -nobrowse Typora.dmg` 后进入 `TypeMark/appsrc`；开源项目直接在素材目录按上表文件路径检索关键词。
