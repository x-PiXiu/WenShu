import { ref } from 'vue'
import { useAdmin } from './useAdmin'

export interface MediaFile {
  id: string
  filename: string
  url: string
  fileSize: number
  mimeType: string
  createdAt?: number
}

export class MediaApiError extends Error {
  constructor(message: string, public status?: number) {
    super(message)
    this.name = 'MediaApiError'
  }
}

export function useMedia() {
  const uploading = ref(false)
  const { adminToken } = useAdmin()

  function authHeaders(): Record<string, string> {
    return adminToken.value ? { 'X-Admin-Token': adminToken.value } : {}
  }

  async function listMedia(): Promise<MediaFile[]> {
    try {
      const res = await fetch('/api/admin/media', { headers: authHeaders() })
      if (res.ok) return await res.json()
      if (res.status === 401) throw new MediaApiError('未登录或登录已过期', 401)
      return []
    } catch (e) {
      if (e instanceof MediaApiError) throw e
      return []
    }
  }

  async function uploadFiles(files: File[]): Promise<MediaFile[]> {
    if (files.length === 0) return []
    uploading.value = true
    try {
      const form = new FormData()
      for (const f of files) form.append('files', f)
      const res = await fetch('/api/admin/media/upload', {
        method: 'POST',
        headers: authHeaders(),
        body: form,
      })
      if (res.ok) return await res.json()
      if (res.status === 401) throw new MediaApiError('未登录或登录已过期', 401)
      throw new MediaApiError('上传失败')
    } finally {
      uploading.value = false
    }
  }

  async function deleteMedia(id: string): Promise<boolean> {
    try {
      const res = await fetch(`/api/admin/media/${id}`, {
        method: 'DELETE',
        headers: authHeaders(),
      })
      if (res.status === 401) throw new MediaApiError('未登录或登录已过期', 401)
      return res.ok
    } catch (e) {
      if (e instanceof MediaApiError) throw e
      return false
    }
  }

  return { uploading, listMedia, uploadFiles, deleteMedia }
}
