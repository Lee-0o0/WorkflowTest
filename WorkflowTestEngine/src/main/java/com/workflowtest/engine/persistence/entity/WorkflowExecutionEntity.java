package com.workflowtest.engine.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作流级执行记录，对应 {@code ts_execution} */
@Data
@TableName("ts_execution")
public class WorkflowExecutionEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属组执行主键（独立运行时为 null） */
    private Long groupExecutionId;
    /** 工作流主键 */
    private Long workflowId;
    /** 执行状态 */
    private String status;
    /** 输入参数 JSON */
    private String inputJson;
    /** 环境变量快照 JSON */
    private String environmentSnapshot;
    /** 工作流运行时变量快照 JSON */
    private String contextSnapshot;
    /** 执行时工作流定义快照 JSON */
    private String workflowSnapshot;
    /** 开始时间 */
    private LocalDateTime startedAt;
    /** 结束时间 */
    private LocalDateTime finishedAt;
    /** 耗时（毫秒） */
    private Long elapsedMs;
    /** 错误摘要 */
    private String errorMessage;
}
