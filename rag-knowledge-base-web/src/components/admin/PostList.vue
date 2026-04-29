<template>
  <div class="admin-posts">
    <div class="posts-header">
      <h2 class="section-title">文章管理</h2>
      <button class="btn-primary" @click="$emit('create')">新建文章</button>
    </div>

    <div v-if="posts.length === 0" class="empty-state">暂无文章，点击「新建文章」开始创作</div>
    <div v-if="errorMessage" class="list-error">{{ errorMessage }}</div>

    <div class="post-list" v-else>
      <div v-for="post in posts" :key="post.id" class="post-row">
        <div class="post-info">
          <span :class="['status-badge', post.status]">{{ statusLabel(post.status) }}</span>
          <span v-if="post.isTop" class="top-badge">置顶</span>
          <span class="post-title">{{ post.title }}</span>
          <span class="post-meta">{{ formatDate(post.updatedAt) }}</span>
        </div>
        <div class="post-actions">
          <button class="action-btn" @click="$emit('edit', post)" title="编辑">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </button>
          <button v-if="post.status === 'draft'" class="action-btn publish" @click="doPublish(post.id)" title="发布">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
          </button>
          <button v-if="post.status === 'published'" class="action-btn" @click="doUnpublish(post.id)" title="取消发布">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18.36 6.64a9 9 0 1 1-12.73 0"/><line x1="12" y1="2" x2="12" y2="12"/></svg>
          </button>
          <button class="action-btn danger" @click="doDelete(post.id)" title="删除">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      </div>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button :disabled="page <= 1" @click="page--; load()">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="page++; load()">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAdmin } from '../../composables/useAdmin'
import type { Article } from '../../types/blog'

defineEmits<{
  (e: 'create'): void
  (e: 'edit', article: Article): void
}>()

const { listAllPosts, deletePost, publishPost, unpublishPost } = useAdmin()

const posts = ref<Article[]>([])
const page = ref(1)
const total = ref(0)
const totalPages = ref(1)
const errorMessage = ref('')

function statusLabel(status: string): string {
  return status === 'draft' ? '草稿' : status === 'published' ? '已发布' : '已归档'
}

function formatDate(ts: number): string {
  return new Date(ts).toLocaleDateString('zh-CN')
}

async function load() {
  errorMessage.value = ''
  try {
    const result = await listAllPosts(page.value)
    if (result) {
      posts.value = result.items
      total.value = result.total
      totalPages.value = Math.ceil(result.total / 20)
    }
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '加载文章列表失败')
  }
}

async function doDelete(id: string) {
  if (confirm('确定删除此文章？')) {
    try {
      errorMessage.value = ''
      await deletePost(id)
      load()
    } catch (error) {
      errorMessage.value = getErrorMessage(error, '删除文章失败')
    }
  }
}

async function doPublish(id: string) {
  try {
    errorMessage.value = ''
    await publishPost(id)
    load()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '发布文章失败')
  }
}

async function doUnpublish(id: string) {
  try {
    errorMessage.value = ''
    await unpublishPost(id)
    load()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '取消发布失败')
  }
}

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

onMounted(load)
</script>

<style scoped>
.admin-posts { padding: 24px; }

.posts-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.section-title { font-size: 18px; font-weight: 600; color: #3D3028; margin: 0; }
.btn-primary {
  padding: 6px 16px; border-radius: 8px; border: none;
  background: #D97B2B; color: #FFFFFF; font-size: 13px; font-weight: 500; cursor: pointer;
}
.btn-primary:hover { background: #C06A1E; }

.list-error {
  padding: 10px 12px; margin-bottom: 12px; border-radius: 8px;
  background: #FFF0F0; color: #C93A3A; border: 1px solid #FFD0D0;
  font-size: 13px;
}

.post-list { display: flex; flex-direction: column; gap: 8px; }
.post-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #FFFFFF; border: 1px solid #E8DDD0;
  border-radius: 10px; transition: border-color 0.2s;
}
.post-row:hover { border-color: #D4C8BA; }

.post-info { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.status-badge {
  font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px; flex-shrink: 0;
}
.status-badge.draft { background: #F5F0EB; color: #8B7E74; }
.status-badge.published { background: #E8F5E9; color: #4CAF50; }
.status-badge.archived { background: #F5F0EB; color: #B8A898; }
.top-badge { font-size: 11px; color: #FFFFFF; background: #E8913A; padding: 2px 8px; border-radius: 4px; flex-shrink: 0; }
.post-title { font-size: 14px; color: #3D3028; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.post-meta { font-size: 12px; color: #B8A898; flex-shrink: 0; }

.post-actions { display: flex; gap: 4px; flex-shrink: 0; margin-left: 12px; }
.action-btn {
  width: 30px; height: 30px; border: 1px solid #E8DDD0; border-radius: 6px;
  background: #FFFFFF; color: #8B7E74; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: all 0.2s;
}
.action-btn:hover { border-color: #D97B2B; color: #D97B2B; }
.action-btn.danger:hover { border-color: #E85D5D; color: #E85D5D; }
.action-btn.publish { color: #4CAF50; }
.action-btn.publish:hover { border-color: #4CAF50; }

.pagination {
  display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 20px;
}
.pagination button {
  padding: 6px 14px; border: 1px solid #E8DDD0; border-radius: 6px;
  background: #FFFFFF; color: #6B5E52; font-size: 13px; cursor: pointer;
}
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
.pagination span { font-size: 13px; color: #8B7E74; }

.empty-state { text-align: center; padding: 40px; color: #B8A898; }
</style>
