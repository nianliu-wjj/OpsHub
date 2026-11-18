package com.nianliu.opshub.bootstrap;

import com.nianliu.opshub.api.base.result.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 数据唯一键和关联约束冲突的统一业务响应。
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@ControllerAdvice
@ResponseBody
public class RbacExceptionAdvice {
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> conflict(DataIntegrityViolationException exception) {
        return Result.fail("A0409", "名称或编码重复，或关联数据冲突");
    }
}
