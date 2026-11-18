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
@Table("sys_role_menu")
public class SysRoleMenu {
    @Id(keyType = KeyType.Auto)
    @Column(value = "id")
    private Long id;
    @Column(value = "role_id")
    private Long roleId;
    @Column(value = "menu_id")
    private Long menuId;
}
