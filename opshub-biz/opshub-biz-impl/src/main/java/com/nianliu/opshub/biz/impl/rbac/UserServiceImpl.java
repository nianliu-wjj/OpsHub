package com.nianliu.opshub.biz.impl.rbac;

import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.biz.shared.template.BizProcessExecutor;
import com.nianliu.opshub.common.opslog.OpsLog;
import com.nianliu.opshub.domain.service.rbac.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final RbacOperations operations;
    private final BizProcessExecutor executor;
    private final PasswordEncoder encoder;

    @org.springframework.beans.factory.annotation.Value("${opshub.security.admin.username}")
    private String bootstrapUsername;

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    @Override
    public PageResult<Map<String, Object>> accountList(int page, int size, String keyword) {
        if (page < 1 || size < 1 || size > 100 || keyword.length() > 100)
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法");
        return executor.run(
                "RBAC_ACCOUNTS_QUERY", 1L, () -> operations.page("accounts", page, size, keyword));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    @Override
    public Map<String, Object> accountGet(long id) {
        return executor.run("RBAC_ACCOUNTS_QUERY", 1L, () -> operations.get("accounts", id));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(
            module = "RBAC",
            type = "ADD",
            success = "accounts变更成功，ID: {#_result}",
            fail = "accounts变更失败")
    @Override
    public Long accountCreate(AccountRequest request) {
        if (request.password() != null
                && request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException(
                    "密码UTF-8长度不能超过72字节");
        if (bootstrapUsername.equals(request.username()))
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("初始管理员用户名保留");
        return executor.run(
                "RBAC_ACCOUNTS_WRITE",
                request,
                () ->
                        operations.saveAccount(
                                null,
                                request,
                                request.password() == null
                                        ? null
                                        : encoder.encode(request.password())));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(
            module = "RBAC",
            type = "UPDATE",
            success = "accounts变更成功，ID: {#_result}",
            fail = "accounts变更失败")
    @Override
    public Long accountUpdate(Long id, AccountRequest request) {
        if (request.password() != null
                && request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException(
                    "密码UTF-8长度不能超过72字节");
        if (bootstrapUsername.equals(request.username()))
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("初始管理员用户名保留");
        return executor.run(
                "RBAC_ACCOUNTS_WRITE",
                request,
                () ->
                        operations.saveAccount(
                                id,
                                request,
                                request.password() == null
                                        ? null
                                        : encoder.encode(request.password())));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(
            module = "RBAC",
            type = "DELETE",
            success = "删除accounts ID: {#id}",
            fail = "删除accounts失败")
    @Override
    public void accountDelete(long id, long version) {
        executor.run(
                "RBAC_ACCOUNTS_DELETE",
                id,
                () -> {
                    operations.delete("accounts", id, version);
                    return null;
                });
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    @Override
    public List<Long> accountAssignments(long id) {
        return executor.run(
                "RBAC_ASSIGNMENTS_QUERY", id, () -> operations.assignments("accounts", id));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module = "RBAC", type = "UPDATE", success = "替换accounts授权 ID: {#id}", fail = "替换授权失败")
    @Override
    public void accountAssign(long id, AssignmentRequest request) {
        executor.run(
                "RBAC_ASSIGN",
                request,
                () -> {
                    operations.assign("accounts", id, request.ids(), request.version());
                    return null;
                });
    }
}
