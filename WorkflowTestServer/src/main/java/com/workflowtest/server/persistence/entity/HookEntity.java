package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 组级钩子：组前置/组后置，对应 {@code ts_hook_definition} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_hook_definition")
public class HookEntity extends BaseEntity {
    /** 所属组主键 */
    private Long groupId;
    /** 钩子类型（BEFORE_GROUP/AFTER_GROUP） */
    private String hookType;
    /** 是否启用 */
    private Boolean enabled;
}
