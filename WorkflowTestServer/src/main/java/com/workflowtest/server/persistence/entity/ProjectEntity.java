package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wt_server_project")
public class ProjectEntity {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String projectKey;
    private String name;
    private String description;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
