<template>
  <div class="fc-study-page">
    <div class="fc-study-container">
      <!-- Header -->
      <div class="fc-study-header">
        <button class="fc-back-btn" @click="handleExit">退出学习</button>
        <div class="fc-study-progress">
          <span>{{ currentIndex + 1 }} / {{ cards.length }}</span>
          <div class="fc-progress-bar">
            <div class="fc-progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
        </div>
        <!-- Combo indicator -->
        <div v-if="combo > 1" class="fc-combo-badge" :class="{ hot: combo >= 5, fire: combo >= 10 }">
          {{ combo }}x
        </div>
        <div class="fc-mode-switch">
          <button :class="['fc-mode-opt', { active: mode === 'flip' }]" @click="mode = 'flip'; flipped = false; revealed = false; typedAnswer = ''">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="M6 8h.01"/><path d="M10 8h8"/></svg>
            翻卡
          </button>
          <button :class="['fc-mode-opt', { active: mode === 'type' }]" @click="mode = 'type'; flipped = false; revealed = false; typedAnswer = ''">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
            打字
          </button>
        </div>
      </div>

      <!-- ===== Summary ===== -->
      <div v-if="finished && cards.length > 0" class="fc-summary">
        <div class="fc-summary-icon">
          <svg v-if="accuracy >= 80" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="1.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          <svg v-else width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
        </div>
        <h2 class="fc-summary-title">{{ accuracy >= 80 ? '太棒了!' : '继续加油!' }}</h2>
        <p class="fc-summary-sub" v-if="submitting">正在保存学习记录...</p>
        <p class="fc-summary-sub" v-else>本次学习 {{ cards.length }} 张卡片</p>

        <!-- Accuracy bar -->
        <div class="fc-accuracy-bar">
          <div class="fc-accuracy-track">
            <div class="fc-accuracy-green" :style="{ width: greenPercent + '%' }"></div>
            <div class="fc-accuracy-yellow" :style="{ width: yellowPercent + '%' }"></div>
          </div>
          <div class="fc-accuracy-labels">
            <span class="green">{{ rememberedCount }} 记住</span>
            <span class="yellow">{{ fuzzyCount }} 模糊</span>
            <span class="red">{{ forgotCount }} 忘了</span>
          </div>
        </div>

        <div class="fc-summary-stats">
          <div class="fc-stat-card orange">
            <div class="fc-stat-value">{{ maxCombo }}x</div>
            <div class="fc-stat-label">最高连击</div>
          </div>
          <div class="fc-stat-card green">
            <div class="fc-stat-value">{{ Math.round(accuracy) }}%</div>
            <div class="fc-stat-label">正确率</div>
          </div>
        </div>
        <div class="fc-summary-actions">
          <button class="fc-done-btn secondary" @click="restudyAll">再学一遍</button>
          <button class="fc-done-btn" @click="$emit('back')" :disabled="submitting">返回</button>
        </div>
      </div>

      <!-- ===== Empty State ===== -->
      <div v-else-if="cards.length === 0" class="fc-empty-state">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#C8B8A8" stroke-width="1.5"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="M6 8h.01"/><path d="M10 8h8"/><path d="M6 12h.01"/><path d="M10 12h8"/></svg>
        <h3>当前没有待复习的卡片</h3>
        <p>所有卡片都在记忆巩固期，稍后再来复习效果更好</p>
        <div class="fc-empty-actions">
          <button class="fc-done-btn" @click="restudyAll">重新学习全部</button>
          <button class="fc-done-btn secondary" @click="$emit('back')">返回</button>
        </div>
      </div>

      <!-- ===== Card Display ===== -->
      <div v-else-if="currentCard" class="fc-study-card-area">

        <!-- ====== TYPING MODE ====== -->
        <template v-if="mode === 'type'">
          <!-- Phase 1: Type answer -->
          <div v-if="!revealed" class="fc-typing-area">
            <div class="fc-question-card">
              <div class="fc-card-badge q">Q</div>
              <div class="fc-question-text">{{ currentCard.front }}</div>
            </div>
            <textarea
              class="fc-typing-input"
              v-model="typedAnswer"
              placeholder="在这里输入你的答案..."
              rows="4"
              @keydown.ctrl.enter="revealAnswer"
            ></textarea>
            <button class="fc-reveal-btn" @click="revealAnswer" :disabled="!typedAnswer.trim()">
              提交并查看答案
              <span class="fc-shortcut">Ctrl+Enter</span>
            </button>
          </div>

          <!-- Phase 2: Compare & Rate -->
          <div v-else class="fc-compare-area">
            <div class="fc-compare-grid">
              <div class="fc-compare-col yours">
                <div class="fc-compare-label">你的回答</div>
                <div class="fc-compare-body">{{ typedAnswer }}</div>
              </div>
              <div class="fc-compare-divider">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
              </div>
              <div class="fc-compare-col correct">
                <div class="fc-compare-label">正确答案</div>
                <div class="fc-compare-body">{{ currentCard.back }}</div>
              </div>
            </div>
            <div class="fc-rating-btns">
              <button class="fc-rate-btn forgot" @click="handleRate('forgot')">
                <span class="rate-emoji">😵</span><span>忘了</span>
              </button>
              <button class="fc-rate-btn fuzzy" @click="handleRate('fuzzy')">
                <span class="rate-emoji">🤔</span><span>模糊</span>
              </button>
              <button class="fc-rate-btn remembered" @click="handleRate('remembered')">
                <span class="rate-emoji">💪</span><span>记住了</span>
              </button>
            </div>
          </div>
        </template>

        <!-- ====== FLIP MODE ====== -->
        <template v-else>
          <div class="fc-card-wrapper" @click="flipped = !flipped">
            <div class="fc-card" :class="{ flipped }">
              <div class="fc-card-inner">
                <div class="fc-card-face fc-card-front">
                  <div class="fc-card-badge q">Q</div>
                  <div class="fc-card-body">{{ currentCard.front }}</div>
                  <div class="fc-card-footer">点击查看答案</div>
                </div>
                <div class="fc-card-face fc-card-back">
                  <div class="fc-card-badge a">A</div>
                  <div class="fc-card-body">{{ currentCard.back }}</div>
                  <div class="fc-card-footer">点击返回问题</div>
                </div>
              </div>
            </div>
          </div>
          <div class="fc-rating-btns" v-if="flipped">
            <button class="fc-rate-btn forgot" @click.stop="handleRate('forgot')">
              <span class="rate-emoji">😵</span><span>忘了</span>
            </button>
            <button class="fc-rate-btn fuzzy" @click.stop="handleRate('fuzzy')">
              <span class="rate-emoji">🤔</span><span>模糊</span>
            </button>
            <button class="fc-rate-btn remembered" @click.stop="handleRate('remembered')">
              <span class="rate-emoji">💪</span><span>记住了</span>
            </button>
          </div>
          <div class="fc-rating-hint" v-else>翻转卡片后评分</div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { Card, ReviewGrade } from '../../types/flashcard'
import { useFlashcard } from '../../composables/useFlashcard'

const props = defineProps<{ deckId: string }>()
const emit = defineEmits<{ back: [] }>()

const { getStudyCards, submitBatchReview } = useFlashcard()

const cards = ref<Card[]>([])
const currentIndex = ref(0)
const flipped = ref(false)
const finished = ref(false)
const submitting = ref(false)
const mode = ref<'flip' | 'type'>('flip')
const typedAnswer = ref('')
const revealed = ref(false)
const studyMode = ref<'due' | 'all'>('due')

// Local review cache — batch submit at end
const pendingReviews = ref<{ cardId: string; grade: ReviewGrade }[]>([])
const rememberedCount = ref(0)
const fuzzyCount = ref(0)
const forgotCount = ref(0)
const combo = ref(0)
const maxCombo = ref(0)

const currentCard = computed(() => cards.value[currentIndex.value] || null)
const progressPercent = computed(() => cards.value.length > 0 ? ((currentIndex.value) / cards.value.length) * 100 : 0)
const accuracy = computed(() => {
  const total = rememberedCount.value + fuzzyCount.value + forgotCount.value
  return total > 0 ? (rememberedCount.value / total) * 100 : 0
})
const greenPercent = computed(() => {
  const total = cards.value.length || 1
  return (rememberedCount.value / total) * 100
})
const yellowPercent = computed(() => {
  const total = cards.value.length || 1
  return (fuzzyCount.value / total) * 100
})

function toggleMode() {
  mode.value = mode.value === 'flip' ? 'type' : 'flip'
  flipped.value = false
  revealed.value = false
  typedAnswer.value = ''
}

function revealAnswer() {
  if (!typedAnswer.value.trim()) return
  revealed.value = true
}

async function handleRate(grade: ReviewGrade) {
  if (!currentCard.value) return

  // Cache locally — no network call
  pendingReviews.value.push({ cardId: currentCard.value.id, grade })

  // Update combo
  if (grade === 'remembered') {
    combo.value++
    rememberedCount.value++
    if (combo.value > maxCombo.value) maxCombo.value = combo.value
  } else if (grade === 'fuzzy') {
    combo.value = 0
    fuzzyCount.value++
  } else {
    combo.value = 0
    forgotCount.value++
  }

  // Reset state for next card
  flipped.value = false
  revealed.value = false
  typedAnswer.value = ''

  if (currentIndex.value + 1 >= cards.value.length) {
    await finishSession()
  } else {
    currentIndex.value++
  }
}

async function finishSession() {
  finished.value = true
  if (pendingReviews.value.length > 0) {
    submitting.value = true
    await submitBatchReview(pendingReviews.value)
    submitting.value = false
  }
}

async function restudyAll() {
  // Reset state and load all cards
  pendingReviews.value = []
  rememberedCount.value = 0
  fuzzyCount.value = 0
  forgotCount.value = 0
  combo.value = 0
  maxCombo.value = 0
  currentIndex.value = 0
  finished.value = false
  flipped.value = false
  revealed.value = false
  typedAnswer.value = ''
  studyMode.value = 'all'
  cards.value = await getStudyCards(props.deckId, 'all')
  if (cards.value.length === 0) finished.value = true
}

async function handleExit() {
  if (pendingReviews.value.length > 0) {
    await submitBatchReview(pendingReviews.value)
  }
  emit('back')
}

onMounted(async () => {
  cards.value = await getStudyCards(props.deckId, studyMode.value)
  if (cards.value.length === 0) finished.value = true
})
</script>

<style scoped>
/* ===== Page ===== */
.fc-study-page {
  flex: 1; overflow-y: auto; background: linear-gradient(135deg, #F8F5EF 0%, #F0EBE3 100%);
  display: flex; justify-content: center;
}
.fc-study-container { max-width: 580px; width: 100%; padding: 24px 20px; min-height: 100vh; display: flex; flex-direction: column; }

/* ===== Header ===== */
.fc-study-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; flex-shrink: 0; }
.fc-back-btn {
  display: flex; align-items: center; gap: 4px;
  background: none; border: none; color: #B8A898; font-size: 13px; cursor: pointer;
}
.fc-back-btn:hover { color: #D97B2B; }
.fc-study-progress { flex: 1; display: flex; align-items: center; gap: 8px; font-size: 13px; color: #8B7E74; }
.fc-progress-bar { flex: 1; height: 4px; background: #E8DDD0; border-radius: 2px; overflow: hidden; }
.fc-progress-fill { height: 100%; background: #D97B2B; border-radius: 2px; transition: width 0.4s ease; }

/* Combo badge */
.fc-combo-badge {
  min-width: 36px; height: 24px; border-radius: 12px; text-align: center; line-height: 24px;
  font-size: 12px; font-weight: 700; background: #FEF3E8; color: #D97B2B;
  transition: all 0.3s ease; animation: combo-pop 0.3s ease;
}
.fc-combo-badge.hot { background: #FEF3C7; color: #CA8A04; }
.fc-combo-badge.fire { background: #FEE2E2; color: #DC2626; }
@keyframes combo-pop { 0% { transform: scale(0.8); } 50% { transform: scale(1.15); } 100% { transform: scale(1); } }

/* Mode switch */
.fc-mode-switch {
  display: flex; border: 1px solid #E8DDD0; border-radius: 8px; overflow: hidden; flex-shrink: 0;
}
.fc-mode-opt {
  display: flex; align-items: center; gap: 4px; padding: 5px 10px;
  border: none; background: #fff; cursor: pointer; font-size: 12px; font-weight: 500;
  color: #B8A898; transition: all 0.2s; white-space: nowrap;
}
.fc-mode-opt:first-child { border-right: 1px solid #E8DDD0; }
.fc-mode-opt:hover { color: #D97B2B; background: #FEF8F0; }
.fc-mode-opt.active { background: #D97B2B; color: #fff; }
.fc-mode-opt.active svg { stroke: #fff; }

/* ===== Card Area ===== */
.fc-study-card-area { flex: 1; display: flex; flex-direction: column; align-items: center; padding-top: 8px; }

/* ===== Typing Mode ===== */
.fc-typing-area { width: 100%; display: flex; flex-direction: column; gap: 16px; }
.fc-question-card {
  background: #fff; border: 2px solid #E8DDD0; border-radius: 14px;
  padding: 24px; position: relative;
  box-shadow: 0 4px 16px rgba(61,48,40,0.08);
}
.fc-question-text { font-size: 17px; color: #3D3028; line-height: 1.7; text-align: center; padding-top: 8px; }
.fc-typing-input {
  width: 100%; border: 2px solid #E8DDD0; border-radius: 12px;
  padding: 14px 16px; font-size: 15px; line-height: 1.6; resize: vertical;
  min-height: 100px; outline: none; background: #fff; color: #3D3028;
  font-family: inherit; transition: border-color 0.2s;
}
.fc-typing-input:focus { border-color: #D97B2B; }
.fc-typing-input::placeholder { color: #C8B8A8; }
.fc-reveal-btn {
  width: 100%; padding: 14px; background: #D97B2B; color: #fff; border: none; border-radius: 10px;
  font-size: 15px; font-weight: 600; cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; justify-content: center; gap: 8px;
}
.fc-reveal-btn:hover:not(:disabled) { background: #C86A20; }
.fc-reveal-btn:disabled { background: #C8B8A8; cursor: not-allowed; }
.fc-shortcut { font-size: 11px; opacity: 0.7; font-weight: 400; }

/* ===== Compare Area ===== */
.fc-compare-area { width: 100%; display: flex; flex-direction: column; gap: 20px; }
.fc-compare-grid {
  display: grid; grid-template-columns: 1fr auto 1fr; gap: 8px; align-items: stretch;
}
.fc-compare-col {
  background: #fff; border-radius: 12px; padding: 18px 16px;
  border: 2px solid #E8DDD0; display: flex; flex-direction: column;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.fc-compare-col.correct { border-color: #16a34a; background: #FAFDF7; }
.fc-compare-divider { display: flex; align-items: center; justify-content: center; padding: 0 4px; }
.fc-compare-label { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #8B7E74; margin-bottom: 10px; }
.fc-compare-col.correct .fc-compare-label { color: #16a34a; }
.fc-compare-body { font-size: 14px; color: #3D3028; line-height: 1.6; flex: 1; word-break: break-word; }

/* ===== Flip Card ===== */
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

/* ===== Rating Buttons ===== */
.fc-rating-btns { display: flex; gap: 12px; width: 100%; }
.fc-rate-btn {
  flex: 1; padding: 14px 8px; border: 2px solid transparent; border-radius: 12px;
  font-size: 14px; font-weight: 600; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  transition: all 0.2s ease;
}
.fc-rate-btn:active { transform: scale(0.95); }
.fc-rate-btn.remembered { background: #F0FDF4; color: #16a34a; border-color: #BBF7D0; }
.fc-rate-btn.remembered:hover { background: #DCFCE7; border-color: #16a34a; }
.fc-rate-btn.fuzzy { background: #FEFCE8; color: #CA8A04; border-color: #FEF08A; }
.fc-rate-btn.fuzzy:hover { background: #FEF3C7; border-color: #CA8A04; }
.fc-rate-btn.forgot { background: #FEF2F2; color: #DC2626; border-color: #FECACA; }
.fc-rate-btn.forgot:hover { background: #FEE2E2; border-color: #DC2626; }
.rate-emoji { font-size: 20px; line-height: 1; }
.fc-rating-hint { font-size: 13px; color: #C8B8A8; }

/* ===== Empty State ===== */
.fc-empty-state {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  text-align: center; padding: 40px 20px; gap: 12px;
}
.fc-empty-state h3 { font-size: 18px; font-weight: 600; color: #3D3028; margin: 16px 0 4px; }
.fc-empty-state p { font-size: 14px; color: #8B7E74; margin-bottom: 24px; }
.fc-empty-actions { display: flex; gap: 12px; }

/* ===== Summary ===== */
.fc-summary { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 40px 0; }
.fc-summary-icon { margin-bottom: 16px; }
.fc-summary-title { font-size: 24px; font-weight: 700; color: #3D3028; margin-bottom: 4px; }
.fc-summary-sub { font-size: 14px; color: #8B7E74; margin-bottom: 20px; }

/* Accuracy bar */
.fc-accuracy-bar { width: 100%; max-width: 320px; margin-bottom: 24px; }
.fc-accuracy-track { height: 8px; border-radius: 4px; overflow: hidden; display: flex; background: #FEE2E2; }
.fc-accuracy-green { background: #16a34a; transition: width 0.6s ease; border-radius: 4px 0 0 4px; }
.fc-accuracy-yellow { background: #CA8A04; transition: width 0.6s ease; }
.fc-accuracy-labels { display: flex; justify-content: center; gap: 16px; margin-top: 8px; font-size: 12px; }
.fc-accuracy-labels .green { color: #16a34a; }
.fc-accuracy-labels .yellow { color: #CA8A04; }
.fc-accuracy-labels .red { color: #DC2626; }

.fc-summary-stats { display: flex; gap: 16px; margin-bottom: 28px; }
.fc-stat-card { background: #fff; border-radius: 12px; padding: 20px 28px; text-align: center; min-width: 90px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.fc-stat-card.orange { border-top: 3px solid #D97B2B; }
.fc-stat-card.green { border-top: 3px solid #16a34a; }
.fc-stat-value { font-size: 28px; font-weight: 700; color: #3D3028; }
.fc-stat-label { font-size: 12px; color: #8B7E74; margin-top: 4px; }

.fc-summary-actions { display: flex; gap: 12px; }
.fc-done-btn {
  padding: 12px 32px; background: #D97B2B; color: #fff;
  border: none; border-radius: 10px; font-size: 15px; font-weight: 600;
  cursor: pointer; transition: all 0.2s;
}
.fc-done-btn:hover:not(:disabled) { background: #C86A20; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(217,123,43,0.3); }
.fc-done-btn:disabled { background: #C8B8A8; cursor: not-allowed; }
.fc-done-btn.secondary {
  background: #fff; color: #3D3028; border: 2px solid #E8DDD0;
}
.fc-done-btn.secondary:hover:not(:disabled) { background: #FEF3E8; border-color: #D97B2B; box-shadow: none; }

/* ===== Responsive ===== */
@media (max-width: 480px) {
  .fc-compare-grid { grid-template-columns: 1fr; }
  .fc-compare-divider { transform: rotate(90deg); padding: 4px 0; }
}
</style>
