export const STROOP_WORDS = ['红', '蓝', '绿', '黄', '黑']
export const STROOP_COLOR_KEYS = ['red', 'blue', 'green', 'yellow', 'black']

export const POKER_SUITS = [
  { key: 'spades', name: '黑桃', symbol: '♠', color: 'black' },
  { key: 'hearts', name: '红桃', symbol: '♥', color: 'red' },
  { key: 'clubs', name: '梅花', symbol: '♣', color: 'black' },
  { key: 'diamonds', name: '方块', symbol: '♦', color: 'red' },
]

export const POKER_RANK_NAMES = [
  '',
  'A',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  '9',
  '10',
  'J',
  'Q',
  'K',
]

function parseObject(raw) {
  if (typeof raw !== 'string' || !raw.trim()) {
    return {}
  }

  try {
    const value = JSON.parse(raw)
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  } catch {
    return {}
  }
}

function positiveNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : null
}

export function generateStroopTrial(random = Math.random) {
  const wordIndex = Math.floor(random() * STROOP_WORDS.length)
  const shouldMatch = random() < 0.25
  let colorIndex = wordIndex

  if (!shouldMatch) {
    do {
      colorIndex = Math.floor(random() * STROOP_COLOR_KEYS.length)
    } while (colorIndex === wordIndex)
  }

  return {
    word: STROOP_WORDS[wordIndex],
    wordIndex,
    colorKey: STROOP_COLOR_KEYS[colorIndex],
    colorIndex,
    congruent: colorIndex === wordIndex,
  }
}

export function readStroopRecords(raw) {
  const records = parseObject(raw)
  const result = {}
  const accuracy = Number(records.bestAccuracy)
  const avgRT = positiveNumber(records.bestAvgRT)

  if (Number.isFinite(accuracy) && accuracy > 0 && accuracy <= 1) {
    result.bestAccuracy = accuracy
  }
  if (avgRT !== null) {
    result.bestAvgRT = avgRT
  }

  return result
}

export function updateStroopRecords(raw, accuracy, avgRT) {
  const records = readStroopRecords(raw)
  const nextAccuracy = Number(accuracy)
  const nextAvgRT = positiveNumber(avgRT)
  let isNewAccuracy = false
  let isNewRT = false

  if (
    Number.isFinite(nextAccuracy)
    && nextAccuracy > 0
    && nextAccuracy <= 1
    && (!records.bestAccuracy || nextAccuracy > records.bestAccuracy)
  ) {
    records.bestAccuracy = nextAccuracy
    isNewAccuracy = true
  }
  if (
    nextAvgRT !== null
    && (!records.bestAvgRT || nextAvgRT < records.bestAvgRT)
  ) {
    records.bestAvgRT = nextAvgRT
    isNewRT = true
  }

  return { records, isNewAccuracy, isNewRT }
}

export function generateDigits(length, random = Math.random) {
  let result = String(Math.floor(random() * 9) + 1)
  for (let index = 1; index < length; index += 1) {
    result += String(Math.floor(random() * 10))
  }

  if (length >= 2 && new Set(result).size === 1) {
    const last = Number(result.at(-1))
    const replacement = Math.floor(random() * 9)
    result = result.slice(0, -1) + String(replacement >= last ? replacement + 1 : replacement)
  }

  return result
}

export function reverseDigits(value) {
  return String(value).split('').reverse().join('')
}

export function getDigitSpanDisplayDuration(digits) {
  return 2000 + Number(digits) * 700
}

export function getDigitSpanRating(span) {
  if (span >= 9) {
    return { text: '天才级', cls: 'rating-genius', icon: '👑' }
  }
  if (span >= 7) {
    return { text: '优秀', cls: 'rating-great', icon: '🌟' }
  }
  if (span >= 5) {
    return { text: '正常', cls: 'rating-normal', icon: '👍' }
  }
  return { text: '待提升', cls: 'rating-improve', icon: '📚' }
}

export function createDeck() {
  return POKER_SUITS.flatMap((suit) => (
    Array.from({ length: 13 }, (_, index) => ({
      suit,
      rank: index + 1,
    }))
  ))
}

export function shuffle(items, random = Math.random) {
  const result = [...items]
  for (let index = result.length - 1; index > 0; index -= 1) {
    const target = Math.floor(random() * (index + 1))
    ;[result[index], result[target]] = [result[target], result[index]]
  }
  return result
}

export function generatePokerCards(count, random = Math.random) {
  return shuffle(createDeck(), random).slice(0, count)
}

export function selectPokerTarget(cards, random = Math.random) {
  const position = Math.floor(random() * cards.length)
  return { card: cards[position], position }
}

export function evaluatePokerRound(correctInRound, attemptsInRound) {
  if (attemptsInRound < 3) {
    return 'continue'
  }
  return correctInRound >= 2 ? 'pass' : 'fail'
}

export function getPokerMemorySpan(level) {
  return Math.max(0, Number(level) - 1)
}

export function readPokerBest(raw) {
  const value = Number.parseInt(raw, 10)
  return Number.isFinite(value) && value > 0 ? value : 0
}

export function updatePokerBest(raw, span) {
  const previous = readPokerBest(raw)
  const next = Number(span)
  if (Number.isFinite(next) && next > previous) {
    return { best: next, isNew: true }
  }
  return { best: previous, isNew: false }
}

export function generateSchulteNumbers(size, random = Math.random) {
  const total = Number(size) * Number(size)
  return shuffle(Array.from({ length: total }, (_, index) => index + 1), random)
}

export function getSchulteClickResult(number, nextNumber) {
  if (number === nextNumber) {
    return 'advance'
  }
  if (number > nextNumber) {
    return 'wrong'
  }
  return 'ignored'
}

export function readSchulteBest(raw) {
  const records = parseObject(raw)
  return Object.fromEntries(
    Object.entries(records)
      .map(([size, time]) => [size, positiveNumber(time)])
      .filter(([, time]) => time !== null),
  )
}

export function updateSchulteBest(raw, size, time) {
  const best = readSchulteBest(raw)
  const key = String(size)
  const next = positiveNumber(time)
  const previous = best[key] ?? null

  if (next !== null && (previous === null || next < previous)) {
    return {
      best: { ...best, [key]: next },
      isNew: true,
      previous,
    }
  }
  return { best, isNew: false, previous }
}

export function formatGameDuration(milliseconds) {
  if (milliseconds == null || !Number.isFinite(Number(milliseconds))) {
    return '--:--.---'
  }

  const value = Math.max(0, Math.floor(Number(milliseconds)))
  const minutes = Math.floor(value / 60_000)
  const seconds = Math.floor((value % 60_000) / 1000)
  const millis = value % 1000
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(millis).padStart(3, '0')}`
}

export function formatReactionTime(milliseconds) {
  if (milliseconds == null || !Number.isFinite(Number(milliseconds))) {
    return '---'
  }
  return `${Math.round(Number(milliseconds))} ms`
}
