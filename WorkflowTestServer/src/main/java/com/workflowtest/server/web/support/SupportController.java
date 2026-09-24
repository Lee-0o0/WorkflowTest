package com.workflowtest.server.web.support;

import com.workflowtest.server.service.support.BackupService;
import com.workflowtest.server.service.support.EnvironmentPreviewService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.BackupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {
    private final EnvironmentPreviewService environmentPreviewService;
    private final BackupService backupService;

    @GetMapping("/environment/preview")
    public ApiResponse<?> previewEnvironment(@RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) Long groupId,
                                             @RequestParam(required = false) Long workflowId) {
        return ApiResponse.ok(environmentPreviewService.preview(projectId, groupId, workflowId));
    }

    @PostMapping("/backup")
    public ApiResponse<Map<String, String>> backup(@RequestBody BackupRequest request) {
        Path destination = request.destinationDirectory() == null || request.destinationDirectory().isBlank()
                ? Path.of(System.getProperty("user.home"), ".workflowtest", "backups")
                : Path.of(request.destinationDirectory());
        Path backupFile = backupService.createBackup(destination);
        return ApiResponse.ok(Map.of("path", backupFile.toString()));
    }
}
