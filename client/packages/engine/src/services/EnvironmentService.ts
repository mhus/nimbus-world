/**
 * EnvironmentService - Manages scene environment
 *
 * Handles lighting, sky, fog, and other environmental effects.
 */

import {
  HemisphericLight,
  DirectionalLight,
  Vector3,
  Color3,
  Scene,
  ShadowGenerator,
  CascadedShadowGenerator,
  RenderTargetTexture,
} from '@babylonjs/core';
import { getLogger, ExceptionHandler, SeasonStatus } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { ScriptActionDefinition } from '@nimbus/shared';

const logger = getLogger('EnvironmentService');

/**
 * Shadow quality presets
 */
const QUALITY_PRESETS = {
  low: {
    mapSize: 512,
    useESM: false,
    usePCF: false,
    bias: 0.0001,
  },
  medium: {
    mapSize: 1024,
    useESM: true,
    usePCF: false,
    bias: 0.00005,
  },
  high: {
    mapSize: 2048,
    useESM: true,
    usePCF: true,
    bias: 0.00002,
  },
} as const;

/**
 * Wind parameters for environment
 */
export interface WindParameters {
  /** Wind direction as a 2D vector (x, z) - normalized */
  windDirection: { x: number; z: number };

  /** Base wind strength (0-1) */
  windStrength: number;

  /** Wind gust strength (0-1) - additional random wind impulses */
  windGustStrength: number;

  /** Wind sway factor (0-2) - multiplier for how much blocks sway */
  windSwayFactor: number;

  /** Current time for wind animation */
  time: number;
}

/**
 * Environment script definition
 * Scripts are stored by action name
 */
export interface EnvironmentScript {
  /** Action name (unique identifier) */
  name: string;

  /** Script name to execute (reference to script in script registry) */
  script: string;
}

/**
 * Running environment script information
 */
interface RunningEnvironmentScript {
  /** Action name */
  name: string;

  /** Executor ID from ScrawlService */
  executorId: string;

  /** Start time (timestamp) */
  startTime: number;
}

/**
 * World Time configuration
 */
export interface WorldTimeConfig {
  /** @Minute scaling: How many world minutes pass per real minute */
  minuteScaling: number;
  /** @Hour: How many @Minutes in one @Hour */
  minutesPerHour: number;
  /** @Day: How many @Hours in one @Day */
  hoursPerDay: number;
  /** @Month: How many @Days in one @Month */
  daysPerMonth: number;
  /** @Year: How many @Months in one @Year */
  monthsPerYear: number;
}

/**
 * Day section definitions
 */
export interface DaySectionConfig {
  /** Morning start @Hour */
  morningStart: number;
  /** Day start @Hour */
  dayStart: number;
  /** Evening start @Hour */
  eveningStart: number;
  /** Night start @Hour */
  nightStart: number;
}

/**
 * Day section type
 */
export type DaySection = 'morning' | 'day' | 'evening' | 'night';

/**
 * World Time state
 */
interface WorldTimeState {
  /** Is world time running */
  running: boolean;
  /** Start world time in @Minutes */
  startWorldMinute: number;
  /** Real time when world time was started (timestamp in ms) */
  startRealTime: number;
}

/**
 * Celestial bodies configuration
 */
export interface CelestialBodiesConfig {
  /** Enable automatic sun/moon position updates */
  enabled: boolean;
  /** Update interval in seconds */
  updateIntervalSeconds: number;
  /** Number of active moons (0-3) */
  activeMoons: number;
  /** Full rotation time for sun in @Hours */
  sunRotationHours: number;
  /** Full rotation time for moon 0 in @Hours */
  moon0RotationHours: number;
  /** Full rotation time for moon 1 in @Hours */
  moon1RotationHours: number;
  /** Full rotation time for moon 2 in @Hours */
  moon2RotationHours: number;
}

/**
 * EnvironmentService - Manages environment rendering
 *
 * Features:
 * - Hemispheric lighting
 * - Background color
 * - Wind parameters for wind-affected blocks
 * - Future: Sky, fog, weather effects
 */
export class EnvironmentService {
  private scene: Scene;
  private appContext: AppContext;

  private ambientLight?: HemisphericLight;
  private sunLight?: DirectionalLight;

  // Wind parameters
  private windParameters: WindParameters;

  // Shadow system
  private shadowGenerator: ShadowGenerator | CascadedShadowGenerator | null = null;
  private shadowEnabled: boolean = false;
  private shadowQuality: 'low' | 'medium' | 'high' = 'low';
  private shadowMaxDistance: number = 50;

  // Ambient audio modifier (priority 50)
  private ambientAudioModifier?: any; // Modifier<string>

  // Environment script management
  private environmentScripts: Map<string, EnvironmentScript> = new Map();
  private runningScripts: Map<string, RunningEnvironmentScript> = new Map();

  // World Time management
  private worldTimeConfig: WorldTimeConfig;
  private daySectionConfig: DaySectionConfig;
  private worldTimeState: WorldTimeState;
  private currentDaySection: DaySection | null = null;
  private currentSeason: SeasonStatus = SeasonStatus.NONE;

  // Celestial bodies automatic update
  private celestialBodiesConfig: CelestialBodiesConfig;
  private celestialUpdateTimer: number = 0;
  private lastCelestialUpdateTime: number = 0;
  private sunPositionModifier?: any;
  private moonPositionModifiers: Map<number, any> = new Map();

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    // Initialize wind parameters with defaults
    this.windParameters = {
      windDirection: { x: 1, z: 0 }, // Default: wind from west (positive X)
      windStrength: 0.3, // 30% base wind strength
      windGustStrength: 0.15, // 15% gust strength
      windSwayFactor: 1.0, // 100% sway factor (neutral)
      time: 0, // Initialize time
    };

    // Initialize World Time configuration with defaults
    this.worldTimeConfig = this.loadWorldTimeConfigFromWorldInfo();
    this.daySectionConfig = this.loadDaySectionConfigFromWorldInfo();
    this.celestialBodiesConfig = this.loadCelestialBodiesConfigFromWorldInfo();
    this.worldTimeState = {
      running: false,
      startWorldMinute: 0,
      startRealTime: 0,
    };

    this.initializeEnvironment();
    this.initializeAmbientAudioModifier();
    this.initializeShadows();
    this.loadEnvironmentScriptsFromWorldInfo();

    logger.debug('EnvironmentService initialized', {
      windParameters: this.windParameters,
      worldTimeConfig: this.worldTimeConfig,
      daySectionConfig: this.daySectionConfig,
      celestialBodiesConfig: this.celestialBodiesConfig,
      shadowsEnabled: this.shadowEnabled,
    });
  }

  /**
   * Initialize ambient audio modifier
   * Environment can set ambient music at priority 50
   */
  private initializeAmbientAudioModifier(): void {
    const modifierService = this.appContext.services.modifier;
    if (!modifierService) {
      logger.debug('ModifierService not available, ambient audio modifier not created');
      return;
    }

    const stack = modifierService.getModifierStack<string>('ambientAudio');
    if (stack) {
      // Create environment modifier (priority 50)
      this.ambientAudioModifier = stack.addModifier('', 50);
      this.ambientAudioModifier.setEnabled(false); // Disabled by default
      logger.debug('Environment ambient audio modifier created', { prio: 50 });
    }
  }

  /**
   * Set environment ambient audio
   * @param soundPath Path to ambient music (empty to clear)
   */
  setEnvironmentAmbientAudio(soundPath: string): void {
    if (!this.ambientAudioModifier) {
      logger.warn('Ambient audio modifier not initialized');
      return;
    }

    this.ambientAudioModifier.setValue(soundPath);
    this.ambientAudioModifier.setEnabled(soundPath.trim() !== '');

    logger.debug('Environment ambient audio set', { soundPath, enabled: soundPath.trim() !== '' });
  }

  /**
   * Initialize environment
   */
  private initializeEnvironment(): void {
    try {
      // Set background color from WorldInfo or use default (light blue sky)
      const settings = this.appContext.worldInfo?.settings;
      const clearColor = settings?.clearColor
        ? new Color3(settings.clearColor.r, settings.clearColor.g, settings.clearColor.b)
        : new Color3(0.5, 0.7, 1.0); // Default sky blue

      this.scene.clearColor = clearColor.toColor4();

      // Create ambient hemispheric light
      this.ambientLight = new HemisphericLight('ambientLight', new Vector3(0, 1, 0), this.scene);

      // Set ambient light properties - MEDIUM INTENSITY for shadow visibility
      this.ambientLight.intensity = 0.85; // Balanced for shadows + visibility
      this.ambientLight.diffuse = new Color3(1, 1, 1); // White light
      this.ambientLight.specular = new Color3(0, 0, 0); // No specular
      this.ambientLight.groundColor = new Color3(0.3, 0.3, 0.3); // Dim ground light

      // Create sun directional light
      this.sunLight = new DirectionalLight('sunLight', new Vector3(-1, -2, -1), this.scene);

      // Set sun light properties
      this.sunLight.intensity = 0.8;
      this.sunLight.diffuse = new Color3(1, 0.95, 0.9); // Warm sunlight
      this.sunLight.specular = new Color3(1, 1, 1); // White specular highlights

      logger.debug('Environment initialized', {
        ambientLightIntensity: this.ambientLight.intensity,
        sunLightIntensity: this.sunLight.intensity,
        backgroundColor: this.scene.clearColor,
      });
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'EnvironmentService.initializeEnvironment');
    }
  }

  /**
   * Get the ambient light
   */
  getAmbientLight(): HemisphericLight | undefined {
    return this.ambientLight;
  }

  /**
   * Get the sun light
   */
  getSunLight(): DirectionalLight | undefined {
    return this.sunLight;
  }

  // ============================================
  // Ambient Light Management
  // ============================================

  /**
   * Set ambient light intensity
   *
   * @param intensity Light intensity (0-1 for normal, can go higher)
   */
  setAmbientLightIntensity(intensity: number): void {
    if (!this.ambientLight) {
      logger.warn('Cannot set ambient light intensity: light not initialized');
      return;
    }

    this.ambientLight.intensity = intensity;
    logger.debug('Ambient light intensity set', { intensity });
  }

  /**
   * Get ambient light intensity
   */
  getAmbientLightIntensity(): number {
    return this.ambientLight?.intensity ?? 0;
  }

  /**
   * Set ambient light specular color
   *
   * @param color Specular color
   */
  setAmbientLightSpecularColor(color: Color3): void {
    if (!this.ambientLight) {
      logger.warn('Cannot set ambient light specular color: light not initialized');
      return;
    }

    this.ambientLight.specular = color;
    logger.debug('Ambient light specular color set', { color });
  }

  /**
   * Set ambient light diffuse color
   *
   * @param color Diffuse color
   */
  setAmbientLightDiffuse(color: Color3): void {
    if (!this.ambientLight) {
      logger.warn('Cannot set ambient light diffuse color: light not initialized');
      return;
    }

    this.ambientLight.diffuse = color;
    logger.debug('Ambient light diffuse color set', { color });
  }

  /**
   * Set ambient light ground color
   *
   * @param color Ground color
   */
  setAmbientLightGroundColor(color: Color3): void {
    if (!this.ambientLight) {
      logger.warn('Cannot set ambient light ground color: light not initialized');
      return;
    }

    this.ambientLight.groundColor = color;
    logger.debug('Ambient light ground color set', { color });
  }

  // ============================================
  // Sun Light Management
  // ============================================

  /**
   * Set sun light intensity
   *
   * @param intensity Light intensity (0-1 for normal, can go higher)
   */
  setSunLightIntensity(intensity: number): void {
    if (!this.sunLight) {
      logger.warn('Cannot set sun light intensity: light not initialized');
      return;
    }

    this.sunLight.intensity = intensity;
    logger.debug('Sun light intensity set', { intensity });
  }

  /**
   * Get sun light intensity
   */
  getSunLightIntensity(): number {
    return this.sunLight?.intensity ?? 0;
  }

  /**
   * Set sun light direction (vector will be normalized)
   *
   * @param x X component of direction
   * @param y Y component of direction
   * @param z Z component of direction
   */
  setSunLightDirection(x: number, y: number, z: number): void {
    if (!this.sunLight) {
      logger.warn('Cannot set sun light direction: light not initialized');
      return;
    }

    const direction = new Vector3(x, y, z).normalize();
    this.sunLight.direction = direction;
    logger.debug('Sun light direction set', { x: direction.x, y: direction.y, z: direction.z });
  }

  /**
   * Get sun light direction
   */
  getSunLightDirection(): Vector3 {
    return this.sunLight?.direction ?? new Vector3(0, -1, 0);
  }

  /**
   * Set sun light diffuse color
   *
   * @param color Diffuse color
   */
  setSunLightDiffuse(color: Color3): void {
    if (!this.sunLight) {
      logger.warn('Cannot set sun light diffuse color: light not initialized');
      return;
    }

    this.sunLight.diffuse = color;
    logger.debug('Sun light diffuse color set', { color });
  }

  /**
   * Set sun light specular color
   *
   * @param color Specular color
   */
  setSunLightSpecularColor(color: Color3): void {
    if (!this.sunLight) {
      logger.warn('Cannot set sun light specular color: light not initialized');
      return;
    }

    this.sunLight.specular = color;
    logger.debug('Sun light specular color set', { color });
  }

  /**
   * Set background color
   *
   * @param r Red (0-1)
   * @param g Green (0-1)
   * @param b Blue (0-1)
   */
  setBackgroundColor(r: number, g: number, b: number): void {
    this.scene.clearColor = new Color3(r, g, b).toColor4();
  }

  // ============================================
  // Shadow System Management
  // ============================================

  /**
   * Initialize shadow system from WorldInfo
   */
  private initializeShadows(): void {
    if (!this.sunLight) {
      logger.warn('Cannot initialize shadows: sunLight not available');
      return;
    }

    try {
      // Read from WorldInfo or use defaults
      const shadowSettings = this.appContext.worldInfo?.settings?.shadows;
      const enabled = shadowSettings?.enabled ?? true; // Default: enabled
      const quality = shadowSettings?.quality ?? 'low'; // Default: low
      const distance = shadowSettings?.maxDistance ?? 1000; // Default: 100 blocks
      const darkness = shadowSettings?.darkness ?? 0.8; // Default: 0.5

      if (!enabled) {
        logger.debug('Shadows disabled in WorldInfo');
        return;
      }

      // Get map size from quality preset
      const mapSize = QUALITY_PRESETS[quality].mapSize;

      // Create CascadedShadowGenerator
      const cascadedGen = new CascadedShadowGenerator(mapSize, this.sunLight, true, this.appContext.services?.camera?.getCamera() );
      cascadedGen.lambda = 0.5; // Optimized for better near-distance shadow quality
      cascadedGen.filter = 0;
      cascadedGen.numCascades = 2;
      cascadedGen.transparencyShadow = true;
      cascadedGen.darkness = darkness;

      // CRITICAL: Stabilize cascades to prevent shadow jumping during camera rotation
      cascadedGen.stabilizeCascades = true;

      // Disable autoCalcDepthBounds to prevent shadow wobbling
      cascadedGen.autoCalcDepthBounds = false;

      this.shadowGenerator = cascadedGen;
      this.shadowQuality = quality;
      this.shadowEnabled = true;
      this.setShadowDistance(distance);

      // Call splitFrustum (required for CascadedShadowGenerator)
      cascadedGen.splitFrustum();

      this.setShadowDistance(distance);

      logger.debug('Shadow system initialized', {
        type: 'CascadedShadowGenerator',
        mapSize: mapSize,
        quality: quality,
        distance: distance,
        stabilizeCascades: true,
        autoCalcDepthBounds: false,
        lambda: cascadedGen.lambda
      });
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'EnvironmentService.initializeShadows');
    }
  }

  /**
   * Apply shadow quality preset settings
   */
  private applyShadowQualitySettings(quality: 'low' | 'medium' | 'high'): void {
    if (!this.shadowGenerator) return;

    const preset = QUALITY_PRESETS[quality];

    // Configure filtering
    this.shadowGenerator.useExponentialShadowMap = preset.useESM;
    this.shadowGenerator.usePercentageCloserFiltering = preset.usePCF;

    // Set bias to prevent shadow acne
    this.shadowGenerator.bias = preset.bias;
    this.shadowGenerator.normalBias = 0;

    // For PCF, set filtering quality
    if (preset.usePCF) {
      this.shadowGenerator.filteringQuality = ShadowGenerator.QUALITY_MEDIUM;
    }

    logger.debug('Shadow quality settings applied', { quality, preset });
  }

  /**
   * Enable or disable shadows
   *
   * @param enabled True to enable shadows, false to disable
   */
  setShadowsEnabled(enabled: boolean): void {
    // If enabling shadows but generator doesn't exist, create it
    if (enabled && !this.shadowGenerator) {
      logger.debug('Creating shadow generator on-the-fly');

      if (!this.sunLight) {
        logger.warn('Cannot create shadow generator: sunLight not available');
        return;
      }

      try {
        // Create shadow generator with default settings
        const quality = 'low'; // Start with low quality
        const mapSize = QUALITY_PRESETS[quality].mapSize;

        this.shadowGenerator = new ShadowGenerator(mapSize, this.sunLight);
        this.applyShadowQualitySettings(quality);
        this.shadowGenerator.setDarkness(1.0); // Full shadow darkness
        this.shadowQuality = quality;

        // Important: Set light position for shadow frustum calculation
        // DirectionalLight needs a position to calculate the shadow frustum
        this.sunLight.position = new Vector3(50, 100, 50);

        // Enable automatic shadow frustum updates based on casters
        // Note: Using type assertion as this property exists at runtime but not in type definitions
        (this.sunLight as any).autoUpdateExtentsShadowMap = true;

        // Set shadow min/max Z for better shadow quality
        this.sunLight.shadowMinZ = 1;
        this.sunLight.shadowMaxZ = 200;

        logger.debug('Shadow generator created successfully', { quality, mapSize });
      } catch (error) {
        logger.error('Failed to create shadow generator', { error });
        return;
      }
    }

    // If disabling or generator exists, proceed
    if (!this.shadowGenerator) {
      logger.warn('Shadow generator not initialized');
      return;
    }

    this.shadowEnabled = enabled;
    this.shadowGenerator.getShadowMap()!.renderList = enabled
      ? this.shadowGenerator.getShadowMap()!.renderList
      : [];

    logger.debug('Shadows ' + (enabled ? 'enabled' : 'disabled'));
  }

  setShadowDistance(distance: number): boolean {
    if (!this.shadowGenerator) {
      logger.warn('Shadow generator not initialized');
      return false;
    }

    const shadowGenerator = this.getShadowGenerator();
    if (!shadowGenerator) {
      return false;
    }

    // Set shadowMaxZ (controls how far shadows are rendered)
    const shadowMaxZ = distance * 10; // Convert blocks to units
    (shadowGenerator as any).shadowMaxZ = shadowMaxZ;

    // Also update camera maxZ to match
    const cameraService = this.appContext.services.camera;
    if (cameraService) {
      const camera = cameraService.getCamera();
      if (camera) {
        camera.maxZ = Math.max(shadowMaxZ, 1000);
      }
    }

    // NOTE: DO NOT call splitFrustum() here - it causes duplicates/issues
    // The shadow frustum will auto-adjust

    logger.debug('Shadow distance changed', {
      distance,
      shadowMaxZ,
    });

    return true;
  }

  /**
   * Set shadow intensity (darkness)
   *
   * @param intensity Shadow darkness (0.0 = no shadows/fully lit, 1.0 = very dark shadows)
   */
  setShadowIntensity(intensity: number): void {
    if (!this.shadowGenerator) {
      logger.warn('Shadow generator not initialized');
      return;
    }

    // Clamp intensity to 0-1
    const clampedIntensity = Math.max(0, Math.min(1, intensity));

    // BabylonJS setDarkness: 0 = no shadow, 1 = full shadow
    this.shadowGenerator.setDarkness(clampedIntensity);

    logger.debug('Shadow intensity set', { intensity: clampedIntensity });
  }

  /**
   * Set shadow quality preset
   *
   * @param quality Quality preset: 'low', 'medium', or 'high'
   */
  setShadowQuality(quality: 'low' | 'medium' | 'high'): void {
    if (!this.shadowGenerator || !this.sunLight) {
      logger.warn('Shadow generator not initialized');
      return;
    }

    const oldMapSize = this.shadowGenerator.getShadowMap()?.getSize().width || 512;
    const newMapSize = QUALITY_PRESETS[quality].mapSize;

    // If map size changed, recreate shadow generator
    if (oldMapSize !== newMapSize) {
      logger.debug('Shadow map size changed, recreating shadow generator', {
        oldSize: oldMapSize,
        newSize: newMapSize,
      });

      // Store render list
      const renderList = this.shadowGenerator.getShadowMap()!.renderList || [];

      // Dispose old generator
      this.shadowGenerator.dispose();

      // Create new CascadedShadowGenerator (same as initializeShadows)
      const cascadedGen = new CascadedShadowGenerator(newMapSize, this.sunLight);
      cascadedGen.lambda = 0.5; // Optimized for better near-distance shadow quality
      cascadedGen.filter = 0;
      cascadedGen.numCascades = 2;
      cascadedGen.transparencyShadow = true;

      // CRITICAL: Stabilize cascades to prevent shadow jumping during camera rotation
      cascadedGen.stabilizeCascades = true;

      // Disable autoCalcDepthBounds to prevent shadow wobbling
      cascadedGen.autoCalcDepthBounds = false;

      this.shadowGenerator = cascadedGen;

      // Restore render list (push directly, not via addShadowCaster)
      const newRenderList = cascadedGen.getShadowMap()!.renderList!;
      for (const mesh of renderList) {
        newRenderList.push(mesh);
      }

      // Call splitFrustum for CascadedShadowGenerator
      cascadedGen.splitFrustum();

      logger.debug('Shadow stabilization reapplied after quality change', {
        stabilizeCascades: true,
        autoCalcDepthBounds: false,
        lambda: cascadedGen.lambda
      });
    }

    // Apply quality settings
    this.applyShadowQualitySettings(quality);
    this.shadowQuality = quality;

    logger.debug('Shadow quality set', { quality });
  }

  /**
   * Get shadow generator for other services
   *
   * @returns Shadow generator or null if not initialized
   */
  getShadowGenerator(): ShadowGenerator | null {
    return this.shadowGenerator;
  }

  /**
   * Get shadow system info for debugging
   *
   * @returns Shadow system information
   */
  getShadowInfo(): {
    enabled: boolean;
    quality: string;
    mapSize: number;
    activeCasters: number;
    darkness: number;
    refreshRate: number;
    stabilizeCascades?: boolean;
    autoCalcDepthBounds?: boolean;
    lambda?: number;
    numCascades?: number;
  } {
    if (!this.shadowGenerator) {
      return {
        enabled: false,
        quality: 'none',
        mapSize: 0,
        activeCasters: 0,
        darkness: 0,
        refreshRate: 0,
      };
    }

    const shadowMap = this.shadowGenerator.getShadowMap();
    const darkness = this.shadowGenerator.getDarkness();

    // CSM-specific properties
    const cascadedGen = this.shadowGenerator as any;
    const stabilizeCascades = cascadedGen.stabilizeCascades;
    const autoCalcDepthBounds = cascadedGen.autoCalcDepthBounds;
    const lambda = cascadedGen.lambda;
    const numCascades = cascadedGen.numCascades;

    return {
      enabled: this.shadowEnabled,
      quality: this.shadowQuality,
      mapSize: shadowMap?.getSize().width || 0,
      activeCasters: shadowMap?.renderList?.length || 0,
      darkness: darkness,
      refreshRate: shadowMap?.refreshRate || 0,
      stabilizeCascades,
      autoCalcDepthBounds,
      lambda,
      numCascades,
    };
  }

  /**
   * Force shadow map refresh (useful after adding casters)
   */
  refreshShadowMap(): void {
    if (!this.shadowGenerator) {
      logger.warn('Shadow generator not initialized');
      return;
    }

    const shadowMap = this.shadowGenerator.getShadowMap();
    if (shadowMap) {
      // Temporarily enable refresh, then set back
      const oldRate = shadowMap.refreshRate;
      shadowMap.refreshRate = RenderTargetTexture.REFRESHRATE_RENDER_ONEVERYFRAME;

      // Set back after next frame
      setTimeout(() => {
        shadowMap.refreshRate = oldRate;
        logger.debug('Shadow map refreshed');
      }, 100);
    }
  }

  /**
   * Update environment (called each frame if needed)
   *
   * @param deltaTime Time since last frame in seconds
   */
  update(deltaTime: number): void {
    this.updateWorldTime(deltaTime);
  }

  // ============================================
  // Wind Parameter Management
  // ============================================

  /**
   * Get current wind parameters
   */
  getWindParameters(): WindParameters {
    return { ...this.windParameters };
  }

  /**
   * Set wind direction (normalizes the vector)
   * @param x X component of wind direction
   * @param z Z component of wind direction
   */
  setWindDirection(x: number, z: number): void {
    // Normalize the direction vector
    const length = Math.sqrt(x * x + z * z);
    if (length > 0) {
      this.windParameters.windDirection.x = x / length;
      this.windParameters.windDirection.z = z / length;
    } else {
      // Default to east if zero vector provided
      this.windParameters.windDirection.x = 1;
      this.windParameters.windDirection.z = 0;
    }

    logger.debug('Wind direction set', {
      x: this.windParameters.windDirection.x.toFixed(2),
      z: this.windParameters.windDirection.z.toFixed(2),
    });
  }

  /**
   * Get wind direction
   */
  getWindDirection(): { x: number; z: number } {
    return { ...this.windParameters.windDirection };
  }

  /**
   * Set wind strength (clamped to 0-1)
   * @param strength Wind strength (0-1)
   */
  setWindStrength(strength: number): void {
    this.windParameters.windStrength = Math.max(0, Math.min(1, strength));
    logger.debug('Wind strength set', {
      strength: this.windParameters.windStrength.toFixed(2),
    });
  }

  /**
   * Get wind strength
   */
  getWindStrength(): number {
    return this.windParameters.windStrength;
  }

  /**
   * Set wind gust strength (clamped to 0-1)
   * @param strength Gust strength (0-1)
   */
  setWindGustStrength(strength: number): void {
    this.windParameters.windGustStrength = Math.max(0, Math.min(1, strength));
    logger.debug('Wind gust strength set', {
      gustStrength: this.windParameters.windGustStrength.toFixed(2),
    });
  }

  /**
   * Get wind gust strength
   */
  getWindGustStrength(): number {
    return this.windParameters.windGustStrength;
  }

  /**
   * Set wind sway factor (clamped to 0-2)
   * @param factor Sway factor (0-2)
   */
  setWindSwayFactor(factor: number): void {
    this.windParameters.windSwayFactor = Math.max(0, Math.min(2, factor));
    logger.debug('Wind sway factor set', {
      swayFactor: this.windParameters.windSwayFactor.toFixed(2),
    });
  }

  /**
   * Get wind sway factor
   */
  getWindSwayFactor(): number {
    return this.windParameters.windSwayFactor;
  }

  // ============================================
  // Environment Script Management
  // ============================================

  /**
   * Load environment scripts from WorldInfo
   */
  private loadEnvironmentScriptsFromWorldInfo(): void {
    const worldInfo = this.appContext.worldInfo;
    if (!worldInfo?.settings?.environmentScripts) {
      logger.debug('No environment scripts defined in WorldInfo');
      return;
    }

    const scripts = worldInfo.settings.environmentScripts;
    if (Array.isArray(scripts)) {
      for (const scriptDef of scripts) {
        if (scriptDef.name && scriptDef.script) {
          this.environmentScripts.set(scriptDef.name, scriptDef);
          logger.debug('Loaded environment script from WorldInfo', {
            name: scriptDef.name,
            script: scriptDef.script,
          });
        }
      }
    }
    logger.debug(`Loaded ${this.environmentScripts.size} environment scripts total`);
  }

  /**
   * Create/register an environment script
   *
   * @param name Action name (unique identifier)
   * @param scriptName Script name to execute (reference to script in script registry)
   */
  createEnvironmentScript(name: string, scriptName: string): void {
    const environmentScript: EnvironmentScript = {
      name,
      script: scriptName,
    };

    this.environmentScripts.set(name, environmentScript);
    logger.debug('Environment script created', { name, script: scriptName });
  }

  /**
   * Delete an environment script
   *
   * @param name Script name
   */
  deleteEnvironmentScript(name: string): boolean {
    const existed = this.environmentScripts.delete(name);
    if (existed) {
      logger.debug('Environment script deleted', { name });
    } else {
      logger.warn('Environment script not found for deletion', { name });
    }
    return existed;
  }

  /**
   * Get an environment script by name
   *
   * @param name Script name
   */
  getEnvironmentScript(name: string): EnvironmentScript | undefined {
    return this.environmentScripts.get(name);
  }

  /**
   * Get all environment scripts
   */
  getAllEnvironmentScripts(): EnvironmentScript[] {
    return Array.from(this.environmentScripts.values());
  }

  /**
   * Start an environment script
   * If a script with the same action name is already running, it will be stopped first
   * If the script is not found in environmentScripts, it will try to start it directly by name
   *
   * @param name Action name (or script name if not in environmentScripts)
   * @returns Executor ID or null if ScrawlService unavailable or script execution failed
   */
  async startEnvironmentScript(name: string): Promise<string | null> {
    const scrawlService = this.appContext.services.scrawl;
    if (!scrawlService) {
      logger.error('ScrawlService not available');
      return null;
    }

    // Stop any running script with the same action name
    await this.stopEnvironmentScript(name);

    try {
      const scriptDef = this.environmentScripts.get(name);
      let scriptId: string;

      if (scriptDef) {
        // Use script from environmentScripts mapping
        scriptId = scriptDef.script;
        logger.debug('Starting environment script from mapping', {
          name: scriptDef.name,
          scriptId: scriptDef.script,
        });
      } else {
        // Try to start script directly by name
        scriptId = name;
        logger.debug('Starting environment script directly by name', {
          scriptId: name,
        });
      }

      // Get script from script registry by ID
      const scriptAction: ScriptActionDefinition = {
        scriptId: scriptId,
        sendToServer: false, // Execute locally only
      };

      const executorId = await scrawlService.executeAction(scriptAction);

      const runningScript: RunningEnvironmentScript = {
        name: name,
        executorId,
        startTime: Date.now(),
      };

      this.runningScripts.set(name, runningScript);

      logger.debug('Environment script started', {
        name: name,
        scriptId: scriptId,
        executorId,
      });

      return executorId;
    } catch (error: any) {
      logger.error('Failed to start environment script', {
        name,
        error: error.message,
      });
      return null;
    }
  }

  /**
   * Start environment script if not in EDITOR mode
   * This is a wrapper around startEnvironmentScript that checks for EDITOR mode
   *
   * @param name Action name
   * @returns Executor ID or null if in EDITOR mode or if script execution failed
   */
  private async startEnvironmentScriptIfNotEditor(name: string): Promise<string | null> {
    // @ts-ignore - __EDITOR__ is defined by Vite
    if (typeof __EDITOR__ !== 'undefined' && __EDITOR__) {
      logger.info('Skipping environment script in EDITOR mode', { name });
      return null;
    }

    return this.startEnvironmentScript(name);
  }

  /**
   * Stop environment script by action name
   *
   * @param name Action name
   */
  async stopEnvironmentScript(name: string): Promise<boolean> {
    const runningScript = this.runningScripts.get(name);
    if (!runningScript) {
      logger.debug('No running script with name', { name });
      return false;
    }

    const scrawlService = this.appContext.services.scrawl;
    if (!scrawlService) {
      logger.error('ScrawlService not available');
      return false;
    }

    const cancelled = scrawlService.cancelExecutor(runningScript.executorId);
    if (cancelled) {
      this.runningScripts.delete(name);

      logger.debug('Environment script stopped', {
        name: runningScript.name,
        executorId: runningScript.executorId,
      });

      return true;
    } else {
      logger.error('Failed to stop environment script', {
        name,
        executorId: runningScript.executorId,
      });
      return false;
    }
  }

  /**
   * Check if an environment script is currently running
   *
   * @param name Action name
   * @returns true if script is running, false otherwise
   */
  isEnvironmentScriptRunning(name: string): boolean {
    return this.runningScripts.has(name);
  }

  /**
   * Get all running scripts
   */
  getRunningEnvironmentScripts(): RunningEnvironmentScript[] {
    return Array.from(this.runningScripts.values());
  }

  // ============================================
  // World Time Management
  // ============================================

  /**
   * Load World Time configuration from WorldInfo
   */
  private loadWorldTimeConfigFromWorldInfo(): WorldTimeConfig {
    const worldTime = this.appContext.worldInfo?.settings?.worldTime;

    return {
      minuteScaling: worldTime?.minuteScaling ?? 10,
      minutesPerHour: worldTime?.minutesPerHour ?? 60,
      hoursPerDay: worldTime?.hoursPerDay ?? 24,
      daysPerMonth: worldTime?.daysPerMonth ?? 30,
      monthsPerYear: worldTime?.monthsPerYear ?? 12,
    };
  }

  /**
   * Load Day Section configuration from WorldInfo
   */
  private loadDaySectionConfigFromWorldInfo(): DaySectionConfig {
    const daySections = this.appContext.worldInfo?.settings?.worldTime?.daySections;

    return {
      morningStart: daySections?.morningStart ?? 6,
      dayStart: daySections?.dayStart ?? 12,
      eveningStart: daySections?.eveningStart ?? 18,
      nightStart: daySections?.nightStart ?? 0,
    };
  }

  /**
   * Load Celestial Bodies configuration from WorldInfo
   */
  private loadCelestialBodiesConfigFromWorldInfo(): CelestialBodiesConfig {
    const celestialBodies = this.appContext.worldInfo?.settings?.worldTime?.celestialBodies;

    return {
      enabled: celestialBodies?.enabled ?? false,
      updateIntervalSeconds: celestialBodies?.updateIntervalSeconds ?? 10,
      activeMoons: celestialBodies?.activeMoons ?? 0,
      sunRotationHours: celestialBodies?.sunRotationHours ?? 24,
      moon0RotationHours: celestialBodies?.moon0RotationHours ?? 672, // 28 days
      moon1RotationHours: celestialBodies?.moon1RotationHours ?? 504, // 21 days
      moon2RotationHours: celestialBodies?.moon2RotationHours ?? 336, // 14 days
    };
  }

  /**
   * Set World Time configuration
   * Command: worldTimeConfig <minuteScaling> <minutesPerHour> <hoursPerDay> <daysPerMonth> <monthsPerYear>
   */
  setWorldTimeConfig(
    minuteScaling: number,
    minutesPerHour: number,
    hoursPerDay: number,
    daysPerMonth: number,
    monthsPerYear: number
  ): void {
    this.worldTimeConfig = {
      minuteScaling: Math.max(0.1, minuteScaling),
      minutesPerHour: Math.max(1, minutesPerHour),
      hoursPerDay: Math.max(1, hoursPerDay),
      daysPerMonth: Math.max(1, daysPerMonth),
      monthsPerYear: Math.max(1, monthsPerYear),
    };

    logger.debug('World Time config updated', this.worldTimeConfig);
  }

  /**
   * Get World Time configuration
   */
  getWorldTimeConfig(): WorldTimeConfig {
    return { ...this.worldTimeConfig };
  }

  startEnvironment(): void {
      if (!this.isWorldTimeRunning()) {
          this.appContext.services.environment?.setUnixEpochWorldTimeOffset(
              this.appContext.worldInfo?.settings?.worldTime?.currentEra || 1,
              this.appContext.worldInfo?.settings?.worldTime?.linuxEpocheDeltaMinutes || 0);
      }
      if (!this.isWorldTimeRunning()) {
          return;
      }

      // Enable celestial bodies if configured in WorldInfo
      if (this.appContext.worldInfo?.settings?.worldTime?.celestialBodies?.enabled) {
          this.celestialBodiesConfig.enabled = true;
      }

      // Calculate current season and season progress from worldInfo.seasonMonths
      this.calculateAndUpdateSeason(
          this.getWorldTimeCurrent()
      );

      // Initialize celestial bodies positions immediately (without animation)
      if (this.celestialBodiesConfig.enabled) {
          this.initializeCelestialPositions();
      }

      logger.info('Environment initialized ', {
          time: this.getWorldTimeCurrentAsString() || 'unknown',
          season: this.getCurrentSeasonAsString() || 'unknown',
          celestialBodiesEnabled: this.celestialBodiesConfig.enabled
      });

  }

    /**
     * Start World Time based on Unix Epoch offset
     * Command: worldTimeUnixEpochOffset <era> <offsetMinutes>
     * @param era
     * @param offsetMinutes if 0 stops world time
     */
  setUnixEpochWorldTimeOffset(era : number, offsetMinutes: number): void {
      if (offsetMinutes === 0) {
            logger.debug('Unix Epoch World Time offset is zero, no action taken', { era, offsetMinutes });
            this.stopWorldTime();
            return;
      }
    // calculate delta to now and call startWorldTime with that value
    // offsetMinutes is now only for the current era (no need to add era offset)
    const now = Date.now();
    const realElapsedMinutes = now / (1000 * 60);
    const worldElapsedMinutes = realElapsedMinutes * this.worldTimeConfig.minuteScaling;
    const currentWorldMinute = offsetMinutes + worldElapsedMinutes;

    this.startWorldTime(currentWorldMinute);

    logger.debug('Unix Epoch World Time offset set', { era, offsetMinutes });

  }

  /**
   * Calculate and update current season and season progress
   *
   * @param worldMinute Current world time in minutes
   */
  private calculateAndUpdateSeason(worldMinute: number): void {
    if (!this.appContext.worldInfo) {
      logger.warn('WorldInfo not available, cannot calculate season');
      return;
    }

    const config = this.worldTimeConfig;

    // Calculate current month (0-based)
    let remainingMinutes = Math.floor(worldMinute);
    remainingMinutes = Math.floor(remainingMinutes / config.minutesPerHour);
    remainingMinutes = Math.floor(remainingMinutes / config.hoursPerDay);
    remainingMinutes = Math.floor(remainingMinutes / config.daysPerMonth);
    const currentMonth = remainingMinutes % config.monthsPerYear; // 0-based month

    // Calculate day of month for progress calculation
    let dayMinutes = Math.floor(worldMinute);
    dayMinutes = Math.floor(dayMinutes / config.minutesPerHour);
    dayMinutes = Math.floor(dayMinutes / config.hoursPerDay);
    const currentDayOfMonth = (dayMinutes % config.daysPerMonth); // 0-based day

    // Get seasonMonths from worldInfo or use default [0,3,6,9]
    let seasonMonths = this.appContext.worldInfo.seasonMonths;
    if (!seasonMonths || seasonMonths.length !== 4) {
      logger.debug('Invalid or missing seasonMonths, using default [0,3,6,9]');
      seasonMonths = [0, 3, 6, 9]; // Winter, Spring, Summer, Autumn
    }

    // Determine current season
    // seasonMonths = [winterStart, springStart, summerStart, autumnStart]
    let seasonStatus = SeasonStatus.WINTER; // Default
    let seasonStartMonth = seasonMonths[0];
    let seasonEndMonth = seasonMonths[1];

    if (currentMonth >= seasonMonths[3]) {
      // Autumn (wraps to next year)
      seasonStatus = SeasonStatus.AUTUMN;
      seasonStartMonth = seasonMonths[3];
      seasonEndMonth = (seasonMonths[0] + config.monthsPerYear) % config.monthsPerYear;
    } else if (currentMonth >= seasonMonths[2]) {
      // Summer
      seasonStatus = SeasonStatus.SUMMER;
      seasonStartMonth = seasonMonths[2];
      seasonEndMonth = seasonMonths[3];
    } else if (currentMonth >= seasonMonths[1]) {
      // Spring
      seasonStatus = SeasonStatus.SPRING;
      seasonStartMonth = seasonMonths[1];
      seasonEndMonth = seasonMonths[2];
    } else {
      // Winter
      seasonStatus = SeasonStatus.WINTER;
      seasonStartMonth = seasonMonths[0];
      seasonEndMonth = seasonMonths[1];
    }

    // Calculate season progress (0.0 to 1.0)
    const monthsInSeason = seasonStatus === SeasonStatus.AUTUMN
      ? (seasonEndMonth + config.monthsPerYear - seasonStartMonth)
      : (seasonEndMonth - seasonStartMonth);
    const monthsIntoCurrent = currentMonth - seasonStartMonth;
    const daysPerMonthFloat = config.daysPerMonth;

    // Progress = (months into season + day progress) / total months in season
    const seasonProgress = Math.min(1.0, Math.max(0.0,
      (monthsIntoCurrent + (currentDayOfMonth / daysPerMonthFloat)) / monthsInSeason
    ));

    // Update worldInfo
    this.appContext.worldInfo.seasonStatus = seasonStatus;
    this.appContext.worldInfo.seasonProgress = seasonProgress;

    logger.debug('Season calculated', {
      currentMonth,
      currentDayOfMonth,
      seasonStatus,
      seasonProgress: seasonProgress.toFixed(3),
      seasonMonths,
    });
  }

  /**
   * Start World Time
   * Command: worldTimeStart <worldMinute>
   *
   * @param worldMinute Start time in @Minutes since @0.1.1.0000 00:00:00
   */
  startWorldTime(worldMinute: number): void {
    if (this.worldTimeState.running) {
      logger.warn('World Time is already running, stopping first');
      this.stopWorldTime();
    }

    this.worldTimeState = {
      running: true,
      startWorldMinute: worldMinute,
      startRealTime: Date.now(),
    };

    // Set initial day section
    this.currentDaySection = this.getWorldDayTimeSection();

    // Start script for initial day section (unless in EDITOR mode)
    const dayScriptName = `daytime_change_${this.currentDaySection}`;
    this.startEnvironmentScriptIfNotEditor(dayScriptName);

    // Start script for current season (unless in EDITOR mode)
    const seasonStatus = this.appContext.worldInfo?.seasonStatus;
    if (seasonStatus !== undefined && seasonStatus !== 0) {
      // Map season status to script name
      const seasonNames = ['', 'winter', 'spring', 'summer', 'autumn'];
      const seasonName = seasonNames[seasonStatus];
      if (seasonName) {
        const seasonScriptName = `season_${seasonName}`;
        this.startEnvironmentScriptIfNotEditor(seasonScriptName);
        this.currentSeason = seasonStatus; // Track current season
        logger.debug('Started season script', { seasonStatus, seasonScriptName });
      }
    }

    logger.debug('World Time started', {
      startWorldMinute: worldMinute,
      startWorldTime: this.getWorldTimeCurrentAsString(),
      daySection: this.currentDaySection,
      initialDayScript: dayScriptName,
      seasonStatus,
    });
  }

  /**
   * Stop World Time
   * Command: worldTimeStop
   */
  stopWorldTime(): void {
    if (!this.worldTimeState.running) {
      logger.warn('World Time is not running');
      return;
    }

    const currentWorldTime = this.getWorldTimeCurrent();

    this.worldTimeState = {
      running: false,
      startWorldMinute: 0,
      startRealTime: 0,
    };

    this.currentDaySection = null;
    this.currentSeason = SeasonStatus.NONE;

    logger.debug('World Time stopped', {
      stoppedAt: currentWorldTime,
      stoppedAtFormatted: this.formatWorldTime(currentWorldTime),
    });
  }

  /**
   * Check if World Time is running
   */
  isWorldTimeRunning(): boolean {
    return this.worldTimeState.running;
  }

  /**
   * Get current World Time in @Minutes
   */
  getWorldTimeCurrent(): number {
    if (!this.worldTimeState.running) {
      return 0;
    }

    const realElapsedMs = Date.now() - this.worldTimeState.startRealTime;
    const realElapsedMinutes = realElapsedMs / (1000 * 60);
    const worldElapsedMinutes = realElapsedMinutes * this.worldTimeConfig.minuteScaling;

    return this.worldTimeState.startWorldMinute + worldElapsedMinutes;
  }

  /**
   * Get current World Time as formatted string
   * Format: @era, @year.@month.@day, @hour:@minute
   */
  getWorldTimeCurrentAsString(): string {
    const worldMinute = this.getWorldTimeCurrent();
    return this.formatWorldTime(worldMinute);
  }

  getCurrentSeasonAsString() : string {
      const progress = ((this.appContext.worldInfo?.seasonProgress || 0) * 100).toFixed(1);
      switch (this.appContext.worldInfo?.seasonStatus) {
          case 1:
              return `Winter ${progress}%`;
          case 2:
              return `Spring ${progress}%`;
          case 3:
              return `Summer ${progress}%`;
          case 4:
              return `Autumn ${progress}%`;
          case 0:
          default:
              return "?";
      }
  }

  /**
   * Format world time in minutes to string
   * Format: @era, @year.@month.@day, @hour:@minute
   *
   * @param worldMinute World time in @Minutes (since start of current era)
   */
  private formatWorldTime(worldMinute: number): string {
    const config = this.worldTimeConfig;

    // Calculate time components
    let remainingMinutes = Math.floor(worldMinute);

    const minute = remainingMinutes % config.minutesPerHour;
    remainingMinutes = Math.floor(remainingMinutes / config.minutesPerHour);

    const hour = remainingMinutes % config.hoursPerDay;
    remainingMinutes = Math.floor(remainingMinutes / config.hoursPerDay);

    const day = (remainingMinutes % config.daysPerMonth) + 1; // Days start at 1
    remainingMinutes = Math.floor(remainingMinutes / config.daysPerMonth);

    const month = (remainingMinutes % config.monthsPerYear) + 1; // Months start at 1
    remainingMinutes = Math.floor(remainingMinutes / config.monthsPerYear);

    // Year is now calculated since start of current era
    const year = remainingMinutes + 1; // Years start at 1

    // Era comes from WorldInfo (current era)
    const era = this.appContext.worldInfo?.settings?.worldTime?.currentEra ?? 1;

    return `@${era}, @${year}.${month}.${day}, ${hour}:${minute.toString().padStart(2, '0')}`;
  }

  /**
   * Get current day time section
   */
  getWorldDayTimeSection(): DaySection {
    const worldMinute = this.getWorldTimeCurrent();
    const config = this.worldTimeConfig;

    // Calculate current hour of the day
    const totalMinutesInDay = config.minutesPerHour * config.hoursPerDay;
    const minuteOfDay = worldMinute % totalMinutesInDay;
    const hourOfDay = Math.floor(minuteOfDay / config.minutesPerHour);

    // Determine day section
    const sections = this.daySectionConfig;

    if (hourOfDay >= sections.morningStart && hourOfDay < sections.dayStart) {
      return 'morning';
    } else if (hourOfDay >= sections.dayStart && hourOfDay < sections.eveningStart) {
      return 'day';
    } else if (hourOfDay >= sections.eveningStart && hourOfDay < config.hoursPerDay) {
      return 'evening';
    } else {
      return 'night';
    }
  }

  /**
   * Update World Time (called each frame)
   * Checks for day section changes and triggers scripts
   *
   * @param deltaTime Time since last frame in seconds
   */
  private updateWorldTime(deltaTime: number): void {
    if (!this.worldTimeState.running) {
      return;
    }

    const newDaySection = this.getWorldDayTimeSection();

    if (this.currentDaySection !== newDaySection) {
      logger.debug('Day section changed', {
        from: this.currentDaySection,
        to: newDaySection,
        worldTime: this.getWorldTimeCurrentAsString(),
      });

      this.currentDaySection = newDaySection;

      // Start corresponding environment script
      const scriptName = `daytime_change_${newDaySection}`;
      this.startEnvironmentScriptIfNotEditor(scriptName);

      // Also check for season change when day section changes
      const currentWorldMinute = this.getWorldTimeCurrent();
      this.calculateAndUpdateSeason(currentWorldMinute);

      // Check if season changed and trigger season script
      const newSeason = this.appContext.worldInfo?.seasonStatus || SeasonStatus.NONE;
      if (this.currentSeason !== newSeason && newSeason !== SeasonStatus.NONE) {
        const seasonNames = ['', 'winter', 'spring', 'summer', 'autumn'];
        const seasonName = seasonNames[newSeason];
        if (seasonName) {
          const seasonScriptName = `season_${seasonName}`;
          this.startEnvironmentScriptIfNotEditor(seasonScriptName);
          logger.debug('Season changed, started season script', {
            from: this.currentSeason,
            to: newSeason,
            seasonScriptName
          });
        }
        this.currentSeason = newSeason;
      }
    }

    // Update celestial bodies positions if enabled
    this.updateCelestialBodies(deltaTime);
  }

  /**
   * Initialize celestial bodies positions immediately (without animation)
   * Called once during startEnvironment() to set initial positions
   */
  private initializeCelestialPositions(): void {
    const modifierService = this.appContext.services.modifier;
    if (!modifierService) {
      return;
    }

    // Get current world time
    const currentWorldMinute = this.getWorldTimeCurrent();
    const currentWorldHour = currentWorldMinute / this.worldTimeConfig.minutesPerHour;

    // Initialize sun position
    const sunRotationHours = this.celestialBodiesConfig.sunRotationHours;
    const sunAngle = (currentWorldHour / sunRotationHours) * 360;
    const sunAngleNormalized = sunAngle % 360;

    const sunPositionStack = modifierService.getModifierStack('sunPosition');
    if (sunPositionStack) {
      // Set currentValue directly without animation
      sunPositionStack.setCurrentValue(sunAngleNormalized);
      logger.debug('Sun position initialized', { angle: sunAngleNormalized });
    }

    // Initialize moon positions
    for (let i = 0; i < this.celestialBodiesConfig.activeMoons && i < 3; i++) {
      let moonRotationHours: number;
      let stackName: string;

      switch (i) {
        case 0:
          moonRotationHours = this.celestialBodiesConfig.moon0RotationHours;
          stackName = 'moon0Position';
          break;
        case 1:
          moonRotationHours = this.celestialBodiesConfig.moon1RotationHours;
          stackName = 'moon1Position';
          break;
        case 2:
          moonRotationHours = this.celestialBodiesConfig.moon2RotationHours;
          stackName = 'moon2Position';
          break;
        default:
          continue;
      }

      const moonAngle = (currentWorldHour / moonRotationHours) * 360;
      const moonAngleNormalized = moonAngle % 360;

      const moonPositionStack = modifierService.getModifierStack(stackName);
      if (moonPositionStack) {
        // Set currentValue directly without animation
        moonPositionStack.setCurrentValue(moonAngleNormalized);
        logger.debug(`Moon ${i} position initialized`, { angle: moonAngleNormalized });
      }
    }
  }

  /**
   * Update celestial bodies positions (sun and moons)
   * Called every frame but only updates at configured intervals
   *
   * @param deltaTime Time since last frame in seconds
   */
  private updateCelestialBodies(deltaTime: number): void {
    if (!this.celestialBodiesConfig.enabled || !this.isWorldTimeRunning()) {
      return;
    }

    // Accumulate time
    this.celestialUpdateTimer += deltaTime;

    // Check if update interval has passed
    if (this.celestialUpdateTimer < this.celestialBodiesConfig.updateIntervalSeconds) {
      return;
    }

    // Reset timer
    this.celestialUpdateTimer = 0;

    // Get modifier service
    const modifierService = this.appContext.services.modifier;
    if (!modifierService) {
      return;
    }

    // Get current world time
    const currentWorldMinute = this.getWorldTimeCurrent();
    const currentWorldHour = currentWorldMinute / this.worldTimeConfig.minutesPerHour;

    // Update sun position
    this.updateSunPosition(modifierService, currentWorldHour);

    // Update moon positions
    for (let i = 0; i < this.celestialBodiesConfig.activeMoons && i < 3; i++) {
      this.updateMoonPosition(modifierService, i, currentWorldHour);
    }

    // Removed debug log for performance
      logger.debug('Celestial bodies updated', {
          worldTime: this.getWorldTimeCurrentAsString(),
      });
  }

  /**
   * Update sun position based on world time
   */
  private updateSunPosition(modifierService: any, currentWorldHour: number): void {
    const sunRotationHours = this.celestialBodiesConfig.sunRotationHours;

    // Calculate sun position (0-360 degrees)
    // At hour 0, sun is at 0° (North)
    // Sun rotates 360° over sunRotationHours
    const sunAngle = (currentWorldHour / sunRotationHours) * 360;
    const sunAngleNormalized = sunAngle % 360;

    // Get sun position stack
    const sunPositionStack = modifierService.getModifierStack('sunPosition');
    if (sunPositionStack) {
      // Create modifier once and reuse it (priority 40, lower than manual scripts at 50+)
      if (!this.sunPositionModifier) {
        this.sunPositionModifier = sunPositionStack.addModifier(sunAngleNormalized, 40);
      } else {
        this.sunPositionModifier.setValue(sunAngleNormalized);
      }
      this.sunPositionModifier.setEnabled(true);
    }
  }

  /**
   * Update moon position based on world time
   */
  private updateMoonPosition(
    modifierService: any,
    moonIndex: number,
    currentWorldHour: number
  ): void {
    let moonRotationHours: number;
    let stackName: string;

    // Select rotation hours and stack name based on moon index
    switch (moonIndex) {
      case 0:
        moonRotationHours = this.celestialBodiesConfig.moon0RotationHours;
        stackName = 'moon0Position';
        break;
      case 1:
        moonRotationHours = this.celestialBodiesConfig.moon1RotationHours;
        stackName = 'moon1Position';
        break;
      case 2:
        moonRotationHours = this.celestialBodiesConfig.moon2RotationHours;
        stackName = 'moon2Position';
        break;
      default:
        return;
    }

    // Calculate moon position (0-360 degrees)
    // Moon rotates 360° over moonRotationHours
    const moonAngle = (currentWorldHour / moonRotationHours) * 360;
    const moonAngleNormalized = moonAngle % 360;

    // Get moon position stack
    const moonPositionStack = modifierService.getModifierStack(stackName);
    if (moonPositionStack) {
      // Create modifier once per moon and reuse it (priority 40, lower than manual scripts)
      let moonModifier = this.moonPositionModifiers.get(moonIndex);
      if (!moonModifier) {
        moonModifier = moonPositionStack.addModifier(moonAngleNormalized, 40);
        this.moonPositionModifiers.set(moonIndex, moonModifier);
      } else {
        moonModifier.setValue(moonAngleNormalized);
      }
      moonModifier.setEnabled(true);
    }
  }

  /**
   * Reset environment to default state
   * Clears clouds, stops precipitation, and disables skybox and horizon gradient
   */
  resetEnvironment(): void {
    const cloudService = this.appContext.services.clouds;
    if (cloudService) {
      cloudService.clearClouds(false);
      logger.debug('Environment reset: clouds cleared');
    } else {
      logger.warn('CloudService not available for environment reset');
    }

    const precipitationService = this.appContext.services.precipitation;
    if (precipitationService) {
      precipitationService.setEnabled(false);
      logger.debug('Environment reset: precipitation stopped');
    } else {
      logger.warn('PrecipitationService not available for environment reset');
    }

    const skyBoxService = this.appContext.services.skyBox;
    if (skyBoxService) {
      skyBoxService.setEnabled(false);
      logger.debug('Environment reset: skybox disabled');
    } else {
      logger.warn('SkyBoxService not available for environment reset');
    }

    const horizonGradientService = this.appContext.services.horizonGradient;
    if (horizonGradientService) {
      horizonGradientService.setEnabled(false);
      logger.debug('Environment reset: horizon gradient disabled');
    } else {
      logger.warn('HorizonGradientService not available for environment reset');
    }

    logger.debug('Environment reset completed');
  }

  /**
   * Dispose environment
   */
  dispose(): void {
    // Stop world time
    if (this.worldTimeState.running) {
      this.stopWorldTime();
    }

    // Stop all running scripts
    for (const name of this.runningScripts.keys()) {
      this.stopEnvironmentScript(name);
    }

    // Dispose shadow generator
    if (this.shadowGenerator) {
      this.shadowGenerator.dispose();
      this.shadowGenerator = null;
      logger.debug('Shadow generator disposed');
    }

    this.ambientLight?.dispose();
    this.sunLight?.dispose();
    logger.debug('Environment disposed');
  }
}
