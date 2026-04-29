<template>
  <div class="blog-detail" v-if="article">
    <div class="detail-container">
      <button class="back-btn" @click="$emit('back')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        返回列表
      </button>

      <article class="article-body">
        <header class="article-header">
          <span class="article-category" v-if="article.category">{{ article.category }}</span>
          <h1 class="article-title">{{ article.title }}</h1>
          <div class="article-meta">
            <span>{{ formatDate(article.publishedAt || article.createdAt) }}</span>
            <span>{{ Math.ceil(article.wordCount / 500) }} min read</span>
            <span>{{ article.viewCount + 1 }} views</span>
          </div>
          <div class="article-tags" v-if="article.tags && article.tags.length">
            <span v-for="tag in article.tags" :key="tag" class="tag">#{{ tag }}</span>
          </div>
        </header>

        <MarkdownRenderer :content="article.content" />
      </article>
    </div>
  </div>

  <div class="blog-detail loading-state" v-else>
    <p>加载中...</p>
  </div>
</template>

<script setup lang="ts">
import type { Article } from '../../types/blog'
import MarkdownRenderer from '../MarkdownRenderer.vue'

defineProps<{ article: Article | null }>()
defineEmits<{ (e: 'back'): void }>()

function formatDate(ts: number): string {
  return new Date(ts).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}
</script>

<style scoped>
.blog-detail { flex: 1; overflow-y: auto; background: #FFFFFF; }
.detail-container { max-width: 800px; margin: 0 auto; padding: 32px 40px; }

.back-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px; border: 1px solid #E8DDD0; border-radius: 8px;
  background: none; color: #8B7E74; font-size: 13px; cursor: pointer;
  margin-bottom: 24px; transition: all 0.2s;
}
.back-btn:hover { color: #D97B2B; border-color: #D97B2B; }

.article-header { margin-bottom: 32px; padding-bottom: 24px; border-bottom: 1px solid #E8DDD0; }
.article-category {
  display: inline-block; font-size: 12px; color: #D97B2B; background: #FEF3E8;
  padding: 3px 12px; border-radius: 10px; margin-bottom: 12px;
}
.article-title { font-size: 28px; font-weight: 700; color: #3D3028; margin: 0 0 12px; line-height: 1.3; }
.article-meta { display: flex; gap: 16px; font-size: 13px; color: #B8A898; margin-bottom: 12px; }
.article-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag {
  font-size: 12px; color: #A89888; background: #F5F0EB;
  padding: 2px 10px; border-radius: 10px;
}

.article-body { font-size: 16px; line-height: 1.8; }

.loading-state {
  display: flex; align-items: center; justify-content: center;
  flex: 1; color: #B8A898; font-size: 15px;
}
</style>
