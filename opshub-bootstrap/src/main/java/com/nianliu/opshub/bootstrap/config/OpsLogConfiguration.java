package com.nianliu.opshub.bootstrap.config;
import com.nianliu.opshub.common.opslog.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.context.request.*;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Configuration public class OpsLogConfiguration {
 @Bean @ConditionalOnMissingBean public IOpsLogService opsLogService() { return record->org.slf4j.LoggerFactory.getLogger("OP_LOG").info("{}",record.toJson()); }
 @Bean @ConditionalOnMissingBean public IOperatorProvider operatorProvider() { return ()->{
  if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) { String operator=a.getRequest().getHeader("X-Operator"); if(operator!=null&&!operator.isBlank()) return operator; } return "system";
 }; }
}