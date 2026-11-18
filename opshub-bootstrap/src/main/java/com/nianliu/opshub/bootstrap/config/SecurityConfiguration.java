package com.nianliu.opshub.bootstrap.config;

import com.alibaba.fastjson2.JSON;
import com.nianliu.opshub.api.base.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证与请求、方法权限的统一装配入口。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${opshub.security.admin.username}") String username,
            @Value("${opshub.security.admin.password-hash}") String passwordHash) {
        if (username.isBlank() || !passwordHash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalArgumentException("必须配置管理员用户名及 BCrypt 密码摘要");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordHash).roles("ADMIN").build());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) ->
                        writeFailure(response, 401, "A0401", "请提供有效的认证凭据")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeFailure(response, 401, "A0401", "请提供有效的认证凭据"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeFailure(response, 403, "A0403", "权限不足或 CSRF 校验失败")))
                .csrf(Customizer.withDefaults());
        return http.build();
    }

    private static void writeFailure(HttpServletResponse response, int status,
                                     String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"OpsHub\"");
        }
        response.getWriter().write(JSON.toJSONString(Result.fail(code, message)));
    }
}
