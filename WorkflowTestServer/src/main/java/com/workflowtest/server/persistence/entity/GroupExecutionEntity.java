package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 组级执行记录，对应 {@code ts_group_execution} */
@Data
@TableName("ts_group_execution")
public class GroupExecutionEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 项目主键 */
    private Long projectId;
    /** 组主键 */
    private Long groupId;
    /** 执行状态 */
    private String status;
    /** 输入参数 JSON */
    private String inputJson;
    /** 环境变量快照 JSON */
    private String environmentSnapshot;
    /** 组级运行时变量快照 JSON */
    private String contextSnapshot;
    /** 开始时间 */
    private LocalDateTime startedAt;
    /** 结束时间 */
    private LocalDateTime finishedAt;
    /** 耗时（毫秒） */
    private Long elapsedMs;
    /** 错误摘要 */
    private String errorMessage;
}
