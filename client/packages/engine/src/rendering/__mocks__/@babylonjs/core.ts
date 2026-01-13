// Mock BabylonJS core for testing

export class NullEngine {
  dispose() {}
}

export class Scene {
  constructor(public engine: any) {}
  dispose() {}
}

export class StandardMaterial {
  public backFaceCulling = true;
  public diffuseColor: any;

  constructor(public name: string, public scene: any) {
    this.diffuseColor = new Color3(1, 1, 1);
  }
}

export class Texture {
  constructor(public url: string, public scene: any) {}
}

export class Color3 {
  constructor(public r: number, public g: number, public b: number) {}
}

export class Vector3 {
  constructor(public x: number, public y: number, public z: number) {}

  static TransformCoordinates(vector: Vector3, matrix: Matrix): Vector3 {
    // Simple mock transformation
    return new Vector3(
      vector.x * matrix.m[0] + vector.y * matrix.m[4] + vector.z * matrix.m[8],
      vector.x * matrix.m[1] + vector.y * matrix.m[5] + vector.z * matrix.m[9],
      vector.x * matrix.m[2] + vector.y * matrix.m[6] + vector.z * matrix.m[10]
    );
  }
}

export class Matrix {
  public m: number[] = [
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, 1
  ];

  static RotationYawPitchRoll(yaw: number, pitch: number, roll: number): Matrix {
    const matrix = new Matrix();
    // Simplified rotation matrix (not accurate but good enough for tests)
    const cy = Math.cos(yaw);
    const sy = Math.sin(yaw);
    const cp = Math.cos(pitch);
    const sp = Math.sin(pitch);

    matrix.m[0] = cy;
    matrix.m[2] = sy;
    matrix.m[5] = cp;
    matrix.m[6] = -sp;
    matrix.m[8] = -sy;
    matrix.m[10] = cy;

    return matrix;
  }
}

export class VertexData {
  positions: number[] = [];
  indices: number[] = [];
  uvs: number[] = [];
  normals: number[] = [];

  applyToMesh(mesh: any) {
    // Mock apply
  }
}

export class Mesh {
  public material: any;

  constructor(public name: string, public scene: any) {}

  dispose() {}
}