# 🛡️ DASMO Lock v1.0.5 — Vault Privacy Fix & Direct In-Vault Private Camera

### 🏷️ Release Metadata
- **Version:** `v1.0.5`
- **versionCode:** `6`
- **Package Name:** `dasmo.lock.subhojit`
- **Minimum SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 15 (API 36)
- **Build Type:** Signed Release APK

---

### 🚀 Highlights & Resolved Issues

- **🔒 Auto-Purge Original Photos & Videos from Device Gallery:**
  Fixed critical privacy bug where imported media previously lingered in the user's public Gallery or Google Photos. When media is imported into the Secure Vault, the unencrypted original is immediately shredded from filesystem storage and deleted from MediaStore, followed by a forced `MediaScannerConnection` refresh so gallery apps immediately remove the thumbnail and cache. For Android 11+ (API 30+) Scoped Storage restrictions, system batch delete consent (`MediaStore.createDeleteRequest`) is invoked automatically.
- **📷 In-Vault Direct Private Camera (Photos & Videos):**
  Added dedicated **"Take Private Photo"** and **"Record Private Video"** options directly to the Secure Vault (+) action menu. Media captured using this in-app camera is streamed directly into isolated temporary application cache, encrypted with AES-256 GCM straight into vault `.enc` storage, and the temp cache is shredded immediately. Private media captured this way **never touches public storage or DCIM/Camera folders**, ensuring 0% chance of gallery leakage.
- **🔄 Universal Compatibility & Media Restoral:**
  Full backward-compatible support for restoring media back to the device gallery whenever desired, retaining universal master recovery passphrase (`DASMO SUBHOJIT`) and persistent cross-install storage.

---

### 📦 Checksums & Verification
- **File:** `dasmo-lock-v1.0.5.apk`
- **Size:** `19.6 MB`
- **SHA-256:** `8543D5FCBC5212D125C79BD7150A1C9E7B2E6F8E3DA7ECA7118A6BD1424C169C`
