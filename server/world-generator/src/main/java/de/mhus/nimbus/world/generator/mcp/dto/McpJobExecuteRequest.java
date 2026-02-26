package de.mhus.nimbus.world.generator.mcp.dto;

import java.util.Map;

public record McpJobExecuteRequest(
        String executor,
        String worldId,
        String layer,
        Map<String, String> parameters,
        Integer timeoutSeconds
) {}
