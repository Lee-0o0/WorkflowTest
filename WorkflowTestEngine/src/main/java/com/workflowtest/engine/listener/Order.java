package com.workflowtest.engine.listener;

/**
 * 监听器执行顺序，值越小越先执行（与 Spring {@code Ordered} 语义一致）。
 */
public interface Order {
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;
    int DEFAULT_ORDER = 0;

    default int getOrder() {
        return DEFAULT_ORDER;
    }
}
