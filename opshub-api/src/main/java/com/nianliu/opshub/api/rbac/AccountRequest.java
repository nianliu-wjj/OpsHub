package com.nianliu.opshub.api.rbac;

import jakarta.validation.constraints.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public record AccountRequest(@NotBlank @Size(max=64) String username, @Size(min=10,max=72) String password, @Email @Size(max=100) String email, @Size(max=200) String realName, @NotNull @Min(0) @Max(1) Integer enable, @NotNull @Min(0) @Max(1) Integer locked, @Min(0) Long version) {}
