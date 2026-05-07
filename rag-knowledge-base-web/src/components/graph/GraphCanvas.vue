<template>
  <div class="graph-canvas-wrapper">
    <div ref="containerRef" class="graph-container"></div>
    <GraphToolbar
      :nodes="nodes"
      :selected-node="selectedNode"
      @search="handleSearch"
      @filter="handleFilter"
      @fit="fitView"
      @export="$emit('export')"
    />
    <NodeDetailPanel
      v-if="selectedNode"
      :node="selectedNode"
      :graph-id="graphId"
      @close="selectedNode = null"
      @ask="$emit('ask', $event)"
      @suggest="$emit('suggest', selectedNode!.id, $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { Network } from 'vis-network'
import type { GraphNode, GraphEdge } from '../../types/graph'
import GraphToolbar from './GraphToolbar.vue'
import NodeDetailPanel from './NodeDetailPanel.vue'

const props = defineProps<{
  nodes: GraphNode[]
  edges: GraphEdge[]
  graphId: string
}>()

const emit = defineEmits<{
  ask: [question: string]
  suggest: [sourceId: string, targetId: string]
  export: []
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const selectedNode = ref<GraphNode | null>(null)
let network: InstanceType<typeof Network> | null = null

const NODE_COLORS: Record<string, { background: string; border: string; highlight: { background: string; border: string } }> = {
  concept: { background: '#F5B041', border: '#D97B2B', highlight: { background: '#FDEBD0', border: '#D97B2B' } },
  person: { background: '#F1948A', border: '#E85D5D', highlight: { background: '#FADBD8', border: '#E85D5D' } },
  org: { background: '#85C1E9', border: '#2196F3', highlight: { background: '#D6EAF8', border: '#2196F3' } },
  technology: { background: '#82E0AA', border: '#4CAF50', highlight: { background: '#D5F5E3', border: '#4CAF50' } },
  event: { background: '#C39BD3', border: '#9C27B0', highlight: { background: '#E8DAEF', border: '#9C27B0' } },
  location: { background: '#76D7C4', border: '#00BCD4', highlight: { background: '#D1F2EB', border: '#00BCD4' } },
  other: { background: '#BEB9B0', border: '#8B7E74', highlight: { background: '#E8E4DE', border: '#8B7E74' } },
}

const DEFAULT_COLOR = NODE_COLORS.other

function buildVisData() {
  const nodeLabelMap = new Map(props.nodes.map(n => [n.id, n.label]))

  const visNodes = props.nodes.map(n => ({
    id: n.id,
    label: n.label,
    title: n.description || n.label,
    group: n.groupName || n.nodeType,
    color: NODE_COLORS[n.nodeType] || DEFAULT_COLOR,
    font: { size: 13, color: '#3D3028', face: 'system-ui, sans-serif' },
    shape: 'dot' as const,
    size: 20,
    borderWidth: 2,
    shadow: { enabled: true, color: 'rgba(0,0,0,0.08)', size: 6 },
  }))

  const visEdges = props.edges.map(e => ({
    id: e.id,
    from: e.sourceId,
    to: e.targetId,
    label: e.label,
    arrows: 'to' as const,
    color: { color: '#C4B8A8', highlight: '#D97B2B', hover: '#D97B2B' },
    font: { size: 10, color: '#8B7E74', strokeWidth: 3, strokeColor: '#FAF7F2' },
    width: Math.max(1, Math.min(4, e.weight)),
    smooth: { enabled: true, type: 'curvedCW', roundness: 0.2 },
  }))

  return { nodes: visNodes, edges: visEdges }
}

function initNetwork() {
  if (!containerRef.value) return

  const data = buildVisData()
  const options = {
    physics: {
      barnesHut: {
        gravitationalConstant: -3000,
        centralGravity: 0.3,
        springLength: 150,
        springConstant: 0.04,
        damping: 0.09,
      },
      stabilization: { iterations: 150 },
    },
    interaction: {
      hover: true,
      tooltipDelay: 200,
      navigationButtons: false,
      keyboard: false,
    },
    layout: {
      improvedLayout: true,
    },
  }

  network = new Network(containerRef.value, data, options)

  network.on('click', (params: any) => {
    if (params.nodes && params.nodes.length > 0) {
      const nodeId = params.nodes[0]
      const node = props.nodes.find(n => n.id === nodeId)
      selectedNode.value = node || null
    } else {
      selectedNode.value = null
    }
  })

  network.on('doubleClick', (params: any) => {
    if (params.nodes && params.nodes.length > 0) {
      network?.focus(params.nodes[0], { scale: 1.5, animation: true })
    }
  })
}

function fitView() {
  network?.fit({ animation: true })
}

function handleSearch(query: string) {
  if (!network || !query.trim()) return
  const matches = props.nodes.filter(n =>
    n.label.toLowerCase().includes(query.toLowerCase()) ||
    (n.description && n.description.toLowerCase().includes(query.toLowerCase()))
  )
  if (matches.length > 0) {
    network.selectNodes(matches.map(n => n.id))
    network.focus(matches[0].id, { scale: 1.3, animation: true })
  }
}

function handleFilter(types: string[]) {
  if (!network) return
  if (types.length === 0) {
    // Show all
    const allNodeIds = props.nodes.map(n => n.id)
    network.selectNodes(allNodeIds)
    setTimeout(() => network?.unselectAll(), 100)
    return
  }
  const matches = props.nodes.filter(n => types.includes(n.nodeType))
  const hiddenIds = props.nodes.filter(n => !types.includes(n.nodeType)).map(n => n.id)
  // Dim non-matching nodes
  const updates = props.nodes.map(n => ({
    id: n.id,
    opacity: types.includes(n.nodeType) ? 1.0 : 0.15,
  }))
  // @ts-ignore vis-network DataGroup accepts opacity
  network.body.data.nodes.update(updates)
}

watch(() => [props.nodes, props.edges], () => {
  if (network) {
    const data = buildVisData()
    network.setData(data)
  }
}, { deep: true })

onMounted(() => {
  if (props.nodes.length > 0) initNetwork()
})

watch(() => props.nodes.length, (newLen) => {
  if (newLen > 0 && !network) initNetwork()
})

onUnmounted(() => {
  network?.destroy()
  network = null
})
</script>

<style scoped>
.graph-canvas-wrapper { position: relative; width: 100%; height: 100%; }
.graph-container { width: 100%; height: 100%; background: #FDF9F3; border-radius: 12px; border: 1px solid #E8DDD0; }
</style>
