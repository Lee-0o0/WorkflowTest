package com.workflowtest.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("wt_user")
public class UserEntity {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String systemRole;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
