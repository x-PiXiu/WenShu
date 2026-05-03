<template>
  <div class="fc-study-page">
    <div class="fc-study-container">
      <!-- Header -->
      <div class="fc-study-header">
        <button class="fc-back-btn" @click="$emit('back')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          返回
        </button>
        <div class="fc-study-progress">
          <span>{{ currentIndex + 1 }} / {{ cards.length }}</span>
          <div class="fc-progress-bar">
            <div class="fc-progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="cards.length === 0" class="fc-empty-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#C8B8A8" stroke-width="1.5"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="M6 8h.01"/><path d="M10 8h8"/></svg>
        <p>这个卡组是空的</p>
        <button class="fc-done-btn" @click="$emit('back')">返回</button>
      </div>

      <!-- Finished -->
      <div v-else-if="finished" class="fc-summary">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="1.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        <h2 class="fc-summary-title">浏览完成</h2>
        <p class="fc-summary-sub">已看完 {{ cards.length }} 张卡片</p>
        <div class="fc-summary-actions">
          <button class="fc-done-btn secondary" @click="reviewAgain">再看一遍</button>
          <button class="fc-done-btn" @click="$emit('back')">返回</button>
        </div>
      </div>

      <!-- Card Display -->
      <div v-else-if="currentCard" class="fc-study-card-area">
        <div class="fc-card-wrapper" @click="flipped = !flipped">
          <div class="fc-card" :class="{ flipped }">
            <div class="fc-card-inner">
              <div class="fc-card-face fc-card-front">
                <div class="fc-card-badge q">Q</div>
                <div class="fc-card-body">{{ currentCard.front }}</div>
                <div class="fc-card-footer">点击翻转</div>
              </div>
              <div class="fc-card-face fc-card-back">
                <div class="fc-card-badge a">A</div>
                <div class="fc-card-body">{{ currentCard.back }}</div>
                <div class="fc-card-footer">点击翻转</div>
              </div>
            </div>
          </div>
        </div>

        <div class="fc-nav-btns">
          <button class="fc-nav-btn" :disabled="currentIndex === 0" @click.stop="prevCard">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
            上一张
          </button>
          <button class="fc-nav-btn primary" @click.stop="nextCard">
            {{ currentIndex + 1 >= cards.length ? '完成' : '下一张' }}
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { Card } from '../../types/flashcard'
import { useFlashcard } from '../../composables/useFlashcard'

const props = defineProps<{ deckId: string }>()
defineEmits<{ back: [] }>()

const { getDeck } = useFlashcard()

const cards = ref<Card[]>([])
const currentIndex = ref(0)
const flipped = ref(false)
const finished = ref(false)

const currentCard = computed(() => cards.value[currentIndex.value] || null)
const progressPercent = computed(() => cards.value.length > 0 ? ((currentIndex.value + 1) / cards.value.length) * 100 : 0)

function nextCard() {
  if (currentIndex.value + 1 >= cards.value.length) {
    finished.value = true
  } else {
    currentIndex.value++
    flipped.value = false
  }
}

function prevCard() {
  if (currentIndex.value > 0) {
    currentIndex.value--
    flipped.value = false
  }
}

function reviewAgain() {
  currentIndex.value = 0
  flipped.value = false
  finished.value = false
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === ' ' || e.key === 'Enter') {
    e.preventDefault()
    flipped.value = !flipped.value
  } else if (e.key === 'ArrowRight') {
    nextCard()
  } else if (e.key === 'ArrowLeft') {
    prevCard()
  }
}

onMounted(async () => {
  const detail = await getDeck(props.deckId)
  if (detail) cards.value = detail.cards
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.fc-study-page {
  flex: 1; overflow-y: auto; background: linear-gradient(135deg, #F8F5EF 0%, #F0EBE3 100%);
  display: flex; justify-content: center;
}
.fc-study-container { max-width: 580px; width: 100%; padding: 24px 20px; min-height: 100vh; display: flex; flex-direction: column; }

/* Header */
.fc-study-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; flex-shrink: 0; }
.fc-back-btn {
  display: flex; align-items: center; gap: 4px;
  background: none; border: none; color: #B8A898; font-size: 13px; cursor: pointer;
}
.fc-back-btn:hover { color: #D97B2B; }
.fc-study-progress { flex: 1; display: flex; align-items: center; gap: 8px; font-size: 13px; color: #8B7E74; }
.fc-progress-bar { flex: 1; height: 4px; background: #E8DDD0; border-radius: 2px; overflow: hidden; }
.fc-progress-fill { height: 100%; background: #D97B2B; border-radius: 2px; transition: width 0.4s ease; }

/* Card Area */
.fc-study-card-area { flex: 1; display: flex; flex-direction: column; align-items: center; padding-top: 8px; }

/* Flip Card */
.fc-card-wrapper { width: 100%; cursor: pointer; perspective: 1200px; margin-bottom: 28px; flex-shrink: 0; }
.fc-card-wrapper:active .fc-card-inner { transform: scale(0.98); }
.fc-card { width: 100%; transform-style: preserve-3d; transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1); }
.fc-card.flipped { transform: rotateY(180deg); }
.fc-card-inner { position: relative; width: 100%; min-height: 300px; transform-style: preserve-3d; transition: transform 0.15s ease; }
.fc-card-face {
  position: absolute; inset: 0; backface-visibility: hidden; -webkit-backface-visibility: hidden;
  border-radius: 16px; padding: 36px 28px 28px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: 0 8px 32px rgba(61,48,40,0.12), 0 2px 8px rgba(61,48,40,0.06);
}
.fc-card-front { background: #fff; border: 2px solid #E8DDD0; }
.fc-card-back { background: linear-gradient(135deg, #FFFDF8, #FFF9F0); border: 2px solid #D97B2B; transform: rotateY(180deg); }
.fc-card-badge { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 800; position: absolute; top: 16px; left: 16px; }
.fc-card-badge.q { background: #FEF3E8; color: #D97B2B; }
.fc-card-badge.a { background: #DCFCE7; color: #16a34a; }
.fc-card-body { font-size: 17px; color: #3D3028; text-align: center; line-height: 1.7; flex: 1; display: flex; align-items: center; justify-content: center; word-break: break-word; padding: 0 8px; }
.fc-card-footer { font-size: 12px; color: #C8B8A8; margin-top: 12px; }

/* Navigation */
.fc-nav-btns { display: flex; gap: 12px; width: 100%; }
.fc-nav-btn {
  flex: 1; padding: 12px; border: 2px solid #E8DDD0; border-radius: 12px;
  background: #fff; font-size: 14px; font-weight: 600; color: #3D3028;
  cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px;
  transition: all 0.2s;
}
.fc-nav-btn:disabled { color: #C8B8A8; border-color: #F0E8DD; cursor: not-allowed; }
.fc-nav-btn:not(:disabled):hover { border-color: #D97B2B; color: #D97B2B; }
.fc-nav-btn.primary { background: #D97B2B; color: #fff; border-color: #D97B2B; }
.fc-nav-btn.primary:hover:not(:disabled) { background: #C86A20; }

/* Empty State */
.fc-empty-state {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  text-align: center; padding: 40px 20px; gap: 12px; color: #B8A898; font-size: 14px;
}

/* Summary */
.fc-summary { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 40px 0; gap: 8px; }
.fc-summary-title { font-size: 22px; font-weight: 700; color: #3D3028; }
.fc-summary-sub { font-size: 14px; color: #8B7E74; margin-bottom: 20px; }
.fc-summary-actions { display: flex; gap: 12px; }
.fc-done-btn {
  padding: 12px 32px; background: #D97B2B; color: #fff;
  border: none; border-radius: 10px; font-size: 15px; font-weight: 600;
  cursor: pointer; transition: all 0.2s;
}
.fc-done-btn:hover { background: #C86A20; }
.fc-done-btn.secondary { background: #fff; color: #3D3028; border: 2px solid #E8DDD0; }
.fc-done-btn.secondary:hover { background: #FEF3E8; border-color: #D97B2B; }
</style>
