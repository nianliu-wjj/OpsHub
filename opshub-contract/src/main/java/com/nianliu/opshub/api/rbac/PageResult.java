package com.nianliu.opshub.api.rbac;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public record PageResult<T>(long total, int page, int size, java.util.List<T> records) {}
