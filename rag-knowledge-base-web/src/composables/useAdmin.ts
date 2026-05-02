import { ref } from 'vue'
import type { Article, BlogCategory, PageResult } from '../types/blog'

const adminToken = ref<string | null>(localStorage.getItem('adminToken'))
const isLoggedIn = ref(!!adminToken.value)

export class AdminApiError extends Error {
  constructor(message: string, public status?: number) {
    super(message)
    this.name = 'AdminApiError'
  }
}

function authHeaders(): Record<string, string> {
  return adminToken.value ? { 'X-Admin-Token': adminToken.value } : {}
}

async function readErrorMessage(res: Response): Promise<string> {
  try {
    const text = await res.text()
    if (!text) return `请求失败（HTTP ${res.status}）`
    try {
      const data = JSON.parse(text)
      return data.error || data.message || text
    } catch {
      return text
    }
  } catch {
    return `请求失败（HTTP ${res.status}）`
  }
}

async function parseJsonOrThrow<T>(res: Response): Promise<T> {
  if (res.ok) return await res.json()

  if (res.status === 401) {
    adminToken.value = null
    localStorage.removeItem('adminToken')
    isLoggedIn.value = false
  }

  throw new AdminApiError(await readErrorMessage(res), res.status)
}

async function ensureOk(res: Response): Promise<boolean> {
  if (res.ok) return true

  if (res.status === 401) {
    adminToken.value = null
    localStorage.removeItem('adminToken')
    isLoggedIn.value = false
  }

  throw new AdminApiError(await readErrorMessage(res), res.status)
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
      return await parseJsonOrThrow<PageResult<Article>>(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法获取文章列表')
    }
  }

  async function createPost(data: { title: string; content: string; contentType?: string; category?: string; tags?: string[]; summary?: string; coverImage?: string }): Promise<Article | null> {
    try {
      const res = await fetch('/api/admin/posts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(data),
      })
      return await parseJsonOrThrow<Article>(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法创建文章')
    }
  }

  async function updatePost(id: string, data: { title: string; content: string; category?: string; tags?: string[]; summary?: string; coverImage?: string }): Promise<Article | null> {
    try {
      const res = await fetch(`/api/admin/posts/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(data),
      })
      return await parseJsonOrThrow<Article>(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法更新文章')
    }
  }

  async function deletePost(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/admin/posts/${id}`, { method: 'DELETE', headers: authHeaders() })
      return await ensureOk(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法删除文章')
    }
  }

  async function publishPost(id: string): Promise<Article | null> {
    try {
      const res = await fetch(`/api/admin/posts/${id}/publish`, { method: 'POST', headers: authHeaders() })
      return await parseJsonOrThrow<Article>(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法发布文章')
    }
  }

  async function unpublishPost(id: string): Promise<Article | null> {
    try {
      const res = await fetch(`/api/admin/posts/${id}/unpublish`, { method: 'POST', headers: authHeaders() })
      return await parseJsonOrThrow<Article>(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法取消发布文章')
    }
  }

  async function createCategory(data: { name: string; slug: string; description?: string }): Promise<BlogCategory | null> {
    try {
      const res = await fetch('/api/admin/categories', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(data),
      })
      return await parseJsonOrThrow<BlogCategory>(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法创建分类')
    }
  }

  async function updateCategory(id: string, data: { name: string; slug: string; description?: string }): Promise<BlogCategory | null> {
    try {
      const res = await fetch(`/api/admin/categories/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(data),
      })
      return await parseJsonOrThrow<BlogCategory>(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法更新分类')
    }
  }

  async function deleteCategory(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/admin/categories/${id}`, { method: 'DELETE', headers: authHeaders() })
      return await ensureOk(res)
    } catch (e) {
      if (e instanceof AdminApiError) throw e
      throw new AdminApiError('网络错误：无法删除分类')
    }
  }

  return {
    adminToken, isLoggedIn,
    authHeaders,
    login, logout,
    listAllPosts, createPost, updatePost, deletePost, publishPost, unpublishPost,
    createCategory, updateCategory, deleteCategory,
  }
}
