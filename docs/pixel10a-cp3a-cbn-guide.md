# Pixel 10a / CP3A.261005.005 / 中国广电使用说明

## 目标与验证边界

本分支基于 TensorIMS 上游 `aa41bc5`，面向以下组合增加中国广电预设、网络诊断及只读 APN 检查：

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

**2026-10-20 已使用源码 `a738b379` 在该手机与真实广电 SIM 上完成部分实测。** 独立 tensor 包的内置电脑 ADB 后端、单卡预设门禁、草稿与写入分离、旧版九键逐项回读及原值恢复均通过，应用与系统共同观察到 SA 注册和小流量联网。七个蜂窝键原本已符合预设，实际变化只有两个 Wi-Fi 通话值从 true 变为 false，随后均已恢复。

应用预设前已有 IMS 注册失败记录，随后拨号也未建立通话。之后发现既有 IMS 类型 APN 指向 `internet`，经用户另外授权修正为 `apn=ims、type=ims` 并重新注册网络，IMS 已注册、VoNR 能力可用，用户确认通话短信正常。最终 CarrierConfig 与测试开始基线相同，保留 APN 两字段修正。APN 修正与重新注册共同构成有效干预，未单独隔离两者作用；不能据此声称旧预设开通了 SA、修复了 IMS，或关闭 Wi-Fi 通话有助于广电注册。详见[脱敏实机测试记录](testing/2026-10-20-pixel10a-cp3a-cbn.md)。

**本轮将广电预设缩减为七个蜂窝键，并新增“广电 APN 检查”；这两项修改尚未在手机上回测。** APN 检查只读，旧版实测中的 APN 修正是另行授权的人工操作，不能当作新检查功能已经通过实测的证据。历史记录仍按当次九键版本保留。

本轮两种 flavor 各 286 项单元测试通过，双包构建及 lint 通过；最终 lint 均为 0 错误、103 条既有告警。详见[实测后修订与构建验证](testing/2026-10-20-apn-followup.md)。

| 验证范围 | 当前结论 |
| --- | --- |
| `a738b379` 独立 tensor 包、内置电脑 ADB 后端、旧九键应用与恢复 | 已实测通过 |
| 广电 SA 注册、同期小流量联网、APN 修正后 IMS 注册 | 已现场观察并交叉核对 |
| 通话、短信 | APN 修正及重新注册后，用户确认正常；未逐项采证所有方向 |
| 本轮七键预设、新增广电 APN 检查 | 待实机回测 |
| 成功通话全程 RAT、LTE VoLTE、EPS Fallback、NSA、重启恢复 | 尚未独立验证 |
| 外置 Shizuku、内置无线配对、legacy 实机 | 本次未覆盖 |

因此保留“实验”标记，表示验证范围有限，不是没有任何实测，也不是已经完成所有设备、网络与通话场景的验收。

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
5. 先核对实时值，再按需要点选广电预设，检查全部待应用内容后点击“应用更改”。预设只增加下表七个蜂窝键对应的草稿，已有手工草稿继续保留；点选时不会立即写入。若显示有项目跳过，应先检查缺失功能；只有完整读回才算相关键已写入。配置本来已允许但 IMS 未注册时，先查看下文 APN 排查，不必反复应用相同开关。
6. 在系统“设置 → 网络和互联网 → SIM”中，确认广电卡是否为本次测试的数据卡，以及首选网络模式是否包含 5G；已正确时保持原设置。如果系统不显示该选项，可先用应用的 NR 诊断确认用户策略是否允许 NR。不要设置 NR only，以保留 LTE 通话回落路径。
7. 需要验证蜂窝数据时，暂时断开 Wi-Fi，产生少量移动数据流量，再打开首页 IMS 状态面板检查 NR 网络诊断，完成后恢复原设置。APN 检查本身不会断网；如另行决定重新注册，应在没有进行中的通话时短暂切换飞行模式，等网络稳定后复核，不能把重新注册请求完成当作 IMS 已注册。

应用保留已有 Android 17 后端路径。若外置 Shizuku 的授权列表无法显示，应先确认后端自身的 Android 17 兼容性；本分支的内置模式可由用户独立选择并配对，应用不会自动切换授权方式。

## 当前广电预设：七个蜂窝配置键

预设仅包含 VoLTE、5G NR、VoNR 和高级 4G 通话四个功能目标，映射为下列七个 Android CarrierConfig 覆盖值。应用继续使用原有 SIM 身份摘要校验和逐键回读机制。

| CarrierConfig 键 | 类型 / 值 | 目的 |
| --- | --- | --- |
| `carrier_nr_availabilities_int_array` | `int[] {1, 2}` | 允许 NSA 和 SA，包含广电要求的 SA；不强制基带使用某种网络 |
| `carrier_volte_available_bool` | `boolean true` | 开放 VoLTE 平台能力 |
| `vonr_enabled_bool` | `boolean true` | 开放 VoNR 平台能力 |
| `vonr_setting_visibility_bool` | `boolean true` | 请求显示 VoNR 用户选项 |
| `editable_enhanced_4g_lte_bool` | `boolean true` | 允许编辑高级 4G 通话开关 |
| `enhanced_4g_lte_on_by_default_bool` | `boolean true` | 将高级 4G 通话默认值设为开启 |
| `hide_enhanced_4g_lte_bool` | `boolean false` | 请求显示高级 4G 通话开关 |

`Enhanced 4G LTE` 的三个键控制高级通话开关，并非 LTE 载波聚合开关。它们也不会覆盖用户已经保存的通话偏好；需要时仍应在系统里开启相应选项。

中国广电白皮书要求 SA、VoLTE、EPS Fallback 和 VoNR。预设开放相应平台能力并保留 LTE 可用性，但不宣称这些开关能完成运营商 IMS 配置或保证通话回落。NSA 数组值的存在同样不证明广电提供 NSA 服务。

预设不再指定 `carrier_wfc_ims_available_bool` 或 `carrier_default_wfc_ims_roaming_enabled_bool`。Wi-Fi 通话与漫游 Wi-Fi 通话原来开启就保留开启，原来关闭就保留关闭，无法读取时不补值；已有明确的手工草稿仍会保留并在用户应用时执行。缺少 Wi-Fi 通话实测不能作为关闭它的依据，此次改动也不意味着已经验证广电 Wi-Fi 通话可用。

预设保留 APN、IMS User Agent、运营商名称、VT、UT、跨卡通话、USSD、5G 图标、信号格数阈值和其他未选中的项目。本分支不写基带频段、不伪造 SIM 归属、不修改网络准入，也不关闭运营商 provisioning 检查。广电与移动共建共享，不代表可把中国移动的 `46000`、`46013` 当作广电卡匹配。

## 数据可用但 IMS 未注册：先检查 APN

“广电 APN 检查”对所选广电 SIM 进行只读检查，为 APN 配置提供排查线索。它不修改或新建 APN、不改变首选数据 APN、不触发 IMS 重启或飞行模式。读取失败或权限不足应理解为无法确认，不能解释为该卡一定没有 IMS APN；找到配置条目也不代表系统已选用它或完成 IMS 注册。

操作入口为：**首页选择广电单卡 → IMS 状态 → 广电 APN 检查 → 检查 APN**。成功读取 IMS 快照并确认该卡归属为 `46015` 后才会显示此区域；打开状态面板不会自动查询 APN。结果优先列出需要核对的 IMS 候选项，并显示 APN、类型、启用状态及普通/漫游协议。可使用“打开系统 APN 设置”继续人工核对，但系统可能忽略选卡参数或隐藏 IMS 条目，编辑前仍需确认目标 SIM。返回后重新检查 APN 并刷新 IMS 状态。

本次实测中，正常上网使用另一条首选 `cbnet` 记录，唯一带 `ims` 类型的非首选记录却是 `apn=internet、type=default,supl,ims`。用户另外授权后，仅将后者的 APN 与类型改为 `ims`、`ims`，保留首选 `cbnet`、协议、网络类型掩码和其他记录。重新注册后，系统才观察到 Internet 与 IMS 数据均连接、IMS 注册以及语音和短信能力可用。具体过程见[实测记录的 APN 专项](testing/2026-10-20-pixel10a-cp3a-cbn.md#第二阶段用户提供-apn-线索后的定位与修正)。

该次核对使用的 [Android 17 AOSP APN 样本](https://android.googlesource.com/device/sample/+/android17-release/etc/apns-full-conf.xml)为 `46015` 分别列出数据与 IMS 条目：

| 用途 | APN | APN 类型 | 普通 / 漫游协议 |
| --- | --- | --- | --- |
| 数据对照样本 | `cbnet` | `default,ia,supl,xcap` | `IPV4V6 / IPV4V6` |
| 独立 IMS 对照样本 | `ims` | `ims` | `IPV4V6 / IPV4V6` |

这是排查对照，不是给所有手机批量重写 APN 的模板。不同设备的厂商数据库、有效条目和系统编辑权限可能不同；应结合所选 SIM、系统实际使用的配置和 IMS 注册状态判断。本次数据 APN 已可用，没有按样本重写其类型，网络类型掩码 `0`（未指定）也已允许 SA，没有改为教程中的固定制式列表。

若发现相似问题，先保留当前 APN 字段与精确恢复依据，再决定是否对已确认的条目做单独修正；不要把首选数据 APN 改成 `ims`，也不要删除其他 APN。字段修正、系统重新加载、网络重新注册和实际通话是不同步骤。本次仅重启 IMS 后仍未注册，随后一次飞行模式切换后才恢复；此过程不是所有设备的固定修复顺序，厂商错误码 `61445` 的具体含义仍未确认。

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

升级应用或点选新七键预设，不会自动撤销旧版已经写入的 Wi-Fi 通话值，也不会删除旧历史中的目标。历史没有记录某项来自预设还是手工选择，因此不会把旧 `false` 擅自迁移为 `true`。已经加载到草稿里的 Wi-Fi 通话目标也会保留；应用前应核对全部草稿，准备开启自动应用前应核对同一卡的保存历史。本次测试的两值已明确恢复到原 `true`，自动应用保持关闭，不需要因为更新应用再次修改手机。

如果应用后异常，先关闭本测试包的“后端就绪后自动应用配置”。有可靠原值记录时，优先在同一卡配置页明确恢复本次修改的项目、应用并回读；不要根据其他人的示例猜测原值。本次旧版测试采用这种方式恢复了两项 Wi-Fi 通话值，没有清空整卡覆盖。

确需移除覆盖时，可在“IMS 配置”页选择同一张卡，使用“重置运营商配置”并阅读影响提示。重置会影响该 SIM 的其他 CarrierConfig 覆盖，并非只撤回本预设；应用会按现有流程处理持久化 VoLTE 原值。若只提示请求完成、未确认删除，先重新读取检查，不要当作恢复已确认。CarrierConfig 重置不撤销另行修改的 APN，APN 恢复应依据该次修正前保留的原字段单独处理。

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
- [Android 17 AOSP APN 配置样本](https://android.googlesource.com/device/sample/+/android17-release/etc/apns-full-conf.xml)：实测时用于核对广电 `46015` 的数据与独立 IMS 条目，不代表所有 Pixel 厂商数据库的实际内容。
- [2026-10-20 Pixel 10a / CP3A / 广电实测记录](testing/2026-10-20-pixel10a-cp3a-cbn.md)：旧九键预设、原值恢复、另行授权的 APN 修正与重新注册结果；本轮七键预设和 APN 检查不在该次测试版本中。
- [外置 Shizuku Android 17 授权列表问题](https://github.com/RikkaApps/Shizuku/issues/2180)：后端问题应与 CarrierConfig 和基带网络问题分开排查。
