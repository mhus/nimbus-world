package de.mhus.nimbus.world.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI Tool for executing JavaScript code via the sandboxed GraalVM JavaScript engine.
 * Useful for calculations, sorting, filtering, aggregating, and string processing.
 *
 * <p>The engine runs the LLM-supplied code with a hardened GraalVM
 * {@link Context}: no host (Java) access, no I/O, no threads, no native access,
 * no host class loading/lookup and no environment access. This removes the
 * Rhino LiveConnect attack surface (e.g. {@code java.lang.Runtime.exec(...)}),
 * so a manipulated prompt can no longer reach the operating system.
 *
 * <p>Runaway code is bounded twice: a statement limit caps the number of
 * executed statements and a wall-clock watchdog cancels the context after a
 * hard timeout, so an infinite loop can no longer block a request thread
 * indefinitely.
 */
@Slf4j
@Service
public class JavaScriptToolService {

    /** Maximum number of JS statements a single execution may run. */
    private static final long STATEMENT_LIMIT = 10_000_000L;

    /** Hard wall-clock limit for a single execution. */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Upper bound on captured console output returned to the caller. */
    private static final int MAX_OUTPUT_CHARS = 100_000;

    private final Engine engine;

    public JavaScriptToolService(Engine engine) {
        this.engine = engine;
    }

    @Tool("Execute JavaScript code for calculations, sorting, filtering, aggregating, or string processing. The result MUST be printed to console.")
    public String executeJavaScript(
            @P("JavaScript code to execute, result MUST be printed to console") String javaScriptCode
    ) {
        log.info("Executing JavaScript code: {}", javaScriptCode);

        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ResourceLimits limits = ResourceLimits.newBuilder()
                .statementLimit(STATEMENT_LIMIT, null)
                .build();

        Context context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.NONE)
                .allowAllAccess(false)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowHostClassLoading(false)
                .allowHostClassLookup(name -> false)
                .allowIO(IOAccess.NONE)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .resourceLimits(limits)
                .out(capturedOutput)
                .err(capturedOutput)
                .build();

        // Run the eval on a dedicated daemon thread so the wall-clock timeout
        // can interrupt a runaway script via context.close(true).
        ExecutorService watchdog = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "js-tool-eval");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Source source = Source.newBuilder("js", javaScriptCode, "<js-tool>").buildLiteral();
            Future<Value> future = watchdog.submit(() -> context.eval(source));
            try {
                Value result = future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                return buildResult(capturedOutput, result);
            } catch (TimeoutException e) {
                future.cancel(true);
                context.close(true);
                log.warn("JavaScript execution timed out after {}ms", TIMEOUT.toMillis());
                return "Error: script timed out after " + TIMEOUT.toMillis() + "ms";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                context.close(true);
                return "Error: script execution interrupted";
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof PolyglotException pe && pe.isResourceExhausted()) {
                    log.warn("JavaScript execution exceeded resource limit: {}", pe.getMessage());
                    return "Error: script exceeded resource limit (statement cap)";
                }
                log.error("JavaScript execution error", cause);
                return "Error: " + cause.getMessage();
            }
        } finally {
            watchdog.shutdownNow();
            try {
                context.close();
            } catch (Exception e) {
                log.debug("Context close after execution raised: {}", e.toString());
            }
        }
    }

    /**
     * Prefers the console output (the tool contract asks the model to print the
     * result) and falls back to the evaluated expression value when nothing was
     * printed, preserving the previous return behaviour.
     */
    private static String buildResult(ByteArrayOutputStream capturedOutput, Value result) {
        String consoleOutput = capturedOutput.toString(StandardCharsets.UTF_8).strip();
        if (!consoleOutput.isEmpty()) {
            if (consoleOutput.length() > MAX_OUTPUT_CHARS) {
                return consoleOutput.substring(0, MAX_OUTPUT_CHARS) + "\n... (output truncated)";
            }
            return consoleOutput;
        }
        if (result == null || result.isNull()) {
            return "undefined";
        }
        return result.toString();
    }
}
