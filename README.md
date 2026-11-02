# 阅读 · 安卓客户端

[reader-rust](../reader-rust) 的原生 Android 客户端。后端是一个自托管的单用户阅读服务
（书源规则解析、正文池与版本管理、缓存、WebDAV 备份），本仓库是它的手机端外壳。

功能范围以 [reader-rust 的 Vue 前端](../reader-rust/frontend) 为基准逐一对齐，
不自行增删产品能力。

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 / 构建 | Kotlin 2.4.21、AGP 9.5.0、Gradle 9.6 |
| UI | Jetpack Compose 1.12.1 + Material 3 1.5.0-alpha22 |
| 架构 | KMP 双模块（`shared` + `androidApp`）、MVVM + `StateFlow` |
| 网络 | OkHttp 5.5.1（含 `okhttp-sse`）、kotlinx.serialization |
| 图片 | Coil 3.6.3（复用会话 HTTP 客户端） |
| SDK | compileSdk 37 / minSdk 28 |

## 当前进度

**已实现**（AVD 实测通过）：

- 登录：服务器地址（支持子路径部署）+ 用户名 + 密码，地址持久化
- 会话：HttpOnly Cookie 持久化，401 自动续期并重放，杀进程不掉登录态
- 书架：列表（默认）/ 网格双视图（选择持久化）、封面、分组筛选（位域语义）、
  关键词过滤、阅读进度
- 发现：书源切换、分类筛选、结果网格无限滚动、加入书架（已入架置灰）
- 阅读器：目录弹层、正文分段渲染、上一章/下一章、进度上报

**规划中**：搜索、最近、书架管理、5 种翻页模式、阅读设置、书签划线、
书内搜索、换源、缓存与离线、章评段评、TTS 听书、正文编辑与版本对比、
书源管理、WebDAV 备份、AI 图书、设置。

完整的优先级清单与验收标准见仓库根的 `todo.md`（本地文件，不进版本库）。

## 构建

```bash
./gradlew :androidApp:assembleDebug
```

产物：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

单元测试：

```bash
./gradlew :shared:testAndroidHostTest
```

## 连接后端

首次启动在登录页填写**接口基址，要填到 `/reader3` 为止**：

| 场景 | 地址示例 |
|---|---|
| 部署在根路径 | `https://example.org/reader3` |
| 子路径部署 | `https://example.org/read/reader3` |
| 连接本机调试实例 | `http://127.0.0.1:8080/reader3` |

地址必须与后端的 `COOKIE_PATH` 前缀一致，否则会出现"登录成功但后续接口全 401"
——会话 Cookie 的 Path 与请求路径不匹配时不会被回传。

## 目录结构

```
shared/src/commonMain/kotlin/org/example/reader/
  core/model/     数据模型（@Serializable DTO，对齐后端结构体）
  core/net/       接口契约、ReaderApi、结果类型
  core/session/   服务地址与登录态
  core/settings/  键值配置抽象
  ui/             主题、导航、各页面（login/shelf/reader/…）
shared/src/androidMain/kotlin/org/example/reader/
  core/           OkHttp 实现、Cookie 持久化、SharedPreferences、Coil 装配
androidApp/       Activity、清单、图标与资源
```

`commonMain` 不引用 Android API；平台能力在 `commonMain` 只留接口，
实现放 `androidMain`。这样业务代码可测试，也给未来其他目标平台留了位置。

## 相关仓库

- [reader-rust](../reader-rust)：Rust 后端 + Vue 前端（功能基准）
- `AGENTS.md`：本仓库的工程约定与已知陷阱，改代码前先读
