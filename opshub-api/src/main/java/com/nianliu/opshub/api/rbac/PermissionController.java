package com.nianliu.opshub.api.rbac;

import com.nianliu.opshub.api.base.result.Result;
import com.nianliu.opshub.domain.service.rbac.PermissionService;

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
public class PermissionController {
    private final PermissionService service;

    @GetMapping("/menus")
    public Result<PageResult<Map<String, Object>>> menuList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return Result.ok(service.menuList(page, size, keyword));
    }

    @GetMapping("/menus/{id}")
    public Result<Map<String, Object>> menuGet(@PathVariable long id) {
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
}
