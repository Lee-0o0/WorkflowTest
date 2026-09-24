package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_project_hook_definition")
public class ProjectHookEntity extends BaseEntity {
    private Long projectId;
    private String hookType;
    private Boolean enabled;
}
