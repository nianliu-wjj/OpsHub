package com.nianliu.opshub.common.opslog;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
/** 返回防御性副本，禁止返回可变缓存的 live 引用。 */ public interface OpsLogBeforeFunction { Object find(Object key); }