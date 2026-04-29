import { ref } from 'vue'
import type { Article, BlogCategory, PageResult } from '../types/blog'

const adminToken = ref<string | null>(localStorage.getItem('adminToken'))
const isLoggedIn = ref(!!adminToken.value)

function authHeaders(): Record<string, string> {
  return adminToken.value ? { 'X-Admin-Token': adminToken.value } : {}
}

export function useAdmin() {

  async function login(password: string): Promise<boolean> {
    try {
      const res = await fetch('/api/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      })
      if (res.ok) {
        const data = await res.json()
        adminToken.value = data.token
        localStorage.setItem('adminToken', data.token)
        isLoggedIn.value = true
        return true
      }
      return false
    } catch { return false }
  }

  function logout() {
    adminToken.value = null
    localStorage.removeItem('adminToken')
    isLoggedIn.value = false
  }

  async function listAllPosts(page = 1, size = 20): Promise<PageResult<Article> | null> {
    try {
      const res = await fetch(`/api/admin/posts?page=${page}&size=${size}`, { headers: authHeaders() })
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function createPost(data: { title: string; content: string; contentType?: string; category?: string; tags?: string[] }): Promise<Article | null> {
    try {
      const res = await fetch('/api/admin/posts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(data),
      })
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function updatePost(id: string, data: { title: string; content: string; category?: string; tags?: string[] }): Promise<Article | null> {
    try {
      const res = await fetch(`/api/admin/posts/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(data),
      })
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function deletePost(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/admin/posts/${id}`, { method: 'DELETE', headers: authHeaders() })
      return res.ok
    } catch { return false }
  }

  async function publishPost(id: string): Promise<Article | null> {
    try {
      const res = await fetch(`/api/admin/posts/${id}/publish`, { method: 'POST', headers: authHeaders() })
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function unpublishPost(id: string): Promise<Article | null> {
    try {
      const res = await fetch(`/api/admin/posts/${id}/unpublish`, { method: 'POST', headers: authHeaders() })
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function createCategory(data: { name: string; slug: string; description?: string }): Promise<BlogCategory | null> {
    try {
      const res = await fetch('/api/admin/categories', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(data),
      })
      return res.ok ? await res.json() : null
    } catch { return null }
  }

  async function deleteCategory(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/admin/categories/${id}`, { method: 'DELETE', headers: authHeaders() })
      return res.ok
    } catch { return false }
  }

  return {
    adminToken, isLoggedIn,
    login, logout,
    listAllPosts, createPost, updatePost, deletePost, publishPost, unpublishPost,
    createCategory, deleteCategory,
  }
}
