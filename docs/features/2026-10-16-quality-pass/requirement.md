# Requirement: quality pass 0.9.0

Sumber: tim bisnis, 16 Okt 2026 malam. Temuan: `ui-audit.md` (CX, 46 temuan UI-01..UI-46) dan `e2e-report.md` (QA, 39 temuan E2E-01..E2E-39, regresi penuh 0.8.1).

## Kata-kata tim bisnis

> "saya ingin kamu melakukan 2 hal, coba test lagi seluruh flow end to end. make sure ga ada defect atau bugs. in parallel audit UI juga menggunakan skill impeccable. hasilnya tolong dijadikan requirement perbaikan. tidak perlu menunggu keputusan dari saya dan menjadikannya blocker. kamu decide saja yg kamu rekomendasikan. saya akan tidur dulu, saya akan cek lagi setelah bangun"

Artinya: semua keputusan di bawah diambil orkestrator atas nama tim bisnis (didelegasikan), dan dicatat di `docs/DECISIONS-PENDING.md` bagian "For your information" supaya bisa dibatalkan setelah tim bisnis bangun. **Rilis ke produksi tetap menunggu persetujuan tim bisnis** (CLAUDE.md langkah 7). Itu gerbang terakhir, bukan penghambat kerja.

## Masalah

0.8.1 aman untuk uang (buku besar selalu Balanced, tidak ada kirim ganda), tapi belum bebas defect:
1. Font 200% di HP kecil mematahkan pembuatan PIN, keypad nominal, dan layar sukses; label dan saldo patah di tengah kata/angka.
2. Kegagalan yang diam: aksi gagal saat offline tanpa pesan, loading selamanya, tidak ada "Try again", tidak ada tanda saat PIN sedang diproses.
3. Celah: tebak PIN tanpa batas di Ganti PIN/password, Reset demo rusak bila ada passkey.
4. Hambatan daftar dan masuk: error baru muncul di langkah terakhir, pesan PIN tidak cocok tidak tampil, field password tertutup keyboard.
5. Sistem visual tidak konsisten (chip, huruf Roboto, kontras, transisi) dan beberapa AC story lama belum dibangun (S1.9, S1.10 AC3, S1.16 AC1, S1.20, S3.8, S2.6).

## Untuk siapa

Semua nasabah demo (termasuk yang memakai font besar, TalkBack, dan HP 360dp), penguji yang menerima APK dari tim bisnis, dan petugas admin.

## Yang dianggap berhasil

- Setiap temuan dalam cakupan di bawah tertutup dan diverifikasi QA di HP dan di render 200% (360×640dp dan 411dp, terang dan gelap).
- QA regresi penuh ulang: **0 blocker, 0 major**, minor yang tersisa hanya yang sengaja ditunda di bawah. Putusan READY FOR RELEASE.
- Buku besar tetap Balanced, tidak ada izin Android baru, tidak ada data perangkat (ADR 0001, 0002).

## Cakupan 0.9.0 (keputusan orkestrator)

Prinsip dari audit CX: perbaiki komponen bersama sekali (PinPad, AmountDisplay, OrvynButton, Chip, MessageState, ListRow, tipografi, transisi), maka banyak layar ikut beres. Tim bisnis minta "tidak ada defect", jadi **semua temuan E2E masuk**, dan audit UI masuk kecuali yang ditunda di bawah.

### A. Font besar (blocker dulu)
E2E-01, E2E-10, F4, UI-01, UI-02, UI-03. Layar PIN, layar nominal, dan layar sukses: konten atas bisa digulir, keypad/tombol utama menempel di bawah. Nominal mengecil agar muat satu baris. Aksi cepat, ListRow, tombol, bottom nav, banner, kartu kantong tahan 200%.

### B. Tidak ada kegagalan yang diam
E2E-09, E2E-37 (F5), E2E-38, UI-04, UI-05, UI-06, UI-07, E2E-08, E2E-13, E2E-21, E2E-28, UI-37. Termasuk layar sukses untuk pencairan deposito lebih awal, Total assets ikut refresh, banner/insight ikut refresh, maintenance langsung mengganti layar saat aktif, layar Unlock bisa "Try again" setelah dibuka petugas.

### C. Keamanan dan admin
E2E-02 (Ganti PIN/password menghitung percobaan salah, kunci di percobaan ke-5 seperti Unlock), E2E-03 (Reset demo menghapus passkeys), E2E-15 (server menolak penolakan pendaftaran tanpa alasan), E2E-16 (password awal petugas tidak lewat URL), E2E-39/UI-14 (FLAG_SECURE di PIN pad), E2E-35 (Ganti PIN memeriksa PIN lama dulu), UI-16 (konfirmasi aksi destruktif), UI-24 (status freeze kartu yang pasti).

### D. Daftar dan masuk
E2E-04, E2E-05, E2E-06, E2E-29, UI-17, UI-18, UI-33. NIK ganda diperiksa saat langkah About you (server endpoint cek NIK), PIN lemah ditolak di langkah Create PIN sebelum diulang.

### E. Sistem visual dan aksesibilitas
UI-10, UI-11, UI-12, UI-13, UI-19, UI-21, UI-22, UI-23, UI-25, UI-28, UI-29, UI-31, UI-32, UI-39..UI-45, E2E-07, E2E-31, E2E-32, E2E-36 (F3, ketukan ganda). MASTER.md ikut diperbarui.

### F. Celah story lama yang dibangun sekarang
- **E2E-11 / UI-09 (S1.9):** layar detail rekening dengan Share account details dan Copy number.
- **E2E-14 / UI-34 (S1.10 AC3):** filter riwayat Date dan Type, tarik untuk refresh, "Couldn't load more".
- **E2E-12 / UI-08:** Share receipt dan tanggal di layar sukses.
- **E2E-17 (S1.16 AC1):** nominal opsional di QR-ku.
- **E2E-18 (S1.20):** nomor sendiri terisi, operator tampil saat mengetik.
- **E2E-19 (S3.6, S3.8):** beli emas dalam gram; detail Fund/Gold menampilkan unit dan gram.
- **E2E-20 (S2.6):** bayar permintaan lewat layar review sebelum PIN; kartu inbox membuka permintaan dengan Pay/Decline.
- **E2E-34 (S1.35, S1.36):** filter jenis transaksi admin lengkap; banner punya tanggal mulai, urutan, dan bisa diedit.

### G. Copy dan detail
E2E-23, E2E-24, E2E-25, E2E-26, E2E-27, E2E-30, E2E-33, UI-20, UI-26, UI-27, UI-35, UI-36, UI-38.

## Keputusan yang diambil orkestrator (didelegasikan tim bisnis)

| # | Pertanyaan | Keputusan | Alasan |
|---|---|---|---|
| K1 | UI-15: posisi carousel promo | **Dipindah ke bawah transaksi terakhir.** Putar otomatis berhenti bila TalkBack aktif; ketuk pil aksi langsung menjalankan aksinya; ketukan tidak menggeser carousel (E2E-33) | Nasabah membuka aplikasi untuk melihat uangnya. Revolut, Monzo, Nubank menaruh promo di bawah |
| K2 | UI-09/E2E-11 dan UI-34/E2E-14: utang story S1.9 dan S1.10 | **Dibangun di 0.9.0** | Story sudah disetujui di Fase 1; PROGRESS mencatatnya ✅ padahal belum ada |
| K3 | UI-30: jalur kontak di Bantuan | **Tanpa nomor telepon.** Baris "Still need help?" dengan "Ask Vyna" yang membuka Vyna, plus chevron dan state expanded di FAQ | Nomor demo yang ditelepon bisa sampai ke orang sungguhan. Vyna sudah ada dan gratis untuk demo |
| K4 | E2E-22: sakelar Widget di admin (S2.19 AC3) | **AC diubah, tidak dibangun.** Widget hanya menampilkan saldo yang sudah tersimpan di HP dan tidak memanggil server, jadi sakelar server tidak punya efek | Membangun sakelar tanpa efek adalah kode mati |
| K5 | E2E-20: kartu inbox permintaan uang | Kartu inbox membuka detail permintaan yang punya Pay dan Decline; Pay lewat layar review lalu PIN | Konsisten dengan prinsip "review sebelum PIN" di semua alur uang |
| K6 | E2E-02: batas percobaan Ganti PIN/password | Memakai penghitung `failed_attempts` yang sama dengan Unlock: salah ke-5 mengunci nasabah, pesan "N tries left" | Satu aturan untuk semua tempat PIN dimasukkan |
| K7 | Versi | **0.9.0**, versionCode 12 | Banyak perubahan tampilan dan beberapa fitur kecil, bukan hotfix |

## Ditunda (tidak di 0.9.0)

- **UI-46** tablet dan landscape: HP demo hanya portrait.
- **S1.22** halaman biller di admin: sudah dicatat di PROGRESS sebagai keterbatasan yang diterima.
- Item yang tidak bisa diuji di HP pemilik (QRIS kamera sungguhan, widget di launcher, ketuk push dari panel notifikasi, suara Vyna): dibangun sesuai desain bila tersentuh, diverifikasi dengan test otomatis.

## Batasan

- Tidak ada izin Android baru (ADR 0002). Senter QRIS memakai `CameraControl.enableTorch` (izin kamera yang sudah ada).
- Uang hanya lewat Journal Entry (ADR 0001). Perubahan server (cek NIK, batas percobaan, reset, reject, banner) disertai test.
- Aplikasi dan admin berbahasa Inggris; copy final dari `ui-audit.md` sudah lolos humanizer.
- Produksi tidak disentuh sampai tim bisnis menyetujui rilis.
