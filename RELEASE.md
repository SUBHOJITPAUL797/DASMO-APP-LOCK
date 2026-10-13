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

## 📋 GitHub Release Notes (v1.0.7)

```markdown
# 🛡️ DASMO Lock v1.0.7 — Seamless Gallery & File Manager Auto-Purge & UX Overhaul

### 🏷️ Metadata
- **Version:** `v1.0.7`
- **versionCode:** `8`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** October 2026

---

### 🚀 Highlights & What's New

- **✨ Seamless Gallery & File Manager Auto-Purge:**
  Importing media from your gallery or file manager now automatically and smoothly removes the unencrypted originals from device storage (`DCIM/Camera`, `Pictures`, `Download`, etc.) and instantly flushes Android's `MediaScannerConnection` cache so photos disappear immediately from Gallery and Google Photos.
- **⚡ Intuitive "Auto-Clean" Permission UX:**
  Fixed premature picker launch race conditions. A one-tap "Enable" banner in the vault guides users cleanly to grant "All Files Access" (`MANAGE_EXTERNAL_STORAGE`). Once enabled, media cleanup happens instantly without repeated OS popups.
- **🔍 Exhaustive On-Device Disk Search Engine:**
  Added recursive, fast targeted disk scanning (`findFileOnDevice`) across camera, screenshots, downloads, and social media directories, matching by filename, metadata, and exact byte size.
- **🛡️ Foolproof MediaStore Deletion & Scoped Storage Fallback:**
  Automatically requests runtime media permissions (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`) on Android 13+ to resolve canonical MediaStore URIs reliably. If All Files Access is not enabled, Android's native confirmation dialog (`createDeleteRequest`) triggers cleanly without silent failures.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.7.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.7/dasmo-lock-v1.0.7.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 19.7 MB | `808504B4A96F0B2E984F536A2C6403B261AFB3A6C6A1D474B6FD2021D22ADBE0` |
```

---

## 🚀 Current Production Release: v1.0.7

### Version Summary
- **Tag:** `v1.0.7`
- **Version Code:** `8`
- **Version Name:** `1.0.7`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
