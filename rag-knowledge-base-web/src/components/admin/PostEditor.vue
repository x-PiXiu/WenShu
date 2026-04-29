<template>
  <div class="post-editor">
    <!-- Top Bar -->
    <div class="editor-topbar">
      <div class="topbar-left">
        <button class="back-btn" @click="$emit('back')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          返回
        </button>
        <span v-if="editArticle" :class="['status-tag', editArticle.status]">{{ statusLabel }}</span>
      </div>
      <div class="topbar-right">
        <button class="btn-secondary" @click="triggerImport" :disabled="saving">
          导入文档
        </button>
        <button class="btn-secondary" @click="generateSummary" :disabled="saving || !content.trim()">
          AI 生成摘要
        </button>
        <input
          ref="fileInputRef"
          class="file-input"
          type="file"
          accept=".txt,.md,.docx,.pdf,text/plain,text/markdown"
          @change="importDocument"
        />
        <input
          ref="imageInputRef"
          class="file-input"
          type="file"
          accept="image/*"
          @change="uploadImage"
        />
        <button class="btn-secondary" @click="saveDraft" :disabled="saving">
          {{ saving ? '保存中...' : '保存草稿' }}
        </button>
        <button class="btn-primary" @click="publish" :disabled="saving">
          {{ saving ? '处理中...' : '发布' }}
        </button>
      </div>
    </div>

    <!-- Meta Section -->
    <div class="editor-meta">
      <div v-if="feedback.message" :class="['editor-feedback', feedback.type]">
        {{ feedback.message }}
      </div>
      <label class="title-field">
        <span class="field-label">文章标题</span>
        <input type="text" v-model="title" placeholder="请输入文章标题" class="title-input" />
      </label>
      <div class="meta-row">
        <select v-model="category" class="category-select">
          <option value="">无分类</option>
          <option v-for="cat in categories" :key="cat.id" :value="cat.name">{{ cat.name }}</option>
        </select>
        <div class="tags-input-wrap">
          <span v-for="(tag, i) in tags" :key="i" class="tag-chip">
            {{ tag }} <span class="tag-remove" @click="removeTag(i)">&times;</span>
          </span>
          <input type="text" v-model="tagInput" placeholder="+ 添加标签，回车确认" class="tag-input"
                 @keydown.enter.prevent="addTag" />
        </div>
      </div>
      <div class="meta-row meta-second">
        <input type="text" v-model="coverImage" placeholder="封面图 URL（可选）" class="cover-input" />
        <input type="text" v-model="summary" placeholder="文章摘要（可选，留空自动截取）" class="summary-input" />
      </div>
      <div v-if="editArticle?.slug" class="slug-display">
        Slug: <code>{{ editArticle.slug }}</code>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="editor-toolbar">
      <button class="tool-btn" @click="insertMarkdown('bold')" title="加粗 (Ctrl+B)">
        <strong>B</strong>
      </button>
      <button class="tool-btn" @click="insertMarkdown('italic')" title="斜体 (Ctrl+I)">
        <em>I</em>
      </button>
      <div class="tool-divider"></div>
      <button class="tool-btn" @click="insertMarkdown('h1')" title="一级标题">H1</button>
      <button class="tool-btn" @click="insertMarkdown('h2')" title="二级标题">H2</button>
      <button class="tool-btn" @click="insertMarkdown('h3')" title="三级标题">H3</button>
      <div class="tool-divider"></div>
      <button class="tool-btn" @click="insertMarkdown('quote')" title="引用">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V21z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3z"/></svg>
      </button>
      <button class="tool-btn" @click="insertMarkdown('ul')" title="无序列表">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
      </button>
      <button class="tool-btn" @click="insertMarkdown('ol')" title="有序列表">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="10" y1="6" x2="21" y2="6"/><line x1="10" y1="12" x2="21" y2="12"/><line x1="10" y1="18" x2="21" y2="18"/><path d="M4 6h1v4"/><path d="M4 10h2"/><path d="M6 18H4c0-1 2-2 2-3s-1-1.5-2-1"/></svg>
      </button>
      <button class="tool-btn" @click="insertMarkdown('code')" title="代码块">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
      </button>
      <div class="tool-divider"></div>
      <button class="tool-btn" @click="insertMarkdown('link')" title="链接">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
      </button>
      <button class="tool-btn" @click="triggerImageUpload" title="上传图片">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
      </button>
      <button class="tool-btn" @click="insertMarkdown('hr')" title="分割线">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="2" y1="12" x2="22" y2="12"/></svg>
      </button>
    </div>

    <!-- Editor Body -->
    <div class="editor-body">
      <div class="editor-pane">
        <textarea ref="textareaRef" v-model="content" class="editor-textarea" placeholder="开始写作... (支持 Markdown)" @keydown="handleKeydown"></textarea>
        <div class="editor-statusbar">
          <span>{{ wordCount }} 字</span>
          <span>{{ readingTime }} 分钟阅读</span>
          <span v-if="saving" class="saving-indicator">保存中...</span>
        </div>
      </div>
      <div class="preview-pane">
        <div class="preview-header">
          <span class="preview-label">PREVIEW</span>
        </div>
        <div class="preview-scroll">
          <MarkdownRenderer :content="content" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAdmin } from '../../composables/useAdmin'
import { useBlog } from '../../composables/useBlog'
import { useMedia } from '../../composables/useMedia'
import type { BlogCategory, Article } from '../../types/blog'
import MarkdownRenderer from '../MarkdownRenderer.vue'

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'saved', article: Article): void
}>()

const props = defineProps<{
  editArticle?: Article | null
}>()

const { createPost, updatePost, publishPost, adminToken } = useAdmin()
const { listCategories } = useBlog()
const { uploadFiles } = useMedia()

const title = ref('')
const content = ref('')
const category = ref('')
const tags = ref<string[]>([])
const tagInput = ref('')
const coverImage = ref('')
const summary = ref('')
const categories = ref<BlogCategory[]>([])
const saving = ref(false)
const feedback = ref<{ type: 'error' | 'success'; message: string }>({ type: 'success', message: '' })
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)

const statusLabel = computed(() => {
  if (!props.editArticle) return '新建'
  const s = props.editArticle.status
  if (s === 'draft') return '草稿'
  if (s === 'published') return '已发布'
  return '已归档'
})

const wordCount = computed(() => {
  const text = content.value.replace(/\s/g, '')
  return text.length
})

const readingTime = computed(() => Math.max(1, Math.ceil(wordCount.value / 500)))

onMounted(async () => {
  categories.value = await listCategories()
  if (props.editArticle) {
    title.value = props.editArticle.title
    content.value = props.editArticle.content
    category.value = props.editArticle.category || ''
    tags.value = [...(props.editArticle.tags || [])]
    coverImage.value = props.editArticle.coverImage || ''
    summary.value = props.editArticle.summary || ''
  }
})

function showError(message: string) {
  feedback.value = { type: 'error', message }
}

function showSuccess(message: string) {
  feedback.value = { type: 'success', message }
}

function clearFeedback() {
  feedback.value = { type: 'success', message: '' }
}

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function triggerImport() {
  fileInputRef.value?.click()
}

function titleFromFile(file: File, fileContent: string): string {
  const firstHeading = fileContent.match(/^#\s+(.+)$/m)
  if (firstHeading?.[1]?.trim()) return firstHeading[1].trim()
  return file.name.replace(/\.(txt|md)$/i, '').trim() || '未命名文章'
}

async function importDocument(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  const fileName = file.name.toLowerCase()

  if ((title.value.trim() || content.value.trim()) && !confirm('导入文档会覆盖当前标题和正文，是否继续？')) {
    return
  }

  if (fileName.endsWith('.docx') || fileName.endsWith('.pdf')) {
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await fetch('/api/admin/import', {
        method: 'POST',
        headers: adminToken.value ? { 'X-Admin-Token': adminToken.value } : {},
        body: form,
      })
      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: '导入失败' }))
        throw new Error(err.error || '导入失败')
      }
      const article = await res.json()
      title.value = article.title || ''
      content.value = article.content || ''
      showSuccess(`已导入：${file.name}`)
    } catch (error) {
      showError(getErrorMessage(error, '文档导入失败'))
    }
    return
  }

  if (!fileName.endsWith('.txt') && !fileName.endsWith('.md')) {
    showError('支持导入 .txt、.md、.docx、.pdf 文档')
    return
  }

  try {
    const fileContent = await file.text()
    title.value = titleFromFile(file, fileContent)
    content.value = fileContent
    showSuccess(`已导入：${file.name}`)
  } catch (error) {
    showError(getErrorMessage(error, '文档读取失败，请确认文件编码为 UTF-8'))
  }
}

function triggerImageUpload() {
  imageInputRef.value?.click()
}

async function uploadImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  try {
    const results = await uploadFiles([file])
    if (results.length > 0 && results[0].url) {
      const ta = textareaRef.value
      if (ta) {
        const start = ta.selectionStart
        const md = `![${file.name}](${results[0].url})`
        content.value = content.value.substring(0, start) + md + content.value.substring(ta.selectionEnd)
        requestAnimationFrame(() => {
          ta.focus()
          ta.setSelectionRange(start + md.length, start + md.length)
        })
      }
      showSuccess('图片已上传')
    } else {
      showError('图片上传失败')
    }
  } catch {
    showError('图片上传失败')
  }
}

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

function insertMarkdown(type: string) {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const selected = content.value.substring(start, end)
  let before = ''
  let after = ''
  let insert = ''

  switch (type) {
    case 'bold':
      before = '**'; after = '**'; insert = selected || '粗体文本'; break
    case 'italic':
      before = '*'; after = '*'; insert = selected || '斜体文本'; break
    case 'h1':
      before = '# '; insert = selected || '一级标题'; break
    case 'h2':
      before = '## '; insert = selected || '二级标题'; break
    case 'h3':
      before = '### '; insert = selected || '三级标题'; break
    case 'quote':
      before = '> '; insert = selected || '引用文本'; break
    case 'ul':
      before = '- '; insert = selected || '列表项'; break
    case 'ol':
      before = '1. '; insert = selected || '列表项'; break
    case 'code':
      before = '```\n'; after = '\n```'; insert = selected || '代码'; break
    case 'link':
      before = '['; after = '](url)'; insert = selected || '链接文本'; break
    case 'image':
      before = '!['; after = '](url)'; insert = selected || '图片描述'; break
    case 'hr':
      before = '\n---\n'; insert = ''; break
  }

  const newText = before + insert + after
  content.value = content.value.substring(0, start) + newText + content.value.substring(end)

  requestAnimationFrame(() => {
    ta.focus()
    const cursorPos = start + before.length + insert.length
    ta.setSelectionRange(cursorPos, cursorPos)
  })
}

function handleKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'b') {
    e.preventDefault()
    insertMarkdown('bold')
  } else if ((e.ctrlKey || e.metaKey) && e.key === 'i') {
    e.preventDefault()
    insertMarkdown('italic')
  }
}

async function generateSummary() {
  if (!content.value.trim()) return
  clearFeedback()
  saving.value = true
  try {
    if (props.editArticle) {
      const res = await fetch(`/api/admin/posts/${props.editArticle.id}/summarize`, {
        method: 'POST',
        headers: adminToken.value ? { 'X-Admin-Token': adminToken.value } : {},
      })
      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: '生成失败' }))
        throw new Error(err.error || '生成失败')
      }
      const article = await res.json()
      summary.value = article.summary || ''
      showSuccess('摘要已生成')
    } else {
      showSuccess('请先保存草稿后再生成摘要')
    }
  } catch (error) {
    showError(getErrorMessage(error, '摘要生成失败'))
  } finally {
    saving.value = false
  }
}

async function saveDraft() {
  if (!title.value.trim()) {
    showError('请先填写文章标题')
    return
  }
  clearFeedback()
  saving.value = true
  try {
    let article: Article | null
    if (props.editArticle) {
      article = await updatePost(props.editArticle.id, {
        title: title.value,
        content: content.value,
        category: category.value || undefined,
        tags: tags.value,
        summary: summary.value || undefined,
        coverImage: coverImage.value || undefined,
      })
    } else {
      article = await createPost({
        title: title.value,
        content: content.value,
        contentType: 'md',
        category: category.value || undefined,
        tags: tags.value,
        summary: summary.value || undefined,
        coverImage: coverImage.value || undefined,
      })
    }
    if (article) {
      showSuccess('草稿已保存')
      emit('saved', article)
    }
  } catch (error) {
    showError(getErrorMessage(error, '保存草稿失败，请稍后重试'))
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!title.value.trim()) {
    showError('请先填写文章标题')
    return
  }
  clearFeedback()
  saving.value = true
  try {
    let article: Article | null
    if (props.editArticle) {
      article = await updatePost(props.editArticle.id, {
        title: title.value,
        content: content.value,
        category: category.value || undefined,
        tags: tags.value,
        summary: summary.value || undefined,
        coverImage: coverImage.value || undefined,
      })
      if (article) article = await publishPost(article.id)
    } else {
      article = await createPost({
        title: title.value,
        content: content.value,
        contentType: 'md',
        category: category.value || undefined,
        tags: tags.value,
        summary: summary.value || undefined,
        coverImage: coverImage.value || undefined,
      })
      if (article) article = await publishPost(article.id)
    }
    if (article) {
      showSuccess('文章已发布')
      emit('saved', article)
    }
  } catch (error) {
    showError(getErrorMessage(error, '发布失败，请稍后重试'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.post-editor { display: flex; flex-direction: column; height: 100%; overflow: hidden; background: #FFFFFF; }

/* Top Bar */
.editor-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 20px; border-bottom: 1px solid #E8DDD0;
  flex-shrink: 0;
}
.topbar-left, .topbar-right { display: flex; align-items: center; gap: 10px; }
.back-btn {
  display: inline-flex; align-items: center; gap: 6px;
  border: none; background: none; color: #8B7E74; font-size: 13px; cursor: pointer;
}
.back-btn:hover { color: #D97B2B; }
.status-tag {
  font-size: 11px; font-weight: 600; padding: 2px 10px; border-radius: 10px;
}
.status-tag.draft { background: #F5F0EB; color: #8B7E74; }
.status-tag.published { background: #E8F5E9; color: #4CAF50; }
.status-tag.archived { background: #F5F0EB; color: #B8A898; }

.btn-secondary, .btn-primary {
  padding: 7px 20px; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all 0.2s;
}
.btn-secondary { border: 1px solid #E8DDD0; background: #FFFFFF; color: #6B5E52; }
.btn-secondary:hover { border-color: #D97B2B; color: #D97B2B; }
.btn-secondary:disabled, .btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { border: none; background: #D97B2B; color: #FFFFFF; }
.btn-primary:hover { background: #C06A1E; }
.file-input { display: none; }

/* Meta Section */
.editor-meta {
  padding: 14px 20px; border-bottom: 1px solid #E8DDD0; background: #FAFAF8;
  flex-shrink: 0;
}
.editor-feedback {
  padding: 8px 12px; border-radius: 8px; font-size: 13px; margin-bottom: 10px;
}
.editor-feedback.error { background: #FFF0F0; color: #C93A3A; border: 1px solid #FFD0D0; }
.editor-feedback.success { background: #E8F5E9; color: #3F8F43; border: 1px solid #C8E6C9; }
.title-field {
  display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px;
}
.field-label {
  font-size: 12px; font-weight: 600; color: #8B7E74;
}
.title-input {
  width: 100%; box-sizing: border-box; border: 1px solid #E8DDD0; border-radius: 10px;
  font-size: 22px; font-weight: 600; color: #3D3028; background: #FFFFFF;
  outline: none; padding: 10px 12px; transition: all 0.2s;
}
.title-input:focus {
  border-color: #D97B2B; box-shadow: 0 0 0 3px rgba(217, 123, 43, 0.12);
}
.title-input::placeholder { color: #D4C8BA; }
.meta-row { display: flex; gap: 12px; align-items: center; }
.meta-second { margin-top: 10px; }
.category-select {
  padding: 6px 10px; border: 1px solid #E8DDD0; border-radius: 8px;
  font-size: 13px; color: #6B5E52; background: #FFFFFF; outline: none;
  min-width: 120px;
}
.tags-input-wrap {
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px; flex: 1;
  padding: 4px 8px; border: 1px solid #E8DDD0; border-radius: 8px;
  background: #FFFFFF; min-height: 34px;
}
.tag-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 10px; background: #FEF3E8; color: #D97B2B;
  border-radius: 10px; font-size: 12px;
}
.tag-remove { cursor: pointer; opacity: 0.6; font-size: 14px; line-height: 1; }
.tag-remove:hover { opacity: 1; }
.tag-input {
  border: none; font-size: 13px; color: #6B5E52; background: transparent;
  outline: none; min-width: 100px; flex: 1;
}
.cover-input, .summary-input {
  flex: 1; padding: 6px 10px; border: 1px solid #E8DDD0; border-radius: 8px;
  font-size: 13px; color: #6B5E52; background: #FFFFFF; outline: none;
}
.cover-input::placeholder, .summary-input::placeholder { color: #D4C8BA; }
.slug-display {
  margin-top: 8px; font-size: 12px; color: #B8A898;
}
.slug-display code {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  background: #F5F0EB; padding: 1px 6px; border-radius: 4px; font-size: 11px;
}

/* Toolbar */
.editor-toolbar {
  display: flex; align-items: center; gap: 2px;
  padding: 6px 16px; border-bottom: 1px solid #E8DDD0;
  background: #FFFFFF; flex-shrink: 0;
}
.tool-btn {
  width: 30px; height: 28px; border: none; border-radius: 6px;
  background: transparent; color: #8B7E74; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; transition: all 0.15s;
}
.tool-btn:hover { background: #FEF3E8; color: #D97B2B; }
.tool-divider { width: 1px; height: 18px; background: #E8DDD0; margin: 0 4px; }

/* Editor Body */
.editor-body { display: flex; flex: 1; overflow: hidden; }
.editor-pane {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
  border-right: 1px solid #E8DDD0;
}
.editor-textarea {
  flex: 1; width: 100%; border: none; resize: none;
  font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 14px;
  line-height: 1.7; color: #3D3028; outline: none; background: transparent;
  padding: 16px 20px;
}
.editor-statusbar {
  display: flex; align-items: center; gap: 16px;
  padding: 4px 20px; background: #FAF7F2;
  border-top: 1px solid #E8DDD0; font-size: 12px; color: #B8A898;
  flex-shrink: 0;
}
.saving-indicator { color: #D97B2B; }
.preview-pane {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
}
.preview-header {
  padding: 6px 20px; border-bottom: 1px solid #E8DDD0;
  flex-shrink: 0; background: #FAFAF8;
}
.preview-label {
  font-size: 10px; font-weight: 700; color: #B8A898; text-transform: uppercase;
  letter-spacing: 1.5px;
}
.preview-scroll {
  flex: 1; overflow-y: auto; padding: 16px 20px;
}
</style>
