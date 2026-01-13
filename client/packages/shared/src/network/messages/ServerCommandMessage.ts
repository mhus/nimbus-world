/**
 * Server Command-related messages
 *
 * Server commands allow the server to execute commands on the client.
 * This is the inverse of client commands (cmd).
 *
 * Message types:
 * - "scmd" (Server -> Client): Server command execution request
 * - "scmd.rs" (Client -> Server): Server command result/response
 *
 * Return codes (rc):
 * - Negative rc are system errors:
 *   * -1 = Command not found
 *   * -3 = Invalid arguments
 *   * -4 = Internal error
 *
 * - Positive rc are command-specific:
 *   * 0 = OK / true
 *   * 1 = Error / false
 *   * Other positive values are command-specific
 */

import type { RequestMessage, ResponseMessage } from '../BaseMessage';

/**
 * Single server command data (for use in cmds array)
 */
export interface SingleServerCommandData {
  /** Command string to execute */
  cmd: string;

  /** Command arguments */
  args?: string[];
}

/**
 * Server command data (Server -> Client)
 * Message type: "scmd"
 *
 * Server sends command(s) to execute on client
 *
 * Single command format (backward compatible):
 * - cmd: Command name
 * - args: Command arguments
 * - oneway: No response expected
 *
 * Multiple commands format:
 * - cmds: Array of commands to execute
 * - parallel: Execute in parallel (true) or serial (false, default)
 */
export interface ServerCommandData {
  /** Command string to execute (single command mode) */
  cmd?: string;

  /** Command arguments (single command mode) */
  args?: string[];

  /** One-way command (no response expected) */
  oneway?: boolean;

  /** Multiple commands to execute */
  cmds?: SingleServerCommandData[];

  /** Execute commands in parallel (default: false = serial) */
  parallel?: boolean;
}

/**
 * Server command message (Server -> Client)
 * Message type: "scmd"
 */
export type ServerCommandMessage = RequestMessage<ServerCommandData>;

/**
 * Server command result data (Client -> Server)
 * Message type: "scmd.rs"
 *
 * Client sends final result of server command execution
 *
 * Return codes:
 * - Negative: System errors (-1 = not found, -3 = invalid args, -4 = internal error)
 * - Zero: Success / OK
 * - Positive: Command-specific (1 = error/false, others are command-specific)
 */
export interface ServerCommandResultData {
  /** Return code */
  rc: number; // javaType: int

  /** Result/error message */
  message: string;
}

/**
 * Server command result message (Client -> Server)
 * Message type: "scmd.rs"
 */
export type ServerCommandResultMessage = ResponseMessage<ServerCommandResultData>;
