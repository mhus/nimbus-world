package de.mhus.nimbus.world.generator.mcp.dto;

public record CreateAnythingRequest(
        String worldId,
        String collection,
        String name,
        String title,
        String description,
        String type,
        Object data
) {}
