import { ref } from 'vue'
import type { Agent } from '../types/chat'

const agents = ref<Agent[]>([])
const currentAgent = ref<Agent | null>(null)

export function useAgents() {

  async function loadAgents() {
    try {
      const res = await fetch('/api/agents')
      if (res.ok) {
        agents.value = await res.json()
        // Auto-select default agent if none selected
        if (!currentAgent.value && agents.value.length > 0) {
          currentAgent.value = agents.value.find(a => a.isDefault) || agents.value[0]
        }
      }
    } catch {
      agents.value = []
    }
  }

  function selectAgent(agent: Agent | null) {
    currentAgent.value = agent
  }

  async function createAgent(agent: { name: string; description?: string; systemPrompt: string; avatar?: string }): Promise<Agent | null> {
    try {
      const res = await fetch('/api/agents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(agent),
      })
      if (res.ok) {
        const created: Agent = await res.json()
        agents.value.push(created)
        return created
      }
    } catch { /* ignore */ }
    return null
  }

  async function updateAgent(id: string, agent: { name: string; description?: string; systemPrompt: string; avatar?: string }): Promise<Agent | null> {
    try {
      const res = await fetch(`/api/agents/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(agent),
      })
      if (res.ok) {
        const updated: Agent = await res.json()
        const idx = agents.value.findIndex(a => a.id === id)
        if (idx >= 0) agents.value[idx] = updated
        if (currentAgent.value?.id === id) currentAgent.value = updated
        return updated
      }
    } catch { /* ignore */ }
    return null
  }

  async function deleteAgent(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/agents/${id}`, { method: 'DELETE' })
      if (res.ok) {
        agents.value = agents.value.filter(a => a.id !== id)
        if (currentAgent.value?.id === id) {
          currentAgent.value = agents.value.find(a => a.isDefault) || agents.value[0] || null
        }
        return true
      }
    } catch { /* ignore */ }
    return false
  }

  return {
    agents, currentAgent,
    loadAgents, selectAgent,
    createAgent, updateAgent, deleteAgent,
  }
}
