# Command Messages

This document describes the command message system for bidirectional command execution between client and server.

## Overview

The command system allows the client to send commands to the server for execution (e.g., console commands, editor commands) and receive feedback and results from the server.

## Message Types

Three message types are defined for command execution:

1. **`cmd`** - Command execution request (Client → Server)
2. **`cmd.msg`** - Command progress/info messages during execution (Server → Client)
3. **`cmd.rs`** - Command result (Server → Client)

## Return Codes

The server responds with return codes (rc) in the `cmd.rs` message:

**Negative rc = System errors:**
- `-1` = Command not found
- `-2` = Command not allowed (permission denied)
- `-3` = Invalid arguments
- `-4` = Internal error

**Positive rc = Command-specific:**
- `0` = OK / true (success)
- `1` = Error / false (command-level error)
- Other positive values are command-specific

## Message Flow

```
Client                                  Server
  |                                      |
  |  CMD (command request)               |
  |------------------------------------->|
  |                                      | (executing...)
  |  CMD_MESSAGE (status update)         |
  |<-------------------------------------|
  |                                      |
  |  CMD_MESSAGE (progress info)         |
  |<-------------------------------------|
  |                                      |
  |  CMD_RESULT (final result)           |
  |<-------------------------------------|
  |                                      |
```

## Data Structures

### CommandData (CMD)

Command execution request from client to server.

```typescript
interface CommandData {
  /** Command string to execute */
  cmd: string;

  /** Command arguments */
  args?: string[];
}
```

**Example JSON:**
```json
{
  "i": "123",
  "t": "cmd",
  "d": {
    "cmd": "say",
    "args": ["Hello, world!"]
  }
}
```

**Example TypeScript:**
```typescript
const message: CommandMessage = {
  i: 'cmd-123',
  t: 'cmd',
  d: {
    cmd: 'setblock',
    args: ['10', '64', '20', 'stone'],
  },
};
```

### CommandMessageData (CMD_MESSAGE)

Progress/informational messages sent by server during command execution.

```typescript
interface CommandMessageData {
  /** Message text */
  message: string;
}
```

**Example JSON:**
```json
{
  "r": "cmd-123",
  "t": "cmd.msg",
  "d": {
    "message": "Processing..."
  }
}
```

**Example TypeScript:**
```typescript
const message: CommandMessageMessage = {
  r: 'cmd-123',  // References command request
  t: 'cmd.msg',
  d: {
    message: 'Setting block at (10, 64, 20)...',
  },
};
```

### CommandResultData (CMD_RESULT)

Final result of command execution sent by server.

```typescript
interface CommandResultData {
  /** Return code (negative = system error, 0 = success, positive = command-specific) */
  rc: number;

  /** Result/error message */
  message: string;
}
```

**Example JSON (Success):**
```json
{
  "r": "cmd-123",
  "t": "cmd.rs",
  "d": {
    "rc": 0,
    "message": "Hello, world!"
  }
}
```

**Example JSON (Error):**
```json
{
  "r": "cmd-123",
  "t": "cmd.rs",
  "d": {
    "rc": -1,
    "message": "Command not found."
  }
}
```

**Example TypeScript (Success):**
```typescript
const message: CommandResultMessage = {
  r: 'cmd-123',  // References command request
  t: 'cmd.rs',
  d: {
    rc: 0,
    message: 'Block set successfully',
  },
};
```

**Example TypeScript (Error):**
```typescript
const message: CommandResultMessage = {
  r: 'cmd-123',  // References command request
  t: 'cmd.rs',
  d: {
    rc: -3,  // Invalid arguments
    message: 'Invalid block type: stone_invalid',
  },
};
```

## Usage Examples

### Client: Send Command

```typescript
import type { CommandMessage, CommandData } from '@nimbus/shared';

// Create command message
const commandData: CommandData = {
  cmd: 'tp',
  args: ['player1', '100', '64', '200'],
};

const message: CommandMessage = {
  i: generateMessageId(),
  t: 'cmd',
  d: commandData,
};

// Send to server
networkService.send(message);
```

### Server: Handle Command

```typescript
import type { CommandMessage, CommandMessageMessage, CommandResultMessage } from '@nimbus/shared';

// Handle command message
function handleCommand(message: CommandMessage) {
  const { cmd, args } = message.d;
  const requestId = message.i!;

  // Send progress message
  sendCommandMessage(requestId, 'Executing teleport...');

  try {
    // Execute command
    const result = executeCommand(cmd, args);

    // Send success result
    sendCommandResult(requestId, {
      rc: 0,
      message: `Teleported player to ${args[1]}, ${args[2]}, ${args[3]}`,
    });
  } catch (error) {
    // Send error result
    sendCommandResult(requestId, {
      rc: -4,  // Internal error
      message: `Command execution failed: ${error.message}`,
    });
  }
}

function sendCommandMessage(requestId: string, message: string) {
  const msg: CommandMessageMessage = {
    r: requestId,
    t: 'cmd.msg',
    d: { message },
  };
  connection.send(msg);
}

function sendCommandResult(requestId: string, data: CommandResultData) {
  const message: CommandResultMessage = {
    r: requestId,
    t: 'cmd.rs',
    d: data,
  };
  connection.send(message);
}
```

### Client: Handle Command Messages and Results

```typescript
import type { CommandMessageMessage, CommandResultMessage } from '@nimbus/shared';

// Register handlers
networkService.on('cmd.msg', handleCommandMessage);
networkService.on('cmd.rs', handleCommandResult);

function handleCommandMessage(message: CommandMessageMessage) {
  const { message: msg } = message.d;

  // Display message to user
  console.log(`[INFO] ${msg}`);

  // Update UI with progress
  updateCommandProgress(message.r!, msg);
}

function handleCommandResult(message: CommandResultMessage) {
  const { rc, message: msg } = message.d;

  if (rc === 0) {
    console.log(`✓ ${msg}`);
  } else if (rc < 0) {
    console.error(`✗ System error (${rc}): ${msg}`);
  } else {
    console.error(`✗ Command error (${rc}): ${msg}`);
  }

  // Complete command execution
  completeCommand(message.r!, rc, msg);
}
```

## Command Types

### Console Commands

Example commands that can be executed:

- **Block Manipulation:**
  - `setblock <x> <y> <z> <blockType>` - Set block at position
  - `fill <x1> <y1> <z1> <x2> <y2> <z2> <blockType>` - Fill region with blocks
  - `replace <x1> <y1> <z1> <x2> <y2> <z2> <oldBlock> <newBlock>` - Replace blocks

- **Player Commands:**
  - `tp <player> <x> <y> <z>` - Teleport player
  - `give <player> <item> [count]` - Give item to player
  - `gamemode <mode> [player]` - Change game mode

- **World Commands:**
  - `time set <value>` - Set world time
  - `weather <type>` - Change weather
  - `save` - Save world

- **Editor Commands:**
  - `select <x1> <y1> <z1> <x2> <y2> <z2>` - Select region
  - `copy` - Copy selection
  - `paste` - Paste clipboard
  - `undo` - Undo last operation
  - `redo` - Redo last undone operation

## Best Practices

1. **Always provide message IDs** - Use `i` field in command requests for proper correlation
2. **Send progress updates** - Use `cmd.msg` for long-running commands
3. **Use correct return codes** - Negative for system errors, 0 for success, positive for command-specific errors
4. **Provide descriptive messages** - Include helpful information in the message field
5. **Document custom return codes** - If your command uses custom positive rc values, document them

## Security Considerations

1. **Validate commands** - Server must validate command names and arguments
2. **Check permissions** - Verify user has permission to execute command
3. **Sanitize input** - Prevent command injection attacks
4. **Rate limiting** - Prevent command spam
5. **Audit logging** - Log all command executions for security audit

## Performance

- **Network efficiency**: Short message type names (`cmd`, `cmd.msg`, `cmd.rs`) reduce bandwidth
- **Async execution**: Commands execute asynchronously without blocking
- **Progress updates**: Optional progress messages for user feedback
- **Execution time tracking**: Monitor command performance

## Error Handling

All errors should be reported via `cmd.rs` with appropriate return codes:

```typescript
{
  rc: -1,  // or other appropriate error code
  message: 'Human-readable error message'
}
```

**System Error Codes (negative):**
- `-1` - Command not found
- `-2` - Permission denied
- `-3` - Invalid arguments
- `-4` - Internal error

**Command-Specific Errors (positive):**
- `1` - Generic command error/false
- `>1` - Command-specific error codes (document these in your command implementation)

## Testing

Example test cases:

```typescript
describe('CommandMessage', () => {
  it('should validate command data', () => {
    const data: CommandData = {
      cmd: 'test',
      args: ['arg1', 'arg2'],
    };
    expect(validateCommandData(data)).toBe(true);
  });

  it('should handle command execution flow', async () => {
    const commandId = 'cmd-123';

    // Send command
    await sendCommand({ cmd: 'test', args: [] });

    // Expect progress message
    const progressMsg = await waitForMessage('cmd.msg', commandId);
    expect(progressMsg.d.message).toBeDefined();

    // Expect result
    const result = await waitForMessage('cmd.rs', commandId);
    expect(result.d.rc).toBe(0);  // Success
  });

  it('should handle command errors', async () => {
    const commandId = 'cmd-124';

    // Send invalid command
    await sendCommand({ cmd: 'invalid_command', args: [] });

    // Expect error result
    const result = await waitForMessage('cmd.rs', commandId);
    expect(result.d.rc).toBe(-1);  // Command not found
    expect(result.d.message).toContain('not found');
  });
});
```

## Future Enhancements

Possible future additions:

1. **Command history** - Track executed commands
2. **Command aliases** - Support command shortcuts
3. **Auto-completion** - Suggest commands and arguments
4. **Batch execution** - Execute multiple commands in sequence
5. **Scheduled commands** - Schedule commands for later execution
6. **Command macros** - Define reusable command sequences
