import { AppContext } from '../AppContext';
import { Vector3, getLogger, ExceptionHandler } from '@nimbus/shared';

const logger = getLogger('CompassService');

export type MarkerShape = 'circle' | 'diamond' | 'triangle' | 'arrow';
export type MarkerBarPosition = 'top' | 'center' | 'bottom';

export interface CompassMarker {
  id: string;
  position: Vector3;
  color: string;
  shape: MarkerShape;
  barPosition: MarkerBarPosition;
  range: number;
  nearClipDistance: number;
  close(): void;
  setPosition(position: Vector3): void;
}

class CompassMarkerImpl implements CompassMarker {
  id: string;
  position: Vector3;
  color: string;
  shape: MarkerShape;
  barPosition: MarkerBarPosition;
  range: number;
  nearClipDistance: number;
  private element: HTMLElement | null = null;
  private service: CompassService;

  constructor(
    id: string,
    position: Vector3,
    color: string,
    shape: MarkerShape,
    barPosition: MarkerBarPosition,
    range: number,
    nearClipDistance: number,
    service: CompassService
  ) {
    this.id = id;
    this.position = position;
    this.color = color;
    this.shape = shape;
    this.barPosition = barPosition;
    this.range = range;
    this.nearClipDistance = nearClipDistance;
    this.service = service;
  }

  setElement(element: HTMLElement): void {
    this.element = element;
  }

  getElement(): HTMLElement | null {
    return this.element;
  }

  close(): void {
    this.service.removeMarker(this.id);
  }

  setPosition(position: Vector3): void {
    this.position = position;
    this.service.updateMarkerPosition(this.id);
  }
}

export class CompassService {
  private appContext: AppContext;
  private compassContainer: HTMLElement | null = null;
  private compassBar: HTMLElement | null = null;
  private markersContainer: HTMLElement | null = null;
  private positionDisplay: HTMLElement | null = null;
  private timeDisplay: HTMLElement | null = null;
  private cardinalMarkers: Map<string, HTMLElement> = new Map();
  private markers: Map<string, CompassMarkerImpl> = new Map();
  private updateInterval: number | null = null;
  private lastUpdateTime: number = 0;
  private readonly UPDATE_THROTTLE_MS = 50; // 50ms = 20fps
  private markerIdCounter: number = 0;
  private disposed: boolean = false;

  constructor(appContext: AppContext) {
    this.appContext = appContext;
    this.init();
  }

  private init(): void {
    try {
      // Get compass container from DOM
      this.compassContainer = document.getElementById('compass-bar');
      if (!this.compassContainer) {
        logger.warn('Compass bar container not found in DOM');
        return;
      }

      // Create compass bar structure
      this.createCompassBar();

      // Start update loop
      this.startUpdateLoop();

      logger.debug('CompassService initialized');
    } catch (error) {
      ExceptionHandler.handle(error, 'Failed to initialize CompassService');
    }
  }

  private createCompassBar(): void {
    if (!this.compassContainer) return;

    // Create main compass bar
    this.compassBar = document.createElement('div');
    this.compassBar.className = 'compass-bar-content';

    // Create cardinal direction markers (N, E, S, W)
    const directions = [
      { name: 'N', angle: 0 },
      { name: 'E', angle: 90 },
      { name: 'S', angle: 180 },
      { name: 'W', angle: 270 },
    ];

    directions.forEach(({ name, angle }) => {
      const marker = document.createElement('div');
      marker.className = 'compass-cardinal';
      marker.textContent = name;
      marker.style.left = '50%';
      marker.setAttribute('data-angle', angle.toString());
      this.compassBar!.appendChild(marker);
      this.cardinalMarkers.set(name, marker);
    });

    // Create markers container (for custom markers)
    this.markersContainer = document.createElement('div');
    this.markersContainer.className = 'compass-markers';
    this.compassBar.appendChild(this.markersContainer);

    // Create time display with season (only in EDITOR mode, left side)
    // @ts-ignore - __EDITOR__ is defined by Vite
    if (typeof __EDITOR__ !== 'undefined' && __EDITOR__) {
      this.timeDisplay = document.createElement('div');
      this.timeDisplay.className = 'compass-time-display';
      this.timeDisplay.textContent = 'Season | 00:00';
      this.compassContainer.appendChild(this.timeDisplay);
    }

    this.compassContainer.appendChild(this.compassBar);

    // Create position display (only in EDITOR mode, right side)
    // @ts-ignore - __EDITOR__ is defined by Vite
    if (typeof __EDITOR__ !== 'undefined' && __EDITOR__) {
      this.positionDisplay = document.createElement('div');
      this.positionDisplay.className = 'compass-position-display';
      this.positionDisplay.textContent = 'X: 0 Y: 0 Z: 0';
      this.compassContainer.appendChild(this.positionDisplay);
    }
  }

  private startUpdateLoop(): void {
    // Use requestAnimationFrame for smooth updates
    const update = () => {
      if (this.disposed) return;

      const now = Date.now();
      if (now - this.lastUpdateTime >= this.UPDATE_THROTTLE_MS) {
        this.updateCompass();
        this.lastUpdateTime = now;
      }

      requestAnimationFrame(update);
    };

    requestAnimationFrame(update);
  }

  private updateCompass(): void {
    try {
      const cameraService = this.appContext.services.camera;
      const playerService = this.appContext.services.player;

      if (!cameraService || !playerService) return;

      const rotation = cameraService.getRotation();
      const playerPosition = playerService.getPosition();

      if (!rotation || !playerPosition) return;

      // Get camera yaw (rotation around Y axis)
      const yaw = rotation.y;

      // Update cardinal markers
      this.updateCardinalMarkers(yaw);

      // Update custom markers
      this.updateCustomMarkers(yaw, playerPosition);

      // Update time display with season (only in EDITOR mode)
      // @ts-ignore - __EDITOR__ is defined by Vite
      if (typeof __EDITOR__ !== 'undefined' && __EDITOR__ && this.timeDisplay) {
        const environmentService = this.appContext.services.environment;
        if (environmentService) {
          const seasonString = environmentService.getCurrentSeasonAsString?.() || 'Season';
          const timeString = environmentService.getWorldTimeCurrentAsString?.() || '00:00';
          this.timeDisplay.textContent = `${seasonString} | ${timeString}`;
        }
      }

      // Update position display (only in EDITOR mode)
      // @ts-ignore - __EDITOR__ is defined by Vite
      if (typeof __EDITOR__ !== 'undefined' && __EDITOR__ && this.positionDisplay) {
        const x = Math.round(playerPosition.x);
        const y = Math.round(playerPosition.y);
        const z = Math.round(playerPosition.z);
        const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
        const chunkX = Math.floor(playerPosition.x / chunkSize);
        const chunkZ = Math.floor(playerPosition.z / chunkSize);
        this.positionDisplay.textContent = `X: ${x} Y: ${y} Z: ${z} | ${chunkX}:${chunkZ}`;
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'Failed to update compass');
    }
  }

  private updateCardinalMarkers(cameraYaw: number): void {
    const compassWidth = this.compassContainer?.offsetWidth || 600;
    const centerX = compassWidth / 2;

    // Convert camera yaw to degrees (0 = North/Z+)
    const cameraAngleDeg = (cameraYaw * 180) / Math.PI;

    this.cardinalMarkers.forEach((element, name) => {
      const markerAngle = parseFloat(element.getAttribute('data-angle') || '0');

      // Calculate relative angle to camera
      let relativeAngle = markerAngle - cameraAngleDeg;

      // Normalize to -180 to 180
      while (relativeAngle > 180) relativeAngle -= 360;
      while (relativeAngle < -180) relativeAngle += 360;

      // Convert to position on compass bar (360° = full width)
      const relativePosition = (relativeAngle / 360) * compassWidth;
      const positionX = centerX + relativePosition;

      // Show/hide based on position
      if (positionX >= -20 && positionX <= compassWidth + 20) {
        element.style.display = 'block';
        element.style.left = `${positionX}px`;
      } else {
        element.style.display = 'none';
      }
    });
  }

  private updateCustomMarkers(cameraYaw: number, playerPosition: Vector3): void {
    const compassWidth = this.compassContainer?.offsetWidth || 600;
    const centerX = compassWidth / 2;

    this.markers.forEach((marker) => {
      const element = marker.getElement();
      if (!element) return;

      // Calculate distance to marker
      const dx = marker.position.x - playerPosition.x;
      const dz = marker.position.z - playerPosition.z;
      const distance = Math.sqrt(dx * dx + dz * dz);

      // Check if marker is too close (near clip)
      if (distance < marker.nearClipDistance) {
        element.style.display = 'none';
        return;
      }

      // Check if marker is in range
      if (marker.range >= 0 && distance > marker.range) {
        element.style.display = 'none';
        return;
      }

      // Calculate angle to marker (0 = North = Z+)
      // In the coordinate system: North = Z+, East = X+
      let angleToMarker = Math.atan2(dx, dz);
      const angleToMarkerDeg = (angleToMarker * 180) / Math.PI;
      const cameraAngleDeg = (cameraYaw * 180) / Math.PI;

      // Calculate relative angle
      let relativeAngle = angleToMarkerDeg - cameraAngleDeg;

      // Normalize to -180 to 180
      while (relativeAngle > 180) relativeAngle -= 360;
      while (relativeAngle < -180) relativeAngle += 360;

      // Convert to position on compass bar
      const relativePosition = (relativeAngle / 360) * compassWidth;
      const positionX = centerX + relativePosition;

      // Show/hide based on position
      if (positionX >= -20 && positionX <= compassWidth + 20) {
        element.style.display = 'block';
        element.style.left = `${positionX}px`;
      } else {
        element.style.display = 'none';
      }
    });
  }

  public addMarker(
    position: Vector3,
    color: string = 'red',
    shape: MarkerShape = 'circle',
    barPosition: MarkerBarPosition = 'center',
    range: number = -1,
    nearClipDistance: number = 5
  ): CompassMarker {
    const id = `marker-${this.markerIdCounter++}`;
    const marker = new CompassMarkerImpl(id, position, color, shape, barPosition, range, nearClipDistance, this);

    // Create DOM element
    const element = document.createElement('div');
    element.className = `compass-marker compass-marker-${barPosition}`;
    element.style.color = color;

    // Set marker symbol based on shape
    const symbols: Record<MarkerShape, string> = {
      circle: '●',
      diamond: '◆',
      triangle: '▲',
      arrow: '↑',
    };
    element.textContent = symbols[shape];

    marker.setElement(element);
    this.markersContainer?.appendChild(element);
    this.markers.set(id, marker);

    logger.debug(`Added marker ${id} at position (${position.x}, ${position.y}, ${position.z})`);

    return marker;
  }

  public removeMarker(id: string): void {
    const marker = this.markers.get(id);
    if (marker) {
      const element = marker.getElement();
      if (element) {
        element.remove();
      }
      this.markers.delete(id);
      logger.debug(`Removed marker ${id}`);
    }
  }

  public updateMarkerPosition(id: string): void {
    // Position will be updated on next update cycle
    logger.debug(`Updated marker ${id} position`);
  }

  public dispose(): void {
    this.disposed = true;

    // Clear all markers
    this.markers.forEach((marker) => {
      const element = marker.getElement();
      if (element) {
        element.remove();
      }
    });
    this.markers.clear();
    this.cardinalMarkers.clear();

    logger.debug('CompassService disposed');
  }
}
