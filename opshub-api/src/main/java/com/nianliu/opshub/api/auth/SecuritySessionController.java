package com.nianliu.opshub.api.auth;

import com.nianliu.opshub.api.base.result.Result;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * 提供当前身份及写操作所需的 CSRF 令牌。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@RestController
public class SecuritySessionController {
    @GetMapping("/auth/me")
    public Result<String> currentUser(Principal principal) {
        return Result.ok(principal.getName());
    }

    @GetMapping("/auth/csrf")
    public Result<Map<String, String>> csrf(CsrfToken token) {
        return Result.ok(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
}
