package com.nianliu.opshub.biz.impl.rbac;

import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.biz.shared.template.BizProcessExecutor;
import com.nianliu.opshub.common.opslog.OpsLog;
import com.nianliu.opshub.domain.model.rbac.*;
import com.nianliu.opshub.domain.service.rbac.RoleService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final RbacOperations operations;
    private final BizProcessExecutor executor;

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    @Override
    public PageResult<Role> roleList(int page, int size, String keyword) {
        if (page < 1 || size < 1 || size > 100 || keyword.length() > 100)
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法");
        return executor.run(
                "RBAC_ROLES_QUERY",
                1L,
                () -> operations.page("roles", Role.class, page, size, keyword));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    @Override
    public Role roleGet(long id) {
        return executor.run("RBAC_ROLES_QUERY", 1L, () -> operations.get("roles", Role.class, id));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module = "RBAC", type = "ADD", success = "roles变更成功，ID: {#_result}", fail = "roles变更失败")
    @Override
    public Long roleCreate(RoleRequest request) {
        return executor.run("RBAC_ROLES_WRITE", request, () -> operations.saveRole(null, request));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(
            module = "RBAC",
            type = "UPDATE",
            success = "roles变更成功，ID: {#_result}",
            fail = "roles变更失败")
    @Override
    public Long roleUpdate(Long id, RoleRequest request) {
        return executor.run("RBAC_ROLES_WRITE", request, () -> operations.saveRole(id, request));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module = "RBAC", type = "DELETE", success = "删除roles ID: {#id}", fail = "删除roles失败")
    @Override
    public void roleDelete(long id, long version) {
        executor.run(
                "RBAC_ROLES_DELETE",
                id,
                () -> {
                    operations.delete("roles", id, version);
                    return null;
                });
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    @Override
    public List<Long> roleAssignments(long id) {
        return executor.run(
                "RBAC_ASSIGNMENTS_QUERY", id, () -> operations.assignments("roles", id));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module = "RBAC", type = "UPDATE", success = "替换roles授权 ID: {#id}", fail = "替换授权失败")
    @Override
    public void roleAssign(long id, AssignmentRequest request) {
        executor.run(
                "RBAC_ASSIGN",
                request,
                () -> {
                    operations.assign("roles", id, request.ids(), request.version());
                    return null;
                });
    }
}
