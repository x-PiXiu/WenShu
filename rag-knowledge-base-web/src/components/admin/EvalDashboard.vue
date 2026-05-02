<template>
  <div class="eval-dashboard">
    <div class="cm-header">
      <h2 class="section-title">检索质量评估</h2>
      <div class="header-actions">
        <button class="btn-primary" @click="runEval" :disabled="running">
          {{ running ? '评估中...' : '运行评估' }}
        </button>
      </div>
    </div>

    <div v-if="errorMessage" class="cm-error">{{ errorMessage }}</div>

    <!-- Latest Results Cards -->
    <div v-if="latestResult" class="metric-cards">
      <div class="metric-card">
        <div class="metric-value" :class="scoreClass(latestResult.hitRate)">{{ (latestResult.hitRate * 100).toFixed(0) }}%</div>
        <div class="metric-label">命中率</div>
      </div>
      <div class="metric-card">
        <div class="metric-value" :class="scoreClass(latestResult.avgRecallAtK)">{{ (latestResult.avgRecallAtK * 100).toFixed(0) }}%</div>
        <div class="metric-label">Recall@K</div>
      </div>
      <div class="metric-card">
        <div class="metric-value" :class="scoreClass(latestResult.avgPrecisionAtK)">{{ (latestResult.avgPrecisionAtK * 100).toFixed(0) }}%</div>
        <div class="metric-label">Precision@K</div>
      </div>
      <div class="metric-card">
        <div class="metric-value" :class="scoreClass(latestResult.mrr)">{{ (latestResult.mrr * 100).toFixed(0) }}%</div>
        <div class="metric-label">MRR</div>
      </div>
    </div>

    <!-- Trend Chart -->
    <div v-if="trend.length >= 2" class="trend-section">
      <h3 class="sub-title">趋势</h3>
      <div class="trend-chart">
        <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" class="chart-svg">
          <line v-for="i in 5" :key="'grid-'+i"
                :x1="40" :y1="chartY(i/5)" :x2="chartWidth-10" :y2="chartY(i/5)"
                stroke="#e8e8e8" stroke-width="1" />
          <text v-for="i in 5" :key="'label-'+i"
                :x="35" :y="chartY(i/5)+4" text-anchor="end" font-size="10" fill="#888">
            {{ ((i/5)*100).toFixed(0) }}%
          </text>
          <polyline :points="hitRatePoints" fill="none" stroke="#16a34a" stroke-width="2" />
          <polyline :points="mrrPoints" fill="none" stroke="#2563eb" stroke-width="2" stroke-dasharray="4" />
          <circle v-for="(p, i) in trend" :key="'dot-'+i"
                  :cx="chartX(i)" :cy="chartY(p.hitRate)" r="3" fill="#16a34a" />
        </svg>
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot green"></span>命中率</span>
          <span class="legend-item"><span class="legend-dot blue"></span>MRR</span>
        </div>
      </div>
    </div>

    <!-- Test Case Management -->
    <div class="testcases-section">
      <h3 class="sub-title">
        测试用例
        <button class="btn-secondary btn-sm" @click="showAddForm = !showAddForm">
          {{ showAddForm ? '取消' : '添加用例' }}
        </button>
      </h3>

      <div v-if="showAddForm" class="add-form">
        <div class="form-row">
          <input v-model="newCase.question" placeholder="测试问题" class="form-input" />
          <input v-model="newCase.relevantSources" placeholder="期望来源（逗号分隔）" class="form-input" />
          <input v-model="newCase.category" placeholder="分类" class="form-input" style="width:120px" />
          <button class="btn-primary btn-sm" @click="addCase">添加</button>
        </div>
      </div>

      <div v-if="cases.length === 0" class="empty-state small">
        暂无测试用例，请添加后运行评估
      </div>

      <div class="case-list" v-else>
        <div v-for="tc in cases" :key="tc.id" class="case-row">
          <div class="case-main">
            <span class="case-q">{{ tc.question }}</span>
            <span class="case-cat">{{ tc.category }}</span>
            <span class="case-sources">{{ tc.relevantSources.join(', ') }}</span>
          </div>
          <button class="action-btn danger" @click="deleteCase(tc.id)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { EvalTestCase, EvalResult } from '../../types/chat'

const cases = ref<EvalTestCase[]>([])
const results = ref<EvalResult[]>([])
const trend = ref<{ hitRate: number; mrr: number }[]>([])
const latestResult = ref<EvalResult | null>(null)
const running = ref(false)
const errorMessage = ref('')
const showAddForm = ref(false)
const newCase = ref({ question: '', relevantSources: '', category: '' })

const chartWidth = 500
const chartHeight = 160
const chartPad = { left: 40, right: 10, top: 10, bottom: 10 }

function chartX(i: number) {
  const w = chartWidth - chartPad.left - chartPad.right
  return chartPad.left + (trend.value.length <= 1 ? w / 2 : (i / (trend.value.length - 1)) * w)
}
function chartY(val: number) {
  const h = chartHeight - chartPad.top - chartPad.bottom
  return chartPad.top + (1 - val) * h
}

const hitRatePoints = computed(() =>
  trend.value.map((p, i) => `${chartX(i)},${chartY(p.hitRate)}`).join(' ')
)
const mrrPoints = computed(() =>
  trend.value.map((p, i) => `${chartX(i)},${chartY(p.mrr)}`).join(' ')
)

function scoreClass(val: number) {
  if (val >= 0.7) return 'score-high'
  if (val >= 0.4) return 'score-medium'
  return 'score-low'
}

async function loadCases() {
  try {
    const res = await fetch('/api/admin/eval/cases')
    if (res.ok) cases.value = await res.json()
  } catch { /* ignore */ }
}

async function loadResults() {
  try {
    const [resResults, resTrend] = await Promise.all([
      fetch('/api/admin/eval/results'),
      fetch('/api/admin/eval/trend'),
    ])
    if (resResults.ok) {
      results.value = await resResults.json()
      latestResult.value = results.value.length > 0 ? results.value[results.value.length - 1] : null
    }
    if (resTrend.ok) trend.value = await resTrend.json()
  } catch { /* ignore */ }
}

async function runEval() {
  running.value = true
  errorMessage.value = ''
  try {
    const res = await fetch('/api/admin/eval/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
    if (!res.ok) {
      const err = await res.json()
      errorMessage.value = err.error || '评估失败'
    } else {
      await loadResults()
    }
  } catch {
    errorMessage.value = '网络错误'
  }
  running.value = false
}

async function addCase() {
  if (!newCase.value.question.trim()) return
  const sources = newCase.value.relevantSources.split(',').map(s => s.trim()).filter(Boolean)
  const tc = {
    id: 'tc-' + Date.now().toString(36),
    question: newCase.value.question.trim(),
    relevantSources: sources,
    category: newCase.value.category.trim() || '通用',
  }
  try {
    await fetch('/api/admin/eval/cases', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(tc),
    })
    newCase.value = { question: '', relevantSources: '', category: '' }
    await loadCases()
  } catch { /* ignore */ }
}

async function deleteCase(id: string) {
  try {
    await fetch(`/api/admin/eval/cases/${id}`, { method: 'DELETE' })
    cases.value = cases.value.filter(c => c.id !== id)
  } catch { /* ignore */ }
}

onMounted(() => { loadCases(); loadResults() })
</script>

<style scoped>
.eval-dashboard { padding: 0; }
.cm-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px;
}
.header-actions { display: flex; gap: 8px; }
.cm-error {
  background: #fef2f2; color: #dc2626; padding: 8px 12px;
  border-radius: 6px; margin-bottom: 12px; font-size: 13px;
}

.metric-cards {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 12px; margin-bottom: 20px;
}
.metric-card {
  text-align: center; padding: 16px 8px;
  background: white; border: 1px solid #e8e8e8;
  border-radius: 8px;
}
.metric-value { font-size: 28px; font-weight: 700; margin-bottom: 4px; }
.metric-label { font-size: 12px; color: #888; }
.score-high { color: #16a34a; }
.score-medium { color: #ca8a04; }
.score-low { color: #6b7280; }

.trend-section { margin-bottom: 20px; }
.sub-title {
  font-size: 15px; font-weight: 600; color: #333;
  margin-bottom: 10px; display: flex; align-items: center; gap: 8px;
}
.chart-svg { width: 100%; height: auto; background: #fafafa; border-radius: 8px; }
.chart-legend { display: flex; gap: 16px; margin-top: 6px; }
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #666; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.legend-dot.green { background: #16a34a; }
.legend-dot.blue { background: #2563eb; }

.testcases-section { margin-top: 20px; }
.add-form { margin-bottom: 12px; }
.form-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.form-input {
  flex: 1; min-width: 100px; padding: 6px 10px; border: 1px solid #d1d5db;
  border-radius: 6px; font-size: 13px;
}
.btn-sm { font-size: 12px; padding: 4px 12px; }

.case-list { display: flex; flex-direction: column; gap: 6px; }
.case-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; background: white; border: 1px solid #e8e8e8;
  border-radius: 6px;
}
.case-main { display: flex; gap: 10px; align-items: center; flex: 1; min-width: 0; }
.case-q { font-size: 13px; color: #333; font-weight: 500; }
.case-cat { font-size: 11px; color: #888; background: #f3f4f6; padding: 2px 6px; border-radius: 3px; }
.case-sources { font-size: 12px; color: #666; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.action-btn {
  background: none; border: none; cursor: pointer; padding: 4px;
  color: #888; border-radius: 4px; display: flex; align-items: center;
}
.action-btn.danger:hover { color: #dc2626; background: #fef2f2; }

.empty-state.small { padding: 20px; font-size: 13px; }
</style>
