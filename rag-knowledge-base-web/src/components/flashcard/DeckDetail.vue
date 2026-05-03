<template>
  <div class="fc-detail-page">
    <div class="fc-container">
      <button class="fc-back-btn" @click="$emit('back')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        返回卡组
      </button>

      <div v-if="!detail" class="fc-empty">加载中...</div>
      <template v-else>
        <div class="fc-detail-header">
          <div>
            <h2 class="section-title">{{ detail.deck.title }}</h2>
            <div class="fc-detail-meta">
              <span v-if="detail.deck.sourceFile">来源: {{ detail.deck.sourceFile }}</span>
              <span>{{ detail.stats.totalCards }} 张卡片</span>
              <span>掌握 {{ detail.stats.masteredCount }}</span>
              <span>待复习 {{ detail.stats.dueToday }}</span>
            </div>
          </div>
          <button class="fc-study-btn-lg" @click="$emit('study', detail.deck.id)">开始学习</button>
        </div>

        <div class="fc-card-list">
          <div v-for="(card, idx) in detail.cards" :key="card.id" class="fc-card-row">
            <div class="fc-card-idx">{{ idx + 1 }}</div>
            <div class="fc-card-content">
              <template v-if="editingId === card.id">
                <textarea v-model="editFront" rows="2" class="fc-edit-area" placeholder="正面（问题）"></textarea>
                <textarea v-model="editBack" rows="2" class="fc-edit-area" placeholder="背面（答案）"></textarea>
                <div class="fc-edit-actions">
                  <button class="fc-save-btn" @click="handleSaveEdit(card.id)">保存</button>
                  <button class="fc-cancel-btn" @click="editingId = ''">取消</button>
                </div>
              </template>
              <template v-else>
                <div class="fc-card-front" @click="toggleExpand(card.id)">
                  {{ card.front }}
                  <span class="fc-expand-hint">{{ expandedId === card.id ? '收起' : '查看答案' }}</span>
                </div>
                <div v-if="expandedId === card.id" class="fc-card-back">{{ card.back }}</div>
                <div class="fc-card-tags">
                  <span v-for="tag in card.tags" :key="tag" class="fc-tag">{{ tag }}</span>
                  <span class="fc-diff-badge" :class="'diff-' + card.difficulty">
                    {{ card.difficulty === 1 ? '基础' : card.difficulty === 3 ? '高级' : '中级' }}
                  </span>
                  <span class="fc-card-stats">复习 {{ card.reviewCount }} 次</span>
                </div>
              </template>
            </div>
            <div v-if="editingId !== card.id" class="fc-card-actions">
              <button @click="startEdit(card)" title="编辑">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              </button>
              <button @click="handleDeleteCard(card.id, card.deckId)" title="删除" class="fc-del-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { DeckDetail, Card } from '../../types/flashcard'
import { useFlashcard } from '../../composables/useFlashcard'

const props = defineProps<{ deckId: string }>()
defineEmits<{ back: []; study: [id: string] }>()

const { getDeck, updateCard, deleteCard } = useFlashcard()

const detail = ref<DeckDetail | null>(null)
const expandedId = ref('')
const editingId = ref('')
const editFront = ref('')
const editBack = ref('')

function toggleExpand(id: string) {
  expandedId.value = expandedId.value === id ? '' : id
}

function startEdit(card: Card) {
  editingId.value = card.id
  editFront.value = card.front
  editBack.value = card.back
}

async function handleSaveEdit(id: string) {
  await updateCard(id, { front: editFront.value, back: editBack.value, tags: [], difficulty: 2 })
  editingId.value = ''
  detail.value = await getDeck(props.deckId)
}

async function handleDeleteCard(cardId: string, deckId: string) {
  if (!confirm('确定删除该卡片？')) return
  await deleteCard(cardId)
  detail.value = await getDeck(deckId)
}

onMounted(async () => { detail.value = await getDeck(props.deckId) })
</script>

<style scoped>
.fc-detail-page { flex: 1; overflow-y: auto; overflow-x: hidden; background: #F8F5EF; }
.fc-container { max-width: 860px; margin: 0 auto; padding: 24px 32px; }
.fc-back-btn { display: flex; align-items: center; gap: 4px; background: none; border: none; color: #B8A898; font-size: 13px; cursor: pointer; margin-bottom: 16px; }
.fc-back-btn:hover { color: #D97B2B; }
.section-title { font-size: 18px; font-weight: 700; color: #3D3028; margin-bottom: 4px; }
.fc-empty { padding: 32px; text-align: center; color: #B8A898; font-size: 13px; }

.fc-detail-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.fc-detail-meta { font-size: 12px; color: #B8A898; display: flex; gap: 12px; margin-top: 4px; }
.fc-study-btn-lg { padding: 10px 24px; background: #D97B2B; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; white-space: nowrap; }
.fc-study-btn-lg:hover { background: #C86A20; }

.fc-card-list { display: flex; flex-direction: column; gap: 8px; }
.fc-card-row { display: flex; gap: 12px; padding: 12px; background: #fff; border: 1px solid #E8DDD0; border-radius: 8px; }
.fc-card-idx { font-size: 12px; color: #B8A898; font-weight: 600; min-width: 20px; text-align: center; padding-top: 2px; }
.fc-card-content { flex: 1; min-width: 0; }
.fc-card-front { font-size: 13px; color: #3D3028; cursor: pointer; line-height: 1.5; }
.fc-expand-hint { font-size: 11px; color: #D97B2B; margin-left: 8px; }
.fc-card-back { font-size: 13px; color: #5D5048; margin-top: 8px; padding: 8px; background: #FAFAF6; border-radius: 6px; line-height: 1.5; }
.fc-card-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; align-items: center; }
.fc-tag { font-size: 11px; background: #F0E8DD; color: #8B7E74; padding: 1px 6px; border-radius: 3px; }
.fc-diff-badge { font-size: 10px; padding: 1px 5px; border-radius: 3px; }
.diff-1 { background: #DCFCE7; color: #16a34a; }
.diff-2 { background: #FEF3C7; color: #CA8A04; }
.diff-3 { background: #FEE2E2; color: #DC2626; }
.fc-card-stats { font-size: 11px; color: #C8B8A8; }

.fc-edit-area { width: 100%; border: 1px solid #E8DDD0; border-radius: 6px; padding: 6px 8px; font-size: 13px; margin-bottom: 6px; outline: none; resize: vertical; }
.fc-edit-area:focus { border-color: #D97B2B; }
.fc-edit-actions { display: flex; gap: 6px; }
.fc-save-btn { padding: 4px 12px; background: #D97B2B; color: #fff; border: none; border-radius: 5px; font-size: 12px; cursor: pointer; }
.fc-cancel-btn { padding: 4px 10px; background: none; border: 1px solid #E8DDD0; border-radius: 5px; font-size: 12px; color: #8B7E74; cursor: pointer; }

.fc-card-actions { display: flex; flex-direction: column; gap: 4px; padding-top: 2px; }
.fc-card-actions button { background: none; border: none; cursor: pointer; color: #B8A898; padding: 4px; border-radius: 4px; }
.fc-card-actions button:hover { background: #F0E8DD; }
.fc-del-icon:hover { color: #E85D5D !important; }
</style>
