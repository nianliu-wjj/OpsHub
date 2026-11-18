package com.nianliu.opshub.api.base.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应信封（编码规范 §8 接口统一响应）：全部 HTTP 接口的成功与业务失败均返回本信封。
 *
 * <p>契约层纯 POJO，零框架依赖，可独立发布给调用方。
 *
 * <ul>
 *     <li>{@code success}：前端分支判定的唯一依据；</li>
 *     <li>{@code code}：成功恒 {@link #SUCCESS_CODE}，失败为 A 类（用户侧）/ B 类（系统侧）分级码；</li>
 *     <li>{@code msg}：成功 {@link #SUCCESS_MSG}；失败为人话文案，可直接展示；</li>
 *     <li>{@code data}：业务数据，失败时恒为 {@code null}。</li>
 * </ul>
 *
 * <p>实例化只允许经静态工厂，保证 code 与 success 的口径不被业务侧写歪。
 *
 * @param <T> 业务数据类型
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 成功码：恒定值，对外语义稳定（§9.2 分级码对外稳定原则） */
    public static final String SUCCESS_CODE = "00000";

    /** 成功文案 */
    public static final String SUCCESS_MSG = "OK";

    /** 前端分支判定的唯一依据 */
    private boolean success;

    /** 成功 "00000"；失败 A 类/B 类分级码 */
    private String code;

    /** 成功 "OK"；失败人话（可直接展示） */
    private String msg;

    /** 业务数据；失败时为 null */
    private T data;

    public Result() {
    }

    private Result(boolean success, String code, String msg, T data) {
        this.success = success;
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功信封。
     *
     * @param data 业务数据（可为 null）
     * @param <T>  业务数据类型
     * @return success=true, code=00000, msg=OK 的信封
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(true, SUCCESS_CODE, SUCCESS_MSG, data);
    }

    /**
     * 失败信封。
     *
     * @param code 分级错误码（A 类用户侧 / B 类系统侧）
     * @param msg  人话文案，可直接展示
     * @param <T>  业务数据类型（失败时 data 恒 null）
     * @return success=false, data=null 的信封
     */
    public static <T> Result<T> fail(String code, String msg) {
        return new Result<>(false, code, msg, null);
    }
}
