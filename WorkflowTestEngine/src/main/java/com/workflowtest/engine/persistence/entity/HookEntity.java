package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wt_hook_definition")
public class HookEntity extends BaseEntity {
    private String ownerType;
    private String ownerId;
    private String hookType;
    private Boolean enabled;
    private String failureStrategy;
}
