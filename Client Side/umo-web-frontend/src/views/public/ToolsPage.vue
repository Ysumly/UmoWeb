<script setup>
import { toolCatalog } from '@/config/tools'
</script>

<template>
  <div class="tools-index">
    <header class="tools-index__heading">
      <span class="editorial-eyebrow">Local Tools</span>
      <h1>本地工具台</h1>
      <p>无需登录，内容与草稿只保存在当前浏览器。</p>
    </header>

    <div class="tools-index__grid">
      <router-link
        v-for="(tool, index) in toolCatalog"
        :key="tool.id"
        class="tool-card"
        :to="tool.path"
        :style="{ '--tool-index': index }"
      >
        <span class="tool-card__mark" aria-hidden="true">{{ tool.mark }}</span>
        <div>
          <small>{{ tool.english }}</small>
          <h2>{{ tool.title }}</h2>
          <p>{{ tool.description }}</p>
        </div>
        <span class="tool-card__enter">打开工具</span>
      </router-link>
    </div>
  </div>
</template>
