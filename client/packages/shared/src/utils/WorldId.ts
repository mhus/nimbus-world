export class WorldId {
    static readonly COLLECTION_REGION = '@region';
    static readonly COLLECTION_SHARED = '@shared';
    static readonly COLLECTION_PUBLIC = '@public';

    private _id: string;
    private _regionId?: string;
    private _worldName?: string;
    private _zone?: string;
    private _instance?: string;

    private constructor(id: string) {
        this._id = id;
    }

    static unchecked(worldId: string): WorldId {
        if (worldId == null) throw new Error('worldId is null');
        return new WorldId(worldId);
    }

    static worldWithInstance(worldId: string, instanceId: string | null | undefined): string {
        if (!instanceId || instanceId.trim() === '') return worldId;
        return worldId + '!' + instanceId;
    }

    get id(): string {
        return this._id;
    }

    getRegionId(): string | undefined {
        this.parseId();
        return this._regionId;
    }

    getWorldName(): string | undefined {
        this.parseId();
        return this._worldName;
    }

    getZone(): string | undefined {
        this.parseId();
        return this._zone;
    }

    getInstance(): string | undefined {
        this.parseId();
        return this._instance;
    }

    isCollection(): boolean {
        return this._id.startsWith('@');
    }

    private parseId(): void {
        if (this._regionId !== undefined) return;
        let string = this._id;
        if (string.startsWith('@')) {
            // Collection ID
            const parts = string.split(':', 3);
            this._regionId = parts[0];
            this._worldName = parts[1];
            this._zone = undefined;
            this._instance = undefined;
            return;
        }
        if (string.indexOf('!') > 0) {
            const parts = string.split('!', 3);
            if (parts.length > 1) {
                this._instance = parts[1];
            }
            string = parts[0];
        }
        const parts = string.split(':', 4);
        this._regionId = parts[0];
        this._worldName = parts[1];
        if (parts.length > 2) {
            this._zone = parts[2];
        }
    }

    toString(): string {
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
            return /^@[a-zA-Z0-9_\-]{1,64}:[a-zA-Z0-9_\-]{1,64}$/.test(id);
        }
        return /^[a-zA-Z0-9_\-]{1,64}:[a-zA-Z0-9_\-]{1,64}(:[a-zA-Z0-9_\-]{1,64})?(![a-zA-Z0-9_\-]{1,64})?$/.test(id);
    }

    isMain(): boolean {
        this.parseId();
        return this._zone === undefined && this._instance === undefined;
    }

    isInstance(): boolean {
        this.parseId();
        return this._instance !== undefined;
    }

    isZone(): boolean {
        this.parseId();
        return this._zone !== undefined;
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
        if (!this._instance) return this;
        let sb = this._regionId + ':' + this._worldName;
        if (this._zone) sb += ':' + this._zone;
        return new WorldId(sb);
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
        let sb = this._regionId + ':' + this._worldName;
        if (this._zone) sb += ':' + this._zone;
        sb += '!' + instanceId;
        return new WorldId(sb);
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
