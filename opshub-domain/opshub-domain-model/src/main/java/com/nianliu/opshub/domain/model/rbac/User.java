package com.nianliu.opshub.domain.model.rbac;

import lombok.Getter;
import lombok.Setter;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Getter
@Setter
public class User {
    private Long id;
    private String username;
    private String email;
    private String realName;
    private Integer enable;
    private Integer locked;
    private Long version;
    private Integer isDeleted;
    private String icon;
    private Integer type;
    private String remark;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    private java.time.LocalDateTime loginTime;
}
