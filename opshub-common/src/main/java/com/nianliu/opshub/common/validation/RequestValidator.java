package com.nianliu.opshub.common.validation;

public final class RequestValidator {
 private RequestValidator() {}
 public static void validate(Object request,String scene,Class<?>... groups) { BeanValidator.validate(request,scene,groups); SpelValidator.validate(request,scene,groups); }
 public static void warmup(Class<?>... types) { BeanValidator.warmup(); SpelValidator.warmup(types); }
}
