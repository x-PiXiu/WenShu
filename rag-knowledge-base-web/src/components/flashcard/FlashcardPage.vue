<template>
  <div class="flashcard-page">
    <div class="fc-container">
      <div class="fc-header">
        <h2 class="section-title">闪卡生成器</h2>
        <span class="fc-hint">上传学习资料，AI 自动生成闪卡，间隔重复高效记忆</span>
      </div>

      <!-- Input Section -->
      <div class="fc-input-section">
        <div class="fc-tabs">
          <button :class="['fc-tab', { active: inputMode === 'file' }]" @click="inputMode = 'file'">上传文件</button>
          <button :class="['fc-tab', { active: inputMode === 'text' }]" @click="inputMode = 'text'">粘贴文本</button>
        </div>

        <div v-if="inputMode === 'file'" class="fc-upload-area"
             @dragover.prevent="dragActive = true" @dragleave="dragActive = false"
             @drop.prevent="handleDrop">
          <input type="file" ref="fileInput" @change="handleFileSelect"
                 accept=".pdf,.docx,.txt,.md" hidden />
          <div v-if="!selectedFile" class="fc-upload-prompt" @click="($refs.fileInput as HTMLInputElement)?.click()">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#B8A898" stroke-width="1.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            <span>拖拽文件到此处，或点击选择</span>
            <span class="fc-upload-hint">支持 PDF、DOCX、TXT、MD</span>
          </div>
          <div v-else class="fc-file-info" @click="($refs.fileInput as HTMLInputElement)?.click()">
            <span class="fc-file-name">{{ selectedFile.name }}</span>
            <button class="fc-file-remove" @click.stop="selectedFile = null">移除</button>
          </div>
        </div>

        <div v-else class="fc-text-area">
          <textarea v-model="inputText" placeholder="在此粘贴学习笔记、课堂内容、教材段落..."
                    rows="6"></textarea>
          <div class="fc-text-meta">
            <input v-model="textTitle" placeholder="标题（可选）" class="fc-title-input" />
          </div>
        </div>
      </div>

      <!-- Config Section -->
      <div class="fc-config">
        <div class="fc-config-group">
          <label>卡片数量</label>
          <div class="fc-option-btns">
            <button v-for="n in [10, 20, 30, 50]" :key="n"
                    :class="['fc-opt-btn', { active: cardCount === n }]"
                    @click="cardCount = n">{{ n }}</button>
          </div>
        </div>
        <div class="fc-config-group">
          <label>难度级别</label>
          <div class="fc-option-btns">
            <button :class="['fc-opt-btn', { active: difficulty === 'basic' }]" @click="difficulty = 'basic'">基础</button>
            <button :class="['fc-opt-btn', { active: difficulty === 'intermediate' }]" @click="difficulty = 'intermediate'">中级</button>
            <button :class="['fc-opt-btn', { active: difficulty === 'advanced' }]" @click="difficulty = 'advanced'">高级</button>
          </div>
        </div>
        <button class="fc-generate-btn" :disabled="!canGenerate || loading" @click="handleGenerate">
          {{ loading ? '正在生成...' : '生成闪卡' }}
        </button>
      </div>

      <div v-if="errorMsg" class="fc-error">{{ errorMsg }}</div>

      <!-- Deck List -->
      <div class="fc-decks-section">
        <h3 class="sub-title">我的卡组</h3>
        <div v-if="decks.length === 0 && !loading" class="fc-empty">还没有卡组，上传学习资料开始生成</div>
        <div class="fc-deck-grid">
          <div v-for="deck in decks" :key="deck.id" class="fc-deck-card" @click="$emit('openDeck', deck.id)">
            <div class="fc-deck-title">{{ deck.title }}</div>
            <div class="fc-deck-meta">
              <span>{{ deck.cardCount }} 张卡片</span>
              <span v-if="deck.sourceFile" class="fc-deck-source">{{ deck.sourceFile }}</span>
            </div>
            <div class="fc-deck-progress">
              <div class="fc-progress-bar">
                <div class="fc-progress-fill mastered" :style="{ width: masteryPercent(deck) + '%' }"></div>
                <div class="fc-progress-fill learning" :style="{ width: learningPercent(deck) + '%', left: masteryPercent(deck) + '%' }"></div>
              </div>
              <span class="fc-progress-text">
                <template v-if="deck.masteredCount > 0">{{ deck.masteredCount }} 掌握</template>
                <template v-else>{{ deck.cardCount }} 待学</template>
              </span>
            </div>
            <div class="fc-deck-actions">
              <button class="fc-study-btn" @click.stop="$emit('study', deck.id)">学习</button>
              <button class="fc-delete-btn" @click.stop="handleDeleteDeck(deck.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { Deck, Difficulty } from '../../types/flashcard'
import { useFlashcard } from '../../composables/useFlashcard'

defineEmits<{ openDeck: [id: string]; study: [id: string] }>()

const { decks, loading, generateFromFile, generateFromText, loadDecks, deleteDeck, loadStats } = useFlashcard()

const inputMode = ref<'file' | 'text'>('file')
const selectedFile = ref<File | null>(null)
const inputText = ref('')
const textTitle = ref('')
const cardCount = ref(20)
const difficulty = ref<Difficulty>('intermediate')
const dragActive = ref(false)
const errorMsg = ref('')

const canGenerate = computed(() => {
  return inputMode.value === 'file' ? !!selectedFile.value : inputText.value.trim().length > 20
})

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files?.[0]) selectedFile.value = target.files[0]
}

function handleDrop(e: DragEvent) {
  dragActive.value = false
  if (e.dataTransfer?.files[0]) selectedFile.value = e.dataTransfer.files[0]
}

async function handleGenerate() {
  errorMsg.value = ''
  let result
  if (inputMode.value === 'file' && selectedFile.value) {
    result = await generateFromFile(selectedFile.value, cardCount.value, difficulty.value)
  } else if (inputMode.value === 'text' && inputText.value.trim()) {
    result = await generateFromText(inputText.value, textTitle.value || '粘贴文本', cardCount.value, difficulty.value)
  }
  if (result) {
    selectedFile.value = null
    inputText.value = ''
    textTitle.value = ''
    await loadDecks()
    await loadStats()
  } else {
    errorMsg.value = '生成失败，请检查文件内容或 LLM 配置'
  }
}

async function handleDeleteDeck(id: string) {
  if (!confirm('确定删除该卡组？')) return
  await deleteDeck(id)
  await loadDecks()
}

function masteryPercent(deck: Deck): number {
  return deck.cardCount > 0 ? (deck.masteredCount / deck.cardCount) * 100 : 0
}

function learningPercent(deck: Deck): number {
  if (deck.cardCount === 0) return 0
  const learned = deck.cardCount - deck.masteredCount
  return learned > 0 ? Math.min((learned / deck.cardCount) * 100, 100 - masteryPercent(deck)) : 0
}

onMounted(() => { loadDecks(); loadStats() })
</script>

<style scoped>
.flashcard-page { flex: 1; overflow-y: auto; overflow-x: hidden; background: #F8F5EF; }
.fc-container { max-width: 860px; margin: 0 auto; padding: 24px 32px; }
.fc-header { margin-bottom: 20px; }
.section-title { font-size: 18px; font-weight: 700; color: #3D3028; margin-bottom: 4px; }
.fc-hint { font-size: 13px; color: #B8A898; }
.sub-title { font-size: 15px; font-weight: 600; color: #3D3028; margin-bottom: 12px; }

.fc-input-section { background: #fff; border: 1px solid #E8DDD0; border-radius: 10px; padding: 16px; margin-bottom: 16px; }
.fc-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.fc-tab { padding: 6px 14px; border: 1px solid #E8DDD0; border-radius: 6px; background: #FAFAF6; cursor: pointer; font-size: 13px; color: #8B7E74; }
.fc-tab.active { background: #D97B2B; color: #fff; border-color: #D97B2B; }

.fc-upload-area { border: 2px dashed #E8DDD0; border-radius: 8px; padding: 24px; text-align: center; transition: border-color 0.2s; }
.fc-upload-area:hover, .fc-upload-area.drag-active { border-color: #D97B2B; }
.fc-upload-prompt { cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 8px; color: #B8A898; font-size: 13px; }
.fc-upload-hint { font-size: 11px; color: #C8B8A8; }
.fc-file-info { display: flex; align-items: center; justify-content: center; gap: 12px; cursor: pointer; }
.fc-file-name { font-size: 13px; color: #3D3028; font-weight: 500; }
.fc-file-remove { font-size: 12px; color: #E85D5D; background: none; border: none; cursor: pointer; }

.fc-text-area textarea { width: 100%; border: 1px solid #E8DDD0; border-radius: 8px; padding: 10px; font-size: 13px; resize: vertical; min-height: 80px; outline: none; }
.fc-text-area textarea:focus { border-color: #D97B2B; }
.fc-text-meta { margin-top: 8px; }
.fc-title-input { width: 100%; border: 1px solid #E8DDD0; border-radius: 6px; padding: 6px 10px; font-size: 13px; outline: none; }
.fc-title-input:focus { border-color: #D97B2B; }

.fc-config { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; margin-bottom: 16px; }
.fc-config-group { display: flex; align-items: center; gap: 8px; }
.fc-config-group label { font-size: 13px; color: #8B7E74; white-space: nowrap; }
.fc-option-btns { display: flex; gap: 4px; }
.fc-opt-btn { padding: 4px 12px; border: 1px solid #E8DDD0; border-radius: 5px; background: #FAFAF6; font-size: 12px; cursor: pointer; color: #8B7E74; }
.fc-opt-btn.active { background: #D97B2B; color: #fff; border-color: #D97B2B; }

.fc-generate-btn { padding: 8px 24px; background: #D97B2B; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; margin-left: auto; }
.fc-generate-btn:disabled { background: #C8B8A8; cursor: not-allowed; }
.fc-generate-btn:hover:not(:disabled) { background: #C86A20; }

.fc-error { color: #E85D5D; font-size: 13px; margin-bottom: 12px; }

.fc-decks-section { margin-top: 24px; }
.fc-empty { padding: 32px; text-align: center; color: #B8A898; font-size: 13px; background: #fff; border-radius: 10px; }
.fc-deck-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.fc-deck-card { background: #fff; border: 1px solid #E8DDD0; border-radius: 10px; padding: 14px; cursor: pointer; transition: box-shadow 0.2s; }
.fc-deck-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.fc-deck-title { font-size: 14px; font-weight: 600; color: #3D3028; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fc-deck-meta { font-size: 12px; color: #B8A898; margin-bottom: 8px; display: flex; gap: 8px; }
.fc-deck-source { color: #D97B2B; }
.fc-deck-progress { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.fc-progress-bar { flex: 1; height: 4px; background: #F0E8DD; border-radius: 2px; overflow: hidden; position: relative; }
.fc-progress-fill { height: 100%; border-radius: 2px; transition: width 0.3s; position: absolute; left: 0; }
.fc-progress-fill.mastered { background: #16a34a; }
.fc-progress-fill.learning { background: #CA8A04; }
.fc-progress-text { font-size: 11px; color: #8B7E74; white-space: nowrap; }
.fc-deck-actions { display: flex; gap: 6px; }
.fc-study-btn { padding: 4px 12px; background: #D97B2B; color: #fff; border: none; border-radius: 5px; font-size: 12px; cursor: pointer; }
.fc-study-btn:hover { background: #C86A20; }
.fc-delete-btn { padding: 4px 10px; background: none; border: 1px solid #E8DDD0; border-radius: 5px; font-size: 12px; color: #B8A898; cursor: pointer; }
.fc-delete-btn:hover { border-color: #E85D5D; color: #E85D5D; }
</style>
