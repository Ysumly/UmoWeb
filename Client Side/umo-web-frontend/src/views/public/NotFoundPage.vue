<template>
  <section class="not-found">
    <div class="not-found__number">404</div>
    <span class="editorial-eyebrow">Page not found</span>
    <h1>这一页没有写进书里。</h1>
    <p>你访问的内容不存在，或者已经从当前卷册中移除。</p>
    <div class="not-found__actions">
      <router-link class="button button--primary" to="/">返回首页</router-link>
      <router-link class="button button--outline" to="/library">进入书库</router-link>
    </div>
  </section>
</template>
