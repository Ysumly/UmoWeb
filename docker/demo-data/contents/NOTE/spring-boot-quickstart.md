# Spring Boot 快速上手

Spring Boot 通过自动配置和起步依赖，让一个可运行的 Web 服务保持足够小。

## 创建接口

```java
@RestController
@RequestMapping("/api/greetings")
class GreetingController {

    @GetMapping
    String greeting() {
        return "Hello, UmoWeb";
    }
}
```

## 常用配置

| 配置 | 用途 |
|---|---|
| `server.port` | HTTP 端口 |
| `spring.datasource.url` | 数据库连接 |
| `spring.profiles.active` | 运行环境 |

> 先用最小配置跑通主路径，再按实际需要增加组件。
