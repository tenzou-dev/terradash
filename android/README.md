# TerraDash APK

Wrapper WebView untuk dashboard TerraDash, sekaligus **launcher (HOME)**.
Setelah dipasang, head unit bisa langsung boot ke dashboard ini.

| Item | Nilai |
|---|---|
| Nama | TerraDash |
| Package | `dev.tenzou.terradash` |
| Versi | 1.0.3 (versionCode 4) |
| minSdk / targetSdk | 24 (Android 7) / 34 |
| APK | `android/dist/TerraDash-1.0.3.apk` (± 0,13 MB) |
| Signing | keystore sendiri: `android/keystore/terradash.jks` |

Isi APK: `assets/index.html` + `fonts/` + `vendor/leaflet.*`, dex kecil
(`MainActivity`, `MediaListener`), tanpa library AndroidX — jadi tidak butuh
Google Play Services dan ringan.

---

## 1. Cara pasang

### A. Lewat file manager di head unit (paling gampang)

1. Copy `TerraDash-1.0.3.apk` ke flashdisk / SD card.
2. Colok ke HU, buka **File Manager** bawaan, ketuk APK-nya.
3. Kalau muncul "Install blocked / sumber tidak dikenal", izinkan dulu:
   **Settings → Apps → Special access → Install unknown apps → File Manager → Allow**.
4. Install → Open.

### B. Lewat ADB (paling aman kalau HU rewel)

```powershell
adb install -r "D:\Documents\Default Project\hu-launcher\android\dist\TerraDash-1.0.3.apk"
```

Kalau pernah install versi dengan kunci berbeda:

```powershell
adb uninstall dev.tenzou.terradash
adb install "…\TerraDash-1.0.3.apk"
```

### C. Play Protect

APK ini ditandatangani sendiri (bukan dari Play Store), jadi kalau muncul
peringatan **"Unsafe app blocked"**, pilih **More details → Install anyway**
atau matikan sementara Play Protect di Play Store.

---

## 2. Jadikan launcher default

Ini bagian yang **beda dari APK biasa**: karena TerraDash mendaftar sebagai
HOME, Android akan menanyakan launcher saat tombol Home ditekan.

1. Tekan tombol **HOME** di HU.
2. Muncul pilihan launcher → pilih **TerraDash** → **Always / Selalu**.
3. Kalau tidak muncul, buka **Settings → Apps → Default apps → Home app →
   TerraDash**.

Lewat ADB (kalau menu default home disembunyikan vendor):

```powershell
adb shell cmd package set-home-activity dev.tenzou.terradash/.MainActivity
```

Balik ke launcher bawaan:

```powershell
adb shell cmd package set-home-activity com.android.launcher3/.Launcher
```

(nama package launcher bawaan beda-beda; cek dengan
`adb shell cmd package resolve-activity -c android.intent.category.HOME`).

Kalau unit menolak launcher pihak ketiga, tidak masalah: jalankan TerraDash
dari daftar aplikasi seperti biasa. Tombol Back akan menyembunyikan dashboard
dan kembali ke app sebelumnya (kalau bukan launcher default).

---

## 3. Izin saat pertama jalan

| Izin | Untuk apa | Kapan |
|---|---|---|
| Lokasi (GPS) | peta, kecepatan, cuaca, jadwal sholat | diminta otomatis saat start |
| Akses notifikasi | baca lagu yang sedang diputar + kontrol play/pause/next | dialog awal / **Pengaturan → Tentang aplikasi → Aktifkan akses notifikasi** |

Akses notifikasi hanya perlu sekali. Setelah diaktifkan, buka Spotify/radio/
pemutar bawaan dan tekan play — dock pemutar di dashboard langsung ikut.

Kalau musik tidak terdeteksi:

- pastikan app pemutarnya benar-benar membuat **MediaSession** (Spotify, YouTube
  Music, radio bawaan biasanya iya);
- cek **Settings → Apps → Special access → Notification access → TerraDash**
  sudah aktif;
- beberapa HU mematikan service saat layar mati; buka dashboard lagi supaya
  service bind ulang.

### Kalau muncul "App was denied access" (Android 13 ke atas)

Android 13+ memblokir izin khusus (akses notifikasi, accessibility) untuk app
yang di-sideload. Ini bukan bug APK. Cara membukanya:

1. Tutup dialog errornya.
2. Buka **Pengaturan → Tentang aplikasi → Buka info aplikasi** (tombol ini ada
   di dashboard kalau akses notifikasi belum aktif), atau
   **Settings → Apps → TerraDash**.
3. Ketuk **titik tiga (⋮)** di kanan atas → **Allow restricted settings**.
4. Balik ke dashboard → **Aktifkan akses notifikasi** lagi → **Allow**.

Setelah itu kontrol musik di dock berfungsi normal.

---

## 4. Build ulang

Toolchain portabel sudah ada di `..\.toolchain` (tidak masuk APK):

- JDK 17 (Temurin)
- Android build-tools 34 (`aapt2`, `d8`, `zipalign`, `apksigner`)
- Android platform 35 (`android.jar`)

Build:

```powershell
powershell -ExecutionPolicy Bypass -File hu-launcher\android\build.ps1
```

Hasil: `android\dist\TerraDash-1.0.3.apk`. Script otomatis:

1. copy `dashboard/index.html`, `fonts/`, `vendor/` ke `app/assets`;
2. `aapt2 compile` + `link` (manifest, ikon, aset);
3. `javac` → `d8` → `classes.dex`;
4. `zipalign` + `apksigner`.

Kalau `..\.toolchain` dihapus, unduh ulang:

| File | URL |
|---|---|
| JDK 17 | `https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse` |
| build-tools 34 | `https://dl.google.com/android/repository/build-tools_r34-windows.zip` |
| platform 35 | `https://dl.google.com/android/repository/platform-35_r02.zip` |

Ekstrak ke `.toolchain\jdk\...` dan `.toolchain\sdk\...`; script mencari
`aapt2.exe` dan `android.jar` otomatis.

### Kunci tanda tangan

`android/keystore/terradash.jks`, alias `terradash`, password `terradash`.
**Simpan file ini.** Update APK harus ditandatangani kunci yang sama, kalau
tidak harus uninstall dulu (data dashboard/peta offline ikut hilang).

---

## 5. Struktur

```
android/
├── app/
│   ├── AndroidManifest.xml        HOME + LAUNCHER, izin, service media
│   ├── res/values/styles.xml      tema fullscreen hitam
│   ├── res/mipmap-*/ic_launcher.png
│   └── src/dev/tenzou/terradash/
│       ├── MainActivity.java      WebView, asset loader, HUApps/HUMedia, layar crash
│       ├── TerraApp.java          penangkap crash + info WebView
│       └── MediaListener.java     NotificationListenerService + MediaSession
├── build.ps1
├── build-probe.ps1                build APK uji (probe/)
├── probe/                         APK uji minimal (UI native + tes WebView)
├── tools/add-dex.py
├── keystore/terradash.jks
└── dist/TerraDash-1.0.3.apk
```

Cara kerja singkat:

- Halaman dimuat dari `https://appassets.androidplatform.net/assets/index.html`
  dan di-intercept dari folder `assets/` → origin https, jadi
  `localStorage`, IndexedDB (cache tile), dan geolocation berjalan normal.
- `HUApps` diisi dari `PackageManager.queryIntentActivities` (MAIN/LAUNCHER),
  ikon 96 px base64. `HUApps.launch(pkg)` membuka app dengan
  `FLAG_ACTIVITY_NEW_TASK`.
- `HUMedia` membaca sesi media aktif lewat izin Notification Listener, lalu
  mendorong perubahan ke `window.HUMediaUpdate(json)`. Tombol di dashboard
  memanggil `play/pause/next/prev/seek`.
- Immersive fullscreen, layar tidak tidur (`FLAG_KEEP_SCREEN_ON`), orientasi
  landscape, back tidak keluar saat jadi launcher.

---

## 6. Troubleshooting

| Gejala | Sebab / solusi |
|---|---|
| APK tidak mau install | izinkan "Install unknown apps" untuk File Manager; atau pakai `adb install` |
| Setelah install tidak ada pilihan launcher | Settings → Apps → Default apps → Home app; atau `adb shell cmd package set-home-activity dev.tenzou.terradash/.MainActivity` |
| Layar putih / "not found" | aset tidak ikut ter-build; jalankan `build.ps1` ulang |
| Peta hitam | tidak ada internet dan area belum di-cache; buka dulu dengan sinyal, atau pakai tombol unduh peta |
| Lokasi tidak jalan | izin lokasi ditolak; buka Settings → Apps → TerraDash → Permissions → Location → Allow |
| Pemutar tetap "Belum ada musik" | akses notifikasi belum aktif, atau app pemutar tidak punya MediaSession |
| Pencarian tidak menemukan kampus/tempat | 1.0.3 sudah menggabungkan Esri + Photon + Nominatim; coba nama resmi lengkap + kota |
| Muncul "App was denied access" saat mengaktifkan akses notifikasi | Android 13+ *restricted settings* untuk app sideload → lihat bagian 3 |
| Karakter aneh di nama jalan/tujuan | sudah diperbaiki di 1.0.3 (fungsi `fixText`, termasuk mojibake ganda) |
| Tampilan berantakan di HU lama (ikon besar, jam kecil, jadwal sholat berdempetan) | 1.0.4 sudah menambahkan polyfill untuk WebView Chromium < 79 (mis. Chromium 61 di unit 8227L). Kalau masih berantakan, update **Android System WebView** |
| Play Protect memblokir | More details → Install anyway |
| Buka app cuma kedip hitam lalu tutup (Android 16) | bug platform Android 16, lihat bagian 7 |

---

## 7. Android 16: kedip hitam lalu tertutup

Gejala: ikon diketuk → layar hitam sekejap → aplikasi tertutup. Ini **bukan**
salah setelan HP dan bukan APK rusak.

Penyebab: bug platform Android 16 (rilis 25Q2) di
`ResourcesManager.redirectAllResourcesToNewImplLocked()`. Saat WebView pertama
kali dibuat, sistem gagal me-load provider WebView dan melempar
`Resources$NotFoundException: failed to redirect ResourcesImpl`. Bug ini sudah
diperbaiki Google di rilis 25Q3; untuk HP yang belum dapat OTA, solusinya:

1. **Update "Android System WebView" dan Chrome** di Play Store.
2. Buka TerraDash **2–3 kali** — pada banyak kasus percobaan berikutnya berhasil
   (bug ini biasanya hanya sekali di awal).
3. APK 1.0.3 sudah punya mitigasi sendiri:
   - WebView dibuat dengan **retry 4×** (jeda 120–480 ms) dan ditunda 60 ms
     setelah Activity start;
   - kalau tetap gagal, muncul **layar diagnostik** berisi stack trace, tombol
     **Salin**, **Hapus & coba lagi**, dan **Info WebView**.

Kalau layar diagnostik muncul, salin teksnya dan kirim ke developer. Kalau
yang muncul malah app langsung tertutup tanpa layar apa pun, berarti proses
mati sebelum Java sempat menangani (crash native) — pakai APK uji:

```
android/dist/TerraDash-Probe-1.0.0.apk   (package dev.tenzou.terradash.probe)
```

Probe hanya menampilkan teks native + tombol tes WebView. Kalau probe juga
langsung tertutup → masalah di APK/ROM (bukan WebView). Kalau probe jalan dan
tombol "Tes tampilkan WebView" berhasil → WebView normal, masalahnya di
dashboard/aset.

Build probe: `powershell -File hu-launcher\android\build-probe.ps1`.

