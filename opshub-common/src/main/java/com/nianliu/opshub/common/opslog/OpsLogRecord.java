package com.nianliu.opshub.common.opslog;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public record OpsLogRecord(String operator,String module,String type,String bizNo,String content,String details,long costMs,boolean success,java.util.List<FieldChange> fieldChanges) { public String toJson() { return com.alibaba.fastjson2.JSON.toJSONString(this); } }