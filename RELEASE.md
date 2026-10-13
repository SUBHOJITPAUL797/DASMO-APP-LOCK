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

## 📋 GitHub Release Notes (v1.0.6)

```markdown
# 🛡️ DASMO Lock v1.0.6 — Bulletproof Gallery Auto-Purge & All-Files-Access Integration

### 🏷️ Metadata
- **Version:** `v1.0.6`
- **versionCode:** `7`
- **Package:** `dasmo.lock.subhojit`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** October 2026

---

### 🚀 Highlights & What's New

- **🔒 Canonical MediaStore Resolution & Guaranteed Gallery Purge:**
  Completely overhauled the media cleanup engine to resolve arbitrary picker and document URIs into canonical MediaStore entries (`content://media/external/images/media/<id>`) and real filesystem paths. Original media imported from the Gallery is deleted immediately via direct filesystem shredding and ContentResolver, followed by a forced `MediaScannerConnection` purge to evict cached thumbnails from the device gallery.
- **⚡ All Files Access ("Auto-Clean") Support:**
  Added optional one-time permission flow to grant All Files Access (`MANAGE_EXTERNAL_STORAGE`). When granted, DASMO deletes original media from storage and gallery silently and instantly without requiring repeated system popups.
- **🛡️ Android 11+ (API 30+) Native Scoped Storage Deletion Fallback:**
  When All Files Access is not granted, canonical MediaStore URIs are passed cleanly to `MediaStore.createDeleteRequest`, displaying Android's native confirmation dialog ("Allow DASMO to delete this photo?") without crashing or failing.
- **📷 In-Vault Direct Private Camera:**
  Snaps private photos and videos directly into AES-256 encrypted storage via isolated app cache without ever entering `DCIM/Camera` or the public gallery.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-lock-v1.0.6.apk`](https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK/releases/download/v1.0.6/dasmo-lock-v1.0.6.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 19.6 MB | `83ADE4B2463383DB84418BE1535989D3211F200AC72FF586F1C9E461F2CF1570` |
```

---

## 🚀 Current Production Release: v1.0.6

### Version Summary
- **Tag:** `v1.0.6`
- **Version Code:** `7`
- **Version Name:** `1.0.6`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-APP-LOCK`
