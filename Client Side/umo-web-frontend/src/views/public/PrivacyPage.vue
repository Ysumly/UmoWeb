<script setup>
import { onMounted, ref } from 'vue'

import { loadAccessPrivacy } from '@/utils/accessPrivacy'

const status = ref('loading')
const errorMessage = ref('')
const policy = ref(null)

async function load() {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    policy.value = await loadAccessPrivacy()
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = error instanceof Error
      ? error.message
      : '访问统计配置暂时不可用'
  }
}

onMounted(load)
</script>

<template>
  <div class="privacy-page">
    <header class="about-hero privacy-hero">
      <div class="about-hero__seal">P</div>
      <div>
        <span class="editorial-eyebrow">Privacy / 访问与日志</span>
        <h1>只保留维护安全<br>所必需的信息。</h1>
      </div>
      <p>本站不接入广告画像、跨站追踪或第三方访问统计，只使用自托管链路记录最小的请求元信息。</p>
    </header>

    <div class="privacy-status" aria-live="polite">
      <template v-if="status === 'loading'">
        <span>正在读取当前保留策略…</span>
      </template>
      <template v-else-if="status === 'success'">
        <span>当前原始日志保留 {{ policy.rawRetentionDays }} 天</span>
        <span>匿名聚合保留 {{ policy.aggregateRetentionDays }} 天</span>
      </template>
      <template v-else>
        <span>当前运行配置暂时不可用，本站不会展示未经确认的保留天数。</span>
        <button type="button" @click="load">重新读取</button>
        <small>{{ errorMessage }}</small>
      </template>
    </div>

    <section class="privacy-policy" aria-label="访问日志政策">
      <article>
        <span>01 / PURPOSE</span>
        <h2>收集目的</h2>
        <p>访问日志仅用于站点安全、故障排查和基础运维分析，不用于广告、用户画像、推荐排序或跨站追踪。</p>
      </article>
      <article>
        <span>02 / MINIMUM FIELDS</span>
        <h2>最小字段</h2>
        <p>原始日志严格限制为客户端 IP、ISO 8601 时间、HTTP 方法、不含查询字符串的路径、状态码和响应字节数。</p>
      </article>
      <article>
        <span>03 / NOT RECORDED</span>
        <h2>不记录的内容</h2>
        <p>不记录查询参数、请求体、Cookie、Authorization、Referer 或 User-Agent，也不把凭据写入日志。</p>
      </article>
      <article>
        <span>04 / RETENTION</span>
        <h2>保存与删除</h2>
        <p v-if="status === 'success'">
          原始日志最多保留 {{ policy.rawRetentionDays }} 天并自动轮转删除；长期只保存不含 IP 的每日匿名聚合，
          最多保留 {{ policy.aggregateRetentionDays }} 天。
        </p>
        <p v-else>
          保留策略由服务器端统一配置。当前配置读取失败时，本站不会猜测或展示可能错误的天数。
        </p>
      </article>
      <article>
        <span>05 / ACCESS</span>
        <h2>访问边界</h2>
        <p>包含原始 IP 的日志和短期报表只允许服务器管理员读取；报表服务仅监听回环地址，通过管理员 SSH 隧道访问，不新增公网端口。</p>
      </article>
      <article>
        <span>06 / THIRD PARTIES</span>
        <h2>第三方边界</h2>
        <p>原始访问日志不发送给广告平台或无法控制的数据服务商。未来若改变数据流向，必须先更新说明并重新评估访问权限。</p>
      </article>
    </section>

    <section class="about-closing privacy-closing">
      <span class="editorial-eyebrow">Minimum necessary data</span>
      <h2>统计应服务于安全，<br>而不是反过来塑造读者。</h2>
      <router-link class="button button--outline" to="/about">了解这个空间</router-link>
    </section>
  </div>
</template>
