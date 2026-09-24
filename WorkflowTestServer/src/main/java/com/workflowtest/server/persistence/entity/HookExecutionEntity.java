package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 钩子执行记录，对应 {@code ts_hook_execution} */
@Data
@TableName("ts_hook_execution")
public class HookExecutionEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 组执行主键 */
    private Long groupExecutionId;
    /** 工作流执行主键 */
    private Long workflowExecutionId;
    /** 钩子定义主键 */
    private Long hookId;
    /** 钩子类型 */
    private String hookType;
    /** 执行状态 */
    private String status;
    /** 钩子输出 JSON */
    private String outputJson;
    /** 开始时间 */
    private LocalDateTime startedAt;
    /** 结束时间 */
    private LocalDateTime finishedAt;
    /** 耗时（毫秒） */
    private Long elapsedMs;
    /** 错误摘要 */
    private String errorMessage;
}
