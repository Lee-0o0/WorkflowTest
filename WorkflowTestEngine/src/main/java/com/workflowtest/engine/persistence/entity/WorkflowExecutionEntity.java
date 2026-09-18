package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wt_execution")
public class WorkflowExecutionEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String groupExecutionId;
    private String workflowId;
    private String status;
    private String inputJson;
    private String environmentSnapshot;
    private String contextSnapshot;
    private String workflowSnapshot;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private String errorMessage;
}
