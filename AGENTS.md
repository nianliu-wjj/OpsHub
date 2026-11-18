# Java 编码规范

- 新建类、接口、枚举、注解、抽象类和 record（含具名内部类型）必须添加 Javadoc：`@author nianliu-wjj` 和 `@since YYYY-MM-DD`，日期填写实际创建日期。
- 合适时使用 Lombok 消除样板代码，保持原有行为，避免无差别使用 `@Data`。
- 避免未使用导入、原始类型、重复代码等规范告警，运行相关构建与测试。
- 每完成一个任务，使用中文提交说明提交到 Git。

- 项目采用 Spring MVC：Controller 放在 opshub-api；业务 Service 接口放在 opshub-domain-service；实现放在 opshub-biz-impl，不引入 RPC Facade。共享数据模型放在 opshub-contract，禁止形成循环依赖。
