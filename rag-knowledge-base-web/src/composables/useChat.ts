import { ref } from 'vue'
import type { ChatMessage, Conversation, RagAnswer, SourceInfo } from '../types/chat'
import { useAgents } from './useAgents'

// Module-level state: shared across all components that call useChat()
const messages = ref<ChatMessage[]>([])
const loading = ref(false)
const conversations = ref<Conversation[]>([])
const currentConversationId = ref<string | null>(null)
const streamingEnabled = ref(true)
let streamGeneration = 0
let activeAbortController: AbortController | null = null

const { currentAgent } = useAgents()

export function useChat() {

  async function loadConversations() {
    try {
      const res = await fetch('/api/conversations')
      if (res.ok) {
        conversations.value = await res.json()
      }
    } catch {
      conversations.value = []
    }
  }

  async function loadStreamingConfig() {
    try {
      const res = await fetch('/api/settings')
      if (res.ok) {
        const settings = await res.json()
        streamingEnabled.value = settings.llm?.streaming ?? true
      }
    } catch { /* ignore */ }
  }

  async function createConversation() {
    try {
      const agentId = currentAgent.value?.id || null
      const res = await fetch('/api/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ agentId }),
      })
      if (res.ok) {
        const conv: Conversation = await res.json()
        conversations.value.unshift(conv)
        await switchConversation(conv.id)
      }
    } catch { /* ignore */ }
  }

  async function switchConversation(id: string) {
    currentConversationId.value = id
    messages.value = []
    try {
      const res = await fetch(`/api/conversations/${id}`)
      if (res.ok) {
        const data = await res.json()
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        messages.value = data.messages.map((m: any) => {
          const msg: ChatMessage = {
            role: m.role as 'user' | 'assistant',
            content: m.content,
            sources: m.sources || [],
            timestamp: m.createdAt,
          }
          // Parse <think/> tags from persisted content for assistant messages
          if (m.role === 'assistant' && m.content) {
            updateThinkingState(msg, m.content)
            // Auto-collapse completed thinking blocks for history view
            if (msg.thinking) {
              msg.thinkingExpanded = false
            }
          }
          return msg
        })
      }
    } catch { /* ignore */ }
  }

  async function deleteConversation(id: string) {
    try {
      const res = await fetch(`/api/conversations/${id}`, { method: 'DELETE' })
      if (res.ok) {
        conversations.value = conversations.value.filter(c => c.id !== id)
        if (currentConversationId.value === id) {
          currentConversationId.value = null
          messages.value = []
        }
      }
    } catch { /* ignore */ }
  }

  async function sendQuestion(question: string): Promise<void> {
    if (!question.trim() || loading.value) return

    // Ensure streaming config is loaded
    if (streamingEnabled.value === null) {
      await loadStreamingConfig()
    }

    messages.value.push({
      role: 'user',
      content: question,
      timestamp: Date.now(),
    })

    // Track insertion index to get the reactive proxy back
    const msgIdx = messages.value.length
    messages.value.push({
      role: 'assistant',
      content: '',
      sources: [],
      timestamp: Date.now(),
      loading: true,
      thinking: undefined,
      isThinking: false,
      thinkingExpanded: true,
    })
    // Use reactive proxy from the array so Vue tracks all property mutations
    const assistantMsg = messages.value[msgIdx] as ChatMessage
    const gen = ++streamGeneration
    loading.value = true

    try {
      if (streamingEnabled.value) {
        await sendQuestionStream(question, assistantMsg, gen)
      } else {
        await sendQuestionSync(question, assistantMsg)
      }
    } catch (e: any) {
      assistantMsg.content = `Error: ${e.message}`
      assistantMsg.loading = false
      assistantMsg.isThinking = false
    } finally {
      if (streamGeneration === gen) {
        loading.value = false
      }
    }
  }

  // ===== Non-streaming fallback =====

  async function sendQuestionSync(question: string, assistantMsg: ChatMessage): Promise<void> {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question,
        conversationId: currentConversationId.value || undefined,
        agentId: currentAgent.value?.id || undefined,
      }),
    })

    if (!res.ok) {
      const err = await res.json()
      throw new Error(err.error || `HTTP ${res.status}`)
    }

    const data: RagAnswer = await res.json()
    assistantMsg.content = data.answer
    assistantMsg.sources = data.sources
    assistantMsg.loading = false

    if (!currentConversationId.value && data.conversationId) {
      currentConversationId.value = data.conversationId
      await loadConversations()
    }
  }

  // ===== SSE Streaming =====

  async function sendQuestionStream(question: string, assistantMsg: ChatMessage, gen: number): Promise<void> {
    const ctrl = new AbortController()
    activeAbortController = ctrl

    const res = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question,
        conversationId: currentConversationId.value || undefined,
        agentId: currentAgent.value?.id || undefined,
      }),
      signal: ctrl.signal,
    })

    if (!res.ok) {
      // Fallback to sync if streaming fails
      return sendQuestionSync(question, assistantMsg)
    }

    const reader = res.body!.getReader()
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
        if (eventType === 'meta') {
          const meta = JSON.parse(eventData)
          if (meta.conversationId && !currentConversationId.value) {
            currentConversationId.value = meta.conversationId
          }
        } else if (eventType === 'status') {
          const status = JSON.parse(eventData)
          assistantMsg.status = { phase: status.phase, message: status.message, segments: status.segments }
        } else if (eventType === 'sources') {
          const sources = JSON.parse(eventData) as SourceInfo[]
          // Always save sources data (fallback if done event is missed)
          assistantMsg.sources = sources
          assistantMsg.sourceCount = sources.length
          if (streamGeneration === gen) {
            loading.value = false
          }
        } else if (eventType === 'token') {
          const { t } = JSON.parse(eventData)
          fullText += t
          assistantMsg.status = undefined
          updateThinkingState(assistantMsg, fullText)
        } else if (eventType === 'done') {
          const doneData = JSON.parse(eventData)
          fullText = doneData.answer || fullText
          updateThinkingState(assistantMsg, fullText)
          if (doneData.sources && Array.isArray(doneData.sources) && doneData.sources.length > 0) {
            assistantMsg.sources = doneData.sources
          }
          assistantMsg.loading = false
          assistantMsg.isThinking = false
          assistantMsg.status = undefined
          assistantMsg.sourceCount = undefined
          if (streamGeneration === gen) {
            loading.value = false
          }

          if (doneData.conversationId && !currentConversationId.value) {
            currentConversationId.value = doneData.conversationId
          }
          loadConversations()
        } else if (eventType === 'error') {
          const errData = JSON.parse(eventData)
          assistantMsg.content = `Error: ${errData.error}`
          assistantMsg.loading = false
          assistantMsg.isThinking = false
          if (streamGeneration === gen) {
            loading.value = false
          }
        }
      } catch {
        // Skip malformed JSON
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      sseBuffer += decoder.decode(value, { stream: true })

      // Parse SSE events from buffer
      const parts = sseBuffer.split('\n\n')
      sseBuffer = parts.pop() || ''

      for (const part of parts) {
        handleSSEPart(part)
      }
    }

    // Process any remaining SSE events buffered after stream closes.
    // This handles the case where the last "done" event was split across
    // TCP chunks and the trailing \n\n never arrived before connection close.
    if (sseBuffer.trim()) {
      for (const part of sseBuffer.split('\n\n')) {
        if (part.trim()) handleSSEPart(part)
      }
    }

    // Final cleanup if done event was missed
    if (assistantMsg.loading) {
      assistantMsg.loading = false
      assistantMsg.isThinking = false
    }
    activeAbortController = null
  }

  /**
   * Parse the accumulated text for <think/> blocks.
   * When <think is found: extract thinking content, show it expanded.
   * When </think is found: auto-collapse thinking, show the actual answer.
   */
  function updateThinkingState(msg: ChatMessage, fullText: string) {
    const openIdx = fullText.indexOf('<think')
    if (openIdx === -1) {
      // No thinking tag: content is the answer directly
      msg.thinking = undefined
      msg.isThinking = false
      msg.content = fullText
      return
    }

    // Find the end of the opening tag (e.g., <think...>)
    const tagEnd = fullText.indexOf('>', openIdx)
    if (tagEnd === -1) {
      // Opening tag is incomplete (streaming in progress)
      msg.isThinking = true
      msg.content = ''
      msg.thinking = ''
      return
    }

    // Find closing tag
    const closeTag = '</think'
    const closeIdx = fullText.indexOf(closeTag, tagEnd)

    if (closeIdx === -1) {
      // Still thinking
      msg.isThinking = true
      msg.thinking = fullText.substring(tagEnd + 1)
      msg.content = ''
    } else {
      // Thinking complete — auto-collapse on first detection
      msg.isThinking = false
      const thinkingText = fullText.substring(tagEnd + 1, closeIdx).trim()
      msg.thinking = thinkingText
      if (msg.thinkingExpanded !== false) {
        msg.thinkingExpanded = false
      }
      const afterClose = fullText.substring(closeIdx + closeTag.length)
      const closeGt = afterClose.indexOf('>')
      msg.content = closeGt !== -1 ? afterClose.substring(closeGt + 1).trim() : ''
    }
  }

  function clearMessages() {
    messages.value = []
    currentConversationId.value = null
  }

  async function stopGeneration() {
    if (!loading.value) return
    // Abort the fetch connection
    if (activeAbortController) {
      activeAbortController.abort()
      activeAbortController = null
    }
    // Notify server to stop streaming
    if (currentConversationId.value) {
      try {
        await fetch('/api/chat/cancel', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ conversationId: currentConversationId.value }),
        })
      } catch { /* ignore */ }
    }
    // Mark the last assistant message as done
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.role === 'assistant' && lastMsg.loading) {
      lastMsg.loading = false
      lastMsg.isThinking = false
      lastMsg.content = lastMsg.content || lastMsg.thinking || '(已停止生成)'
    }
    loading.value = false
  }

  return {
    messages, loading, conversations, currentConversationId, streamingEnabled,
    sendQuestion, stopGeneration, clearMessages,
    loadConversations, loadStreamingConfig, createConversation, switchConversation, deleteConversation,
  }
}
