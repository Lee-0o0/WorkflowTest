package com.workflowtest.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.listener.ExecutionListener;
import com.workflowtest.engine.application.execution.ExecutionListenerPublisher;
import com.workflowtest.engine.application.execution.WorkflowEngineImpl;
import com.workflowtest.engine.executor.DeleteVarStepExecutor;
import com.workflowtest.engine.executor.DelayStepExecutor;
import com.workflowtest.engine.executor.HttpStepExecutor;
import com.workflowtest.engine.executor.SetVarStepExecutor;
import com.workflowtest.engine.executor.SqlStepExecutor;
import com.workflowtest.engine.executor.support.RuntimeDataSourceManager;
import com.workflowtest.engine.executor.support.RuntimeFileResourceManager;
import com.workflowtest.engine.executor.support.RuntimeResourceManager;
import com.workflowtest.engine.executor.config.StepConfigReader;
import com.workflowtest.engine.runtime.StepExecutorRegistry;
import com.workflowtest.engine.runtime.StepPostProcessor;
import com.workflowtest.engine.runtime.StepRunner;
import com.workflowtest.engine.runtime.VariableTemplateResolver;
import com.workflowtest.engine.spi.ExecutionListenerLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 无 Spring 依赖的 Engine 装配入口。 */
public final class WorkflowEngines {
    private WorkflowEngines() {}

    public static WorkflowEngine create() {
        return create(defaultExecutor(), ExecutionListenerLoader.load());
    }

    public static WorkflowEngine create(List<ExecutionListener> listeners) {
        return create(defaultExecutor(), listeners);
    }

    public static ManagedWorkflowEngine createManaged(List<ExecutionListener> listeners) {
        ExecutorService executor = defaultExecutor();
        WorkflowEngineImpl engine = buildEngine(executor, listeners);
        return new ManagedWorkflowEngineImpl(engine, executor);
    }

    public static WorkflowEngine create(ExecutorService executor, List<ExecutionListener> listeners) {
        return buildEngine(executor, listeners);
    }

    private static WorkflowEngineImpl buildEngine(ExecutorService executor, List<ExecutionListener> listeners) {
        ObjectMapper objectMapper = new ObjectMapper();
        RuntimeDataSourceManager dataSourceManager = new RuntimeDataSourceManager(objectMapper);
        RuntimeFileResourceManager fileResourceManager = new RuntimeFileResourceManager();
        RuntimeResourceManager resourceManager = new RuntimeResourceManager(dataSourceManager, fileResourceManager);
        StepExecutorRegistry executorRegistry = new StepExecutorRegistry(List.of(
                new HttpStepExecutor(objectMapper),
                new SqlStepExecutor(dataSourceManager, objectMapper),
                new DelayStepExecutor(objectMapper),
                new SetVarStepExecutor(objectMapper),
                new DeleteVarStepExecutor(objectMapper)
        ));
        VariableTemplateResolver templateResolver = new VariableTemplateResolver(objectMapper);
        StepPostProcessor postProcessor = new StepPostProcessor(objectMapper);
        ExecutionListenerPublisher listenerPublisher = new ExecutionListenerPublisher(copyListeners(listeners));
        StepConfigReader stepConfigReader = new StepConfigReader(objectMapper);
        StepRunner stepRunner = new StepRunner(objectMapper, templateResolver, executorRegistry, postProcessor,
                listenerPublisher, stepConfigReader);
        return new WorkflowEngineImpl(stepRunner, listenerPublisher, resourceManager, objectMapper, executor);
    }

    private static List<ExecutionListener> copyListeners(List<ExecutionListener> listeners) {
        return listeners == null ? List.of() : List.copyOf(new ArrayList<>(listeners));
    }

    private static ExecutorService defaultExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("workflow-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(4, factory);
    }

    public interface ManagedWorkflowEngine extends WorkflowEngine, AutoCloseable {}

    private static final class ManagedWorkflowEngineImpl implements ManagedWorkflowEngine {
        private final WorkflowEngineImpl delegate;
        private final ExecutorService executor;

        private ManagedWorkflowEngineImpl(WorkflowEngineImpl delegate, ExecutorService executor) {
            this.delegate = delegate;
            this.executor = executor;
        }

        @Override
        public ExecutionHandle execute(com.workflowtest.engine.model.plan.ProjectExecutionPlan plan) {
            return delegate.execute(plan);
        }

        @Override
        public ExecutionHandle execute(com.workflowtest.engine.model.plan.GroupExecutionPlan plan) {
            return delegate.execute(plan);
        }

        @Override
        public ExecutionHandle execute(com.workflowtest.engine.model.plan.WorkflowExecutionPlan plan) {
            return delegate.execute(plan);
        }

        @Override
        public void cancel(String executionId) {
            delegate.cancel(executionId);
        }

        @Override
        public void close() {
            executor.shutdown();
        }
    }
}
