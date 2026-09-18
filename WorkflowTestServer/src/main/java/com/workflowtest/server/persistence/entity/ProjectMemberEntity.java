package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wt_project_member")
public class ProjectMemberEntity {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String projectId;
    private String userId;
    private String projectRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
