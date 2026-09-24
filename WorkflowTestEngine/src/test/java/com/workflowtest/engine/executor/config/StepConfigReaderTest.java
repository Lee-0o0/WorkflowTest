package com.workflowtest.engine.executor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.support.EngineMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepConfigReaderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StepConfigReader reader;

    @BeforeEach
    void setUp() {
        reader = new StepConfigReader(objectMapper);
    }

    @Test
    void read_httpConfig() throws Exception {
        StepConfig config = reader.read(StepType.HTTP, objectMapper.readTree("""
                {"method":"POST","url":"http://example.com","headers":{"X-Test":"1"}}
                """));

        assertThat(config).isInstanceOf(HttpStepConfig.class);
        HttpStepConfig http = (HttpStepConfig) config;
        assertThat(http.resolvedMethod()).isEqualTo("POST");
        assertThat(http.url()).isEqualTo("http://example.com");
        assertThat(http.headers()).containsEntry("X-Test", "1");
    }

    @Test
    void read_delayConfig() throws Exception {
        StepConfig config = reader.read(StepType.DELAY, objectMapper.readTree("{\"millis\":2000}"));

        assertThat(config).isInstanceOf(DelayStepConfig.class);
        assertThat(((DelayStepConfig) config).millis()).isEqualTo(2000L);
    }

    @Test
    void read_sqlConfig_rejectsMissingDatasource() {
        assertThatThrownBy(() -> reader.read(StepType.SQL, objectMapper.readTree("{\"sql\":\"select 1\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EngineMessages.SQL_DATASOURCE_REQUIRED);
    }
}
