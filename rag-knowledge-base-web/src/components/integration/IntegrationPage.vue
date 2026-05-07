<template>
  <div class="integration-page">
    <div class="tabs">
      <button :class="['tab-btn', { active: tab === 'agents' }]" @click="tab = 'agents'">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        智能体
      </button>
      <button :class="['tab-btn', { active: tab === 'mcp' }]" @click="tab = 'mcp'">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><circle cx="6" cy="6" r="1" fill="currentColor"/><circle cx="6" cy="18" r="1" fill="currentColor"/></svg>
        MCP 工具服务
      </button>
      <button :class="['tab-btn', { active: tab === 'remote' }]" @click="tab = 'remote'">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"/></svg>
        远程 Agent
      </button>
    </div>

    <!-- Agents Tab -->
    <div v-if="tab === 'agents'" class="tab-content">
      <AgentManager />
    </div>

    <!-- MCP Tab -->
    <div v-if="tab === 'mcp'" class="tab-content">
      <div class="config-card">
        <div class="card-header">
          <span class="card-title">MCP 外部工具服务</span>
        </div>
        <p class="section-desc">连接外部 MCP Server，自动发现并注册其提供的工具。保存后需要重启服务生效。</p>
        <div v-for="(server, i) in mcpServers" :key="i" class="ext-entry">
          <input v-model="server.name" placeholder="名称" class="ext-name" />
          <input v-model="server.sseUrl" placeholder="SSE URL (如 http://localhost:3001/sse)" class="ext-url" />
          <label class="ext-toggle"><input type="checkbox" v-model="server.enabled" />启用</label>
          <button class="ext-del-btn" @click="mcpServers.splice(i, 1)">x</button>
        </div>
        <button class="ext-add-btn" @click="mcpServers.push({ name: '', sseUrl: '', enabled: true })">+ 添加 MCP Server</button>
      </div>
      <div class="save-row">
        <button class="save-btn" @click="saveConfig" :disabled="saving">{{ saving ? '保存中...' : '保存配置' }}</button>
      </div>
    </div>

    <!-- Remote Agents Tab -->
    <div v-if="tab === 'remote'" class="tab-content">
      <div class="config-card">
        <div class="card-header">
          <span class="card-title">远程 Agent 发现</span>
        </div>
        <p class="section-desc">配置远程 A2A Agent 地址，自动发现其技能并在本地注册。保存后需要重启服务生效。</p>
        <div v-for="(agent, i) in remoteAgents" :key="i" class="ext-entry">
          <input v-model="agent.name" placeholder="名称" class="ext-name" />
          <input v-model="agent.url" placeholder="URL (如 http://localhost:8082)" class="ext-url" />
          <label class="ext-toggle"><input type="checkbox" v-model="agent.enabled" />启用</label>
          <button class="ext-del-btn" @click="remoteAgents.splice(i, 1)">x</button>
        </div>
        <button class="ext-add-btn" @click="remoteAgents.push({ name: '', url: '', enabled: true })">+ 添加远程 Agent</button>
      </div>
      <div class="save-row">
        <button class="save-btn" @click="saveConfig" :disabled="saving">{{ saving ? '保存中...' : '保存配置' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import AgentManager from '../AgentManager.vue'

const tab = ref('agents')
const mcpServers = ref<{ name: string; sseUrl: string; enabled: boolean }[]>([])
const remoteAgents = ref<{ name: string; url: string; enabled: boolean }[]>([])
const saving = ref(false)

onMounted(async () => {
  try {
    const res = await fetch('/api/settings')
    if (res.ok) {
      const s = await res.json()
      mcpServers.value = s.mcpServers ? s.mcpServers.map((x: any) => ({ ...x })) : []
      remoteAgents.value = s.remoteAgents ? s.remoteAgents.map((x: any) => ({ ...x })) : []
    }
  } catch { /* ignore */ }
})

async function saveConfig() {
  saving.value = true
  try {
    const res = await fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mcpServers: mcpServers.value, remoteAgents: remoteAgents.value }),
    })
    if (res.ok) {
      alert('配置已保存，部分变更需要重启服务生效。')
    }
  } catch { /* ignore */ }
  saving.value = false
}
</script>

<style scoped>
.integration-page { padding: 0; height: 100%; display: flex; flex-direction: column; }
.tabs {
  display: flex; border-bottom: 1px solid #E8DDD0;
  background: #FFF; padding: 0 24px; flex-shrink: 0;
}
.tab-btn {
  display: inline-flex; align-items: center; gap: 6px;
  border: none; background: none; color: #8B7E74;
  font-size: 13px; font-weight: 500; cursor: pointer;
  padding: 12px 16px; border-bottom: 2px solid transparent;
  transition: all 0.2s; white-space: nowrap;
}
.tab-btn:hover { color: #D97B2B; }
.tab-btn.active { color: #D97B2B; border-bottom-color: #D97B2B; }

.tab-content { flex: 1; overflow-y: auto; padding: 24px; }

/* Config cards */
.config-card {
  background: #FFF; border: 1px solid #E8DDD0; border-radius: 12px;
  padding: 20px; margin-bottom: 16px;
}
.card-header { margin-bottom: 8px; }
.card-title { font-size: 15px; font-weight: 600; color: #3D3028; }
.section-desc { font-size: 13px; color: #8B7E74; margin: 0 0 16px; line-height: 1.5; }

.ext-entry { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ext-name {
  width: 140px; padding: 7px 10px; border: 1px solid #E8DDD0;
  border-radius: 6px; font-size: 12px; color: #3D3028; font-family: inherit;
}
.ext-name:focus, .ext-url:focus { outline: none; border-color: #D97B2B; }
.ext-url {
  flex: 1; padding: 7px 10px; border: 1px solid #E8DDD0;
  border-radius: 6px; font-size: 12px; color: #3D3028; font-family: inherit;
}
.ext-toggle { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #6B5E52; white-space: nowrap; cursor: pointer; }
.ext-toggle input { cursor: pointer; }
.ext-del-btn {
  width: 28px; height: 28px; border-radius: 6px; border: 1px solid #E8DDD0;
  background: #FFF; font-size: 14px; cursor: pointer; color: #8B7E74; transition: all 0.15s;
}
.ext-del-btn:hover { border-color: #E85D5D; color: #E85D5D; }
.ext-add-btn {
  padding: 6px 14px; border-radius: 6px; border: 1px dashed #E8DDD0;
  background: transparent; font-size: 12px; color: #8B7E74; cursor: pointer; transition: all 0.2s;
}
.ext-add-btn:hover { border-color: #D97B2B; color: #D97B2B; }

.save-row { display: flex; justify-content: flex-end; margin-top: 8px; }
.save-btn {
  padding: 8px 24px; border-radius: 8px; border: none;
  background: #D97B2B; color: #FFF; font-size: 13px;
  font-weight: 500; cursor: pointer; transition: background 0.2s;
}
.save-btn:hover { background: #C06A1E; }
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
