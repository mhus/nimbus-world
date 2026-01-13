<template>
  <div class="card bg-base-200 rounded-box">
    <!-- Header with checkbox and collapse toggle -->
    <div class="card-body p-3">
      <div class="flex items-center gap-2">
        <input
          type="checkbox"
          class="checkbox checkbox-sm"
          :checked="modelValue"
          @click="handleCheckboxClick"
        />
        <button
          class="flex-1 text-left font-medium flex items-center justify-between"
          @click="toggleOpen"
          :disabled="!modelValue"
        >
          <span :class="{ 'text-base-content/40': !modelValue }">{{ title }}</span>
          <span v-if="!modelValue" class="badge badge-ghost badge-sm mr-2">disabled</span>
          <svg
            v-if="modelValue"
            class="w-4 h-4 transition-transform"
            :class="{ 'rotate-180': isOpen }"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>

      <!-- Content -->
      <div v-if="modelValue && isOpen" class="mt-3 pt-3 border-t border-base-300">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

interface Props {
  title: string;
  modelValue?: boolean; // Is this section enabled?
  defaultOpen?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: true,
  defaultOpen: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

const isOpen = ref(props.defaultOpen);

const handleCheckboxClick = (event: Event) => {
  event.stopPropagation();
  const newValue = (event.target as HTMLInputElement).checked;
  emit('update:modelValue', newValue);

  // Auto-open when enabling
  if (newValue) {
    isOpen.value = true;
  }
};

const toggleOpen = () => {
  if (props.modelValue) {
    isOpen.value = !isOpen.value;
  }
};
</script>
