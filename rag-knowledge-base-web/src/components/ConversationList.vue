<template>
  <div class="conv-panel">
    <div class="section-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
      <span class="section-title">对话记录</span>
      <button class="new-btn" @click="$emit('create')" title="新建对话">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
      </button>
    </div>

    <div v-if="conversations.length === 0" class="empty-conv">
      <span>暂无对话记录</span>
    </div>

    <div v-else class="conv-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        :class="['conv-item', { active: conv.id === currentId }]"
        @click="$emit('switch', conv.id)"
      >
        <span class="conv-title">{{ conv.title }}</span>
        <button class="conv-delete" @click.stop="$emit('delete', conv.id)" title="删除对话">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Conversation } from '../types/chat'

defineProps<{
  conversations: Conversation[]
  currentId: string | null
}>()

defineEmits<{
  create: []
  switch: [id: string]
  delete: [id: string]
}>()
</script>

<style scoped>
.conv-panel {
  padding: 12px 0;
  border-bottom: 1px solid #E8DDD0;
  margin-bottom: 8px;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #3D3028;
  flex: 1;
}
.new-btn {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px dashed #D4C8BA;
  background: transparent;
  color: #B8A898;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.new-btn:hover {
  border-color: #E8913A;
  color: #E8913A;
  background: #FEF3E8;
}
.empty-conv {
  text-align: center;
  color: #D4C8BA;
  font-size: 12px;
  padding: 12px 0;
}
.conv-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-height: 240px;
  overflow-y: auto;
}
.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  gap: 6px;
}
.conv-item:hover {
  background: #FAF7F2;
}
.conv-item.active {
  background: #FEF3E8;
  border: 1px solid #F5DFC8;
}
.conv-title {
  font-size: 12px;
  color: #3D3028;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.conv-item.active .conv-title {
  font-weight: 500;
  color: #D97B2B;
}
.conv-delete {
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
}
.conv-item:hover .conv-delete {
  color: #B8A898;
}
.conv-delete:hover {
  background: #FFEBEE !important;
  color: #E85D5D !important;
}
</style>
