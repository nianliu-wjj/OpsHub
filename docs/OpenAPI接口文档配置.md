# OpenAPI 接口文档配置

日期：2026-11-18。Spring Boot 3.5 使用 springdoc 2.8.x 兼容线，当前引入 2.8.17 的 `springdoc-openapi-starter-webmvc-ui`。版本在根 POM 登记，依赖及配置放在 bootstrap。

## 访问与配置

- 开发、测试环境开启文档；公共配置默认关闭，生产显式开启需设置 `springdoc.api-docs.enabled=true` 和 `springdoc.swagger-ui.enabled=true`。
- Swagger UI：`/swagger-ui.html`（跳转到 `/swagger-ui/index.html`）。
- OpenAPI JSON：`/v3/api-docs`，UI 配置：`/v3/api-docs/swagger-config`。
- 文档及 UI 静态资源全部要求 ADMIN 角色，沿用 HTTP Basic，无匿名文档白名单。浏览器初次访问会收到 Basic 认证挑战，使用已配置管理员凭据。
- 扫描 `com.nianliu.opshub`，排除 `/error` 与 `/actuator/**`。OpenApiConfiguration 配置标题、版本、联系人和全局 Basic 认证 scheme。

## Swagger UI 调用业务接口

通过 Authorize 配置 HTTP Basic 凭据。写操作保留 CSRF 校验：使用同一浏览器会话调用 `/auth/csrf`，把响应 data.token 放入 `localStorage.setItem('opshub.csrf', token)`。Swagger UI 已开启读取 localStorage 并发送 X-CSRF-TOKEN；刷新会话或令牌变化后重新获取。不要把凭据或令牌写入源码。

## JSON 兼容性

springdoc 内部使用 Jackson/swagger-core 生成 OpenAPI，因此引入其必要的 Jackson 依赖。这是文档组件的内部依赖，业务 HTTP 接口仍优先使用 Fastjson2。ByteArrayHttpMessageConverter 放在 Fastjson2 前面，确保 springdoc 返回的文档字节原样输出，避免被二次编码为 Base64 或字符串。

本次是对原手册“JSON 唯一依赖实现”规则的明确例外：HTTP 业务序列化继续统一，文档生成允许组件内部 Jackson。业务模块不新增 Jackson 调用。

## 验证

测试实际访问 `/v3/api-docs`，解析返回 JSON 并断言标题、Basic scheme 和控制器路径，验证匿名/普通角色拦截与管理员 Swagger UI 静态页面访问。
