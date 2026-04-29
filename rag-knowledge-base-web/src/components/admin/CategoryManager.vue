<template>
  <div class="category-manager">
    <div class="cm-header">
      <h2 class="section-title">分类管理</h2>
      <button class="btn-primary" @click="openCreate" v-if="!editing">新建分类</button>
      <button class="btn-secondary" @click="cancelEdit" v-else>取消编辑</button>
    </div>

    <div v-if="errorMessage" class="cm-error">{{ errorMessage }}</div>

    <!-- Create / Edit Form -->
    <div v-if="editing" class="cm-form">
      <div class="form-row">
        <label class="form-field">
          <span class="form-label">分类名称</span>
          <input type="text" v-model="form.name" placeholder="例如：Java" class="form-input" @input="autoSlug" />
        </label>
        <label class="form-field">
          <span class="form-label">Slug</span>
          <input type="text" v-model="form.slug" placeholder="例如：java" class="form-input" />
        </label>
      </div>
      <label class="form-field">
        <span class="form-label">描述（可选）</span>
        <input type="text" v-model="form.description" placeholder="分类描述" class="form-input" />
      </label>
      <div class="form-actions">
        <button class="btn-primary" @click="saveCategory" :disabled="saving">
          {{ saving ? '保存中...' : (editId ? '更新' : '创建') }}
        </button>
      </div>
    </div>

    <!-- Category List -->
    <div v-if="categories.length === 0 && !editing" class="empty-state">暂无分类，点击「新建分类」开始</div>

    <div class="cm-list" v-else>
      <div v-for="cat in categories" :key="cat.id" class="cm-row">
        <div class="cm-info">
          <span class="cat-name">{{ cat.name }}</span>
          <code class="cat-slug">{{ cat.slug }}</code>
          <span v-if="cat.description" class="cat-desc">{{ cat.description }}</span>
        </div>
        <div class="cm-actions">
          <button class="action-btn" @click="openEdit(cat)" title="编辑">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </button>
          <button class="action-btn danger" @click="doDelete(cat)" title="删除">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAdmin } from '../../composables/useAdmin'
import { useBlog } from '../../composables/useBlog'
import type { BlogCategory } from '../../types/blog'

const { createCategory, updateCategory, deleteCategory } = useAdmin()
const { listCategories } = useBlog()

const categories = ref<BlogCategory[]>([])
const editing = ref(false)
const editId = ref<string | null>(null)
const saving = ref(false)
const errorMessage = ref('')

const form = ref({ name: '', slug: '', description: '' })

async function load() {
  errorMessage.value = ''
  categories.value = await listCategories()
}

function autoSlug() {
  form.value.slug = form.value.name
    .toLowerCase()
    .replace(/[^a-z0-9一-龥]+/g, '-')
    .replace(/^-|-$/g, '')
}

function openCreate() {
  editId.value = null
  form.value = { name: '', slug: '', description: '' }
  editing.value = true
}

function openEdit(cat: BlogCategory) {
  editId.value = cat.id
  form.value = { name: cat.name, slug: cat.slug, description: cat.description }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editId.value = null
  form.value = { name: '', slug: '', description: '' }
}

function getError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function saveCategory() {
  const { name, slug, description } = form.value
  if (!name.trim() || !slug.trim()) {
    errorMessage.value = '名称和 Slug 不能为空'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    if (editId.value) {
      await updateCategory(editId.value, { name: name.trim(), slug: slug.trim(), description: description.trim() || undefined })
    } else {
      await createCategory({ name: name.trim(), slug: slug.trim(), description: description.trim() || undefined })
    }
    cancelEdit()
    await load()
  } catch (error) {
    errorMessage.value = getError(error, '保存分类失败')
  } finally {
    saving.value = false
  }
}

async function doDelete(cat: BlogCategory) {
  if (!confirm(`确定删除分类「${cat.name}」？`)) return
  errorMessage.value = ''
  try {
    await deleteCategory(cat.id)
    await load()
  } catch (error) {
    errorMessage.value = getError(error, '删除分类失败')
  }
}

onMounted(load)
</script>

<style scoped>
.category-manager { padding: 24px; }

.cm-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.section-title { font-size: 18px; font-weight: 600; color: #3D3028; margin: 0; }

.btn-primary {
  padding: 6px 16px; border-radius: 8px; border: none;
  background: #D97B2B; color: #FFFFFF; font-size: 13px; font-weight: 500; cursor: pointer;
}
.btn-primary:hover { background: #C06A1E; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary {
  padding: 6px 16px; border-radius: 8px;
  border: 1px solid #E8DDD0; background: #FFFFFF; color: #6B5E52;
  font-size: 13px; font-weight: 500; cursor: pointer;
}
.btn-secondary:hover { border-color: #D97B2B; color: #D97B2B; }

.cm-error {
  padding: 10px 12px; margin-bottom: 12px; border-radius: 8px;
  background: #FFF0F0; color: #C93A3A; border: 1px solid #FFD0D0; font-size: 13px;
}

.cm-form {
  background: #FAFAF8; border: 1px solid #E8DDD0; border-radius: 12px;
  padding: 18px 20px; margin-bottom: 20px;
}
.form-row { display: flex; gap: 14px; }
.form-field { display: flex; flex-direction: column; gap: 5px; margin-bottom: 12px; flex: 1; }
.form-label { font-size: 12px; font-weight: 600; color: #8B7E74; }
.form-input {
  padding: 7px 12px; border: 1px solid #E8DDD0; border-radius: 8px;
  font-size: 13px; color: #3D3028; background: #FFFFFF; outline: none;
}
.form-input:focus { border-color: #D97B2B; box-shadow: 0 0 0 3px rgba(217,123,43,0.1); }
.form-actions { display: flex; justify-content: flex-end; }

.cm-list { display: flex; flex-direction: column; gap: 8px; }
.cm-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #FFFFFF; border: 1px solid #E8DDD0;
  border-radius: 10px; transition: border-color 0.2s;
}
.cm-row:hover { border-color: #D4C8BA; }

.cm-info { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.cat-name { font-size: 14px; font-weight: 500; color: #3D3028; }
.cat-slug {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 12px; background: #F5F0EB; padding: 2px 8px; border-radius: 4px; color: #8B7E74;
}
.cat-desc { font-size: 12px; color: #B8A898; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.cm-actions { display: flex; gap: 4px; flex-shrink: 0; margin-left: 12px; }
.action-btn {
  width: 30px; height: 30px; border: 1px solid #E8DDD0; border-radius: 6px;
  background: #FFFFFF; color: #8B7E74; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: all 0.2s;
}
.action-btn:hover { border-color: #D97B2B; color: #D97B2B; }
.action-btn.danger:hover { border-color: #E85D5D; color: #E85D5D; }

.empty-state { text-align: center; padding: 40px; color: #B8A898; }
</style>
