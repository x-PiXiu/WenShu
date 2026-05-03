<template>
  <div class="prompt-page">
    <div class="prompt-container">
      <div class="prompt-header">
        <h2 class="section-title">Prompt 模板管理</h2>
        <span class="prompt-summary">{{ loadedCount }} 个模板</span>
      </div>

      <div v-if="loadError" class="prompt-error">{{ loadError }}</div>

      <div v-else-if="loading" class="prompt-loading">加载中...</div>

      <div v-else class="prompt-body">
        <div v-for="(cat, catIdx) in categories" :key="cat.key" class="prompt-category">
          <div class="prompt-cat-header" @click="cat.expanded = !cat.expanded">
            <svg :class="['chevron', { expanded: cat.expanded }]" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#B8A898" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            <span class="prompt-cat-label">{{ cat.label }}</span>
            <span class="prompt-cat-count">{{ cat.keys.length }}</span>
          </div>
          <div v-if="cat.expanded" class="prompt-items">
            <div v-for="key in cat.keys" :key="key" class="prompt-item">
              <div class="prompt-item-header">
                <span class="prompt-item-desc">{{ prompts[key]?.description || key }}</span>
                <div class="prompt-item-actions">
                  <span v-if="isModified(key)" class="prompt-modified-dot" title="已修改"></span>
                  <button class="prompt-reset-btn" @click="resetPrompt(key)" title="恢复默认">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                    重置
                  </button>
                </div>
              </div>
              <textarea
                v-if="prompts[key]"
                class="prompt-textarea"
                v-model="prompts[key].template"
                rows="6"
                placeholder="Prompt 模板内容..."
              ></textarea>
              <div v-else class="prompt-loading-item">未加载</div>
              <div v-if="prompts[key] && hasVars(key)" class="prompt-var-hint">
                支持变量：<code v-for="v in getVars(key)" :key="v">{{ v }}</code>
              </div>
            </div>
          </div>
        </div>

        <div class="info-tip">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          变量使用 <code>${'{'}变量名{'}'}</code> 格式，如 <code>${'{'}cardCount{'}'}</code>。修改后点击「保存」立即生效。
        </div>
      </div>

      <div class="prompt-actions">
        <button class="action-btn secondary" @click="loadSettings">放弃修改</button>
        <button class="action-btn primary" :disabled="saving || modifiedCount === 0" @click="handleSave">
          {{ saving ? '保存中...' : '保存修改' }}
        </button>
      </div>

      <div v-if="message" :class="['toast-msg', message.includes('Error') ? 'error' : 'success']">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import type { PromptEntry } from '../types/chat'

const loading = ref(false)
const saving = ref(false)
const message = ref('')
const loadError = ref('')
const prompts = reactive<Record<string, PromptEntry>>({})
const defaults = ref<Record<string, PromptEntry> | null>(null)

const categories = reactive([
  { key: 'system-agent', label: '系统 Agent', expanded: true, keys: ['rag_qa'] },
  { key: 'core', label: '核心功能', expanded: false, keys: ['blog_qa', 'flashcard_generate', 'conversation_summary', 'article_summary'] },
  { key: 'tool', label: '工具提示词', expanded: false, keys: ['translate', 'document_compare', 'search_summary'] },
  { key: 'user-agent', label: '自定义 Agent', expanded: false, keys: [] as string[] },
])

const loadedCount = computed(() => Object.keys(prompts).length)

const modifiedCount = computed(() => {
  if (!defaults.value) return 0
  let count = 0
  for (const key of Object.keys(prompts)) {
    if (defaults.value[key] && prompts[key].template !== defaults.value[key].template) count++
  }
  return count
})

function isModified(key: string) {
  return defaults.value?.[key] && prompts[key]?.template !== defaults.value[key].template
}

function hasVars(key: string) {
  return /\$\{[^}]+\}/.test(prompts[key]?.template || '')
}

function getVars(key: string) {
  const tmpl = prompts[key]?.template || ''
  return [...tmpl.matchAll(/\$\{([^}]+)\}/g)].map(m => m[1])
}

function resetPrompt(key: string) {
  if (defaults.value?.[key] && prompts[key]) {
    prompts[key].template = defaults.value[key].template
  }
}

async function loadSettings() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetch('/api/settings')
    if (!res.ok) {
      loadError.value = '加载失败: HTTP ' + res.status
      return
    }
    const data = await res.json()
    if (data.prompts) {
      Object.keys(prompts).forEach(k => delete prompts[k])
      Object.entries(data.prompts).forEach(([k, v]) => {
        prompts[k] = { ...(v as PromptEntry) }
      })
      // 动态填充 Agent 提示词分类
      const agentKeys = Object.entries(data.prompts)
        .filter(([_, v]) => (v as PromptEntry).category === 'agent')
        .map(([k, _]) => k)
      categories[3].keys = agentKeys
      if (!defaults.value) {
        defaults.value = Object.fromEntries(
          Object.entries(data.prompts).map(([k, v]) => [k, { ...(v as PromptEntry) }])
        )
      }
    } else {
      loadError.value = '服务器未返回 prompts 配置'
    }
  } catch (e) {
    loadError.value = '网络错误，无法加载 Prompt 配置'
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  message.value = ''
  try {
    const res = await fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompts: { ...prompts } }),
    })
    if (res.ok) {
      message.value = '保存成功，Prompt 已即时生效'
      defaults.value = Object.fromEntries(
        Object.entries(prompts).map(([k, v]) => [k, { ...v }])
      )
    } else {
      message.value = '保存失败'
    }
  } catch {
    message.value = '保存失败: 网络错误'
  } finally {
    saving.value = false
    setTimeout(() => { message.value = '' }, 3000)
  }
}

onMounted(loadSettings)
</script>

<style scoped>
.prompt-page { flex: 1; overflow-y: auto; background: #F8F5EF; }
.prompt-container { max-width: 860px; margin: 0 auto; padding: 24px 32px; }
.prompt-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.section-title { font-size: 18px; font-weight: 700; color: #3D3028; margin: 0; }
.prompt-summary { font-size: 13px; color: #B8A898; }

.prompt-loading { padding: 40px; text-align: center; color: #B8A898; font-size: 13px; }
.prompt-error { padding: 16px; background: #FEF2F2; color: #DC2626; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
.prompt-loading-item { padding: 8px; color: #C8B8A8; font-size: 12px; }

.prompt-body {
  background: #fff; border: 1px solid #E8DDD0; border-radius: 10px; padding: 4px 0;
  min-height: 200px;
}

.prompt-category { border-bottom: 1px solid #F5F0EB; }
.prompt-category:last-child { border-bottom: none; }
.prompt-cat-header { display: flex; align-items: center; gap: 8px; padding: 10px 18px; cursor: pointer; }
.prompt-cat-header:hover { background: #FEFCFA; }
.prompt-cat-label { font-size: 13px; font-weight: 600; color: #3D3028; }
.prompt-cat-count { font-size: 11px; color: #8B7E74; background: #F0E8DD; padding: 1px 6px; border-radius: 8px; }
.chevron { transition: transform 0.2s; }
.chevron.expanded { transform: rotate(180deg); }

.prompt-items { padding: 0 18px 12px; display: flex; flex-direction: column; gap: 12px; }
.prompt-item { background: #FEFCFA; border: 1px solid #F0E8DD; border-radius: 8px; padding: 10px 12px; }
.prompt-item-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.prompt-item-desc { font-size: 12px; font-weight: 600; color: #6B5E52; }
.prompt-item-actions { display: flex; align-items: center; gap: 6px; }
.prompt-modified-dot { width: 6px; height: 6px; border-radius: 50%; background: #D97B2B; }
.prompt-reset-btn {
  display: inline-flex; align-items: center; gap: 3px; background: none;
  border: 1px solid #E8DDD0; border-radius: 5px; font-size: 11px;
  color: #B8A898; cursor: pointer; padding: 2px 8px;
}
.prompt-reset-btn:hover { color: #D97B2B; border-color: #D97B2B; }
.prompt-textarea {
  width: 100%; border: 1px solid #E8DDD0; border-radius: 6px; padding: 8px 10px;
  font-size: 12px; font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
  line-height: 1.5; resize: vertical; min-height: 80px; outline: none; background: #fff; color: #3D3028;
}
.prompt-textarea:focus { border-color: #D97B2B; }
.prompt-var-hint { margin-top: 4px; font-size: 11px; color: #B8A898; display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.prompt-var-hint code { background: #F0E8DD; padding: 1px 5px; border-radius: 3px; font-size: 10px; color: #8B7E74; font-family: monospace; }
.info-tip { padding: 10px 18px; font-size: 12px; color: #B8A898; display: flex; align-items: center; gap: 6px; }
.info-tip code { background: #F0E8DD; padding: 1px 5px; border-radius: 3px; font-size: 11px; color: #8B6914; font-family: monospace; }

.prompt-actions { display: flex; gap: 10px; margin-top: 16px; justify-content: flex-end; }
.action-btn {
  padding: 8px 20px; border-radius: 8px; font-size: 13px; font-weight: 600;
  cursor: pointer; transition: all 0.2s; display: flex; align-items: center; gap: 6px;
}
.action-btn.primary { background: #D97B2B; color: #fff; border: none; }
.action-btn.primary:hover:not(:disabled) { background: #C86A20; }
.action-btn.primary:disabled { background: #C8B8A8; cursor: not-allowed; }
.action-btn.secondary { background: #fff; color: #3D3028; border: 1px solid #E8DDD0; }
.action-btn.secondary:hover { background: #FEF3E8; border-color: #D97B2B; }

.toast-msg {
  margin-top: 12px; padding: 10px 14px; border-radius: 8px; font-size: 13px;
}
.toast-msg.success { background: #F0FDF4; color: #16a34a; }
.toast-msg.error { background: #FEF2F2; color: #DC2626; }
</style>
