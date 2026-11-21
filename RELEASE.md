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

## 📋 GitHub Release Notes (v1.0.11)

```markdown
# 🛡️ DASMO Lock v1.0.11 — Covert Intruder Video Recording, Installed Apps Cache & Removal Tracking

### 🏷️ Metadata
- **Version:** `v1.0.11`
- **versionCode:** `12`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** November 2026

---

### 🚀 Highlights & What's New

- **📹 Covert Intruder Video Recording:**
  - Added covert front-camera video recording (`IntruderCameraCapture.kt`) triggered upon repeated failed unlock attempts.
  - Updated `IntruderLogStore` to track and distinguish between photo and video evidence types.
  - Enhanced `IntruderVaultScreen` with seamless in-app video playback, scrubbing controls, and evidence export.
- **⚡ Installed Apps Cache Performance Engine:**
  - Introduced `InstalledAppsCache` providing instantaneous app list loading, background querying, and reduced main-thread load.
- **📦 Comprehensive App Removal & Uninstall Tracking:**
  - Upgraded `PackageInstallReceiver` to actively monitor uninstalled packages and automatically prune outdated lock rules and caches.
- **🚨 Enhanced Alarm Siren & Tamper Defense:**
  - Upgraded `AlarmAlertHelper` siren activation and volume override logic to deter unauthorized device access.
- **🧪 Robust Automated Test Coverage:**
  - Added unit and Robolectric UI tests validating video recording metadata, cache invalidation, and app uninstall listeners.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.11.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.11/dasmo-lock-v1.0.11.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 20.91 MB | `2E54FC6C38C9064B3FFCF34CF17E7A4F60732C66CC688EEEA47B277675FD3B0E` |
```

---

## 🚀 Current Production Release: v1.0.11

### Version Summary
- **Tag:** `v1.0.11`
- **Version Code:** `12`
- **Version Name:** `1.0.11`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
