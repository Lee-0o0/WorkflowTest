package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public record StepResult(JsonNode output, JsonNode request, JsonNode response, long elapsedMs) {
    /** 步骤输出 JSON 字段名 */
    public static final class OutputField {
        public static final String REQUEST = "request";
        public static final String RESPONSE = "response";
        public static final String ELAPSED_MS = "elapsedMs";
        public static final String STATUS = "status";
        public static final String ROWS = "rows";
        public static final String ROW_COUNT = "rowCount";
        public static final String UPDATE_COUNT = "updateCount";

        private OutputField() {}
    }
}
