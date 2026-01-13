/**
 * ShaderService - Manages shader effects for blocks
 *
 * Provides shader effects based on BlockModifier.visibility.effect parameter.
 *
 * Implemented effects:
 * - wind: Wind animation shader with physical displacement
 *
 * Future effects:
 * - water: Water wave shader
 * - lava: Lava wave shader
 * - fog: Fog effect shader
 * - flipbox: Rotating box shader
 */

import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import {
  Material,
  ShaderMaterial,
  Scene,
  Effect,
  Texture,
  Vector2,
  Vector3,
  Color3,
} from '@babylonjs/core';
import type { EnvironmentService, WindParameters } from './EnvironmentService';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('ShaderService');

/**
 * Shader effect definition
 */
export interface ShaderEffect {
  /** Effect name */
  name: string;

  /** Create material for this effect */
  createMaterial: (params?: Record<string, any>) => Material | null;
}

/**
 * ShaderService - Manages shader effects
 *
 * Creates and manages shader materials for special block effects.
 * Integrates with EnvironmentService for wind parameters.
 */
export class ShaderService {
  private effects: Map<string, ShaderEffect> = new Map();
  private scene?: Scene;
  private environmentService?: EnvironmentService;

  // Wind shader materials for automatic parameter updates
  private windMaterials: ShaderMaterial[] = [];

  constructor(private appContext: AppContext) {
    logger.debug('ShaderService initialized');
  }

  /**
   * Initialize shader service with scene
   * Must be called after scene is created
   */
  initialize(scene: Scene): void {
    this.scene = scene;

    // Register passthrough shader (for testing)
    this.registerPassthroughShader();

    // Register wind shader
    this.registerWindShader();

    // Register fog shader
    this.registerFogShader();

    // Register thin instance wind shader
    this.registerThinInstanceWindShader();

    // Register flipbox shader
    this.registerFlipboxShader();

    logger.debug('ShaderService initialized with scene');
  }

  /**
   * Set EnvironmentService for automatic wind parameter updates
   */
  setEnvironmentService(environmentService: EnvironmentService): void {
    this.environmentService = environmentService;
    logger.debug('EnvironmentService connected for automatic wind updates');

    // Update existing wind materials with current parameters
    if (this.windMaterials.length > 0) {
      const params = environmentService.getWindParameters();
      logger.debug('Initial wind parameters', params);
      this.updateWindMaterials(params);
    }

    // Setup automatic updates every frame
    if (this.scene) {
      this.scene.onBeforeRenderObservable.add(() => {
        if (this.environmentService && this.windMaterials.length > 0) {
          const params = this.environmentService.getWindParameters();
          this.updateWindMaterials(params);
        }
      });
    }
  }

  /**
   * Register a shader effect
   *
   * @param effect Shader effect to register
   */
  registerEffect(effect: ShaderEffect): void {
    this.effects.set(effect.name, effect);
    logger.debug('Registered shader effect', { name: effect.name });
  }

  /**
   * Get a shader effect by name
   *
   * @param name Effect name (from BlockModifier.visibility.effect)
   * @returns Shader effect or undefined if not found
   */
  getEffect(name: string): ShaderEffect | undefined {
    return this.effects.get(name);
  }

  /**
   * Check if an effect is registered
   *
   * @param name Effect name
   * @returns True if effect is registered
   */
  hasEffect(name: string): boolean {
    return this.effects.has(name);
  }

  /**
   * Create a material for an effect
   *
   * @param effectName Effect name (from BlockModifier.visibility.effect)
   * @param params Effect-specific parameters (from BlockModifier.visibility.effectParameters)
   * @returns Material or null if effect not found
   */
  createMaterial(effectName: string, params?: Record<string, any>): Material | null {
    const effect = this.effects.get(effectName);
    if (!effect) {
      logger.debug('Shader effect not found', { effectName });
      return null;
    }

    try {
      return effect.createMaterial(params);
    } catch (error) {
      logger.error('Failed to create material for effect', { effectName }, error as Error);
      return null;
    }
  }

  /**
   * Get all registered effect names
   *
   * @returns Array of effect names
   */
  getEffectNames(): string[] {
    return Array.from(this.effects.keys());
  }

  /**
   * Clear all registered effects
   *
   * Useful for testing or when switching worlds
   */
  clear(): void {
    this.effects.clear();
    this.windMaterials = [];
    logger.debug('Shader effects cleared');
  }

  // ============================================
  // Passthrough Shader Implementation (for testing)
  // ============================================

  /**
   * Register passthrough shader - simple shader that just displays texture
   */
  private registerPassthroughShader(): void {
    // Vertex shader - minimal, no transformations
    Effect.ShadersStore['passthroughVertexShader'] = `
      precision highp float;

      // Attributes
      attribute vec3 position;
      attribute vec3 normal;
      attribute vec2 uv;
      attribute vec4 color;

      // Uniforms
      uniform mat4 worldViewProjection;
      uniform mat4 world;

      // Varyings to fragment shader
      varying vec2 vUV;
      varying vec4 vColor;
      varying vec3 vNormal;

      void main(void) {
        gl_Position = worldViewProjection * vec4(position, 1.0);
        vUV = uv;
        vColor = color;
        vNormal = normalize((world * vec4(normal, 0.0)).xyz);
      }
    `;

    // Fragment shader - minimal, just texture (NO lighting, NO vertex color)
    Effect.ShadersStore['passthroughFragmentShader'] = `
      precision highp float;

      varying vec2 vUV;
      varying vec4 vColor;
      varying vec3 vNormal;

      uniform sampler2D textureSampler;

      void main(void) {
        vec4 texColor = texture2D(textureSampler, vUV);

        if (texColor.a < 0.5) {
          discard;
        }

        // Output texture directly without any modifications
        gl_FragColor = vec4(texColor.rgb, 1.0);
      }
    `;

    // Register passthrough effect
    const passthroughEffect: ShaderEffect = {
      name: 'passthrough',
      createMaterial: (params?: Record<string, any>) => {
        return this.createPassthroughMaterial(params?.texture, params?.name);
      },
    };

    this.registerEffect(passthroughEffect);
    logger.debug('Passthrough shader registered');
  }

  /**
   * Create passthrough shader material
   */
  private createPassthroughMaterial(texture: Texture | undefined, name: string = 'passthroughMaterial'): ShaderMaterial | null {
    if (!this.scene) {
      logger.error('Cannot create passthrough material: Scene not initialized');
      return null;
    }

    const material = new ShaderMaterial(
      name,
      this.scene,
      {
        vertex: 'passthrough',
        fragment: 'passthrough',
      },
      {
        attributes: ['position', 'normal', 'uv', 'color'],
        uniforms: ['worldViewProjection', 'world', 'textureSampler', 'lightDirection'],
        samplers: ['textureSampler'],
      }
    );

    material.onError = (effect, errors) => {
      logger.error('Passthrough shader compilation error', { name, errors });
    };

    material.onCompiled = () => {
      logger.debug('Passthrough shader compiled successfully', { name });
    };

    if (texture) {
      material.setTexture('textureSampler', texture);
      logger.debug('Passthrough material texture set', { name });
    } else {
      logger.warn('Passthrough material created without texture', { name });
    }

    material.setVector3('lightDirection', new Vector3(0.5, 1.0, 0.5));
    material.backFaceCulling = false;

    return material;
  }

  // ============================================
  // Wind Shader Implementation
  // ============================================

  /**
   * Register wind shader effect
   */
  private registerWindShader(): void {
    // Register wind shaders with Babylon.js
    this.registerWindShaderCode();

    // Register wind effect
    const windEffect: ShaderEffect = {
      name: 'wind',
      createMaterial: (params?: Record<string, any>) => {
        return this.createWindMaterial(params?.texture, params?.name);
      },
    };

    this.registerEffect(windEffect);
  }

  /**
   * Register custom wind shader code with Babylon.js
   */
  private registerWindShaderCode(): void {
    // Vertex shader with lighting and physical wind animation
    Effect.ShadersStore['windVertexShader'] = `
      precision highp float;

      // Attributes
      attribute vec3 position;
      attribute vec3 normal;
      attribute vec2 uv;
      attribute vec4 color;

      // Wind properties (per-vertex)
      attribute float windLeafiness;
      attribute float windStability;
      attribute float windLeverUp;
      attribute float windLeverDown;

      // Uniforms
      uniform mat4 worldViewProjection;
      uniform mat4 world;
      uniform float time;
      uniform vec2 windDirection;
      uniform float windStrength;
      uniform float windGustStrength;
      uniform float windSwayFactor;

      // Varyings to fragment shader
      varying vec2 vUV;
      varying vec4 vColor;
      varying vec3 vNormal;

      void main(void) {
        vec3 pos = position;

        // Get world position for phase shift
        vec4 worldPos = world * vec4(position, 1.0);

        // Base sway wave (smooth sinusoidal)
        float baseWave = sin(time * windSwayFactor + worldPos.x * 0.01 + worldPos.z * 0.01) * windStrength;

        // Gust effect (faster, irregular pulses)
        float gustWave = sin(time * windSwayFactor * 2.3 + worldPos.x * 0.01) * windGustStrength;
        gustWave *= sin(time * windSwayFactor * 0.7); // Modulate gust intensity

        // Secondary wave for more organic movement (leafiness effect)
        float leafiness = max(windLeafiness, 0.5);
        float leafWave = sin(time * windSwayFactor * 1.7 + worldPos.y * 0.01) * leafiness;

        // Combine all waves
        float totalWave = baseWave + gustWave * 0.5 + leafWave * 0.3;

        // Stability reduces movement (1.0 = stable/no movement, 0.0 = unstable/full movement)
        float stabilityFactor = 1.0 - windStability;

        // Shear direction (normalized wind direction in XZ plane)
        vec3 shearDir = vec3(windDirection.x, 0.0, windDirection.y);
        if (length(shearDir) > 0.01) {
          shearDir = normalize(shearDir);
        }

        // Pivot Y: base of the lever
        float pivotY = -windLeverDown;

        // Block height
        float blockHeight = 1.0;

        // Normalized height within block (0.0 at bottom, 1.0 at top)
        float h = clamp((position.y - pivotY) / (pivotY + blockHeight + windLeverUp), 0.0, 1.0);

        // Total height from pivot to top of this vertex
        float heightFromPivot = position.y - pivotY;

        // Calculate lever length for this vertex
        float leverAtThisHeight = mix(windLeverDown, windLeverUp, h);

        // Horizontal displacement at this height
        float horizontalDisp = totalWave * leverAtThisHeight * stabilityFactor * 0.05;

        // Apply horizontal shearing
        pos += shearDir * horizontalDisp * h;

        // Vertical compression (physics: when bent horizontally, becomes shorter)
        float horizontalDispTotal = abs(horizontalDisp * h);
        float compressionFactor = (horizontalDispTotal * horizontalDispTotal) / (2.0 * max(heightFromPivot, 0.1));
        pos.y -= compressionFactor;

        // Vertical leafiness movement (additional up/down movement for leaves)
        float verticalLeafWave = sin(time * windSwayFactor * 2.1 + worldPos.x * 0.015 + worldPos.z * 0.015) * leafiness;
        verticalLeafWave += cos(time * windSwayFactor * 1.3 + worldPos.z * 0.02) * leafiness * 0.5;
        pos.y += verticalLeafWave * 0.02 * h;

        // Transform to clip space
        gl_Position = worldViewProjection * vec4(pos, 1.0);

        // Pass to fragment shader
        vUV = uv;
        vColor = color;
        vNormal = normalize((world * vec4(normal, 0.0)).xyz);
      }
    `;

    // Fragment shader with proper lighting and alpha handling
    Effect.ShadersStore['windFragmentShader'] = `
      precision highp float;

      // Varyings from vertex shader
      varying vec2 vUV;
      varying vec4 vColor;
      varying vec3 vNormal;

      // Uniforms
      uniform sampler2D textureSampler;

      void main(void) {
        // Sample texture from atlas
        vec4 texColor = texture2D(textureSampler, vUV);

        // Alpha test: discard transparent pixels (alpha < 0.5)
        if (texColor.a < 0.5) {
          discard;
        }

        // Output texture directly without any modifications
        gl_FragColor = vec4(texColor.rgb, 1.0);
      }
    `;

    logger.debug('Wind shader code registered with Babylon.js');
  }

  /**
   * Create wind shader material
   */
  private createWindMaterial(texture: Texture | undefined, name: string = 'windMaterial'): ShaderMaterial | null {
    if (!this.scene) {
      logger.error('Cannot create wind material: Scene not initialized');
      return null;
    }

    logger.debug('Creating wind material', { name });

    const material = new ShaderMaterial(
      name,
      this.scene,
      {
        vertex: 'wind',
        fragment: 'wind',
      },
      {
        attributes: [
          'position',
          'normal',
          'uv',
          'color',
          'windLeafiness',
          'windStability',
          'windLeverUp',
          'windLeverDown',
        ],
        uniforms: [
          'worldViewProjection',
          'world',
          'time',
          'windDirection',
          'windStrength',
          'windGustStrength',
          'windSwayFactor',
          'textureSampler',
          'lightDirection',
        ],
        samplers: ['textureSampler'],
      }
    );

    // Error handling for shader compilation
    material.onError = (effect, errors) => {
      logger.error('Shader compilation error', { name, errors });
    };

    material.onCompiled = () => {
      logger.debug('Wind shader compiled successfully', { name });
    };

    // Set texture if provided
    if (texture) {
      material.setTexture('textureSampler', texture);
      logger.debug('Wind material texture set', { name, hasTexture: !!texture, textureReady: texture.isReady() });
    } else {
      logger.warn('Wind material created without texture', { name });
    }

    // Set default wind parameters (will be updated from EnvironmentService)
    material.setVector2('windDirection', new Vector2(1.0, 0.0));
    material.setFloat('windStrength', 0.8);
    material.setFloat('windGustStrength', 0.4);
    material.setFloat('windSwayFactor', 2.0);

    // Set light direction (from above-front)
    material.setVector3('lightDirection', new Vector3(0.5, 1.0, 0.5));

    // Configure material properties
    material.backFaceCulling = false; // Transparent blocks visible from both sides

    // Store material for automatic updates
    this.windMaterials.push(material);

    // Update time uniform every frame
    let totalTime = 0;
    const scene = this.scene; // Capture scene reference for closure
    scene.onBeforeRenderObservable.add(() => {
      totalTime += scene.getEngine().getDeltaTime() / 1000.0;
      material.setFloat('time', totalTime);
    });

    // If EnvironmentService is already connected, update this material immediately
    if (this.environmentService) {
      const params = this.environmentService.getWindParameters();
      this.updateSingleWindMaterial(material, params);
    }

    return material;
  }

  /**
   * Update all wind materials with new wind parameters
   */
  private updateWindMaterials(params: WindParameters): void {
    for (const material of this.windMaterials) {
      this.updateSingleWindMaterial(material, params);
    }
  }

  /**
   * Update a single wind material with wind parameters
   */
  private updateSingleWindMaterial(material: ShaderMaterial, params: WindParameters): void {
    material.setVector2('windDirection', new Vector2(params.windDirection.x, params.windDirection.z));
    material.setFloat('windStrength', params.windStrength);
    material.setFloat('windGustStrength', params.windGustStrength);
    material.setFloat('windSwayFactor', params.windSwayFactor);
  }

  // ============================================
  // Fog Shader Implementation
  // ============================================

  /**
   * Register fog shader effect
   */
  private registerFogShader(): void {
    this.registerFogShaderCode();

    const fogEffect: ShaderEffect = {
      name: 'fog',
      createMaterial: (params?: Record<string, any>) => {
        return this.createFogMaterial(params?.texture, params?.effectParameters, params?.name);
      },
    };

    this.registerEffect(fogEffect);
  }

  /**
   * Register fog shader code with Babylon.js
   */
  private registerFogShaderCode(): void {
    // Vertex shader - standard transformation
    Effect.ShadersStore['fogVertexShader'] = `
      precision highp float;

      attribute vec3 position;
      attribute vec3 normal;
      attribute vec2 uv;
      attribute vec4 color;

      uniform mat4 worldViewProjection;
      uniform mat4 world;
      uniform vec3 cameraPosition;

      varying vec2 vUV;
      varying vec3 vWorldPosition;
      varying vec3 vViewDirection;
      varying float vDepth;

      void main(void) {
        vec4 worldPos = world * vec4(position, 1.0);
        gl_Position = worldViewProjection * vec4(position, 1.0);

        vUV = uv;
        vWorldPosition = worldPos.xyz;
        vViewDirection = cameraPosition - worldPos.xyz;
        vDepth = length(vViewDirection);
      }
    `;

    // Fragment shader - volumetric fog effect
    Effect.ShadersStore['fogFragmentShader'] = `
      precision highp float;

      varying vec2 vUV;
      varying vec3 vWorldPosition;
      varying vec3 vViewDirection;
      varying float vDepth;

      uniform sampler2D textureSampler;
      uniform float time;
      uniform float density;
      uniform vec3 fogColor;

      // Simple 3D noise function
      float hash(vec3 p) {
        p = fract(p * 0.3183099 + 0.1);
        p *= 17.0;
        return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
      }

      float noise(vec3 x) {
        vec3 p = floor(x);
        vec3 f = fract(x);
        f = f * f * (3.0 - 2.0 * f);

        return mix(
          mix(mix(hash(p + vec3(0, 0, 0)), hash(p + vec3(1, 0, 0)), f.x),
              mix(hash(p + vec3(0, 1, 0)), hash(p + vec3(1, 1, 0)), f.x), f.y),
          mix(mix(hash(p + vec3(0, 0, 1)), hash(p + vec3(1, 0, 1)), f.x),
              mix(hash(p + vec3(0, 1, 1)), hash(p + vec3(1, 1, 1)), f.x), f.y),
          f.z
        );
      }

      void main(void) {
        // Sample texture
        vec4 texColor = texture2D(textureSampler, vUV);

        // Animated noise for fog movement
        vec3 noiseCoord = vWorldPosition * 2.0 + vec3(time * 0.1, time * 0.05, 0.0);
        float noiseValue = noise(noiseCoord);
        noiseValue += 0.5 * noise(noiseCoord * 2.0);
        noiseValue /= 1.5;

        // Fog density with noise variation
        float fogDensity = density * (0.7 + 0.3 * noiseValue);

        // Depth fade (fog becomes more transparent with distance)
        float depthFade = clamp(vDepth / 10.0, 0.0, 1.0);
        float alpha = fogDensity * (1.0 - depthFade * 0.3);

        // Mix texture color with fog color
        vec3 finalColor = mix(texColor.rgb, fogColor, 0.5);

        // Output with fog density as alpha
        gl_FragColor = vec4(finalColor, alpha);
      }
    `;

    logger.debug('Fog shader code registered');
  }

  /**
   * Create fog shader material
   */
  private createFogMaterial(
    texture: Texture | undefined,
    effectParameters: string | undefined,
    name: string = 'fogMaterial'
  ): ShaderMaterial | null {
    if (!this.scene) {
      logger.error('Cannot create fog material: Scene not initialized');
      return null;
    }

    const material = new ShaderMaterial(
      name,
      this.scene,
      {
        vertex: 'fog',
        fragment: 'fog',
      },
      {
        attributes: ['position', 'normal', 'uv', 'color'],
        uniforms: [
          'worldViewProjection',
          'world',
          'cameraPosition',
          'time',
          'density',
          'fogColor',
        ],
        samplers: ['textureSampler'],
      }
    );

    material.onError = (effect, errors) => {
      logger.error('Fog shader compilation error', { name, errors });
    };

    material.onCompiled = () => {
      logger.debug('Fog shader compiled successfully', { name });
    };

    // Set texture
    if (texture) {
      material.setTexture('textureSampler', texture);
    }

    // Parse density from effectParameters (format: "density")
    let density = 0.3; // Default
    if (effectParameters) {
      const parsed = parseFloat(effectParameters);
      if (!isNaN(parsed)) {
        density = Math.max(0.0, Math.min(1.0, parsed));
      }
    }

    material.setFloat('density', density);
    material.setColor3('fogColor', new Color3(0.8, 0.8, 0.9)); // Light blue-gray fog

    // Update time uniform every frame
    let totalTime = 0;
    const scene = this.scene;
    scene.onBeforeRenderObservable.add(() => {
      totalTime += scene.getEngine().getDeltaTime() / 1000.0;
      material.setFloat('time', totalTime);
    });

    // Set camera position every frame
    scene.onBeforeRenderObservable.add(() => {
      if (scene.activeCamera) {
        material.setVector3('cameraPosition', scene.activeCamera.position);
      }
    });

    // Fog needs alpha blending
    material.alpha = 1.0;
    material.backFaceCulling = false;
    material.needDepthPrePass = true;

    logger.debug('Fog material created', { name, density });

    return material;
  }

  // ============================================
  // Flipbox Shader Implementation
  // ============================================

  /**
   * Register flipbox shader effect
   */
  private registerFlipboxShader(): void {
    // Register flipbox shaders with Babylon.js
    this.registerFlipboxShaderCode();

    // Register flipbox effect
    const flipboxEffect: ShaderEffect = {
      name: 'flipbox',
      createMaterial: (params?: Record<string, any>) => {
        return this.createFlipboxMaterial(params?.texture, params?.shaderParameters, params?.name);
      },
    };

    this.registerEffect(flipboxEffect);
  }

  /**
   * Register custom flipbox shader code with Babylon.js
   */
  private registerFlipboxShaderCode(): void {
    // Vertex shader - standard pass-through with UV
    Effect.ShadersStore['flipboxVertexShader'] = `
      precision highp float;

      // Attributes
      attribute vec3 position;
      attribute vec3 normal;
      attribute vec2 uv;

      // Uniforms
      uniform mat4 worldViewProjection;
      uniform mat4 world;

      // Varyings to fragment shader
      varying vec2 vUV;
      varying vec3 vNormal;

      void main(void) {
        gl_Position = worldViewProjection * vec4(position, 1.0);
        vUV = uv;
        vNormal = normalize((world * vec4(normal, 0.0)).xyz);
      }
    `;

    // Fragment shader - sprite-sheet frame animation
    Effect.ShadersStore['flipboxFragmentShader'] = `
      precision highp float;

      // Varyings from vertex shader
      varying vec2 vUV;
      varying vec3 vNormal;

      // Uniforms
      uniform sampler2D textureSampler;
      uniform vec3 lightDirection;
      uniform int currentFrame;
      uniform int frameCount;
      uniform int flipDirection; // 0 = horizontal, 1 = vertical

      void main(void) {
        // Calculate frame offset in UV space
        float frameWidth = 1.0 / float(frameCount);

        // Half-pixel offset to sample from pixel centers (prevents edge bleeding)
        // Assuming texture height of 512 pixels, 1 pixel = 1/512 = 0.00195
        float pixelSize = 1.0 / 512.0; // Adjust based on typical texture size
        float halfPixel = pixelSize * 0.5;

        // Adjust UV to sample current frame based on direction
        vec2 frameUV;
        if (flipDirection == 0) {
          // Horizontal flip (default)
          float frameOffset = float(currentFrame) * frameWidth;
          float u = vUV.x * (frameWidth - pixelSize) + frameOffset + halfPixel;
          frameUV = vec2(clamp(u, 0.0, 1.0), vUV.y);
        } else {
          // Vertical flip - shift down by 1 pixel to hide top line artifact
          float frameOffset = float(currentFrame) * frameWidth;
          // Map vUV.y (0=top, 1=bottom) to frame range, shifting down by 1 pixel
          float v = vUV.y * (frameWidth - pixelSize * 3.0) + frameOffset + pixelSize;
          frameUV = vec2(vUV.x, clamp(v, 0.0, 1.0));
        }

        // Sample texture
        vec4 texColor = texture2D(textureSampler, frameUV);

        // Alpha test: discard transparent pixels
        if (texColor.a < 0.1) {
          discard;
        }

        // Simple directional lighting
        float lightIntensity = max(dot(normalize(vNormal), normalize(lightDirection)), 0.4) + 0.3;
        vec3 finalColor = texColor.rgb * clamp(lightIntensity, 0.7, 1.3);

        // Output final color
        gl_FragColor = vec4(finalColor, 1.0);
      }
    `;

    logger.debug('Flipbox shader code registered with Babylon.js');
  }

  /**
   * Create flipbox shader material
   *
   * @param texture Original texture (not atlas) with sprite-sheet frames
   * @param shaderParameters Format: "direction,frameCount,delayMs[,mode]" (e.g., "h,4,100" or "v,4,100,bumerang")
   * @param name Material name
   */
  private createFlipboxMaterial(
    texture: Texture | undefined,
    shaderParameters: string | undefined,
    name: string = 'flipboxMaterial'
  ): ShaderMaterial | null {
    logger.debug('createFlipboxMaterial called', { name, shaderParameters, hasTexture: !!texture, hasScene: !!this.scene });

    if (!this.scene) {
      logger.error('Cannot create flipbox material: Scene not initialized');
      return null;
    }

    // Use defaults if no parameters provided (for debugging)
    let flipDirection = 'h';
    let frameCount = 1;
    let delayMs = 100;
    let mode = 'rotate';

    if (shaderParameters) {
      // Parse shader parameters: "direction,frameCount,delayMs[,mode]"
      const parts = shaderParameters.split(',');
      if (parts.length < 3 || parts.length > 4) {
        logger.error('Invalid flipbox shaderParameters format (expected "direction,frameCount,delayMs[,mode]")', { shaderParameters });
        logger.info('Using defaults: h,1,100,rotate');
      } else {
        flipDirection = parts[0]?.trim().toLowerCase() || 'h'; // Default: horizontal
        frameCount = parseInt(parts[1], 10);
        delayMs = parseInt(parts[2], 10);
        mode = parts[3]?.trim().toLowerCase() || 'rotate'; // Default: rotate
      }
    } else {
      logger.warn('No shaderParameters provided, using defaults: h,1,100,rotate');
    }

    if (flipDirection !== 'h' && flipDirection !== 'v') {
      logger.error('Invalid flipbox direction (expected "h" or "v")', { flipDirection });
      return null;
    }

    if (isNaN(frameCount) || isNaN(delayMs) || frameCount < 1 || delayMs < 1) {
      logger.error('Invalid flipbox shaderParameters values', { frameCount, delayMs });
      return null;
    }

    // Normalize mode spelling (accept both "bumerang" and "boomerang")
    if (mode === 'boomerang') {
      mode = 'bumerang';
    }

    // Default to rotate if mode is unknown
    if (mode !== 'rotate' && mode !== 'bumerang') {
      logger.warn('Unknown flipbox mode, defaulting to "rotate"', { mode });
      mode = 'rotate';
    }

    logger.debug('Creating flipbox material', { name, flipDirection, frameCount, delayMs, mode });

    const material = new ShaderMaterial(
      name,
      this.scene,
      {
        vertex: 'flipbox',
        fragment: 'flipbox',
      },
      {
        attributes: ['position', 'normal', 'uv'],
        uniforms: [
          'worldViewProjection',
          'world',
          'currentFrame',
          'frameCount',
          'flipDirection',
          'textureSampler',
          'lightDirection',
        ],
        samplers: ['textureSampler'],
      }
    );

    // Error handling for shader compilation
    material.onError = (effect, errors) => {
      logger.error('Flipbox shader compilation error', { name, errors });
    };

    material.onCompiled = () => {
      logger.debug('Flipbox shader compiled successfully', { name });
    };

    // Set texture if provided
    if (texture) {
      material.setTexture('textureSampler', texture);
      // Enable alpha for transparency
      texture.hasAlpha = true;
      // Clamp texture to prevent wrapping/bleeding
      texture.wrapU = Texture.CLAMP_ADDRESSMODE;
      texture.wrapV = Texture.CLAMP_ADDRESSMODE;
    }

    // Set frame count
    material.setInt('frameCount', frameCount);

    // Set flip direction (0 = horizontal, 1 = vertical)
    material.setInt('flipDirection', flipDirection === 'h' ? 0 : 1);

    // Set light direction (from above-front)
    material.setVector3('lightDirection', new Vector3(0.5, 1.0, 0.5));

    // Configure material properties for alpha test
    material.backFaceCulling = false; // Show both sides
    material.transparencyMode = Material.MATERIAL_ALPHATEST; // Use alpha test mode

    // Setup frame animation
    let currentFrame = 0;
    let direction = 1; // 1 = forward, -1 = backward (for bumerang mode)
    let lastFrameTime = Date.now();
    const scene = this.scene; // Capture scene reference for closure

    scene.onBeforeRenderObservable.add(() => {
      const now = Date.now();
      if (now - lastFrameTime >= delayMs) {
        if (mode === 'bumerang') {
          // Bumerang mode: 0,1,2,3,2,1,0,1,2,3,2,1...
          currentFrame += direction;

          // Reverse direction at boundaries
          if (currentFrame >= frameCount - 1) {
            direction = -1; // Start going backward
          } else if (currentFrame <= 0) {
            direction = 1; // Start going forward
          }
        } else {
          // Rotate mode: 0,1,2,3,0,1,2,3...
          currentFrame = (currentFrame + 1) % frameCount;
        }

        material.setInt('currentFrame', currentFrame);
        lastFrameTime = now;
      }
    });

    logger.debug('Flipbox material created successfully', { name, materialCreated: !!material });
    return material;
  }

  /**
   * Register thin instance wind shader code
   */
  private registerThinInstanceWindShader(): void {
    // Vertex shader for thin instances with Y-axis billboard and wind
    Effect.ShadersStore['thinInstanceWindVertexShader'] = `
      precision highp float;

      // Attributes
      attribute vec3 position;
      attribute vec3 normal;
      attribute vec2 uv;

      // Uniforms
      uniform mat4 viewProjection;
      uniform mat4 view;
      uniform vec3 cameraPosition;
      uniform float time;
      uniform vec2 windDirection;
      uniform float windStrength;
      uniform float windGustStrength;
      uniform float windSwayFactor;

      // Varyings to fragment shader
      varying vec2 vUV;
      varying vec3 vNormal;

      #ifdef INSTANCES
        attribute vec4 world0;
        attribute vec4 world1;
        attribute vec4 world2;
        attribute vec4 world3;
        attribute vec4 windParams; // (leafiness, stability, leverUp, leverDown)
      #else
        uniform mat4 world;
      #endif

      void main(void) {
        // Reconstruct world matrix from thin instance attributes
        #ifdef INSTANCES
          mat4 world = mat4(world0, world1, world2, world3);
        #endif

        // Get instance position (from matrix translation)
        vec3 instancePos = vec3(world[3][0], world[3][1], world[3][2]);

        // Get wind parameters (per-instance or defaults)
        #ifdef INSTANCES
          float windLeafiness = windParams.x;
          float windStability = windParams.y;
          float windLeverUp = windParams.z;
          float windLeverDown = windParams.w;
        #else
          float windLeafiness = 0.5;
          float windStability = 0.5;
          float windLeverUp = 0.0;
          float windLeverDown = 0.0;
        #endif

        // Y-Axis Billboard transformation
        // Calculate direction from instance to camera (XZ plane only)
        vec3 toCamera = cameraPosition - instancePos;
        toCamera.y = 0.0; // Ignore Y component (stay vertical)
        vec3 forward = normalize(toCamera);

        // Calculate right vector (perpendicular to forward in XZ plane)
        vec3 up = vec3(0.0, 1.0, 0.0);
        vec3 right = normalize(cross(up, forward));

        // Reconstruct forward to ensure orthogonality
        forward = cross(right, up);

        // Build billboard rotation matrix (Y-axis only)
        mat3 billboardRotation = mat3(
          right,
          up,
          forward
        );

        // Apply billboard rotation to vertex position
        vec3 rotatedPos = billboardRotation * position;

        // Wind animation (only if windLeafiness > 0.0, only affects top vertices)
        if (windLeafiness > 0.0) {
          float heightFactor = clamp(rotatedPos.y / 2.0, 0.0, 1.0); // 0 at bottom, 1 at top

          if (heightFactor > 0.0) {
            // Base sway wave modulated by leafiness (flutter)
            float baseWave = sin(time * windSwayFactor + instancePos.x * 0.1 + instancePos.z * 0.1) * windStrength;

            // Leaf flutter (high frequency, affected by leafiness)
            float flutter = sin(time * windSwayFactor * 5.0 + instancePos.x * 0.3 + instancePos.z * 0.3) * windLeafiness * 0.2;

            // Gust effect
            float gustWave = sin(time * windSwayFactor * 2.3 + instancePos.x * 0.1) * windGustStrength;
            gustWave *= sin(time * windSwayFactor * 0.7);

            // Combine waves with stability factor (0 = full movement, 1 = no movement)
            float totalWave = ((baseWave + gustWave * 0.5) * 0.3 + flutter) * (1.0 - windStability * 0.7);

            // Wind direction
            vec3 windDir = vec3(windDirection.x, 0.0, windDirection.y);
            if (length(windDir) > 0.01) {
              windDir = normalize(windDir);
            }

            // Lever effect: Adjust height-based bending
            // leverUp extends the top part, leverDown moves pivot point
            float leverFactor = heightFactor;
            if (windLeverUp > 0.0) {
              // Extend bending to higher parts
              leverFactor = pow(heightFactor, 1.0 / (1.0 + windLeverUp * 0.5));
            }
            if (windLeverDown > 0.0) {
              // Lower the pivot point
              leverFactor = smoothstep(windLeverDown * 0.1, 1.0, heightFactor);
            }

            // Apply wind displacement (more at top, scaled by lever factor)
            rotatedPos += windDir * totalWave * leverFactor;
          }
        }

        // Transform to world space
        vec4 worldPosition = world * vec4(rotatedPos, 1.0);

        // Transform to clip space
        gl_Position = viewProjection * worldPosition;

        // Pass to fragment shader
        vUV = uv;
        vNormal = normalize((world * vec4(normal, 0.0)).xyz);
      }
    `;

    // Fragment shader (simple textured)
    Effect.ShadersStore['thinInstanceWindFragmentShader'] = `
      precision highp float;

      varying vec2 vUV;
      varying vec3 vNormal;

      uniform sampler2D textureSampler;
      uniform vec3 lightDirection;

      void main(void) {
        vec4 texColor = texture2D(textureSampler, vUV);

        // Alpha test
        if (texColor.a < 0.5) {
          discard;
        }

        // Simple diffuse lighting
        float diffuse = max(dot(vNormal, -lightDirection), 0.3); // Min 0.3 ambient
        vec3 finalColor = texColor.rgb * diffuse;

        gl_FragColor = vec4(finalColor, texColor.a);
      }
    `;

    logger.debug('Thin instance wind shader registered');
  }

  /**
   * Create material for thin instances with Y-axis billboard and wind animation
   *
   * @param texturePath Path to texture
   * @returns Material with Y-axis billboard and wind shader
   */
  async createThinInstanceMaterial(texturePath: string): Promise<ShaderMaterial | null> {
    if (!this.scene) {
      logger.error('Scene not initialized');
      return null;
    }

    const networkService = this.appContext.services.network;
    if (!networkService) {
      logger.error('NetworkService not available');
      return null;
    }

    try {
      // Load texture with credentials
      const url = networkService.getAssetUrl(texturePath);
      const blobUrl = await loadTextureUrlWithCredentials(url);
      const texture = new Texture(blobUrl, this.scene);
      texture.hasAlpha = true;

      // Also set after texture loads to ensure it takes effect
      texture.onLoadObservable.addOnce(() => {
        texture.updateSamplingMode(Texture.NEAREST_SAMPLINGMODE);
      });

      // Create shader material
      const material = new ShaderMaterial(
        `thinInstanceWind_${texturePath}`,
        this.scene,
        {
          vertex: 'thinInstanceWind',
          fragment: 'thinInstanceWind',
        },
        {
          attributes: ['position', 'normal', 'uv'],
          uniforms: [
            'viewProjection',
            'view',
            'world',
            'cameraPosition',
            'time',
            'windDirection',
            'windStrength',
            'windGustStrength',
            'windSwayFactor',
            'lightDirection',
          ],
          defines: ['#define INSTANCES'],
        }
      );

      // Set texture
      material.setTexture('textureSampler', texture);

      // Set initial wind parameters
      if (this.environmentService) {
        const windParams = this.environmentService.getWindParameters();
        this.setWindParametersOnMaterial(material, windParams);
      } else {
        // Default wind parameters
        material.setVector2('windDirection', new Vector2(1, 0));
        material.setFloat('windStrength', 0.5);
        material.setFloat('windGustStrength', 0.3);
        material.setFloat('windSwayFactor', 1.0);
      }

      // Set light direction
      material.setVector3('lightDirection', new Vector3(0.5, -1, 0.5));

      // Set time uniform (will be updated in render loop)
      material.setFloat('time', 0);

      // Add to wind materials for automatic updates
      this.windMaterials.push(material);

      // Disable backface culling
      material.backFaceCulling = false;

      logger.debug('Thin instance wind material created', { texturePath });

      return material;
    } catch (error) {
      logger.error('Failed to create thin instance material', { texturePath, error });
      return null;
    }
  }

  /**
   * Set wind parameters on a material
   */
  private setWindParametersOnMaterial(material: ShaderMaterial, params: WindParameters): void {
    material.setVector2('windDirection', new Vector2(params.windDirection.x, params.windDirection.z));
    material.setFloat('windStrength', params.windStrength);
    material.setFloat('windGustStrength', params.windGustStrength);
    material.setFloat('windSwayFactor', params.windSwayFactor);
    material.setFloat('time', params.time);
  }
}
