import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';

const logger = getLogger('LoopingSoundEffect');

export interface LoopingSoundOptions {
  soundId: string;
  volume?: number;
}

/**
 * Looping sound effect for While/Until loops
 * Effect runs continuously until stop() is called
 */
export class LoopingSoundEffect extends ScrawlEffectHandler<LoopingSoundOptions> {
  private sound: any | null = null;

  isSteadyEffect(): boolean {
    return true;
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const audioService = ctx.appContext.services.audio;
    if (!audioService) {
      logger.warn('AudioService not available');
      return;
    }

    // Create and start looping sound
    // Note: Actual AudioService implementation needed
    // This is a placeholder for the audio API
    try {
      this.sound = (audioService as any).createSound(this.options.soundId, {
        loop: true,
        volume: this.options.volume ?? 1.0,
      });
      this.sound.play();
    } catch (error) {
      logger.warn('Failed to create sound', { error });
      return;
    }

    logger.debug('Looping sound started', { soundId: this.options.soundId });
  }

  stop(): void {
    if (this.sound) {
      try {
        this.sound.stop();
        this.sound.dispose();
      } catch (error) {
        logger.warn('Failed to stop sound', { error });
      }
      this.sound = null;
    }

    logger.debug('Looping sound stopped', { soundId: this.options.soundId });
  }

  isRunning(): boolean {
    return this.sound !== null;
  }

  onParameterChanged(paramName: string, value: any): void {
    // Supports dynamic volume changes
    if (paramName === 'volume' && this.sound) {
      this.sound.setVolume(value);
      logger.debug('Sound volume updated', { volume: value });
    }
  }
}
