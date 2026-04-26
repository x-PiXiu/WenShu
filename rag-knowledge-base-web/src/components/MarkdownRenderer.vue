<template>
  <div class="markdown-body" v-html="rendered"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import json from 'highlight.js/lib/languages/json'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('json', json)

const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight(str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch { /* fallback */ }
    }
    return `<pre class="hljs"><code>${str.replace(/[<>&"]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;'}[c] || c))}</code></pre>`
  },
})

const props = defineProps<{ content: string }>()

const rendered = computed(() => md.render(props.content || ''))
</script>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  word-wrap: break-word;
  color: #3D3028;
}
.markdown-body :deep(p) {
  margin: 0.5em 0;
}
.markdown-body :deep(strong) {
  color: #3D3028;
  font-weight: 600;
}
.markdown-body :deep(a) {
  color: #D97B2B;
  text-decoration: none;
}
.markdown-body :deep(a:hover) {
  text-decoration: underline;
}
.markdown-body :deep(pre) {
  background: #F5F0EB;
  border: 1px solid #E8DDD0;
  border-radius: 8px;
  padding: 12px 16px;
  overflow-x: auto;
  margin: 8px 0;
}
.markdown-body :deep(code) {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 13px;
}
.markdown-body :deep(:not(pre) > code) {
  background: #F5F0EB;
  padding: 2px 6px;
  border-radius: 4px;
  color: #D4644A;
  font-size: 13px;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #E8913A;
  margin: 8px 0;
  padding: 4px 16px;
  color: #8B7E74;
  background: #FAF7F2;
  border-radius: 0 6px 6px 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}
.markdown-body :deep(li) {
  margin: 2px 0;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
  font-size: 13px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #E8DDD0;
  padding: 6px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #FAF7F2;
  font-weight: 600;
  color: #3D3028;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #E8DDD0;
  margin: 12px 0;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: #3D3028;
  margin: 16px 0 8px;
  font-weight: 600;
}
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 15px; }
</style>
