package com.nianliu.opshub.api.rbac;

import com.nianliu.opshub.api.base.result.Result;
import com.nianliu.opshub.domain.model.rbac.*;
import com.nianliu.opshub.domain.service.rbac.RbacService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * RBAC HTTP 接口，仅负责请求绑定与统一响应。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@RestController
@RequestMapping("/rbac")
@RequiredArgsConstructor
public class RbacController {
    private final RbacService service;

    @GetMapping("/login-logs")
    public Result<PageResult<LoginLog>> loginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return Result.ok(service.loginLogs(page, size, keyword));
    }

    @GetMapping("/me/permissions")
    public Result<List<String>> currentPermissions(java.security.Principal principal) {
        return Result.ok(service.currentPermissions(principal.getName()));
    }

    @GetMapping("/me/menus")
    public Result<List<Permission>> currentMenus(java.security.Principal principal) {
        return Result.ok(service.currentMenus(principal.getName()));
    }
}
