package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 测试项目：顶层资产容器，对应 {@code ts_project} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_project")
public class ProjectEntity extends BaseEntity {
    /** 项目名称 */
    private String name;
    /** 项目说明 */
    private String description;
    /** 是否启用 */
    private Boolean enabled;
}
