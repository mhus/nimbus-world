/**
 * Error types and codes for the Nimbus system
 */

/**
 * Error codes (HTTP-style + custom)
 */
export enum ErrorCode {
  // Success (not an error, but included for completeness)
  SUCCESS = 200,

  // Client errors (400-499)
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  CONFLICT = 409,
  INVALID_DATA = 422,
  TOO_MANY_REQUESTS = 429,

  // Server errors (500-599)
  INTERNAL_SERVER_ERROR = 500,
  NOT_IMPLEMENTED = 501,
  SERVICE_UNAVAILABLE = 503,

  // Network errors (1000-1099)
  CONNECTION_FAILED = 1000,
  CONNECTION_LOST = 1001,
  CONNECTION_TIMEOUT = 1002,
  MESSAGE_TOO_LARGE = 1003,
  INVALID_MESSAGE = 1004,

  // Authentication errors (1100-1199)
  INVALID_CREDENTIALS = 1100,
  SESSION_EXPIRED = 1101,
  INVALID_TOKEN = 1102,
  ACCOUNT_BANNED = 1103,

  // World errors (1200-1299)
  WORLD_NOT_FOUND = 1200,
  WORLD_FULL = 1201,
  WORLD_LOCKED = 1202,
  INVALID_WORLD_DATA = 1203,

  // Chunk errors (1300-1399)
  CHUNK_NOT_FOUND = 1300,
  CHUNK_LOAD_FAILED = 1301,
  CHUNK_GENERATION_FAILED = 1302,
  INVALID_CHUNK_DATA = 1303,

  // Block errors (1400-1499)
  BLOCK_NOT_FOUND = 1400,
  INVALID_BLOCK_TYPE = 1401,
  INVALID_BLOCK_POSITION = 1402,
  BLOCK_UPDATE_FAILED = 1403,
  BLOCK_PROTECTED = 1404,

  // Entity errors (1500-1599)
  ENTITY_NOT_FOUND = 1500,
  INVALID_ENTITY_TYPE = 1501,
  ENTITY_SPAWN_FAILED = 1502,
  ENTITY_UPDATE_FAILED = 1503,

  // Permission errors (1600-1699)
  NO_PERMISSION = 1600,
  INSUFFICIENT_PRIVILEGES = 1601,
  AREA_RESTRICTED = 1602,

  // Validation errors (1700-1799)
  VALIDATION_FAILED = 1700,
  INVALID_FORMAT = 1701,
  OUT_OF_RANGE = 1702,
  MISSING_REQUIRED_FIELD = 1703,

  // Resource errors (1800-1899)
  RESOURCE_NOT_FOUND = 1800,
  RESOURCE_LOAD_FAILED = 1801,
  ASSET_NOT_FOUND = 1802,

  // Unknown error
  UNKNOWN = 9999,
}

/**
 * Error severity levels
 */
export enum ErrorSeverity {
  /** Informational, no action needed */
  INFO = 0,

  /** Warning, may cause issues */
  WARNING = 1,

  /** Error, action failed */
  ERROR = 2,

  /** Critical, system may be unstable */
  CRITICAL = 3,

  /** Fatal, system must stop */
  FATAL = 4,
}

/**
 * Game error interface
 */
export interface GameError {
  /** Error code */
  code: ErrorCode;

  /** Error message */
  message: string;

  /** Error severity */
  severity: ErrorSeverity;

  /** Additional error details */
  details?: any;

  /** Timestamp */
  timestamp?: number;

  /** Stack trace (development only) */
  stack?: string;
}

/**
 * Network error response
 */
export interface ErrorResponse {
  success: false;
  errorCode: ErrorCode;
  errorMessage: string;
  details?: any;
}

/**
 * Error helper functions
 */
export namespace ErrorHelper {
  /**
   * Create GameError
   * @param code Error code
   * @param message Error message
   * @param severity Severity level
   * @param details Additional details
   * @returns GameError instance
   */
  export function create(
    code: ErrorCode,
    message: string,
    severity: ErrorSeverity = ErrorSeverity.ERROR,
    details?: any
  ): GameError {
    return {
      code,
      message,
      severity,
      details,
      timestamp: Date.now(),
    };
  }

  /**
   * Create error from exception
   * @param error Exception
   * @param code Error code
   * @returns GameError instance
   */
  export function fromException(
    error: Error,
    code: ErrorCode = ErrorCode.UNKNOWN
  ): GameError {
    return {
      code,
      message: error.message,
      severity: ErrorSeverity.ERROR,
      stack: error.stack,
      timestamp: Date.now(),
    };
  }

  /**
   * Create error response for network
   * @param code Error code
   * @param message Error message
   * @param details Additional details
   * @returns Error response
   */
  export function createResponse(
    code: ErrorCode,
    message: string,
    details?: any
  ): ErrorResponse {
    return {
      success: false,
      errorCode: code,
      errorMessage: message,
      details,
    };
  }

  /**
   * Get error code name
   * @param code Error code
   * @returns Error code name
   */
  export function getCodeName(code: ErrorCode): string {
    return ErrorCode[code] ?? 'UNKNOWN';
  }

  /**
   * Get severity name
   * @param severity Severity level
   * @returns Severity name
   */
  export function getSeverityName(severity: ErrorSeverity): string {
    return ErrorSeverity[severity] ?? 'UNKNOWN';
  }

  /**
   * Check if error code is client error (400-499)
   * @param code Error code
   * @returns True if client error
   */
  export function isClientError(code: ErrorCode): boolean {
    return code >= 400 && code < 500;
  }

  /**
   * Check if error code is server error (500-599)
   * @param code Error code
   * @returns True if server error
   */
  export function isServerError(code: ErrorCode): boolean {
    return code >= 500 && code < 600;
  }

  /**
   * Check if error is retryable
   * @param code Error code
   * @returns True if should retry
   */
  export function isRetryable(code: ErrorCode): boolean {
    const retryableCodes = [
      ErrorCode.CONNECTION_TIMEOUT,
      ErrorCode.SERVICE_UNAVAILABLE,
      ErrorCode.CONNECTION_FAILED,
    ];
    return retryableCodes.includes(code);
  }

  /**
   * Format error for logging
   * @param error Game error
   * @returns Formatted string
   */
  export function format(error: GameError): string {
    const codeName = getCodeName(error.code);
    const severityName = getSeverityName(error.severity);
    const timestamp = error.timestamp
      ? new Date(error.timestamp).toISOString()
      : 'N/A';

    let str = `[${severityName}] ${codeName} (${error.code}): ${error.message}`;
    str += `\nTimestamp: ${timestamp}`;

    if (error.details) {
      str += `\nDetails: ${JSON.stringify(error.details)}`;
    }

    if (error.stack) {
      str += `\nStack: ${error.stack}`;
    }

    return str;
  }

  /**
   * Log error to console with appropriate level
   * @param error Game error
   */
  export function log(error: GameError): void {
    const formatted = format(error);

    switch (error.severity) {
      case ErrorSeverity.INFO:
        console.info(formatted);
        break;
      case ErrorSeverity.WARNING:
        console.warn(formatted);
        break;
      case ErrorSeverity.ERROR:
        console.error(formatted);
        break;
      case ErrorSeverity.CRITICAL:
      case ErrorSeverity.FATAL:
        console.error('🔥', formatted);
        break;
    }
  }
}

/**
 * Common error factory functions
 */
export namespace CommonErrors {
  export function invalidCredentials(message?: string): GameError {
    return ErrorHelper.create(
      ErrorCode.INVALID_CREDENTIALS,
      message ?? 'Invalid username or password',
      ErrorSeverity.ERROR
    );
  }

  export function worldNotFound(worldId: string): GameError {
    return ErrorHelper.create(
      ErrorCode.WORLD_NOT_FOUND,
      `World not found: ${worldId}`,
      ErrorSeverity.ERROR
    );
  }

  export function chunkNotFound(cx: number, cz: number): GameError {
    return ErrorHelper.create(
      ErrorCode.CHUNK_NOT_FOUND,
      `Chunk not found: (${cx}, ${cz})`,
      ErrorSeverity.WARNING
    );
  }

  export function invalidBlockType(blockTypeId: number): GameError {
    return ErrorHelper.create(
      ErrorCode.INVALID_BLOCK_TYPE,
      `Invalid block type ID: ${blockTypeId}`,
      ErrorSeverity.ERROR
    );
  }

  export function connectionLost(): GameError {
    return ErrorHelper.create(
      ErrorCode.CONNECTION_LOST,
      'Connection to server lost',
      ErrorSeverity.CRITICAL
    );
  }

  export function noPermission(action: string): GameError {
    return ErrorHelper.create(
      ErrorCode.NO_PERMISSION,
      `No permission to ${action}`,
      ErrorSeverity.WARNING
    );
  }

  export function validationFailed(details: string[]): GameError {
    return ErrorHelper.create(
      ErrorCode.VALIDATION_FAILED,
      'Data validation failed',
      ErrorSeverity.ERROR,
      { errors: details }
    );
  }
}
