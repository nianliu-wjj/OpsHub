package com.nianliu.opshub.domain.model.rbac;

import lombok.Getter;
import lombok.Setter;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Getter
@Setter
public class UserCredential {
    private Long id;
    private String username;
    private String password;
    private Integer enable;
    private Integer locked;
}
