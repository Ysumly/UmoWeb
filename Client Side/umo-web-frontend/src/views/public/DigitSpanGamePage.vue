<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import GameResultDialog from '@/components/games/GameResultDialog.vue'
import GameShell from '@/components/games/GameShell.vue'
import {
  generateDigits,
  getDigitSpanDisplayDuration,
  getDigitSpanRating,
  reverseDigits,
} from '@/games/gameLogic'

const INITIAL_DIGITS = 4
const ROUNDS_PER_LEVEL = 3
const REQUIRED_CORRECT = 2
const GAME_OVER_WRONG = 2

const phase = ref('idle')
const currentDigits = ref(INITIAL_DIGITS)
const roundIndex = ref(0)
const correctCount = ref(0)
const wrongCount = ref(0)
const totalScore = ref(0)
const maxSpan = ref(0)
const currentNumber = ref('')
const answer = ref('')
const feedback = ref('')
const feedbackTone = ref('')
const roundResults = ref([])
const resultOpen = ref(false)
const inputRef = ref(null)

let hideTimer = null
let feedbackTimer = null

const roundProgress = computed(() => correctCount.value + wrongCount.value)
const rating = computed(() => getDigitSpanRating(maxSpan.value))
const resultStats = computed(() => [
  { label: '最大记忆广度', value: `${maxSpan.value} 位`, tone: 'accent' },
  { label: '正确次数', value: totalScore.value },
  { label: '最终评级', value: rating.value.text, tone: 'green' },
])
const resultDetail = computed(() => (
  `从 ${INITIAL_DIGITS} 位开始，共完成 ${totalScore.value} 次正确倒背。`
))

function clearTimers() {
  window.clearTimeout(hideTimer)
  window.clearTimeout(feedbackTimer)
}

function setFeedback(message, tone = '') {
  feedback.value = message
  feedbackTone.value = tone
}

function showDigits() {
  if (phase.value !== 'idle') {
    return
  }

  currentNumber.value = generateDigits(currentDigits.value)
  answer.value = ''
  setFeedback('')
  phase.value = 'showing'
  window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(hideDigits, getDigitSpanDisplayDuration(currentDigits.value))
}

async function hideDigits() {
  if (phase.value !== 'showing') {
    return
  }
  phase.value = 'input'
  await nextTick()
  inputRef.value?.focus()
}

function submitAnswer() {
  if (phase.value !== 'input') {
    return
  }

  const value = answer.value.trim()
  if (!value || !/^\d+$/.test(value)) {
    setFeedback('请输入纯数字答案', 'wrong')
    inputRef.value?.focus()
    return
  }

  const expected = reverseDigits(currentNumber.value)
  const isCorrect = value === expected
  const nextResults = [...roundResults.value]
  nextResults[roundIndex.value] = isCorrect ? 'correct' : 'wrong'
  roundResults.value = nextResults

  if (isCorrect) {
    correctCount.value += 1
    totalScore.value += 1
    setFeedback(`正确，${currentNumber.value} 的倒序是 ${expected}。`, 'correct')
  } else {
    wrongCount.value += 1
    setFeedback(`答案应为 ${expected}，你的输入是 ${value}。`, 'wrong')
  }

  phase.value = 'feedback'
  window.clearTimeout(feedbackTimer)
  feedbackTimer = window.setTimeout(proceedRound, isCorrect ? 500 : 1000)
}

function proceedRound() {
  if (phase.value !== 'feedback') {
    return
  }

  roundIndex.value += 1
  if (correctCount.value >= REQUIRED_CORRECT) {
    levelUp()
    return
  }
  if (wrongCount.value >= GAME_OVER_WRONG) {
    endGame()
    return
  }

  answer.value = ''
  setFeedback('')
  phase.value = 'idle'
}

function levelUp() {
  maxSpan.value = Math.max(maxSpan.value, currentDigits.value)
  currentDigits.value += 1
  resetLevel()
  setFeedback(`升级到 ${currentDigits.value} 位数字。`, 'info')
}

function resetLevel() {
  roundIndex.value = 0
  correctCount.value = 0
  wrongCount.value = 0
  roundResults.value = []
  answer.value = ''
  currentNumber.value = ''
  phase.value = 'idle'
  clearTimers()
}

function endGame() {
  maxSpan.value = Math.max(maxSpan.value, currentDigits.value - 1, INITIAL_DIGITS - 1)
  phase.value = 'ended'
  clearTimers()
  resultOpen.value = true
}

function restart() {
  clearTimers()
  currentDigits.value = INITIAL_DIGITS
  roundIndex.value = 0
  correctCount.value = 0
  wrongCount.value = 0
  totalScore.value = 0
  maxSpan.value = 0
  currentNumber.value = ''
  roundResults.value = []
  resultOpen.value = false
  setFeedback('')
  phase.value = 'idle'
}

function skipFeedback() {
  if (phase.value !== 'feedback') {
    return
  }
  window.clearTimeout(feedbackTimer)
  proceedRound()
}

function handleKeydown(event) {
  if (phase.value === 'feedback' && (event.key === 'Enter' || event.key === ' ')) {
    event.preventDefault()
    skipFeedback()
    return
  }
  if (phase.value === 'idle' && (event.key === ' ' || event.code === 'Space') && !resultOpen.value) {
    event.preventDefault()
    showDigits()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  clearTimers()
})
</script>

<template>
  <GameShell
    eyebrow="Reverse Digit Span"
    title="倒背数字训练"
    subtitle="记住数字并按相反顺序输入，每级三题答对两题即可升级。"
    note="空格显示数字，Enter 提交答案；反馈阶段可用 Enter 或空格跳过等待。"
  >
    <div class="game-stats">
      <div>
        <span>当前位数</span>
        <strong>{{ currentDigits }}</strong>
      </div>
      <div>
        <span>总分</span>
        <strong>{{ totalScore }}</strong>
      </div>
      <div>
        <span>本轮进度</span>
        <strong>{{ roundProgress }} / {{ ROUNDS_PER_LEVEL }}</strong>
      </div>
      <div class="game-stats__best">
        <span>规则</span>
        <strong>答对 {{ REQUIRED_CORRECT }} 次升级</strong>
      </div>
    </div>

    <div class="digit-rounds" aria-label="本轮进度">
      <span
        v-for="index in ROUNDS_PER_LEVEL"
        :key="index"
        :class="{
          'is-correct': roundResults[index - 1] === 'correct',
          'is-wrong': roundResults[index - 1] === 'wrong',
          'is-current': index - 1 === roundIndex && phase !== 'ended',
        }"
      />
    </div>

    <div class="digit-stage">
      <div v-if="phase === 'showing'" class="digit-display">{{ currentNumber }}</div>
      <p v-else-if="phase === 'input'">数字已隐藏，请倒序输入。</p>
      <p v-else-if="phase === 'feedback'" :class="`is-${feedbackTone}`">{{ feedback }}</p>
      <p v-else-if="phase === 'ended'">本轮训练已结束。</p>
      <p v-else>点击显示数字，记住后倒序输入。</p>
    </div>

    <div class="digit-input">
      <label for="digit-answer">倒序答案</label>
      <input
        id="digit-answer"
        ref="inputRef"
        v-model="answer"
        type="text"
        inputmode="numeric"
        autocomplete="off"
        maxlength="24"
        :disabled="phase !== 'input'"
        @keydown.enter.prevent="submitAnswer"
      >
    </div>

    <div class="game-actions">
      <button class="button button--quiet" type="button" @click="restart">重新开始</button>
      <button class="button button--primary" type="button" :disabled="phase !== 'idle'" @click="showDigits">
        显示数字
      </button>
      <button class="button button--primary" type="button" :disabled="phase !== 'input'" @click="submitAnswer">
        确认
      </button>
    </div>
  </GameShell>

  <GameResultDialog
    :open="resultOpen"
    title="倒背数字训练结束"
    icon="数"
    :stats="resultStats"
    :detail="resultDetail"
    action-label="再来一局"
    @close="restart"
  />
</template>
