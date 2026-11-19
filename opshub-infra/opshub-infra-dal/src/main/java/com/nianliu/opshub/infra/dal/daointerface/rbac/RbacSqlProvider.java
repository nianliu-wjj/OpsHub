package com.nianliu.opshub.infra.dal.daointerface.rbac;

import java.util.Map;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
public final class RbacSqlProvider {
    private RbacSqlProvider() {}

    public static String bind(Map<String, Object> parameters) {
        String sql = (String) parameters.get("sql");
        Object[] args = (Object[]) parameters.get("args");
        StringBuilder result = new StringBuilder();
        int index = 0;
        for (char c : sql.toCharArray()) {
            if (c == '?') result.append("#{args[").append(index++).append("]}");
            else result.append(c);
        }
        if (index != args.length) throw new IllegalArgumentException("SQL 参数数量不匹配");
        return result.toString();
    }
}
