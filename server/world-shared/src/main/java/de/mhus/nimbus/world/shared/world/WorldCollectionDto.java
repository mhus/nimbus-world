package de.mhus.nimbus.world.shared.world;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for World Collection data stored in WAnything.
 * Contains metadata about a world collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorldCollectionDto {

    /**
     * Display title for the collection.
     */
    private String title;

    /**
     * Optional description of the collection.
     */
    private String description;
}
