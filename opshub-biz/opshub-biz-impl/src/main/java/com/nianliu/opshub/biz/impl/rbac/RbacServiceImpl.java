package com.nianliu.opshub.biz.impl.rbac;

import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.domain.service.rbac.RbacService;
import com.nianliu.opshub.biz.shared.template.*;
import com.nianliu.opshub.common.opslog.OpsLog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {
    private final RbacOperations operations;
    private final BizProcessTemplate template;
    private final PasswordEncoder encoder;
    @org.springframework.beans.factory.annotation.Value("${opshub.security.admin.username}")
    private String bootstrapUsername;
    private <T> T run(String code,Object request,java.util.function.Supplier<T> action) {
        return template.doInProcess(code,request,new AbstractProcessCallback<Object,T>() {
            @Override public T execute(Object ignored) { return action.get(); }
        });
    }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    @Override
    public PageResult<Map<String,Object>> accountList(int page, int size, String keyword) { if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法"); return run("RBAC_ACCOUNTS_QUERY",1L,()->operations.page("accounts",page,size,keyword)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    @Override
    public Map<String,Object> accountGet(long id) {  return run("RBAC_ACCOUNTS_QUERY",1L,()->operations.get("accounts",id)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="ADD", success="accounts变更成功，ID: {#_result}", fail="accounts变更失败")
    @Override
    public Long accountCreate(AccountRequest request) { if(request.password()!=null && request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("密码UTF-8长度不能超过72字节"); if(bootstrapUsername.equals(request.username())) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("初始管理员用户名保留"); return run("RBAC_ACCOUNTS_WRITE",request,()->operations.saveAccount(null,request,request.password()==null?null:encoder.encode(request.password()))); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="accounts变更成功，ID: {#_result}", fail="accounts变更失败")
    @Override
    public Long accountUpdate(Long id, AccountRequest request) { if(request.password()!=null && request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("密码UTF-8长度不能超过72字节"); if(bootstrapUsername.equals(request.username())) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("初始管理员用户名保留"); return run("RBAC_ACCOUNTS_WRITE",request,()->operations.saveAccount(id,request,request.password()==null?null:encoder.encode(request.password()))); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="DELETE", success="删除accounts ID: {#id}", fail="删除accounts失败")
    @Override
    public void accountDelete(long id, long version) { run("RBAC_ACCOUNTS_DELETE",id,()->{operations.delete("accounts",id,version);return null;}); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    @Override
    public List<Long> accountAssignments(long id) { return run("RBAC_ASSIGNMENTS_QUERY",id,()->operations.assignments("accounts",id)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="替换accounts授权 ID: {#id}", fail="替换授权失败")
    @Override
    public void accountAssign(long id,AssignmentRequest request) { run("RBAC_ASSIGN",request,()->{operations.assign("accounts",id,request.ids(),request.version());return null;}); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    @Override
    public PageResult<Map<String,Object>> roleList(int page, int size, String keyword) { if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法"); return run("RBAC_ROLES_QUERY",1L,()->operations.page("roles",page,size,keyword)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    @Override
    public Map<String,Object> roleGet(long id) {  return run("RBAC_ROLES_QUERY",1L,()->operations.get("roles",id)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="ADD", success="roles变更成功，ID: {#_result}", fail="roles变更失败")
    @Override
    public Long roleCreate(RoleRequest request) {  return run("RBAC_ROLES_WRITE",request,()->operations.saveRole(null,request)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="roles变更成功，ID: {#_result}", fail="roles变更失败")
    @Override
    public Long roleUpdate(Long id, RoleRequest request) {  return run("RBAC_ROLES_WRITE",request,()->operations.saveRole(id,request)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="DELETE", success="删除roles ID: {#id}", fail="删除roles失败")
    @Override
    public void roleDelete(long id, long version) { run("RBAC_ROLES_DELETE",id,()->{operations.delete("roles",id,version);return null;}); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    @Override
    public List<Long> roleAssignments(long id) { return run("RBAC_ASSIGNMENTS_QUERY",id,()->operations.assignments("roles",id)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="替换roles授权 ID: {#id}", fail="替换授权失败")
    @Override
    public void roleAssign(long id,AssignmentRequest request) { run("RBAC_ASSIGN",request,()->{operations.assign("roles",id,request.ids(),request.version());return null;}); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:menu')")
    @Override
    public PageResult<Map<String,Object>> menuList(int page, int size, String keyword) { if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法"); return run("RBAC_MENUS_QUERY",1L,()->operations.page("menus",page,size,keyword)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:menu')")
    @Override
    public Map<String,Object> menuGet(long id) {  return run("RBAC_MENUS_QUERY",1L,()->operations.get("menus",id)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="ADD", success="menus变更成功，ID: {#_result}", fail="menus变更失败")
    @Override
    public Long menuCreate(MenuRequest request) {  return run("RBAC_MENUS_WRITE",request,()->operations.saveMenu(null,request)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="menus变更成功，ID: {#_result}", fail="menus变更失败")
    @Override
    public Long menuUpdate(Long id, MenuRequest request) {  return run("RBAC_MENUS_WRITE",request,()->operations.saveMenu(id,request)); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="DELETE", success="删除menus ID: {#id}", fail="删除menus失败")
    @Override
    public void menuDelete(long id, long version) { run("RBAC_MENUS_DELETE",id,()->{operations.delete("menus",id,version);return null;}); }
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:logging:login')")
    @Override
    public PageResult<Map<String,Object>> loginLogs(int page,int size,String keyword) {
        if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法");
        return run("RBAC_LOGIN_LOG_QUERY",1L,()->operations.page("login-logs",page,size,keyword));
    }
    @PreAuthorize("@rbacPermission.allowed(authentication, '__authenticated')")
    @Override
    public List<String> currentPermissions(String username) {
        var account=operations.credentials(username);
        if(account==null && bootstrapUsername.equals(username)) return List.of("ROLE_ADMIN");
        if(account==null) throw new org.springframework.security.access.AccessDeniedException("账号不存在");
        return run("RBAC_CURRENT_PERMISSIONS",1L,()->operations.authorities(((Number)account.get("id")).longValue()));
    }
    @PreAuthorize("@rbacPermission.allowed(authentication, '__authenticated')")
    @Override
    public List<Map<String,Object>> currentMenus(String username) {
        var account=operations.credentials(username);
        if(account==null && bootstrapUsername.equals(username)) return List.of();
        if(account==null) throw new org.springframework.security.access.AccessDeniedException("账号不存在");
        return run("RBAC_CURRENT_MENUS",1L,()->operations.menus(((Number)account.get("id")).longValue()));
    }
    @Override public Map<String,Object> credentials(String username) { return operations.credentials(username); }
    @Override public List<String> authorities(long id) { return operations.authorities(id); }
    @Override public List<Map<String,Object>> menus(long id) { return operations.menus(id); }
}
