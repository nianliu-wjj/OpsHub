package com.nianliu.opshub.biz.shared.template;

public interface ProcessCallback<REQ,RES> {
 default Class<?>[] validationGroups() { return new Class<?>[0]; }
 default void validateBiz(REQ request) {} default void preProcess(REQ request) {}
 RES execute(REQ request);
 default RES onDuplicate(REQ request) { return null; }
 default void postProcess(REQ request,RES result) {}
}
