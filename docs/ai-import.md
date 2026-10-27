# AI 通知导入

## 使用

1. 设置 → AI 服务与密钥，选择 OpenAI 兼容接口或 Anthropic Messages。
2. 填写自己的 HTTPS Base URL、模型名和 Key；超时默认 20 秒。密钥使用 Android Keystore 的 AES-GCM 加密后存入 DataStore，页面只显示末四位。首次配置或更换地址/服务商必须重新提供密钥。
3. 点击测试连接可发送短 ping 请求；首次网络请求前确认数据发送授权。识别请求也需要该授权。应用不提供代理、默认服务或内置密钥。
4. “通知导入”中手动粘贴，或从其他应用分享纯文本到“待办”。分享只填入文本，不自动发送。未配置时引导设置，返回后文本仍在，也可手动创建。
5. 识别后逐项核对并修改标题、日期/时间、全天、地点、备注与提醒。空标题丢弃，非法时间清空并提示；过去时间默认不启用提醒。只有点击“添加 N 项待办”才会在一次 Room 事务中保存，提交后统一调度提醒。
6. 请求失败可重试、手动创建，或明确点击“使用本地规则”。本地规则不访问网络，仅提取一项，处理常见相对日期、星期、月日、中文时刻与会议室；不承诺模型级语义理解。空结果可将原文带入手动创建的备注。

整理空白后仅发送前 2000 字，超长文本在界面提示。导入原文默认不保存；设置中的开关仅影响后续确认的导入。此开关不删除用户已保存的数据。离开页面/取消会取消进行中的请求，已确认保存由 ViewModel 完成。API Key 不放入保存实例状态、源代码或日志；原文不写诊断日志。普通 CRUD、提醒、离线规则均不需要 AI 授权或密钥。

## 接口约定与限制

- OpenAI 兼容：`/v1/chat/completions`，Bearer Key，temperature=0，JSON mode；400 时最多重试一次，移除 `response_format`。保留用户自定义代理路径，已含完整接口路径时不重复追加。
- Anthropic：`/v1/messages`，`x-api-key`，`anthropic-version: 2023-06-01`，max_tokens=1024。使用支持该旧式 Key 头的、已绑定 workspace 的 API Key；不包含 OAuth 或额外 workspace 选择流程。
- 无流式、无自动切换服务；禁用重定向和网络层自动重试，避免密钥被带到另一个目标。HTTPS 地址不接受用户名、密码、查询参数或片段。响应上限 256 KiB。
- 401/403、404、429、超时、网络故障和无效响应显示固定错误说明，不显示远端响应正文。模型输出始终是待核对的数据，不执行指令或工具调用。
- 本地日期时间按识别/确认时的系统时区解释；全天保存日历日期，定时待办保存绝对时刻。更换设备时区后的行为沿用基础数据模型。

实现参考：[OpenAI JSON mode](https://developers.openai.com/api/docs/guides/structured-outputs)、[Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[Anthropic authentication](https://platform.claude.com/docs/en/manage-claude/authentication)。

## 验证

MockWebServer 用本地伪密钥覆盖成功、分块传输、400 降级、401/403、404、429、500、超时、非 JSON、围栏 JSON、空内容、取消、超大响应与禁止跨站重定向。解析单测覆盖相对日期、跨年/闰年、过去/非法时间、首行标题与显式降级。普通构建不会发起真实模型请求。

`app/src/test/resources/notices/` 有 **32 组人工编写的合成通知**及期望 JSON，统一参考时间为 **2026-10-27 10:00 Asia/Shanghai**。包含多事项、无事项、无时间、过期日、范围和注入式文本；并非真实用户通知。样例格式校验是自动测试，模型准确率尚未测量。

`NoticeParseEvalTest` 的付费网络测试默认 `@Ignore`。需要真实评测时，在本地私密环境中设置 `LLM_BASE_URL`、`LLM_KEY`、`LLM_MODEL`，显式执行：

```bash
source scripts/android-env.sh
./gradlew :app:evaluateNotices
```

勿把密钥直接写入命令历史或仓库。此显式任务调用同一评测入口，约 32 次请求，输出成功案例数及标题/时间+全天/地点的严格匹配比例；按事项顺序对齐，漏项和额外项都计作不匹配，标题同义改写也会算不匹配，因此指标用于回归而非泛化结论。不打印原始模型返回或密钥。

Room 批量事务/回滚、Keystore 重启实例解密/篡改拒绝、注入 fake LlmClient 的成功确认保存与失败后显式降级 UI 测试已经编写。它们需要设备执行；构建测试 APK 不代表通过。用户已推迟设备测试，当前未运行这些新增仪器测试。分享入口保留文本/设置重定向回归测试也已组装，等待设备执行。
