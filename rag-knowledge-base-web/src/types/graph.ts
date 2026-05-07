export interface Graph {
  id: string
  title: string
  sourceFile: string | null
  description: string | null
  nodeCount: number
  edgeCount: number
  status: 'generating' | 'ready' | 'failed'
  createdAt: number
  updatedAt: number
}

export interface GraphNode {
  id: string
  graphId: string
  label: string
  nodeType: string
  description: string | null
  groupName: string | null
  properties: Record<string, string> | null
  createdAt: number
  updatedAt: number
}

export interface GraphEdge {
  id: string
  graphId: string
  sourceId: string
  targetId: string
  label: string
  edgeType: string
  weight: number
  properties: Record<string, string> | null
  createdAt: number
  updatedAt: number
}

export interface GraphDetail {
  graph: Graph
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface GraphSuggestion {
  label: string
  type: string
  weight: number
  reason: string
}
