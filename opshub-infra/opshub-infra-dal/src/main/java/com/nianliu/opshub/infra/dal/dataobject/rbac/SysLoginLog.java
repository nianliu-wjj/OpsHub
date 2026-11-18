package com.nianliu.opshub.infra.dal.dataobject.rbac;

import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Getter
@Setter
@Table("sys_login_log")
public class SysLoginLog {
    @Id(keyType = KeyType.Auto)
    @Column(value = "id")
    private Long id;
    @Column(value = "created_time")
    private java.time.LocalDateTime createdTime;
    @Column(value = "account")
    private String account;
    @Column(value = "ip")
    private String ip;
    @Column(value = "user_agent")
    private String userAgent;
    @Column(value = "mac")
    private String mac;
    @Column(value = "status")
    private String status;
    @Column(value = "reason")
    private String reason;
}
