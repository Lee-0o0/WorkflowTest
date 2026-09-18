package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wt_workflow")
public class WorkflowEntity extends BaseEntity {
    private String groupId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Boolean enabled;
}
