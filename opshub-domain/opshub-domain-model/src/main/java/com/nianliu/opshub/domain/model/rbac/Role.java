package com.nianliu.opshub.domain.model.rbac;

import lombok.Getter;
import lombok.Setter;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Getter
@Setter
public class Role {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer status;
    private Integer sort;
    private Long version;
    private Integer isDeleted;
}
