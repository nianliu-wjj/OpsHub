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
@Table("sys_account")
public class SysAccount {
    @Id(keyType = KeyType.Auto)
    @Column(value = "id")
    private Long id;
    @Column(value = "username")
    private String username;
    @Column(value = "password")
    private String password;
    @Column(value = "icon")
    private String icon;
    @Column(value = "email")
    private String email;
    @Column(value = "type")
    private Integer type;
    @Column(value = "real_name")
    private String realName;
    @Column(value = "remark")
    private String remark;
    @Column(value = "create_time")
    private java.time.LocalDateTime createTime;
    @Column(value = "update_time")
    private java.time.LocalDateTime updateTime;
    @Column(value = "login_time")
    private java.time.LocalDateTime loginTime;
    @Column(value = "enable")
    private Integer enable;
    @Column(value = "lock")
    private Integer lock;
    @Column(value = "version", version = true)
    private Long version;
    @Column(value = "is_deleted", isLogicDelete = true)
    private Integer isDeleted;
}
