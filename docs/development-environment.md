# Android 开发环境

检查及安装日期：2026-10-27。当前阶段只准备开发环境，Todo 应用业务工程尚未开始。

## 工具与位置

主机：Ubuntu 24.04 x86_64，约 31 GiB 内存；安装前可用磁盘约 815 GiB。

| 工具 | 固定版本 / 位置 |
|---|---|
| JDK | Eclipse Temurin 21.0.13+9，`.tools/jdk` |
| Gradle | 9.6.0，`.tools/gradle-9.6.0` |
| Android SDK | `.tools/android-sdk` |
| Android 平台 | Android 17 / API 37.0，平台修订版 2 |
| SDK Build Tools | 37.0.0 |
| Platform Tools / adb | 37.0.1 / 1.0.41 |
| Android Command-line Tools | 23.0，安装目录 `cmdline-tools/latest` |
| Android CLI | 1.1.16717390，自动下载缓存位于 `.tools/android-user` |
| Gradle 缓存及本机代理配置 | `.tools/gradle-user` |
| 最小构建验证工程 | `.tools/smoke`，独立包名 `com.example.environmentsmoke` |

验证工程使用 AGP 9.4.1、Kotlin/Compose 编译器 2.4.21、Compose BOM 2026.09.00、Activity Compose 1.13.0。Gradle 自身显示的内置 Kotlin 版本用于构建脚本，并非应用 Kotlin 编译器版本。

JDK、Gradle、命令行工具和手动下载的 SDK 安装包已核对官方元数据中的校验值。AGP 9.4 要求 Gradle 至少 9.6.0、JDK 至少 17，并支持 API 37，见[官方兼容说明](https://developer.android.com/build/releases/agp-9-4-0-release-notes)。

没有安装 Android Studio 或模拟器镜像；当前以命令行构建及后续 Android 17 真机测试为路径。`/dev/kvm` 可读写，但未验证模拟器启动。adb 会按自身默认行为在用户的 `~/.android` 创建设备授权密钥，工具主体和 Gradle 缓存位于本项目。

## 使用

在 Bash 中加载环境（只影响当前终端，不改系统 Java 或 shell 启动文件）：

```bash
cd /home/js/project/todoAPP
source scripts/android-env.sh
bash scripts/check-android-env.sh
```

新版 SDK 工具已转向 Android CLI，使用项目包装脚本管理 SDK：

```bash
bash scripts/android-cli.sh sdk list
```

旧命令 `sdkmanager --licenses` 在这版工具中只输出弃用提示，不完成许可证处理。SDK 平台已通过新版 CLI 安装，SDK 下有相应许可证及包元数据。

本机 HTTP(S) 代理为 `127.0.0.1:7890`，已配置在忽略版本控制的 `.tools/gradle-user/gradle.properties` 中。Android CLI 引导程序会优先选择无法连接的 SOCKS 代理，因此包装脚本仅对其子进程移除 `ALL_PROXY/all_proxy`，保留 HTTP(S) 代理，并传入 `--no-metrics`。更换机器或代理端口时需调整本机 Gradle 配置。

## 验证与证据

工具版本、SDK 包列表、打包工具启动和 adb 服务检查已通过。当前 `adb devices -l` 为空，未连接真机。

已完成的构建验收：

- `gradle -p .tools/smoke assembleDebug assembleRelease --console=plain` 成功，86 个任务执行完成。
- Gradle Wrapper 已生成并带官方发行包 SHA-256 校验；通过 Wrapper 启动 Gradle 9.6.0 验证成功。
- Kotlin/Compose 编译、资源打包、DEX、R8、资源裁剪、Release 必要 Lint 检查及 APK 生成均成功。
- Debug 和 Release APK 均通过 `apksigner verify --verbose`。
- Release APK 清单确认 `minSdk=26`、`targetSdk=37`、`compileSdk=37`。
- 三个交付脚本通过 `bash -n` 语法检查。

验证 APK 位于：

```text
.tools/smoke/build/outputs/apk/debug/AndroidEnvironmentSmoke-debug.apk
.tools/smoke/build/outputs/apk/release/AndroidEnvironmentSmoke-release.apk
```

构建日志为 `.tools/downloads/smoke-build.log`；签名、体积和 SHA-256 记录为 `.tools/downloads/apk-verification.json`；环境检查日志为 `.tools/downloads/environment-check.log`。工具、缓存、下载包和验证工程合计约 3.2 GiB。

复跑构建：

```bash
source scripts/android-env.sh
./.tools/smoke/gradlew -p .tools/smoke assembleDebug assembleRelease
```

日志与校验元数据保留在 `.tools/downloads/`，该目录不提交版本控制。最小验证工程开启 R8 和资源裁剪，Release 使用开发调试签名，仅用于验证工具链，不是正式发布身份，也不是 Todo 应用交付包。

## 真机测试准备

用户设备运行 Android 17。后续测试时启用开发者选项和 USB 调试，通过数据线连接，并在手机上接受本机调试授权。然后检查：

```bash
source scripts/android-env.sh
adb devices -l
```

设备状态应为 `device`；`unauthorized` 表示尚未在手机上接受授权。正式 Todo APK 构建完成后再安装测试。

锁屏、Doze、进程回收、重启、厂商省电策略、预测性返回、启动时间和帧率均待真机验证。至少三个品牌的测试矩阵仍待执行。正式应用以功能完整正确、稳定可靠和优秀体验为优先；APK 体积与资源占用仅作为轻量化优化参考，不设 8 MB 硬性门槛。最小验证工程的体积不能代表正式应用的体积或运行表现。
