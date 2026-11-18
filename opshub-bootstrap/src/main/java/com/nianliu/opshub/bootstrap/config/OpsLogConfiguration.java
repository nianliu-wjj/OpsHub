package com.nianliu.opshub.bootstrap.config;
import com.nianliu.opshub.common.opslog.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Configuration public class OpsLogConfiguration {
 @Bean @ConditionalOnMissingBean public IOpsLogService opsLogService() { return record->org.slf4j.LoggerFactory.getLogger("OP_LOG").info("{}",record.toJson()); }
 @Bean @ConditionalOnMissingBean public IOperatorProvider operatorProvider() { return ()->{
  var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  if (authentication != null && authentication.isAuthenticated()
      && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
   return authentication.getName();
  }
  return "system";
 }; }
}