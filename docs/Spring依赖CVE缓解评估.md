# Spring 依赖 CVE 缓解评估与决策记录

| 项 | 说明 |
| --- | --- |
| 决策编号 | OPS-SEC-2026-01 |
| 决策 | **维持 Spring Boot 3.5.16（Spring Framework 6.2.19）+ 触发面逐项缓解 + 有条件排期升级** |
| 决策人 | 项目负责人（2026-11-17 选定 B 方案） |
| 背景事件 | IDE 漏洞扫描（Mend）标记 spring-web/beans/expressions 6.2.19 存在 2026-08-20 批次新披露 CVE（含 CISA 评 9.8/9.1 的条目） |
| 复核周期 | 每月（新 CVE 披露联动复核）；升级窗口见 §5 |

---

## 一、事实核查结论（本轮逐一拉取公开详情核实）

### 1.1 关键事实一：这批 "Critical" 分数与 Spring 官方评级严重背离

2026-08-20 Spring 单日披露 91 个 CVE。CISA-ADP 将其中 6 个评为 Critical（9.8/9.1），
而 **Spring 官方按真实触发条件与实际影响评分全部为 Low~Medium（2.6~6.5）**。
安全研究机构对该批 CVE 逐条对照代码修复 commit 后的结论：
"CISA 的 Critical 评级无一被代码路径支持，厂商评分才反映实际风险"
（完整对照分析见 JFrog Security Research 2026-09 公开报告）。

**本项目以 Spring 官方 advisory 的 CVSS 向量为准做二次评估**，Mend/CISA 评分作为暴露面排查的线索而非结论。

### 1.2 关键事实二：OpsHub 触发面逐项对照，全部未命中

| CVE | 组件 | 官方评分 | 触发条件（缺一不成立） | OpsHub 触发面 |
| --- | --- | --- | --- | --- |
| 2026-47891 | spring-web | 4.3 | WebFlux 栈 + **aalto-xml** 可选依赖 + JAXB Flux 流式解码 XML 端点 | ❌ 不命中：MVC servlet 栈；无 aalto-xml；无 XML 端点 |
| 2026-59283 | spring-expression | 6.5 | **SimpleEvaluationContext**（非默认）+ SpEL **编译器** IMMEDIATE/MIXED 模式（默认关） | ❌ 不命中：见 §2.1 专项说明（本工程 SpEL 重度使用者，已专项核查） |
| 2026-47890 / 59313 | webmvc/webflux | 2.6 | fragments-over-SSE 特性 + 攻击者数据流入 SSE 流 | ❌ 不命中：无任何 SSE 端点 |
| 2026-47892 | spring-webflux | 4.8 | RouterFunctions.toHttpHandler() **脱离 Spring Boot 的 standalone 部署**（CORS preflight 绕过） | ❌ 不命中：标准 Boot 部署（DispatcherServlet 先行拦截 preflight） |
| 2026-47884 | spring-webmvc | 5.8 | XsltView 遗留视图 + catch-all `/**` 映射 + 视图名取自请求路径 → 条件性 RCE | ❌ 不命中：纯 REST + fastjson2，无模板视图 |
| 2026-59282 | spring-beans | 7.5(CISA) | DataBinder/BeanWrapper 绑定**用户提交的属性路径** + 自填充 List 实现 | ❌ 不命中：无表单 DataBinder 场景（@RequestBody 走消息转换器，非 DataBinder） |
| 2026-59281 | webmvc | 6.1 | Errors.getFieldErrors() 渲染到 HTML（MVC 表单/JSP 场景 XSS） | ❌ 不命中：REST JSON 应答，无服务端 HTML 渲染 |
| 2026-59314 | spring-web | 3.1 | 文件下载接口把用户输入直接拼入 Content-Disposition → 响应拆分 | ❌ 不命中：当前无文件下载端点（未来新增时按红线清单执行） |
| 47889 / 47885 / 47883 | 待逐项 | 中危 | 同批次边缘路径（MVC/WebFlux 特性面） | 批量低风险：MVC 基础 CRUD 查询面不涉触发路径；排期升级后整体消失 |

### 1.3 结论

**当前代码形态下，9 项可核查 CVE 的触发路径在本工程全部不可达**；叠加 OpsHub 属
内网管理面（业务面数据不经过本平台，网络仅内网可达），实际风险敞口评级：**低**。
维持 3.5.16 的决策在"不触发条件持续成立"前提下是安全的。

---

## 二、专项核查：CVE-2026-59283（SpEL 守卫绕过）—— 必须单独说清

本工程是 **SpEL 重度用户**（操作日志模板 + 校验组件），58283 与我们技术面同域，专项核查结论：

**不命中的两道阀门**：

1. **我们不使用 SimpleEvaluationContext** —— 操作日志用 `MethodBasedEvaluationContext`、校验用 `StandardEvaluationContext`（root=宿主）。58283 的本质是"编译后的字节码绕过 SimpleEvaluationContext 的运行时策略检查"——**连受限上下文都不在用，不存在"绕过守卫"概念可发生的前提**；
2. **SpEL 编译器未开启** —— 两处求值器均为 `SpelExpressionParser` 缺省解释模式执行（IMMEDIATE/MIXED 编译模式默认关闭），表达式编译缓存缓存的是 **AST**（`Expression` 对象）而非字节码产物，不触及该 CVE 的代码路径。

**另有一道结构性防线**：本工程全部 SpEL 表达式均为**编译期字面量**（注解属性、VO 约束声明），
无任何"运行时拼接表达式"入口 —— SpEL 注入面在架构上就不存在（@OpsLog javadoc 已固化此红线）。

### 2.1 防回归红线（新增到 CR 检查清单）

| # | 红线 | 理由 |
| --- | --- | --- |
| 1 | **禁止开启 SpEL 编译模式**（`spring spel.compiler.mode` 相关配置/代码注入 IMMEDIATE、MIXED） | 58283 的触发前提之一；解释模式性能足用（有 AST 缓存） |
| 2 | **禁止将外部输入拼接进任何 SpEL 表达式串** | 运行时动态表达式将打开整个注入面（架构性红线，已有） |
| 3 | 新增依赖引入 `aalto-xml`、SSE 端点、XsltView、文件下载接口、WebFlux 栈前，**先复核本表 §1.2 对应行** | 触发条件逐项成立 Cooke 的防回归 |
| 4 | 新表单类接口接入 `DataBinder`/`Errors` 渲染路径前，复核 59282/59281 | 同上 |

---

## 三、3.5 OSS 线终止的诚实面对

必须书面承认两个事实（避免决策被误读为"永远不升"）：

1. 该批 CVE 在 3.5.x/6.2.x 上**永无官方开源修复**（OSS 维护线已终止；商业支持线除外）。
   本决策的安全性**依赖"触发条件持续不可达"**——这是一条需要**持续复核警戒**的前提，不是一次性结论；
2. "不触发"是**当前代码形态**的结论。任何引入 WebFlux/SSE/XML 端点/文件下载/受管 List 绑定的新需求，
   都必须先过 §2.1 红线清单——单不满足即触发"升级前置条件"。

---

## 四、缓解措施清单（本决策下的常态化防线）

| # | 措施 | 状态 |
| --- | --- | --- |
| 1 | 触发面逐项评估表（§1.2）与防回归红线（§2.1）进入工程文档，CR 按 §2.1 清单核对新增功能 | ✅ 本文档固化 |
| 2 | 部署收口：生产仅内网网段可达 + 网关访问控制（运维平台不暴露公网） | ▲ 部署期执行（阶段②上线检查项） |
| 3 | 网关/反代请求体大小上限（对冲全部资源耗尽型 CVE 的一般性缓解） | ▲ 部署期执行 |
| 4 | 依赖扫描（Mend/OWASP Dependency-Check）纳入构建流程，新披露 CVE 联动 §1.2 表复核 | ▲ 阶段②接入 CI |
| 5 | SpEL 编译模式/动态表达式红线（§2.1 第 1/2 条） | ✅ 代码与注释已固化 |

---

## 五、升级排期触发条件（何时必须启动 Spring Boot 4.x 专项）

满足**任一**即在一周内启动专项（预估 30~60min 级专项验证 + 迁移窗口）：

1. **新功能需求触碰 §2.1 红线清单第 3/4 条**且无法绕开（需要 WebFlux/SSE/受管 List 绑定等场景）；
2. **新披露 CVE 的触发路径落在本工程实际使用面内**（每月复核 + 扫描告警联动）；
3. **生态迁移窗口**：MyBatis-Flex / fastjson2 spring 扩展发布 Spring Boot 4 官方适配版本（升级阻力面消失）；
4. 阶段②接口开发启动时（借势执行，避免线上运行期升级窗口混沌）。

> 升级目标线：Spring Boot 4.1.x 当前最新 patch（携带 Spring Framework 7.0.9+ 的全量修复）。
> 迁移验证面：MyBatis-Flex starter 兼容性、fastjson2 spring 扩展包代次、hibernate-validator 9 行为差异、
> jakarta.servlet API 版本（日志/审计/校验组件均为自研 SPI 优先结构，改造面收敛在装配层）。

---

## 六、决策一句话收口

> CISA 的 9.8 没有被代码支持，官方的 2.6~6.5 没有被本项目触发 —— **维持版本、把触发面证据固化成红线清单、把升级从"是否"改为"何时"**：任何触碰红线的新需求或新 CVE 落进真实使用面，专项即启。

---

**关联文档**：《系统设计方案.md》§3.10 版本安全治理（本决策已回写该表）；
本次复核的信息来源：Spring 官方 security advisory（spring.io/security/cve-2026-*）、
NVD/CISA 记录、以及独立安全研究机构对该批 CVE 修复 commit 的逐条对照分析报告。
