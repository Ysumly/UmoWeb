<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import GameResultDialog from '@/components/games/GameResultDialog.vue'
import GameShell from '@/components/games/GameShell.vue'
import {
  formatGameDuration,
  generateSchulteNumbers,
  getSchulteClickResult,
  readSchulteBest,
  updateSchulteBest,
} from '@/games/gameLogic'

const STORAGE_KEY = 'schulte_parchment_best'
const sizes = [3, 4, 5, 6, 7, 8, 9, 10]
const size = ref(5)
const numbers = ref([])
const nextNumber = ref(1)
const elapsed = ref(0)
const running = ref(false)
const completed = ref(new Set())
const wrongNumber = ref(null)
const resultOpen = ref(false)
const records = ref({})
const reducedMotion = ref(false)

let animationFrameId = null
let wrongTimer = null
let startTime = 0

const total = computed(() => size.value * size.value)
const currentBest = computed(() => records.value[String(size.value)] ?? null)
const resultStats = computed(() => [
  { label: '用时', value: formatGameDuration(elapsed.value), tone: 'accent' },
  { label: '最佳成绩', value: formatGameDuration(currentBest.value) },
  { label: '点击次数', value: nextNumber.value - 1 },
])
const gridStyle = computed(() => ({
  '--schulte-size': size.value,
}))

function readStorage() {
  try {
    records.value = readSchulteBest(localStorage.getItem(STORAGE_KEY))
  } catch {
    records.value = {}
  }
}

function writeStorage(nextRecords) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(nextRecords))
  } catch {
    // Ignore storage failures while keeping the game available.
  }
}

function shuffleBoard() {
  numbers.value = generateSchulteNumbers(size.value)
  completed.value = new Set()
  wrongNumber.value = null
  nextNumber.value = 1
  elapsed.value = 0
}

function tick() {
  if (!running.value) {
    return
  }
  elapsed.value = performance.now() - startTime
  animationFrameId = requestAnimationFrame(tick)
}

function startGame() {
  if (running.value) {
    return
  }
  shuffleBoard()
  running.value = true
  startTime = performance.now()
  animationFrameId = requestAnimationFrame(tick)
}

function selectSize(nextSize) {
  if (running.value) {
    return
  }
  size.value = nextSize
  shuffleBoard()
}

function handleNumber(number) {
  if (!running.value) {
    return
  }

  const result = getSchulteClickResult(number, nextNumber.value)
  if (result === 'ignored') {
    return
  }
  if (result === 'wrong') {
    wrongNumber.value = number
    window.clearTimeout(wrongTimer)
    wrongTimer = window.setTimeout(() => {
      wrongNumber.value = null
    }, 180)
    return
  }

  completed.value = new Set([...completed.value, number])
  if (number >= total.value) {
    finish()
    return
  }
  nextNumber.value += 1
}

function finish() {
  running.value = false
  nextNumber.value = total.value
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
  elapsed.value = performance.now() - startTime
  const result = updateSchulteBest(
    JSON.stringify(records.value),
    size.value,
    elapsed.value,
  )
  records.value = result.best
  writeStorage(records.value)
  resultOpen.value = true
}

function resetGame() {
  running.value = false
  resultOpen.value = false
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
  readStorage()
  shuffleBoard()
}

function handleKeydown(event) {
  if (resultOpen.value && event.key === 'Escape') {
    resetGame()
    return
  }
  if (
    !running.value
    && (event.key === ' ' || event.code === 'Space')
    && !resultOpen.value
  ) {
    event.preventDefault()
    startGame()
  }
}

onMounted(() => {
  reducedMotion.value = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  readStorage()
  shuffleBoard()
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  window.clearTimeout(wrongTimer)
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
  }
})
</script>

<template>
  <div class="game-route">
    <GameShell
    eyebrow="Schulte Grid"
    title="舒尔特方格"
    subtitle="按顺序从 1 点击到末尾，训练视觉搜索速度。"
    note="空格开始；已点击数字不会重复计算，点错较高数字只会短暂反馈。"
  >
    <div class="game-stats">
      <div>
        <span>用时</span>
        <strong>{{ formatGameDuration(elapsed) }}</strong>
      </div>
      <div>
        <span>进度</span>
        <strong>{{ nextNumber - 1 }} / {{ total }}</strong>
      </div>
      <div class="game-stats__best">
        <span>当前尺寸最佳</span>
        <strong>{{ formatGameDuration(currentBest) }}</strong>
      </div>
    </div>

    <div class="schulte-sizes" aria-label="选择网格尺寸">
      <button
        v-for="item in sizes"
        :key="item"
        type="button"
        :class="{ 'is-active': item === size }"
        :disabled="running"
        @click="selectSize(item)"
      >
        {{ item }} × {{ item }}
      </button>
    </div>

    <div class="schulte-stage">
      <div class="schulte-grid" :style="gridStyle" role="grid" :aria-label="`${size} × ${size} 数字网格`">
        <button
          v-for="number in numbers"
          :key="number"
          type="button"
          role="gridcell"
          :data-number="number"
          :class="{
            'is-complete': completed.has(number),
            'is-wrong': wrongNumber === number,
          }"
          :disabled="!running"
          @click="handleNumber(number)"
        >
          {{ number }}
        </button>
      </div>
    </div>

    <div class="game-actions">
      <button class="button button--outline" type="button" @click="resetGame">重新洗牌</button>
      <button class="button button--primary" type="button" :disabled="running" @click="startGame">
        {{ running ? '挑战中…' : '开始挑战' }}
      </button>
    </div>

    <div v-if="resultOpen && !reducedMotion" class="game-confetti" aria-hidden="true">
      <span v-for="index in 10" :key="index" :style="{ '--confetti-index': index }" />
    </div>
    </GameShell>

    <GameResultDialog
      :open="resultOpen"
      :title="`${size} × ${size} 挑战完成`"
      icon="格"
      :stats="resultStats"
      :detail="`完成 ${total} 个数字，成绩已与当前尺寸的历史最佳比较。`"
      action-label="再来一局"
      @close="resetGame"
    />
  </div>
</template>
