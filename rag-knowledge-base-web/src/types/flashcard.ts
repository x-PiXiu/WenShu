export interface Deck {
  id: string
  title: string
  description: string | null
  sourceFile: string | null
  cardCount: number
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
  createdAt: number
  updatedAt: number
}

export interface DeckDetail {
  deck: Deck
  cards: Card[]
}

export type Difficulty = 'basic' | 'intermediate' | 'advanced'
