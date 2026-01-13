package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB Asset Entity. Speichert kleine Binärdaten direkt (content) bis zur konfigurierten Grenze.
 * Größere Inhalte werden über einen externen StorageService ausgelagert und via storageId referenziert.
 * Metadaten aus *.info Dateien werden in publicData gespeichert.
 */
@Document(collection = "s_assets")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "region_world_path_idx", def = "{ 'worldId': 1, 'path': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SAsset implements Identifiable {

    @Id
    private String id;

    /** Voller Pfad inkl. Dateiname (Unique innerhalb Region/Welt-Kombination). */
    @Indexed
    private String path;

    /** Nur Dateiname extrahiert aus path. */
    @Indexed
    private String name;

    /**
     * Original uncompressed size of the content in bytes.
     * This is always the actual file size, regardless of compression in storage.
     */
    private long size;

    /** Falls ausgelagert im StorageService. */
    @Indexed
    private String storageId;

    /**
     * Indicates whether the storage data is compressed.
     * Default is false for backward compatibility (uncompressed).
     * If field is not set in DB (legacy documents), defaults to false (uncompressed).
     */
    private boolean compressed;

    /**
     * Public metadata from *.info files.
     * Contains description, dimensions, color, mimeType, etc.
     */
    private AssetMetadata publicData;

    @CreatedDate
    private Instant createdAt;

    private String createdBy;

    @Builder.Default
    private boolean enabled = true;

    /** Optional: Welt Identifier. */
    @Indexed
    private String worldId; // kann null sein

}

