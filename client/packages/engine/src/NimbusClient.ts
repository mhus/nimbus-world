/**
 * NimbusClient - Main entry point for Nimbus 3D Voxel Engine Client
 *
 * Build variants:
 * - Viewer: Read-only 3D engine for viewing worlds
 * - Editor: Full 3D engine + editor functions + console
 *
 * Unreachable code is eliminated by the bundler based on __EDITOR__ and __VIEWER__ flags
 */

import {SHARED_VERSION, getLogger, ExceptionHandler, LoggerFactory, LogLevel} from '@nimbus/shared';
import { loadClientConfig } from './config/ClientConfig';
import { ClientService } from './services/ClientService';
import {createAppContext, loadSessionCookie} from './AppContext';
import type { AppContext } from './AppContext';
import { NetworkService } from './services/NetworkService';
import { BlockTypeService } from './services/BlockTypeService';
import { ShaderService } from './services/ShaderService';
import { AudioService } from './services/AudioService';
import { ChunkService } from './services/ChunkService';
import { EngineService } from './services/EngineService';
import { ModalService } from './services/ModalService';
import { NotificationService } from './services/NotificationService';
import { TeamService } from './services/TeamService';
import { CompassService } from './services/CompassService';
import { EntityService } from './services/EntityService';
import { ItemService } from './services/ItemService';
import { ScrawlService } from './scrawl/ScrawlService';
import { ConfigService } from './services/ConfigService';
import { LoginMessageHandler } from './network/handlers/LoginMessageHandler';
import { ChunkMessageHandler } from './network/handlers/ChunkMessageHandler';
import { TeamDataMessageHandler } from './network/handlers/TeamDataMessageHandler';
import { TeamStatusMessageHandler } from './network/handlers/TeamStatusMessageHandler';
import { BlockUpdateHandler } from './network/handlers/BlockUpdateHandler';
import { ItemBlockUpdateHandler } from './network/handlers/ItemBlockUpdateHandler';
import { EffectTriggerHandler } from './network/handlers/EffectTriggerHandler';
import { EffectParameterUpdateHandler } from './network/handlers/EffectParameterUpdateHandler';
import { PingMessageHandler } from './network/handlers/PingMessageHandler';
import { EntityPathwayMessageHandler } from './network/handlers/EntityPathwayMessageHandler';
import { EntityStatusUpdateMessageHandler } from './network/handlers/EntityStatusUpdateMessageHandler';
import { CommandMessageHandler } from './network/handlers/CommandMessageHandler';
import { CommandResultHandler } from './network/handlers/CommandResultHandler';
import { ServerCommandHandler } from './network/handlers/ServerCommandHandler';

import {CommandsFactory} from "./commands/CommandsFactory";

const CLIENT_VERSION = '2.0.0';

// Initialize logger (basic setup before ClientService)
const logger = getLogger('NimbusClient');

// Build mode info
const buildMode = __EDITOR__ ? 'Editor' : 'Viewer';

/**
 * Initialize application
 */
async function initializeApp(): Promise<AppContext> {
  try {
//    LoggerFactory.setDefaultLevel(LogLevel.DEBUG);
    logger.debug(`Nimbus Client v${CLIENT_VERSION} (${buildMode} Build)`);
    logger.debug(`Shared Library v${SHARED_VERSION}`);
    logger.debug(`Build Mode: ${__BUILD_MODE__}`);

    // Display license notice
    console.log('\n' + '='.repeat(70));
    console.log('Nimbus – NOTICE');
    console.log('='.repeat(70));
    console.log('\nThis software is provided free of charge under a restrictive license.\n');
    console.log('Permitted use:');
    console.log('  - non-commercial');
    console.log('  - non-production');
    console.log('  - testing, evaluation, research only\n');
    console.log('Production, operational, or commercial use is NOT permitted.\n');
    console.log('Any modifications must be released under the same license.\n');
    console.log('See LICENSE for full terms.');
    console.log('https://github.com/mhus/nimbus/blob/main/LICENSE.txt');
    console.log('='.repeat(70) + '\n');

    // Load client configuration
    logger.debug('Loading client configuration...');
    const config = await loadClientConfig();

    // Create ClientService
    logger.debug('Initializing ClientService...');
    const clientService = new ClientService(config);

    // Setup logger with proper transports
    clientService.setupLogger();

    // Create AppContext
    logger.debug('Creating AppContext...');
    const appContext = createAppContext(config, clientService);
    loadSessionCookie(appContext);

    // PlayerInfo will be initialized by server configuration via ConfigService

    // Initialize ModalService (no dependencies, UI-only)
    logger.debug('Initializing ModalService...');
    const modalService = new ModalService(appContext);
    appContext.services.modal = modalService;
    logger.debug('ModalService initialized');

    // Initialize NotificationService (no dependencies, UI-only)
    logger.debug('Initializing NotificationService...');
    const notificationService = new NotificationService(appContext);
    appContext.services.notification = notificationService;
    logger.debug('NotificationService initialized');

    // Initialize TeamService (depends on NotificationService)
    logger.debug('Initializing TeamService...');
    const teamService = new TeamService(appContext);
    appContext.services.team = teamService;
    logger.debug('TeamService initialized');

    // Initialize ItemService (loads items from server REST API)
    logger.debug('Initializing ItemService...');
    const itemService = new ItemService(appContext);
    appContext.services.item = itemService;
    logger.debug('ItemService initialized');

    // Initialize ScrawlService (before CommandService so commands can use it)
    logger.debug('Initializing ScrawlService...');
    const scrawlService = new ScrawlService(appContext);
    appContext.services.scrawl = scrawlService;
    await scrawlService.initialize();
    logger.debug('ScrawlService initialized');

    // Initialize TargetingService (before ShortcutService, which depends on it)
    logger.debug('Initializing TargetingService...');
    const { TargetingService } = await import('./services/TargetingService');
    const targetingService = new TargetingService(appContext);
    appContext.services.targeting = targetingService;
    logger.debug('TargetingService initialized');

    // Initialize ShortcutService (after ScrawlService and TargetingService)
    logger.debug('Initializing ShortcutService...');
    const { ShortcutService } = await import('./services/ShortcutService');
    const shortcutService = new ShortcutService(appContext);
    appContext.services.shortcut = shortcutService;
    logger.debug('ShortcutService initialized');

    CommandsFactory.createCommands(appContext);

    logger.debug('App initialization complete', {
      clientType: clientService.getClientType(),
      isEditor: clientService.isEditor(),
      isDevMode: clientService.isDevMode(),
    });

    return appContext;
  } catch (error) {
    throw ExceptionHandler.handleAndRethrow(error, 'NimbusClient.initializeApp');
  }
}

function showSplashScreen(appContext: AppContext, networkService: NetworkService) {
  // Show splash screen if enabled and configured (after worldInfo and NetworkService are loaded)
  const showSplashScreen = import.meta.env.VITE_SHOW_SPLASH_SCREEN !== 'false';
  const splashScreenPath = appContext.worldInfo?.splashScreen;
  const splashScreenAudio = appContext.worldInfo?.splashScreenAudio;

  const notificationService = appContext.services.notification;
  if (showSplashScreen && splashScreenPath && notificationService) {
    logger.debug('Showing splash screen', {splashScreenPath, splashScreenAudio});
    notificationService.showSplashScreen(splashScreenPath, splashScreenAudio);
  } else {
    logger.warn('Splash screen not shown', {
      reason: !showSplashScreen ? 'disabled in env' :
          !splashScreenPath ? 'no splashScreenPath in worldInfo' :
              'notificationService not available'
    });
  }
}

/**
 * Initialize core services (Network, BlockType, Shader, Chunk)
 */
async function initializeCoreServices(appContext: AppContext): Promise<void> {
  try {
    logger.debug('Initializing core services...');

    // Initialize ConfigService BEFORE NetworkService
    logger.debug('Initializing ConfigService...');
    const configService = new ConfigService(appContext);
    appContext.services.config = configService;

    // Load configuration from REST API before connecting to WebSocket
    logger.debug('Loading configuration from REST API...');
    const clientType = __EDITOR__ ? 'editor' : 'viewer';
    const worldId = appContext.config?.worldId;
    try {
      await configService.loadConfig(clientType, false, worldId);
      logger.debug('Configuration loaded successfully');
    } catch (error) {
      logger.error('Failed to load configuration from REST API', undefined, error as Error);
      throw new Error('Failed to load configuration. Please check server connection.');
    }

    // Initialize NetworkService
    logger.debug('Initializing NetworkService...');
    const networkService = new NetworkService(appContext);
    appContext.services.network = networkService;

    // Register message handlers BEFORE connecting
    logger.debug('Registering message handlers...');
    const loginHandler = new LoginMessageHandler(appContext, networkService);
    networkService.registerHandler(loginHandler);

    const pingHandler = new PingMessageHandler(networkService, appContext);
    networkService.registerHandler(pingHandler);

    // Register team handlers
    if (appContext.services.team) {
      const teamDataHandler = new TeamDataMessageHandler(appContext, appContext.services.team);
      networkService.registerHandler(teamDataHandler);

      const teamStatusHandler = new TeamStatusMessageHandler(appContext, appContext.services.team);
      networkService.registerHandler(teamStatusHandler);
    }

    // Add error handler to prevent unhandled errors
    networkService.on('error', (error) => {
      logger.error('Network error', undefined, error);
    });

    // Connect to server
    logger.debug('Connecting to server...');
    await networkService.connect();
    logger.debug('Connected to server');

    // Wait for login response and world info
    await new Promise<void>((resolve, reject) => {
      // Add error handler
      networkService.once('login:error', (error) => {
        logger.error('Login failed', undefined, error);
        reject(error);
      });

      networkService.once('login:success', () => {
        logger.debug('Login successful');

        // Start ping interval after successful login
        const pingInterval = appContext.worldInfo?.settings?.pingInterval || 30;
        pingHandler.pingIntervalMs = pingInterval * 1000; // Convert seconds to milliseconds
        pingHandler.startPingInterval();
        logger.debug('Ping interval started', { intervalSeconds: pingInterval });

        resolve();
      });

      // Add timeout
      setTimeout(() => {
        reject(new Error('Login timeout'));
      }, 30000);
    });

    // Initialize ModifierService FIRST (before other services that depend on it)
    logger.debug('Initializing ModifierService...');
    const { ModifierService } = await import('./services/ModifierService');
    const modifierService = new ModifierService();
    appContext.services.modifier = modifierService;
    logger.debug('ModifierService initialized');

    // Create all StackModifiers centrally
    logger.debug('Creating all StackModifiers...');
    const { createAllStackModifiers } = await import('./services/StackModifierCreator');
    createAllStackModifiers(appContext);
    logger.debug('All StackModifiers created');

    // Initialize BlockTypeService (with lazy loading)
    logger.debug('Initializing BlockTypeService...');
    const blockTypeService = new BlockTypeService(appContext);
    appContext.services.blockType = blockTypeService;
    logger.debug('BlockTypeService initialized (chunks will be loaded on-demand)');

    // Initialize ShaderService
    logger.debug('Initializing ShaderService...');
    const shaderService = new ShaderService(appContext);
    appContext.services.shader = shaderService;

    // Initialize AudioService (handles both audio loading and gameplay sounds)
    logger.debug('Initializing AudioService...');
    const audioService = new AudioService(appContext);
    appContext.services.audio = audioService;

    // a good time to show splash screen
    showSplashScreen(appContext, networkService);

    // Initialize ChunkService
    logger.debug('Initializing ChunkService...');
    const chunkService = new ChunkService(networkService, appContext);
    appContext.services.chunk = chunkService;

    // Initialize EntityService
    logger.debug('Initializing EntityService...');
    const entityService = new EntityService(appContext);
    appContext.services.entity = entityService;

    // Register ChunkMessageHandler
    const chunkHandler = new ChunkMessageHandler(chunkService);
    networkService.registerHandler(chunkHandler);

    // Register BlockUpdateHandler
    const blockUpdateHandler = new BlockUpdateHandler(chunkService, networkService);
    networkService.registerHandler(blockUpdateHandler);
    logger.debug('🔵 BlockUpdateHandler registered for message type: b.u');

    // Register ItemBlockUpdateHandler
    const itemBlockUpdateHandler = new ItemBlockUpdateHandler(chunkService);
    networkService.registerHandler(itemBlockUpdateHandler);
    logger.debug('🔵 ItemBlockUpdateHandler registered for message type: b.iu');

    // Register EntityPathwayMessageHandler
    const entityPathwayHandler = new EntityPathwayMessageHandler(entityService);
    networkService.registerHandler(entityPathwayHandler);
    logger.debug('🔵 EntityPathwayMessageHandler registered for message type: e.p');

    // Register EntityStatusUpdateMessageHandler
    const entityStatusUpdateHandler = new EntityStatusUpdateMessageHandler(entityService);
    networkService.registerHandler(entityStatusUpdateHandler);
    logger.debug('🔵 EntityStatusUpdateMessageHandler registered for message type: e.s.u');

    // Register EffectTriggerHandler (ScrawlService was initialized earlier)
    if (appContext.services.scrawl) {
      const effectTriggerHandler = new EffectTriggerHandler(appContext.services.scrawl);
      networkService.registerHandler(effectTriggerHandler);
      logger.debug('🔵 EffectTriggerHandler registered for message type: e.t');

      const effectParameterUpdateHandler = new EffectParameterUpdateHandler(appContext.services.scrawl);
      networkService.registerHandler(effectParameterUpdateHandler);
      logger.debug('🔵 EffectParameterUpdateHandler registered for message type: s.u');
    } else {
      logger.warn('ScrawlService not available - effect handlers not registered');
    }

    // Register CommandMessageHandler and CommandResultHandler
    const commandService = appContext.services.command;
    if (commandService) {
      const commandMessageHandler = new CommandMessageHandler(commandService);
      networkService.registerHandler(commandMessageHandler);
      logger.debug('CommandMessageHandler registered for message type: cmd.msg');

      const commandResultHandler = new CommandResultHandler(commandService);
      networkService.registerHandler(commandResultHandler);
      logger.debug('CommandResultHandler registered for message type: cmd.rs');

      // Register ServerCommandHandler for server -> client commands
      const serverCommandHandler = new ServerCommandHandler(commandService);
      networkService.registerHandler(serverCommandHandler);
      logger.debug('ServerCommandHandler registered for message type: scmd');
    }

    logger.debug('Core services initialized');
  } catch (error) {
    throw ExceptionHandler.handleAndRethrow(error, 'NimbusClient.initializeCoreServices');
  }
}

/**
 * Initialize 3D engine
 */
async function initializeEngine(appContext: AppContext, canvas: HTMLCanvasElement): Promise<void> {
  try {
    logger.debug('Initializing 3D Engine...');

    // Create EngineService
    const engineService = new EngineService(appContext, canvas);
    appContext.services.engine = engineService;

    // Initialize engine (loads textures, creates scene, etc.)
    await engineService.initialize();
    logger.debug('Engine initialized');

    // Initialize NotificationService event subscriptions (now that PlayerService exists)
    const notifService = appContext.services.notification;
    if (notifService) {
      notifService.initializeEventSubscriptions();
    }

    // Initialize ItemService event subscriptions (now that PlayerService exists)
    const itmService = appContext.services.item;
    if (itmService) {
      itmService.initializeEventSubscriptions();
    }

    // Start render loop
    engineService.startRenderLoop();
    logger.debug('Render loop started');

    // Initialize CompassService
    logger.debug('Initializing CompassService...');
    const compassService = new CompassService(appContext);
    appContext.services.compass = compassService;
    logger.debug('CompassService initialized');

    logger.debug('3D Engine ready');
  } catch (error) {
    throw ExceptionHandler.handleAndRethrow(error, 'NimbusClient.initializeEngine');
  }
}

// Initialize application
const appContextPromise = initializeApp();

// Main initialization
appContextPromise
  .then(async (appContext) => {
    logger.debug('AppContext ready', {
      hasConfig: !!appContext.config,
      hasClientService: !!appContext.services.client,
    });

    // Get canvas
    const canvas = document.getElementById('renderCanvas') as HTMLCanvasElement;
    if (!canvas) {
      throw new Error('Canvas element not found');
    }

    // Show loading message
    showLoadingMessage(canvas, 'Connecting to server...');

    try {
      // Initialize core services (Network, BlockType, Chunk)
      await initializeCoreServices(appContext);

      await postCoreServiceInitialization(appContext);

      // Show progress
      showLoadingMessage(canvas, 'Initializing 3D engine...');

      // Clear canvas and prepare for WebGL
      // BabylonJS needs a fresh canvas without existing 2D context
      const parent = canvas.parentElement;
      if (parent) {
        const newCanvas = document.createElement('canvas');
        newCanvas.id = 'renderCanvas';
        newCanvas.width = window.innerWidth;
        newCanvas.height = window.innerHeight;
        newCanvas.style.width = '100%';
        newCanvas.style.height = '100%';
        parent.replaceChild(newCanvas, canvas);

        logger.debug('Canvas replaced for WebGL initialization');

        // Initialize 3D engine with new canvas
        await initializeEngine(appContext, newCanvas);
      } else {
        throw new Error('Canvas has no parent element');
      }

      await postEngineInitialization(appContext);

      await startEngineAtPlayerPosition(appContext);

      // Hide loading screen
      const loadingElement = document.getElementById('loading');
      if (loadingElement) {
        loadingElement.classList.add('hidden');
      }

      logger.debug('Nimbus Client ready!');
    } catch (error) {
      throw error; // Re-throw to outer catch
    }

    // Editor-specific initialization (tree-shaken in viewer build)
    if (__EDITOR__) {
      logger.debug('Editor mode active');

      // Expose commands to browser console
      const commandService = appContext.services.command;
      if (commandService) {
        commandService.exposeToBrowserConsole();
      }

      // TODO: Initialize EditorService
      // TODO: Load editor UI components
    }

    logger.debug('Nimbus Client initialized successfully');
  })
  .catch((error) => {
    ExceptionHandler.handle(error, 'NimbusClient.main');
    logger.fatal('Failed to initialize client', undefined, error as Error);

    // Show error on canvas
    const canvas = document.getElementById('renderCanvas') as HTMLCanvasElement;
    if (canvas) {
      showErrorMessage(canvas, error instanceof Error ? error.message : 'Unknown error');
    }
  });


async function postCoreServiceInitialization(appContext: AppContext): Promise<void> {
}

async function postEngineInitialization(appContext: AppContext): Promise<void> {
  // set time if defined (synchronous)
  if (appContext.worldInfo?.settings?.worldTime?.linuxEpocheDeltaMinutes !== undefined) {
    appContext.services.environment?.startEnvironment();
  }

  // set shortcuts if defined (asynchronous - fire and forget)
  // Use editorShortcuts in EDITOR mode, otherwise use shortcuts
  logger.warn('playerInfo:', appContext.playerInfo);
  const shortcuts = __EDITOR__
    ? (appContext.playerInfo?.editorShortcuts || appContext.playerInfo?.shortcuts)
    : appContext.playerInfo?.shortcuts;
  if (shortcuts) {
    logger.info('Initializing player shortcuts asynchronously', {
      count: Object.keys(shortcuts).length,
      mode: __EDITOR__ ? 'editor' : 'viewer'
    });

    // Execute all shortcut initializations in parallel without waiting
    Promise.all(
      Object.entries(shortcuts).map(async ([key, shortcut]) => {
        // Skip null/undefined shortcuts
        if (!shortcut) {
          logger.debug('Skipping null/undefined shortcut', { key });
          return;
        }

        try {
          await appContext.services.command?.executeCommand('setShortcut', [
            key,
            shortcut.type,
            {
              itemId: shortcut.itemId,
              wait: shortcut.wait,
              name: shortcut.name,
              description: shortcut.description,
              command: shortcut.command,
              commandArgs: shortcut.commandArgs,
              iconPath: shortcut.iconPath
            }
          ]);
          logger.info('Shortcut initialized', { key, shortcut });
        } catch (error) {
          logger.error('Failed to initialize shortcut', { key, shortcut }, error as Error);
        }
      })
    ).then(() => {
      logger.info('All shortcuts initialized');
    }).catch((error) => {
      logger.error('Error during shortcuts initialization', undefined, error as Error);
    });
  }
}

async function startEngineAtPlayerPosition(appContext: AppContext): Promise<void> {
  try {
    // Register some chunks around player spawn
    const chunkService = appContext.services.chunk;
    if (chunkService) {
      const playerService = appContext.services.engine?.getPlayerService();
      const playerPos = playerService?.getPosition();

      if (playerPos) {
        logger.debug('Registering chunks around player', {
          x: playerPos.x,
          y: playerPos.y,
          z: playerPos.z
        });
        chunkService.updateChunksAroundPosition(playerPos.x, playerPos.z);
      }
    }
  } catch (error) {
    logger.error('Failed to register chunks around player', undefined, error as Error);
    throw ExceptionHandler.handleAndRethrow(error, 'NimbusClient.startEngineAtPlayerPosition');
  }
}

/**
 * Show loading message on canvas
 */
function showLoadingMessage(canvas: HTMLCanvasElement, message: string): void {
  const ctx = canvas.getContext('2d');
  if (ctx) {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    ctx.fillStyle = '#1a1a1a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#ffffff';
    ctx.font = '24px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(
      `Nimbus Client v${CLIENT_VERSION} (${buildMode})`,
      canvas.width / 2,
      canvas.height / 2 - 40
    );
    ctx.font = '16px sans-serif';
    ctx.fillStyle = '#4a9eff';
    ctx.fillText(message, canvas.width / 2, canvas.height / 2 + 10);
  }
}

/**
 * Show error message on canvas
 */
function showErrorMessage(canvas: HTMLCanvasElement, message: string): void {
  const ctx = canvas.getContext('2d');
  if (ctx) {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    ctx.fillStyle = '#1a1a1a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#ff4444';
    ctx.font = '24px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Initialization Error', canvas.width / 2, canvas.height / 2 - 20);
    ctx.font = '16px sans-serif';
    ctx.fillStyle = '#ffaaaa';
    ctx.fillText(message, canvas.width / 2, canvas.height / 2 + 10);
  }
}

/**
 * Test functions for NotificationService
 * Call from browser console:
 * - testNotifications() - Test all notification types
 * - testSystemNotifications() - Test system area
 * - testChatNotifications() - Test chat area
 * - testOverlayNotifications() - Test overlay area
 * - testQuestNotifications() - Test quest area
 */

// Make test functions globally available
(window as any).testNotifications = () => {
  appContextPromise.then((appContext) => {
    const ns = appContext.services.notification;
    if (!ns) {
      logger.error('NotificationService not initialized');
      return;
    }

    logger.debug('Testing all notification types...');

    // System notifications
    ns.newNotification(0, null, 'System Info: Client initialized');
    setTimeout(() => ns.newNotification(1, null, 'System Error: Connection failed'), 500);
    setTimeout(() => ns.newNotification(3, null, 'Command Result: Build successful'), 1000);

    // Chat notifications
    setTimeout(() => ns.newNotification(10, null, 'Player joined the game'), 1500);
    setTimeout(() => ns.newNotification(11, 'Max', 'Hello everyone!'), 2000);
    setTimeout(() => ns.newNotification(12, 'Anna', 'Hi there!'), 2500);

    // Overlay notifications
    setTimeout(() => ns.newNotification(20, null, 'LEVEL UP!'), 3000);
    setTimeout(() => ns.newNotification(21, null, 'Achievement unlocked'), 5500);

    // Quest notifications
    setTimeout(() => ns.newNotification(30, null, 'Quest: Find the Crystal'), 6000);
    setTimeout(() => ns.newNotification(31, null, 'Target: Search the cave (0/5)'), 6500);

    logger.debug('Test sequence started. Notifications will appear over 7 seconds.');
  });
};

(window as any).testSystemNotifications = () => {
  appContextPromise.then((appContext) => {
    const ns = appContext.services.notification;
    if (!ns) return;
    ns.newNotification(0, null, 'System Info Message');
    ns.newNotification(1, null, 'System Error Message');
    ns.newNotification(3, null, 'Command Result Message');
  });
};

(window as any).testChatNotifications = () => {
  appContextPromise.then((appContext) => {
    const ns = appContext.services.notification;
    if (!ns) return;
    ns.newNotification(10, null, 'Player joined');
    ns.newNotification(11, 'GroupChat', 'This is a group message');
    ns.newNotification(12, 'PrivateUser', 'This is a private message');
  });
};

(window as any).testOverlayNotifications = () => {
  appContextPromise.then((appContext) => {
    const ns = appContext.services.notification;
    if (!ns) return;
    ns.newNotification(20, null, 'BIG OVERLAY MESSAGE');
    setTimeout(() => ns.newNotification(21, null, 'Small overlay message'), 2500);
  });
};

(window as any).testQuestNotifications = () => {
  appContextPromise.then((appContext) => {
    const ns = appContext.services.notification;
    if (!ns) return;
    ns.newNotification(30, null, 'Quest: Explore the Dungeon');
    ns.newNotification(31, null, 'Kill 10 monsters (3/10)');
  });
};

(window as any).clearChat = () => {
  appContextPromise.then((appContext) => {
    const ns = appContext.services.notification;
    if (!ns) return;
    ns.clearChatNotifications();
    logger.debug('Chat notifications cleared');
  });
};

(window as any).toggleNotifications = (visible: boolean) => {
  appContextPromise.then((appContext) => {
    const ns = appContext.services.notification;
    if (!ns) return;
    ns.notificationsVisible(visible);
    logger.debug(`Notifications ${visible ? 'enabled' : 'disabled'}`);
  });
};

// Compass test functions
(window as any).testCompass = () => {
  appContextPromise.then((appContext) => {
    const compass = appContext.services.compass;
    const player = appContext.services.player;
    if (!compass || !player) {
      logger.error('CompassService or PlayerService not initialized');
      return;
    }

    const playerPos = player.getPosition();
    if (!playerPos) {
      logger.error('Player position not available');
      return;
    }

    logger.debug('Testing compass with markers...');

    // Add markers at different positions relative to player
    // North marker (Z+)
    const northMarker = compass.addMarker(
      { x: playerPos.x, y: playerPos.y, z: playerPos.z + 50 },
      'red',
      'arrow',
      'top',
      100
    );
    logger.debug('Added North marker (red arrow, 50 blocks North)');

    // East marker (X+)
    const eastMarker = compass.addMarker(
      { x: playerPos.x + 50, y: playerPos.y, z: playerPos.z },
      'yellow',
      'diamond',
      'center',
      100
    );
    logger.debug('Added East marker (yellow diamond, 50 blocks East)');

    // South marker (Z-)
    const southMarker = compass.addMarker(
      { x: playerPos.x, y: playerPos.y, z: playerPos.z - 50 },
      'blue',
      'triangle',
      'bottom',
      100
    );
    logger.debug('Added South marker (blue triangle, 50 blocks South)');

    // West marker (X-)
    const westMarker = compass.addMarker(
      { x: playerPos.x - 50, y: playerPos.y, z: playerPos.z },
      'green',
      'circle',
      'center',
      100
    );
    logger.debug('Added West marker (green circle, 50 blocks West)');

    logger.debug('Compass test markers added. Move the camera to see them rotate!');
  });
};

(window as any).testCompassNearClip = () => {
  appContextPromise.then((appContext) => {
    const compass = appContext.services.compass;
    const player = appContext.services.player;
    if (!compass || !player) {
      logger.error('CompassService or PlayerService not initialized');
      return;
    }

    const playerPos = player.getPosition();
    if (!playerPos) {
      logger.error('Player position not available');
      return;
    }

    logger.debug('Testing near-clip distance functionality...');

    // Add marker very close (3 blocks) - should be hidden
    compass.addMarker(
      { x: playerPos.x + 3, y: playerPos.y, z: playerPos.z },
      'red',
      'circle',
      'center',
      100,
      5  // nearClipDistance = 5 blocks
    );
    logger.debug('Added marker 3 blocks away (should be HIDDEN, near clip = 5)');

    // Add marker at medium distance (10 blocks) - should be visible
    compass.addMarker(
      { x: playerPos.x + 10, y: playerPos.y, z: playerPos.z },
      'green',
      'diamond',
      'center',
      100,
      5  // nearClipDistance = 5 blocks
    );
    logger.debug('Added marker 10 blocks away (should be VISIBLE, near clip = 5)');

    // Add marker with custom near clip (2 blocks)
    compass.addMarker(
      { x: playerPos.x, y: playerPos.y, z: playerPos.z + 3 },
      'yellow',
      'triangle',
      'center',
      100,
      2  // nearClipDistance = 2 blocks
    );
    logger.debug('Added marker 3 blocks away (should be VISIBLE, near clip = 2)');

    logger.debug('Move towards the markers to see them disappear when too close!');
  });
};

(window as any).clearCompassMarkers = () => {
  appContextPromise.then((appContext) => {
    const compass = appContext.services.compass;
    if (!compass) return;

    // Note: We don't have a clearAll method, but markers will be removed when out of range
    logger.debug('Compass markers will be cleared when out of range');
  });
};

logger.debug('=== Notification Test Functions ===');
logger.debug('testNotifications() - Test all types');
logger.debug('testSystemNotifications() - System area');
logger.debug('testChatNotifications() - Chat area');
logger.debug('testOverlayNotifications() - Overlay area');
logger.debug('testQuestNotifications() - Quest area');
logger.debug('clearChat() - Clear chat notifications');
logger.debug('toggleNotifications(true/false) - Enable/disable');
logger.debug('===================================');
logger.debug('');
logger.debug('=== Compass Test Functions ===');
logger.debug('testCompass() - Add test markers at N/E/S/W');
logger.debug('testCompassNearClip() - Test near-clip distance functionality');
logger.debug('===============================');
