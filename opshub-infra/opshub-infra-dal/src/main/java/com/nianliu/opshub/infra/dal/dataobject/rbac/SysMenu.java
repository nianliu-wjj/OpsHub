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
@Table("sys_menu")
public class SysMenu {
    @Id(keyType = KeyType.Auto)
    @Column(value = "id")
    private Long id;
    @Column(value = "parent_id")
    private Long parentId;
    @Column(value = "icon")
    private String icon;
    @Column(value = "name")
    private String name;
    @Column(value = "code")
    private String code;
    @Column(value = "url")
    private String url;
    @Column(value = "method")
    private String method;
    @Column(value = "type")
    private Integer type;
    @Column(value = "sort")
    private Integer sort;
    @Column(value = "redirect")
    private String redirect;
    @Column(value = "hidden")
    private Integer hidden;
    @Column(value = "description")
    private String description;
    @Column(value = "create_time")
    private java.time.LocalDateTime createTime;
    @Column(value = "version", version = true)
    private Long version;
    @Column(value = "is_deleted", isLogicDelete = true)
    private Integer isDeleted;
}
