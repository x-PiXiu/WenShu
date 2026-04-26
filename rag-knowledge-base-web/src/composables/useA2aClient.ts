import { ref } from 'vue'
import type { AgentCard, A2ATask } from '../types/a2a'

export function useA2aClient() {
  const agentCard = ref<AgentCard | null>(null)
  const tasks = ref<A2ATask[]>([])
  const loading = ref(false)

  async function fetchAgentCard(): Promise<void> {
    try {
      const res = await fetch('/a2a/v1/agent')
      if (res.ok) {
        agentCard.value = await res.json()
      }
    } catch {
      agentCard.value = null
    }
  }

  async function fetchTasks(): Promise<void> {
    loading.value = true
    try {
      const res = await fetch('/a2a/v1/tasks')
      if (res.ok) {
        tasks.value = await res.json()
      }
    } catch {
      tasks.value = []
    } finally {
      loading.value = false
    }
  }

  async function sendTask(question: string): Promise<string> {
    const rpcId = 'req-' + Date.now()
    const taskId = 'task-' + crypto.randomUUID()

    const res = await fetch('/a2a/v1/rpc', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        id: rpcId,
        method: 'tasks/send',
        params: {
          id: taskId,
          skillId: 'rag-query',
          input: { question },
        },
      }),
    })

    const data = await res.json()
    const result = data.result?.task
    if (result?.status?.state === 'completed' && result?.artifacts?.length > 0) {
      return result.artifacts[0].parts[0].text
    }
    if (result?.status?.state === 'failed') {
      throw new Error(result.status.message || 'A2A task failed')
    }
    throw new Error('Unexpected A2A response')
  }

  return { agentCard, tasks, loading, fetchAgentCard, fetchTasks, sendTask }
}
