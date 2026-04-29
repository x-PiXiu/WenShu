<template>
  <aside class="blog-sidebar">
    <div class="sidebar-section" v-if="categories.length">
      <h3 class="section-title">分类</h3>
      <ul class="category-list">
        <li v-for="cat in categories" :key="cat.id"
            :class="['category-item', { active: selectedCategory === cat.name }]"
            @click="$emit('select-category', cat.name)">
          {{ cat.name }}
        </li>
      </ul>
    </div>

    <div class="sidebar-section" v-if="tags.length">
      <h3 class="section-title">标签</h3>
      <div class="tag-cloud">
        <span v-for="tag in tags" :key="tag"
              :class="['sidebar-tag', { active: selectedTag === tag }]"
              @click="$emit('select-tag', tag)">
          #{{ tag }}
        </span>
      </div>
    </div>

    <div class="sidebar-section" v-if="stats">
      <h3 class="section-title">统计</h3>
      <div class="stats-list">
        <div class="stat-row"><span>文章</span><span>{{ stats.totalArticles }}</span></div>
        <div class="stat-row"><span>分类</span><span>{{ stats.totalCategories }}</span></div>
        <div class="stat-row"><span>标签</span><span>{{ stats.totalTags }}</span></div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useBlog } from '../../composables/useBlog'
import type { BlogCategory } from '../../types/blog'

defineProps<{
  selectedCategory: string | null
  selectedTag: string | null
}>()

defineEmits<{
  (e: 'select-category', cat: string): void
  (e: 'select-tag', tag: string): void
}>()

const { listCategories, listTags, fetchStats, blogStats } = useBlog()

const categories = ref<BlogCategory[]>([])
const tags = ref<string[]>([])
const stats = blogStats

onMounted(async () => {
  categories.value = await listCategories()
  tags.value = await listTags()
  fetchStats()
})
</script>

<style scoped>
.blog-sidebar { width: 240px; flex-shrink: 0; }
.sidebar-section {
  background: #FFFFFF; border: 1px solid #E8DDD0; border-radius: 12px;
  padding: 16px 20px; margin-bottom: 16px;
}
.section-title { font-size: 14px; font-weight: 600; color: #3D3028; margin: 0 0 12px; }

.category-list { list-style: none; padding: 0; margin: 0; }
.category-item {
  padding: 6px 0; font-size: 13px; color: #6B5E52; cursor: pointer;
  border-bottom: 1px solid #F5F0EB; transition: color 0.2s;
}
.category-item:last-child { border-bottom: none; }
.category-item:hover { color: #D97B2B; }
.category-item.active { color: #D97B2B; font-weight: 500; }

.tag-cloud { display: flex; flex-wrap: wrap; gap: 6px; }
.sidebar-tag {
  font-size: 12px; color: #A89888; background: #F5F0EB; padding: 3px 10px;
  border-radius: 10px; cursor: pointer; transition: all 0.2s;
}
.sidebar-tag:hover { color: #D97B2B; background: #FEF3E8; }
.sidebar-tag.active { color: #D97B2B; background: #FEF3E8; font-weight: 500; }

.stats-list { display: flex; flex-direction: column; gap: 6px; }
.stat-row { display: flex; justify-content: space-between; font-size: 13px; color: #8B7E74; }
.stat-row span:last-child { font-weight: 500; color: #3D3028; }
</style>
