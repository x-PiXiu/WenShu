import { ref } from 'vue'
import type { Graph, GraphDetail, GraphSuggestion } from '../types/graph'

export interface GenerateProgress {
  processed: number
  total: number
  failed: number
  currentFile: string
}

export function useGraph() {
  const graphs = ref<Graph[]>([])
  const loading = ref(false)
  const generateProgress = ref<GenerateProgress | null>(null)

  async function generate(sourceFile: string, maxNodes = 30, maxEdges = 50): Promise<{ graphId: string; status: string; nodeCount: number; edgeCount: number } | null> {
    loading.value = true
    try {
      const res = await fetch('/api/graph/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sourceFile, maxNodes, maxEdges }),
      })
      if (!res.ok) throw new Error(await res.text())
      return await res.json()
    } catch (e) {
      console.error('Failed to generate graph:', e)
      return null
    } finally {
      loading.value = false
    }
  }

  /**
   * Generate from all docs via SSE — calls onProgress for each document, returns final result.
   * No polling needed.
   */
  async function generateAll(
    maxNodesPerDoc = 20,
    maxEdgesPerDoc = 30,
    onProgress?: (p: GenerateProgress) => void,
  ): Promise<{ graphId: string; nodeCount: number; edgeCount: number } | null> {
    loading.value = true
    generateProgress.value = null
    try {
      const res = await fetch('/api/graph/generate-all', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ maxNodesPerDoc, maxEdgesPerDoc }),
      })

      if (!res.ok) {
        throw new Error(await res.text())
      }

      const reader = res.body?.getReader()
      if (!reader) return null
      const decoder = new TextDecoder()
      let buffer = ''
      let finalResult: { graphId: string; nodeCount: number; edgeCount: number } | null = null

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        let i = 0
        while (i < lines.length) {
          const line = lines[i]
          if (line === 'event: progress' && i + 1 < lines.length && lines[i + 1].startsWith('data: ')) {
            const data = JSON.parse(lines[i + 1].slice(6))
            generateProgress.value = data
            onProgress?.(data)
            i += 2
          } else if (line === 'event: done' && i + 1 < lines.length && lines[i + 1].startsWith('data: ')) {
            const data = JSON.parse(lines[i + 1].slice(6))
            finalResult = data
            i += 2
          } else {
            i++
          }
        }
      }

      return finalResult
    } catch (e) {
      console.error('Failed to generate all graphs:', e)
      return null
    } finally {
      loading.value = false
      generateProgress.value = null
    }
  }

  async function loadGraphs(): Promise<void> {
    try {
      const res = await fetch('/api/graph/graphs')
      if (res.ok) graphs.value = await res.json()
    } catch { /* ignore */ }
  }

  async function getDetail(id: string): Promise<GraphDetail | null> {
    try {
      const res = await fetch(`/api/graph/graphs/${id}`)
      if (!res.ok) return null
      return await res.json()
    } catch { return null }
  }

  async function deleteGraph(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/graph/graphs/${id}`, { method: 'DELETE' })
      return res.ok
    } catch { return false }
  }

  async function askQuestion(graphId: string, question: string): Promise<string | null> {
    try {
      const res = await fetch(`/api/graph/graphs/${graphId}/qa`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question }),
      })
      if (!res.ok) return null
      const data = await res.json()
      return data.answer
    } catch { return null }
  }

  async function suggestRelations(graphId: string, sourceNodeId: string, targetNodeId: string): Promise<GraphSuggestion[] | null> {
    try {
      const res = await fetch(`/api/graph/graphs/${graphId}/suggest`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sourceNodeId, targetNodeId }),
      })
      if (!res.ok) return null
      const data = await res.json()
      return data.suggestions
    } catch { return null }
  }

  async function exportGraph(graphId: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/graph/graphs/${graphId}/export`)
      if (!res.ok) return false
      const data = await res.json()
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `graph-${graphId}.json`
      a.click()
      URL.revokeObjectURL(url)
      return true
    } catch { return false }
  }

  async function mergeDocument(graphId: string, sourceFile: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/graph/graphs/${graphId}/merge`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sourceFile }),
      })
      return res.ok
    } catch { return false }
  }

  return {
    graphs, loading, generateProgress,
    generate, generateAll, mergeDocument,
    loadGraphs, getDetail, deleteGraph,
    askQuestion, suggestRelations, exportGraph,
  }
}
