package com.nianliu.opshub.domain.service.rbac;

import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.domain.model.rbac.*;

import java.util.*;

/**
 * RBAC 业务接口，具体实现由 biz-impl 提供。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public interface RbacService {
    PageResult<LoginLog> loginLogs(int page, int size, String keyword);

    List<String> currentPermissions(String username);

    List<Permission> currentMenus(String username);

    UserCredential credentials(String username);

    List<String> authorities(long id);

    List<Permission> menus(long id);
}
