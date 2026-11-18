package com.nianliu.opshub.infra.integration.cache;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacCache {
    private final StringRedisTemplate redis;
    public <T> T get(String key,Class<T> type,Supplier<T> loader) {
        try { String cached=redis.opsForValue().get(key); if(cached!=null) return JSON.parseObject(cached,type); }
        catch(RuntimeException e) { log.warn("RBAC缓存读取失败，回源数据库@{}",key); }
        T value=loader.get();
        try { redis.opsForValue().set(key,JSON.toJSONString(value),Duration.ofSeconds(60)); }
        catch(RuntimeException e) { log.warn("RBAC缓存写入失败@{}",key); }
        return value;
    }
}
