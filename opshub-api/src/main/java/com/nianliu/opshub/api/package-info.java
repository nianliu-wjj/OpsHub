/**
 * 接口契约层（编码规范 §2.7.2）：对调用方的稳定承诺。
 * 按业务域分包，每域固定 request + dto + enums 三件套；纯 POJO + JSR-303 注解（§11.1）；零实现依赖、独立版本发布（§2.4 规则 3）。
 */
package com.nianliu.opshub.api;
