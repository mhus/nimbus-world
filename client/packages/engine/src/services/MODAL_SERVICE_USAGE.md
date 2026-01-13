# ModalService - IFrame Modal Usage Guide

## Overview

The ModalService provides advanced IFrame-based modal dialogs with features like:
- Reference-based modal reuse
- Multiple size and position presets
- Configurable behavior flags (closeable, borderless, break-out)
- PostMessage-based communication between iframe and parent
- Automatic `embedded=true` parameter handling

## Basic Usage

### Opening a Modal

```typescript
import { ModalService, ModalFlags, ModalSizePreset } from '@nimbus/engine/services';

// Simple modal (center, closeable)
const modal = modalService.openModal(
  null,                           // referenceKey (null = new modal)
  'My Modal',                     // title
  'https://example.com',          // url
  ModalSizePreset.CENTER_MEDIUM,  // preset
  ModalFlags.CLOSEABLE            // flags
);

// Modal with reference key (reuses existing modal)
const modal = modalService.openModal(
  'editor',                                    // referenceKey
  'Editor',                                    // title
  'http://localhost/editor',                   // url
  ModalSizePreset.LEFT,                        // preset
  ModalFlags.CLOSEABLE | ModalFlags.BREAK_OUT  // flags
);
```

### Reference Keys

Reference keys allow you to reuse existing modals instead of creating new ones:

```typescript
// First call: Creates new modal
modalService.openModal('settings', 'Settings', 'http://localhost/settings');

// Second call: Reuses existing modal, updates URL
modalService.openModal('settings', 'Settings', 'http://localhost/settings?tab=advanced');
```

## Size and Position Presets

### Available Presets

**Side Panels (50% width, full height with margins)**
- `ModalSizePreset.LEFT` - Left side of screen
- `ModalSizePreset.RIGHT` - Right side of screen

**Top/Bottom Panels (full width, 50% height with margins)**
- `ModalSizePreset.TOP` - Top of screen
- `ModalSizePreset.BOTTOM` - Bottom of screen

**Centered Modals**
- `ModalSizePreset.CENTER_SMALL` - 600x400px, centered
- `ModalSizePreset.CENTER_MEDIUM` - 800x600px, centered (default)
- `ModalSizePreset.CENTER_LARGE` - 90vw x 90vh, centered

**Quadrants (50% width, 50% height with margins)**
- `ModalSizePreset.LEFT_TOP` - Top-left quadrant
- `ModalSizePreset.LEFT_BOTTOM` - Bottom-left quadrant
- `ModalSizePreset.RIGHT_TOP` - Top-right quadrant
- `ModalSizePreset.RIGHT_BOTTOM` - Bottom-right quadrant

All presets include 20px margins on all sides.

### Example

```typescript
// Open editor on left side
modalService.openModal('editor', 'Editor', '/editor', ModalSizePreset.LEFT);

// Open preview on right side
modalService.openModal('preview', 'Preview', '/preview', ModalSizePreset.RIGHT);

// Open settings centered
modalService.openModal('settings', 'Settings', '/settings', ModalSizePreset.CENTER_MEDIUM);
```

## Modal Flags

Flags control modal behavior using bitflags:

### Available Flags

- `ModalFlags.CLOSEABLE` - User can close modal (close button, ESC, backdrop)
- `ModalFlags.NO_BORDERS` - Minimal borders (no title bar, no buttons, thin border)
- `ModalFlags.BREAK_OUT` - Show break-out button to open in new window

### Examples

```typescript
// Closeable modal with break-out
modalService.openModal(
  'editor',
  'Editor',
  '/editor',
  ModalSizePreset.LEFT,
  ModalFlags.CLOSEABLE | ModalFlags.BREAK_OUT
);

// Non-closeable, borderless modal (embedded content)
modalService.openModal(
  'preview',
  'Preview',
  '/preview',
  ModalSizePreset.RIGHT,
  ModalFlags.NO_BORDERS
);

// All flags enabled
modalService.openModal(
  'editor',
  'Editor',
  '/editor',
  ModalSizePreset.CENTER_LARGE,
  ModalFlags.CLOSEABLE | ModalFlags.NO_BORDERS | ModalFlags.BREAK_OUT
);
```

## IFrame Communication

The ModalService automatically:
1. Adds `embedded=true` to iframe URLs
2. Listens for postMessage events from iframes
3. Provides IFrameHelper for iframe-side communication

### Using IFrameHelper in IFrame Content

```typescript
import { IFrameHelper } from '@nimbus/engine/services';

// Check if running embedded
if (IFrameHelper.isEmbedded()) {
  console.log('Running in modal iframe');
}

// Notify parent that iframe is ready
IFrameHelper.notifyReady();

// Request to close modal
document.getElementById('closeBtn')?.addEventListener('click', () => {
  IFrameHelper.requestClose('user_done');
});

// Request position change
import { ModalSizePreset } from '@nimbus/engine/types';

document.getElementById('moveLeft')?.addEventListener('click', () => {
  IFrameHelper.requestPositionChange(ModalSizePreset.LEFT);
});

// Send notification to parent
IFrameHelper.sendNotification('info', 'MyApp', 'Data saved successfully');
```

### Message Types

**From IFrame to Parent:**
- `IFRAME_READY` - IFrame has loaded and is ready
- `REQUEST_CLOSE` - Request to close this modal (with optional reason)
- `REQUEST_POSITION_CHANGE` - Request to change modal position (with preset name)
- `NOTIFICATION` - Send notification (forwarded to NotificationService)

## Break-out Feature

When `ModalFlags.BREAK_OUT` is enabled:
1. A break-out button (↗) appears in the modal header
2. Clicking it opens the URL in a new window with `embedded=false`
3. The original modal is automatically closed

```typescript
// Enable break-out
modalService.openModal(
  'editor',
  'Editor',
  '/editor',
  ModalSizePreset.CENTER_LARGE,
  ModalFlags.CLOSEABLE | ModalFlags.BREAK_OUT
);

// In the iframe, the URL will be: /editor?embedded=true
// When broken out, new window will have: /editor?embedded=false
```

## Closing Modals

### From Parent

```typescript
// Get modal reference
const modal = modalService.openModal(...);

// Close with reason
modal.close('user_cancelled');

// Close all modals
modalService.closeAll();
```

### From IFrame

```typescript
import { IFrameHelper } from '@nimbus/engine/services';

// Request close
IFrameHelper.requestClose('done');
```

## Changing Position

### From Parent

```typescript
import { ModalSizePreset } from '@nimbus/engine/types';

const modal = modalService.openModal(...);

// Change position
modal.changePosition(ModalSizePreset.RIGHT);
```

### From IFrame

```typescript
import { IFrameHelper } from '@nimbus/engine/services';
import { ModalSizePreset } from '@nimbus/engine/types';

// Request position change
IFrameHelper.requestPositionChange(ModalSizePreset.RIGHT_BOTTOM);
```

## Complete Example

### Parent Application

```typescript
import { ModalService, ModalFlags, ModalSizePreset } from '@nimbus/engine/services';

const modalService = new ModalService(appContext);

// Open editor on left side
const editor = modalService.openModal(
  'editor',
  'Block Editor',
  'http://localhost:8080/editor',
  ModalSizePreset.LEFT,
  ModalFlags.CLOSEABLE | ModalFlags.BREAK_OUT
);

// Later, update editor with different block
modalService.openModal(
  'editor',
  'Block Editor',
  'http://localhost:8080/editor?blockId=123',
  ModalSizePreset.LEFT,
  ModalFlags.CLOSEABLE | ModalFlags.BREAK_OUT
);
```

### IFrame Content

```typescript
import { IFrameHelper } from '@nimbus/engine/services';

// Initialize
if (IFrameHelper.isEmbedded()) {
  console.log('Running in embedded mode');
  IFrameHelper.notifyReady();
}

// Close button
document.getElementById('closeBtn')?.addEventListener('click', () => {
  if (IFrameHelper.isInIFrame()) {
    IFrameHelper.requestClose('user_closed');
  } else {
    window.close();
  }
});

// Save button
document.getElementById('saveBtn')?.addEventListener('click', async () => {
  await saveData();
  IFrameHelper.sendNotification('success', 'Editor', 'Changes saved');
  IFrameHelper.requestClose('saved');
});

// Move to right side
import { ModalSizePreset } from '@nimbus/engine/types';

document.getElementById('moveRight')?.addEventListener('click', () => {
  IFrameHelper.requestPositionChange(ModalSizePreset.RIGHT);
});
```

## Security Notes

1. **Origin Checking**: The ModalService only accepts messages from iframes it created
2. **IFrame Sandbox**: Iframes have sandbox attribute: `allow-same-origin allow-scripts allow-forms allow-popups`
3. **Target Origin**: IFrameHelper attempts to determine parent origin from referrer, falls back to '*' if unavailable

## TypeScript Types

All types are exported from `@nimbus/engine/types`:

```typescript
import type {
  ModalReference,
  ModalOptions,
  ModalFlags,
  ModalSizePreset,
  IFrameMessageType,
  IFrameMessageFromChild,
} from '@nimbus/engine/types';
```

## Styling

The ModalService creates these CSS classes:

- `.nimbus-modal-backdrop` - Modal backdrop (overlay)
- `.nimbus-modal-centered` - Centered modal backdrop
- `.nimbus-modal-container` - Modal container
- `.nimbus-modal-no-borders` - Borderless modal container
- `.nimbus-modal-header` - Modal header
- `.nimbus-modal-title` - Modal title
- `.nimbus-modal-buttons` - Header buttons container
- `.nimbus-modal-close` - Close button
- `.nimbus-modal-breakout` - Break-out button
- `.nimbus-modal-iframe` - IFrame element

Add your own CSS to customize appearance.
