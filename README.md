# OpsHub

OpsHub 是基于 JDK 21、Spring Boot 3.5 的多模块 Maven 运维平台后端。当前已实现统一响应与异常处理、业务处理模板、参数校验、操作审计、Spring Security 认证、RBAC 管理接口、Redis 权限缓存和 springdoc 接口文档。

## 目录结构

```text
OpsHub/
├── pom.xml                              # 根父 POM：模块聚合、版本登记、编译与依赖收敛
├── AGENTS.md                            # Java 编码与提交规范
├── README.md                            # 项目结构、模块职责与使用入口
├── docs/                                # 工程复现、系统设计、认证、RBAC 与缓存设计文档
├── sql/
│   ├── rbac-reference-schema.sql        # 从参考脚本提取的原始 RBAC DDL
│   ├── rbac-schema.sql                  # 新库完整建表脚本
│   └── rbac-upgrade.sql                 # 原库补字段、索引与约束的升级脚本
├── opshub-api/                          # 接口契约层
│   └── src/main/java/.../api/
│       ├── base/result/                 # Result 统一响应信封
│       ├── rbac/                        # RBAC 请求、校验注解与分页响应契约
│       └── order/                       # 订单契约扩展包，目前为结构预留
├── opshub-common/                       # 公共能力层
│   └── src/main/java/.../common/
│       ├── error/                       # 错误码
│       ├── opslog/                      # 审计注解、记录模型、消费 SPI 与字段 diff
│       └── validation/                  # JSR-303/SpEL 校验门面、约束与分组
├── opshub-biz/                          # 接口实现模块群的聚合父模块
│   ├── opshub-biz-shared/
│   │   └── src/main/java/.../biz/shared/template/
│   │                                    # 七步业务模板、回调与降级守卫
│   └── opshub-biz-impl/
│       └── src/main/java/.../biz/impl/rbac/
│                                        # RBAC Controller、权限检查与请求编排
├── opshub-domain/                       # 领域模块群的聚合父模块
│   ├── opshub-domain-model/
│   │   └── src/main/java/.../domain/model/
│   │       ├── exception/               # 业务拒绝、参数拒绝和唯一键冲突异常
│   │       ├── base/                    # 实体、值对象、事件和仓储接口扩展包
│   │       └── order/                   # 订单领域扩展包，目前为结构预留
│   └── opshub-domain-service/
│       ├── src/main/java/.../domain/service/rbac/
│       │                                # RBAC 业务规则、事务、关联校验与版本控制
│       └── src/main/resources/flow/      # 业务流程配置扩展位
├── opshub-infra/                        # 基础设施模块群的聚合父模块
│   ├── opshub-infra-dal/
│   │   └── src/main/java/.../infra/dal/
│   │       ├── dataobject/rbac/          # 生成的 MyBatis-Flex 实体
│   │       ├── daointerface/rbac/        # 生成的 BaseMapper 接口
│   │       ├── repository/rbac/          # 独立维护的参数化 SQL 与数据访问实现
│   │       └── generator/               # 离线 SQL → Entity/Mapper 代码生成器
│   └── opshub-infra-integration/
│       └── src/main/java/.../infra/integration/cache/
│                                        # Redis 权限与菜单缓存、故障回源
├── opshub-bootstrap/                    # 启动装配层与可执行 JAR 出口
│   ├── src/main/java/.../bootstrap/
│   │   ├── OpsHubApplication.java       # 应用启动入口
│   │   ├── *ExceptionAdvice.java        # 全局异常及 RBAC 冲突响应
│   │   ├── config/                      # Security、OpenAPI、JSON、Mapper 与审计 SPI 装配
│   │   ├── aspect/                      # OpsLog 审计切面
│   │   └── preheat/                     # 校验预热和组件 Banner
│   ├── src/main/resources/
│   │   ├── application.properties       # 公共应用配置
│   │   ├── config/app-*.properties      # dev/test/prod 环境配置
│   │   ├── spring/datasource.xml        # 数据源与事务管理器装配
│   │   └── logback-spring.xml           # biz/monitor/dal/oplog/error 五通道日志
│   └── src/test/java/                   # 当前框架、Security、OpenAPI、RBAC 回归测试
└── opshub-test/                         # 独立测试模块
    ├── src/main/java/.../test/framework/ # 测试基础设施扩展包，目前为结构预留
    └── src/test/java/.../test/testcase/  # 跨模块测试用例扩展包，目前为结构预留
```

`...` 表示公共包前缀 `com/nianliu/opshub`。上图展示主要业务目录；各模块的 `target/` 是构建产物，根目录的 `logs/` 是运行日志，均不作为源码维护。部分领域、流程和测试包只有 `package-info.java`，属于扩展骨架。

## 每个模块的功能

| 模块 | 功能与边界 |
| --- | --- |
| 根模块 `OpsHub` | 管理所有模块的构建顺序，统一登记内部模块和三方依赖版本，配置 JDK、Lombok/MapStruct 注解处理器以及 Maven Enforcer 依赖收敛规则。 |
| `opshub-api` | 定义对外数据契约，包括请求对象、原生参数校验、分页结果和 Result。调用方可独立引用，不能依赖业务实现或数据访问模块。 |
| `opshub-common` | 提供跨业务共享的错误码、操作日志模型与 SPI、字段 diff、JSR-303 和 SpEL 校验。侧重公共能力，具体 HTTP 切面及消费实现由启动层装配。 |
| `opshub-biz` | `packaging=pom` 的聚合父模块，组织 biz-shared 与 biz-impl，自身不承担业务实现。 |
| `opshub-biz-shared` | 提供统一七步处理模板、业务回调、校验组透传和降级守卫。模板不开启业务事务，事务边界由领域服务负责。 |
| `opshub-biz-impl` | 提供 HTTP 接口和应用编排。当前负责账号、角色、菜单、授权绑定、当前权限/菜单和登录日志接口；调用模板及领域服务，处理认证授权检查与审计声明。 |
| `opshub-domain` | `packaging=pom` 的聚合父模块，组织领域模型和领域服务。 |
| `opshub-domain-model` | 定义领域异常和后续实体、值对象、事件、仓储接口的落位。目前 RBAC 业务规则位于 domain-service，基础领域包主要为结构预留。 |
| `opshub-domain-service` | 实现 RBAC 核心规则：实体存在性与菜单循环校验、授权关系替换、应用层关联完整性、事务、主行乐观锁和全局权限版本。通过基础设施访问数据库与缓存。 |
| `opshub-infra` | `packaging=pom` 的聚合父模块，组织数据访问和外部集成模块。 |
| `opshub-infra-dal` | 管理 MyBatis-Flex、JDBC 和 MySQL 驱动。保存生成的实体/Mapper及独立自定义 SQL；生成器支持离线建模并拒绝覆盖现有代码，避免重新生成影响业务查询。 |
| `opshub-infra-integration` | 封装 Redis 等外部系统集成。当前缓存生效角色/权限码和授权菜单，账号密码、状态、管理列表和日志不缓存，Redis 故障回源数据库。 |
| `opshub-bootstrap` | 应用唯一可执行 JAR 出口。组装各层组件，配置 Security、OpenAPI、JSON 转换、Mapper 扫描、数据源、事务、异常响应和审计切面，执行启动预热，管理环境配置与日志通道。当前自动化回归测试也集中于本模块。 |
| `opshub-test` | 独立测试扩展模块，依赖 bootstrap 聚合出口并登记 TestNG/Spring Boot 测试依赖。当前主要保留测试框架和用例包结构，后续放置跨模块测试。 |

## 依赖约束

聚合父模块只归拢目录和构建，不是业务调用层。主要业务调用链为：

```text
bootstrap → biz-impl → biz-shared / domain-service / api / common
domain-service → domain-model / infra-dal / infra-integration
infra-dal → domain-model / api / common / infra-integration
infra-integration → common
common → api / domain-model
test → bootstrap
```

bootstrap 还会直接依赖装配所需的公共、领域和基础设施模块。直接使用某模块的类，应在自身 POM 显式声明依赖；biz-impl 通过领域服务访问数据，不能直接依赖 infra-dal。禁止反向依赖和依赖环。

内部模块与三方依赖版本优先在根 POM 集中管理，子模块引用统一版本。依赖治理由 Maven Enforcer 校验；OpenAPI 内部所需 Jackson 是已记录的组件例外，业务 HTTP JSON 仍优先采用 Fastjson2。

## 构建与访问

```powershell
mvn validate
mvn install
java -jar opshub-bootstrap/target/opshub.jar
```

启动前配置 `OPSHUB_ADMIN_USERNAME` 和 `OPSHUB_ADMIN_PASSWORD_HASH`（BCrypt 摘要）；数据库和 Redis 连接参数通过应用环境配置提供。新库使用 `sql/rbac-schema.sql`，原库使用 `sql/rbac-upgrade.sql`，两条初始化路径互斥。

dev/test 默认开启 Swagger UI：`http://localhost:8080/swagger-ui.html`，需要管理员认证。写接口保留 CSRF 校验，具体调用步骤见下方文档。

## 详细文档

- [工程实现复现手册](docs/工程实现复现手册.md)：分层、模板、校验、审计与响应规范。
- [权限认证框架选型与配置](docs/权限认证框架选型与配置.md)：Spring Security 选型、认证和 CSRF。
- [OpenAPI 接口文档配置](docs/OpenAPI接口文档配置.md)：Swagger UI、访问权限和 JSON 兼容性。
- [RBAC 权限系统架构与生成指南](docs/RBAC权限系统架构与生成指南.md)：接口、SQL、缓存及代码生成。
- [RBAC 数据删除策略](docs/RBAC数据删除策略.md)：逻辑删除、物理删除、乐观锁和日志保留理由。
