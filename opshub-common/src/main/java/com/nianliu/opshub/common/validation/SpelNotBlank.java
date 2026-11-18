package com.nianliu.opshub.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级条件必填（"勾选 A 则 B 必填"高频场景独立成注解）——《工程实现复现手册》§4.4。
 *
 * <p>{@code condition} 为空 = 无条件必填；非空 = 条件成立时才要求非空。
 * 触发通路与 {@link SpelAssert} 相同：模板第②步显式扫描执行。
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Repeatable(SpelNotBlank.List.class)
public @interface SpelNotBlank {

    /** 开关 SpEL：空 = 无条件必填；false 跳过 */
    String condition() default "";

    /** 违规消息 */
    String message() default "该字段不能为空";

    /** 分组：空 = 全场景；非空 = 与请求组相交才校验 */
    Class<?>[] groups() default {};

    /** 可重复容器 */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface List {

        SpelNotBlank[] value();
    }
}
