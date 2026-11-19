package com.nianliu.opshub.domain.service.rbac;

import com.nianliu.opshub.api.rbac.*;

import java.util.*;

/**
 * RBAC 业务接口，具体实现由 biz-impl 提供。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public interface RbacService {
    PageResult<Map<String, Object>> loginLogs(int page, int size, String keyword);

    List<String> currentPermissions(String username);

    List<Map<String, Object>> currentMenus(String username);

    Map<String, Object> credentials(String username);

    List<String> authorities(long id);
    List<Map<String, Object>> menus(long id);
}
