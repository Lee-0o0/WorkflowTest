package com.workflowtest.engine.application.support;

import com.workflowtest.engine.api.support.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final DataSource dataSource;

    @Override
    public Path createBackup(Path destinationDirectory) {
        try {
            Path directory = destinationDirectory.toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve("workflow-test-" + FILE_TIME.format(LocalDateTime.now()) + ".db");
            String escaped = target.toString().replace("'", "''");
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + escaped + "'");
            }
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("备份失败: " + e.getMessage(), e);
        }
    }
}
