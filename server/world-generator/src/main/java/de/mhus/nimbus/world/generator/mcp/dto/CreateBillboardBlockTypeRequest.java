package de.mhus.nimbus.world.generator.mcp.dto;

import java.util.Map;

public record CreateBillboardBlockTypeRequest(
        String blockTypeId,
        String title,
        String description,
        Map<String, Object> texture,
        String type,
        Boolean solid,
        Double autoJump
) {}
