# OpsHub

按《Java 后端编码规范》（五层模块群架构）组织的多模块 Maven 工程，JDK 21。

## 五层模块群

| 层 | 模块 | 职责 |
| --- | --- | --- |
| ① 启动装配层 | `opshub-bootstrap` | 应用入口、全局切面（业务监控 / 数据访问监控）、环境配置、启动预热；唯一打包出口，零业务 |
| ② 接口契约层 | `opshub-api` | 对外 RPC 契约：FacadeService + Request/DTO/统一 Result；纯 POJO + JSR-303，零实现依赖，独立版本发布 |
| ③ 接口实现层 | `opshub-biz-impl` / `opshub-biz-shared` | Facade 实现（只做编排）；统一接入模板 / 校验器 / 转换器 / 补偿任务 / 消息监听 |
| ④ 领域层 | `opshub-domain-model` / `opshub-domain-service` | 充血领域模型 + 仓储【接口】（依赖倒置抽象端）；领域服务（业务逻辑唯一归属地） |
| ⑤ 基础设施层 | `opshub-infra-dal` / `opshub-infra-integration` | 仓储实现 / DAO·DO（代码生成）/ 事务封装 / 发号器；外部系统 Client / 事件 / 缓存（外部依赖唯一出口） |
| 公共工具 | `opshub-common` | 横切能力唯一定义点：统一日志 / 结果生成 / 错误码 / 监控注解 / 事务后执行器 / 分片键工具 |
| 测试域 | `opshub-test` | 数据驱动单测框架 + TestNG 组织；仅依赖启动装配（聚合出口） |

`opshub-biz`、`opshub-domain`、`opshub-infra` 为**纯 POM 聚合父模块**（`packaging = pom`，无代码、不引依赖），只做目录归拢与构建聚合；依赖判定一律按叶子模块约束。

## 依赖方向（严格单向，禁止反向与成环）

```text
opshub-test ──▶ opshub-bootstrap（唯一聚合出口）──▶ opshub-biz-impl
  opshub-biz-impl ──▶ opshub-biz-shared / opshub-domain-service / opshub-api
  opshub-biz-shared ──▶ opshub-domain-service ──▶ opshub-domain-model / opshub-infra-dal / opshub-infra-integration
  opshub-infra-dal ──▶ opshub-domain-model / opshub-common / opshub-infra-integration
  opshub-infra-integration ──▶ opshub-common ──▶ opshub-domain-model / opshub-api
```

要点：

- `opshub-api` 零内部依赖、独立版本号（`api.release.version`，不随工程快照波动）；
- `opshub-biz-impl` 不得依赖 `opshub-infra-dal`（数据访问细节经领域服务与仓储接口隔离）；
- 内部依赖与三方件版本只在根 POM `dependencyManagement` 登记一次，子模块声明依赖**不带版本号**；
- 构建期由 `maven-enforcer-plugin` 强制依赖收敛（同构件多版本、禁用冲突组件直接阻断构建）。

## 模块内部包结构

每个模块的子包以 `package-info.java` 注明层职责与规范出处（§ 编号），详见各模块源码树：

```text
opshub-api                api/{base/{request,result,context}, order/{request,dto,enums}}
opshub-common             common/{log,result,error,monitor,validation,transaction,sharding}
opshub-bootstrap          bootstrap/{config,aspect,preheat} + resources/{spring,config}
opshub-biz-impl           biz/impl/{facade,event,answer}
opshub-biz-shared         biz/shared/{template,validator,convert,compensation,listener}
opshub-domain-model       domain/model/{base/{entity,valueobject,event,repository,marker}, order/{vo,enums,repository}, exception}
opshub-domain-service     domain/service/{application,statemachine,flow/node,strategy} + resources/flow
opshub-infra-dal          infra/dal/{repository/convert,dataobject,daointerface,ibatis,transaction,lock,seq} + resources/sqlmap
opshub-infra-integration  infra/integration/{client,param,event/{publisher,bus},cache,config}
opshub-test               test/framework（main）+ test/testcase（test，TestNG）
```

包名与模块名对照（规范 §2.9.2）：`opshub-domain-model` → `domain.model`，`opshub-infra-dal` → `infra.dal`，以此类推。

## 构建

```bash
mvn validate            # 依赖收敛校验（Enforcer）
mvn dependency:tree     # 依赖树排查（规范 §2.5.3）
```
