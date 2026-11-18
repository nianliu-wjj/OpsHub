package com.nianliu.opshub.biz.shared.template;

import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.slf4j.*;
import com.nianliu.opshub.common.validation.RequestValidator;
import com.nianliu.opshub.domain.model.exception.*;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Component public class BizProcessTemplate {
 private static final Logger log=LoggerFactory.getLogger(BizProcessTemplate.class);
 private final DegradeGuard guard;
 public BizProcessTemplate(DegradeGuard guard) { this.guard=guard; }
 public <REQ,RES> RES doInProcess(String code,REQ request,ProcessCallback<REQ,RES> callback) {
  if(guard.isDegraded(code)) throw OpsException.rejected("A0500","流程["+code+"]已降级熔断，请稍后重试");
  StopWatch watch=new StopWatch(code); RES result=null;
  try {
   watch.start("validateRequest"); RequestValidator.validate(request,code,callback.validationGroups()); watch.stop();
   watch.start("validateBiz"); callback.validateBiz(request); watch.stop();
   watch.start("preProcess"); callback.preProcess(request); watch.stop();
   watch.start("execute");
   try { result=callback.execute(request); } catch(DuplicateKeyException e) { result=callback.onDuplicate(request); if(result==null) throw e; }
   watch.stop(); return result;
  } finally {
   if(watch.isRunning()) watch.stop();
   try { callback.postProcess(request,result); } catch(Exception e) { log.error("收尾异常@doInProcess 流程码:{}",code,e); }
   log.info("完成@doInProcess 流程码:{}, 耗时明细:{}",code,watch.prettyPrint());
   LoggerFactory.getLogger("MONITOR_SUMMARY").info("流程码:{}|耗时ms:{}",code,watch.getTotalTimeMillis());
  }
 }
}
