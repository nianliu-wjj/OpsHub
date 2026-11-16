/**
 * 聚合根包示例：订单聚合（§2.7.5：每聚合 = 领域模型 + 值对象包 + 仓储接口包；新增聚合照此结构扩展）。
 * 聚合根 / 实体（继承实体基类）直接置于本包，含状态推进与守卫方法（canXxx / driveXxx，§33）。
 */
package com.nianliu.opshub.domain.model.order;
