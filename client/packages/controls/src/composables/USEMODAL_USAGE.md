# useModal Composable - Usage Guide

## Overview

The `useModal` composable provides Vue 3 applications (nimbus_editors) with the ability to communicate with the parent Nimbus Client when running in an IFrame modal.

## Features

- **isEmbedded()** - Check if running in embedded mode
- **closeModal(reason)** - Request to close the modal
- **changePosition(preset)** - Request to change modal position
- **sendNotification(type, from, message)** - Send notification to parent
- **notifyReady()** - Notify parent that iframe is ready

## Basic Usage

### In Vue Component

```vue
<template>
  <div>
    <div v-if="isEmbedded()" class="embedded-indicator">
      Running in modal
    </div>

    <button @click="handleSave">Save</button>
    <button @click="handleClose">Close</button>
    <button @click="moveToRight">Move to Right</button>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useModal, ModalSizePreset } from '@/composables/useModal';

const {
  isEmbedded,
  closeModal,
  changePosition,
  sendNotification,
  notifyReady
} = useModal();

// Notify parent when component is ready
onMounted(() => {
  if (isEmbedded()) {
    notifyReady();
  }
});

function handleSave() {
  // Save logic...

  // Send success notification
  sendNotification('0', 'Block Editor', 'Block saved successfully');

  // Close modal after save
  closeModal('saved');
}

function handleClose() {
  closeModal('user_cancelled');
}

function moveToRight() {
  changePosition(ModalSizePreset.RIGHT);
}
</script>
```

### In Composable

```typescript
import { useModal, ModalSizePreset } from '@/composables/useModal';

export function useBlockEditor() {
  const { isEmbedded, closeModal, sendNotification } = useModal();

  async function saveBlock(blockData: any) {
    try {
      // Save block...

      if (isEmbedded()) {
        sendNotification('0', 'Block Editor', 'Block saved');
        closeModal('saved');
      }
    } catch (error) {
      if (isEmbedded()) {
        sendNotification('1', 'Block Editor', 'Failed to save block');
      }
    }
  }

  return {
    saveBlock
  };
}
```

## API Reference

### isEmbedded()

Check if the application is running in embedded mode (iframe with `?embedded=true`).

```typescript
if (isEmbedded()) {
  console.log('Running in modal');
}
```

**Returns:** `boolean`

### isInIFrame()

Check if the application is running inside any iframe (regardless of embedded parameter).

```typescript
if (isInIFrame()) {
  console.log('Running in iframe');
}
```

**Returns:** `boolean`

### closeModal(reason?)

Request the parent to close this modal.

```typescript
// Close with reason
closeModal('user_done');

// Close without reason
closeModal();
```

**Parameters:**
- `reason?: string` - Optional reason for closing

### changePosition(preset)

Request the parent to change the modal position.

```typescript
import { ModalSizePreset } from '@/composables/useModal';

// Move to left side
changePosition(ModalSizePreset.LEFT);

// Move to right side
changePosition(ModalSizePreset.RIGHT);

// Center large
changePosition(ModalSizePreset.CENTER_LARGE);
```

**Parameters:**
- `preset: ModalSizePreset` - Position preset enum

**Available Presets:**
- `ModalSizePreset.LEFT` - Left side panel
- `ModalSizePreset.RIGHT` - Right side panel
- `ModalSizePreset.TOP` - Top panel
- `ModalSizePreset.BOTTOM` - Bottom panel
- `ModalSizePreset.CENTER_SMALL` - Center small
- `ModalSizePreset.CENTER_MEDIUM` - Center medium
- `ModalSizePreset.CENTER_LARGE` - Center large
- `ModalSizePreset.LEFT_TOP` - Top-left quadrant
- `ModalSizePreset.LEFT_BOTTOM` - Bottom-left quadrant
- `ModalSizePreset.RIGHT_TOP` - Top-right quadrant
- `ModalSizePreset.RIGHT_BOTTOM` - Bottom-right quadrant

### sendNotification(notificationType, from, message)

Send a notification to the parent, which forwards it to the NotificationService.

```typescript
// Info notification (type 0)
sendNotification('0', 'Block Editor', 'Block saved successfully');

// Error notification (type 1)
sendNotification('1', 'Block Editor', 'Failed to save block');

// Command result (type 3)
sendNotification('3', 'Block Editor', 'Command executed');
```

**Parameters:**
- `notificationType: string` - Notification type ('0' = info, '1' = error, '3' = command result)
- `from: string` - Source of notification
- `message: string` - Notification message

### notifyReady()

Notify the parent that the iframe is ready and loaded.

```typescript
onMounted(() => {
  if (isEmbedded()) {
    notifyReady();
  }
});
```

## Common Patterns

### Auto-close after save

```vue
<script setup lang="ts">
import { useModal } from '@/composables/useModal';

const { isEmbedded, closeModal, sendNotification } = useModal();

async function handleSave() {
  try {
    await saveData();

    if (isEmbedded()) {
      sendNotification('0', 'Editor', 'Saved successfully');
      // Auto-close after 1 second
      setTimeout(() => closeModal('saved'), 1000);
    }
  } catch (error) {
    if (isEmbedded()) {
      sendNotification('1', 'Editor', 'Failed to save');
    }
  }
}
</script>
```

### Conditional UI for embedded mode

```vue
<template>
  <div>
    <!-- Show close button only when embedded -->
    <button v-if="isEmbedded()" @click="closeModal('user_closed')">
      Close
    </button>

    <!-- Show different actions when embedded -->
    <div v-if="isEmbedded()" class="modal-actions">
      <button @click="changePosition(ModalSizePreset.LEFT)">← Left</button>
      <button @click="changePosition(ModalSizePreset.RIGHT)">Right →</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useModal, ModalSizePreset } from '@/composables/useModal';

const { isEmbedded, closeModal, changePosition } = useModal();
</script>
```

### Notify on errors

```vue
<script setup lang="ts">
import { useModal } from '@/composables/useModal';

const { isEmbedded, sendNotification } = useModal();

async function loadBlock(blockId: string) {
  try {
    const block = await fetchBlock(blockId);
    return block;
  } catch (error) {
    if (isEmbedded()) {
      sendNotification('1', 'Block Editor', `Failed to load block: ${error.message}`);
    }
    throw error;
  }
}
</script>
```

## Example: Complete Block Editor

```vue
<template>
  <div class="block-editor">
    <!-- Embedded indicator -->
    <div v-if="isEmbedded()" class="embedded-bar">
      <span>Editing in modal</span>
      <div class="position-controls">
        <button @click="changePosition(ModalSizePreset.LEFT)">← Left</button>
        <button @click="changePosition(ModalSizePreset.RIGHT)">Right →</button>
      </div>
    </div>

    <!-- Editor content -->
    <div class="editor-content">
      <input v-model="blockName" placeholder="Block name" />
      <!-- ... other fields ... -->
    </div>

    <!-- Actions -->
    <div class="actions">
      <button @click="handleSave" :disabled="saving">
        {{ saving ? 'Saving...' : 'Save' }}
      </button>
      <button v-if="isEmbedded()" @click="handleClose">
        Cancel
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useModal, ModalSizePreset } from '@/composables/useModal';

const {
  isEmbedded,
  closeModal,
  changePosition,
  sendNotification,
  notifyReady
} = useModal();

const blockName = ref('');
const saving = ref(false);

onMounted(() => {
  if (isEmbedded()) {
    notifyReady();
  }
});

async function handleSave() {
  saving.value = true;

  try {
    // Save logic...
    await saveBlock({ name: blockName.value });

    if (isEmbedded()) {
      sendNotification('0', 'Block Editor', 'Block saved successfully');
      closeModal('saved');
    } else {
      alert('Saved successfully!');
    }
  } catch (error) {
    if (isEmbedded()) {
      sendNotification('1', 'Block Editor', `Failed to save: ${error.message}`);
    } else {
      alert(`Error: ${error.message}`);
    }
  } finally {
    saving.value = false;
  }
}

function handleClose() {
  if (confirm('Discard changes?')) {
    closeModal('cancelled');
  }
}
</script>

<style scoped>
.embedded-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background-color: #f0f0f0;
  border-bottom: 1px solid #ddd;
}

.position-controls {
  display: flex;
  gap: 0.5rem;
}
</style>
```

## Testing

### Check if running embedded

Navigate to: `http://localhost:5173?embedded=true`

```typescript
console.log('Is embedded:', isEmbedded()); // true
console.log('Is in iframe:', isInIFrame()); // false (unless actually in iframe)
```

### Test in actual modal

Open from Nimbus Client:
```typescript
modalService.openModal(
  'block-editor',
  'Block Editor',
  'http://localhost:5173/block-editor',
  ModalSizePreset.LEFT,
  ModalFlags.CLOSEABLE
);
```

## Security Notes

1. **Origin Checking**: The composable attempts to determine the parent origin from `document.referrer`
2. **Fallback**: If origin cannot be determined, falls back to wildcard `*`
3. **Same-origin**: For better security, ensure nimbus_editors and client are on the same origin

## TypeScript

The composable is fully typed. Import types from `@nimbus/shared`:

```typescript
import type { ModalSizePreset } from '@nimbus/shared';
import { useModal } from '@/composables/useModal';

const { closeModal } = useModal();

// TypeScript will enforce correct preset values
closeModal('saved'); // ✓
changePosition(ModalSizePreset.LEFT); // ✓
```
