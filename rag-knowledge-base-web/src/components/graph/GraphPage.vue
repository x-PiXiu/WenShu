<template>
  <div class="graph-page">
    <!-- ===== Main Graph Section ===== -->
    <div class="section-divider">全库图谱</div>

    <div v-if="mainGraph" class="main-graph-section">
      <div class="main-header">
        <div class="main-info">
          <h2>{{ mainGraph.title }}</h2>
          <span :class="['status-badge', mainGraph.status]">{{ statusLabel(mainGraph.status) }}</span>
        </div>
        <div class="main-meta">
          <span>{{ mainGraph.nodeCount }} 节点</span>
          <span>{{ mainGraph.edgeCount }} 关系</span>
        </div>
        <div class="main-actions">
          <button class="gen-btn" @click="$emit('select', mainGraph.id)" :disabled="mainGraph.status !== 'ready'">查看图谱</button>
          <button class="outline-btn" @click="handleExport(mainGraph.id)">导出</button>
          <button class="outline-btn danger" @click="handleDeleteMain">删除</button>
        </div>
      </div>
      <p v-if="mainGraph.description" class="main-desc">{{ mainGraph.description }}</p>
    </div>

    <!-- Progress bar during generation -->
    <div v-if="generatingAll && progress" class="progress-section">
      <div class="progress-bar-wrapper">
        <div class="progress-bar" :style="{ width: progressPct + '%' }"></div>
      </div>
      <div class="progress-text">
        {{ progress.processed }} / {{ progress.total }} 文档
        <span v-if="progress.currentFile" class="current-file">{{ progress.currentFile }}</span>
        <span v-if="progress.failed > 0" class="failed-count">{{ progress.failed }} 失败</span>
      </div>
    </div>
    <div v-if="generatingAll && !progress" class="progress-section">
      <div class="progress-text">正在初始化...</div>
    </div>

    <!-- No main graph yet -->
    <div v-if="!mainGraph && !generatingAll" class="empty-state">
      <p class="empty-title">尚未生成全库知识图谱</p>
      <p class="hint">从知识库中所有文档提取知识实体和关系，生成一张完整的知识图谱。<br>后续上传新文档时会自动补充新知识点。</p>
      <button class="gen-btn big" @click="handleGenerateAll">一键生成全库图谱</button>
    </div>

    <!-- Re-generate / refresh -->
    <div v-if="mainGraph && !generatingAll" class="toolbar-row">
      <button class="gen-btn" @click="handleGenerateAll">重新生成</button>
      <button class="outline-btn" @click="refreshGraphs">刷新</button>
    </div>

    <!-- ===== Single Document Graphs ===== -->
    <div class="section-divider">单文档图谱</div>

    <div class="doc-toolbar">
      <button class="gen-btn" @click="showSingleModal = true" :disabled="documents.length === 0 || generatingSingle">
        从文档生成
      </button>
      <span v-if="generatingSingle" class="gen-hint">正在提取知识...</span>
    </div>

    <div v-if="docGraphs.length === 0" class="doc-empty">暂无单文档图谱</div>

    <div v-else class="doc-list">
      <div v-for="g in docGraphs" :key="g.id" class="doc-card" @click="$emit('select', g.id)">
        <div class="doc-card-info">
          <span class="doc-card-title">{{ g.title }}</span>
          <span :class="['status-badge', g.status]">{{ statusLabel(g.status) }}</span>
        </div>
        <div class="doc-card-meta">
          <span>{{ g.nodeCount }} 节点</span>
          <span>{{ g.edgeCount }} 关系</span>
          <span v-if="g.sourceFile && g.sourceFile !== '__FULL_KB__'" class="doc-source">{{ g.sourceFile }}</span>
        </div>
        <div class="doc-card-actions">
          <button class="sm-btn" @click.stop="handleExport(g.id)">导出</button>
          <button class="sm-btn danger" @click.stop="handleDeleteDoc(g.id, g.title)">删除</button>
        </div>
      </div>
    </div>

    <!-- Single doc generate modal -->
    <div v-if="showSingleModal" class="modal-overlay" @click.self="showSingleModal = false">
      <div class="modal">
        <h3>从文档生成知识图谱</h3>
        <div class="form-group">
          <label>选择文档</label>
          <select v-model="selectedFile">
            <option value="">请选择...</option>
            <option v-for="doc in documents" :key="doc.name" :value="doc.name">{{ doc.name }}</option>
          </select>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>最大节点数</label>
            <input type="number" v-model.number="maxNodes" min="5" max="100" />
          </div>
          <div class="form-group">
            <label>最大关系数</label>
            <input type="number" v-model.number="maxEdges" min="5" max="200" />
          </div>
        </div>
        <div class="modal-actions">
          <button class="cancel-btn" @click="showSingleModal = false">取消</button>
          <button class="gen-btn" @click="handleGenerateSingle" :disabled="!selectedFile || generatingSingle">
            {{ generatingSingle ? '生成中...' : '开始生成' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGraph } from '../../composables/useGraph'
import type { GenerateProgress } from '../../composables/useGraph'
import type { Graph } from '../../types/graph'

const emit = defineEmits<{
  select: [id: string]
}>()

const { graphs, loadGraphs, generate, generateAll, deleteGraph, exportGraph } = useGraph()

const generatingAll = ref(false)
const generatingSingle = ref(false)
const progress = ref<GenerateProgress | null>(null)
const mainGraph = ref<Graph | null>(null)
const documents = ref<{ name: string }[]>([])

// Single doc form
const showSingleModal = ref(false)
const selectedFile = ref('')
const maxNodes = ref(30)
const maxEdges = ref(50)

const progressPct = computed(() => {
  if (!progress.value || progress.value.total === 0) return 0
  return Math.round((progress.value.processed / progress.value.total) * 100)
})

const docGraphs = computed(() => graphs.value.filter(g => g.sourceFile !== '__FULL_KB__'))

onMounted(async () => {
  await loadGraphs()
  updateMainGraph()
  await loadDocuments()
})

function updateMainGraph() {
  mainGraph.value = graphs.value.find(g => g.sourceFile === '__FULL_KB__') || null
}

async function loadDocuments() {
  try {
    const res = await fetch('/api/documents')
    if (res.ok) documents.value = await res.json()
  } catch { /* ignore */ }
}

async function handleGenerateAll() {
  generatingAll.value = true
  progress.value = null
  const result = await generateAll(20, 30, (p) => { progress.value = p })
  generatingAll.value = false
  progress.value = null
  await loadGraphs()
  updateMainGraph()
  if (result && mainGraph.value?.status === 'ready') emit('select', mainGraph.value.id)
}

async function handleGenerateSingle() {
  if (!selectedFile.value) return
  generatingSingle.value = true
  const result = await generate(selectedFile.value, maxNodes.value, maxEdges.value)
  generatingSingle.value = false
  if (result) {
    showSingleModal.value = false
    selectedFile.value = ''
    await loadGraphs()
    updateMainGraph()
    emit('select', result.graphId)
  }
}

async function refreshGraphs() {
  await loadGraphs()
  updateMainGraph()
}

async function handleDeleteMain() {
  if (!mainGraph.value) return
  if (!confirm('确定删除全库知识图谱？')) return
  await deleteGraph(mainGraph.value.id)
  await refreshGraphs()
}

async function handleDeleteDoc(id: string, title: string) {
  if (!confirm(`确定删除「${title}」？`)) return
  await deleteGraph(id)
  await loadGraphs()
}

async function handleExport(id: string) {
  await exportGraph(id)
}

function statusLabel(s: string) {
  return s === 'generating' ? '生成中' : s === 'ready' ? '就绪' : s === 'failed' ? '失败' : s
}
</script>

<style scoped>
.graph-page { padding: 20px 0; }

.section-divider {
  font-size: 14px; font-weight: 600; color: #3D3028;
  margin: 20px 0 12px; padding-bottom: 8px;
  border-bottom: 2px solid #E8DDD0;
}
.section-divider:first-child { margin-top: 0; }

/* Main graph */
.main-graph-section {
  background: #FFF; border: 1px solid #E8DDD0; border-radius: 12px;
  padding: 20px; margin-bottom: 12px;
}
.main-header { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.main-info { display: flex; align-items: center; gap: 10px; }
.main-info h2 { margin: 0; font-size: 18px; color: #3D3028; }
.main-meta { display: flex; gap: 12px; font-size: 13px; color: #8B7E74; }
.main-actions { display: flex; gap: 8px; margin-left: auto; }
.main-desc { font-size: 13px; color: #8B7E74; margin: 10px 0 0; line-height: 1.5; }

.toolbar-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }

/* Progress */
.progress-section {
  background: #FFF; border: 1px solid #E8DDD0; border-radius: 10px;
  padding: 16px 20px; margin-bottom: 12px;
}
.progress-bar-wrapper { height: 6px; background: #E8DDD0; border-radius: 3px; overflow: hidden; margin-bottom: 8px; }
.progress-bar { height: 100%; background: linear-gradient(90deg, #D97B2B, #E8913A); border-radius: 3px; transition: width 0.3s ease; }
.progress-text { font-size: 13px; color: #6B5E52; display: flex; align-items: center; gap: 8px; }
.current-file { color: #B8A898; font-size: 11px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.failed-count { color: #E85D5D; font-size: 11px; }

/* Empty state */
.empty-state { text-align: center; padding: 40px 20px; margin-bottom: 12px; background: #FAF7F2; border-radius: 12px; }
.empty-title { font-size: 16px; font-weight: 600; color: #3D3028; margin: 0 0 8px; }
.hint { font-size: 13px; color: #B8A898; line-height: 1.6; margin: 0 0 16px; }

/* Doc toolbar */
.doc-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.gen-hint { font-size: 13px; color: #E8913A; }
.doc-empty { font-size: 13px; color: #B8A898; padding: 20px 0; text-align: center; }

/* Doc list */
.doc-list { display: flex; flex-direction: column; gap: 8px; }
.doc-card {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #FAF7F2; border: 1px solid #E8DDD0;
  border-radius: 10px; cursor: pointer; transition: all 0.2s;
}
.doc-card:hover { border-color: #D97B2B; }
.doc-card-info { display: flex; align-items: center; gap: 8px; }
.doc-card-title { font-size: 14px; font-weight: 600; color: #3D3028; }
.doc-card-meta { display: flex; gap: 10px; font-size: 12px; color: #8B7E74; }
.doc-source { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.doc-card-actions { display: flex; gap: 6px; flex-shrink: 0; }
.sm-btn {
  padding: 3px 10px; border-radius: 5px; border: 1px solid #E8DDD0;
  background: #FFF; font-size: 11px; cursor: pointer; color: #6B5E52; transition: all 0.15s;
}
.sm-btn:hover { border-color: #D97B2B; color: #D97B2B; }
.sm-btn.danger:hover { border-color: #E85D5D; color: #E85D5D; }

/* Badges */
.status-badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 500; }
.status-badge.generating { background: #FFF3E0; color: #E8913A; }
.status-badge.ready { background: #E8F5E9; color: #4CAF50; }
.status-badge.failed { background: #FFEBEE; color: #E85D5D; }

/* Buttons */
.gen-btn {
  padding: 8px 20px; border-radius: 8px; border: none;
  background: #D97B2B; color: #FFF; font-size: 13px;
  font-weight: 500; cursor: pointer; transition: background 0.2s;
}
.gen-btn:hover { background: #C06A1E; }
.gen-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.gen-btn.big { padding: 12px 32px; font-size: 15px; }
.outline-btn {
  padding: 8px 16px; border-radius: 8px; border: 1px solid #E8DDD0;
  background: #FFF; color: #6B5E52; font-size: 13px;
  cursor: pointer; transition: all 0.2s;
}
.outline-btn:hover { border-color: #D97B2B; color: #D97B2B; }
.outline-btn.danger:hover { border-color: #E85D5D; color: #E85D5D; }

/* Modal */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.3); display: flex; align-items: center;
  justify-content: center; z-index: 1000;
}
.modal {
  background: #FFF; border-radius: 14px; padding: 24px;
  width: 440px; max-width: 90vw; box-shadow: 0 8px 32px rgba(0,0,0,0.12);
}
.modal h3 { margin: 0 0 20px; font-size: 17px; color: #3D3028; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 500; color: #6B5E52; margin-bottom: 6px; }
.form-group select, .form-group input[type="number"] {
  width: 100%; padding: 8px 12px; border: 1px solid #E8DDD0;
  border-radius: 8px; font-size: 13px; color: #3D3028; font-family: inherit; box-sizing: border-box;
}
.form-group select:focus, .form-group input:focus { outline: none; border-color: #D97B2B; }
.form-row { display: flex; gap: 12px; }
.form-row .form-group { flex: 1; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.cancel-btn {
  padding: 8px 20px; border-radius: 8px; border: 1px solid #E8DDD0;
  background: #FFF; font-size: 13px; cursor: pointer;
}
</style>
