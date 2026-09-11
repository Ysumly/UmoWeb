# UmoWeb 依赖管理文档

> **项目坐标**: `com.ysumly:UmoWebBackend:0.0.1-SNAPSHOT`
> **父 POM**: `org.springframework.boot:spring-boot-starter-parent:4.1.0`
> **Java 版本**: 17
> **文档维护**: 与后端 [pom.xml](../Server%20Side/UmoWebBackend/pom.xml) 和前端 [package.json](../Client%20Side/umo-web-frontend/package.json) 同步更新

---

## 目录

- [1. 版本管理策略](#1-版本管理策略)
- [2. 核心依赖清单](#2-核心依赖清单)
- [3. BOM 依赖管理](#3-bom-依赖管理)
- [4. 构建插件](#4-构建插件)
- [5. 依赖范围说明](#5-依赖范围说明)
- [6. 版本兼容性矩阵](#6-版本兼容性矩阵)
- [7. 依赖审计与更新](#7-依赖审计与更新)

---

## 1. 版本管理策略

### 1.1 Maven Properties

在 `pom.xml` 的 `<properties>` 块中集中管理可升级的版本号：

| Property | 当前值 | 说明 |
|----------|--------|------|
| `java.version` | `17` | JDK 编译与运行版本 |
| `spring-ai.version` | `2.0.0-M4` | Spring AI BOM 版本（里程碑） |

### 1.2 版本来源

| 来源 | 管理范围 | 说明 |
|------|---------|------|
| `spring-boot-starter-parent:4.1.0` | Spring Boot 生态、Jackson、Logback 等 | 继承自父 POM，保证兼容性 |
| `spring-ai-bom:2.0.0-M4` | Spring AI 组件 | 通过 `<dependencyManagement>` 导入 |
| **显式声明** | MyBatis Starter、JJWT | 非 Spring 生态组件，需显式指定版本 |

---

## 2. 核心依赖清单

### 2.1 Spring Boot 基础

| GroupId | ArtifactId | 版本 | Scope | 用途 |
|---------|-----------|------|-------|------|
| `org.springframework.boot` | `spring-boot-starter-webmvc` | *(继承)* | compile | RESTful Web 服务，内嵌 Tomcat |
| `org.springframework.boot` | `spring-boot-starter-validation` | *(继承)* | compile | 请求参数校验（Hibernate Validator） |
| `org.springframework.boot` | `spring-boot-starter-test` | *(继承)* | test | 测试框架集（JUnit 5, Mockito, AssertJ） |

> **传递依赖亮点**: `spring-boot-starter-webmvc` 会拉入 `spring-webmvc`、
> `tools.jackson.core:jackson-databind:3.1.4`、`tomcat-embed-core` 等。

### 2.2 持久层 — MyBatis

| GroupId | ArtifactId | 版本 | Scope | 用途 |
|---------|-----------|------|-------|------|
| `org.mybatis.spring.boot` | `mybatis-spring-boot-starter` | **4.0.1** | compile | MyBatis + Spring Boot 自动配置 |
| `org.mybatis.spring.boot` | `mybatis-spring-boot-starter-test` | **4.0.1** | test | MyBatis 测试支持（`@MybatisTest`） |
| `com.mysql` | `mysql-connector-j` | *(继承)* | runtime | MySQL 8.x JDBC 驱动 |

> **版本说明**: 当前项目显式使用 MyBatis Starter 4.0.1。2026-09-10 使用隔离的临时 Maven settings 完成编译和测试，构建通过。

### 2.3 AI 集成 — Spring AI

| GroupId | ArtifactId | 版本 | Scope | 用途 |
|---------|-----------|------|-------|------|
| `org.springframework.ai` | `spring-ai-starter-model-openai` | *(BOM)* | compile | OpenAI 模型接入（当前业务代码尚未调用） |

> **注意**: Spring AI 当前为 `2.0.0-M4` 里程碑版本，API 可能在正式版发布前发生变动。

### 2.4 认证与安全

| GroupId | ArtifactId | 版本 | Scope | 用途 |
|---------|-----------|------|-------|------|
| `io.jsonwebtoken` | `jjwt-api` | **0.12.6** | compile | JWT 创建、解析、验证 API |
| `io.jsonwebtoken` | `jjwt-impl` | **0.12.6** | runtime | JJWT 默认实现 |
| `io.jsonwebtoken` | `jjwt-jackson` | **0.12.6** | runtime | JJWT Jackson 序列化适配 |
| `org.springframework.security` | `spring-security-crypto` | *(继承)* | compile | BCrypt / SCrypt 密码编码 |

> **JJWT 0.12.x**: 相比旧版拆分为 `api` / `impl` / `jackson` 三个模块，`impl` 和 `jackson` 仅在运行时需要。

### 2.5 序列化 — Jackson

| GroupId | ArtifactId | 版本 | Scope | 用途 |
|---------|-----------|------|-------|------|
| `org.springframework.boot` | `spring-boot-starter-jackson` | *(随 webmvc 传递)* | compile | Spring Boot 4 Jackson 自动配置 |
| `tools.jackson.core` | `jackson-databind` | **3.1.4** | compile | 业务 JSON 序列化、`ObjectMapper` Bean |
| `com.fasterxml.jackson.core` | `jackson-databind` | *(继承)* | compile | JJWT Jackson 2 适配兼容 |
| `com.fasterxml.jackson.datatype` | `jackson-datatype-jsr310` | *(继承)* | compile | Jackson 2 JSR-310 兼容依赖 |

Spring Boot 4 自动配置并注入的是 `tools.jackson.databind.ObjectMapper`。业务代码、验证器和测试禁止继续注入
`com.fasterxml.jackson.databind.ObjectMapper`，否则完整 Spring Context 会因为找不到 Bean 而启动失败。
`com.fasterxml` 依赖主要留给 JJWT 的 Jackson 2 模块使用。

### 2.6 开发工具

| GroupId | ArtifactId | 版本 | Scope | 用途 |
|---------|-----------|------|-------|------|
| `org.projectlombok` | `lombok` | *(继承)* | `optional` | 消除样板代码（`@Data`, `@Builder`, `@Slf4j` 等） |

> **`optional` 作用**: 防止 Lombok 作为传递依赖泄露给引用本项目的下游模块。

### 2.7 前端依赖

`Client Side/umo-web-frontend/package.json` 当前声明：

| 包 | 版本范围 | 用途 |
|---|---|---|
| `vue` | `^3.5.38` | 前端框架 |
| `vue-router` | `^5.1.0` | 路由 |
| `pinia` | `^3.0.4` | 状态管理 |
| `axios` | `^1.18.1` | HTTP 请求 |
| `marked` | `^18.0.5` | Markdown 渲染 |
| `highlight.js` | `^11.11.1` | 代码高亮 |
| `vite` | `^8.1.0` | 构建工具 |
| `@vitejs/plugin-vue` | `^6.0.7` | Vite Vue 插件 |
| `tailwindcss` | `^4.3.1` | 样式框架 |
| `@tailwindcss/vite` | `^4.3.1` | Tailwind Vite 插件 |

前端没有安装 CodeMirror、Monaco、测试框架或 E2E 框架。

---

## 3. BOM 依赖管理

项目通过 `<dependencyManagement>` 引入 Spring AI BOM，实现其组件版本的集中管控：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>  <!-- 2.0.0-M4 -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**效果**: `spring-ai-starter-model-openai` 及其所有传递依赖的版本均由 BOM 统一指定，避免版本冲突。

---

## 4. 构建插件

| GroupId | ArtifactId | 用途 | 关键配置 |
|---------|-----------|------|---------|
| `org.springframework.boot` | `spring-boot-maven-plugin` | 打包可执行 Fat JAR，提供 `spring-boot:run` | 排除 Lombok 避免打包 |
| `org.apache.maven.plugins` | `maven-compiler-plugin` | 编译期注解处理 | 配置 `lombok` 为注解处理器路径 |

### 4.1 Lombok 注解处理器配置

```xml
<!-- compile 阶段 -->
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </path>
</annotationProcessorPaths>
```

该配置确保 Maven 编译时启用 Lombok 注解处理，生成对应的 getter / setter / builder 等方法。

---

## 5. 依赖范围说明

| Scope | 含义 | 本项目中的实例 |
|-------|------|-------------|
| **compile** | 编译、运行、打包均可用（默认） | `spring-boot-starter-webmvc`, `jjwt-api`, `mybatis-spring-boot-starter` |
| **runtime** | 编译不需要，运行和打包需要 | `mysql-connector-j`, `jjwt-impl`, `jjwt-jackson` |
| **optional** | 默认不传递到下游项目 | `lombok` |
| **test** | 仅测试编译和运行 | `spring-boot-starter-test`, `mybatis-spring-boot-starter-test` |
| **import** | BOM 导入，仅用于 `<dependencyManagement>` | `spring-ai-bom` |

---

## 6. 版本兼容性矩阵

| 组件 | 当前版本 | 依赖的 Java 最低版本 | 备注 |
|------|---------|-------------------|------|
| Spring Boot | 4.1.0 | Java 17 | 2025 年发布，Spring Framework 7.x 基线 |
| MyBatis Spring Boot Starter | 4.0.1 | Java 17 | 当前项目已声明版本 |
| JJWT | 0.12.6 | Java 8 | 纯 Java 实现，无额外系统依赖 |
| Spring AI | 2.0.0-M4 | Java 17 | 里程碑版本，谨慎用于生产 |
| MySQL Connector/J | (继承) | Java 17 | 由 Spring Boot 管理，支持 MySQL 8.0+ |
| Lombok | (继承) | Java 8 | 需 IDE 插件配合 |

---

## 7. 依赖审计与更新

### 7.1 日常检查命令

```bash
# 查看全部依赖树
mvn dependency:tree

# 检查可用的版本更新
mvn versions:display-dependency-updates

# 检查插件更新
mvn versions:display-plugin-updates
```

### 7.2 重点关注项

| 关注点 | 原因 | 检查频率 |
|--------|------|---------|
| `spring-ai.version` (2.0.0-M4) | 里程碑版本，关注正式版发布 | 每月 |
| `jjwt` (0.12.6) | 关注安全漏洞公告 | 每季度 |
| `mysql-connector-j` | 随 Spring Boot 升级自动更新 | 每次 Boot 升级 |
| Spring Boot (4.1.0) | 主版本升级需全面回归测试 | 每半年评估 |

### 7.3 安全漏洞扫描

```bash
# OWASP 依赖检查（需先安装插件）
mvn org.owasp:dependency-check-maven:check
```

---

*最后更新: 2026-09-10 · 对应当前 pom.xml 和 package.json*
