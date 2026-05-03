export interface Deck {
  id: string
  title: string
  description: string | null
  sourceFile: string | null
  cardCount: number
  masteredCount: number
  createdAt: number
  updatedAt: number
}

export interface Card {
  id: string
  deckId: string
  front: string
  back: string
  tags: string[]
  difficulty: number
  reviewCount: number
  correctCount: number
  intervalDays: number
  nextReviewAt: number | null
  lastReviewedAt: number | null
  createdAt: number
  updatedAt: number
}

export interface DeckStats {
  totalCards: number
  dueToday: number
  masteredCount: number
  learningCount: number
  newCount: number
}

export interface DeckDetail {
  deck: Deck
  cards: Card[]
  stats: DeckStats
}

export type Difficulty = 'basic' | 'intermediate' | 'advanced'
export type ReviewGrade = 'remembered' | 'fuzzy' | 'forgot'
