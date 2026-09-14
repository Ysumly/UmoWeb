<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import GameResultDialog from '@/components/games/GameResultDialog.vue'
import GameShell from '@/components/games/GameShell.vue'
import {
  POKER_RANK_NAMES,
  evaluatePokerRound,
  generatePokerCards,
  getPokerMemorySpan,
  readPokerBest,
  selectPokerTarget,
  updatePokerBest,
} from '@/games/gameLogic'

const STORAGE_KEY = 'poker_memory_best_span'
const level = ref(2)
const attempts = ref(0)
const correct = ref(0)
const cards = ref([])
const target = ref(null)
const targetPosition = ref(-1)
const phase = ref('idle')
const faceUp = ref(true)
const countdown = ref(0)
const question = ref('')
const feedback = ref('')
const resultOpen = ref(false)
const resultSpan = ref(0)
const resultIsNew = ref(false)
const bestSpan = ref(0)

let countdownTimer = null
let feedbackTimer = null
let nextAttemptTimer = null

const resultStats = computed(() => [
  { label: '最终记忆广度', value: resultSpan.value > 0 ? `${resultSpan.value} 张` : '< 2', tone: 'accent' },
  { label: '当前等级', value: `${level.value} 张` },
  { label: '历史最佳', value: bestSpan.value > 0 ? `${bestSpan.value} 张` : '--', tone: 'green' },
])

function readStorage() {
  try {
    bestSpan.value = readPokerBest(localStorage.getItem(STORAGE_KEY))
  } catch {
    bestSpan.value = 0
  }
}

function writeStorage(span) {
  try {
    localStorage.setItem(STORAGE_KEY, String(span))
  } catch {
    // Ignore storage failures while keeping the game available.
  }
}

function clearTimers() {
  window.clearTimeout(countdownTimer)
  window.clearTimeout(feedbackTimer)
  window.clearTimeout(nextAttemptTimer)
}

function startNewAttempt() {
  clearTimers()
  faceUp.value = true
  countdown.value = 0
  feedback.value = ''
  question.value = ''
  cards.value = generatePokerCards(level.value)
  const selection = selectPokerTarget(cards.value)
  target.value = selection.card
  targetPosition.value = selection.position
  phase.value = 'idle'
}

function startMemorize() {
  if (phase.value !== 'idle') {
    return
  }

  phase.value = 'memorizing'
  faceUp.value = false
  countdown.value = 3
  feedback.value = '记住牌面位置'

  const tick = () => {
    if (phase.value !== 'memorizing') {
      return
    }
    countdown.value -= 1
    if (countdown.value <= 0) {
      countdown.value = 0
      askQuestion()
      return
    }
    countdownTimer = window.setTimeout(tick, 1000)
  }

  countdownTimer = window.setTimeout(tick, 1000)
}

function askQuestion() {
  phase.value = 'question'
  question.value = `${target.value.suit.name}${POKER_RANK_NAMES[target.value.rank]} 在第几个位置？`
  feedback.value = ''
}

function answerPosition(position) {
  if (phase.value !== 'question') {
    return
  }

  phase.value = 'feedback'
  faceUp.value = true
  const isCorrect = position === targetPosition.value
  if (isCorrect) {
    correct.value += 1
    feedback.value = '正确！'
  } else {
    feedback.value = `错误，正确答案是位置 ${targetPosition.value + 1}。`
  }
  attempts.value += 1

  feedbackTimer = window.setTimeout(() => {
    const outcome = evaluatePokerRound(correct.value, attempts.value)
    if (outcome === 'continue') {
      startNewAttempt()
      return
    }
    if (outcome === 'pass') {
      level.value += 1
      attempts.value = 0
      correct.value = 0
      nextAttemptTimer = window.setTimeout(startNewAttempt, 250)
      return
    }
    endGame()
  }, 700)
}

function endGame() {
  clearTimers()
  phase.value = 'game-over'
  const span = getPokerMemorySpan(level.value)
  resultSpan.value = span
  const result = updatePokerBest(String(bestSpan.value), span)
  if (result.isNew && span >= 2) {
    bestSpan.value = result.best
    writeStorage(bestSpan.value)
    resultIsNew.value = true
  } else {
    resultIsNew.value = false
  }
  resultOpen.value = true
}

function resetGame() {
  clearTimers()
  level.value = 2
  attempts.value = 0
  correct.value = 0
  resultOpen.value = false
  resultSpan.value = 0
  resultIsNew.value = false
  readStorage()
  startNewAttempt()
}

function handleKeydown(event) {
  if ((event.key === ' ' || event.code === 'Space') && phase.value === 'idle' && !resultOpen.value) {
    event.preventDefault()
    startMemorize()
    return
  }

  const position = Number(event.key) - 1
  if (phase.value === 'question' && position >= 0 && position < level.value) {
    event.preventDefault()
    answerPosition(position)
  }
}

onMounted(() => {
  readStorage()
  startNewAttempt()
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  clearTimers()
})
</script>

<template>
  <div class="game-route">
    <GameShell
    eyebrow="Poker Memory"
    title="扑克牌记忆训练"
    subtitle="观察牌面三秒，牌背朝上后回答目标牌的位置。"
    note="每轮三题答对两题升级；数字键可快速选择位置。"
  >
    <div class="game-stats">
      <div>
        <span>当前牌数</span>
        <strong>{{ level }}</strong>
      </div>
      <div>
        <span>本轮答题</span>
        <strong>{{ attempts }} / 3</strong>
      </div>
      <div>
        <span>本轮答对</span>
        <strong>{{ correct }}</strong>
      </div>
      <div class="game-stats__best">
        <span>历史最佳</span>
        <strong>{{ bestSpan > 0 ? `${bestSpan} 张` : '--' }}</strong>
      </div>
    </div>

    <div class="poker-stage">
      <div class="poker-status" aria-live="polite">
        <span v-if="phase === 'memorizing'">记忆倒计时 {{ countdown }}</span>
        <span v-else-if="phase === 'question'">{{ question }}</span>
        <span v-else>{{ feedback || '牌面已就绪，点击开始记忆。' }}</span>
      </div>

      <div
        class="poker-cards"
        :style="{ '--card-count': Math.min(level, 10) }"
        :class="`cards-${Math.min(level, 10)}`"
      >
        <div
          v-for="(card, index) in cards"
          :key="`${card.suit.key}-${card.rank}`"
          class="playing-card"
          :class="[
            faceUp ? 'is-face-up' : 'is-face-down',
            card.suit.color === 'red' ? 'is-red' : 'is-black',
          ]"
          :aria-label="faceUp ? `位置 ${index + 1}，${card.suit.name}${POKER_RANK_NAMES[card.rank]}` : `位置 ${index + 1}，牌面朝下`"
        >
          <template v-if="faceUp">
            <span>{{ POKER_RANK_NAMES[card.rank] }}</span>
            <strong>{{ card.suit.symbol }}</strong>
          </template>
          <span v-else aria-hidden="true">牌</span>
        </div>
      </div>
    </div>

    <div v-if="phase === 'question' || phase === 'feedback'" class="position-grid" aria-label="选择目标牌位置">
      <button
        v-for="position in level"
        :key="position"
        type="button"
        :aria-label="`位置 ${position}`"
        :disabled="phase !== 'question'"
        @click="answerPosition(position - 1)"
      >
        {{ position }}
      </button>
    </div>

    <div class="game-actions">
      <button class="button button--quiet" type="button" @click="resetGame">重新开始</button>
      <button class="button button--primary" type="button" :disabled="phase !== 'idle'" @click="startMemorize">
        开始记忆
      </button>
    </div>
    </GameShell>

    <GameResultDialog
      :open="resultOpen"
      title="扑克牌记忆结束"
      icon="牌"
      :stats="resultStats"
      :detail="resultIsNew ? '本次刷新了本地最佳记忆广度。' : '成绩只保存在当前浏览器。'"
      action-label="再来一局"
      @close="resetGame"
    />
  </div>
</template>
