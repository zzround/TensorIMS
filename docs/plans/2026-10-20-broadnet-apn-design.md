# 中国广电 APN 补齐与产品整合设计

## 需求与依据

用户在初始化后的 Pixel 10a / CP3A.261005.005 上完成七键预设与 APN 只读检查复测，确认主要功能正常，唯一剩余问题是缺失的 APN 仍要手工创建。此次明确要求补齐自动创建并纳入正常产品流程，取代此前仅只读诊断的功能范围。

[初始化后实机记录](../testing/2026-10-20-pixel10a-seven-key-apn-retest.md)确认系统广电 APN 为空；另行新增 CBNET/cbnet 与 IMS/ims 后重新注册，观察到 SA、IMS 及数据连接恢复。该操作是固定模板的依据，不等于本次新写入代码已经实机通过。

平台依据：

- [AOSP Android 17 APN 样本](https://android.googlesource.com/device/sample/+/android17-release/etc/apns-full-conf.xml)：46015 的独立数据与 IMS 配置。
- [Telephony.Carriers](https://developer.android.com/reference/android/provider/Telephony.Carriers)：SIM_APN_URI 等接口。
- [AOSP TelephonyProvider](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/refs/heads/main/src/com/android/providers/telephony/TelephonyProvider.java)：SIM 匹配查询与普通 carriers 插入的区别，冲突插入可能合并已有条目。
- [ContentProvider 默认 applyBatch](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/ContentProvider.java)：逐项执行，不提供通用事务回滚保证。当前参考 TelephonyProvider 没有独立 applyBatch 事务重载。

## 正常用户流程

首页选择单张广电 SIM，在 IMS 配置中选择“中国广电 5G”，生成蜂窝草稿与身份绑定的 APN 待办；点击既有“应用更改”后执行。预设按钮本身没有系统写入副作用，也不自动启用后台恢复。

APN 待办独立于 Feature，并参与 hasPendingChanges、底部应用按钮、重读丢弃确认。普通开关编辑保留可见 APN 待办；换其他预设、加载历史、确认丢弃草稿或目标身份/归属改变时撤销。相同目标临时读取失败保留待办但禁止写入。

操作在 ConfigurationOperations 的串行协调边界内依次执行：

1. 派发前验证编辑器仍对应当前单卡、身份摘要、后端和 generation。
2. 固定 APN 补齐操作完成完整检查、必要写入及回读。
3. APN 阶段确认完成后，再执行原有 CarrierConfig 七键目标并回读。
4. 只保存已确认的蜂窝目标；APN 意图不进入 sim_config_v2。
5. 各阶段结果分别展示。APN 成功、蜂窝失败保留待办；下次显式应用先重新检查，避免重复创建。配置已成功但历史保存失败显示独立提示，不能伪装系统写入未发生。

每个阶段在 Runtime 真正派发前再次核对 canStart。撤销只阻止尚未派发的操作，已发出操作继续按原恢复机制完成清理，迟到结果不覆盖新卡页面。

## 固定接口与写入范围

两个固定操作分别对应 BroadnetApnProvisioner 与 BroadnetApnStateReader：

- ENSURE_BROADNET_APNS：显式应用时确保条目具备。
- READ_BROADNET_APN_STATE：恢复状态时仅重新读取，不插入。

参数只有有效 subId 与预期 SIM 身份摘要，不接收 action、任意 URI、SQL、ContentValues 或运营商参数。两入口均核对活动单卡归属 46015；写入口另沿用 Runtime 捕获的 bridge 身份核对。只有 APN 操作委托 WRITE_APN_SETTINGS，普通 IMS 读取权限保持既有范围。

新增字段固定：

| 字段 | 数据 | IMS |
| --- | --- | --- |
| name / apn | CBNET / cbnet | IMS / ims |
| numeric / mcc / mnc | 46015 / 460 / 15 | 46015 / 460 / 15 |
| type | default,ia,supl,xcap | ims |
| protocol / roaming_protocol | IPV4V6 / IPV4V6 | IPV4V6 / IPV4V6 |
| carrier_enabled | 1 | 1 |
| network_type_bitmask | 0 | 0 |
| edited | USER_EDITED | USER_EDITED |

不主动编辑或删除已有条目，不写首选 APN，不更改默认数据卡、首选网络类型、基带频段或飞行模式。无首选项时允许系统自然选择新数据条目；已有首选项发生意外变化时停止后续写入并提示核对，不用再次写首选项强行覆盖外部操作。

## 完整读取、复用与冲突

每次补齐先读取系统按 subId 匹配的 APN 列表，再读取固定 46015 范围供冲突核对，并读取该订阅的首选项和分组。任一读取权限失败、缺列、类型异常、超限或身份改变，都不能推断缺少 APN。普通列表查询的 null 游标为失败；仅 preferapnset 专属接口按 AOSP 的明确约定将 null 解为默认分组 0，且与其他成功查询交叉核对，非 null 空游标不适用此规则。

普通 APN 插入进入按运营商共享的数据表，不具备物理 SIM 独占性；同运营商其他 SIM 可能看到新增模板。SIM 匹配列表用于判断实际适用性，共享范围用于排除可见的已有行冲突。Provider 会隐藏删除标记，查询无法证明所有底层记录均不存在；不绕过该过滤，插入触发隐藏冲突时按未知结果处理。遇到不支持的 MVNO/管理配置或 APN 分组应明确返回待核对，不能以缺项为由绕过匹配。

复用现有可用数据条目时不要求照模板重写 APN 名称或类型。独立 IMS 需要适用、启用、APN 为 ims 且类型为 ims。混合 IMS、ALL、禁用或无法明确解释的相关记录进入冲突提示；不会把“出现 ims 字样”等同于正确配置。

真正缺少的固定条目逐条插入，前后复核身份、旧行和首选项，确认新行 ID、字段及在目标列表的适用性。重复应用重新读取当前状态，已经具备时返回复用数量，不重新插入。

## 非原子写入与恢复

Provider 多次插入不是原子事务。返回 null 也不保证数据库没变，不能用异常后立即重试实现“补齐”。一条成功后第二条失败可以留下已确认条目，不自动删除，结果必须反映已确认进度。

操作在派发前保存既有 OperationJournal。未知写入、超时、清理失败或异常回读保持恢复态，不开放盲目并发重试。

恢复仅运行 READ_BROADNET_APN_STATE。结果区分：

- COMPLETE：当前已经具备两种适用 APN。
- CONFLICT：当前读取完整，但存在冲突。
- FAILED：可明确描述的未完成状态；是否完整读取得由 readComplete 独立表达。
- UNKNOWN：当前执行或返回内容不足以确认。

readComplete 仅在身份、两种列表和首选项全部完成核对时成立。Runtime 确认完整当前观察及权限清理后才可解除可核对的恢复态，继续保持自动应用暂停；这不代表原写入成功。缺失或冲突通过界面显示，用户可以复核后显式重新应用。读取失败、SIM 不匹配或清理未知时保留记录。

APN 写入不参与自动恢复，也不因加载旧 Feature 历史重新授权。CarrierConfig 重置不删除 APN，应用卸载也不是 APN 撤销方式。

## 产品与验证

共享业务代码同时用于 tensor、legacy、debug、release。默认 applicationId 和正式签名约束保留；.cp3acbn 仅作为已有测试安装的可选开发身份，不是广电功能门禁。

验证重点包括完整空库、缺一条、重复应用、已存在其他有效数据 APN、混合/禁用/通配冲突、SIM 变化、读取失败、部分插入与未知返回，以及 APN 待办的刷新和历史行为。执行双 flavor 单元测试、构建和 lint；新写入路径的真实 Provider 行为仍需按该版本单独实机核验，不挪用旧只读版本或手工新增的验收结论。
