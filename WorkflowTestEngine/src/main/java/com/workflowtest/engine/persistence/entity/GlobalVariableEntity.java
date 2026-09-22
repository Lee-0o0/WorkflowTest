package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 全局环境变量：持久化存储，运行时通过 {@code global.变量名} 引用，对应 {@code ts_global_variable} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_global_variable")
public class GlobalVariableEntity extends BaseEntity {
    /** 变量名（全局唯一） */
    private String variableKey;
    /** 值类型（AUTO 等） */
    private String valueType;
    /** 变量值 JSON */
    private String valueJson;
    /** 是否启用 */
    private Boolean enabled;
}
