package com.workflowtest.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动完成后输出实际监听端口，并写入 data 目录供 Desktop / 脚本读取。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerPortPublisher implements ApplicationListener<ApplicationReadyEvent> {
    private final WorkflowTestProperties properties;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof ServletWebServerApplicationContext webContext)) {
            return;
        }
        int port = webContext.getWebServer().getPort();
        String baseUrl = "http://localhost:" + port;
        log.info("WorkflowTestServer 已启动，监听端口 {}，访问地址 {}", port, baseUrl);
        writePortFile(port);
    }

    private void writePortFile(int port) {
        try {
            Path dataDir = properties.getDataDir().toAbsolutePath().normalize();
            Files.createDirectories(dataDir);
            Path portFile = dataDir.resolve("server.port");
            Files.writeString(portFile, String.valueOf(port), StandardCharsets.UTF_8);
            log.info("服务端口已写入 {}", portFile);
        } catch (Exception e) {
            log.warn("写入 server.port 文件失败: {}", e.getMessage());
        }
    }
}
