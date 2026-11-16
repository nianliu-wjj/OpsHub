/**
 * 启动预热器（§2.7.1.1）：预热的唯一归属地，业务模块不得各自预热；实现 InitializingBean / ApplicationRunner；幂等无副作用、失败不阻断启动、打点耗时。
 */
package com.nianliu.opshub.bootstrap.preheat;
