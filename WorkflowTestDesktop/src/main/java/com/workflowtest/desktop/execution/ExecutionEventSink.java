package com.workflowtest.desktop.execution;

import com.workflowtest.engine.listener.ExecutionEvent;
import com.workflowtest.engine.listener.ExecutionListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 桌面端执行事件汇聚点，UI 可临时订阅以接收引擎广播的事件。
 */
@Component
public class ExecutionEventSink implements ExecutionListener {
    private final List<Consumer<ExecutionEvent>> subscribers = new CopyOnWriteArrayList<>();

    public Subscription subscribe(Consumer<ExecutionEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    @Override
    public boolean supports(ExecutionEvent event) {
        return true;
    }

    @Override
    public void onEvent(ExecutionEvent event) {
        for (Consumer<ExecutionEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
