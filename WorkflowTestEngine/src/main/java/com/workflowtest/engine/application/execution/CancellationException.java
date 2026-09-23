package com.workflowtest.engine.application.execution;

import com.workflowtest.engine.support.EngineMessages;

public class CancellationException extends RuntimeException {
    public CancellationException() {
        super(EngineMessages.EXECUTION_CANCELLED);
    }

    public CancellationException(String message) {
        super(message);
    }
}
