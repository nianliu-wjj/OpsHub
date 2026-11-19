package com.nianliu.opshub.infra.dal.daointerface.rbac;

import com.nianliu.opshub.domain.model.rbac.*;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
public interface RbacSqlMapper {
    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<User> selectUsers(@Param("sql") String sql, @Param("args") Object[] args);

    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<Role> selectRoles(@Param("sql") String sql, @Param("args") Object[] args);

    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<Permission> selectPermissions(@Param("sql") String sql, @Param("args") Object[] args);

    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<UserCredential> selectCredentials(@Param("sql") String sql, @Param("args") Object[] args);

    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<LoginLog> selectLogs(@Param("sql") String sql, @Param("args") Object[] args);

    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<Long> selectLongs(@Param("sql") String sql, @Param("args") Object[] args);

    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<String> selectStrings(@Param("sql") String sql, @Param("args") Object[] args);

    @UpdateProvider(type = RbacSqlProvider.class, method = "bind")
    int execute(@Param("sql") String sql, @Param("args") Object[] args);

    @InsertProvider(type = RbacSqlProvider.class, method = "bind")
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insert(
            @Param("sql") String sql, @Param("args") Object[] args, @Param("row") GeneratedKey row);
}
