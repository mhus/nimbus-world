package de.mhus.nimbus.world.generator.mcp.dto;

import java.util.List;
import java.util.Map;

public record ImportLayerModelRequest(
        String name,
        String title,
        String licenseSource,
        String licenseType,
        String licenseAuthor,
        Integer mountX,
        Integer mountY,
        Integer mountZ,
        Integer rotation,
        Integer order,
        Integer sizeX,
        Integer sizeY,
        Integer sizeZ,
        Map<String, String> groups,
        Map<String, String> parameters,
        List<BlockRequest> blocks
) {}
