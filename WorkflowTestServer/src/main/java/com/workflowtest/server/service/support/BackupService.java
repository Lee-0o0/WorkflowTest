package com.workflowtest.server.service.support;

import java.nio.file.Path;

/**
 * 本地数据库备份接口
 */
public interface BackupService {
    /**
     * 创建元数据数据库的一致性备份
     * @param destinationDirectory 备份文件存放目录
     * @return 生成的备份文件路径
     */
    Path createBackup(Path destinationDirectory);
}
