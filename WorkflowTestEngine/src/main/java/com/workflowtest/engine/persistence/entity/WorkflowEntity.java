package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流：隶属于组，对应 {@code ts_workflow} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_workflow")
public class WorkflowEntity extends BaseEntity {
    /** 所属组主键 */
    private Long groupId;
    /** 工作流名称 */
    private String name;
    /** 工作流说明 */
    private String description;
    /** 显示与执行顺序（升序） */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;
}
