package com.nianliu.opshub.biz.shared.template;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * @author nianliu-wjj
 * @since 2026-11-20
 */
@Component
@RequiredArgsConstructor
public class BizProcessExecutor {
    private final BizProcessTemplate template;

    public <T> T run(String code, Object request, Supplier<T> action) {
        return template.doInProcess(
                code,
                request,
                new AbstractProcessCallback<Object, T>() {
                    @Override
                    public T execute(Object ignored) {
                        return action.get();
                    }
                });
    }
}
