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

## 📋 GitHub Release Notes (v1.0.2)

```markdown
# 🛡️ DASMO Lock v1.0.2 — Vault Media Restore to Gallery, Setup Wizard & Master Key Recovery

### 🏷️ Metadata
- **Version:** `v1.0.2`
- **versionCode:** `3`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** October 2026

---

### 🚀 Highlights & What's New

- **🖼️ Restore Vault Photos & Videos to Gallery (Full Legacy Compatibility):**
  Users can now easily restore any encrypted photo or video directly back to their device gallery (`Pictures/DASMO_Vault` and `Movies/DASMO_Vault`). Full backward compatibility for all legacy encrypted files with automatic magic-byte inspection, scoped `MediaStore` integration, and instant `MediaScannerConnection` indexing!
- **✅ Multi-Select & Batch Media Management:**
  Select multiple photos or videos at once to batch restore them to your gallery or batch delete them from the vault with real-time file counters!
- **🔐 First-Time Vault Guided Setup Wizard:**
  New installs now feature a guided 3-step setup wizard: Create Master PIN -> Confirm Master PIN -> Set Security Recovery Question & Answer.
- **🔑 Universal Master Key Recovery ("DASMO SUBHOJIT"):**
  Built-in emergency master recovery passphrase (`DASMO SUBHOJIT`) to unlock the vault or any locked app if the user forgets their PIN.
- **🎨 Cryptographic & Lock Animations:**
  Pulsing security shield with rotating neon cyan/purple ring during encryption and restoration, tactile bounce on keypad presses, and padlock morphing to green unlocked upon correct PIN entry.
- **📱 Responsive Settings Dialog Across All Devices:**
  Adaptive, full-height responsive dialog supporting all screen aspect ratios, foldables, tablets, and small screens with native `imePadding()` to prevent keyboard clipping.
- **📦 In-App Package Installer Optimization:**
  Direct permission granting loop for seamless 1-tap APK installation across all Android versions.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-applock-v1.0.2.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.2/dasmo-applock-v1.0.2.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 17.9 MB | `4123b994b93bdcad0e4cf2e093b1b384401aa8b8cbab62cb7ba724c269216c59` |

---

### 🛠️ Detailed Changelog

#### 🖼️ Vault & Media Engine
- Added `VaultRestoreManager` with MediaStore scoped storage (Android 10+) and public external storage fallback (Android 7-9).
- Single-file "Restore to Gallery" button inside preview dialog.
- Multi-selection mode for batch restoring and batch deleting photos and videos.
- Upgraded file import to `ActivityResultContracts.GetMultipleContents()` to pick multiple media items at once.
- Added animated `CryptoProgressModal` with pulsing shield, rotating neon border, file counter, and percentage bar.

#### 🔑 Authentication & Recovery
- Added `MasterKeyManager` supporting case-insensitive, normalized master recovery (`DASMO SUBHOJIT`).
- First-time vault guided setup wizard in `SecureVaultScreen`.
- Emergency recovery dialog with security question verification and master key override in both `LockActivity` and `SecureVaultScreen`.
- Padlock morphing animation (`Lock` -> `LockOpen`) with smooth transition.

#### ⚙️ Settings UI & Device Compatibility
- Replaced cramped fixed-height dialog in `SettingsDialog` with adaptive `DialogProperties(usePlatformDefaultWidth = false)` container.
- Added Security Recovery Question editor in Settings.
- Added `WindowInsets.ime` padding to avoid on-screen keyboard overlap.
```

---

## 🚀 Current Production Release: v1.0.2

### Version Summary
- **Tag:** `v1.0.2`
- **Version Code:** `3`
- **Version Name:** `1.0.2`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
- **APK Checksum (SHA-256):** `4123b994b93bdcad0e4cf2e093b1b384401aa8b8cbab62cb7ba724c269216c59`
