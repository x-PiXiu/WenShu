import { ref } from 'vue'
import type { LlmProvider } from '../types/chat'

const providers = ref<LlmProvider[]>([])
const activeProvider = ref<LlmProvider | null>(null)

export function useLlmProviders() {

  async function loadProviders() {
    try {
      const res = await fetch('/api/llm-providers')
      if (res.ok) {
        providers.value = await res.json()
        activeProvider.value = providers.value.find(p => p.isDefault) || providers.value[0] || null
      }
    } catch {
      providers.value = []
    }
  }

  async function createProvider(data: {
    name: string; provider: string; baseUrl: string; apiKey: string; modelName: string;
    temperature: number | null; maxTokens: number | null; streaming: boolean;
  }): Promise<LlmProvider | null> {
    try {
      const res = await fetch('/api/llm-providers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      })
      if (res.ok) {
        const created: LlmProvider = await res.json()
        await loadProviders()
        return created
      }
    } catch { /* ignore */ }
    return null
  }

  async function updateProvider(id: string, data: {
    name: string; provider: string; baseUrl: string; apiKey: string; modelName: string;
    temperature: number | null; maxTokens: number | null; streaming: boolean;
  }): Promise<LlmProvider | null> {
    try {
      const res = await fetch(`/api/llm-providers/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      })
      if (res.ok) {
        const updated: LlmProvider = await res.json()
        await loadProviders()
        return updated
      }
    } catch { /* ignore */ }
    return null
  }

  async function deleteProvider(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/llm-providers/${id}`, { method: 'DELETE' })
      if (res.ok) {
        await loadProviders()
        return true
      }
    } catch { /* ignore */ }
    return false
  }

  async function activateProvider(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/llm-providers/${id}/activate`, { method: 'POST' })
      if (res.ok) {
        await loadProviders()
        return true
      }
    } catch { /* ignore */ }
    return false
  }

  return {
    providers, activeProvider,
    loadProviders, createProvider, updateProvider, deleteProvider, activateProvider,
  }
}
