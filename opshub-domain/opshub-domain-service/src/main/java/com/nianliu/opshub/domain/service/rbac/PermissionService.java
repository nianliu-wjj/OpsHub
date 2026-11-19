package com.nianliu.opshub.domain.service.rbac;

import com.nianliu.opshub.api.rbac.*;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
public interface PermissionService {
    PageResult<Map<String, Object>> menuList(int page, int size, String keyword);

    Map<String, Object> menuGet(long id);

    Long menuCreate(MenuRequest request);

    Long menuUpdate(Long id, MenuRequest request);

    void menuDelete(long id, long version);
}
