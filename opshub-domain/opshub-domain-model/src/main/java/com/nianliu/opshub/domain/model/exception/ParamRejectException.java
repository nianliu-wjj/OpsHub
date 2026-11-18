package com.nianliu.opshub.domain.model.exception;

/**
 * 参数校验拒绝异常（统一接入模板第②步 / SpEL 校验引擎产出）。
 *
 * <p>由全局异常处理器映射为 HTTP 400 + fail(A0400) 信封 ——
 * 比 {@link OpsException} 的 HTTP 200 更具体的双轨口径（《工程实现复现手册》§9.3）。
 */
public class ParamRejectException extends OpsException {

    private static final long serialVersionUID = 1L;

    public ParamRejectException(String message) {
        super("A0400", message);
    }

    /**
     * @param scene   场景码（流程码，用于错误消息定位，如 USER_CREATE）
     * @param subject 校验对象（字段名 / 字段路径）
     * @param message 具体违规消息
     */
    public static ParamRejectException of(String scene, String subject, String message) {
        return new ParamRejectException("SpEL 校验未通过@" + scene + " " + subject + ": " + message);
    }
}
