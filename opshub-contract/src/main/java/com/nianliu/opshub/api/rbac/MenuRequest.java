package com.nianliu.opshub.api.rbac;

import jakarta.validation.constraints.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public record MenuRequest(@NotNull @Min(0) Long parentId, @NotBlank @Size(max=100) String name, @NotBlank @Size(max=255) String code, @Size(max=255) String url, @Pattern(regexp="GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method, @NotNull @Min(1) @Max(3) Integer type, Integer sort, @Min(0) @Max(1) Integer hidden, @Min(0) Long version) {}
