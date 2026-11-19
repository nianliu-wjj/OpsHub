package com.nianliu.opshub.biz.impl.rbac;

import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.biz.shared.template.BizProcessExecutor;
import com.nianliu.opshub.domain.model.rbac.*;
import com.nianliu.opshub.domain.service.rbac.RbacService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {
    private final RbacOperations operations;
    private final BizProcessExecutor executor;
    private final PasswordEncoder encoder;

    @Value("${opshub.security.admin.username}")
    private String bootstrapUsername;

    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:logging:login')")
    @Override
    public PageResult<LoginLog> loginLogs(int page, int size, String keyword) {
        if (page < 1 || size < 1 || size > 100 || keyword.length() > 100)
            throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法");
        return executor.run(
                "RBAC_LOGIN_LOG_QUERY",
                1L,
                () -> operations.page("login-logs", LoginLog.class, page, size, keyword));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, '__authenticated')")
    @Override
    public List<String> currentPermissions(String username) {
        var account = operations.credentials(username);
        if (account == null && bootstrapUsername.equals(username)) return List.of("ROLE_ADMIN");
        if (account == null)
            throw new org.springframework.security.access.AccessDeniedException("账号不存在");
        return executor.run(
                "RBAC_CURRENT_PERMISSIONS", 1L, () -> operations.authorities(account.getId()));
    }

    @PreAuthorize("@rbacPermission.allowed(authentication, '__authenticated')")
    @Override
    public List<Permission> currentMenus(String username) {
        var account = operations.credentials(username);
        if (account == null && bootstrapUsername.equals(username)) return List.of();
        if (account == null)
            throw new org.springframework.security.access.AccessDeniedException("账号不存在");
        return executor.run("RBAC_CURRENT_MENUS", 1L, () -> operations.menus(account.getId()));
    }

    @Override
    public UserCredential credentials(String username) {
        return operations.credentials(username);
    }

    @Override
    public List<String> authorities(long id) {
        return operations.authorities(id);
    }

    @Override
    public List<Permission> menus(long id) {
        return operations.menus(id);
    }
}
