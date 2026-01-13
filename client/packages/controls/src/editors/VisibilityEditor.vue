<template>
  <div class="space-y-4 pt-2">
    <!-- Shape Selection -->
    <div class="form-control">
      <label class="label">
        <span class="label-text font-semibold">Shape</span>
      </label>
      <select v-model.number="localValue.shape" class="select select-bordered select-sm">
        <option v-for="(name, value) in shapeOptions" :key="value" :value="parseInt(value)">
          {{ name }} ({{ value }})
        </option>
      </select>
    </div>

    <!-- Model Path (for MODEL shape) -->
    <div v-if="localValue.shape === 4" class="form-control">
      <label class="label">
        <span class="label-text">Model Path</span>
      </label>
      <div class="flex items-center gap-2">
        <input
          v-model="localValue.path"
          type="text"
          class="input input-bordered input-sm flex-1"
          placeholder="models/skull.babylon"
        />
        <!-- Asset Picker Button -->
        <button
          class="btn btn-ghost btn-sm btn-square"
          @click="openModelAssetPicker"
          title="Select model from assets"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Scaling & Rotation -->
    <div class="grid grid-cols-5 gap-2">
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Scale X</span>
        </label>
        <input
          v-model.number="localValue.scalingX"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="1.0"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Scale Y</span>
        </label>
        <input
          v-model.number="localValue.scalingY"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="1.0"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Scale Z</span>
        </label>
        <input
          v-model.number="localValue.scalingZ"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="1.0"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Rotation X</span>
        </label>
        <input
          v-model.number="localValue.rotation.x"
          type="number"
          class="input input-bordered input-sm"
          placeholder="0"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Rotation Y</span>
        </label>
        <input
          v-model.number="localValue.rotation.y"
          type="number"
          class="input input-bordered input-sm"
          placeholder="0"
        />
      </div>
    </div>

    <!-- Global Effect & Effect Parameters -->
    <div class="form-control">
      <label class="label">
        <span class="label-text font-semibold">Global Effect</span>
        <span class="label-text-alt text-xs text-base-content/50">Applies to all textures by default</span>
      </label>
      <div class="grid grid-cols-2 gap-2">
        <div>
          <label class="label py-0">
            <span class="label-text text-xs">Effect Type</span>
          </label>
          <select
            v-model.number="localValue.effect"
            class="select select-bordered select-sm w-full"
          >
            <option :value="0">NONE</option>
            <option :value="2">WIND</option>
          </select>
        </div>
        <div>
          <label class="label py-0">
            <span class="label-text text-xs">Effect Parameters</span>
          </label>
          <input
            v-model="localValue.effectParameters"
            type="text"
            class="input input-bordered input-sm w-full"
            placeholder="e.g. 4,100 (frameCount,delayMs)"
          />
        </div>
      </div>
    </div>

    <!-- Offsets -->
    <CollapsibleSection
      title="Geometry Offsets"
      :model-value="hasOffsets"
      :default-open="false"
      @update:model-value="toggleOffsets"
    >
      <OffsetsEditor
        v-model="localValue.offsets"
        :shape="localValue.shape"
      />
    </CollapsibleSection>

    <!-- Face Visibility -->
    <CollapsibleSection
      title="Face Visibility"
      :model-value="hasFaceVisibility"
      :default-open="false"
      @update:model-value="toggleFaceVisibility"
    >
      <div class="space-y-3 pt-2">
        <div class="space-y-2">
          <!-- Face Checkboxen (6 Faces) -->
          <div class="grid grid-cols-2 gap-2">
            <label class="label cursor-pointer justify-start gap-2">
              <input
                type="checkbox"
                class="checkbox checkbox-sm"
                :checked="isFaceVisible(1)"
                @change="toggleFace(1)"
              />
              <span class="label-text">Top</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input
                type="checkbox"
                class="checkbox checkbox-sm"
                :checked="isFaceVisible(2)"
                @change="toggleFace(2)"
              />
              <span class="label-text">Bottom</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input
                type="checkbox"
                class="checkbox checkbox-sm"
                :checked="isFaceVisible(4)"
                @change="toggleFace(4)"
              />
              <span class="label-text">Left</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input
                type="checkbox"
                class="checkbox checkbox-sm"
                :checked="isFaceVisible(8)"
                @change="toggleFace(8)"
              />
              <span class="label-text">Right</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input
                type="checkbox"
                class="checkbox checkbox-sm"
                :checked="isFaceVisible(16)"
                @change="toggleFace(16)"
              />
              <span class="label-text">Front</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input
                type="checkbox"
                class="checkbox checkbox-sm"
                :checked="isFaceVisible(32)"
                @change="toggleFace(32)"
              />
              <span class="label-text">Back</span>
            </label>
          </div>

          <!-- Fixed/Auto mode -->
          <div class="divider divider-start text-xs">Mode</div>
          <label class="label cursor-pointer justify-start gap-2">
            <input
              type="checkbox"
              class="checkbox checkbox-sm"
              :checked="isFixedMode"
              @change="toggleFixedMode"
            />
            <span class="label-text">Fixed Mode (disable auto-calculation)</span>
          </label>

          <!-- Bitfield value display -->
          <div class="text-xs text-base-content/50">
            Bitfield value: {{ localValue.faceVisibility || 0 }}
          </div>
        </div>
      </div>
    </CollapsibleSection>

    <!-- Textures -->
    <CollapsibleSection
      title="Textures"
      :model-value="hasTextures"
      :default-open="true"
      @update:model-value="toggleTextures"
    >
      <div class="space-y-3 pt-2">
      <div v-for="key in existingTextureKeys" :key="key" class="space-y-2">
        <!-- Texture Row -->
        <div class="flex items-center gap-2">
          <span class="text-sm w-24 text-base-content/70">{{ textureKeyOptions[key] }}:</span>
          <input
            :value="getTexturePathValue(key)"
            @input="setTexturePath(key, ($event.target as HTMLInputElement).value)"
            type="text"
            class="input input-bordered input-sm flex-1"
            :placeholder="`textures/block/my_${textureKeyOptions[key]}.png`"
          />
          <!-- Asset Picker Button -->
          <button
            class="btn btn-ghost btn-sm btn-square"
            @click="openAssetPicker(key)"
            title="Select from assets"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
            </svg>
          </button>
          <!-- Expand/Collapse Button -->
          <button
            v-if="getTexturePathValue(key)"
            class="btn btn-ghost btn-sm btn-square"
            @click="toggleTextureExpansion(key)"
            :title="isTextureExpanded(key) ? 'Hide advanced settings' : 'Show advanced settings'"
          >
            <svg
              class="w-4 h-4 transition-transform"
              :class="{ 'rotate-180': isTextureExpanded(key) }"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          <!-- Remove Button -->
          <button
            v-if="getTexturePathValue(key)"
            class="btn btn-ghost btn-sm btn-square"
            @click="removeTexture(key)"
            title="Remove texture"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Expanded Settings -->
        <div
          v-if="isTextureExpanded(key)"
          class="ml-28 pl-4 border-l-2 border-base-300 space-y-3"
        >
          <!-- Texture Preview Canvas -->
          <div>
            <div class="text-xs font-semibold text-base-content/70 mb-2">Preview</div>
            <div class="relative bg-base-200 rounded-lg p-2 flex items-center justify-center">
              <canvas
                :ref="(el) => setCanvasRef(key, el as HTMLCanvasElement | null)"
                width="256"
                height="256"
                class="border border-base-300"
                style="image-rendering: pixelated;"
              />
              <!-- Loading Indicator -->
              <div v-if="isTextureLoading(key)" class="absolute inset-0 flex items-center justify-center bg-base-200/80 rounded-lg">
                <span class="loading loading-spinner loading-md"></span>
              </div>
            </div>
          </div>

          <!-- Atlas Extraction -->
          <div>
            <div class="grid grid-cols-4 gap-2">
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">X (px)</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.x')"
                  @input="setTextureDefValue(key, 'uvMapping.x', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  class="input input-bordered input-xs"
                  placeholder="0"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">Y (px)</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.y')"
                  @input="setTextureDefValue(key, 'uvMapping.y', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  class="input input-bordered input-xs"
                  placeholder="0"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">W (px)</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.w')"
                  @input="setTextureDefValue(key, 'uvMapping.w', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  class="input input-bordered input-xs"
                  placeholder="16"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">H (px)</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.h')"
                  @input="setTextureDefValue(key, 'uvMapping.h', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  class="input input-bordered input-xs"
                  placeholder="16"
                />
              </div>
            </div>
          </div>

          <!-- UV Transformation - Scale & Offset (all 4 fields in one row) -->
          <div>
            <div class="grid grid-cols-4 gap-2">
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">uScale</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.uScale')"
                  @input="setTextureDefValue(key, 'uvMapping.uScale', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  step="0.1"
                  class="input input-bordered input-xs"
                  placeholder="1.0"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">vScale</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.vScale')"
                  @input="setTextureDefValue(key, 'uvMapping.vScale', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  step="0.1"
                  class="input input-bordered input-xs"
                  placeholder="1.0"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">uOffset</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.uOffset')"
                  @input="setTextureDefValue(key, 'uvMapping.uOffset', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  step="0.1"
                  class="input input-bordered input-xs"
                  placeholder="0.0"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">vOffset</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.vOffset')"
                  @input="setTextureDefValue(key, 'uvMapping.vOffset', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  step="0.1"
                  class="input input-bordered input-xs"
                  placeholder="0.0"
                />
              </div>
            </div>
          </div>

          <!-- UV Transformation - Wrap Mode & Rotation Center (all 4 fields in one row) -->
          <div>
            <div class="grid grid-cols-4 gap-2">
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">wrapU</span>
                </label>
                <select
                  :value="getTextureDefValue(key, 'uvMapping.wrapU') ?? ''"
                  @change="handleWrapChange(key, 'wrapU', ($event.target as HTMLSelectElement).value)"
                  class="select select-bordered select-xs"
                >
                  <option value="">undefined (CLAMP)</option>
                  <option :value="0">CLAMP</option>
                  <option :value="1">REPEAT</option>
                  <option :value="2">MIRROR</option>
                </select>
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">wrapV</span>
                </label>
                <select
                  :value="getTextureDefValue(key, 'uvMapping.wrapV') ?? ''"
                  @change="handleWrapChange(key, 'wrapV', ($event.target as HTMLSelectElement).value)"
                  class="select select-bordered select-xs"
                >
                  <option value="">undefined (CLAMP)</option>
                  <option :value="0">CLAMP</option>
                  <option :value="1">REPEAT</option>
                  <option :value="2">MIRROR</option>
                </select>
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">uRotCenter</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.uRotationCenter')"
                  @input="setTextureDefValue(key, 'uvMapping.uRotationCenter', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  step="0.1"
                  class="input input-bordered input-xs"
                  placeholder="0.5"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">vRotCenter</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'uvMapping.vRotationCenter')"
                  @input="setTextureDefValue(key, 'uvMapping.vRotationCenter', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  step="0.1"
                  class="input input-bordered input-xs"
                  placeholder="0.5"
                />
              </div>
            </div>
          </div>

          <!-- Texture Rotation Angles -->
          <div>
            <div class="grid grid-cols-3 gap-2">
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">wAng (W-Axis)</span>
                </label>
                <input
                  :value="radiansToDegreesDisplay(getTextureDefValue(key, 'uvMapping.wAng'))"
                  @input="setTextureDefValue(key, 'uvMapping.wAng', degreesToRadians(parseFloat(($event.target as HTMLInputElement).value)))"
                  type="number"
                  step="1"
                  class="input input-bordered input-xs"
                  placeholder="0"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">uAng (U-Axis)</span>
                </label>
                <input
                  :value="radiansToDegreesDisplay(getTextureDefValue(key, 'uvMapping.uAng'))"
                  @input="setTextureDefValue(key, 'uvMapping.uAng', degreesToRadians(parseFloat(($event.target as HTMLInputElement).value)))"
                  type="number"
                  step="1"
                  class="input input-bordered input-xs"
                  placeholder="0"
                />
              </div>
              <div class="form-control">
                <label class="label py-0">
                  <span class="label-text text-xs">vAng (V-Axis)</span>
                </label>
                <input
                  :value="radiansToDegreesDisplay(getTextureDefValue(key, 'uvMapping.vAng'))"
                  @input="setTextureDefValue(key, 'uvMapping.vAng', degreesToRadians(parseFloat(($event.target as HTMLInputElement).value)))"
                  type="number"
                  step="1"
                  class="input input-bordered input-xs"
                  placeholder="0"
                />
              </div>
            </div>
          </div>

          <!-- Sampling, Transparency, Opacity & Color (all 4 fields in one row) -->
          <div>
            <div class="grid grid-cols-4 gap-2">
              <div>
                <label class="label py-0">
                  <span class="label-text text-xs">Sampling</span>
                </label>
                <select
                  :value="getTextureDefValue(key, 'samplingMode') ?? 0"
                  @change="setTextureDefValue(key, 'samplingMode', parseInt(($event.target as HTMLSelectElement).value))"
                  class="select select-bordered select-xs w-full"
                >
                  <option :value="0">NEAREST</option>
                  <option :value="1">LINEAR</option>
                  <option :value="2">MIPMAP</option>
                </select>
              </div>
              <div>
                <label class="label py-0">
                  <span class="label-text text-xs">Transparency</span>
                </label>
                <select
                  :value="getTextureDefValue(key, 'transparencyMode') ?? 0"
                  @change="setTextureDefValue(key, 'transparencyMode', parseInt(($event.target as HTMLSelectElement).value))"
                  class="select select-bordered select-xs w-full"
                >
                  <option :value="0">NONE</option>
                  <option :value="1">ALPHA_TEST</option>
                  <option :value="2">ALPHA_BLEND</option>
                  <option :value="3">ALPHA_TEST_FROM_RGB</option>
                  <option :value="4">ALPHA_BLEND_FROM_RGB</option>
                  <option :value="5">ALPHA_TESTANDBLEND</option>
                  <option :value="6">ALPHA_TESTANDBLEND_FROM_RGB</option>
                </select>
              </div>
              <div>
                <label class="label py-0">
                  <span class="label-text text-xs">Opacity</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'opacity')"
                  @input="setTextureDefValue(key, 'opacity', parseFloat(($event.target as HTMLInputElement).value))"
                  type="number"
                  step="0.1"
                  min="0"
                  max="1"
                  class="input input-bordered input-xs w-full"
                  placeholder="1.0"
                />
              </div>
              <div>
                <label class="label py-0">
                  <span class="label-text text-xs">Tint Color</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'color')"
                  @input="setTextureDefValue(key, 'color', ($event.target as HTMLInputElement).value)"
                  type="text"
                  class="input input-bordered input-xs w-full"
                  placeholder="#ffffff"
                />
              </div>
            </div>
          </div>

          <!-- Back Face Culling, Effect & Shader Parameters (Shader Parameters with double width) -->
          <div>
            <div class="grid grid-cols-4 gap-2">
              <div>
                <label class="label py-0">
                  <span class="label-text text-xs">Back Face Culling</span>
                </label>
                <div class="form-control">
                  <label class="label cursor-pointer justify-start gap-2 py-1">
                    <input
                      type="checkbox"
                      :checked="getTextureDefValue(key, 'backFaceCulling') ?? true"
                      @change="setTextureDefValue(key, 'backFaceCulling', ($event.target as HTMLInputElement).checked)"
                      class="checkbox checkbox-sm"
                    />
                    <span class="label-text text-xs">Enable</span>
                  </label>
                </div>
              </div>
              <div>
                <label class="label py-0">
                  <span class="label-text text-xs">Effect</span>
                </label>
                <select
                  :value="getTextureDefValue(key, 'effect') ?? 0"
                  @change="setTextureDefValue(key, 'effect', parseInt(($event.target as HTMLSelectElement).value))"
                  class="select select-bordered select-xs w-full"
                >
                  <option :value="0">NONE</option>
                  <option :value="2">WIND</option>
                </select>
              </div>
              <div>
                <label class="label py-0">
                  <span class="label-text text-xs">Effect Parameters</span>
                </label>
                <input
                  :value="getTextureDefValue(key, 'effectParameters')"
                  @input="setTextureDefValue(key, 'effectParameters', ($event.target as HTMLInputElement).value)"
                  type="text"
                  class="input input-bordered input-xs w-full"
                  placeholder="e.g. 4,100 (frameCount,delayMs)"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Add Texture Button -->
      <button
        class="btn btn-outline btn-sm w-full mt-3"
        @click="showAddTextureDialog = true"
        :disabled="availableTextureKeys.length === 0"
      >
        <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        Add Texture
        <span v-if="availableTextureKeys.length === 0" class="text-xs ml-2">(All used)</span>
      </button>
      </div>
    </CollapsibleSection>

  <!-- Add Texture Dialog -->
  <div v-if="showAddTextureDialog" class="fixed inset-0 z-50 flex items-center justify-center">
    <div class="fixed inset-0 bg-black bg-opacity-25" @click="showAddTextureDialog = false"></div>
    <div class="relative bg-base-100 rounded-lg shadow-xl p-6 max-w-md w-full mx-4">
      <h3 class="text-lg font-bold mb-4">Select Texture Type</h3>
      <div class="space-y-2 max-h-96 overflow-y-auto">
        <button
          v-for="key in availableTextureKeys"
          :key="key"
          class="btn btn-outline btn-sm w-full justify-start"
          @click="addTexture(key)"
        >
          <span class="font-mono text-xs text-base-content/50 w-8">{{ key }}</span>
          <span class="flex-1 text-left">{{ textureKeyOptions[key] }}</span>
        </button>
      </div>
      <div class="mt-4 flex justify-end">
        <button class="btn btn-ghost btn-sm" @click="showAddTextureDialog = false">
          Cancel
        </button>
      </div>
    </div>
  </div>
  </div>

  <!-- Texture Asset Picker Dialog -->
  <AssetPickerDialog
    v-if="isAssetPickerOpen"
    :world-id="worldId"
    :current-path="getTexturePathValue(selectedTextureKey)"
    :extensions="['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg']"
    @close="closeAssetPicker"
    @select="handleAssetSelected"
  />

  <!-- Model Asset Picker Dialog -->
  <AssetPickerDialog
    v-if="isModelAssetPickerOpen"
    :world-id="worldId"
    :current-path="localValue.path"
    :extensions="['glb', 'gltf', 'babylon', 'obj', 'fbx']"
    @close="closeModelAssetPicker"
    @select="handleModelAssetSelected"
  />
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import type { VisibilityModifier, TextureDefinition } from '@nimbus/shared';
import { Shape, ShapeNames, TextureKey, TextureKeyNames, BlockEffect } from '@nimbus/shared';
import AssetPickerDialog from '@components/AssetPickerDialog.vue';
import CollapsibleSection from '@components/CollapsibleSection.vue';
import OffsetsEditor from './OffsetsEditor.vue';
import { assetService } from '../services/AssetService';

interface Props {
  modelValue?: VisibilityModifier;
  worldId?: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: VisibilityModifier | undefined): void;
}>();

const localValue = ref<VisibilityModifier>(
  props.modelValue ? JSON.parse(JSON.stringify(props.modelValue)) : { shape: 1, textures: {} }
);

// Track which textures are expanded
const expandedTextures = ref<Set<number>>(new Set());

// Show add texture dialog
const showAddTextureDialog = ref(false);

// Canvas References per Texture Key
const previewCanvasRefs = ref<Map<number, HTMLCanvasElement>>(new Map());

// Loading state per texture
const textureLoadingState = ref<Map<number, boolean>>(new Map());

// Image Cache (reuse loaded images across renders)
const textureImageCache = ref<Map<string, HTMLImageElement>>(new Map());

// Error Cache (track failed texture loads to prevent retry loops)
const textureErrorCache = ref<Map<string, Error>>(new Map());

// Helper to set canvas ref
const setCanvasRef = (key: number, el: HTMLCanvasElement | null) => {
  if (el) {
    previewCanvasRefs.value.set(key, el);
    // Render preview when canvas is mounted
    setTimeout(() => renderTexturePreview(key), 100);
  } else {
    previewCanvasRefs.value.delete(key);
  }
};

const isTextureLoading = (key: number): boolean => {
  return textureLoadingState.value.get(key) ?? false;
};

// Only watch localValue changes to emit updates (one-way)
watch(localValue, (newValue) => {
  emit('update:modelValue', newValue);
}, { deep: true });

// Shape options
const shapeOptions = ShapeNames;

// Texture key options - use directly from shared package
const textureKeyOptions = TextureKeyNames;

// ============================================
// Texture Management Functions
// ============================================

// Computed: Get list of existing texture keys
const existingTextureKeys = computed(() => {
  if (!localValue.value.textures) return [];
  return Object.keys(localValue.value.textures).map(Number).sort((a, b) => a - b);
});

// Computed: Get list of available texture keys (not yet used)
const availableTextureKeys = computed(() => {
  const existing = new Set(existingTextureKeys.value);
  return Object.keys(textureKeyOptions)
    .map(Number)
    .filter(key => !existing.has(key))
    .sort((a, b) => a - b);
});

// Function to add a new texture
const addTexture = (key: number) => {
  if (!localValue.value.textures) {
    localValue.value.textures = {};
  }
  localValue.value.textures[key] = '';
  showAddTextureDialog.value = false;
};

const isTextureExpanded = (key: number): boolean => {
  return expandedTextures.value.has(key);
};

const toggleTextureExpansion = (key: number) => {
  if (expandedTextures.value.has(key)) {
    // Collapse: Keep as TextureDefinition object (don't convert back to string)
    // This preserves all settings like samplingMode, backFaceCulling, etc.
    expandedTextures.value.delete(key);
  } else {
    // Expand: Convert to TextureDefinition if needed
    const texture = localValue.value.textures?.[key];
    if (texture && typeof texture === 'string') {
      localValue.value.textures![key] = { path: texture };
    }
    expandedTextures.value.add(key);
  }
};

const getTexturePathValue = (key: number): string => {
  if (!localValue.value.textures) return '';
  const texture = localValue.value.textures[key];
  if (!texture) return '';
  return typeof texture === 'string' ? texture : texture.path;
};

const setTexturePath = (key: number, path: string) => {
  if (!localValue.value.textures) {
    localValue.value.textures = {};
  }

  const oldPath = getTexturePathValue(key);

  if (path.trim()) {
    const existing = localValue.value.textures[key];

    // If it's already a TextureDefinition object, keep it as an object
    // If it's a string, keep it as a string (unless expanded)
    if (typeof existing === 'object' && existing !== null) {
      // Already an object, just update the path
      existing.path = path.trim();
    } else if (expandedTextures.value.has(key)) {
      // Not yet an object but expanded, convert to object
      localValue.value.textures[key] = { path: path.trim() };
    } else {
      // Simple string, keep as string
      localValue.value.textures[key] = path.trim();
    }

    // Clear error cache when path changes (allow retry)
    if (oldPath !== path.trim()) {
      textureErrorCache.value.delete(path.trim());
    }
  } else {
    delete localValue.value.textures[key];
    expandedTextures.value.delete(key);
  }
};

const removeTexture = (key: number) => {
  if (localValue.value.textures) {
    delete localValue.value.textures[key];
    expandedTextures.value.delete(key);
  }
};

// Get nested value from TextureDefinition
const getTextureDefValue = (key: number, path: string): any => {
  const texture = localValue.value.textures?.[key];
  if (!texture || typeof texture === 'string') return undefined;

  const parts = path.split('.');
  let value: any = texture;
  for (const part of parts) {
    value = value?.[part];
  }
  return value;
};

// Set nested value in TextureDefinition
const setTextureDefValue = (key: number, path: string, value: any) => {
  if (!localValue.value.textures) {
    localValue.value.textures = {};
  }

  let texture = localValue.value.textures[key];

  // Ensure it's a TextureDefinition
  if (typeof texture === 'string') {
    texture = { path: texture };
    localValue.value.textures[key] = texture;
  } else if (!texture) {
    texture = { path: '' };
    localValue.value.textures[key] = texture;
  }

  // Set nested value
  const parts = path.split('.');
  let obj: any = texture;

  for (let i = 0; i < parts.length - 1; i++) {
    const part = parts[i];
    if (!obj[part]) {
      obj[part] = {};
    }
    obj = obj[part];
  }

  const lastPart = parts[parts.length - 1];

  // Only set if value is not empty/undefined
  // Note: false is a valid value (e.g., for backFaceCulling), so we allow boolean false
  if (value !== undefined && value !== null && value !== '' || typeof value === 'boolean') {
    obj[lastPart] = value;
  } else {
    delete obj[lastPart];
  }
};

// Special handler for wrap mode dropdowns to handle undefined
const handleWrapChange = (key: number, property: 'wrapU' | 'wrapV', value: string) => {
  if (value === '' || value === 'undefined') {
    // Set to undefined (will be deleted)
    setTextureDefValue(key, `uvMapping.${property}`, undefined);
  } else {
    // Set to numeric value
    setTextureDefValue(key, `uvMapping.${property}`, parseInt(value));
  }
};

// ============================================
// Degrees ↔ Radians Conversion
// ============================================

const degreesToRadians = (degrees: number): number => {
  if (isNaN(degrees)) return 0;
  return degrees * (Math.PI / 180);
};

const radiansToDegreesDisplay = (radians: number | undefined): number | '' => {
  if (radians === undefined || radians === null) return '';
  return Math.round(radians * (180 / Math.PI) * 100) / 100; // Round to 2 decimal places
};

// ============================================
// Texture Preview Rendering
// ============================================

const loadTextureImage = async (texturePath: string): Promise<HTMLImageElement> => {
  // Check success cache first
  if (textureImageCache.value.has(texturePath)) {
    return textureImageCache.value.get(texturePath)!;
  }

  // Check error cache - don't retry failed loads
  if (textureErrorCache.value.has(texturePath)) {
    throw textureErrorCache.value.get(texturePath)!;
  }

  // Load image using fetch with credentials, then convert to Image
  return new Promise(async (resolve, reject) => {
    try {
      // Use AssetService to construct correct URL
      if (!props.worldId) {
        const error = new Error('World ID not provided');
        textureErrorCache.value.set(texturePath, error);
        reject(error);
        return;
      }
      const assetUrl = assetService.getAssetUrl(props.worldId, texturePath);

      // Fetch with credentials
      const response = await fetch(assetUrl, {
        credentials: 'include',
        mode: 'cors'
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      // Convert to blob and create object URL
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);

      // Create image from blob URL
      const img = new Image();

      img.onload = () => {
        // Cache successful load
        textureImageCache.value.set(texturePath, img);
        resolve(img);
        // Clean up object URL after image is loaded
        URL.revokeObjectURL(objectUrl);
      };

      img.onerror = () => {
        const error = new Error(`Failed to create image from blob: ${texturePath}`);
        textureErrorCache.value.set(texturePath, error);
        URL.revokeObjectURL(objectUrl);
        reject(error);
      };

      img.src = objectUrl;

    } catch (error) {
      // Cache error to prevent retry loops
      const err = error instanceof Error ? error : new Error(`Failed to load texture: ${texturePath}`);
      textureErrorCache.value.set(texturePath, err);
      reject(err);
    }
  });
};

const renderTexturePreview = async (key: number) => {
  const canvas = previewCanvasRefs.value.get(key);
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  // Clear canvas with checkerboard pattern
  ctx.clearRect(0, 0, 256, 256);
  drawCheckerboard(ctx, 256, 256);

  // Get texture path
  const texturePath = getTexturePathValue(key);
  if (!texturePath) {
    // Draw placeholder
    ctx.fillStyle = '#666';
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('No texture selected', 128, 128);
    return;
  }

  textureLoadingState.value.set(key, true);

  try {
    // Load image
    const img = await loadTextureImage(texturePath);

    // Get UV parameters
    const uvMapping = {
      x: getTextureDefValue(key, 'uvMapping.x') ?? 0,
      y: getTextureDefValue(key, 'uvMapping.y') ?? 0,
      w: getTextureDefValue(key, 'uvMapping.w') ?? img.width,
      h: getTextureDefValue(key, 'uvMapping.h') ?? img.height,
      uScale: getTextureDefValue(key, 'uvMapping.uScale') ?? 1,
      vScale: getTextureDefValue(key, 'uvMapping.vScale') ?? 1,
      uOffset: getTextureDefValue(key, 'uvMapping.uOffset') ?? 0,
      vOffset: getTextureDefValue(key, 'uvMapping.vOffset') ?? 0,
      wAng: getTextureDefValue(key, 'uvMapping.wAng') ?? 0,
      wrapU: getTextureDefValue(key, 'uvMapping.wrapU') ?? 0, // Default: CLAMP
      wrapV: getTextureDefValue(key, 'uvMapping.wrapV') ?? 0, // Default: CLAMP
      uRotationCenter: getTextureDefValue(key, 'uvMapping.uRotationCenter') ?? 0.5,
      vRotationCenter: getTextureDefValue(key, 'uvMapping.vRotationCenter') ?? 0.5,
    };

    // Apply UV transformations
    applyUVTransformations(ctx, img, uvMapping);

  } catch (error) {
    // Show error on canvas (from cache or new error)
    ctx.fillStyle = '#ff0000';
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Failed to load texture', 128, 118);
    ctx.font = '10px sans-serif';
    ctx.fillStyle = '#999';
    ctx.fillText('(Check texture path)', 128, 138);
    console.error('Texture preview error:', error);
  } finally {
    textureLoadingState.value.set(key, false);
  }
};

const drawCheckerboard = (ctx: CanvasRenderingContext2D, width: number, height: number) => {
  const squareSize = 8;
  ctx.fillStyle = '#e0e0e0';
  ctx.fillRect(0, 0, width, height);
  ctx.fillStyle = '#c0c0c0';
  for (let y = 0; y < height; y += squareSize) {
    for (let x = 0; x < width; x += squareSize) {
      if ((x / squareSize + y / squareSize) % 2 === 0) {
        ctx.fillRect(x, y, squareSize, squareSize);
      }
    }
  }
};

const applyUVTransformations = (
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement,
  uv: any
) => {
  ctx.save();

  // Extract source region (Atlas Extraction)
  const sourceX = Math.max(0, Math.min(uv.x, img.width));
  const sourceY = Math.max(0, Math.min(uv.y, img.height));
  const sourceW = Math.max(1, Math.min(uv.w, img.width - sourceX));
  const sourceH = Math.max(1, Math.min(uv.h, img.height - sourceY));

  // Calculate rotation center in canvas space
  const rotCenterX = 128 + (uv.uRotationCenter - 0.5) * 256;
  const rotCenterY = 128 + (uv.vRotationCenter - 0.5) * 256;

  // Move to rotation center
  ctx.translate(rotCenterX, rotCenterY);

  // Apply rotation (wAng)
  if (uv.wAng) {
    ctx.rotate(uv.wAng);
  }

  // Apply scale
  ctx.scale(uv.uScale, uv.vScale);

  // Move back from rotation center
  ctx.translate(-rotCenterX, -rotCenterY);

  // Apply offset (in UV space 0-1, convert to canvas space)
  const offsetX = uv.uOffset * 256;
  const offsetY = uv.vOffset * 256;
  ctx.translate(offsetX, offsetY);

  // Handle wrap modes
  if (uv.wrapU === 1 || uv.wrapV === 1) {
    // REPEAT mode - draw max 3x3 tiles
    const repeatU = uv.wrapU === 1 ? 3 : 1;
    const repeatV = uv.wrapV === 1 ? 3 : 1;

    // Calculate tile size
    const tileWidth = 256 / repeatU;
    const tileHeight = 256 / repeatV;

    // Draw tiles
    for (let row = 0; row < repeatV; row++) {
      for (let col = 0; col < repeatU; col++) {
        const x = col * tileWidth;
        const y = row * tileHeight;
        ctx.drawImage(img, sourceX, sourceY, sourceW, sourceH, x, y, tileWidth, tileHeight);
      }
    }
  } else if (uv.wrapU === 2 || uv.wrapV === 2) {
    // MIRROR mode - draw mirrored copies (max 3x3)
    const repeatU = uv.wrapU === 2 ? 3 : 1;
    const repeatV = uv.wrapV === 2 ? 3 : 1;

    const tileWidth = 256 / repeatU;
    const tileHeight = 256 / repeatV;

    for (let row = 0; row < repeatV; row++) {
      for (let col = 0; col < repeatU; col++) {
        const x = col * tileWidth;
        const y = row * tileHeight;

        ctx.save();
        ctx.translate(x + tileWidth / 2, y + tileHeight / 2);

        // Mirror alternating tiles
        if (col % 2 === 1) ctx.scale(-1, 1);
        if (row % 2 === 1) ctx.scale(1, -1);

        ctx.drawImage(img, sourceX, sourceY, sourceW, sourceH, -tileWidth / 2, -tileHeight / 2, tileWidth, tileHeight);
        ctx.restore();
      }
    }
  } else {
    // CLAMP mode (0) or undefined - single draw
    ctx.drawImage(img, sourceX, sourceY, sourceW, sourceH, 0, 0, 256, 256);
  }

  ctx.restore();
};

// Watch for texture changes and re-render preview (debounced)
let previewRenderTimeout: number | null = null;
watch(
  () => localValue.value.textures,
  () => {
    // Debounce re-render
    if (previewRenderTimeout) {
      clearTimeout(previewRenderTimeout);
    }
    previewRenderTimeout = window.setTimeout(() => {
      expandedTextures.value.forEach((key) => {
        renderTexturePreview(key);
      });
    }, 300);
  },
  { deep: true }
);

// ============================================
// Asset Picker
// ============================================

const isAssetPickerOpen = ref(false);
const selectedTextureKey = ref<number>(0);
const isModelAssetPickerOpen = ref(false);

const openAssetPicker = (key: number) => {
  selectedTextureKey.value = key;
  isAssetPickerOpen.value = true;
};

const closeAssetPicker = () => {
  isAssetPickerOpen.value = false;
};

const handleAssetSelected = (path: string) => {
  setTexturePath(selectedTextureKey.value, path);
  closeAssetPicker();
};

const openModelAssetPicker = () => {
  isModelAssetPickerOpen.value = true;
};

const closeModelAssetPicker = () => {
  isModelAssetPickerOpen.value = false;
};

const handleModelAssetSelected = (path: string) => {
  localValue.value.path = path;
  closeModelAssetPicker();
};

// ===== Geometry Offsets Functions =====

const hasOffsets = computed(() => {
  return localValue.value.offsets !== undefined;
});

const toggleOffsets = (enabled: boolean) => {
  if (!enabled) {
    localValue.value.offsets = undefined;
  } else if (!localValue.value.offsets) {
    localValue.value.offsets = [];
  }
};

// ===== Textures Functions =====

const hasTextures = computed(() => {
  return localValue.value.textures !== undefined;
});

const toggleTextures = (enabled: boolean) => {
  if (!enabled) {
    localValue.value.textures = undefined;
  } else if (!localValue.value.textures) {
    localValue.value.textures = {};
  }
};

// ===== Face Visibility Functions =====

const hasFaceVisibility = computed(() => {
  return localValue.value.faceVisibility !== undefined;
});

const isFixedMode = computed(() => {
  if (!localValue.value.faceVisibility) return false;
  return (localValue.value.faceVisibility & 64) !== 0; // FIXED flag (bit 6)
});

const toggleFaceVisibility = (enabled: boolean) => {
  if (!enabled) {
    localValue.value.faceVisibility = undefined;
  } else if (!localValue.value.faceVisibility) {
    localValue.value.faceVisibility = 63; // 0b00111111 = all 6 faces visible
  }
};

const isFaceVisible = (faceFlag: number): boolean => {
  if (!localValue.value.faceVisibility) return false;
  return (localValue.value.faceVisibility & faceFlag) !== 0;
};

const toggleFace = (faceFlag: number) => {
  if (!localValue.value.faceVisibility) {
    localValue.value.faceVisibility = 0;
  }
  localValue.value.faceVisibility ^= faceFlag; // XOR to toggle bit
};

const toggleFixedMode = () => {
  if (!localValue.value.faceVisibility) {
    localValue.value.faceVisibility = 0;
  }
  localValue.value.faceVisibility ^= 64; // Toggle FIXED flag (bit 6)
};

// ensure rotation object always exists so template v-model bindings are safe
if (!localValue.value.rotation) {
  localValue.value.rotation = { x: 0, y: 0, z: 0 } as any;
} else {
  // fill missing components with defaults
  localValue.value.rotation.x ??= 0 as any;
  localValue.value.rotation.y ??= 0 as any;
  (localValue.value.rotation as any).z ??= 0;
}
</script>
