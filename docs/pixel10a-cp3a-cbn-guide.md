# Pixel 10a / CP3A.261005.005 / 中国广电使用说明

## 目标与验证边界

本分支基于 TensorIMS 上游 `aa41bc5`，面向以下组合增加中国广电预设及网络诊断：

| 项目 | 目标 |
| --- | --- |
| 手机 | Google Pixel 10a |
| 设备代号 | `stallion` |
| Android | 17，API 37 |
| 系统构建号 | `CP3A.261005.005` |
| SIM | 中国广电，归属 PLMN `46015` |
| 特权方式 | 用户选择的外置 Shizuku，或内置无线 ADB / 电脑 ADB / 已有 root |

Google 官方 OTA 列表确认了该设备、代号与构建号。上游已包含 SDK 37、Android 17 本地网络权限及 shell 权限委托接口的兼容实现。本分支沿用这些实现，没有通过硬编码构建号跳过接口检查。

本次也修复了一个实际诊断错误：原 VoNR 能力查询使用注册技术编号 `2`，但 Android 的 `2` 表示跨卡 IMS，NR 应为 `3`。现在使用系统公开常量查询，避免把跨卡 IMS 能力当作 VoNR 结果。此修复改善状态判断，本身不是基带解锁。

**2026-10-20 已在该手机与真实广电 SIM 上完成部分实测。** 独立包的内置电脑 ADB 后端、单卡预设门禁、草稿与写入分离、9 个键逐项回读及原值恢复均通过，应用与系统共同观察到 SA 注册和小流量联网。初始广电 IMS 未注册、拨号提示无信号；随后发现既有 IMS 类型 APN 指向 `internet`，经用户另外授权修正为 `ims / ims` 并短暂重新注册后，IMS 已注册、VoNR 能力可用，用户确认通话短信正常。九键预设的两项实际改动已恢复，最终保留 APN 两字段修正。尚未独立验证成功通话全程的无线制式、LTE VoLTE、EPS Fallback 或重启恢复，不能据此宣称全部适配完成。详见[脱敏实机测试记录](testing/2026-10-20-pixel10a-cp3a-cbn.md)。

## 安装独立测试包

推荐安装本分支生成的独立 `tensor` Debug 包：

```text
应用名称：TensorIMS CP3A CBN
applicationId：app.mystery0.ims.tensor.cp3acbn
架构：arm64-v8a
签名：本次构建的开发签名，不是上游正式签名
```

它与正式版使用不同包名，不会覆盖正式应用及其持久化 VoLTE 原值备份。两者数据独立，也不会自动迁移配置。不要为了安装测试包而卸载旧版。测试前关闭其他 IMS 工具对同一 SIM 的自动应用，避免互相覆盖。

可在手机文件管理器中打开 APK 安装，也可在已授权手机的电脑上使用：

```bash
adb install TensorIMS-Pixel10a-CP3A-CBN-test.apk
```

如使用本分支后续、相同开发签名的包升级，可加 `-r`。默认构建的 `app.mystery0.ims.tensor` 与 `io.github.vvb2060.ims` Debug 包仍沿用原包名，不适合直接覆盖签名不同的正式版。

## 配置步骤

1. 打开测试应用，进入首页设备详情，核对设备代号 `stallion`、构建号 `CP3A.261005.005` 和 Android 17。相同构建号可能用于多种 Pixel，需同时核对设备。
2. 进入“特权模式”，选择并启动一种后端。使用外置 Shizuku 时需完成授权。使用内置无线 ADB 时，在开发者选项开启无线调试，并按应用引导配对；Android 17 还需要本地网络发现权限。后端应显示“就绪”。
3. 首页选择**单张中国广电 SIM**。如果有 Stellar 等境外漫游 eSIM，即使驻留在中国运营商网络，也不应套用广电预设。“所有 SIM”不会启用广电专用按钮。
4. 进入“IMS 配置”，等待当前配置读取完成。只有读到该卡的归属 PLMN 为 `46015` 时，“中国广电 5G（实验）”才可用。未识别时先重读，不要伪造运营商身份。
5. 点选广电预设，检查待应用内容，再点击“应用更改”。预设只改草稿，不会在点选时立即写入。若显示有项目跳过，应先检查缺失功能；只有完整读回才算相关键已写入。
6. 在系统“设置 → 网络和互联网 → SIM”中，将广电卡设为移动数据卡，并选择包含 5G 的首选网络模式。如果系统不显示该选项，可先用应用的 NR 诊断确认用户策略是否允许 NR。不要设置 NR only，以保留 LTE 通话回落路径。
7. 断开 Wi-Fi，产生少量移动数据流量，再打开首页 IMS 状态面板检查 NR 网络诊断。需要重新注册网络时可短暂切换飞行模式，然后等待网络稳定再检查。

应用保留已有 Android 17 后端路径。若外置 Shizuku 的授权列表无法显示，应先确认后端自身的 Android 17 兼容性；本分支的内置模式可由用户独立选择并配对，应用不会自动切换授权方式。

## 广电预设实际写入的配置

这些都是 Android CarrierConfig 覆盖值，应用继续使用原有 SIM 身份摘要校验和逐键回读机制。

| CarrierConfig 键 | 类型 / 值 | 目的 |
| --- | --- | --- |
| `carrier_nr_availabilities_int_array` | `int[] {1, 2}` | 允许 NSA 和 SA，包含广电要求的 SA；不强制基带使用某种网络 |
| `carrier_volte_available_bool` | `boolean true` | 开放 VoLTE 平台能力 |
| `vonr_enabled_bool` | `boolean true` | 开放 VoNR 平台能力 |
| `vonr_setting_visibility_bool` | `boolean true` | 请求显示 VoNR 用户选项 |
| `editable_enhanced_4g_lte_bool` | `boolean true` | 允许编辑高级 4G 通话开关 |
| `enhanced_4g_lte_on_by_default_bool` | `boolean true` | 将高级 4G 通话默认值设为开启 |
| `hide_enhanced_4g_lte_bool` | `boolean false` | 请求显示高级 4G 通话开关 |
| `carrier_wfc_ims_available_bool` | `boolean false` | 本预设关闭未在此组合验证的 Wi-Fi 通话 |
| `carrier_default_wfc_ims_roaming_enabled_bool` | `boolean false` | 关闭默认漫游 Wi-Fi 通话 |

`Enhanced 4G LTE` 的三个键控制高级通话开关，并非 LTE 载波聚合开关。它们也不会覆盖用户已经保存的通话偏好；需要时仍应在系统里开启相应选项。

中国广电白皮书要求 SA、VoLTE、EPS Fallback 和 VoNR。预设开放相应平台能力并保留 LTE 可用性，但不宣称这些开关能完成运营商 IMS 配置或保证通话回落。NSA 数组值的存在同样不证明广电提供 NSA 服务。

预设保留 APN、IMS User Agent、运营商名称、VT、UT、跨卡通话、USSD、5G 图标、信号格数阈值和其他未选中的项目。本分支不写基带频段、不伪造 SIM 归属、不修改网络准入，也不关闭运营商 provisioning 检查。广电与移动共建共享，不代表可把中国移动的 `46000`、`46013` 当作广电卡匹配。

## 怎样判断实际效果

| 观察 | 说明与下一步 |
| --- | --- |
| CarrierConfig 允许 SA/NSA | 只说明框架配置允许，不代表已接入 5G |
| 用户策略不允许 NR | 检查所选 SIM 的系统首选网络类型；本工具不自动覆盖该偏好 |
| 运营商策略不允许 NR | 策略层仍有限制，记录结果；不要用图标开关判断为已解决 |
| SA 已注册 | 当前 WWAN 分组网络在 NR 注册；不保证每时每刻有活跃数据流量 |
| NSA 已连接 | 当前系统报告 LTE 锚点上的 NR 连接；空闲时可能释放 NR 辅载波 |
| NR 允许但未连接 | 有可用性指示，尚无 NR 连接证据；产生流量并等待稳定后再看 |
| 未知 / 读取失败 | 对应字段未取得，不能当作关闭，更不能当作开启 |
| 数据为 5G、通话回 LTE | 可能是 EPS Fallback，应验证 VoLTE 通话是否正常，不能只因回 LTE 判定故障 |
| 5G 图标但未观察到 NR 连接 | 图标和无线连接是不同状态，以实际注册观测为准 |

诊断为当时的快照，权限受限、飞行模式、未加载的配置或网络切换都可能产生未知值。用户与运营商策略位也不是所有限制的全集；电源策略、无线硬件、区域频段、基带运营商策略和网络准入仍可能限制 5G。

实际验收建议依次覆盖：移动数据、普通电话拨入拨出、短信、通话前后数据恢复、飞行模式恢复，以及重启并重新启动后端后的配置恢复。不拨打紧急电话进行测试。

## 恢复与回报问题

如果应用后异常，先关闭本测试包的“后端就绪后自动应用配置”。在“IMS 配置”页选择同一张卡，使用“重置运营商配置”，阅读影响提示后操作。重置会影响该 SIM 的其他 CarrierConfig 覆盖，并非只撤回本预设；应用会按现有流程处理持久化 VoLTE 原值。若只提示请求完成、未确认删除，先重新读取检查，不要当作恢复已确认。

卸载应用不能替代配置恢复；尤其不要在恢复持久化 VoLTE 原值前删除保存备份的旧应用。

问题回报至少包括：设备详情、当前后端状态、所选 SIM 的归属 PLMN、应用配置结果、NR 诊断内容和大致测试步骤。可从“应用设置 → 应用日志”导出，导出文件会带构建号、代号与系统构建指纹。不要公开号码、ICCID、IMSI、EID、配对码或完整基站定位信息。

## 从源码构建

使用 JDK 21、SDK 37（当前 SDK 包标识为 `platforms;android-37.0`）、NDK `29.0.14206865`、CMake `3.22.1`，并保留仓库的 Gradle 9.8.0 / AGP 9.4.1 / Kotlin 2.4.21。

在完整 Git checkout 中执行默认双包验证：

```bash
./gradlew :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest \
  :app:assembleTensorDebug :app:assembleLegacyDebug \
  :app:lintTensorDebug :app:lintLegacyDebug --stacktrace --console=plain
```

生成独立测试包：

```bash
./gradlew -PtensorimsIndependentDebug=true \
  :app:assembleTensorDebug --stacktrace --console=plain
```

产物仍位于 `app/build/outputs/apk/tensor/debug/app-tensor-debug.apk`，但实际包名带 `.cp3acbn`。版本号由 Git 历史生成；源码 ZIP 如果没有 Git 元数据，应先在干净 checkout 中应用交付补丁，再构建。不要将开发签名当作上游发布签名。Release 构建仍要求提供完整正式签名配置，不会回退到开发证书。

## 依据

- [Google 官方 OTA 列表：Pixel 10a / stallion](https://developers.google.cn/android/ota?hl=en#stallion)：目标构建 `CP3A.261005.005`，Android 17，2026 年 10 月。
- [Google Pixel 2026 年 10 月更新公告](https://support.google.com/pixelphone/thread/471669543/google-pixel-update-october-2026?hl=en-GB)。
- [Android 17 CarrierConfigManager 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/telephony/java/android/telephony/CarrierConfigManager.java)：NR 可用性数组、VoNR 与高级通话键的含义。
- [Android IMS 注册技术常量](https://developer.android.com/reference/android/telephony/ims/stub/ImsRegistrationImplBase)：`REGISTRATION_TECH_NR=3`、`REGISTRATION_TECH_CROSS_SIM=2`。
- [Android 本地网络权限说明](https://developer.android.com/privacy-and-security/local-network-permission)：Android 17 的无线本地发现权限要求。
- [中国广电《5G 手机产品白皮书（2023）》公开镜像](https://aimg8.dlssyht.cn/u/551001/ueditor/file/276/551001/1672710945816354.pdf)，正文第 5–6 页：`46015` 归属识别、SA、VoLTE、EPS Fallback、VoNR。此文件用于说明配置设计依据，不代表已取得 2026 年网络准入保证。
- [外置 Shizuku Android 17 授权列表问题](https://github.com/RikkaApps/Shizuku/issues/2180)：后端问题应与 CarrierConfig 和基带网络问题分开排查。
