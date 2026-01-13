package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import de.mhus.nimbus.shared.types.UserId;
import de.mhus.nimbus.shared.user.ActorRoles;
import de.mhus.nimbus.shared.user.WorldRoles;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "worlds")
@ActualSchemaVersion("1.0.0")
// @GenerateTypeScript("entities") - need to clarify WEntryPoint type
public class WWorld implements Identifiable {

    @Id
    private String id; // MongoDB ObjectId als String oder UUID

    @Indexed(unique = true)
    private String worldId; // Externe World ID

    @Indexed
    private String regionId; // Zugehörige Region ID

    private String name;

    private String description;

    /**
     * Public data containing the generated WorldInfo DTO.
     * This is what gets serialized and sent to clients.
     */
    @TypeScript(type="WorldInfo")
    private WorldInfo publicData; // eingebettete Struktur aus generated Modul

    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private boolean enabled = true; // Standardmäßig aktiviert
    private String parent; // optionale Referenz auf übergeordnete Welt / Gruppe

    // Zugriff / Berechtigungen
    @Builder.Default
    private Set<String> owner = Set.of(); // liste von userIds
    @Builder.Default
    private Set<String> editor = Set.of(); // liste von userIds
    @Builder.Default
    private Set<String> supporter = Set.of(); // liste von userIds
    @Builder.Default
    private Set<String> player = Set.of(); // liste von userIds oder ['*'] für alle

    @Builder.Default
    private boolean publicFlag = false; // ob Welt öffentlich zugänglich ist

    /**
     * Whether this world can create instances.
     * If true, players can create independent copies (instances) of this world.
     */
    @Indexed
    @Builder.Default
    private boolean instanceable = false;

    /**
     * Default ground level for chunk generation (Y coordinate).
     * Used when no chunk data exists in database.
     */
    @Builder.Default
    private int groundLevel = 0;

    /**
     * Water level for ocean generation (Y coordinate).
     * If set, water blocks are generated up to this level.
     */
    private Integer waterLevel;

    /**
     * Block type ID for ground blocks (e.g., "r/grass" for grass).
     * Used when generating default chunks.
     */
    @Builder.Default
    private String groundBlockType = "n:g";

    /**
     * Block type ID for water blocks (e.g., "core:water").
     * Used when generating ocean in default chunks.
     */
    @Builder.Default
    private String waterBlockType = "n:o";

    /**
     * History of era durations in minutes.
     * Each entry represents the duration of a completed era.
     * Index 0 = Era 1, Index 1 = Era 2, etc.
     * Updated when an era ends (incrementEra is called).
     */
    @Builder.Default
    private List<Long> eraHistory = List.of();

    public void touchForCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public void touchForUpdate() {
        updatedAt = Instant.now();
    }

    public int getChunkX(double worldX) {
        return getChunkX((int) Math.floor(worldX));
    }

    /**
     * Calculate chunk X coordinate from world X coordinate.
     * Uses configured chunkSize from WorldInfo.
     *
     * @param worldX World X coordinate
     * @return Chunk X coordinate
     */
    public int getChunkX(int worldX) {
        if (publicData == null) {
            throw new IllegalStateException("World has no publicData");
        }
        int chunkSize = publicData.getChunkSize();
        return Math.floorDiv(worldX, chunkSize);
    }

    public int getChunkZ(double worldZ) {
        return getChunkZ((int) Math.floor(worldZ));
    }

    /**
     * Calculate chunk Z coordinate from world Z coordinate.
     * Uses configured chunkSize from WorldInfo.
     *
     * @param worldZ World Z coordinate
     * @return Chunk Z coordinate
     */
    public int getChunkZ(int worldZ) {
        if (publicData == null) {
            throw new IllegalStateException("World has no publicData");
        }
        int chunkSize = publicData.getChunkSize();
        return Math.floorDiv(worldZ, chunkSize);
    }

    /**
     * Calculate chunk key from world coordinates.
     * Format: "{cx}:{cz}"
     *
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Chunk key string
     */
    public String getChunkKey(double worldX, double worldZ) {
        return getChunkX(worldX) + ":" + getChunkZ(worldZ);
    }

    /**
     * Calculate chunk key from world coordinates.
     * Format: "{cx}:{cz}"
     *
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Chunk key string
     */
    public String getChunkKey(int worldX, int worldZ) {
        return getChunkX(worldX) + ":" + getChunkZ(worldZ);
    }

    public boolean isPlayerAllowed(UserId userId) {
        if (publicFlag) return true;
        if (player.contains("*")) return true;
        if (userId == null) return false;
        return player.contains(userId.getId());
    }

    public boolean isEditorAllowed(UserId userId) {
        if (userId == null) return false;
        return editor.contains(userId.getId());
    }

    public boolean isSupporterAllowed(UserId userId) {
        if (userId == null) return false;
        return supporter.contains(userId.getId());
    }

    public boolean isOwnerAllowed(UserId userId) {
        if (userId == null) return false;
        return owner.contains(userId.getId());
    }

    public List<WorldRoles> getRolesForUser(UserId userId) {
        if (userId == null) return List.of();
        if (isOwnerAllowed(userId)) return List.of(WorldRoles.OWNER, WorldRoles.SUPPORT, WorldRoles.EDITOR, WorldRoles.PLAYER);
        if (isEditorAllowed(userId)) {
            if (isSupporterAllowed(userId))
                return List.of(WorldRoles.SUPPORT, WorldRoles.EDITOR, WorldRoles.PLAYER);
            else
                return List.of(WorldRoles.EDITOR, WorldRoles.PLAYER);
        }
        if (isPlayerAllowed(userId)) return List.of(WorldRoles.PLAYER);
        return List.of();
    }

    public List<ActorRoles> getActorRolesForUser(UserId userId) {
        if (userId == null) return List.of();
        if (isOwnerAllowed(userId)) return List.of(ActorRoles.SUPPORT, ActorRoles.EDITOR, ActorRoles.PLAYER);
        if (isEditorAllowed(userId)) {
            if (isSupporterAllowed(userId))
                return List.of(ActorRoles.SUPPORT, ActorRoles.EDITOR, ActorRoles.PLAYER);
            else
                return List.of(ActorRoles.EDITOR, ActorRoles.PLAYER);
        }
        if (isPlayerAllowed(userId)) return List.of(ActorRoles.PLAYER);
        return List.of();
    }

}
