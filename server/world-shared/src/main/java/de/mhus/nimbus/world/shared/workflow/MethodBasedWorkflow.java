package de.mhus.nimbus.world.shared.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

@Slf4j
public abstract class MethodBasedWorkflow implements Workflow {

    @Override
    public void event(WorkflowContext context, WorkflowEvent event) throws WorkflowException {
            String status = context.getStatus();
            String eventType = event.getEvent();
            if (WorkflowEvent.SUCCESS.equals(eventType)) {
                onSuccess(context, status, event.getData());
            } else if (WorkflowEvent.FAILURE.equals(eventType)) {
                onFailure(context, status, event.getData());
            } else {
                log.warn("Unknown event type '{}' for workflow '{}'", eventType, context.getWorkflowName());
                context.updateWorkflowStatus(StatusRecord.TERMINATED);
            }
    }

    protected abstract void onFailure(WorkflowContext context, String status, Map<String, String> data);

    private void onSuccess(WorkflowContext context, String status, Map<String, String> data) {
        final var maybeEventHandler =
                Arrays.stream(ReflectionUtils.getAllDeclaredMethods(getClass()))
                        .filter(
                                method -> {
                                    final var anno = method.getAnnotation(OnSuccess.class);
                                    if (anno == null) {
                                        return false;
                                    }
                                    return (anno.value().equals(status));
                                })
                        .sorted(
                                (m1, m2) ->
                                        Integer.compare(
                                                m2.getParameterCount(), m1.getParameterCount()))
                        .findFirst();

        if (maybeEventHandler.isPresent()) {
            final var handler = maybeEventHandler.get();
            if (!handler.canAccess(this)) {
                handler.setAccessible(true);
            }
            try {
                ReflectionUtils.invokeMethod(
                        handler, this, findArgsForMethod(handler, context, status, data));
            } catch (Exception e) {
                log.warn(
                        "Error invoking event handler {} for status {}, cancel workflow",
                        handler,
                        status,
                        e);
                context.updateWorkflowStatus(StatusRecord.FAILED);
            }
        } else {
            log.warn(
                    "No successful handler found for workflow '{}' with status '{}'",
                    context.getWorkflowName(),
                    status);
            onUnhandledEvent(context, status, data);
        }

    }

    protected void onUnhandledEvent(WorkflowContext context, String status, Map<String, String> data) {
        context.addNote("Unhandled success event for status: " + status);
        context.updateWorkflowStatus(StatusRecord.TERMINATED);
    }

    private Object[] findArgsForMethod(Method method, Object... args) {
        final var result = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++) {
            final var paramType = method.getParameterTypes()[i];
            for (var arg : args) {
                if (paramType.isAssignableFrom(arg.getClass())) {
                    result[i] = arg;
                    break;
                }
            }
        }
        log.debug("Resolved method args: {}", Arrays.toString(result));
        return result;
    }

}
