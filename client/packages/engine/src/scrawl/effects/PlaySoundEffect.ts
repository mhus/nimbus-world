/**
 * PlaySoundEffect - Plays a sound once (one-shot)
 *
 * This is a non-steady effect that plays a sound once and automatically ends when playback finishes.
 * Supports both 2D (non-spatial) and 3D (spatial) sound playback.
 */

import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import { Vector3, CreateSoundAsync } from '@babylonjs/core';

const logger = getLogger('PlaySoundEffect');

/**
 * Options for PlaySoundEffect
 */
export interface PlaySoundOptions {
  /** Path to the sound asset (e.g., "audio/effects/explosion.ogg") */
  soundClip: string;

  /** Volume (0.0 - 1.0, default: 1.0) */
  volume?: number;

  /** Optional 3D position for spatial sound */
  position?: { x: number; y: number; z: number };

  /** Whether to stream the sound (default: false) */
  stream?: boolean;
}

/**
 * PlaySoundEffect - Plays a sound once and automatically ends
 *
 * Usage examples:
 *
 * 2D one-shot sound:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "playSound",
 *   "ctx": {
 *     "soundClip": "audio/effects/explosion.ogg",
 *     "volume": 1.0
 *   }
 * }
 * ```
 *
 * 3D spatial one-shot sound:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "playSound",
 *   "ctx": {
 *     "soundClip": "audio/effects/door_open.ogg",
 *     "volume": 0.8,
 *     "position": {"x": 100, "y": 65, "z": 200}
 *   }
 * }
 * ```
 */
export class PlaySoundEffect extends ScrawlEffectHandler<PlaySoundOptions> {
  private sound: any | null = null;
  private hasEnded: boolean = false;

  isSteadyEffect(): boolean {
    return false; // One-shot effect, not steady
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const audioService = ctx.appContext.services.audio;
    if (!audioService) {
      logger.warn('AudioService not available');
      return;
    }

    const soundClip = this.options.soundClip;
    const volume = this.options.volume ?? 1.0;
    const stream = this.options.stream ?? false;
    const position = this.options.position;

    // Validate sound clip path
    if (!soundClip || soundClip.trim() === '') {
      logger.warn('Sound clip path is empty');
      return;
    }

    // Validate volume
    if (volume < 0 || volume > 1) {
      logger.warn('Invalid volume, clamping to 0-1 range', { volume });
    }

    try {
      // 3D spatial sound
      if (position) {
        logger.debug('Playing 3D spatial one-shot sound', {
          soundClip,
          volume,
          position,
          stream
        });

        // Use AudioService.playSoundAtPosition() which handles pooling
        await audioService.playSoundAtPosition(
          soundClip,
          position.x,
          position.y,
          position.z,
          Math.max(0, Math.min(1, volume))
        );

        // Sound is automatically released via pool system after playback
        logger.debug('3D spatial one-shot sound playing', { soundClip });

        // Mark as ended (pool handles cleanup automatically)
        this.hasEnded = true;
      } else {
        // 2D non-spatial sound
        logger.debug('Playing 2D one-shot sound', {
          soundClip,
          volume,
          stream
        });

        // Create a new sound instance (not cached) so we can safely dispose it later
        const networkService = ctx.appContext.services.network;
        if (!networkService) {
          logger.warn('NetworkService not available');
          return;
        }

        const audioUrl = networkService.getAssetUrl(soundClip);
        this.sound = await CreateSoundAsync(soundClip, audioUrl);

        if (this.sound) {
          // Configure sound
          this.sound.volume = Math.max(0, Math.min(1, volume));
          this.sound.loop = false; // One-shot, no loop

          // Register onEnded callback to mark as ended
          // Note: We do NOT dispose here because Babylon.js already handles cleanup internally
          if (this.sound.onEndedObservable) {
            this.sound.onEndedObservable.addOnce(() => {
              logger.debug('Sound playback ended naturally', { soundClip });
              this.hasEnded = true;
              this.sound = null; // Clear reference, but don't dispose (already done by Babylon.js)
            });
          } else {
            // Fallback: assume ended after 10 seconds max
            logger.warn('onEndedObservable not available, using timeout fallback');
            setTimeout(() => {
              logger.debug('Sound playback timeout reached', { soundClip });
              this.hasEnded = true;
              this.cleanup();
            }, 10000); // 10 second timeout
          }

          // Start playback
          this.sound.play();
          logger.debug('2D one-shot sound started', { soundClip });
        }
      }

      if (!this.sound && !position) {
        logger.warn('Failed to create one-shot sound', { soundClip });
      }
    } catch (error) {
      logger.error('Failed to execute PlaySoundEffect', {
        soundClip,
        error: (error as Error).message
      });
      this.hasEnded = true;
    }
  }

  stop(): void {
    this.cleanup();
  }

  private cleanup(): void {
    if (this.sound) {
      const sound = this.sound;
      this.sound = null; // Clear reference first to prevent double-cleanup

      try {
        // If sound ended naturally (onEndedObservable fired), do NOT stop/dispose
        // Babylon.js already cleaned up internally and calling dispose() causes "Disconnect failed"
        if (this.hasEnded) {
          logger.debug('One-shot sound already ended naturally, skipping cleanup', {
            soundClip: this.options.soundClip
          });
        } else {
          // Sound was stopped manually before ending, need to stop and dispose
          if (typeof sound.stop === 'function') {
            sound.stop();
          }

          if (typeof sound.dispose === 'function') {
            sound.dispose();
          }

          logger.debug('One-shot sound stopped and disposed manually', {
            soundClip: this.options.soundClip
          });
        }
      } catch (error) {
        logger.warn('Failed to cleanup one-shot sound', {
          soundClip: this.options.soundClip,
          error: (error as Error).message
        });
      }
    }

    this.hasEnded = true;
  }

  isRunning(): boolean {
    // Effect is running until sound has ended
    return !this.hasEnded;
  }
}
