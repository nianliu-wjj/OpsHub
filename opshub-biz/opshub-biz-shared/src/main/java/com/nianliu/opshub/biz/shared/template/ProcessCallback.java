package com.nianliu.opshub.biz.shared.template;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public interface ProcessCallback<REQ, RES> {
    default Class<?>[] validationGroups() {
        return new Class<?>[0];
    }

    default void validateBiz(REQ request) {}

    default void preProcess(REQ request) {}

    RES execute(REQ request);

    default RES onDuplicate(REQ request) {
        return null;
    }

    default void postProcess(REQ request, RES result) {}
}
