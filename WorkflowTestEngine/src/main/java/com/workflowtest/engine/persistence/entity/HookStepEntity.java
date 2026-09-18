package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wt_hook_step")
public class HookStepEntity extends BaseEntity {
    private String hookId;
    private String stepCode;
    private String stepName;
    private String stepType;
    private Integer sortOrder;
    private Boolean enabled;
    private String configJson;
    private String extractionJson;
    private String assertionJson;
    private String failureStrategy;
    private String retryJson;
}
