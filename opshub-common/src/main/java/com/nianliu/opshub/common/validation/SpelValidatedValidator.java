package com.nianliu.opshub.common.validation;

import jakarta.validation.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public class SpelValidatedValidator implements ConstraintValidator<SpelValidated,Object> {
 private SpelValidated annotation;
 public void initialize(SpelValidated annotation) { this.annotation=annotation; }
 public boolean isValid(Object value,ConstraintValidatorContext context) {
  if(value==null) return true;
  if(!annotation.condition().isBlank()) { try { if(!Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(annotation.condition()).getValue(new StandardEvaluationContext(value),Boolean.class))) return true; } catch(Exception e) { return true; } }
  var errors=SpelConstraintEngine.INSTANCE.validate(value,"JSR303",annotation.groups());
  if(errors.isEmpty()) return true;
  context.disableDefaultConstraintViolation();
  for(String error:errors) context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
  return false;
 }
}
