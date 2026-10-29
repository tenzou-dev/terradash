# TerraDash — Dashboard Head Unit (WebView)

Dashboard HTML/CSS/JS untuk head unit Android. Tampil fullscreen: peta live,
jam besar, kecepatan GPS, cuaca, jadwal sholat, pemutar musik, dock aplikasi,
dan navigasi ke tujuan. Semua data lokasi ikut posisi mobil.

File utama: `index.html` (satu file, tanpa build step). Leaflet dan font
dibundel lokal di `vendor/` dan `fonts/`, jadi peta tetap jalan tanpa internet
(selama tile area tersebut sudah di-cache).

**APK siap pasang:** `hu-launcher\android\dist\TerraDash-1.0.3.apk`
(package `dev.tenzou.terradash`). Cara pasang dan build ulang ada di
`hu-launcher\android\README.md`.

---

## 1. Cara buka

**Di PC / browser:**

```
start hu-launcher\dashboard\index.html
```

Boleh langsung dari `file://`. Geolocation Chrome hanya aktif untuk origin
yang diizinkan; kalau lokasi tidak bergerak, jalankan lewat server lokal:

```
python -m http.server 8080 -d hu-launcher\dashboard
```

lalu buka `http://localhost:8080`.

**Di head unit:** pakai APK TerraDash (bagian 7). Alternatif tanpa APK:
Fully Kiosk Browser / Wallpanel diarahkan ke file atau URL di atas.

---

## 2. Isi layar

| Elemen | Sumber | Update |
|---|---|---|
| Jam + tanggal | jam device | tiap detik |
| Kecepatan | GPS `coords.speed` | tiap fix GPS |
| Nama tempat | reverse geocode BigDataCloud | pindah > 3 km atau 15 menit |
| Peta | Esri (mode bisa diganti: satelit/gelap/terang/jalan/terrain, default **satelit**) | live, di-cache otomatis |
| Cuaca + suhu | Open-Meteo (tanpa API key) | pindah > 2 km atau 10 menit |
| Jadwal sholat | Aladhan, `method=20` (Kemenag) | pindah > 15 km, ganti hari, atau 6 jam |
| Pemutar musik | jembatan `HUMedia` (APK) | tiap status berubah |
| Dock aplikasi | jembatan `HUApps` (APK) | saat dibuka |
| Rute | OSRM + pencarian Esri/Photon | saat tujuan dipilih / keluar jalur |
| Jejak rute | breadcrumb GPS (merah) | tiap fix |

Kalau GPS mati / tidak dapat fix, dashboard memakai kota manual dari setting.

**Kecepatan bukan dari OBD.** HU ini tidak membaca CAN bus. Kalau `coords.speed`
kosong, kecepatan dihitung dari jarak antar-fix GPS dibagi waktu.

---

## 3. Setting (ikon gear kanan atas)

| Setting | Keterangan |
|---|---|
| Warna aksen | amber / hijau / biru / merah / putih |
| Kota manual | dipakai saat "Ikuti GPS" dimatikan |
| Geser peta ke kanan | 0–30 %, default 12 % (biar tidak ketutup HUD kiri) |
| Zoom peta | 12–18 |
| Tampilan peta | satelit (default) / gelap / terang / jalan / terrain |
| Font jam | Sistem / Orbitron / Chakra Petch / Rajdhani |
| Ukuran jam | 70–140 % |
| Ikuti GPS | lokasi otomatis (nama tempat, cuaca, sholat) |
| Kecepatan / Cuaca / Sholat | tampil–sembunyi |
| Pemutar musik | tampil–sembunyi |
| Dock aplikasi | pilih maks. 6 app yang tampil di dock bawah |
| Format 24 jam | on/off |
| Peta offline | unduh area sekitar posisi sekarang |
| Tentang aplikasi | nama app, versi, copyright |
| Kembalikan default | reset setting (cache peta tidak dihapus) |

Semua perubahan **langsung tersimpan** sebagai default (tanpa tombol Simpan),
jadi setelah mobil mati dan nyala lagi tampilannya tetap sama.

Disimpan di `localStorage`:

- `hu.dashboard.cfg.v2` — setting tampilan
- `hu.dashboard.place` — label lokasi terakhir
- `hu.dashboard.prayer` — jadwal sholat terakhir (biar tetap tampil offline)

**Migrasi default sekali jalan:** sejak 1.0.2 default tampilan peta adalah
**Satelit**. Supaya head unit yang sudah pernah dipasang versi lama ikut
berubah, `loadState()` memakai `CFG_REV`. Kalau `cfgRev` tersimpan masih di
bawah 2, `mapMode` di-set ke `satellite` sekali, lalu `cfgRev = 2` disimpan.
Setelah itu pilihan user tidak pernah ditimpa lagi — jadi kalau nanti pilih
Gelap, tetap Gelap walau app dibuka ulang.

### 3.1 Mode peta & kenapa zoom 17–18 dulu kosong

Esri tidak menyediakan tile detail sampai zoom 18 untuk semua basemap. Kalau
Leaflet tetap minta z17/z18 ke layanan yang berhenti di z16, server membalas
tile abu-abu kosong (bukan error), jadi layar kelihatan blank.

Sekarang tiap mode punya `maxNativeZoom`, jadi Leaflet berhenti meminta tile
baru dan **memperbesar tile terakhir**:

| Mode | Basemap | Layer tambahan | Tile asli s/d |
|---|---|---|---|
| Gelap | Canvas World Dark Gray | label jalan/nama tempat | z16 |
| Satelit (default) | World Imagery | jalan + batas & nama tempat | z18 |
| Terang | Canvas World Light Gray | label | z16 |
| Jalan | World Street Map | – | z17 |
| Terrain | World Topo Map | – | z17 |

Zoom maksimal di UI tetap 18. Di mode Gelap, z17–18 tampil sebagai tile z16
yang diperbesar (sedikit lebih blur, tapi tidak kosong). Kalau butuh detail
paling tajam saat zoom dekat, pakai **Satelit** — citranya asli sampai z18.

**Traffic:** layer `Reference/World_Traffic` Esri sudah tidak publik (balas
`Token Required` / `Service not found`). Garis jalan yang lebih terang di
contoh itu bukan traffic, tapi efek layer `World_Transportation` di atas citra
satelit — sudah dipakai di mode Satelit. Kalau nanti mau traffic sungguhan,
perlu API key TomTom / HERE / Google.

### 3.2 Font jam

Font dibundel lokal di `fonts/` (woff2, total ±32 KB) supaya tetap tampil
tanpa internet:

- `orbitron-700.woff2` — default, paling "digital"
- `chakra-petch-600.woff2` — agak teknik, sudut terpotong
- `rajdhani-600.woff2` — ramping, mirip speedometer

Slider **Ukuran jam** 70–140 % menskalakan ukuran dasar yang responsif
(`clamp`), jadi di layar 7" maupun 10" tetap proporsional.

### 3.3 Navigasi ke tujuan

Kotak **Cari tujuan…** di pojok kanan atas:

1. ketik minimal 3 huruf → saran tempat muncul dari **tiga sumber sekaligus**
   (semuanya tanpa API key):
   - **Esri World Geocoding** — paling kuat untuk alamat/jalan;
   - **Photon (OSM)** dan **Nominatim (OSM)** — paling lengkap untuk POI
     (kampus, masjid, rumah sakit, minimarket) yang tidak ada di Esri.

   Hasil ketiganya digabung, duplikat dibuang (nama sama + jarak < 200 m), lalu
   diurutkan: cocok kata kunci dulu, kemudian jarak dari posisi mobil. Hasil
   yang jelas beda pulau/benua dibuang kalau sudah ada hasil dalam 150 km.
   Jadi "Unair kampus C" ketemu **Unair Kampus C, Surabaya**, bukan "Kampus C"
   di Palembang;
2. pilih salah satu → rute dihitung OSRM demo dan digambar di peta sebagai
   garis aksen + garis tepi gelap;
3. peta otomatis zoom ke seluruh rute, lalu kartu navigasi menampilkan:
   belokan berikutnya + nama jalan, sisa jarak, sisa waktu, jam tiba, dan
   progress bar.

Kalau mobil keluar jalur > 100 m, rute dihitung ulang otomatis (maks. sekali
per 20 detik). Tombol **×** di kartu atau **×** di kotak pencarian mengakhiri
rute.

Catatan: OSRM demo (`router.project-osrm.org`) gratis tapi bukan untuk
produksi berat; kalau nanti dipakai harian, ganti ke server OSRM sendiri atau
GraphHopper/Valhalla ber-key. Pencarian dan rute butuh internet; peta yang
sudah di-cache tetap tampil walau sinyal hilang.

### 3.4 Dock bawah: pemutar musik + aplikasi

Urutan dari kiri: **pemutar musik**, lalu **dock aplikasi**, dan **Semua
aplikasi** selalu di ujung kanan.

- Pemutar: art, judul, artis, prev / play-pause / next, progress bar (bisa
  diklik untuk seek). **Lebarnya mengikuti baris jadwal sholat** (diukur oleh
  `syncPlayerWidth()`, dihitung ulang saat resize / jadwal sholat berubah),
  jadi tepi kanannya sejajar dengan jam sholat dan judul lagu tidak
  terpotong. Kalau jadwal sholat disembunyikan, dipakai
  `clamp(250px, 34vw, 420px)`. Datanya dari jembatan `HUMedia` (lihat
  bagian 5). Di browser biasa tampil contoh + progres palsu supaya tata letak
  kelihatan.
- Dock aplikasi: maksimal 6 app. Di **Pengaturan → Dock aplikasi** hanya
  tampil *chip* app yang sudah dipilih (masing-masing ada tombol **×** untuk
  melepas), plus tombol **Pilih aplikasi…** yang membuka dialog pencarian
  terpisah — jadi daftar puluhan app tidak lagi memanjangkan halaman setting.
  Kalau belum dipilih, otomatis diisi 5 app pertama dari daftar Android.
- Kalau **Pemutar musik dimatikan**, tata letak bawah berubah jadi grid
  (`body.no-player`): cuaca di atas, lalu **jadwal sholat dan dock aplikasi
  dalam satu baris** (sholat kiri, ikon app kanan). Tidak ada lagi pita kosong
  bekas pemutar.

### 3.5 Layar penuh (mode bersih)

Tombol ⛶ di kanan atas menyembunyikan semua kontrol: kotak pencarian, gear,
pemutar, dan dock aplikasi. Yang tersisa cuma jam, tanggal, kecepatan
(dibesarkan + warna aksen), cuaca, dan jadwal sholat. Tombol kecil di pojok
kanan bawah untuk keluar (opasitasnya sengaja rendah biar tidak ganggu).

---

## 4. Peta offline

Tile yang pernah tampil otomatis disimpan di **IndexedDB** (`hu-tiles`), jadi
area yang sudah dilewati tetap muncul saat sinyal hilang.

Tombol **Unduh peta sekitar sini** menyimpan tile secara sengaja:

- radius 3 / 5 / 10 / 20 km
- sampai zoom 14 / 15 / 16 (dibatasi `maxNativeZoom` mode yang aktif, jadi
  tidak ada tile kosong yang ikut tersimpan)
- unduhan jalan di latar (5 worker), ada progress bar

Perkiraan ukuran tampil sebelum mulai. Contoh: radius 5 km sampai zoom 15
≈ 1.200 tile ≈ 20 MB. Radius 20 km sampai zoom 16 bisa > 10.000 tile
(± 180 MB) — pilih sesuai kapasitas.

Batas cache 24.000 tile; tile terlama dihapus otomatis. **Hapus cache peta**
mengosongkan semuanya.

Catatan: IndexedDB butuh origin yang stabil. APK TerraDash memuat halaman dari
`https://appassets.androidplatform.net/assets/index.html` (di-intercept dari
folder `assets/`), bukan `file://`, supaya cache tile, localStorage, dan
geolocation berjalan normal.

---

## 5. Bridge aplikasi Android

HTML tidak bisa membuka app Android sendiri. Drawer aplikasi (ikon grid)
memanggil objek yang disuntikkan wrapper **sebelum** halaman dimuat:

```js
window.HUApps = {
  list:   function () { return JSON.stringify([
    { pkg: 'com.android.settings', label: 'Setelan', icon: 'data:image/png;base64,...' }
  ]); },
  launch: function (pkg) { /* startActivity */ }
};
```

- `icon` opsional; kalau kosong dipakai huruf awal + warna otomatis.
- Tanpa `HUApps`, drawer menampilkan pesan penjelasan.

APK TerraDash sudah menyuntikkan `HUApps` otomatis dari
`PackageManager.queryIntentActivities` (lihat `android/app/src/.../MainActivity.java`),
jadi di HU tidak perlu wrapper tambahan.

### 5.1 Bridge pemutar musik (`HUMedia`)

```js
window.HUMedia = {
  state: function () {                    // dipanggil dashboard tiap 1 detik
    return JSON.stringify({
      title: 'Lathi', artist: 'Weird Genius',
      art: 'data:image/png;base64,...',   // opsional
      playing: true,
      pos: 61000,                         // milidetik
      dur: 208000
    });
  },
  play: function () {}, pause: function () {},
  next: function () {}, prev: function () {},
  seek: function (ms) {}
};

// dorong perubahan tanpa nunggu polling (mis. dari MediaSession callback)
window.HUMediaUpdate(JSON.stringify({ /* bentuk sama seperti state() */ }));
```

Tanpa `HUMedia`, dock pemutar menampilkan contoh (tidak error). Di APK,
`HUMedia` diisi oleh `MediaListener` (`NotificationListenerService` +
`MediaSessionManager`) — perlu izin akses notifikasi sekali saja.

Kalau saat mengaktifkan izin muncul **“App was denied access”**, itu proteksi
Android 13+ untuk app sideload (*restricted settings*), bukan bug. Di
**Pengaturan → Tentang aplikasi** ada tombol **Buka info aplikasi**; dari sana
ketuk **⋮ → Allow restricted settings**, lalu aktifkan lagi akses notifikasinya.

---

## 6. Yang perlu disiapkan saat jadi APK

- Izin `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, lalu
  `WebChromeClient.onGeolocationPermissionsShowPrompt` → `invoke(origin, true, false)`.
- `settings.domStorageEnabled = true`, `databaseEnabled = true`,
  `javaScriptEnabled = true`.
- Launcher default: activity dengan
  `category.HOME` + `category.DEFAULT` (supaya muncul saat mobil nyala).
- Immersive fullscreen + `setKeepScreenOn(true)`.
- Muat `index.html` dari `assets/` lewat WebViewAssetLoader (bukan `file://`)
  agar IndexedDB, geolocation, dan fetch berjalan normal.
- Untuk musik: `NotificationListenerService` (diizinkan user) atau
  `MediaSessionManager.getActiveSessions()` — yang terakhir butuh
  `MEDIA_CONTENT_CONTROL` (biasanya hanya system app). Lalu panggil
  `window.HUMediaUpdate(...)` dari callback media.
- Untuk membuka app dari dock: `Intent` + `FLAG_ACTIVITY_NEW_TASK`, dan
  daftarkan app di `HUApps.list()`.

---

## 7. Troubleshooting

| Gejala | Sebab / solusi |
|---|---|
| Peta hitam, tile kosong | tidak ada internet dan area belum pernah di-cache |
| Zoom 17–18 jadi abu-abu/kosong | sudah diperbaiki lewat `maxNativeZoom`; kalau masih kosong, cek apakah tile z16 mode itu termuat |
| Suhu `--°` | Open-Meteo gagal; nilai terakhir dibiarkan kalau sudah ada |
| Jadwal sholat kosong | Aladhan gagal dan belum ada cache hari ini |
| Nama tempat tidak berubah | reverse geocode dibatasi 3 km / 15 menit |
| Kecepatan selalu 0 | HU tidak dapat fix GPS, atau `coords.speed` null (fallback jarak/waktu) |
| Pemutar tetap tampil "Contoh…" | `HUMedia` belum disuntik APK (atau izin akses notifikasi belum diberikan) |
| Dock aplikasi abu-abu putus-putus | contoh di browser; `HUApps` belum ada di APK |
| Cari tujuan tidak ada hasil | Esri/Photon butuh internet; coba nama kota + kecamatan |
| Tempat/POI tidak ketemu (kampus, masjid, toko) | 1.0.3 mencari ke Esri + Photon + Nominatim; coba nama resmi lengkap atau tambahkan nama kota |
| Karakter aneh di nama jalan/tujuan | sudah diperbaiki 1.0.2 lewat `fixText()` (cp1252 → UTF-8, termasuk mojibake ganda) |
| Rute gagal dihitung | server OSRM demo sedang down/limit; coba lagi atau pakai server sendiri |
| Setting tidak tersimpan | `localStorage` diblokir WebView |
