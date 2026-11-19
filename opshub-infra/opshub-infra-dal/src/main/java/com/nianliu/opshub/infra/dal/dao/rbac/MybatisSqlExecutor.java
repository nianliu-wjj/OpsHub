package com.nianliu.opshub.infra.dal.dao.rbac;

import com.nianliu.opshub.domain.model.rbac.*;
import com.nianliu.opshub.infra.dal.daointerface.rbac.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Component
@RequiredArgsConstructor
public class MybatisSqlExecutor {
    private final RbacSqlMapper mapper;

    public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
        List<?> rows;
        if (type == User.class) rows = mapper.selectUsers(sql, args);
        else if (type == Role.class) rows = mapper.selectRoles(sql, args);
        else if (type == Permission.class) rows = mapper.selectPermissions(sql, args);
        else if (type == UserCredential.class) rows = mapper.selectCredentials(sql, args);
        else if (type == LoginLog.class) rows = mapper.selectLogs(sql, args);
        else if (type == Long.class) rows = mapper.selectLongs(sql, args);
        else if (type == String.class) rows = mapper.selectStrings(sql, args);
        else throw new IllegalArgumentException("不支持的查询实体类型");
        return rows.stream().map(type::cast).toList();
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        var values = queryForList(sql, type, args);
        if (values.size() != 1) throw new IllegalStateException("查询必须返回一行");
        return values.getFirst();
    }

    public int update(String sql, Object... args) {
        return mapper.execute(sql, args);
    }

    public long insert(String sql, Object... args) {
        var row = new GeneratedKey();
        mapper.insert(sql, args, row);
        return Objects.requireNonNull(row.getId());
    }
}
