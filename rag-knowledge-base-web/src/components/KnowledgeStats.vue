<template>
  <div class="knowledge-stats">
    <div class="stats-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
      <span class="stats-title">藏书阁</span>
    </div>
    <div v-if="!stats" class="stats-loading">Loading...</div>
    <template v-if="stats">
      <div class="stats-row">
        <div class="stat-card">
          <span class="stat-value">{{ stats.documentCount }}</span>
          <span class="stat-label">文档</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ stats.segmentCount }}</span>
          <span class="stat-label">分段</span>
        </div>
      </div>
      <div class="stats-details">
        <div class="detail-row">
          <span class="detail-label">LLM</span>
          <span class="detail-value">{{ stats.llmProvider }} / {{ stats.llmModel }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Embedding</span>
          <span class="detail-value">{{ stats.embeddingProvider }} / {{ stats.embeddingModel }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">向量存储</span>
          <span class="detail-value">{{ stats.vectorStoreType }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { KnowledgeStats } from '../types/chat'

defineProps<{ stats: KnowledgeStats | null }>()
</script>

<style scoped>
.knowledge-stats {
  padding: 12px 0;
}
.stats-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.stats-title {
  font-size: 13px;
  font-weight: 600;
  color: #3D3028;
}
.stats-loading {
  text-align: center;
  color: #B8A898;
  font-size: 13px;
  padding: 16px 0;
}
.stats-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.stat-card {
  flex: 1;
  background: #FAF7F2;
  border: 1px solid #E8DDD0;
  border-radius: 10px;
  padding: 12px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #E8913A;
}
.stat-label {
  font-size: 11px;
  color: #8B7E74;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.stats-details {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}
.detail-label {
  font-size: 12px;
  color: #8B7E74;
  font-weight: 500;
}
.detail-value {
  font-size: 12px;
  color: #3D3028;
  font-weight: 500;
  text-align: right;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
