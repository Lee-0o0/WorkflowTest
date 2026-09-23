package com.workflowtest.engine.api.execution.listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 执行监听器组合与按事件类型过滤的工具类。
 */
public final class ExecutionListeners {
    private ExecutionListeners() {}

    /** 将多个监听器合并为一个，按 {@link Order#getOrder()} 升序依次通知。 */
    public static ExecutionListener broadcast(List<ExecutionListener> listeners) {
        List<ExecutionListener> active = sorted(listeners);
        if (active.isEmpty()) {
            return noop();
        }
        if (active.size() == 1) {
            return active.getFirst();
        }
        return new ExecutionListener() {
            @Override
            public boolean supports(ExecutionEvent event) {
                return active.stream().anyMatch(listener -> listener.supports(event));
            }

            @Override
            public void onEvent(ExecutionEvent event) {
                active.stream()
                        .filter(listener -> listener.supports(event))
                        .forEach(listener -> listener.onEvent(event));
            }
        };
    }

    public static ExecutionListener broadcast(ExecutionListener... listeners) {
        if (listeners == null || listeners.length == 0) {
            return noop();
        }
        return broadcast(List.of(listeners));
    }

    /** 仅当事件类型匹配时才通知监听器。 */
    public static ExecutionListener when(ExecutionEventType type, ExecutionListener listener) {
        Objects.requireNonNull(type, "type");
        ExecutionListener delegate = listener == null ? noop() : listener;
        return withOrder(delegate, type, event -> delegate.onEvent(event));
    }

    public static ExecutionListener when(ExecutionEventType type, Consumer<ExecutionEvent> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        return when(type, new ExecutionListener() {
            @Override
            public boolean supports(ExecutionEvent event) {
                return event.type() == type;
            }

            @Override
            public void onEvent(ExecutionEvent event) {
                handler.accept(event);
            }
        });
    }

    /** 当事件类型属于给定集合时才通知监听器。 */
    public static ExecutionListener whenAny(Set<ExecutionEventType> types, ExecutionListener listener) {
        Objects.requireNonNull(types, "types");
        Set<ExecutionEventType> accepted = EnumSet.copyOf(types);
        ExecutionListener delegate = listener == null ? noop() : listener;
        return withOrder(delegate, accepted, event -> delegate.onEvent(event));
    }

    public static ExecutionListener whenAny(Set<ExecutionEventType> types, Consumer<ExecutionEvent> handler) {
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(handler, "handler");
        Set<ExecutionEventType> accepted = EnumSet.copyOf(types);
        return new ExecutionListener() {
            @Override
            public boolean supports(ExecutionEvent event) {
                return accepted.contains(event.type());
            }

            @Override
            public void onEvent(ExecutionEvent event) {
                handler.accept(event);
            }
        };
    }

    /** 构建按事件类型分派的监听器列表。 */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ExecutionListener> listeners = new ArrayList<>();

        public Builder add(ExecutionListener listener) {
            if (listener != null) {
                listeners.add(listener);
            }
            return this;
        }

        public Builder on(ExecutionEventType type, ExecutionListener listener) {
            return add(when(type, listener));
        }

        public Builder on(ExecutionEventType type, Consumer<ExecutionEvent> handler) {
            return add(when(type, handler));
        }

        public Builder onAny(Set<ExecutionEventType> types, ExecutionListener listener) {
            return add(whenAny(types, listener));
        }

        public Builder onAny(Set<ExecutionEventType> types, Consumer<ExecutionEvent> handler) {
            return add(whenAny(types, handler));
        }

        public List<ExecutionListener> buildList() {
            return sorted(listeners);
        }

        public ExecutionListener build() {
            return broadcast(listeners);
        }
    }

    public static List<ExecutionListener> copyOf(Collection<ExecutionListener> listeners) {
        return sorted(listeners);
    }

    /** 按 order 升序依次通知 supports 匹配的监听器。 */
    public static void notify(List<ExecutionListener> listeners, ExecutionEvent event) {
        if (listeners == null || listeners.isEmpty() || event == null) {
            return;
        }
        for (ExecutionListener listener : listeners) {
            try {
                if (listener.supports(event)) {
                    listener.onEvent(event);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void notify(List<ExecutionListener> listeners, ExecutionEventType type,
                              String executionId, String code, String message) {
        notify(listeners, ExecutionEvent.of(type, executionId, code, message));
    }

    public static void notify(List<ExecutionListener> listeners, ExecutionEventType type,
                              String executionId, String code, String message, ExecutionEventContext context) {
        notify(listeners, ExecutionEvent.of(type, executionId, code, message, context));
    }

    private static ExecutionListener noop() {
        return new ExecutionListener() {
            @Override
            public boolean supports(ExecutionEvent event) {
                return false;
            }

            @Override
            public void onEvent(ExecutionEvent event) {
            }
        };
    }

    private static List<ExecutionListener> sorted(Collection<ExecutionListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return List.of();
        }
        return listeners.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ExecutionListener::getOrder))
                .toList();
    }

    private static ExecutionListener withOrder(ExecutionListener delegate, ExecutionEventType type,
                                               Consumer<ExecutionEvent> handler) {
        return new ExecutionListener() {
            @Override
            public boolean supports(ExecutionEvent event) {
                return event.type() == type && delegate.supports(event);
            }

            @Override
            public void onEvent(ExecutionEvent event) {
                handler.accept(event);
            }

            @Override
            public int getOrder() {
                return delegate.getOrder();
            }
        };
    }

    private static ExecutionListener withOrder(ExecutionListener delegate, Set<ExecutionEventType> types,
                                               Consumer<ExecutionEvent> handler) {
        return new ExecutionListener() {
            @Override
            public boolean supports(ExecutionEvent event) {
                return types.contains(event.type()) && delegate.supports(event);
            }

            @Override
            public void onEvent(ExecutionEvent event) {
                handler.accept(event);
            }

            @Override
            public int getOrder() {
                return delegate.getOrder();
            }
        };
    }
}
