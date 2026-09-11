<script setup>
import MarkdownArticle from '@/components/public/MarkdownArticle.vue'
import { siteInfo } from '@/demo/content'
</script>

<template>
  <div class="about-page">
    <header class="about-hero">
      <div class="about-hero__seal">U</div>
      <div>
        <span class="editorial-eyebrow">About / 关于这个空间</span>
        <h1>一个人如何留下<br>长期思考的痕迹。</h1>
      </div>
      <p>{{ siteInfo.introduction }}</p>
    </header>

    <div class="about-layout">
      <aside class="about-facts">
        <span>EDITION 01</span>
        <dl>
          <div><dt>地点</dt><dd>中国 · 上海</dd></div>
          <div><dt>方向</dt><dd>Java / Spring / Vue</dd></div>
          <div><dt>正在写</dt><dd>技术笔记与长篇故事</dd></div>
        </dl>
      </aside>
      <div class="about-content">
        <MarkdownArticle :source="siteInfo.aboutHtml" />
      </div>
    </div>

    <section class="about-closing">
      <span class="editorial-eyebrow">Continue reading</span>
      <h2>如果这里有一句话对你有用，<br>它就已经完成了自己的任务。</h2>
      <router-link class="button button--primary" to="/library">去书库看看</router-link>
    </section>
  </div>
</template>
