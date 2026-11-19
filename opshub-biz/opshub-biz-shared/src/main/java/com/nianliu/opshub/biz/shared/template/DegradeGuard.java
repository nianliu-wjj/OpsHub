package com.nianliu.opshub.biz.shared.template;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Component
public class DegradeGuard {
    private final Set<String> codes = ConcurrentHashMap.newKeySet();

    public DegradeGuard(@Value("${opshub.degrade.process-codes:}") String configured) {
        for (String code : configured.split(",")) if (!code.isBlank()) codes.add(code.trim());
    }

    public boolean isDegraded(String code) {
        return codes.contains(code);
    }

    public void degrade(String code) {
        codes.add(code);
    }

    public void resume(String code) {
        codes.remove(code);
    }
}
