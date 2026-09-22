package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 步骤执行记录，对应 {@code ts_step_execution} */
@Data
@TableName("ts_step_execution")
public class StepExecutionEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工作流执行主键 */
    private Long executionId;
    /** 钩子执行主键 */
    private Long hookExecutionId;
    /** 步骤定义主键 */
    private Long stepId;
    /** 步骤编码 */
    private String stepCode;
    /** 执行阶段（如 WORKFLOW、BEFORE_GROUP） */
    private String phase;
    /** 执行状态 */
    private String status;
    /** 请求快照 JSON */
    private String requestJson;
    /** 响应快照 JSON */
    private String responseJson;
    /** 输出 JSON */
    private String outputJson;
    /** 提取结果 JSON */
    private String extractedJson;
    /** 断言结果 JSON */
    private String assertionJson;
    /** 开始时间 */
    private LocalDateTime startedAt;
    /** 结束时间 */
    private LocalDateTime finishedAt;
    /** 耗时（毫秒） */
    private Long elapsedMs;
    /** 错误摘要 */
    private String errorMessage;
}
