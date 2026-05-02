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
        <template v-if="msg.role === 'assistant'">
          <!-- Thinking block -->
          <div v-if="msg.thinking" class="thinking-block" :class="{ collapsed: !msg.thinkingExpanded }">
            <div class="thinking-header" @click="msg.thinkingExpanded = !msg.thinkingExpanded">
              <svg class="thinking-chevron" :class="{ rotated: msg.thinkingExpanded }" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              <span class="thinking-label">{{ msg.isThinking ? '思考中...' : '思考过程' }}</span>
              <span v-if="msg.isThinking" class="thinking-dots"><span></span><span></span><span></span></span>
            </div>
            <div class="thinking-content">{{ msg.thinking }}</div>
          </div>
          <div v-if="msg.isThinking && !msg.thinking" class="thinking-dots"><span></span><span></span><span></span></div>
          <div v-if="msg.content" class="msg-content"><MarkdownRenderer :content="msg.content" /></div>
        </template>
        <div v-else class="msg-content">{{ msg.content }}</div>
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
import MarkdownRenderer from '../MarkdownRenderer.vue'

interface BlogMessage {
  role: 'user' | 'assistant'
  content: string
  thinking?: string
  isThinking?: boolean
  thinkingExpanded?: boolean
  status?: { phase: string; message: string; segments?: number }
}

const props = defineProps<{ slug: string }>()

const isOpen = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref<BlogMessage[]>([])
const messagesRef = ref<HTMLElement | null>(null)

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function updateThinkingState(msg: BlogMessage, fullText: string) {
  const openIdx = fullText.indexOf('<think')
  if (openIdx === -1) {
    msg.thinking = undefined
    msg.isThinking = false
    msg.content = fullText
    return
  }

  const tagEnd = fullText.indexOf('>', openIdx)
  if (tagEnd === -1) {
    msg.isThinking = true
    msg.content = ''
    msg.thinking = ''
    return
  }

  const closeTag = '</think'
  const closeIdx = fullText.indexOf(closeTag, tagEnd)
  if (closeIdx === -1) {
    msg.isThinking = true
    msg.thinking = fullText.substring(tagEnd + 1)
    msg.content = ''
  } else {
    msg.isThinking = false
    msg.thinking = fullText.substring(tagEnd + 1, closeIdx).trim()
    msg.thinkingExpanded = false
    const afterClose = fullText.substring(closeIdx + closeTag.length)
    const closeGt = afterClose.indexOf('>')
    msg.content = closeGt !== -1 ? afterClose.substring(closeGt + 1).trim() : ''
  }
}

async function send() {
  const q = input.value.trim()
  if (!q || loading.value) return

  messages.value = [...messages.value, { role: 'user', content: q }]
  input.value = ''
  loading.value = true
  await scrollToBottom()

  // Push empty assistant message and get the reactive proxy back from the array
  messages.value.push({
    role: 'assistant',
    content: '',
    thinking: undefined,
    isThinking: false,
    thinkingExpanded: true,
  })
  const assistantMsg = messages.value[messages.value.length - 1]

  try {
    const res = await fetch('/api/blog/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question: q,
        slug: props.slug,
        history: messages.value
          .filter(m => m.content)
          .slice(0, -1) // exclude the empty assistant message we just added
          .map(m => ({ role: m.role, content: m.content })),
      }),
    })

    if (!res.ok || !res.body) {
      assistantMsg.content = '请求失败，请重试'
      assistantMsg.isThinking = false
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let sseBuffer = ''
    let fullText = ''

    const handleSSEPart = (part: string) => {
      let eventType = 'message'
      let eventData = ''

      for (const line of part.split('\n')) {
        if (line.startsWith('event: ')) {
          eventType = line.substring(7)
        } else if (line.startsWith('data: ')) {
          eventData = line.substring(6)
        }
      }

      if (!eventData) return

      try {
        if (eventType === 'status') {
          const status = JSON.parse(eventData)
          assistantMsg.status = { phase: status.phase, message: status.message, segments: status.segments }
        } else if (eventType === 'token') {
          const { t } = JSON.parse(eventData)
          fullText += t
          assistantMsg.status = undefined
          updateThinkingState(assistantMsg, fullText)
        } else if (eventType === 'done') {
          const doneData = JSON.parse(eventData)
          fullText = doneData.answer || fullText
          updateThinkingState(assistantMsg, fullText)
          assistantMsg.isThinking = false
          assistantMsg.status = undefined
        } else if (eventType === 'error') {
          const errData = JSON.parse(eventData)
          assistantMsg.content = 'Error: ' + (errData.error || 'Unknown error')
          assistantMsg.isThinking = false
        }
      } catch { /* skip malformed JSON */ }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      sseBuffer += decoder.decode(value, { stream: true })

      const parts = sseBuffer.split('\n\n')
      sseBuffer = parts.pop() || ''

      for (const part of parts) {
        handleSSEPart(part)
      }
      await scrollToBottom()
    }

    // Handle remaining buffer after stream closes
    if (sseBuffer.trim()) {
      for (const part of sseBuffer.split('\n\n')) {
        if (part.trim()) handleSSEPart(part)
      }
    }

    if (assistantMsg.isThinking) {
      assistantMsg.isThinking = false
    }
  } catch {
    assistantMsg.content = '网络错误，请重试'
    assistantMsg.isThinking = false
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
  width: 400px; height: 520px;
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
  font-size: 13px; line-height: 1.6;
}
.chat-msg.user .msg-content {
  background: #D97B2B; color: #FFFFFF; border-bottom-right-radius: 4px;
}
.chat-msg.assistant .msg-content {
  background: #F5F0EB; color: #3D3028; border-bottom-left-radius: 4px;
}

/* Thinking Block */
.thinking-block {
  margin-bottom: 6px;
  border: 1px solid #E8DDD0;
  border-radius: 8px;
  overflow: hidden;
  background: #FAF7F2;
}
.thinking-header {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; cursor: pointer;
  font-size: 11px; color: #8B7E74;
}
.thinking-header:hover { background: #F5F0EB; }
.thinking-chevron { transition: transform 0.25s ease; flex-shrink: 0; }
.thinking-chevron.rotated { transform: rotate(180deg); }
.thinking-label { font-weight: 500; color: #A89888; }
.thinking-content {
  max-height: 150px; overflow-y: auto;
  padding: 4px 10px 8px; border-top: 1px solid #E8DDD0;
  font-size: 11px; color: #8B7E74; line-height: 1.5;
}
.thinking-block.collapsed .thinking-content {
  max-height: 0; padding: 0 10px; border-top-color: transparent; overflow: hidden;
}

.thinking-dots {
  display: inline-flex; gap: 3px; padding: 4px 0;
}
.thinking-dots span {
  width: 5px; height: 5px; border-radius: 50%;
  background: #D4C8BA;
  animation: dotBounce 1.4s infinite both;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dotBounce {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.1); }
}

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
