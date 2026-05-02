<template>
  <div class="llm-monitor">
    <div class="cm-header">
      <h2 class="section-title">LLM 调用监控</h2>
    </div>

    <!-- Metric Cards -->
    <div class="metric-cards">
      <div class="metric-card">
        <div class="metric-value">{{ todayStats.totalCalls }}</div>
        <div class="metric-label">今日调用</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ formatTokens(todayStats.totalInputTokens + todayStats.totalOutputTokens) }}</div>
        <div class="metric-label">今日 Token</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ formatTokens(totalStats.totalInputTokens + totalStats.totalOutputTokens) }}</div>
        <div class="metric-label">累计 Token</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ todayStats.avgDurationMs > 0 ? Math.round(todayStats.avgDurationMs) + 'ms' : '-' }}</div>
        <div class="metric-label">平均延迟</div>
      </div>
    </div>

    <!-- Token Trend -->
    <div v-if="dailyStats.length >= 2" class="trend-section">
      <h3 class="sub-title">Token 消耗趋势（近 {{ dailyStats.length }} 天）</h3>
      <div class="trend-chart">
        <svg :viewBox="`0 0 ${cw} ${ch}`" class="chart-svg">
          <line v-for="i in 5" :key="'g'+i" :x1="50" :y1="cy(i/5)" :x2="cw-10" :y2="cy(i/5)" stroke="#e8e8e8" stroke-width="1" />
          <text v-for="i in 5" :key="'l'+i" :x="45" :y="cy(i/5)+4" text-anchor="end" font-size="10" fill="#888">
            {{ formatTokens(maxTokens * i / 5) }}
          </text>
          <polyline :points="inputPoints" fill="none" stroke="#16a34a" stroke-width="2" />
          <polyline :points="outputPoints" fill="none" stroke="#2563eb" stroke-width="2" stroke-dasharray="4" />
          <circle v-for="(d, i) in dailyStats" :key="'d'+i" :cx="cx(i)" :cy="cy(d.inputTokens / maxTokens)" r="3" fill="#16a34a" />
          <circle v-for="(d, i) in dailyStats" :key="'o'+i" :cx="cx(i)" :cy="cy(d.outputTokens / maxTokens)" r="3" fill="#2563eb" />
        </svg>
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot green"></span>Input Tokens</span>
          <span class="legend-item"><span class="legend-dot blue"></span>Output Tokens</span>
        </div>
      </div>
    </div>

    <!-- Visualization Charts Row -->
    <div class="charts-row">
      <!-- Hourly Activity Bar Chart -->
      <div class="chart-card">
        <h3 class="sub-title">今日逐时调用量</h3>
        <div class="hourly-chart">
          <svg :viewBox="`0 0 ${hw} ${hh}`" class="chart-svg">
            <line v-for="i in 4" :key="'hg'+i" :x1="30" :y1="hCy(i/4)" :x2="hw" :y2="hCy(i/4)" stroke="#e8e8e8" stroke-width="1" />
            <text v-for="i in 4" :key="'hl'+i" :x="26" :y="hCy(i/4)+4" text-anchor="end" font-size="9" fill="#888">
              {{ Math.round(maxHourlyCalls * i / 4) }}
            </text>
            <g v-for="(d, i) in hourlyStats" :key="'hb'+i">
              <rect :x="hCx(i) - barW/2" :y="hCy(d.calls / maxHourlyCalls)" :width="barW" :height="hBarH(d.calls)" rx="1" :fill="d.calls > 0 ? '#2563eb' : '#e8e8e8'" />
              <text :x="hCx(i)" :y="hh - 2" text-anchor="middle" font-size="8" fill="#888">{{ d.hour }}</text>
            </g>
          </svg>
        </div>
      </div>

      <div class="chart-col">
        <!-- Call Type Donut -->
        <div class="chart-card">
          <h3 class="sub-title">调用类型分布</h3>
          <div class="donut-chart" v-if="typeDistribution.length > 0">
            <svg viewBox="0 0 160 160" class="chart-svg donut-svg">
              <circle v-for="(seg, i) in donutSegments" :key="'ds'+i"
                :cx="80" :cy="80" :r="donutR" fill="none"
                :stroke="seg.color" stroke-width="24"
                :stroke-dasharray="seg.dashArray"
                :stroke-dashoffset="seg.offset"
                :transform="`rotate(-90 80 80)`" />
              <text x="80" y="76" text-anchor="middle" font-size="20" font-weight="700" fill="#333">{{ typeTotal }}</text>
              <text x="80" y="92" text-anchor="middle" font-size="9" fill="#888">总调用</text>
            </svg>
            <div class="donut-legend">
              <span v-for="(t, i) in typeDistribution" :key="'tl'+i" class="legend-item">
                <span class="legend-dot" :style="{ background: typeColor(t.callType) }"></span>
                {{ t.callType }} ({{ t.count }})
              </span>
            </div>
          </div>
          <div v-else class="empty-state small">暂无调用类型数据</div>
        </div>

        <!-- Latency Distribution -->
        <div class="chart-card">
          <h3 class="sub-title">延迟分布</h3>
          <div class="latency-chart" v-if="latencyDistribution.length > 0">
            <svg :viewBox="`0 0 ${lw} ${lRowH * latencyDistribution.length + 10}`" class="chart-svg">
              <g v-for="(b, i) in latencyDistribution" :key="'lb'+i">
                <text :x="0" :y="lRowH * i + 14" font-size="10" fill="#555">{{ b.range }}</text>
                <rect :x="60" :y="lRowH * i + 2" :height="14" rx="2" fill="#f3f4f6" :width="lw - 110" />
                <rect :x="60" :y="lRowH * i + 2" :height="14" rx="2" :fill="latencyColor(i)" :width="latencyBarW(b.count)" />
                <text :x="lw - 45" :y="lRowH * i + 14" font-size="10" fill="#333" font-weight="500">{{ b.count }}</text>
              </g>
            </svg>
          </div>
          <div v-else class="empty-state small">暂无延迟数据</div>
        </div>
      </div>
    </div>

    <!-- Recent Calls -->
    <div class="calls-section">
      <h3 class="sub-title">最近调用</h3>
      <div v-if="calls.length === 0" class="empty-state small">暂无调用记录</div>
      <div class="call-list" v-else>
        <div v-for="call in calls" :key="call.id" class="call-row" :class="{ 'has-error': call.error }">
          <div class="call-main">
            <span class="call-time">{{ formatTime(call.createdAt) }}</span>
            <span class="call-type">{{ call.callType }}</span>
            <span class="call-model">{{ call.model || '-' }}</span>
            <span class="call-tokens">
              <span v-if="call.inputTokens != null">{{ call.inputTokens }}in</span>
              <span v-if="call.outputTokens != null"> / {{ call.outputTokens }}out</span>
            </span>
            <span class="call-duration" v-if="call.durationMs != null">{{ call.durationMs }}ms</span>
            <span class="call-reason" v-if="call.finishReason">{{ call.finishReason }}</span>
            <span class="call-error" v-if="call.error" :title="call.error">ERROR</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { LlmCall, LlmStats, LlmDailyStat, LlmHourlyStat, LlmTypeCount, LlmLatencyBucket } from '../../types/chat'
import { useAdmin } from '../../composables/useAdmin'

const { authHeaders } = useAdmin()
const calls = ref<LlmCall[]>([])
const todayStats = ref<LlmStats>({ totalCalls: 0, totalInputTokens: 0, totalOutputTokens: 0, avgDurationMs: 0 })
const totalStats = ref<LlmStats>({ totalCalls: 0, totalInputTokens: 0, totalOutputTokens: 0, avgDurationMs: 0 })
const dailyStats = ref<LlmDailyStat[]>([])
const hourlyStats = ref<LlmHourlyStat[]>(buildEmptyHourly())
const typeDistribution = ref<LlmTypeCount[]>([])
const latencyDistribution = ref<LlmLatencyBucket[]>([])

function buildEmptyHourly(): LlmHourlyStat[] {
  return Array.from({ length: 24 }, (_, i) => ({ hour: i, calls: 0, inputTokens: 0, outputTokens: 0, avgDurationMs: 0 }))
}

// Token trend chart config
const cw = 500, ch = 160
const pad = { l: 50, r: 10, t: 10, b: 10 }

const maxTokens = computed(() => {
  const vals = dailyStats.value.flatMap(d => [d.inputTokens, d.outputTokens])
  return vals.length > 0 ? Math.max(...vals, 1) : 1
})

function cx(i: number) {
  const w = cw - pad.l - pad.r
  return pad.l + (dailyStats.value.length <= 1 ? w / 2 : (i / (dailyStats.value.length - 1)) * w)
}
function cy(ratio: number) {
  const h = ch - pad.t - pad.b
  return pad.t + (1 - Math.min(1, ratio)) * h
}

const inputPoints = computed(() => dailyStats.value.map((d, i) => `${cx(i)},${cy(d.inputTokens / maxTokens.value)}`).join(' '))
const outputPoints = computed(() => dailyStats.value.map((d, i) => `${cx(i)},${cy(d.outputTokens / maxTokens.value)}`).join(' '))

// Hourly bar chart config
const hw = 520, hh = 140
const hPad = { l: 30, r: 5, t: 5, b: 16 }
const barW = computed(() => {
  const w = hw - hPad.l - hPad.r
  return Math.max(4, (w / 24) - 3)
})

const maxHourlyCalls = computed(() => {
  const m = Math.max(...hourlyStats.value.map(d => d.calls), 1)
  return m
})

function hCx(i: number) {
  const w = hw - hPad.l - hPad.r
  return hPad.l + ((i + 0.5) / 24) * w
}
function hCy(ratio: number) {
  const h = hh - hPad.t - hPad.b
  return hPad.t + (1 - Math.min(1, ratio)) * h
}
function hBarH(calls: number) {
  const h = hh - hPad.t - hPad.b
  return calls <= 0 ? 0 : Math.max(1, (calls / maxHourlyCalls.value) * h)
}

// Donut chart
const donutR = 50
const donutC = 2 * Math.PI * donutR
const typeTotal = computed(() => typeDistribution.value.reduce((s, t) => s + t.count, 0))

const typeColors: Record<string, string> = {
  chat: '#16a34a', stream: '#2563eb', memory: '#ca8a04',
  blog: '#9333ea', eval: '#dc2626',
}
function typeColor(t: string) { return typeColors[t] || '#6b7280' }

const donutSegments = computed(() => {
  if (typeTotal.value === 0) return []
  let acc = 0
  return typeDistribution.value.map(t => {
    const ratio = t.count / donutC * donutC / typeTotal.value
    const len = (t.count / typeTotal.value) * donutC
    const offset = -acc
    acc += len
    return { color: typeColor(t.callType), dashArray: `${len} ${donutC - len}`, offset }
  })
})

// Latency chart
const lw = 300
const lRowH = 22
const maxLatencyCount = computed(() => Math.max(...latencyDistribution.value.map(b => b.count), 1))
function latencyBarW(count: number) { return count <= 0 ? 0 : Math.max(2, (count / maxLatencyCount.value) * (lw - 110)) }
function latencyColor(i: number) {
  const colors = ['#16a34a', '#65a30d', '#ca8a04', '#ea580c', '#dc2626', '#991b1b']
  return colors[Math.min(i, colors.length - 1)]
}

function formatTokens(n: number) {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

async function loadData() {
  try {
    const [callsRes, statsRes, dailyRes, hourlyRes, typeRes, latencyRes] = await Promise.all([
      fetch('/api/admin/llm/calls?limit=30', { headers: authHeaders() }),
      fetch('/api/admin/llm/stats', { headers: authHeaders() }),
      fetch('/api/admin/llm/daily?days=7', { headers: authHeaders() }),
      fetch('/api/admin/llm/hourly', { headers: authHeaders() }),
      fetch('/api/admin/llm/type-distribution', { headers: authHeaders() }),
      fetch('/api/admin/llm/latency-distribution', { headers: authHeaders() }),
    ])
    if (callsRes.ok) calls.value = await callsRes.json()
    if (statsRes.ok) {
      const data = await statsRes.json()
      todayStats.value = data.today || todayStats.value
      totalStats.value = data.total || totalStats.value
    }
    if (dailyRes.ok) dailyStats.value = await dailyRes.json()
    if (hourlyRes.ok) hourlyStats.value = await hourlyRes.json()
    if (typeRes.ok) typeDistribution.value = await typeRes.json()
    if (latencyRes.ok) latencyDistribution.value = await latencyRes.json()
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<style scoped>
.llm-monitor { padding: 0; }
.cm-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }

.metric-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px; }
.metric-card { text-align: center; padding: 16px 8px; background: white; border: 1px solid #e8e8e8; border-radius: 8px; }
.metric-value { font-size: 24px; font-weight: 700; color: #333; margin-bottom: 4px; }
.metric-label { font-size: 12px; color: #888; }

.trend-section { margin-bottom: 20px; }
.sub-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 10px; }
.chart-svg { width: 100%; height: auto; background: #fafafa; border-radius: 8px; }
.chart-legend { display: flex; gap: 16px; margin-top: 6px; }
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #666; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.legend-dot.green { background: #16a34a; }
.legend-dot.blue { background: #2563eb; }

/* Charts row layout */
.charts-row {
  display: grid; grid-template-columns: 1.5fr 1fr;
  gap: 12px; margin-bottom: 20px;
}
.chart-card {
  background: white; border: 1px solid #e8e8e8;
  border-radius: 8px; padding: 14px;
}
.chart-col { display: flex; flex-direction: column; gap: 12px; }
.hourly-chart { }
.donut-chart { display: flex; flex-direction: column; align-items: center; }
.donut-svg { width: 140px; height: 140px; }
.donut-legend { display: flex; flex-wrap: wrap; gap: 6px 12px; margin-top: 8px; justify-content: center; }
.latency-chart { }

.calls-section { margin-top: 20px; }
.empty-state.small { padding: 20px; font-size: 13px; color: #888; background: #fafafa; border-radius: 8px; text-align: center; }
.call-list { display: flex; flex-direction: column; gap: 4px; }
.call-row {
  display: flex; align-items: center; padding: 8px 12px;
  background: white; border: 1px solid #e8e8e8; border-radius: 6px; font-size: 13px;
}
.call-row.has-error { border-color: #fca5a5; background: #fef2f2; }
.call-main { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.call-time { color: #888; font-size: 12px; font-family: monospace; }
.call-type { background: #f3f4f6; padding: 1px 6px; border-radius: 3px; font-size: 11px; color: #555; }
.call-model { color: #333; font-weight: 500; }
.call-tokens { color: #16a34a; font-family: monospace; font-size: 12px; }
.call-duration { color: #888; font-size: 12px; }
.call-reason { font-size: 11px; color: #888; background: #f9fafb; padding: 1px 5px; border-radius: 3px; }
.call-error { color: #dc2626; font-weight: 600; font-size: 11px; }

@media (max-width: 768px) {
  .charts-row { grid-template-columns: 1fr; }
  .metric-cards { grid-template-columns: repeat(2, 1fr); }
}
</style>
