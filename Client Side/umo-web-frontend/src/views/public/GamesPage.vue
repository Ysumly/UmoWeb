<script setup>
import { gameCatalog } from '@/config/games'
</script>

<template>
  <div class="games-index">
    <header class="games-index__heading">
      <span class="editorial-eyebrow">Brain Training</span>
      <h1>脑力训练馆</h1>
      <p>四种短时认知训练，成绩只保存在当前浏览器。</p>
    </header>

    <div class="games-index__grid">
      <router-link
        v-for="(game, index) in gameCatalog"
        :key="game.id"
        class="game-card"
        :to="game.path"
        :style="{ '--game-index': index }"
      >
        <span class="game-card__mark" aria-hidden="true">{{ game.mark }}</span>
        <div>
          <small>{{ game.english }}</small>
          <h2>{{ game.title }}</h2>
          <p>{{ game.description }}</p>
        </div>
        <span class="game-card__enter">开始训练</span>
      </router-link>
    </div>
  </div>
</template>
