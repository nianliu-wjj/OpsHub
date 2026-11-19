package com.nianliu.opshub.infra.dal.dao.rbac;

import com.nianliu.opshub.infra.dal.daointerface.rbac.RbacSqlMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Component
@RequiredArgsConstructor
public class MybatisSqlExecutor {
    private final RbacSqlMapper mapper;

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return mapper.select(sql, args).stream()
                .map(
                        row -> {
                            Map<String, Object> normalized = new LinkedCaseInsensitiveMap<>();
                            normalized.putAll(row);
                            return normalized;
                        })
                .toList();
    }

    public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
        return mapper.select(sql, args).stream()
                .map(row -> convert(row.values().iterator().next(), type))
                .toList();
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        var values = queryForList(sql, type, args);
        if (values.size() != 1) throw new IllegalStateException("查询必须返回一行");
        return values.getFirst();
    }

    private <T> T convert(Object value, Class<T> type) {
        if (type == Long.class && value instanceof Number number)
            return type.cast(number.longValue());
        return type.cast(value);
    }

    public int update(String sql, Object... args) {
        return mapper.execute(sql, args);
    }

    public long insert(String sql, Object... args) {
        Map<String, Object> row = new HashMap<>();
        mapper.insert(sql, args, row);
        return ((Number) Objects.requireNonNull(row.get("id"))).longValue();
    }
}
