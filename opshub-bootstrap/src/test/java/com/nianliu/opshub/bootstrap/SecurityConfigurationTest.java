package com.nianliu.opshub.bootstrap;

import com.nianliu.opshub.bootstrap.config.SecurityConfiguration;
import com.nianliu.opshub.bootstrap.config.SecuritySessionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 验证过滤链的认证、授权及 CSRF 默认保护。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@WebMvcTest(SecuritySessionController.class)
@org.springframework.test.context.ContextConfiguration(classes = {SecurityConfiguration.class, SecuritySessionController.class, com.nianliu.opshub.bootstrap.config.FastJsonConverterConfig.class, RootExceptionAdvice.class})
class SecurityConfigurationTest {
    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("opshub.security.database-enabled", () -> "false");
        registry.add("opshub.security.admin.username", () -> "admin");
        registry.add("opshub.security.admin.password-hash",
                () -> new BCryptPasswordEncoder().encode("test-password"));
    }

    @Test
    void anonymousAndWrongPasswordReturn401() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A0401")));
        mvc.perform(get("/auth/me").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCredentialsExposeIdentityAndCsrf() throws Exception {
        mvc.perform(get("/auth/me").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("admin")));
        mvc.perform(get("/auth/csrf").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("token")));
    }

    @Test
    void nonAdminAndMissingCsrfReturn403() throws Exception {
        mvc.perform(get("/actuator/metrics").with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/me").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
