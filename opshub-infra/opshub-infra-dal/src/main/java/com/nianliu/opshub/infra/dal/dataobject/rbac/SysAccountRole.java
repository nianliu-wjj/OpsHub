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
@Table("sys_account_role")
public class SysAccountRole {
    @Id(keyType = KeyType.Auto)
    @Column(value = "id")
    private Long id;
    @Column(value = "account_id")
    private Long accountId;
    @Column(value = "role_id")
    private Long roleId;
}
