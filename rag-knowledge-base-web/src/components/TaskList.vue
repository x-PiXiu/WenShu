<template>
  <div class="task-panel">
    <div class="section-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
      <span class="section-title">A2A 任务记录</span>
      <span class="task-count" v-if="tasks.length > 0">{{ tasks.length }}</span>
    </div>

    <div v-if="tasks.length === 0" class="empty-tasks">
      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#D4C8BA" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="9" y1="9" x2="15" y2="15"/><line x1="15" y1="9" x2="9" y2="15"/></svg>
      <span>暂无 A2A 任务</span>
      <span class="empty-hint">其他 Agent 调用时将自动记录</span>
    </div>

    <div v-else class="task-items">
      <div v-for="task in tasks" :key="task.id" class="task-card">
        <!-- Task Header (clickable) -->
        <div class="task-header" @click="toggleTask(task.id)">
          <div class="task-meta">
            <span :class="['task-state', task.state]">{{ stateLabel(task.state) }}</span>
            <span class="task-time">{{ formatTime(task.createdAt) }}</span>
            <span class="task-skill">{{ skillLabel(task.skillId) }}</span>
          </div>
          <div class="task-question" v-if="task.question">{{ task.question }}</div>
          <div class="task-question task-id-fallback" v-else>{{ task.id }}</div>
          <span :class="['expand-arrow', { expanded: expandedTaskId === task.id }]">&#x25B8;</span>
        </div>

        <!-- Expanded Detail: Completed -->
        <div v-if="expandedTaskId === task.id && task.state === 'completed'" class="task-detail">
          <!-- Step 1: Question -->
          <div class="think-step">
            <div class="think-step-head">
              <span class="step-dot">1</span>
              <span class="step-label">接收问题</span>
            </div>
            <div class="think-step-body">
              <div class="question-box">{{ task.question }}</div>
            </div>
          </div>

          <!-- Step 2: Retrieval -->
          <div class="think-step" v-if="task.sources && task.sources.length > 0">
            <div class="think-step-head">
              <span class="step-dot">2</span>
              <span class="step-label">混合检索</span>
              <span class="step-badge">{{ task.sources.length }} 条结果</span>
            </div>
            <div class="think-step-body">
              <div v-for="s in task.sources" :key="s.index" class="source-card">
                <div class="source-head">
                  <span class="source-idx">[{{ s.index }}]</span>
                  <span class="source-name">{{ s.source }}</span>
                  <span class="source-score">RRF {{ s.rrfScore.toFixed(4) }} | Vec {{ s.vectorScore.toFixed(4) }}</span>
                </div>
                <div class="source-text">{{ s.text }}</div>
              </div>
            </div>
          </div>

          <!-- Step 3: Answer -->
          <div class="think-step">
            <div class="think-step-head">
              <span class="step-dot">{{ (task.sources && task.sources.length > 0) ? '3' : '2' }}</span>
              <span class="step-label">生成回答</span>
            </div>
            <div class="think-step-body">
              <div class="answer-box">
                <MarkdownRenderer :content="task.result || ''" />
              </div>
            </div>
          </div>
        </div>

        <!-- Expanded Detail: Failed -->
        <div v-if="expandedTaskId === task.id && task.state === 'failed'" class="task-detail">
          <div class="error-box">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E85D5D" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            <span>{{ task.error || '未知错误' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import type { A2ATask } from '../types/a2a'

defineProps<{ tasks: A2ATask[] }>()

const expandedTaskId = ref<string | null>(null)

function toggleTask(id: string) {
  expandedTaskId.value = expandedTaskId.value === id ? null : id
}

function stateLabel(state: string): string {
  const labels: Record<string, string> = {
    completed: '已完成',
    failed: '失败',
    working: '执行中',
    submitted: '已提交',
    canceled: '已取消',
  }
  return labels[state] || state
}

function skillLabel(skillId: string): string {
  const labels: Record<string, string> = {
    'rag-query': '藏书阁问答',
  }
  return labels[skillId] || skillId
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
</script>

<style scoped>
.task-panel {
  padding: 12px 0;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #3D3028;
}
.task-count {
  font-size: 11px;
  font-weight: 600;
  color: white;
  background: #E8913A;
  border-radius: 10px;
  padding: 1px 8px;
  min-width: 20px;
  text-align: center;
}

/* Empty */
.empty-tasks {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  color: #B8A898;
  font-size: 13px;
}
.empty-hint {
  font-size: 12px;
  color: #D4C8BA;
}

/* Task Cards */
.task-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.task-card {
  background: #FAF7F2;
  border: 1px solid #E8DDD0;
  border-radius: 10px;
  overflow: hidden;
  transition: border-color 0.2s;
}
.task-card:hover {
  border-color: #D4C8BA;
}
.task-header {
  padding: 12px 14px;
  cursor: pointer;
  position: relative;
  user-select: none;
}
.task-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.task-state {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  letter-spacing: 0.3px;
}
.task-state.completed { background: #E8F5E9; color: #4CAF50; }
.task-state.failed { background: #FFEBEE; color: #E85D5D; }
.task-state.working { background: #FEF3E8; color: #E8913A; }
.task-state.submitted { background: #E3F2FD; color: #5C9CE6; }
.task-state.canceled { background: #F5F0EB; color: #B8A898; }
.task-time {
  font-size: 11px;
  color: #B8A898;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
}
.task-skill {
  font-size: 11px;
  color: #8B7E74;
  background: #F5F0EB;
  padding: 1px 6px;
  border-radius: 6px;
}
.task-question {
  font-size: 13px;
  color: #3D3028;
  line-height: 1.4;
  padding-right: 24px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.task-id-fallback {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 12px;
  color: #8B7E74;
}
.expand-arrow {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #B8A898;
  font-size: 14px;
  transition: transform 0.2s;
}
.expand-arrow.expanded {
  transform: translateY(-50%) rotate(90deg);
}

/* Task Detail */
.task-detail {
  border-top: 1px solid #E8DDD0;
  padding: 16px 14px;
  background: #FFFFFF;
}

/* Thinking Steps */
.think-step {
  margin-bottom: 16px;
}
.think-step:last-child {
  margin-bottom: 0;
}
.think-step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.step-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, #E8913A, #D97B2B);
  color: white;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.step-label {
  font-size: 12px;
  font-weight: 600;
  color: #3D3028;
}
.step-badge {
  font-size: 10px;
  font-weight: 600;
  color: #E8913A;
  background: #FEF3E8;
  padding: 2px 8px;
  border-radius: 10px;
}
.think-step-body {
  padding-left: 28px;
}

/* Question Box */
.question-box {
  font-size: 13px;
  color: #3D3028;
  background: #FEF3E8;
  border: 1px solid #F5DFC8;
  border-radius: 8px;
  padding: 10px 12px;
  line-height: 1.5;
}

/* Source Cards */
.source-card {
  background: #FAF7F2;
  border: 1px solid #E8DDD0;
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  font-size: 12px;
}
.source-card:last-child {
  margin-bottom: 0;
}
.source-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.source-idx {
  font-weight: 700;
  color: #D97B2B;
  font-size: 11px;
}
.source-name {
  font-size: 11px;
  color: #6B5E52;
  background: #F5F0EB;
  padding: 1px 6px;
  border-radius: 4px;
}
.source-score {
  font-size: 10px;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  color: #B8A898;
}
.source-text {
  color: #6B5E52;
  line-height: 1.5;
}

/* Answer Box */
.answer-box {
  font-size: 13px;
  color: #3D3028;
  background: #F8FBF8;
  border: 1px solid #C8E6C9;
  border-radius: 8px;
  padding: 12px;
  line-height: 1.6;
}

/* Error Box */
.error-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: #FFF5F5;
  border: 1px solid #FFCDD2;
  border-radius: 8px;
  padding: 12px;
  font-size: 13px;
  color: #C62828;
  line-height: 1.5;
}
</style>
