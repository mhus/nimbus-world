package de.mhus.nimbus.shared.dto.universe;

import java.util.List;

public record URegionResponse(String id, String name, String apiUrl, List<String> maintainers) {
}
