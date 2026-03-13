package de.mhus.nimbus.world.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;

/**
 * AI Tool for executing Python3 code.
 * Checks python3 availability on startup.
 */
@Slf4j
@Service
public class PythonToolService {

    private boolean available;

    @PostConstruct
    void init() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            available = exitCode == 0;
            if (available) {
                log.info("Python3 is available");
            } else {
                log.warn("Python3 is not available (exit code {})", exitCode);
            }
        } catch (Exception e) {
            log.warn("Python3 is not available: {}", e.getMessage());
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    @Tool("Execute Python3 code for calculations, data processing, or scripting. Result MUST be printed to console.")
    public String executePython(
            @P("Python code to execute, result MUST be printed to console") String pythonCode
    ) {
        if (!available) {
            return "Error: Python3 is not available on this server";
        }
        log.info("Executing Python code: {}", pythonCode);
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "-c", pythonCode);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringWriter output = new StringWriter();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.write(line);
                    output.write("\n");
                }
            }

            int exitCode = process.waitFor();
            String result = output.toString();
            if (exitCode != 0) {
                return "Error (exit code " + exitCode + "): " + result;
            }
            log.info("Python execution result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Error executing Python code", e);
            return "Error: " + e.getMessage();
        }
    }
}
