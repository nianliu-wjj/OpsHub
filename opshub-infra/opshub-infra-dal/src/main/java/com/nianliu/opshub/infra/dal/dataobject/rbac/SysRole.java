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
@Table("sys_role")
public class SysRole {
    @Id(keyType = KeyType.Auto)
    @Column(value = "id")
    private Long id;
    @Column(value = "name")
    private String name;
    @Column(value = "code")
    private String code;
    @Column(value = "description")
    private String description;
    @Column(value = "create_time")
    private java.time.LocalDateTime createTime;
    @Column(value = "status")
    private Integer status;
    @Column(value = "sort")
    private Integer sort;
    @Column(value = "version", version = true)
    private Long version;
    @Column(value = "is_deleted", isLogicDelete = true)
    private Integer isDeleted;
}
