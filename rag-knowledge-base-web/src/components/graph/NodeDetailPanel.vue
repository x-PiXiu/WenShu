<template>
  <div class="node-detail">
    <div class="detail-header">
      <span class="node-type-badge" :style="{ background: typeColor }">{{ node.nodeType }}</span>
      <h4>{{ node.label }}</h4>
      <button class="close-btn" @click="$emit('close')">&times;</button>
    </div>
    <p v-if="node.description" class="node-desc">{{ node.description }}</p>
    <p v-if="node.groupName" class="node-group">分组: {{ node.groupName }}</p>

    <div class="ask-section">
      <div class="ask-input-row">
        <input
          v-model="question"
          placeholder="针对此节点提问..."
          @keyup.enter="handleAsk"
          :disabled="asking"
        />
        <button class="ask-btn" @click="handleAsk" :disabled="!question.trim() || asking">
          {{ asking ? '...' : '提问' }}
        </button>
      </div>
      <div v-if="answer" class="answer-box">
        <p>{{ answer }}</p>
      </div>
    </div>

    <div class="suggest-section">
      <p class="suggest-hint">选择另一个节点推荐关系:</p>
      <div class="node-chips">
        <button
          v-for="n in otherNodes" :key="n.id"
          :class="['node-chip', { selected: targetNodeId === n.id }]"
          @click="targetNodeId = targetNodeId === n.id ? '' : n.id"
        >
          {{ n.label }}
        </button>
      </div>
      <button
        class="suggest-btn"
        @click="handleSuggest"
        :disabled="!targetNodeId || suggesting"
      >
        {{ suggesting ? '推理中...' : '推荐关系' }}
      </button>
      <div v-if="suggestions.length > 0" class="suggest-results">
        <div v-for="(s, i) in suggestions" :key="i" class="suggest-item">
          <strong>{{ s.label }}</strong>
          <span class="suggest-type">{{ s.type }}</span>
          <p class="suggest-reason">{{ s.reason }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { GraphNode, GraphSuggestion } from '../../types/graph'
import { useGraph } from '../../composables/useGraph'

const props = defineProps<{
  node: GraphNode
  graphId: string
  allNodes?: GraphNode[]
}>()

const emit = defineEmits<{
  close: []
  ask: [question: string]
  suggest: [targetId: string]
}>()

const { askQuestion, suggestRelations } = useGraph()

const question = ref('')
const answer = ref('')
const asking = ref(false)
const targetNodeId = ref('')
const suggesting = ref(false)
const suggestions = ref<GraphSuggestion[]>([])

const TYPE_COLORS: Record<string, string> = {
  concept: '#D97B2B', person: '#E85D5D', org: '#2196F3',
  technology: '#4CAF50', event: '#9C27B0', location: '#00BCD4', other: '#8B7E74',
}
const typeColor = TYPE_COLORS[props.node.nodeType] || TYPE_COLORS.other

const otherNodes = props.allNodes || []

async function handleAsk() {
  if (!question.value.trim()) return
  asking.value = true
  answer.value = ''
  const contextQ = `关于节点「${props.node.label}」(${props.node.nodeType}): ${question.value}`
  const result = await askQuestion(props.graphId, contextQ)
  answer.value = result || '无法获取回答'
  asking.value = false
}

async function handleSuggest() {
  if (!targetNodeId.value) return
  suggesting.value = true
  suggestions.value = []
  const result = await suggestRelations(props.graphId, props.node.id, targetNodeId.value)
  suggestions.value = result || []
  suggesting.value = false
}
</script>

<style scoped>
.node-detail {
  position: absolute; right: 12px; top: 60px; bottom: 12px; width: 280px;
  background: rgba(255,255,255,0.95); border-radius: 12px;
  border: 1px solid #E8DDD0; padding: 16px; overflow-y: auto;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08); z-index: 10;
  backdrop-filter: blur(4px);
}
.detail-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.detail-header h4 { margin: 0; font-size: 15px; color: #3D3028; flex: 1; }
.node-type-badge {
  font-size: 10px; padding: 2px 8px; border-radius: 10px;
  color: #FFF; font-weight: 500; flex-shrink: 0;
}
.close-btn {
  background: none; border: none; font-size: 18px; color: #8B7E74;
  cursor: pointer; padding: 0; line-height: 1;
}
.close-btn:hover { color: #3D3028; }
.node-desc { font-size: 13px; color: #6B5E52; margin: 0 0 8px; line-height: 1.5; }
.node-group { font-size: 12px; color: #8B7E74; margin: 0 0 12px; }

.ask-section { margin-bottom: 16px; padding-top: 12px; border-top: 1px solid #E8DDD0; }
.ask-input-row { display: flex; gap: 6px; }
.ask-input-row input {
  flex: 1; padding: 6px 10px; border: 1px solid #E8DDD0;
  border-radius: 6px; font-size: 12px; color: #3D3028; font-family: inherit;
}
.ask-input-row input:focus { outline: none; border-color: #D97B2B; }
.ask-btn {
  padding: 6px 12px; border-radius: 6px; border: none;
  background: #D97B2B; color: #FFF; font-size: 12px;
  cursor: pointer; flex-shrink: 0;
}
.ask-btn:disabled { opacity: 0.5; }
.answer-box {
  margin-top: 8px; padding: 10px; background: #FAF7F2;
  border-radius: 8px; font-size: 12px; color: #3D3028;
  line-height: 1.6;
}
.answer-box p { margin: 0; }

.suggest-section { padding-top: 12px; border-top: 1px solid #E8DDD0; }
.suggest-hint { font-size: 12px; color: #8B7E74; margin: 0 0 8px; }
.node-chips { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; max-height: 120px; overflow-y: auto; }
.node-chip {
  padding: 3px 8px; border-radius: 6px; border: 1px solid #E8DDD0;
  background: #FFF; font-size: 11px; cursor: pointer;
  color: #6B5E52; transition: all 0.15s;
}
.node-chip.selected { border-color: #D97B2B; background: #FEF3E8; color: #D97B2B; }
.suggest-btn {
  width: 100%; padding: 6px; border-radius: 6px; border: 1px solid #D97B2B;
  background: #FFF; color: #D97B2B; font-size: 12px; cursor: pointer;
  transition: all 0.2s;
}
.suggest-btn:hover { background: #FEF3E8; }
.suggest-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.suggest-results { margin-top: 8px; }
.suggest-item {
  padding: 8px; background: #FAF7F2; border-radius: 6px;
  margin-bottom: 6px; font-size: 12px;
}
.suggest-item strong { color: #3D3028; }
.suggest-type {
  font-size: 10px; padding: 1px 6px; border-radius: 8px;
  background: #FEF3E8; color: #D97B2B; margin-left: 4px;
}
.suggest-reason { color: #8B7E74; margin: 4px 0 0; font-size: 11px; }
</style>
