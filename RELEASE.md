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

## 📋 GitHub Release Notes (v1.0.10)

```markdown
# 🛡️ DASMO Lock v1.0.10 — Intruder Detection Selfie Vault, New App Auto-Lock & Health Engine

### 🏷️ Metadata
- **Version:** `v1.0.10`
- **versionCode:** `11`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** November 2026

---

### 🚀 Highlights & What's New

- **🚨 Intruder Detection & Selfie Capture Vault:**
  - Automated front-camera selfie capture upon failed authentication attempts.
  - Built-in `IntruderLogStore` tracking breach timestamps, targeted app packages, and attempt frequency.
  - Revamped `IntruderVaultScreen` with interactive gallery preview, batch delete, and evidence export.
- **📲 Instant Auto-Lock for Newly Installed Applications:**
  - Added `PackageInstallReceiver` to immediately detect newly installed apps from Play Store or APK sideloads.
  - Configurable `autoLockNewAppsEnabled` setting in Security Settings to proactively protect privacy without manual intervention.
- **🛡️ Dynamic Security Health Engine:**
  - Upgraded real-time security health scoring in `AppGuardHomeScreen` dynamically assessing intruder activity, unprotected sensitive apps, and biometric configurations.
- **🧪 Robust Automated Test Suite:**
  - Expanded unit and Robolectric UI coverage for package installation listeners, intruder selfie store lifecycle, and health score calculations.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.10.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.10/dasmo-lock-v1.0.10.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 20.90 MB | `18F229A8E2E249427034447D839B9B0791630A779264431E4AE4F3DCAC2026BE` |
```

---

## 🚀 Current Production Release: v1.0.10

### Version Summary
- **Tag:** `v1.0.10`
- **Version Code:** `11`
- **Version Name:** `1.0.10`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
