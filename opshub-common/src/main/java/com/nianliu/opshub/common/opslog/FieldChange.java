package com.nianliu.opshub.common.opslog;
public record FieldChange(String field,Object oldValue,Object newValue) {}