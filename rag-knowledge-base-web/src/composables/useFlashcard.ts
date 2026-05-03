import { ref } from 'vue'
import type { Deck, Card, DeckDetail, Difficulty } from '../types/flashcard'

export function useFlashcard() {
  const decks = ref<Deck[]>([])
  const loading = ref(false)

  async function generateFromFile(file: File, cardCount: number, difficulty: Difficulty): Promise<DeckDetail | null> {
    loading.value = true
    try {
      const form = new FormData()
      form.append('file', file)
      form.append('cardCount', String(cardCount))
      form.append('difficulty', difficulty)
      const res = await fetch('/api/flashcard/generate', { method: 'POST', body: form })
      if (!res.ok) throw new Error(await res.text())
      return await res.json()
    } catch (e) {
      console.error('Failed to generate flashcards:', e)
      return null
    } finally {
      loading.value = false
    }
  }

  async function generateFromText(text: string, title: string, cardCount: number, difficulty: Difficulty): Promise<DeckDetail | null> {
    loading.value = true
    try {
      const res = await fetch('/api/flashcard/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text, title, cardCount, difficulty }),
      })
      if (!res.ok) throw new Error(await res.text())
      return await res.json()
    } catch (e) {
      console.error('Failed to generate flashcards:', e)
      return null
    } finally {
      loading.value = false
    }
  }

  async function loadDecks(): Promise<void> {
    try {
      const res = await fetch('/api/flashcard/decks')
      if (res.ok) decks.value = await res.json()
    } catch { /* ignore */ }
  }

  async function getDeck(id: string): Promise<DeckDetail | null> {
    try {
      const res = await fetch(`/api/flashcard/decks/${id}`)
      if (!res.ok) return null
      return await res.json()
    } catch { return null }
  }

  async function updateCard(id: string, data: { front: string; back: string; tags: string[]; difficulty: number }): Promise<Card | null> {
    try {
      const res = await fetch(`/api/flashcard/cards/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      })
      if (!res.ok) return null
      return await res.json()
    } catch { return null }
  }

  async function deleteDeck(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/flashcard/decks/${id}`, { method: 'DELETE' })
      return res.ok
    } catch { return false }
  }

  async function deleteCard(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/flashcard/cards/${id}`, { method: 'DELETE' })
      return res.ok
    } catch { return false }
  }

  async function exportDeckPdf(deckId: string): Promise<void> {
    try {
      const res = await fetch(`/api/flashcard/decks/${deckId}/export`)
      if (!res.ok) return
      const blob = await res.blob()
      const disposition = res.headers.get('Content-Disposition') || ''
      const match = disposition.match(/filename="?(.+?)"?$/)
      const filename = match ? match[1] : `flashcards-${deckId}.pdf`
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    } catch { /* ignore */ }
  }

  /** Export deck as JSON file (client-side) */
  async function exportDeckJson(deckId: string): Promise<boolean> {
    try {
      const detail = await getDeck(deckId)
      if (!detail) return false
      const exportData = {
        title: detail.deck.title,
        description: detail.deck.description,
        sourceFile: detail.deck.sourceFile,
        cards: detail.cards.map(c => ({
          front: c.front,
          back: c.back,
          tags: c.tags,
          difficulty: c.difficulty,
        })),
      }
      const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${detail.deck.title}.json`
      a.click()
      URL.revokeObjectURL(url)
      return true
    } catch { return false }
  }

  /** Import deck from JSON file */
  async function importDeckFromFile(file: File): Promise<Deck | null> {
    try {
      const text = await file.text()
      const data = JSON.parse(text)
      if (!data.cards || !Array.isArray(data.cards) || data.cards.length === 0) return null
      const res = await fetch('/api/flashcard/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: data.title || file.name.replace('.json', ''),
          description: data.description || '',
          cards: data.cards,
        }),
      })
      if (!res.ok) return null
      return await res.json()
    } catch { return null }
  }

  return {
    decks, loading,
    generateFromFile, generateFromText,
    loadDecks, getDeck, updateCard,
    deleteDeck, deleteCard, exportDeckPdf,
    exportDeckJson, importDeckFromFile,
  }
}
