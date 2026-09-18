package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wt_scope_variable")
public class ScopeVariableEntity extends BaseEntity {
    private String scopeType;
    private String scopeId;
    private String variableKey;
    private String valueType;
    private String valueJson;
    private Boolean sensitive;
    private Boolean enabled;
}
