package de.mhus.nimbus.world.ai.config;

import org.graalvm.polyglot.Engine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the shared GraalVM {@link Engine} used by the JavaScript tool.
 *
 * <p>An {@link Engine} is expensive to build but cheap to share, so a single
 * bean is reused across all script executions; each execution creates its own
 * short-lived {@link org.graalvm.polyglot.Context} on top of it. Sharing the
 * engine is thread-safe.
 */
@Configuration
public class ScriptEngineConfig {

    @Bean(destroyMethod = "close")
    public Engine scriptEngine() {
        return Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }
}
