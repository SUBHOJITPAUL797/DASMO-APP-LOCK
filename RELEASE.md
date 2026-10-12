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

## 📋 GitHub Release Notes (v1.0.4)

```markdown
# 🛡️ DASMO Lock v1.0.4 — Maximum Security: Entrance Lock, Uninstall Protection & Persistent Vault

### 🏷️ Metadata
- **Version:** `v1.0.4`
- **versionCode:** `5`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** October 2026

---

### 🚀 Highlights & What's New

- **🔐 Mandatory App Launch Authentication (Entrance Lock):**
  DASMO App Lock now requires authentication immediately upon launching the app! No unauthenticated user can view locked apps, change settings, or access the vault. Re-locks automatically whenever the app is sent to the background (`ON_STOP`).
- **🛡️ Device Administrator Uninstall Protection:**
  Integrated Android `DevicePolicyManager` to disable and grey out the system "Uninstall" button in Android Settings and Launcher. Background service actively intercepts system package installer apps, demanding the Master PIN. Disabling uninstall protection requires Master PIN or Master Key override (`DASMO SUBHOJIT`).
- **💾 Persistent Vault & Configuration Across Uninstallation:**
  Encrypted files are stored in external persistent shared storage (`Documents/.dasmo_secure_vault` with `.nomedia`). Cryptographic engine uses deterministic PBKDF2WithHmacSHA256 AES-256 GCM key derivation, ensuring files remain 100% decryptable even after uninstalling and reinstalling the app!
- **🔄 Auto-Restoring Configuration Backup:**
  Settings, Master PIN, Security Recovery questions, and Decoy PIN are backed up in encrypted `.vault_config.enc`. Re-installing the app restores your PIN and vault contents automatically!
- **🎯 Accurate In-App Update Engine:**
  Fixed update checking logic using live installed `PackageManager` version and strict Semantic Versioning. Eliminates false-positive update prompts and ensures users are only notified when a genuine new version is released.
- **⚡ Hardware-Accelerated Biometrics & Scrambled Keypad:**
  Fast fingerprint/face authentication on app launch and randomized numeric layout to prevent shoulder-surfing.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.4.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.4/dasmo-lock-v1.0.4.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 19.6 MB | `16541570cad180e0503d1a248962d92f287edc4156d561c8ea91d22562fb9f1a` |
```

---

## 🚀 Current Production Release: v1.0.4

### Version Summary
- **Tag:** `v1.0.4`
- **Version Code:** `5`
- **Version Name:** `1.0.4`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
- **APK Checksum (SHA-256):** `16541570cad180e0503d1a248962d92f287edc4156d561c8ea91d22562fb9f1a`
