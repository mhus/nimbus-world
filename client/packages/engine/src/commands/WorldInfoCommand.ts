/**
 * WorldInfoCommand - Shows complete world information
 *
 * Displays all WorldInfo fields including:
 * - Basic info (worldId, name, description)
 * - Season information (seasonStatus, seasonProgress)
 * - World boundaries (start, stop)
 * - World settings (max players, PvP, etc.)
 * - Environment configuration
 */

import { CommandHandler } from './CommandHandler';
import { getLogger, SeasonStatus } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('WorldInfoCommand');

/**
 * WorldInfo command - Shows complete world information
 */
export class WorldInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'worldInfo';
  }

  description(): string {
    return 'Shows complete world information including season, boundaries, and settings';
  }

  execute(parameters: any[]): any {
    const worldInfo = this.appContext.worldInfo;

    if (!worldInfo) {
      logger.info('World not loaded');
      return { error: 'World not loaded' };
    }

    const lines: string[] = [];
    lines.push('=== World Info ===');
    lines.push('');

    // Basic Information
    lines.push('Basic Information:');
    lines.push(`  World ID        : ${worldInfo.worldId}`);
    lines.push(`  Name            : ${worldInfo.name}`);
    if (worldInfo.description) {
      lines.push(`  Description     : ${worldInfo.description}`);
    }
    lines.push('');

    // Season Information
    lines.push('Season Information:');
    const seasonName = SeasonStatus[worldInfo.seasonStatus] || 'UNKNOWN';
    lines.push(`  Season          : ${seasonName} (${worldInfo.seasonStatus})`);
    lines.push(`  Season Progress : ${(worldInfo.seasonProgress * 100).toFixed(1)}%`);
    lines.push('');

    // World Boundaries
    if (worldInfo.start || worldInfo.stop) {
      lines.push('World Boundaries:');
      if (worldInfo.start) {
        lines.push(`  Start Position  : (${worldInfo.start.x}, ${worldInfo.start.y}, ${worldInfo.start.z})`);
      }
      if (worldInfo.stop) {
        lines.push(`  Stop Position   : (${worldInfo.stop.x}, ${worldInfo.stop.y}, ${worldInfo.stop.z})`);
      }
      lines.push('');
    }

    // World Configuration
    lines.push('World Configuration:');
    lines.push(`  Chunk Size      : ${worldInfo.chunkSize ?? 16}`);
    if (worldInfo.status !== undefined) {
      lines.push(`  Status          : ${worldInfo.status}`);
    }
    lines.push('');

    // Timestamps
    if (worldInfo.createdAt || worldInfo.updatedAt) {
      lines.push('Timestamps:');
      if (worldInfo.createdAt) {
        lines.push(`  Created At      : ${worldInfo.createdAt}`);
      }
      if (worldInfo.updatedAt) {
        lines.push(`  Updated At      : ${worldInfo.updatedAt}`);
      }
      lines.push('');
    }

    // Owner Information
    if (worldInfo.owner) {
      lines.push('Owner Information:');
      lines.push(`  User            : ${worldInfo.owner.user}`);
      lines.push(`  Display Name    : ${worldInfo.owner.title}`);
      if (worldInfo.owner.email) {
        lines.push(`  Email           : ${worldInfo.owner.email}`);
      }
      lines.push('');
    }

    // World Settings
    if (worldInfo.settings) {
      lines.push('World Settings:');
      lines.push(`  Max Players     : ${worldInfo.settings.maxPlayers}`);
      lines.push(`  Allow Guests    : ${worldInfo.settings.allowGuests}`);
      lines.push(`  PvP Enabled     : ${worldInfo.settings.pvpEnabled}`);
      lines.push(`  Ping Interval   : ${worldInfo.settings.pingInterval}ms`);

      if (worldInfo.settings.allowedMovementModes) {
        lines.push(`  Allowed Modes   : ${worldInfo.settings.allowedMovementModes.join(', ')}`);
      }
      if (worldInfo.settings.defaultMovementMode) {
        lines.push(`  Default Mode    : ${worldInfo.settings.defaultMovementMode}`);
      }

      // Audio Settings
      if (worldInfo.settings.deadAmbientAudio) {
        lines.push(`  Dead Audio      : ${worldInfo.settings.deadAmbientAudio}`);
      }
      if (worldInfo.settings.swimStepAudio) {
        lines.push(`  Swim Audio      : ${worldInfo.settings.swimStepAudio}`);
      }

      // Environment Settings
      if (worldInfo.settings.clearColor) {
        const c = worldInfo.settings.clearColor;
        lines.push(`  Clear Color     : RGB(${c.r}, ${c.g}, ${c.b})`);
      }
      if (worldInfo.settings.cameraMaxZ) {
        lines.push(`  Camera MaxZ     : ${worldInfo.settings.cameraMaxZ}`);
      }

      // Sun Settings
      if (worldInfo.settings.sunEnabled !== undefined) {
        lines.push(`  Sun Enabled     : ${worldInfo.settings.sunEnabled}`);
      }
      if (worldInfo.settings.sunTexture) {
        lines.push(`  Sun Texture     : ${worldInfo.settings.sunTexture}`);
      }
      if (worldInfo.settings.sunSize) {
        lines.push(`  Sun Size        : ${worldInfo.settings.sunSize}`);
      }
      if (worldInfo.settings.sunAngleY !== undefined) {
        lines.push(`  Sun Angle Y     : ${worldInfo.settings.sunAngleY}°`);
      }
      if (worldInfo.settings.sunElevation !== undefined) {
        lines.push(`  Sun Elevation   : ${worldInfo.settings.sunElevation}°`);
      }
      if (worldInfo.settings.sunColor) {
        const c = worldInfo.settings.sunColor;
        lines.push(`  Sun Color       : RGB(${c.r}, ${c.g}, ${c.b})`);
      }

      lines.push('');
    }

    // SkyBox Configuration
    if (worldInfo.settings?.skyBox) {
      const sb = worldInfo.settings.skyBox;
      lines.push('SkyBox Configuration:');
      lines.push(`  Enabled         : ${sb.enabled}`);
      lines.push(`  Mode            : ${sb.mode}`);
      if (sb.color) {
        lines.push(`  Color           : RGB(${sb.color.r}, ${sb.color.g}, ${sb.color.b})`);
      }
      if (sb.texturePath) {
        lines.push(`  Texture Path    : ${sb.texturePath}`);
      }
      if (sb.size) {
        lines.push(`  Size            : ${sb.size}`);
      }
      if (sb.rotation !== undefined) {
        lines.push(`  Rotation        : ${sb.rotation}°`);
      }
      lines.push('');
    }

    // Moon Configurations
    if (worldInfo.settings?.moons && worldInfo.settings.moons.length > 0) {
      lines.push('Moon Configurations:');
      worldInfo.settings.moons.forEach((moon, index) => {
        lines.push(`  Moon ${index}:`);
        lines.push(`    Enabled       : ${moon.enabled}`);
        if (moon.size) {
          lines.push(`    Size          : ${moon.size}`);
        }
        if (moon.positionOnCircle !== undefined) {
          lines.push(`    Position      : ${moon.positionOnCircle}°`);
        }
        if (moon.heightOverCamera !== undefined) {
          lines.push(`    Height        : ${moon.heightOverCamera}°`);
        }
        if (moon.distance) {
          lines.push(`    Distance      : ${moon.distance}`);
        }
        if (moon.phase !== undefined) {
          lines.push(`    Phase         : ${moon.phase.toFixed(2)}`);
        }
        if (moon.texture) {
          lines.push(`    Texture       : ${moon.texture}`);
        }
      });
      lines.push('');
    }

    // Horizon Gradient
    if (worldInfo.settings?.horizonGradient) {
      const hg = worldInfo.settings.horizonGradient;
      lines.push('Horizon Gradient:');
      lines.push(`  Enabled         : ${hg.enabled}`);
      if (hg.distance) {
        lines.push(`  Distance        : ${hg.distance}`);
      }
      if (hg.y !== undefined) {
        lines.push(`  Y Position      : ${hg.y}`);
      }
      if (hg.height) {
        lines.push(`  Height          : ${hg.height}`);
      }
      if (hg.color0) {
        lines.push(`  Color 0         : RGB(${hg.color0.r}, ${hg.color0.g}, ${hg.color0.b})`);
      }
      if (hg.color1) {
        lines.push(`  Color 1         : RGB(${hg.color1.r}, ${hg.color1.g}, ${hg.color1.b})`);
      }
      if (hg.alpha !== undefined) {
        lines.push(`  Alpha           : ${hg.alpha}`);
      }
      lines.push('');
    }

    // World Time Configuration
    if (worldInfo.settings?.worldTime) {
      const wt = worldInfo.settings.worldTime;
      lines.push('World Time Configuration:');
      if (wt.minuteScaling) {
        lines.push(`  Minute Scaling  : ${wt.minuteScaling}x`);
      }
      if (wt.minutesPerHour) {
        lines.push(`  Minutes/Hour    : ${wt.minutesPerHour}`);
      }
      if (wt.hoursPerDay) {
        lines.push(`  Hours/Day       : ${wt.hoursPerDay}`);
      }
      if (wt.daysPerMonth) {
        lines.push(`  Days/Month      : ${wt.daysPerMonth}`);
      }
      if (wt.monthsPerYear) {
        lines.push(`  Months/Year     : ${wt.monthsPerYear}`);
      }
      if (wt.currentEra) {
        lines.push(`  Current Era     : ${wt.currentEra}`);
      }

      if (wt.daySections) {
        const ds = wt.daySections;
        lines.push('  Day Sections:');
        if (ds.morningStart !== undefined) {
          lines.push(`    Morning Start : ${ds.morningStart}:00`);
        }
        if (ds.dayStart !== undefined) {
          lines.push(`    Day Start     : ${ds.dayStart}:00`);
        }
        if (ds.eveningStart !== undefined) {
          lines.push(`    Evening Start : ${ds.eveningStart}:00`);
        }
        if (ds.nightStart !== undefined) {
          lines.push(`    Night Start   : ${ds.nightStart}:00`);
        }
      }

      if (wt.celestialBodies) {
        const cb = wt.celestialBodies;
        lines.push('  Celestial Bodies:');
        if (cb.enabled !== undefined) {
          lines.push(`    Enabled       : ${cb.enabled}`);
        }
        if (cb.updateIntervalSeconds) {
          lines.push(`    Update Int.   : ${cb.updateIntervalSeconds}s`);
        }
        if (cb.activeMoons !== undefined) {
          lines.push(`    Active Moons  : ${cb.activeMoons}`);
        }
        if (cb.sunRotationHours) {
          lines.push(`    Sun Rotation  : ${cb.sunRotationHours}h`);
        }
        if (cb.moon0RotationHours) {
          lines.push(`    Moon 0 Rot.   : ${cb.moon0RotationHours}h`);
        }
        if (cb.moon1RotationHours) {
          lines.push(`    Moon 1 Rot.   : ${cb.moon1RotationHours}h`);
        }
        if (cb.moon2RotationHours) {
          lines.push(`    Moon 2 Rot.   : ${cb.moon2RotationHours}h`);
        }
      }
      lines.push('');
    }

    // Shadow Configuration
    if (worldInfo.settings?.shadows) {
      const s = worldInfo.settings.shadows;
      lines.push('Shadow Configuration:');
      lines.push(`  Enabled         : ${s.enabled}`);
      if (s.mapSize) {
        lines.push(`  Map Size        : ${s.mapSize}`);
      }
      if (s.quality) {
        lines.push(`  Quality         : ${s.quality}`);
      }
      if (s.darkness !== undefined) {
        lines.push(`  Darkness        : ${s.darkness}`);
      }
      if (s.maxDistance) {
        lines.push(`  Max Distance    : ${s.maxDistance}`);
      }
      lines.push('');
    }

    // Environment Scripts
    if (worldInfo.settings?.environmentScripts && worldInfo.settings.environmentScripts.length > 0) {
      lines.push('Environment Scripts:');
      worldInfo.settings.environmentScripts.forEach((script, index) => {
        lines.push(`  ${index + 1}. ${script.name} -> ${script.script}`);
      });
      lines.push('');
    }

    // Entry Point
    if (worldInfo.entryPoint) {
      // entryPoint is now { area: string; grid: HexVector2 }
      const grid = worldInfo.entryPoint.grid;
      lines.push(`  Entry Point   : Area=${worldInfo.entryPoint.area}, Grid=(${grid.q}, ${grid.r})`);
    }

    // Editor & Splash
    if (worldInfo.editorUrl) {
      lines.push(`Editor URL        : ${worldInfo.editorUrl}`);
    }
    if (worldInfo.splashScreen) {
      lines.push(`Splash Screen     : ${worldInfo.splashScreen}`);
    }
    if (worldInfo.splashScreenAudio) {
      lines.push(`Splash Audio      : ${worldInfo.splashScreenAudio}`);
    }

    lines.push('');
    lines.push('==================');

    const output = lines.join('\n');
    logger.info(output);

    // Return structured data
    return worldInfo;
  }
}
