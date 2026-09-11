export const siteInfo = {
  siteTitle: 'Umo Blog',
  siteSubtitle: '代码 · 阅读 · 创作',
  tagline: '把代码写进时间的纸页',
  introduction: '一个关于技术、阅读与长期创作的个人空间。收录工程笔记、书评与小说章节，也保留缓慢思考留下的真实痕迹。',
  aboutHtml: `## 关于我

我是一个普通但持续的开发者，常年往返于 Java、Spring、Vue 与文字之间。

这里记录三类内容：

- 工程实践与技术笔记
- 读书后的真实感受
- 仍然在生长的长篇小说

比起追逐短期热点，我更关心代码如何被理解，以及一个想法如何在几年后仍然成立。

> 写作不是为了证明知道，而是为了发现还没有想清楚的地方。`,
  projectHtml: `## 长期项目

### UmoWeb

个人博客、内容管理与长期写作空间。当前前端正在完成第一版公开端视觉样片。

| 阶段 | 状态 |
| --- | --- |
| 后端 API | 已完成核心能力 |
| 前端视觉基线 | MVP 进行中 |
| 内容迁移 | 持续整理 |

### XianXiaGame

一个以回合制为核心的仙侠游戏实验，关注叙事节奏、战斗反馈和长期成长。`,
}

export const categories = [
  {
    id: 1,
    name: '编程',
    slug: 'programming',
    type: 'NOTE',
    children: [
      { id: 2, name: 'Java', slug: 'java', type: 'NOTE', children: [] },
      { id: 3, name: 'Spring', slug: 'spring', type: 'NOTE', children: [] },
      { id: 4, name: '数据库', slug: 'database', type: 'NOTE', children: [] },
    ],
  },
  {
    id: 5,
    name: '前端',
    slug: 'frontend',
    type: 'NOTE',
    children: [
      { id: 6, name: 'JavaScript', slug: 'javascript', type: 'NOTE', children: [] },
      { id: 7, name: 'Vue', slug: 'vue', type: 'NOTE', children: [] },
    ],
  },
  {
    id: 8,
    name: '小说',
    slug: 'novels',
    type: 'NOVEL',
    children: [
      { id: 9, name: '凡人修仙传', slug: 'fanren', type: 'NOVEL', children: [] },
    ],
  },
  {
    id: 10,
    name: '读后感',
    slug: 'reviews',
    type: 'BOOK_REVIEW',
    children: [],
  },
]

export const tags = [
  { id: 1, name: 'Java', slug: 'java' },
  { id: 2, name: 'Spring', slug: 'spring' },
  { id: 3, name: 'MySQL', slug: 'mysql' },
  { id: 4, name: 'Vue', slug: 'vue' },
  { id: 5, name: '前端', slug: 'frontend' },
  { id: 6, name: '笔记', slug: 'note' },
  { id: 7, name: '教程', slug: 'tutorial' },
  { id: 8, name: '修仙', slug: 'xiuxian' },
  { id: 9, name: '读后感', slug: 'review' },
]

export const contentTypes = [
  {
    value: '',
    label: '全部',
    zh: '全部篇幅',
    description: '按时间浏览所有公开内容',
  },
  {
    value: 'NOTE',
    label: '笔记',
    zh: '技术笔记',
    description: '工程实践、语言与工具',
  },
  {
    value: 'BOOK_REVIEW',
    label: '书评',
    zh: '读后有感',
    description: '阅读留下的判断与疑问',
  },
  {
    value: 'NOVEL',
    label: '小说',
    zh: '连载章节',
    description: '仍在生长的长篇故事',
  },
]

export const contents = [
  {
    id: 1,
    title: 'Spring Boot 快速上手',
    slug: 'spring-boot-quickstart',
    summary: '从零搭建一个 Spring Boot 项目，涵盖基础配置、依赖注入与 REST API。',
    type: 'NOTE',
    categories: [
      { id: 2, name: 'Java', slug: 'java', type: 'NOTE', children: null },
      { id: 3, name: 'Spring', slug: 'spring', type: 'NOTE', children: null },
    ],
    tags: [
      { id: 1, name: 'Java', slug: 'java' },
      { id: 2, name: 'Spring', slug: 'spring' },
      { id: 7, name: '教程', slug: 'tutorial' },
    ],
    metadata: { readingTime: 10, difficulty: 'beginner' },
    publishedAt: '2026-07-06T10:00:00',
    featured: true,
    body: `## 从最小可运行系统开始

工程学习最容易失控的地方，是一开始就试图理解所有配置。更有效的方法，是先把系统缩小到真正可运行的最小状态。

### 1. 建立应用入口

\`\`\`java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
\`\`\`

### 2. 提供第一个接口

\`\`\`java
@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    @GetMapping
    public List<Article> list() {
        return List.of(new Article("Spring Boot"));
    }
}
\`\`\`

## 配置不是起点

当你能清楚说出每一个依赖存在的原因，再逐步引入数据库、认证和异常处理。配置应该服务于问题，而不是反过来定义问题。

> 先让系统工作，再让它整洁；但不要停在“能工作”。`,
  },
  {
    id: 2,
    title: 'Java 集合框架详解',
    slug: 'java-collections',
    summary: '理解 List、Set、Map 的底层结构、复杂度与真实业务中的选择。',
    type: 'NOTE',
    categories: [{ id: 2, name: 'Java', slug: 'java', type: 'NOTE', children: null }],
    tags: [
      { id: 1, name: 'Java', slug: 'java' },
      { id: 6, name: '笔记', slug: 'note' },
    ],
    metadata: { readingTime: 25, difficulty: 'advanced' },
    publishedAt: '2026-07-03T14:30:00',
    body: `## 为什么需要集合

数组解决的是连续内存与固定长度的问题，而业务数据通常需要动态增长、快速查找和稳定遍历。

### 选择的顺序

1. 先判断访问模式。
2. 再判断是否允许重复。
3. 最后考虑扩容、内存和顺序要求。

### 一个常见误区

\`\`\`java
List<Integer> values = new ArrayList<>();
if (values.contains(id)) {
    // 对未排序 ArrayList，这里是 O(n)
}
\`\`\`

如果这段判断位于高频路径，应优先考虑 Set 或一次批量转换。`,
  },
  {
    id: 3,
    title: 'Vue 3 Composition API 入门',
    slug: 'vue3-composition-api',
    summary: '通过 ref、reactive、computed 与 watch 建立更清晰的组件状态边界。',
    type: 'NOTE',
    categories: [
      { id: 6, name: 'JavaScript', slug: 'javascript', type: 'NOTE', children: null },
      { id: 7, name: 'Vue', slug: 'vue', type: 'NOTE', children: null },
    ],
    tags: [
      { id: 4, name: 'Vue', slug: 'vue' },
      { id: 5, name: '前端', slug: 'frontend' },
    ],
    metadata: { readingTime: 15, difficulty: 'intermediate' },
    publishedAt: '2026-06-29T09:00:00',
    body: `## 状态应该靠近使用它的地方

Composition API 的价值不只是写法变化，而是允许我们围绕业务能力组织状态，而不是围绕选项名称堆放代码。

\`\`\`js
const query = ref('')
const results = computed(() => {
  return articles.filter((item) => item.title.includes(query.value))
})
\`\`\`

当状态之间开始形成稳定的因果关系，composable 就会自然出现。`,
  },
  {
    id: 4,
    title: '读《代码整洁之道》',
    slug: 'clean-code-review',
    summary: '代码质量并非个人审美，而是一种会被团队和时间不断放大的协作成本。',
    type: 'BOOK_REVIEW',
    categories: [{ id: 10, name: '读后感', slug: 'reviews', type: 'BOOK_REVIEW', children: null }],
    tags: [{ id: 9, name: '读后感', slug: 'review' }],
    metadata: { bookTitle: '代码整洁之道', author: 'Robert C. Martin', rating: 5 },
    publishedAt: '2026-06-25T16:00:00',
    body: `## 书中最有价值的部分

这本书最能说服我的地方，不是某种命名风格，而是把“整洁”还原成长期协作中的成本问题。

### 命名是一种压缩

好名字让读者不必先运行代码，就能在脑中建立行为模型。

### 函数需要保持单一叙事

函数不只是短，而是应该让读者沿着一个清晰的动因向下阅读。

> 整洁不是额外装饰，而是在为未来的人减少理解成本。`,
  },
  {
    id: 5,
    title: '第一章 山村少年',
    slug: 'fanren-chapter-001',
    summary: '一个平凡的山村少年，在雨夜里第一次看见山外的光。',
    type: 'NOVEL',
    categories: [
      { id: 8, name: '小说', slug: 'novels', type: 'NOVEL', children: null },
      { id: 9, name: '凡人修仙传', slug: 'fanren', type: 'NOVEL', children: null },
    ],
    tags: [{ id: 8, name: '修仙', slug: 'xiuxian' }],
    metadata: { volume: 1, chapter: 1, wordCount: 3200 },
    publishedAt: '2026-06-26T08:00:00',
    body: `## 第一章 山村少年

雨从后半夜开始下，一直到清晨都没有停。

少年站在屋檐下，看见远处的山脊被一道淡青色的光划开。那光只持续了一瞬，却让他想起三年前离开村子的那个人。

“山外到底有什么？”

没有人回答。只有雨水沿着陈旧的瓦片落下，在青石板上敲出细密的声音。

---

他第一次意识到，自己熟悉的世界也许只是更大世界的一角。`,
  },
  {
    id: 6,
    title: '从索引到查询计划',
    slug: 'mysql-query-plan',
    summary: '一次真实的慢查询排查：从直觉猜测回到执行计划与数据分布。',
    type: 'NOTE',
    categories: [{ id: 4, name: '数据库', slug: 'database', type: 'NOTE', children: null }],
    tags: [
      { id: 3, name: 'MySQL', slug: 'mysql' },
      { id: 6, name: '笔记', slug: 'note' },
    ],
    metadata: { readingTime: 18, difficulty: 'advanced' },
    publishedAt: '2026-07-08T11:20:00',
    body: `## 不要先猜索引

慢查询优化最危险的起点，是看到一个字段出现在 WHERE 中，就直接添加索引。

\`\`\`sql
EXPLAIN ANALYZE
SELECT id, title, published_at
FROM contents
WHERE status = 'PUBLISHED'
ORDER BY published_at DESC
LIMIT 20;
\`\`\`

先观察访问类型、扫描行数和排序来源，再结合真实数据分布判断。索引解决的是访问路径问题，不是所有“慢”的通用答案。`,
  },
  {
    id: 7,
    title: '读《人月神话》',
    slug: 'mythical-man-month-review',
    summary: '软件工程最难的从来不是增加人手，而是维持沟通结构与概念完整性。',
    type: 'BOOK_REVIEW',
    categories: [{ id: 10, name: '读后感', slug: 'reviews', type: 'BOOK_REVIEW', children: null }],
    tags: [{ id: 9, name: '读后感', slug: 'review' }],
    metadata: { bookTitle: '人月神话', author: 'Frederick P. Brooks Jr.', rating: 5 },
    publishedAt: '2026-07-10T19:40:00',
    body: `## 为什么人月不能互换

项目延期时，直觉总是增加人手。但沟通路径会随人数平方增长，新增成员也需要时间进入上下文。

### 概念完整性

一个系统需要统一的设计思想，否则局部看似合理的选择会彼此抵消。

这本书写于几十年前，但它讨论的问题今天仍在每一个协作系统中出现。`,
  },
  {
    id: 8,
    title: '第二章 山外来客',
    slug: 'fanren-chapter-002',
    summary: '青石村来了一个受伤的旅人，也带来了一件不该出现在这里的法器。',
    type: 'NOVEL',
    categories: [
      { id: 8, name: '小说', slug: 'novels', type: 'NOVEL', children: null },
      { id: 9, name: '凡人修仙传', slug: 'fanren', type: 'NOVEL', children: null },
    ],
    tags: [{ id: 8, name: '修仙', slug: 'xiuxian' }],
    metadata: { volume: 1, chapter: 2, wordCount: 2800 },
    publishedAt: '2026-07-12T08:00:00',
    body: `## 第二章 山外来客

旅人是在正午进村的。

他穿着与季节不符的深色长衣，右肩有血迹。村里人只在远处看着，没有人先靠近。

少年注意到，他腰间挂着一枚没有绳结的青色小铃。风吹过时，铃没有响。

但山后的竹林却忽然安静了。`,
  },
]

export const publicContents = contents.filter((content) => content.body)

export const allCategoryOptions = categories.flatMap((category) => [
  { id: category.id, name: category.name, type: category.type },
  ...(category.children || []).map((child) => ({
    id: child.id,
    name: child.name,
    type: child.type,
  })),
])
