package com.workflowtest.engine.support;

import com.workflowtest.engine.api.execution.listener.ExecutionEvent;
import com.workflowtest.engine.api.execution.listener.ExecutionListener;

import java.util.concurrent.atomic.AtomicInteger;

/** 供测试验证 spring.factories 注册的监听器。 */
public class FactoryRegisteredExecutionListener implements ExecutionListener {
    public static final AtomicInteger EVENT_COUNT = new AtomicInteger();

    @Override
    public boolean supports(ExecutionEvent event) {
        return true;
    }

    @Override
    public void onEvent(ExecutionEvent event) {
        EVENT_COUNT.incrementAndGet();
    }

    public static void reset() {
        EVENT_COUNT.set(0);
    }
}
