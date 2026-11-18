package com.nianliu.opshub.bootstrap.aspect;
import com.nianliu.opshub.common.opslog.*;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.Expression;
import org.springframework.aop.support.AopUtils;
import com.alibaba.fastjson2.JSON;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;
@Aspect @Component public class OpsLogAspect {
 private final IOpsLogService sink; private final IOperatorProvider operator; private final ApplicationContext beans;
 private final Map<String,Expression> cache=new ConcurrentHashMap<>(); private final SpelExpressionParser parser=new SpelExpressionParser();
 private static final Pattern PLACEHOLDER=Pattern.compile("\\{\\{([^{}]+)}}|\\{([^{}]+)}"), BEFORE=Pattern.compile("\\{\\s*(\\w+)\\s*\\{([^{}]+)}}");
 public OpsLogAspect(IOpsLogService sink,IOperatorProvider operator,ApplicationContext beans) { this.sink=sink;this.operator=operator;this.beans=beans; }
 private Object eval(String text,MethodBasedEvaluationContext ctx) { return cache.computeIfAbsent(text,parser::parseExpression).getValue(ctx); }
 private Object value(String text,MethodBasedEvaluationContext ctx) { if(text.startsWith("{")&&text.endsWith("}")) text=text.substring(1,text.length()-1); return eval(text,ctx); }
 private String render(String text,MethodBasedEvaluationContext ctx) {
  if(text.isEmpty()) return "";
  Matcher m=PLACEHOLDER.matcher(text); StringBuffer out=new StringBuffer();
  if(!m.find()) { try { return stringify(eval(text,ctx)); } catch(Exception e) { return text; } }
  m.reset(); while(m.find()) { String expression=m.group(1)!=null?m.group(1):m.group(2); String replacement; try { replacement=stringify(eval(expression,ctx)); } catch(Exception e) { replacement=expression; } m.appendReplacement(out,Matcher.quoteReplacement(replacement)); } m.appendTail(out); return out.toString();
 }
 private String stringify(Object value) { return value==null?"":value instanceof String||value instanceof Number||value instanceof Boolean?value.toString():JSON.toJSONString(value); }
 @Around("@annotation(com.nianliu.opshub.common.opslog.OpsLog) || @annotation(com.nianliu.opshub.common.opslog.OpsLogs)")
 public Object around(ProceedingJoinPoint point) throws Throwable {
  var method=AopUtils.getMostSpecificMethod(((MethodSignature)point.getSignature()).getMethod(),point.getTarget().getClass());
  var annotations=method.getAnnotationsByType(OpsLog.class); List<MethodBasedEvaluationContext> contexts=new ArrayList<>();
  for(var annotation:annotations) { var ctx=new MethodBasedEvaluationContext(null,method,point.getArgs(),new DefaultParameterNameDiscoverer()); contexts.add(ctx);
   try { if(!annotation.before().isEmpty()) { Matcher match=BEFORE.matcher(annotation.before()); Object snapshot=match.matches()?beans.getBean(match.group(1),OpsLogBeforeFunction.class).find(eval(match.group(2),ctx)):value(annotation.before(),ctx); ctx.setVariable("_before",snapshot); } } catch(Exception e) { warn(e); }
  }
  long start=System.nanoTime(); Object result=null; Throwable failure=null;
  try { result=point.proceed(); return result; } catch(Throwable e) { failure=e; throw e; } finally {
   long cost=(System.nanoTime()-start)/1000000;
   for(int i=0;i<annotations.length;i++) try { var a=annotations[i];var ctx=contexts.get(i);ctx.setVariable("_result",result);ctx.setVariable("_errMsg",failure==null?"":failure.getMessage());ctx.setVariable("_cost",cost);
    List<FieldChange> changes=a.diff().isEmpty()?List.of():OpsLogDiffer.diffFields(ctx.lookupVariable("_before"),value(a.diff(),ctx));ctx.setVariable("_changedFields",changes.stream().map(FieldChange::field).toList());ctx.setVariable("_diff",changes);
    boolean record=true; if(!a.condition().isEmpty()) try { record=!Boolean.FALSE.equals(value(a.condition(),ctx)); } catch(Exception e) { warn(e); }
    if(record) sink.record(new OpsLogRecord(a.operator().isEmpty()?operator.getOperator():render(a.operator(),ctx),a.module(),render(a.type(),ctx),render(a.bizNo(),ctx),failure==null?render(a.success(),ctx):a.fail().isEmpty()?"["+method.getName()+"] 执行失败: "+failure.getMessage():render(a.fail(),ctx),render(a.detail(),ctx),cost,failure==null,changes));
   } catch(Exception e) { warn(e); }
  }
 }
 private void warn(Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("审计处理失败@around",e); }
}