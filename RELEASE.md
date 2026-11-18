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

## 📋 GitHub Release Notes (v1.0.8)

```markdown
# 🛡️ DASMO Lock v1.0.8 — Advanced Architecture, Security Database & UI State Machine Overhaul

### 🏷️ Metadata
- **Version:** `v1.0.8`
- **versionCode:** `9`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** November 2026

---

### 🚀 Highlights & What's New

- **🗄️ Enhanced Database Architecture & Security Models:**
  - Integrated Room database structure (`AppGuardDatabase`, `AppLockDao`, `LockedAppEntity`) for persistent app lock rules and state caching.
  - Added centralized security models (`SecurityModels.kt`) ensuring type safety across app lock, vault, and disguise subsystems.
- **🔢 Calculator Disguise State Machine:**
  - Upgraded Calculator Disguise screen with dedicated state machine (`CalculatorStateMachine.kt`) for robust vault access without UI flicker.
- **🔐 Lock Screen State Machine & Session Tracking:**
  - Modernized lock activity lifecycle (`LockStateMachine.kt`, `LockSessionTracker.kt`, `AppEntranceLockScreen.kt`) with smoother biometric, pattern, and PIN verification.
- **🖼️ Vault Media Viewers & Storage Improvements:**
  - Dedicated full-screen vault media viewer components (`VaultMediaViewers.kt`, `VaultStorage.kt`) supporting high-resolution image zoom and video playback.
- **⚙️ Dedicated Settings Dashboard:**
  - Integrated comprehensive settings architecture (`SettingsScreen.kt`, `SettingsState.kt`) with tactile haptic components.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.8.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.8/dasmo-lock-v1.0.8.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 20.78 MB | `85bee7fd5d15fbbe48c9e44b4ea3a88f24b22a66de02f076693a2427f9e2b4b8` |
```

---

## 🚀 Current Production Release: v1.0.8

### Version Summary
- **Tag:** `v1.0.8`
- **Version Code:** `9`
- **Version Name:** `1.0.8`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
