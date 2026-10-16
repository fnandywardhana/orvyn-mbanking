# Test plan: Hotfix 0.8.1, tanggal lahir dan Quick Amount

Sumber: `requirement.md`, `prd.md`, `stories.md` (H1 sampai H5), `design.md` di folder ini. Ditulis QA, 16 Okt 2026, sebelum laporan IT masuk. Skenario dibuat dengan skill `test-scenarios`. Bila laporan IT mengubah sesuatu (nama file, test baru), rencana ini disesuaikan sebelum dijalankan.

## 1. Lingkungan dan batasan

| Hal | Isi |
|---|---|
| Server | Dev server lokal: `docker compose -f compose.dev.yaml -p orvyn-dev up -d`, lalu di `server/`: `ADMIN_EMAIL=… ADMIN_PASSWORD=… PORT=8790 node src/main.ts`. Admin dev di `http://localhost:8790/admin`. |
| Aplikasi | Debug build 0.8.1 (versionCode 11) dari `./gradlew assembleDebug`, ke dev server lewat `adb reverse tcp:8790 tcp:8790`. |
| HP | Galaxy Note10+ pemilik, Samsung Keyboard bawaan. Lebar 411dp. |
| Pengecekan buku besar | Dashboard admin dev, harus "✓ Balanced", sebelum dan sesudah setiap skenario yang memindahkan uang. |
| Akhir sesi | Pasang lagi `releases/orvyn.apk`, hapus `adb reverse`, kembalikan tema aplikasi ke nilai semula. |

**Aturan HP** (`.claude/agents/qa.md`): sebelum setiap tap, ketikan, atau key event, cek `adb shell dumpsys window | grep mCurrentFocus` berisi `id.orvyn.app`, berhenti bila tidak. Tidak menekan Back di beranda ORVYN. Screenshot hanya saat ORVYN di depan, status bar dipotong. Kamera tidak dipakai (selalu "Skip this step"). Tidak mengubah pengaturan HP, tidak menyentuh aplikasi lain.

**Akibatnya, tiga hal diuji dengan cara pengganti:**

| Hal | Kenapa tidak langsung di HP | Cara uji di rencana ini |
|---|---|---|
| Ukuran huruf terbesar | Mengubah ukuran huruf adalah pengaturan HP. | (a) Test Roborazzi/Robolectric dengan `fontScale = 2f` pada lebar 360dp dan 411dp, terang dan gelap (dari IT bila ada; bila tidak, QA menambah file test sementara di `android/app/src/test/`, tidak di-commit, dihapus setelah dijalankan). (b) Bila pemilik HP bersedia mengubah sendiri ukuran huruf ke terbesar lalu mengembalikannya, skenario E5 diulang di HP. |
| TalkBack | Menyalakan TalkBack adalah pengaturan HP. | (a) `adb shell uiautomator dump` saat ORVYN di depan: baca `content-desc` dan `selected` setiap chip (ini pohon aksesibilitas yang sama yang dibaca TalkBack). (b) Test Compose semantics bila IT menambahkannya. (c) Bila pemilik menyalakan TalkBack sendiri, ucapan asli dicek. |
| Terang dan gelap | Tidak perlu pengaturan HP. | Pakai pengaturan tema di dalam ORVYN: Profile, tema "Light" atau "Dark". Nilai semula dicatat dan dikembalikan. |
| Tempel (paste) | Mengisi clipboard dari luar ORVYN berarti menyentuh sistem atau aplikasi lain. | Teks disalin dari field lain di dalam ORVYN (field "Full name" atau "Address" di langkah yang sama), lalu ditempel lewat menu teks bawaan field "Date of birth". `adb shell input text` dipakai untuk meniru keyboard yang punya tombol "/". |

## 2. Data uji

| Kode | Nasabah atau keadaan | Dipakai untuk |
|---|---|---|
| D1 | **Nomor HP baru** di dev server, belum pernah mendaftar, misalnya `081290010001`, `081290010002`, `081290010003` (cek dulu di admin dev bahwa belum ada). | H1, H2, E1–E4 (pendaftaran). Satu nomor per pendaftaran yang dikirim. |
| D2 | **Raka Pratama** `081211112222`, nasabah biasa. Cek di admin dev: saldo Everyday, ada kantong, punya profil risiko atau belum. | H3 (deposito), H5 (layar harian), E7 saldo kurang, E8 PIN salah. |
| D3 | **Hendra** `081255556666`, Prestige, saldo besar, sudah punya investasi. Cek profil risikonya dan Holding Fund dan emasnya. | H3 (deposito 50M), H4 (Fund, Bond, Gold, jual), E9 dobel ketuk. |
| D4 | Produk di atas profil risiko Hendra (Fund saham atau Bond dengan risiko lebih tinggi dari profilnya). Bila semua produk sesuai profil, pakai Raka setelah mengisi kuis dengan jawaban konservatif. | H4 AC3 (pernyataan risiko). |
| D5 | Kartu kredit dengan tagihan berjalan (Hendra punya Voyage). | H5 AC1 dan AC3 ("Another amount"). |
| D6 | Pengaturan fitur di admin dev: `register`, `deposits`, `invest`. | E10 fitur dimatikan. |

Persiapan di HP: bila release ORVYN terpasang dengan tanda tangan berbeda, debug build tidak bisa menimpanya dan release harus dicopot dulu (sesi ORVYN pemilik ikut hilang, dikembalikan dengan memasang `releases/orvyn.apk` di akhir). Untuk pendaftaran, keluar dulu dari nasabah yang sedang masuk lewat Profile, "Sign out".

## 3. Peta kriteria ke skenario

| Story | AC | Skenario | Tempat |
|---|---|---|---|
| H1 | 1 | H1-1 | HP |
| H1 | 2 | H1-2 | HP + unit test |
| H1 | 3 | H1-3 | HP |
| H1 | 4 | H1-4 | HP |
| H1 | 5 | H1-5 | HP + unit test |
| H1 | 6 | H1-6 | HP |
| H2 | 1 | H2-1 | HP |
| H2 | 2 | H2-2 | HP |
| H2 | 3 | H2-3 | HP |
| H2 | 4 | H2-4 | HP |
| H2 | 5 | H2-5 | HP + admin dev |
| H3 | 1–4 | H3-1 | HP |
| H3 | 3 | H3-2 | HP + admin dev (buku besar) |
| H3 | 5 | E5 | Robolectric (+ HP bila pemilik mengubah huruf) |
| H3 | 6 | E6 | `uiautomator dump` |
| H4 | 1, 3 | H4-1 | HP |
| H4 | 2, 3 | H4-2 | HP |
| H4 | 3 | H4-3 | HP |
| H4 | 4 | H4-4 | HP |
| H4 | 5 | H4-5, E5 | HP + Robolectric |
| H5 | 1, 2 | H5-1 | HP |
| H5 | 3 | H5-2 | HP |
| H5 | 4 | H5-3 | HP + admin dev (buku besar) |
| Umum | test otomatis | A1 | laptop |

---

## 4. Test otomatis

### A1 Test server dan aplikasi
**Tujuan:** tidak ada yang rusak (PRD tujuan 3).
**Langkah:**
1. `cd server && npm test` → semua lolos (jumlah test dicatat, tidak boleh berkurang dari 0.8.0).
2. `cd android && ./gradlew testDebugUnitTest assembleDebug` → semua lolos, APK debug terbentuk.
3. Cek `android/app/build.gradle.kts`: `versionName "0.8.1"`, `versionCode 11`.
4. Bandingkan `AndroidManifest.xml` dengan 0.8.0 (`git diff`): tidak ada `uses-permission` baru.
5. `git diff` tidak berisi file rahasia (`.env`, `secrets/`, `*.jks`, `signing.properties`).
6. Bila IT menambah unit test untuk saringan tanggal atau label chip, baca isinya: harus mencakup `17081990`, angka ke-9, tempel `17/08/1990`, `17-08-1990`, `17.08.1990`, dan label `50k`, `1M`, `50M`.
7. Cek `server/` tidak berubah, kecuali perbaikan zona waktu H2-5 (PRD bagian 8).
**Hasil yang diharapkan:** semua lolos, versi benar, tidak ada izin baru, tidak ada rahasia.

---

## 5. H1 Mengetik tanggal lahir

Semua skenario H1: **HP**, data **D1**, keadaan awal di langkah "About you" (Step 3 of 8) setelah OTP berhasil. Kode OTP dibaca dari layar fallback ORVYN atau dari admin dev, bukan dari notifikasi sistem.

### H1-1 Mengetik 8 angka dengan Samsung Keyboard (AC1)
**Tujuan:** tanggal lahir bisa diisi dengan keyboard angka bawaan tanpa "/".
**Langkah:**
1. Ketuk field "Date of birth" → keyboard angka Samsung terbuka (screenshot).
2. Ketik `17081990` lewat tombol keyboard di layar (tap pada tombol angka keyboard ORVYN yang sedang terbuka; `input text 17081990` sebagai pembanding).
**Hasil:** field menampilkan `17/08/1990`. Tidak ada "/" yang perlu diketik.

### H1-2 Garis miring otomatis dan batas 8 angka (AC2)
**Langkah:** dari field kosong, ketik satu per satu dan screenshot setiap tahap.
1. `1` → `1`
2. `7` → `17/`
3. `0` → `17/0`
4. `8` → `17/08/`
5. `1990` → `17/08/1990`
6. Ketik `5` (angka ke-9) → tetap `17/08/1990`.
7. Taruh kursor di tengah (setelah `17/`) lalu ketik `3` → tetap `17/08/1990` (field penuh, perubahan diabaikan).
**Hasil:** sesuai `design.md` 1.2. Kursor ada di akhir setelah langkah 5.

### H1-3 Hapus satu angka per tekan (AC3)
**Langkah:** dari `17/08/1990`, kursor di akhir, tekan hapus (`input keyevent 67`) satu kali per langkah, screenshot setiap tekan.
**Hasil:** `17/08/199`, `17/08/19`, `17/08/1`, `17/08/`, `17/0`, `17/`... sampai kosong tepat setelah **8** tekan. Tidak ada tekan yang hanya menghapus "/", kursor tidak tersangkut di depan "/".

### H1-4 Mengedit di tengah (AC4)
**Langkah:**
1. Isi `17/08/1990`.
2. Ketuk tepat setelah `08` (sebelum "/" kedua), tekan hapus dua kali → `17/1990` tampil sebagai `17/19/90`. Catat yang tampil.
3. Ketik `09`.
**Hasil:** `17/09/1990`. Kursor berada setelah `09`.
**Variasi:** ketuk tepat setelah "/" pertama lalu hapus satu kali → angka `7` terhapus (kursor setelah "/" dianggap sebelum angka berikutnya, hapus mengenai angka sebelumnya). Catat perilaku aktual.

### H1-5 Tempel dan karakter non-angka (AC5)
**Langkah:**
1. Di field "Full name", ketik `17/08/1990`, pilih semua, "Copy" (menu teks ORVYN).
2. Field "Date of birth" kosong: tekan lama, "Paste" → `17/08/1990`.
3. Kosongkan, ulangi dengan `17081990` → `17/08/1990`.
4. Ulangi dengan `17-08-1990` dan `17.08.1990` → `17/08/1990`.
5. Kosongkan, `adb shell input text "17/08/1990"` (meniru keyboard yang punya "/") → `17/08/1990`.
6. Tempel `1708199012345` ke field kosong → `17/08/1990` (8 angka pertama).
7. Kembalikan "Full name" ke nama uji.
**Hasil:** semua tampil `17/08/1990`, tidak ada "/" ganda, tidak ada huruf.

### H1-6 Helper tetap tampil (AC6)
**Langkah:** amati field di keadaan kosong, sedang diisi (`1708`), dan lengkap (`17/08/1990`).
**Hasil:** helper `DD/MM/YYYY` tampil di ketiga keadaan, di bawah field, teks persis. Label `Date of birth` persis.

---

## 6. H2 Tanggal lahir diperiksa dan tersimpan

### H2-1 Tanggal tidak ada atau belum lengkap (AC1)
**Tempat:** HP, D1. **Kondisi awal:** "About you", semua field lain lengkap dan valid.
**Langkah:**
1. Isi `31021990`, tekan Continue → error `Use the format DD/MM/YYYY.` dengan ikon peringatan, helper hilang, tetap di langkah ini.
2. Ganti ke `1708199` (7 angka), Continue → error yang sama.
3. Kosongkan, Continue → error yang sama.
4. Isi `29022000` (tahun kabisat, valid) → error hilang saat diedit, Continue lanjut. Kembali dengan tombol kembali header untuk skenario berikutnya.
**Hasil:** teks error persis, tidak lanjut pada langkah 1–3.

### H2-2 Umur di bawah 17 (AC2)
**Langkah:** tanggal hari ini 16/10/2026.
1. `16102016` (10 tahun lalu), Continue → `You need to be at least 17 to open an account.`, tidak lanjut.
2. Batas: `17102009` (17 tahun kurang satu hari) → error umur.
3. Batas: `16102009` (tepat 17 tahun hari ini) → lanjut.
**Hasil:** teks error persis. Batas 17 tahun sama dengan 0.8.0.

### H2-3 Tanggal valid membawa ke langkah berikutnya (AC3)
**Langkah:** isi semua field valid, `17081990`, Continue.
**Hasil:** layar "Photo of your KTP" (Step 4 of 8) terbuka.

### H2-4 Kembali ke langkah "About you" (AC4)
**Langkah:**
1. Dari "Photo of your KTP", tekan tombol kembali di header → "About you" menampilkan `17/08/1990`, helper tampil, tanpa error.
2. Tekan Continue tanpa mengetik → lanjut ke "Photo of your KTP".
3. Tutup ORVYN dari layar ini (pindah ke recent apps tidak dipakai; cukup `adb shell am force-stop id.orvyn.app`), buka lagi ORVYN (`adb shell monkey -p id.orvyn.app 1` atau `am start`) → draft dilanjutkan.
4. Bila layar pembuka adalah "Photo of your KTP", tekan kembali di header → "About you" menampilkan `17/08/1990`, Continue langsung bisa.
**Hasil:** tanggal tersimpan tampil `DD/MM/YYYY`, bukan ISO, bukan angka tanpa "/". Lihat juga E4.

### H2-5 Tanggal di admin tidak bergeser (AC5)
**Tempat:** HP + admin dev. **Data:** D1 (nomor pertama).
**Langkah:**
1. Selesaikan pendaftaran dengan `17/08/1990`: "Skip this step" di foto KTP dan selfie, buat password, PIN, ulangi PIN.
2. Buka admin dev, Registrations, buka pendaftaran ini.
3. Cek zona waktu proses server dev (`date`, variabel `TZ`) dan catat. Ulangi langkah 2 dengan server dev dijalankan `TZ=Asia/Jakarta` bila zona waktunya berbeda, dan cek juga zona waktu server produksi (hanya baca, tanpa deploy).
**Hasil:** "Date of birth" `1990-08-17` di semua zona waktu.
**Catatan dari membaca kode:** admin menampilkan `r.birth_date?.toISOString().slice(0, 10)` di `server/src/admin-registrations.ts`. Bila kolom bertipe `date` dibaca sebagai tengah malam waktu lokal server, pada zona waktu di depan UTC (WIB) hasilnya bisa `1990-08-16`. Ini yang diwaspadai PRD bagian 8.

---

## 7. H3 Quick Amount deposito

### H3-1 Nilai, urutan, label, dan Next aktif (AC1–AC4)
**Tempat:** HP. **Data:** D2 Raka (lalu D3 Hendra untuk 50M bila saldo Raka kurang). **Kondisi awal:** masuk, Wealth atau menu deposito, "Open a deposit".
**Langkah:**
1. Screenshot layar → tepat empat chip: `1M`, `5M`, `10M`, `50M`, kiri ke kanan, tidak ada chip di bawah 1M, tidak ada `1000k`.
2. Ketuk `1M` → angka besar `Rp1,000,000`, chip `1M` terpilih (warna `primary`), Next aktif.
3. Ulangi untuk `5M`, `10M`, `50M` → angka `Rp5,000,000`, `Rp10,000,000`, `Rp50,000,000`, Next aktif setiap kali.
4. Hapus satu digit dengan keypad dari `Rp1,000,000` → `Rp100,000`, chip tidak ada yang terpilih, Next tidak aktif (perilaku 0.8.0).
**Hasil:** sesuai H3 AC1–AC4 dan `design.md` 2.1, 2.2, 2.4. Ulangi screenshot di tema Light dan Dark ORVYN.

### H3-2 Membuka deposito dari Quick Amount (AC3, buku besar)
**Data:** D2. **Langkah:**
1. Catat status buku besar di admin dev ("✓ Balanced") dan saldo Everyday Raka.
2. Ketuk `1M`, Next, pilih tenor, review, PIN benar.
3. Bukti transaksi tampil. Saldo Everyday berkurang Rp1,000,000, deposito baru muncul.
4. Admin dev: buku besar tetap "✓ Balanced", transaksi tercatat.
**Hasil:** deposito Rp1,000,000 berhasil, buku besar OK.

---

## 8. H4 Quick Amount Fund, Bond, dan Gold

### H4-1 Fund, beli (AC1, AC3)
**Tempat:** HP. **Data:** D3 Hendra. **Kondisi awal:** profil risiko sudah ada.
**Langkah:** untuk **setiap** Fund (empat produk):
1. Buka produk, cek "Smallest purchase" `Rp100,000`.
2. Buy → chip tepat `100k`, `250k`, `500k`, `1M`, urutan itu.
3. Ketuk tiap chip → nominal sama persis (`Rp100,000` … `Rp1,000,000`), tombol `Buy Rp…` aktif (produk sesuai profil).
4. Untuk satu Fund: beli dengan chip `100k` sampai bukti, cek buku besar "✓ Balanced".
**Hasil:** tidak ada chip di bawah minimum, semua chip membuat Buy aktif.

### H4-2 Bond, beli (AC2, AC3)
**Data:** D3. **Langkah:** untuk ORI-D01 dan SR-D01:
1. Buy → chip tepat `1M`, `2M`, `5M`, `10M`. Teks "Bonds are bought in steps of Rp1,000,000." tetap ada.
2. Ketuk tiap chip → `Rp1,000,000`, `Rp2,000,000`, `Rp5,000,000`, `Rp10,000,000`, Buy aktif.
3. Untuk satu Bond: beli `1M` sampai bukti, cek buku besar "✓ Balanced".
**Hasil:** semua chip ≥ Rp1,000,000 dan kelipatan Rp1,000,000, Buy aktif untuk semuanya. Bond tidak punya tombol Sell.

### H4-3 Produk di atas profil risiko (AC3)
**Data:** D4. **Langkah:**
1. Buka produk bertanda "Above your profile", Buy.
2. Ketuk tiap chip → nominal terisi, Buy **tidak** aktif.
3. Centang "I understand this is riskier than my … profile and I could lose money." → Buy aktif untuk tiap chip.
**Hasil:** sesuai AC3. Tidak perlu menyelesaikan pembelian.

### H4-4 Gold Savings dan jual (AC4)
**Data:** D3 (punya Holding Fund dan emas). **Langkah:**
1. Gold Savings, Buy → chip `50k`, `100k`, `250k`, `500k`. Ketuk masing-masing → nominal sama, Buy aktif.
2. Gold Savings, Sell → chip `50k`, `100k`, `250k`, `500k`.
3. Fund dengan Holding, Sell → chip `100k`, `250k`, `500k`, `1M`.
4. Pada layar jual yang Holding-nya lebih kecil dari salah satu chip (bila tidak ada, catat nilai Holding dan lewati dengan alasan): ketuk chip yang lebih besar → nominal menjadi nilai Holding, bukan nilai chip.
**Hasil:** sama dengan 0.8.0 kecuali nilai chip Fund. Tidak perlu menyelesaikan penjualan.

### H4-5 Label juta dan tampilan (AC5)
**Langkah:** screenshot layar beli Fund dan Bond di tema Light dan Dark ORVYN.
**Hasil:** label `1M`, `2M`, `5M`, `10M`, tidak ada `1000k`. Ukuran huruf terbesar diuji di E5.

---

## 9. H5 Layar nominal lain

### H5-1 Chip tetap 50k sampai 500k di layar lain (AC1, AC2)
**Tempat:** HP. **Data:** D2 Raka, D3 Hendra untuk kartu kredit.
**Langkah:** di setiap layar berikut, screenshot chip lalu ketuk keempatnya, catat nominal yang terisi:

| Layar | Cara masuk | Minimum | Nasabah |
|---|---|---|---|
| Transfer | Transfer, pilih penerima | Rp10,000 | Raka |
| QRIS nominal diketik | QR merchant dev tanpa nominal. Kamera tidak dipakai dan galeri pemilik tidak dibuka, jadi hanya bila ORVYN punya jalur masuk QR tanpa keduanya (dicek di laporan IT) | Rp1,000 | Raka |
| Top up e-wallet | Pay and buy, e-wallet | Rp10,000 | Raka |
| Kantong, masuk | Pockets, pilih kantong, tambah | tidak ada | Raka |
| Kantong, keluar | Pockets, pilih kantong, ambil | tidak ada | Raka |
| Minta uang | Request money | Rp1,000 | Raka |
| Split bill, total | Split bill | Rp1,000 | Raka |
| Split bill, bagian teman | Split bill, ubah bagian | Rp1,000 | Raka |
| Kartu, "Another amount" | Cards, kartu kredit, Pay | Rp10,000 | Hendra |
| Anggaran | Insights, budget | Rp10,000 | Raka |

**Hasil:** semua layar menampilkan `50k`, `100k`, `250k`, `500k`, nominal yang terisi sama persis, tidak ada chip di bawah minimum layar. Bila jalur QRIS nominal diketik tidak bisa dicapai tanpa kamera atau aplikasi lain, dicatat sebagai "tidak diuji di HP" dan diganti review kode pemanggil `AmountDisplay` di `QrisFlow.kt`.

### H5-2 Pesan batas saldo tetap sama (AC3)
**Langkah:**
1. Hendra, kartu kredit, "Another amount", ketuk chip lebih besar dari sisa tagihan (bila tagihan ≥ 500k, ketik nominal lebih besar dulu lalu bandingkan) → `That's more than you owe (Rp…).`
2. Raka, kantong keluar dari kantong bersaldo kecil, ketuk `500k` → `You have Rp… to move.`
3. Raka, Transfer, chip lebih besar dari saldo (bila saldo besar, pakai nasabah dengan saldo kecil) → pesan saldo 0.8.0.
**Hasil:** teks pesan sama persis dengan 0.8.0 (bandingkan dengan kode 0.8.0 lewat `git show`).

### H5-3 Transfer dan QRIS sampai bukti, buku besar OK (AC4)
**Data:** D2. **Langkah:**
1. Admin dev: buku besar "✓ Balanced".
2. Transfer ke sesama ORVYN dengan chip `50k`, PIN benar → bukti transaksi.
3. QRIS dengan chip `50k` (merchant dev), PIN benar → bukti transaksi. Bila QR tidak bisa dibaca tanpa kamera atau galeri pemilik, langkah ini dicatat "tidak diuji di HP" dan orkestrator diberi tahu (QRIS bisa diuji pemilik sendiri).
4. Admin dev: dua transaksi tercatat, buku besar tetap "✓ Balanced".
**Hasil:** keduanya berhasil, saldo turun sesuai, buku besar OK.

---

## 10. Edge case

### E1 Dobel ketuk di keypad tanggal lahir
**Tempat:** HP, D1. **Langkah:** ketik `17` lalu ketuk tombol `0` dua kali sangat cepat (dua `input tap` berurutan tanpa jeda).
**Hasil:** `17/00` tampil (dua angka masuk, sesuai keinginan pengguna), tidak ada "/" ganda, kursor di akhir.

### E2 Tanggal tak masuk akal yang diterima seperti 0.8.0
**Langkah:** `00001990`, `17131990`, `17080000` → error format. `17081800` → mengikuti 0.8.0 (PRD: tahun 1800 di luar lingkup), dicatat saja.
**Hasil:** tidak ada crash, perilaku sama dengan 0.8.0.

### E3 Offline di langkah tanggal lahir dan saat kirim
**Tempat:** HP, D1. **Langkah:** hentikan dev server (bukan pengaturan HP), isi tanggal lahir dan Continue → lanjut (langkah ini tidak memanggil server). Selesaikan sampai kirim PIN → pesan error jaringan 0.8.0, draft tetap ada. Nyalakan server, kirim ulang → berhasil.
**Hasil:** format tanggal tetap bekerja offline, tidak ada crash, draft tidak hilang.

### E4 Pendaftar yang kembali dengan draft tersimpan dari 0.8.0
**Tempat:** HP, D1 (nomor baru). **Tujuan:** draft yang disimpan versi lama tetap terbaca di 0.8.1.
**Langkah:**
1. Pasang build 0.8.0 (release lama) bila bisa dipasang berdampingan dengan tanda tangan yang sama; isi pendaftaran sampai lewat "About you" dengan tanggal yang bisa diketik di 0.8.0 (`input text "17/08/1990"`), tutup ORVYN.
2. Pasang debug 0.8.1 di atasnya tanpa menghapus data (bila tanda tangan berbeda, langkah ini tidak bisa; catat dan ganti dengan draft yang dibuat 0.8.1 seperti H2-4).
3. Buka ORVYN → draft dilanjutkan; kembali ke "About you" → `17/08/1990`, Continue bisa langsung ditekan.
**Hasil:** draft lama dengan `birthDate` ISO tampil benar.

### E5 Ukuran huruf terbesar, terang dan gelap (H3 AC5, H4 AC5, design 1.4 dan 2.3)
**Tempat:** Robolectric/Roborazzi (wajib), HP (hanya bila pemilik sendiri mengubah ukuran huruf).
**Langkah:** render `fontScale = 2f` pada `w360dp` dan `w411dp-h869dp-420dpi`, tema terang dan gelap:
1. `AmountDisplay` dengan nilai deposito, Fund, Bond, dan default.
2. Field "Date of birth" berisi `17/08/1990`, dengan helper dan dengan kedua error.
**Hasil:** chip turun ke baris kedua bila tidak muat, rata tengah, tidak ada chip terpotong, menyusut, atau keluar layar, tinggi chip ≥ 48dp. `17/08/1990` utuh di field, helper dan error membungkus ke baris berikut. Bila keypad terdorong keluar di layar pendek, dicatat sebagai keadaan 0.8.0 (di luar lingkup, `design.md` 2.3) kecuali chip ikut terpotong.

### E6 TalkBack: label dan state chip (H3 AC6, design 2.5)
**Tempat:** HP (`uiautomator dump`), ditambah test semantics bila ada.
**Langkah:**
1. "Open a deposit", ketuk `1M`, `adb shell uiautomator dump` lalu tarik XML-nya.
2. Cek setiap chip: `content-desc` = `Rp1,000,000`, `Rp5,000,000`, `Rp10,000,000`, `Rp50,000,000`; chip `1M` `selected="true"`, lainnya `false`; `clickable="true"`.
3. Ulangi di Fund (`Rp100,000` … `Rp1,000,000`), Bond, dan Transfer (`Rp50,000` …, membuktikan label penuh berlaku di semua layar).
4. Cek chip lain yang memakai komponen `Chip` (tenor deposito, Renew dan Pay out, rentang grafik) kini punya `selected` yang benar dan tampilannya tidak berubah.
5. Field "Date of birth": node berisi teks `17/08/1990` dan label `Date of birth`; saat error, teks error ada di node.
**Hasil:** sesuai `design.md` 1.4 dan 2.5. Laporan menyebut bahwa ucapan TalkBack asli tidak didengar bila pemilik tidak menyalakannya.

### E7 Saldo tidak cukup
**Data:** Raka (atau nasabah bersaldo kecil). **Langkah:** "Open a deposit", ketuk `50M` dengan saldo Everyday di bawah Rp50,000,000, lanjut sampai PIN.
**Hasil:** server menolak dengan pesan 0.8.0, tidak ada deposito dibuat, saldo tidak berubah, buku besar "✓ Balanced".

### E8 PIN salah
**Data:** Raka. **Langkah:** deposito dengan chip `1M`, PIN salah sekali.
**Hasil:** pesan PIN salah 0.8.0, kembali ke layar nominal (atau tetap di PIN, sesuai 0.8.0) dengan nominal `Rp1,000,000` masih terisi, tidak ada uang bergerak. Tidak dicoba sampai terkunci.

### E9 Dobel ketuk pada chip dan tombol lanjut
**Data:** Hendra. **Langkah:**
1. Layar beli Bond, ketuk `2M` dua kali cepat → nominal tetap `Rp2,000,000` (bukan `Rp4,000,000`), chip tetap terpilih.
2. Ketuk `1M` lalu `5M` cepat → nominal `Rp5,000,000`, hanya `5M` terpilih.
3. "Open a deposit", `1M`, Next diketuk dua kali cepat → hanya satu langkah maju (layar tenor), tidak melompat.
4. Di konfirmasi PIN deposito, PIN benar dikirim lalu tombol/aksi diulang cepat → hanya satu deposito dan satu Journal Entry (kunci idempoten), buku besar "✓ Balanced".
**Hasil:** tidak ada nominal berlipat, tidak ada transaksi ganda.

### E10 Fitur dimatikan
**Tempat:** admin dev + HP. **Langkah:**
1. Matikan `deposits`, refresh aplikasi (tarik beranda/Wealth) → pintu masuk deposito hilang, tanpa crash. Nyalakan lagi.
2. Matikan `invest` → layar investasi hilang. Nyalakan lagi.
3. Matikan `register`, keluar ke layar Welcome → "Open an account" mengikuti perilaku 0.8.0. Nyalakan lagi.
**Hasil:** perilaku sama dengan 0.8.0, semua fitur dinyalakan lagi di akhir.

### E11 Tema terang dan gelap
**Langkah:** setiap screenshot di H1-6, H2-1 (error), H3-1, H4-1, H4-2, H5-1 diambil di tema "Light" dan "Dark" ORVYN.
**Hasil:** chip terpilih dan tidak terpilih bisa dibedakan, teks error terbaca, tidak ada warna baru di luar token.

---

## 11. Urutan di HP

1. A1 di laptop.
2. Pasang debug build, `adb reverse`, catat tema ORVYN semula.
3. Pendaftaran (nomor baru 1): H1-1 sampai H1-6, E1, E2, H2-1, H2-2, H2-3, H2-4, lalu H2-5 sampai terkirim.
4. Pendaftaran (nomor baru 2): E3 offline. Nomor baru 3 cadangan untuk E4.
5. Masuk sebagai Raka: H3-1, E8, H3-2, E7, H5-1, H5-2, H5-3, E6 (deposito, Transfer).
6. Keluar, masuk sebagai Hendra: H3-1 (50M), H4-1 sampai H4-5, E9, H5-1 dan H5-2 (kartu), E6 (Fund, Bond).
7. E10 di admin dev, E11 bersamaan dengan langkah di atas.
8. E5 di Robolectric.
9. Selesai: kembalikan tema, pasang `releases/orvyn.apk`, `adb reverse --remove tcp:8790`.

Setiap screenshot disimpan di `docs/features/2026-10-16-hotfix-dob-pills/qa-shots/` dengan status bar dipotong, dan direferensikan di `qa-report.md`.
