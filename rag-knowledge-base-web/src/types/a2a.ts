// A2A Protocol Types

export interface AgentCard {
  name: string
  description: string
  url: string
  version: string
  capabilities: {
    streaming: boolean
    pushNotifications: boolean
    stateTransitionHistory: boolean
  }
  skills: AgentSkill[]
}

export interface AgentSkill {
  id: string
  name: string
  description: string
  tags: string[]
}

export interface TaskSource {
  index: number
  text: string
  source: string
  rrfScore: number
  vectorScore: number
}

export interface A2ATask {
  id: string
  skillId: string
  state: 'submitted' | 'working' | 'completed' | 'failed' | 'canceled'
  result: string | null
  error: string | null
  createdAt: number
  question: string | null
  sources: TaskSource[] | null
}

export interface A2ARpcRequest {
  jsonrpc: '2.0'
  id: string
  method: string
  params: Record<string, unknown>
}

export interface A2ATaskResult {
  task: {
    id: string
    status: {
      state: string
      message?: string
    }
    artifacts?: Array<{
      name: string
      parts: Array<{ type: string; text: string }>
    }>
  }
}
