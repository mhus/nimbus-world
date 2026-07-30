package de.mhus.nimbus.world.ai.tool;

import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the GraalVM-sandboxed JavaScript tool: functional console output,
 * and — most importantly — that the Rhino RCE surface is closed (no host/Java
 * access, runaway loops are bounded by the wall-clock timeout).
 */
class JavaScriptToolServiceTest {

    private static Engine engine;
    private JavaScriptToolService service;

    @BeforeAll
    static void setupEngine() {
        engine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    @AfterAll
    static void closeEngine() {
        engine.close();
    }

    JavaScriptToolServiceTest() {
        // instance field init happens per test via getService()
    }

    private JavaScriptToolService getService() {
        if (service == null) {
            service = new JavaScriptToolService(engine);
        }
        return service;
    }

    @Test
    void consoleOutputIsReturned() {
        String result = getService().executeJavaScript("console.log(1 + 2 * 3);");
        assertThat(result).isEqualTo("7");
    }

    @Test
    void computationWithoutConsoleFallsBackToExpressionValue() {
        String result = getService().executeJavaScript("40 + 2");
        assertThat(result).isEqualTo("42");
    }

    @Test
    void javaHostAccessIsBlocked() {
        String result = getService().executeJavaScript(
                "var Runtime = Java.type('java.lang.Runtime'); Runtime.getRuntime().exec('id');");
        // Java.type must not exist in the sandbox -> the eval fails, no OS call happens.
        assertThat(result).startsWith("Error:");
        assertThat(result).doesNotContain("uid=");
    }

    @Test
    void hostClassLookupIsBlocked() {
        // Even a benign host class must be unreachable: the Java global object
        // may exist, but every class lookup through it is denied.
        String result = getService().executeJavaScript(
                "Java.type('java.lang.System').exit(1);");
        assertThat(result).startsWith("Error:");
    }

    @Test
    void infiniteLoopIsBoundedByTimeout() {
        long start = System.nanoTime();
        String result = getService().executeJavaScript("while (true) {}");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(result).startsWith("Error:");
        // Must terminate well within the watchdog window, not run forever.
        assertThat(elapsedMs).isLessThan(30_000);
    }
}
