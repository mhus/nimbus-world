/**
 * PlaySoundLoopEffect - Plays a sound in a loop until the effect is stopped
 *
 * This is a steady effect that continuously plays a sound until stop() is called.
 * Supports both 2D (non-spatial) and 3D (spatial) sound playback.
 */

import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import { Vector3, CreateSoundAsync } from '@babylonjs/core';

const logger = getLogger('PlaySoundLoopEffect');

/**
 * Options for PlaySoundLoopEffect
 */
export interface PlaySoundLoopOptions {
  /** Path to the sound asset (e.g., "audio/ambient/forest.ogg") */
  soundClip: string;

  /** Volume (0.0 - 1.0, default: 1.0) */
  volume?: number;

  /** Optional 3D position for spatial sound */
  position?: { x: number; y: number; z: number };

  /** Whether to stream the sound (default: false) */
  stream?: boolean;
}

/**
 * PlaySoundLoopEffect - Plays a looping sound until stopped
 *
 * Usage examples:
 *
 * 2D looping sound:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "playSoundLoop",
 *   "ctx": {
 *     "soundClip": "audio/ambient/forest.ogg",
 *     "volume": 0.8,
 *     "stream": true
 *   }
 * }
 * ```
 *
 * 3D spatial looping sound:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "playSoundLoop",
 *   "ctx": {
 *     "soundClip": "audio/ambient/waterfall.ogg",
 *     "volume": 1.0,
 *     "position": {"x": 100, "y": 65, "z": 200},
 *     "stream": true
 *   }
 * }
 * ```
 */
export class PlaySoundLoopEffect extends ScrawlEffectHandler<PlaySoundLoopOptions> {
  private sound: any | null = null;

  isSteadyEffect(): boolean {
    return true; // This is a steady/endless effect
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
        logger.debug('Creating 3D spatial looping sound', {
          soundClip,
          volume,
          position,
          stream
        });

        // Use AudioService to create a permanent spatial sound
        // This returns a deferred wrapper that handles audio unlock
        this.sound = await audioService.createPermanentSoundForBlock(
          {
            block: {
              position: {
                x: position.x,
                y: position.y,
                z: position.z
              }
            },
            currentModifier: {}
          } as any, // Fake ClientBlock for position
          {
            path: soundClip,
            volume: Math.max(0, Math.min(1, volume)),
            loop: true,
            enabled: true,
            type: 'ambient' as any,
            maxDistance: 15 // Default max distance for spatial sounds
          }
        );

        if (this.sound) {
          // Sound will auto-play via createPermanentSoundForBlock
          logger.debug('3D spatial looping sound created', { soundClip });
        }
      } else {
        // 2D non-spatial sound
        logger.debug('Creating 2D looping sound', {
          soundClip,
          volume,
          stream
        });

        // Create a new sound instance (not cached) so we can safely dispose it later
        // Using CreateSoundAsync directly instead of AudioService.loadAudio() to avoid cache
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
          this.sound.loop = true;

          // Start playback
          this.sound.play();
          logger.debug('2D looping sound started', { soundClip });
        }
      }

      if (!this.sound) {
        logger.warn('Failed to create looping sound', { soundClip });
      }
    } catch (error) {
      logger.error('Failed to execute PlaySoundLoopEffect', {
        soundClip,
        error: (error as Error).message
      });
    }
  }

  stop(): void {
    if (this.sound) {
      const sound = this.sound;
      this.sound = null; // Clear reference first to prevent double-cleanup

      try {
        // Stop playback
        if (typeof sound.stop === 'function') {
          sound.stop();
        }

        // Dispose sound
        if (typeof sound.dispose === 'function') {
          sound.dispose();
        }

        logger.debug('Looping sound stopped and disposed', {
          soundClip: this.options.soundClip
        });
      } catch (error) {
        logger.warn('Failed to stop looping sound', {
          soundClip: this.options.soundClip,
          error: (error as Error).message
        });
      }
    }
  }

  isRunning(): boolean {
    return this.sound !== null;
  }
}
