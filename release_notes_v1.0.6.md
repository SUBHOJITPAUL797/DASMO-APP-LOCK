# 🛡️ DASMO Lock v1.0.6 — Bulletproof Gallery Auto-Purge & All-Files-Access Integration

### 🏷️ Release Metadata
- **Version:** `v1.0.6`
- **versionCode:** `7`
- **Package Name:** `dasmo.lock.subhojit`
- **Minimum SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 15 (API 36)
- **Build Type:** Signed Release APK

---

### 🚀 Highlights & Resolved Issues

- **🔒 Canonical MediaStore Resolution & Guaranteed Gallery Purge:**
  Completely overhauled the media cleanup engine to resolve arbitrary picker and document URIs into canonical MediaStore entries (`content://media/external/images/media/<id>`) and real filesystem paths. Original media imported from the Gallery is deleted immediately via direct filesystem shredding and ContentResolver, followed by a forced `MediaScannerConnection` purge to evict cached thumbnails from the device gallery.
- **⚡ All Files Access ("Auto-Clean") Support:**
  Added optional one-time permission flow to grant All Files Access (`MANAGE_EXTERNAL_STORAGE`). When granted, DASMO deletes original media from storage and gallery silently and instantly without requiring repeated system popups.
- **🛡️ Android 11+ (API 30+) Native Scoped Storage Deletion Fallback:**
  When All Files Access is not granted, canonical MediaStore URIs are passed cleanly to `MediaStore.createDeleteRequest`, displaying Android's native confirmation dialog ("Allow DASMO to delete this photo?") without crashing or failing.
- **📷 In-Vault Direct Private Camera:**
  Snaps private photos and videos directly into AES-256 encrypted storage via isolated app cache without ever entering `DCIM/Camera` or the public gallery.

---

### 📦 Checksums & Verification
- **File:** `dasmo-lock-v1.0.6.apk`
- **Size:** `19.6 MB`
- **SHA-256:** `83ADE4B2463383DB84418BE1535989D3211F200AC72FF586F1C9E461F2CF1570`
