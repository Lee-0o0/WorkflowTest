package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目资源：按 resource_type 区分（DATASOURCE / FILE 等），对应 {@code ts_project_resource} */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ts_project_resource")
public class ProjectResourceEntity extends BaseEntity {
    /** 所属项目主键 */
    private Long projectId;
    /** 资源类型（DATASOURCE / FILE） */
    private String resourceType;
    /** 资源名称（项目内唯一） */
    private String name;
    /** 类型相关配置 JSON */
    private String configJson;
    /** 是否启用 */
    private Boolean enabled;
}
