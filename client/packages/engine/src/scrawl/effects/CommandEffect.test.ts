/**
 * CommandEffect Unit Tests
 */

import { CommandEffect } from './CommandEffect';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import type { EffectDeps } from '../ScrawlEffectHandler';
import type { CommandService } from '../../services/CommandService';

describe('CommandEffect', () => {
  let mockDeps: EffectDeps;
  let mockContext: ScrawlExecContext;
  let mockCommandService: jest.Mocked<CommandService>;

  beforeEach(() => {
    mockDeps = {
      log: jest.fn(),
      now: () => 0,
    };

    mockCommandService = {
      executeCommand: jest.fn().mockResolvedValue('command result'),
    } as any;

    mockContext = {
      appContext: {
        services: {
          command: mockCommandService,
        },
      } as any,
      executor: {} as any,
      scriptId: 'test-script',
    };
  });

  it('should execute a command with no parameters', async () => {
    const effect = new CommandEffect(mockDeps, {
      cmd: 'help',
    });

    await effect.execute(mockContext);

    expect(mockCommandService.executeCommand).toHaveBeenCalledWith('help', []);
  });

  it('should execute a command with numbered parameters', async () => {
    const effect = new CommandEffect(mockDeps, {
      cmd: 'teleport',
      parameter0: 100,
      parameter1: 64,
      parameter2: 200,
    });

    await effect.execute(mockContext);

    expect(mockCommandService.executeCommand).toHaveBeenCalledWith('teleport', [
      100,
      64,
      200,
    ]);
  });

  it('should execute a command with parameters array', async () => {
    const effect = new CommandEffect(mockDeps, {
      cmd: 'notification',
      parameters: ['Test message', 'info'],
    });

    await effect.execute(mockContext);

    expect(mockCommandService.executeCommand).toHaveBeenCalledWith('notification', [
      'Test message',
      'info',
    ]);
  });

  it('should handle all 11 numbered parameters (parameter0-parameter10)', async () => {
    const effect = new CommandEffect(mockDeps, {
      cmd: 'testcmd',
      parameter0: 0,
      parameter1: 1,
      parameter2: 2,
      parameter3: 3,
      parameter4: 4,
      parameter5: 5,
      parameter6: 6,
      parameter7: 7,
      parameter8: 8,
      parameter9: 9,
      parameter10: 10,
    });

    await effect.execute(mockContext);

    expect(mockCommandService.executeCommand).toHaveBeenCalledWith('testcmd', [
      0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
    ]);
  });

  it('should skip undefined parameters', async () => {
    const effect = new CommandEffect(mockDeps, {
      cmd: 'testcmd',
      parameter0: 'first',
      parameter2: 'third', // parameter1 is skipped
    });

    await effect.execute(mockContext);

    expect(mockCommandService.executeCommand).toHaveBeenCalledWith('testcmd', [
      'first',
      'third',
    ]);
  });

  it('should handle command execution errors gracefully', async () => {
    mockCommandService.executeCommand.mockRejectedValue(new Error('Command failed'));

    const effect = new CommandEffect(mockDeps, {
      cmd: 'invalid',
    });

    // Should not throw
    await expect(effect.execute(mockContext)).resolves.not.toThrow();
  });

  it('should handle missing cmd gracefully', async () => {
    const effect = new CommandEffect(mockDeps, {
      cmd: '',
    });

    await effect.execute(mockContext);

    expect(mockCommandService.executeCommand).not.toHaveBeenCalled();
  });

  it('should handle missing CommandService gracefully', async () => {
    const contextWithoutCommandService: ScrawlExecContext = {
      ...mockContext,
      appContext: {
        services: {},
      } as any,
    };

    const effect = new CommandEffect(mockDeps, {
      cmd: 'test',
    });

    // Should not throw
    await expect(effect.execute(contextWithoutCommandService)).resolves.not.toThrow();
  });

  it('should prefer parameters array over numbered parameters', async () => {
    const effect = new CommandEffect(mockDeps, {
      cmd: 'testcmd',
      parameter0: 'ignored',
      parameter1: 'also ignored',
      parameters: ['array', 'wins'],
    });

    await effect.execute(mockContext);

    expect(mockCommandService.executeCommand).toHaveBeenCalledWith('testcmd', [
      'array',
      'wins',
    ]);
  });
});
