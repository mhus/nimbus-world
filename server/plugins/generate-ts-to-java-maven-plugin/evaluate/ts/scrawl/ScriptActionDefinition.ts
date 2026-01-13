import { ScrawlScript } from './ScrawlScript';

/**
 * Script action definition used to trigger scrawl scripts.
 * Can be attached to items, abilities, events, etc.
 */
export interface ScriptActionDefinition {
  /**
   * Script ID to execute (without .scrawl.json extension).
   * If both scriptId and script are provided, inline script takes precedence.
   */
  scriptId?: string;

  /**
   * Parameters to pass to the script.
   * These override the default parameter values defined in the script.
   */
  parameters?: Record<string, any>;

  /**
   * Inline script definition (optional).
   * If provided, this script will be executed instead of loading by scriptId.
   */
  script?: ScrawlScript;

  /**
   * Whether to send this effect to server for synchronization (default: true)
   *
   * If true (default), the effect will be sent to the server which broadcasts
   * it to other clients in the affected chunks for multiplayer synchronization.
   *
   * If false, the effect is only executed locally and not synchronized.
   * Useful for client-only visual feedback or UI effects.
   */
  sendToServer?: boolean;
}
