<template>
  <div class="radar-chart">
    <svg :viewBox="`0 0 ${size} ${size}`" :width="size" :height="size">
      <defs>
        <linearGradient id="radarFill" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#D97B2B" stop-opacity="0.25" />
          <stop offset="100%" stop-color="#C86A20" stop-opacity="0.08" />
        </linearGradient>
        <filter id="glow">
          <feGaussianBlur stdDeviation="2" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>

      <!-- Grid circles -->
      <circle v-for="i in 4" :key="'grid-'+i"
        :cx="cx" :cy="cy" :r="radius * i / 4"
        fill="none" stroke="#E8DDD0" :stroke-width="i === 4 ? 1.5 : 0.8" />

      <!-- Axis lines -->
      <line v-for="(_, i) in axes" :key="'axis-'+i"
        :x1="cx" :y1="cy"
        :x2="cx + radius * Math.cos(angle(i) - Math.PI/2)"
        :y2="cy + radius * Math.sin(angle(i) - Math.PI/2)"
        stroke="#E8DDD0" stroke-width="0.8" />

      <!-- Data polygon -->
      <polygon v-if="axes.length >= 3"
        :points="dataPoints"
        fill="url(#radarFill)" stroke="#D97B2B" stroke-width="2"
        stroke-linejoin="round" filter="url(#glow)" />

      <!-- Data points -->
      <circle v-for="(axis, i) in axes" :key="'dot-'+i"
        :cx="pointX(i)" :cy="pointY(i)" r="4"
        :fill="dotColor(axis.value)"
        stroke="#fff" stroke-width="1.5" />

      <!-- Labels -->
      <text v-for="(axis, i) in axes" :key="'label-'+i"
        :x="labelX(i)" :y="labelY(i)"
        text-anchor="middle" dominant-baseline="central"
        :font-size="axis.value >= 70 ? 12 : 11"
        :font-weight="axis.value >= 70 ? 600 : 400"
        :fill="axis.value >= 70 ? '#D97B2B' : '#8B7E74'">
        {{ axis.label }}
      </text>

      <!-- Score labels -->
      <text v-for="(axis, i) in axes" :key="'score-'+i"
        :x="scoreX(i)" :y="scoreY(i)"
        text-anchor="middle" dominant-baseline="central"
        font-size="10" :fill="dotColor(axis.value)">
        {{ axis.value }}%
      </text>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Axis { label: string; value: number }

const props = withDefaults(defineProps<{
  axes: Axis[]
  size?: number
}>(), { size: 320 })

const cx = computed(() => props.size / 2)
const cy = computed(() => props.size / 2)
const radius = computed(() => props.size * 0.32)

function angle(i: number) {
  return (2 * Math.PI * i) / props.axes.length
}

function pointX(i: number) {
  return cx.value + radius.value * (props.axes[i].value / 100) * Math.cos(angle(i) - Math.PI / 2)
}
function pointY(i: number) {
  return cy.value + radius.value * (props.axes[i].value / 100) * Math.sin(angle(i) - Math.PI / 2)
}

const dataPoints = computed(() =>
  props.axes.map((_, i) => `${pointX(i)},${pointY(i)}`).join(' ')
)

function labelX(i: number) {
  const offset = radius.value + 28
  return cx.value + offset * Math.cos(angle(i) - Math.PI / 2)
}
function labelY(i: number) {
  const offset = radius.value + 28
  return cy.value + offset * Math.sin(angle(i) - Math.PI / 2)
}
function scoreX(i: number) {
  const offset = radius.value + 44
  return cx.value + offset * Math.cos(angle(i) - Math.PI / 2)
}
function scoreY(i: number) {
  const offset = radius.value + 44
  return cy.value + offset * Math.sin(angle(i) - Math.PI / 2)
}

function dotColor(value: number) {
  if (value >= 70) return '#16a34a'
  if (value >= 40) return '#CA8A04'
  return '#DC2626'
}
</script>

<style scoped>
.radar-chart {
  display: flex; justify-content: center; align-items: center;
  padding: 8px;
}
</style>
