package com.nianliu.opshub.common.validation;

import jakarta.validation.*;
import com.nianliu.opshub.domain.model.exception.ParamRejectException;
public final class BeanValidator {
 private BeanValidator() {}
 private static final class Holder { static final ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory(); static final Validator VALIDATOR=FACTORY.getValidator(); }
 public static void warmup() { Holder.VALIDATOR.getConstraintsForClass(Object.class); }
 public static void validate(Object request,String scene,Class<?>... groups) {
  if(request==null) throw new ParamRejectException("请求不能为空@"+scene);
  var violations=Holder.VALIDATOR.validate(request,groups);
  if(!violations.isEmpty()) throw new ParamRejectException("参数校验未通过@"+scene+" "+violations.stream().map(v->v.getPropertyPath()+": "+v.getMessage()).sorted().collect(java.util.stream.Collectors.joining("; ")));
 }
}
