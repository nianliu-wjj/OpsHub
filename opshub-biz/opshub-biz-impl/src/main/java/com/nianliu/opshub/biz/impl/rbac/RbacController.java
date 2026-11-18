package com.nianliu.opshub.biz.impl.rbac;

import com.nianliu.opshub.api.base.result.Result;
import com.nianliu.opshub.api.rbac.*;
import com.nianliu.opshub.domain.service.rbac.RbacService;
import com.nianliu.opshub.biz.shared.template.*;
import com.nianliu.opshub.common.opslog.OpsLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.Valid;
import java.util.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@RestController
@RequestMapping("/rbac")
@RequiredArgsConstructor
public class RbacController {
    private final RbacService service;
    private final BizProcessTemplate template;
    private final PasswordEncoder encoder;
    @org.springframework.beans.factory.annotation.Value("${opshub.security.admin.username}")
    private String bootstrapUsername;
    private <T> Result<T> run(String code,Object request,java.util.function.Supplier<T> action) {
        return Result.ok(template.doInProcess(code,request,new AbstractProcessCallback<Object,T>() {
            @Override public T execute(Object ignored) { return action.get(); }
        }));
    }
    @GetMapping("/accounts")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    public Result<PageResult<Map<String,Object>>> accountList(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String keyword) { if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法"); return run("RBAC_ACCOUNTS_QUERY",1L,()->service.page("accounts",page,size,keyword)); }
    @GetMapping("/accounts/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    public Result<Map<String,Object>> accountGet(@PathVariable long id) {  return run("RBAC_ACCOUNTS_QUERY",1L,()->service.get("accounts",id)); }
    @PostMapping("/accounts")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="ADD", success="accounts变更成功，ID: {#_result.data}", fail="accounts变更失败")
    public Result<Long> accountCreate(@Valid @RequestBody AccountRequest request) { if(request.password()!=null && request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("密码UTF-8长度不能超过72字节"); if(bootstrapUsername.equals(request.username())) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("初始管理员用户名保留"); return run("RBAC_ACCOUNTS_WRITE",request,()->service.saveAccount(null,request,request.password()==null?null:encoder.encode(request.password()))); }
    @PutMapping("/accounts/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="accounts变更成功，ID: {#_result.data}", fail="accounts变更失败")
    public Result<Long> accountUpdate(@PathVariable Long id, @Valid @RequestBody AccountRequest request) { if(request.password()!=null && request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("密码UTF-8长度不能超过72字节"); if(bootstrapUsername.equals(request.username())) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("初始管理员用户名保留"); return run("RBAC_ACCOUNTS_WRITE",request,()->service.saveAccount(id,request,request.password()==null?null:encoder.encode(request.password()))); }
    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="DELETE", success="删除accounts ID: {#id}", fail="删除accounts失败")
    public Result<Void> accountDelete(@PathVariable long id, @RequestParam long version) { return run("RBAC_ACCOUNTS_DELETE",id,()->{service.delete("accounts",id,version);return null;}); }
    @GetMapping("/accounts/{id}/roles")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:account')")
    public Result<List<Long>> accountAssignments(@PathVariable long id) { return run("RBAC_ASSIGNMENTS_QUERY",id,()->service.assignments("accounts",id)); }
    @PutMapping("/accounts/{id}/roles")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="替换accounts授权 ID: {#id}", fail="替换授权失败")
    public Result<Void> accountAssign(@PathVariable long id,@Valid @RequestBody AssignmentRequest request) { return run("RBAC_ASSIGN",request,()->{service.assign("accounts",id,request.ids(),request.version());return null;}); }
    @GetMapping("/roles")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    public Result<PageResult<Map<String,Object>>> roleList(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String keyword) { if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法"); return run("RBAC_ROLES_QUERY",1L,()->service.page("roles",page,size,keyword)); }
    @GetMapping("/roles/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    public Result<Map<String,Object>> roleGet(@PathVariable long id) {  return run("RBAC_ROLES_QUERY",1L,()->service.get("roles",id)); }
    @PostMapping("/roles")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="ADD", success="roles变更成功，ID: {#_result.data}", fail="roles变更失败")
    public Result<Long> roleCreate(@Valid @RequestBody RoleRequest request) {  return run("RBAC_ROLES_WRITE",request,()->service.saveRole(null,request)); }
    @PutMapping("/roles/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="roles变更成功，ID: {#_result.data}", fail="roles变更失败")
    public Result<Long> roleUpdate(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {  return run("RBAC_ROLES_WRITE",request,()->service.saveRole(id,request)); }
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="DELETE", success="删除roles ID: {#id}", fail="删除roles失败")
    public Result<Void> roleDelete(@PathVariable long id, @RequestParam long version) { return run("RBAC_ROLES_DELETE",id,()->{service.delete("roles",id,version);return null;}); }
    @GetMapping("/roles/{id}/menus")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:role')")
    public Result<List<Long>> roleAssignments(@PathVariable long id) { return run("RBAC_ASSIGNMENTS_QUERY",id,()->service.assignments("roles",id)); }
    @PutMapping("/roles/{id}/menus")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="替换roles授权 ID: {#id}", fail="替换授权失败")
    public Result<Void> roleAssign(@PathVariable long id,@Valid @RequestBody AssignmentRequest request) { return run("RBAC_ASSIGN",request,()->{service.assign("roles",id,request.ids(),request.version());return null;}); }
    @GetMapping("/menus")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:menu')")
    public Result<PageResult<Map<String,Object>>> menuList(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String keyword) { if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法"); return run("RBAC_MENUS_QUERY",1L,()->service.page("menus",page,size,keyword)); }
    @GetMapping("/menus/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:menu')")
    public Result<Map<String,Object>> menuGet(@PathVariable long id) {  return run("RBAC_MENUS_QUERY",1L,()->service.get("menus",id)); }
    @PostMapping("/menus")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="ADD", success="menus变更成功，ID: {#_result.data}", fail="menus变更失败")
    public Result<Long> menuCreate(@Valid @RequestBody MenuRequest request) {  return run("RBAC_MENUS_WRITE",request,()->service.saveMenu(null,request)); }
    @PutMapping("/menus/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="UPDATE", success="menus变更成功，ID: {#_result.data}", fail="menus变更失败")
    public Result<Long> menuUpdate(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {  return run("RBAC_MENUS_WRITE",request,()->service.saveMenu(id,request)); }
    @DeleteMapping("/menus/{id}")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'rbac:admin')")
    @OpsLog(module="RBAC", type="DELETE", success="删除menus ID: {#id}", fail="删除menus失败")
    public Result<Void> menuDelete(@PathVariable long id, @RequestParam long version) { return run("RBAC_MENUS_DELETE",id,()->{service.delete("menus",id,version);return null;}); }
    @GetMapping("/login-logs")
    @PreAuthorize("@rbacPermission.allowed(authentication, 'sys:logging:login')")
    public Result<PageResult<Map<String,Object>>> loginLogs(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword) {
        if(page<1||size<1||size>100||keyword.length()>100) throw new com.nianliu.opshub.domain.model.exception.ParamRejectException("分页参数非法");
        return run("RBAC_LOGIN_LOG_QUERY",1L,()->service.page("login-logs",page,size,keyword));
    }
    @PreAuthorize("@rbacPermission.allowed(authentication, '__authenticated')")
    @GetMapping("/me/permissions")
    public Result<List<String>> currentPermissions(java.security.Principal principal) {
        var account=service.credentials(principal.getName());
        if(account==null && bootstrapUsername.equals(principal.getName())) return Result.ok(List.of("ROLE_ADMIN"));
        if(account==null) throw new org.springframework.security.access.AccessDeniedException("账号不存在");
        return run("RBAC_CURRENT_PERMISSIONS",1L,()->service.authorities(((Number)account.get("id")).longValue()));
    }
    @PreAuthorize("@rbacPermission.allowed(authentication, '__authenticated')")
    @GetMapping("/me/menus")
    public Result<List<Map<String,Object>>> currentMenus(java.security.Principal principal) {
        var account=service.credentials(principal.getName());
        if(account==null && bootstrapUsername.equals(principal.getName())) return Result.ok(List.of());
        if(account==null) throw new org.springframework.security.access.AccessDeniedException("账号不存在");
        return run("RBAC_CURRENT_MENUS",1L,()->service.menus(((Number)account.get("id")).longValue()));
    }
}
