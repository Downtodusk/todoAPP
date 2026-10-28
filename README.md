# 待办 · Android

原生 Kotlin / Jetpack Compose 离线待办，按 [实施规格](todo-app-spec.md) 与 [已确认设计](design/selected-direction.md) 开发。

当前 **0.4.3** 按确认的 B 版方案返回：先明显缩小与圆角变化，详情下移并完全淡出后，再渐显条目并收回其真实位置；未创建或目标不可见时回到中央。保留原进入效果及现有功能，见 [方案与验证记录](docs/detail-motion-0.4.3.md)。真机手感及其他未完成验收仍待用户反馈。

## 构建

本机工具已准备在忽略版本控制的 `.tools/` 中，详见 [环境说明](docs/development-environment.md)。

```bash
source scripts/android-env.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

换机器时需自行安装对应 JDK/SDK，或调整环境脚本；仓库不包含工具缓存、代理设置或任何签名密钥。

## 安装开发包

已有此前测试安装时，下载 `dist/todo-0.4.3-debug.apk` 覆盖更新，保留数据。全新安装可选 `dist/todo-0.4.3-release.apk`（独立发布签名）。两种签名不能互相覆盖，请不要为切换包而卸载重要数据。详见[签名与诊断交付](docs/release-and-diagnostics.md)。

原始 Debug 构建输出：`app/build/outputs/apk/debug/app-debug.apk`，包名 `com.example.todo`，最低 Android 8.0（API 26）。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可将 APK 传到手机后点击安装，并允许该文件来源安装应用。Debug 包使用本地开发签名。

手机接在 Windows、开发机为远程 Linux 时，使用 [Windows USB / SSH ADB 转发步骤](docs/remote-device-testing.md)。

安装后可添加、编辑、完成、删除和撤销待办；通过周历或“全部待办”查看未来事项。首次设置提醒时再申请权限，在“设置 → 提醒自检”中检查并测试。系统强行停止期间不能保证提醒；再次打开应用后恢复调度。

提醒默认使用普通模式。可在提醒自检中选择“系统闹钟提醒”；它可能显示系统下一次闹钟标记，并增加资源使用。在本次 OPPO 对照测试中，它改善了锁屏、进程退出后的送达时间；其他机型仍需实测。两种模式均需正确配置通知及厂商后台权限，详见[兼容性决策](docs/reminder-compatibility-proposal.md)。

设置中可选择跟随系统、简体中文或 English；配色提供 5 套预设，也可输入十六进制色值或调整 RGB 滑块。语言与颜色保存在本机。选择配色会关闭动态取色，深浅色外观仍可独立选择。

## 验证与发布状态

- [实施进度与验证边界](docs/implementation-status.md)
- [提醒真机测试矩阵](docs/reminder-test-matrix.md)
- [依赖用途](docs/dependencies.md)
- [实现决策](docs/decisions.md)
- [交互与性能验收](docs/motion-and-performance.md)
- [AI 配置、隐私与评测](docs/ai-import.md)
- [签名、日志导出与后续反馈](docs/release-and-diagnostics.md)

`./gradlew :app:assembleRelease` 使用本地发布身份生成可安装的签名包；`bash scripts/package-apks.sh` 汇总 APK、校验值与证书报告到 `dist/`。私钥和 `keystore.properties` 不入 Git，需一起安全备份；新机器应恢复同一身份。

AI 导入使用你自行填写的服务与密钥，默认不保存原文，首次发送前征求授权；可在识别失败后选择本地规则或手动创建。设置中可导出不含用户内容的本地崩溃日志。

已知限制：OPPO 重启恢复提醒失败按用户要求暂缓；Doze、省电、时区、三品牌覆盖、真实模型准确率及启动/帧率/内存指标尚未完成验收。用户会在方便时下载测试，本轮不继续操作手机。
