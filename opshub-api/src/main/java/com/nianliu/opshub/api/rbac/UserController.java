package com.nianliu.opshub.api.rbac;

import com.nianliu.opshub.api.base.result.Result;
import com.nianliu.opshub.domain.model.rbac.*;
import com.nianliu.opshub.domain.service.rbac.UserService;

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
public class UserController {
    private final UserService service;

    @GetMapping("/accounts")
    public Result<PageResult<User>> accountList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return Result.ok(service.accountList(page, size, keyword));
    }

    @GetMapping("/accounts/{id}")
    public Result<User> accountGet(@PathVariable long id) {
        return Result.ok(service.accountGet(id));
    }

    @PostMapping("/accounts")
    public Result<Long> accountCreate(@Valid @RequestBody AccountRequest request) {
        return Result.ok(service.accountCreate(request));
    }

    @PutMapping("/accounts/{id}")
    public Result<Long> accountUpdate(
            @PathVariable Long id, @Valid @RequestBody AccountRequest request) {
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
    public Result<Void> accountAssign(
            @PathVariable long id, @Valid @RequestBody AssignmentRequest request) {
        service.accountAssign(id, request);
        return Result.ok(null);
    }
}
