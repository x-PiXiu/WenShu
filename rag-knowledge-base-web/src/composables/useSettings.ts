import { ref } from 'vue'
import type { AppSettings, KnowledgeStats } from '../types/chat'

export function useSettings() {
  const settings = ref<AppSettings | null>(null)
  const stats = ref<KnowledgeStats | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const message = ref('')

  async function fetchSettings(): Promise<void> {
    loading.value = true
    try {
      const res = await fetch('/api/settings')
      if (res.ok) {
        settings.value = await res.json()
      }
    } catch {
      message.value = 'Failed to load settings'
    } finally {
      loading.value = false
    }
  }

  async function fetchStats(): Promise<void> {
    try {
      const res = await fetch('/api/knowledge/stats')
      if (res.ok) {
        stats.value = await res.json()
      }
    } catch {
      stats.value = null
    }
  }

  async function saveSettings(newSettings: AppSettings): Promise<boolean> {
    saving.value = true
    message.value = ''
    try {
      const res = await fetch('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newSettings),
      })
      const data = await res.json()
      if (res.ok) {
        message.value = data.message || 'Settings saved'
        await fetchSettings()
        return true
      } else {
        message.value = data.error || 'Save failed'
        return false
      }
    } catch (e: any) {
      message.value = e.message
      return false
    } finally {
      saving.value = false
    }
  }

  async function reindex(): Promise<boolean> {
    saving.value = true
    message.value = 'Reindexing...'
    try {
      const res = await fetch('/api/settings/reindex', { method: 'POST' })
      const data = await res.json()
      if (res.ok) {
        message.value = `Reindexed: ${data.documents} docs, ${data.segments} segments`
        await fetchStats()
        return true
      } else {
        message.value = data.error || 'Reindex failed'
        return false
      }
    } catch (e: any) {
      message.value = e.message
      return false
    } finally {
      saving.value = false
    }
  }

  return { settings, stats, loading, saving, message, fetchSettings, fetchStats, saveSettings, reindex }
}
