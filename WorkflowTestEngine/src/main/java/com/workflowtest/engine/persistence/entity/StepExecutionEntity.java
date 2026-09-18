package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wt_step_execution")
public class StepExecutionEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String executionId;
    private String hookExecutionId;
    private String stepId;
    private String stepCode;
    private String phase;
    private String status;
    private String requestJson;
    private String responseJson;
    private String outputJson;
    private String extractedJson;
    private String assertionJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private String errorMessage;
}
