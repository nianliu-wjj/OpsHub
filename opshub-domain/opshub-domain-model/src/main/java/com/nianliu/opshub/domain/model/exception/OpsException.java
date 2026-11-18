package com.nianliu.opshub.domain.model.exception;

/**
 * OpsHub 业务异常根（编码规范 §8 统一异常处理）。
 *
 * <p>业务预期拒绝（状态不允许 / 资源不存在 / 业务规则拒绝），非系统故障：
 *
 * <ul>
 *     <li>继承 {@link RuntimeException}：经 Spring 事务代理天然触发回滚；</li>
 *     <li>携带分级错误码：由全局异常处理器转 HTTP 200 + 失败信封（业务失败是语义分支，
 *         不是服务故障，见《工程实现复现手册》§8.3 双轨口径）。</li>
 * </ul>
 *
 * <p>子类分工：{@link ParamRejectException} 参数校验拒绝（映射 HTTP 400）、
 * {@link DuplicateKeyException} 唯一键冲突（模板幂等兜底触发器）。
 */
public class OpsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 分级错误码（A 类用户侧 / B 类系统侧，常量取 OpsErrorCodes） */
    private final String code;

    public OpsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public OpsException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 构造业务拒绝异常的标准入口。
     *
     * @param code    分级错误码
     * @param message 人话文案，可直接展示
     * @return 业务异常实例
     */
    public static OpsException rejected(String code, String message) {
        return new OpsException(code, message);
    }

    public String getCode() {
        return code;
    }
}
