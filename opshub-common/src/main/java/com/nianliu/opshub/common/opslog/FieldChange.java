package com.nianliu.opshub.common.opslog;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public record FieldChange(String field,Object oldValue,Object newValue) {}