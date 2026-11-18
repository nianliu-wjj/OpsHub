package com.nianliu.opshub.bootstrap.preheat;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Component public class OpsComponentBannerRunner implements ApplicationRunner {
 private final boolean enabled;
 public OpsComponentBannerRunner(@Value("${opshub.banner.enable:true}") boolean enabled) { this.enabled=enabled; }
 public void run(ApplicationArguments args) { if(enabled) System.out.println(":: OpsHub Framework :: v1.0.0 | OpsLog 审计 | SpEL 分组校验 | 七步业务模板"); }
}
