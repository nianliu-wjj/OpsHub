/**
 * 仓储实现（§20.3）：实现领域模型层定义的仓储接口；保存 / 更新骨架 = 可选事务包裹 + 异常转译（唯一键冲突 → 幂等异常，§20.2）。
 */
package com.nianliu.opshub.infra.dal.repository;
