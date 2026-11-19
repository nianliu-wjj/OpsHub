package com.nianliu.opshub.biz.impl.rbac;

import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.biz.shared.template.BizProcessExecutor;
import com.nianliu.opshub.common.opslog.OpsLog;
import com.nianliu.opshub.domain.service.rbac.PermissionService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final RbacOperations operations;
    private final BizProcessExecutor executor;

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:menu')")
    @Override
    public PageResult<Map<String, Object>> menuList(int page, int size, String keyword) {
        if (page < 1 || size < 1 || size > 100 || keyword.length() > 100)
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法");
        return executor.run(
                "RBAC_MENUS_QUERY", 1L, () -> operations.page("menus", page, size, keyword));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:menu')")
    @Override
    public Map<String, Object> menuGet(long id) {
        return executor.run("RBAC_MENUS_QUERY", 1L, () -> operations.get("menus", id));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module = "RBAC", type = "ADD", success = "menus变更成功，ID: {#_result}", fail = "menus变更失败")
    @Override
    public Long menuCreate(MenuRequest request) {
        return executor.run("RBAC_MENUS_WRITE", request, () -> operations.saveMenu(null, request));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(
            module = "RBAC",
            type = "UPDATE",
            success = "menus变更成功，ID: {#_result}",
            fail = "menus变更失败")
    @Override
    public Long menuUpdate(Long id, MenuRequest request) {
        return executor.run("RBAC_MENUS_WRITE", request, () -> operations.saveMenu(id, request));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module = "RBAC", type = "DELETE", success = "删除menus ID: {#id}", fail = "删除menus失败")
    @Override
    public void menuDelete(long id, long version) {
        executor.run(
                "RBAC_MENUS_DELETE",
                id,
                () -> {
                    operations.delete("menus", id, version);
                    return null;
                });
    }
}
