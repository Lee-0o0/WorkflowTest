package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wt_server_workflow")
public class WorkflowEntity {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String groupId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer revision;
    private String draftJson;
    private Boolean enabled;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
