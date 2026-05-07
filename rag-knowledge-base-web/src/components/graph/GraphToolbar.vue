<template>
  <div class="graph-toolbar">
    <div class="toolbar-left">
      <input
        v-model="searchQuery"
        class="search-input"
        placeholder="搜索节点..."
        @input="onSearch"
      />
    </div>
    <div class="toolbar-center">
      <button
        v-for="t in nodeTypes" :key="t.value"
        :class="['filter-chip', { active: activeTypes.has(t.value) }]"
        @click="toggleType(t.value)"
        :title="t.label"
      >
        <span class="chip-dot" :style="{ background: t.color }"></span>
        {{ t.label }}
      </button>
    </div>
    <div class="toolbar-right">
      <button class="tool-btn" @click="$emit('fit')" title="适配视图">适配</button>
      <button class="tool-btn" @click="$emit('export')" title="导出 JSON">导出</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GraphNode } from '../../types/graph'

const props = defineProps<{
  nodes: GraphNode[]
  selectedNode: GraphNode | null
}>()

const emit = defineEmits<{
  search: [query: string]
  filter: [types: string[]]
  fit: []
  export: []
}>()

const searchQuery = ref('')
const activeTypes = ref<Set<string>>(new Set())

const nodeTypes = [
  { value: 'concept', label: '概念', color: '#D97B2B' },
  { value: 'person', label: '人物', color: '#E85D5D' },
  { value: 'org', label: '组织', color: '#2196F3' },
  { value: 'technology', label: '技术', color: '#4CAF50' },
  { value: 'event', label: '事件', color: '#9C27B0' },
  { value: 'location', label: '地点', color: '#00BCD4' },
  { value: 'other', label: '其他', color: '#8B7E74' },
]

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    emit('search', searchQuery.value)
  }, 300)
}

function toggleType(type: string) {
  const next = new Set(activeTypes.value)
  if (next.has(type)) next.delete(type)
  else next.add(type)
  activeTypes.value = next
  emit('filter', Array.from(next))
}
</script>

<style scoped>
.graph-toolbar {
  position: absolute; top: 12px; left: 12px; right: 12px;
  display: flex; align-items: center; gap: 12px;
  padding: 8px 12px; background: rgba(255,255,255,0.92);
  border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  z-index: 10; backdrop-filter: blur(4px);
}
.toolbar-left { flex-shrink: 0; }
.search-input {
  width: 160px; padding: 6px 10px; border: 1px solid #E8DDD0;
  border-radius: 6px; font-size: 12px; color: #3D3028;
  font-family: inherit;
}
.search-input:focus { outline: none; border-color: #D97B2B; }
.toolbar-center { display: flex; gap: 4px; flex-wrap: wrap; flex: 1; justify-content: center; }
.filter-chip {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 8px; border-radius: 6px; border: 1px solid #E8DDD0;
  background: #FFF; font-size: 11px; cursor: pointer;
  color: #6B5E52; transition: all 0.15s; white-space: nowrap;
}
.filter-chip.active { border-color: #D97B2B; background: #FEF3E8; color: #D97B2B; }
.chip-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.toolbar-right { display: flex; gap: 6px; flex-shrink: 0; }
.tool-btn {
  padding: 5px 12px; border-radius: 6px; border: 1px solid #E8DDD0;
  background: #FFF; font-size: 12px; cursor: pointer;
  color: #6B5E52; transition: all 0.15s;
}
.tool-btn:hover { border-color: #D97B2B; color: #D97B2B; }
</style>
