package com.nianliu.opshub.common.validation;

import java.lang.annotation.*;
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @Repeatable(SpelAssert.List.class)
public @interface SpelAssert {
 String assertTrue(); String condition() default ""; String message() default "字段值不满足校验规则"; Class<?>[] groups() default {};
 @Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @interface List { SpelAssert[] value(); }
}
