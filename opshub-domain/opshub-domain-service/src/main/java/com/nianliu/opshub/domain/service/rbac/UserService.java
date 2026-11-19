package com.nianliu.opshub.domain.service.rbac;

import com.nianliu.opshub.api.rbac.*;

import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
public interface UserService {
    PageResult<Map<String, Object>> accountList(int page, int size, String keyword);

    Map<String, Object> accountGet(long id);

    Long accountCreate(AccountRequest request);

    Long accountUpdate(Long id, AccountRequest request);

    void accountDelete(long id, long version);

    List<Long> accountAssignments(long id);

    void accountAssign(long id, AssignmentRequest request);
}
