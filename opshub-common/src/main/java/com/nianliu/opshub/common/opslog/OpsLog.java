package com.nianliu.opshub.common.opslog;
import java.lang.annotation.*;
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @Repeatable(OpsLogs.class)
public @interface OpsLog {
 String success(); String fail() default ""; String module() default ""; String type() default "";
 String operator() default ""; String bizNo() default ""; String detail() default ""; String condition() default "";
 String before() default ""; String diff() default "";
}