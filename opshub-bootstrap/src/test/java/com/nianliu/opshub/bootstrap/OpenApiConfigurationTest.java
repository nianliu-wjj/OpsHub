package com.nianliu.opshub.bootstrap;

import com.nianliu.opshub.bootstrap.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.alibaba.fastjson2.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证真实 OpenAPI 自动装配与业务 JSON 转换器兼容性。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@SpringBootTest(classes = OpenApiConfigurationTest.TestConfiguration.class,
        properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
class OpenApiConfigurationTest {
    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("opshub.security.admin.username", () -> "admin");
        registry.add("opshub.security.admin.password-hash", () -> new BCryptPasswordEncoder().encode("test-password"));
    }

    @Test
    void documentIsValidJsonWithAuthenticationScheme() throws Exception {
        var result = mvc.perform(get("/v3/api-docs").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        var document = JSON.parseObject(result.getResponse().getContentAsString());
        assertEquals("OpsHub 接口文档", document.getJSONObject("info").getString("title"));
        assertEquals("basic", document.getJSONObject("components").getJSONObject("securitySchemes")
                .getJSONObject("basicAuth").getString("scheme"));
        org.junit.jupiter.api.Assertions.assertTrue(document.getJSONObject("paths").containsKey("/auth/me"));
    }

    @Test
    void docsAndUiRequireAdmin() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs").with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui/index.html").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    /**
     * @author nianliu-wjj
     * @since 2026-11-18
     */
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({OpenApiConfiguration.class, SecurityConfiguration.class, SecuritySessionController.class,
            FastJsonConverterConfig.class, RootExceptionAdvice.class})
    static class TestConfiguration {
    }
}
