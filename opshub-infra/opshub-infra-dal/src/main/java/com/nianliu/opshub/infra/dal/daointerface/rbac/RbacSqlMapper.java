package com.nianliu.opshub.infra.dal.daointerface.rbac;

import org.apache.ibatis.annotations.*;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
public interface RbacSqlMapper {
    @SelectProvider(type = RbacSqlProvider.class, method = "bind")
    List<Map<String, Object>> select(@Param("sql") String sql, @Param("args") Object[] args);

    @UpdateProvider(type = RbacSqlProvider.class, method = "bind")
    int execute(@Param("sql") String sql, @Param("args") Object[] args);

    @InsertProvider(type = RbacSqlProvider.class, method = "bind")
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insert(
            @Param("sql") String sql,
            @Param("args") Object[] args,
            @Param("row") Map<String, Object> row);
}
