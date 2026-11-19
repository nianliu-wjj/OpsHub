package com.nianliu.opshub.infra.dal.dao.rbac;

import com.nianliu.opshub.api.rbac.PageResult;
import com.nianliu.opshub.domain.model.rbac.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Repository
@RequiredArgsConstructor
public class RbacDao {
    private final MybatisSqlExecutor sqlExecutor;
    private static final Map<String, String> TABLES =
            Map.of(
                    "accounts",
                    "sys_account",
                    "roles",
                    "sys_role",
                    "menus",
                    "sys_menu",
                    "login-logs",
                    "sys_login_log");
    private static final Map<String, String> COLUMNS =
            Map.of(
                    "accounts",
                            "id,username,icon,email,type,real_name AS realName,remark,create_time"
                                    + " AS createTime,update_time AS updateTime,login_time AS"
                                    + " loginTime,enable,`lock` AS locked,version",
                    "roles",
                            "id,name,code,description,create_time AS"
                                    + " createTime,status,sort,version",
                    "menus",
                            "id,parent_id AS"
                                + " parentId,icon,name,code,url,method,type,sort,redirect,hidden,description,create_time"
                                + " AS createTime,version",
                    "login-logs",
                            "id,created_time AS createdTime,account,ip,user_agent AS"
                                    + " userAgent,status,reason");

    public <T> PageResult<T> page(
            String resource, Class<T> type, int page, int size, String keyword) {
        String table = TABLES.get(resource);
        if (table == null) throw new IllegalArgumentException("非法资源");
        String field =
                resource.equals("accounts")
                        ? "username"
                        : resource.equals("login-logs") ? "account" : "name";
        String where =
                " WHERE "
                        + (resource.equals("login-logs") ? "" : "is_deleted=0 AND ")
                        + field
                        + " LIKE ?";
        String term = "%" + keyword + "%";
        Long total =
                sqlExecutor.queryForObject(
                        "SELECT COUNT(*) FROM " + table + where, Long.class, term);
        var rows =
                sqlExecutor.queryForList(
                        "SELECT "
                                + COLUMNS.get(resource)
                                + " FROM "
                                + table
                                + where
                                + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        type,
                        term,
                        size,
                        (long) (page - 1) * size);
        return new PageResult<>(total == null ? 0 : total, page, size, rows);
    }

    public <T> T get(String resource, Class<T> type, long id) {
        var rows =
                sqlExecutor.queryForList(
                        "SELECT "
                                + COLUMNS.get(resource)
                                + " FROM "
                                + TABLES.get(resource)
                                + " WHERE "
                                + (resource.equals("login-logs") ? "" : "is_deleted=0 AND ")
                                + "id=?",
                        type,
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public UserCredential credentials(String username) {
        var rows =
                sqlExecutor.queryForList(
                        "SELECT id,username,password,enable,`lock` AS locked FROM sys_account WHERE"
                                + " is_deleted=0 AND username=?",
                        UserCredential.class,
                        username);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long version() {
        return Objects.requireNonNull(
                sqlExecutor.queryForObject(
                        "SELECT version FROM sys_rbac_version WHERE id=1", Long.class));
    }

    public void changed() {
        if (sqlExecutor.update("UPDATE sys_rbac_version SET version=version+1 WHERE id=1") != 1)
            throw new IllegalStateException("RBAC版本行不存在");
    }

    public List<String> authorities(long id) {
        List<String> roles =
                sqlExecutor.queryForList(
                        "SELECT DISTINCT CONCAT('ROLE_',UPPER(r.code)) AS authority FROM sys_role r"
                            + " JOIN sys_account_role ar ON ar.role_id=r.id WHERE ar.account_id=?"
                            + " AND r.status=1 AND r.is_deleted=0",
                        String.class,
                        id);
        List<String> permissions =
                sqlExecutor.queryForList(
                        "SELECT DISTINCT m.code FROM sys_menu m JOIN sys_role_menu rm ON"
                            + " rm.menu_id=m.id JOIN sys_role r ON r.id=rm.role_id JOIN"
                            + " sys_account_role ar ON ar.role_id=r.id WHERE ar.account_id=? AND"
                            + " r.status=1 AND r.is_deleted=0 AND m.is_deleted=0",
                        String.class,
                        id);
        var result = new ArrayList<>(roles);
        result.addAll(permissions);
        return result;
    }

    public List<Permission> grantedMenus(long id) {
        return sqlExecutor.queryForList(
                "SELECT DISTINCT m.id,m.parent_id AS"
                    + " parentId,m.name,m.code,m.url,m.type,m.sort,m.hidden FROM sys_menu m JOIN"
                    + " sys_role_menu rm ON rm.menu_id=m.id JOIN sys_role r ON r.id=rm.role_id JOIN"
                    + " sys_account_role ar ON ar.role_id=r.id WHERE ar.account_id=? AND r.status=1"
                    + " AND r.is_deleted=0 AND m.is_deleted=0 ORDER BY m.sort,m.id",
                Permission.class,
                id);
    }

    public List<Long> assignments(String resource, long id) {
        return sqlExecutor.queryForList(
                resource.equals("accounts")
                        ? "SELECT role_id FROM sys_account_role WHERE account_id=?"
                        : "SELECT menu_id FROM sys_role_menu WHERE role_id=?",
                Long.class,
                id);
    }

    public long insert(String table, Map<String, Object> values) {
        var keys = new ArrayList<>(values.keySet());
        return sqlExecutor.insert(
                "INSERT INTO "
                        + table
                        + " ("
                        + String.join(",", keys)
                        + ") VALUES ("
                        + String.join(",", Collections.nCopies(keys.size(), "?"))
                        + ")",
                keys.stream().map(values::get).toArray());
    }

    public void update(String table, long id, long expectedVersion, Map<String, Object> values) {
        var args = new ArrayList<>(values.values());
        args.add(id);
        args.add(expectedVersion);
        if (sqlExecutor.update(
                        "UPDATE "
                                + table
                                + " SET "
                                + String.join(
                                        ",", values.keySet().stream().map(k -> k + "=?").toList())
                                + ",version=version+1 WHERE id=? AND version=? AND is_deleted=0",
                        args.toArray())
                != 1)
            throw com.nianliu.opshub.domain.model.exception.OpsException.rejected(
                    "A0409", "数据已修改，请刷新后重试");
    }

    public void delete(String resource, long id, long expectedVersion) {
        if (resource.equals("accounts"))
            sqlExecutor.update("DELETE FROM sys_account_role WHERE account_id=?", id);
        if (resource.equals("roles")) {
            sqlExecutor.update("DELETE FROM sys_account_role WHERE role_id=?", id);
            sqlExecutor.update("DELETE FROM sys_role_menu WHERE role_id=?", id);
        }
        if (resource.equals("menus"))
            sqlExecutor.update("DELETE FROM sys_role_menu WHERE menu_id=?", id);
        if (sqlExecutor.update(
                        "UPDATE "
                                + TABLES.get(resource)
                                + " SET is_deleted=1,version=version+1 WHERE id=? AND version=? AND"
                                + " is_deleted=0",
                        id,
                        expectedVersion)
                != 1)
            throw com.nianliu.opshub.domain.model.exception.OpsException.rejected(
                    "A0409", "数据已修改，请刷新后重试");
    }

    public void replaceAssignments(String resource, long id, List<Long> ids) {
        String table = resource.equals("accounts") ? "sys_account_role" : "sys_role_menu";
        String owner = resource.equals("accounts") ? "account_id" : "role_id",
                target = resource.equals("accounts") ? "role_id" : "menu_id";
        sqlExecutor.update("DELETE FROM " + table + " WHERE " + owner + "=?", id);
        for (Long targetId : new LinkedHashSet<>(ids))
            sqlExecutor.update(
                    "INSERT INTO " + table + " (" + owner + "," + target + ") VALUES (?,?)",
                    id,
                    targetId);
    }

    public void lockGraph() {
        sqlExecutor.queryForObject(
                "SELECT version FROM sys_rbac_version WHERE id=1 FOR UPDATE", Long.class);
    }

    public void checkAndAdvance(String resource, long id, long version) {
        if (sqlExecutor.update(
                        "UPDATE "
                                + TABLES.get(resource)
                                + " SET version=version+1 WHERE id=? AND version=? AND"
                                + " is_deleted=0",
                        id,
                        version)
                != 1)
            throw com.nianliu.opshub.domain.model.exception.OpsException.rejected(
                    "A0409", "数据已修改，请刷新后重试");
    }

    public List<Long> childMenus(long id) {
        return sqlExecutor.queryForList(
                "SELECT id FROM sys_menu WHERE is_deleted=0 AND parent_id=?", Long.class, id);
    }

    public void loginLog(String username, String ip, String agent, String status, String reason) {
        sqlExecutor.update(
                "INSERT INTO sys_login_log (created_time,account,ip,user_agent,status,reason)"
                        + " VALUES (CURRENT_TIMESTAMP,?,?,?,?,?)",
                username,
                ip,
                agent,
                status,
                reason);
    }
}
