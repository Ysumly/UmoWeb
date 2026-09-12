# Vue 3 Composition API 入门

Composition API 把相关状态和逻辑放在同一个组合函数中，便于复用了测试。

## 响应式状态

```js
import { computed, ref } from 'vue'

const count = ref(0)
const doubled = computed(() => count.value * 2)
```

`ref` 适合保存单个值，`reactive` 适合组织对象状态。组合函数应以 `use` 开头，并返回调用方需要的状态和操作。

> 页面状态、请求状态和派生状态应保持明确的边界。
