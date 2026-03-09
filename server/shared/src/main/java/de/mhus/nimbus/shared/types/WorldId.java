package de.mhus.nimbus.shared.types;

import java.util.Optional;

/*
 * WorldId represents a unique identifier for a world in the
 * format: "regionId:worldName[:zone[:instance]]".
 * e.g.
 * - regionId:worldName::instance
 * - regionId:worldName:zone:instance
 * - regionId:worldName::
 *
 * - worldName without zone is main world
 * - worldName with zone is zone world
 * - main or zone without instance is a base world
 * - instance is an instance world
 * - full id is a world id with all parts but zone and instance could be empty
 *
 * - if zone is empty it's a main world
 *
 * or starts with @ for collections:
 * @collection:collectinId
 *
 * Every part is a string 'a-zA-Z0-9_-' from 1 to 64 characters.
 */
public class WorldId implements Comparable<WorldId> {
    public static final String COLLECTION_REGION = "@region";
    public static final String COLLECTION_SHARED = "@shared";
    public static final String COLLECTION_PUBLIC = "@public";

    private String id;
    private String regionId;
    private String worldName;
    private String zone;
    private String instance;
    private String fullId;

    private WorldId(String id) {
        this.id = id;
    }

    public static WorldId unchecked(String worldId) {
        if (worldId == null) throw new NullPointerException("worldId is null");
        return new WorldId(worldId);
    }

    public static String worldWithInstance(String worldId, String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return worldId;
        }
        // ensure we append at the right position depending on existing parts
        var parts = worldId.split(":", 4);
        if (parts.length == 2) {
            // regionId:worldName -> regionId:worldName::instanceId
            return worldId + "::" + instanceId;
        } else if (parts.length == 3) {
            // regionId:worldName:zone -> regionId:worldName:zone:instanceId
            return worldId + ":" + instanceId;
        } else if (parts.length == 4) {
            // already has instance slot, replace it
            return parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + instanceId;
        }
        return worldId + "::" + instanceId;
    }

    public String getId() {
        parseId();
        return id;
    }

    public String getRegionId() {
        parseId();
        return regionId;
    }

    public String getWorldName() {
        parseId();
        return worldName;
    }

    public String getZone() {
        parseId();
        return zone;
    }

    public String getInstance() {
        parseId();
        return instance;
    }

    /**
     * Returns the full id string with all 4 parts: regionId:worldName:zone:instance
     * Zone and instance may be empty strings.
     */
    public String getFullId() {
        parseId();
        if (fullId == null) {
             fullId = regionId + ":" + worldName + ":" + zone + ":" + instance;
        }
        return fullId;
    }

    public boolean isCollection() {
        return id.startsWith("@");
    }

    private void parseId() { // no need for sync, worst case double parse
        if (regionId != null) return;
        var string = id;
        if (string.startsWith("@")) {
            // Collection ID
            var parts = string.split(":", 3); // one more for garbage
            regionId = parts[0];
            worldName = parts[1];
            zone = "";
            instance = "";
            return;
        }
        var parts = string.split(":", 5); // one more for garbage
        regionId = parts[0];
        worldName = parts[1];
        zone = parts.length > 2 ? parts[2] : "";
        instance = parts.length > 3 ? parts[3] : "";
        // normalize id to reduced canonical form (strip trailing empty colons)
        int len = id.length();
        while (len > 0 && id.charAt(len - 1) == ':') len--;
        if (len < id.length()) id = id.substring(0, len);
    }

    public String toString() {
        return getId();
    }

    public static Optional<WorldId> of(String first, String second) {
        return of(first + ":" + second);
    }

    public static Optional<WorldId> of(String id) {
        if (!validate(id)) return Optional.empty();
        return Optional.of(new WorldId(id));
    }

    public static boolean validate(String id) {
        if (id == null || id.isBlank()) return false;
        if (id.length() < 3) return false;
        if (id.startsWith("@")) {
            // Collection ID
            return id.matches("^@[a-zA-Z0-9_\\-]{1,64}:[a-zA-Z0-9_\\-]{1,64}$");
        }
         // Every part is a string 'a-zA-Z0-9_-' from 1 to 64 characters.
        // format: regionId:worldName[:zone[:instance]] where zone can be empty
        return id.matches("^[a-zA-Z0-9_\\-]{1,64}:[a-zA-Z0-9_\\-]{1,64}(:[a-zA-Z0-9_\\-]{0,64}(:[a-zA-Z0-9_\\-]{0,64})?)?$");
    }

    public boolean isMain() {
        parseId();
        return zone.isEmpty() && instance.isEmpty();
    }

    public boolean isInstance() {
        parseId();
        return !instance.isEmpty();
    }

    public boolean isZone() {
        parseId();
        return !zone.isEmpty();
    }

    // base means main or zone but not instance
    public boolean isBase() {
        parseId();
        return instance.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorldId other = (WorldId) o;
        return getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public int compareTo(WorldId o) {
        return this.getId().compareTo(o.getId());
    }

    public WorldId toBaseWorldId() {
        parseId();
        if (instance.isEmpty()) return this;
        String result = regionId + ":" + worldName;
        if (!zone.isEmpty()) result += ":" + zone;
        return new WorldId(result);
    }

    /**
     * Creates a new WorldId with the given instance identifier.
     * If this WorldId already has an instance, it will be replaced.
     *
     * @param instanceId The instance identifier to add
     * @return A new WorldId with the instance part set
     */
    public WorldId toWorldWithInstance(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId cannot be null or blank");
        }
        parseId();
        return new WorldId(regionId + ":" + worldName + ":" + zone + ":" + instanceId);
    }

    /**
     * Converts this WorldId to its corresponding region collection WorldId.
     * The region collection is shared across all worlds in the same region.
     * This is used for region-scoped entities like ItemTypes.
     *
     * @return WorldId for the region collection (@region:regionId)
     */
    public WorldId toRegionCollection() {
        parseId();
        if (isCollection()) {
            if (isSharedCollection())
                throw new IllegalStateException("Shared collection cannot be converted to region collection: " + id);
            return this;
        }
        return WorldId.of(COLLECTION_REGION, regionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid region worldId: " + regionId));
    }

    /**
     * Converts this WorldId to its corresponding region collection WorldId.
     * If the WorldId is already a collection, it will be returned as is.
     * Even if it is another collection type (e.g. public or shared), it will be returned as is.
     *
     * @return WorldId for the region collection (@region:regionId)
     */
    public WorldId toCollection() {
        parseId();
        if (isCollection()) {
            return this;
        }
        return WorldId.of(COLLECTION_REGION, regionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid region worldId: " + regionId));
    }
    public WorldId toMainWorld() {
        parseId();
        return new WorldId(regionId + ":" + worldName);
    }

    public String getCollectionRegion() {
        parseId();
        if (isRegionCollection() || isPublicRegion()) return worldName;
        if (isCollection()) return null;
        return regionId;
    }

    public boolean isInstanceOrZone() {
        return isInstance() || isZone();
    }

    public boolean isRegionCollection() {
        return id.startsWith(COLLECTION_REGION + ":");
    }

    public boolean isSharedCollection() {
        return id.startsWith(COLLECTION_SHARED + ":");
    }

    public boolean isPublicRegion() {
        return id.startsWith(COLLECTION_PUBLIC + ":");
    }

    /**
     * Checks if this WorldId matches the given worldId string.
     * For editor instances, also matches the base world (without instance suffix).
     * This is used for Redis broadcast filtering where editor sessions
     * need to receive base world messages.
     *
     * @param otherWorldId The worldId string to compare against
     * @return true if this WorldId matches (exact or editor-to-base match)
     */
    public boolean matchesWorld(String otherWorldId) {
        parseId();
        if (getId().equals(otherWorldId)) return true;
        if (getFullBaseId().equals(otherWorldId)) return true;
        return false;
    }

    /**
     * Returns the full base worldId (without instance suffix for editor instances).
     * For editor instances, returns the base worldId so messages go to the
     * shared base world channel (editors work on base world data).
     * For all other WorldIds, returns the regular id.
     *
     * @return base worldId for editor instances, otherwise this worldId
     */
    public String getFullBaseId() {
        parseId();
        return regionId + ":" + worldName + ":" + zone + ":";
    }

    /**
     * Check if this is a synthetic editor instance.
     * Editor instances use the format "xN" where N is the epoch number (e.g. "x0", "x2").
     * They operate on base world data without COW.
     *
     * @return true if this is a synthetic editor instance
     */
    public boolean isEditorInstance() {
        parseId();
        if (instance.isEmpty()) return false;
        return instance.startsWith("x") && instance.length() > 1;
    }

    /**
     * Get the epoch number from a synthetic editor instance.
     * Only valid when {@link #isEditorInstance()} returns true.
     *
     * @return epoch number extracted from instance ID (e.g. "x2" -> 2)
     * @throws NumberFormatException if instance ID is not a valid editor instance
     */
    public int getEditorEpoch() {
        parseId();
        return Integer.parseInt(instance.substring(1));
    }

}
