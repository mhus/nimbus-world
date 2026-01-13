# Patch for BlockInstanceEditor.vue

## Changes needed to add "Save as BlockType" functionality

### 1. Add import at top of script section (after line 487):
```typescript
import { saveBlockAsBlockType, getBlockTypeEditorUrl as getBlockTypeEditorUrlHelper } from './BlockInstanceEditor_SaveAsBlockType';
```

### 2. Add state variables (after line 565, near other dialog state):
```typescript
// Save as BlockType dialog state
const showSaveAsBlockTypeDialog = ref(false);
const saveAsBlockTypeDialog = ref<HTMLDialogElement | null>(null);
const newBlockTypeId = ref('');
const savingAsBlockType = ref(false);
const saveAsBlockTypeError = ref<string | null>(null);
```

### 3. Add button in template (after line 412, after the "Source" button):
```vue
              <button
                class="btn btn-outline btn-sm btn-info"
                @click="openSaveAsBlockTypeDialog"
                :disabled="!isValid || saving"
                title="Save this custom block as a new BlockType template"
              >
                <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
                </svg>
                Save as BlockType
              </button>
```

### 4. Add dialog in template (before closing </template> tag, after line 471):
```vue
    <!-- Save as BlockType Dialog -->
    <dialog ref="saveAsBlockTypeDialog" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Save as BlockType</h3>
        <p class="py-4 text-sm text-base-content/70">
          Convert this custom block instance into a reusable BlockType template.
        </p>

        <div class="form-control">
          <label class="label">
            <span class="label-text font-semibold">BlockType ID</span>
            <span class="label-text-alt text-error" v-if="!newBlockTypeId">Required</span>
          </label>
          <input
            v-model="newBlockTypeId"
            type="text"
            class="input input-bordered"
            placeholder="e.g., custom:my-block or w/123"
            @keyup.enter="handleSaveAsBlockType"
          />
          <label class="label">
            <span class="label-text-alt">Use format: group:name or group/name (e.g., custom:stone, w/123)</span>
          </label>
        </div>

        <div v-if="saveAsBlockTypeError" class="alert alert-error mt-4">
          <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>{{ saveAsBlockTypeError }}</span>
        </div>

        <div class="modal-action">
          <button class="btn" @click="closeSaveAsBlockTypeDialog" :disabled="savingAsBlockType">Cancel</button>
          <button
            class="btn btn-primary"
            @click="handleSaveAsBlockType"
            :disabled="!newBlockTypeId || savingAsBlockType"
          >
            {{ savingAsBlockType ? 'Saving...' : 'Save as BlockType' }}
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button @click="closeSaveAsBlockTypeDialog">close</button>
      </form>
    </dialog>
```

### 5. Add functions at end of script section (before closing </script> tag, after line 1253):
```typescript
// Save as BlockType functionality
function openSaveAsBlockTypeDialog() {
  newBlockTypeId.value = '';
  saveAsBlockTypeError.value = null;
  saveAsBlockTypeDialog.value?.showModal();
}

function closeSaveAsBlockTypeDialog() {
  saveAsBlockTypeDialog.value?.close();
  newBlockTypeId.value = '';
  saveAsBlockTypeError.value = null;
}

async function handleSaveAsBlockType() {
  if (!newBlockTypeId.value || savingAsBlockType.value) {
    return;
  }

  savingAsBlockType.value = true;
  saveAsBlockTypeError.value = null;

  try {
    const result = await saveBlockAsBlockType(
      worldId,
      newBlockTypeId.value,
      blockData.value
    );

    if (result.success) {
      // Close dialog
      closeSaveAsBlockTypeDialog();

      // Show success notification with link
      const params = new URLSearchParams(window.location.search);
      const sessionId = params.get('sessionId');
      const editorUrl = getBlockTypeEditorUrlHelper(newBlockTypeId.value, worldId, sessionId || undefined);

      if (isEmbedded()) {
        sendNotification(
          '0',
          'BlockType Created',
          `BlockType "${newBlockTypeId.value}" created successfully. <a href="${editorUrl}" target="_blank">Open in editor</a>`
        );
      } else {
        alert(`BlockType "${newBlockTypeId.value}" created successfully!\n\nOpen editor: ${editorUrl}`);
      }

      // Optionally open in new tab
      window.open(editorUrl, '_blank');
    } else {
      saveAsBlockTypeError.value = result.error || 'Failed to save BlockType';
    }
  } catch (err) {
    saveAsBlockTypeError.value = err instanceof Error ? err.message : 'Unknown error occurred';
  } finally {
    savingAsBlockType.value = false;
  }
}
```

## Manual steps to apply:
1. Open BlockInstanceEditor.vue
2. Add the import statement from section 1
3. Add the state variables from section 2
4. Add the button from section 3 after the "Source" button
5. Add the dialog from section 4 before the closing </template> tag
6. Add the functions from section 5 before the closing </script> tag
