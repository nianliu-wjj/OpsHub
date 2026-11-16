/**
 * 事件处理器（§32.3）：一事件一处理器，方法体只做「取业务键 → 调领域服务」，不做业务；必须幂等（状态守卫 + 唯一键）。
 */
package com.nianliu.opshub.biz.impl.event;
