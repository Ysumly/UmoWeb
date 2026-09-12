# Java 集合框架详解

Java 集合框架把数据组织方式抽象为 `List`、`Set`、`Map` 和 `Queue`。

## 选择集合

- 需要稳定索引访问时选择 `ArrayList`。
- 需要去重且不关心顺序时选择 `HashSet`。
- 需要按键查询时选择 `HashMap`。
- 需要可重复的遍历顺序时选择 `LinkedHashMap`。

```java
Map<String, Integer> counts = new HashMap<>();
counts.merge("java", 1, Integer::sum);
```

集合的复杂度取决于底层结构，不能只根据接口名称判断性能。
