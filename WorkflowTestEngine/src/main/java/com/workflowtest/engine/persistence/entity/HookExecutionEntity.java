package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wt_hook_execution")
public class HookExecutionEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String groupExecutionId;
    private String workflowExecutionId;
    private String hookId;
    private String hookType;
    private String status;
    private String outputJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private String errorMessage;
}
