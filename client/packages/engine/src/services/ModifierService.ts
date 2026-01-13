/**
 * ModifierService - Manages modifier stacks
 *
 * A modifier system that allows multiple values to override a base value with priorities.
 * Higher priority modifiers override lower priority ones.
 * When modifiers have the same priority, the most recently created one wins.
 */

import { getLogger, ExceptionHandler } from '@nimbus/shared';

// Export animation helpers for convenience
export * from './AnimationHelpers';

const logger = getLogger('ModifierService');

/** Maximum priority value for default modifiers */
const MAX_PRIORITY = Number.MAX_SAFE_INTEGER;

/**
 * Enum für alle zentral verwalteten Stack-Namen
 *
 * WICHTIG: Alle neuen StackModifier müssen hier registriert werden!
 *
 * Workflow für neue Stacks:
 * 1. Stack-Namen hier als Enum-Wert hinzufügen
 * 2. Stack in StackModifierCreator.createAllStackModifiers() erstellen
 * 3. In Service nur noch per getModifierStack(StackName.XXX) holen
 *
 * Vorteile:
 * - Typsicherheit (keine Tippfehler)
 * - Zentrale Initialisierung garantiert Verfügbarkeit
 * - IDE-Autovervollständigung
 * - Alle Stacks an einem Ort dokumentiert
 *
 * @example
 * // Im Creator (StackModifierCreator.ts):
 * modifierService.createModifierStack<boolean>(
 *   StackName.PLAYER_VIEW_MODE,
 *   true,
 *   (value) => {
 *     const playerService = appContext.services.player;
 *     if (playerService) playerService.onViewModeChanged(value);
 *   }
 * );
 *
 * @example
 * // Im Service (z.B. PlayerService.ts):
 * get viewModeStack(): ModifierStack<boolean> | undefined {
 *   return this.appContext.services.modifier?.getModifierStack<boolean>(
 *     StackName.PLAYER_VIEW_MODE
 *   );
 * }
 */
export enum StackName {
  /** Player view mode: true = ego-view (first-person), false = third-person */
  PLAYER_VIEW_MODE = 'playerViewMode',

  /** Player movement state: WALK, SPRINT, JUMP, FALL, FLY, SWIM, CROUCH, RIDING */
  PLAYER_MOVEMENT_STATE = 'playerMovementState',

  /** Player pose: Animation/pose to display (e.g., 'idle', 'walk', 'attack', 'use') */
  PLAYER_POSE = 'playerPose',

  /** Fog view mode: number intensity (0 = disabled, 0.1-1.0 = fog intensity) */
  FOG_VIEW_MODE = 'fogViewMode',

  /** Ambient audio: string path to ambient music file (empty = no music) */
  AMBIENT_AUDIO = 'ambientAudio',

  /** Ambient light intensity: number (0-10, default: 1.0) */
  AMBIENT_LIGHT_INTENSITY = 'ambientLightIntensity',

  /** Sun position: number angle in degrees (0-360, 0=North, 90=East, 180=South, 270=West) */
  SUN_POSITION = 'sunPosition',

  /** Sun elevation: number angle in degrees (-90 to 90, -90=down, 0=horizon, 90=up) */
  SUN_ELEVATION = 'sunElevation',

  /** Moon 0 position: number angle in degrees (0-360, 0=North, 90=East, 180=South, 270=West) */
  MOON_0_POSITION = 'moon0Position',

  /** Moon 1 position: number angle in degrees (0-360, 0=North, 90=East, 180=South, 270=West) */
  MOON_1_POSITION = 'moon1Position',

  /** Moon 2 position: number angle in degrees (0-360, 0=North, 90=East, 180=South, 270=West) */
  MOON_2_POSITION = 'moon2Position',

  /** Horizon gradient alpha: number (0-1, transparency) */
  HORIZON_GRADIENT_ALPHA = 'horizonGradientAlpha',

  /** Sun light intensity multiplier: number (0-10, default: 1.0) */
  SUN_LIGHT_INTENSITY_MULTIPLIER = 'sunLightIntensityMultiplier',

  /** Ambient light intensity multiplier: number (0-10, default: 0.5) */
  AMBIENT_LIGHT_INTENSITY_MULTIPLIER = 'ambientLightIntensityMultiplier',

  // Weitere Stacks hier hinzufügen
}

/**
 * Modifier - A value that can be applied to a ModifierStack
 * @template T The type of the value
 */
export class Modifier<T> {
  private static _sequenceCounter = 0;
  private _value: T;
  private readonly _prio: number;
  private readonly _created: number;
  private readonly _sequence: number;
  private readonly _stack: ModifierStack<T>;
  private _enabled: boolean = true;

  /**
   * Create a new modifier
   * @param value The initial value
   * @param prio The priority (higher values win)
   * @param stack The owning stack
   */
  constructor(value: T, prio: number, stack: ModifierStack<T>) {
    this._value = value;
    this._prio = prio;
    this._created = Date.now();
    this._sequence = Modifier._sequenceCounter++;
    this._stack = stack;
  }

  /**
   * Get the current value
   */
  get value(): T {
    return this._value;
  }

  /**
   * Get the current value (alias for value getter)
   */
  getValue(): T {
    return this._value;
  }

  /**
   * Set a new value
   * @param value The new value
   */
  setValue(value: T): void {
    this._value = value;
    this._stack.update(false);
  }

  /**
   * Get the priority
   */
  get prio(): number {
    return this._prio;
  }

  /**
   * Get the creation timestamp
   */
  get created(): number {
    return this._created;
  }

  /**
   * Get the sequence number (for ordering within same priority)
   */
  get sequence(): number {
    return this._sequence;
  }

  /**
   * Get enabled state
   */
  get enabled(): boolean {
    return this._enabled;
  }

  /**
   * Set enabled state
   * When disabled, the modifier is ignored in stack calculations
   */
  setEnabled(enabled: boolean): void {
    if (this._enabled !== enabled) {
      this._enabled = enabled;
      this._stack.update(false);
    }
  }

  /**
   * Close this modifier (remove from stack)
   */
  close(): void {
    this._stack.removeModifier(this);
  }
}

/**
 * ModifierStack - Manages a prioritized list of modifiers
 * @template T The type of the values
 */
export class ModifierStack<T> {
  private readonly _stackName: string;
  private readonly _defaultModifier: Modifier<T>;
  private readonly _action: (value: T) => void;
  private readonly _modifiers: Modifier<T>[] = [];
  private _currentValue: T;
  private readonly _service: ModifierService;

  /**
   * Create a new modifier stack
   * @param stackName The name of this stack
   * @param defaultValue The default value (fallback)
   * @param action The action to execute when the value changes
   * @param service The ModifierService that owns this stack
   */
  constructor(stackName: string, defaultValue: T, action: (value: T) => void, service: ModifierService) {
    this._stackName = stackName;
    this._action = action;
    this._service = service;
    this._defaultModifier = new Modifier(defaultValue, MAX_PRIORITY, this);
    this._currentValue = defaultValue;
  }

  /**
   * Get the stack name
   */
  get stackName(): string {
    return this._stackName;
  }

  /**
   * Get the default modifier
   */
  getDefaultModifier(): Modifier<T> {
    return this._defaultModifier;
  }

  /**
   * Add a modifier to the stack
   * @param value The value
   * @param prio The priority
   * @returns The created modifier
   */
  addModifier(value: T, prio: number): Modifier<T> {
    const modifier = new Modifier(value, prio, this);
    this._modifiers.push(modifier);
    this.update(false);
    return modifier;
  }

  /**
   * Remove a modifier from the stack
   * @param modifier The modifier to remove
   */
  removeModifier(modifier: Modifier<T>): void {
    const index = this._modifiers.indexOf(modifier);
    if (index !== -1) {
      this._modifiers.splice(index, 1);
      this.update(false);
    }
  }

  /**
   * Update the current value
   * @param force Force the action to execute even if the value hasn't changed
   */
  update(force: boolean): void {
    try {
      const newValue = this._calculateValue();
      const valueChanged = newValue !== this._currentValue;

      if (valueChanged || force) {
        this._currentValue = newValue;
        this._action(newValue);

        logger.debug('ModifierStack updated', {
          stackName: this._stackName,
          newValue,
          forced: force,
          modifierCount: this._modifiers.length,
        });
      }
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ModifierStack.update',
        { stackName: this._stackName, force }
      );
    }
  }

  /**
   * Calculate the current value by selecting the highest priority modifier
   * @returns The current value
   */
  private _calculateValue(): T {
    // Filter out disabled modifiers
    const enabledModifiers = this._modifiers.filter(m => m.enabled);

    // If no enabled modifiers, use default
    if (enabledModifiers.length === 0) {
      return this._defaultModifier.value;
    }

    // Sort by priority (descending), then by sequence (descending for newest first)
    // Lower priority value = higher priority, so we sort ascending by prio
    const sorted = [...enabledModifiers, this._defaultModifier].sort((a, b) => {
      if (a.prio !== b.prio) {
        return a.prio - b.prio; // Lower prio value = higher priority
      }
      // Use sequence number to ensure newest wins (higher sequence = newer)
      return b.sequence - a.sequence;
    });

    // The first one wins
    return sorted[0].value;
  }

  /**
   * Get the current value
   */
  get currentValue(): T {
    return this._currentValue;
  }

  /**
   * Set the current value directly (without modifiers)
   * This bypasses the modifier system and sets the value immediately.
   * Used for initialization to avoid animation from default to initial value.
   * @param value The value to set
   */
  setCurrentValue(value: T): void {
    this._currentValue = value;
    try {
      this._action(value);
    } catch (error) {
      ExceptionHandler.handle(error, 'ModifierStack.setCurrentValue', {
        stackName: this._stackName,
        value,
      });
    }
  }

  /**
   * Get the current value (alias for currentValue getter)
   */
  getValue(): T {
    return this._currentValue;
  }

  /**
   * Get all modifiers (excluding default)
   */
  get modifiers(): readonly Modifier<T>[] {
    return this._modifiers;
  }

  /**
   * Dispose the stack
   * Clears all modifiers and removes itself from the ModifierService
   */
  dispose(): void {
    this._modifiers.length = 0;
    this._service._removeStackFromMap(this._stackName);
  }
}

/**
 * Animation step function - returns the next value and whether to continue
 * @param current The current value
 * @param target The target value
 * @returns [nextValue, shouldContinue]
 */
export type AnimationStepFunction<T> = (current: T, target: T) => [T, boolean];

/**
 * AnimationModifier - A modifier that animates value changes over time
 * @template T The type of the value
 */
export class AnimationModifier<T> extends Modifier<T> {
  private _targetValue: T;
  private _animationTimeout: NodeJS.Timeout | null = null;
  private readonly _stepFunction: AnimationStepFunction<T>;
  private _waitTime: number;

  /**
   * Create a new animation modifier
   * @param value The initial value
   * @param prio The priority (higher values win)
   * @param stack The owning stack
   * @param stepFunction Function that calculates the next animation step
   * @param waitTime Time in milliseconds between animation steps (default: 100ms)
   */
  constructor(
    value: T,
    prio: number,
    stack: AnimationStack<T>,
    stepFunction: AnimationStepFunction<T>,
    waitTime: number = 100
  ) {
    super(value, prio, stack);
    this._targetValue = value;
    this._stepFunction = stepFunction;
    this._waitTime = waitTime;
  }

  /**
   * Get the target value
   */
  get targetValue(): T {
    return this._targetValue;
  }

  /**
   * Set a new target value and start animation
   * @param value The new target value
   * @param waitTime Optional new wait time in milliseconds
   */
  override setValue(value: T, waitTime?: number): void {
    this._targetValue = value;
    if (waitTime !== undefined) {
      this._waitTime = waitTime;
    }
    this._startAnimation();
  }

  /**
   * Get the wait time
   */
  get waitTime(): number {
    return this._waitTime;
  }

  /**
   * Set the wait time
   * @param waitTime The new wait time in milliseconds
   */
  setWaitTime(waitTime: number): void {
    this._waitTime = waitTime;
  }

  /**
   * Start the animation loop
   */
  private _startAnimation(): void {
    // Clear any existing animation
    if (this._animationTimeout) {
      clearTimeout(this._animationTimeout);
      this._animationTimeout = null;
    }

    // Perform animation step
    this._animationStep();
  }

  /**
   * Perform one animation step
   */
  private _animationStep(): void {
    const [nextValue, shouldContinue] = this._stepFunction(this.value, this._targetValue);

    // Update the current value using the parent class method
    super.setValue(nextValue);

    // Continue animation if needed
    if (shouldContinue && nextValue !== this._targetValue) {
      this._animationTimeout = setTimeout(() => {
        this._animationStep();
      }, this._waitTime);
    }
  }

  /**
   * Stop the animation
   */
  stopAnimation(): void {
    if (this._animationTimeout) {
      clearTimeout(this._animationTimeout);
      this._animationTimeout = null;
    }
  }

  /**
   * Close this modifier (remove from stack and stop animation)
   */
  override close(): void {
    this.stopAnimation();
    super.close();
  }
}

/**
 * AnimationStack - Manages a prioritized list of animation modifiers
 * @template T The type of the values
 */
export class AnimationStack<T> extends ModifierStack<T> {
  private readonly _stepFunction: AnimationStepFunction<T>;
  private readonly _defaultWaitTime: number;

  /**
   * Create a new animation stack
   * @param stackName The name of this stack
   * @param defaultValue The default value (fallback)
   * @param action The action to execute when the value changes
   * @param service The ModifierService that owns this stack
   * @param stepFunction Function that calculates the next animation step
   * @param defaultWaitTime Default wait time in milliseconds (default: 100ms)
   */
  constructor(
    stackName: string,
    defaultValue: T,
    action: (value: T) => void,
    service: ModifierService,
    stepFunction: AnimationStepFunction<T>,
    defaultWaitTime: number = 100
  ) {
    super(stackName, defaultValue, action, service);
    this._stepFunction = stepFunction;
    this._defaultWaitTime = defaultWaitTime;
  }

  /**
   * Add an animation modifier to the stack
   * @param targetValue The target value to animate to
   * @param prio The priority
   * @param waitTime Optional wait time in milliseconds (uses default if not specified)
   * @returns The created animation modifier
   */
  addAnimationModifier(targetValue: T, prio: number, waitTime?: number): AnimationModifier<T> {
    // Start from current stack value for smooth transition
    const currentValue = this.getValue();

    const modifier = new AnimationModifier(
      currentValue, // Start from current value
      prio,
      this,
      this._stepFunction,
      waitTime ?? this._defaultWaitTime
    );

    (this as any)._modifiers.push(modifier);
    this.update(false);

    // Now animate to target value
    modifier.setValue(targetValue, waitTime);

    return modifier;
  }

  /**
   * Add a modifier to the stack (overridden to create AnimationModifier)
   * @param value The value
   * @param prio The priority
   * @returns The created modifier
   */
  override addModifier(value: T, prio: number): AnimationModifier<T> {
    return this.addAnimationModifier(value, prio);
  }
}

/**
 * Modifier configuration for creating a modifier
 */
export interface ModifierConfig<T> {
  /** The value */
  value: T;
  /** The priority (lower values = higher priority) */
  prio: number;
  /** Optional wait time for AnimationStack (in milliseconds) */
  waitTime?: number;
}

/**
 * ModifierService - Central service for managing modifier stacks
 */
export class ModifierService {
  private readonly stackModifiers = new Map<string, ModifierStack<any>>();

  /**
   * Create a new modifier stack
   * @param stackName The name of the stack
   * @param defaultValue The default value
   * @param action The action to execute when the value changes
   * @returns The created modifier stack
   */
  createModifierStack<T>(
    stackName: string,
    defaultValue: T,
    action: (value: T) => void
  ): ModifierStack<T> {
    try {
      if (this.stackModifiers.has(stackName)) {
        throw new Error(`ModifierStack '${stackName}' already exists`);
      }

      const stack = new ModifierStack(stackName, defaultValue, action, this);
      this.stackModifiers.set(stackName, stack);

      // Execute action immediately with default value
      action(defaultValue);

      logger.debug('ModifierStack created', {
        stackName,
        defaultValue,
      });

      return stack;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ModifierService.createModifierStack',
        { stackName, defaultValue }
      );
    }
  }

  /**
   * Create a new animation stack
   * @param stackName The name of the stack
   * @param defaultValue The default value
   * @param action The action to execute when the value changes
   * @param stepFunction Function that calculates the next animation step
   * @param defaultWaitTime Default wait time in milliseconds (default: 100ms)
   * @returns The created animation stack
   */
  createAnimationStack<T>(
    stackName: string,
    defaultValue: T,
    action: (value: T) => void,
    stepFunction: AnimationStepFunction<T>,
    defaultWaitTime: number = 100
  ): AnimationStack<T> {
    try {
      if (this.stackModifiers.has(stackName)) {
        throw new Error(`AnimationStack '${stackName}' already exists`);
      }

      const stack = new AnimationStack(
        stackName,
        defaultValue,
        action,
        this,
        stepFunction,
        defaultWaitTime
      );
      this.stackModifiers.set(stackName, stack);

      // Execute action immediately with default value
      action(defaultValue);

      logger.debug('AnimationStack created', {
        stackName,
        defaultValue,
        defaultWaitTime,
      });

      return stack;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ModifierService.createAnimationStack',
        { stackName, defaultValue, defaultWaitTime }
      );
    }
  }

  /**
   * Get or create a modifier stack
   * @param stackName The name of the stack
   * @param defaultValue The default value (only used if stack doesn't exist)
   * @param action The action to execute when the value changes (only used if stack doesn't exist)
   * @returns The existing or newly created modifier stack
   */
  getOrCreateModifierStack<T>(
    stackName: string,
    defaultValue: T,
    action: (value: T) => void
  ): ModifierStack<T> {
    const existing = this.stackModifiers.get(stackName);
    if (existing) {
      return existing as ModifierStack<T>;
    }

    return this.createModifierStack(stackName, defaultValue, action);
  }

  /**
   * Add a modifier to a stack
   * @param stackName The name of the stack
   * @param config The modifier configuration
   * @returns The created modifier
   */
  addModifier<T>(stackName: string, config: ModifierConfig<T>): Modifier<T> | AnimationModifier<T> {
    try {
      const stack = this.stackModifiers.get(stackName);
      if (!stack) {
        throw new Error(`ModifierStack '${stackName}' does not exist`);
      }

      // Check if it's an AnimationStack and use waitTime if provided
      if (stack instanceof AnimationStack && config.waitTime !== undefined) {
        return stack.addAnimationModifier(config.value, config.prio, config.waitTime);
      }

      return stack.addModifier(config.value, config.prio);
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ModifierService.addModifier',
        { stackName, config }
      );
    }
  }

  /**
   * Remove a modifier (called by Modifier.close())
   * @param modifier The modifier to remove
   */
  removeModifier<T>(modifier: Modifier<T>): void {
    // Modifier.close() calls stack.removeModifier() directly
    // This method is here for API completeness but not strictly needed
  }

  /**
   * Get a modifier stack
   * @param stackName The name of the stack
   * @returns The modifier stack or undefined
   */
  getModifierStack<T>(stackName: string): ModifierStack<T> | undefined {
    return this.stackModifiers.get(stackName);
  }

  /**
   * Remove a stack
   * @param stackName The name of the stack
   */
  removeStack(stackName: string): void {
    try {
      const stack = this.stackModifiers.get(stackName);
      if (stack) {
        stack.dispose();
        this.stackModifiers.delete(stackName);

        logger.debug('ModifierStack removed', { stackName });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'ModifierService.removeStack', { stackName });
    }
  }

  /**
   * Check if a stack exists
   * @param stackName The name of the stack
   * @returns True if the stack exists
   */
  hasStack(stackName: string): boolean {
    return this.stackModifiers.has(stackName);
  }

  /**
   * Get all stack names
   */
  get stackNames(): string[] {
    return Array.from(this.stackModifiers.keys());
  }

  /**
   * Dispose all stacks
   */
  dispose(): void {
    for (const stack of this.stackModifiers.values()) {
      stack.dispose();
    }
    this.stackModifiers.clear();
    logger.debug('ModifierService disposed');
  }

  /**
   * Internal method to remove a stack from the map
   * Called by ModifierStack.dispose()
   * @internal
   */
  _removeStackFromMap(stackName: string): void {
    this.stackModifiers.delete(stackName);
  }
}
