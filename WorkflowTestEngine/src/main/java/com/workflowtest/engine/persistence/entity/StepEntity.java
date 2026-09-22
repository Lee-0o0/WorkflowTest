package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流步骤：HTTP / SQL / DELAY 等类型及 JSON 配置，对应 {@code ts_step_definition} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_step_definition")
public class StepEntity extends BaseEntity {
    /** 所属工作流主键 */
    private Long workflowId;
    /** 步骤编码（工作流内唯一） */
    private String stepCode;
    /** 步骤名称 */
    private String stepName;
    /** 步骤类型（HTTP/SQL/DELAY） */
    private String stepType;
    /** 执行顺序（升序） */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;
    /** 步骤配置 JSON */
    private String configJson;
    /** 变量提取规则 JSON 数组 */
    private String extractionJson;
    /** 断言规则 JSON 数组 */
    private String assertionJson;
}
