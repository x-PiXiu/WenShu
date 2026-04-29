// Blog Types

export interface Article {
  id: string
  title: string
  slug: string
  summary: string | null
  content: string
  contentType: string
  category: string | null
  tags: string[]
  coverImage: string | null
  status: 'draft' | 'published' | 'archived'
  isTop: boolean
  viewCount: number
  wordCount: number
  publishedAt: number | null
  createdAt: number
  updatedAt: number
}

export interface BlogCategory {
  id: string
  name: string
  slug: string
  description: string
  sortOrder: number
  createdAt: number
  updatedAt: number
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface BlogStats {
  totalArticles: number
  totalCategories: number
  totalTags: number
  blogTitle: string
  blogDescription: string
}
