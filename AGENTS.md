# AGENTS.md

面向 AI 编码代理与贡献者的工程约定。**改代码前先读「依赖约束」与「已知陷阱」两节**，
里面的每条都是实际踩过的，违反会浪费整轮调试。

## 项目定位

- 本仓库是 `reader-rust` 的原生 Android 客户端；后端（Rust）与 Web 前端（Vue）都在
  同级目录 `../reader-rust`。
- **功能基准是 `../reader-rust/frontend`**。产品能力以它为准，不自行增删；
  改功能前先看对应 Vue 组件在做什么，而不是凭"阅读 App 该有什么"推断。
- 后端是单用户、自托管服务：没有注册、没有多租户、没有权限角色。
- 界面语言为简体中文。

## 常用命令

```bash
./gradlew :androidApp:assembleDebug        # 构建 APK
./gradlew :shared:testAndroidHostTest      # 单元测试（host JVM）
./gradlew :androidApp:installDebug         # 装到已连接设备
```

## 代码结构

```
shared/src/commonMain/kotlin/org/example/reader/
  core/model/      @Serializable DTO，字段对齐后端结构体
  core/net/        HttpClient 接口、ReaderApi、ApiResult
  core/session/    ServerConfig 与 AuthState
  core/settings/   KeyValueStore 抽象 + SettingsKeys + LayoutPreference（列表/网格全局偏好）
  core/recent/     最近阅读本地镜像（JSON 落在 KeyValueStore）
  core/util/       expect/actual 的平台能力（时间戳等）
  ui/theme/        Material 3 色板/字体/阅读器主题预设
  ui/nav/          主框架（底部导航 + 阅读器 / 详情 / 目录覆盖层）
  ui/<feature>/    每个功能一个包：Screen + ViewModel
  ui/components/   跨页面复用的状态视图等
shared/src/androidMain/kotlin/org/example/reader/
  core/            AppDependencies 装配、OkHttp、Cookie 持久化、SharedPreferences、Coil
androidApp/        Activity、清单、图标、资源
```

**依赖方向**：`commonMain` 不得引用 Android API。需要平台能力时，在 `commonMain`
定义接口（如 `HttpClient`、`KeyValueStore`），实现放 `androidMain`，
在 `AndroidDependencies` 里装配。

**新增页面**：`ui/<feature>/XxxScreen.kt` + `XxxViewModel.kt`，ViewModel 通过
`viewModel { XxxViewModel(deps) }` 注入 `AppDependencies`，不要用全局单例。

## 依赖约束

- **Compose 与 Material3 必须成套**：`androidx-compose = 1.12.1` +
  `androidx-compose-material3 = 1.5.0-alpha22`。旧版 Material3 依赖的 Compose
  内部 API 在 1.9+ 已被移除，混用不会有编译错误，但运行时抛 `NoSuchMethodError`。
  升级时两者一起升，先查 Material3 的 POM 依赖哪个 Compose 版本。
- **material-icons 系列只到 1.7.8**（官方最后一版，之后停发）。核心集
  `material-icons-core` 不含 `Explore` / `History` / `Visibility` 等常用图标，
  需要时换图标或改用 `material-icons-extended`（体积较大）。
- 新增依赖统一写进 `gradle/libs.versions.toml`，不要在子模块里写死版本号。
- 未使用 Compose Multiplatform 的 `org.jetbrains.compose`，而是直接依赖 androidx
  产物：两者在 Android 上是同一套类，直接用 androidx 版本更可控。

## 网络与鉴权

- 响应统一是 `{isSuccess, errorMsg, data}`。注意实际取值：成功时 `errorMsg` 是
  **空字符串**而不是 null；失败时 `data` 通常为 null。解包在 `ApiResult.kt`。
- **401 由 OkHttp 自带的 `Authenticator` 处理**：静默换令牌后自动重放，
  同一请求最多重放一次，`/login` 与 `/refreshToken` 的 401 绝不触发刷新。
  业务层不要再包一层重试，否则会与它叠加成多次请求。
- **请求体一律用 `@Serializable` DTO + kotlinx.serialization 编码**，不要手拼
  JSON 字符串（书名/搜索词里的引号反斜杠会漏转义）。
- **字段名必须对照后端结构体**，不能凭直觉命名。已踩过的两个：
  - `saveBookProgress` → `index` / `position`（不是 `chapterIndex` / `chapterPos`）
  - `setBookSource` → `targetBookId`（候选搜索结果的 bookId，不是 `bookSourceId`）
- **响应体必须在 OkHttp 回调线程读完再 resume**（见「已知陷阱」）。
- 会话是两枚 HttpOnly Cookie（`reader_access` / `reader_refresh`），由
  `SharedPrefsCookieJar` 持久化；登录响应 JSON 里的 `accessToken` 不落盘。
- 服务地址由用户在登录页填写，**要填到 `/reader3` 为止**，且必须与后端
  `COOKIE_PATH` 前缀一致；子路径部署时形如 `https://example.org/read/reader3`。
- **本地书导入**：两个端点 `/uploadTxtBook`、`/uploadEpubBook`，multipart 字段名都是
  `file`，按文件扩展名选择端点；必须用 `HttpRequest.UPLOAD_TIMEOUT_MILLIS`
  （默认 120s 不够传几十 MB）。文件选择在 `rememberBookFilePicker`（expect/actual）。
- **SSE 端点**（`/searchBookMultiSSE` 等）的帧约定：默认帧是业务 JSON
  （搜索是 `{lastIndex, data:[SearchBook]}`），命名 `error` 帧是 `{errorMsg}`，
  命名 `end` 帧收尾。`error` 帧与传输层失败都表现为 error 事件，必须区分：
  前者要展示 `errorMsg`，后者才是「连接中断」。见 `SearchViewModel`。

## 数据模型要点

这几条语义搞错会导致数据错乱，且界面上看不出来：

- **书籍身份 `bookId`**（形如 `book_<n>`）：稳定，不随换源变化，所有请求都用它。
- **章节身份 `chapterUrl`**：换源后会变。按 `chapterUrl` 存的东西换源即失效。
- **分组是位域**：`book.group` 是 bitmask，一本书可同时属于多个分组，
  `group and groupId != 0` 为命中；`0` 表示未分组。
- **阅读进度是万分比**：`durChapterPos` / `position` 取值 0–10000，不是 0–1 也不是百分比。
- **时间戳单位不统一**：后端部分字段是毫秒、部分是秒，用之前先确认来源接口。
- **`COOKIE_SECURE=auto`** 时，非回环 Host 下发的会话 Cookie 会带 `Secure`，
  明文 HTTP 下客户端不会回传。调试用回环地址可规避。
- **分组排序是整表覆盖**：`/saveBookGroupOrder` 不在数组里的分组会被**删除**
  （并清掉书籍上的对应位），所以必须提交完整分组列表，`orderNo` 按数组下标重写。
- **分组批量语义**：`/saveBookGroupId` 是**单本**「设置分组」（`groupId=0` 清空）；
  `/addBookGroupMulti` / `removeBookGroupMulti` 是位域 OR/AND-NOT，不是替换。
  「批量移动」要逐本调 `saveBookGroupId`，否则会变成「加组」而不是「移到该组」。
- **最近阅读是本地镜像**：`RecentBooksStore` 存「整本 Book + `recentReadAt`」，
  按 `bookId` 去重、时间倒序、上限 100；阅读器在**章节内容成功后**写入。
  跨端同步（后端 userdata 文档）还没做，不要假设换设备能看到。

## UI 约定

- 严格 Material 3：用 `Scaffold` / `TopAppBar` / `NavigationBar` /
  `ModalBottomSheet` / `FilterChip` / `ListItem` 等标准组件，不自造样式。
- 品牌色在 `ui/theme/ReaderTokens.kt`：由应用图标的粉色（`#CF497C`）派生 M3 色板，
  浅色主题的 `primary` 比图标粉深一档以满足对比度要求。
- **阅读器正文主题独立于 M3**：8 个纸张预设 + 夜间，定义在
  `ReaderThemePresets`，与 Web 端 `utils/readerTheme.ts` 一一对应。
  不要把它合并进 Material 主题。
- 加载/空/错误三态统一用 `ui/components/StateViews.kt`，不要各页面自己写一套。
- 列表项点击区域不小于 48dp；中文正文行高要比 Material 默认值略大。
- 底部导航 5 项：书架 / 发现 / 搜索 / 最近 / 我的。搜索是独立入口，
  不要塞回书架的本地过滤里（书架的放大镜只过滤当前书架）。
- 书籍卡片与列表行是共享组件：`BookListRow`（书架、最近）、
  `BookResultGrid` + `BookResultCard`（发现、搜索）。加新列表页先复用，
  不要在页面里复制一份布局。
- **详情与目录都是独立页面**，不是弹层/底部面板。详情只放书籍元信息；
  目录在 `ui/catalog/`，详情与阅读器共用，靠 `onSelectChapter(原始下标)` 回传。
- 书架交互：点击阅读、长按进详情；多选走顶栏「更多 → 选择书籍」，
  不要在长按上再叠多选手势。
- 书架排序是独立模式：入口在「更多 → 排序书籍」，进入后清空分组/关键词筛选
  （排序作用于整架，筛选会让下标错位），右侧三线手柄拖动实时换位，顶栏「保存」
  用 `saveBooks` 整架提交。仅列表模式提供。
- 列表/网格是全局偏好（`deps.layout`），发现与搜索结果必须跟随，
  不要各自维护一份视图状态；切换入口在书架顶栏「更多」菜单里，不占顶栏按钮。
- 多选角标用 `SelectionBadge` 压在封面左上角（半透明底 + 白描边），
  不要再用行首 Checkbox——它会挤压列表宽度。
- **所有覆盖层走 `MainShell` 的 Overlay 栈 + `AnimatedContent`**：阅读器、详情、目录
  都是栈上一帧，入栈右滑入、出栈右滑出；新增覆盖页要接 `onBack = popOverlay`，
  并在需要拦截返回的页面用 `AppBackHandler`（commonMain expect/actual）。
  不接的后果是系统返回直接退到桌面。

## 已知陷阱

**1. OkHttp + 协程的主线程 I/O**

不能先 `resume` 再读 `body.bytes()`：`enqueue` 的回调只保证响应头到达，body 仍是
网络流；协程恢复到主线程后再读会抛 `NetworkOnMainThreadException`。
小响应因为已被完整缓冲所以看不出来，响应一大（比如上千章的目录）必崩，
而且该异常的 `message` 是 `null`，不打异常类名根本定位不到。
正确做法见 `CallAwait.kt` 的 `awaitBuffered`：在 `onResponse` 里读完再 resume。

**2. Cookie 持久化会丢域名**

`Cookie.toString()` 对 hostOnly Cookie 不输出 `domain=`，只存这个串，回读时
`Cookie.parse` 会退化成基准 URL 的域名，Cookie 就绑错主机、请求不再携带。
表现为"杀进程后掉登录态"。落盘格式必须把 `cookie.domain` 单独存一列，
回读时用它构造基准 URL。

**3. 图片请求不走会话客户端就全是 401**

`/reader3/image/<id>` 需要会话 Cookie。Coil 默认用自己的 OkHttpClient，
必须通过 `OkHttpNetworkFetcherFactory(callFactory = { http.callFactory })`
复用同一个实例，并使用 `deps.imageLoader`。

**4. 块注释里不能直接写路径通配**

Kotlin 的块注释可嵌套，KDoc 里写 `/reader3/*` 会开启一个未闭合的嵌套注释，
报错位置在文件末尾的 `Unclosed comment`，与真正的笔误位置相差很远。

**5. 分类错判**

网络失败先确认请求到底有没有到达后端（看后端日志），再看客户端状态码；
`403` 不一定来自业务服务，也可能是链路上的代理/网关按白名单拒绝。

**6. 系统返回直接退桌面**

Compose 不会自动把系统返回映射到页面栈。每个覆盖层必须：
栈式页面在 `MainShell` 统一 `AppBackHandler(enabled = overlays.isNotEmpty())`；
页面内部状态（书架多选、搜索输入等）各自再挂一个更靠近的 `AppBackHandler`。
早期的表现是「阅读器里按返回直接退出应用」。

**7. `saveBookGroup` 只回 `"success"`，不回分组实体**

新建分组后需要 id（比如「建完直接移动过去」）时，必须回查
`/getBookGroups` 按名字找。把它声明成 `ApiResult<BookGroup>` 会解码失败，
表现为「服务端已建组、客户端报错且列表不刷新」。

**8. `encodeDefaults = true` 会把默认值写进请求体**

`ReaderJson` 开启了 `encodeDefaults`（显式 null 反而被裁掉）。直接序列化完整
`Book` 去调 `/saveBook`，会带上 `durChapterIndex: 0`、`group: 0`，
把已在架书籍的阅读进度覆盖成 0。写请求体要用最小字段 DTO（`SaveBookRequest`），
不要复用响应模型。

## 不做的事

这些是明确的产品决策，不要当遗漏去"补"：

| 项 | 理由 |
|---|---|
| PWA 安装、Service Worker、更新提示 | 本身就是原生 App |
| 桌面键盘快捷键 | 无物理键盘；可选做音量键翻页替代 |
| 顶部导航栏、汉堡菜单、响应式断点 | Android 用 `NavigationBar` 与原生断点 |
| 阅读器内书架侧栏 | 移动端横向空间有限，换书走「返回书架 → 选书」 |
| 桌面双页翻页（<960dp） | Web 端在窄屏同样禁用 |

## 文档地图

| 文档 | 读者 | 内容 |
|---|---|---|
| `README.md` | 使用者 / 新贡献者 | 项目定位、技术栈、构建方式、连接后端、目录结构 |
| `AGENTS.md`（本文件） | AI 代理 / 贡献者 | 工程约定、依赖约束、数据语义、已知陷阱 |
| `todo.md`（本地，不进 git） | 维护者 | 对齐 Vue 前端的功能清单与进度 |

接口契约看 `../reader-rust/docs/api/`；后端行为与部署细节看 `../reader-rust/AGENTS.md`。
