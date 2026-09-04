export class WorldId {
    static readonly COLLECTION_REGION = '@region';
    static readonly COLLECTION_SHARED = '@shared';
    static readonly COLLECTION_PUBLIC = '@public';

    private _id: string;
    private _regionId: string | undefined;
    private _worldName: string = '';
    private _zone: string = '';
    private _instance: string = '';
    private _parsed: boolean = false;

    private constructor(id: string) {
        this._id = id;
    }

    static unchecked(worldId: string): WorldId {
        if (worldId == null) throw new Error('worldId is null');
        return new WorldId(worldId);
    }

    static worldWithInstance(worldId: string, instanceId: string | null | undefined): string {
        if (!instanceId || instanceId.trim() === '') return worldId;
        const parts = worldId.split(':', 4);
        if (parts.length === 2) {
            // regionId:worldName -> regionId:worldName::instanceId
            return worldId + '::' + instanceId;
        } else if (parts.length === 3) {
            // regionId:worldName:zone -> regionId:worldName:zone:instanceId
            return worldId + ':' + instanceId;
        } else if (parts.length === 4) {
            // already has instance slot, replace it
            return parts[0] + ':' + parts[1] + ':' + parts[2] + ':' + instanceId;
        }
        return worldId + '::' + instanceId;
    }

    get id(): string {
        this.parseId();
        return this._id;
    }

    getRegionId(): string {
        this.parseId();
        return this._regionId!;
    }

    getWorldName(): string {
        this.parseId();
        return this._worldName;
    }

    getZone(): string {
        this.parseId();
        return this._zone;
    }

    getInstance(): string {
        this.parseId();
        return this._instance;
    }

    getFullId(): string {
        this.parseId();
        return this._regionId + ':' + this._worldName + ':' + this._zone + ':' + this._instance;
    }

    isCollection(): boolean {
        return this._id.startsWith('@');
    }

    private parseId(): void {
        if (this._parsed) return;
        this._parsed = true;
        const string = this._id;
        if (string.startsWith('@')) {
            // Collection ID
            const parts = string.split(':', 3);
            this._regionId = parts[0];
            this._worldName = parts[1];
            return;
        }
        const parts = string.split(':', 5);
        this._regionId = parts[0];
        this._worldName = parts[1];
        this._zone = parts.length > 2 ? parts[2] : '';
        this._instance = parts.length > 3 ? parts[3] : '';
        // normalize id to reduced canonical form (strip trailing empty colons)
        let len = this._id.length;
        while (len > 0 && this._id.charAt(len - 1) === ':') len--;
        if (len < this._id.length) this._id = this._id.substring(0, len);
    }

    toString(): string {
        this.parseId();
        return this._id;
    }

    static of(first: string, second?: string): WorldId | undefined {
        if (second !== undefined) {
            return WorldId.ofRaw(first + ':' + second);
        }
        return WorldId.ofRaw(first);
    }

    private static ofRaw(id: string): WorldId | undefined {
        if (!WorldId.validate(id)) return undefined;
        return new WorldId(id);
    }

    static validate(id: string | null | undefined): boolean {
        if (!id || id.trim() === '') return false;
        if (id.length < 3) return false;
        if (id.startsWith('@')) {
            return /^@[a-zA-Z0-9_-]{1,64}:[a-zA-Z0-9_-]{1,64}$/.test(id);
        }
        // format: regionId:worldName[:zone[:instance]] where zone can be empty
        return /^[a-zA-Z0-9_-]{1,64}:[a-zA-Z0-9_-]{1,64}(:[a-zA-Z0-9_-]{0,64}(:[a-zA-Z0-9_-]{0,64})?)?$/.test(id);
    }

    isMain(): boolean {
        this.parseId();
        return this._zone === '' && this._instance === '';
    }

    isInstance(): boolean {
        this.parseId();
        return this._instance !== '';
    }

    isZone(): boolean {
        this.parseId();
        return this._zone !== '';
    }

    equals(other: any): boolean {
        if (this === other) return true;
        if (!other || !(other instanceof WorldId)) return false;
        return this._id === other._id;
    }

    compareTo(other: WorldId): number {
        return this._id.localeCompare(other._id);
    }

    withoutInstance(): WorldId {
        this.parseId();
        if (this._instance === '') return this;
        let result = this._regionId + ':' + this._worldName;
        if (this._zone !== '') result += ':' + this._zone;
        return new WorldId(result);
    }

    /**
     * @deprecated Use mainWorld() instead
     */
    withoutInstanceAndZone(): WorldId {
        return this.mainWorld();
    }

    withInstance(instanceId: string): WorldId {
        if (!instanceId || instanceId.trim() === '') {
            throw new Error('instanceId cannot be null or blank');
        }
        this.parseId();
        return new WorldId(this._regionId + ':' + this._worldName + ':' + this._zone + ':' + instanceId);
    }

    toRegionCollection(): WorldId {
        this.parseId();
        const id = WorldId.COLLECTION_REGION + ':' + this._regionId;
        const result = WorldId.ofRaw(id);
        if (!result) throw new Error('Invalid region worldId: ' + this._regionId);
        return result;
    }

    mainWorld(): WorldId {
        this.parseId();
        return new WorldId(this._regionId + ':' + this._worldName);
    }

    isInstanceOrZone(): boolean {
        return this.isInstance() || this.isZone();
    }
}
