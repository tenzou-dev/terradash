# Changelog

Semua perubahan penting TerraDash. Format mengikuti
[Keep a Changelog](https://keepachangelog.com/id/1.1.0/).

## [1.0.4] — 2026-10-30

### Diperbaiki
- **Tampilan rusak di head unit dengan WebView lama** (Chromium 61 / Android
  System WebView 61, umum di unit 8227L). Penyebabnya `clamp()`, `min()`, dan
  flex `gap` yang baru ada di Chrome 79–84, sehingga semua ukuran jatuh ke
  nilai default: ikon raksasa, jam mengecil, jadwal sholat berdempetan.
- Ditambahkan polyfill `clamp()` / `min()` / `max()` (dihitung ulang saat layar
  berubah) plus margin pengganti flex `gap`, `grid-gap`, dan warna `#rrggbbaa`
  diganti `rgba()` supaya panel pengaturan tidak transparan.

## [1.0.3] — 2026-10-29

### Diubah
- Lebar pemutar musik sekarang **mengikuti lebar baris jadwal sholat**
  (diukur otomatis), jadi tepi kanannya sejajar dan judul lagu tidak terpotong.
- Kalau pemutar dimatikan, jadwal sholat dan dock aplikasi berbagi satu baris —
  tidak ada lagi ruang kosong bekas pemutar.
- Pencarian tujuan memakai **tiga sumber sekaligus** (Esri + Photon + Nominatim),
  hasil digabung, duplikat dibuang, lalu diurutkan berdasarkan kecocokan kata
  kunci dan jarak. "Unair kampus C" sekarang ketemu di Surabaya.

## [1.0.2] — 2026-10-29

### Ditambahkan
- Tombol **Buka info aplikasi** + panduan *Allow restricted settings*
  (Android 13+ memblokir akses notifikasi untuk app sideload).
- `fixText()` untuk memperbaiki teks mojibake dari data OSM (termasuk ganda).

### Diubah
- Default tampilan peta jadi **Satelit** (migrasi sekali lewat `CFG_REV`).
- Pemilih aplikasi dock dipindah ke dialog pencarian terpisah — halaman
  pengaturan tidak lagi memanjang karena daftar semua app.
- Asset loader mengirim `charset=utf-8` untuk file teks.

## [1.0.1] — 2026-10-29

### Diperbaiki
- Crash saat WebView dibuat di Android 16 (bug platform 25Q2): retry 4× +
  jeda 60 ms, plus layar diagnostik dengan tombol Salin / Coba lagi.
- Encoding `index.html` yang rusak (karakter acak di UI).

## [1.0.0] — 2026-10-29

### Ditambahkan
- Rilis pertama: peta Esri + cache offline, jam, kecepatan GPS, cuaca,
  jadwal sholat, pemutar musik, dock aplikasi, navigasi OSRM, mode bersih.
