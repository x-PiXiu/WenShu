<template>
  <div class="blog-page">
    <div class="blog-container">
      <div class="blog-main">
        <!-- Search -->
        <div class="blog-search" v-if="!selectedCategory && !selectedTag">
          <input type="text" v-model="searchQuery" placeholder="搜索文章..." class="search-input" @keyup.enter="doSearch" />
        </div>

        <!-- Category/Tag filter banner -->
        <div class="filter-banner" v-if="selectedCategory || selectedTag">
          <span v-if="selectedCategory">分类: {{ selectedCategory }}</span>
          <span v-if="selectedTag">标签: #{{ selectedTag }}</span>
          <button class="clear-filter" @click="clearFilter">清除筛选</button>
        </div>

        <!-- Article Cards -->
        <div v-if="searchResults.length > 0" class="article-list">
          <div v-for="article in searchResults" :key="article.id" class="article-card" @click="openArticle(article.slug)">
            <div class="card-header">
              <span v-if="article.isTop" class="top-badge">置顶</span>
              <span class="card-category" v-if="article.category">{{ article.category }}</span>
            </div>
            <h2 class="card-title">{{ article.title }}</h2>
            <p class="card-summary">{{ article.summary || article.content.substring(0, 150) + '...' }}</p>
            <div class="card-meta">
              <span class="meta-date">{{ formatDate(article.publishedAt || article.createdAt) }}</span>
              <span class="meta-words">{{ Math.ceil(article.wordCount / 500) }} min read</span>
              <span class="meta-views">{{ article.viewCount }} views</span>
            </div>
            <div class="card-tags" v-if="article.tags && article.tags.length">
              <span v-for="tag in article.tags" :key="tag" class="tag" @click.stop="filterByTag(tag)">#{{ tag }}</span>
            </div>
          </div>
        </div>

        <div v-else-if="!loading" class="empty-state">
          <p>暂无文章</p>
        </div>

        <!-- Pagination -->
        <div class="pagination" v-if="totalPages > 1">
          <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
          <span class="page-info">{{ page }} / {{ totalPages }}</span>
          <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
        </div>
      </div>

      <!-- Sidebar -->
      <BlogSidebar
        :selected-category="selectedCategory"
        :selected-tag="selectedTag"
        @select-category="filterByCategory"
        @select-tag="filterByTag"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useBlog } from '../../composables/useBlog'
import type { Article } from '../../types/blog'
import BlogSidebar from './BlogSidebar.vue'

const emit = defineEmits<{ (e: 'open-article', slug: string): void }>()

const { listPosts, searchPosts } = useBlog()

const articles = ref<Article[]>([])
const searchResults = ref<Article[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const size = ref(10)
const searchQuery = ref('')
const selectedCategory = ref<string | null>(null)
const selectedTag = ref<string | null>(null)

const totalPages = ref(1)

function formatDate(ts: number): string {
  return new Date(ts).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

async function loadPosts() {
  loading.value = true
  const result = await listPosts(page.value, size.value, selectedCategory.value || undefined, selectedTag.value || undefined)
  if (result) {
    articles.value = result.items
    searchResults.value = result.items
    total.value = result.total
    totalPages.value = Math.ceil(result.total / size.value)
  }
  loading.value = false
}

async function doSearch() {
  if (!searchQuery.value.trim()) { loadPosts(); return }
  loading.value = true
  const results = await searchPosts(searchQuery.value)
  searchResults.value = results
  total.value = results.length
  totalPages.value = 1
  loading.value = false
}

function filterByCategory(cat: string) {
  selectedCategory.value = selectedCategory.value === cat ? null : cat
  selectedTag.value = null
  page.value = 1
  loadPosts()
}

function filterByTag(tag: string) {
  selectedTag.value = selectedTag.value === tag ? null : tag
  selectedCategory.value = null
  page.value = 1
  loadPosts()
}

function clearFilter() {
  selectedCategory.value = null
  selectedTag.value = null
  page.value = 1
  loadPosts()
}

function goPage(p: number) {
  page.value = p
  loadPosts()
}

function openArticle(slug: string) {
  emit('open-article', slug)
}

onMounted(loadPosts)
</script>

<style scoped>
.blog-page { flex: 1; overflow-y: auto; background: #F8F5EF; }
.blog-container { max-width: 1100px; margin: 0 auto; padding: 24px 32px; display: flex; gap: 32px; }
.blog-main { flex: 1; min-width: 0; }

.blog-search { margin-bottom: 20px; }
.search-input {
  width: 100%; padding: 10px 16px; border: 1px solid #E8DDD0; border-radius: 10px;
  font-size: 14px; background: #FFFFFF; color: #3D3028; outline: none;
  transition: border-color 0.2s;
}
.search-input:focus { border-color: #D97B2B; }

.filter-banner {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 16px; background: #FEF3E8; border-radius: 8px;
  margin-bottom: 16px; font-size: 13px; color: #D97B2B;
}
.clear-filter {
  margin-left: auto; border: none; background: none; color: #A89888;
  cursor: pointer; font-size: 12px;
}
.clear-filter:hover { color: #D97B2B; }

.article-list { display: flex; flex-direction: column; gap: 16px; }
.article-card {
  background: #FFFFFF; border: 1px solid #E8DDD0; border-radius: 12px;
  padding: 20px 24px; cursor: pointer; transition: all 0.2s;
}
.article-card:hover { border-color: #D97B2B; box-shadow: 0 2px 12px rgba(217,123,43,0.08); }

.card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.top-badge {
  font-size: 11px; font-weight: 600; color: #FFFFFF; background: #E8913A;
  padding: 2px 8px; border-radius: 4px;
}
.card-category {
  font-size: 12px; color: #D97B2B; background: #FEF3E8;
  padding: 2px 10px; border-radius: 10px;
}

.card-title { font-size: 18px; font-weight: 600; color: #3D3028; margin: 0 0 8px; }
.card-summary { font-size: 14px; color: #8B7E74; line-height: 1.6; margin: 0 0 12px; }

.card-meta { display: flex; gap: 16px; font-size: 12px; color: #B8A898; margin-bottom: 8px; }
.card-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag {
  font-size: 12px; color: #A89888; background: #F5F0EB; padding: 2px 10px;
  border-radius: 10px; cursor: pointer; transition: all 0.2s;
}
.tag:hover { color: #D97B2B; background: #FEF3E8; }

.pagination {
  display: flex; align-items: center; justify-content: center; gap: 16px;
  margin-top: 24px; padding: 16px 0;
}
.pagination button {
  padding: 6px 16px; border: 1px solid #E8DDD0; border-radius: 8px;
  background: #FFFFFF; color: #6B5E52; cursor: pointer; font-size: 13px;
}
.pagination button:hover:not(:disabled) { border-color: #D97B2B; color: #D97B2B; }
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
.page-info { font-size: 13px; color: #8B7E74; }

.empty-state { text-align: center; padding: 60px 20px; color: #B8A898; font-size: 15px; }
</style>
