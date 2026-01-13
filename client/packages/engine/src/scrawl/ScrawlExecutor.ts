import {getLogger, ExceptionHandler, MessageType, ChunkCoordinate} from '@nimbus/shared';
import type {
  ScrawlScript,
  ScrawlStep,
  ScrawlCondition,
  ScrawlSubject,
  ScrawlScriptLibrary,
} from '@nimbus/shared';
import type { ScrawlExecContext } from './ScrawlExecContext';
import type { ScrawlEffectFactory } from './ScrawlEffectFactory';
import type { AppContext } from '../AppContext';

const logger = getLogger('ScrawlExecutor');

/**
 * Defines how the executor is controlled
 */
export enum ExecutorControlType {
  /** Private script - runs only locally, no network sync */
  LOCAL_ONLY = 'local_only',
  /** Main/Common script - locally controlled, sends updates to others */
  LOCAL_CONTROLLED = 'local_controlled',
  /** Slave/Common script - remotely controlled via network messages */
  REMOTE_CONTROLLED = 'remote_controlled',
}

/**
 * Executor for scrawl scripts.
 * Handles the execution logic for all step types.
 */
export class ScrawlExecutor {
  private cancelled = false;
  private paused = false;
  private shouldBreak = false; // Set when script should break (from shortcut end or __stop__)
  private vars = new Map<string, any>();
  private eventEmitter: EventTarget;
  private runningEffects = new Map<string, any>();
  private executorId: string = `exec_${Date.now()}_${Math.random()}`; // Default, will be set by setExecutorId()
  private taskCompletionPromises = new Map<string, Promise<void>>();

  /** Defines how this executor is controlled (local vs remote) */
  private controlType: ExecutorControlType;
  private shortcutEndListener: ((data: any) => void) | null = null;
  private playerDirectionListener: ((data: any) => void) | null = null;
  private playerDirectionBroadcastActive: boolean = false;

  // For multiplayer synchronization
  private effectId?: string; // Unique effect ID for this execution
  private affectedChunks?: Array<ChunkCoordinate>; // Chunks affected by this effect
  private sendToServer: boolean = true; // Whether to send updates to server

  constructor(
    private readonly effectFactory: ScrawlEffectFactory,
    private readonly scriptLibrary: ScrawlScriptLibrary,
    private readonly appContext: AppContext,
    private readonly script: ScrawlScript,
    private readonly initialContext: Partial<ScrawlExecContext>
  ) {
    this.eventEmitter = new EventTarget();

    // Determine control type based on context
    const isLocal = (initialContext as any)?.isLocal ?? true;
    const sendToServer = this.sendToServer; // Will be set later via setMultiplayerData

    if (!isLocal) {
      // Script came from network - remotely controlled
      this.controlType = ExecutorControlType.REMOTE_CONTROLLED;
    } else if (sendToServer) {
      // Local script that syncs to network - locally controlled
      this.controlType = ExecutorControlType.LOCAL_CONTROLLED;
    } else {
      // Local only script - no network
      this.controlType = ExecutorControlType.LOCAL_ONLY;
    }

    logger.debug('Executor created', {
      executorId: this.executorId,
      controlType: this.controlType,
      isLocal,
      sendToServer,
    });

    // Listen for shortcut ended events to stop Until/While loops
    // For all control types - remote will receive via __stop__ parameter
    this.setupShortcutEndListener();
  }

  /**
   * Setup listener for shortcut ended events
   * When shortcut ends, emit 'stop_event' to terminate Until/While loops
   *
   * Note: Position updates are handled by InputService.updateActiveExecutors()
   * which calls updateParameter() automatically each frame.
   */
  private setupShortcutEndListener(): void {
    const playerService = this.appContext.services.player;
    if (!playerService) return;

    this.shortcutEndListener = (data: any) => {
      // Check if this is our executor
      if (data.executorId === this.executorId) {
        logger.debug('Shortcut ended for this executor, setting shouldBreak flag', {
          executorId: this.executorId,
          scriptId: this.script.id,
          controlType: this.controlType,
        });

        // Set flag to break Until/While loops
        this.shouldBreak = true;
        // Also emit event for compatibility
        this.emit('stop_event');
      }
    };

    playerService.on('shortcut:ended', this.shortcutEndListener);
  }

  /**
   * Cleanup shortcut end listener
   */
  private cleanupShortcutEndListener(): void {
    if (this.shortcutEndListener) {
      const playerService = this.appContext.services.player;
      if (playerService) {
        playerService.off('shortcut:ended', this.shortcutEndListener);
      }
      this.shortcutEndListener = null;
    }
  }

  /**
   * Setup player direction listener for effects that need continuous target updates
   */
  private setupPlayerDirectionListener(): void {
    const playerService = this.appContext.services.player;
    if (!playerService) return;

    this.playerDirectionListener = (data: any) => {
      // Update target position in context
      const ctx = this.createContext();
      if (ctx.vars && data.targetPos) {
        ctx.vars.target = {
          position: data.targetPos,
        };

        // Update all running effects
        for (const [effectKey, effectHandler] of this.runningEffects) {
          if (effectHandler.onParameterChanged) {
            effectHandler.onParameterChanged('target', ctx.vars.target, ctx);
          }
        }
      }
    };

    playerService.on('player:direction', this.playerDirectionListener);
    logger.debug('Player direction listener setup');
  }

  /**
   * Cleanup player direction listener
   */
  private cleanupPlayerDirectionListener(): void {
    if (this.playerDirectionListener) {
      const playerService = this.appContext.services.player;
      if (playerService) {
        playerService.off('player:direction', this.playerDirectionListener);
      }
      this.playerDirectionListener = null;
    }

    // Disable broadcast if we enabled it
    if (this.playerDirectionBroadcastActive) {
      const physicsService = this.appContext.services.physics;
      if (physicsService) {
        physicsService.setPlayerDirectionBroadcast(false);
      }
      this.playerDirectionBroadcastActive = false;
    }
  }

  /**
   * Set executor ID (called by ScrawlService)
   */
  setExecutorId(id: string): void {
    this.executorId = id;
  }

  /**
   * Get executor ID
   */
  getExecutorId(): string {
    return this.executorId;
  }

  /**
   * Start executing the script
   */
  async start(): Promise<void> {
    try {
      logger.debug(`Starting script: ${this.script.id}`);

      // Determine root step
      let rootStep: ScrawlStep | undefined;

      if (this.script.root) {
        rootStep = this.script.root;
      } else if (this.script.sequences?.['main']) {
        rootStep = this.script.sequences['main'].step;
      }

      if (!rootStep) {
        throw new Error(`Script ${this.script.id} has no root step or main sequence`);
      }

      // Create execution context
      const ctx = this.createContext();

      // Execute root step
      await this.execStep(ctx, rootStep);

      logger.debug(`Script completed: ${this.script.id}`);
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ScrawlExecutor.start',
        { scriptId: this.script.id }
      );
    } finally {
      // Cleanup listeners when script ends
      this.cleanupShortcutEndListener();
      this.cleanupPlayerDirectionListener();
    }
  }

  /**
   * Execute a single step
   */
  async execStep(ctx: ScrawlExecContext, step: ScrawlStep): Promise<void> {
    if (this.cancelled) {
      return;
    }

    // Wait while paused
    while (this.paused && !this.cancelled) {
      await this.sleep(0.1);
    }

    try {
      switch (step.kind) {
        case 'Play':
          await this.execStepPlay(ctx, step);
          break;
        case 'Wait':
          await this.sleep(step.seconds);
          break;
        case 'Sequence':
          await this.execStepSequence(ctx, step);
          break;
        case 'Parallel':
          await this.execStepParallel(ctx, step);
          break;
        case 'Repeat':
          await this.execStepRepeat(ctx, step);
          break;
        case 'ForEach':
          await this.execStepForEach(ctx, step);
          break;
        case 'LodSwitch':
          await this.execStepLodSwitch(ctx, step);
          break;
        case 'Call':
          await this.execStepCall(ctx, step);
          break;
        case 'If':
          await this.execStepIf(ctx, step);
          break;
        case 'EmitEvent':
          this.emit(step.name, step.payload);
          break;
        case 'WaitEvent':
          await this.waitEvent(step.name, step.timeout ?? 0);
          break;
        case 'SetVar':
          this.setVar(step.name, step.value);
          break;
        case 'Cmd':
          await this.execStepCmd(ctx, step);
          break;
        case 'While':
          await this.execStepWhile(ctx, step);
          break;
        case 'Until':
          await this.execStepUntil(ctx, step);
          break;
        default:
          logger.warn(`Unknown step kind: ${(step as any).kind}`);
      }
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ScrawlExecutor.execStep',
        { scriptId: this.script.id, stepKind: step.kind }
      );
    }
  }

  private async execStepPlay(ctx: ScrawlExecContext, step: any): Promise<void> {
    // Merge context with step context
    // All parameters (including source, target, targets) come through ctx.vars
    const effectCtx: ScrawlExecContext = {
      ...ctx,
      vars: {
        ...ctx.vars,
        ...(step.ctx || {}),
      },
    };

    // Check if effect needs player direction updates (ONLY for local scripts)
    // Remote effects (isLocal === false) NEVER activate player direction
    const needsPlayerDirection =
      step.receivePlayerDirection === true &&
      effectCtx.isLocal === true; // Explicit check for true

    if (needsPlayerDirection) {
      // Setup player direction listener
      if (!this.playerDirectionListener) {
        this.setupPlayerDirectionListener();
      }

      // Enable broadcast in PhysicsService
      const physicsService = this.appContext.services.physics;
      if (physicsService) {
        physicsService.setPlayerDirectionBroadcast(true);
        this.playerDirectionBroadcastActive = true;
        logger.debug('Player direction broadcast enabled for Play effect', {
          effectId: step.effectId,
          isLocal: effectCtx.isLocal,
        });
      }
    } else if (step.receivePlayerDirection && !effectCtx.isLocal) {
      logger.debug('Skipping player direction for remote Play effect', {
        effectId: step.effectId,
        isLocal: effectCtx.isLocal,
      });
    }

    try {
      await this.effectFactory.play(step.effectId, step.ctx || {}, effectCtx);
    } finally {
      // Disable broadcast after effect ends (if we enabled it)
      if (needsPlayerDirection && this.playerDirectionBroadcastActive) {
        this.cleanupPlayerDirectionListener();
        logger.debug('Player direction broadcast disabled after effect', {
          effectId: step.effectId,
        });
      }
    }
  }

  private async execStepSequence(ctx: ScrawlExecContext, step: any): Promise<void> {
    for (const childStep of step.steps) {
      await this.execStep(ctx, childStep);
      if (this.cancelled) break;
    }
  }

  private async execStepParallel(ctx: ScrawlExecContext, step: any): Promise<void> {
    const steps = step.steps || [];
    const taskPromises: Promise<void>[] = [];

    for (let i = 0; i < steps.length; i++) {
      const parallelStep = steps[i];

      // Use explicit ID or generate one
      const taskId = (parallelStep as any).id || `${this.executorId}_parallel_${i}`;

      // Fork context and execute
      const forkedCtx = this.fork(ctx);
      const taskPromise = this.execStep(forkedCtx, parallelStep);
      taskPromises.push(taskPromise);

      // Register task completion promise (for While-loops)
      this.taskCompletionPromises.set(taskId, taskPromise);

      // Cleanup after completion
      taskPromise.finally(() => {
        this.taskCompletionPromises.delete(taskId);
      });
    }

    // Wait for all parallel tasks to complete
    await Promise.all(taskPromises);
  }

  private async execStepRepeat(ctx: ScrawlExecContext, step: any): Promise<void> {
    if (step.times != null) {
      // Repeat N times
      for (let i = 0; i < step.times && !this.cancelled; i++) {
        await this.execStep(ctx, step.step);
      }
    } else if (step.untilEvent) {
      // Repeat until event
      while (!this.cancelled) {
        const done = await this.waitEvent(step.untilEvent, 0);
        if (done) break;
        await this.execStep(ctx, step.step);
      }
    }
  }

  private async execStepForEach(ctx: ScrawlExecContext, step: any): Promise<void> {
    // Resolve collection (e.g., "$patients")
    const collection = this.resolveValue(ctx, step.collection);

    if (!Array.isArray(collection)) {
      logger.warn(`ForEach: collection is not an array: ${step.collection}`);
      return;
    }

    // Execute step for each item
    for (const item of collection) {
      if (this.cancelled) break;

      // Create context with item variable
      const itemCtx = { ...ctx };
      this.setContextVar(itemCtx, step.itemVar, item);

      await this.execStep(itemCtx, step.step);
    }
  }

  private async execStepLodSwitch(ctx: ScrawlExecContext, step: any): Promise<void> {
    const lodLevel = ctx.lodLevel || 'medium';
    const lodStep = step.levels[lodLevel];

    if (lodStep) {
      await this.execStep(ctx, lodStep);
    } else {
      logger.debug(`No step defined for LOD level: ${lodLevel}`);
    }
  }

  private async execStepCall(ctx: ScrawlExecContext, step: any): Promise<void> {
    // Load the called script
    const calledScript = await this.scriptLibrary.load(step.scriptId);

    if (!calledScript) {
      logger.warn(`Called script not found: ${step.scriptId}`);
      return;
    }

    // Create new executor for called script
    const subExecutor = new ScrawlExecutor(
      this.effectFactory,
      this.scriptLibrary,
      this.appContext,
      calledScript,
      {
        ...ctx,
        ...(step.args || {}),
      }
    );

    await subExecutor.start();
  }

  private async execStepIf(ctx: ScrawlExecContext, step: any): Promise<void> {
    const conditionMet = this.evalCondition(ctx, step.cond);

    if (conditionMet) {
      await this.execStep(ctx, step.then);
    } else if (step.else) {
      await this.execStep(ctx, step.else);
    }
  }

  private async execStepCmd(ctx: ScrawlExecContext, step: any): Promise<void> {
    const commandService = ctx.appContext.services.command;
    if (!commandService) {
      logger.warn('CommandService not available, skipping Cmd step');
      return;
    }

    const { cmd, parameters = [] } = step;

    if (!cmd) {
      logger.warn('Cmd step: cmd is required');
      return;
    }

    try {
      // Pass parameters as-is - commands should use CastUtil for type conversion
      logger.debug('Executing command from Cmd step', {
        cmd,
        parameters,
        scriptId: this.script.id,
      });

      await commandService.executeCommand(cmd, parameters);
    } catch (error) {
      // Log error but continue script execution
      ExceptionHandler.handle(error, 'ScrawlExecutor.execStepCmd', {
        cmd,
        scriptId: this.script.id,
      });
      logger.warn('Command execution failed in Cmd step', {
        cmd,
        error: (error as Error).message,
      });
    }
  }

  /**
   * Evaluate a condition
   */
  private evalCondition(ctx: ScrawlExecContext, cond: ScrawlCondition): boolean {
    switch (cond.kind) {
      case 'VarEquals':
        return this.getVar(cond.name) === cond.value;

      case 'VarExists':
        return this.vars.has(cond.name);

      case 'Chance':
        return Math.random() < cond.p;

      case 'HasTargets': {
        const patients = ctx.patients || [];
        const min = cond.min ?? 1;
        return patients.length >= min;
      }

      case 'HasSource':
        return ctx.actor !== undefined;

      case 'IsVarTrue': {
        const value = this.vars.get(cond.name);
        if (value === undefined) {
          return cond.defaultValue ?? false;
        }
        return value === true;
      }

      case 'IsVarFalse': {
        const value = this.vars.get(cond.name);
        if (value === undefined) {
          return cond.defaultValue ?? true;
        }
        return value === false;
      }

      default:
        logger.warn(`Unknown condition kind: ${(cond as any).kind}`);
        return false;
    }
  }

  /**
   * Resolve a subject reference (e.g., "$actor", "$patient", "$patient[0]")
   */
  private resolveSubject(
    ctx: ScrawlExecContext,
    ref?: string
  ): ScrawlSubject | ScrawlSubject[] | undefined {
    if (!ref) return undefined;

    // Handle $actor
    if (ref === '$actor') {
      return ctx.actor;
    }

    // Handle $patients
    if (ref === '$patients') {
      return ctx.patients;
    }

    // Handle $patient (first patient)
    if (ref === '$patient') {
      return ctx.patients?.[0];
    }

    // Handle $patient[N]
    const match = ref.match(/^\$patient\[(\d+)\]$/);
    if (match) {
      const index = parseInt(match[1], 10);
      return ctx.patients?.[index];
    }

    // Handle context variables
    return this.resolveValue(ctx, ref);
  }

  /**
   * Resolve a value reference from context
   */
  private resolveValue(ctx: ScrawlExecContext, ref: string): any {
    // Variable reference: $varName
    if (ref.startsWith('$')) {
      const varName = ref.substring(1);

      // Check context first
      if (varName in ctx) {
        return ctx[varName];
      }

      // Check vars map
      return this.vars.get(varName);
    }

    // Literal value
    return ref;
  }

  /**
   * Set a variable in context (for ForEach)
   */
  private setContextVar(ctx: ScrawlExecContext, name: string, value: any): void {
    // Remove $ prefix if present
    const varName = name.startsWith('$') ? name.substring(1) : name;
    (ctx as any)[varName] = value;
  }

  /**
   * Create execution context
   */
  private createContext(): ScrawlExecContext {
    // IMPORTANT: isLocal must be preserved from initialContext, not from vars!
    // It indicates whether THIS execution was triggered locally or remotely
    const isLocalValue = this.initialContext.isLocal ?? true;

    // Extract vars without isLocal (prevent override via spread)
    const { isLocal: _unused, vars: initialVars, ...restContext } = this.initialContext;

    const ctx: ScrawlExecContext = {
      ...restContext,
      appContext: this.appContext,
      executor: this,
      scriptId: this.script.id,
      vars: initialVars || {},
      // isLocal MUST be last to override any spread values
      isLocal: isLocalValue,
    } as ScrawlExecContext;

    logger.debug('Context created', {
      scriptId: this.script.id,
      isLocal: ctx.isLocal,
      fromInitialContext: this.initialContext.isLocal,
    });

    // Set default variables from initial context
    this.setDefaultVariables(ctx);

    return ctx;
  }

  /**
   * Sets default variables in the context.
   * These are automatically available in all scripts.
   */
  private setDefaultVariables(ctx: ScrawlExecContext): void {
    // $source, $target, $targets - These come from vars (parameters)
    if (ctx.vars?.source) {
      this.vars.set('source', ctx.vars.source);
    }

    if (ctx.vars?.target) {
      this.vars.set('target', ctx.vars.target);
    }

    if (ctx.vars?.targets) {
      this.vars.set('targets', ctx.vars.targets);
    }

    // $item, $itemId, $itemName, $itemTexture - These come from vars
    if (ctx.vars?.item) {
      this.vars.set('item', ctx.vars.item);
    }

    if (ctx.vars?.itemId) {
      this.vars.set('itemId', ctx.vars.itemId);
    }

    if (ctx.vars?.itemName) {
      this.vars.set('itemName', ctx.vars.itemName);
    }

    if (ctx.vars?.itemTexture) {
      this.vars.set('itemTexture', ctx.vars.itemTexture);
    }
  }

  /**
   * Fork context for parallel execution
   */
  private fork(ctx: ScrawlExecContext): ScrawlExecContext {
    return {
      ...ctx,
      vars: { ...ctx.vars },
    };
  }

  /**
   * Sleep for a duration
   */
  private sleep(seconds: number): Promise<void> {
    const startTime = this.now();
    const targetTime = startTime + seconds;

    return new Promise<void>((resolve) => {
      const tick = () => {
        if (this.cancelled) {
          resolve();
          return;
        }

        if (this.paused) {
          requestAnimationFrame(tick);
          return;
        }

        if (this.now() >= targetTime) {
          resolve();
          return;
        }

        requestAnimationFrame(tick);
      };

      tick();
    });
  }

  /**
   * Get current time in seconds
   */
  private now(): number {
    return performance.now() / 1000;
  }

  // Public API

  cancel(): void {
    this.cancelled = true;
    this.cleanupShortcutEndListener();
    this.cleanupPlayerDirectionListener();
    logger.debug(`Script cancelled: ${this.script.id}`);
  }

  pause(): void {
    this.paused = true;
    logger.debug(`Script paused: ${this.script.id}`);
  }

  resume(): void {
    this.paused = false;
    logger.debug(`Script resumed: ${this.script.id}`);
  }

  emit(eventName: string, payload?: any): void {
    const event = new CustomEvent(eventName, { detail: payload });
    this.eventEmitter.dispatchEvent(event);
    logger.debug(`Event emitted: ${eventName}`, { payload });
  }

  waitEvent(eventName: string, timeoutSec = 0): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      let timeoutHandle: any = null;

      const handler = (event: Event) => {
        cleanup();
        resolve(true);
      };

      const cleanup = () => {
        this.eventEmitter.removeEventListener(eventName, handler);
        if (timeoutHandle) {
          clearTimeout(timeoutHandle);
        }
      };

      this.eventEmitter.addEventListener(eventName, handler, { once: true });

      if (timeoutSec > 0) {
        timeoutHandle = setTimeout(() => {
          cleanup();
          resolve(false);
        }, timeoutSec * 1000);
      }
    });
  }

  getVar(name: string): any {
    return this.vars.get(name);
  }

  setVar(name: string, value: any): void {
    this.vars.set(name, value);
    logger.debug(`Variable set: ${name}`, { value });
  }

  isCancelled(): boolean {
    return this.cancelled;
  }

  isPaused(): boolean {
    return this.paused;
  }

  getScriptId(): string {
    return this.script.id;
  }

  /**
   * Updates a parameter in the context and notifies running effects.
   * Called externally via ScrawlService.
   *
   * Only relevant for StepUntil - StepWhile does not trigger parameter updates.
   *
   * @param paramName Name of the parameter
   * @param value New value
   */
  updateParameter(paramName: string, value: any, targeting?: import('@nimbus/shared').SerializableTargetingContext): void {
    // Update context variable
    const ctx = this.createContext();
    ctx.vars = ctx.vars || {};

    // Handle special stop parameter (from remote shortcut end)
    if (paramName === '__stop__' && value === true) {
      logger.debug('Stop event received via parameter update', {
        executorId: this.executorId,
        controlType: this.controlType,
      });

      // Only handle __stop__ for REMOTE_CONTROLLED executors
      if (this.controlType === ExecutorControlType.REMOTE_CONTROLLED) {
        // Set flag to break Until/While loops (same as local shortcut end)
        this.shouldBreak = true;

        // Also emit shortcut:ended event for compatibility
        const playerService = this.appContext.services.player;
        if (playerService) {
          playerService.emitShortcutEnded({
            executorId: this.executorId,
            shortcutNr: 0,
            shortcutKey: '',
            duration: 0,
          });
        }

        logger.debug('Set shouldBreak=true and emitted shortcut:ended', {
          executorId: this.executorId,
        });
      } else {
        logger.warn('Received __stop__ for non-remote executor', {
          executorId: this.executorId,
          controlType: this.controlType,
        });
      }

      return; // Don't update vars or notify effects
    }

    // Map InputService parameter names to script variable names
    if (paramName === 'targetPos') {
      // targeting is legacy for setting target position, should be freely provided. targeting is for the backend to reconstruct full target if needed
      // if (targeting) {
      //   // Use targeting context to reconstruct full target
      //   switch (targeting.targetType) {
      //     case 'entity':
      //       ctx.vars.target = {
      //         id: targeting.entityId,
      //         currentPosition: value,
      //         position: value,
      //       };
      //       logger.debug('Target updated from targeting context (entity)', {
      //         entityId: targeting.entityId,
      //         position: value,
      //         executorId: this.executorId,
      //       });
      //       break;
      //     case 'block':
      //       ctx.vars.target = {
      //         block: {
      //           position: targeting.blockPosition,
      //         },
      //         position: value,
      //         currentPosition: value,
      //       };
      //       logger.debug('Target updated from targeting context (block)', {
      //         blockPosition: targeting.blockPosition,
      //         position: value,
      //         executorId: this.executorId,
      //       });
      //       break;
      //     case 'ground':
      //       ctx.vars.target = {
      //         position: value,
      //         currentPosition: value,
      //       };
      //       logger.debug('Target updated from targeting context (ground)', {
      //         position: value,
      //         executorId: this.executorId,
      //       });
      //       break;
      //     case 'none':
      //       ctx.vars.target = undefined;
      //       logger.debug('Target cleared (none)', { executorId: this.executorId });
      //       break;
      //   }
      // } else {
        // InputService sends 'targetPos' as Vector3 without targeting context
        // Convert to 'target' with both currentPosition (for entities) and position (for compatibility)
        ctx.vars.target = {
          currentPosition: value,
          position: value,
        };
        logger.debug('Target position updated (legacy)', { position: value, executorId: this.executorId });
//      }
    } else if (paramName === 'sourcePos') {
      // InputService sends 'sourcePos' (maybe player pos) as Vector3
      // Convert to 'source' with both currentPosition and position
      ctx.vars.source = {
        currentPosition: value,
        position: value,
      };
      logger.debug('Source position updated', { position: value, executorId: this.executorId });
    } else {
      // Other parameters stored as-is
      ctx.vars[paramName] = value;
      logger.debug('Parameter updated', { paramName, value, executorId: this.executorId });
    }

    // Notify all running effects that implement onParameterChanged
    for (const [effectId, effect] of this.runningEffects) {
      if (effect.onParameterChanged) {
        try {
          effect.onParameterChanged(paramName, value, ctx);
        } catch (error) {
          logger.error('onParameterChanged failed', { effectId, paramName, error });
        }
      }
    }

    // Note: Server updates are now handled by ShortcutService
    // which sends throttled updates (100ms) for all active shortcuts
  }

  /**
   * Executes StepWhile: Loop while a parallel task is running
   */
  private async execStepWhile(ctx: ScrawlExecContext, step: any): Promise<void> {
    const timeout = step.timeout ?? 60;
    const startTime = performance.now() / 1000;

    // Check if inner step needs player direction (ONLY for local Play steps)
    // Remote effects (isLocal === false) NEVER activate player direction
    const needsPlayerDirection =
      step.step.kind === 'Play' &&
      step.step.receivePlayerDirection === true &&
      ctx.isLocal === true; // Explicit check for true

    if (needsPlayerDirection) {
      // Setup player direction listener
      if (!this.playerDirectionListener) {
        this.setupPlayerDirectionListener();
      }

      // Enable broadcast in PhysicsService
      const physicsService = this.appContext.services.physics;
      if (physicsService) {
        physicsService.setPlayerDirectionBroadcast(true);
        this.playerDirectionBroadcastActive = true;
        logger.debug('Player direction broadcast ENABLED for While effect', {
          effectId: step.step.effectId,
          executorId: this.executorId,
          isLocal: ctx.isLocal,
        });
      }
    } else if (step.step.kind === 'Play' && step.step.receivePlayerDirection && !ctx.isLocal) {
      logger.debug('Skipping player direction for remote While effect', {
        effectId: step.step.effectId,
        isLocal: ctx.isLocal,
      });
    }

    // Get task completion promise
    const taskPromise = this.taskCompletionPromises.get(step.taskId);
    if (!taskPromise) {
      logger.warn(`Task not found: ${step.taskId}`, { scriptId: this.script.id });
      return;
    }

    let taskCompleted = false;
    taskPromise.then(() => {
      taskCompleted = true;
    });

    let effectHandler: any | null = null;

    try {
      const isSteadyEffect = await this.checkIfSteadyEffect(step.step);

      if (isSteadyEffect) {
        // Steady: Execute once, runs until task ends
        effectHandler = await this.executeAndTrackEffect(ctx, step.step);

        // Wait for task completion or timeout
        while (!this.cancelled && !this.shouldBreak && !taskCompleted) {
          const elapsed = performance.now() / 1000 - startTime;
          if (elapsed >= timeout) {
            logger.warn(`While loop timed out after ${timeout}s`, {
              taskId: step.taskId,
              scriptId: this.script.id,
            });
            break;
          }

          // Check if effect finished early
          if (effectHandler && !effectHandler.isRunning()) {
            logger.debug('Steady effect finished early in While loop');
            break;
          }

          await this.sleep(0.1); // 100ms polling
        }
      } else {
        // One-Shot: Execute repeatedly until task ends
        while (!this.cancelled && !this.shouldBreak && !taskCompleted) {
          const elapsed = performance.now() / 1000 - startTime;
          if (elapsed >= timeout) {
            logger.warn(`While loop timed out after ${timeout}s`, {
              taskId: step.taskId,
              scriptId: this.script.id,
            });
            break;
          }

          await this.execStep(ctx, step.step);
          await this.sleep(0.016); // ~60fps
        }
      }
    } finally {
      // Cleanup steady effect
      if (effectHandler) {
        this.stopAndUntrackEffect(effectHandler);
      }

      // Disable player direction broadcast if we enabled it
      if (needsPlayerDirection && this.playerDirectionBroadcastActive) {
        this.cleanupPlayerDirectionListener();
        logger.debug('Player direction broadcast DISABLED after While loop');
      }

      // Reset shouldBreak for next loops in script
      if (this.shouldBreak) {
        logger.debug('While loop ended due to shouldBreak, resetting flag', {
          executorId: this.executorId,
        });
      }
      this.shouldBreak = false;
    }
  }

  /**
   * Executes StepUntil: Loop until an event is emitted
   * Supports parameter updates via updateParameter()
   */
  private async execStepUntil(ctx: ScrawlExecContext, step: any): Promise<void> {
    const timeout = step.timeout ?? 60;
    const startTime = performance.now() / 1000;

    // Check if inner step needs player direction (ONLY for local Play steps)
    // Remote effects (isLocal === false) NEVER activate player direction
    const needsPlayerDirection =
      step.step.kind === 'Play' &&
      step.step.receivePlayerDirection === true &&
      ctx.isLocal === true; // Explicit check for true

    if (needsPlayerDirection) {
      // Setup player direction listener
      if (!this.playerDirectionListener) {
        this.setupPlayerDirectionListener();
      }

      // Enable broadcast in PhysicsService
      const physicsService = this.appContext.services.physics;
      if (physicsService) {
        physicsService.setPlayerDirectionBroadcast(true);
        this.playerDirectionBroadcastActive = true;
        logger.debug('Player direction broadcast ENABLED for Until effect', {
          effectId: step.step.effectId,
          executorId: this.executorId,
          isLocal: ctx.isLocal,
        });
      }
    } else if (step.step.kind === 'Play' && step.step.receivePlayerDirection && !ctx.isLocal) {
      logger.debug('Skipping player direction for remote effect', {
        effectId: step.step.effectId,
        isLocal: ctx.isLocal,
      });
    }

    // Set up event listener
    let eventReceived = false;
    const eventPromise = this.waitEvent(step.event, 0);
    eventPromise.then(() => {
      eventReceived = true;
    });

    let effectHandler: any | null = null;

    try {
      const isSteadyEffect = await this.checkIfSteadyEffect(step.step);

      if (isSteadyEffect) {
        // Steady: Execute once, runs until event
        effectHandler = await this.executeAndTrackEffect(ctx, step.step);

        // Wait for event or timeout
        while (!this.cancelled && !this.shouldBreak && !eventReceived) {
          const elapsed = performance.now() / 1000 - startTime;
          if (elapsed >= timeout) {
            logger.warn(`Until loop timed out after ${timeout}s`, {
              event: step.event,
              scriptId: this.script.id,
            });
            break;
          }

          // Check if effect finished early
          if (effectHandler && !effectHandler.isRunning()) {
            logger.debug('Steady effect finished early in Until loop');
            break;
          }

          await this.sleep(0.1); // 100ms polling
        }
      } else {
        // One-Shot: Execute repeatedly until event
        while (!this.cancelled && !this.shouldBreak && !eventReceived) {
          const elapsed = performance.now() / 1000 - startTime;
          if (elapsed >= timeout) {
            logger.warn(`Until loop timed out after ${timeout}s`, {
              event: step.event,
              scriptId: this.script.id,
            });
            break;
          }

          await this.execStep(ctx, step.step);
          await this.sleep(0.016); // ~60fps
        }
      }
    } finally {
      // Cleanup steady effect
      if (effectHandler) {
        this.stopAndUntrackEffect(effectHandler);
      }

      // Disable player direction broadcast if we enabled it
      if (needsPlayerDirection && this.playerDirectionBroadcastActive) {
        this.cleanupPlayerDirectionListener();
        logger.debug('Player direction broadcast DISABLED after Until loop');
      }

      // Reset shouldBreak for next loops in script
      if (this.shouldBreak) {
        logger.debug('Until loop ended due to shouldBreak, resetting flag', {
          executorId: this.executorId,
        });
      }
      this.shouldBreak = false;
    }
  }

  /**
   * Checks if a step contains a steady effect
   */
  private async checkIfSteadyEffect(step: ScrawlStep): Promise<boolean> {
    if (step.kind !== 'Play') return false;

    try {
      const handler = this.effectFactory.create((step as any).effectId, (step as any).ctx || {});
      return handler.isSteadyEffect();
    } catch (error) {
      logger.error('Failed to check if steady effect', { error });
      return false;
    }
  }

  /**
   * Executes a Play step and tracks the handler
   */
  private async executeAndTrackEffect(ctx: ScrawlExecContext, step: any): Promise<any> {
    // Resolve source and target (like in execStepPlay)
    const source = this.resolveSubject(ctx, step.source);
    const target = this.resolveSubject(ctx, step.target);

    const effectCtx: ScrawlExecContext = {
      ...ctx,
      ...(step.ctx || {}),
    };

    if (source) {
      if (Array.isArray(source)) {
        effectCtx.patients = source;
      } else {
        effectCtx.actor = source;
      }
    }
    if (target) {
      if (Array.isArray(target)) {
        effectCtx.patients = target;
      } else {
        effectCtx.patients = [target];
      }
    }

    // Create and execute effect
    const handler = this.effectFactory.create(step.effectId, step.ctx || {});
    const effectId = `effect_${this.executorId}_${Date.now()}_${Math.random()}`;

    // Register in running effects
    this.runningEffects.set(effectId, handler);

    try {
      const result = handler.execute(effectCtx);
      if (result instanceof Promise) {
        await result;
      }
    } catch (error) {
      // Remove on error
      this.runningEffects.delete(effectId);
      throw error;
    }

    return handler;
  }

  /**
   * Stops an effect and removes it from tracking
   */
  private stopAndUntrackEffect(handler: any): void {
    try {
      if (handler.stop) {
        const result = handler.stop();
        if (result instanceof Promise) {
          result.catch((err: any) => logger.error('Effect stop failed', { err }));
        }
      }
    } finally {
      // Remove from running effects
      for (const [key, h] of this.runningEffects) {
        if (h === handler) {
          this.runningEffects.delete(key);
          break;
        }
      }
    }
  }

  /**
   * Set multiplayer synchronization data
   *
   * Called by ScrawlService when creating the executor.
   *
   * @param effectId Unique effect ID for synchronization
   * @param affectedChunks Chunks affected by this effect
   * @param sendToServer Whether to send parameter updates to server
   */
  setMultiplayerData(
    effectId: string,
    affectedChunks: Array<{ cx: number; cz: number }>,
    sendToServer: boolean
  ): void {
    this.effectId = effectId;
    this.affectedChunks = affectedChunks;
    this.sendToServer = sendToServer;
  }

  /**
   * Get the effect ID for this executor
   */
  getEffectId(): string | undefined {
    return this.effectId;
  }

  getAffectedChunks(): Array<{ cx: number; cz: number }> {
    if (!this.affectedChunks) {
      return [];
    }
    return this.affectedChunks;
  }

}
