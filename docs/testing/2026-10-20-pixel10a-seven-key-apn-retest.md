# Pixel 10a 初始化后：七键预设与 APN 诊断实机复测

2026-10-20，UTC+08:00，约 15:30–15:50。测试源码为 `52fe11b6b37abe4126193274c59f83fc0139e2b0`。本记录与[首次九键测试](2026-10-20-pixel10a-cp3a-cbn.md)分开保存，不沿用首次测试的手机状态或通话结论。

**本轮结论：七键预设及新增只读 APN 检查通过实机验证；经用户另行授权新增 cbnet 与 ims 后，重新注册得到广电 SA、IMS 注册和可用的 VoNR 能力，小流量蜂窝数据通过。用户本轮不方便配合，普通通话、短信及通话全程制式未测试。没有修改项目代码。**

## 实际环境与开始状态

| 项目 | 本轮实际读取 |
| --- | --- |
| 手机 | Google Pixel 10a / stallion |
| 系统 | Android 17 / API 37 |
| Build ID | CP3A.261005.005 |
| Fingerprint | google/stallion/stallion:17/CP3A.261005.005/16271449:user/release-keys |
| 基带，两槽 | g5400i-260604-260807-B-16035863 |
| 广电 SIM | subId 14，卡槽 0，归属 46015，默认数据卡 |
| 另一活动 eSIM | subId 12，卡槽 1，归属 45403，驻留 46000、漫游 |
| 广电初始无线状态 | WWAN 分组域未注册，应用 IMS 未注册，各语音能力不可用 |
| CarrierConfig 覆盖 | 两卡非持久及持久覆盖均为空 |
| 广电 APN | 指定订阅查询、46015 条件查询和系统设置列表均为空 |
| 开关与首选网络 | 飞行模式 0，Wi-Fi 1，移动数据 1，默认数据卡 14，首选模式 27,27 |

用户说明手机已初始化；实际读取仍发现旧独立包 `4.0.1.d232.a738b379-cp3acbn` 和原应用历史，因此不将其描述为已恢复出厂的全新应用环境。自动应用仍关闭，本包没有持久化 VoLTE 原值备份文件；没有卸载应用或删除任何恢复记录。

原始系统状态、操作证据和日志仅存于 Git 忽略目录 `outputs/device-retest-20261020-1530/raw/`。本记录不含序列号、号码、IMSI、ICCID、EID、配对码或完整基站信息。

## 构建、升级与后端

- 本地 master 开始时干净，包含既有适配和测试提交，fork master 同为 52fe11b。
- 保持 JDK 21、Gradle 9.8.0、AGP 9.4.1、Kotlin 2.4.21、SDK 37、NDK 29.0.14206865、CMake 3.22.1。
- 独立 APK：`outputs/device-retest-20261020-1530/TensorIMS-CP3A-CBN-52fe11b6.apk`。
- 包名 `app.mystery0.ims.tensor.cp3acbn`，名称 TensorIMS CP3A CBN，versionCode 234，versionName `4.0.1.d234.52fe11b6-cp3acbn`，arm64-v8a。
- APK SHA-256：`0848074b6759c67624a2c9347ab4c4f57d4cfa2c8ff86c98cc3c67d49f9b41fe`。
- 拉取旧 APK 后核对开发证书一致，再用 install -r 原位升级成功，保留应用数据。Provider authority 和 Instrumentation 目标均为独立包身份。
- 在新包界面开启有限电脑 ADB 等待，执行新安装路径的原生启动器；内置后端就绪，shell UID 2000，授权已验证，版本 234。真实 SIM、配置和诊断操作成功，权限委托清理有日志确认。

本轮执行：

```text
./gradlew -PtensorimsIndependentDebug=true :app:assembleTensorDebug --stacktrace --console=plain
./gradlew :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest \
  :app:assembleTensorDebug :app:assembleLegacyDebug \
  :app:lintTensorDebug :app:lintLegacyDebug --stacktrace --console=plain
```

独立包构建成功；标准双 flavor 验证 BUILD SUCCESSFUL。两 flavor 各 286 项、0 failure、0 error；两份 lint 均为 0 error、103 warning。默认双包仅构建，没有安装到手机。构建日志保留在同一本地忽略目录。

## 七键预设验证

1. 实时读取确认单张广电卡归属 46015。两项 Wi-Fi 通话实际值原本均为 false；本轮另在所有 SIM 和归属 45403 单卡配置页确认广电按钮父节点 enabled=false，门禁有效。
2. 仅在草稿中将 Wi-Fi 通话切为 true，再点广电预设；实机确认该手工草稿仍为 true，漫游 Wi-Fi 通话保持 false。没有应用这份测试草稿。
3. 通过重新读取及丢弃确认清空临时草稿，核对页面回到已读取状态，再生成纯广电预设。
4. 点选预设后的系统 CarrierConfig 与开始值相同，确认没有提前写入。
5. 通过正常应用流程写入，界面显示“已应用并回读确认”；独立 dumpsys 检查覆盖集合恰好包含下列七键，没有 Wi-Fi 通话目标，也没有写到另一张卡。

| 键 | 实时原值 | 预设回读值 |
| --- | --- | --- |
| carrier_nr_availabilities_int_array | int[] [1] | int[] [1, 2] |
| carrier_volte_available_bool | false | true |
| vonr_enabled_bool | false | true |
| vonr_setting_visibility_bool | true | true |
| editable_enhanced_4g_lte_bool | true | true |
| enhanced_4g_lte_on_by_default_bool | true | true |
| hide_enhanced_4g_lte_bool | false | false |

`carrier_wfc_ims_available_bool` 和 `carrier_default_wfc_ims_roaming_enabled_bool` 从 false 保持 false，未出现在本轮覆盖集合。应用后的版本 2 稀疏历史恰好包含 VOLTE、VONR、FIVE_G_NR、ENHANCED_4G_LTE 四个功能目标，并保留 SIM 身份摘要。未使用历史加载替代实时状态。

实际改变的有效值为 NR 数组、VoLTE 与 VoNR 三项；另四键原本已匹配，仍按明确预设目标写入并回读。应用配置、APN 新增和网络重新注册是不同操作，本轮分别记录。

## 新增 APN 检查验证

| 场景 | 实机结果与证据 |
| --- | --- |
| 打开广电 IMS 面板 | 显示 APN 检查入口，尚未自动查询 |
| APN 列表为空时手动查询 | 完整结果 0 条，显示“本次完整查询未观察到 IMS 候选条目”；与系统订阅 URI、条件查询、设置空列表一致 |
| 系统设置入口 | 正常打开广电对应的空 APN 页；返回应用后旧诊断结果清除，需主动重查 |
| 两条 APN 建立后手动查询 | 完整结果 2 条；显示启用的独立 ims / ims，cbnet 为其他类型；普通及漫游协议均 IPV4V6 |
| 真实 Cursor / Bundle / 权限 | 空结果及非空字段均解析成功，carrier_enabled 正确显示“是”，每次操作清理确认，无读取失败被伪装为空的证据 |
| 非广电 45403 卡 | 实机 IMS 面板不显示广电 APN 检查入口；未对该卡查询或修改 APN |
| 诊断只读 | 数据库变更来自下面另行授权的系统操作；检查本身未创建、更新或删除条目，也未触发重启及网络切换 |

本轮未人为制造混合、通配、禁用、超长、缺列、权限拒绝或 SIM 更换场景；相关分类和边界由既有单元测试覆盖，不描述为已完成对应实机场景。

## 另行授权的 APN 新增与网络验证

原列表为空，没有可按上一轮授权修正的旧条目。先保存空结果、首选查询及精确新增/回退方案，再请求一次扩大授权。用户回复“允许新增两条并继续回测”。新增记录 ID 仅保存在本地，用于必要时仅撤回本轮新增记录；没有做全表 APN 重置。

| 名称 / APN | 类型 | 普通 / 漫游协议 | 网络类型 / 启用 |
| --- | --- | --- | --- |
| CBNET / cbnet | default,ia,supl,xcap | IPV4V6 / IPV4V6 | 0，启用 |
| IMS / ims | ims | IPV4V6 / IPV4V6 | 0，启用 |

两条均为 MCC 460 / MNC 15，edited=1。经系统 TelephonyProvider 创建并回读，未创建错误 IMS 混合条目，也没有操作另一张卡。系统随后选用 cbnet 作为实际首选数据 APN；没有显式修改默认数据卡、首选网络或设置 NR only。

APN 创建后先观察到广电 LTE 与 Internet 数据连接，IMS 数据尚未连接。提前告知短暂断网后，暂时关闭 Wi-Fi，并于 15:43:27–15:43:33 做一次约 6.4 秒的飞行模式重新注册；finally 恢复飞行模式。没有重启手机、重启 IMS、root、刷机、清除电话服务数据或操作 eSIM profile。

15:43:41–15:44:24 六次系统快照均显示广电 Internet、IMS 数据为 CONNECTED，IMS 注册跟踪为 true，MmTel Voice / SMS 能力为 true。当前 WWAN PS 为 NR / HOME、驻留 46015；应用刷新显示 IMS 已注册、VoNR 可用、SA 已注册。LTE VoLTE 能力为不可用，与当前按 NR 技术查询到的语音能力分开记录。

15:45:30–15:45:57，Wi-Fi 关闭时五轮小流量验收：

- 五次系统快照均为广电 NR/SA，另一张卡为 LTE。
- 从实际 cbnet 数据网络确认接口 rmnet1，绑定该接口的 15 个 ping 全部收到。
- 五次 IPv4 HTTP 请求均返回 200，各收到 2814 字节正文，总正文 14,070 字节。仅证明本轮小流量可达，不扩展为长期或全部站点稳定性结论。

本轮没有观察到 NSA。分组域未注册、LTE 无 NR、SA 三种状态先后由系统与应用交叉核对；没有为覆盖测试伪造状态。

## 普通通话、短信与剩余范围

在用户手动测试前已开启连续只读采样；用户明确回复“暂时不方便，留为未测试”，因此结束呼叫采样，没有自动拨号或发送短信。

**本轮普通拨入、拨出、双向声音、短信收发、VoNR 呼叫全程承载、LTE VoLTE、EPS Fallback 均未完成业务验收。** IMS 注册、Voice / SMS 能力和 VoNR 可用不替代这些项目，也不沿用首次测试中用户确认的通话短信结果。

外置 Shizuku、无线配对、legacy 实机、NSA 各状态、未知/权限失败实机场景及设备重启恢复未覆盖。下一步最有价值的是在方便时同步采集一次广电普通拨入/拨出全程的 IMS 和 WWAN RAT，再按需要安排重启恢复验收。

## 最终保留状态

- 广电单卡保留已回读的七键覆盖；另一张卡覆盖仍为空。
- Wi-Fi 通话、漫游 Wi-Fi 通话保持初始 false，不代表已验证其运营商可用性。
- 保留用户授权新增的 cbnet 与 ims 两条；实际首选为 cbnet。
- 默认数据卡 14、首选模式 27,27；飞行模式 0、Wi-Fi 1、移动数据 1，与本轮开始一致。Wi-Fi 已恢复。
- 独立测试包与内置后端保留，自动应用、内置自动启动、闲置自动停止均关闭；既有恢复文件未删除，持久化 VoLTE 未操作。
- 最终应用与系统显示广电 IMS 已注册、SA 已注册、VoNR 能力可用；本轮没有通话短信成功结论。
- 本轮未发现可定位、可复现的项目代码问题，源码保持 52fe11b。交付仅补充本脱敏实测记录及指南验证状态；APK 的源码版本仍为 52fe11b。
