package com.workflowtest.engine.executor.support;

import com.workflowtest.engine.model.plan.RuntimeFilePlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeFileResourceManagerTest {

    @TempDir
    Path tempDir;

    private final RuntimeFileResourceManager manager = new RuntimeFileResourceManager();

    @AfterEach
    void tearDown() {
        manager.clear();
    }

    @Test
    void mergeAndReadByName() throws Exception {
        Path file = tempDir.resolve("payload.json");
        Files.writeString(file, "{\"ok\":true}");

        manager.merge(List.of(new RuntimeFilePlan(1L, "payload", file.toString(), "UTF-8")));

        assertEquals("{\"ok\":true}", manager.readTextByName("payload"));
        assertEquals("{\"ok\":true}", manager.readText(1L));
    }

    @Test
    void missingResourceThrows() {
        assertThrows(IllegalArgumentException.class, () -> manager.readTextByName("missing"));
    }
}
