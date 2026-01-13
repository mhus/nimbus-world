import { getLogger } from '@nimbus/shared';
import {
  Scene,
  TransformNode,
  Mesh,
  MeshBuilder,
  StandardMaterial,
  Color3,
  RawTexture,
  Constants,
  Texture,
  Vector3,
} from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import type { CameraService } from './CameraService';
import type { NetworkService } from './NetworkService';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('CloudsService');

/**
 * Cloud configuration interface
 */
export interface CloudConfig {
  id: string;              // Unique cloud identifier (user-defined)
  level: number;           // Level/priority (0-9)
  startX: number;          // Start X position in world
  startZ: number;          // Start Z position in world
  y: number;               // Height (Y position)
  width: number;           // Cloud width
  height: number;          // Cloud height
  texture: string;         // Texture path (e.g., "textures/clouds/cloud1.png")
  speed: number;           // Movement speed (blocks per second, 0 = static)
  direction: number;       // Direction in degrees (0=North, 90=East, 180=South, 270=West)
}

/**
 * Area definition for cloud spawning
 */
export interface Area {
  minX: number;
  maxX: number;
  minZ: number;
  maxZ: number;
  minY: number;
  maxY: number;
  minWidth: number;
  maxWidth: number;
  minHeight: number;
  maxHeight: number;
}

/**
 * Cloud animation job
 */
interface CloudAnimationJob {
  jobName: string;
  emitCountPerMinute: number;
  emitProbability: number;
  area: Area;
  speed: number;
  direction: number;
  textures: string[];
  intervalId: number;
  lastEmitTime: number;
}

/**
 * Internal cloud instance
 */
interface CloudInstance {
  // Configuration
  id: string;
  level: number;
  enabled: boolean;

  // Position & Movement
  startX: number;
  startZ: number;
  y: number;
  currentX: number;
  currentZ: number;

  // Size
  width: number;
  height: number;

  // Texture
  texturePath: string;

  // Movement
  speed: number;
  direction: number;

  // Animation state
  fadeAlpha: number;

  // BabylonJS objects
  root?: TransformNode;
  mesh?: Mesh;
  material?: StandardMaterial;
  texture?: Texture | RawTexture;
}

/**
 * CloudsService - Manages clouds in the sky
 *
 * Features:
 * - Horizontal flat planes (lying on XZ plane)
 * - Individual textures per cloud
 * - Distance-based fading
 * - Movement with configurable speed and direction
 * - Unlimited clouds (dynamically managed)
 * - Attached to camera environment root
 */
export class CloudsService {
  private scene: Scene;
  private appContext: AppContext;
  private cameraService: CameraService;
  private networkService?: NetworkService;

  // Cloud management
  private cloudsRoot?: TransformNode;
  private clouds: Map<string, CloudInstance> = new Map();

  // Animation jobs
  private animationJobs: Map<string, CloudAnimationJob> = new Map();

  // Fade configuration
  private readonly fadeStartDistance: number = 200;  // Start fading
  private readonly fadeEndDistance: number = 100;    // Fully visible/invisible

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;
    this.cameraService = appContext.services.camera!;
    this.networkService = appContext.services.network;

    this.initialize();
  }

  /**
   * Initialize clouds service
   */
  private initialize(): void {
    // Get camera environment root
    const cameraRoot = this.cameraService.getCameraEnvironmentRoot();
    if (!cameraRoot) {
      logger.error('Camera environment root not available');
      return;
    }

    // Create clouds root node attached to camera environment root
    this.cloudsRoot = new TransformNode('cloudsRoot', this.scene);
    this.cloudsRoot.parent = cameraRoot;

    logger.debug('CloudsService initialized');
  }

  /**
   * Add a new cloud to the scene
   * @param config Cloud configuration
   * @returns Cloud ID
   */
  public async addCloud(config: CloudConfig): Promise<string> {
    // Check if cloud with this ID already exists
    if (this.clouds.has(config.id)) {
      throw new Error(`Cloud with ID '${config.id}' already exists`);
    }

    // Validate parameters
    this.validateCloudConfig(config);

    // Use provided ID
    const id = config.id;

    // Create cloud instance
    const cloud: CloudInstance = {
      id,
      level: config.level,
      enabled: true,
      startX: config.startX,
      startZ: config.startZ,
      y: config.y,
      currentX: config.startX,
      currentZ: config.startZ,
      width: config.width,
      height: config.height,
      texturePath: config.texture,
      speed: config.speed,
      direction: config.direction,
      fadeAlpha: 1.0,
    };

    try {
      // Load texture
      cloud.texture = await this.loadCloudTexture(config.texture);

      // Create material
      cloud.material = this.createCloudMaterial(cloud);

      // Create mesh
      cloud.mesh = this.createCloudMesh(cloud);

      // Create root node
      cloud.root = new TransformNode(`cloud_${id}_root`, this.scene);
      if (this.cloudsRoot) {
        cloud.root.parent = this.cloudsRoot;
      }
      cloud.root.position.set(config.startX, config.y, config.startZ);

      // Attach mesh to root
      cloud.mesh.parent = cloud.root;

      // Add to clouds map
      this.clouds.set(id, cloud);

      logger.debug('Cloud added', {
        id,
        level: config.level,
        position: { x: config.startX, z: config.startZ, y: config.y },
        size: { width: config.width, height: config.height },
        texture: config.texture,
        speed: config.speed,
        direction: config.direction,
      });

      return id;
    } catch (error) {
      logger.error('Failed to create cloud', { error, config });
      throw error;
    }
  }

  /**
   * Remove a cloud from the scene
   * @param id Cloud ID
   */
  public removeCloud(id: string): void {
    const cloud = this.clouds.get(id);
    if (!cloud) {
      logger.warn('Cloud not found', { id });
      return;
    }

    this.disposeCloud(cloud);
    this.clouds.delete(id);
    logger.debug('Cloud removed', { id });
  }

  /**
   * Enable or disable a cloud
   * @param id Cloud ID
   * @param enabled True to enable, false to disable
   */
  public setCloudEnabled(id: string, enabled: boolean): void {
    const cloud = this.clouds.get(id);
    if (!cloud) {
      logger.warn('Cloud not found', { id });
      return;
    }

    cloud.enabled = enabled;
    if (cloud.mesh) {
      cloud.mesh.setEnabled(enabled);
    }

    logger.debug('Cloud enabled state changed', { id, enabled });
  }

  /**
   * Set cloud speed
   * @param id Cloud ID
   * @param speed Speed in blocks per second
   */
  public setCloudSpeed(id: string, speed: number): void {
    const cloud = this.clouds.get(id);
    if (!cloud) {
      logger.warn('Cloud not found', { id });
      return;
    }

    if (speed < 0) {
      throw new Error('Cloud speed cannot be negative');
    }

    cloud.speed = speed;
    logger.debug('Cloud speed changed', { id, speed });
  }

  /**
   * Set cloud direction
   * @param id Cloud ID
   * @param direction Direction in degrees (0=North, 90=East, 180=South, 270=West)
   */
  public setCloudDirection(id: string, direction: number): void {
    const cloud = this.clouds.get(id);
    if (!cloud) {
      logger.warn('Cloud not found', { id });
      return;
    }

    cloud.direction = direction;
    logger.debug('Cloud direction changed', { id, direction });
  }

  /**
   * Set cloud position
   * @param id Cloud ID
   * @param x X position
   * @param z Z position
   * @param y Y position (height)
   */
  public setCloudPosition(id: string, x: number, z: number, y: number): void {
    const cloud = this.clouds.get(id);
    if (!cloud) {
      logger.warn('Cloud not found', { id });
      return;
    }

    cloud.currentX = x;
    cloud.currentZ = z;
    cloud.y = y;

    if (cloud.root) {
      cloud.root.position.set(x, y, z);
    }

    logger.debug('Cloud position changed', { id, position: { x, z, y } });
  }

  /**
   * Set cloud size
   * @param id Cloud ID
   * @param width Width
   * @param height Height
   */
  public setCloudSize(id: string, width: number, height: number): void {
    const cloud = this.clouds.get(id);
    if (!cloud) {
      logger.warn('Cloud not found', { id });
      return;
    }

    if (width <= 0 || height <= 0) {
      throw new Error('Cloud dimensions must be positive');
    }

    const oldWidth = cloud.width;
    const oldHeight = cloud.height;
    cloud.width = width;
    cloud.height = height;

    if (cloud.mesh) {
      const scaleX = width / oldWidth;
      const scaleY = height / oldHeight;
      cloud.mesh.scaling.x *= scaleX;
      cloud.mesh.scaling.y *= scaleY;
    }

    logger.debug('Cloud size changed', { id, size: { width, height } });
  }

  /**
   * Get cloud by ID
   * @param id Cloud ID
   * @returns Cloud instance or null
   */
  public getCloud(id: string): Readonly<CloudInstance> | null {
    return this.clouds.get(id) || null;
  }

  /**
   * Get all clouds
   * @returns Array of cloud instances
   */
  public getAllClouds(): readonly Readonly<CloudInstance>[] {
    return Array.from(this.clouds.values());
  }

  /**
   * Get cloud count
   * @returns Number of clouds
   */
  public getCloudCount(): number {
    return this.clouds.size;
  }

  /**
   * Clear clouds from the scene
   * @param clearAll If false (default), only static clouds (speed = 0) are cleared. If true, all clouds are cleared.
   */
  public clearClouds(clearAll: boolean = false): void {
    const cloudIds = Array.from(this.clouds.keys());
    let clearedCount = 0;

    for (const id of cloudIds) {
      const cloud = this.clouds.get(id);
      if (!cloud) continue;

      // Check if we should remove this cloud
      if (clearAll || cloud.speed === 0) {
        this.removeCloud(id);
        clearedCount++;
      }
    }

    const mode = clearAll ? 'all' : 'static';
    logger.debug(`Cleared ${mode} clouds`, { count: clearedCount });
  }

  /**
   * Start automated cloud animation that creates clouds over time
   * @param jobName Unique name for this animation job
   * @param emitCountPerMinute Number of emit attempts per minute
   * @param emitProbability Probability (0-1) that a cloud is actually created on each emit attempt
   * @param area Area definition for random cloud positioning
   * @param speed Base speed for clouds (slightly randomized)
   * @param direction Wind direction in degrees (0=North, 90=East, 180=South, 270=West) - same for all clouds
   * @param textures Array of texture paths to randomly choose from
   */
  public startCloudsAnimation(
    jobName: string,
    emitCountPerMinute: number,
    emitProbability: number,
    area: Area,
    speed: number,
    direction: number,
    textures: string[]
  ): void {
    // Validate parameters
    if (!jobName || jobName.trim() === '') {
      throw new Error('Job name cannot be empty');
    }

    if (this.animationJobs.has(jobName)) {
      throw new Error(`Animation job '${jobName}' already exists`);
    }

    if (emitCountPerMinute <= 0) {
      throw new Error('Emit count per minute must be positive');
    }

    if (emitProbability < 0 || emitProbability > 1) {
      throw new Error('Emit probability must be between 0 and 1');
    }

    if (!textures || textures.length === 0) {
      throw new Error('At least one texture must be provided');
    }

    if (speed < 0) {
      throw new Error('Speed cannot be negative');
    }

    // Calculate interval in milliseconds
    const intervalMs = (60 * 1000) / emitCountPerMinute;

    // Create animation job
    const job: CloudAnimationJob = {
      jobName,
      emitCountPerMinute,
      emitProbability,
      area,
      speed,
      direction,
      textures,
      intervalId: 0,
      lastEmitTime: Date.now(),
    };

    // Start interval
    const intervalId = window.setInterval(() => {
      this.emitCloudFromJob(job);
    }, intervalMs);

    job.intervalId = intervalId;
    this.animationJobs.set(jobName, job);

    logger.debug('Cloud animation started', {
      jobName,
      emitCountPerMinute,
      emitProbability,
      speed,
      direction,
      textureCount: textures.length,
      intervalMs,
    });
  }

  /**
   * Stop cloud animation job
   * @param jobName Name of the job to stop, or undefined to stop all jobs
   */
  public stopCloudsAnimation(jobName?: string): void {
    if (jobName) {
      // Stop specific job
      const job = this.animationJobs.get(jobName);
      if (!job) {
        logger.warn('Animation job not found', { jobName });
        return;
      }

      window.clearInterval(job.intervalId);
      this.animationJobs.delete(jobName);
      logger.debug('Cloud animation stopped', { jobName });
    } else {
      // Stop all jobs
      const jobNames = Array.from(this.animationJobs.keys());
      for (const name of jobNames) {
        const job = this.animationJobs.get(name);
        if (job) {
          window.clearInterval(job.intervalId);
        }
      }
      this.animationJobs.clear();
      logger.debug('All cloud animations stopped', { count: jobNames.length });
    }
  }

  /**
   * Get list of active animation jobs
   * @returns Array of job names
   */
  public getActiveAnimationJobs(): string[] {
    return Array.from(this.animationJobs.keys());
  }

  /**
   * Remove all clouds from the scene
   * @deprecated Use clearClouds(true) instead
   */
  public clearAllClouds(): void {
    this.clearClouds(true);
  }

  /**
   * Update clouds (called every frame)
   * @param deltaTime Time since last frame in seconds
   */
  public update(deltaTime: number): void {
    // Get camera position for fade calculation
    const cameraPos = this.cameraService.getPosition();
    if (!cameraPos) return;

    for (const cloud of this.clouds.values()) {
      if (!cloud.enabled) continue;

      // Update movement
      if (cloud.speed > 0) {
        const dirRad = (cloud.direction * Math.PI) / 180;
        const velocityX = Math.sin(dirRad) * cloud.speed * deltaTime;
        const velocityZ = Math.cos(dirRad) * cloud.speed * deltaTime;

        cloud.currentX += velocityX;
        cloud.currentZ += velocityZ;

        if (cloud.root) {
          cloud.root.position.set(cloud.currentX, cloud.y, cloud.currentZ);
        }
      }

      // Update fade based on distance to camera
      const dx = cloud.currentX - cameraPos.x;
      const dz = cloud.currentZ - cameraPos.z;
      const distance = Math.sqrt(dx * dx + dz * dz);

      // Calculate fade alpha
      if (distance < this.fadeEndDistance) {
        cloud.fadeAlpha = 1.0;  // Fully visible
      } else if (distance < this.fadeStartDistance) {
        // Smooth fade
        const fadeRange = this.fadeStartDistance - this.fadeEndDistance;
        cloud.fadeAlpha = 1.0 - (distance - this.fadeEndDistance) / fadeRange;
      } else {
        cloud.fadeAlpha = 0.0;  // Invisible
      }

      // Apply fade to material
      if (cloud.material) {
        cloud.material.alpha = cloud.fadeAlpha;
      }
    }
  }

  /**
   * Dispose clouds service
   */
  public dispose(): void {
    // Stop all animations
    this.stopCloudsAnimation();

    // Dispose all clouds
    for (const cloud of this.clouds.values()) {
      this.disposeCloud(cloud);
    }
    this.clouds.clear();
    this.cloudsRoot?.dispose();
    logger.debug('CloudsService disposed');
  }

  // ========== Private Helper Methods ==========

  /**
   * Validate cloud configuration
   */
  private validateCloudConfig(config: CloudConfig): void {
    if (config.width <= 0 || config.height <= 0) {
      throw new Error('Cloud dimensions must be positive');
    }

    if (config.speed < 0) {
      throw new Error('Cloud speed cannot be negative');
    }

    if (config.level < 0 || config.level > 9) {
      throw new Error('Cloud level must be between 0 and 9');
    }

    if (!config.texture || config.texture.trim() === '') {
      throw new Error('Cloud texture path cannot be empty');
    }

    if (!config.id || config.id.trim() === '') {
      throw new Error('Cloud ID cannot be empty');
    }
  }

  /**
   * Load cloud texture
   */
  private async loadCloudTexture(texturePath: string): Promise<Texture | RawTexture> {
    if (!this.networkService) {
      logger.warn('NetworkService not available, using fallback texture');
      return this.createFallbackTexture();
    }

    try {
      const textureUrl = this.networkService.getAssetUrl(texturePath);

      // Load texture with credentials
      const blobUrl = await loadTextureUrlWithCredentials(textureUrl);

      return await new Promise<Texture>((resolve, reject) => {
        const texture = new Texture(
          blobUrl,
          this.scene,
          false,  // noMipmap
          true,   // invertY
          Constants.TEXTURE_TRILINEAR_SAMPLINGMODE,
          () => {
            logger.debug('Cloud texture loaded', { path: texturePath });
            resolve(texture);
          },
          (message) => {
            logger.error('Failed to load cloud texture', { path: texturePath, error: message });
            reject(new Error(`Failed to load texture: ${message}`));
          }
        );
        texture.hasAlpha = true;
      }).catch(() => {
        // Return fallback on error
        return this.createFallbackTexture();
      });
    } catch (error) {
      logger.error('Error loading cloud texture, using fallback', { error, path: texturePath });
      return this.createFallbackTexture();
    }
  }

  /**
   * Create fallback texture (white cloud with soft edges)
   */
  private createFallbackTexture(): RawTexture {
    const size = 256;
    const center = size / 2;
    const textureData = new Uint8Array(size * size * 4);

    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const dx = x - center;
        const dy = y - center;
        const dist = Math.sqrt(dx * dx + dy * dy);

        let alpha = 0;
        const radius = size / 2 - 10;

        if (dist < radius) {
          const edgeDist = radius - dist;
          if (edgeDist < 20) {
            alpha = edgeDist / 20;  // Soft edge
          } else {
            alpha = 1.0;  // Full opacity
          }
        }

        const idx = (y * size + x) * 4;
        textureData[idx] = 255;      // R
        textureData[idx + 1] = 255;  // G
        textureData[idx + 2] = 255;  // B
        textureData[idx + 3] = Math.floor(alpha * 255);  // A
      }
    }

    const texture = RawTexture.CreateRGBATexture(
      textureData,
      size,
      size,
      this.scene,
      false,
      false,
      Constants.TEXTURE_BILINEAR_SAMPLINGMODE
    );

    logger.debug('Fallback cloud texture created');
    return texture;
  }

  /**
   * Create cloud material
   */
  private createCloudMaterial(cloud: CloudInstance): StandardMaterial {
    const material = new StandardMaterial(`cloud_${cloud.id}_material`, this.scene);

    material.diffuseTexture = cloud.texture!;
    material.emissiveTexture = cloud.texture!;
    material.disableLighting = true;
    material.useAlphaFromDiffuseTexture = true;
    material.transparencyMode = StandardMaterial.MATERIAL_ALPHABLEND;
    material.alpha = cloud.fadeAlpha;
    material.backFaceCulling = false;

    return material;
  }

  /**
   * Create cloud mesh
   */
  private createCloudMesh(cloud: CloudInstance): Mesh {
    // Use CreateGround for horizontal plane (lies flat on XZ plane)
    const mesh = MeshBuilder.CreateGround(
      `cloud_${cloud.id}`,
      {
        width: cloud.width,
        height: cloud.height,  // height parameter is depth in Z direction
      },
      this.scene
    );

    mesh.material = cloud.material!;
    // Note: infiniteDistance removed - clouds are at fixed camera-relative positions
    mesh.renderingGroupId = RENDERING_GROUPS.ENVIRONMENT;

    return mesh;
  }

  /**
   * Dispose a single cloud
   */
  private disposeCloud(cloud: CloudInstance): void {
    try {
      cloud.mesh?.dispose();
      cloud.material?.dispose();
      cloud.texture?.dispose();
      cloud.root?.dispose();
    } catch (error) {
      logger.warn('Failed to dispose cloud', { id: cloud.id, error });
    }
  }

  /**
   * Emit a cloud from an animation job
   */
  private emitCloudFromJob(job: CloudAnimationJob): void {
    // Check probability
    if (Math.random() > job.emitProbability) {
      return;
    }

    try {
      // Generate random values within area constraints
      const x = this.randomInRange(job.area.minX, job.area.maxX);
      const z = this.randomInRange(job.area.minZ, job.area.maxZ);
      const y = this.randomInRange(job.area.minY, job.area.maxY);
      const width = this.randomInRange(job.area.minWidth, job.area.maxWidth);
      const height = this.randomInRange(job.area.minHeight, job.area.maxHeight);

      // Speed varies slightly with height (up to 1% faster)
      const heightFactor = (y - job.area.minY) / (job.area.maxY - job.area.minY);
      const speedVariation = 1.0 + (heightFactor * 0.01);
      const speed = job.speed * speedVariation;

      // Random texture
      const texture = job.textures[Math.floor(Math.random() * job.textures.length)];

      // Generate UUID for cloud ID
      const id = this.generateUUID();

      // Create cloud configuration
      const config: CloudConfig = {
        id,
        level: 5, // Default level
        startX: x,
        startZ: z,
        y,
        width,
        height,
        texture,
        speed,
        direction: job.direction, // Use fixed wind direction from job
      };

      // Add cloud (async, but we don't wait)
      this.addCloud(config).catch((error) => {
        logger.warn('Failed to emit cloud from animation', { jobName: job.jobName, error });
      });
    } catch (error) {
      logger.error('Error emitting cloud', { jobName: job.jobName, error });
    }
  }

  /**
   * Generate random value in range
   */
  private randomInRange(min: number, max: number): number {
    return min + Math.random() * (max - min);
  }

  /**
   * Generate a simple UUID
   */
  private generateUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
}
