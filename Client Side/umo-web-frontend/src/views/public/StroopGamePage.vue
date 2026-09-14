<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import GameResultDialog from '@/components/games/GameResultDialog.vue'
import GameShell from '@/components/games/GameShell.vue'
import {
  STROOP_COLOR_KEYS,
  STROOP_WORDS,
  formatGameDuration,
  formatReactionTime,
  generateStroopTrial,
  readStroopRecords,
  updateStroopRecords,
} from '@/games/gameLogic'

const TOTAL_TRIALS = 84
const STORAGE_KEY = 'stroop_84_parchment'
const colorValues = {
  red: 'var(--game-red)',
  blue: 'var(--game-blue)',
  green: 'var(--game-green)',
  yellow: 'var(--game-gold)',
  black: 'var(--game-ink)',
}

const word = ref('红')
const colorKey = ref('red')
const progress = ref(0)
const correct = ref(0)
const errors = ref(0)
const elapsed = ref(0)
const running = ref(false)
const finished = ref(false)
const inputLocked = ref(false)
const flash = ref('')
const feedback = ref('点击下方与字体颜色一致的按钮')
const records = ref({})
const reactionTimes = ref([])

let animationFrameId = null
let startTime = 0
let trialStartTime = 0
let flashTimer = null

const accuracy = computed(() => {
  const total = correct.value + errors.value
  return total ? correct.value / total : 0
})

const averageReactionTime = computed(() => {
  const valid = reactionTimes.value.filter((item) => item.correct).map((item) => item.milliseconds)
  return valid.length
    ? valid.reduce((sum, value) => sum + value, 0) / valid.length
    : 0
})

const bestAccuracyLabel = computed(() => (
  records.value.bestAccuracy
    ? `${(records.value.bestAccuracy * 100).toFixed(1).replace(/\.0$/, '')}%`
    : '--'
))

const bestReactionLabel = computed(() => (
  records.value.bestAvgRT ? formatReactionTime(records.value.bestAvgRT) : '---'
))

const resultStats = computed(() => [
  { label: '正确率', value: `${Math.round(accuracy.value * 100)}%`, tone: 'accent' },
  { label: '平均反应时', value: formatReactionTime(averageReactionTime.value), tone: 'green' },
  { label: '正确 / 错误', value: `${correct.value} / ${errors.value}` },
  { label: '总用时', value: formatGameDuration(elapsed.value) },
])

const resultDetail = computed(() => {
  if (accuracy.value === 1 && correct.value === TOTAL_TRIALS) {
    return '全部正确，本次训练保持满准确率。'
  }
  return `历史最佳正确率 ${bestAccuracyLabel.value}，最佳平均反应时 ${bestReactionLabel.value}。`
})

function readStorage() {
  try {
    records.value = readStroopRecords(localStorage.getItem(STORAGE_KEY))
  } catch {
    records.value = {}
  }
}

function writeRecords(nextRecords) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(nextRecords))
  } catch {
    // The game remains playable when browser storage is unavailable.
  }
}

function tick() {
  if (!running.value) {
    return
  }
  elapsed.value = performance.now() - startTime
  animationFrameId = requestAnimationFrame(tick)
}

function showTrial() {
  const trial = generateStroopTrial()
  word.value = trial.word
  colorKey.value = trial.colorKey
  trialStartTime = performance.now()
}

function startGame() {
  if (running.value) {
    return
  }

  running.value = true
  finished.value = false
  inputLocked.value = false
  correct.value = 0
  errors.value = 0
  progress.value = 0
  reactionTimes.value = []
  elapsed.value = 0
  feedback.value = '判断字体颜色，不需要读出文字'
  flash.value = ''
  showTrial()

  startTime = performance.now()
  animationFrameId = requestAnimationFrame(tick)
}

function markFlash(type) {
  flash.value = type
  window.clearTimeout(flashTimer)
  flashTimer = window.setTimeout(() => {
    flash.value = ''
  }, 180)
}

function answer(nextColor) {
  if (!running.value || inputLocked.value) {
    return
  }

  inputLocked.value = true
  const isCorrect = nextColor === colorKey.value
  reactionTimes.value.push({
    correct: isCorrect,
    milliseconds: Math.max(performance.now() - trialStartTime, 0.1),
  })

  if (isCorrect) {
    correct.value += 1
    markFlash('correct')
  } else {
    errors.value += 1
    markFlash('wrong')
  }

  progress.value = correct.value + errors.value
  inputLocked.value = false

  if (progress.value >= TOTAL_TRIALS) {
    finish()
    return
  }

  showTrial()
}

function finish() {
  running.value = false
  finished.value = true
  inputLocked.value = true
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
  elapsed.value = performance.now() - startTime

  const result = updateStroopRecords(
    JSON.stringify(records.value),
    accuracy.value,
    averageReactionTime.value,
  )
  records.value = result.records
  writeRecords(records.value)
}

function resetGame() {
  running.value = false
  finished.value = false
  inputLocked.value = false
  correct.value = 0
  errors.value = 0
  progress.value = 0
  elapsed.value = 0
  word.value = '红'
  colorKey.value = 'red'
  flash.value = ''
  reactionTimes.value = []
  feedback.value = '点击下方与字体颜色一致的按钮'
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
}

function handleKeydown(event) {
  if ((event.key === ' ' || event.code === 'Space') && !running.value && !finished.value) {
    event.preventDefault()
    startGame()
    return
  }

  const colorIndex = Number(event.key) - 1
  if (running.value && !inputLocked.value && colorIndex >= 0 && colorIndex < STROOP_COLOR_KEYS.length) {
    event.preventDefault()
    answer(STROOP_COLOR_KEYS[colorIndex])
  }
}

onMounted(() => {
  readStorage()
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  window.clearTimeout(flashTimer)
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
  }
})
</script>

<template>
  <div class="game-route">
    <GameShell
    eyebrow="Stroop Test"
    title="色词测试"
    subtitle="84 试次反应训练，只判断字体颜色。"
    note="键盘 1–5 作答，空格开始；作答后立即进入下一试次。"
  >
    <div class="game-stats">
      <div>
        <span>进度</span>
        <strong>{{ progress }} / {{ TOTAL_TRIALS }}</strong>
      </div>
      <div>
        <span>正确</span>
        <strong>{{ correct }}</strong>
      </div>
      <div>
        <span>错误</span>
        <strong>{{ errors }}</strong>
      </div>
      <div>
        <span>用时</span>
        <strong>{{ formatGameDuration(elapsed) }}</strong>
      </div>
      <div class="game-stats__best">
        <span>历史最佳</span>
        <strong>{{ bestAccuracyLabel }} · {{ bestReactionLabel }}</strong>
      </div>
    </div>

    <div class="stroop-stage">
      <div class="stroop-word" :class="flash ? `is-${flash}` : ''" :style="{ color: colorValues[colorKey] }">
        {{ word }}
      </div>
      <p>{{ feedback }}</p>
    </div>

    <div class="stroop-colors" aria-label="选择字体颜色">
      <button
        v-for="(key, index) in STROOP_COLOR_KEYS"
        :key="key"
        class="stroop-color-button"
        :class="`is-${key}`"
        type="button"
        :aria-label="`${STROOP_WORDS[index]}色，数字键 ${index + 1}`"
        :disabled="!running"
        @click="answer(key)"
      >
        <span>{{ STROOP_WORDS[index] }}</span>
      </button>
    </div>

    <div class="game-actions">
      <button class="button button--primary" type="button" :disabled="running" @click="startGame">
        {{ running ? '测试中…' : '开始挑战' }}
      </button>
    </div>
    </GameShell>

    <GameResultDialog
      :open="finished"
      title="84 试次完成"
      icon="色"
      :stats="resultStats"
      :detail="resultDetail"
      action-label="再来一局"
      @close="resetGame"
    />
  </div>
</template>
