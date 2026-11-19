package com.nianliu.opshub.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nianliu.opshub.biz.impl.rbac.RbacOperations;
import com.nianliu.opshub.infra.dal.repository.rbac.RbacRepository;
import com.nianliu.opshub.infra.integration.cache.RbacCache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

/**
 * 验证版本切换及 Redis 故障时权限回源行为。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
class RbacCacheTest {
    @Test
    @SuppressWarnings("unchecked")
    void changedVersionNeverReusesPreviousPermissionKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get("opshub:rbac:1:authorities:7")).thenReturn("\"[\\\"sys:account\\\"]\"");
        RbacRepository repository = mock(RbacRepository.class);
        when(repository.version()).thenReturn(1L, 2L);
        when(repository.authorities(7L)).thenReturn(List.of());
        RbacOperations service = new RbacOperations(repository, new RbacCache(redis));
        assertEquals(List.of("sys:account"), service.authorities(7L));
        assertTrue(service.authorities(7L).isEmpty());
        verify(operations).get("opshub:rbac:2:authorities:7");
        verify(operations)
                .set(eq("opshub:rbac:2:authorities:7"), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void redisFailureLoadsDatabaseAndDoesNotInventPermissions() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        RbacCache cache = new RbacCache(redis);
        assertEquals("database", cache.get("key", String.class, () -> "database"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        cache.get(
                                "key",
                                String.class,
                                () -> {
                                    throw new IllegalStateException("database offline");
                                }));
    }

    @Test
    void applicationRejectsMissingReferenceBeforeWritingAssociations() {
        RbacRepository repository = mock(RbacRepository.class);
        var user = new com.nianliu.opshub.domain.model.rbac.User();
        user.setId(7L);
        user.setVersion(0L);
        when(repository.get("accounts", com.nianliu.opshub.domain.model.rbac.User.class, 7L))
                .thenReturn(user);
        when(repository.get("roles", com.nianliu.opshub.domain.model.rbac.Role.class, 999L))
                .thenReturn(null);
        RbacOperations service = new RbacOperations(repository, mock(RbacCache.class));
        assertThrows(
                com.nianliu.opshub.domain.model.exception.OpsException.class,
                () -> service.assign("accounts", 7L, List.of(999L), 0L));
        verify(repository).lockGraph();
        verify(repository, never()).replaceAssignments(anyString(), anyLong(), anyList());
    }
}
