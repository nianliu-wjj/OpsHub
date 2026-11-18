package com.nianliu.opshub.api.rbac;

import com.nianliu.opshub.api.base.result.Result;
import com.nianliu.opshub.domain.service.rbac.RbacService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
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

    @GetMapping("/accounts")
    public Result<PageResult<Map<String,Object>>> accountList(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String keyword) {
        return Result.ok(service.accountList(page, size, keyword));
    }

    @GetMapping("/accounts/{id}")
    public Result<Map<String,Object>> accountGet(@PathVariable long id) {
        return Result.ok(service.accountGet(id));
    }

    @PostMapping("/accounts")
    public Result<Long> accountCreate(@Valid @RequestBody AccountRequest request) {
        return Result.ok(service.accountCreate(request));
    }

    @PutMapping("/accounts/{id}")
    public Result<Long> accountUpdate(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return Result.ok(service.accountUpdate(id, request));
    }

    @DeleteMapping("/accounts/{id}")
    public Result<Void> accountDelete(@PathVariable long id, @RequestParam long version) {
        service.accountDelete(id, version);
        return Result.ok(null);
    }

    @GetMapping("/accounts/{id}/roles")
    public Result<List<Long>> accountAssignments(@PathVariable long id) {
        return Result.ok(service.accountAssignments(id));
    }

    @PutMapping("/accounts/{id}/roles")
    public Result<Void> accountAssign(@PathVariable long id,@Valid @RequestBody AssignmentRequest request) {
        service.accountAssign(id, request);
        return Result.ok(null);
    }

    @GetMapping("/roles")
    public Result<PageResult<Map<String,Object>>> roleList(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String keyword) {
        return Result.ok(service.roleList(page, size, keyword));
    }

    @GetMapping("/roles/{id}")
    public Result<Map<String,Object>> roleGet(@PathVariable long id) {
        return Result.ok(service.roleGet(id));
    }

    @PostMapping("/roles")
    public Result<Long> roleCreate(@Valid @RequestBody RoleRequest request) {
        return Result.ok(service.roleCreate(request));
    }

    @PutMapping("/roles/{id}")
    public Result<Long> roleUpdate(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return Result.ok(service.roleUpdate(id, request));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> roleDelete(@PathVariable long id, @RequestParam long version) {
        service.roleDelete(id, version);
        return Result.ok(null);
    }

    @GetMapping("/roles/{id}/menus")
    public Result<List<Long>> roleAssignments(@PathVariable long id) {
        return Result.ok(service.roleAssignments(id));
    }

    @PutMapping("/roles/{id}/menus")
    public Result<Void> roleAssign(@PathVariable long id,@Valid @RequestBody AssignmentRequest request) {
        service.roleAssign(id, request);
        return Result.ok(null);
    }

    @GetMapping("/menus")
    public Result<PageResult<Map<String,Object>>> menuList(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String keyword) {
        return Result.ok(service.menuList(page, size, keyword));
    }

    @GetMapping("/menus/{id}")
    public Result<Map<String,Object>> menuGet(@PathVariable long id) {
        return Result.ok(service.menuGet(id));
    }

    @PostMapping("/menus")
    public Result<Long> menuCreate(@Valid @RequestBody MenuRequest request) {
        return Result.ok(service.menuCreate(request));
    }

    @PutMapping("/menus/{id}")
    public Result<Long> menuUpdate(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        return Result.ok(service.menuUpdate(id, request));
    }

    @DeleteMapping("/menus/{id}")
    public Result<Void> menuDelete(@PathVariable long id, @RequestParam long version) {
        service.menuDelete(id, version);
        return Result.ok(null);
    }

    @GetMapping("/login-logs")
    public Result<PageResult<Map<String,Object>>> loginLogs(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword) {
        return Result.ok(service.loginLogs(page, size, keyword));
    }

    @GetMapping("/me/permissions")
    public Result<List<String>> currentPermissions(java.security.Principal principal) {
        return Result.ok(service.currentPermissions(principal.getName()));
    }

    @GetMapping("/me/menus")
    public Result<List<Map<String,Object>>> currentMenus(java.security.Principal principal) {
        return Result.ok(service.currentMenus(principal.getName()));
    }
}
