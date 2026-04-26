<template>
  <div class="agent-manager">
    <div class="section-header">
      <h3>智能体管理</h3>
      <button class="add-btn" @click="startCreate">+ 创建智能体</button>
    </div>

    <div class="agent-list">
      <div v-for="agent in agents" :key="agent.id" class="agent-card">
        <div class="agent-info">
          <span class="agent-avatar">{{ agent.avatar || '🤖' }}</span>
          <div class="agent-meta">
            <span class="agent-name">{{ agent.name }}</span>
            <span v-if="agent.isDefault" class="default-badge">默认</span>
          </div>
          <p class="agent-desc">{{ agent.description || '暂无描述' }}</p>
        </div>
        <div class="agent-actions">
          <button class="edit-btn" @click="startEdit(agent)">编辑</button>
          <button v-if="!agent.isDefault" class="del-btn" @click="handleDelete(agent)">删除</button>
        </div>
      </div>
    </div>

    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="modal">
        <h3>{{ editingId ? '编辑智能体' : '创建智能体' }}</h3>
        <div class="form-group">
          <label>头像</label>
          <input v-model="form.avatar" placeholder="输入 emoji，如 🔍" class="avatar-input" />
        </div>
        <div class="form-group">
          <label>名称</label>
          <input v-model="form.name" placeholder="智能体名称" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <input v-model="form.description" placeholder="简短描述" />
        </div>
        <div class="form-group">
          <label>系统提示词</label>
          <textarea v-model="form.systemPrompt" rows="10" placeholder="定义智能体的角色、行为和回答风格..." />
        </div>
        <div class="modal-actions">
          <button class="cancel-btn" @click="showForm = false">取消</button>
          <button class="save-btn" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAgents } from '../composables/useAgents'

const { agents, createAgent, updateAgent, deleteAgent } = useAgents()

const showForm = ref(false)
const editingId = ref<string | null>(null)
const form = ref({ name: '', description: '', systemPrompt: '', avatar: '🤖' })

function startCreate() {
  editingId.value = null
  form.value = { name: '', description: '', systemPrompt: '', avatar: '🤖' }
  showForm.value = true
}

function startEdit(agent: { id: string; name: string; description: string; systemPrompt: string; avatar: string }) {
  editingId.value = agent.id
  form.value = { name: agent.name, description: agent.description, systemPrompt: agent.systemPrompt, avatar: agent.avatar }
  showForm.value = true
}

async function handleSave() {
  if (!form.value.name.trim() || !form.value.systemPrompt.trim()) return
  if (editingId.value) {
    await updateAgent(editingId.value, form.value)
  } else {
    await createAgent(form.value)
  }
  showForm.value = false
}

async function handleDelete(agent: { id: string; isDefault: boolean }) {
  if (agent.isDefault) return
  await deleteAgent(agent.id)
}
</script>

<style scoped>
.agent-manager { padding: 20px 0; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.section-header h3 { margin: 0; font-size: 16px; color: #3D3028; }
.add-btn {
  padding: 6px 16px; border-radius: 8px; border: 1px solid #D97B2B;
  background: #FFFFFF; color: #D97B2B; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.add-btn:hover { background: #FEF3E8; }

.agent-list { display: flex; flex-direction: column; gap: 10px; }
.agent-card {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; background: #FAF7F2; border: 1px solid #E8DDD0;
  border-radius: 10px; transition: all 0.2s;
}
.agent-card:hover { border-color: #D97B2B; }
.agent-info { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; }
.agent-avatar { font-size: 24px; flex-shrink: 0; }
.agent-meta { display: flex; align-items: center; gap: 8px; }
.agent-name { font-size: 14px; font-weight: 600; color: #3D3028; }
.default-badge {
  font-size: 11px; padding: 2px 8px; border-radius: 10px;
  background: #E8F5E9; color: #4CAF50; font-weight: 500;
}
.agent-desc {
  font-size: 12px; color: #A89888; margin: 0; flex: 1; min-width: 0;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.agent-actions { display: flex; gap: 6px; flex-shrink: 0; margin-left: 12px; }
.edit-btn, .del-btn {
  padding: 4px 12px; border-radius: 6px; border: 1px solid #E8DDD0;
  background: #FFFFFF; font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.edit-btn:hover { border-color: #D97B2B; color: #D97B2B; }
.del-btn:hover { border-color: #E85D5D; color: #E85D5D; }

.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.3); display: flex; align-items: center;
  justify-content: center; z-index: 1000;
}
.modal {
  background: #FFFFFF; border-radius: 14px; padding: 24px;
  width: 560px; max-width: 90vw; max-height: 85vh; overflow-y: auto;
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
</style>
