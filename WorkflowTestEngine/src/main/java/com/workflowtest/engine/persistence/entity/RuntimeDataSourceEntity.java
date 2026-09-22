package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wt_runtime_datasource")
public class RuntimeDataSourceEntity extends BaseEntity {
    private Long projectId;
    private String name;
    private String driverClass;
    private String jdbcUrl;
    private String username;
    private String encryptedPassword;
    private String optionsJson;
    private Boolean allowDangerousSql;
    private Boolean enabled;
}
