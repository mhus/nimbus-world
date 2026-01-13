/**
 * MaterialService - Manages Babylon.js materials for rendering
 *
 * Creates and caches materials for blocks based on BlockModifier properties.
 * Each unique combination of material properties gets its own cached material.
 * Supports shader effects via ShaderService integration.
 */

import {
  StandardMaterial,
  Scene,
  Texture,
  Color3,
  Material
} from '@babylonjs/core';
import {
  getLogger,
  ExceptionHandler,
  type BlockModifier,
  type TextureDefinition,
  type TextureKey,
  type UVMapping,
  TextureHelper,
  SamplingMode,
  TransparencyMode,
  BlockEffect
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { TextureAtlas } from '../rendering/TextureAtlas';
import type { ShaderService } from './ShaderService';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('MaterialService');

/**
 * Material type for rendering
 */
export enum MaterialType {
  STANDARD = 'standard',
  // Future material types will be added here based on BlockModifier.visibility.effect:
  // WATER = 'water',
  // LAVA = 'lava',
  // WIND = 'wind',
  // FOG = 'fog',
}

/**
 * MaterialService - Manages materials for rendering
 *
 * Creates and caches materials based on BlockModifier properties.
 * Integrates with ShaderService for effect-based materials.
 */
export class MaterialService {
  public scene: Scene; // Public for renderer access
  private appContext: AppContext;
  private textureAtlas?: TextureAtlas;
  private shaderService?: ShaderService;

  // Material cache: Map<materialKey, Material>
  public materials: Map<string, Material> = new Map(); // Public for glass material caching

  // Texture cache: Map<texturePath, Babylon.js Texture>
  private textures: Map<string, Texture> = new Map();

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    logger.debug('MaterialService initialized');
  }

  /**
   * Set shader service for effect-based materials
   */
  setShaderService(shaderService: ShaderService): void {
    this.shaderService = shaderService;
    logger.debug('ShaderService set');
  }

  /**
   * Set the texture atlas
   *
   * Must be called before creating materials
   * @deprecated TextureAtlas is deprecated in favor of direct texture loading
   */
  setTextureAtlas(textureAtlas: TextureAtlas): void {
    this.textureAtlas = textureAtlas;
    logger.debug('Texture atlas set');
  }

  /**
   * Generate a unique material key from BlockModifier and textureIndex
   *
   * The key consists of all material-relevant properties:
   * - path: texture file path
   * - uvMapping: all UV transformation parameters
   * - samplingMode: texture sampling mode
   * - transparencyMode: transparency handling
   * - opacity: alpha value
   * - shaderParameters: effect-specific parameters
   * - effect: final effect (Texture.effect overrides BlockModifier.effect)
   * - color: tint color
   *
   * @param modifier BlockModifier containing material properties
   * @param textureIndex TextureKey indicating which texture to use
   * @returns Unique material key string for caching
   */
  getMaterialKey(modifier: BlockModifier, textureIndex: TextureKey): string {
    // Extract texture from modifier
    const texture = this.getTextureFromModifier(modifier, textureIndex);

    if (!texture) {
      // No texture defined - return default key
      return 'atlas:default';
    }

    // Normalize texture (string → TextureDefinition)
    const textureDef = TextureHelper.normalizeTexture(texture);

    // Check for effect (texture.effect or visibility.effect)
    const finalEffect = textureDef.effect ?? modifier.visibility?.effect ?? BlockEffect.NONE;

    // Build key based on MATERIAL PROPERTIES, not texture path
    // This allows grouping blocks with different textures but same material properties
    const parts: string[] = ['atlas']; // Always use atlas texture

    // 1. backFaceCulling (default: true)
    parts.push(`bfc:${textureDef.backFaceCulling ?? true}`);

    // 2. Transparency mode (default: NONE)
    parts.push(`tm:${textureDef.transparencyMode ?? TransparencyMode.NONE}`);

    // 3. Opacity (default: 1.0)
    parts.push(`op:${textureDef.opacity ?? 1.0}`);

    // 4. Sampling mode (default: LINEAR)
    parts.push(`sm:${textureDef.samplingMode ?? SamplingMode.LINEAR}`);

    // 5. Effect (already computed above, only add if not NONE)
    // Note: FLIPBOX already handled above (early return), so we don't need to check it here
    if (finalEffect !== BlockEffect.NONE) {
      parts.push(`eff:${finalEffect}`);

      // Effect parameters (if defined)
      const effectParams = textureDef.effectParameters ?? modifier.visibility?.effectParameters;
      if (effectParams) {
        parts.push(`ep:${effectParams}`);
      }
    }

    // 6. Color (if defined)
    if (textureDef.color) {
        parts.push(`c:${textureDef.color}`);
    }

    // 7. Illumination (if defined)
    if (modifier.illumination?.color || modifier.illumination?.strength) {
      const color = modifier.illumination.color ?? '#ffffff';
      const strength = modifier.illumination.strength ?? 1.0;
      parts.push(`illum:${color}:${strength}`);
    }

    // Note: Texture path is NOT part of the key (except for FLIPBOX) - UVs handle texture selection via atlas
    // Note: UV mapping is NOT part of the key - handled per-vertex in geometry

    // Join all parts with separator
    return parts.join('|');
  }

  /**
   * Extract texture from BlockModifier for a given textureIndex
   */
  private getTextureFromModifier(
    modifier: BlockModifier,
    textureIndex: TextureKey
  ): TextureDefinition | string | undefined {
    if (!modifier.visibility?.textures) {
      return undefined;
    }

    return TextureHelper.getTexture(modifier.visibility, textureIndex);
  }

  /**
   * Parse material key into properties
   *
   * @param materialKey Material key string (e.g., "atlas|bfc:false|tm:0|op:1.0|sm:1|eff:1|ep:4,100")
   * @returns Parsed material properties
   */
  private parseMaterialKey(materialKey: string): {
    backFaceCulling: boolean;
    transparencyMode: number;
    opacity: number;
    samplingMode: number;
    effect: number;
    effectParameters?: string;
    texturePath?: string;
    color?: string;
    illuminationColor?: string;
    illuminationStrength?: number;
  } {
    const parts = materialKey.split('|');
    const props: any = {
      backFaceCulling: true,
      transparencyMode: 0,
      opacity: 1.0,
      samplingMode: 1,
      effect: BlockEffect.NONE,
    };

    for (const part of parts) {
      const [key, value] = part.split(':');
      switch (key) {
        case 'bfc':
          props.backFaceCulling = value === 'true';
          break;
        case 'tm':
          props.transparencyMode = parseInt(value);
          break;
        case 'op':
          props.opacity = parseFloat(value);
          break;
        case 'sm':
          props.samplingMode = parseInt(value);
          break;
        case 'eff':
          props.effect = parseInt(value);
          break;
        case 'ep':
          props.effectParameters = value;
          break;
        case 'tex':
          props.texturePath = value;
          break;
        case 'c':
          props.color = value;
          break;
        case 'illum':
          const [_, illumColor, illumStrength] = part.split(':');
          props.illuminationColor = illumColor;
          props.illuminationStrength = parseFloat(illumStrength);
          break;
      }
    }

    return props;
  }

  /**
   * Check if BlockModifier has wind properties
   */
  private hasWindProperties(modifier: BlockModifier): boolean {
    return !!(
      (modifier.wind?.leafiness && modifier.wind.leafiness > 0) ||
      (modifier.wind?.stability && modifier.wind.stability > 0) ||
      (modifier.wind?.leverUp && modifier.wind.leverUp > 0) ||
      (modifier.wind?.leverDown && modifier.wind.leverDown > 0)
    );
  }

  /**
   * Get or create a material for a block based on BlockModifier and textureIndex
   *
   * NEW IMPLEMENTATION (Property-based grouping):
   * 1. Generates a material key based on properties (not texture path)
   * 2. Returns cached material if available
   * 3. Otherwise creates a new material with atlas texture and properties
   *
   * Note: All materials use the texture atlas. UVs (calculated in RenderService)
   * determine which part of the atlas each face uses.
   *
   * @param modifier BlockModifier containing material properties
   * @param textureIndex TextureKey indicating which texture to use
   * @returns Material (cached or newly created)
   */
  async getMaterial(
    modifier: BlockModifier,
    textureIndex: TextureKey
  ): Promise<Material> {
    try {
      // Generate cache key based on properties
      const cacheKey = this.getMaterialKey(modifier, textureIndex);

      // Check cache
      if (this.materials.has(cacheKey)) {
        return this.materials.get(cacheKey)!;
      }

      // Parse material properties from key
      const props = this.parseMaterialKey(cacheKey);

      // Create material based on effect
      let material: Material | null = null;

      // Check for effect (e.g., WIND)
      if (props.effect !== BlockEffect.NONE && this.shaderService) {
        const effectName = BlockEffect[props.effect].toLowerCase();

        if (this.shaderService.hasEffect(effectName)) {
          // Effects use atlas texture
          const atlasTexture = this.textureAtlas?.getTexture();
          material = this.shaderService.createMaterial(
            effectName,
            {
              texture: atlasTexture,
              effectParameters: props.effectParameters,
              name: cacheKey,
              materialProps: props,  // Pass material properties to shader
            }
          );
          logger.debug('Created effect material with atlas', { effectName, cacheKey });
        }
      }

      // Fallback to StandardMaterial
      if (!material) {
        material = this.createStandardMaterialWithAtlas(cacheKey, props);
      }

      // Apply material properties to shader materials
      if (material && props.effect !== BlockEffect.NONE) {
        material.backFaceCulling = props.backFaceCulling;
        material.alpha = props.opacity;
        // Note: ShaderMaterial doesn't have transparencyMode, alpha test is handled in shader
        logger.debug('Applied material properties to shader material', {
          backFaceCulling: props.backFaceCulling,
          opacity: props.opacity,
        });
      }

      // Cache material
      this.materials.set(cacheKey, material);

      logger.debug('Material created and cached', { cacheKey, props });

      return material;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'MaterialService.getMaterial',
        { textureIndex }
      );
    }
  }

  /**
   * Create a StandardMaterial with atlas texture and properties
   *
   * @param name Material name (cache key)
   * @param props Material properties parsed from key
   * @returns StandardMaterial with atlas texture and applied properties
   */
  private createStandardMaterialWithAtlas(
    name: string,
    props: {
      backFaceCulling: boolean;
      transparencyMode: number;
      opacity: number;
      samplingMode: number;
      color?: string;
      illuminationColor?: string;
      illuminationStrength?: number;
    }
  ): StandardMaterial {
    const material = new StandardMaterial(name, this.scene);

    // Use texture atlas
    const atlasTexture = this.textureAtlas?.getTexture();
    if (atlasTexture) {
      material.diffuseTexture = atlasTexture;
    }

    // Apply properties from key
    material.backFaceCulling = props.backFaceCulling;

    // Map TransparencyMode to Babylon.js Material.transparencyMode
    // Note: Atlas texture has hasAlpha=true by default (set in TextureAtlas.load())
    // We control transparency solely via material.transparencyMode and material.alpha
    switch (props.transparencyMode) {
      case TransparencyMode.NONE:
        material.transparencyMode = Material.MATERIAL_OPAQUE;
        break;
      case TransparencyMode.ALPHA_TEST:
        material.transparencyMode = Material.MATERIAL_ALPHATEST;
        material.useAlphaFromDiffuseTexture = true;
        break;
      case TransparencyMode.ALPHA_BLEND:
        material.transparencyMode = Material.MATERIAL_ALPHABLEND;
        material.useAlphaFromDiffuseTexture = true;
        break;
      case TransparencyMode.ALPHA_TEST_FROM_RGB:
        material.transparencyMode = Material.MATERIAL_ALPHATEST;
        // Note: getAlphaFromRGB would need per-material texture, not supported with atlas
        logger.warn('ALPHA_TEST_FROM_RGB not fully supported with atlas texture');
        break;
      case TransparencyMode.ALPHA_BLEND_FROM_RGB:
        material.transparencyMode = Material.MATERIAL_ALPHABLEND;
        // Note: getAlphaFromRGB would need per-material texture, not supported with atlas
        logger.warn('ALPHA_BLEND_FROM_RGB not fully supported with atlas texture');
        break;
      case TransparencyMode.ALPHA_TESTANDBLEND:
        material.transparencyMode = Material.MATERIAL_ALPHATESTANDBLEND;
        material.useAlphaFromDiffuseTexture = true;
        break;
      case TransparencyMode.ALPHA_TESTANDBLEND_FROM_RGB:
        material.transparencyMode = Material.MATERIAL_ALPHATESTANDBLEND;
        // Note: getAlphaFromRGB would need per-material texture, not supported with atlas
        logger.warn('ALPHA_TESTANDBLEND_FROM_RGB not fully supported with atlas texture');
        break;
      default:
        // Fallback to opaque
        material.transparencyMode = Material.MATERIAL_OPAQUE;
        break;
    }

    material.alpha = props.opacity;

    // Disable specular highlights for blocks
    if (props.color) {
      try {
        const color = Color3.FromHexString(props.color);
        material.diffuseColor = color;
        material.specularColor = color;
        material.emissiveColor = color;
        material.diffuseTexture = null; // Remove texture if color tint is applied
      } catch (error) {
        logger.warn('Failed to parse color for material', { color: props.color, error });
      }
    } else {
      material.specularColor = new Color3(0, 0, 0);
    }

    // Apply illumination (emissive color for glow effect)
    if (props.illuminationColor) {
      const strength = props.illuminationStrength ?? 1.0;
      const baseColor = Color3.FromHexString(props.illuminationColor);

      // Scale color by strength for intensity control
      material.emissiveColor = baseColor.scale(strength);

      // If using atlas texture, also set emissiveTexture
      if (atlasTexture) {
        material.emissiveTexture = atlasTexture;
      }
    }

    return material;
  }

  /**
   * Create a StandardMaterial with texture (OLD - kept for compatibility)
   * @deprecated Use createStandardMaterialWithAtlas instead
   */
  private async createStandardMaterial(
    textureDef: TextureDefinition,
    name: string
  ): Promise<StandardMaterial> {
    const material = new StandardMaterial(name, this.scene);

    // Load and apply texture
    const bTexture = await this.loadTexture(textureDef);
    if (bTexture) {
      material.diffuseTexture = bTexture;
    }

    // Disable specular highlights for blocks
    material.specularColor = new Color3(0, 0, 0);

    // Note: backFaceCulling will be set in applyMaterialProperties()

    return material;
  }

  /**
   * Load a Babylon.js Texture from path
   * Public for renderer access (FLIPBOX, BILLBOARD, FLAME need original textures)
   */
  async loadTexture(pathOrDef: string | TextureDefinition): Promise<Texture | null> {
    // Handle string path
    if (typeof pathOrDef === 'string') {
      const textureDef: TextureDefinition = { path: pathOrDef };
      return this.loadTextureInternal(textureDef);
    }

    // Handle TextureDefinition
    return this.loadTextureInternal(pathOrDef);
  }

  /**
   * Load a Babylon.js Texture from TextureDefinition (internal)
   */
  private async loadTextureInternal(textureDef: TextureDefinition): Promise<Texture | null> {
    try {
      // Get full URL from NetworkService
      const networkService = this.appContext.services.network;
      if (!networkService) {
        throw new Error('NetworkService not available');
      }

      const url = networkService.getAssetUrl(textureDef.path);

      // Check texture cache
      const cacheKey = this.getTextureCacheKey(textureDef);
      if (this.textures.has(cacheKey)) {
        logger.debug('Texture loaded from cache', { path: textureDef.path });
        return this.textures.get(cacheKey)!;
      }

      logger.debug('Loading texture with credentials', { path: textureDef.path, url });

      // Load texture with credentials (returns blob URL)
      const blobUrl = await loadTextureUrlWithCredentials(url);

      // Create Babylon.js Texture from blob URL
      const texture = new Texture(blobUrl, this.scene);

      // Apply UV mapping transformations if defined
      if (textureDef.uvMapping) {
        this.applyUVMapping(texture, textureDef.uvMapping);
      }

      // Apply sampling mode
      if (textureDef.samplingMode !== undefined) {
        texture.updateSamplingMode(this.toBabylonSamplingMode(textureDef.samplingMode));
      }

      // Cache texture
      this.textures.set(cacheKey, texture);

      logger.debug('Texture loaded', { path: textureDef.path, url });

      return texture;
    } catch (error) {
      ExceptionHandler.handle(error, 'MaterialService.loadTexture', {
        path: textureDef.path,
      });
      return null;
    }
  }

  /**
   * Apply UV mapping properties to Babylon.js Texture
   */
  private applyUVMapping(texture: Texture, uvMapping: UVMapping): void {
    // Apply UV transformations
    if (uvMapping.uScale !== undefined) texture.uScale = uvMapping.uScale;
    if (uvMapping.vScale !== undefined) texture.vScale = uvMapping.vScale;
    if (uvMapping.uOffset !== undefined) texture.uOffset = uvMapping.uOffset;
    if (uvMapping.vOffset !== undefined) texture.vOffset = uvMapping.vOffset;

    // Apply wrap modes
    if (uvMapping.wrapU !== undefined) {
      texture.wrapU = this.toBabylonWrapMode(uvMapping.wrapU);
    }
    if (uvMapping.wrapV !== undefined) {
      texture.wrapV = this.toBabylonWrapMode(uvMapping.wrapV);
    }

    // Apply rotation centers
    if (uvMapping.uRotationCenter !== undefined) {
      texture.uRotationCenter = uvMapping.uRotationCenter;
    }
    if (uvMapping.vRotationCenter !== undefined) {
      texture.vRotationCenter = uvMapping.vRotationCenter;
    }

    // Apply rotation angles
    if (uvMapping.wAng !== undefined) texture.wAng = uvMapping.wAng;
    if (uvMapping.uAng !== undefined) texture.uAng = uvMapping.uAng;
    if (uvMapping.vAng !== undefined) texture.vAng = uvMapping.vAng;
  }

  /**
   * Apply material properties (opacity, transparency, color)
   * @deprecated This method is not currently used. Material properties are set directly in getMaterial().
   */
  private applyMaterialProperties(material: StandardMaterial, textureDef: TextureDefinition): void {
    // Apply opacity
    if (textureDef.opacity !== undefined && textureDef.opacity !== 1.0) {
      material.alpha = textureDef.opacity;
    }

    // Apply transparency mode
    if (textureDef.transparencyMode !== undefined) {
      // Map TransparencyMode to Babylon.js Material.transparencyMode
      // Note: Do NOT modify texture properties (hasAlpha, getAlphaFromRGB) if using shared atlas
      switch (textureDef.transparencyMode) {
        case TransparencyMode.NONE:
          material.transparencyMode = Material.MATERIAL_OPAQUE;
          break;
        case TransparencyMode.ALPHA_TEST:
          material.transparencyMode = Material.MATERIAL_ALPHATEST;
          material.useAlphaFromDiffuseTexture = true;
          break;
        case TransparencyMode.ALPHA_BLEND:
          material.transparencyMode = Material.MATERIAL_ALPHABLEND;
          material.useAlphaFromDiffuseTexture = true;
          break;
        case TransparencyMode.ALPHA_TEST_FROM_RGB:
          material.transparencyMode = Material.MATERIAL_ALPHATEST;
          logger.warn('ALPHA_TEST_FROM_RGB may not work correctly with shared atlas texture');
          break;
        case TransparencyMode.ALPHA_BLEND_FROM_RGB:
          material.transparencyMode = Material.MATERIAL_ALPHABLEND;
          logger.warn('ALPHA_BLEND_FROM_RGB may not work correctly with shared atlas texture');
          break;
        case TransparencyMode.ALPHA_TESTANDBLEND:
          material.transparencyMode = Material.MATERIAL_ALPHATESTANDBLEND;
          material.useAlphaFromDiffuseTexture = true;
          break;
        case TransparencyMode.ALPHA_TESTANDBLEND_FROM_RGB:
          material.transparencyMode = Material.MATERIAL_ALPHATESTANDBLEND;
          logger.warn('ALPHA_TESTANDBLEND_FROM_RGB may not work correctly with shared atlas texture');
          break;
      }
    }

    // Apply color tint (if StandardMaterial)
    if (material instanceof StandardMaterial && textureDef.color) {
      const color = this.parseColor(textureDef.color);
      if (color) {
        material.diffuseColor = color;
      }
    }

    // Apply backFaceCulling (default: true if not specified)
    const backFaceCullingValue = textureDef.backFaceCulling !== undefined
      ? textureDef.backFaceCulling
      : true;

    material.backFaceCulling = backFaceCullingValue;
  }

  /**
   * Parse color string to Color3
   */
  private parseColor(colorStr: string): Color3 | null {
    try {
      // Support hex colors (#RRGGBB or #RGB)
      if (colorStr.startsWith('#')) {
        return Color3.FromHexString(colorStr);
      }
      // Support named colors or other formats
      // For now, just return null for unsupported formats
      return null;
    } catch (error) {
      logger.warn('Failed to parse color', { color: colorStr });
      return null;
    }
  }

  /**
   * Convert SamplingMode enum to Babylon.js constant
   */
  private toBabylonSamplingMode(mode: SamplingMode): number {
    switch (mode) {
      case SamplingMode.NEAREST:
        return Texture.NEAREST_SAMPLINGMODE;
      case SamplingMode.LINEAR:
        return Texture.BILINEAR_SAMPLINGMODE;
      case SamplingMode.MIPMAP:
        return Texture.TRILINEAR_SAMPLINGMODE;
      default:
        return Texture.BILINEAR_SAMPLINGMODE;
    }
  }

  /**
   * Convert WrapMode to Babylon.js constant
   */
  private toBabylonWrapMode(mode: number): number {
    // WrapMode enum: CLAMP=0, REPEAT=1, MIRROR=2
    switch (mode) {
      case 0: // CLAMP
        return Texture.CLAMP_ADDRESSMODE;
      case 1: // REPEAT
        return Texture.WRAP_ADDRESSMODE;
      case 2: // MIRROR
        return Texture.MIRROR_ADDRESSMODE;
      default:
        return Texture.WRAP_ADDRESSMODE;
    }
  }

  /**
   * Get texture cache key
   */
  private getTextureCacheKey(textureDef: TextureDefinition): string {
    if (textureDef.uvMapping) {
      return `${textureDef.path}:${textureDef.uvMapping.x},${textureDef.uvMapping.y},${textureDef.uvMapping.w},${textureDef.uvMapping.h}`;
    }
    return textureDef.path;
  }

  /**
   * Get or create default material
   */
  private getOrCreateDefaultMaterial(): StandardMaterial {
    const cacheKey = 'default';
    if (this.materials.has(cacheKey)) {
      return this.materials.get(cacheKey) as StandardMaterial;
    }

    const material = new StandardMaterial(cacheKey, this.scene);
    material.diffuseColor = new Color3(1, 0, 1); // Magenta for missing texture
    material.specularColor = new Color3(0, 0, 0);
    this.materials.set(cacheKey, material);

    return material;
  }

  /**
   * Create a glass material with transparency and color
   *
   * @param name Material name (cache key)
   * @param color Glass color (hex string, e.g., "#00ff00" or "#00ff0080")
   * @param opacity Opacity value (0-1)
   * @returns StandardMaterial configured for glass effect
   */
  createGlassMaterial(name: string, color: string = '#ffffff', opacity: number = 0.5): StandardMaterial {
    const material = new StandardMaterial(name, this.scene);

    // Parse color (supports #RRGGBB or #RRGGBBAA format)
    let parsedColor = Color3.FromHexString(color.substring(0, 7)); // Take first 6 chars for RGB
    let parsedAlpha = opacity;

    // Check if alpha is included in hex color (#RRGGBBAA)
    if (color.length === 9) {
      const alphaHex = color.substring(7, 9);
      parsedAlpha = parseInt(alphaHex, 16) / 255;
    }

    // Set glass properties
    material.diffuseColor = parsedColor;
    material.alpha = parsedAlpha;
    material.backFaceCulling = false; // Glass visible from both sides

    // Glass-like specular (shiny, reflective)
    material.specularColor = new Color3(0.5, 0.5, 0.5); // Some specular for glass shine
    material.specularPower = 64; // High specular power for glass-like highlights

    // Enable transparency (glass uses smooth alpha blending)
    material.transparencyMode = Material.MATERIAL_ALPHABLEND;

    logger.debug('Created glass material', { name, color, opacity: parsedAlpha });

    return material;
  }

  /**
   * Get or create a standard material
   *
   * @param name Material name
   * @returns Standard material
   * @deprecated Use getMaterial() with BlockModifier instead
   */
  getStandardMaterial(name: string = 'default'): StandardMaterial {
    const cacheKey = `standard:${name}`;

    // Check cache
    if (this.materials.has(cacheKey)) {
      const cached = this.materials.get(cacheKey)!;
      if (cached instanceof StandardMaterial) {
        return cached;
      }
    }

    // Create new material
    try {
      if (!this.textureAtlas) {
        throw new Error('TextureAtlas not set');
      }

      const atlasMaterial = this.textureAtlas.getMaterial();
      if (!atlasMaterial) {
        throw new Error('Atlas material not available');
      }

      // For now, we return the atlas material directly
      // In the future, we might create variants with different properties
      this.materials.set(cacheKey, atlasMaterial);

      logger.debug('Created standard material', { name });

      return atlasMaterial;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'MaterialService.getStandardMaterial', { name });
    }
  }

  /**
   * Create a material with original texture (not cached)
   *
   * Used for blocks that need individual textures (BILLBOARD, SPRITE, etc.)
   * Applies all material properties from BlockModifier:
   * - opacity, transparencyMode
   * - backFaceCulling
   * - samplingMode
   * - UV transformations
   *
   * @param name Material name (for debugging)
   * @param modifier BlockModifier containing material properties
   * @param textureIndex TextureKey indicating which texture to use
   * @returns New StandardMaterial with original texture (not cached)
   */
  async createOriginalTextureMaterial(
    name: string,
    modifier: BlockModifier,
    textureIndex: TextureKey
  ): Promise<StandardMaterial> {
    try {
      // Get texture definition
      const textures = modifier.visibility?.textures;
      if (!textures) {
        throw new Error('No textures defined in modifier');
      }

      const textureDef = TextureHelper.normalizeTexture(textures[textureIndex]);
      if (!textureDef) {
        throw new Error(`No texture found for index ${textureIndex}`);
      }

      // Create material
      const material = new StandardMaterial(name, this.scene);

      // Load original texture (not atlas)
      const texture = await this.loadTexture(textureDef);
      if (texture) {
        material.diffuseTexture = texture;
      } else {
        logger.warn('Failed to load texture for original material', {
          name,
          texturePath: textureDef.path
        });
      }

      // Apply material properties from textureDef
      // Opacity & Transparency
      if (textureDef.opacity !== undefined) {
        material.alpha = textureDef.opacity;
      }

      // Transparency mode
      if (textureDef.transparencyMode !== undefined) {
        switch (textureDef.transparencyMode) {
          case TransparencyMode.NONE:
            material.transparencyMode = Material.MATERIAL_OPAQUE;
            break;
          case TransparencyMode.ALPHA_TEST:
            material.transparencyMode = Material.MATERIAL_ALPHATEST;
            if (texture) texture.hasAlpha = true;
            material.useAlphaFromDiffuseTexture = true;
            break;
          case TransparencyMode.ALPHA_BLEND:
            material.transparencyMode = Material.MATERIAL_ALPHABLEND;
            if (texture) texture.hasAlpha = true;
            material.useAlphaFromDiffuseTexture = true;
            break;
          case TransparencyMode.ALPHA_TEST_FROM_RGB:
            material.transparencyMode = Material.MATERIAL_ALPHATEST;
            if (texture) texture.getAlphaFromRGB = true;
            break;
          case TransparencyMode.ALPHA_BLEND_FROM_RGB:
            material.transparencyMode = Material.MATERIAL_ALPHABLEND;
            if (texture) texture.getAlphaFromRGB = true;
            break;
          case TransparencyMode.ALPHA_TESTANDBLEND:
            material.transparencyMode = Material.MATERIAL_ALPHATESTANDBLEND;
            if (texture) texture.hasAlpha = true;
            material.useAlphaFromDiffuseTexture = true;
            break;
          case TransparencyMode.ALPHA_TESTANDBLEND_FROM_RGB:
            material.transparencyMode = Material.MATERIAL_ALPHATESTANDBLEND;
            if (texture) texture.getAlphaFromRGB = true;
            break;
        }
      }

      // Backface Culling
      if (textureDef.backFaceCulling !== undefined) {
        material.backFaceCulling = textureDef.backFaceCulling;
      } else {
        // Default: disable backface culling for billboards/sprites (visible from both sides)
        material.backFaceCulling = false;
      }

      // Sampling Mode (if texture loaded)
      if (texture && textureDef.samplingMode !== undefined) {
        switch (textureDef.samplingMode) {
          case SamplingMode.NEAREST:
            texture.updateSamplingMode(Texture.NEAREST_NEAREST);
            break;
          case SamplingMode.LINEAR:
            texture.updateSamplingMode(Texture.LINEAR_LINEAR);
            break;
          case SamplingMode.MIPMAP:
            texture.updateSamplingMode(Texture.TRILINEAR_SAMPLINGMODE);
            break;
        }
      }

      // Disable specular highlights for blocks
      material.specularColor = new Color3(0, 0, 0);

      logger.debug('Created original texture material', {
        name,
        texturePath: textureDef.path,
      });

      return material;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'MaterialService.createOriginalTextureMaterial',
        { name, textureIndex }
      );
    }
  }

  /**
   * Get material for a block based on effect name
   *
   * This is a placeholder for future shader integration.
   * Currently always returns standard material.
   *
   * @param effectName Effect name from BlockModifier.visibility.effect
   * @param effectParameters Effect parameters from BlockModifier.visibility.effectParameters
   * @returns Material for the block
   */
  getMaterialForEffect(
    effectName?: string,
    effectParameters?: Record<string, any>
  ): StandardMaterial {
    // Future implementation will check ShaderService for custom effects
    // For now, always return standard material
    if (effectName) {
      logger.debug('Effect requested but not yet implemented', { effectName });
    }

    return this.getStandardMaterial();
  }

  /**
   * Get the atlas material (shorthand)
   */
  getAtlasMaterial(): StandardMaterial {
    return this.getStandardMaterial();
  }

  /**
   * Check if material service is ready
   */
  isReady(): boolean {
    return this.textureAtlas !== undefined && this.textureAtlas.isReady();
  }

  /**
   * Clear material cache
   *
   * Clears all cached materials and textures without disposing them.
   * Use this when you want to reload materials.
   */
  clearCache(): void {
    this.materials.clear();
    this.textures.clear();
    logger.debug('Material and texture caches cleared');
  }

  /**
   * Get all material keys in cache
   */
  getAllMaterialKeys(): string[] {
    return Array.from(this.materials.keys());
  }

  /**
   * Get a material by its cache key
   */
  getMaterialByKey(key: string): Material | undefined {
    return this.materials.get(key);
  }

  /**
   * Dispose all materials and textures
   *
   * Warning: This will dispose all cached materials and textures.
   * Only call when shutting down or switching worlds.
   */
  dispose(): void {
    // Dispose all materials
    for (const material of this.materials.values()) {
      // Skip atlas material if still using TextureAtlas
      if (material.name !== 'atlasMaterial') {
        material.dispose();
      }
    }

    // Dispose all textures
    for (const texture of this.textures.values()) {
      texture.dispose();
    }

    this.materials.clear();
    this.textures.clear();

    logger.debug('Materials and textures disposed');
  }

  /**
   * Get cache statistics
   *
   * @returns Object with cache statistics
   */
  getCacheStats(): { materials: number; textures: number } {
    return {
      materials: this.materials.size,
      textures: this.textures.size,
    };
  }
}
