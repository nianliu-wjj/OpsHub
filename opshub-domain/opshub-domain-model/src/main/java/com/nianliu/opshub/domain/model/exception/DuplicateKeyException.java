package com.nianliu.opshub.domain.model.exception;

/**
 * 唯一键冲突异常（统一接入模板第⑥步幂等兜底的触发器）。
 *
 * <p>模板 {@code doInProcess} 捕获本异常后回调 {@code onDuplicate}，
 * 命中幂等则返回既有结果，未处理则原样上抛走失败信封兜底。
 */
public class DuplicateKeyException extends OpsException {

    private static final long serialVersionUID = 1L;

    public DuplicateKeyException(String message) {
        super("A0409", message);
    }

    public DuplicateKeyException(String message, Throwable cause) {
        super("A0409", message, cause);
    }
}
