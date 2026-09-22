package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 钩子步骤：语义与工作流步骤一致，对应 {@code ts_hook_step} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_hook_step")
public class HookStepEntity extends BaseEntity {
    /** 所属钩子主键 */
    private Long hookId;
    /** 步骤编码（钩子内唯一） */
    private String stepCode;
    /** 步骤名称 */
    private String stepName;
    /** 步骤类型 */
    private String stepType;
    /** 执行顺序 */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;
    /** 步骤配置 JSON */
    private String configJson;
    /** 提取规则 JSON */
    private String extractionJson;
    /** 断言规则 JSON */
    private String assertionJson;
}
