<template>
  <div class="memory-manager">
    <div class="cm-header">
      <h2 class="section-title">记忆管理</h2>
      <div class="header-actions">
        <span class="mem-count">共 {{ memories.length }} 条记忆</span>
        <button class="btn-secondary" @click="recalcDecay" :disabled="loading">
          {{ loading ? '计算中...' : '重算衰减' }}
        </button>
      </div>
    </div>

    <div v-if="errorMessage" class="cm-error">{{ errorMessage }}</div>

    <div v-if="memories.length === 0 && !loading" class="empty-state">
      暂无记忆。对话积累 6 轮后会自动生成记忆摘要。
    </div>

    <div class="mem-list" v-else>
      <div v-for="mem in memories" :key="mem.id" class="mem-row">
        <div class="mem-main">
          <div class="mem-summary">{{ mem.summary }}</div>
          <div class="mem-meta">
            <span class="mem-tag" :class="importanceClass(mem)">
              {{ mem.importance >= 0.7 ? 'HIGH' : mem.importance >= 0.4 ? 'MEDIUM' : 'LOW' }}
              ({{ mem.importance.toFixed(2) }})
            </span>
            <span class="mem-info">衰减: {{ mem.decayedImportance.toFixed(2) }}</span>
            <span class="mem-info">召回: {{ mem.accessCount }} 次</span>
            <span class="mem-info">{{ formatAge(mem.createdAt) }}</span>
          </div>
        </div>
        <div class="mem-actions">
          <button class="action-btn danger" @click="doDelete(mem)" title="删除">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { MemoryEntry } from '../../types/chat'

const memories = ref<MemoryEntry[]>([])
const loading = ref(false)
const errorMessage = ref('')

async function loadMemories() {
  loading.value = true
  try {
    const res = await fetch('/api/admin/memories')
    if (res.ok) memories.value = await res.json()
  } catch { /* ignore */ }
  loading.value = false
}

async function recalcDecay() {
  loading.value = true
  try {
    await fetch('/api/admin/memories/recalc', { method: 'POST' })
    await loadMemories()
  } catch { /* ignore */ }
  loading.value = false
}

async function doDelete(mem: MemoryEntry) {
  if (!confirm(`确定删除该记忆？\n"${mem.summary.substring(0, 60)}..."`)) return
  try {
    await fetch(`/api/admin/memories/${mem.id}`, { method: 'DELETE' })
    memories.value = memories.value.filter(m => m.id !== mem.id)
  } catch {
    errorMessage.value = '删除失败'
    setTimeout(() => errorMessage.value = '', 3000)
  }
}

function importanceClass(mem: MemoryEntry) {
  if (mem.importance >= 0.7) return 'tag-high'
  if (mem.importance >= 0.4) return 'tag-medium'
  return 'tag-low'
}

function formatAge(ts: number) {
  const days = Math.floor((Date.now() - ts) / 86400000)
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 30) return `${days} 天前`
  return `${Math.floor(days / 30)} 个月前`
}

onMounted(loadMemories)
</script>

<style scoped>
.memory-manager { padding: 0; }
.cm-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px;
}
.header-actions { display: flex; align-items: center; gap: 12px; }
.mem-count { font-size: 13px; color: #888; }
.cm-error {
  background: #fef2f2; color: #dc2626; padding: 8px 12px;
  border-radius: 6px; margin-bottom: 12px; font-size: 13px;
}
.empty-state {
  text-align: center; padding: 40px 20px; color: #888;
  font-size: 14px; background: #fafafa; border-radius: 8px;
}
.mem-list { display: flex; flex-direction: column; gap: 8px; }
.mem-row {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 12px 16px; background: white; border: 1px solid #e8e8e8;
  border-radius: 8px; transition: box-shadow 0.2s;
}
.mem-row:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.mem-main { flex: 1; min-width: 0; }
.mem-summary {
  font-size: 14px; color: #333; line-height: 1.5;
  margin-bottom: 6px; word-break: break-word;
}
.mem-meta { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.mem-tag {
  font-size: 11px; font-weight: 600; padding: 2px 8px;
  border-radius: 4px; letter-spacing: 0.5px;
}
.tag-high { background: #dcfce7; color: #16a34a; }
.tag-medium { background: #fef9c3; color: #ca8a04; }
.tag-low { background: #f3f4f6; color: #6b7280; }
.mem-info { font-size: 12px; color: #888; }
.mem-actions { display: flex; gap: 4px; margin-left: 12px; }
.action-btn {
  background: none; border: none; cursor: pointer; padding: 4px;
  color: #888; border-radius: 4px; display: flex; align-items: center;
}
.action-btn:hover { color: #333; background: #f5f5f5; }
.action-btn.danger:hover { color: #dc2626; background: #fef2f2; }
</style>
