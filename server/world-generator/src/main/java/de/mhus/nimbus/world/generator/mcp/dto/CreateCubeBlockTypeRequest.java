package de.mhus.nimbus.world.generator.mcp.dto;

import java.util.Map;

public record CreateCubeBlockTypeRequest(
        String blockTypeId,
        String title,
        String description,
        Map<Integer, Object> textures,
        String type,
        Boolean solid,
        Double autoJump
) {}
