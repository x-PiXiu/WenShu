<template>
  <div class="kb-page">
    <div class="kb-container">
      <!-- Upload Area -->
      <div class="kb-card upload-card">
        <div class="card-header">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
          <span>上传文档</span>
        </div>

        <!-- Document Type Selector -->
        <div class="type-selector">
          <span class="type-label">文档类型：</span>
          <div class="type-options">
            <button
              :class="['type-btn type-btn-auto', { active: selectedType === 'AUTO' }]"
              @click="selectedType = 'AUTO'"
              title="自动检测文档类型并选择最优分块策略"
            >
              自动检测
            </button>
            <button
              v-for="t in documentTypes"
              :key="t.name"
              :class="['type-btn', { active: selectedType === t.name }]"
              @click="selectedType = t.name"
              :title="`${t.label}: chunk=${t.chunkSize}, overlap=${t.chunkOverlap}`"
            >
              {{ t.label }}
            </button>
          </div>
          <span class="type-hint" v-if="selectedType !== 'AUTO'">分块: {{ currentTypePreview.chunkSize }} / 重叠: {{ currentTypePreview.chunkOverlap }}</span>
          <span class="type-hint" v-else>智能识别文档类型，自动选择最优分块策略</span>
        </div>

        <div
          class="drop-zone"
          :class="{ dragging: isDragging, uploading: uploading }"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
          @click="triggerFileInput"
        >
          <input
            ref="fileInputRef"
            type="file"
            multiple
            accept=".pdf,.docx,.md,.txt"
            style="display: none"
            @change="handleFileSelect"
          />
          <template v-if="!uploading">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#C8B8A8" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><polyline points="9 15 12 12 15 15"/></svg>
            <p class="drop-text">拖拽文件到此处，或点击选择</p>
            <p class="drop-hint">支持 PDF、DOCX、MD、TXT 格式，将以「{{ currentTypePreview.label }}」策略分块</p>
          </template>
          <template v-else>
            <div class="upload-spinner"></div>
            <p class="drop-text">正在上传并向量化...</p>
          </template>
        </div>
        <div v-if="uploadResult" class="upload-result">
          <div v-if="uploadResult.indexed.length" class="result-success">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#4CAF50" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            成功索引: {{ uploadResult.indexed.join(', ') }}
          </div>
          <div v-if="uploadResult.failed.length" class="result-fail">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#E85D5D" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            失败: {{ uploadResult.failed.join('; ') }}
          </div>
        </div>
      </div>

      <!-- Stats Summary -->
      <div class="kb-card stats-card" v-if="stats">
        <div class="stat-item">
          <span class="stat-value">{{ stats.documentCount }}</span>
          <span class="stat-label">文档</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value">{{ stats.segmentCount }}</span>
          <span class="stat-label">分段</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value">{{ stats.embeddingModel }}</span>
          <span class="stat-label">Embedding</span>
        </div>
      </div>

      <!-- Document List -->
      <div class="kb-card">
        <div class="card-header">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
          <span>知识库文档</span>
          <button class="refresh-btn" @click="loadDocuments" title="刷新">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          </button>
        </div>

        <div v-if="documents.length === 0" class="empty-docs">
          <p>暂无文档，请上传文件到知识库</p>
        </div>

        <div v-else class="doc-list">
          <div v-for="doc in documents" :key="doc.name" class="doc-item">
            <div :class="['doc-icon', formatClass(doc.format)]">
              {{ doc.format }}
            </div>
            <div class="doc-info">
              <div class="doc-name-row">
                <span class="doc-name">{{ doc.name }}</span>
                <span :class="['doc-type-badge', docTypeBadgeClass(doc.docType)]">{{ doc.docTypeLabel }}</span>
                <span v-if="doc.detectionMethod === 'structure'" class="detect-auto" title="自动检测识别">AUTO</span>
              </div>
              <span class="doc-meta">{{ formatSize(doc.size) }} &middot; {{ formatDate(doc.lastModified) }} &middot; chunk={{ doc.chunkSize }}/{{ doc.chunkOverlap }}</span>
            </div>
            <button class="doc-delete" @click="handleDelete(doc.name)" title="删除">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

interface DocTypeInfo {
  name: string
  label: string
  chunkSize: number
  chunkOverlap: number
}

interface DocumentInfo {
  name: string
  size: number
  lastModified: number
  format: string
  docType: string
  docTypeLabel: string
  chunkSize: number
  chunkOverlap: number
  detectionMethod: string
}

interface UploadResult {
  indexed: string[]
  failed: string[]
  documentCount: number
  segmentCount: number
}

const documents = ref<DocumentInfo[]>([])
const uploading = ref(false)
const isDragging = ref(false)
const uploadResult = ref<UploadResult | null>(null)
const stats = ref<{ documentCount: number; segmentCount: number; embeddingModel: string } | null>(null)
const fileInputRef = ref<HTMLInputElement>()
const selectedType = ref('AUTO')
const documentTypes = ref<DocTypeInfo[]>([])

const currentTypePreview = computed(() => {
  if (selectedType.value === 'AUTO') {
    return { name: 'AUTO', label: '自动检测', chunkSize: '-', chunkOverlap: '-' }
  }
  return documentTypes.value.find(t => t.name === selectedType.value) || { name: 'GENERAL', label: '通用文档', chunkSize: 512, chunkOverlap: 50 }
})

async function loadDocumentTypes() {
  try {
    const res = await fetch('/api/documents/types')
    if (res.ok) {
      documentTypes.value = await res.json()
    }
  } catch { /* ignore */ }
}

async function loadDocuments() {
  try {
    const res = await fetch('/api/documents')
    if (res.ok) {
      documents.value = await res.json()
    }
  } catch { /* ignore */ }
}

async function loadStats() {
  try {
    const res = await fetch('/api/knowledge/stats')
    if (res.ok) {
      stats.value = await res.json()
    }
  } catch { /* ignore */ }
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

async function uploadFiles(files: FileList | File[]) {
  if (files.length === 0) return
  uploading.value = true
  uploadResult.value = null

  const formData = new FormData()
  for (const file of files) {
    formData.append('files', file)
  }
  formData.append('type', selectedType.value)

  try {
    const res = await fetch('/api/documents/upload', {
      method: 'POST',
      body: formData,
    })
    if (res.ok) {
      uploadResult.value = await res.json()
      await loadDocuments()
      await loadStats()
    } else {
      const err = await res.json()
      uploadResult.value = { indexed: [], failed: [err.error || `HTTP ${res.status}`], documentCount: 0, segmentCount: 0 }
    }
  } catch (e: any) {
    uploadResult.value = { indexed: [], failed: [e.message], documentCount: 0, segmentCount: 0 }
  } finally {
    uploading.value = false
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) uploadFiles(input.files)
}

function handleDrop(e: DragEvent) {
  isDragging.value = false
  if (e.dataTransfer?.files) uploadFiles(e.dataTransfer.files)
}

async function handleDelete(filename: string) {
  try {
    const res = await fetch(`/api/documents/${encodeURIComponent(filename)}`, { method: 'DELETE' })
    if (res.ok) {
      await loadDocuments()
      await loadStats()
    }
  } catch { /* ignore */ }
}

function formatClass(format: string): string {
  const f = format.toUpperCase()
  if (f === 'PDF') return 'type-pdf'
  if (f === 'DOCX') return 'type-docx'
  if (f === 'MD') return 'type-md'
  return 'type-txt'
}

function docTypeBadgeClass(docType: string): string {
  switch (docType) {
    case 'TECHNICAL': return 'badge-tech'
    case 'FAQ': return 'badge-faq'
    case 'LOG': return 'badge-log'
    case 'ARTICLE': return 'badge-article'
    default: return 'badge-general'
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(ts: number): string {
  return new Date(ts).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  loadDocumentTypes()
  loadDocuments()
  loadStats()
})
</script>

<style scoped>
.kb-page {
  flex: 1;
  overflow-y: auto;
  background: #F8F5EF;
}
.kb-container {
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 32px;
}
.kb-card {
  background: #FFFFFF;
  border: 1px solid #E8DDD0;
  border-radius: 14px;
  padding: 20px 24px;
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #3D3028;
  margin-bottom: 16px;
}
.refresh-btn {
  margin-left: auto;
  border: none;
  background: none;
  cursor: pointer;
  color: #A89888;
  display: flex;
  align-items: center;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}
.refresh-btn:hover { color: #D97B2B; background: #FEF3E8; }

/* Document Type Selector */
.type-selector {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.type-label {
  font-size: 13px;
  font-weight: 500;
  color: #6B5E52;
}
.type-options {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.type-btn {
  padding: 4px 12px;
  border-radius: 8px;
  border: 1px solid #E8DDD0;
  background: #FAF7F2;
  color: #8B7E74;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.type-btn:hover { border-color: #D97B2B; color: #D97B2B; }
.type-btn.active {
  background: linear-gradient(135deg, #FEF3E8, #FFF5ED);
  border-color: #D97B2B;
  color: #D97B2B;
}
.type-btn-auto {
  border-style: dashed;
  font-weight: 500;
}
.type-btn-auto.active {
  border-style: solid;
  background: linear-gradient(135deg, #E8F5E9, #F1F8E9);
  border-color: #4CAF50;
  color: #2E7D32;
}
.type-hint {
  font-size: 11px;
  color: #B8A898;
  margin-left: auto;
}

/* Drop Zone */
.drop-zone {
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
.drop-zone:hover { border-color: #D97B2B; background: #FEF3E8; }
.drop-zone.dragging { border-color: #D97B2B; background: #FEF3E8; }
.drop-zone.uploading { border-color: #D97B2B; background: #FFF9F2; pointer-events: none; }
.drop-text { font-size: 14px; color: #6B5E52; margin: 0; }
.drop-hint { font-size: 12px; color: #B8A898; margin: 0; }

.upload-spinner {
  width: 28px; height: 28px;
  border: 3px solid #E8DDD0;
  border-top-color: #D97B2B;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.upload-result {
  margin-top: 12px;
  font-size: 13px;
}
.result-success {
  display: flex; align-items: center; gap: 6px;
  color: #4CAF50; margin-bottom: 4px;
}
.result-fail {
  display: flex; align-items: center; gap: 6px;
  color: #E85D5D;
}

/* Stats Card */
.stats-card {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 16px 24px;
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat-value { font-size: 16px; font-weight: 600; color: #3D3028; }
.stat-label { font-size: 11px; color: #A89888; }
.stat-divider { width: 1px; height: 32px; background: #E8DDD0; }

/* Document List */
.empty-docs {
  text-align: center;
  padding: 24px 0;
  color: #B8A898;
  font-size: 14px;
}
.doc-list { display: flex; flex-direction: column; gap: 0; }
.doc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #F5F0EB;
}
.doc-item:last-child { border-bottom: none; }
.doc-icon {
  width: 40px; height: 40px;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700;
  flex-shrink: 0;
}
.type-pdf { background: #FFEBEE; color: #E85D5D; }
.type-docx { background: #E3F2FD; color: #2196F3; }
.type-md { background: #E8F5E9; color: #4CAF50; }
.type-txt { background: #F5F0EB; color: #8B7E74; }
.doc-info { flex: 1; min-width: 0; }
.doc-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}
.doc-name {
  font-size: 14px; font-weight: 500; color: #3D3028;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.doc-type-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
  letter-spacing: 0.3px;
}
.badge-general { background: #F5F0EB; color: #8B7E74; }
.badge-tech { background: #E3F2FD; color: #1565C0; }
.badge-faq { background: #FFF3E0; color: #E65100; }
.badge-log { background: #F3E5F5; color: #7B1FA2; }
.badge-article { background: #E8F5E9; color: #2E7D32; }
.detect-auto {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 700;
  background: #E8F5E9;
  color: #4CAF50;
  letter-spacing: 0.5px;
}
.doc-meta { font-size: 12px; color: #A89898; }
.doc-delete {
  border: none; background: none; cursor: pointer;
  color: #C8B8A8; padding: 6px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.2s; flex-shrink: 0;
}
.doc-delete:hover { color: #E85D5D; background: #FFEBEE; }
</style>
