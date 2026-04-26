<template>
  <div class="agent-card-panel">
    <div class="section-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"/></svg>
      <span class="section-title">Agent 身份卡</span>
    </div>

    <template v-if="agentCard">
      <div class="agent-info">
        <div class="info-row">
          <span class="info-label">名称</span>
          <span class="info-value">{{ agentCard.name }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">版本</span>
          <span class="info-value">{{ agentCard.version }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">地址</span>
          <span class="info-value mono">{{ agentCard.url }}</span>
        </div>
        <div class="info-row" v-if="agentCard.description">
          <span class="info-label">描述</span>
          <span class="info-value desc">{{ agentCard.description }}</span>
        </div>
      </div>

      <div class="skills-section" v-if="agentCard.skills?.length">
        <div class="sub-header">技能</div>
        <div class="tag-list">
          <span v-for="skill in agentCard.skills" :key="skill.id" class="skill-tag">
            {{ skill.name }}
          </span>
        </div>
      </div>

      <div class="caps-section" v-if="agentCard.capabilities">
        <div class="sub-header">能力</div>
        <div class="tag-list">
          <span :class="['cap-tag', agentCard.capabilities.streaming ? 'on' : 'off']">
            流式输出
          </span>
          <span :class="['cap-tag', agentCard.capabilities.pushNotifications ? 'on' : 'off']">
            推送通知
          </span>
        </div>
      </div>
    </template>

    <div v-if="!agentCard" class="empty-agent">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#D4C8BA" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
      <span>Agent 暂不可用</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AgentCard } from '../types/a2a'

defineProps<{ agentCard: AgentCard | null }>()
</script>

<style scoped>
.agent-card-panel {
  padding: 12px 0;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #3D3028;
}
.agent-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.info-label {
  font-size: 12px;
  color: #8B7E74;
  font-weight: 500;
  flex-shrink: 0;
}
.info-value {
  font-size: 12px;
  color: #3D3028;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}
.info-value.mono {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 11px;
}
.info-value.desc {
  text-align: left;
  max-width: 200px;
  line-height: 1.5;
}
.skills-section,
.caps-section {
  margin-top: 10px;
}
.sub-header {
  font-size: 11px;
  font-weight: 600;
  color: #8B7E74;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.skill-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  background: #FEF3E8;
  color: #D97B2B;
  border: 1px solid #F5DFC8;
}
.cap-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}
.cap-tag.on {
  background: #E8F5E9;
  color: #4CAF50;
  border: 1px solid #C8E6C9;
}
.cap-tag.off {
  background: #F5F0EB;
  color: #B8A898;
  border: 1px solid #E8DDD0;
}
.empty-agent {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: #B8A898;
  font-size: 13px;
}
</style>
