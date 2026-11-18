package com.nianliu.opshub.common.validation;

/**
 * 校验分组建议集（「新增才校 / 修改才校」场景组语义）。
 *
 * <p>三层声明协作（《工程实现复现手册》§4.2）：
 * <ol>
 *     <li>约束侧：字段约束声明所属 {@code groups}（空 = 全场景无条件生效）；</li>
 *     <li>请求侧：模板回调 {@code validationGroups()} 声明本次请求组（空 = 不启用组筛选）；</li>
 *     <li>引擎判定：请求组为空 → 全部约束执行；非空 → 「无条件约束」或
 *         「约束组 ∩ 请求组 ≠ 空」者执行。</li>
 * </ol>
 *
 * <p>业务自建组按域声明（如 {@code OrderValidationGroups.Reject}），组的语义对外稳定。
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public interface ValidationGroups {

    /** 新增场景组 */
    interface Create {
    }

    /** 修改场景组 */
    interface Update {
    }

    /** 删除场景组 */
    interface Delete {
    }

    /** 查询场景组 */
    interface Query {
    }
}
