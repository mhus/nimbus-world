/**
 * TestAudioCommand - Test audio playback
 */

import { CommandHandler } from './CommandHandler';
import {  toNumber , getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('TestAudioCommand');

/**
 * Test audio command - Play audio file for testing
 *
 * Usage:
 *   /testaudio audio/step/grass1.ogg
 *   /testaudio audio/step/grass2.ogg 0.5
 */
export class TestAudioCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'testaudio';
  }

  description(): string {
    return 'Test audio playback (/testaudio <path> [volume])';
  }

  async execute(parameters: any[]): Promise<any> {
    const audioService = this.appContext.services.audio;

    if (!audioService) {
      logger.error('AudioService not available');
      return { error: 'AudioService not available' };
    }

    if (parameters.length === 0) {
      logger.error('Usage: /testaudio <path> [volume]');
      logger.debug('Example: /testaudio audio/step/grass1.ogg');
      logger.debug('Example: /testaudio audio/step/grass2.ogg 0.5');
      return { error: 'Missing audio path parameter' };
    }

    const assetPath = String(parameters[0]);
    const volume = parameters.length > 1 ? toNumber(parameters[1]) : 1.0;

    logger.debug(`Loading and playing audio: ${assetPath}`);
    logger.debug(`Volume: ${volume}`);

    try {
      // Load audio
      const sound = await audioService.loadAudio(assetPath, {
        volume,
        loop: false,
        autoplay: false,
        spatialSound: false,
      });

      if (!sound) {
        logger.error('Failed to load audio');
        return { error: 'Failed to load audio' };
      }

      logger.debug('Sound loaded:', {
        assetPath,
        isPlaying: sound.isPlaying,
        hasOnEndedObservable: !!sound.onEndedObservable,
      });

      // Check if audio engine is unlocked
      const BABYLON = await import('@babylonjs/core');
      const audioEngine = BABYLON.Engine.audioEngine;

      logger.debug('Audio engine status:', {
        hasAudioEngine: !!audioEngine,
        unlocked: audioEngine?.unlocked,
        canUseWebAudio: audioEngine?.canUseWebAudio,
      });

      if (audioEngine && !audioEngine.unlocked) {
        logger.warn('⚠️ Audio engine is LOCKED!');
        logger.warn('Audio cannot play until user interacts with the page (click, key press, etc.)');
        logger.warn('Try clicking on the canvas or pressing a key, then run /testaudio again');
        return { error: 'Audio engine locked - user interaction required' };
      }

      // Play sound - Babylon.js will play when ready
      logger.debug('Calling sound.play()...');
      sound.play();

      logger.debug('✓ sound.play() called:', {
        assetPath,
        volume: sound.volume !== undefined ? sound.volume : (typeof sound.getVolume === 'function' ? sound.getVolume() : 'unknown'),
        isPlaying: sound.isPlaying !== undefined ? sound.isPlaying : 'unknown',
      });

      // Check status after delays
      setTimeout(() => {
        logger.debug('Sound status after 500ms:', {
          isPlaying: sound.isPlaying !== undefined ? sound.isPlaying : 'unknown',
          isReady: typeof sound.isReady === 'function' ? sound.isReady() : 'unknown',
        });
      }, 500);

      setTimeout(() => {
        logger.debug('Sound status after 2000ms:', {
          isPlaying: sound.isPlaying !== undefined ? sound.isPlaying : 'unknown',
          isReady: typeof sound.isReady === 'function' ? sound.isReady() : 'unknown',
        });
      }, 2000);

      setTimeout(() => {
        logger.debug('Sound status after 5000ms:', {
          isPlaying: sound.isPlaying !== undefined ? sound.isPlaying : 'unknown',
          isReady: typeof sound.isReady === 'function' ? sound.isReady() : 'unknown',
        });
      }, 5000);

      return {
        success: true,
        assetPath,
        volume,
        isPlaying: sound.isPlaying,
      };
    } catch (error) {
      logger.error('Error playing audio:', error);
      return { error: (error as Error).message };
    }
  }
}
