package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流组：隶属于项目，按 sort_order 排序与串行执行，对应 {@code ts_workflow_group} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_workflow_group")
public class WorkflowGroupEntity extends BaseEntity {
    /** 所属项目主键 */
    private Long projectId;
    /** 组名称 */
    private String name;
    /** 组说明 */
    private String description;
    /** 显示与执行顺序（升序） */
    private Integer sortOrder;
    /** 是否启用 */
    private Boolean enabled;
}
