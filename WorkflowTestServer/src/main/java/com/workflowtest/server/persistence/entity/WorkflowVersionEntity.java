package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wt_workflow_version")
public class WorkflowVersionEntity {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String workflowId;
    private Integer versionNo;
    private Integer revision;
    private String snapshotJson;
    private String checksum;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
