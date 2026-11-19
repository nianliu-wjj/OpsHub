package com.nianliu.opshub.bootstrap;

import com.nianliu.opshub.bootstrap.config.*;
import com.nianliu.opshub.api.auth.SecuritySessionController;
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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.nianliu.opshub.biz.impl.rbac.RbacOperations rbacService;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("opshub.security.database-enabled", () -> "false");
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
        org.junit.jupiter.api.Assertions.assertTrue(document.getJSONObject("paths").containsKey("/rbac/accounts"));
        org.junit.jupiter.api.Assertions.assertTrue(document.getJSONObject("paths").getJSONObject("/rbac/accounts/{id}").getJSONObject("delete").getJSONArray("parameters").toJSONString().contains("version"));
    }

    @Test
    void docsAndUiRequireAdmin() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs").with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui/index.html").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void accountCreationUsesTypedRequestAndEncodesPassword() throws Exception {
        org.mockito.Mockito.when(rbacService.saveAccount(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn(7L);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/rbac/accounts")
                .with(user("admin").roles("ADMIN"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json")
                .content("{\"username\":\"operator\",\"password\":\"change-me-123\",\"enable\":1,\"locked\":0}"))
                .andExpect(status().isOk());
        var encoded=org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(rbacService).saveAccount(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),encoded.capture());
        org.junit.jupiter.api.Assertions.assertTrue(new BCryptPasswordEncoder().matches("change-me-123",encoded.getValue()));
    }

    @Test
    void uniqueConstraintConflictUses409Envelope() throws Exception {
        org.mockito.Mockito.when(rbacService.saveAccount(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/rbac/accounts")
                .with(user("admin").roles("ADMIN"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json")
                .content("{\"username\":\"operator\",\"password\":\"change-me-123\",\"enable\":1,\"locked\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void serviceImplementationRejectsUnauthorizedAccountWrites() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/rbac/accounts")
                .with(user("reader").roles("USER"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json")
                .content("{\"username\":\"operator\",\"password\":\"change-me-123\",\"enable\":1,\"locked\":0}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(rbacService,org.mockito.Mockito.never()).saveAccount(
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * @author nianliu-wjj
     * @since 2026-11-18
     */
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({OpenApiConfiguration.class, SecurityConfiguration.class, SecuritySessionController.class,
            FastJsonConverterConfig.class, RootExceptionAdvice.class, RbacExceptionAdvice.class,
            com.nianliu.opshub.api.rbac.RbacController.class, com.nianliu.opshub.api.rbac.UserController.class, com.nianliu.opshub.api.rbac.RoleController.class, com.nianliu.opshub.api.rbac.PermissionController.class, com.nianliu.opshub.biz.impl.rbac.UserServiceImpl.class, com.nianliu.opshub.biz.impl.rbac.RoleServiceImpl.class, com.nianliu.opshub.biz.impl.rbac.PermissionServiceImpl.class, com.nianliu.opshub.biz.shared.template.BizProcessExecutor.class, com.nianliu.opshub.biz.impl.rbac.RbacServiceImpl.class, RbacPermission.class,
            com.nianliu.opshub.biz.shared.template.BizProcessTemplate.class,
            com.nianliu.opshub.biz.shared.template.DegradeGuard.class})
    static class TestConfiguration {
    }
}
