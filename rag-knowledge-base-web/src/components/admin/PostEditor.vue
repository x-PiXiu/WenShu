<template>
  <div class="post-editor">
    <div class="editor-topbar">
      <button class="back-btn" @click="$emit('back')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        返回
      </button>
      <div class="editor-actions">
        <button class="btn-secondary" @click="saveDraft">保存草稿</button>
        <button class="btn-primary" @click="publish">发布</button>
      </div>
    </div>

    <div class="editor-meta">
      <input type="text" v-model="title" placeholder="文章标题" class="title-input" />
      <div class="meta-row">
        <select v-model="category" class="category-select">
          <option value="">无分类</option>
          <option v-for="cat in categories" :key="cat.id" :value="cat.name">{{ cat.name }}</option>
        </select>
        <div class="tags-input-wrap">
          <span v-for="(tag, i) in tags" :key="i" class="tag-chip">
            {{ tag }} <span class="tag-remove" @click="removeTag(i)">&times;</span>
          </span>
          <input type="text" v-model="tagInput" placeholder="+ 标签" class="tag-input"
                 @keydown.enter.prevent="addTag" />
        </div>
      </div>
    </div>

    <div class="editor-body">
      <div class="editor-pane">
        <textarea v-model="content" class="editor-textarea" placeholder="开始写作... (支持 Markdown)"></textarea>
      </div>
      <div class="preview-pane">
        <div class="preview-label">预览</div>
        <MarkdownRenderer :content="content" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAdmin } from '../../composables/useAdmin'
import { useBlog } from '../../composables/useBlog'
import type { BlogCategory, Article } from '../../types/blog'
import MarkdownRenderer from '../MarkdownRenderer.vue'

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'saved', article: Article): void
}>()

const props = defineProps<{
  editArticle?: Article | null
}>()

const { createPost, updatePost, publishPost } = useAdmin()
const { listCategories } = useBlog()

const title = ref('')
const content = ref('')
const category = ref('')
const tags = ref<string[]>([])
const tagInput = ref('')
const categories = ref<BlogCategory[]>([])
const saving = ref(false)

onMounted(async () => {
  categories.value = await listCategories()
  if (props.editArticle) {
    title.value = props.editArticle.title
    content.value = props.editArticle.content
    category.value = props.editArticle.category || ''
    tags.value = [...(props.editArticle.tags || [])]
  }
})

function addTag() {
  const t = tagInput.value.trim()
  if (t && !tags.value.includes(t)) {
    tags.value = [...tags.value, t]
  }
  tagInput.value = ''
}

function removeTag(index: number) {
  tags.value = tags.value.filter((_, i) => i !== index)
}

async function saveDraft() {
  if (!title.value.trim()) return
  saving.value = true
  try {
    let article: Article | null
    if (props.editArticle) {
      article = await updatePost(props.editArticle.id, {
        title: title.value,
        content: content.value,
        category: category.value || undefined,
        tags: tags.value,
      })
    } else {
      article = await createPost({
        title: title.value,
        content: content.value,
        contentType: 'md',
        category: category.value || undefined,
        tags: tags.value,
      })
    }
    if (article) emit('saved', article)
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!title.value.trim()) return
  saving.value = true
  try {
    let article: Article | null
    if (props.editArticle) {
      article = await updatePost(props.editArticle.id, {
        title: title.value,
        content: content.value,
        category: category.value || undefined,
        tags: tags.value,
      })
      if (article) article = await publishPost(article.id)
    } else {
      article = await createPost({
        title: title.value,
        content: content.value,
        contentType: 'md',
        category: category.value || undefined,
        tags: tags.value,
      })
      if (article) article = await publishPost(article.id)
    }
    if (article) emit('saved', article)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.post-editor { display: flex; flex-direction: column; height: 100%; overflow: hidden; }

.editor-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px; border-bottom: 1px solid #E8DDD0; background: #FFFFFF;
  flex-shrink: 0;
}
.back-btn {
  display: inline-flex; align-items: center; gap: 6px;
  border: none; background: none; color: #8B7E74; font-size: 13px; cursor: pointer;
}
.back-btn:hover { color: #D97B2B; }

.editor-actions { display: flex; gap: 8px; }
.btn-secondary, .btn-primary {
  padding: 6px 16px; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer;
}
.btn-secondary { border: 1px solid #E8DDD0; background: #FFFFFF; color: #6B5E52; }
.btn-secondary:hover { border-color: #D97B2B; color: #D97B2B; }
.btn-primary { border: none; background: #D97B2B; color: #FFFFFF; }
.btn-primary:hover { background: #C06A1E; }

.editor-meta {
  padding: 12px 16px; border-bottom: 1px solid #E8DDD0; background: #FAFAF8;
  flex-shrink: 0;
}
.title-input {
  width: 100%; border: none; font-size: 20px; font-weight: 600;
  color: #3D3028; background: transparent; outline: none; margin-bottom: 8px;
}
.title-input::placeholder { color: #D4C8BA; }

.meta-row { display: flex; gap: 12px; align-items: center; }
.category-select {
  padding: 4px 8px; border: 1px solid #E8DDD0; border-radius: 6px;
  font-size: 13px; color: #6B5E52; background: #FFFFFF; outline: none;
}
.tags-input-wrap {
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px; flex: 1;
}
.tag-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; background: #FEF3E8; color: #D97B2B;
  border-radius: 10px; font-size: 12px;
}
.tag-remove { cursor: pointer; opacity: 0.6; }
.tag-remove:hover { opacity: 1; }
.tag-input {
  border: none; font-size: 13px; color: #6B5E52; background: transparent;
  outline: none; min-width: 80px;
}

.editor-body { display: flex; flex: 1; overflow: hidden; }
.editor-pane, .preview-pane {
  flex: 1; overflow-y: auto; padding: 16px;
}
.editor-pane { border-right: 1px solid #E8DDD0; }
.editor-textarea {
  width: 100%; height: 100%; border: none; resize: none;
  font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 14px;
  line-height: 1.7; color: #3D3028; outline: none; background: transparent;
}
.preview-label {
  font-size: 11px; font-weight: 600; color: #B8A898; text-transform: uppercase;
  letter-spacing: 1px; margin-bottom: 12px;
}
</style>
