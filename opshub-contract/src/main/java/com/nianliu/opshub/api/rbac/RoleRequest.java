package com.nianliu.opshub.api.rbac;

import jakarta.validation.constraints.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public record RoleRequest(@NotBlank @Size(max=100) String name, @NotBlank @Pattern(regexp="[A-Za-z0-9_:-]+") @Size(max=100) String code, @Size(max=500) String description, @NotNull @Min(0) @Max(1) Integer status, Integer sort, @Min(0) Long version) {}
