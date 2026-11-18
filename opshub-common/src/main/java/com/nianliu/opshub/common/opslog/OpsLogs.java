package com.nianliu.opshub.common.opslog;
import java.lang.annotation.*;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface OpsLogs { OpsLog[] value(); }