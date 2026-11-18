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
    PageResult<Map<String,Object>> accountList(int page, int size, String keyword);
    Map<String,Object> accountGet(long id);
    Long accountCreate(AccountRequest request);
    Long accountUpdate(Long id, AccountRequest request);
    void accountDelete(long id, long version);
    List<Long> accountAssignments(long id);
    void accountAssign(long id,AssignmentRequest request);
    PageResult<Map<String,Object>> roleList(int page, int size, String keyword);
    Map<String,Object> roleGet(long id);
    Long roleCreate(RoleRequest request);
    Long roleUpdate(Long id, RoleRequest request);
    void roleDelete(long id, long version);
    List<Long> roleAssignments(long id);
    void roleAssign(long id,AssignmentRequest request);
    PageResult<Map<String,Object>> menuList(int page, int size, String keyword);
    Map<String,Object> menuGet(long id);
    Long menuCreate(MenuRequest request);
    Long menuUpdate(Long id, MenuRequest request);
    void menuDelete(long id, long version);
    PageResult<Map<String,Object>> loginLogs(int page,int size,String keyword);
    List<String> currentPermissions(String username);
    List<Map<String,Object>> currentMenus(String username);
    Map<String,Object> credentials(String username);
    List<String> authorities(long id);
    List<Map<String,Object>> menus(long id);
}
