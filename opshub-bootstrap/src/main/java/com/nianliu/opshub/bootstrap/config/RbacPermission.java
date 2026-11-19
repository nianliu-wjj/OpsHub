package com.nianliu.opshub.bootstrap.config;

import com.nianliu.opshub.domain.service.rbac.RbacService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Component("rbacPermission")
@RequiredArgsConstructor
public class RbacPermission {
    private final RbacService service;

    @Value("${opshub.security.admin.username}")
    private String bootstrapUsername;

    public boolean allowed(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (bootstrapUsername.equals(authentication.getName()))
            return authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        var account = service.credentials(authentication.getName());
        if (account == null
                || !Integer.valueOf(1).equals(account.getEnable())
                || Integer.valueOf(1).equals(account.getLocked())) return false;
        var authorities = service.authorities(account.getId());
        return permission.equals("__authenticated")
                || authorities.contains("ROLE_ADMIN")
                || authorities.contains(permission);
    }
}
