# 实体拆分与 DAO 数据访问

2026-11-20。

用户、角色、权限分别定义 User、Role、Permission 领域实体，位于 domain-model/rbac；持久化实体对应 SysAccount、SysRole、SysMenu，保留 MyBatis-Flex 表映射。

HTTP 接口分别由 UserController、RoleController、PermissionController 提供；对应 UserService、RoleService、PermissionService 业务接口位于 domain-service，实现分别位于 biz-impl。原 RbacController/Service 只保留日志查询、当前权限与身份相关能力。URI 保持兼容。

Repository 不直接操作 JDBC 或维护 SQL。数据访问顺序为 Service 实现 → 内部事务组件 → RbacRepository → RbacDao → MyBatis Mapper。RbacDao 维护 RBAC 查询与绑定 SQL，MybatisSqlExecutor 通过 RbacSqlMapper 执行；SqlProvider 将位置参数转换为 MyBatis 参数绑定，不把请求值插入 SQL 文本。新增 Mapper 属于自定义查询，不属于离线生成器覆盖范围。

所有写操作继续处于原事务范围，保留主表乐观锁、逻辑删除过滤、关联物理删除及权限版本行锁。领域实体不包含密码，认证查询仍是内部专用路径。

代码使用四空格缩进和统一 Java 格式化处理。测试使用真实 MyBatis Mapper + H2 验证 SQL 查询、参数绑定、授权过滤及主键回填；JdbcTemplate 仅用于测试数据表初始化。

开发服务：MySQL 位于 192.168.37.130:3306，已在 opshub 库创建 sys_account、sys_role、sys_menu、sys_account_role、sys_role_menu、sys_login_log 和 sys_rbac_version 七张表。Redis 192.168.37.130:6379 已通过 PING 和临时 key 读写验证，验证 key 已清理。
