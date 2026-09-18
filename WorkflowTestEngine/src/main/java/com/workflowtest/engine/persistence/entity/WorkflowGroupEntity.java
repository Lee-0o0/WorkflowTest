package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wt_workflow_group")
public class WorkflowGroupEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Boolean enabled;
}
