package com.nianliu.opshub.domain.model.rbac;

import lombok.Getter;
import lombok.Setter;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Getter
@Setter
public class Permission {
    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private String url;
    private String method;
    private Integer type;
    private Integer sort;
    private Integer hidden;
    private Long version;
    private Integer isDeleted;
}
