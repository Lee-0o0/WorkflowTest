package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分层环境变量：scope_type + scope_id 标识 PROJECT/GROUP/WORKFLOW 作用域，对应 {@code ts_scope_variable} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_scope_variable")
public class ScopeVariableEntity extends BaseEntity {
    /** 作用域类型（PROJECT/GROUP/WORKFLOW） */
    private String scopeType;
    /** 作用域主键（对应项目/组/工作流 id） */
    private Long scopeId;
    /** 变量名 */
    private String variableKey;
    /** 值类型（AUTO 等） */
    private String valueType;
    /** 变量值 JSON */
    private String valueJson;
    /** 是否启用 */
    private Boolean enabled;
}
