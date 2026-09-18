package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wt_server_variable")
public class ScopeVariableEntity {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String scopeType;
    private String scopeId;
    private String variableKey;
    private String valueJson;
    private Boolean enabled;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
