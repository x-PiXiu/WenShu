<template>
  <div :class="['file-diff-card', change.status]">
    <div class="fd-header" @click="expanded = !expanded">
      <svg class="fd-chevron" :class="{ open: expanded }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
      <span class="fd-filename">{{ shortPath }}</span>
      <span class="fd-stats">
        <span v-if="change.oldLines > 0" class="fd-stat-del">-{{ change.oldLines }}</span>
        <span class="fd-stat-add">+{{ change.newLines }}</span>
      </span>
      <span :class="['fd-status-tag', change.status]">{{ statusLabel }}</span>
    </div>

    <div v-if="expanded && diffLoaded" class="fd-diff-view">
      <div v-for="(line, i) in diffLines" :key="i" :class="['fd-line', line.type]">
        <span class="fd-line-num">{{ line.num }}</span>
        <span class="fd-line-content">{{ line.text }}</span>
      </div>
    </div>
    <div v-else-if="expanded && loading" class="fd-loading">加载差异...</div>

    <div v-if="change.status === 'pending'" class="fd-actions">
      <button class="fd-apply-btn" @click.stop="handleApply">应用修改</button>
      <button class="fd-reject-btn" @click.stop="handleReject">放弃</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FileChangeInfo } from '../../types/chat'

const props = defineProps<{ change: FileChangeInfo }>()
const emit = defineEmits<{ statusChange: [changeId: string, status: 'applied' | 'rejected'] }>()

const expanded = ref(false)
const loading = ref(false)
const diffLoaded = ref(false)
const oldContent = ref('')
const newContent = ref('')

const shortPath = computed(() => {
  const p = props.change.path
  const sep = p.includes('/') ? '/' : '\\'
  const parts = p.split(sep)
  return parts.length > 2 ? '.../' + parts.slice(-2).join('/') : p
})

const statusLabel = computed(() => {
  switch (props.change.status) {
    case 'applied': return props.change.oldLines === 0 ? '已创建' : '已应用'
    case 'rejected': return '已放弃'
    default: return props.change.oldLines === 0 ? '新建文件' : '待确认'
  }
})

interface DiffLine {
  type: 'context' | 'add' | 'del'
  num: string
  text: string
}

const diffLines = computed((): DiffLine[] => {
  const oldLines = oldContent.value.split('\n')
  const newLines = newContent.value.split('\n')
  const lines: DiffLine[] = []

  // Special case: new file (no old content)
  if (!oldContent.value.trim()) {
    for (let i = 0; i < newLines.length; i++) {
      lines.push({ type: 'add', num: `${i + 1}`, text: newLines[i] })
    }
    return lines
  }

  // Simple line-by-line diff (skip identical prefix/suffix)
  let oi = 0, ni = 0
  const maxOld = oldLines.length
  const maxNew = newLines.length

  while (oi < maxOld && ni < maxNew && oldLines[oi] === newLines[ni]) {
    lines.push({ type: 'context', num: `${oi + 1}`, text: oldLines[oi] })
    oi++; ni++
  }

  const oldEnd = maxOld - 1
  const newEnd = maxNew - 1
  let commonSuffix = 0
  while (oldEnd - commonSuffix >= oi && newEnd - commonSuffix >= ni
         && oldLines[oldEnd - commonSuffix] === newLines[newEnd - commonSuffix]) {
    commonSuffix++
  }

  for (let i = oi; i <= oldEnd - commonSuffix; i++) {
    lines.push({ type: 'del', num: `${i + 1}`, text: oldLines[i] })
  }
  for (let i = ni; i <= newEnd - commonSuffix; i++) {
    lines.push({ type: 'add', num: `${i + 1}`, text: newLines[i] })
  }

  for (let i = maxOld - commonSuffix; i < maxOld; i++) {
    lines.push({ type: 'context', num: `${i + 1}`, text: oldLines[i] })
  }

  return lines
})

async function loadDiff() {
  if (diffLoaded.value) return
  loading.value = true
  try {
    const res = await fetch(`/api/file-change/${props.change.changeId}/diff`)
    if (res.ok) {
      const data = await res.json()
      oldContent.value = data.oldContent || ''
      newContent.value = data.newContent || ''
      diffLoaded.value = true
    }
  } catch { /* ignore */ }
  loading.value = false
}

async function handleApply() {
  try {
    const res = await fetch(`/api/file-change/${props.change.changeId}/apply`, { method: 'POST' })
    if (res.ok) {
      emit('statusChange', props.change.changeId, 'applied')
    }
  } catch { /* ignore */ }
}

async function handleReject() {
  try {
    const res = await fetch(`/api/file-change/${props.change.changeId}/reject`, { method: 'POST' })
    if (res.ok) {
      emit('statusChange', props.change.changeId, 'rejected')
    }
  } catch { /* ignore */ }
}

// Auto-load diff on expand
import { watch } from 'vue'
watch(expanded, (v) => { if (v) loadDiff() })
</script>

<style scoped>
.file-diff-card {
  border: 1px solid #E8DDD0; border-radius: 8px; overflow: hidden;
  margin-top: 8px; background: #FAFAF6;
}
.file-diff-card.applied { border-color: #4CAF50; opacity: 0.7; }
.file-diff-card.rejected { border-color: #B8A898; opacity: 0.5; }

.fd-header {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; cursor: pointer; font-size: 12px;
  border-bottom: 1px solid #F0E8DD;
}
.fd-header:hover { background: #F5F0EB; }
.fd-chevron { transition: transform 0.2s; flex-shrink: 0; }
.fd-chevron.open { transform: rotate(180deg); }

.fd-filename {
  font-weight: 600; color: #3D3028; flex: 1; min-width: 0;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  font-family: 'SF Mono', 'Cascadia Code', monospace; font-size: 11px;
}

.fd-stats { display: flex; gap: 4px; flex-shrink: 0; }
.fd-stat-add { color: #4CAF50; font-weight: 600; font-size: 11px; }
.fd-stat-del { color: #E85D5D; font-weight: 600; font-size: 11px; }

.fd-status-tag {
  font-size: 10px; font-weight: 600; padding: 2px 6px;
  border-radius: 4px; flex-shrink: 0;
}
.fd-status-tag.pending { background: #FEF3E8; color: #D97B2B; }
.fd-status-tag.applied { background: #E8F5E9; color: #4CAF50; }
.fd-status-tag.rejected { background: #F0E8DD; color: #A89888; }

.fd-diff-view {
  max-height: 600px; overflow-y: auto; font-family: 'SF Mono', 'Cascadia Code', monospace;
  font-size: 11px; line-height: 1.6;
}
.fd-line { display: flex; }
.fd-line-num {
  width: 40px; text-align: right; padding-right: 8px; color: #C8B8A8;
  user-select: none; flex-shrink: 0; border-right: 1px solid #F0E8DD;
}
.fd-line-content { flex: 1; padding-left: 8px; white-space: pre-wrap; word-break: break-all; }
.fd-line.context { background: #fff; }
.fd-line.add { background: #E8F5E9; }
.fd-line.del { background: #FFEBEE; }

.fd-loading { padding: 16px; text-align: center; color: #B8A898; font-size: 12px; }

.fd-actions {
  display: flex; gap: 8px; padding: 8px 12px;
  border-top: 1px solid #F0E8DD; background: #FEFCFA;
}
.fd-apply-btn {
  padding: 4px 16px; border-radius: 6px; border: 1px solid #4CAF50;
  background: #4CAF50; color: #fff; font-size: 12px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.fd-apply-btn:hover { background: #388E3C; }
.fd-reject-btn {
  padding: 4px 16px; border-radius: 6px; border: 1px solid #E8DDD0;
  background: #FAF7F2; color: #8B7E74; font-size: 12px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.fd-reject-btn:hover { border-color: #E85D5D; color: #E85D5D; }
</style>
