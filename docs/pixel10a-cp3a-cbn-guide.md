# Pixel 10a / CP3A.261005.005 / 中国广电使用说明

## 使用中国广电 5G

中国广电支持已整合到常规 `tensor`、`legacy` 安装包，不需要专用 flavor。它包含七个蜂窝配置键、缺失 APN 自动补齐和实际 NR/IMS 状态检查。

1. 打开 TensorIMS，在“特权模式”中选择并启动后端，等待“就绪”。外置模式需要已授权的 Shizuku；内置模式可使用电脑 ADB、无线 ADB 配对或已有 root。Android 17 无线配对还需要本地网络发现权限。
2. 首页选择**单张中国广电 SIM**，进入“IMS 配置”，等待当前配置读取完成。只有实时归属 PLMN 为 `46015` 的单卡可以使用广电预设；“所有 SIM”及驻留中国网络的境外漫游卡不适用。
3. 点选 **中国广电 5G**。此时仅生成草稿，核对七个蜂窝配置键对应的功能和“应用时检查并补齐 APN”的待办。已有手工草稿继续保留；选择其他预设、加载历史或确认丢弃草稿会撤销广电 APN 待办。
4. 点击 **应用更改**。应用先完整检查广电 APN，复用已有可用条目并补齐缺少的条目，逐条回读；确认后再应用和回读蜂窝配置。即使七个键已经符合目标，也可通过广电预设执行 APN 补齐。
5. 查看 APN 与蜂窝配置各自的结果。两部分都完成才表示组合配置完成；若 APN 已具备而蜂窝配置失败，保留待办再次应用时会先重新检查，避免重复新增。查询失败、已有冲突或结果未知时按提示核对，不会把这些情况当作“没有 APN”。
6. 在系统 SIM 设置中确认需要使用的默认数据卡和包含 5G 的网络模式。应用不主动切换数据卡、首选 APN、网络模式或飞行模式。原本没有首选 APN 时，系统可能自行选中新建的数据条目。
7. 在没有进行中通话时验证蜂窝数据，必要时断开 Wi-Fi，产生少量流量后查看“IMS 状态”中的 NR 与注册信息。APN 配置已具备不等于已经注册或实际呼叫成功；系统尚未重新加载时可由用户另行重新连接网络，等稳定后复核。

广电 APN 补齐只随明确的预设应用执行。应用升级、打开状态页、加载保存历史或后台自动恢复都不会自动新建 APN。用户后来重置 APN 后，可再次选择广电预设并应用。

## 安装与现有测试包

本仓库的发布入口为 [zzround/TensorIMS Releases](https://github.com/zzround/TensorIMS/releases)。正常构建保留两套安装身份，均包含完整广电功能：

| 安装身份 | 用途 |
| --- | --- |
| `app.mystery0.ims.tensor` | 常规 tensor 包 |
| `io.github.vvb2060.ims` | 保留原有旧包身份的 legacy 包 |
| `app.mystery0.ims.tensor.cp3acbn` | 显式选择的独立 Debug 验证包，供已有测试安装继续验证 |

覆盖升级需要签名一致、版本号符合系统要求。不同包名的数据及持久化 VoLTE 备份独立，不自动迁移；不要通过卸载旧应用绕过签名不匹配。若手机当前安装的是 `TensorIMS CP3A CBN`，后续同签名的 `.cp3acbn` 包可继续覆盖验证，无需更换应用身份。

独立 Debug 包使用开发签名，不是上游发布签名；正式 Release 构建仍需完整正式签名配置。广电功能位于共享业务代码中，不依赖独立测试包开关。

## APN 自动补齐的范围

Android 17 AOSP 的[广电 APN 样本](https://android.googlesource.com/device/sample/+/android17-release/etc/apns-full-conf.xml)及本项目初始化后复测使用以下两条参考配置：

| 用途 | 名称 | APN | 类型 | 普通 / 漫游协议 |
| --- | --- | --- | --- | --- |
| 数据 | `CBNET` | `cbnet` | `default,ia,supl,xcap` | `IPV4V6 / IPV4V6` |
| 独立 IMS | `IMS` | `ims` | `ims` | `IPV4V6 / IPV4V6` |

新增条目固定使用 MCC `460`、MNC `15`、启用状态和网络类型掩码 `0`（未指定），不写入用户凭据。数据条目与独立 IMS 分开，避免将首选数据 APN 改成 `ims`。已有适用的数据 APN可以复用，不要求按样本重写其名称或类型。

应用按所选 SIM 查询系统匹配的 APN，并核对固定 `46015` 范围内可见的潜在冲突。Provider 隐藏的删除记录无法通过普通查询排除，若插入因此返回不确定结果，应用会停止并进入恢复核对。Android 的 APN 数据按运营商共享，普通插入不是物理 SIM 独占写入；新增的广电记录也可能适用于同运营商的另一张卡。应用不提供任意 URI、APN 参数或其他运营商模板写入入口。

| 检查结果 | 应用行为 |
| --- | --- |
| 数据和独立 IMS 均可用 | 复用，新增数量为零 |
| 完整查询确认缺少一条或两条，且没有冲突 | 只补齐缺少部分，每次插入后回读 |
| 已有混合 IMS、通配类型、禁用或其他冲突条目 | 提示核对，不编辑或删除现有条目 |
| 权限不足、未知归属、缺列、普通 APN 列表返回空游标引用或超限 | 停止，不能据此新增 |
| 已新增部分后下一步失败 | 显示已确认进度，保留待办；已确认的条目不自动删除 |
| 超时、返回内容不完整、身份变化或清理不明确 | 保留恢复核对状态，不自动重试写入 |

Android Provider 不保证多个 APN 插入组成原子事务，冲突插入也可能发生合并。因此应用逐条检查结果，不把空返回值解释为“一定没有写入”。恢复仅重新读取当前状态并确认权限清理，不重放原写入。实现约束见 [APN 补齐设计](plans/2026-10-20-broadnet-apn-design.md)。

## 七个蜂窝配置键

预设的 CarrierConfig 部分包含 VoLTE、5G NR、VoNR 和高级 4G 通话四个功能目标，映射为以下七个覆盖值，继续使用 SIM 身份摘要核对和逐键回读。

| CarrierConfig 键 | 类型 / 值 | 目的 |
| --- | --- | --- |
| `carrier_nr_availabilities_int_array` | `int[] {1, 2}` | 允许 NSA 和 SA，不强制基带使用某种网络 |
| `carrier_volte_available_bool` | `boolean true` | 开放 VoLTE 平台能力 |
| `vonr_enabled_bool` | `boolean true` | 开放 VoNR 平台能力 |
| `vonr_setting_visibility_bool` | `boolean true` | 请求显示 VoNR 用户选项 |
| `editable_enhanced_4g_lte_bool` | `boolean true` | 允许编辑高级 4G 通话开关 |
| `enhanced_4g_lte_on_by_default_bool` | `boolean true` | 将高级 4G 通话默认值设为开启 |
| `hide_enhanced_4g_lte_bool` | `boolean false` | 请求显示高级 4G 通话开关 |

`Enhanced 4G LTE` 控制高级通话开关，并非 LTE 载波聚合开关；这些键不会覆盖已保存的用户通话偏好。预设保留 LTE 可用性，不设置 NR only，以保留通话回落路径。

Wi-Fi 通话与漫游 Wi-Fi 通话不在这七个键中。其当前值和明确的手工草稿保持原样，无法读取时不补值。旧历史中的显式目标不静默迁移或清除，因此应用前应检查完整草稿。

预设保留未选中的 IMS User Agent、运营商名称、VT、UT、跨卡通话、USSD、5G 图标和信号格数阈值。它不写基带频段、不伪造 SIM 归属、不修改网络准入，也不关闭运营商 provisioning 检查。`46000`、`46013` 不用来匹配广电卡。

## APN 检查与实际网络状态

**首页选择广电单卡 → IMS 状态 → 广电 APN 检查 → 检查 APN**，可独立查看当前 APN。此入口仍只读，打开 IMS 面板不会自动查询 APN。结果显示 APN、类型、启用状态及普通/漫游协议，优先展示需要核对的 IMS 候选项。

“打开系统 APN 设置”供用户检查冲突条目。系统可能忽略选卡参数或隐藏 IMS 条目，编辑前需核对目标 SIM；设置页看不到条目不能证明数据库为空。返回应用后重新检查并刷新 IMS 状态。应用不会自动编辑已有混合条目，也不会替用户清空 APN 列表。

| 观察 | 说明 |
| --- | --- |
| APN 已具备 | 已找到或创建适用记录，尚不能证明系统正在使用或 IMS 已注册 |
| CarrierConfig 允许 SA/NSA | 框架配置允许，尚不能证明接入 5G |
| 用户策略不允许 NR | 检查系统首选网络类型；应用不主动覆盖该偏好 |
| 运营商策略不允许 NR | 记录策略层限制，不能用图标开关证明已解决 |
| SA 已注册 | 当前 WWAN 分组网络在 NR 注册，不保证每时每刻有活跃数据 |
| NSA 已连接 | 系统报告 LTE 锚点上的 NR 连接，空闲时可能释放辅载波 |
| NR 允许但未连接 | 产生少量流量，等网络稳定后再观察 |
| 数据为 5G、通话回 LTE | 可能是 EPS Fallback，需验证实际通话及数据恢复 |
| 未知 / 读取失败 | 未取得对应字段，不应当作开启或关闭 |

实际验收应覆盖移动数据、普通电话拨入拨出、短信、通话前后数据恢复、飞行模式恢复，以及重启并重新启动后端后的状态。不使用紧急电话做功能测试。硬件、频段、基带策略和网络准入仍可能影响结果。

## Pixel 10a 验证依据

目标组合为 **Pixel 10a（stallion）、Android 17 / API 37、CP3A.261005.005、广电归属 `46015`**。沿用上游 SDK 37、本地网络权限及 shell 权限委托兼容实现，不硬编码构建号绕过接口检查。NR 诊断使用系统公开注册技术常量，区分 NR 与跨卡 IMS。

| 记录 / 范围 | 已有证据 |
| --- | --- |
| [首次 a738b379 测试](testing/2026-10-20-pixel10a-cp3a-cbn.md) | 独立 tensor 包、内置电脑 ADB、旧九键应用与恢复；手工修正既有 IMS APN 并重新注册后，用户确认通话短信正常 |
| [52fe11b6 初始化后复测](testing/2026-10-20-pixel10a-seven-key-apn-retest.md) | 七键草稿、Wi-Fi 通话值保留、应用回读、APN 空/非空检查及权限清理通过 |
| 初始化后手工新增 `cbnet` / `ims` 并重新注册 | SA、IMS 注册、语音短信能力位及五轮小流量数据成功；能力位不等于已完成呼叫 |
| 初始化后的普通电话、短信、成功通话全程 RAT | 当轮明确未测试，不能沿用首次结论 |
| 本次产品内自动创建 APN | 本次新实现，不能将此前手工新增当作该代码的实机验收 |
| LTE VoLTE、EPS Fallback、NSA、重启恢复、外置 Shizuku及 legacy 实机 | 上述复测未完成独立验证 |

历史测试记录保留当时的版本与操作范围。广电作为正常产品功能提供，仍应按具体代码版本、设备与业务区分验证结论。

## 恢复与问题回报

已确认成功的蜂窝目标按 SIM 身份保存，自动恢复仍只处理版本 2 的蜂窝配置历史。APN 待办不保存为 Feature，不由后台恢复推断生成。自动恢复开关由用户管理，应用不会因为选用广电预设自动打开。

若需要撤销蜂窝修改，有可靠原值时优先在同一卡明确恢复并回读；“重置运营商配置”会影响该 SIM 的其他 CarrierConfig 覆盖，也会按现有流程处理持久化 VoLTE 原值。请求已发出但删除未确认时，需重新读取，不应当作恢复完成。

**CarrierConfig 重置和卸载应用都不会撤销已创建的 APN。** 若需处理 APN，依据当前记录和系统设置单独核对；不要清空全表或删除其他运营商数据。写入不确定时先完成应用的恢复核对，不反复提交相同操作。

问题回报包括设备详情、后端状态、所选 SIM 归属、APN 与蜂窝应用结果、NR 诊断及操作步骤。可从“应用设置 → 应用日志”导出构建号、设备代号与系统构建指纹。不要公开号码、ICCID、IMSI、EID、配对码或完整基站定位。

## 从源码构建

使用 JDK 21、SDK 37（当前包标识 `platforms;android-37.0`）、NDK `29.0.14206865`、CMake `3.22.1`，保留仓库 Gradle 9.8.0 / AGP 9.4.1 / Kotlin 2.4.21。在完整 Git checkout 中执行：

```bash
./gradlew :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest \
  :app:assembleTensorDebug :app:assembleLegacyDebug \
  :app:lintTensorDebug :app:lintLegacyDebug --stacktrace --console=plain
```

已有独立测试安装需要相同签名的升级包时：

```bash
./gradlew -PtensorimsIndependentDebug=true \
  :app:assembleTensorDebug --stacktrace --console=plain
```

产物位于 `app/build/outputs/apk/tensor/debug/app-tensor-debug.apk`，此选项下包名带 `.cp3acbn`。正常 Release 仍使用两个默认包名和完整正式签名，不回退开发证书。

## 平台参考

- [Google 官方 OTA 列表：Pixel 10a / stallion](https://developers.google.cn/android/ota?hl=en#stallion)：设备与目标构建。
- [Android 17 CarrierConfigManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/android/telephony/CarrierConfigManager.java)：七个蜂窝键。
- [Telephony.Carriers 官方接口](https://developer.android.com/reference/android/provider/Telephony.Carriers)及 [AOSP TelephonyProvider](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/refs/heads/main/src/com/android/providers/telephony/TelephonyProvider.java)：按 SIM 查询、APN 插入及共享数据库行为。
- [Android 17 AOSP APN 样本](https://android.googlesource.com/device/sample/+/android17-release/etc/apns-full-conf.xml)：广电参考字段。
- [IMS 注册技术常量](https://developer.android.com/reference/android/telephony/ims/stub/ImsRegistrationImplBase)：NR 与跨卡注册技术。
