import { getLogger, ExceptionHandler, MessageType } from '@nimbus/shared';
import type { ScrawlScript, ScrawlScriptLibrary, ScriptActionDefinition } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import { ScrawlEffectRegistry, ScrawlEffectFactory } from './ScrawlEffectFactory';
import { ScrawlExecutor } from './ScrawlExecutor';
import type { EffectDeps } from './ScrawlEffectHandler';
import type { ScrawlExecContext } from './ScrawlExecContext';
import { LogEffect } from './effects/LogEffect';
import { CommandEffect } from './effects/CommandEffect';
import { CircleMarkerEffect } from './effects/CircleMarkerEffect';
import { ProjectileEffect } from './effects/ProjectileEffect';
import { ParticleBeamEffect } from './effects/ParticleBeamEffect';
import { LoopingSoundEffect } from './effects/LoopingSoundEffect';
import { BeamFollowEffect } from './effects/BeamFollowEffect';
import { ParticleExplosionEffect } from './effects/ParticleExplosionEffect';
import { ParticleFireEffect } from './effects/ParticleFireEffect';
import { ParticleFireSteadyEffect } from './effects/ParticleFireSteadyEffect';
import { ParticlePositionFlashEffect } from './effects/ParticlePositionFlashEffect';
import { ParticleWandFlashEffect } from './effects/ParticleWandFlashEffect';
import { ParticleWandFlashSteadyEffect } from './effects/ParticleWandFlashSteadyEffect';
import { PlaySoundLoopEffect } from './effects/PlaySoundLoopEffect';
import { PlaySoundEffect } from './effects/PlaySoundEffect';
import { PositionFlashEffect } from './effects/PositionFlashEffect';

const logger = getLogger('ScrawlService');

/**
 * Central service for managing scrawl scripts and effect execution.
 * Manages running executors, effect factory, and script library.
 */
export class ScrawlService {
  private effectRegistry: ScrawlEffectRegistry;
  private effectFactory: ScrawlEffectFactory;
  private scriptLibrary: ScrawlScriptLibrary;
  private runningExecutors = new Map<string, ScrawlExecutor>();
  private executorIdCounter = 0;

  // Track sent effect IDs to prevent executing our own effects when they come back from server
  private sentEffectIds: Set<string> = new Set();

  // Track received effect IDs to prevent duplicate execution
  private receivedEffectIds: Set<string> = new Set();

  // Map effectId → executorId for parameter updates
  private effectIdToExecutorId = new Map<string, string>();

  // Reverse mapping executorId → effectId for cleanup
  private executorIdToEffectId = new Map<string, string>();

  constructor(private readonly appContext: AppContext) {
    // Initialize effect registry and factory
    this.effectRegistry = new ScrawlEffectRegistry();
    this.effectFactory = new ScrawlEffectFactory(this.effectRegistry, this.createEffectDeps());

    // Initialize script library (simple in-memory implementation)
    this.scriptLibrary = this.createScriptLibrary();

    logger.debug('ScrawlService initialized');
  }

  /**
   * Initialize the service (called after AppContext is fully set up)
   */
  async initialize(): Promise<void> {
    try {
      logger.debug('ScrawlService initializing...');

      // Register built-in effects
      this.registerBuiltInEffects();

      logger.debug('ScrawlService initialized successfully');
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ScrawlService.initialize');
    }
  }

  /**
   * Register built-in effect handlers
   */
  private registerBuiltInEffects(): void {
    // Register LogEffect for testing and debugging
    this.effectRegistry.register('log', LogEffect);

    // Register CommandEffect for executing commands
    this.effectRegistry.register('command', CommandEffect);

    // Register CircleMarkerEffect for visual AOE markers
    this.effectRegistry.register('circleMarker', CircleMarkerEffect);

    // Register ProjectileEffect for flying projectiles
    this.effectRegistry.register('projectile', ProjectileEffect);

    // Register ParticleBeamEffect for magical beam effects
    this.effectRegistry.register('particleBeam', ParticleBeamEffect);

    // Register LoopingSoundEffect for looping sounds in While/Until loops
    this.effectRegistry.register('sound:loop', LoopingSoundEffect);

    // Register BeamFollowEffect for dynamic beam tracking
    this.effectRegistry.register('beam:follow', BeamFollowEffect);

    // Register ParticleExplosionEffect for radial particle explosions
    this.effectRegistry.register('particleExplosion', ParticleExplosionEffect);

    // Register ParticleFireEffect for fire simulation (one-shot)
    this.effectRegistry.register('particleFire', ParticleFireEffect);

    // Register ParticleFireSteadyEffect for endless fire
    this.effectRegistry.register('particleFireSteady', ParticleFireSteadyEffect);

    // Register ParticlePositionFlashEffect for lightning strikes
    this.effectRegistry.register('particlePositionFlash', ParticlePositionFlashEffect);

    // Register ParticleWandFlashEffect for wand beams (one-shot)
    this.effectRegistry.register('particleWandFlash', ParticleWandFlashEffect);

    // Register ParticleWandFlashSteadyEffect for continuous wand beams
    this.effectRegistry.register('particleWandFlashSteady', ParticleWandFlashSteadyEffect);

    // Register PlaySoundLoopEffect for looping sounds
    this.effectRegistry.register('playSoundLoop', PlaySoundLoopEffect);

    // Register PlaySoundEffect for one-shot sounds
    this.effectRegistry.register('playSound', PlaySoundEffect);

    // Register PositionFlashEffect for texture-based lightning strikes
    this.effectRegistry.register('positionFlash', PositionFlashEffect);

    logger.debug('Built-in effects registered', {
      effects: [
        'log',
        'command',
        'circleMarker',
        'projectile',
        'particleBeam',
        'sound:loop',
        'beam:follow',
        'particleExplosion',
        'particleFire',
        'particleFireSteady',
        'particlePositionFlash',
        'particleWandFlash',
        'particleWandFlashSteady',
        'playSoundLoop',
        'playSound',
        'positionFlash',
      ],
    });
  }

  /**
   * Get the effect registry for registering custom effects
   */
  getEffectRegistry(): ScrawlEffectRegistry {
    return this.effectRegistry;
  }

  /**
   * Get the effect factory
   */
  getEffectFactory(): ScrawlEffectFactory {
    return this.effectFactory;
  }

  /**
   * Get the script library
   */
  getScriptLibrary(): ScrawlScriptLibrary {
    return this.scriptLibrary;
  }

  /**
   * Get standard parameters that are automatically added to every script execution
   * @returns Object with worldSeason and worldDaytime parameters
   */
  private getStandardParameters(): Record<string, any> {
    const worldInfo = this.appContext.worldInfo;
    const environmentService = this.appContext.services.environment;

    const params: Record<string, any> = {};

    // Add worldSeason from WorldInfo
    if (worldInfo?.seasonStatus) {
      params.worldSeason = worldInfo.seasonStatus;
    }

    // Add worldDaytime from EnvironmentService
    if (environmentService) {
      params.worldDaytime = environmentService.getWorldDayTimeSection();
    }

    return params;
  }

  /**
   * Execute a script action definition
   * @param action Script action definition
   * @param context Initial execution context
   * @returns Executor ID
   */
  async executeAction(
    action: ScriptActionDefinition,
    context?: Partial<ScrawlExecContext>
  ): Promise<string> {
    try {
      // Calculate multiplayer data (effectId, chunks, sendToServer)
      const shouldSendToServer = action.sendToServer !== false;
      let effectId: string | undefined;
      let affectedChunks: Array<{ cx: number; cz: number }> | undefined;

      if (shouldSendToServer) {
        const mpData = this.sendEffectTriggerToServer(action, context);
        effectId = mpData.effectId;
        affectedChunks = mpData.chunks;
      }

      // Determine which script to execute
      let script: ScrawlScript | undefined;

      if (action.script) {
        // Inline script
        script = action.script;
      } else if (action.scriptId) {
        // Load script by ID
        script = await this.scriptLibrary.load(action.scriptId);
      }

      if (!script) {
        throw new Error('No script provided in action definition');
      }

      // Merge parameters into vars (everything goes into vars now)
      // Check if context specifies isLocal (from EffectTriggerHandler for remote effects)
      const isLocal = (context as any)?.isLocal ?? true; // Default to true for local

      // Add standard parameters (worldSeason, worldDaytime)
      const standardParams = this.getStandardParameters();

      const executionContext: Partial<ScrawlExecContext> = {
        isLocal, // Use from context if provided (remote: false), otherwise default to true (local)
        vars: {
          ...standardParams,
          ...(context as any)?.vars,
          ...(action.parameters || {}),
        },
      };

      logger.debug('ExecutionContext prepared', {
        isLocal,
        fromContext: (context as any)?.isLocal,
        scriptId: script.id,
      });

      // Log all parameters for debugging
      logger.debug('Executing script with parameters', {
        scriptId: script.id,
        hasSource: !!executionContext.vars?.source,
        hasTarget: !!executionContext.vars?.target,
        hasTargets: !!executionContext.vars?.targets,
        targetsLength: executionContext.vars?.targets?.length,
        sourcePos: executionContext.vars?.source?.position,
        targetPos: executionContext.vars?.target?.position,
        targetsPos: executionContext.vars?.targets?.map((t: any) => t?.position),
        allVars: executionContext.vars ? Object.keys(executionContext.vars) : [],
      });

      // Execute script locally and get executor ID
      const executorId = await this.executeScript(script, executionContext);

      // Set multiplayer data on the executor if available
      if (effectId && affectedChunks && shouldSendToServer) {
        const executor = this.runningExecutors.get(executorId);
        if (executor) {
          executor.setMultiplayerData(effectId, affectedChunks, shouldSendToServer);
          // Map effectId to executorId for parameter updates
          this.effectIdToExecutorId.set(effectId, executorId);
        }
      }

      return executorId;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ScrawlService.executeAction',
        { scriptId: action.scriptId }
      );
    }
  }

  /**
   * Execute a script
   * @param scriptIdOrObj Script ID or script object
   * @param context Initial execution context
   * @returns Executor ID
   */
  async executeScript(
    scriptIdOrObj: string | ScrawlScript,
    context?: Partial<ScrawlExecContext>
  ): Promise<string> {
    try {
      // Load script if ID provided
      const script =
        typeof scriptIdOrObj === 'string'
          ? await this.scriptLibrary.load(scriptIdOrObj)
          : scriptIdOrObj;

      if (!script) {
        throw new Error(`Script not found: ${scriptIdOrObj}`);
      }

      // Add standard parameters to context
      const standardParams = this.getStandardParameters();
      const enrichedContext: Partial<ScrawlExecContext> = {
        ...context,
        vars: {
          ...standardParams,
          ...(context?.vars || {}),
        },
      };

      // Create executor
      const executor = new ScrawlExecutor(
        this.effectFactory,
        this.scriptLibrary,
        this.appContext,
        script,
        enrichedContext
      );

      // Generate executor ID
      const executorId = `executor_${this.executorIdCounter++}`;
      executor.setExecutorId(executorId); // Set executor ID for shortcut tracking
      this.runningExecutors.set(executorId, executor);

      logger.debug(`Starting script execution: ${script.id}`, { executorId });

      // Execute script (fire-and-forget)
      executor
        .start()
        .then(() => {
          logger.debug(`Script execution completed: ${script.id}`, { executorId });
          this.cleanupExecutor(executorId);
        })
        .catch((error) => {
          ExceptionHandler.handle(error, 'ScrawlService.executeScript.executor', {
            scriptId: script.id,
            executorId,
          });
          this.cleanupExecutor(executorId);
        });

      return executorId;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ScrawlService.executeScript',
        { scriptId: typeof scriptIdOrObj === 'string' ? scriptIdOrObj : scriptIdOrObj.id }
      );
    }
  }

  /**
   * Get a running executor by ID
   */
  getExecutor(executorId: string): ScrawlExecutor | undefined {
    return this.runningExecutors.get(executorId);
  }

  /**
   * Cancel a running executor
   */
  cancelExecutor(executorId: string): boolean {
    const executor = this.runningExecutors.get(executorId);
    if (executor) {
      executor.cancel();
      this.cleanupExecutor(executorId);
      logger.debug(`Executor cancelled: ${executorId}`);
      return true;
    }
    return false;
  }

  /**
   * Pause a running executor
   */
  pauseExecutor(executorId: string): boolean {
    const executor = this.runningExecutors.get(executorId);
    if (executor) {
      executor.pause();
      logger.debug(`Executor paused: ${executorId}`);
      return true;
    }
    return false;
  }

  /**
   * Resume a paused executor
   */
  resumeExecutor(executorId: string): boolean {
    const executor = this.runningExecutors.get(executorId);
    if (executor) {
      executor.resume();
      logger.debug(`Executor resumed: ${executorId}`);
      return true;
    }
    return false;
  }

  /**
   * Get all running executor IDs
   */
  getRunningExecutorIds(): string[] {
    return Array.from(this.runningExecutors.keys());
  }

  /**
   * Cancel all running executors
   */
  cancelAllExecutors(): void {
    const ids = this.getRunningExecutorIds();
    ids.forEach((id) => this.cancelExecutor(id));
    logger.debug(`Cancelled ${ids.length} executors`);
  }

  /**
   * Clean up executor and associated mappings
   *
   * @param executorId Executor ID to clean up
   */
  private cleanupExecutor(executorId: string): void {
    // Clean up effectId mapping if exists
    const effectId = this.executorIdToEffectId.get(executorId);
    if (effectId) {
      this.effectIdToExecutorId.delete(effectId);
      this.executorIdToEffectId.delete(executorId);
      logger.debug('Effect mapping cleaned up', { effectId, executorId });
    }

    // Remove from running executors
    this.runningExecutors.delete(executorId);
  }

  /**
   * Create effect dependencies
   */
  private createEffectDeps(): EffectDeps {
    return {
      log: (message: string, ...args: any[]) => logger.debug(message, ...args),
      now: () => performance.now() / 1000,
      // Add more dependencies as needed (scene, audio, etc.)
    };
  }

  /**
   * Create script library (simple in-memory implementation)
   */
  private createScriptLibrary(): ScrawlScriptLibrary {
    const scripts = new Map<string, ScrawlScript>();

    return {
      get: (id: string) => scripts.get(id),

      load: async (id: string) => {
        // Check cache first
        let script = scripts.get(id);
        if (script) {
          return script;
        }

        // Load from assets server (e.g., /assets/scrawl/{id}.scrawl.json)
        try {
          const networkService = this.appContext.services.network;
          if (!networkService) {
            logger.error('NetworkService not available for loading scripts');
            return undefined;
          }

          // Build asset path for script
          const scriptPath = `scrawl/${id}.scrawl.json`;
          const scriptUrl = networkService.getAssetUrl(scriptPath);

          logger.debug('Loading script from asset server', { id, scriptUrl });

          // Fetch script from server
          const response = await fetch(scriptUrl, {
            credentials: 'include',
          });
          if (!response.ok) {
            logger.error('Failed to load script from server', {
              id,
              status: response.status,
              statusText: response.statusText,
            });
            return undefined;
          }

          // Parse JSON
          script = await response.json();
          if (!script) {
            logger.error('Script loaded but failed to parse JSON', { id });
            return undefined;
          }

          // Cache script for future use
          scripts.set(id, script);

          logger.debug('Script loaded successfully', { id });
          return script;
        } catch (error) {
          logger.error('Failed to load script', {
            id,
            error: (error as Error).message,
          });
          return undefined;
        }
      },

      has: (id: string) => scripts.has(id),
    };
  }

  /**
   * Register a script in the library (for testing/development)
   */
  registerScript(script: ScrawlScript): void {
    const library = this.scriptLibrary as any;
    if (library.scripts) {
      library.scripts.set(script.id, script);
    }
    logger.debug(`Script registered: ${script.id}`);
  }

  /**
   * Register effectId mapping for remote effects
   *
   * Called by EffectTriggerHandler after remote script execution starts.
   * Creates the effectIdToExecutorId mapping so that parameter updates (s.u) work.
   *
   * @param effectId Server-provided effect ID
   * @param executorId Local executor ID
   */
  registerRemoteEffectMapping(effectId: string, executorId: string): void {
    const executor = this.runningExecutors.get(executorId);
    if (!executor) {
      logger.warn('Cannot register remote effect mapping: executor not found', {
        effectId,
        executorId,
      });
      return;
    }

    // Set multiplayer data on executor (no chunks, no server send for remote)
    executor.setMultiplayerData(effectId, [], false);

    // Create bidirectional mapping
    this.effectIdToExecutorId.set(effectId, executorId);
    this.executorIdToEffectId.set(executorId, effectId);

    logger.debug('Remote effect mapping registered', {
      effectId,
      executorId,
    });
  }

  /**
   * Updates a parameter in a running executor.
   * Can be called from any source (e.g., InputService, NetworkService, etc.)
   *
   * @param executorIdOrEffectId ID of the executor or effectId (for remote updates)
   * @param paramName Name of the parameter (e.g. 'targetPos', 'mousePos', 'volume')
   * @param value New value (any type)
   * @param targeting Optional targeting context from network synchronization
   * @returns True if executor was found and updated, false otherwise
   */
  updateExecutorParameter(
    executorIdOrEffectId: string,
    paramName: string,
    value: any,
    targeting?: import('@nimbus/shared').SerializableTargetingContext
  ): boolean {
    // Try direct executor ID first
    let executor = this.runningExecutors.get(executorIdOrEffectId);

    // If not found, try mapping from effectId
    if (!executor) {
      const executorId = this.effectIdToExecutorId.get(executorIdOrEffectId);
      if (executorId) {
        executor = this.runningExecutors.get(executorId);
      }
    }

    if (executor) {
      executor.updateParameter(paramName, value, targeting);
      logger.debug('Executor parameter updated via ScrawlService', {
        id: executorIdOrEffectId,
        paramName,
        hasTargeting: !!targeting,
      });
      return true;
    } else {
      logger.debug(`Executor not found for parameter update: ${executorIdOrEffectId}`, {
        paramName,
      });
      return false;
    }
  }

  /**
   * Send effect trigger to server for synchronization
   *
   * Calculates affected chunks from source/target positions and sends
   * the effect to server for broadcasting to other clients.
   *
   * @param action Script action definition
   * @param context Execution context with source/target
   * @returns Effect ID and affected chunks
   */
  private sendEffectTriggerToServer(
    action: ScriptActionDefinition,
    context?: Partial<ScrawlExecContext>
  ): { effectId: string; chunks: Array<{ cx: number; cz: number }> } {
    try {
      const networkService = this.appContext.services.network;

      // Calculate affected chunks from source and target positions
      const chunks: Array<{ cx: number; cz: number }> = [];
      const chunkSize = this.appContext.worldInfo?.chunkSize || 16;

      // Get source and targets from vars
      const vars = (context as any)?.vars || {};
      const source = vars.source;
      const targets = vars.targets;

      // Add chunk for source position
      if (source?.position) {
        const pos = source.position;
        const cx = Math.floor(pos.x / chunkSize);
        const cz = Math.floor(pos.z / chunkSize);
        chunks.push({ cx, cz });
      }

      // Add chunks for target(s)
      if (targets) {
        for (const target of targets) {
          if (target?.position) {
            const pos = target.position;
            const cx = Math.floor(pos.x / chunkSize);
            const cz = Math.floor(pos.z / chunkSize);
            // Avoid duplicates
            if (!chunks.some(c => c.cx === cx && c.cz === cz)) {
              chunks.push({ cx, cz });
            }
          }
        }
      }

      // Get entity ID (if available)
      const entityId = source?.entityId;

      // Generate unique effect ID
      const effectId = `effect_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;

      // Track this effect ID so we don't execute it again when it comes back from server
      this.sentEffectIds.add(effectId);
      // Cleanup after 10 seconds
      setTimeout(() => this.sentEffectIds.delete(effectId), 10000);

      // Serialize source/target/targets for network transmission
      // Extract only position data, remove Mesh references
      const serializeObject = (obj: any): any => {
        if (!obj) return obj;

        // Extract position from various possible properties
        const pos = obj.currentPosition || obj.position || obj.block?.position;
        if (pos) {
          return {
            position: {
              x: pos.x,
              y: pos.y,
              z: pos.z,
            },
            entityId: obj.entityId || obj.entity?.entityId || obj.id,
            blockTypeId: obj.blockTypeId || obj.block?.blockTypeId,
          };
        }
        return obj;
      };

      // Build effect with source/target/targets in parameters
      // Serialize to remove Mesh references
      // IMPORTANT: Don't send isLocal to server (it's client-side only)
      const { isLocal: _removeIsLocal, ...cleanAction } = action as any;

      const effectWithContext = {
        ...cleanAction,
        parameters: {
          ...(action.parameters || {}),
          source: serializeObject(vars.source),
          target: serializeObject(vars.target),
          targets: vars.targets?.map(serializeObject),
        },
      };

      // Build effect trigger message
      const effectTriggerData = {
        effectId,
        entityId,
        chunks: chunks.length > 0 ? chunks : undefined,
        effect: effectWithContext,
      };

      // Send to server if NetworkService is available
      if (networkService) {
        logger.debug('Sending effect trigger to server', {
          effectId,
          entityId,
          chunkCount: chunks.length,
          scriptId: action.scriptId,
          messageType: MessageType.EFFECT_TRIGGER,
        });

        networkService.send({
          t: MessageType.EFFECT_TRIGGER,
          d: effectTriggerData,
        });

        logger.debug('Effect trigger sent to server', {
          effectId,
        });
      } else {
        logger.warn('NetworkService not available, effect not sent to server');
      }

      // Return effectId and chunks for executor
      return { effectId, chunks };
    } catch (error) {
      // Log but don't fail - local effect should still execute
      logger.warn('Failed to send effect to server (effect still executed locally)', {
        error: (error as Error).message,
      });
      // Return empty data on error
      const fallbackId = `effect_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
      return { effectId: fallbackId, chunks: [] };
    }
  }

  /**
   * Check if an effect was sent by this client
   *
   * Used by EffectTriggerHandler to prevent executing effects that
   * this client already executed locally.
   *
   * @param effectId Effect ID to check
   * @returns True if this client sent the effect
   */
  wasEffectSentByUs(effectId: string): boolean {
    return this.sentEffectIds.has(effectId);
  }

  /**
   * Mark an effect as received to prevent duplicate execution
   *
   * Used by EffectTriggerHandler to track received effects and prevent
   * duplicate execution if the same effect is received multiple times.
   *
   * @param effectId Effect ID to mark as received
   * @returns True if this is the first time receiving this effect, false if duplicate
   */
  markEffectAsReceived(effectId: string): boolean {
    if (this.receivedEffectIds.has(effectId)) {
      return false; // Duplicate
    }

    this.receivedEffectIds.add(effectId);
    // Cleanup after 10 seconds (same as sentEffectIds)
    setTimeout(() => this.receivedEffectIds.delete(effectId), 10000);
    return true; // First time
  }

  /**
   * Get effectId for a specific executor
   *
   * Used by ShortcutService to send position updates to server.
   *
   * @param executorId Executor ID
   * @returns Effect ID or undefined if not found or not synchronized
   */
  getEffectIdForExecutor(executorId: string): string | undefined {
    // Check if executor exists
    const executor = this.runningExecutors.get(executorId);
    if (!executor) {
      return undefined;
    }

    // Return effectId (only set if executor is synchronized to server)
    return executor.getEffectId();
  }

  /**
   * Dispose the service
   */
  dispose(): void {
    logger.debug('Disposing ScrawlService...');
    this.cancelAllExecutors();
    this.sentEffectIds.clear();
    this.receivedEffectIds.clear();
    this.effectIdToExecutorId.clear();
    this.executorIdToEffectId.clear();
    logger.debug('ScrawlService disposed');
  }
}
