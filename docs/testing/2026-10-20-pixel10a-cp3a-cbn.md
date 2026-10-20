# Pixel 10a / CP3A 中国广电实机测试记录

**最终结果：在用户另外授权修正既有 IMS APN 并完成一次重新注册后，广电 IMS 已注册、VoNR 能力可用、SA 已注册，用户确认通话和短信正常。没有修改项目代码。九键预设的两项实际改动已恢复，手机保留 APN 两字段修正。**

测试日期：2026-10-20（UTC+08:00）。本记录仅覆盖本次实际操作；既往构建结果不视作实机验收。原始日志保存在本地 Git 忽略目录 `outputs/device-test-20261020/raw/`，不随本记录提交。

## 测试环境

- 源码基线：`a738b379c5a27df4f95675eb09db7e385808bf0e`，本地及 fork 的 `master` 一致，开始时工作区干净。
- 手机：Google Pixel 10a，`stallion`；Android 17，API 37。
- Build ID：`CP3A.261005.005`。
- Fingerprint：`google/stallion/stallion:17/CP3A.261005.005/16271449:user/release-keys`。
- 基带（两槽）：`g5400i-260604-260807-B-16035863`。
- 构建工具：JDK 21、Gradle 9.8.0、AGP 9.4.1、Kotlin 2.4.21、SDK 37.0、NDK 29.0.14206865、CMake 3.22.1，未降级版本。
- ADB 唯一已授权 USB 设备，所有后续命令绑定该设备；本记录不包含序列号。

## 修改前基线

14:20 左右读取系统当前状态，另保存三次空闲快照，区分实时状态与 dumpsys 中的历史日志。

| 项目 | 广电实体卡 | 另一张活动 eSIM |
| --- | --- | --- |
| subId / 卡槽 | 14 / 0 | 12 / 1 |
| 归属 PLMN | 46015 | 45403 |
| 驻留 PLMN | 46015 | 46000 |
| PS 注册 | HOME / LTE | ROAMING / LTE |
| 默认数据卡 | 是 | 否 |
| 用户 / 运营商允许类型掩码 | 588799 / 588799 | 850943 / 588799 |

Stellar（45412）仅存在于非活动订阅历史，没有激活、停用或删除其 profile。只根据活动订阅的 MCC/MNC 判定广电资格。

开始时未安装 `app.mystery0.ims.tensor`、`io.github.vvb2060.ims`、独立测试包或标准 Shizuku 管理器；未发现其他常见 IMS 修改工具的安装包。存在既有 `shizuku_server` 进程，未停止或接管它。不能据安装包清单证明不存在其他来源的修改。

两卡均已有非持久 CarrierConfig 覆盖，持久覆盖为 null；其中含既有图标、阈值、Wi-Fi 通话、跨卡通话等值，不能归因于本次测试。广电还已有 `sim_country_iso_override_string=687`，本次基线原样保留。完整覆盖集合及系统加载配置已保存在本地原始记录。

广电已允许 SA/NSA、VoLTE、VoNR，但这只代表配置允许，实时 PS 为 LTE。用户网络策略包含 NR，无需切换默认数据卡或修改首选模式。广电持久化 VoLTE 订阅值为 opt-in=1、user=1；本次不操作该独立工具。

14:24:44–14:24:50 的小流量基线：广电接口 `rmnet1` 4 个 ping 全部收到，极小 HTTP 请求返回 204。Wi-Fi 开关开启但未关联；存在 VPN 接口，HTTP 请求可能经过 VPN，因此不将其单独当作直连蜂窝证据；绑定蜂窝接口的 ping 单独记录。未调整 VPN。

## 安装与应用功能

安装包：`outputs/device-test-20261020/TensorIMS-CP3A-CBN-a738b379.apk`，应用名称 `TensorIMS CP3A CBN`，包名 `app.mystery0.ims.tensor.cp3acbn`，versionCode `232`，versionName `4.0.1.d232.a738b379-cp3acbn`，仅 `arm64-v8a`。SHA-256：`6d721ec3baa2709042515a132799b7051a3c91757a978493d8b06f513267dad2`。

目标包原先不存在，使用本次开发签名全新安装成功；没有覆盖、卸载或清空任何旧应用。核对 APK 内全部 8 个 Instrumentation 的 targetPackage、Shizuku / 内置桥接 / 日志 Provider authority 均跟随独立包名。应用启动后选择内置模式，在应用正常界面开启有限电脑等待，执行界面生成的本包原生启动器命令；服务就绪、shell UID 2000、授权已验证，服务版本 232。真实 SIM 读取、配置读取、IMS 读取、配置写入及权限委托清理均成功，未观察到本次应用的 LinkageError、权限委托冲突或未知写入结果。

| 验证项 | 结果 | 依据 / 边界 |
| --- | --- | --- |
| 实际设备与目标固件 | 通过 | ADB getprop 与应用信息匹配 |
| 独立包安装、身份、启动 | 通过 | 安装成功，manifest、安装后版本与运行会话一致 |
| 内置电脑 ADB 后端真实读取与写入 | 通过 | 两张活动 SIM、CarrierConfig、IMS 状态及写后回读完成；清理日志确认 |
| 外置 Shizuku、手机无线配对、legacy 实机 | 未测试 | 未安装外置管理器，未创建无线配对；仅 tensor 独立包安装到手机 |
| 所有 SIM 的广电预设门禁 | 通过 | 实机按钮父节点 enabled=false |
| 45403 漫游 eSIM 的广电门禁 | 通过 | 实机显示归属 45403，按钮父节点 enabled=false |
| 未知归属、46013、身份变化拒绝 | 单元测试覆盖，实机场景未测试 | 没有伪造 SIM 或主动切换 profile |
| 广电 46015 单卡预设 | 通过 | 实时读取归属 46015 后按钮可用 |
| 点选预设仅生成草稿 | 通过 | 系统配置前后相同，界面显示待应用 |
| 九键写入与逐项回读 | 通过 | 正常应用流程和独立系统 dump 核对一致 |
| 其他 SIM 与其他覆盖保留 | 通过 | 两卡完整覆盖集合比较，只有广电两项变化 |
| 两项原值恢复 | 通过 | 正常开关应用后，两卡完整覆盖集合均与基线相等 |

## 九键核对与实际变化

14:30:11 通过项目正常“应用更改”提交，日志明确记录 `Target configuration verified for subId=14` 及 `Stopped owned shell permission delegation`；成功后才保存版本 2 的稀疏历史及 SIM 身份摘要。应用内部按原始类型匹配，独立系统 dump 再次核对如下。

| 键 | 类型 | 修改前 | 预设后 | 最终恢复后 |
| --- | --- | --- | --- | --- |
| carrier_nr_availabilities_int_array | int[] | [1, 2] | [1, 2] | [1, 2] |
| carrier_volte_available_bool | boolean | true | true | true |
| vonr_enabled_bool | boolean | true | true | true |
| vonr_setting_visibility_bool | boolean | true | true | true |
| editable_enhanced_4g_lte_bool | boolean | true | true | true |
| enhanced_4g_lte_on_by_default_bool | boolean | true | true | true |
| hide_enhanced_4g_lte_bool | boolean | false | false | false |
| carrier_wfc_ims_available_bool | boolean | true | false | true |
| carrier_default_wfc_ims_roaming_enabled_bool | boolean | true | false | true |

预设之外的图标、阈值、名称格式、跨卡通话和国家覆盖等现有值未改变；另一张卡完全未改变。未设置 APN、频段、NR only、默认数据卡或首选网络；未执行持久化 VoLTE 工具。新包开始及结束均没有 `persistent_volte_*.json`，原订阅 opt-in=1 / user=1 保持不变；没有删除任何原值备份。

恢复通过同卡配置页将两个 Wi-Fi 通话开关恢复为原 true 并应用完成。开启 Wi-Fi 通话对应的辅助键在原基线中均已为 true，完整比较确认没有额外变化；没有使用全卡 CarrierConfig 重置。

## 第一阶段：九键预设与语音失败基线

### 时间与多次快照

- 修改前约 14:20–14:29：广电 PS 为 HOME / LTE，应用显示“未观察到 NR”；IMS 未注册，VoLTE / VoNR / VoWiFi / VT 均显示不可用。另一张卡为 ROAMING / LTE。
- 约 14:31：应用首次显示广电“SA 已注册”，刷新后仍显示 SA；系统 WWAN PS 记录同时为 HOME、accessNetworkTechnology=NR、RPLMN 46015、`getRilDataRadioTechnology=20(NR_SA)`。该证据来自当前记录，不是状态栏或历史日志。
- 14:31:31–14:32:29：连续 7 次系统快照均为广电 NR/SA；14:32:39 的第 8 次快照为 LTE。另一张活动卡各次均为 LTE。
- 同期 8 轮小流量测试：绑定广电 `rmnet1` 的 24 个 ping 全部收到；HTTP 7 次返回 204，1 次未收到响应。HTTP 可能经过既有 VPN，不能据此宣称全程直连或完全无中断。绑定蜂窝接口的 ping 与系统当前 `subId=14`、WWAN 数据网络互相核对。
- 手动拨号尝试后重新刷新，应用仍可读到 SA；最终恢复原值后，应用和系统快照又为 LTE、未观察到 NR。无线状态可变，不能声称持续驻留 SA，也不能将本次 SA 归因于原本就已开启的 NR 允许值。

| 网络 / 业务项目 | 实机结果 |
| --- | --- |
| SA 注册 | 已观察，应用与系统交叉确认 |
| SA 同期小流量联网 | 已观察，7 次 SA 系统快照，蜂窝 ping 可达；HTTP 有一次无响应 |
| NSA 已连接 | 未观察到，不判通过或不支持 |
| NSA 允许但未连接 / NR 受限 | 未观察到相应实时状态；分类单元测试覆盖 |
| 无 NR | 观察到 LTE 快照及应用“未观察到 NR” |
| 未注册 / 未知分类 | 未主动制造无线断开或权限失败；实机未覆盖，不把缺失值改为 false |
| 用户、运营商、CarrierConfig SA/NSA 策略 | 所选广电卡均显示允许，与原策略和配置对应 |
| IMS / VoLTE / VoNR 能力读取 | 应用成功读取“未注册 / 不可用”；系统当前 IMS 与 Voice 能力为 false，不能据允许配置宣布可用 |
| 广电普通拨出 | 失败：用户反馈拨号面板提示无信号，未建立通话 |
| 广电普通拨入、短信收发 | 未完成测试，不能宣称成功 |
| VoNR、VoLTE 语音及 EPS Fallback | 未验证，因为广电没有建立通话 |
| 飞行模式、重启恢复 | 第一阶段未执行；后续 APN 专项执行一次飞行模式，设备重启始终未测试 |

通话监测还捕获到一次第二卡 OFFHOOK，不能计作广电通话成功；不保存号码到本记录。

### 语音失败的证据与定位边界

系统 `TelephonyDebugService` 与应用一致：广电 `mImsRegistered=false`，`mImsRegistrationOnOff=false`，MmTel Voice=false。系统同时显示 `isVolteEnabledByPlatform=true`、`isVolteProvisionedOnDevice=true`、`isEnhanced4gLteModeSettingEnabledByUser=true`。允许、provisioning 查询结果与真正完成 IMS 注册是不同条件。

系统历史中，测试开始之前的 14:11:22、14:11:28 已有广电 `CODE_REGISTRATION_ERROR`（code=1000、extraCode=61445）。这说明失败在本轮修改之前已存在；额外码的具体厂商/网络含义未知，不能将它当作已解析的 SIP 错误，也不能仅据此断言运营商封锁、基带缺频段或硬件不支持。

本轮正常后端读写和回读没有暴露可定位、可复现的项目代码故障。保留已验证的源码实现、provisioning 检查和统一权限委托机制。

在这一阶段，最有价值的检查是 IMS APN 与厂商注册失败原因；随后按用户提供的 APN 线索完成下述专项。另应追溯测试前已有 `sim_country_iso_override_string=687` 的来源；[AOSP 对该键的说明](https://android.googlesource.com/platform/frameworks/base/+/87c13a545d7d8e5feabb13ca2c396969f0c21369/telephony/java/android/telephony/CarrierConfigManager.java)确认它覆盖 SIM 国家信息，但本轮没有验证它与 IMS 失败的因果关系，也未擅自清除它。

## 第一阶段恢复状态（APN 专项检查前）

- 两张卡的完整 CarrierConfig 覆盖集合恢复到测试开始基线，持久覆盖仍为空；没有全卡重置。
- 默认数据卡仍为 subId 14；首选模式仍为 `27,27`；飞行模式关闭，Wi-Fi 开关开启、移动数据开启，与开始一致；没有执行临时网络开关切换。
- 独立测试包保留，内置电脑 ADB 后端已就绪；自动应用、内置自动启动和闲置自动停止均保持默认关闭。
- 本包保留本轮成功历史及身份摘要，最新一次是基线恢复目标；自动应用没有启用。其他应用与既有 Shizuku 服务未被停止或删除。
- 最终界面保留广电 IMS/NR 快照：LTE、未观察到 NR、IMS 未注册、VoLTE/VoNR 不可用。恢复 CarrierConfig 后的小流量回测：绑定蜂窝接口的 3 个 ping 全收到，HTTP 未收到响应。不能将 ping 可达等同于 HTTP 全程稳定或语音恢复。



## 第二阶段：用户提供 APN 线索后的定位与修正

用户提供的 [APN 资料](https://blog.csdn.net/weixin_51894735/article/details/126977643)提示数据与 IMS 应使用不同 APN。只读查询数据库、系统 DataProfileManager 和设置界面后确认：本机没有独立的广电 `ims` APN，唯一包含 ims 类型的既有记录实际指向 `internet`。系统历史也曾使用这条记录建立 IMS 数据网络，当前 IMS 数据状态为 DISCONNECTED。

从 [Android 17 AOSP APN 原始配置](https://android.googlesource.com/device/sample/+/android17-release/etc/apns-full-conf.xml)核对，46015 的数据条目为 `cbnet` / `default,ia,supl,xcap`，IMS 条目为 `ims` / `ims`，两者普通与漫游协议均为 IPV4V6。AOSP 是对照样本，不能把它等同于这台 Pixel 厂商数据库或运营商现场保证。当前数据 APN 已可用，因此未顺便补改数据类型；网络类型字段 0（设置界面“未指定”）已实际允许 SA，也未改成教程列出的 1～20。

### 经新增授权的设备配置变更

这超出原九键 CarrierConfig 范围，所以先保存原值与精确恢复方案，再向用户明确请求一次授权。用户回复“允许修正这两字段并回测”。仅修正原非首选记录，保留其余 APN 及首选数据卡：

| 字段 | 原值 | 最终值 |
| --- | --- | --- |
| 名称 | 中国广电 | 中国广电 |
| 归属 PLMN | 46015 | 46015 |
| APN | internet | ims |
| 类型 | default,supl,ims | ims |
| 协议 / 漫游协议 | IPV4V6 / IPV4V6 | 不变 |
| 网络类型掩码 | 0 | 不变 |
| 首选数据 APN | cbnet（另一条记录） | 不变 |

原字段、单行更新前后结果及回退方案只存本地忽略目录。通过系统 TelephonyProvider 有条件更新已核对的 46015 指定行，并立即回读。Provider 自动将该行 edited 元数据从 1 更新为 4；未创建新条目、删除其他条目或改变 cbnet 首选。

### 重新注册与结果

- 两字段写入后，DataProfileManager 确认已加载 ims / ims，但 IMS 数据仍断开。
- 14:46:57 经 TensorIMS 正常单卡流程执行一次 IMS 重启，日志确认 subId 14 / slot 0 及权限委托清理；API 请求完成后连续约一分钟仍未注册，没有把“重启请求完成”记作“注册成功”。
- 提前告知短暂断网后，14:48:21 开启飞行模式，14:48:28 恢复，约 7.6 秒；Wi-Fi 原开关由 finally 复核恢复。未重启手机、修改首选网络或操作 eSIM profile。
- 飞行模式中，应用真实显示“分组域未注册”，与系统未注册状态对应；不是将接口失败填成 false。
- 14:48:38–14:49:10 连续四次系统记录：广电 Internet 与 IMS 数据状态均 CONNECTED；IMS 注册跟踪 `mImsRegistrationOnOff=true`，MmTel Voice / SMS 能力为 true。旧字段 `mImsRegistered=false` 仍出现在系统 dump，不能脱离字段来源单独用于否定 IMS；本轮以实际注册跟踪、应用注册 API 与用户通话结果交叉判断。
- 应用刷新显示 IMS 已注册、VoNR 可用、SA 已注册；LTE VoLTE 能力为不可用，VoWiFi / LTE VT 也不可用。能力查询依当前注册技术变化，不能将 VoNR 可用单独当作实际呼叫承载证明。
- 用户随后明确反馈“已经测试，通话短信正常”。记录为用户完成的业务验证；用户未逐项列出拨入/拨出方向、短信条数，未扩大为每个方向都已独立采证。
- 通话后系统实时记录仍为广电 NR/SA、另一卡 LTE；应用继续显示 IMS 已注册和 VoNR 可用。本轮未完整同步采到这次成功通话全程的 RAT，因此**不宣称已独立证明 VoNR 呼叫全程驻留 SA 或 EPS Fallback**；LTE VoLTE 语音仍需单独测试。

这是设备已有 APN 配置差异的修正，项目代码没有改变。APN 修正与随后重新注册共同构成有效干预，未再切回错误 APN 做额外 A/B 测试，因此不把两者各自作用强行分开，也不声称已解码厂商错误 61445。修正后通话短信恢复，保留已获用户授权的两字段配置。

## 构建验证

本轮使用原工具链完成一次默认双 flavor 验证：

```text
./gradlew :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest \
  :app:assembleTensorDebug :app:assembleLegacyDebug \
  :app:lintTensorDebug :app:lintLegacyDebug --stacktrace --console=plain
```

结果 `BUILD SUCCESSFUL`；两 flavor 各 274 项测试、0 failure、0 error；两包构建成功；两份 lint 均 0 error、103 warning。保留既有告警，没有为了测试扩大无关修改。另用 `-PtensorimsIndependentDebug=true :app:assembleTensorDebug` 构建并安装上述独立包。默认双包产物仅用于构建验证，未安装到手机。

代码未修改，交付提交仅包含本脱敏记录与指南验证状态更新；已安装 APK 的源码版本仍是 `a738b379`，不是后续文档提交。

## 手机最终保留状态与剩余事项

- 最终保留广电原非首选记录的 `apn=ims、type=ims` 修正，Provider edited 元数据为 4；其他字段、APN 条目和首选 cbnet 保持原样。
- 两张卡完整 CarrierConfig 覆盖集合经飞行模式后再次比较，仍与测试开始基线相同；原九键预设没有留在手机上造成额外差异。原值备份、订阅 opt-in=1 / user=1 保留。
- 默认移动数据卡仍为 14，首选模式仍为 `27,27`；飞行模式 0、Wi-Fi 1、移动数据 1，与开始相同。只做了上述一次飞行模式重新注册。
- 独立测试包与内置后端保留，自动应用、内置自动启动、闲置自动停止均关闭。没有重启设备、root、刷机、清除电话服务数据或卸载应用。
- 最终应用与系统显示广电 SA、IMS 已注册、VoNR 能力可用；用户确认通话短信正常。通话后的小流量回测另列下方，不宣称长期稳定性。
- NSA 各状态没有现场覆盖；外置 Shizuku、无线配对、legacy 实机、LTE VoLTE 语音、EPS Fallback、成功通话全程 RAT、重启后配置与后端恢复仍未验证。
- 下一步最有价值的是同步采集一次成功通话全程的注册技术与无线 RAT，之后在有必要且用户确认时验证重启恢复；无需继续堆叠 CarrierConfig 开关。

### 通话后的数据收尾验证

14:52 的三轮系统快照均为广电 SA，绑定 `rmnet1` 的 9 个 ping 全部收到；既有 Google HTTP 探针三次未收到响应，不能把该探针当作稳定性通过。随后复核 netcat 的等待选项并使用显式 IPv4 / EOF 后等待请求：Google 端点仍无响应，国内端点返回 200，尚未单独隔离站点、路由或 VPN 的影响。

14:55:28–14:55:29 对国内端点连续三次 HTTP 请求全部为 200，其中一次 GET 实际收到 2814 字节正文，另外两次 HEAD 只取响应头；三次对应系统快照均为广电 NR/SA。由此确认修正后和通话后的真实小流量数据可用，但没有把个别端点不可达掩盖为“全网、长期稳定”。未关闭或修改既有 VPN。
