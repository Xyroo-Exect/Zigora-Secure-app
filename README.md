# Zigora-Secure — Android App (Shizuku Edition)

App native Android yang menjalankan dashboard Zigora-Secure (WebView) dengan akses shell lewat **Shizuku**, bukan Magisk module.

## Cara Build (via GitHub Actions, tanpa PC)

1. Upload seluruh isi folder ini ke repo GitHub baru (lewat GitHub app di HP, atau `git` via Termux).
2. Push ke branch `main` — GitHub Actions otomatis jalan (lihat `.github/workflows/build.yml`).
3. Buka tab **Actions** di repo → tunggu workflow selesai (~3-5 menit).
4. Download APK dari bagian **Artifacts** di run yang sukses → file `Zigora-Secure-debug-apk.zip` (di dalamnya ada `app-debug.apk`).

## Cara Pakai di HP

1. **Install Shizuku** dari Play Store: https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api
2. **Start Shizuku**:
   - Kalau device sudah root (Magisk/KernelSU) → buka Shizuku app → tap "Start" lewat metode root.
   - Kalau tidak root → start lewat ADB wireless debugging (lihat petunjuk di app Shizuku).
3. Install `app-debug.apk` hasil build (aktifkan "Install dari sumber tidak diketahui" kalau diminta).
4. Buka app **Zigora-Secure** → akan muncul dialog permintaan izin dari Shizuku → tap **Allow**.
5. Dashboard akan jalan seperti versi WebView module, tapi sekarang sebagai app mandiri.

## Catatan

- File `intro.mp4` (splash video) **tidak diikutkan** ke APK ini karena ukurannya besar (~21MB). Kalau mau pakai, taruh manual file video ke `app/src/main/assets/webroot/intro.mp4` sebelum build.
- `applicationId` di `app/build.gradle` adalah `com.xyroo.zigorasecure` — ganti kalau perlu sebelum build ulang.
- APK hasil build pakai **debug signing key** bawaan Android Gradle Plugin — cukup untuk pemakaian pribadi/testing, belum untuk rilis Play Store.
- Source HTML/CSS/JS dashboard ada di `app/src/main/assets/webroot/index.html` — edit langsung file ini kalau mau update tampilan, tidak perlu sentuh kode Kotlin.
