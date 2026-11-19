# OpsHub

基于 JDK 21、Spring Boot 3.5 的多模块 Maven 运维平台后端。HTTP 接口由 api 模块提供，业务 Service 接口定义在 domain-service，具体实现位于 biz-impl。当前包含 RBAC、Spring Security、Redis 权限缓存、Springdoc、统一校验、业务模板和操作审计。

## 目录结构

```text
OpsHub/
├── pom.xml                             # 版本管理、模块聚合与构建规则
├── AGENTS.md                           # 编码与提交规范
├── docs/                               # 架构、认证、缓存、生成及删除策略说明
├── sql/                                # RBAC 原始 DDL、新库建表与原库升级 SQL
├── opshub-contract/                    # 共享请求、响应及 Result 数据模型
│   └── src/main/java/.../api/
│       ├── base/result/                # Result
│       └── rbac/                       # AccountRequest、RoleRequest、MenuRequest 等
├── opshub-api/                         # HTTP 接入层
│   └── src/main/java/.../api/
│       ├── rbac/                       # User/Role/PermissionController 与公共查询接口
│       └── auth/                       # 当前身份和 CSRF 接口
├── opshub-biz/                         # 业务实现模块群聚合父 POM
│   ├── opshub-biz-shared/
│   │   └── src/main/java/.../biz/shared/template/
│   │                                   # 七步模板、回调与降级守卫
│   └── opshub-biz-impl/
│       └── src/main/java/.../biz/impl/rbac/
│           ├── *ServiceImpl.java       # 用户/角色/权限独立实现与公共查询
│           └── RbacOperations.java     # 实现层内部的事务、关联校验与缓存协调组件
├── opshub-domain/                      # 领域模块群聚合父 POM
│   ├── opshub-domain-service/
│   │   └── src/main/java/.../domain/service/rbac/
│   │                                   # User/Role/PermissionService 业务接口
│   └── opshub-domain-model/
│       └── src/main/java/.../domain/model/
│           ├── exception/              # 业务异常
│           └── base/、order/           # 领域模型扩展包，目前主要为结构预留
├── opshub-common/
│   └── src/main/java/.../common/
│       ├── opslog/                     # 审计注解、SPI、记录与字段 diff
│       ├── validation/                 # JSR-303/SpEL 校验与分组
│       └── error/                      # 错误码
├── opshub-infra/                       # 基础设施模块群聚合父 POM
│   ├── opshub-infra-dal/
│   │   └── src/main/java/.../infra/dal/
│   │       ├── dataobject/rbac/         # MyBatis-Flex 实体
│   │       ├── daointerface/rbac/       # BaseMapper 接口
│   │       ├── dao/rbac/                # DAO 和 MyBatis 参数化 SQL 执行
│   │       ├── repository/rbac/         # Repository 委托 DAO
│   │       └── generator/              # 离线代码生成器
│   └── opshub-infra-integration/
│       └── src/main/java/.../infra/integration/cache/
│                                       # Redis 权限与菜单缓存
├── opshub-bootstrap/
│   ├── src/main/java/.../bootstrap/
│   │   ├── OpsHubApplication.java      # 启动入口
│   │   ├── *ExceptionAdvice.java       # 统一异常响应
│   │   ├── config/                     # Security、OpenAPI、JSON、Mapper 和审计装配
│   │   ├── aspect/                     # 审计切面
│   │   └── preheat/                    # 启动预热与 Banner
│   ├── src/main/resources/
│   │   ├── application.properties      # 公共配置
│   │   ├── config/app-*.properties     # 环境配置
│   │   ├── spring/datasource.xml       # 数据源与事务装配
│   │   └── logback-spring.xml           # 五通道日志
│   └── src/test/java/                  # HTTP、认证、RBAC 和框架回归测试
└── opshub-test/                        # 独立跨模块测试扩展模块
```

`...` 表示包前缀 `com/nianliu/opshub`。共享数据对象保留原 Java 包名，以保持现有源码引用兼容，但归属 opshub-contract。target 是构建产物，logs 是运行日志；部分扩展包只有 package-info.java。

## 模块职责

| 模块 | 功能与边界 |
| --- | --- |
| 根 OpsHub | 聚合构建、管理版本和注解处理器、校验依赖收敛。 |
| opshub-contract | 定义共享 DTO、请求校验、分页结果及 Result。只提供数据模型，供 Controller、Service 和基础设施共同引用。 |
| opshub-api | 放置 Controller，负责路由、HTTP 参数绑定、入口校验和 Result 包装；只调用 domain-service 的 Service 接口。 |
| opshub-domain-service | 放置业务 Service 接口，描述业务能力及输入输出；不依赖 biz-impl、api 或基础设施。 |
| opshub-biz-impl | 实现 domain-service 的 Service 接口，负责业务规则、模板编排、密码编码、权限检查、审计和事务处理，通过内部组件访问基础设施。 |
| opshub-biz-shared | 提供实现层可复用的业务模板、回调和降级能力，不承载 Controller。 |
| opshub-domain-model | 定义领域异常，预留实体、值对象、事件等领域模型。 |
| opshub-common | 提供日志模型、审计 SPI、校验引擎、分组和错误码等公共能力。 |
| opshub-infra-dal | 管理 MyBatis-Flex/JDBC/MySQL，提供生成实体、Mapper、自定义 SQL 和离线生成器。 |
| opshub-infra-integration | 封装 Redis 等外部集成。当前只缓存生效权限和授权菜单，缓存故障回源数据库。 |
| opshub-bootstrap | 统一装配 api、biz-impl 及基础组件，是可执行 JAR 出口；负责认证、OpenAPI、异常、配置、切面与预热。 |
| opshub-test | 独立跨模块测试扩展位；当前回归测试主要位于 bootstrap。 |
| opshub-biz/domain/infra | packaging=pom 的聚合父模块，只组织子模块构建。 |

## 依赖与调用方向

```text
HTTP 请求 → api.Controller → domain-service.Service 接口
                                  ↑ 实现
                            biz-impl.ServiceImpl
                                  ↓
                            infra-dal / infra-integration

bootstrap → api + biz-impl + 装配所需模块
api → domain-service + contract
domain-service → contract + domain-model
biz-impl → domain-service + biz-shared + infra + common + contract
common / infra → contract（共享模型）
```

api 不依赖 biz-impl，接口实现由 Spring 装配注入。contract 拆出共享数据对象，避免公共层/基础设施反向依赖包含 Controller 的 api 而形成循环。直接使用某模块的类必须显式声明依赖。具体依赖版本由根 POM 管理，Maven Enforcer 校验收敛。

## 构建与访问

```powershell
mvn validate
mvn clean install
java -jar opshub-bootstrap/target/opshub.jar
```

启动配置 OPSHUB_ADMIN_USERNAME 和 OPSHUB_ADMIN_PASSWORD_HASH（BCrypt 摘要）。数据库和 Redis 参数通过环境配置提供。新库执行 sql/rbac-schema.sql，原库执行 sql/rbac-upgrade.sql，两条路径互斥。

dev/test 的 Swagger UI 位于 http://localhost:8080/swagger-ui.html，需要管理员认证；写接口保留 CSRF。共享模型迁移后首次构建使用 clean，避免旧模块 class 文件导致重复类或重复路由。

## 详细说明

- [分层架构调整](docs/分层架构调整.md)
- [工程实现复现手册](docs/工程实现复现手册.md)
- [权限认证框架选型与配置](docs/权限认证框架选型与配置.md)
- [OpenAPI 接口文档配置](docs/OpenAPI接口文档配置.md)
- [RBAC 权限系统架构与生成指南](docs/RBAC权限系统架构与生成指南.md)
- [RBAC 数据删除策略](docs/RBAC数据删除策略.md)

用户、角色、权限拆分与当前数据访问链见 [实体拆分与DAO数据访问](docs/实体拆分与DAO数据访问.md)。
