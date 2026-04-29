<template>
  <div class="media-manager">
    <div class="mm-header">
      <h2 class="section-title">媒体库</h2>
      <div class="header-actions">
        <button class="btn-secondary" @click="triggerUpload" :disabled="uploading">
          {{ uploading ? '上传中...' : '上传文件' }}
        </button>
        <input ref="fileInputRef" class="file-input" type="file" multiple accept="image/*,.pdf,.doc,.docx" @change="handleUpload" />
      </div>
    </div>

    <div v-if="errorMessage" class="mm-error">{{ errorMessage }}</div>

    <div v-if="media.length === 0 && !uploading" class="empty-state">暂无媒体文件，点击「上传文件」开始</div>

    <div class="mm-grid" v-else>
      <div v-for="item in media" :key="item.id" class="mm-card">
        <div class="card-preview" @click="copyUrl(item.url)">
          <img v-if="item.mimeType.startsWith('image/')" :src="item.url" :alt="item.filename" />
          <div v-else class="file-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <span class="file-ext">{{ ext(item.filename) }}</span>
          </div>
        </div>
        <div class="card-info">
          <span class="card-name" :title="item.filename">{{ item.filename }}</span>
          <span class="card-size">{{ formatSize(item.fileSize) }}</span>
        </div>
        <div class="card-actions">
          <button class="action-btn" @click="copyUrl(item.url)" title="复制链接">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
          </button>
          <button class="action-btn danger" @click="doDelete(item)" title="删除">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      </div>
    </div>

    <div v-if="copiedUrl" class="copy-toast">已复制: {{ copiedUrl }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMedia, MediaApiError } from '../../composables/useMedia'
import type { MediaFile } from '../../composables/useMedia'

const { uploading, listMedia, uploadFiles, deleteMedia } = useMedia()

const media = ref<MediaFile[]>([])
const errorMessage = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)
const copiedUrl = ref('')

async function load() {
  errorMessage.value = ''
  try {
    media.value = await listMedia()
  } catch (e) {
    errorMessage.value = e instanceof MediaApiError ? e.message : '加载媒体列表失败'
  }
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (files.length === 0) return

  errorMessage.value = ''
  try {
    await uploadFiles(files)
    await load()
  } catch (e) {
    errorMessage.value = e instanceof MediaApiError ? e.message : '上传失败，请重试'
  }
}

async function doDelete(item: MediaFile) {
  if (!confirm(`确定删除「${item.filename}」？`)) return
  try {
    await deleteMedia(item.id)
    await load()
  } catch (e) {
    errorMessage.value = e instanceof MediaApiError ? e.message : '删除失败'
  }
}

function copyUrl(url: string) {
  navigator.clipboard.writeText(window.location.origin + url).then(() => {
    copiedUrl.value = url
    setTimeout(() => { copiedUrl.value = '' }, 2000)
  })
}

function ext(filename: string): string {
  const i = filename.lastIndexOf('.')
  return i >= 0 ? filename.substring(i + 1).toUpperCase() : 'FILE'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(load)
</script>

<style scoped>
.media-manager { padding: 24px; position: relative; }

.mm-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.section-title { font-size: 18px; font-weight: 600; color: #3D3028; margin: 0; }
.header-actions { display: flex; gap: 10px; align-items: center; }

.btn-secondary {
  padding: 6px 16px; border-radius: 8px;
  border: 1px solid #E8DDD0; background: #FFFFFF; color: #6B5E52;
  font-size: 13px; font-weight: 500; cursor: pointer;
}
.btn-secondary:hover { border-color: #D97B2B; color: #D97B2B; }
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }
.file-input { display: none; }

.mm-error {
  padding: 10px 12px; margin-bottom: 12px; border-radius: 8px;
  background: #FFF0F0; color: #C93A3A; border: 1px solid #FFD0D0; font-size: 13px;
}

.empty-state { text-align: center; padding: 40px; color: #B8A898; }

.mm-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}

.mm-card {
  background: #FFFFFF; border: 1px solid #E8DDD0; border-radius: 12px;
  overflow: hidden; transition: border-color 0.2s;
}
.mm-card:hover { border-color: #D4C8BA; }

.card-preview {
  height: 130px; background: #FAFAF8; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.card-preview img { width: 100%; height: 100%; object-fit: cover; }
.file-icon {
  display: flex; flex-direction: column; align-items: center; gap: 6px; color: #B8A898;
}
.file-ext {
  font-size: 11px; font-weight: 600; color: #8B7E74;
  background: #F5F0EB; padding: 2px 8px; border-radius: 4px;
}

.card-info { padding: 8px 10px; display: flex; justify-content: space-between; align-items: center; }
.card-name { font-size: 12px; color: #3D3028; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.card-size { font-size: 11px; color: #B8A898; margin-left: 8px; flex-shrink: 0; }

.card-actions {
  display: flex; gap: 4px; padding: 0 8px 8px; justify-content: flex-end;
}
.action-btn {
  width: 26px; height: 26px; border: 1px solid #E8DDD0; border-radius: 5px;
  background: #FFFFFF; color: #8B7E74; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: all 0.2s;
}
.action-btn:hover { border-color: #D97B2B; color: #D97B2B; }
.action-btn.danger:hover { border-color: #E85D5D; color: #E85D5D; }

.copy-toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: #3D3028; color: #FFFFFF; padding: 8px 20px; border-radius: 8px;
  font-size: 13px; z-index: 200; animation: fadeIn 0.2s;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
</style>
