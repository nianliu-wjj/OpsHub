package com.nianliu.opshub.infra.dal.repository.rbac;

import com.nianliu.opshub.api.rbac.PageResult;
import com.nianliu.opshub.infra.dal.dao.rbac.RbacDao;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * RBAC 仓储，数据查询与持久化委托 DAO。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Repository
@RequiredArgsConstructor
public class RbacRepository {
    private final RbacDao dao;

    public PageResult<Map<String, Object>> page(
            String resource, int page, int size, String keyword) {
        return dao.page(resource, page, size, keyword);
    }

    public Map<String, Object> get(String resource, long id) {
        return dao.get(resource, id);
    }

    public Map<String, Object> credentials(String username) {
        return dao.credentials(username);
    }

    public long version() {
        return dao.version();
    }

    public void changed() {
        dao.changed();
    }

    public List<String> authorities(long id) {
        return dao.authorities(id);
    }

    public List<Map<String, Object>> grantedMenus(long id) {
        return dao.grantedMenus(id);
    }

    public List<Long> assignments(String resource, long id) {
        return dao.assignments(resource, id);
    }

    public long insert(String table, Map<String, Object> values) {
        return dao.insert(table, values);
    }

    public void update(String table, long id, long expectedVersion, Map<String, Object> values) {
        dao.update(table, id, expectedVersion, values);
    }

    public void delete(String resource, long id, long expectedVersion) {
        dao.delete(resource, id, expectedVersion);
    }

    public void replaceAssignments(String resource, long id, List<Long> ids) {
        dao.replaceAssignments(resource, id, ids);
    }

    public void lockGraph() {
        dao.lockGraph();
    }

    public void checkAndAdvance(String resource, long id, long version) {
        dao.checkAndAdvance(resource, id, version);
    }

    public List<Long> childMenus(long id) {
        return dao.childMenus(id);
    }

    public void loginLog(String username, String ip, String agent, String status, String reason) {
        dao.loginLog(username, ip, agent, status, reason);
    }
}
