package de.mhus.nimbus.world.generator.mcp;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(List<McpToolBean> tools) {
        log.info(
                "Registering {} MCP tools: {}",
                tools.size(),
                tools.stream().map(t -> t.getClass().getSimpleName()).toList());
        return MethodToolCallbackProvider.builder().toolObjects(tools.toArray()).build();
    }
}
