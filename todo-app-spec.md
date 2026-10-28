# SPEC: Todo Android App

> 本文档是给 coding agent 执行的实施规格。约定：MUST/MUST NOT/SHOULD 为强约束等级；所有标识符、路径、类名使用英文原文；任务带 ID 便于引用；每个任务有验收标准（AC）。

## 0. 已确认决策（不可更改，除非用户明确变更）

| ID | 决策 |
|---|---|
| D1 | 平台：仅 Android，原生 Kotlin + Jetpack Compose |
| D2 | AI 接入：**用户自填 API Key（BYOK）**，App 直连模型 API，**无自建后端** |
| D3 | **不上架 Google Play**，通过 APK 直接分发（侧载）。因此可直接声明 `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`，无需考虑 Play 政策审核 |
| D4 | 质量优先：首先保证功能完整正确、运行稳定可靠、交互与性能表现优秀；冷启动 < 500ms（中端机）。在此前提下优化 release APK 体积和空闲内存占用，轻量化仅为加分项，APK 体积不设硬性上限，不得为缩小体积或降低资源占用牺牲功能、可靠性或体验 |
| D5 | 离线优先：所有基础功能不依赖网络；AI 失败不影响手动添加 |

## 1. 目标与非目标

**目标**
- G1 添加待办（标题、备注、时间、提醒、地点）
- G2 到点可靠提醒
- G3 首页展示当天待办
- G4 粘贴/分享工作通知 → 调用大模型提取事项 → 预览确认 → 批量创建待办
- G5 UI 简洁美观，交互动效参考 Gmail（容器转场、预测性返回、滑动手势）

**非目标（MUST NOT 实现）**
- 账号体系、云同步、协作、项目/标签/看板、后端服务、统计分析 SDK、广告、第三方推送 SDK、Firebase/GMS 依赖

## 2. 技术栈与约束

| 项 | 值 |
|---|---|
| 语言 | Kotlin（最新稳定版） |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose（需支持 Predictive Back 与共享元素转场） |
| 架构 | 单模块，MVVM + 单向数据流（`StateFlow`） |
| DB | Room（SQLite） |
| 设置存储 | Jetpack DataStore (Preferences) |
| 网络 | OkHttp + kotlinx.serialization（MUST NOT 引入 Retrofit/Gson/Moshi） |
| DI | 手动构造注入（`AppContainer`），MUST NOT 使用 Hilt |
| 提醒 | 默认 `AlarmManager.setExactAndAllowWhileIdle`；增加用户可选的 `setAlarmClock` 系统闹钟提醒模式（2026-10-27 真机对照后用户确认，见 `docs/decisions.md`） |
| 密钥存储 | Android Keystore（AES-GCM）加密后存入 DataStore；MUST NOT 明文存储，MUST NOT 写入日志 |
| minSdk / targetSdk | 26 / 最新稳定 API |
| 构建 | R8 minify + shrinkResources 开启；包含 Baseline Profile |
| 字体 | 按用户后续确认，内置 Source Serif 4 / Source Sans 3 与思源宋体（SIL OFL 1.1）；保留许可证与来源，不使用 Claude 专有字体 |
| 图标 | 采用用户选定的 I01 极简细线风格；优先用内联 `ImageVector` 保持统一线宽与轮廓，`material-icons-core` 仅在风格一致时复用；MUST NOT 引入 `material-icons-extended` |
| 依赖原则 | 每新增依赖须说明必要性；优先官方库；在既定技术栈内，依赖取舍优先保证功能、稳定性和体验，不以压缩 APK 体积为首要依据 |

包名：`com.example.todo`（可改）。

## 3. 目录结构

```
app/src/main/java/com/example/todo/
├─ App.kt                       // Application, 创建 AppContainer, 创建通知渠道
├─ AppContainer.kt              // 手动 DI
├─ MainActivity.kt              // 单 Activity, 承载 NavHost, 处理 ACTION_SEND
├─ data/
│  ├─ local/
│  │  ├─ TodoDatabase.kt
│  │  ├─ TodoEntity.kt
│  │  ├─ ReminderEntity.kt
│  │  ├─ TodoDao.kt
│  │  └─ ReminderDao.kt
│  ├─ remote/
│  │  ├─ LlmClient.kt           // interface + OpenAiCompatClient + AnthropicClient
│  │  ├─ LlmDtos.kt
│  │  └─ PromptBuilder.kt
│  ├─ settings/
│  │  ├─ SettingsStore.kt       // DataStore 封装
│  │  └─ SecretStore.kt         // Keystore 加解密 API Key
│  └─ repo/
│     ├─ TodoRepository.kt
│     └─ NoticeParseRepository.kt
├─ domain/
│  ├─ RepeatRule.kt             // 重复规则解析与下次时间计算
│  ├─ DateTimeUtil.kt
│  └─ LocalNoticeFallback.kt    // 无 AI 时的本地正则兜底
├─ reminder/
│  ├─ AlarmScheduler.kt
│  ├─ ReminderReceiver.kt
│  ├─ ActionReceiver.kt         // 通知按钮: 完成/稍后提醒
│  ├─ BootReceiver.kt           // BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED
│  ├─ NotificationHelper.kt
│  └─ RescheduleAll.kt
├─ ui/
│  ├─ theme/                    // Color.kt Type.kt Theme.kt Motion.kt
│  ├─ nav/                      // Routes.kt TodoNavHost.kt
│  ├─ today/                    // TodayScreen.kt TodayViewModel.kt
│  ├─ add/                      // QuickAddSheet.kt AddViewModel.kt
│  ├─ ai/                       // AiImportScreen.kt AiImportViewModel.kt
│  ├─ detail/                   // DetailScreen.kt
│  ├─ settings/                 // SettingsScreen.kt AiSettingsScreen.kt ReminderCheckScreen.kt
│  └─ components/               // TodoItem.kt DateTimeChips.kt Skeleton.kt SwipeBackground.kt
```

## 4. 数据模型

### 4.1 `TodoEntity`（表 `todo`）

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| id | Long | PK autoGenerate |
| title | String | NOT NULL，长度 1–200 |
| note | String? | |
| dueAt | Long? | UTC epoch millis；allDay=true 时存当天 00:00（设备时区）对应的 millis |
| allDay | Boolean | 默认 false |
| location | String? | 纯文本 |
| repeatRule | String? | 见 4.3 |
| status | Int | 0=TODO, 1=DONE |
| completedAt | Long? | |
| source | Int | 0=MANUAL, 1=AI |
| sourceText | String? | AI 导入原文，仅当用户设置开启“保存原文”时写入 |
| createdAt | Long | |
| updatedAt | Long | |

索引：`dueAt`、`status`。

### 4.2 `ReminderEntity`（表 `reminder`）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | PK autoGenerate |
| todoId | Long | FK → todo.id, `onDelete = CASCADE`，建索引 |
| triggerAt | Long | UTC epoch millis |
| fired | Boolean | 默认 false |

一个 todo 可有多个 reminder（MVP UI 只暴露一个，数据层 MUST 支持多个）。

### 4.3 重复规则 `repeatRule` 字符串格式

- `null`：不重复
- `DAILY`
- `WEEKLY:<d1,d2,...>`，d 为 1–7（1=周一…7=周日）
- `MONTHLY`（按 dueAt 的日期；该月无此日则取当月最后一天）

完成重复待办时：将当前实例标记完成，并创建下一个实例（或直接把 dueAt 推进到下一次并保持 TODO，二选一；**采用“推进 dueAt 并重建 reminder”方案**，历史完成记录不保留，以保持轻量）。

## 5. 功能规格

### 5.1 首页（今日页）

- 布局采用用户选定的 **U03「周历与日程」**：上部为七日周历，显示星期、日期及事项标记，选中日期用克制的轮廓强调；下部为所选日期的日程清单。默认选中今天，点击其他日期可查看该日日程，并提供回到今天的操作。
- 保留“全部待办”入口，能够查找和编辑未来日期的事项；周历不替代该入口。
- 顶部：日期（如“10月26日 周一”）+ 进度（“今天 3/7”）+ 右上角设置入口
- 默认选中今天时的列表分区（按顺序）：
  1. **已逾期**：`status=TODO AND dueAt < 今天 00:00`（含 allDay 的昨天及以前）
  2. **今天**：`status=TODO AND dueAt` 在今天范围内，按时间升序，全天事项排在最前
  3. **无时间**：`status=TODO AND dueAt IS NULL`
  4. **已完成（今天）**：折叠，`status=DONE AND completedAt` 在今天
- 分区为空则不显示该分区；全部为空显示空状态插画（纯 Compose 绘制，不用位图）
- “今天”边界使用设备当前时区；监听 `ACTION_DATE_CHANGED`/`TIMEZONE_CHANGED`/`ON_RESUME` 刷新
- 浏览非今天日期时，标题、日程和当日完成分区切换为所选日期；不得把未来日期之前的所有事项误判为“已逾期”，逾期始终以实际今天为参照。无时间事项继续可访问。
- FAB：展开为 QuickAddSheet

### 5.2 QuickAddSheet（快速添加）

- ModalBottomSheet；打开先显示月历，点击标题输入时弹出键盘；取消／保存退出时协调键盘和 Sheet 动画，退场完成后再移除页面
- 输入框：标题；回车=保存
- Chip 行：`今天` `明天` `选时间` `提醒` `重复` `地点` `粘贴通知`
  - `今天/明天`：设置日期（未选时间则 allDay=true）
  - `选时间`：Material3 DatePicker + TimePicker
  - `提醒`：选项 = 准时 / 提前5分钟 / 提前10分钟 / 提前30分钟 / 提前1小时 / 提前1天 / 自定义；仅当有 dueAt 且非 allDay 时可选；allDay 时提醒默认当天 09:00（可在设置中改）
  - `粘贴通知`：跳转 AiImportScreen
- 保存时：若设置了提醒且无通知权限/精确闹钟权限，触发权限引导（见 6.1）

### 5.3 详情/编辑页

- 编辑全部字段；删除；复制
- 进入/返回使用容器转场（见 7）

### 5.4 手势与撤销

- 左滑展开完成／重新打开按钮，右滑展开删除按钮；滑动松手不执行，点击按钮后才修改数据，并显示 Snackbar“撤销”（5 秒）
- 删除撤销需恢复 reminder 并重新注册闹钟

### 5.5 设置页

- AI 设置（见 8.1）
- 提醒自检（见 6.4）
- 提醒方式：普通提醒（默认）/ 系统闹钟提醒；后者会显示系统下一次闹钟标记，可能增加耗电，切换后重注册未触发提醒。不得因机型自动、静默切换。用户已选择在其 OPPO 测试手机上启用系统闹钟提醒。
- 主题：跟随系统 / 浅色 / 深色；动态取色开关（Android 12+）
- 默认全天提醒时间（默认 09:00）
- 保存 AI 原文开关（默认关）
- 动画降级开关（默认自动：低内存设备 `ActivityManager.isLowRamDevice` 时开启）

## 6. 提醒系统（最高优先级）

### 6.1 权限

| 权限 | 声明 | 请求时机 |
|---|---|---|
| `POST_NOTIFICATIONS` | Manifest | Android 13+；首次设置提醒时请求，不在启动时请求 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Manifest（因 D3，两者均可声明；`USE_EXACT_ALARM` 在 API 33+ 自动授予） | API 31–32 需检查 `canScheduleExactAlarms()`，否则引导至系统设置页 |
| `RECEIVE_BOOT_COMPLETED` | Manifest | 无需运行时请求 |
| `INTERNET` | Manifest | 无需运行时请求 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Manifest（因 D3 可用） | 自检页引导时使用 |

被拒绝时：保存待办仍成功，并显示一条非阻塞提示“未开启通知权限，提醒不会生效”+ 去设置入口。

### 6.2 调度

- `AlarmScheduler.schedule(reminder)`：`PendingIntent` 使用 `requestCode = reminder.id`（Int 范围内），flags=`FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`，extras 带 `reminderId`
- `AlarmScheduler.cancel(reminderId)`
- 用户选中“系统闹钟提醒”时，以 `setAlarmClock(AlarmClockInfo, PendingIntent)` 注册同一 reminder；系统的查看闹钟入口打开对应待办详情。两种模式共享提醒 ID、取消、重复推进、撤销和恢复流程；新安装默认仍为普通提醒。
- 触发时 `ReminderReceiver`：读取数据库 → 校验 todo 仍为 TODO → 发通知 → `fired=true`；使用 `goAsync()` 并在协程中完成，10 秒内结束
- 过去的 triggerAt：不调度（若 todo 未完成且 triggerAt 已过且 fired=false，开机重注册时立即补发一次通知）
- 通知渠道：`todo_reminder`，`IMPORTANCE_HIGH`，用户可自行调整铃声/震动

### 6.3 通知内容与按钮

- 标题=todo.title；正文=时间 + 地点（如有）
- 点击通知：打开对应详情页
- Action 按钮：`完成`、`稍后` → `稍后`点击后弹出（通过多个 action 或 RemoteInput 外的方式实现）：10 分钟 / 1 小时 / 明天同一时间。实现方式：三个独立 action 会过多，**采用 2 个 action：`完成`、`推迟10分钟`**；更多推迟选项在点击通知进入详情页后提供
- 所有 Action 由 `ActionReceiver` 处理，处理完后取消该通知

### 6.4 重新注册与保活

- `BootReceiver` 监听 `BOOT_COMPLETED`、`TIME_SET`、`TIMEZONE_CHANGED`、`MY_PACKAGE_REPLACED` → 调用 `RescheduleAll`（查询所有 `fired=false` 且关联 todo 为 TODO 的 reminder 并重新调度）
- App 每次冷启动也执行一次 `RescheduleAll`（幂等）
- **提醒自检页（ReminderCheckScreen）**：
  1. 显示各项状态：通知权限、精确闹钟权限、电池优化白名单、通知渠道是否被静音
  2. “发送测试提醒”按钮：10 秒后触发一条测试通知
  3. 检测 `Build.MANUFACTURER`（xiaomi/huawei/honor/oppo/realme/vivo/samsung/其他），展示对应的“自启动/后台运行/锁定任务”设置指引文本，并尽力通过 `Intent` 跳转厂商设置页（跳转失败 fallback 到应用详情页 `ACTION_APPLICATION_DETAILS_SETTINGS`）
  4. 厂商 Intent 列表放在常量表，对 `resolveActivity` 为空的跳过

## 7. UI / 交互 / 动效规格

### 7.1 视觉

- 用户已选定 **U03「周历与日程」+ I01「极简细线」**，详见 `design/selected-direction.md`；参考图为 `design/exploration-v1/U03.png` 和 `I01.png`。
- Material 3，圆角：卡片 16dp，Sheet 28dp，FAB 16dp
- 基础配色参考 U03：暖白背景、炭黑文字、少量低饱和陶土色强调，具体色值写入 `Color.kt`；Android 12+ 默认启用动态取色，设置可关。动态取色和深色模式下保持相同布局、排版层级与细线图标风格
- 日程以平整行式清单和细分隔线组织，留白适中，减少不必要的卡片包裹和装饰；选中、完成和提醒状态必须清晰可辨
- 间距基准 8dp；列表项最小高度 56dp；触控目标 ≥ 48dp
- 文字：使用 M3 Typography 层级；按用户后续确认，英文标题及清单使用 Source Serif 4，辅助文字使用 Source Sans 3，中文使用思源宋体。字体随 APK 离线提供并保留许可证；Android 8/9 使用思源宋体自带拉丁字形兼容回退。以中文版实际可读性和字号缩放表现为准，不宣称复用 Claude 原版字体。
- 界面图标采用 I01 的均匀细线、轻圆端点和充足负空间；应用图标沿用 I01 的笔记本与勾选意象，按 Android 图标要求重新制作，不直接裁切风格板

### 7.2 动效（`Motion.kt` 集中定义常量）

| 常量 | 值 |
|---|---|
| `DurationShort` | 150ms |
| `DurationMedium` | 300ms |
| 详情进入 / 内容交接延时 | 190ms / 75ms（0.4.4，用户确认动效后自主小幅提速） |
| 返回补足缩小 / 收回 / 取消恢复 | 最多 90ms / 290ms / 150ms；手势已缩小时按剩余进度缩短补足阶段 |
| 曲线 | `CubicBezierEasing(0.2f, 0f, 0f, 1f)`（Emphasized decelerate）用于进入；`CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` 用于退出 |
| 交互 spring | `spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)` |

### 7.3 动效清单与验收

| ID | 场景 | 实现要求 | AC |
|---|---|---|---|
| M-1 | 列表项 → 详情 | 按用户验收通过的 0.4.3 B 方案：固定布局淡入平移进入；返回先有限等比缩小并增加圆角，详情下移并完全淡出后，实际条目内容仅以透明度渐显，容器收回当前可见条目处。未创建或目标不可见时收回中央，不自动滚动。动画使用固定画面层，避免逐帧重排表单 | 用户已确认流程与观感；进入/返回均无闪烁，帧率不低于 60fps（中端机）的测量验收仍独立待完成 |
| M-2 | 系统返回手势 | Manifest `android:enableOnBackInvokedCallback="true"`；使用 Compose Navigation 的 Predictive Back 支持，手势过程中页面随手指缩放/位移 | Android 14+ 设备上拖动手势可预览上一页，取消手势可回弹 |
| M-3 | FAB → QuickAddSheet | FAB 容器变形展开为 Sheet（或 FAB 淡出 + Sheet 弹出，若容器变形不稳定则降级） | 无跳变 |
| M-4 | 左/右滑 | `AnchoredDraggable` 展开一级操作菜单；左滑露出完成／重新打开，右滑露出删除；只有点击菜单按钮才执行；越过展开阈值触发 `HapticFeedbackType.LongPress` | 滑动松手不修改数据；点击按钮执行后可撤销；未过阈值则回弹 |
| M-5 | 勾选完成 | 复选框描边绘制动画（约 200ms）→ 标题划线从左到右展开 → 条目淡出收起 | 三段动画顺序连贯；其余条目 `Modifier.animateItem()` 平滑补位 |
| M-6 | 列表增删/重排 | `LazyColumn` 提供稳定 `key = todo.id`；使用 `animateItem` | 无条目跳变 |
| M-7 | 点击反馈 | 统一 ripple + 按压缩放至 0.97（`graphicsLayer` + `animateFloatAsState`） | 全部可点击卡片一致 |
| M-8 | 加载 | 骨架屏 shimmer（AI 解析等待时） | 不使用圆形转圈作为主加载 |
| M-9 | 动画降级 | 设置开关或低内存设备：转场改为简单 fade，禁用共享元素 | 开启后无共享元素 |

### 7.4 性能要求

- 动画期间 MUST NOT 执行 DB/网络操作；数据来自内存 `StateFlow`
- Composable 参数保持稳定（必要时 `@Immutable`/`@Stable`；列表使用 `kotlinx.collections.immutable` 之外的方案以避免新依赖，可用 `@Immutable` 包装类）
- 使用 `collectAsStateWithLifecycle`
- 提供 Baseline Profile 模块（`baselineprofile`），覆盖：冷启动、打开 Sheet、滑动列表、进入详情

## 8. AI 通知识别（BYOK）

### 8.1 AI 设置（`AiSettingsScreen`）

用户输入字段：

| 字段 | 说明 | 默认 |
|---|---|---|
| provider | 枚举：`OPENAI_COMPAT`、`ANTHROPIC` | `OPENAI_COMPAT` |
| baseUrl | 例：`https://api.openai.com/v1`、`https://api.deepseek.com/v1`、`https://api.anthropic.com` | 空，必填 |
| apiKey | 密文输入框，保存时经 `SecretStore` 加密；界面只显示末 4 位 | 空，必填 |
| model | 模型名文本框 | 空，必填 |
| timeoutSeconds | 请求超时 | 20 |
| 保存原文 | 见 5.5 | 关 |

- 提供“测试连接”按钮：发送一个极短请求（如 `ping`，`max_tokens` 极小），显示成功/失败原因（401 鉴权失败、404 URL 错误、超时、解析错误等）
- 首次使用 AI 功能前弹出一次性隐私同意对话框：“通知文本将发送到你配置的模型服务商”，用户同意后记录 `aiConsentGranted=true`
- MUST NOT 在代码、日志、崩溃信息中输出 API Key 或完整通知原文

### 8.2 LlmClient 接口

```kotlin
interface LlmClient {
    suspend fun complete(system: String, user: String): Result<String>  // 返回模型原始文本
}
```

- `OpenAiCompatClient`：`POST {baseUrl}/chat/completions`，Header `Authorization: Bearer <key>`，body：`model`、`messages=[{role:system},{role:user}]`、`temperature=0`；若服务商支持，附 `response_format={"type":"json_object"}`，失败（400）时自动去掉该字段重试一次
- `AnthropicClient`：`POST {baseUrl}/v1/messages`，Header `x-api-key`、`anthropic-version: 2023-06-01`，body：`model`、`max_tokens=1024`、`system`、`messages=[{role:user}]`
- 统一超时：`callTimeout = timeoutSeconds`；不做流式
- URL 拼接需容错（尾部是否有 `/`、是否已含 `/v1`）

### 8.3 解析流程

```
输入文本 (粘贴 / ACTION_SEND 分享)
 → 预处理: trim, 合并连续空白, 截断到 2000 字符 (超出提示用户)
 → PromptBuilder 构造 system + user
 → LlmClient.complete
 → 提取 JSON: 去除 ```json 围栏; 取首个 '{' 到最后一个 '}'
 → kotlinx.serialization 解码 (ignoreUnknownKeys = true, isLenient = true)
 → 校验 + 规范化
 → 预览卡片列表
 → 用户勾选/编辑 → 批量写入 DB + 注册提醒
```

### 8.4 Prompt 模板

System（`{now}` 为设备当前时间，格式 `yyyy-MM-dd EEEE HH:mm`；`{tz}` 为设备时区 ID）：

```
你是待办提取助手。从用户提供的工作通知中提取需要“用户本人”执行的事项。
当前时间：{now}，时区：{tz}。
规则：
1. 只输出一个 JSON 对象，不要输出任何解释、前后缀或 Markdown 围栏。
2. 相对时间（明天、后天、下周三、本周五前、月底）必须依据“当前时间”换算为绝对时间，格式 yyyy-MM-dd'T'HH:mm（无时区后缀，视为 {tz} 本地时间）。
3. 通知中只有日期没有具体时刻时，dueAt 填 yyyy-MM-dd'T'00:00 且 allDay=true。
4. 如果是时间段（如 14:00-16:00），dueAt 取开始时间，并在 note 中写明结束时间。
5. 通知中没有需要执行的事项时，todos 返回空数组。
6. 不要编造原文中没有的信息；不确定的字段填 null。
7. title 简洁（不超过 30 字），以动词开头，描述要做的事。
8. 一条通知含多个事项时拆分为多个 todo。
输出格式：
{"todos":[{"title":"string","dueAt":"string|null","allDay":false,"location":"string|null","note":"string|null","confidence":0.0}]}
```

User：直接放入预处理后的通知原文。

### 8.5 数据结构与校验

```kotlin
@Serializable data class ParsedTodo(
    val title: String,
    val dueAt: String? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val note: String? = null,
    val confidence: Double? = null
)
@Serializable data class ParseResult(val todos: List<ParsedTodo> = emptyList())
```

校验规则：
- `title` 为空 → 丢弃该条
- `dueAt` 解析失败 → 置 null，并在卡片标记“时间未识别”
- `dueAt` 早于当前时间 → 保留但卡片显示“时间已过”警告，默认不勾选提醒
- `confidence < 0.6` 或 `dueAt == null` → 卡片字段高亮提示
- 默认提醒：有具体时间则“准时”；allDay 则当天默认全天提醒时间

### 8.6 AiImportScreen 交互

1. 顶部：文本输入框（支持粘贴按钮，点击才读取剪贴板；MUST NOT 在后台/启动时自动读取剪贴板内容）
2. 按钮“识别”，点击后显示骨架屏
3. 结果：卡片列表，每张卡片含复选框（默认勾选）、可编辑的标题/时间/地点/备注，点击时间区域弹出日期时间选择器
4. 底部固定栏：“添加 N 项”
5. 空结果：提示“没有识别到待办”，提供“手动创建”按钮并携带原文到备注
6. 失败（超时/401/解析失败/网络）：显示具体原因 + 两个按钮：`重试`、`用本地规则识别`

### 8.7 分享入口

- `MainActivity` 注册 `intent-filter`：`ACTION_SEND`，`mimeType=text/plain`
- 收到后直接导航到 `AiImportScreen` 并预填文本；若 AI 未配置，则导航到 AiSettings 并提示

### 8.8 本地兜底（`LocalNoticeFallback`）

仅在 AI 失败且用户选择时使用。规则：
- 识别：`M月D日`、`M/D`、`明天`、`后天`、`周X/星期X/下周X`、`HH:mm`、`H点(半)`、`上午/下午/晚上`
- 输出：单条 `ParsedTodo`，title 取原文首行（截断 30 字），其余字段尽力填充
- 必须经预览确认，不直接入库

### 8.9 AI 评测集（开发期）

- 在 `app/src/test/resources/notices/` 放置不少于 30 条脱敏通知样例，每条 `*.txt` 对应 `*.expected.json`
- 提供 `NoticeParseEvalTest`（需手动配置环境变量 `LLM_BASE_URL/LLM_KEY/LLM_MODEL` 后运行，默认 `@Ignore`），输出标题/时间/地点的匹配准确率

## 9. 任务分解（按顺序执行）

每个任务完成后 MUST 保证项目可编译且已有测试通过。

### M1 基础骨架
- T1.1 创建 Gradle 工程、依赖、R8、minSdk/targetSdk、主题（含浅/深色/动态取色）
- T1.2 Room：两张表 + DAO + 迁移策略（初版 `exportSchema=true`）
- T1.3 `TodoRepository`：增删改查、今日分区查询 Flow、完成/撤销、重复规则推进
- T1.4 Navigation 骨架（Today / Detail / AiImport / Settings）
- T1.5 TodayScreen 静态列表 + ViewModel
- AC：可增删改查待办，分区显示正确；`DateTimeUtil`、`RepeatRule` 单元测试通过（覆盖跨月、跨年、闰年、月末、DST 无关）

### M2 提醒系统
- T2.1 通知渠道、`NotificationHelper`
- T2.2 `AlarmScheduler`、`ReminderReceiver`、`ActionReceiver`
- T2.3 `BootReceiver` + `RescheduleAll`
- T2.4 权限引导流程（6.1）
- T2.5 提醒自检页（6.4）
- AC：设置 1 分钟后的提醒，锁屏/杀进程/重启后均能收到；通知按钮“完成/推迟10分钟”生效

### M3 交互打磨
- T3.1 QuickAddSheet（5.2）
- T3.2 M-1 ~ M-9 全部动效
- T3.3 详情页编辑
- T3.4 Baseline Profile
- AC：7.3 表中所有 AC 通过；Macrobenchmark 滑动掉帧率不高于 10%

### M4 AI 导入
- T4.1 `SecretStore`、`SettingsStore`、AiSettingsScreen（含测试连接）
- T4.2 `LlmClient` 两种实现 + 单元测试（使用 `MockWebServer`，覆盖 200、401、404、超时、非 JSON 响应、带围栏 JSON）
- T4.3 `PromptBuilder`、JSON 提取与校验、`NoticeParseRepository`
- T4.4 AiImportScreen + 分享入口
- T4.5 本地兜底
- T4.6 评测集与评测测试
- AC：能对示例通知（含“明天下午三点”“本周五前”“4月12日 14:00-16:00 会议室A”）生成正确的预览卡片；断网时可降级

### M5 稳定性与优化
- T5.1 多机型提醒测试清单执行并记录结果到 `docs/reminder-test-matrix.md`
- T5.2 优先验证功能、稳定性和运行体验，实测并记录启动时间、帧率、空闲内存占用及 release APK 体积；按 D4 优化启动与交互性能，在不损害功能、可靠性和体验的前提下进行体积及资源占用优化，APK 大小不作为验收通过与否的门槛
- T5.3 崩溃处理：本地捕获未处理异常并写入应用私有日志（无第三方 SDK），设置页提供“导出日志”（日志内容 MUST NOT 含密钥与通知原文）
- T5.4 `./gradlew assembleRelease` 产出签名 APK，附安装说明 `README.md`

## 10. 测试要求

| 类型 | 范围 |
|---|---|
| 单元测试 | `RepeatRule`、`DateTimeUtil`（今天边界、时区切换）、JSON 提取与校验、`LocalNoticeFallback`、`LlmClient`（MockWebServer） |
| 仪器测试 | Room DAO、迁移；`RescheduleAll` 幂等性 |
| UI 测试 | Compose UI Test：添加 → 完成 → 撤销；AI 解析成功/失败路径（注入 fake `LlmClient`） |
| 手工测试 | 提醒专项：锁屏、Doze、杀进程、重启、改时区、省电模式；设备至少覆盖小米/华为或荣耀/OPPO 或 vivo/三星/Pixel 中的 3 个品牌 |

## 11. 全局验收清单

- [ ] 无后端依赖，AI 请求直连用户配置的 baseUrl
- [ ] API Key 经 Keystore 加密，日志中无密钥
- [ ] 不自动读取剪贴板
- [ ] 无 GMS/Firebase/第三方统计 SDK
- [ ] 功能完整正确、运行稳定，启动和交互性能达到方案要求；不为轻量化削减功能或降低可靠性与体验
- [ ] 已记录 release APK 体积和资源占用，作为后续优化参考；轻量化为加分项，无 APK 体积硬性门槛
- [ ] 断网/AI 失败时手动添加与提醒完全正常
- [ ] 重启手机后未触发提醒仍会触发
- [ ] Android 14+ 预测性返回手势可用
- [ ] 所有动效在“动画降级”开启时可简化为 fade

## 12. 开放项（agent 遇到时自行做合理决定并在 `docs/decisions.md` 记录）

- 主色具体色值
- 空状态插画的绘制样式
- 厂商设置页 Intent 的具体 component 列表（以实测为准，失败则回退）
- 共享元素在个别设备上出现问题时的降级处理
