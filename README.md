# 待办 · Android

原生 Kotlin / Jetpack Compose 离线待办，按 [实施规格](todo-app-spec.md) 与 [已确认设计](design/selected-direction.md) 开发。

当前已实现 **M1–M4：基础待办、提醒、交互与 AI 导入**。M2 检查点通过了 19 项设备自动测试；新增交互、AI 与性能工具已完成主机构建检查，真机体验和正式验收等待后续反馈。不要将开发包视为已经完成全部验收。

## 构建

本机工具已准备在忽略版本控制的 `.tools/` 中，详见 [环境说明](docs/development-environment.md)。

```bash
source scripts/android-env.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

换机器时需自行安装对应 JDK/SDK，或调整环境脚本；仓库不包含工具缓存、代理设置或任何签名密钥。

## 安装开发包

APK：`app/build/outputs/apk/debug/app-debug.apk`，包名 `com.example.todo`，最低 Android 8.0（API 26）。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可将 APK 传到手机后点击安装，并允许该文件来源安装应用。Debug 包使用本地开发签名。

手机接在 Windows、开发机为远程 Linux 时，使用 [Windows USB / SSH ADB 转发步骤](docs/remote-device-testing.md)。

安装后可添加、编辑、完成、删除和撤销待办；通过周历或“全部待办”查看未来事项。首次设置提醒时再申请权限，在“设置 → 提醒自检”中检查并测试。系统强行停止期间不能保证提醒；再次打开应用后恢复调度。

提醒默认使用普通模式。可在提醒自检中选择“系统闹钟提醒”；它可能显示系统下一次闹钟标记，并增加资源使用。在本次 OPPO 对照测试中，它改善了锁屏、进程退出后的送达时间；其他机型仍需实测。两种模式均需正确配置通知及厂商后台权限，详见[兼容性决策](docs/reminder-compatibility-proposal.md)。

## 验证与发布状态

- [实施进度与验证边界](docs/implementation-status.md)
- [提醒真机测试矩阵](docs/reminder-test-matrix.md)
- [依赖用途](docs/dependencies.md)
- [实现决策](docs/decisions.md)
- [交互与性能验收](docs/motion-and-performance.md)
- [AI 配置、隐私与评测](docs/ai-import.md)

`./gradlew :app:assembleRelease` 可验证 R8 和资源裁剪，但当前产物 **未签名，不能直接安装**。正式签名身份尚待 M5 确认，不会以开发签名冒充发布包。真机提醒可靠性、60 fps、冷启动指标和三品牌覆盖均待实测。
