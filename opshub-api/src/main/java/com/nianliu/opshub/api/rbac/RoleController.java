package com.nianliu.opshub.api.rbac;

import com.nianliu.opshub.api.base.result.Result;
import com.nianliu.opshub.domain.service.rbac.RoleService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@RestController
@RequestMapping("/rbac")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;

    @GetMapping("/roles")
    public Result<PageResult<Map<String, Object>>> roleList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return Result.ok(service.roleList(page, size, keyword));
    }

    @GetMapping("/roles/{id}")
    public Result<Map<String, Object>> roleGet(@PathVariable long id) {
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
    public Result<Void> roleAssign(
            @PathVariable long id, @Valid @RequestBody AssignmentRequest request) {
        service.roleAssign(id, request);
        return Result.ok(null);
    }
}
