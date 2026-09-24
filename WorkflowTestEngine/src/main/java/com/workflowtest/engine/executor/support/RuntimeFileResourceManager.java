package com.workflowtest.engine.executor.support;

import com.workflowtest.engine.model.plan.RuntimeFilePlan;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行期文件资源注册表：由 Server 通过执行计划 {@code resources.files} 传入，Engine 运行时合并注册。
 */
public class RuntimeFileResourceManager {
    private volatile Map<Long, FileRuntimeDefinition> definitions = Map.of();
    private volatile Map<String, Long> idsByName = Map.of();

    /** 合并执行计划中的文件资源定义（同 id 后者覆盖前者）。 */
    public void merge(List<RuntimeFilePlan> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        Map<Long, FileRuntimeDefinition> nextDefinitions = new ConcurrentHashMap<>(definitions);
        Map<String, Long> nextIdsByName = new ConcurrentHashMap<>(idsByName);
        for (RuntimeFilePlan file : files) {
            if (file.id() <= 0) {
                continue;
            }
            nextDefinitions.put(file.id(), new FileRuntimeDefinition(
                    file.id(),
                    file.name(),
                    file.path(),
                    normalizeEncoding(file.encoding())));
            if (file.name() != null && !file.name().isBlank()) {
                nextIdsByName.put(file.name(), file.id());
            }
        }
        definitions = Map.copyOf(nextDefinitions);
        idsByName = Map.copyOf(nextIdsByName);
    }

    public void clear() {
        definitions = Map.of();
        idsByName = Map.of();
    }

    public FileRuntimeDefinition definition(long id) {
        FileRuntimeDefinition file = definitions.get(id);
        if (file == null) {
            throw new IllegalArgumentException("执行计划中未找到文件资源: " + id);
        }
        return file;
    }

    public FileRuntimeDefinition definitionByName(String name) {
        Long id = idsByName.get(name);
        if (id == null) {
            throw new IllegalArgumentException("执行计划中未找到文件资源: " + name);
        }
        return definition(id);
    }

    public String readText(long id) {
        FileRuntimeDefinition file = definition(id);
        return readText(file);
    }

    public String readTextByName(String name) {
        return readText(definitionByName(name));
    }

    private String readText(FileRuntimeDefinition file) {
        try {
            return Files.readString(Path.of(file.path()), Charset.forName(file.encoding()));
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件资源失败: " + file.name(), e);
        }
    }

    private static String normalizeEncoding(String encoding) {
        return encoding == null || encoding.isBlank() ? "UTF-8" : encoding;
    }
}
