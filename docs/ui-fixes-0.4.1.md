# 0.4.1 已完成布局与详情转场

版本 **0.4.1 / code 6**，沿用原签名、Room v1、字体及个人设置。

- **已完成事项取消时间留白**：勾选框回到列表左侧，标题随之前移；适用于首页的已完成栏目及全部待办中的完成项。未完成的全天／定时事项仍保持对齐，完成项的日期等附属信息保留。
- **详情进入／返回保持页面尺寸固定**：移除将整张详情 Scaffold（含日历、标题输入框和表单）与列表条目绑定的 `RemeasureToBounds`，以及外层 `SharedTransitionLayout`。详情不再随条目矩形逐帧重排，也不再缩放整页文字。
- **新的过渡节奏**：进入 220ms、返回 180ms；详情移动 24dp，底层列表移动 8dp。通过 90ms 的淡出交接避免两页文字长时间重叠；背景固定为当前主题纸面色。减少动态效果时仍只使用 150ms 淡入淡出。
- 保留滑动菜单、完成／撤销、快速添加动效、独立待办编辑状态及通知跳转。详情删除后的列表更新延迟与新的返回时长保持一致。

`RemeasureToBounds` 可能因每帧边界变化重新测量及布局，其行为见 [Android 官方说明](https://developer.android.com/develop/ui/compose/animation/shared-elements/customize)。本轮移除这一高开销路径；不以缩短时长代替帧耗时验证。Compose 阶段与跳过无须布局的说明见 [官方性能指南](https://developer.android.com/develop/ui/compose/performance/phases)。

## 性能对照方法

独立 API 36 x86_64 模拟器、1080×2400 / 420dpi、100% 字号、SwiftShader；正常动效。使用独立包 `com.example.todo.benchmark`，40 条合成待办，R8 构建和 `CompilationMode.Full()`。每个版本 10 轮，每轮进入／返回详情 3 次，共 30 次往返。测试类为 `DetailTransitionBenchmark`，以 `FrameTimingMetric` 收集实际帧数据及 Perfetto 跟踪。两次测量期间不并行运行 Gradle 构建。

这是模拟器的软件渲染对照，不能代表手机帧率、达成全局掉帧率门槛或完成 Baseline Profile 验收。完整的真机性能与预测性返回验收仍待用户方便时进行。

修复了此前仅编译过的性能工具两个运行问题：为性能测试 runner 显式指定 Debug 签名；准备合成数据时重启独立测试包，并按实际可见的合成条目选择目标，不假定排序后的首项一定是 01。早期未进入测量的失败日志保留，包括模拟器 System UI 无响应遮挡；这些失败不计入对照数据。未修改或操作用户手机。

## 模拟器对照结果

| 指标（毫秒，越低越好） | 0.4.0 | 0.4.1 |
| --- | ---: | ---: |
| 帧 CPU 耗时 P50 | 44.99 | 24.32 |
| 帧 CPU 耗时 P95 | 80.41 | 40.77 |
| 帧超出期限时间 P50 | 43.66 | 9.31 |
| 帧超出期限时间 P95 | 96.03 | 35.36 |

帧 CPU 耗时 P95 在本次对照中下降约 49.3%。原版共 973 帧，新版共 832 帧；动画持续时间及实现均有调整，不能把样本帧数直接解读为帧率。新版在模拟器中仍有超期帧，不宣称已满足真机掉帧率 ≤10% 的全局验收条件。

原始 AndroidX 指标：[0.4.0](performance-0.4.1/before-benchmarkData.json)、[0.4.1](performance-0.4.1/after-benchmarkData.json)；[环境、APK 哈希与汇总](performance-0.4.1/comparison.json)。完整 Perfetto traces 与日志保留于忽略目录 `.tools/performance/ui041/`，有效对照日志为 `before-r3.log` 与 `after.log`。

## 回归与截图

- Debug／测试包、签名 R8 Release、profile／benchmark 目标与 runner 均构建通过；两种性能 runner 的签名已验证。
- 54 项 JVM 测试通过，1 项需真实模型的测试按设计跳过；Lint 零错误（25 warnings、1 hint）。
- API 36 模拟器完整 UI／数据库／提醒套件：38 项通过，1 项手动场景工具按设计跳过。
- 360dp／130% 字号专项 2/2 通过，恢复 420dpi／100% 字号后专项 2/2 再次通过。首轮窄屏测试需要滚动后才能取得尚未组合的列表节点，测试改用 LazyColumn 的滚动查找后通过。
- 新增布局检查覆盖已完成的全天／定时事项取消留白；新增转场检查在普通与减少动态效果模式下确认长标题的排版尺寸、行数和字号在进入过程中保持一致，并采样进入／返回画面。
- 首轮完整回归有一处既有通知文案测试抢在系统登记通知前读取；补上等待 `activeNotifications` 的条件后完整重跑通过，生产通知逻辑未修改。

[已完成布局](ui-0.4.1/completed-no-gutter.png) · [进入途中](ui-0.4.1/detail-enter-144ms-false.png) · [详情静止](ui-0.4.1/detail-settled-false.png) · [返回途中](ui-0.4.1/detail-return-48ms-false.png)

截图为合成数据、模拟器实际渲染；文件名中的毫秒数是测试时钟的采样点，不代表设备实际渲染延迟。测试日志：`.tools/performance/ui041/final-build.log`、`test-final-build.log`、`full-suite-final.log`。

大字号截图：[已完成栏目](ui-0.4.1/narrow-font130-completed.png)、[详情](ui-0.4.1/narrow-font130-detail.png)。补充日志：`narrow-test-build.log`、`narrow-font130-final.log`、`normal-final.log`、`release-build.log`。
