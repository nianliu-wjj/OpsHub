package com.nianliu.opshub.common.validation;

import com.alibaba.fastjson2.JSON;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL 约束执行引擎（唯一内核，《工程实现复现手册》§4.4）。
 *
 * <p>两条触发通路（模板第②步显式调用 / jakarta 薄壳）共享本内核，
 * 保证任务缓存、表达式缓存与求值口径完全一致。
 *
 * <p>健壮性三防线（实测产出，防御"一条校验链被一个不可扫类型炸掉"）：
 * <ol>
 *     <li><b>平台类型短路</b>：java.*、jakarta.*、基本类型直接空任务 ——
 *         JDK 强封装禁止对 java.base 深度反射（demo 的 Long 型请求曾在此 500）；</li>
 *     <li><b>扫描整体容错</b>：单类扫描异常返回空任务并 warn，不外溢；</li>
 *     <li><b>单字段反射受限跳过</b>：单字段读取失败跳过该字段，不中断整链。</li>
 * </ol>
 *
 * <p><b>安全红线</b>（SpEL 注入治理）：禁止开启 SpEL 编译模式、禁止运行时拼接表达式串 ——
 * 全部表达式必须是编译期字面量，架构上不存在注入入口。
 */
public final class SpelConstraintEngine {

    private static final Logger log = LoggerFactory.getLogger(SpelConstraintEngine.class);

    /** 单例内核：两条通路共享同一份缓存 */
    public static final SpelConstraintEngine INSTANCE = new SpelConstraintEngine();

    /** SpEL 解析器（线程安全）；不开启编译模式 —— 安全红线 */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /** 任务缓存：Class → 约束任务列表（启动预热命中后首校验不再反射扫描） */
    private final Map<Class<?>, List<ConstraintTask>> taskCache = new ConcurrentHashMap<>(64);

    /** 表达式缓存：编译期字面量一次编译全周期复用 */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(64);

    private SpelConstraintEngine() {
    }

    // ==================== 对外主流程 ====================

    /**
     * 校验宿主对象（模板第②步主通路 / 薄壳共用）。
     *
     * @param target   宿主对象
     * @param scene    场景码（流程码，用于错误消息定位）
     * @param requestGroups 本次请求组；空 = 全部约束执行
     * @return 违规消息列表（[字段: 消息] 形态），空列表 = 全部通过
     */
    public List<String> validate(Object target, String scene, Class<?>... requestGroups) {
        if (target == null) {
            return Collections.emptyList();
        }
        List<ConstraintTask> tasks = tasksOf(target.getClass());
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> violations = new ArrayList<>();
        for (ConstraintTask task : tasks) {
            if (!groupMatch(task.groups(), requestGroups)) {
                continue;                                       // 分组过滤：不相交即跳过
            }
            Object violation = evaluateTask(task, target, requestGroups);
            if (violation instanceof String message) {
                violations.add(task.fieldName + ": " + message);
            }
        }
        if (!violations.isEmpty()) {
            log.warn("SpEL 校验违规@{} 共{}条: {}", scene, violations.size(), violations);
        }
        return violations;
    }

    /**
     * 启动预热入口：扫描类并预编译表达式（幂等，命中缓存即返回）。
     *
     * @param types 预热的请求/VO 类型
     */
    public void warmup(Class<?>... types) {
        if (types == null) {
            return;
        }
        for (Class<?> type : types) {
            tasksOf(type);                                      // 幂等：命中缓存即返回
        }
    }

    // ==================== 任务扫描（带三防线） ====================

    private List<ConstraintTask> tasksOf(Class<?> hostClass) {
        return taskCache.computeIfAbsent(hostClass, clazz -> {
            try {
                return scanTasks(clazz);                        // 防线②：扫描整体容错
            } catch (Throwable e) {
                log.warn("SpEL 约束任务扫描失败@{} 原因:{}, 按空任务处理", clazz.getName(), e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    private List<ConstraintTask> scanTasks(Class<?> hostClass) {
        // 防线①：平台类型短路 —— java.*、jakarta.*、基本类型直接空任务（JDK 强封装禁止深反射）
        if (hostClass.isPrimitive() || hostClass.isArray()
                || hostClass.getName().startsWith("java.")
                || hostClass.getName().startsWith("jakarta.")
                || ClassUtils.isPrimitiveWrapper(hostClass)) {
            return Collections.emptyList();
        }
        List<ConstraintTask> tasks = new ArrayList<>();
        for (Field field : hostClass.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            // SpEL 段：条件必填
            for (SpelNotBlank spelNotBlank : field.getAnnotationsByType(SpelNotBlank.class)) {
                tasks.add(new ConstraintTask(field.getName(), TaskKind.SPEL_NOT_BLANK,
                        spelNotBlank.condition(), null, spelNotBlank.message(), spelNotBlank.groups()));
            }
            // SpEL 段：布尔断言
            for (SpelAssert spelAssert : field.getAnnotationsByType(SpelAssert.class)) {
                tasks.add(new ConstraintTask(field.getName(), TaskKind.SPEL_ASSERT,
                        spelAssert.condition(), spelAssert.assertTrue(), spelAssert.message(), spelAssert.groups()));
            }
        }
        for (ConstraintTask task : tasks) {
            if (task.condition != null && !task.condition.isBlank()) expressionCache.computeIfAbsent(task.condition, parser::parseExpression);
            if (task.assertion != null && !task.assertion.isBlank()) expressionCache.computeIfAbsent(task.assertion, parser::parseExpression);
        }
        return List.copyOf(tasks);
    }

    private Class<?>[] groupsOf(Field field, boolean jsr) {
        if (jsr) {
            NotBlank a = AnnotatedElementUtils.findMergedAnnotation(field, NotBlank.class);
            if (a != null) {
                return a.groups();
            }
            NotEmpty b = AnnotatedElementUtils.findMergedAnnotation(field, NotEmpty.class);
            if (b != null) {
                return b.groups();
            }
            NotNull c = AnnotatedElementUtils.findMergedAnnotation(field, NotNull.class);
            return c != null ? c.groups() : new Class<?>[0];
        }
        return new Class<?>[0];
    }

    // ==================== 单任务求值 ====================

    private Object evaluateTask(ConstraintTask task, Object target, Class<?>[] requestGroups) {
        try {
            Object fieldValue = readField(target, task.fieldName);
            switch (task.kind) {
                case JSR_REQUIRED -> {
                    if (isEmpty(fieldValue)) {
                        return task.message;
                    }
                    return null;
                }
                case SPEL_NOT_BLANK -> {
                    if (!conditionPass(task.condition, target)) {
                        return null;                            // 条件不成立 → 跳过
                    }
                    if (fieldValue == null || (fieldValue instanceof String s && s.isBlank())) {
                        return task.message;
                    }
                    return null;
                }
                case SPEL_ASSERT -> {
                    if (!conditionPass(task.condition, target)) {
                        return null;
                    }
                    Boolean pass = evaluateBool(task.assertion, target);
                    // 宽容度契约：断言求值 null/异常一律判失败 —— 歪守卫比无守卫更危险
                    return Boolean.TRUE.equals(pass) ? null : task.message;
                }
                default -> {
                    return null;
                }
            }
        } catch (Throwable e) {
            // 防线③：单字段反射受限跳过，不中断整链
            log.warn("SpEL 约束任务执行受限@{}#{}, 跳过, 原因:{}", target.getClass().getSimpleName(), task.fieldName, e.getMessage());
            return null;
        }
    }

    private Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        ReflectionUtils.makeAccessible(field);
        return field.get(target);
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof java.util.Collection<?> c) {
            return c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    // ==================== 求值口径 ====================

    /** condition 求值：null/异常视为不成立（保守关闭放行） */
    private boolean conditionPass(String condition, Object root) {
        if (condition == null || condition.isBlank()) {
            return true;                                        // 空 = 无条件生效
        }
        Boolean pass = evaluateBool(condition, root);
        return Boolean.TRUE.equals(pass);
    }

    /** 布尔求值：root 即 #this；异常返回 null（由调用方按宽容度契约裁决） */
    private Boolean evaluateBool(String expression, Object root) {
        try {
            Expression compiled = expressionCache.computeIfAbsent(expression,
                    expr -> parser.parseRaw(expr));             // 编译期字面量，不开启编译模式
            return compiled.getValue(evaluationContext(root), Boolean.class);
        } catch (Exception e) {
            log.warn("SpEL 表达式求值失败@{} 原因:{}", expression, e.getMessage());
            return null;
        }
    }

    private StandardEvaluationContext evaluationContext(Object root) {
        StandardEvaluationContext context = new StandardEvaluationContext(root);
        context.addPropertyAccessor(new MapAccessor());         // 支持 #this.map.key 取值
        return context;
    }

    /** 序列化留档口径：复杂对象走 fastjson2（对象哈希无留档价值） */
    static String toJsonText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof java.time.temporal.Temporal || value instanceof java.util.Date) {
            return String.valueOf(value);
        }
        return JSON.toJSONString(value);
    }

    // ==================== 内部模型 ====================

    private enum TaskKind {
        /** jakarta 原生必填段 */
        JSR_REQUIRED,
        /** SpEL 条件必填 */
        SPEL_NOT_BLANK,
        /** SpEL 布尔断言 */
        SPEL_ASSERT
    }

    private record ConstraintTask(String fieldName, TaskKind kind,
                                  String condition, String assertion,
                                  String message, Class<?>[] groups) {
    }

    private boolean groupMatch(Class<?>[] constraintGroups, Class<?>[] requestGroups) {
        if (requestGroups == null || requestGroups.length == 0) {
            return true;                                        // 请求组为空 → 全部约束执行
        }
        if (constraintGroups == null || constraintGroups.length == 0) {
            return true;                                        // 无条件约束恒执行
        }
        for (Class<?> requestGroup : requestGroups) {           // 约束组 ∩ 请求组 ≠ 空
            for (Class<?> constraintGroup : constraintGroups) {
                if (requestGroup.equals(constraintGroup)) {
                    return true;
                }
            }
        }
        return false;
    }
}
