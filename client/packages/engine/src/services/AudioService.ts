/**
 * AudioService - Manages audio loading, caching, and playback
 *
 * Responsible for:
 * - Loading audio files from the server
 * - Caching Babylon.js Sound objects
 * - Managing audio playback
 * - Handling gameplay sound playback (step sounds, swim sounds, etc.)
 */

import { getLogger, type AudioDefinition, type ClientEntity, AudioType } from '@nimbus/shared';
import {IDisposable, Vector3} from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import { Scene, Sound, Engine, CreateAudioEngineAsync, CreateSoundAsync } from '@babylonjs/core';
import type { AudioEngine, StaticSound } from '@babylonjs/core';
import type { NetworkService } from './NetworkService';
import type { PhysicsService } from './PhysicsService';
import type { ClientBlock } from '../types/ClientBlock';
import { loadAudioUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('AudioService');

// Constants for spatial audio
const DEFAULT_MAX_DISTANCE = 15;
const DEFAULT_INITIAL_POOL_SIZE = 1;
const STEP_SOUND_INITIAL_POOL_SIZE = 3;
const POOL_MAX_SIZE = 10; // Maximum pool size per sound

/**
 * Step over event data
 */
interface StepOverEvent {
  entityId: string;
  block: ClientBlock; // For swim mode: contains position but no audioSteps
  movementType: string;
}

/**
 * Collision with block event data
 */
interface CollideWithBlockEvent {
  entityId: string;
  block: ClientBlock;
  x: number;
  y: number;
  z: number;
}

/**
 * Audio cache entry (legacy, for non-spatial sounds)
 */
interface AudioCacheEntry {
  /** Babylon.js Sound object (Sound or StaticSound) */
  sound: any; // Can be Sound or StaticSound
  /** Asset path */
  path: string;
  /** Load timestamp */
  loadedAt: number;
  /** Is sound ready to play (set in ready callback) */
  isReady: boolean;
}

/**
 * Deferred sound wrapper interface - compatible with IDisposable
 */
interface DeferredSoundWrapper extends IDisposable {
  _sound: any;
  _disposed: boolean;
  _playPending: boolean;
  _config?: {
    name: string;
    url: string;
    scene: Scene;
    position: Vector3;
    volume: number;
    loop: boolean;
    maxDistance: number;
  };
  _createSound?: () => Promise<void>;
  _needsRecreate?: boolean;
  play(): void | Promise<void>;
  stop(): void;
  setPosition?(position: any): void;
  setVolume?(volume: number): void;
}

/**
 * AudioPoolItem - Manages a single sound instance in the pool
 * Handles blocking, spatial configuration, and auto-release
 */
class AudioPoolItem {
  public sound: any; // StaticSound
  public inUse: boolean = false;
  public blockedAt: number = 0; // Timestamp when blocked
  public lastUsed: number = 0; // Timestamp when last used

  constructor(sound: any) {
    this.sound = sound;
  }

  /**
   * Blocks the instance for playback
   * Sets spatial parameters and registers onEndedObservable for auto-release
   * @param position 3D position for spatial sound
   * @param maxDistance Maximum hearing distance
   * @param onReleaseCallback Callback when released
   */
  public block(
    position: Vector3,
    maxDistance: number,
    onReleaseCallback: () => void
  ): void {
    this.inUse = true;
    this.blockedAt = Date.now();

    // Spatial configuration
    this.configureSpatial(position, maxDistance);

    // Auto-release via onEndedObservable (Babylon.js Observable pattern)
    if (this.sound.onEndedObservable) {
      this.sound.onEndedObservable.addOnce(() => {
        logger.debug('onEndedObservable fired, releasing item');
        this.release();
        onReleaseCallback();
      });
      logger.debug('onEndedObservable registered');
    } else {
      // Fallback: release after 1 second if Observable not available
      logger.warn('onEndedObservable not available, using timeout fallback');
      setTimeout(() => {
        logger.debug('Timeout fallback fired, releasing item');
        this.release();
        onReleaseCallback();
      }, 1000);
    }
  }

  /**
   * Releases the instance back to the pool (available)
   */
  public release(): void {
    this.inUse = false;
    this.lastUsed = Date.now();
    this.blockedAt = 0;
  }

  /**
   * Configures spatial audio parameters
   * StaticSound uses sound.spatial object for configuration
   * Omnidirectional sound (equal in all directions from position)
   */
  private configureSpatial(position: Vector3, maxDistance: number): void {
    // StaticSound has a spatial property
    if (this.sound.spatial) {
      this.sound.spatial.position = position;
      this.sound.spatial.maxDistance = maxDistance;
      this.sound.spatial.distanceModel = 'linear';
      this.sound.spatial.refDistance = 1;
      this.sound.spatial.rolloffFactor = 1;

      // Omnidirectional sound (no cone, equal in all directions)
      this.sound.spatial.coneInnerAngle = 2 * Math.PI; // 360 degrees
      this.sound.spatial.coneOuterAngle = 2 * Math.PI; // 360 degrees
      this.sound.spatial.coneOuterGain = 1.0; // Full volume in all directions

      logger.debug('Spatial audio configured (StaticSound API)', {
        position: { x: position.x, y: position.y, z: position.z },
        maxDistance,
        distanceModel: 'linear',
        omnidirectional: true
      });
    } else {
      logger.warn('Spatial audio not configured (StaticSound API not available)');
      // Fallback for regular Sound (if used)
      this.sound.spatialSound = true;
      this.sound.distanceModel = 'linear';
      this.sound.maxDistance = maxDistance;
      this.sound.refDistance = 1;
      this.sound.rolloffFactor = 1;

      if (typeof this.sound.setPosition === 'function') {
        this.sound.setPosition(position);
      }

      logger.debug('Spatial audio configured (legacy Sound API)', {
        position: { x: position.x, y: position.y, z: position.z },
        maxDistance
      });
    }
  }

  /**
   * Sets volume and starts playback
   */
  public play(volume: number): void {
    this.sound.volume = volume;

    try {
      this.sound.play();
    } catch (error) {
      logger.warn('Failed to play sound', { error: (error as Error).message });
      this.release(); // Release on error
    }
  }

  /**
   * Checks if instance is available
   */
  public isAvailable(): boolean {
    return !this.inUse;
  }

  /**
   * Cleanup - dispose sound
   */
  public dispose(): void {
    this.sound?.dispose();
  }
}

/**
 * AudioPool - Manages pool of AudioPoolItems for a sound path
 */
class AudioPool {
  public path: string;
  public audioUrl: string; // URL for creating new instances
  public items: AudioPoolItem[] = [];
  public loadedAt: number;

  constructor(path: string, audioUrl: string, initialSounds: any[]) {
    this.path = path;
    this.audioUrl = audioUrl;
    this.loadedAt = Date.now();

    // Add initial sounds to pool
    for (const sound of initialSounds) {
      this.items.push(new AudioPoolItem(sound));
    }

    logger.debug('AudioPool created', { path, initialSize: initialSounds.length });
  }

  /**
   * Gets available item from pool or creates new via CreateSoundAsync
   * Returns null if pool is at max capacity and no items are available
   */
  public async getAvailableItem(): Promise<AudioPoolItem | null> {
    // Find free item
    let item = this.items.find(item => item.isAvailable());

    // No free item → check if we can grow pool
    if (!item) {
      // Check max pool size
      if (this.items.length >= POOL_MAX_SIZE) {
        // Before giving up, check for stuck items (blocked >1 second)
        const now = Date.now();
        const stuckItems = this.items.filter(
          item => item.inUse && item.blockedAt > 0 && (now - item.blockedAt) > 1000
        );

        if (stuckItems.length > 0) {
          logger.debug('Found stuck items in pool, releasing them', { // TODO should be warn and not happen
            path: this.path,
            stuckCount: stuckItems.length,
            totalItems: this.items.length
          });

          // Force release stuck items
          stuckItems.forEach(stuckItem => {
            stuckItem.release();
          });

          // Try to find free item again
          item = this.items.find(item => item.isAvailable());

          if (item) {
            logger.debug('Recovered stuck item from pool', { path: this.path }); // TODO should not happen
            return item;
          }
        }

        // Still no free item after cleanup
        logger.warn('Pool at maximum capacity, sound skipped', {
          path: this.path,
          maxSize: POOL_MAX_SIZE,
          available: this.getAvailableCount()
        });
        return null; // Pool is full, skip this sound
      }

      // Grow pool
      logger.debug('Pool full, creating new instance', { path: this.path });
      const blobUrl = await loadAudioUrlWithCredentials(this.audioUrl);
      const newSound = await CreateSoundAsync(this.path, blobUrl);
      item = new AudioPoolItem(newSound);
      this.items.push(item);
      logger.debug('Pool grown', { path: this.path, newSize: this.items.length });
    }

    return item;
  }

  /**
   * Returns number of available items
   */
  public getAvailableCount(): number {
    return this.items.filter(item => item.isAvailable()).length;
  }

  /**
   * Cleanup - dispose all items
   */
  public dispose(): void {
    this.items.forEach(item => item.dispose());
    this.items = [];
  }
}

/**
 * AudioService - Manages audio resources
 *
 * Loads audio files as Babylon.js Sound objects and caches them for reuse.
 * Integrates with NetworkService to fetch audio assets.
 */
export class AudioService implements IDisposable {
  private audioCache: Map<string, AudioCacheEntry> = new Map(); // Legacy cache for non-spatial sounds
  private soundPools: Map<string, AudioPool> = new Map(); // Pool system for spatial sounds
  private loadingPromises: Map<string, Promise<void>> = new Map(); // Cache for in-progress loading operations
  private scene?: Scene;
  private networkService?: NetworkService;
  private physicsService?: PhysicsService;
  private audioEnabled: boolean = true;
  private audioEngine?: any; // AudioEngineV2
  private stepVolume: number = 1.0; // Default step sound volume multiplier
  private ambientVolume: number = 0.5; // Default ambient music volume multiplier
  private speechVolume: number = 1.0; // Default speech volume multiplier

  // Track last swim sound time per entity to prevent overlapping
  private lastSwimSoundTime: Map<string, number> = new Map();

  // Ambient music
  private currentAmbientSound?: any; // Current ambient music sound
  private currentAmbientPath?: string; // Current ambient music path
  private ambientFadeInterval?: number; // Fade in/out interval ID
  private pendingAmbientPath?: string; // Pending ambient music path (waiting for engine ready)
  private pendingAmbientVolume?: number; // Pending ambient music volume

  // Speech/narration
  private currentSpeech?: any; // Current speech sound
  private currentSpeechPath?: string; // Current speech stream path

  // Pending sounds waiting for audio unlock
  private pendingSounds: Array<{ sound: any; wrapper: any }> = [];

  // Flag to track if unlock handler is registered
  private unlockHandlerRegistered: boolean = false;

  constructor(private appContext: AppContext) {
    logger.debug('AudioService created');
  }

  /**
   * Enable or disable audio playback
   * Audio files are still loaded/cached but not played when disabled
   */
  setAudioEnabled(enabled: boolean): void {
    this.audioEnabled = enabled;
    logger.debug('Audio playback ' + (enabled ? 'enabled' : 'disabled'));

    // Stop all playing audio when disabling
    if (!enabled) {
      this.audioCache.forEach(entry => {
        // StaticSound has stop() but not isPlaying, so just call stop()
        entry.sound.stop();
      });
    }
  }

  /**
   * Get current audio enabled state
   */
  isAudioEnabled(): boolean {
    return this.audioEnabled;
  }

  /**
   * Set step sound volume multiplier
   * @param volume Volume multiplier (0.0 = silent, 1.0 = full volume)
   */
  setStepVolume(volume: number): void {
    this.stepVolume = Math.max(0, Math.min(1, volume)); // Clamp between 0 and 1
    logger.debug('Step volume set to ' + this.stepVolume);
  }

  /**
   * Get current step sound volume multiplier
   */
  getStepVolume(): number {
    return this.stepVolume;
  }

  /**
   * Set ambient music volume multiplier
   * @param volume Volume multiplier (0.0 = silent, 1.0 = full volume)
   */
  setAmbientVolume(volume: number): void {
    this.ambientVolume = Math.max(0, Math.min(1, volume)); // Clamp between 0 and 1
    logger.debug('Ambient volume set to ' + this.ambientVolume);

    // Update current ambient sound volume if playing
    if (this.currentAmbientSound && this.ambientVolume > 0) {
      this.currentAmbientSound.volume = this.ambientVolume;
    } else if (this.currentAmbientSound && this.ambientVolume <= 0) {
      // Stop ambient if volume is 0 or below
      this.stopAmbientSound();
    }
  }

  /**
   * Get current ambient music volume multiplier
   */
  getAmbientVolume(): number {
    return this.ambientVolume;
  }

  /**
   * Set speech volume multiplier
   * @param volume Volume multiplier (0.0 = silent, 1.0 = full volume)
   */
  setSpeechVolume(volume: number): void {
    this.speechVolume = Math.max(0, Math.min(1, volume)); // Clamp between 0 and 1
    logger.debug('Speech volume set to ' + this.speechVolume);

    // Update current speech volume if playing
    if (this.currentSpeech) {
      this.currentSpeech.volume = this.speechVolume;
    }
  }

  /**
   * Get current speech volume multiplier
   */
  getSpeechVolume(): number {
    return this.speechVolume;
  }

  /**
   * Initialize audio service with scene
   * Must be called after scene is created
   * Also subscribes to PhysicsService events for gameplay sounds
   */
  async initialize(scene: Scene): Promise<void> {
    this.scene = scene;
    this.networkService = this.appContext.services.network;
    this.physicsService = this.appContext.services.physics;

    if (!this.networkService) {
      logger.error('NetworkService not available in AppContext');
      return;
    }

    // Create audio engine using async API
    try {
      this.audioEngine = await CreateAudioEngineAsync();

      logger.debug('Audio engine created', {
        hasEngine: !!this.audioEngine,
        unlocked: this.audioEngine?.unlocked,
        hasListener: !!this.audioEngine?.listener
      });

      // Attach audio listener to active camera for spatial audio (Babylon.js 8.x)
      if (this.audioEngine && this.audioEngine.listener && scene.activeCamera) {
        try {
          const listener = this.audioEngine.listener as any;

          // Babylon.js 8.x: audioEngine.listener.spatial.attach(camera)
          // 'spatial' might be a getter, so try accessing it
          if (listener.spatial) {
            logger.debug('Listener has spatial property', {
              spatialKeys: Object.keys(listener.spatial)
            });

            if (typeof listener.spatial.attach === 'function') {
              listener.spatial.attach(scene.activeCamera);
              logger.debug('Audio listener attached to camera via spatial.attach()', {
                cameraName: scene.activeCamera.name
              });
            } else {
              logger.debug('spatial.attach is not a function', {
                spatialType: typeof listener.spatial.attach
              });
            }
          } else {
            logger.debug('Listener has no spatial property - checking alternatives');

            // Try direct attach
            if (typeof listener.attach === 'function') {
              listener.attach(scene.activeCamera);
              logger.debug('Audio listener attached via direct attach()');
            } else {
              logger.error('No attach method found on listener');
            }
          }
        } catch (error) {
          logger.error('Failed to attach audio listener', {}, error as Error);
        }
      } else {
        logger.warn('Cannot attach audio listener', {
          hasEngine: !!this.audioEngine,
          hasListener: !!this.audioEngine?.listener,
          hasCamera: !!scene.activeCamera
        });
      }

      // Unlock audio engine (waits for user interaction if needed)
      if (this.audioEngine && !this.audioEngine.unlocked) {
        logger.debug('Audio engine locked - waiting for user interaction');

        // Register unlock handler only once
        if (!this.unlockHandlerRegistered) {
          this.unlockHandlerRegistered = true;

          // Set up click/key event listeners to unlock audio
          const unlockHandler = async () => {
            try {
              if (this.audioEngine && !this.audioEngine.unlocked) {
                await this.audioEngine.unlockAsync();
                logger.debug('Audio engine unlocked and ready via user interaction');
                this.playPendingSounds();
                this.playPendingAmbientMusic();

                // Remove event listeners after unlock
                window.removeEventListener('click', unlockHandler);
                window.removeEventListener('keydown', unlockHandler);
                window.removeEventListener('touchstart', unlockHandler);
              }
            } catch (error) {
              logger.error('Failed to unlock audio engine', {}, error as Error);
            }
          };

          // Listen for user interaction
          window.addEventListener('click', unlockHandler);
          window.addEventListener('keydown', unlockHandler);
          window.addEventListener('touchstart', unlockHandler);

          logger.debug('Audio unlock listeners registered (click, keydown, touchstart)');
        }
      } else if (this.audioEngine) {
        logger.debug('Audio engine ready');
        // Already unlocked - play any pending sounds immediately
        this.playPendingSounds();
        this.playPendingAmbientMusic();
      }
    } catch (error) {
      logger.error('Failed to create audio engine', {}, error as Error);
    }

    // Subscribe to PhysicsService events for gameplay sounds
    if (this.physicsService) {
      this.physicsService.on('step:over', (event: StepOverEvent) => {
        this.onStepOver(event);
      });
      this.physicsService.on('collide:withBlock', (event: CollideWithBlockEvent) => {
        this.onCollideWithBlock(event);
      });
      logger.debug('AudioService subscribed to PhysicsService events');
    } else {
      logger.warn('PhysicsService not available - gameplay sounds will not work');
    }

    logger.debug('AudioService initialized with scene');
  }

  /**
   * Load sound into pool with initial pool size (forecast)
   * @param path Audio asset path
   * @param initialPoolSize Initial number of instances (default: 1)
   */
  async loadSoundIntoPool(path: string, initialPoolSize = DEFAULT_INITIAL_POOL_SIZE): Promise<void> {
    // Already loaded?
    if (this.soundPools.has(path)) {
      logger.debug('Sound already in pool', { path });
      return;
    }

    // Already loading? Wait for existing promise to prevent race condition
    const existingPromise = this.loadingPromises.get(path);
    if (existingPromise) {
      logger.debug('Sound already loading, waiting for completion', { path });
      await existingPromise;
      return;
    }

    if (!this.networkService) {
      logger.error('NetworkService not available - cannot load sound into pool', { path });
      return;
    }

    if (!this.scene) {
      logger.error('Scene not initialized - cannot load sound into pool', { path });
      return;
    }

    // Store reference for use in async IIFE (TypeScript null-check)
    const networkService = this.networkService;

    // Create loading promise and cache it
    const loadingPromise = (async () => {
      try {
        // Get audio URL with cache-busting timestamp
        const baseUrl = networkService.getAssetUrl(path);
        const timestamp = Date.now();
        const audioUrl = `${baseUrl}?t=${timestamp}`;
        logger.debug('Loading sound into pool', { path, audioUrl, initialPoolSize });

        // Load audio URL with credentials (once for all instances)
        const blobUrl = await loadAudioUrlWithCredentials(audioUrl);

        // Load initial sound instances
        const initialSounds: any[] = [];
        for (let i = 0; i < initialPoolSize; i++) {
          try {
            const sound = await CreateSoundAsync(path, blobUrl);
            initialSounds.push(sound);
          } catch (soundError) {
            // Decoding failed - try one more time with a fresh fetch (cache-busting)
            logger.info('Audio decode failed, retrying with cache-busting', { path });
            try {
              const retryTimestamp = Date.now();
              const retryUrl = `${audioUrl}?retry=${retryTimestamp}`;
              const retryBlobUrl = await loadAudioUrlWithCredentials(retryUrl);
              const retrySound = await CreateSoundAsync(path, retryBlobUrl);

              logger.info('Retry successful', { path });
              initialSounds.push(retrySound);
              // Continue with next instance
              continue;
            } catch (retryError) {
              logger.error('Failed to load sound after retry', {
                path,
                audioUrl,
                error: (soundError as Error).message,
                retryError: (retryError as Error).message
              }, soundError as Error);
              throw soundError;
            }
          }
        }

        // Create AudioPool with pre-loaded sounds
        const pool = new AudioPool(path, audioUrl, initialSounds);
        this.soundPools.set(path, pool);

        logger.debug('Sound loaded into pool', { path, initialPoolSize });
      } catch (error) {
        const audioUrl = networkService.getAssetUrl(path);
        logger.error('Failed to load sound into pool', {
          path,
          audioUrl,
          error: (error as Error).message,
          stack: (error as Error).stack
        });
        // Re-throw to let callers handle the error
        throw error;
      } finally {
        // Remove from loading cache when done (success or error)
        this.loadingPromises.delete(path);
      }
    })();

    // Store promise in cache
    this.loadingPromises.set(path, loadingPromise);

    // Wait for completion
    await loadingPromise;
  }

  /**
   * Get blocked AudioPoolItem from pool
   * @param path Audio asset path
   * @param position 3D position for spatial sound
   * @param maxDistance Maximum hearing distance
   * @returns AudioPoolItem or null if failed
   */
  async getBlockedSoundFromPool(
    path: string,
    position: Vector3,
    maxDistance: number
  ): Promise<AudioPoolItem | null> {
    // Pool doesn't exist → lazy load with default size
    if (!this.soundPools.has(path)) {
      logger.debug('Pool does not exist, loading sound', { path });
      await this.loadSoundIntoPool(path, DEFAULT_INITIAL_POOL_SIZE);

      // Check if pool was created successfully
      if (!this.soundPools.has(path)) {
        logger.warn('Failed to create pool after loading attempt', { path });
        return null;
      }
    }

    const pool = this.soundPools.get(path);
    if (!pool) {
      logger.error('Failed to get pool (should not happen)', { path });
      return null;
    }

    // Get available item from pool (async now)
    const item = await pool.getAvailableItem();

    // Pool at max capacity, no available items
    if (!item) {
      logger.debug('No available items in pool', {
        path,
        poolSize: pool.items.length,
        available: pool.getAvailableCount()
      });
      return null;
    }

    // Block item with onRelease callback
    const onReleaseCallback = () => {
      logger.debug('AudioPoolItem released', { path });
    };

    item.block(position, maxDistance, onReleaseCallback);

    return item;
  }

  /**
   * Load audio file and return Babylon.js Sound object
   * Uses cache if audio was previously loaded
   * NOTE: Legacy method for non-spatial sounds (UI, music, etc.)
   *
   * @param assetPath Path to audio asset (e.g., "audio/step/grass.ogg")
   * @param options Optional Babylon.js Sound options
   * @returns Sound object or null if loading failed
   */
  async loadAudio(
    assetPath: string,
    options?: {
      volume?: number;
      loop?: boolean;
      autoplay?: boolean;
      spatialSound?: boolean;
    }
  ): Promise<any> {
    if (!this.scene) {
      logger.error('Scene not initialized');
      return null;
    }

    if (!this.networkService) {
      logger.error('NetworkService not available');
      return null;
    }

    // Check cache
    const cached = this.audioCache.get(assetPath);
    if (cached) {
      logger.debug('Audio loaded from cache', { assetPath });

      // Apply options to cached sound
      if (options) {
        this.applySoundOptions(cached.sound, options);
      }

      return cached.sound;
    }

    // Load new audio
    try {
      const audioUrl = this.networkService.getAssetUrl(assetPath);

      logger.debug('Loading audio with credentials', { assetPath, audioUrl });

      // Load audio URL with credentials
      const blobUrl = await loadAudioUrlWithCredentials(audioUrl);

      // Create Babylon.js Sound object using async API
      const sound = await CreateSoundAsync(assetPath, blobUrl);

      // Apply options - StaticSound uses direct properties
      if (options?.loop !== undefined) {
        sound.loop = options.loop;
      }
      if (options?.volume !== undefined) {
        sound.volume = options.volume;
      }
      if (options?.autoplay) {
        sound.play();
      }

      logger.debug('Sound loaded', { assetPath });

      // Cache the sound immediately (it will load in background)
      this.audioCache.set(assetPath, {
        sound,
        path: assetPath,
        loadedAt: Date.now(),
        isReady: true, // Set to true - Babylon.js handles loading, we can call play() anytime
      });

      logger.debug('Audio cached', { assetPath });
      return sound;
    } catch (error) {
      logger.warn('Failed to load audio', { assetPath, error: (error as Error).message });
      return null;
    }
  }

  /**
   * Check if audio engine is unlocked and ready to play sounds
   * @returns true if audio engine is unlocked, false otherwise
   */
  isAudioUnlocked(): boolean {
    // Always return true if audio engine exists
    // Babylon.js handles audio unlock internally via unlockAsync()
    return !!this.audioEngine;
  }

  /**
   * Play pending ambient music after audio engine becomes ready
   */
  private async playPendingAmbientMusic(): Promise<void> {
    if (!this.pendingAmbientPath || this.pendingAmbientPath.trim() === '') {
      return; // No pending ambient music
    }

    logger.debug('Playing pending ambient music', {
      path: this.pendingAmbientPath,
      volume: this.pendingAmbientVolume
    });

    const path = this.pendingAmbientPath;
    const volume = this.pendingAmbientVolume ?? 1.0;

    // Clear pending before playing (to avoid loops)
    this.pendingAmbientPath = undefined;
    this.pendingAmbientVolume = undefined;

    // Play the ambient music
    await this.playAmbientSound(path, true, volume);
  }

  /**
   * Play all pending sounds after audio unlock
   */
  private async playPendingSounds(): Promise<void> {
    logger.debug('Playing pending sounds', { count: this.pendingSounds.length });

    const soundsToPlay = [...this.pendingSounds];
    this.pendingSounds = []; // Clear immediately to avoid duplicates

    for (const { sound, wrapper } of soundsToPlay) {
      if (!wrapper._disposed && wrapper._playPending) {
        try {
          // Create sound if needed (for deferred wrappers) - now async
          if (wrapper._createSound && !wrapper._sound) {
            await wrapper._createSound();
            // Sound will auto-play after creation
          } else if (wrapper._sound) {
            // Sound already exists, play it
            wrapper._sound.play();
            logger.debug('Deferred sound played after audio unlock');
            wrapper._playPending = false;
          } else if (sound) {
            sound.play();
            logger.debug('Deferred sound played after audio unlock');
            wrapper._playPending = false;
          }
        } catch (error) {
          logger.warn('Failed to play pending sound', { error: (error as Error).message });
        }
      }
    }
  }

  /**
   * Wrapper for Sound that automatically plays when audio engine is unlocked
   * Stores sound configuration and creates/recreates the sound after unlock
   */
  private createDeferredSound(sound: any): DeferredSoundWrapper {
    const wrapper = {
      _sound: sound,
      _disposed: false,
      _playPending: false,
      _needsRecreate: false,

      play: () => {
        if (wrapper._disposed) return;

        if (this.isAudioUnlocked()) {
          // If sound was created before unlock, it needs to be recreated
          if (wrapper._needsRecreate) {
            logger.warn('Sound created before unlock - attempting to play anyway');
          }
          wrapper._sound.play();
          logger.debug('Deferred sound played immediately (audio already unlocked)');
        } else {
          wrapper._playPending = true;
          wrapper._needsRecreate = true; // Mark for recreation after unlock
          logger.debug('Deferred sound play pending (waiting for audio unlock)', {
            pendingCount: this.pendingSounds.length + 1
          });

          // Add to pending sounds list
          this.pendingSounds.push({ sound: wrapper._sound, wrapper });
        }
      },

      stop: () => {
        wrapper._playPending = false;
        if (!wrapper._disposed) {
          try {
            wrapper._sound.stop();
          } catch (error) {
            logger.warn('Failed to stop sound', { error: (error as Error).message });
          }
        }
      },

      dispose: () => {
        wrapper._disposed = true;
        wrapper._playPending = false;

        // Remove from pending sounds if present
        const index = this.pendingSounds.findIndex(p => p.wrapper === wrapper);
        if (index !== -1) {
          this.pendingSounds.splice(index, 1);
        }

        try {
          wrapper._sound.dispose();
        } catch (error) {
          logger.warn('Failed to dispose sound', { error: (error as Error).message });
        }
      },

      // Proxy other properties to the underlying sound
      setPosition: (position: any) => wrapper._sound.setPosition(position),
      setVolume: (volume: number) => wrapper._sound.setVolume(volume),
    };

    return wrapper;
  }

  /**
   * Creates a permanent (non-cached) spatial sound for a block
   * Used for ambient sounds that play continuously while the block is visible
   * Audio is streamed and looped automatically (Babylon.js handles streaming for large files)
   *
   * If audio engine is locked, returns a placeholder that will create the actual sound after unlock
   *
   * @param block Block to attach sound to
   * @param audioDef Audio definition with path, volume, loop, etc.
   * @returns Wrapper object that creates sound after audio unlock
   */
  async createPermanentSoundForBlock(block: ClientBlock, audioDef: AudioDefinition): Promise<DeferredSoundWrapper | null> {
    if (!this.scene) {
      logger.error('Scene not initialized');
      return null;
    }

    if (!this.networkService) {
      logger.error('NetworkService not available');
      return null;
    }

    const audioUrl = this.networkService.getAssetUrl(audioDef.path);
    const blockPos = block.block.position;
    const blockPosition = new Vector3(blockPos.x, blockPos.y, blockPos.z);

    // Create a deferred sound wrapper that will create the actual sound after unlock
    const deferredWrapper : DeferredSoundWrapper = {
      _sound: null as any,
      _disposed: false,
      _playPending: false,
      _config: {
        name: audioDef.path,
        url: audioUrl,
        scene: this.scene,
        position: blockPosition,
        volume: audioDef.volume,
        loop: audioDef.loop !== false,
        maxDistance: audioDef.maxDistance || DEFAULT_MAX_DISTANCE,
      },

      _createSound: async () => {
        if (deferredWrapper._sound || deferredWrapper._disposed || !deferredWrapper._config) return;

        logger.debug('Creating permanent sound for block', {
          path: audioDef.path,
          position: blockPosition,
          volume: audioDef.volume
        });

        // Load audio URL with credentials
        const blobUrl = await loadAudioUrlWithCredentials(deferredWrapper._config.url);

        // Create spatial sound using CreateSoundAsync (returns StaticSound)
        const sound = await CreateSoundAsync(
          deferredWrapper._config.name,
          blobUrl
        );

        if (deferredWrapper._disposed) {
          sound.dispose();
          return;
        }

        deferredWrapper._sound = sound;

        // Configure as spatial sound using StaticSound API
        if (sound.spatial) {
          sound.spatial.position = deferredWrapper._config.position;
          sound.spatial.maxDistance = deferredWrapper._config.maxDistance;
          sound.spatial.distanceModel = 'linear'; // Linear: volume reaches 0 at maxDistance
          sound.spatial.rolloffFactor = 1;
          // Omnidirectional
          sound.spatial.coneInnerAngle = 2 * Math.PI;
          sound.spatial.coneOuterAngle = 2 * Math.PI;

          logger.debug('Spatial audio configured', {
            position: deferredWrapper._config.position,
            maxDistance: deferredWrapper._config.maxDistance,
            distanceModel: 'linear'
          });
        }

        // Set volume and loop
        sound.volume = deferredWrapper._config.volume;
        sound.loop = deferredWrapper._config.loop;

        logger.debug('Permanent sound created and ready', {
          path: audioDef.path,
          position: deferredWrapper._config.position,
          volume: deferredWrapper._config.volume,
          loop: deferredWrapper._config.loop,
          maxDistance: deferredWrapper._config.maxDistance,
          hasSpatial: !!sound.spatial
        });

        // Auto-play permanent sounds (they should always play when loaded)
        if (!deferredWrapper._disposed) {
          sound.play();
          logger.debug('Auto-playing permanent sound after load', {
            path: audioDef.path
          });
          deferredWrapper._playPending = false;
        }
      },

      play: async () => {
        if (deferredWrapper._disposed) return;

        if (this.isAudioUnlocked()) {
          // Create sound if not created yet (async operation)
          if (deferredWrapper._createSound && !deferredWrapper._sound) {
            await deferredWrapper._createSound();
          }

          if (deferredWrapper._sound) {
            deferredWrapper._sound.play();
            logger.debug('Permanent sound playing (audio already unlocked)', { path: audioDef.path });
          }
        } else {
          deferredWrapper._playPending = true;
          logger.debug('Permanent sound play pending (will create after unlock)', {
            path: audioDef.path
          });

          // Add to pending sounds list
          this.pendingSounds.push({ sound: null, wrapper: deferredWrapper });
        }
      },

      stop: () => {
        deferredWrapper._playPending = false;
        if (deferredWrapper._sound && !deferredWrapper._disposed) {
          try {
            deferredWrapper._sound.stop();
          } catch (error) {
            logger.warn('Failed to stop sound', { error: (error as Error).message });
          }
        }
      },

      dispose: () => {
        deferredWrapper._disposed = true;
        deferredWrapper._playPending = false;

        // Remove from pending sounds
        const index = this.pendingSounds.findIndex(p => p.wrapper === deferredWrapper);
        if (index !== -1) {
          this.pendingSounds.splice(index, 1);
        }

        if (deferredWrapper._sound) {
          try {
            deferredWrapper._sound.dispose();
          } catch (error) {
            logger.warn('Failed to dispose sound', { error: (error as Error).message });
          }
        }
      },

      setPosition: (position: any) => {
        if (deferredWrapper._config) {
          deferredWrapper._config.position = position;
        }
        if (deferredWrapper._sound && deferredWrapper._sound.spatial) {
          deferredWrapper._sound.spatial.position = position;
        }
      },

      setVolume: (volume: number) => {
        if (deferredWrapper._config) {
          deferredWrapper._config.volume = volume;
        }
        if (deferredWrapper._sound) {
          deferredWrapper._sound.volume = volume;
        }
      },
    };

    return deferredWrapper;
  }

  /**
   * Play audio for an entity
   * Creates a one-shot spatial sound at the entity's position
   * Sound is automatically disposed after playback
   *
   * @param entity - The entity to play audio for
   * @param type - Audio type (e.g., 'attack', 'hurt', 'idle')
   */
  async playEntityAudio(entity: ClientEntity, type: string): Promise<void> {
    if (!this.scene) {
      logger.error('Scene not initialized');
      return;
    }

    if (!this.networkService) {
      logger.error('NetworkService not available');
      return;
    }

    // Get audio definitions from entity modifier (priority) or entity model (default)
    const audioDefinitions = entity.entity.modifier?.audio || entity.model.audio;
    if (!audioDefinitions || audioDefinitions.length === 0) {
      logger.warn('No audio definitions for entity', { entityId: entity.id });
      return;
    }

    // Find first enabled audio definition matching the type
    const audioDef = audioDefinitions.find(
      def => def.type === type && def.enabled
    );

    if (!audioDef) {
      logger.warn('No audio definition found for type', {
        entityId: entity.id,
        type,
        availableTypes: audioDefinitions.map(d => d.type)
      });
      return;
    }

    try {
      const entityPosition = new Vector3(
        entity.currentPosition.x,
        entity.currentPosition.y,
        entity.currentPosition.z
      );

      logger.debug('Playing entity audio', {
        entityId: entity.id,
        type,
        path: audioDef.path,
        position: entityPosition
      });

      // Use sound pool system (like step sounds) - handles lifecycle automatically
      const poolItem = await this.getBlockedSoundFromPool(
        audioDef.path,
        entityPosition,
        audioDef.maxDistance || DEFAULT_MAX_DISTANCE
      );

      if (!poolItem) {
        logger.warn('Failed to get sound from pool for entity audio', {
          entityId: entity.id,
          type,
          path: audioDef.path
        });
        return;
      }

      // Play the sound (will auto-release after playback via pool system)
      poolItem.play(audioDef.volume);

      logger.debug('Entity audio playing via pool', {
        entityId: entity.id,
        type,
        path: audioDef.path
      });
    } catch (error) {
      logger.warn('Failed to play entity audio', {
        entityId: entity.id,
        type,
        path: audioDef.path,
        error: (error as Error).message
      });
    }
  }

  /**
   * Apply options to existing Sound object
   * Works with both Sound and StaticSound
   */
  private applySoundOptions(sound: any, options: {
    volume?: number;
    loop?: boolean;
    autoplay?: boolean;
    spatialSound?: boolean;
  }): void {
    if (options.volume !== undefined) {
      // StaticSound uses .volume property, Sound uses setVolume()
      if (typeof sound.setVolume === 'function') {
        sound.setVolume(options.volume);
      } else {
        sound.volume = options.volume;
      }
    }
    if (options.loop !== undefined) {
      sound.loop = options.loop;
    }
    if (options.spatialSound !== undefined && 'spatialSound' in sound) {
      sound.spatialSound = options.spatialSound;
    }
    if (options.autoplay) {
      // StaticSound doesn't have isPlaying property
      const shouldPlay = !sound.isPlaying || sound.isPlaying === undefined;
      if (shouldPlay) {
        sound.play();
      }
    }
  }

  /**
   * Play audio by asset path
   * Loads audio if not already cached
   * Respects audioEnabled flag
   *
   * @param assetPath Path to audio asset
   * @param options Playback options
   * @returns Sound object or null if loading failed or audio disabled
   */
  async playAudio(
    assetPath: string,
    options?: {
      volume?: number;
      loop?: boolean;
    }
  ): Promise<any> {
    // Check if audio is enabled
    if (!this.audioEnabled) {
      logger.debug('Audio playback disabled, skipping', { assetPath });
      return null;
    }

    const sound = await this.loadAudio(assetPath, {
      ...options,
      autoplay: false, // Don't autoplay, we control it below
    });

    if (sound && !sound.isPlaying && this.audioEnabled) {
      sound.play();
    }

    return sound;
  }

  /**
   * Stop audio by asset path
   *
   * @param assetPath Path to audio asset
   */
  stopAudio(assetPath: string): void {
    const cached = this.audioCache.get(assetPath);
    if (cached && cached.sound.isPlaying) {
      cached.sound.stop();
      logger.debug('Audio stopped', { assetPath });
    }
  }

  /**
   * Stop all playing audio
   */
  stopAllAudio(): void {
    let stoppedCount = 0;
    this.audioCache.forEach((entry) => {
      if (entry.sound.isPlaying) {
        entry.sound.stop();
        stoppedCount++;
      }
    });

    if (stoppedCount > 0) {
      logger.debug('Stopped all audio', { count: stoppedCount });
    }
  }

  /**
   * Check if audio is ready to play
   * Uses cache entry flag instead of sound.isReady() for reliability
   */
  isAudioReady(assetPath: string): boolean {
    const cached = this.audioCache.get(assetPath);
    return cached?.isReady ?? false;
  }

  /**
   * Get cached audio count
   */
  getCacheSize(): number {
    return this.audioCache.size;
  }

  /**
   * Clear audio cache
   * Disposes all cached Sound objects
   */
  clearCache(): void {
    logger.debug('Clearing audio cache', { count: this.audioCache.size });

    this.audioCache.forEach((entry) => {
      entry.sound.dispose();
    });

    this.audioCache.clear();
  }

  /**
   * Dispose service and cleanup resources
   */
  dispose(): void {
    logger.debug('Disposing AudioService');

    // Stop ambient music
    this.stopAmbientSound();

    // Dispose legacy cache
    this.clearCache();

    // Dispose all sound pools
    this.soundPools.forEach(pool => pool.dispose());
    this.soundPools.clear();

    // Clear swim sound throttle
    this.lastSwimSoundTime.clear();
  }

  // ========================================
  // General Sound Playback Methods
  // ========================================

  /**
   * Play sound directly (non-spatial, non-looping)
   * Useful for UI sounds, notifications, or sounds that should play at player position
   * @param soundPath Path to sound file
   * @param stream Whether to stream the audio (default: false for small sounds)
   * @param volume Volume (0.0 - 1.0)
   */
  async playSound(
    soundPath: string,
    stream: boolean = false,
    volume: number = 1.0
  ): Promise<void> {
    // Validate volume
    if (volume < 0 || volume > 1) {
      logger.warn('Invalid volume, clamping to 0-1 range', { volume });
      volume = Math.max(0, Math.min(1, volume));
    }

    // Check if audio is enabled
    if (!this.audioEnabled) {
      logger.debug('Audio disabled, skipping playSound', { soundPath });
      return;
    }

    try {
      // Load sound (non-spatial, non-looping, one-shot)
      const sound = await this.loadAudio(soundPath, {
        volume,
        loop: false,
        autoplay: true, // Play immediately
        spatialSound: false, // Non-spatial (plays directly at listener)
      });

      if (!sound) {
        logger.warn('Failed to load sound', { soundPath });
        return;
      }

      logger.debug('Playing non-spatial sound', { soundPath, volume, stream });
    } catch (error) {
      logger.error('Failed to play sound', {
        soundPath,
        error: (error as Error).message
      });
    }
  }

  /**
   * Play sound at specific world position (spatial, non-looping)
   * @param soundPath Path to sound file
   * @param x World X coordinate
   * @param y World Y coordinate
   * @param z World Z coordinate
   * @param volume Volume (0.0 - 1.0)
   */
  async playSoundAtPosition(
    soundPath: string,
    x: number,
    y: number,
    z: number,
    volume: number = 1.0
  ): Promise<void> {
    // Validate volume
    if (volume < 0 || volume > 1) {
      logger.warn('Invalid volume, clamping to 0-1 range', { volume });
      volume = Math.max(0, Math.min(1, volume));
    }

    // Check if audio is enabled
    if (!this.audioEnabled) {
      logger.debug('Audio disabled, skipping playSoundAtPosition', { soundPath });
      return;
    }

    // Position
    const position = new Vector3(x, y, z);

    // Get blocked sound from pool
    const item = await this.getBlockedSoundFromPool(
      soundPath,
      position,
      DEFAULT_MAX_DISTANCE
    );

    if (!item) {
      logger.warn('Failed to get sound from pool for playSoundAtPosition', { soundPath });
      return;
    }

    // Play sound (no loop, one-shot)
    item.play(volume);

    logger.debug('Playing sound at position', {
      soundPath,
      position: { x, y, z },
      volume
    });
  }

  // ========================================
  // Ambient Music Methods
  // ========================================

  /**
   * Play ambient background music with fade in
   * @param soundPath Path to ambient music file (empty string stops ambient music)
   * @param stream Whether to stream the audio (default: true for large music files)
   * @param volume Volume (0.0 - 1.0), multiplied by ambientVolume
   */
  async playAmbientSound(soundPath: string, stream: boolean = true, volume: number = 1.0): Promise<void> {
    // Empty path → stop ambient music
    if (!soundPath || soundPath.trim() === '') {
      this.pendingAmbientPath = undefined;
      this.pendingAmbientVolume = undefined;
      await this.stopAmbientSound();
      return;
    }

    // Check if audio engine is ready
    if (!this.audioEngine) {
      logger.debug('Audio engine not ready, deferring ambient music', { soundPath, volume });
      this.pendingAmbientPath = soundPath;
      this.pendingAmbientVolume = volume;
      return;
    }

    // Check if ambientVolume is 0 or below → don't play
    if (this.ambientVolume <= 0) {
      logger.debug('Ambient volume is 0 or below, not playing ambient music', { soundPath });
      return;
    }

    // Stop current ambient music if playing different track
    if (this.currentAmbientSound && this.currentAmbientPath !== soundPath) {
      await this.stopAmbientSound();
    }

    // Already playing this track → don't restart
    if (this.currentAmbientPath === soundPath && this.currentAmbientSound) {
      logger.debug('Ambient music already playing', { soundPath });
      return;
    }

    // Clear pending (we're playing now)
    this.pendingAmbientPath = undefined;
    this.pendingAmbientVolume = undefined;

    try {
      logger.debug('Loading ambient music', { soundPath, stream, volume });

      // Load ambient music (non-spatial, looping)
      const sound = await this.loadAudio(soundPath, {
        volume: 0, // Start at 0 for fade in
        loop: true, // Always loop ambient music
        autoplay: false,
        spatialSound: false, // Ambient music is non-spatial
      });

      if (!sound) {
        logger.error('Failed to load ambient music', { soundPath });
        return;
      }

      this.currentAmbientSound = sound;
      this.currentAmbientPath = soundPath;

      // Start playing
      sound.play();

      // Fade in
      const targetVolume = volume * this.ambientVolume;
      await this.fadeSound(sound, 0, targetVolume, 2000); // 2 second fade in

      logger.debug('Ambient music playing', { soundPath, volume: targetVolume });
    } catch (error) {
      logger.error('Failed to play ambient music', { soundPath, error: (error as Error).message });
    }
  }

  /**
   * Stop ambient background music with fade out
   */
  async stopAmbientSound(): Promise<void> {
    if (!this.currentAmbientSound) {
      return; // No ambient music playing
    }

    logger.debug('Stopping ambient music', { path: this.currentAmbientPath });

    // Fade out
    const currentVolume = this.currentAmbientSound.volume;
    await this.fadeSound(this.currentAmbientSound, currentVolume, 0, 1000); // 1 second fade out

    // Stop and dispose
    this.currentAmbientSound.stop();
    this.currentAmbientSound = undefined;
    this.currentAmbientPath = undefined;

    logger.debug('Ambient music stopped');
  }

  /**
   * Fade sound volume from start to end over duration
   * @param sound Sound object
   * @param startVolume Starting volume (0.0 - 1.0)
   * @param endVolume Target volume (0.0 - 1.0)
   * @param duration Duration in milliseconds
   */
  private fadeSound(sound: any, startVolume: number, endVolume: number, duration: number): Promise<void> {
    return new Promise((resolve) => {
      const steps = 50; // Number of fade steps
      const stepDuration = duration / steps;
      const volumeStep = (endVolume - startVolume) / steps;

      let currentStep = 0;
      sound.volume = startVolume;

      // Clear any existing fade interval
      if (this.ambientFadeInterval) {
        clearInterval(this.ambientFadeInterval);
      }

      this.ambientFadeInterval = window.setInterval(() => {
        currentStep++;

        if (currentStep >= steps) {
          sound.volume = endVolume;
          clearInterval(this.ambientFadeInterval!);
          this.ambientFadeInterval = undefined;
          resolve();
        } else {
          sound.volume = startVolume + (volumeStep * currentStep);
        }
      }, stepDuration);
    });
  }

  // ========================================
  // Speech/Narration Methods
  // ========================================

  /**
   * Play speech/narration audio (streamed from server)
   * Only one speech can play at a time - new speech stops current
   * Returns promise that resolves when speech ends or is stopped
   *
   * @param streamPath Speech stream path (e.g., "welcome", "tutorial/intro")
   * @param volume Volume (0.0 - 1.0), multiplied by speechVolume
   * @returns Promise that resolves when speech ends
   */
  async speak(streamPath: string, volume: number = 1.0): Promise<void> {
    // Stop current speech if playing
    if (this.currentSpeech) {
      await this.stopSpeech();
    }

    // Check if speechVolume is 0 or below → don't play
    if (this.speechVolume <= 0) {
      logger.debug('Speech volume is 0 or below, not playing speech', { streamPath });
      return;
    }

    if (!this.networkService) {
      logger.error('NetworkService not available - cannot get speech URL');
      return;
    }

    try {
      // Get speech URL from NetworkService
      const speechUrl = this.networkService.getSpeechUrl(streamPath);
      logger.debug('Loading speech with credentials', { streamPath, speechUrl, volume });

      // Load speech URL with credentials
      const blobUrl = await loadAudioUrlWithCredentials(speechUrl);

      // Load speech (non-spatial, non-looping, streamed)
      const sound = await CreateSoundAsync(streamPath, blobUrl);

      if (!sound) {
        logger.error('Failed to load speech', { streamPath });
        return;
      }

      this.currentSpeech = sound;
      this.currentSpeechPath = streamPath;

      // Set volume
      const finalVolume = volume * this.speechVolume;
      sound.volume = finalVolume;

      // Return promise that resolves when speech ends
      return new Promise<void>((resolve) => {
        // Register onEnded callback
        if (sound.onEndedObservable) {
          sound.onEndedObservable.addOnce(() => {
            logger.debug('Speech ended', { streamPath });
            this.currentSpeech = undefined;
            this.currentSpeechPath = undefined;
            resolve();
          });
        } else {
          // Fallback: assume speech ended after 60 seconds max
          logger.warn('onEndedObservable not available for speech, using 60s timeout');
          setTimeout(() => {
            logger.debug('Speech timeout reached', { streamPath });
            this.currentSpeech = undefined;
            this.currentSpeechPath = undefined;
            resolve();
          }, 60000); // 60 second timeout
        }

        // Start playing
        sound.play();
        logger.debug('Speech playing', { streamPath, volume: finalVolume });
      });
    } catch (error) {
      logger.error('Failed to play speech', { streamPath, error: (error as Error).message });
      this.currentSpeech = undefined;
      this.currentSpeechPath = undefined;
      throw error;
    }
  }

  /**
   * Stop current speech playback
   */
  async stopSpeech(): Promise<void> {
    if (!this.currentSpeech) {
      return; // No speech playing
    }

    logger.debug('Stopping speech', { path: this.currentSpeechPath });

    // Stop immediately (no fade for speech)
    this.currentSpeech.stop();
    this.currentSpeech = undefined;
    this.currentSpeechPath = undefined;

    logger.debug('Speech stopped');
  }

  // ========================================
  // Gameplay Sound Methods (from SoundService)
  // ========================================

  /**
   * Handle step over event
   * Plays random step sound from block's audioSteps using pool system
   */
  /**
   * Handle collision with block event from PhysicsService
   * Plays random collision sound from block's audio definitions
   */
  private async onCollideWithBlock(event: CollideWithBlockEvent): Promise<void> {
    const { entityId, block, x, y, z } = event;

    // Check if audio is enabled
    if (!this.audioEnabled) {
      return;
    }

    // Get collision audio definitions from block
    const audioModifier = block.currentModifier.audio;
    if (!audioModifier || audioModifier.length === 0) {
      return; // No audio defined
    }

    // Filter for collision audio
    const collisionAudioDefs = audioModifier.filter(
      def => def.type === AudioType.COLLISION && def.enabled
    );

    if (collisionAudioDefs.length === 0) {
      return; // No collision audio
    }

    // Select random collision audio
    const randomIndex = Math.floor(Math.random() * collisionAudioDefs.length);
    const audioDef = collisionAudioDefs[randomIndex];

    // Get configuration
    const maxDistance = audioDef.maxDistance ?? DEFAULT_MAX_DISTANCE;
    const position = new Vector3(x, y, z);

    // Get blocked sound from pool (auto-released via onEndedObservable)
    const item = await this.getBlockedSoundFromPool(
      audioDef.path,
      position,
      maxDistance
    );

    if (!item) {
      logger.warn('Failed to get collision sound from pool', { path: audioDef.path });
      return;
    }

    // Play sound with defined volume
    const finalVolume = audioDef.volume;
    item.play(finalVolume);

    logger.debug('Collision audio played', {
      entityId,
      path: audioDef.path,
      position: { x, y, z },
      volume: finalVolume
    });
  }

  private async onStepOver(event: StepOverEvent): Promise<void> {
    const { entityId, block, movementType } = event;

    // Check if audio engine is ready
    if (!this.audioEngine) {
      return; // Audio engine not initialized
    }

    // Check if audio is enabled
    if (!this.audioEnabled) {
      return; // Audio disabled
    }

    // SWIM mode: play special swim sound
    if (movementType === 'swim') {
      await this.playSwimSound(entityId, block);
      return;
    }

    // Get step audio definitions from currentModifier
    const audioModifier = block.currentModifier.audio;
    if (!audioModifier || audioModifier.length === 0) {
      return; // No audio defined
    }

    // Filter for step audio
    const stepAudioDefs = audioModifier.filter(
      def => def.type === AudioType.STEPS && def.enabled
    );

    if (stepAudioDefs.length === 0) {
      return; // No step audio
    }

    // Select random step audio
    const randomIndex = Math.floor(Math.random() * stepAudioDefs.length);
    const audioDef = stepAudioDefs[randomIndex];

    // Get configuration
    const maxDistance = audioDef.maxDistance ?? DEFAULT_MAX_DISTANCE;
    const position = new Vector3(
      block.block.position.x,
      block.block.position.y,
      block.block.position.z
    );

    // Get blocked sound from pool (auto-released via onEndedObservable)
    const item = await this.getBlockedSoundFromPool(
      audioDef.path,
      position,
      maxDistance
    );

    if (!item) {
      logger.warn('Failed to get sound from pool', { path: audioDef.path });
      return;
    }

    // Volume calculation
    let volumeMultiplier = this.stepVolume;

    // CROUCH mode: reduce volume to 50%
    if (movementType === 'crouch') {
      volumeMultiplier *= 0.5;
    }

    const finalVolume = audioDef.volume * volumeMultiplier;

    // Play sound (automatically released via onended callback in AudioPoolItem)
    item.play(finalVolume);
  }

  /**
   * Play swim sound at player position using pool system
   * Prevents overlapping by checking if sound was played recently
   * Sound path is read from WorldInfo.settings.swimStepAudio
   */
  private async playSwimSound(entityId: string, block: ClientBlock): Promise<void> {
    // Get swim sound path from WorldInfo settings (optional)
    const settings = this.appContext.worldInfo?.settings as any;
    const swimSoundPath = settings?.swimStepAudio || 'audio/liquid/swim1.ogg'; // Fallback to default

    // Skip if no swim sound configured
    if (!swimSoundPath || swimSoundPath.trim() === '') {
      return;
    }

    // Throttling: Check if swim sound was played recently (prevent overlapping)
    // Swim sounds are typically 500-1000ms long, so wait at least 500ms
    const now = Date.now();
    const lastPlayTime = this.lastSwimSoundTime.get(entityId);
    if (lastPlayTime && now - lastPlayTime < 500) {
      return; // Still too soon
    }

    // Position
    const position = new Vector3(
      block.block.position.x,
      block.block.position.y,
      block.block.position.z
    );

    // Get blocked sound from pool
    const item = await this.getBlockedSoundFromPool(
      swimSoundPath,
      position,
      DEFAULT_MAX_DISTANCE
    );

    if (!item) {
      logger.warn('Failed to get swim sound from pool', { swimSoundPath });
      return;
    }

    // Volume
    const finalVolume = 1.0 * this.stepVolume; // Full volume for swim sounds

    // Update throttle timestamp
    this.lastSwimSoundTime.set(entityId, now);

    // Play (automatically released via onended callback)
    item.play(finalVolume);
  }
}
