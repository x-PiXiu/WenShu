<template>
  <!-- Floating Button -->
  <button v-if="!isOpen" class="chat-fab" @click="isOpen = true" title="基于本文提问">
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
  </button>

  <!-- Chat Panel -->
  <div v-if="isOpen" class="chat-panel">
    <div class="chat-header">
      <span class="chat-title">基于本文提问</span>
      <button class="chat-close" @click="isOpen = false">&times;</button>
    </div>
    <div class="chat-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="chat-empty">
        基于当前文章内容进行智能问答
      </div>
      <div v-for="(msg, i) in messages" :key="i" :class="['chat-msg', msg.role]">
        <div class="msg-content">{{ msg.content }}</div>
      </div>
      <div v-if="loading" class="chat-msg assistant">
        <div class="msg-content typing">思考中...</div>
      </div>
    </div>
    <div class="chat-input-area">
      <input type="text" v-model="input" class="chat-input" placeholder="输入问题..."
             @keydown.enter="send" :disabled="loading" />
      <button class="chat-send" @click="send" :disabled="loading || !input.trim()">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

const props = defineProps<{ slug: string }>()

const isOpen = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref<{ role: 'user' | 'assistant'; content: string }[]>([])
const messagesRef = ref<HTMLElement | null>(null)

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

async function send() {
  const q = input.value.trim()
  if (!q || loading.value) return

  messages.value = [...messages.value, { role: 'user', content: q }]
  input.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const res = await fetch('/api/blog/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: q }),
    })

    if (!res.ok || !res.body) {
      const err = await res.text()
      messages.value = [...messages.value, { role: 'assistant', content: '请求失败: ' + err }]
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let answer = ''
    let buffer = ''

    messages.value = [...messages.value, { role: 'assistant', content: '' }]

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          try {
            const data = JSON.parse(line.slice(6))
            if (data.t) {
              answer += data.t
              messages.value = [...messages.value.slice(0, -1), { role: 'assistant', content: answer }]
              await scrollToBottom()
            }
          } catch { /* ignore parse errors */ }
        }
      }
    }
  } catch {
    messages.value = [...messages.value, { role: 'assistant', content: '网络错误，请重试' }]
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}
</script>

<style scoped>
.chat-fab {
  position: fixed; bottom: 28px; right: 28px;
  width: 52px; height: 52px; border-radius: 50%;
  background: #D97B2B; color: #FFFFFF; border: none;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 16px rgba(217,123,43,0.35);
  transition: all 0.3s; z-index: 100;
}
.chat-fab:hover { background: #C06A1E; transform: scale(1.05); }

.chat-panel {
  position: fixed; bottom: 28px; right: 28px;
  width: 380px; height: 500px;
  background: #FFFFFF; border: 1px solid #E8DDD0;
  border-radius: 16px; display: flex; flex-direction: column;
  box-shadow: 0 8px 32px rgba(61,48,40,0.15);
  z-index: 100; overflow: hidden;
}

.chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; border-bottom: 1px solid #E8DDD0;
  background: #FAF7F2;
}
.chat-title { font-size: 14px; font-weight: 600; color: #3D3028; }
.chat-close {
  border: none; background: none; font-size: 20px; color: #8B7E74;
  cursor: pointer; line-height: 1; padding: 0;
}
.chat-close:hover { color: #E85D5D; }

.chat-messages {
  flex: 1; overflow-y: auto; padding: 14px 16px;
  display: flex; flex-direction: column; gap: 10px;
}
.chat-empty {
  text-align: center; color: #B8A898; font-size: 13px;
  padding: 40px 10px; line-height: 1.6;
}
.chat-msg { max-width: 88%; }
.chat-msg.user { align-self: flex-end; }
.chat-msg.assistant { align-self: flex-start; }
.msg-content {
  padding: 8px 14px; border-radius: 12px;
  font-size: 13px; line-height: 1.6; white-space: pre-wrap;
}
.chat-msg.user .msg-content {
  background: #D97B2B; color: #FFFFFF; border-bottom-right-radius: 4px;
}
.chat-msg.assistant .msg-content {
  background: #F5F0EB; color: #3D3028; border-bottom-left-radius: 4px;
}
.typing { color: #8B7E74; font-style: italic; }

.chat-input-area {
  display: flex; gap: 8px; padding: 12px 14px;
  border-top: 1px solid #E8DDD0;
}
.chat-input {
  flex: 1; border: 1px solid #E8DDD0; border-radius: 10px;
  padding: 8px 14px; font-size: 13px; color: #3D3028; outline: none;
}
.chat-input:focus { border-color: #D97B2B; }
.chat-input:disabled { opacity: 0.5; }
.chat-send {
  width: 36px; height: 36px; border-radius: 10px; border: none;
  background: #D97B2B; color: #FFFFFF; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.2s;
}
.chat-send:hover { background: #C06A1E; }
.chat-send:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
