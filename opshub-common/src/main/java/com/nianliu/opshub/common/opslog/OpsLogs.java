package com.nianliu.opshub.common.opslog;
import java.lang.annotation.*;
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface OpsLogs { OpsLog[] value(); }