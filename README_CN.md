# TensorIMS

> **聚焦中国大陆运营商网络下的 Google Pixel IMS 配置，由 Mystery00 独立维护。**
> 原 **Mystery00/TurboIMS**，现更名为 **TensorIMS**。仅支持搭载 Google Tensor 芯片的 Pixel 设备，需 Android 13+，并通过明确选择的后端取得特权。
>
> 主要适配和维护范围为中国移动、中国联通、中国电信网络下的使用场景。中国广电已完成下述 Pixel 10a 组合的部分实测；其他设备与运营商组合仍需针对性验证。不在主要维护范围内，不表示该网络一定无法使用。

[下载最新版](https://github.com/Pixel-Tailor-CN/TensorIMS/releases/latest) · [项目主页](https://pixel.mystery0.app) · [使用说明](#使用) · [问题反馈](https://github.com/Pixel-Tailor-CN/TensorIMS/issues)

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="200" alt="TensorIMS Logo"/>
</p>

<p align="center">
  <strong>在 Google Pixel 设备上开启 VoLTE、VoWiFi 和其他 IMS 功能。</strong>
</p>

<p align="center">
    <a href="https://github.com/Pixel-Tailor-CN/TensorIMS/releases"><img src="https://img.shields.io/github/v/release/Pixel-Tailor-CN/TensorIMS" alt="GitHub release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/Pixel-Tailor-CN/TensorIMS" alt="License"></a>
    <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Pixel-Tailor-CN/TensorIMS"><img src="https://img.shields.io/badge/Obtainium-Import-blue?logo=obtainium&logoColor=white" alt="Obtainium"></a>
</p>

[English](README.md)

## 本分支：Pixel 10a / 中国广电实验适配

目标设备为 **Pixel 10a（stallion）、Android 17、CP3A.261005.005**。本分支在上游现有 Android 17 兼容实现上增加中国广电单卡预设、实际 NR 网络诊断、只读的广电 APN 检查和系统构建标识。

**2026-10-20 已使用源码 `a738b379` 完成部分实机测试。** 独立 tensor 包、内置电脑 ADB 后端、单卡门禁、草稿、九键写入回读及原值恢复通过，观察到 SA 注册与小流量联网。七个蜂窝配置键原本已符合预设，实际只关闭了两个 Wi-Fi 通话值，随后均已恢复。之后经用户另行授权，将既有 IMS APN 手工修正为 `apn=ims、type=ims` 并重新注册网络，用户确认通话短信正常。因此不能把 SA 或语音恢复归功于预设。详见[实机测试记录](docs/testing/2026-10-20-pixel10a-cp3a-cbn.md)。

当前预设保留 Wi-Fi 通话，只准备七个蜂窝配置键。**修订后的七键预设与新增广电 APN 检查尚未在手机上回测。** APN 检查不修改 APN、不触发重新注册。成功通话全程制式、LTE VoLTE、EPS Fallback、重启恢复、外置 Shizuku 和 legacy 实机仍未验证；实验适配不表示已完成全部运营商验收。

完整安装、配置、验证和恢复步骤见 [Pixel 10a / CP3A / 中国广电使用说明](docs/pixel10a-cp3a-cbn-guide.md)。本分支支持生成独立测试包，避免开发签名与已安装正式版冲突。

## 截图

<p align="center">
  <img src="docs/home_zh.png" width="260" alt="首页概览"/>
  <img src="docs/ims_zh.png" width="260" alt="IMS 配置与预设"/>
  <img src="docs/advanced_zh.png" width="260" alt="高级工具"/>
</p>

## 关于

TensorIMS 是一个允许您在 Google Pixel 手机上启用或禁用 VoLTE（高清语音通话）、VoWiFi（Wi-Fi 通话）、VT（视频通话）和 VoNR（5G 语音）等 IMS 功能的工具。外置模式需要 [Shizuku](https://shizuku.rikka.app/zh-CN/) 或兼容其 API 的 fork 版本；内置模式需要独立无线 ADB 配对或已有 root。选择模式本身不代表已经获得权限。

## 功能

- **清晰的分层信息架构**：
    - **首页**：集中展示设备与 Shizuku 状态、SIM 卡选择、功能入口导航，以及“后端就绪后自动应用配置”开关。
    - **IMS 配置**：采用草稿模式设计，按“通话 / 网络 / 显示 / 高级覆盖”清晰分组管理，支持一键快速预设与按需微调。
    - **系统网络**：独立管理设备级系统网络设置，提供 Captive Portal 探测地址配置及国内常用预设快捷填入。
    - **高级工具**：区分“诊断与常规维护”（实时 IMS 状态、重启 IMS、应用日志）与“危险操作”（重置 CarrierConfig 覆盖），并承载持久化 VoLTE 管理。
- **一键快速配置预设**：
    - **推荐配置**：开启 4G/5G 核心通话与网络能力（VoLTE、VoNR、5G NR、5G 信号门限对齐、VT），默认关闭国内卡暂不支持的 VoWiFi。
    - **国内 5G**：一键开启国内三大运营商 5G 完整网络体验，包含 VoNR、5GA/5G+ 图标与 5G 信号门限对齐。
    - **中国广电 5G（实验）**：对归属网络为 `46015` 的单张 SIM 准备 VoLTE、VoNR、NSA/SA 与高级通话的七个 CarrierConfig 键，保留 Wi-Fi 通话、漫游 Wi-Fi 通话的现有设置与手工草稿，以及 5G 图标和信号格数阈值。实际注册和通话仍取决于 APN、设备与网络；应用新预设不会删除旧历史中的 Wi-Fi 通话目标。
    - **国内 LTE**：纯净 4G 模式，显式关闭 5G NR 与 VoNR，专注网络稳定、省电抗发热及长续航。
    - **全部开启**：一键开启全部可用功能开关，保留名称格式与 USSD 连接方式的选择。
- **自动化与持久化**：
    - **打开应用时自动启动内置服务**：默认关闭。启用后，应用进入前台时使用已有无线 ADB 配对凭据尝试连接一次；需要无线调试与必要权限已经可用，失败后可通过现有手动入口处理。设备重启后仍需打开应用，不会在后台开机启动。
    - **Shizuku 就绪后自动应用**：可选择在设备重启且当前选定后端启动授权后自动恢复上次成功应用的配置，无需每次开机手动操作。
    - **持久化 VoLTE（实验性）**：使用系统底层 VoIMS opt-in 接口，启用后正常重启设备无需依赖 Shizuku 即可保持 VoLTE 生效。
    - **配置持久化**：按 SIM 卡（或全部卡）自动保存配置历史。
- **可定制的 IMS 功能**：
    - **运营商名称**：覆盖设备上显示的运营商名称。
    - **IMS User Agent**：自定义 IMS User Agent 字符串。
    - **VoLTE（高清语音通话）**：开启 4G 高清语音通话。
    - **VoWiFi（Wi-Fi 通话）**：通过 Wi-Fi 网络拨打电话。
    - **Wi-Fi 通话名称格式**：可选择仅显示运营商名称，或添加“Wi-Fi 通话”“Wi-Fi”“VoWifi”后缀。文字由系统资源和语言决定；开启 VoWiFi 时不再强制修改名称，需单独选择格式。
    - **漫游 VoWiFi**：可选择漫游时默认启用 VoWiFi。
    - **VT（视频通话）**：开启基于 IMS 的视频通话。
    - **VoNR（5G 语音）**：开启 5G 高清语音通话（需要 Android 14+）。
    - **Cross-SIM Calling（跨卡通话）**：开启双卡互连功能。
    - **UT（补充业务）**：通过 UT 开启呼叫转移、呼叫等待等补充服务。
    - **USSD 连接方式**：可选择 CS 优先、IMS 优先（允许回退 CS）、仅 CS 或仅 IMS。IMS USSD 仍需设备与运营商支持，该选项不会修改首选网络类型或强制 LTE only。
    - **5G NR**：开启 5G NSA（非独立组网）和 SA（独立组网）网络。
    - **5G 信号强度阈值**：可选择是否应用自定义的 5G 信号强度阈值。
    - **5G+/5GA 图标**：配置 Android 显示增强 5G 图标时使用的运营商阈值。
    - **Enhanced 4G LTE（高级通话）**：显示并允许修改高级 4G 通话开关，将其默认值设为开启。这不是 LTE 载波聚合开关，也不覆盖已有的用户通话偏好。
    - **隐藏增强型数据图标**：可选择隐藏 LTE+/4G+ 数据图标；实际 LTE+/4G+ 可用性仍取决于设备、运营商和当前网络。
    - **LTE 显示为 4G**：将 LTE 数据网络显示为 4G 图标。
    - **始终显示蜂窝网络类型**：连接 Wi-Fi 或关闭移动数据时，仍请求系统显示蜂窝网络类型。实际显示及双卡行为由系统决定，显示 5G 不代表当前存在活跃的 NR 数据连接。
- **系统网络 / Captive Portal**：读取、覆盖或移除 Android `captive_portal_http_url` 与 `captive_portal_https_url` 的 SettingsProvider 值，内置 Google 官方、V2EX、小米、vivo、华为等快捷填入预设。
- **Logcat 查看器**：实时查看和一键导出应用日志以排查问题。
- **NR 网络诊断**：在 IMS 状态面板分别检查 SA/NSA 配置、用户与运营商的 NR 允许状态、实际分组网络注册；区分已注册 SA、NSA 连接和仅允许 NR。设备详情及日志提供构建号与设备代号，日志另含系统构建指纹。
- **广电 APN 检查**：对所选广电 SIM 进行只读检查，为“数据可用但 IMS 未注册”提供 APN 排查线索。不修复 APN、不选择数据 APN、不触发网络重新注册；检查结果不能代替 IMS 注册和通话验证。

> **Captive Portal 限制：** TensorIMS 可以确认写入 SettingsProvider 的值，但部分 Android/NetworkStack 版本可能优先使用资源 overlay，因此“写入成功”并不等于当前网络探测一定已经改用新地址。修改后请重新连接网络，并在目标设备上验证实际行为。

名称格式与 USSD 连接方式均为明确的覆盖选项。“仅运营商名称”（格式 `0`）和“CS 优先”（方式 `0`）不代表恢复运营商原始默认值；移除覆盖需要使用配置重置，也会影响所选 SIM 的其他覆盖配置。推荐配置与国内预设保留这些选项及蜂窝网络类型显示设置。

## 要求

- **支持设备**：搭载 Google Tensor 芯片的 Pixel 设备。
    - Pixel 6, 6 Pro, 6a
    - Pixel 7, 7 Pro, 7a
    - Pixel 8, 8 Pro, 8a
    - Pixel 9, 9 Pro, 9 Pro XL, 9 Pro Fold, 9a
    - Pixel 10, 10 Pro, 10 Pro XL, 10 Pro Fold, 10a
    - Pixel 11, 11 Pro, 11 Pro XL, 11 Pro Fold
    - Pixel Fold, Pixel Tablet
    - **注意：** 搭载 Qualcomm Snapdragon 芯片的设备（Pixel 5 及更早机型）**不支持**。
- Android 13 或更高版本
- 外置模式：已安装并运行 [Shizuku](https://shizuku.rikka.app/zh-CN/) 并授权；内置模式：由用户手动开启无线调试并独立配对，或批准已有 root

## 安装

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Pixel-Tailor-CN/TensorIMS"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/refs/heads/main/assets/graphics/badge_obtainium.png" alt="Obtainium" height="96"></a>

1. 从 [Releases](https://github.com/Pixel-Tailor-CN/TensorIMS/releases) 选择包名对应的 APK：新装独立应用选 `app.mystery0.ims.tensor`；保留旧应用身份选 `io.github.vvb2060.ims`。两包功能相同，均可选择外置或内置模式。
2. 安装前核对签名、版本并保护原有数据；不要为解决签名不匹配而先卸载旧版。
3. 从首页设备卡进入“特权模式”：外置模式需要运行 Shizuku 并授权；内置模式可通过无线调试配对、电脑 ADB 或已有 root 手动启动。后端就绪后，先读取当前配置再决定是否修改。

新包与旧包数据独立，不自动迁移；旧包覆盖升级要求签名一致且版本号更高。更换安装包前关闭自动恢复，恢复旧包的持久化 VoLTE 原值并保留其数据。不要同时让两个安装包自动修改同一 SIM。

兼容性取决于具体设备和系统；设备列表与历史截图不代表所有功能均已通过真实设备验证。

## 使用

1. **检查状态**：确保当前选定后端显示就绪且应用已获得权限。
2. **选择 SIM 卡**：在首页选择要配置的单张 SIM 卡或“所有 SIM”。
3. **IMS 配置**：进入“IMS 配置”，可直接使用“推荐配置”、“国内 5G”或“国内 LTE”快捷预设，亦可按分组手动调整开关与选项，点击底部“应用更改”生效。名称格式与蜂窝网络类型显示位于“显示”，USSD 连接方式位于“通话”。
4. **自动恢复（可选）**：在首页开启“后端就绪后自动应用配置”，设备重启且当前选定后端启动授权后将自动恢复已保存的配置。
5. **系统网络**：进入“系统网络”管理 Captive Portal 联网探测地址，可一键选择国内节点预设；该设置作用于全局设备，不随 SIM 切换。
6. **高级工具**：进入“高级工具”可查看当前 SIM 的实时 IMS 能力快照、卡死时重启 IMS，或在“危险操作”区重置配置覆盖。

> 重置结果以界面提示为准；“已请求、尚未确认”不代表已确认恢复成功。
> 重置请求和权限清理已完成，但无法独立核验覆盖删除时，后端仍可继续使用；自动应用会暂停，对应 SIM 的旧历史也不会自动恢复。请重新读取配置检查结果。

## 项目说明

本项目最初 fork 自 [Turbo1123/TurboIMS](https://github.com/Turbo1123/TurboIMS)。在使用原项目的过程中，由于遇到诸多问题，我决定对整个代码库进行彻底重构。这包括重写 SIM 卡读取、运营商配置的核心逻辑，以及重新设计 UI 和图标。

本项目此前以 **Mystery00/TurboIMS** 的名称发布，现以 **TensorIMS** 继续独立维护，后续不计划合并上游代码。更名仅针对本维护版本；GitHub 上保留原有 fork 关系及来源说明。

## 鸣谢

- **[vvb2060/Ims](https://github.com/vvb2060/Ims)**
- **[nullbytepl/CarrierVanityName](https://github.com/nullbytepl/CarrierVanityName)**：运营商名称修改功能的代码参考自此项目。
- **[kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)**
- App 图标源于 [iconfont](https://www.iconfont.cn/collections/detail?cid=28924) 平台的设计，并在此基础上进行了修改以适配本项目。

## 免责声明

本应用会修改设备的运营商配置，并在用户主动操作时修改部分设备级网络设置。请自行承担使用风险。开发者对任何功能损坏或损失概不负责。

## 许可证

本项目使用 Apache License 2.0 许可证。有关详细信息，请参阅 [LICENSE](LICENSE) 文件。

### 应用语言

点击首页右上角齿轮进入“应用设置”，可选择“自动（跟随系统）”、English 或简体中文。默认使用自动，更改立即生效并由 Android 系统保存，与系统应用语言设置同步。
