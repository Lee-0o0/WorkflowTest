package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wt_group_execution")
public class GroupExecutionEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String groupId;
    private String status;
    private String inputJson;
    private String environmentSnapshot;
    private String contextSnapshot;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private String errorMessage;
}
