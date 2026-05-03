import { ref } from 'vue'
import type { Deck, Card, DeckStats, DeckDetail, Difficulty, ReviewGrade } from '../types/flashcard'

export function useFlashcard() {
  const decks = ref<Deck[]>([])
  const stats = ref<DeckStats | null>(null)
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

  async function submitReview(cardId: string, grade: ReviewGrade): Promise<Card | null> {
    try {
      const res = await fetch('/api/flashcard/review', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cardId, grade }),
      })
      if (!res.ok) return null
      return await res.json()
    } catch { return null }
  }

  async function submitBatchReview(reviews: { cardId: string; grade: ReviewGrade }[]): Promise<number> {
    if (reviews.length === 0) return 0
    try {
      const res = await fetch('/api/flashcard/review/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(reviews),
      })
      if (!res.ok) return 0
      const data = await res.json()
      return data.updated ?? 0
    } catch { return 0 }
  }

  async function getStudyCards(deckId: string, mode: 'due' | 'all' = 'due'): Promise<Card[]> {
    try {
      const param = mode === 'all' ? '?mode=all' : ''
      const res = await fetch(`/api/flashcard/study/${deckId}${param}`)
      if (res.ok) return await res.json()
      return []
    } catch { return [] }
  }

  async function loadStats(): Promise<void> {
    try {
      const res = await fetch('/api/flashcard/stats')
      if (res.ok) stats.value = await res.json()
    } catch { /* ignore */ }
  }

  async function exportDeckPdf(deckId: string): Promise<void> {
    try {
      const res = await fetch(`/api/flashcard/decks/${deckId}/export`)
      if (!res.ok) return
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `flashcards-${deckId}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    } catch { /* ignore */ }
  }

  return {
    decks, stats, loading,
    generateFromFile, generateFromText,
    loadDecks, getDeck, updateCard,
    deleteDeck, deleteCard, submitReview, submitBatchReview,
    getStudyCards, loadStats, exportDeckPdf,
  }
}
