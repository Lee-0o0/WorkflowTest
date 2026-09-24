package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_project_hook_step")
public class ProjectHookStepEntity extends BaseEntity {
    private Long projectHookId;
    private String stepCode;
    private String stepName;
    private String stepType;
    private Integer sortOrder;
    private Boolean enabled;
    private String configJson;
    private String extractionJson;
    private String assertionJson;
}
