# PRD: Hotfix 0.8.1, tanggal lahir dan Quick Amount

Sumber: `requirement.md` di folder ini (kata-kata tim bisnis, temuan orkestrator, keputusan tim bisnis). Story: `stories.md` (H1–H5). Istilah dari `CONTEXT.md`.

**Pertanyaan terbuka:** tidak ada. Semua keputusan sudah dijawab tim bisnis di `requirement.md`.

## 1. Ringkasan

Dua bug di produksi (0.8.0) yang ditemukan tim bisnis sendiri. Pertama, calon nasabah tidak bisa mengisi tanggal lahir saat daftar, jadi pendaftaran macet. Kedua, tombol nominal cepat (Quick Amount, disebut "pills" oleh tim bisnis) di layar deposito dan investasi menawarkan nominal yang ditolak aturan produknya sendiri. Rilis 0.8.1 memperbaiki keduanya tanpa fitur baru.

## 2. Masalah dan untuk siapa

| Bug | Siapa yang kena | Penyebab (temuan orkestrator) |
|---|---|---|
| Tanggal lahir tidak bisa diisi | Calon nasabah yang mendaftar dari aplikasi (Registration), langkah "About you" | Field memakai keyboard angka, tapi isinya harus berformat DD/MM/YYYY dengan "/". Keyboard angka Samsung dan Gboard tidak punya tombol "/". Pendaftaran tidak bisa lanjut. |
| Quick Amount di bawah minimum | Nasabah yang membuka Time Deposit, membeli Fund atau Bond | Semua layar nominal memakai Quick Amount yang sama: 50k, 100k, 250k, 500k. Deposito (min Rp1.000.000): keempatnya tidak valid. Fund (min Rp100.000): 50k tidak valid. Bond (min Rp1.000.000, kelipatan Rp1.000.000): keempatnya tidak valid. |

Bug pertama paling berat: calon nasabah baru tidak bisa menjadi Customer sama sekali.

## 3. Tujuan dan cara mengukurnya

1. Calon nasabah bisa mengisi tanggal lahir dengan keyboard angka bawaan HP dan lanjut ke langkah berikutnya. Diukur: QA mendaftar sampai selesai di Galaxy Note10+ (Samsung Keyboard) tanpa mengetik "/".
2. Tidak ada Quick Amount di layar mana pun yang nilainya ditolak oleh aturan produk di layar itu. Diukur: QA mengetuk setiap Quick Amount di setiap layar nominal (daftar di bagian 5.2) dan tombol lanjutnya aktif untuk semuanya.
3. Tidak ada yang rusak di layar lain. Diukur: semua test server dan aplikasi yang ada tetap lolos, dan uji regresi H5 lolos.

## 4. Lingkup

**Masuk:**
- Field "Date of birth" di langkah "About you" pendaftaran: garis miring muncul otomatis.
- Quick Amount per layar sesuai aturan produk layar itu, plus label yang terbaca untuk nominal Rp1.000.000 ke atas.
- Cek ulang semua layar lain yang memakai Quick Amount.

**Tidak masuk:**
- Pemilih tanggal berbentuk kalender (tim bisnis memilih ketik 8 angka).
- Mengubah teks error tanggal lahir yang sudah ada.
- Perubahan server, admin, database, atau aturan produk (minimum deposito, Fund, Bond tetap). Satu-satunya pengecualian ada di bagian 8 (tanggal lahir bergeser di admin, hanya bila H2 AC5 gagal).
- Batas yang bergantung pada keadaan nasabah, yaitu saldo tabungan, saldo kantong, sisa tagihan kartu, atau nilai Holding. Quick Amount yang lebih besar dari angka-angka ini tetap boleh tampil seperti sekarang, dan pesan error yang sudah ada tetap berlaku (misalnya "That's more than you owe"). Pada layar jual investasi, nominal tetap dipotong ke nilai Holding seperti di 0.8.0. "Aturan produk" di PRD ini berarti minimum dan kelipatan yang tetap.
- Validasi tahun yang tidak masuk akal (misalnya 1800). Perilaku 0.8.0 dipertahankan.

## 5. Solusi

### 5.1 Tanggal lahir (H1, H2)

- Nasabah cukup mengetik 8 angka. Aplikasi menambahkan "/" sendiri: `17081990` tampil `17/08/1990`.
- Keyboard tetap keyboard angka. Angka ke-9 dan seterusnya diabaikan. Bila ada "/" yang ikut masuk (ditempel, atau keyboard punya tombol "/"), hasilnya sama dengan mengetik angkanya saja.
- Menghapus terasa biasa: satu tekan tombol hapus menghilangkan satu angka, tanpa perlu menghapus garis miring tersendiri. Kursor bisa diletakkan di tengah untuk mengganti hari, bulan, atau tahun.
- Validasi tetap sama dan memakai teks yang sudah ada:
  - Tanggal tidak lengkap atau tidak ada di kalender (misalnya `31/02/1990`): "Use the format DD/MM/YYYY."
  - Umur di bawah 17 tahun: "You need to be at least 17 to open an account."
- Helper "DD/MM/YYYY" di bawah field tetap ada.
- Pendaftar yang kembali ke langkah ini (tanggal lahirnya sudah tersimpan) melihat tanggalnya dalam format DD/MM/YYYY dan bisa langsung lanjut.
- Data yang dikirim ke server tidak berubah (tanggal ISO `YYYY-MM-DD`), jadi admin tetap menampilkan tanggal yang sama.

### 5.2 Quick Amount per layar (H3, H4, H5)

Aturan umum: **setiap Quick Amount yang tampil di sebuah layar harus diterima aturan produk layar itu.** Mengetuknya mengisi nominal, dan tombol lanjut di layar itu aktif (selama syarat lain di layar itu, misalnya pernyataan risiko, sudah terpenuhi).

| Layar | Aturan produk | Quick Amount |
|---|---|---|
| Buka deposito (Time Deposit) | min Rp1.000.000 | **Tepat 1jt, 5jt, 10jt, 50jt** (keputusan tim bisnis) |
| Beli dan jual Fund | min beli = minimum produk (Rp100.000 untuk keempat Fund) | Ditentukan CX. Semua ≥ minimum produk. |
| Beli Bond | min beli = minimum produk (Rp1.000.000) dan kelipatan Rp1.000.000 | Ditentukan CX. Semua ≥ minimum produk dan kelipatan Rp1.000.000. |
| Beli dan jual Gold Savings | min beli Rp50.000 | Tetap 50k, 100k, 250k, 500k (sudah valid) |
| Transfer | min Rp10.000 | Tetap |
| QRIS (nominal diketik) | min Rp1.000 | Tetap |
| Top up e-wallet | min Rp10.000 | Tetap |
| Pindah uang kantong (Pocket) | tidak ada minimum produk | Tetap |
| Minta uang, split bill (total dan bagian teman) | min Rp1.000 | Tetap |
| Bayar tagihan kartu, "Another amount" | min Rp10.000 | Tetap |
| Anggaran (budget) | min Rp10.000 | Tetap |

**Label:** nominal di bawah Rp1.000.000 tetap seperti sekarang (mis. "50k"). Nominal Rp1.000.000 ke atas memakai label juta yang pendek dan terbaca (bukan "1000k"). Bentuk persisnya (mis. "1M") ditentukan CX di `design.md`, dalam bahasa Inggris. Pembaca layar (TalkBack) membacakan nominal sesuai `design.md`.

**Tampilan:** empat Quick Amount dalam satu baris harus tetap terbaca utuh di layar kecil dan di ukuran huruf terbesar HP. Cara menampilkannya (mis. pindah baris) ditentukan CX.

### 5.3 Admin

Tidak ada perubahan admin.

## 6. Dampak buku besar (ADR 0001)

Tidak ada. Tidak ada Journal Entry baru, tidak ada perubahan pada cara uang bergerak, dan validasi server (minimum deposito, Fund, Bond, transfer, dan lainnya) tidak diubah. Server tetap menjadi penjaga terakhir: bila aplikasi salah mengirim nominal, server menolaknya seperti sekarang.

## 7. Dampak privasi (ADR 0002)

Tidak ada. Tidak ada izin Android baru dan tidak ada data perangkat baru. Tanggal lahir sudah dikumpulkan sejak story S1.1 dan cara menyimpannya tidak berubah.

## 8. Risiko dan asumsi

| Risiko atau asumsi | Penanganan |
|---|---|
| Format otomatis bisa salah menaruh kursor di beberapa keyboard (Samsung Keyboard, Gboard). | H1 diuji di Note10+ dengan keyboard bawaannya, termasuk menghapus dan mengedit di tengah. |
| Komponen Quick Amount dipakai di 11 tempat. Mengubahnya bisa merusak layar lain. | Story regresi H5 mencakup semua layar di tabel 5.2. |
| Label juta yang lebih panjang membuat baris Quick Amount tidak muat. | Kriteria tampilan di H3 dan H4 (ukuran huruf terbesar). |
| Admin menampilkan tanggal lahir lewat konversi tanggal ke teks. Bila zona waktu server bukan UTC, tanggal bisa bergeser satu hari. | H2 AC5 mengeceknya. Bila gagal, itu bug lama yang ikut diperbaiki IT, satu-satunya alasan server boleh berubah di hotfix ini. |
| Teks "Use the format DD/MM/YYYY." untuk `31/02/1990` agak kurang tepat. | Diterima: tim bisnis meminta error yang ada tetap dipakai. |
| Asumsi: minimum Fund dan Bond tidak bisa diubah petugas dari admin (memang belum ada fiturnya). | Bila suatu saat bisa diubah, Quick Amount harus mengikuti minimum produk (aturan di 5.2 sudah ditulis begitu). |

## 9. Rencana rilis

- Versi **0.8.1**, versionCode **11**. Hanya aplikasi Android yang berubah. Server hanya berubah bila H2 AC5 gagal (bagian 8).
- Urutan: CX (`design.md`, untuk label juta dan Quick Amount Fund/Bond), IT, QA. Setelah QA menyatakan READY FOR RELEASE, orkestrator langsung merilis tanpa menunggu persetujuan tambahan (keputusan tim bisnis no. 4): build release, `releases/orvyn-0.8.1.txt`, perbarui APK dan link unduhan, cek link, perbarui `docs/PROGRESS.md`.
- Tidak ada migrasi data. Nasabah 0.8.0 cukup memasang APK baru.
