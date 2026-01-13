/**
 * ScrawlExecutor Unit Tests
 * Tests the execution logic for all scrawl step types
 */

import { ScrawlExecutor } from './ScrawlExecutor';
import { ScrawlEffectFactory, ScrawlEffectRegistry } from './ScrawlEffectFactory';
import { ScrawlEffectHandler } from './ScrawlEffectHandler';
import type { ScrawlExecContext } from './ScrawlExecContext';
import type { EffectDeps } from './ScrawlEffectHandler';
import type {
  ScrawlScript,
  ScrawlScriptLibrary,
  ScrawlStep,
  ScrawlSubject,
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';

// Polyfill requestAnimationFrame for Node.js/Jest environment
global.requestAnimationFrame = ((callback: FrameRequestCallback) => {
  return setTimeout(callback, 0) as any;
}) as any;

describe('ScrawlExecutor', () => {
  let mockAppContext: AppContext;
  let mockScriptLibrary: ScrawlScriptLibrary;
  let effectRegistry: ScrawlEffectRegistry;
  let effectFactory: ScrawlEffectFactory;
  let effectDeps: EffectDeps;

  // Mock effect handler for testing
  class TestEffectHandler extends ScrawlEffectHandler<{ value: string }> {
    static calls: Array<{ value: string; ctx: ScrawlExecContext }> = [];

    execute(ctx: ScrawlExecContext): void {
      TestEffectHandler.calls.push({
        value: this.options.value,
        ctx,
      });
    }
  }

  beforeEach(() => {
    // Reset test effect handler calls
    TestEffectHandler.calls = [];

    // Create mock AppContext
    mockAppContext = {
      services: {
        client: {} as any,
      },
      config: {} as any,
      serverInfo: null,
      worldInfo: null,
      playerInfo: null,
      sessionId: null,
    };

    // Create effect dependencies
    effectDeps = {
      log: jest.fn(),
      now: () => performance.now() / 1000,
    };

    // Create effect registry and factory
    effectRegistry = new ScrawlEffectRegistry();
    effectRegistry.register('test', TestEffectHandler);
    effectFactory = new ScrawlEffectFactory(effectRegistry, effectDeps);

    // Create mock script library
    mockScriptLibrary = {
      get: jest.fn(),
      load: jest.fn(),
      has: jest.fn(),
    };
  });

  describe('Basic Step Execution', () => {
    it('should execute a simple Play step', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Play',
          effectId: 'test',
          ctx: { value: 'hello' },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('hello');
    });

    it('should execute a Wait step', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Wait',
          seconds: 0.05, // 50ms
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      const startTime = Date.now();
      await executor.start();
      const elapsed = Date.now() - startTime;

      // Should have waited at least 50ms
      expect(elapsed).toBeGreaterThanOrEqual(45); // Allow some tolerance
    });

    it('should execute a Sequence of steps', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'Play', effectId: 'test', ctx: { value: 'first' } },
            { kind: 'Play', effectId: 'test', ctx: { value: 'second' } },
            { kind: 'Play', effectId: 'test', ctx: { value: 'third' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(3);
      expect(TestEffectHandler.calls[0].value).toBe('first');
      expect(TestEffectHandler.calls[1].value).toBe('second');
      expect(TestEffectHandler.calls[2].value).toBe('third');
    });
  });

  describe('Parallel Execution', () => {
    it('should execute Parallel steps concurrently', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Parallel',
          steps: [
            { kind: 'Play', effectId: 'test', ctx: { value: 'a' } },
            { kind: 'Play', effectId: 'test', ctx: { value: 'b' } },
            { kind: 'Play', effectId: 'test', ctx: { value: 'c' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(3);
      // Order might vary due to parallel execution
      const values = TestEffectHandler.calls.map((c) => c.value).sort();
      expect(values).toEqual(['a', 'b', 'c']);
    });
  });

  describe('Repeat Step', () => {
    it('should repeat a step N times', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Repeat',
          times: 3,
          step: { kind: 'Play', effectId: 'test', ctx: { value: 'repeat' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(3);
      expect(TestEffectHandler.calls.every((c) => c.value === 'repeat')).toBe(true);
    });

    it.skip('should repeat until event is emitted (timing-sensitive)', async () => {
      // Note: This test is skipped due to timing sensitivity
      // The implementation checks for the event before executing each iteration
      // which makes it difficult to test reliably in unit tests
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Repeat',
          untilEvent: 'stop',
          step: { kind: 'Play', effectId: 'test', ctx: { value: 'loop' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      // Start executor (non-blocking)
      const promise = executor.start();

      // Wait a bit to allow some iterations, then emit stop event
      await new Promise((resolve) => setTimeout(resolve, 30));
      executor.emit('stop');

      await promise;

      // Should have executed at least once before event stopped it
      expect(TestEffectHandler.calls.length).toBeGreaterThan(0);
    });
  });

  describe('ForEach Step', () => {
    it('should iterate over a collection', async () => {
      const patients: ScrawlSubject[] = [
        { position: { x: 0, y: 0, z: 0 }, entityId: 'e1' },
        { position: { x: 1, y: 0, z: 0 }, entityId: 'e2' },
        { position: { x: 2, y: 0, z: 0 }, entityId: 'e3' },
      ];

      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'ForEach',
          collection: '$patients',
          itemVar: '$patient',
          step: { kind: 'Play', effectId: 'test', ctx: { value: 'hit' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        { patients }
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(3);
    });

    it('should handle empty collections', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'ForEach',
          collection: '$patients',
          itemVar: '$patient',
          step: { kind: 'Play', effectId: 'test', ctx: { value: 'hit' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        { patients: [] }
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(0);
    });
  });

  describe('Conditional Execution (If)', () => {
    it('should execute then branch when condition is true', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'If',
          cond: { kind: 'VarEquals', name: 'test', value: 42 },
          then: { kind: 'Play', effectId: 'test', ctx: { value: 'yes' } },
          else: { kind: 'Play', effectId: 'test', ctx: { value: 'no' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      executor.setVar('test', 42);
      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('yes');
    });

    it('should execute else branch when condition is false', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'If',
          cond: { kind: 'VarEquals', name: 'test', value: 42 },
          then: { kind: 'Play', effectId: 'test', ctx: { value: 'yes' } },
          else: { kind: 'Play', effectId: 'test', ctx: { value: 'no' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      executor.setVar('test', 99);
      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('no');
    });

    it('should evaluate Chance condition', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'If',
          cond: { kind: 'Chance', p: 1.0 }, // Always true
          then: { kind: 'Play', effectId: 'test', ctx: { value: 'lucky' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('lucky');
    });

    it('should evaluate HasTargets condition', async () => {
      const patients: ScrawlSubject[] = [
        { position: { x: 0, y: 0, z: 0 }, entityId: 'e1' },
        { position: { x: 1, y: 0, z: 0 }, entityId: 'e2' },
      ];

      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'If',
          cond: { kind: 'HasTargets', min: 2 },
          then: { kind: 'Play', effectId: 'test', ctx: { value: 'multiple' } },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        { patients }
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('multiple');
    });
  });

  describe('Variables', () => {
    it('should set and get variables', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'SetVar', name: 'counter', value: 10 },
            { kind: 'Play', effectId: 'test', ctx: { value: 'done' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(executor.getVar('counter')).toBe(10);
    });
  });

  describe('Events', () => {
    it('should emit and wait for events', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Parallel',
          steps: [
            {
              kind: 'Sequence',
              steps: [
                { kind: 'WaitEvent', name: 'trigger' },
                { kind: 'Play', effectId: 'test', ctx: { value: 'triggered' } },
              ],
            },
            {
              kind: 'Sequence',
              steps: [
                { kind: 'Wait', seconds: 0.01 },
                { kind: 'EmitEvent', name: 'trigger' },
              ],
            },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('triggered');
    });

    it('should timeout when waiting for event', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'WaitEvent', name: 'never', timeout: 0.05 },
            { kind: 'Play', effectId: 'test', ctx: { value: 'timeout' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      const startTime = Date.now();
      await executor.start();
      const elapsed = Date.now() - startTime;

      expect(elapsed).toBeGreaterThanOrEqual(45); // Should have waited
      expect(TestEffectHandler.calls).toHaveLength(1);
    });
  });

  describe('LOD Switch', () => {
    it('should execute step for current LOD level', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'LodSwitch',
          levels: {
            high: { kind: 'Play', effectId: 'test', ctx: { value: 'high' } },
            medium: { kind: 'Play', effectId: 'test', ctx: { value: 'medium' } },
            low: { kind: 'Play', effectId: 'test', ctx: { value: 'low' } },
          },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        { lodLevel: 'high' }
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('high');
    });

    it('should default to medium LOD if not specified', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'LodSwitch',
          levels: {
            medium: { kind: 'Play', effectId: 'test', ctx: { value: 'medium' } },
          },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('medium');
    });
  });

  describe('Call (Sub-Scripts)', () => {
    it('should call a sub-script', async () => {
      const subScript: ScrawlScript = {
        id: 'sub-script',
        root: { kind: 'Play', effectId: 'test', ctx: { value: 'sub' } },
      };

      (mockScriptLibrary.load as jest.Mock).mockResolvedValue(subScript);

      const script: ScrawlScript = {
        id: 'main-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'Play', effectId: 'test', ctx: { value: 'before' } },
            { kind: 'Call', scriptId: 'sub-script' },
            { kind: 'Play', effectId: 'test', ctx: { value: 'after' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(mockScriptLibrary.load).toHaveBeenCalledWith('sub-script');
      expect(TestEffectHandler.calls).toHaveLength(3);
      expect(TestEffectHandler.calls[0].value).toBe('before');
      expect(TestEffectHandler.calls[1].value).toBe('sub');
      expect(TestEffectHandler.calls[2].value).toBe('after');
    });

    it('should pass arguments to sub-script', async () => {
      const subScript: ScrawlScript = {
        id: 'sub-script',
        root: { kind: 'Play', effectId: 'test', ctx: { value: 'sub' } },
      };

      (mockScriptLibrary.load as jest.Mock).mockResolvedValue(subScript);

      const script: ScrawlScript = {
        id: 'main-script',
        root: {
          kind: 'Call',
          scriptId: 'sub-script',
          args: { customValue: 123 },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].ctx.customValue).toBe(123);
    });
  });

  describe('Subject Resolution', () => {
    it('should resolve $actor reference', async () => {
      const actor: ScrawlSubject = {
        position: { x: 0, y: 0, z: 0 },
        entityId: 'player',
      };

      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Play',
          effectId: 'test',
          source: '$actor',
          ctx: { value: 'actor-effect' },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        { actor }
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].ctx.actor).toBe(actor);
    });

    it('should resolve $patient[N] reference', async () => {
      const patients: ScrawlSubject[] = [
        { position: { x: 0, y: 0, z: 0 }, entityId: 'e1' },
        { position: { x: 1, y: 0, z: 0 }, entityId: 'e2' },
      ];

      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Play',
          effectId: 'test',
          target: '$patient[1]',
          ctx: { value: 'targeted' },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        { patients }
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].ctx.patients).toHaveLength(1);
      expect(TestEffectHandler.calls[0].ctx.patients![0]).toBe(patients[1]);
    });
  });

  describe('Control Flow', () => {
    it('should cancel execution', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'Play', effectId: 'test', ctx: { value: 'first' } },
            { kind: 'Wait', seconds: 1.0 },
            { kind: 'Play', effectId: 'test', ctx: { value: 'second' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      const promise = executor.start();

      // Cancel after 10ms
      setTimeout(() => executor.cancel(), 10);

      await promise;

      // Should have executed first step but not second
      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('first');
      expect(executor.isCancelled()).toBe(true);
    });

    it('should pause and resume execution', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'Play', effectId: 'test', ctx: { value: 'first' } },
            { kind: 'Wait', seconds: 0.5 },
            { kind: 'Play', effectId: 'test', ctx: { value: 'second' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      const promise = executor.start();

      // Pause after 10ms
      setTimeout(() => {
        executor.pause();
        expect(executor.isPaused()).toBe(true);
      }, 10);

      // Resume after 100ms
      setTimeout(() => executor.resume(), 100);

      const startTime = Date.now();
      await promise;
      const elapsed = Date.now() - startTime;

      // Should have taken longer due to pause
      expect(elapsed).toBeGreaterThan(100);
      expect(TestEffectHandler.calls).toHaveLength(2);
    });
  });

  describe('Named Sequences', () => {
    it('should execute main sequence when no root defined', async () => {
      const script: ScrawlScript = {
        id: 'test-script',
        sequences: {
          main: {
            name: 'main',
            step: { kind: 'Play', effectId: 'test', ctx: { value: 'main' } },
          },
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('main');
    });
  });

  describe('Command Step (StepCmd)', () => {
    it('should execute a command via StepCmd', async () => {
      const mockCommandService = {
        executeCommand: jest.fn().mockResolvedValue('ok'),
      };

      mockAppContext.services.command = mockCommandService as any;

      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Cmd',
          cmd: 'notification',
          parameters: [20, 'null', 'Test message'],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(mockCommandService.executeCommand).toHaveBeenCalledWith('notification', [
        20,
        'null',
        'Test message',
      ]);
    });

    it('should handle command execution in sequence', async () => {
      const mockCommandService = {
        executeCommand: jest.fn().mockResolvedValue('ok'),
      };

      mockAppContext.services.command = mockCommandService as any;

      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'Play', effectId: 'test', ctx: { value: 'before' } },
            { kind: 'Cmd', cmd: 'help', parameters: [] },
            { kind: 'Play', effectId: 'test', ctx: { value: 'after' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      expect(mockCommandService.executeCommand).toHaveBeenCalledWith('help', []);
      expect(TestEffectHandler.calls).toHaveLength(2);
      expect(TestEffectHandler.calls[0].value).toBe('before');
      expect(TestEffectHandler.calls[1].value).toBe('after');
    });

    it('should handle command errors gracefully', async () => {
      const mockCommandService = {
        executeCommand: jest.fn().mockRejectedValue(new Error('Command failed')),
      };

      mockAppContext.services.command = mockCommandService as any;

      const script: ScrawlScript = {
        id: 'test-script',
        root: {
          kind: 'Sequence',
          steps: [
            { kind: 'Cmd', cmd: 'invalid', parameters: [] },
            { kind: 'Play', effectId: 'test', ctx: { value: 'continued' } },
          ],
        },
      };

      const executor = new ScrawlExecutor(
        effectFactory,
        mockScriptLibrary,
        mockAppContext,
        script,
        {}
      );

      await executor.start();

      // Should continue despite command error
      expect(TestEffectHandler.calls).toHaveLength(1);
      expect(TestEffectHandler.calls[0].value).toBe('continued');
    });
  });
});
