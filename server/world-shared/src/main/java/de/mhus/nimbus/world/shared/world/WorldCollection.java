package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.WorldId;
import org.apache.logging.log4j.util.Strings;

public record WorldCollection(TYPE type, WorldId worldId, String path) {

//    public static final String SHARED_PUBLIC = "public";

    public enum TYPE {
        WORLD,
        REGION,
        PUBLIC,
        SHARED
    }

    public static WorldCollection of(WorldId worldId, String path) {
        if (worldId.isCollection()) {
            var type = switch(worldId.getRegionId()) {
                case WorldId.COLLECTION_REGION -> TYPE.REGION;
                case WorldId.COLLECTION_PUBLIC -> TYPE.PUBLIC;
                default -> TYPE.SHARED;
            };
            int pos = path.indexOf(':');
            if (pos >= 0) {
                if (type == TYPE.REGION) {
                    // could switch if needed
                    var group = path.substring(0, pos).toLowerCase();
                    if (group.equals("rp")) {
                        type = TYPE.PUBLIC;
                    } else if (!group.equals("r")) {
                        type = TYPE.SHARED;
                        worldId = WorldId.of(WorldId.COLLECTION_SHARED, group).get();
                    }
                }
                path = path.substring(pos + 1);
            }
            return new WorldCollection(type, worldId, path);
        }
        int pos = path.indexOf(':');
        if (pos < 0) {
            if (path.startsWith("w/")) { // legacy support
                path = path.substring(2);
            }
            return new WorldCollection(TYPE.WORLD, worldId, path);
        }
        var group = path.substring(0, pos).toLowerCase();
        path = path.substring(pos + 1);

        switch (group) {
            case "w":
                return new WorldCollection(TYPE.WORLD, worldId, path);
            case "r":
                return new WorldCollection(TYPE.REGION, WorldId.of(WorldId.COLLECTION_REGION, worldId.getRegionId()).get(), path);
            case "rp":
                return new WorldCollection(TYPE.PUBLIC, WorldId.of(WorldId.COLLECTION_PUBLIC, worldId.getRegionId()).get(), path);
//            case "p":
//                return new WorldCollection(TYPE.SHARED, WorldId.of(WorldId.COLLECTION_SHARED, SHARED_PUBLIC).get(), path);
            default:
                return new WorldCollection(TYPE.SHARED, WorldId.of(WorldId.COLLECTION_SHARED, group).get(), path);
        }
    }

    public String prefix() {
        switch (type) {
            case WORLD:
                return "w";
            case REGION:
                return "r";
            case PUBLIC:
                return "rp";
            case SHARED:
//                if (SHARED_PUBLIC.equals(worldId.getWorldName()))
//                    return "p";
                return worldId.getWorldName();
        }
        return "w"; // should not happen
    }

    public static String findPrefix(WorldId worldId) {
        if (worldId.isCollection()) {
            switch (worldId.getRegionId()) {
                case WorldId.COLLECTION_REGION -> {
                    return "r";
                }
                case WorldId.COLLECTION_PUBLIC -> {
                    return "rp";
                }
                default -> {
                    return worldId.getRegionId();
                }
            }
        } else {
            return "w";
        }
    }

    public static  String findPrefix(String worldId) {
        if (Strings.isBlank(worldId))
            return "w";
        if (worldId.startsWith("@")) {
            var parts = worldId.split(":");
            if (parts.length > 1) {
                switch (parts[0]) {
                    case WorldId.COLLECTION_REGION -> {
                        return "r";
                    }
                    case WorldId.COLLECTION_PUBLIC -> {
                        return "rp";
                    }
                    default -> {
                        return parts[1];
                    }
                }
            }
        }
        return "w";
    }

    public static String appendPrefix(String worldId, String id) {
        if (id == null) return null;
        if (worldId == null) return id;
        var prefix = findPrefix(worldId);
        return prefix + ":" + removePrefix(id);
    }

    public static String removePrefix(String id) {
        if (id == null) return null;
        int pos = id.indexOf(':');
        if (pos >= 0) return id.substring(pos + 1);
        return id;
    }


}
