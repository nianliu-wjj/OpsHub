package com.nianliu.opshub.common.error;

/**
 * OpsHub 分级错误码常量表（编码规范 §8 统一响应）。
 *
 * <p>口径（《工程实现复现手册》§8.1）：
 * <ul>
 *     <li>A 类：用户侧可感知，文案可直接展示；</li>
 *     <li>B 类：系统侧故障，对外统一兜底文案，明细仅服务端日志。</li>
 * </ul>
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public final class OpsErrorCodes {

    private OpsErrorCodes() {
    }

    /** 参数校验未通过（HTTP 400） */
    public static final String BAD_REQUEST = "A0400";

    /** 未授权 / 登录态失效 */
    public static final String UNAUTHORIZED = "A0301";

    /** 资源不存在（业务语义，走 HTTP 200 + 失败信封） */
    public static final String NOT_FOUND = "A0404";

    /** 唯一键冲突 */
    public static final String DUPLICATE_KEY = "A0409";

    /** 业务规则拒绝 / 降级熔断 */
    public static final String REJECTED = "A0500";

    /** 系统侧兜底错误码（HTTP 500，对外统一"系统繁忙"） */
    public static final String SYSTEM_ERROR = "B0001";
}
