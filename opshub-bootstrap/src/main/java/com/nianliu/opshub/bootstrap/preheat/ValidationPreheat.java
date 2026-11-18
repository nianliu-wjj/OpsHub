package com.nianliu.opshub.bootstrap.preheat;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import com.nianliu.opshub.common.validation.RequestValidator;
@Component public class ValidationPreheat implements InitializingBean {
 private final String classes; public ValidationPreheat(@Value("${opshub.validation.preheat.classes:}") String classes) { this.classes=classes; }
 public void afterPropertiesSet() { long start=System.nanoTime(); try { RequestValidator.warmup(); } catch(Exception e) { warn(e); }
  for(String name:classes.split(",")) if(!name.isBlank()) try { RequestValidator.warmup(Class.forName(name.trim())); } catch(Exception e) { warn(e); }
  org.slf4j.LoggerFactory.getLogger(getClass()).info("校验预热完成@afterPropertiesSet 耗时ms:{}",(System.nanoTime()-start)/1000000);
 }
 private void warn(Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("校验预热失败",e); }
}