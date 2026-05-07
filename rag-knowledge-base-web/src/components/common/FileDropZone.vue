<template>
  <div class="file-drop-zone"
       :class="{ dragging: isDragging, disabled }"
       @dragover.prevent="isDragging = true"
       @dragleave.prevent="isDragging = false"
       @drop.prevent="handleDrop"
       @click="!disabled && triggerFileInput()">
    <input ref="fileInputRef" type="file" :multiple="multiple" :accept="accept" hidden @change="handleFileSelect" />
    <slot :is-dragging="isDragging"></slot>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  accept?: string
  multiple?: boolean
  disabled?: boolean
}>(), {
  accept: '.pdf,.docx,.txt,.md',
  multiple: false,
  disabled: false,
})

const emit = defineEmits<{
  select: [files: File[]]
}>()

const isDragging = ref(false)
const fileInputRef = ref<HTMLInputElement>()

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleDrop(e: DragEvent) {
  isDragging.value = false
  if (props.disabled) return
  if (e.dataTransfer?.files) {
    emit('select', Array.from(e.dataTransfer.files))
  }
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    emit('select', Array.from(input.files))
  }
  input.value = ''
}

function reset() {
  if (fileInputRef.value) fileInputRef.value.value = ''
}

defineExpose({ reset })
</script>

<style scoped>
.file-drop-zone {
  border: 2px dashed #D4C8BA;
  border-radius: 12px;
  padding: 36px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.25s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.file-drop-zone:hover { border-color: #D97B2B; background: #FEF3E8; }
.file-drop-zone.dragging { border-color: #D97B2B; background: #FEF3E8; }
.file-drop-zone.disabled { pointer-events: none; border-color: #D97B2B; background: #FFF9F2; }
</style>
