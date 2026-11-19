package com.nianliu.opshub.biz.impl.rbac;

import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.domain.model.exception.*;
import com.nianliu.opshub.domain.model.rbac.*;
import com.nianliu.opshub.infra.dal.repository.rbac.RbacRepository;
import com.nianliu.opshub.infra.integration.cache.RbacCache;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class RbacOperations {
    private final RbacRepository repository;
    private final RbacCache cache;

    public <T> PageResult<T> page(
            String resource, Class<T> type, int page, int size, String keyword) {
        return repository.page(resource, type, page, size, keyword);
    }

    public <T> T get(String resource, Class<T> type, long id) {
        var result = repository.get(resource, type, id);
        if (result == null) throw OpsException.rejected("A0404", "资源不存在");
        return result;
    }

    public UserCredential credentials(String username) {
        return repository.credentials(username);
    }

    public List<String> authorities(long id) {
        long version = repository.version();
        String json =
                cache.get(
                        "opshub:rbac:" + version + ":authorities:" + id,
                        String.class,
                        () -> com.alibaba.fastjson2.JSON.toJSONString(repository.authorities(id)));
        return com.alibaba.fastjson2.JSON.parseArray(json, String.class);
    }

    public List<Permission> menus(long id) {
        long version = repository.version();
        String json =
                cache.get(
                        "opshub:rbac:" + version + ":menus:" + id,
                        String.class,
                        () -> com.alibaba.fastjson2.JSON.toJSONString(repository.grantedMenus(id)));
        return com.alibaba.fastjson2.JSON.parseObject(
                json, new com.alibaba.fastjson2.TypeReference<List<Permission>>() {});
    }

    public List<Long> assignments(String resource, long id) {
        get(resource, id);
        return repository.assignments(resource, id);
    }

    @Transactional
    public long saveAccount(Long id, AccountRequest request, String encodedPassword) {
        repository.lockGraph();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("username", request.username());
        values.put("email", request.email());
        values.put("real_name", request.realName());
        values.put("enable", request.enable());
        values.put("`lock`", request.locked());
        if (encodedPassword != null) values.put("password", encodedPassword);
        if (id == null && encodedPassword == null) throw new ParamRejectException("新增账号必须提供密码");
        return save("accounts", "sys_account", id, request.version(), values);
    }

    @Transactional
    public long saveRole(Long id, RoleRequest r) {
        repository.lockGraph();
        var v = new LinkedHashMap<String, Object>();
        v.put("name", r.name());
        v.put("code", r.code());
        v.put("description", r.description());
        v.put("status", r.status());
        v.put("sort", r.sort());
        return save("roles", "sys_role", id, r.version(), v);
    }

    @Transactional
    public long saveMenu(Long id, MenuRequest r) {
        repository.lockGraph();
        if (r.parentId() != 0) {
            get("menus", r.parentId());
            Set<Long> visited = new HashSet<>();
            long current = r.parentId();
            while (current != 0) {
                if (Objects.equals(id, current) || !visited.add(current))
                    throw new ParamRejectException("菜单不能形成循环");
                current = get("menus", Permission.class, current).getParentId();
            }
        }
        var v = new LinkedHashMap<String, Object>();
        v.put("parent_id", r.parentId());
        v.put("name", r.name());
        v.put("code", r.code());
        v.put("url", r.url());
        v.put("method", r.method());
        v.put("type", r.type());
        v.put("sort", r.sort());
        v.put("hidden", r.hidden());
        return save("menus", "sys_menu", id, r.version(), v);
    }

    private long save(
            String resource,
            String table,
            Long id,
            Long expectedVersion,
            Map<String, Object> values) {
        long result;
        if (id == null) {
            if (expectedVersion != null && expectedVersion != 0)
                throw new ParamRejectException("新增记录version必须为0或不填写");
            values.put("create_time", java.time.LocalDateTime.now());
            result = repository.insert(table, values);
        } else {
            get(resource, id);
            repository.update(table, id, requiredVersion(expectedVersion), values);
            result = id;
        }
        repository.changed();
        return result;
    }

    @Transactional
    public void delete(String resource, long id, long version) {
        repository.lockGraph();
        get(resource, id);
        if (resource.equals("menus") && !repository.childMenus(id).isEmpty())
            throw OpsException.rejected("A0409", "请先删除子菜单");
        repository.delete(resource, id, version);
        repository.changed();
    }

    @Transactional
    public void assign(String resource, long id, List<Long> targets, Long version) {
        repository.lockGraph();
        get(resource, id);
        for (Long target : targets) get(resource.equals("accounts") ? "roles" : "menus", target);
        repository.checkAndAdvance(resource, id, requiredVersion(version));
        repository.replaceAssignments(resource, id, targets);
        repository.changed();
    }

    private long requiredVersion(Long version) {
        if (version == null || version < 0) throw new ParamRejectException("修改或授权替换必须提供当前version");
        return version;
    }

    private Object get(String resource, long id) {
        return switch (resource) {
            case "accounts" -> get(resource, User.class, id);
            case "roles" -> get(resource, Role.class, id);
            case "menus" -> get(resource, Permission.class, id);
            default -> throw new IllegalArgumentException("非法资源");
        };
    }
}
