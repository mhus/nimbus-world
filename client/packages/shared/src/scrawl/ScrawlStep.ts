import { ScrawlCondition } from './ScrawlCondition';
import { ScrawlLodLevel } from './ScrawlTypes';

/**
 * Step: Play an effect
 */
export interface StepPlay {
  kind: 'Play';
  /** Effect ID to play */
  effectId: string;
  /** Context data passed to the effect (including source, target, etc.) */
  ctx?: Record<string, any>;
  /** Whether this effect should receive player direction updates (for effects like beam:follow) */
  receivePlayerDirection?: boolean;
}

/**
 * Step: Wait for a specified duration
 */
export interface StepWait {
  kind: 'Wait';
  /** Duration in seconds */
  seconds: number; // javaType: int
}

/**
 * Step: Execute steps sequentially
 */
export interface StepSequence {
  kind: 'Sequence';
  /** Steps to execute in order */
  steps: ScrawlStep[];
}

/**
 * Step: Execute steps in parallel
 */
export interface StepParallel {
  kind: 'Parallel';
  /** Steps to execute concurrently */
  steps: ScrawlStep[];
}

/**
 * Step: Repeat a step multiple times or until an event
 */
export interface StepRepeat {
  kind: 'Repeat';
  /** Number of times to repeat (if specified) */
  times?: number | null; // javaType: int
  /** Event name to wait for as termination condition (if specified) */
  untilEvent?: string | null;
  /** Step to repeat */
  step: ScrawlStep;
}

/**
 * Step: Iterate over subjects (e.g., patients)
 */
export interface StepForEach {
  kind: 'ForEach';
  /** Collection to iterate over (e.g., "$patients") */
  collection: string;
  /** Variable name for current item (e.g., "$patient") */
  itemVar: string;
  /** Step to execute for each item */
  step: ScrawlStep;
}

/**
 * Step: LOD-based step selection
 */
export interface StepLodSwitch {
  kind: 'LodSwitch';
  /** Steps for different LOD levels */
  levels: Partial<Record<ScrawlLodLevel, ScrawlStep>>; // javaType: java.util.Map<String,ScrawlStep>
}

/**
 * Step: Call a sub-sequence or imported script
 */
export interface StepCall {
  kind: 'Call';
  /** Script or sequence ID to call */
  scriptId: string;
  /** Arguments to pass to the called script */
  args?: Record<string, any>;
}

/**
 * Step: Conditional execution
 */
export interface StepIf {
  kind: 'If';
  /** Condition to evaluate */
  cond: ScrawlCondition;
  /** Step to execute if condition is true */
  then: ScrawlStep;
  /** Step to execute if condition is false (optional) */
  else?: ScrawlStep;
}

/**
 * Step: Emit an event
 */
export interface StepEmitEvent {
  kind: 'EmitEvent';
  /** Event name */
  name: string;
  /** Event payload (optional) */
  payload?: any;
}

/**
 * Step: Wait for an event
 */
export interface StepWaitEvent {
  kind: 'WaitEvent';
  /** Event name to wait for */
  name: string;
  /** Timeout in seconds (optional, 0 = no timeout) */
  timeout?: number; // javaType: long
}

/**
 * Step: Set a variable in the context
 */
export interface StepSetVar {
  kind: 'SetVar';
  /** Variable name */
  name: string;
  /** Value to set */
  value: any;
}

/**
 * Step: Execute a command via CommandService
 */
export interface StepCmd {
  kind: 'Cmd';
  /** Command name to execute */
  cmd: string;
  /** Parameters to pass to the command */
  parameters?: any[];
}

/**
 * Step: Loop while a parallel task is running
 * Terminates automatically when the referenced task completes
 */
export interface StepWhile {
  kind: 'While';
  /** Task ID from a parallel step */
  taskId: string;
  /** Step to execute repeatedly while task runs */
  step: ScrawlStep;
  /** Safety timeout in seconds (default: 60) */
  timeout?: number; // javaType: long
}

/**
 * Step: Loop until an event is emitted
 * Supports dynamic parameter updates via updateParameter()
 */
export interface StepUntil {
  kind: 'Until';
  /** Event name that terminates the loop */
  event: string;
  /** Step to execute repeatedly until event */
  step: ScrawlStep;
  /** Safety timeout in seconds (default: 60) */
  timeout?: number; // javaType: long
}

/**
 * Union type of all possible steps
 */
export type ScrawlStep =
  | StepPlay
  | StepWait
  | StepSequence
  | StepParallel
  | StepRepeat
  | StepWhile
  | StepUntil
  | StepForEach
  | StepLodSwitch
  | StepCall
  | StepIf
  | StepEmitEvent
  | StepWaitEvent
  | StepSetVar
  | StepCmd;
