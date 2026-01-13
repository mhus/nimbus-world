/**
 * Command-related messages
 *
 * Commands are bidirectional messages that allow execution of commands
 * on the server from the client (e.g., console commands, editor commands).
 * The server can also send command-related messages back to the client.
 *
 * Message types:
 * - "cmd" (Client -> Server): Command execution request
 * - "cmd.msg" (Server -> Client): Command messages during execution
 * - "cmd.rs" (Server -> Client): Command result/response
 *
 * Return codes (rc):
 * - Negative rc are system errors:
 *   * -1 = Command not found
 *   * -2 = Command not allowed (permission denied)
 *   * -3 = Invalid arguments
 *   * -4 = Internal error
 *
 * - Positive rc are command-specific:
 *   * 0 = OK / true
 *   * 1 = Error / false
 *   * Other positive values are command-specific
 */

import type { BaseMessage } from '../BaseMessage';

/**
 * Command data (Client -> Server)
 * Message type: "cmd"
 *
 * Client sends command to execute on server
 */
export interface CommandData {
  /** Command string to execute */
  cmd: string;

  /** Command arguments */
  args?: string[];

  /** One-way command (no response expected) */
  oneway?: boolean;
}

/**
 * Command message (Client -> Server)
 * Message type: "cmd"
 */
export type CommandMessage = BaseMessage<CommandData>;

/**
 * Command message data (Server -> Client)
 * Message type: "cmd.msg"
 *
 * Server sends informational/progress messages during command execution
 */
export interface CommandMessageData {
  /** Message text */
  message: string;
}

/**
 * Command message (Server -> Client)
 * Message type: "cmd.msg"
 */
export type CommandMessageMessage = BaseMessage<CommandMessageData>;

/**
 * Command result data (Server -> Client)
 * Message type: "cmd.rs"
 *
 * Server sends final result of command execution
 *
 * Return codes:
 * - Negative: System errors (-1 = not found, -2 = permission denied, -3 = invalid args, -4 = internal error)
 * - Zero: Success / OK
 * - Positive: Command-specific (1 = error/false, others are command-specific)
 */
export interface CommandResultData {
  /** Return code */
  rc: number;

  /** Result/error message */
  message: string;
}

/**
 * Command result message (Server -> Client)
 * Message type: "cmd.rs"
 */
export type CommandResultMessage = BaseMessage<CommandResultData>;
