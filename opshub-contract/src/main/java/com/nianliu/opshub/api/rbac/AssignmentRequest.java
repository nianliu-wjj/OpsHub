package com.nianliu.opshub.api.rbac;

import jakarta.validation.constraints.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public record AssignmentRequest(@NotNull @Size(max=1000) java.util.List<@NotNull @Min(1) Long> ids, @Min(0) Long version) {}
