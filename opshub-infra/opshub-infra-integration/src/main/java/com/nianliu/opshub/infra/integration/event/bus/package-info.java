/**
 * 事件总线族（§31.3 / §31.6）：四条总线；分布式总线 = 注解式消费 → 按事件名分发处理器 → 失败重投 → 超阈值告警 → 超上限熔断 + 缓存去重隔离。
 */
package com.nianliu.opshub.infra.integration.event.bus;
