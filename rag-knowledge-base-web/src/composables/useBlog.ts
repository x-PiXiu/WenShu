import { ref } from 'vue'
import type { Article, BlogCategory, PageResult, BlogStats } from '../types/blog'

const blogStats = ref<BlogStats | null>(null)

export function useBlog() {

  async function listPosts(page = 1, size = 10, category?: string, tag?: string): Promise<PageResult<Article> | null> {
    try {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (category) params.set('category', category)
      if (tag) params.set('tag', tag)
      const res = await fetch(`/api/blog/posts?${params}`)
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function getPost(slug: string): Promise<Article | null> {
    try {
      const res = await fetch(`/api/blog/posts/${slug}`)
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function listCategories(): Promise<BlogCategory[]> {
    try {
      const res = await fetch('/api/blog/categories')
      return res.ok ? await res.json() : []
    } catch { return [] }
  }

  async function listTags(): Promise<string[]> {
    try {
      const res = await fetch('/api/blog/tags')
      return res.ok ? await res.json() : []
    } catch { return [] }
  }

  async function searchPosts(query: string): Promise<Article[]> {
    try {
      const res = await fetch(`/api/blog/search?q=${encodeURIComponent(query)}`)
      return res.ok ? await res.json() : []
    } catch { return [] }
  }

  async function fetchStats(): Promise<void> {
    try {
      const res = await fetch('/api/blog/stats')
      if (res.ok) blogStats.value = await res.json()
    } catch { /* ignore */ }
  }

  return { blogStats, listPosts, getPost, listCategories, listTags, searchPosts, fetchStats }
}
