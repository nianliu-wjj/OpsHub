package com.nianliu.opshub.common.validation;

import com.nianliu.opshub.domain.model.exception.ParamRejectException;

import java.util.List;

/**
 * SpEL 校验编程门面（主通路的一部分，《工程实现复现手册》§4.4）。
 *
 * <p>违规即抛 {@link ParamRejectException}（全局异常处理器映射 HTTP 400）；
 * {@link #warmup} 为启动预热转发入口（幂等）。
 */
public final class SpelValidator {

    private SpelValidator() {
    }

    /**
     * 校验宿主对象，违规即抛参数拒绝异常。
     *
     * @param target        宿主对象
     * @param scene         场景码（流程码，用于错误消息定位）
     * @param requestGroups 本次请求组；空 = 全部约束执行
     */
    public static void validate(Object target, String scene, Class<?>... requestGroups) {
        List<String> violations = SpelConstraintEngine.INSTANCE.validate(target, scene, requestGroups);
        if (!violations.isEmpty()) {
            throw ParamRejectException.of(scene, String.join("; ", violations), "");
        }
    }

    /**
     * 启动预热入口转发（引擎任务扫描 + 表达式编译前移）。
     *
     * @param types 预热的请求/VO 类型
     */
    public static void warmup(Class<?>... types) {
        SpelConstraintEngine.INSTANCE.warmup(types);
    }
}
