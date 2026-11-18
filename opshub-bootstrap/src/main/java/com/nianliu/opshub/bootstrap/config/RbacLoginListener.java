package com.nianliu.opshub.bootstrap.config;

import com.nianliu.opshub.infra.dal.repository.rbac.RbacRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacLoginListener {
    private final RbacRepository repository;
    @EventListener
    public void success(AuthenticationSuccessEvent event) { record(event.getAuthentication().getName(),"success",null); }
    @EventListener
    public void failure(AbstractAuthenticationFailureEvent event) { record(event.getAuthentication().getName(),"failure","认证失败"); }
    private void record(String username,String status,String reason) {
        String ip=null,agent=null;
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) { ip=attributes.getRequest().getRemoteAddr();agent=attributes.getRequest().getHeader("User-Agent"); }
        try { repository.loginLog(username,ip,agent,status,reason); } catch(RuntimeException e) { log.error("登录日志写入失败@record",e); }
    }
}
