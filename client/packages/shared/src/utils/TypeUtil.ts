// --- Type definitions ---
// These should be replaced by the actual types/interfaces from your model if available
export interface Vector2 { x: number; z: number; }
export interface Vector3 { x: number; y: number; z: number; }
export interface Vector3Int { x: number; y: number; z: number; }
export interface Vector2Pair { a: Vector2; b: Vector2; }
export interface Vector3Pair { a: Vector3; b: Vector3; }
export interface HexVector2 { q: number; r: number; }
export interface Area { position: Vector3Int; size: Vector3Int; }

export class TypeUtil {
    // --- Builder methods ---
    static area(x: number, z: number, sizeX: number, sizeZ: number): Area {
        return this.areaFull(x, 0, z, sizeX, 255, sizeZ);
    }
    static areaFull(x: number, y: number, z: number, sizeX: number, sizeY: number, sizeZ: number): Area {
        return {
            position: this.vector3Int(x, y, z),
            size: this.vector3Int(sizeX, sizeY, sizeZ)
        };
    }
    static vector3Int(x: number, y: number, z: number): Vector3Int {
        return { x, y, z };
    }
    static vector3(x: number, y: number, z: number): Vector3 {
        return { x, y, z };
    }
    static vector2(x: number, z: number): Vector2 {
        return { x, z };
    }
    static vector2Pair(a: Vector2, b: Vector2): Vector2Pair {
        return { a, b };
    }
    static vector2PairFromNumbers(ax: number, az: number, bx: number, bz: number): Vector2Pair {
        return { a: this.vector2(ax, az), b: this.vector2(bx, bz) };
    }
    static vector3Pair(a: Vector3, b: Vector3): Vector3Pair {
        return { a, b };
    }
    static vector3PairFromNumbers(ax: number, ay: number, az: number, bx: number, by: number, bz: number): Vector3Pair {
        return { a: this.vector3(ax, ay, az), b: this.vector3(bx, by, bz) };
    }
    static hexVector2(q: number, r: number): HexVector2 {
        return { q, r };
    }

    // --- World Coordinates (x,y,z) ---
    static parseWorldCoord(s: string): Vector3 {
        if (!s) throw new Error('Input is null');
        const parts = s.split(',');
        if (parts.length !== 3) throw new Error('Invalid world coordinate: ' + s);
        return this.vector3(Number(parts[0]), Number(parts[1]), Number(parts[2]));
    }
    static toStringWorldCoord(v: Vector3 | Vector3Int): string {
        return `${v.x},${v.y},${v.z}`;
    }

    // --- Flat World Coordinates (x,z) ---
    static parseFlatWorldCoord(s: string): Vector2 {
        if (!s) throw new Error('Input is null');
        const parts = s.split(',');
        if (parts.length !== 2) throw new Error('Invalid flat world coordinate: ' + s);
        return this.vector2(Number(parts[0]), Number(parts[1]));
    }
    static toStringFlatWorldCoord(v: Vector2): string {
        return `${v.x},${v.z}`;
    }

    // --- Chunk Coordinates (cx:cz) ---
    static parseChunkCoord(s: string): [number, number] {
        if (!s) throw new Error('Input is null');
        const parts = s.split(':');
        if (parts.length !== 2) throw new Error('Invalid chunk coordinate: ' + s);
        return [Number(parts[0]), Number(parts[1])];
    }
    static toStringChunkCoord(cx: number, cz: number): string {
        return `${Math.floor(cx)}:${Math.floor(cz)}`;
    }

    // --- Hex Grid Coordinates (q;r) ---
    static parseHexCoord(s: string): HexVector2 {
        if (!s) throw new Error('Input is null');
        const parts = s.split(';');
        if (parts.length !== 2) throw new Error('Invalid hex coordinate: ' + s);
        return this.hexVector2(Number(parts[0]), Number(parts[1]));
    }
    static toStringHexCoord(v: HexVector2): string {
        return `${v.q};${v.r}`;
    }

    // --- Local Coordinates (x/y/z or x/z) ---
    static parseLocalCoord3(s: string): Vector3Int {
        if (!s) throw new Error('Input is null');
        const parts = s.split('/');
        if (parts.length !== 3) throw new Error('Invalid local 3D coordinate: ' + s);
        return this.vector3Int(Number(parts[0]), Number(parts[1]), Number(parts[2]));
    }
    static parseLocalCoord2(s: string): Vector2 {
        if (!s) throw new Error('Input is null');
        const parts = s.split('/');
        if (parts.length !== 2) throw new Error('Invalid local 2D coordinate: ' + s);
        return this.vector2(Number(parts[0]), Number(parts[1]));
    }
    static toStringLocalCoord3(v: Vector3Int): string {
        return `${v.x}/${v.y}/${v.z}`;
    }
    static toStringLocalCoord2(v: Vector2): string {
        return `${Math.floor(v.x)}/${Math.floor(v.z)}`;
    }

    // --- Size (sizex x sizez) ---
    static parseSize(s: string): Vector2 {
        if (!s) throw new Error('Input is null');
        const parts = s.split('x');
        if (parts.length !== 2) throw new Error('Invalid size: ' + s);
        return this.vector2(Number(parts[0]), Number(parts[1]));
    }
    static toStringSize(v: Vector2): string {
        return `${v.x}x${v.z}`;
    }

    // --- Area (Coordinate+Size) ---
    static parseArea(s: string): Area {
        if (!s) throw new Error('Input is null');
        const parts = s.split('+');
        if (parts.length !== 2) throw new Error('Invalid area: ' + s);
        const coord = parts[0];
        const size = parts[1];
        const sz = this.parseSize(size);
        if (coord.includes(',')) {
            if (coord.split(',').length === 3) {
                const pos = this.parseWorldCoord(coord);
                return this.areaFull(pos.x, pos.y, pos.z, sz.x, 255, sz.z);
            } else {
                const pos = this.parseFlatWorldCoord(coord);
                return this.area(pos.x, pos.z, sz.x, sz.z);
            }
        } else if (coord.includes(':')) {
            const [cx, cz] = this.parseChunkCoord(coord);
            return this.area(cx, cz, sz.x, sz.z);
        } else if (coord.includes(';')) {
            const h = this.parseHexCoord(coord);
            return {
                position: this.vector3Int(h.q, 0, h.r),
                size: this.vector3Int(sz.x, 255, sz.z)
            };
        } else if (coord.includes('/')) {
            if (coord.split('/').length === 3) {
                const pos = this.parseLocalCoord3(coord);
                return {
                    position: this.vector3Int(pos.x, pos.y, pos.z),
                    size: this.vector3Int(sz.x, 255, sz.z)
                };
            } else {
                const pos = this.parseLocalCoord2(coord);
                return {
                    position: this.vector3Int(pos.x, 0, pos.z),
                    size: this.vector3Int(sz.x, 255, sz.z)
                };
            }
        } else {
            throw new Error('Unknown area coordinate type: ' + coord);
        }
    }
    static toStringArea(a: Area): string {
        const pos = a.position;
        const size = a.size;
        return this.toStringWorldCoord(pos) + '+' + this.toStringSize({ x: size.x, z: size.z });
    }
}
