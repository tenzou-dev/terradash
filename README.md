# TerraDash

Dashboard sekaligus **launcher (HOME)** berbasis WebView untuk head unit Android —
dibuat untuk ORCA ADR-9988 7" (1042×600), tapi jalan juga di HP/tablet Android 7+.

Peta satelit live, jam besar, kecepatan GPS, cuaca, jadwal sholat, pemutar musik,
dock aplikasi, dan navigasi ke tujuan — semuanya dalam satu layar.

> **Mau langsung pakai?** Unduh APK-nya di halaman
> [**Releases**](../../releases/latest) — tidak perlu build sendiri.

---

## Fitur

| Fitur | Keterangan |
|---|---|
| **Peta live** | Esri World Imagery (default **Satelit**), plus mode Gelap / Terang / Jalan / Terrain |
| **Offline tiles** | Tile disimpan di IndexedDB; ada tombol unduh area (radius 3–20 km) |
| **Jam + tanggal** | Font futuristik (Orbitron / Chakra Petch / Rajdhani), ukuran bisa diatur |
| **Kecepatan** | Dari GPS (`coords.speed`), fallback hitung jarak/waktu antar-fix |
| **Cuaca** | Open-Meteo, ikut posisi mobil otomatis |
| **Jadwal sholat** | Aladhan `method=20` (Kemenag), ikut posisi + ganti hari otomatis |
| **Pemutar musik** | Baca MediaSession via Notification Listener; play/pause/next/seek dari dashboard |
| **Dock aplikasi** | Maks 6 app pilihan + tombol **Semua aplikasi** di ujung kanan |
| **Navigasi** | Cari tujuan (Esri + Photon + Nominatim), rute OSRM, kartu belokan + ETA |
| **Mode bersih** | Sembunyikan semua kontrol; sisa jam, kecepatan, cuaca, jadwal sholat |
| **Pengaturan** | Warna aksen, geser peta, zoom, font jam, dock app, cache peta — semua auto-save |

Semua pengaturan **langsung tersimpan** (tanpa tombol Simpan), jadi setelah mobil
mati dan nyala lagi tampilannya tetap sama.

---

## Unduh & pasang

1. Buka halaman [Releases](../../releases) → pilih versi terbaru.
2. Unduh file `TerraDash-x.y.z.apk`.
3. Di head unit: buka File Manager → ketuk APK → izinkan
   **Install unknown apps** kalau diminta → Install.
4. Tekan tombol **HOME** → pilih **TerraDash** → **Selalu**.

Lewat ADB (kalau lebih nyaman):

```powershell
adb install -r TerraDash-1.0.3.apk
```

> APK ini ditandatangani sendiri (bukan dari Play Store). Kalau Play Protect
> protes, pilih **More details → Install anyway**. Update hanya bisa menimpa
> versi lama kalau ditandatangani dengan kunci yang sama — kalau berbeda,
> harus uninstall dulu (setting + cache peta ikut hilang).

### Izin yang dibutuhkan

| Izin | Untuk apa |
|---|---|
| Lokasi (GPS) | peta, kecepatan, cuaca, jadwal sholat |
| Akses notifikasi | membaca & mengontrol pemutar musik (MediaSession) |

Android 13+ memblokir izin khusus untuk app sideload (*restricted settings*).
Kalau muncul **"App was denied access"**:
**Info aplikasi → ⋮ → Allow restricted settings** → aktifkan lagi akses
notifikasinya. Detailnya ada di [`android/README.md`](android/README.md).

---

## Struktur repo

```
dashboard/            Dashboard-nya: satu file index.html (HTML + CSS + JS)
  ├── fonts/          Font jam (woff2, dibundel lokal)
  └── vendor/         Leaflet 1.9.4 (dibundel lokal, tanpa CDN)
android/              Wrapper WebView + launcher
  ├── app/            Manifest, resource, source Java
  ├── build.ps1       Build APK tanpa Gradle (aapt2 + javac + d8 + zipalign)
  ├── probe/          APK uji minimal untuk diagnosa WebView
  └── README.md       Panduan pasang, izin, build, troubleshooting
tools/                Skrip bantu (cek encoding, verifikasi APK)
```

APK dibangun **tanpa Gradle/AndroidX** — cuma `aapt2`, `javac`, `d8`,
`zipalign`, `apksigner`. Hasilnya ±140 KB, tanpa Google Play Services.

---

## Build sendiri

Butuh Windows + PowerShell, JDK 17, dan Android build-tools + platform
(tidak perlu Android Studio).

```powershell
# toolchain portabel: .toolchain\jdk\... dan .toolchain\sdk\...
powershell -ExecutionPolicy Bypass -File android\build.ps1
```

Hasil: `android\dist\TerraDash-<versi>.apk`, otomatis ditandatangani.

Kalau `android\keystore\terradash.jks` belum ada, script akan membuatnya.
**Simpan file itu** — update APK harus memakai kunci yang sama.

Build tanpa toolchain lokal (mis. di CI):

```powershell
.\android\build.ps1 -Toolchain "C:\path\toolchain" -Keystore "C:\path\key.jks" -OutDir ".\out"
```

### GitHub Actions (opsional)

`.github/workflows/build.yml` bisa build APK otomatis dan membuat Release
saat kamu push tag `v*`. Sekali saja, isi 2 secret di
**Settings → Secrets and variables → Actions**:

| Secret | Isi |
|---|---|
| `KEYSTORE_BASE64` | isi file `terradash.jks` dalam base64 (lihat di bawah) |
| `KEYSTORE_PASS` | password keystore (`terradash` kalau tidak diubah) |

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("android\keystore\terradash.jks")) | Set-Clipboard
```

Kalau tidak mau pakai Actions, hapus folder `.github/` dan upload APK manual
lewat **Releases → Draft a new release**.

---

## Kustomisasi

- **Kota default / koordinat**: `DEFAULTS` di `dashboard/index.html`
  (default Tuban, Jawa Timur).
- **Warna aksen, font, ukuran jam, dock app**: lewat ikon gear di dashboard
  (tersimpan di `localStorage`, key `hu.dashboard.cfg.v2`).
- **Versi**: `android:versionCode` + `android:versionName` di
  `android/app/AndroidManifest.xml` dan teks `Versi x.y.z` di
  `dashboard/index.html` (panel Tentang).

Setelah mengubah `dashboard/index.html`, build ulang APK-nya — script otomatis
menyalin aset terbaru.

---

## Lisensi & atribusi

Kode: [MIT](LICENSE) © 2026 TenZou.Dev.

Data & layanan pihak ketiga punya syaratnya sendiri:

- **Esri** — basemap & World Geocoding (gratis untuk non-komersial, wajib atribusi).
- **OpenStreetMap** — data POI via Photon & Nominatim (Nominatim: maks 1 req/detik).
- **OSRM demo** — perhitungan rute, bukan untuk produksi berat.
- **Open-Meteo** — cuaca. **Aladhan** — jadwal sholat.
- **Leaflet** — peta, lisensi BSD-2-Clause.

Kalau mau dipakai komersial atau penggunanya banyak, ganti layanan gratis di
atas dengan instance/API berlangganan.
