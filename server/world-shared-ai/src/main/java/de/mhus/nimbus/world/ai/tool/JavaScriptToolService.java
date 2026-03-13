package de.mhus.nimbus.world.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * AI Tool for executing JavaScript code via the Rhino engine.
 * Useful for calculations, sorting, filtering, aggregating, and string processing.
 */
@Slf4j
@Service
public class JavaScriptToolService {

    @Tool("Execute JavaScript code for calculations, sorting, filtering, aggregating, or string processing. The result MUST be printed to console.")
    public String executeJavaScript(
            @P("JavaScript code to execute, result MUST be printed to console") String javaScriptCode
    ) {
        log.info("Executing JavaScript code: {}", javaScriptCode);
        RhinoScriptEngineFactory factory = new RhinoScriptEngineFactory();
        ScriptEngine jsEngine = factory.getScriptEngine();
        try {
            return String.valueOf(jsEngine.eval(javaScriptCode));
        } catch (ScriptException e) {
            log.error("JavaScript execution error", e);
            return "Error: " + e.getMessage();
        }
    }
}
