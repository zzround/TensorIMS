# Pixel 10a / 广电实测后修订与构建验证

日期：2026-10-20。本轮从 fork 的 `1b7eff0405010cb976ca33c21e3f6f888d9c1914` 拉取测试结果并继续修改，接续[实机测试记录](2026-10-20-pixel10a-cp3a-cbn.md)。原记录对应手机运行的源码 `a738b379`，保留原文；本记录中的新功能尚未在手机上回测。

## 根据实测作出的调整

| 实测发现 | 本轮处理 |
| --- | --- |
| 七个蜂窝键开始时已符合广电预设；旧九键预设仅把两项 Wi-Fi 通话值从 true 改成 false，随后恢复 | 广电预设缩减为七个蜂窝键，保留 Wi-Fi 通话当前值、明确手工草稿和旧历史，不将缺少实测当作关闭理由 |
| 可用的数据 APN 是另一条首选 cbnet；唯一含 IMS 类型的非首选条目却为 internet / default,supl,ims | 增加所选广电 SIM 的只读 APN 检查，识别独立、混合、通配及待核对类型，展示原字段并提供系统设置入口 |
| 经另行授权将该条目修正为 ims / ims 并重新注册后，IMS 注册、VoNR 能力和通话短信恢复 | 文档记录有效干预和验证边界，不把恢复归因于七个原本相同的 CarrierConfig 值，也不自动重复修改已恢复的手机 |
| SA 下 VoNR 可用、LTE VoLTE 可用性为 false，但没有成功通话全程 RAT 证据 | 补充注册技术相关说明，不把 LTE 能力快照直接等同于配置关闭，也不把 VoNR 可用性当作真实呼叫承载证明 |

本轮预设仍为四个功能目标（VoLTE、5G NR、VoNR、高级 4G 通话），映射到七个 CarrierConfig 键。升级不会自动把旧历史中的 Wi-Fi 通话 false 改成 true，已有手工草稿也不会被新预设删除。本次手机测试中两项原值已经恢复，自动应用保持关闭。

## 新增 APN 检查的实际范围

入口：**首页选择广电单卡 → IMS 状态 → 广电 APN 检查 → 检查 APN**。先读取 IMS 快照并核对归属 `46015`；普通状态刷新不会自动查询 APN。

- 新增固定 `READ_APN_DIAGNOSTICS` 操作，仅接收有效的单个 subId；通过 `Telephony.Carriers.SIM_APN_URI/<subId>` 让系统匹配 SIM/MVNO。读取前后核对同一活动 SIM 的身份摘要和归属，不退回默认卡、驻留网络或全表查询。
- 只读取 APN、类型、启用状态、协议和漫游协议，不读取用户名、密码或 SIM 匹配凭据；没有新增 APN 写入、删除、首选项修改或飞行模式操作。
- APN 查询所需权限只在这次操作内临时委托，不扩大普通 IMS 读取权限。[AOSP TelephonyProvider](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/refs/heads/main/src/com/android/providers/telephony/TelephonyProvider.java) 对现代目标 SDK 的 APN 读取使用 APN 写权限或运营商权限作为门禁；这不意味着本功能会写 APN，也不代表已验证 CP3A 厂商实现。
- 按 [AOSP ApnSetting](https://android.googlesource.com/platform/frameworks/base/+/master/telephony/java/android/telephony/data/ApnSetting.java) 处理类型：真实 null、空串和 `*` 包含 IMS；逗号分隔后只规范大小写，不 trim；混合和未知类型不能当作独立 IMS。启用字段兼容 Provider 的整数与字符串 `0/1` 返回值。
- 权限拒绝、缺列、查询失败、卡变化、响应不完整或清理失败均返回未知，不据此判断缺少 IMS APN。结果限制为 64 行、每字段 256 字符、总文本 16,384 字符；超限整次失效，不用截断结果作判断。
- “发现已启用的独立 ims / ims 条目”仅说明查到对应候选配置。协议单独显示，系统是否选用、IMS 是否注册以及真实呼叫承载需另行观察。界面优先展示需核对项，最多展开八条，并说明其余数量；评估使用完整结果。

系统 APN 设置可能忽略订阅参数或隐藏 IMS 条目，进入后仍需确认目标 SIM。应用仅打开设置界面，返回后需重新检查 APN 并刷新 IMS 状态。参考配置和具体排查步骤见[使用指南](../pixel10a-cp3a-cbn-guide.md#数据可用但-ims-未注册先检查-apn)。

## 构建与回归结果

使用隔离 worktree 中的既有工具链：JDK `21.0.12.1+1`、Gradle `9.8.0`、SDK `37.0` revision 2、NDK `29.0.14206865`、CMake `3.22.1`。未降级项目依赖，未更改原 checkout 的源码或安装手机应用。

| 验证 | Tensor Debug | Legacy Debug |
| --- | --- | --- |
| 单元测试套件 | 44 | 44 |
| 测试数量 | 286 | 286 |
| failure / error / skipped | 0 / 0 / 0 | 0 / 0 / 0 |
| APK 构建 | 通过 | 通过 |
| 最终 lint error / warning | 0 / 103 | 0 / 103 |

新增九项 APN 模型测试和三项 Wi-Fi 通话保留回归测试，另收紧既有预设测试为精确七键集合；从原每 flavor 274 项增加到 286 项，共 572 次执行。覆盖实测混合条目、候选共存、类型解析、未知启用状态、读取失败与缺失区分、归属限制、大小边界，以及原值/手工草稿/历史保留。

初次执行以下任务，结果 `BUILD SUCCESSFUL in 3m 17s`：

```bash
./gradlew :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest \
  :app:assembleTensorDebug :app:assembleLegacyDebug \
  :app:lintTensorDebug :app:lintLegacyDebug \
  --stacktrace --console=plain --no-daemon --max-workers=4
```

初次 lint 每 flavor 为 104 条告警；与原测试报告比较，唯一新增项是英文未展开条目数量的 `PluralsCandidate`。将英文改为计数标签后，只增量复核双 flavor 的 assemble 和 lint，`BUILD SUCCESSFUL in 46s`，最终回到原有的 103 条告警，没有新增告警。该次仅修改一条英文资源，未修改业务 Kotlin 或测试，未重复单测。

本地证据保存在构建 worktree 的 `outputs/standard-verification.log`、`outputs/standard-verification-summary.json`、`outputs/resource-verification.log` 和 `outputs/resource-verification-summary.json`，不将原始输出提交到仓库。完成资源复核后的 `app/` Git 子树为 `84d9a7d4b70dc669edaf0e63af2b3933c69f6ed4`；随后只补本记录及指南链接。

标准验证构建时 HEAD 仍是测试报告提交 `1b7eff04`，本轮代码以冻结工作树参与构建，因此标准包的版本字符串不是新功能的提交编号。用于后续回测的独立包需基于最终提交重新生成并核对包名、版本与签名；标准包不用于替代已安装的独立测试包。

## 下一轮实机回测重点

1. 使用对应最终提交的独立包，更新后重新启动匹配版本的内置后端，再读取所选广电卡的 IMS/NR 快照。
2. 手动触发 APN 检查，核对已修正的 `ims / ims`、启用状态及协议；与系统实际配置和 IMS 注册分开记录。特别验证 CP3A 上真实的 Cursor、Bundle、临时权限及清理路径。
3. 只预览新广电预设的草稿，确认没有新增 Wi-Fi 通话或漫游 Wi-Fi 通话目标；当前蜂窝值已符合目标时，无需为验证而重复写入。

本轮没有新增手机实测结论。外置 Shizuku、无线配对、legacy 实机、成功通话全程 RAT、LTE VoLTE、EPS Fallback、NSA 和重启恢复仍按历史记录保留为未验证范围。当前手机通话短信已经恢复，后续检查不以重新制造原 APN 故障为前提。
