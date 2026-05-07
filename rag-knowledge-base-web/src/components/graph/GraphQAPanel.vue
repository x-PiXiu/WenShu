<template>
  <div class="graph-qa">
    <div class="qa-header">
      <h4>图谱问答</h4>
      <button class="close-btn" @click="$emit('close')">&times;</button>
    </div>
    <div class="qa-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="qa-empty">
        针对当前知识图谱提问
      </div>
      <div v-for="(msg, i) in messages" :key="i" :class="['qa-msg', msg.role]">
        <div class="msg-content">{{ msg.content }}</div>
      </div>
    </div>
    <div class="qa-input-row">
      <input
        v-model="question"
        placeholder="输入问题..."
        @keyup.enter="handleAsk"
        :disabled="asking"
      />
      <button class="qa-btn" @click="handleAsk" :disabled="!question.trim() || asking">
        {{ asking ? '...' : '发送' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useGraph } from '../../composables/useGraph'

const props = defineProps<{
  graphId: string
}>()

const emit = defineEmits<{
  close: []
}>()

const { askQuestion } = useGraph()

const question = ref('')
const asking = ref(false)
const messages = ref<{ role: 'user' | 'assistant'; content: string }[]>([])
const messagesRef = ref<HTMLDivElement | null>(null)

async function handleAsk() {
  if (!question.value.trim() || asking.value) return
  const q = question.value.trim()
  question.value = ''
  messages.value.push({ role: 'user', content: q })
  asking.value = true
  await nextTick()
  scrollToBottom()

  const answer = await askQuestion(props.graphId, q)
  messages.value.push({ role: 'assistant', content: answer || '无法获取回答' })
  asking.value = false
  await nextTick()
  scrollToBottom()
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}
</script>

<style scoped>
.graph-qa {
  position: absolute; right: 12px; top: 60px; bottom: 12px; width: 320px;
  background: rgba(255,255,255,0.95); border-radius: 12px;
  border: 1px solid #E8DDD0; display: flex; flex-direction: column;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08); z-index: 10;
  backdrop-filter: blur(4px);
}
.qa-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid #E8DDD0;
}
.qa-header h4 { margin: 0; font-size: 14px; color: #3D3028; }
.close-btn {
  background: none; border: none; font-size: 18px; color: #8B7E74;
  cursor: pointer; padding: 0;
}
.close-btn:hover { color: #3D3028; }
.qa-messages { flex: 1; overflow-y: auto; padding: 12px 16px; }
.qa-empty { text-align: center; color: #B8A898; padding-top: 40px; font-size: 13px; }
.qa-msg { margin-bottom: 10px; }
.qa-msg.user .msg-content {
  background: #D97B2B; color: #FFF; padding: 8px 12px;
  border-radius: 10px 10px 2px 10px; display: inline-block;
  font-size: 13px; max-width: 90%;
}
.qa-msg.assistant .msg-content {
  background: #FAF7F2; color: #3D3028; padding: 8px 12px;
  border-radius: 10px 10px 10px 2px; display: inline-block;
  font-size: 13px; line-height: 1.6; max-width: 90%; white-space: pre-wrap;
}
.qa-input-row { display: flex; gap: 6px; padding: 12px 16px; border-top: 1px solid #E8DDD0; }
.qa-input-row input {
  flex: 1; padding: 8px 12px; border: 1px solid #E8DDD0;
  border-radius: 8px; font-size: 13px; color: #3D3028; font-family: inherit;
}
.qa-input-row input:focus { outline: none; border-color: #D97B2B; }
.qa-btn {
  padding: 8px 16px; border-radius: 8px; border: none;
  background: #D97B2B; color: #FFF; font-size: 13px;
  cursor: pointer; flex-shrink: 0;
}
.qa-btn:disabled { opacity: 0.5; }
</style>
