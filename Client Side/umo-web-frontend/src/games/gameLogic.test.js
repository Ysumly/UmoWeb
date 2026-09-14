import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createDeck,
  evaluatePokerRound,
  formatGameDuration,
  formatReactionTime,
  generateDigits,
  generatePokerCards,
  generateSchulteNumbers,
  generateStroopTrial,
  getDigitSpanDisplayDuration,
  getDigitSpanRating,
  getPokerMemorySpan,
  getSchulteClickResult,
  readPokerBest,
  readSchulteBest,
  readStroopRecords,
  reverseDigits,
  selectPokerTarget,
  shuffle,
  updatePokerBest,
  updateSchulteBest,
  updateStroopRecords,
} from './gameLogic.js'

function sequenceRandom(values) {
  let index = 0
  return () => values[index++ % values.length]
}

test('Stroop generates congruent and incongruent trials from the injected random source', () => {
  const congruent = generateStroopTrial(sequenceRandom([0, 0.1]))
  assert.deepEqual(congruent, {
    word: '红',
    wordIndex: 0,
    colorKey: 'red',
    colorIndex: 0,
    congruent: true,
  })

  const incongruent = generateStroopTrial(sequenceRandom([0, 0.9, 0.4]))
  assert.equal(incongruent.word, '红')
  assert.equal(incongruent.colorKey, 'green')
  assert.equal(incongruent.congruent, false)
})

test('Stroop records keep the legacy fields and only accept valid improvements', () => {
  assert.deepEqual(readStroopRecords('{bad json'), {})
  assert.deepEqual(readStroopRecords('{"bestAccuracy":2,"bestAvgRT":-1}'), {})

  const first = updateStroopRecords('{}', 0.8, 620)
  assert.deepEqual(first, {
    records: { bestAccuracy: 0.8, bestAvgRT: 620 },
    isNewAccuracy: true,
    isNewRT: true,
  })

  const second = updateStroopRecords(JSON.stringify(first.records), 0.75, 700)
  assert.deepEqual(second, {
    records: { bestAccuracy: 0.8, bestAvgRT: 620 },
    isNewAccuracy: false,
    isNewRT: false,
  })
})

test('digit span generation avoids leading zero and repeated-only sequences', () => {
  assert.equal(generateDigits(4, sequenceRandom([0, 0.1, 0.1, 0.1, 0])), '1110')
  assert.equal(generateDigits(3, sequenceRandom([0.9, 0.1, 0.2])), '912')
  assert.equal(reverseDigits('1024'), '4201')
})

test('digit span duration and rating preserve the original thresholds', () => {
  assert.equal(getDigitSpanDisplayDuration(4), 4800)
  assert.equal(getDigitSpanDisplayDuration(8), 7600)
  assert.equal(getDigitSpanRating(9).text, '天才级')
  assert.equal(getDigitSpanRating(7).text, '优秀')
  assert.equal(getDigitSpanRating(5).text, '正常')
  assert.equal(getDigitSpanRating(4).text, '待提升')
})

test('poker deck generation and target selection are deterministic with an injected random source', () => {
  const deck = createDeck()
  assert.equal(deck.length, 52)
  assert.equal(new Set(deck.map((card) => `${card.suit.key}-${card.rank}`)).size, 52)

  const cards = generatePokerCards(3, sequenceRandom([0, 0.25, 0.5]))
  assert.equal(cards.length, 3)
  assert.deepEqual(selectPokerTarget(cards, () => 0.5), { card: cards[1], position: 1 })
  assert.deepEqual(shuffle([1, 2, 3], () => 0), [2, 3, 1])
})

test('poker round and span rules match the original game', () => {
  assert.equal(evaluatePokerRound(2, 3), 'pass')
  assert.equal(evaluatePokerRound(1, 3), 'fail')
  assert.equal(evaluatePokerRound(1, 2), 'continue')
  assert.equal(getPokerMemorySpan(5), 4)
  assert.equal(getPokerMemorySpan(1), 0)
})

test('poker best span keeps the legacy integer storage', () => {
  assert.equal(readPokerBest(null), 0)
  assert.equal(readPokerBest('not-a-number'), 0)
  assert.equal(readPokerBest('4'), 4)
  assert.deepEqual(updatePokerBest('4', 3), { best: 4, isNew: false })
  assert.deepEqual(updatePokerBest('4', 6), { best: 6, isNew: true })
})

test('Schulte numbers and click results preserve the original sequence rules', () => {
  assert.deepEqual(generateSchulteNumbers(2, () => 0), [2, 3, 4, 1])
  assert.equal(getSchulteClickResult(1, 1), 'advance')
  assert.equal(getSchulteClickResult(3, 2), 'wrong')
  assert.equal(getSchulteClickResult(1, 2), 'ignored')
})

test('Schulte best times remain a size-keyed legacy object', () => {
  assert.deepEqual(readSchulteBest('{bad json'), {})
  assert.deepEqual(readSchulteBest('{"3":1200,"4":-1}'), { 3: 1200 })
  assert.deepEqual(updateSchulteBest('{"3":1200}', 3, 1300), {
    best: { 3: 1200 },
    isNew: false,
    previous: 1200,
  })
  assert.deepEqual(updateSchulteBest('{"3":1200}', 3, 900), {
    best: { 3: 900 },
    isNew: true,
    previous: 1200,
  })
})

test('game time formatting supports totals and reaction times', () => {
  assert.equal(formatGameDuration(0), '00:00.000')
  assert.equal(formatGameDuration(62_345), '01:02.345')
  assert.equal(formatGameDuration(null), '--:--.---')
  assert.equal(formatReactionTime(619.6), '620 ms')
  assert.equal(formatReactionTime(null), '---')
})
