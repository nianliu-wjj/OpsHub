package com.nianliu.opshub.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SpEL 校验类级【可选兼容标记，不再是触发前提】（《工程实现复现手册》§4.4）。
 *
 * <p>主通路是统一接入模板第②步的 {@code RequestValidator.validate} 显式触发 ——
 * 宿主类无需标注本注解，字段上的 {@link SpelAssert} / {@link SpelNotBlank} 即被发现执行。
 * 本注解退化为可选兼容标记：需要 jakarta 标准 ConstraintViolation 报告流的场景使用
 * （配 {@code @Valid} + {@link SpelValidatedValidator} 薄壳），与主通路共享同一执行内核。
 *
 * <p>对象级前置条件 {@code condition}：求值为 false 时整体放行（宽容度契约：null/异常视为不成立）。
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(SpelValidated.Group.class)
@Constraint(validatedBy = SpelValidatedValidator.class)
public @interface SpelValidated {

    /** 对象级前置条件 SpEL：false 全放行（root = 宿主对象） */
    String condition() default "";

    /** 违规消息 */
    String message() default "SpEL 校验未通过";

    /** 分组：与请求组相交才生效（jakarta 原生 groups 语义透传） */
    Class<?>[] groups() default {};

    /** jakarta 标准载荷 */
    Class<? extends Payload>[] payload() default {};

    /** 可重复容器 */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Group {

        SpelValidated[] value();
    }
}
