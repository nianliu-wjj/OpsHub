package com.nianliu.opshub.domain.service.rbac;

import com.nianliu.opshub.api.rbac.*;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
public interface RoleService {
    PageResult<Map<String, Object>> roleList(int page, int size, String keyword);

    Map<String, Object> roleGet(long id);

    Long roleCreate(RoleRequest request);

    Long roleUpdate(Long id, RoleRequest request);

    void roleDelete(long id, long version);

    List<Long> roleAssignments(long id);

    void roleAssign(long id, AssignmentRequest request);
}
