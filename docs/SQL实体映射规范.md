# SQL 实体映射规范

2026-11-20。查询链使用 MyBatis 直接创建目标实体，禁止将查询行返回 Map 再通过 JSON 或字段键名转换。

| 查询 | 返回类型 |
| --- | --- |
| 用户分页/详情 | User、PageResult<User> |
| 角色分页/详情 | Role、PageResult<Role> |
| 权限分页/详情、授权菜单 | Permission、PageResult<Permission>、List<Permission> |
| 登录日志 | LoginLog、PageResult<LoginLog> |
| 认证凭据 | UserCredential，仅用于内部认证 |
| 数量、版本、关联 ID | Long 或 List<Long> |
| 权限码 | List<String> |

RbacSqlMapper 每个查询入口明确声明结果类型；列别名对应实体属性，Mapper 完成实例化和字段映射。DAO、Repository 及业务接口传递相同类型，Controller 返回 Result<Entity>。认证凭据与用户展示实体分离，User 不包含密码，列表和详情的 SQL 也不选择密码列。

insert/update 参数绑定中的键值容器属于写入参数，不作为 SQL 查询结果。自增主键使用 GeneratedKey 承接。Redis 菜单缓存反序列化为 List<Permission>，不再读取 Map。字段缺失或拼写错误应在映射测试中暴露，业务代码不再使用 row.get("字段名")。
