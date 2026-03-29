/**
 * TouchOverlayService - Visual touch controls for mobile devices
 *
 * Creates and manages virtual joystick UI overlays.
 * Left joystick: Movement (WASD equivalent)
 * Right joystick: Camera rotation (mouse look equivalent)
 *
 * The service only creates the visual elements and reports joystick state.
 * The TouchController reads the state each frame to drive input handlers.
 */

import { getLogger } from '@nimbus/shared';

const logger = getLogger('TouchOverlayService');

/** Current joystick state: normalized direction and strength */
export interface JoystickState {
  active: boolean;
  /** Horizontal axis: -1 (left) to 1 (right) */
  dx: number;
  /** Vertical axis: -1 (up/forward) to 1 (down/backward) */
  dy: number;
  /** Distance from center, 0-1 */
  strength: number;
}

/** Current rotation joystick state: continuous analog stick */
export interface RotationState {
  active: boolean;
  /** Horizontal axis: -1 (left) to 1 (right) */
  dx: number;
  /** Vertical axis: -1 (up) to 1 (down) */
  dy: number;
  /** Distance from center, 0-1 */
  strength: number;
}

const JOYSTICK_SIZE = 130;
const KNOB_SIZE = 50;
const DEADZONE = 15;
const MARGIN = 30;
const ACTION_BUTTON_SIZE = 50;

export class TouchOverlayService {
  private container: HTMLDivElement | null = null;

  // Left joystick (movement)
  private leftBase: HTMLDivElement | null = null;
  private leftKnob: HTMLDivElement | null = null;
  private leftTouchId: number | null = null;
  private leftState: JoystickState = { active: false, dx: 0, dy: 0, strength: 0 };

  // Right joystick (rotation - continuous analog stick)
  private rightBase: HTMLDivElement | null = null;
  private rightKnob: HTMLDivElement | null = null;
  private rightTouchId: number | null = null;
  private rightState: RotationState = { active: false, dx: 0, dy: 0, strength: 0 };

  // Double-tap on right side for jump
  private lastRightTapTime = 0;
  private readonly doubleTapThreshold = 300;
  private jumpRequested = false;

  // Tap detection (no drag = click)
  private rightTouchStartX = 0;
  private rightTouchStartY = 0;
  private tapRequested = false;

  // Action buttons
  private movementToggleButton: HTMLDivElement | null = null;
  private movementToggleRequested = false;

  // Shortcut display toggle (T key equivalent)
  private shortcutToggleButton: HTMLDivElement | null = null;
  private shortcutToggleRequested = false;

  // Shortcut bar (keys 1-9)
  private shortcutBar: HTMLDivElement | null = null;
  private shortcutRequested: number | null = null;

  // Menu
  private menuButton: HTMLDivElement | null = null;
  private menuPanel: HTMLDivElement | null = null;
  private menuOpen = false;
  private menuActionRequested: string | null = null;

  initialize(): void {
    this.createOverlay();
    logger.debug('TouchOverlayService initialized');
  }

  dispose(): void {
    if (this.container) {
      this.container.remove();
      this.container = null;
    }
    this.leftBase = null;
    this.leftKnob = null;
    this.rightBase = null;
    this.rightKnob = null;
    logger.debug('TouchOverlayService disposed');
  }

  /** Get current movement joystick state (left) */
  getMovementState(): JoystickState {
    return this.leftState;
  }

  /** Get current rotation joystick state (right) - continuous, not consumed */
  getRotationState(): RotationState {
    return this.rightState;
  }

  /** Check and consume jump request (double-tap right) */
  consumeJumpRequest(): boolean {
    if (this.jumpRequested) {
      this.jumpRequested = false;
      return true;
    }
    return false;
  }

  /** Check and consume movement toggle request */
  consumeMovementToggleRequest(): boolean {
    if (this.movementToggleRequested) {
      this.movementToggleRequested = false;
      return true;
    }
    return false;
  }

  /** Check and consume tap request (single tap right, no drag) */
  consumeTapRequest(): boolean {
    if (this.tapRequested) {
      this.tapRequested = false;
      return true;
    }
    return false;
  }

  /** Check and consume shortcut key request (returns 1-9 or null) */
  consumeShortcutRequest(): number | null {
    const nr = this.shortcutRequested;
    this.shortcutRequested = null;
    return nr;
  }

  /** Check and consume shortcut display toggle request (T key equivalent) */
  consumeShortcutToggleRequest(): boolean {
    if (this.shortcutToggleRequested) {
      this.shortcutToggleRequested = false;
      return true;
    }
    return false;
  }

  /** Check and consume menu action request */
  consumeMenuAction(): string | null {
    const action = this.menuActionRequested;
    this.menuActionRequested = null;
    return action;
  }

  private createOverlay(): void {
    // Main container covers full screen, passes through pointer events
    this.container = document.createElement('div');
    this.container.id = 'touch-overlay';
    this.container.style.cssText = `
      position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
      z-index: 900; pointer-events: none;
    `;

    this.leftBase = this.createJoystickBase('left');
    this.leftKnob = this.createJoystickKnob(this.leftBase);
    this.leftBase.style.cssText += `
      left: ${MARGIN}px; bottom: ${MARGIN}px;
    `;

    this.rightBase = this.createJoystickBase('right');
    this.rightKnob = this.createJoystickKnob(this.rightBase);
    this.rightBase.style.cssText += `
      right: ${MARGIN}px; bottom: ${MARGIN}px;
    `;

    // Movement toggle button - above the left joystick
    this.movementToggleButton = this.createActionButton('move-toggle', '\u{1F3C3}');
    this.movementToggleButton.style.cssText += `
      left: ${MARGIN + (JOYSTICK_SIZE - ACTION_BUTTON_SIZE) / 2}px;
      bottom: ${MARGIN + JOYSTICK_SIZE + 20}px;
    `;
    this.movementToggleButton.addEventListener('touchstart', (e) => {
      e.preventDefault();
      e.stopPropagation();
      this.movementToggleRequested = true;
      this.flashButton(this.movementToggleButton);
    }, { passive: false });

    // Shortcut display toggle button - above the right joystick (T key equivalent)
    this.shortcutToggleButton = this.createActionButton('shortcut-toggle', '\u2606');
    this.shortcutToggleButton.style.cssText += `
      right: ${MARGIN + (JOYSTICK_SIZE - ACTION_BUTTON_SIZE) / 2}px;
      bottom: ${MARGIN + JOYSTICK_SIZE + 20}px;
    `;
    this.shortcutToggleButton.addEventListener('touchstart', (e) => {
      e.preventDefault();
      e.stopPropagation();
      this.shortcutToggleRequested = true;
      this.flashButton(this.shortcutToggleButton);
    }, { passive: false });

    // Menu button - top right
    this.menuButton = this.createActionButton('menu', '\u2630');
    this.menuButton.style.cssText += `
      right: ${MARGIN}px; top: ${MARGIN}px;
    `;
    this.menuButton.addEventListener('touchstart', (e) => {
      e.preventDefault();
      e.stopPropagation();
      this.toggleMenu();
    }, { passive: false });

    // Menu panel (hidden by default)
    this.menuPanel = this.createMenuPanel();

    this.container.appendChild(this.leftBase);
    this.container.appendChild(this.rightBase);
    this.container.appendChild(this.movementToggleButton);
    // Shortcut bar (keys 1-9, always visible)
    this.shortcutBar = this.createShortcutBar();

    this.container.appendChild(this.shortcutToggleButton);
    this.container.appendChild(this.shortcutBar);
    this.container.appendChild(this.menuButton);
    this.container.appendChild(this.menuPanel);
    document.body.appendChild(this.container);

    // Touch events on the full container (not individual joysticks)
    // so touches anywhere on the correct half of the screen work
    this.container.style.pointerEvents = 'auto';
    this.container.addEventListener('touchstart', this.onTouchStart, { passive: false });
    this.container.addEventListener('touchmove', this.onTouchMove, { passive: false });
    this.container.addEventListener('touchend', this.onTouchEnd, { passive: false });
    this.container.addEventListener('touchcancel', this.onTouchEnd, { passive: false });
  }

  private createJoystickBase(side: 'left' | 'right'): HTMLDivElement {
    const base = document.createElement('div');
    base.id = `joystick-${side}`;
    const half = JOYSTICK_SIZE / 2;
    base.style.cssText = `
      position: fixed;
      width: ${JOYSTICK_SIZE}px; height: ${JOYSTICK_SIZE}px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(255,255,255,0.08) 0%, rgba(255,255,255,0.03) 70%, rgba(255,255,255,0.01) 100%);
      border: 2px solid rgba(255,255,255,0.15);
      pointer-events: none;
    `;
    return base;
  }

  private createJoystickKnob(base: HTMLDivElement): HTMLDivElement {
    const knob = document.createElement('div');
    const offset = (JOYSTICK_SIZE - KNOB_SIZE) / 2;
    knob.style.cssText = `
      position: absolute;
      width: ${KNOB_SIZE}px; height: ${KNOB_SIZE}px;
      top: ${offset}px; left: ${offset}px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(255,255,255,0.35) 0%, rgba(255,255,255,0.15) 100%);
      border: 1px solid rgba(255,255,255,0.3);
      pointer-events: none;
      transition: none;
    `;
    base.appendChild(knob);
    return knob;
  }

  private getScreenZone(x: number): 'left' | 'right' {
    return x < window.innerWidth / 2 ? 'left' : 'right';
  }

  private getJoystickCenter(side: 'left' | 'right'): { cx: number; cy: number } {
    const base = side === 'left' ? this.leftBase : this.rightBase;
    if (!base) return { cx: 0, cy: 0 };
    const rect = base.getBoundingClientRect();
    return { cx: rect.left + rect.width / 2, cy: rect.top + rect.height / 2 };
  }

  // --- Touch event handlers ---

  private onTouchStart = (event: TouchEvent): void => {
    event.preventDefault();

    for (let i = 0; i < event.changedTouches.length; i++) {
      const touch = event.changedTouches[i];
      const zone = this.getScreenZone(touch.clientX);

      if (zone === 'left' && this.leftTouchId === null) {
        this.leftTouchId = touch.identifier;
        this.leftState = { active: true, dx: 0, dy: 0, strength: 0 };
      } else if (zone === 'right' && this.rightTouchId === null) {
        this.rightTouchId = touch.identifier;
        this.rightState = { active: true, dx: 0, dy: 0, strength: 0 };
        this.rightTouchStartX = touch.clientX;
        this.rightTouchStartY = touch.clientY;

        // Double-tap detection
        const now = Date.now();
        if (now - this.lastRightTapTime < this.doubleTapThreshold) {
          this.jumpRequested = true;
          this.lastRightTapTime = 0;
        } else {
          this.lastRightTapTime = now;
        }
      }
    }
  };

  private onTouchMove = (event: TouchEvent): void => {
    event.preventDefault();

    for (let i = 0; i < event.changedTouches.length; i++) {
      const touch = event.changedTouches[i];

      if (touch.identifier === this.leftTouchId) {
        this.updateLeftJoystick(touch.clientX, touch.clientY);
      } else if (touch.identifier === this.rightTouchId) {
        this.updateRightJoystick(touch.clientX, touch.clientY);
      }
    }
  };

  private onTouchEnd = (event: TouchEvent): void => {
    event.preventDefault();

    for (let i = 0; i < event.changedTouches.length; i++) {
      const touch = event.changedTouches[i];

      if (touch.identifier === this.leftTouchId) {
        this.leftTouchId = null;
        this.leftState = { active: false, dx: 0, dy: 0, strength: 0 };
        this.resetKnobVisual(this.leftKnob);
      } else if (touch.identifier === this.rightTouchId) {
        // Detect tap (barely moved)
        const dx = touch.clientX - this.rightTouchStartX;
        const dy = touch.clientY - this.rightTouchStartY;
        if (Math.abs(dx) < DEADZONE && Math.abs(dy) < DEADZONE) {
          this.tapRequested = true;
        }

        this.rightTouchId = null;
        this.rightState = { active: false, dx: 0, dy: 0, strength: 0 };
        this.resetKnobVisual(this.rightKnob);
      }
    }
  };

  private updateLeftJoystick(touchX: number, touchY: number): void {
    const { cx, cy } = this.getJoystickCenter('left');
    const dx = touchX - cx;
    const dy = touchY - cy;
    const distance = Math.sqrt(dx * dx + dy * dy);
    const maxRadius = JOYSTICK_SIZE / 2;

    if (distance < DEADZONE) {
      this.leftState = { active: true, dx: 0, dy: 0, strength: 0 };
      this.resetKnobVisual(this.leftKnob);
      return;
    }

    const strength = Math.min(distance / maxRadius, 1);
    const normDx = dx / distance;
    const normDy = dy / distance;

    this.leftState = {
      active: true,
      dx: normDx * strength,
      dy: normDy * strength,
      strength,
    };

    // Update knob visual position (clamped to base circle)
    if (this.leftKnob) {
      const clampedDist = Math.min(distance, maxRadius);
      const knobX = (normDx * clampedDist);
      const knobY = (normDy * clampedDist);
      const center = (JOYSTICK_SIZE - KNOB_SIZE) / 2;
      this.leftKnob.style.left = `${center + knobX}px`;
      this.leftKnob.style.top = `${center + knobY}px`;
    }
  }

  private updateRightJoystick(touchX: number, touchY: number): void {
    const { cx, cy } = this.getJoystickCenter('right');
    const dx = touchX - cx;
    const dy = touchY - cy;
    const distance = Math.sqrt(dx * dx + dy * dy);
    const maxRadius = JOYSTICK_SIZE / 2;

    if (distance < DEADZONE) {
      this.rightState = { active: true, dx: 0, dy: 0, strength: 0 };
      this.resetKnobVisual(this.rightKnob);
      return;
    }

    const strength = Math.min(distance / maxRadius, 1);
    const normDx = dx / distance;
    const normDy = dy / distance;

    this.rightState = {
      active: true,
      dx: normDx * strength,
      dy: normDy * strength,
      strength,
    };

    // Update knob visual position (clamped to base circle)
    if (this.rightKnob) {
      const clampedDist = Math.min(distance, maxRadius);
      const center = (JOYSTICK_SIZE - KNOB_SIZE) / 2;
      this.rightKnob.style.left = `${center + normDx * clampedDist}px`;
      this.rightKnob.style.top = `${center + normDy * clampedDist}px`;
    }
  }

  private createActionButton(id: string, label: string): HTMLDivElement {
    const btn = document.createElement('div');
    btn.id = `touch-btn-${id}`;
    btn.style.cssText = `
      position: fixed;
      width: ${ACTION_BUTTON_SIZE}px; height: ${ACTION_BUTTON_SIZE}px;
      border-radius: 50%;
      background: rgba(255,255,255,0.05);
      border: 2px solid rgba(255,255,255,0.15);
      pointer-events: auto;
      display: flex; align-items: center; justify-content: center;
      font-size: 20px; color: rgba(255,255,255,0.4);
      user-select: none; -webkit-user-select: none;
      transition: background 0.15s;
    `;
    btn.textContent = label;
    return btn;
  }

  private createShortcutBar(): HTMLDivElement {
    const bar = document.createElement('div');
    bar.id = 'touch-shortcut-bar';
    bar.style.cssText = `
      position: fixed;
      bottom: ${MARGIN}px;
      left: 50%; transform: translateX(-50%);
      display: flex;
      flex-direction: row; gap: 6px;
      pointer-events: auto;
    `;

    for (let i = 1; i <= 9; i++) {
      const btn = document.createElement('div');
      btn.style.cssText = `
        width: 36px; height: 36px;
        border-radius: 50%;
        background: rgba(255,255,255,0.05);
        border: 2px solid rgba(255,255,255,0.15);
        display: flex; align-items: center; justify-content: center;
        font-size: 14px; color: rgba(255,255,255,0.4);
        user-select: none; -webkit-user-select: none;
        transition: background 0.15s;
      `;
      btn.textContent = `${i}`;
      const shortcutNr = i;
      btn.addEventListener('touchstart', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.shortcutRequested = shortcutNr;
        btn.style.background = 'rgba(255,255,255,0.2)';
        setTimeout(() => { btn.style.background = 'rgba(255,255,255,0.05)'; }, 150);
      }, { passive: false });
      bar.appendChild(btn);
    }

    return bar;
  }

  private createMenuPanel(): HTMLDivElement {
    const panel = document.createElement('div');
    panel.id = 'touch-menu-panel';
    panel.style.cssText = `
      position: fixed;
      right: ${MARGIN}px; top: ${MARGIN + ACTION_BUTTON_SIZE + 10}px;
      display: none;
      flex-direction: column; gap: 8px;
      pointer-events: auto;
    `;

    const items: { id: string; label: string; icon: string }[] = [
      { id: 'message', label: 'Nachricht', icon: '\u2709' },
      { id: 'panel', label: 'Panel', icon: '\u25A3' },
      { id: 'viewToggle', label: 'Ansicht', icon: '\u{1F441}' },
      { id: 'fullscreen', label: 'Vollbild', icon: '\u26F6' },
    ];

    for (const item of items) {
      const btn = document.createElement('div');
      btn.style.cssText = `
        display: flex; align-items: center; gap: 8px;
        padding: 8px 14px;
        border-radius: 25px;
        background: rgba(0,0,0,0.6);
        border: 2px solid rgba(255,255,255,0.15);
        color: rgba(255,255,255,0.5);
        font-size: 14px;
        user-select: none; -webkit-user-select: none;
        transition: background 0.15s;
      `;
      btn.innerHTML = `<span style="font-size:18px">${item.icon}</span> ${item.label}`;
      btn.addEventListener('touchstart', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.menuActionRequested = item.id;
        this.toggleMenu();
      }, { passive: false });
      panel.appendChild(btn);
    }

    return panel;
  }

  private toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
    if (this.menuPanel) {
      this.menuPanel.style.display = this.menuOpen ? 'flex' : 'none';
    }
  }

  private flashButton(btn: HTMLDivElement | null): void {
    if (!btn) return;
    btn.style.background = 'rgba(255,255,255,0.2)';
    setTimeout(() => { btn.style.background = 'rgba(255,255,255,0.05)'; }, 150);
  }

  private resetKnobVisual(knob: HTMLDivElement | null): void {
    if (!knob) return;
    const center = (JOYSTICK_SIZE - KNOB_SIZE) / 2;
    knob.style.left = `${center}px`;
    knob.style.top = `${center}px`;
  }
}
