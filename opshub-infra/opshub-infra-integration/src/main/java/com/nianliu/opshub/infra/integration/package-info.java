/**
 * 集成层（编码规范 §2.7.7）：外部世界的唯一出口；不反向依赖领域层。
 * 外部 SDK / 客户端只允许在本模块引入（§2.5 机制 4）；对三方 SDK 的一切调用只能经本层 Client（§2.2 铁律）。
 */
package com.nianliu.opshub.infra.integration;
