package com.nianliu.opshub.bootstrap;

import com.nianliu.opshub.api.base.result.Result;
import com.nianliu.opshub.domain.model.exception.OpsException;
import com.nianliu.opshub.domain.model.exception.ParamRejectException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@ControllerAdvice
@ResponseBody
public class RootExceptionAdvice {
    @ExceptionHandler(ParamRejectException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> parameter(ParamRejectException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(OpsException.class)
    public Result<Void> business(OpsException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.validation.BindException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> invalid(Exception e) {
        return Result.fail("A0400", "请求参数格式或校验不通过");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> missing(Exception e) {
        return Result.fail("A0404", "资源不存在");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> denied(org.springframework.security.access.AccessDeniedException e) {
        return Result.fail("A0403", "权限不足");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> unexpected(Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("系统异常@unexpected", e);
        return Result.fail("B0500", "系统繁忙，请稍后重试");
    }
}