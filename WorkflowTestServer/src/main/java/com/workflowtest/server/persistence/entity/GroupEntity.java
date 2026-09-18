package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wt_server_group")
public class GroupEntity {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String projectId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
