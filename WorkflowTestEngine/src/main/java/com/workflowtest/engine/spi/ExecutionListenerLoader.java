package com.workflowtest.engine.spi;

import com.workflowtest.engine.listener.ExecutionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class ExecutionListenerLoader {
    private ExecutionListenerLoader() {}

    public static List<ExecutionListener> load() {
        List<ExecutionListener> listeners = new ArrayList<>();
        ServiceLoader.load(ExecutionListener.class).forEach(listeners::add);
        return List.copyOf(listeners);
    }
}
