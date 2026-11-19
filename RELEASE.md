# 🛡️ DASMO LOCK — Production Release & In-App Update Specification

[![GitHub Release](https://img.shields.io/github/v/release/SUBHOJITPAUL797/DASMO-APP-LOCK?style=for-the-badge&color=2563EB)](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases)
[![Build Status](https://img.shields.io/badge/Build-Passing-16A34A?style=for-the-badge&logo=android)](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK)
[![Android Min SDK](https://img.shields.io/badge/Min_SDK-24_(Android_7.0)-blue?style=for-the-badge&logo=android)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-36_(Android_15)-indigo?style=for-the-badge&logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Proprietary-orange?style=for-the-badge)](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK)

> **Repository:** [`SUBHOJITPAUL797/DASMO-APP-LOCK`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK)  
> **Application ID:** `dasmo.lock.subhojit`  
> **API Release Manifest:** `https://api.github.com/repos/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/latest`  
> **Distribution Model:** Direct In-App OTA Update via GitHub Releases Engine + Android `FileProvider`  

---

## 📋 GitHub Release Notes (v1.0.9)

```markdown
# 🛡️ DASMO Lock v1.0.9 — Multi-Method Authentication, Reinstall Persistence & Vault Integrity Overhaul

### 🏷️ Metadata
- **Version:** `v1.0.9`
- **versionCode:** `10`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** November 2026

---

### 🚀 Highlights & What's New

- **🔐 Multi-Method Authentication (Pattern, Password, PIN):**
  - Added full support for flexible authentication types including custom dot patterns, text passwords, and numeric PINs in security settings and lock screens.
  - Interactive tactile `PatternLockView` with smooth gestures, dynamic animations, and real-time validation.
- **🔄 Re-install & Update Persistence:**
  - Enhanced Room persistence layer in `AppLockDao` so locked application configurations survive app updates and re-installation.
  - Added `MY_PACKAGE_REPLACED` intent receiver in `BootReceiver` ensuring immediate service startup and security enforcement right after an OTA update.
- **🖼️ Vault Media Cleaning & Gallery De-indexing:**
  - Upgraded media scanner and gallery cleaner logic in `VaultMediaCleaner` to promptly purge encrypted media files from device gallery and external media stores.
- **⚙️ Redesigned Settings & Update Tabs:**
  - Modernized settings architecture with dedicated tabs (`SettingsTabContents.kt`, `SettingsUpdatesTab.kt`) for granular app protection and in-app update management.
- **🧪 Comprehensive Test Coverage:**
  - Expanded unit and Robolectric UI test suites covering multi-method security workflows, biometric fallbacks, and encryption models.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.9.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.9/dasmo-lock-v1.0.9.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 20.87 MB | `0A12332193435C5DE159FFCE8A217A498C5A1612E7E2D520CA8839B26F1D6DA9` |
```

---

## 🚀 Current Production Release: v1.0.9

### Version Summary
- **Tag:** `v1.0.9`
- **Version Code:** `10`
- **Version Name:** `1.0.9`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
