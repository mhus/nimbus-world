/**
 * ModalService - Manages HTML IFrame modals
 *
 * Provides modal dialogs with IFrame content, configurable size and position.
 */

import { getLogger, ExceptionHandler, ModalFlags, IFrameMessageType, ModalSizePreset } from '@nimbus/shared';
import type { IFrameMessageFromChild } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type {
  ModalOptions,
  ModalReference,
  ModalSize,
  ModalPosition,
  ModalSizePositionPreset,
} from '../types/Modal';

const logger = getLogger('ModalService');

/**
 * Default modal size and position presets
 * Margins: 20px on all sides
 */
const SIZE_PRESETS: Record<ModalSizePreset, ModalSizePositionPreset> = {
  // Side panels (full height, with margins)
  [ModalSizePreset.LEFT]: {
    size: { width: 'calc(50% - 30px)', height: 'calc(100vh - 40px)' },
    position: { x: '20px', y: '20px' },
  },
  [ModalSizePreset.RIGHT]: {
    size: { width: 'calc(50% - 30px)', height: 'calc(100vh - 40px)' },
    position: { x: 'calc(50% + 10px)', y: '20px' },
  },

  // Top/Bottom panels (full width, with margins)
  [ModalSizePreset.TOP]: {
    size: { width: 'calc(100vw - 40px)', height: 'calc(50% - 30px)' },
    position: { x: '20px', y: '20px' },
  },
  [ModalSizePreset.BOTTOM]: {
    size: { width: 'calc(100vw - 40px)', height: 'calc(50% - 30px)' },
    position: { x: '20px', y: 'calc(50% + 10px)' },
  },

  // Center (small, medium, large)
  [ModalSizePreset.CENTER_SMALL]: {
    size: { width: '600px', height: '400px' },
    position: { x: 'center', y: 'center' },
  },
  [ModalSizePreset.CENTER_MEDIUM]: {
    size: { width: '800px', height: '600px' },
    position: { x: 'center', y: 'center' },
  },
  [ModalSizePreset.CENTER_LARGE]: {
    size: { width: '90vw', height: '90vh' },
    position: { x: 'center', y: 'center' },
  },

  // Quadrants (with margins)
  [ModalSizePreset.LEFT_TOP]: {
    size: { width: 'calc(50% - 30px)', height: 'calc(50% - 30px)' },
    position: { x: '20px', y: '20px' },
  },
  [ModalSizePreset.LEFT_BOTTOM]: {
    size: { width: 'calc(50% - 30px)', height: 'calc(50% - 30px)' },
    position: { x: '20px', y: 'calc(50% + 10px)' },
  },
  [ModalSizePreset.RIGHT_TOP]: {
    size: { width: 'calc(50% - 30px)', height: 'calc(50% - 30px)' },
    position: { x: 'calc(50% + 10px)', y: '20px' },
  },
  [ModalSizePreset.RIGHT_BOTTOM]: {
    size: { width: 'calc(50% - 30px)', height: 'calc(50% - 30px)' },
    position: { x: 'calc(50% + 10px)', y: 'calc(50% + 10px)' },
  },
};

/**
 * ModalService - Manages modal dialogs with IFrame content
 *
 * Features:
 * - IFrame-based modals for displaying websites
 * - Configurable size (presets or custom)
 * - Configurable position (center or absolute)
 * - Close via X button, ESC key, or backdrop click
 * - Multiple modals support with z-index management
 * - Reference key support for reusing modals
 * - Bitflags for options (CLOSEABLE, NO_BORDERS, BREAK_OUT)
 * - PostMessage communication with IFrame content
 */
export class ModalService {
  private appContext: AppContext;
  private modals: Map<string, ModalReference> = new Map();
  private modalsByReferenceKey: Map<string, ModalReference> = new Map();
  private nextModalId: number = 1;
  private baseZIndex: number = 10000;
  private messageHandler: ((event: MessageEvent<IFrameMessageFromChild>) => void) | null = null;

  constructor(appContext: AppContext) {
    this.appContext = appContext;

    // Setup postMessage listener for IFrame communication
    this.setupPostMessageListener();

    logger.debug('ModalService initialized');
  }

  /**
   * Open a modal with IFrame content
   *
   * @param referenceKey Reference key for reusing modals (optional)
   * @param title Modal title
   * @param url URL to load in IFrame
   * @param preset Size/position preset (optional, defaults to 'center_medium')
   * @param flags Behavior flags (optional, bitflags: CLOSEABLE, NO_BORDERS, BREAK_OUT)
   * @param onClose Optional callback function executed when modal closes
   * @returns Modal reference for closing
   */
  openModal(
    referenceKey: string | null,
    title: string,
    url: string,
    preset?: ModalSizePreset,
    flags: number = ModalFlags.CLOSEABLE,
    onClose?: (reason?: string) => void
  ): ModalReference {
    try {
      // Check if modal with this reference key already exists
      if (referenceKey) {
        const existingModal = this.modalsByReferenceKey.get(referenceKey);
        if (existingModal) {
          // Update existing modal with new URL
          logger.debug('Reusing existing modal', { referenceKey, url });
          this.updateModalURL(existingModal, url);
          return existingModal;
        }
      }

      // Generate unique modal ID
      const id = `modal-${this.nextModalId++}`;

      // Use preset or default
      const sizePreset = preset ?? ModalSizePreset.CENTER_MEDIUM;

      // Add embedded=true to URL
      const embeddedUrl = this.addEmbeddedParameter(url, true);

      // Create modal DOM structure
      const { backdrop, modalContainer, iframe } = this.createModalDOM(
        id,
        title,
        embeddedUrl,
        sizePreset,
        flags
      );

      // Create modal reference
      const modalRef: ModalReference = {
        id,
        referenceKey: referenceKey ?? undefined,
        element: backdrop,
        iframe,
        close: (reason?: string) => this.closeModal(modalRef, reason),
        changePosition: (newPreset: ModalSizePreset) =>
          this.changeModalPosition(modalRef, newPreset),
        onClose,
      };

      // Store reference
      this.modals.set(id, modalRef);
      if (referenceKey) {
        this.modalsByReferenceKey.set(referenceKey, modalRef);
      }

      // Setup event handlers
      this.setupEventHandlers(modalRef, flags);

      // Add to DOM
      document.body.appendChild(backdrop);

      logger.debug('Modal opened', { id, referenceKey, title, url, preset: sizePreset, flags });

      return modalRef;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ModalService.openModal', {
        referenceKey,
        title,
        url,
        preset,
        flags,
      });
    }
  }

  /**
   * Close a specific modal
   *
   * @param ref Modal reference to close
   * @param reason Optional reason for closing
   */
  closeModal(ref: ModalReference, reason?: string): void {
    try {
      // Execute onClose callback if provided
      if (ref.onClose) {
        try {
          ref.onClose(reason);
        } catch (error) {
          ExceptionHandler.handle(error, 'ModalService.closeModal.onCloseCallback', {
            modalId: ref.id,
            reason,
          });
        }
      }

      // Remove from DOM
      if (ref.element.parentNode) {
        ref.element.parentNode.removeChild(ref.element);
      }

      // Remove from tracking
      this.modals.delete(ref.id);
      if (ref.referenceKey) {
        this.modalsByReferenceKey.delete(ref.referenceKey);
      }

      // Cleanup event listeners
      const escHandler = (ref as any)._escHandler;
      if (escHandler) {
        document.removeEventListener('keydown', escHandler);
        delete (ref as any)._escHandler;
      }

      // Cleanup moveable handlers
      const moveHandlers = (ref as any)._moveHandlers;
      if (moveHandlers) {
        moveHandlers.header.removeEventListener('mousedown', moveHandlers.onMouseDown);
        document.removeEventListener('mousemove', moveHandlers.onMouseMove);
        document.removeEventListener('mouseup', moveHandlers.onMouseUp);
        delete (ref as any)._moveHandlers;
      }

      // Cleanup resizeable handlers
      const resizeHandles = (ref as any)._resizeHandles;
      if (resizeHandles) {
        resizeHandles.forEach((handle: HTMLElement) => {
          const handlers = (handle as any)._resizeHandlers;
          if (handlers) {
            handle.removeEventListener('mousedown', handlers.onMouseDown);
            document.removeEventListener('mousemove', handlers.onMouseMove);
            document.removeEventListener('mouseup', handlers.onMouseUp);
          }
        });
        delete (ref as any)._resizeHandles;
      }

      logger.debug('Modal closed', { id: ref.id, referenceKey: ref.referenceKey, reason });
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.closeModal', { ref, reason });
    }
  }

  /**
   * Close all open modals
   */
  closeAll(): void {
    try {
      const modalRefs = Array.from(this.modals.values());

      modalRefs.forEach((ref) => {
        this.closeModal(ref);
      });

      logger.debug('All modals closed', { count: modalRefs.length });
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.closeAll');
    }
  }

  /**
   * Get count of currently open modals
   */
  getOpenModalCount(): number {
    return this.modals.size;
  }

  /**
   * Create modal DOM structure
   */
  private createModalDOM(
    id: string,
    title: string,
    url: string,
    preset: ModalSizePreset,
    flags: number
  ): { backdrop: HTMLElement; modalContainer: HTMLElement; iframe: HTMLIFrameElement } {
    // Calculate z-index (each modal gets higher z-index)
    const zIndex = this.baseZIndex + this.modals.size * 2;

    // Get preset configuration
    const presetConfig = SIZE_PRESETS[preset];
    const size = presetConfig.size;
    const position = presetConfig.position;

    // Check flags
    const isCloseable = (flags & ModalFlags.CLOSEABLE) !== 0;
    const noBorders = (flags & ModalFlags.NO_BORDERS) !== 0;
    const hasBreakOut = (flags & ModalFlags.BREAK_OUT) !== 0;
    const noBackgroundLock = (flags & ModalFlags.NO_BACKGROUND_LOCK) !== 0;
    const isMoveable = (flags & ModalFlags.MOVEABLE) !== 0;
    const isResizeable = (flags & ModalFlags.RESIZEABLE) !== 0;

    // Create backdrop
    const backdrop = document.createElement('div');
    backdrop.className = 'nimbus-modal-backdrop';
    backdrop.id = `${id}-backdrop`;
    backdrop.style.zIndex = zIndex.toString();

    // Apply NO_BACKGROUND_LOCK styling
    if (noBackgroundLock) {
      backdrop.classList.add('nimbus-modal-no-background-lock');
    }

    // Determine if centered
    const isCentered = position.x === 'center' && position.y === 'center';
    if (isCentered) {
      backdrop.classList.add('nimbus-modal-centered');
    }

    // Create modal container
    const modalContainer = document.createElement('div');
    modalContainer.className = 'nimbus-modal-container';
    modalContainer.id = id;

    // Apply NO_BORDERS styling
    if (noBorders) {
      modalContainer.classList.add('nimbus-modal-no-borders');
    }

    // Apply MOVEABLE styling
    if (isMoveable) {
      modalContainer.classList.add('nimbus-modal-moveable');
    }

    // Apply RESIZEABLE styling
    if (isResizeable) {
      modalContainer.classList.add('nimbus-modal-resizeable');
    }

    // Apply size
    modalContainer.style.width = size.width;
    modalContainer.style.height = size.height;

    // Apply position
    this.applyPosition(modalContainer, position);

    // Create header (unless NO_BORDERS)
    if (!noBorders) {
      const header = document.createElement('div');
      header.className = 'nimbus-modal-header';

      // Add moveable class to header
      if (isMoveable) {
        header.classList.add('nimbus-modal-header-moveable');
      }

      // Title
      const titleElement = document.createElement('h2');
      titleElement.className = 'nimbus-modal-title';
      titleElement.textContent = title;
      header.appendChild(titleElement);

      // Buttons container
      const buttonsContainer = document.createElement('div');
      buttonsContainer.className = 'nimbus-modal-buttons';

      // Minimize button (if enabled)
      const isMinimizable = (flags & ModalFlags.MINIMIZABLE) !== 0;
      if (isMinimizable) {
        const minimizeButton = document.createElement('button');
        minimizeButton.className = 'nimbus-modal-minimize';
        minimizeButton.innerHTML = '&#x2013;'; // – (en dash)
        minimizeButton.setAttribute('aria-label', 'Minimize modal');
        buttonsContainer.appendChild(minimizeButton);
      }

      // Break-out button (if enabled)
      if (hasBreakOut) {
        const breakOutButton = document.createElement('button');
        breakOutButton.className = 'nimbus-modal-breakout';
        breakOutButton.innerHTML = '&#x2197;'; // ↗ arrow
        breakOutButton.setAttribute('aria-label', 'Open in new window');
        breakOutButton.setAttribute('data-url', url);
        buttonsContainer.appendChild(breakOutButton);
      }

      // Close button (if closeable)
      if (isCloseable) {
        const closeButton = document.createElement('button');
        closeButton.className = 'nimbus-modal-close';
        closeButton.innerHTML = '&times;';
        closeButton.setAttribute('aria-label', 'Close modal');
        buttonsContainer.appendChild(closeButton);
      }

      header.appendChild(buttonsContainer);
      modalContainer.appendChild(header);
    }

    // Create IFrame
    const iframe = document.createElement('iframe');
    iframe.className = 'nimbus-modal-iframe';
    iframe.src = url;
    iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts allow-forms allow-popups');
    iframe.setAttribute('data-modal-id', id);

    // Assemble structure
    modalContainer.appendChild(iframe);
    backdrop.appendChild(modalContainer);

    return { backdrop, modalContainer, iframe };
  }

  /**
   * Apply position to modal container
   */
  private applyPosition(element: HTMLElement, position: ModalPosition): void {
    const xCentered = position.x === 'center';
    const yCentered = position.y === 'center';

    if (xCentered && yCentered) {
      // Both centered - use flex centering (handled by backdrop)
      element.style.position = 'relative';
    } else if (xCentered) {
      // Only X centered
      element.style.position = 'absolute';
      element.style.left = '50%';
      element.style.top = position.y;
      element.style.transform = 'translateX(-50%)';
    } else if (yCentered) {
      // Only Y centered
      element.style.position = 'absolute';
      element.style.left = position.x;
      element.style.top = '50%';
      element.style.transform = 'translateY(-50%)';
    } else {
      // Both absolute
      element.style.position = 'absolute';
      element.style.left = position.x;
      element.style.top = position.y;
    }
  }

  /**
   * Setup event handlers for modal
   */
  private setupEventHandlers(modalRef: ModalReference, flags: number): void {
    const backdrop = modalRef.element;
    const isCloseable = (flags & ModalFlags.CLOSEABLE) !== 0;
    const hasBreakOut = (flags & ModalFlags.BREAK_OUT) !== 0;
    const noBackgroundLock = (flags & ModalFlags.NO_BACKGROUND_LOCK) !== 0;
    const isMoveable = (flags & ModalFlags.MOVEABLE) !== 0;
    const isResizeable = (flags & ModalFlags.RESIZEABLE) !== 0;
    const isMinimizable = (flags & ModalFlags.MINIMIZABLE) !== 0;

    // Minimize button click
    const minimizeButton = backdrop.querySelector('.nimbus-modal-minimize');
    if (minimizeButton && isMinimizable) {
      minimizeButton.addEventListener('click', () => {
        this.toggleMinimize(modalRef);
      });
    }

    // Close button click
    const closeButton = backdrop.querySelector('.nimbus-modal-close');
    if (closeButton && isCloseable) {
      closeButton.addEventListener('click', () => {
        modalRef.close('user_closed');
      });
    }

    // Break-out button click
    const breakOutButton = backdrop.querySelector('.nimbus-modal-breakout');
    if (breakOutButton && hasBreakOut) {
      breakOutButton.addEventListener('click', () => {
        const url = breakOutButton.getAttribute('data-url');
        if (url) {
          this.breakOutModal(modalRef, url);
        }
      });
    }

    // Backdrop click (only close if closeable AND not NO_BACKGROUND_LOCK)
    if (isCloseable && !noBackgroundLock) {
      backdrop.addEventListener('click', (e) => {
        // Only close if clicking directly on backdrop, not on modal content
        if (e.target === backdrop) {
          modalRef.close('backdrop_click');
        }
      });
    }

    // ESC key (only if closeable)
    if (isCloseable) {
      const escHandler = (e: KeyboardEvent) => {
        if (e.key === 'Escape') {
          // Only close top-most modal
          const topModal = this.getTopModal();
          if (topModal && topModal.id === modalRef.id) {
            modalRef.close('escape_key');
          }
        }
      };

      document.addEventListener('keydown', escHandler);

      // Store handler for cleanup
      (modalRef as any)._escHandler = escHandler;
    }

    // Setup MOVEABLE functionality
    if (isMoveable) {
      this.setupMoveableModal(modalRef);
    }

    // Setup RESIZEABLE functionality
    if (isResizeable) {
      this.setupResizeableModal(modalRef);
    }
  }

  /**
   * Setup moveable modal (drag by header)
   */
  private setupMoveableModal(modalRef: ModalReference): void {
    const backdrop = modalRef.element;
    const modalContainer = backdrop.querySelector('.nimbus-modal-container') as HTMLElement;
    const header = backdrop.querySelector('.nimbus-modal-header') as HTMLElement;

    if (!modalContainer || !header) {
      return;
    }

    let isDragging = false;
    let dragStartX = 0;
    let dragStartY = 0;
    let modalStartLeft = 0;
    let modalStartTop = 0;

    const onMouseDown = (e: MouseEvent) => {
      // Only start drag if clicking on header (not buttons)
      if (
        e.target === header ||
        (e.target as HTMLElement).classList.contains('nimbus-modal-title')
      ) {
        isDragging = true;
        dragStartX = e.clientX;
        dragStartY = e.clientY;

        // Get current position
        const rect = modalContainer.getBoundingClientRect();
        modalStartLeft = rect.left;
        modalStartTop = rect.top;

        // Convert to absolute positioning if not already
        if (modalContainer.style.position !== 'absolute') {
          modalContainer.style.position = 'absolute';
          modalContainer.style.left = `${modalStartLeft}px`;
          modalContainer.style.top = `${modalStartTop}px`;
          modalContainer.style.transform = 'none';
        }

        e.preventDefault();
      }
    };

    const onMouseMove = (e: MouseEvent) => {
      if (!isDragging) return;

      const deltaX = e.clientX - dragStartX;
      const deltaY = e.clientY - dragStartY;

      const newLeft = modalStartLeft + deltaX;
      const newTop = modalStartTop + deltaY;

      modalContainer.style.left = `${newLeft}px`;
      modalContainer.style.top = `${newTop}px`;
    };

    const onMouseUp = () => {
      isDragging = false;
    };

    header.addEventListener('mousedown', onMouseDown);
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);

    // Store handlers for cleanup
    (modalRef as any)._moveHandlers = { onMouseDown, onMouseMove, onMouseUp, header };
  }

  /**
   * Setup resizeable modal (resize by edges/corners)
   */
  private setupResizeableModal(modalRef: ModalReference): void {
    const backdrop = modalRef.element;
    const modalContainer = backdrop.querySelector('.nimbus-modal-container') as HTMLElement;

    if (!modalContainer) {
      return;
    }

    // Create resize handles
    const handles = [
      { class: 'nimbus-resize-handle-n', cursor: 'ns-resize', edge: 'top' },
      { class: 'nimbus-resize-handle-s', cursor: 'ns-resize', edge: 'bottom' },
      { class: 'nimbus-resize-handle-e', cursor: 'ew-resize', edge: 'right' },
      { class: 'nimbus-resize-handle-w', cursor: 'ew-resize', edge: 'left' },
      { class: 'nimbus-resize-handle-ne', cursor: 'nesw-resize', edge: 'top-right' },
      { class: 'nimbus-resize-handle-nw', cursor: 'nwse-resize', edge: 'top-left' },
      { class: 'nimbus-resize-handle-se', cursor: 'nwse-resize', edge: 'bottom-right' },
      { class: 'nimbus-resize-handle-sw', cursor: 'nesw-resize', edge: 'bottom-left' },
    ];

    const resizeHandles: HTMLElement[] = [];

    handles.forEach(({ class: className, cursor, edge }) => {
      const handle = document.createElement('div');
      handle.className = className;
      handle.style.cursor = cursor;
      handle.setAttribute('data-edge', edge);
      modalContainer.appendChild(handle);
      resizeHandles.push(handle);

      let isResizing = false;
      let resizeStartX = 0;
      let resizeStartY = 0;
      let modalStartWidth = 0;
      let modalStartHeight = 0;
      let modalStartLeft = 0;
      let modalStartTop = 0;

      const onMouseDown = (e: MouseEvent) => {
        isResizing = true;
        resizeStartX = e.clientX;
        resizeStartY = e.clientY;

        const rect = modalContainer.getBoundingClientRect();
        modalStartWidth = rect.width;
        modalStartHeight = rect.height;
        modalStartLeft = rect.left;
        modalStartTop = rect.top;

        // Convert to absolute positioning if not already
        if (modalContainer.style.position !== 'absolute') {
          modalContainer.style.position = 'absolute';
          modalContainer.style.left = `${modalStartLeft}px`;
          modalContainer.style.top = `${modalStartTop}px`;
          modalContainer.style.transform = 'none';
        }

        e.preventDefault();
        e.stopPropagation();
      };

      const onMouseMove = (e: MouseEvent) => {
        if (!isResizing) return;

        const deltaX = e.clientX - resizeStartX;
        const deltaY = e.clientY - resizeStartY;

        let newWidth = modalStartWidth;
        let newHeight = modalStartHeight;
        let newLeft = modalStartLeft;
        let newTop = modalStartTop;

        // Handle different edges
        if (edge.includes('right')) {
          newWidth = Math.max(200, modalStartWidth + deltaX);
        }
        if (edge.includes('left')) {
          newWidth = Math.max(200, modalStartWidth - deltaX);
          newLeft = modalStartLeft + deltaX;
          if (newWidth === 200) newLeft = modalStartLeft + modalStartWidth - 200;
        }
        if (edge.includes('bottom')) {
          newHeight = Math.max(150, modalStartHeight + deltaY);
        }
        if (edge.includes('top')) {
          newHeight = Math.max(150, modalStartHeight - deltaY);
          newTop = modalStartTop + deltaY;
          if (newHeight === 150) newTop = modalStartTop + modalStartHeight - 150;
        }

        modalContainer.style.width = `${newWidth}px`;
        modalContainer.style.height = `${newHeight}px`;
        modalContainer.style.left = `${newLeft}px`;
        modalContainer.style.top = `${newTop}px`;
      };

      const onMouseUp = () => {
        isResizing = false;
      };

      handle.addEventListener('mousedown', onMouseDown);
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);

      // Store handlers for cleanup
      (handle as any)._resizeHandlers = { onMouseDown, onMouseMove, onMouseUp };
    });

    // Store resize handles for cleanup
    (modalRef as any)._resizeHandles = resizeHandles;
  }

  /**
   * Get top-most (highest z-index) modal
   */
  private getTopModal(): ModalReference | null {
    const modals = Array.from(this.modals.values());
    if (modals.length === 0) return null;

    // Last modal in the map is the most recent one
    return modals[modals.length - 1];
  }

  /**
   * Add embedded, worldId, and sessionId parameters to URL
   */
  private addEmbeddedParameter(url: string, embedded: boolean): string {
    try {
      const urlObj = new URL(url, window.location.href);

      // Add embedded parameter
      urlObj.searchParams.set('embedded', embedded.toString());

      // Add worldId if available
      if (this.appContext.worldInfo?.worldId) {
        urlObj.searchParams.set('worldId', this.appContext.worldInfo.worldId);
      }

      // Add sessionId if available
      if (this.appContext.sessionId) {
        urlObj.searchParams.set('sessionId', this.appContext.sessionId);
      }

      return urlObj.toString();
    } catch (error) {
      // If URL parsing fails, append as query string
      const separator = url.includes('?') ? '&' : '?';
      let params = `embedded=${embedded}`;

      if (this.appContext.worldInfo?.worldId) {
        params += `&worldId=${this.appContext.worldInfo.worldId}`;
      }

      if (this.appContext.sessionId) {
        params += `&sessionId=${this.appContext.sessionId}`;
      }

      return `${url}${separator}${params}`;
    }
  }

  /**
   * Update modal URL (for reusing existing modals)
   */
  private updateModalURL(modalRef: ModalReference, url: string): void {
    try {
      const embeddedUrl = this.addEmbeddedParameter(url, true);
      modalRef.iframe.src = embeddedUrl;

      // Update break-out button URL if present
      const breakOutButton = modalRef.element.querySelector('.nimbus-modal-breakout');
      if (breakOutButton) {
        breakOutButton.setAttribute('data-url', embeddedUrl);
      }

      logger.debug('Modal URL updated', { id: modalRef.id, url: embeddedUrl });
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.updateModalURL', { modalRef, url });
    }
  }

  /**
   * Change modal position to a new preset
   */
  private changeModalPosition(modalRef: ModalReference, preset: ModalSizePreset): void {
    try {
      const presetConfig = SIZE_PRESETS[preset];
      const size = presetConfig.size;
      const position = presetConfig.position;

      // Find modal container
      const modalContainer = modalRef.element.querySelector('.nimbus-modal-container') as HTMLElement;
      if (!modalContainer) {
        logger.warn('Modal container not found', { id: modalRef.id });
        return;
      }

      // Apply new size
      modalContainer.style.width = size.width;
      modalContainer.style.height = size.height;

      // Apply new position
      this.applyPosition(modalContainer, position);

      // Update backdrop centering class
      const isCentered = position.x === 'center' && position.y === 'center';
      if (isCentered) {
        modalRef.element.classList.add('nimbus-modal-centered');
      } else {
        modalRef.element.classList.remove('nimbus-modal-centered');
      }

      logger.debug('Modal position changed', { id: modalRef.id, preset });
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.changeModalPosition', { modalRef, preset });
    }
  }

  /**
   * Toggle minimize state of modal
   */
  private toggleMinimize(modalRef: ModalReference): void {
    try {
      const backdrop = modalRef.element;
      const modalContainer = backdrop.querySelector('.nimbus-modal-container') as HTMLElement;
      const iframe = modalContainer?.querySelector('.nimbus-modal-iframe') as HTMLElement;
      const minimizeButton = backdrop.querySelector('.nimbus-modal-minimize');

      if (!modalContainer || !minimizeButton) {
        return;
      }

      const isMinimized = modalContainer.classList.contains('nimbus-modal-minimized');

      if (isMinimized) {
        // Restore
        modalContainer.classList.remove('nimbus-modal-minimized');
        if (iframe) {
          iframe.style.display = '';
        }
        minimizeButton.innerHTML = '&#x2013;'; // – (en dash)
        minimizeButton.setAttribute('aria-label', 'Minimize modal');
        logger.debug('Modal restored', { id: modalRef.id });
      } else {
        // Minimize
        modalContainer.classList.add('nimbus-modal-minimized');
        if (iframe) {
          iframe.style.display = 'none';
        }
        minimizeButton.innerHTML = '&#x25A1;'; // □ (square)
        minimizeButton.setAttribute('aria-label', 'Restore modal');
        logger.debug('Modal minimized', { id: modalRef.id });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.toggleMinimize', { modalRef });
    }
  }

  /**
   * Break out modal to new window
   */
  private breakOutModal(modalRef: ModalReference, url: string): void {
    try {
      // Open URL in new window with embedded=false
      const breakOutUrl = this.addEmbeddedParameter(url, false);
      window.open(breakOutUrl, '_blank');

      // Close this modal
      modalRef.close('break_out');

      logger.debug('Modal broken out', { id: modalRef.id, url: breakOutUrl });
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.breakOutModal', { modalRef, url });
    }
  }

  /**
   * Setup postMessage listener for IFrame communication
   */
  private setupPostMessageListener(): void {
    this.messageHandler = (event: MessageEvent<IFrameMessageFromChild>) => {
      try {
        // Security: Check if message is from one of our iframes
        const iframe = Array.from(this.modals.values())
          .map((ref) => ref.iframe)
          .find((iframe) => iframe.contentWindow === event.source);

        if (!iframe) {
          // Message not from our iframe, ignore
          return;
        }

        // Get modal reference
        const modalId = iframe.getAttribute('data-modal-id');
        if (!modalId) {
          logger.warn('IFrame missing data-modal-id attribute');
          return;
        }

        const modalRef = this.modals.get(modalId);
        if (!modalRef) {
          logger.warn('Modal not found for IFrame message', { modalId });
          return;
        }

        // Handle message
        this.handleIFrameMessage(modalRef, event.data);
      } catch (error) {
        ExceptionHandler.handle(error, 'ModalService.messageHandler', { event });
      }
    };

    window.addEventListener('message', this.messageHandler);
  }

  /**
   * Handle IFrame message
   */
  private handleIFrameMessage(modalRef: ModalReference, message: IFrameMessageFromChild): void {
    try {
      switch (message.type) {
        case IFrameMessageType.IFRAME_READY:
          logger.debug('IFrame ready', { id: modalRef.id });
          break;

        case IFrameMessageType.REQUEST_CLOSE:
          logger.debug('IFrame requests close', { id: modalRef.id, reason: message.reason });
          modalRef.close(message.reason ?? 'iframe_request');
          break;

        case IFrameMessageType.REQUEST_POSITION_CHANGE:
          logger.debug('IFrame requests position change', {
            id: modalRef.id,
            preset: message.preset,
          });
          modalRef.changePosition(message.preset);
          break;

        case IFrameMessageType.NOTIFICATION:
          logger.debug('IFrame notification', {
            id: modalRef.id,
            notificationType: message.notificationType,
            from: message.from,
            message: message.message,
          });

          // Forward to NotificationService if available
          if (this.appContext.services?.notification) {
            // Parse notificationType string to NotificationType enum
            const notificationType =
              parseInt(message.notificationType, 10) || 0; // Default to SYSTEM_INFO
            this.appContext.services.notification.newNotification(
              notificationType,
              message.from,
              message.message
            );
          }
          break;

        default:
          logger.warn('Unknown IFrame message type', { message });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.handleIFrameMessage', { modalRef, message });
    }
  }

  /**
   * Open block editor modal for a specific block position
   *
   * @param x World X coordinate
   * @param y World Y coordinate
   * @param z World Z coordinate
   * @returns Modal reference
   */
  openBlockEditor(x: number, y: number, z: number): ModalReference {
    try {
      // Exit pointer lock so user can interact with modal
      if (document.pointerLockElement) {
        document.exitPointerLock();
        logger.debug('Exited pointer lock for block editor');
      }

      // Get component base URL from NetworkService
      const componentBaseUrl = this.appContext?.services.network?.getComponentBaseUrl();

      if (!componentBaseUrl) {
        logger.warn('No editor URL configured for this world');
        throw new Error('Block editor is not available in this world');
      }

      // Build URL with path and parameters
      const separator = componentBaseUrl.includes('?') ? '&' : '?';
      const worldId = this.appContext.worldInfo?.worldId;
      const editorUrl = `${componentBaseUrl}block-editor.html${separator}world=${worldId}&x=${x}&y=${y}&z=${z}`;

      logger.debug('Opening block editor', { position: { x, y, z }, editorUrl });

      return this.openModal(
        'block-editor', // referenceKey - reuse same modal for editor
        `Block Editor`,
        editorUrl,
        ModalSizePreset.RIGHT,
        ModalFlags.CLOSEABLE | ModalFlags.BREAK_OUT | ModalFlags.RESIZEABLE | ModalFlags.MOVEABLE | ModalFlags.NO_BACKGROUND_LOCK | ModalFlags.MINIMIZABLE
      );
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ModalService.openBlockEditor', { x, y, z });
    }
  }

  /**
   * Open edit configuration modal (top-left position)
   *
   * @returns Modal reference
   */
  openEditConfiguration(): ModalReference {
    try {
      // Exit pointer lock so user can interact with modal
      if (document.pointerLockElement) {
        document.exitPointerLock();
        logger.debug('Exited pointer lock for edit configuration');
      }

      // Get editor base URL from NetworkService
      const componentBaseUrl = this.appContext?.services.network?.getComponentBaseUrl();

      if (!componentBaseUrl) {
        logger.warn('No editor URL configured for this world');
        throw new Error('Edit configuration editor is not available in this world');
      }

      // Build URL with path and parameters
      const separator = componentBaseUrl.includes('?') ? '&' : '?';
      const worldId = this.appContext.worldInfo?.worldId;
      const sessionId = this.appContext.sessionId;

      const editorUrl = `${componentBaseUrl}edit-config.html${separator}embedded=true&worldId=${worldId}&sessionId=${sessionId}`;

      logger.debug('Opening edit configuration modal', { editorUrl });

      return this.openModal(
        'edit-config', // referenceKey
        'Edit Configuration',
        editorUrl,
        ModalSizePreset.LEFT_TOP, // Top-left quadrant (50% x 50%)
        ModalFlags.CLOSEABLE | ModalFlags.MOVEABLE | ModalFlags.NO_BACKGROUND_LOCK | ModalFlags.RESIZEABLE | ModalFlags.BREAK_OUT | ModalFlags.MINIMIZABLE
      );
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ModalService.openEditConfiguration');
    }
  }

  /**
   * Open agent chat widget
   *
   * @returns Modal reference
   */
  openAgentChat(): ModalReference {
    try {
      // Exit pointer lock so user can interact with modal
      if (document.pointerLockElement) {
        document.exitPointerLock();
        logger.debug('Exited pointer lock for agent chat');
      }

      // Get component base URL from NetworkService
      const componentBaseUrl = this.appContext?.services.network?.getComponentBaseUrl();

      if (!componentBaseUrl) {
        logger.warn('No component URL configured for this world');
        throw new Error('Agent chat is not available in this world');
      }

      // Build URL with path and parameters
      const separator = componentBaseUrl.includes('?') ? '&' : '?';
      const worldId = this.appContext.worldInfo?.worldId;
      const playerId = this.appContext.playerInfo?.playerId;

      const chatUrl = `${componentBaseUrl}agent-chat-widget.html${separator}embedded=true&worldId=${worldId}&playerId=${playerId}`;

      logger.debug('Opening agent chat widget', { chatUrl });

      return this.openModal(
        'agent-chat', // referenceKey
        'Agent Chat',
        chatUrl,
        ModalSizePreset.RIGHT_TOP, // Top-right quadrant (50% x 50%)
        ModalFlags.CLOSEABLE | ModalFlags.MOVEABLE | ModalFlags.NO_BACKGROUND_LOCK | ModalFlags.RESIZEABLE | ModalFlags.BREAK_OUT | ModalFlags.MINIMIZABLE
      );
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ModalService.openAgentChat');
    }
  }

  /**
   * Open panel navigation modal (full preset with all features)
   *
   * @returns Modal reference
   */
  openPanel(): ModalReference {
    try {
      // Exit pointer lock so user can interact with modal
      if (document.pointerLockElement) {
        document.exitPointerLock();
        logger.debug('Exited pointer lock for panel navigation');
      }

      // Get component base URL from NetworkService
      const componentBaseUrl = this.appContext?.services.network?.getComponentBaseUrl();

      if (!componentBaseUrl) {
        logger.warn('No component URL configured for this world');
        throw new Error('Panel navigation is not available in this world');
      }

      // Build URL with path and parameters
      const separator = componentBaseUrl.includes('?') ? '&' : '?';
      const worldId = this.appContext.worldInfo?.worldId;
      const sessionId = this.appContext.sessionId;

      const panelUrl = `${componentBaseUrl}panels.html${separator}embedded=true&worldId=${worldId}&sessionId=${sessionId}`;

      logger.debug('Opening panel navigation modal', { panelUrl });

      return this.openModal(
        'panel-navigation', // referenceKey - reuse same modal for panel
        'Panels',
        panelUrl,
        ModalSizePreset.CENTER_LARGE, // Full preset - large centered modal
        ModalFlags.CLOSEABLE | ModalFlags.MOVEABLE | ModalFlags.NO_BACKGROUND_LOCK | ModalFlags.RESIZEABLE | ModalFlags.BREAK_OUT | ModalFlags.MINIMIZABLE
      );
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ModalService.openPanel');
    }
  }

  /**
   * Open widget selector dialog (for F9 key)
   * Shows a dialog to select between available widgets
   */
  openWidgetSelector(): void {
    try {
      // Exit pointer lock so user can interact with dialog
      if (document.pointerLockElement) {
        document.exitPointerLock();
        logger.debug('Exited pointer lock for widget selector');
      }

      // Create dialog HTML
      const dialog = document.createElement('dialog');
      dialog.className = 'nimbus-widget-selector-dialog';
      dialog.style.cssText = `
        padding: 2rem;
        border: 2px solid #333;
        border-radius: 8px;
        background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
        color: #fff;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
        min-width: 400px;
        z-index: 20000;
        font-family: system-ui, -apple-system, sans-serif;
      `;

      // Create content
      const content = `
        <h2 style="margin: 0 0 1.5rem 0; font-size: 1.5rem; font-weight: 600; text-align: center; color: #fff;">
          Select Widget
        </h2>
        <div style="display: flex; flex-direction: column; gap: 1rem;">
          <button id="widget-edit-config" style="
            padding: 1rem 1.5rem;
            border: 2px solid #4a5568;
            border-radius: 6px;
            background: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);
            color: #fff;
            font-size: 1rem;
            cursor: pointer;
            transition: all 0.2s;
            text-align: left;
          " onmouseover="this.style.borderColor='#60a5fa'; this.style.background='linear-gradient(135deg, #3b4252 0%, #2d3748 100%)'" onmouseout="this.style.borderColor='#4a5568'; this.style.background='linear-gradient(135deg, #2d3748 0%, #1a202c 100%)'">
            <div style="font-weight: 600; margin-bottom: 0.25rem;">📝 Edit Configuration</div>
            <div style="font-size: 0.875rem; opacity: 0.8;">Configure edit actions and layer settings</div>
          </button>

          <button id="widget-agent-chat" style="
            padding: 1rem 1.5rem;
            border: 2px solid #4a5568;
            border-radius: 6px;
            background: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);
            color: #fff;
            font-size: 1rem;
            cursor: pointer;
            transition: all 0.2s;
            text-align: left;
          " onmouseover="this.style.borderColor='#60a5fa'; this.style.background='linear-gradient(135deg, #3b4252 0%, #2d3748 100%)'" onmouseout="this.style.borderColor='#4a5568'; this.style.background='linear-gradient(135deg, #2d3748 0%, #1a202c 100%)'">
            <div style="font-weight: 600; margin-bottom: 0.25rem;">💬 Agent Chat</div>
            <div style="font-size: 0.875rem; opacity: 0.8;">Chat with AI agents (Eliza, builders, etc.)</div>
          </button>

          <button id="widget-cancel" style="
            padding: 0.75rem 1.5rem;
            border: 1px solid #4a5568;
            border-radius: 6px;
            background: transparent;
            color: #9ca3af;
            font-size: 0.875rem;
            cursor: pointer;
            transition: all 0.2s;
            margin-top: 0.5rem;
          " onmouseover="this.style.borderColor='#60a5fa'; this.style.color='#fff'" onmouseout="this.style.borderColor='#4a5568'; this.style.color='#9ca3af'">
            Cancel
          </button>
        </div>
      `;

      dialog.innerHTML = content;

      // Setup event handlers
      const editConfigBtn = dialog.querySelector('#widget-edit-config') as HTMLButtonElement;
      const agentChatBtn = dialog.querySelector('#widget-agent-chat') as HTMLButtonElement;
      const cancelBtn = dialog.querySelector('#widget-cancel') as HTMLButtonElement;

      editConfigBtn?.addEventListener('click', () => {
        dialog.close();
        document.body.removeChild(dialog);
        this.openEditConfiguration();
      });

      agentChatBtn?.addEventListener('click', () => {
        dialog.close();
        document.body.removeChild(dialog);
        this.openAgentChat();
      });

      cancelBtn?.addEventListener('click', () => {
        dialog.close();
        document.body.removeChild(dialog);
      });

      // Close on ESC key
      dialog.addEventListener('cancel', (e) => {
        e.preventDefault();
        dialog.close();
        document.body.removeChild(dialog);
      });

      // Add to DOM and show
      document.body.appendChild(dialog);
      dialog.showModal();

      logger.debug('Widget selector dialog opened');
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.openWidgetSelector');
    }
  }

  /**
   * Open a predefined component modal
   *
   * @param component Component name (e.g., 'block_editor', 'settings', 'inventory')
   * @param attributes Component-specific attributes
   * @returns Modal reference
   */
  openComponent(component: string, attributes: string[]): ModalReference {
    try {
      logger.debug('Opening component modal', { component, attributes });

      // Handle different component types
      switch (component.toLowerCase()) {
        case 'block_editor':
          // Expect attributes: [x, y, z]
          if (attributes.length < 3) {
            throw new Error('block_editor requires 3 attributes: x, y, z');
          }
          const x = parseFloat(attributes[0]);
          const y = parseFloat(attributes[1]);
          const z = parseFloat(attributes[2]);

          if (isNaN(x) || isNaN(y) || isNaN(z)) {
            throw new Error('block_editor coordinates must be valid numbers');
          }

          return this.openBlockEditor(x, y, z);

        case 'edit_config':
          // No attributes required
          return this.openEditConfiguration();

        case 'agent_chat':
        case 'chat':
          // No attributes required
          return this.openAgentChat();

        case 'panel':
        case 'panels':
          // No attributes required
          return this.openPanel();

        // Future components can be added here:
        // case 'settings':
        //   return this.openSettings(attributes);
        // case 'inventory':
        //   return this.openInventory(attributes);
        // case 'map':
        //   return this.openMap(attributes);

        default:
          throw new Error(`Unknown component: ${component}`);
      }
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ModalService.openComponent', {
        component,
        attributes,
      });
    }
  }

  /**
   * Dispose service and close all modals
   */
  dispose(): void {
    try {
      this.closeAll();

      // Remove postMessage listener
      if (this.messageHandler) {
        window.removeEventListener('message', this.messageHandler);
        this.messageHandler = null;
      }

      // Remove any remaining event listeners
      this.modals.forEach((modalRef) => {
        const escHandler = (modalRef as any)._escHandler;
        if (escHandler) {
          document.removeEventListener('keydown', escHandler);
        }
      });

      this.modals.clear();
      this.modalsByReferenceKey.clear();

      logger.debug('ModalService disposed');
    } catch (error) {
      ExceptionHandler.handle(error, 'ModalService.dispose');
    }
  }
}

/**
 * Export SIZE_PRESETS for external use
 */
export { SIZE_PRESETS };
