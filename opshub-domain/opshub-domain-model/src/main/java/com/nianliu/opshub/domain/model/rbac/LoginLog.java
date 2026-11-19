package com.nianliu.opshub.domain.model.rbac;

import lombok.Getter;
import lombok.Setter;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Getter
@Setter
public class LoginLog {
    private Long id;
    private java.time.LocalDateTime createdTime;
    private String account;
    private String ip;
    private String userAgent;
    private String status;
    private String reason;
}
