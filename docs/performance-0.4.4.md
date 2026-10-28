# 0.4.4：动效小幅提速与实测 Profile

用户已确认 0.4.3 动效通过。本版本保持容器轨迹、90% 缩小下限、圆角、55% 内容交接点、实际条目落点与中央兜底，仅将节奏略微加快。

| 阶段 | 0.4.3 | 0.4.4 |
| --- | ---: | ---: |
| 进入详情 | 220ms | 190ms |
| 返回时补足缩小 | 最多 110ms | 最多 90ms |
| 详情淡出 → 条目渐显并收回 | 340ms | 290ms |
| 未提前缩小时的完整返回 | 450ms | 380ms |
| 取消手势后恢复 | 180ms | 150ms |

进入约缩短 14%，返回约缩短 16%。手指拖动仍直接控制缩小进度；已经缩小时不重复完整补足阶段。增加速度不等于提高帧率。勾选、滑动菜单、减少动态效果等无关节奏保持不变。

## 继续执行 T3.4 / T5.2

- 在独立 API 36 x86_64 / SwiftShader 模拟器（ADB 15038）运行实际 Profile 采集，覆盖冷启动、打开／关闭添加面板、列表滑动和详情往返。仅使用隔离包 `com.example.todo.benchmark` 和合成数据。
- 修复 profile 变体遗漏 Kotlin 测试数据入口：共享源码目录需配置为 `kotlin.srcDir`。首次失败日志保留；该入口从未加入普通 Debug/Release。
- 长路径采集还暴露了滚动后偶发未进入详情的问题；仅等待无障碍空闲不足以稳定测试。采集脚本改为确认完整可见条目坐标连续稳定 300ms，再执行一次 80ms 点击，不重试导航；失败时保留合成界面截图及结构。此变更仅影响测试工具。
- `scripts/device-performance.sh ... --emulator` 显式选择隔离服务并核对模拟器属性。默认手机流程仍需用户授权，不在普通构建中自动连接手机。
- 采集成功后合并真实规则并排除性能夹具，两个 Profile 都通过校验才写入。打包校验应用规则及 Release 中的 `baseline.prof` / `baseline.profm`，在 build-info 中记录源文件哈希。
- 启动及交互测量使用 `CompilationMode.Partial(BaselineProfileMode.Require)`；缺少可安装 Profile 时直接失败，不改用无 Profile 模式冒充成功。

## 验证记录

- JVM 59 项通过、1 项真实模型评估按设计跳过；Lint 零错误。Debug、测试 APK、Profile、R8 benchmark 和签名 Release 均构建成功。
- 动效专项 9/9 通过：包括真实系统边缘返回、取消恢复、详情／条目透明度互斥、真实落点、不可见目标、新建／取消、并发通知和长标题／减少动态效果。导航设置测试后恢复。
- Profile 采集 2/2 通过；baseline 24,666 条规则（应用自身 1,583 条），startup 19,335 条（应用自身 1,026 条）。保留真实依赖路径，排除测试夹具。R8 处理后的规则中仍可查到 MainActivity；Release 的二进制 profile 为 10,187 字节，元数据 1,151 字节。
- [采集日志](performance-0.4.4/collection.log)及[源码状态、APK 与规则哈希](performance-0.4.4/profile-provenance.json)。第一次构建夹具遗漏、两次未进入详情和一次带截图的诊断失败均保留于本地 `.tools/performance/ui044/collect*.log`；未计入成功样本。

模拟器结果不能代替中端机 <500ms、60fps 或滑动掉帧比例 ≤10% 的验收。

版本 0.4.4 / code 9；相同包名、Room v1 与签名，Debug 可覆盖之前测试版。没有修改暂缓的 OPPO 重启提醒问题，也没有操作用户手机或调用真实模型。


## Profile 启用后的模拟器测量

`TodoMacrobenchmark` 的冷启动和交互两项均通过；Profile 使用 Require 模式完成安装，无降级。冷启动 10 轮；交互 5 轮，每轮含添加面板、6 次列表滑动、详情进入／返回。420dpi、100% 字体、三键导航，R8 优化包；测试时没有并行 Gradle 构建。没有运行无 Profile 对照，因此不声称 Profile 带来了已测量的加速。

| 指标 | 本次观测 |
| --- | ---: |
| 冷启动首屏 TTID 中位数 / P95 | 826.66 / 978.17ms |
| 工具报告的完整显示 TTFD 中位数 / P95 | 896.04 / 1084.62ms |
| 全交互 CPU 帧耗时 P50 / P95 | 22.25 / 45.85ms |
| 全交互帧截止超时 P50 / P95 | 7.58 / 60.03ms |
| 全交互采样帧超出截止时间比例 | 79.36% |
| 前台空闲 PSS 中位数 | 57.84MiB |
| 前台空闲 RSS 中位数 | 183.54MiB |

启动 P95 按 10 个样本线性插值；内存等待 30 秒后取 3 个间隔 10 秒的样本。帧超时比例覆盖整个交互流程，并非规格中的“仅滑动掉帧比例”，不能换算成 FPS。软件渲染模拟器这组数值没有达到可声称性能通过的依据，也不能推断手机表现；<500ms、60fps、滑动掉帧 ≤10% 仍待实机测量。普通 Debug 更新包与 R8 性能包不等价。

[原始帧／启动数据](performance-0.4.4/benchmarkData.json) · [测量日志](performance-0.4.4/benchmark.log) · [指标、内存、APK 大小及哈希](performance-0.4.4/measurements.json) · [构建／回归摘要](performance-0.4.4/verification.txt)。完整 15 份 Perfetto trace 保存在 `.tools/performance/20261028T080522Z-benchmark/outputs/`。
