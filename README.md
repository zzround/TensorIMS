# TensorIMS

> **IMS configuration for Google Pixel devices on mainland Chinese carrier networks.**
> Based on Mystery00’s **TensorIMS**, previously **Mystery00/TurboIMS**, with China Broadnet support integrated in this repository. Requires a Google Tensor-powered Pixel device, Android 13+, and privileged access through the backend you explicitly select.
>
> Provides configuration for China Mobile, China Unicom, China Telecom and China Broadnet. Broadnet’s seven-key preset, APN diagnostics and network connectivity have been tested on Pixel 10a / CP3A.261005.005; other devices, firmware and carrier services need their own validation.

[Download latest](https://github.com/zzround/TensorIMS/releases) · [Project website](https://pixel.mystery0.app) · [Chinese guide](README_CN.md) · [Report an issue](https://github.com/zzround/TensorIMS/issues)

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="200" alt="TensorIMS Logo"/>
</p>

<p align="center">
  <strong>Enable VoLTE, VoWiFi, and other IMS features on Google Pixel devices.</strong>
</p>

<p align="center">
    <a href="https://github.com/zzround/TensorIMS/releases"><img src="https://img.shields.io/github/v/release/zzround/TensorIMS" alt="GitHub release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/zzround/TensorIMS" alt="License"></a>
    <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/zzround/TensorIMS"><img src="https://img.shields.io/badge/Obtainium-Import-blue?logo=obtainium&logoColor=white" alt="Obtainium"></a>
</p>

[简体中文](README_CN.md)

## China Broadnet 5G

China Broadnet support is included in both standard application packages. Select **one SIM with home PLMN `46015`**, open **IMS configuration**, choose **China Broadnet 5G**, review the cellular targets and APN task, then tap **Apply changes**. The app first checks and fills missing Broadnet data (`cbnet`) and dedicated IMS APNs, then applies seven configuration keys for VoLTE, VoNR, NSA/SA availability and advanced calling. Existing suitable entries are reused; applying again does not create duplicates.

The app does not explicitly change an existing preferred data APN. When there was no preferred entry, Android may select the newly created `cbnet` itself. Conflicting entries, SIM changes or failed reads stop the operation for review. APN progress and cellular configuration results are shown separately. Current Wi-Fi calling settings, roaming Wi-Fi calling settings and manual drafts are preserved.

The [initialized-device retest](docs/testing/2026-10-20-pixel10a-seven-key-apn-retest.md) on **Pixel 10a (stallion), Android 17, CP3A.261005.005** verified the seven-key write/readback and empty/nonempty APN checks. Manually adding `cbnet` and `ims`, followed by network re-registration, produced SA, IMS registration and five successful data rounds. The new in-app APN creation path still needs its own device confirmation. Calls and SMS were not tested in that round.

See the [Pixel 10a / China Broadnet guide (Chinese)](docs/pixel10a-cp3a-cbn-guide.md) for use, result handling and recovery. Broadnet does not require a separate flavor; the optional `.cp3acbn` debug identity remains available for existing test installations.

## Screenshots

<p align="center">
  <img src="docs/home_en.png" width="260" alt="Home Screen"/>
  <img src="docs/ims_en.png" width="260" alt="IMS Configuration"/>
  <img src="docs/advanced_en.png" width="260" alt="Advanced Tools"/>
</p>

## About

TensorIMS is a tool that allows you to enable or disable IMS features like Voice over LTE (VoLTE), Wi-Fi Calling (VoWiFi), Video Calling (VT), and 5G Voice (VoNR) on Google Pixel phones. External mode uses [Shizuku](https://shizuku.rikka.app/) or a fork with a compatible API; embedded mode requires independent wireless ADB pairing or existing root approval.

## Features

- **Structured Information Architecture**:
    - **Home Screen**: Overview of device and selected backend status, SIM selector, navigation to core modules, and the toggle for "Apply configs when the backend is ready".
    - **IMS Configuration**: Draft mode configuration organized into "Calling", "Network", "Display", and "Advanced Overrides", with one-tap quick presets and manual fine-tuning.
    - **System Network**: Dedicated device-level system network settings, offering Captive Portal probe endpoint configuration with popular presets.
    - **Advanced Tools**: Segregated into "Diagnostics & Maintenance" (IMS status snapshot, restart IMS, application logs) and "Danger Zone" (reset CarrierConfig overrides), alongside Persistent VoLTE management.
- **One-Tap Quick Presets**:
    - **Recommended**: Enables essential 4G/5G calling and network capabilities (VoLTE, VoNR, 5G NR, 5G signal threshold alignment, VT) while keeping VoWiFi disabled.
    - **China 5G**: Full 5G experience for Chinese mainland carriers with VoNR, 5GA/5G+ icon, and 5G signal threshold alignment.
    - **China Broadnet 5G**: For a single SIM with home PLMN `46015`, prepare seven cellular configuration keys and an APN task. Applying checks and creates missing fixed Broadnet APNs, reuses suitable existing entries and verifies the result. Wi-Fi calling, roaming Wi-Fi calling, manual drafts and unselected display settings are preserved.
    - **China LTE**: Pure 4G mode with 5G NR & VoNR explicitly disabled for stable cellular connectivity, battery saving, and thermal reduction.
    - **Enable all**: Enables all available toggleable features at once; name format and USSD choices remain unchanged.
- **Automation & Persistence**:
    - **Start the embedded service when opening the app**: An optional, default-off setting reuses existing wireless ADB pairing credentials for one connection attempt when the app enters the foreground. Wireless debugging and the required permissions must already be available; failures can be handled with the existing manual controls. This does not start the app in the background after a reboot.
    - **Apply configs when the backend is ready**: Automatically restores saved configurations when the device reboots and the selected backend is ready and authorized.
    - **Persistent VoLTE (Experimental)**: Leverages system-level VoIMS opt-in to keep VoLTE active across device reboots without needing Shizuku on every restart.
    - **Configuration Persistence**: Automatically saves configuration history per SIM card or for all SIMs.
- **Customizable IMS Features**:
    - **Carrier Name**: Override the carrier name displayed on your device.
    - **IMS User Agent**: Override the IMS User Agent string.
    - **VoLTE (Voice over LTE)**: Enable high-definition voice calls over 4G.
    - **VoWiFi (Wi-Fi Calling)**: Make calls over Wi-Fi networks, with options for Wi-Fi only mode.
    - **Wi-Fi Calling Name Format**: Choose the carrier name alone, or append “Wi-Fi Calling”, “Wi-Fi”, or “VoWifi”. Text follows the system's resources and language. Enabling VoWiFi leaves the name format unchanged until you choose a format separately.
    - **VT (Video Calling)**: Enable IMS-based video calls.
    - **VoNR (Voice over 5G)**: Enable high-definition voice calls over 5G (Requires Android 14+).
    - **Cross-SIM Calling**: Enable dual-SIM interconnection features.
    - **UT (Supplementary Services)**: Enable call forwarding, call waiting, and other supplementary services over UT.
    - **USSD Connection Method**: Choose CS preferred, IMS preferred (with CS fallback), CS only, or IMS only. IMS USSD requires device and carrier support; this option does not change the preferred network type or force LTE-only mode.
    - **5G NR**: Enable 5G NSA (Non-Standalone) and SA (Standalone) networks.
    - **5G Signal Strength Thresholds**: Option to apply custom 5G signal strength thresholds.
    - **5G+/5GA Icon**: Configure the carrier thresholds used by Android to expose the enhanced 5G icon.
    - **Enhanced 4G LTE (advanced calling)**: Expose the advanced 4G calling switch, make it editable and set its default to on. This is not a carrier aggregation switch and does not override an existing user calling preference.
    - **Hide Enhanced Data Icon**: Optionally hide the LTE+/4G+ data icon; actual LTE+/4G+ availability still depends on the device, carrier, and current network.
    - **Show 4G for LTE**: Display the 4G label for LTE data.
    - **Always Show Cellular Network Type**: Request that the system keep its cellular network type label visible when connected to Wi-Fi or when mobile data is off. Appearance and dual-SIM behavior depend on the system; a 5G label does not confirm an active NR data connection.
- **System Network / Captive Portal**: Read, override, or remove SettingsProvider values for Android's `captive_portal_http_url` and `captive_portal_https_url`, with built-in presets (Google, V2EX, Xiaomi, vivo, Huawei).
- **Logcat Viewer**: View and export application logs for troubleshooting.
- **NR diagnostics**: Inspect CarrierConfig SA/NSA availability, user/carrier NR policy and actual packet registration separately in the IMS status sheet. Device details and exported logs include the build ID and codename; exported logs also include the firmware fingerprint.
- **China Broadnet APN setup and diagnostics**: Applying the Broadnet preset fills missing reference APNs. A separate manual, read-only check and system settings entry in IMS status help inspect the result or review conflicts. The app does not edit or delete existing APNs or trigger network re-registration. Suitable APN entries do not by themselves prove IMS registration or working calls.

> **Captive Portal limitation:** TensorIMS verifies the values written to SettingsProvider, but some Android/NetworkStack builds may prefer resource overlays over those values. A successful write therefore does **not** prove that the active network probe URL changed. Reconnect the network and verify behavior on the target device.

The name format and USSD choices are explicit overrides. “Carrier name only” (format `0`) and “CS preferred” (method `0`) do not restore the carrier's original defaults. Removing these overrides requires the configuration reset, which affects other overrides on the selected SIM too. Recommended and mainland China presets leave these choices and the cellular network type visibility setting unchanged.

## Requirements

- **Supported Devices**: Google Pixel devices with Google Tensor chips.
    - Pixel 6, 6 Pro, 6a
    - Pixel 7, 7 Pro, 7a
    - Pixel 8, 8 Pro, 8a
    - Pixel 9, 9 Pro, 9 Pro XL, 9 Pro Fold, 9a
    - Pixel 10, 10 Pro, 10 Pro XL, 10 Pro Fold, 10a
    - Pixel 11, 11 Pro, 11 Pro XL, 11 Pro Fold
    - Pixel Fold, Pixel Tablet
    - **Note:** Devices with Qualcomm Snapdragons (Pixel 5 and older) are NOT supported.
- Android 13 or higher
- External mode: [Shizuku](https://shizuku.rikka.app/) installed, running and authorized; embedded mode: explicit wireless ADB pairing or existing root approval

## Installation

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/zzround/TensorIMS"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/refs/heads/main/assets/graphics/badge_obtainium.png" alt="Obtainium" height="96"></a>

1. Choose the APK by application ID on the [Releases](https://github.com/zzround/TensorIMS/releases) page: `app.mystery0.ims.tensor` for a separate installation, or `io.github.vvb2060.ims` to keep the old application identity. Both include external and embedded modes.
2. Check the APK signature and version before installation, and preserve existing app data. Do not uninstall the old app to bypass a signature mismatch.
3. Open **Privilege mode** from the device card on the home screen. External mode requires Shizuku to be running and authorized; embedded mode can be started manually through wireless debugging pairing, computer ADB, or existing root access. Once the backend is ready, read the current configuration before making changes.

The new and legacy packages store data separately and do not migrate it automatically. Updating the legacy package requires a matching signature and a higher version code. Before switching packages, disable auto-restore, restore the legacy app's original Persistent VoLTE values, and retain its data. Do not let both packages automatically modify the same SIM at the same time.

Compatibility depends on the device and system version. The device list and historical screenshots do not imply that every feature has passed real-device validation.

## Usage

1. **Check Status**: Ensure the selected backend is running and authorized.
2. **Select SIM**: Choose the single SIM card or "All SIM" to configure on the home screen.
3. **IMS Configuration**: Open **IMS configuration**, pick a quick preset ("Recommended", "China 5G", "China Broadnet 5G", or "China LTE") or adjust switches and choices by category, then tap **Apply changes**. Name format and cellular network type visibility are under **Display**; the USSD method is under **Calling**.
4. **Auto-Restore (Optional)**: Enable "Apply configs when the backend is ready" on the home screen to automatically restore saved cellular configurations after reboots once the selected backend is ready. APN creation is not saved as configuration history or replayed in the background; after resetting APNs, select the Broadnet preset and apply it again.
5. **System Network**: Open **System network** to manage Captive Portal detection URLs with one-tap presets; changes apply globally across the device.
6. **Advanced Tools**: Use the advanced page to inspect real-time IMS capability snapshots, restart IMS if needed, or reset CarrierConfig overrides in the Danger Zone.

> Follow the reset status shown in the app. A status indicating that the reset was requested but remains unconfirmed does not mean restoration has been confirmed successful.
> If the reset request and permission cleanup completed but override removal cannot be independently verified, the backend remains usable. Automatic application is paused, and the affected SIM's previous history is excluded from automatic restoration. Reload the configuration to inspect the result.

## About this Project

This project originated as a fork of [Turbo1123/TurboIMS](https://github.com/Turbo1123/TurboIMS). However, due to various stability issues encountered during usage, the codebase has undergone a complete refactoring. This includes rewriting the core logic for SIM card reading and carrier configuration, as well as redesigning the UI and icons.

The original project was previously released as **Mystery00/TurboIMS** and was renamed **TensorIMS** at [Pixel-Tailor-CN/TensorIMS](https://github.com/Pixel-Tailor-CN/TensorIMS). This repository, [zzround/TensorIMS](https://github.com/zzround/TensorIMS), integrates China Broadnet support while retaining the project name, application identities and attribution.

## Credits

- **[vvb2060/Ims](https://github.com/vvb2060/Ims)**
- **[nullbytepl/CarrierVanityName](https://github.com/nullbytepl/CarrierVanityName)**: Carrier name modification logic is derived from this project.
- **[kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)**
- The app icon is based on an original design from [iconfont](https://www.iconfont.cn/collections/detail?cid=28924), modified for use in this project.

## Disclaimer

This application modifies your device's carrier configuration and, when requested, selected device-level network settings. Use it at your own risk. The developers are not responsible for any damage or loss of functionality.

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

### App Language

Tap the gear icon at the top right of the home screen to open **App settings**. Choose to follow the system language, use English, or use Simplified Chinese. The default follows the system. Changes take effect immediately, are saved by Android, and stay synchronized with the system's per-app language settings.
