import { WorldId } from './WorldId';

export enum WorldCollectionType {
    WORLD = 'WORLD',
    REGION = 'REGION',
    PUBLIC = 'PUBLIC',
    SHARED = 'SHARED',
}

export class WorldCollection {
    type: WorldCollectionType;
    worldId: WorldId;
    path: string;

    constructor(type: WorldCollectionType, worldId: WorldId, path: string) {
        this.type = type;
        this.worldId = worldId;
        this.path = path;
    }

    static of(worldId: WorldId, path: string): WorldCollection {
        // worldId muss Methoden isCollection(), getRegionId(), getWorldName() bereitstellen
        if (worldId.isCollection && worldId.isCollection()) {
            let type: WorldCollectionType;
            switch (worldId.getRegionId()) {
                case WorldId.COLLECTION_REGION:
                    type = WorldCollectionType.REGION;
                    break;
                case WorldId.COLLECTION_PUBLIC:
                    type = WorldCollectionType.PUBLIC;
                    break;
                default:
                    type = WorldCollectionType.SHARED;
            }
            let pos = path.indexOf(':');
            if (pos >= 0) path = path.substring(pos + 1);
            return new WorldCollection(type, worldId, path);
        }
        let pos = path.indexOf(':');
        if (pos < 0) {
            if (path.startsWith('w/')) {
                path = path.substring(2);
            }
            return new WorldCollection(WorldCollectionType.WORLD, worldId, path);
        }
        let group = path.substring(0, pos).toLowerCase();
        path = path.substring(pos + 1);
        switch (group) {
            case 'w':
                return new WorldCollection(WorldCollectionType.WORLD, worldId, path);
            case 'r':
                // Annahme: WorldId.of('COLLECTION_REGION', worldId.getRegionId())
                return new WorldCollection(
                    WorldCollectionType.REGION,
                    WorldId.of('COLLECTION_REGION', worldId.getRegionId())!,
                    path
                );
            case 'rp':
                return new WorldCollection(
                    WorldCollectionType.PUBLIC,
                    WorldId.of('COLLECTION_PUBLIC', worldId.getRegionId())!,
                    path
                );
            default:
                return new WorldCollection(
                    WorldCollectionType.SHARED,
                    WorldId.of('COLLECTION_SHARED', group)!,
                    path
                );
        }
    }

    static getPrefixForWorld(worldId: string): string {
        // Schnelle Typ-Erkennung anhand des worldId-Formats
        if (!worldId) return 'w'; // should be 'n'
        if (worldId.startsWith('@')) {
            // Collection-IDs
            const parts = worldId.split(':', 3);
            switch (parts[0]) {
                case WorldId.COLLECTION_REGION:
                    return 'r';
                case WorldId.COLLECTION_PUBLIC:
                    return 'rp';
                default:
                    return parts[1] || 'w'; // should be 'n'
            }
        }
        return 'w';
    }

    prefix(): string {
        switch (this.type) {
            case WorldCollectionType.WORLD:
                return 'w';
            case WorldCollectionType.REGION:
                return 'r';
            case WorldCollectionType.PUBLIC:
                return 'rp';
            case WorldCollectionType.SHARED:
                return this.worldId.getWorldName ? this.worldId.getWorldName() ?? '' : '';
        }
        return 'w';
    }
}