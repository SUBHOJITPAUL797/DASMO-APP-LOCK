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

## 📋 GitHub Release Notes (v1.0.5)

```markdown
# 🛡️ DASMO Lock v1.0.5 — Vault Privacy Fix & Direct In-Vault Private Camera

### 🏷️ Metadata
- **Version:** `v1.0.5`
- **versionCode:** `6`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** October 2026

---

### 🚀 Highlights & What's New

- **🔒 Auto-Purge Original Photos/Videos from Gallery:**
  Resolved privacy issue where imported photos and videos previously remained visible in the device's public Gallery or Google Photos. Upon AES-256 encryption into the Secure Vault, DASMO automatically shreds the original file from storage, removes it from the MediaStore index, and triggers a full `MediaScannerConnection` refresh to immediately evict cached gallery thumbnails. Supports Android 11+ Scoped Storage batch deletion request fallback.
- **📷 In-Vault Direct Private Camera (Photos & Videos):**
  Added direct "Take Private Photo" and "Record Private Video" actions inside the Secure Vault FAB menu. Media is captured into an isolated temporary cache, encrypted straight to AES-256 `.enc` vault storage, and the temp cache is wiped immediately. Captured media **never** enters `DCIM/Camera` or public gallery folders.
- **🛡️ Comprehensive Security & Media Restoral:**
  Full backward-compatible support for restoring media back to the device gallery whenever desired, retaining universal master recovery passphrase (`DASMO SUBHOJIT`) and persistent cross-install storage.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.5.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.5/dasmo-lock-v1.0.5.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 19.6 MB | `8543D5FCBC5212D125C79BD7150A1C9E7B2E6F8E3DA7ECA7118A6BD1424C169C` |
```

---

## 🚀 Current Production Release: v1.0.5

### Version Summary
- **Tag:** `v1.0.5`
- **Version Code:** `6`
- **Version Name:** `1.0.5`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
