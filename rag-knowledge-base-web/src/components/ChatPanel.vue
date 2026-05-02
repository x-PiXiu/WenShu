<template>
  <div class="chat-panel">
    <!-- Agent selector bar -->
    <div class="agent-bar" v-if="agents.length > 0">
      <div class="agent-bar-left">
        <span class="agent-bar-avatar">{{ currentAgent?.avatar || '🤖' }}</span>
        <span class="agent-bar-name">{{ currentAgent?.name || '选择智能体' }}</span>
      </div>
      <div class="agent-bar-right">
        <select class="agent-select" :value="currentAgent?.id || ''" @change="onAgentChange">
          <option v-for="a in agents" :key="a.id" :value="a.id">
            {{ a.avatar }} {{ a.name }}
          </option>
        </select>
        <button class="agent-action-btn" title="创建智能体" @click="startCreateAgent">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        </button>
        <button class="agent-action-btn" title="编辑当前智能体" @click="startEditAgent" :disabled="!currentAgent">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
        </button>
        <button class="agent-action-btn del" title="删除当前智能体" @click="handleDeleteAgent" :disabled="!currentAgent || currentAgent.isDefault">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </div>
    </div>

    <!-- Agent create/edit modal -->
    <div v-if="showAgentForm" class="modal-overlay" @click.self="showAgentForm = false">
      <div class="modal">
        <h3>{{ agentEditingId ? '编辑智能体' : '创建智能体' }}</h3>
        <div class="form-group">
          <label>头像</label>
          <input v-model="agentForm.avatar" placeholder="输入 emoji" class="avatar-input" />
        </div>
        <div class="form-group">
          <label>名称</label>
          <input v-model="agentForm.name" placeholder="智能体名称" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <input v-model="agentForm.description" placeholder="简短描述" />
        </div>
        <div class="form-group">
          <label>系统提示词</label>
          <textarea v-model="agentForm.systemPrompt" rows="8" placeholder="定义智能体的角色、行为和回答风格..." />
        </div>
        <div class="modal-actions">
          <button class="cancel-btn" @click="showAgentForm = false">取消</button>
          <button class="save-btn" @click="handleSaveAgent">保存</button>
        </div>
      </div>
    </div>
    <div class="chat-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#C8B8A8" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
        </div>
        <p class="empty-text">向藏书阁提问，探索知识的深渊</p>
        <p class="empty-hint">在下方输入您的问题，按 Enter 发送</p>
      </div>
      <div
        v-for="(msg, i) in messages"
        :key="i"
        :class="['message-row', msg.role]"
      >
        <div :class="['msg-avatar', msg.role]">
          <span>{{ msg.role === 'user' ? 'U' : 'AI' }}</span>
        </div>
        <div :class="['msg-bubble', msg.role]">
          <div v-if="msg.loading && !msg.thinking && !msg.content" class="typing">
            <span v-if="msg.status" class="retrieval-status">{{ msg.status.message }}</span>
            <span v-else class="typing-dots"><span></span><span></span><span></span></span>
          </div>
          <template v-else>
            <!-- Thinking block -->
            <div
              v-if="msg.thinking"
              class="thinking-block"
              :class="{ collapsed: !msg.thinkingExpanded }"
            >
              <div class="thinking-header" @click="toggleThinking(msg)">
                <svg
                  class="thinking-chevron"
                  :class="{ rotated: msg.thinkingExpanded }"
                  width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                >
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
                <span class="thinking-label">
                  {{ msg.isThinking ? '思考中...' : '思考过程' }}
                </span>
                <span v-if="msg.isThinking" class="thinking-dots">
                  <span></span><span></span><span></span>
                </span>
                <span v-else class="thinking-toggle-hint">（点击展开）</span>
              </div>
              <div class="thinking-content">
                <MarkdownRenderer :content="msg.thinking" />
              </div>
            </div>
            <!-- Loading inside thinking (no content yet) -->
            <div v-if="msg.isThinking && !msg.thinking" class="typing">
              <span></span><span></span><span></span>
            </div>
            <!-- Answer content -->
            <div v-if="msg.content">
              <MarkdownRenderer :content="msg.content" />
            </div>
          </template>
          <!-- Streaming: lightweight retrieval count -->
          <div v-if="msg.loading && msg.sourceCount" class="retrieval-hint">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <span>检索到 {{ msg.sourceCount }} 篇文章</span>
          </div>
          <!-- Completed: detailed expandable sources -->
          <div v-if="!msg.loading && msg.sources && msg.sources.length > 0" class="sources">
            <n-collapse>
              <n-collapse-item title="参考来源" name="sources">
                <div v-for="s in msg.sources" :key="s.index" class="source-item">
                  <div class="source-header">
                    <n-tag size="small" round :bordered="false" type="warning">{{ s.index }}</n-tag>
                    <span class="source-name">{{ s.source }}</span>
                    <n-tag
                      size="small"
                      :type="s.confidenceLabel === 'HIGH' ? 'success' : s.confidenceLabel === 'MEDIUM' ? 'warning' : 'default'"
                      :bordered="false"
                      round
                    >{{ s.confidenceLabel }}</n-tag>
                    <span class="source-scores">RRF {{ s.rrfScore.toFixed(3) }} &middot; Vec {{ s.vectorScore.toFixed(3) }}</span>
                  </div>
                  <div v-if="s.explanation" class="source-explanation">{{ s.explanation }}</div>
                  <div v-if="s.breadcrumb" class="source-breadcrumb">{{ s.breadcrumb }}</div>
                  <div class="source-text">{{ s.text }}</div>
                </div>
              </n-collapse-item>
            </n-collapse>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <div class="input-wrapper">
        <textarea
          ref="textareaRef"
          v-model="inputText"
          placeholder="输入您的问题..."
          rows="1"
          @keydown.enter.exact.prevent="handleSend"
          @input="autoResize"
        />
        <button v-if="loading" class="stop-btn" @click="stopGeneration" title="停止生成">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>
        </button>
        <button class="send-btn" :disabled="!inputText.trim() || loading" @click="handleSend">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onMounted } from 'vue'
import { NCollapse, NCollapseItem, NTag } from 'naive-ui'
import MarkdownRenderer from './MarkdownRenderer.vue'
import { useChat } from '../composables/useChat'
import { useAgents } from '../composables/useAgents'
import type { ChatMessage } from '../types/chat'

const inputText = ref('')
const messagesRef = ref<HTMLElement>()
const textareaRef = ref<HTMLTextAreaElement>()

const { messages, loading, sendQuestion, stopGeneration } = useChat()
const { agents, currentAgent, loadAgents, selectAgent, createAgent, updateAgent, deleteAgent } = useAgents()

// Agent form state
const showAgentForm = ref(false)
const agentEditingId = ref<string | null>(null)
const agentForm = ref({ name: '', description: '', systemPrompt: '', avatar: '🤖' })

onMounted(() => loadAgents())

function onAgentChange(e: Event) {
  const id = (e.target as HTMLSelectElement).value
  const agent = agents.value.find(a => a.id === id)
  if (agent) selectAgent(agent)
}

function startCreateAgent() {
  agentEditingId.value = null
  agentForm.value = { name: '', description: '', systemPrompt: '', avatar: '🤖' }
  showAgentForm.value = true
}

function startEditAgent() {
  if (!currentAgent.value) return
  agentEditingId.value = currentAgent.value.id
  agentForm.value = {
    name: currentAgent.value.name,
    description: currentAgent.value.description,
    systemPrompt: currentAgent.value.systemPrompt,
    avatar: currentAgent.value.avatar,
  }
  showAgentForm.value = true
}

async function handleSaveAgent() {
  if (!agentForm.value.name.trim() || !agentForm.value.systemPrompt.trim()) return
  if (agentEditingId.value) {
    await updateAgent(agentEditingId.value, agentForm.value)
  } else {
    await createAgent(agentForm.value)
  }
  showAgentForm.value = false
}

async function handleDeleteAgent() {
  if (!currentAgent.value || currentAgent.value.isDefault) return
  await deleteAgent(currentAgent.value.id)
}

async function handleSend() {
  if (loading.value) return
  const q = inputText.value.trim()
  if (!q) return
  inputText.value = ''
  if (textareaRef.value) textareaRef.value.style.height = 'auto'
  await sendQuestion(q)
}

function toggleThinking(msg: ChatMessage) {
  msg.thinkingExpanded = !msg.thinkingExpanded
}

function autoResize() {
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
      textareaRef.value.style.height = Math.min(textareaRef.value.scrollHeight, 120) + 'px'
    }
  })
}

watch(messages, () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}, { deep: true })
</script>

<style scoped>
.agent-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px; background: #FFFFFF;
  border-bottom: 1px solid #E8DDD0; flex-shrink: 0;
}
.agent-bar-left { display: flex; align-items: center; gap: 8px; }
.agent-bar-right { display: flex; align-items: center; gap: 6px; }
.agent-bar-avatar { font-size: 18px; }
.agent-bar-name { font-size: 13px; font-weight: 600; color: #3D3028; }
.agent-select {
  padding: 4px 10px; border: 1px solid #E8DDD0; border-radius: 6px;
  font-size: 12px; color: #6B5E52; background: #FAF7F2;
  cursor: pointer; outline: none;
}
.agent-select:focus { border-color: #D97B2B; }
.agent-action-btn {
  width: 28px; height: 28px; border-radius: 6px;
  border: 1px solid #E8DDD0; background: #FFFFFF;
  color: #8B7E74; cursor: pointer; display: flex;
  align-items: center; justify-content: center;
  transition: all 0.15s; flex-shrink: 0;
}
.agent-action-btn:hover:not(:disabled) { border-color: #D97B2B; color: #D97B2B; }
.agent-action-btn.del:hover:not(:disabled) { border-color: #E85D5D; color: #E85D5D; }
.agent-action-btn:disabled { opacity: 0.3; cursor: not-allowed; }

/* Modal */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.3); display: flex; align-items: center;
  justify-content: center; z-index: 1000;
}
.modal {
  background: #FFFFFF; border-radius: 14px; padding: 24px;
  width: 520px; max-width: 90vw; max-height: 85vh; overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0,0,0,0.12);
}
.modal h3 { margin: 0 0 20px; font-size: 17px; color: #3D3028; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 500; color: #6B5E52; margin-bottom: 6px; }
.form-group input, .form-group textarea {
  width: 100%; padding: 8px 12px; border: 1px solid #E8DDD0;
  border-radius: 8px; font-size: 13px; color: #3D3028;
  font-family: inherit; transition: border-color 0.2s;
  box-sizing: border-box;
}
.form-group input:focus, .form-group textarea:focus { outline: none; border-color: #D97B2B; }
.form-group textarea { resize: vertical; line-height: 1.5; }
.avatar-input { width: 80px !important; text-align: center; font-size: 20px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.cancel-btn {
  padding: 8px 20px; border-radius: 8px; border: 1px solid #E8DDD0;
  background: #FFFFFF; font-size: 13px; cursor: pointer;
}
.save-btn {
  padding: 8px 20px; border-radius: 8px; border: none;
  background: #D97B2B; color: #FFFFFF; font-size: 13px;
  font-weight: 500; cursor: pointer; transition: background 0.2s;
}
.save-btn:hover { background: #C06A1E; }
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #FAF7F2;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}
.empty-state {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  height: 100%; text-align: center;
}
.empty-icon { margin-bottom: 16px; }
.empty-text { font-size: 16px; color: #8B7E74; margin: 0 0 4px; }
.empty-hint { font-size: 13px; color: #B8A898; margin: 0; }
.message-row {
  display: flex; gap: 12px;
  margin-bottom: 20px;
  align-items: flex-start;
}
.message-row.user { flex-direction: row-reverse; }
.msg-avatar {
  width: 34px; height: 34px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; flex-shrink: 0;
}
.msg-avatar.user {
  background: linear-gradient(135deg, #E8913A, #D4644A);
  color: white;
}
.msg-avatar.assistant {
  background: #E8DDD0;
  color: #6B5E52;
}
.msg-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
}
.msg-bubble.user {
  background: linear-gradient(135deg, #E8913A, #D97B2B);
  color: white;
  border-bottom-right-radius: 4px;
}
.msg-bubble.assistant {
  background: #FFFFFF;
  color: #3D3028;
  border: 1px solid #E8DDD0;
  border-bottom-left-radius: 4px;
}

/* Thinking Block */
.thinking-block {
  margin-bottom: 10px;
  border: 1px solid #E8DDD0;
  border-radius: 10px;
  overflow: hidden;
  background: #FAF7F2;
}
.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  color: #8B7E74;
  transition: background 0.15s;
}
.thinking-header:hover {
  background: #F5F0EB;
}
.thinking-chevron {
  transition: transform 0.25s ease;
  flex-shrink: 0;
}
.thinking-chevron.rotated {
  transform: rotate(180deg);
}
.thinking-label {
  font-weight: 500;
  color: #A89888;
}
.thinking-toggle-hint {
  font-size: 11px;
  color: #C8B8A8;
  margin-left: 2px;
}
.thinking-dots {
  display: inline-flex;
  gap: 3px;
  margin-left: 4px;
}
.thinking-dots span {
  width: 4px; height: 4px; border-radius: 50%;
  background: #D4C8BA;
  animation: dotBounce 1.4s infinite both;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dotBounce {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.1); }
}
.thinking-content {
  max-height: 300px;
  opacity: 1;
  padding: 4px 12px 10px;
  border-top: 1px solid #E8DDD0;
  font-size: 13px;
  color: #8B7E74;
  overflow-y: auto;
  transition: max-height 0.35s ease, opacity 0.25s ease, padding 0.35s ease;
}
.thinking-block.collapsed .thinking-content {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
  border-top-color: transparent;
  overflow: hidden;
}

.sources { margin-top: 8px; font-size: 12px; min-width: 0; }
.source-item {
  padding: 8px 0;
  border-bottom: 1px solid #F0E8DE;
  min-width: 0;
}
.source-item:last-child { border-bottom: none; }
.source-explanation { color: #8B7355; font-size: 11px; margin-bottom: 4px; }
.source-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.source-name {
  font-size: 11px;
  color: #A89888;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}
.source-scores {
  font-size: 10px;
  color: #C8B8A8;
  white-space: nowrap;
  flex-shrink: 0;
}
.source-breadcrumb {
  font-size: 11px;
  color: #A89888;
  padding: 2px 0;
  letter-spacing: 0.3px;
}
.source-text {
  color: #6B5E52;
  font-size: 12px;
  line-height: 1.55;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  word-break: break-all;
}
.typing { display: flex; gap: 5px; padding: 6px 0; align-items: center; }
.typing-dots span {
  width: 8px; height: 8px; border-radius: 50%;
  background: #D4C8BA;
  animation: bounce 1.4s infinite both;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
.retrieval-status {
  color: #8B7E74;
  font-size: 13px;
  animation: pulse-opacity 1.5s ease-in-out infinite;
}
.retrieval-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 4px 8px;
  background: #FAF7F2;
  border-radius: 6px;
  font-size: 12px;
  color: #8B7E74;
}
@keyframes pulse-opacity {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
@keyframes bounce {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.9); }
  40% { opacity: 1; transform: scale(1.1); }
}
.chat-input-area {
  padding: 16px 32px 20px;
  border-top: 1px solid #E8DDD0;
  background: #FFFFFF;
}
.input-wrapper {
  display: flex; align-items: flex-end; gap: 10px;
  background: #FAF7F2;
  border: 1px solid #E8DDD0;
  border-radius: 14px;
  padding: 10px 14px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within { border-color: #E8913A; }
textarea {
  flex: 1; border: none; outline: none;
  background: transparent;
  font-family: inherit; font-size: 14px; color: #3D3028;
  resize: none; line-height: 1.5;
  max-height: 120px;
}
textarea::placeholder { color: #B8A898; }
.stop-btn {
  width: 36px; height: 36px; border-radius: 50%; border: none; cursor: pointer;
  background: #E74C3C; color: #fff; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: all 0.2s;
}
.stop-btn:hover { transform: scale(1.05); background: #C0392B; }

.send-btn {
  width: 36px; height: 36px; border-radius: 10px;
  border: none; cursor: pointer;
  background: linear-gradient(135deg, #E8913A, #D97B2B);
  color: white;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;
}
.send-btn:hover:not(:disabled) { transform: scale(1.05); box-shadow: 0 2px 8px rgba(217,123,43,0.3); }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
