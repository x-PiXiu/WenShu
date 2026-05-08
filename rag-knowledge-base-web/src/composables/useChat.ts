import { ref } from 'vue'
import type { ChatMessage, Conversation, RagAnswer, SourceInfo, FileChangeInfo, MessageSegment } from '../types/chat'
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
            // Auto-collapse all thinking segments for history view
            if (msg.segments) {
              for (const seg of msg.segments) {
                if (seg.type === 'thinking') seg.expanded = false
              }
            }
            msg.thinkingExpanded = false
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
        } else if (eventType === 'file_diff') {
          const diff = JSON.parse(eventData)
          const change: FileChangeInfo = {
            changeId: diff.changeId,
            path: diff.path,
            tool: diff.tool,
            oldLines: diff.oldLines,
            newLines: diff.newLines,
            status: 'pending',
          }
          if (!assistantMsg.fileChanges) assistantMsg.fileChanges = []
          assistantMsg.fileChanges.push(change)
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
          // Collapse all thinking segments after stream completes
          if (assistantMsg.segments) {
            for (const seg of assistantMsg.segments) {
              if (seg.type === 'thinking') {
                seg.expanded = false
                seg.isActive = false
              }
            }
          }
          assistantMsg.thinkingExpanded = false
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
          // Update conversation list locally without triggering a full API reload.
          // loadConversations() causes reactive cascade that interrupts diff card UI.
          const convId = doneData.conversationId || currentConversationId.value
          if (convId) {
            const conv = conversations.value.find(c => c.id === convId)
            if (conv && conv.title === '新对话') {
              const userMsg = messages.value.find(m => m.role === 'user')
              if (userMsg) {
                conv.title = userMsg.content.length > 20
                      ? userMsg.content.substring(0, 20) + '...'
                      : userMsg.content
              }
            } else if (!conv) {
              // Insert stub for new conversation
              const userMsg = messages.value.find(m => m.role === 'user')
              conversations.value.unshift({
                id: convId,
                title: userMsg
                  ? (userMsg.content.length > 20
                      ? userMsg.content.substring(0, 20) + '...'
                      : userMsg.content)
                  : '新对话',
                agentId: null,
                createdAt: Date.now(),
                updatedAt: Date.now(),
              })
            }
          }
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
   * Parse <think/> blocks from accumulated streaming text into ordered segments.
   * Each thinking block becomes its own independent collapsible segment,
   * preserving the order they appear in the LLM output.
   *
   * Layout example:
   *   [Thinking 1 - collapsed] → Content 1 → [Thinking 2 - collapsed] → Content 2
   */
  function updateThinkingState(msg: ChatMessage, fullText: string) {
    const segments: MessageSegment[] = []
    let segIndex = 0
    let pos = 0

    // Regex to find all <think...>...</think...> blocks
    const thinkRegex = /<think[^>]*>([\s\S]*?)<\/think[^>]*>/g
    let match

    while ((match = thinkRegex.exec(fullText)) !== null) {
      // Content before this thinking block
      const before = fullText.substring(pos, match.index).replace(/\n{3,}/g, '\n\n').trim()
      if (before) {
        segments.push({ type: 'content', content: before, segIndex: segIndex++ })
      }

      // The thinking block itself
      const thinkingText = match[1].trim()
      if (thinkingText) {
        // Preserve expanded state from existing segment at same index
        const existing = msg.segments?.find(s => s.segIndex === segIndex)
        const expanded = existing?.type === 'thinking' ? existing.expanded : true
        segments.push({ type: 'thinking', content: thinkingText, expanded, isActive: false, segIndex: segIndex++ })
      }

      pos = match.index + match[0].length
    }

    // Check for incomplete think block at end (still streaming)
    const remaining = fullText.substring(pos)
    const lastOpen = remaining.lastIndexOf('<think')
    let isThinking = false

    if (lastOpen !== -1) {
      // Content before the incomplete think block
      const beforeIncomplete = remaining.substring(0, lastOpen).replace(/\n{3,}/g, '\n\n').trim()
      if (beforeIncomplete) {
        segments.push({ type: 'content', content: beforeIncomplete, segIndex: segIndex++ })
      }

      const partial = remaining.substring(lastOpen)
      const gtIdx = partial.indexOf('>')
      if (gtIdx !== -1) {
        const thinkingText = partial.substring(gtIdx + 1).trim()
        // Preserve expanded state or default to true (active block)
        const existing = msg.segments?.find(s => s.segIndex === segIndex)
        const expanded = existing?.type === 'thinking' ? existing.expanded : true
        segments.push({ type: 'thinking', content: thinkingText, expanded, isActive: true, segIndex: segIndex++ })
      }
      isThinking = true
    } else {
      // No incomplete think block — remaining is pure content
      const after = remaining.replace(/\n{3,}/g, '\n\n').trim()
      if (after) {
        segments.push({ type: 'content', content: after, segIndex: segIndex++ })
      }
    }

    msg.segments = segments
    msg.isThinking = isThinking

    // For backward compat (copy actions, etc.), also set legacy fields
    const allThinking = segments
      .filter((s): s is Extract<typeof s, { type: 'thinking' }> => s.type === 'thinking')
      .map(s => s.content)
      .filter(Boolean)
    msg.thinking = allThinking.length > 0 ? allThinking.join('\n\n') : undefined
    msg.content = segments
      .filter((s): s is Extract<typeof s, { type: 'content' }> => s.type === 'content')
      .map(s => s.content)
      .join('\n\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
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
      // Collapse all thinking segments
      if (lastMsg.segments) {
        for (const seg of lastMsg.segments) {
          if (seg.type === 'thinking') { seg.expanded = false; seg.isActive = false }
        }
      }
    }
    loading.value = false
  }

  return {
    messages, loading, conversations, currentConversationId, streamingEnabled,
    sendQuestion, stopGeneration, clearMessages,
    loadConversations, loadStreamingConfig, createConversation, switchConversation, deleteConversation,
  }
}
