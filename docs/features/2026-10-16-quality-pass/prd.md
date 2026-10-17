# PRD: Quality pass 0.9.0

Sumber: `requirement.md` di folder ini (cakupan A–G, keputusan K1–K7, yang ditunda, batasan), temuan QA di `e2e-report.md` (E2E-01..E2E-39), temuan CX di `ui-audit.md` (UI-01..UI-46), dan temuan lama F3, F4, F5 di `docs/features/2026-10-16-hotfix-dob-pills/qa-report.md`. Story: `stories.md` (Q-A1 sampai Q-G11). Istilah dari `CONTEXT.md`.

**Pertanyaan terbuka:** tidak ada. K1–K7 sudah final (didelegasikan tim bisnis ke orkestrator). Pilihan yang masih tersisa saya putuskan sebagai PO di bagian 4, dengan alasannya. Semuanya bisa dibatalkan tim bisnis sebelum rilis.

## 1. Ringkasan

Versi 0.8.1 aman untuk uang. Buku besar selalu Balanced dan tidak ada transaksi ganda. Tapi aplikasinya belum bebas defect. Font besar di HP kecil membuat PIN tidak bisa dibuat, beberapa aksi gagal tanpa pesan, PIN bisa ditebak tanpa batas di Ganti PIN, Reset demo rusak bila ada passkey, dan beberapa story Fase 1 sampai 3 yang disetujui ternyata belum dibangun. Rilis 0.9.0 "quality pass" menutup semua temuan itu. Caranya terutama dengan memperbaiki komponen bersama sekali (PinPad, layar nominal, layar sukses, AmountDisplay, OrvynButton, Chip, MessageState, ListRow, tipografi, transisi), sehingga banyak layar ikut beres. Fitur baru hanya utang story lama: detail rekening, filter riwayat, bagikan bukti, QR-ku dengan nominal, pulsa, emas dalam gram, bayar permintaan lewat layar periksa, dan perbaikan admin.

## 2. Masalah dan untuk siapa

| Masalah | Siapa yang kena | Temuan |
|---|---|---|
| Font 200% di HP 360dp mematahkan layar PIN, layar nominal, dan layar sukses. Saldo dan label patah di tengah angka atau kata | Nasabah lansia atau berpenglihatan rendah, semua pemakai HP kecil | E2E-01, E2E-10, F4, UI-01..UI-03 |
| Kegagalan yang diam: aksi gagal saat offline tanpa pesan, loading selamanya, tidak ada "Try again", tidak ada tanda saat PIN diproses, pencairan deposito berhasil tanpa konfirmasi | Semua nasabah, terutama di sinyal lemah | E2E-08, E2E-09, E2E-13, E2E-21, E2E-28, E2E-37, E2E-38, UI-04..UI-07, UI-37 |
| Celah keamanan: tebak PIN tanpa batas di Ganti PIN dan Ganti password, PIN pad bisa direkam layar, Reset demo gagal bila ada passkey, password awal petugas lewat URL, penolakan pendaftaran tanpa alasan diterima server | Nasabah yang HP-nya dipegang orang lain, petugas Super Admin | E2E-02, E2E-03, E2E-15, E2E-16, E2E-35, E2E-39, UI-14, UI-16, UI-24 |
| Hambatan daftar dan masuk: NIK ganda dan PIN lemah baru ditolak di langkah terakhir, pesan PIN tidak cocok tidak tampil, field password tertutup keyboard | Calon nasabah dan setiap penguji di masuk pertama | E2E-04..E2E-06, E2E-29, UI-17, UI-18, UI-33 |
| Sistem visual tidak konsisten (chip terbalik di mode gelap, huruf Roboto, kontras, transisi) dan label TalkBack yang kurang | Semua nasabah, pengguna TalkBack | Kelompok E |
| Story lama belum dibangun: S1.9, S1.10 AC3, S1.16 AC1, S1.20, S2.6, S3.6 AC5, S3.8 AC2, S1.35 AC1, S1.36 AC1 | Nasabah dan petugas | Kelompok F |
| Copy dan format kecil yang membingungkan | Semua nasabah | Kelompok G |

Paling berat: E2E-01 (blocker). Nasabah dengan font besar di HP kecil tidak bisa membuat PIN, jadi tidak bisa memakai aplikasi sama sekali.

## 3. Tujuan dan cara mengukurnya

1. **Setiap temuan dalam cakupan tertutup.** Diukur: setiap baris di tabel jejak (bagian 6) punya story, dan setiap kriteria penerimaan story itu lolos QA, di HP (Galaxy Note10+) dan di render font 200% (360×640dp dan 411×869dp, terang dan gelap).
2. **Regresi penuh ulang: 0 blocker, 0 major.** Minor yang tersisa hanya yang sengaja ditunda (bagian 5). Putusan QA: READY FOR RELEASE.
3. **Uang tetap benar.** Buku besar dev Balanced di awal, setelah setiap transaksi uji, dan di akhir regresi. Tidak ada Journal Entry baru jenis apa pun selain yang sudah ada di 0.8.1.
4. **Privasi tetap.** Daftar izin di APK 0.9.0 sama persis dengan 0.8.1 (pemeriksaan izin saat build lolos), dan tidak ada data perangkat baru yang dikirim ke server.

## 4. Keputusan PO

K1–K7 ada di `requirement.md` dan tidak dibuka lagi. Keputusan di bawah ini menjawab pilihan yang tidak diatur K1–K7. Semuanya dicatat supaya bisa dibatalkan tim bisnis.

| # | Pilihan | Keputusan PO | Alasan |
|---|---|---|---|
| PO-1 | Prioritas F3 (ketukan ganda, minor di laporan QA) | **P0** | Dengan K6, satu digit yang terbawa ke PIN pad memakai satu jatah dari 5 percobaan. Nasabah bisa terkunci karena ketukan yang bukan kesalahannya |
| PO-2 | Cara cek NIK ganda di langkah About you | Endpoint pendaftaran baru yang hanya bisa dipanggil dengan token pendaftaran yang sudah lolos OTP. Jawabannya hanya "tersedia" atau "sudah terdaftar", tanpa data orang lain. Paling banyak 10 cek per pendaftaran. NIK tidak ditulis ke log server. Server tetap memeriksa lagi saat pendaftaran dikirim | Mencegah orang memakai endpoint ini untuk menebak NIK siapa saja yang jadi nasabah |
| PO-3 | Di mana aturan PIN lemah diperiksa | Di aplikasi, tepat setelah digit ke-6 di langkah pertama setiap layar pembuatan PIN (pendaftaran, masuk pertama, Ganti PIN, Forgot PIN). Aturannya sama dengan server ("all the same or in a row"). Server tidak berubah dan tetap menolak | Nasabah tahu sebelum mengulang PIN, dan server tetap penjaga terakhir |
| PO-4 | Urutan Ganti PIN | Langkah 1 PIN lama, diperiksa server saat itu juga dan dihitung ke `failed_attempts`. Baru setelah benar, PIN baru dua kali | E2E-35: 18 digit sebelum tahu PIN lama salah |
| PO-5 | Apa yang terjadi di percobaan salah ke-5 di Ganti PIN atau Ganti password | Sama dengan PIN salah ke-5 di pembayaran: nasabah terkunci (Lock), aplikasi menampilkan layar terkunci, dan hanya petugas yang bisa membukanya | K6: satu aturan untuk semua tempat PIN dimasukkan |
| PO-6 | Alur "Forgot PIN?" (UI-33) | Sheet → "Sign in with password" → masuk dengan nomor HP dan password di layar Sign in → langsung ke Create PIN. Server menerima PIN baru tanpa PIN lama hanya dalam 10 menit setelah masuk dengan password di sesi yang sama | Copy dari CX menjanjikan "then choose a new PIN", jadi alurnya harus benar-benar sampai ke PIN baru. Batas 10 menit mencegah sesi lama dipakai mengganti PIN |
| PO-7 | Pesan layar terkunci untuk nasabah yang dikunci petugas (E2E-23) | Satu pesan netral yang benar untuk kedua sebab (salah 5 kali atau dikunci petugas), copy dari CX. Server tidak perlu menyimpan alasan kunci | Lebih sedikit perubahan server, dan pesannya tidak pernah salah |
| PO-8 | Kapan aplikasi tahu maintenance menyala (E2E-21) | Pada jawaban server mana pun yang menyebut maintenance, saat aplikasi kembali ke depan, saat tarik untuk refresh, dan dengan cek `/config` setiap 60 detik selama aplikasi terbuka di depan | "Langsung mengganti layar" juga harus berlaku bila nasabah sedang diam di beranda |
| PO-9 | Detail rekening (S1.9) untuk rekening apa | Hanya Savings Account (satu-satunya yang punya nomor rekening). Pocket, FX Account, dan Time Deposit tetap memakai layarnya sendiri. Bila saldo disembunyikan di beranda, saldo di detail juga tersembunyi | Sesuai S1.9 ("membagikan nomor rekening") dan pilihan sembunyikan saldo yang diingat (S1.7 AC2) |
| PO-10 | Layar sukses mana yang punya "Share receipt" | Hanya yang memindahkan uang ke pihak lain: Transfer (sekarang), QRIS, Bill Payment, bayar Money Request. Baris "Date" ada di semua layar sukses. Sukses transfer terjadwal tidak punya Share receipt karena belum ada uang yang terkirim | Bukti hanya berguna bila ada pihak lain yang menerima uang |
| PO-11 | Pilihan "Type" di filter riwayat (UI-34 menyebut 5) | Transfers, QRIS, Bills, Cards, Pockets, ditambah **Wealth** (deposito, valas, investasi). Label akhir dari CX | Tanpa itu transaksi deposito, valas, dan investasi tidak bisa difilter |
| PO-12 | Beli emas dalam gram: Quick Amount | Mode gram tanpa Quick Amount. Mode Rupiah tetap 50k, 100k, 250k, 500k (H4) | Quick Amount adalah nominal Rupiah (CONTEXT); gram jarang dipilih cepat |
| PO-13 | Format kurs JPY (E2E-23) | Mata uang yang kursnya di bawah Rp1,000 per unit ditampilkan per 100 unit dengan 2 desimal, misalnya "100 JPY = Rp10,802.50". Kurs yang tersimpan dan hitungan tidak berubah | "Rp108.025" mudah dibaca seratus delapan ribu |
| PO-14 | Tombol "Review" di layar tukar valas yang langsung ke PIN (E2E-23) | Label tombol menyebut aksi dan nominal (pola "Send Rp500,000"), copy dari CX. Alurnya tetap langsung ke PIN, karena kurs dan jumlah sudah tampil di layar itu (S3.2 AC1) | Label tidak boleh menjanjikan layar yang tidak ada |
| PO-15 | Dialog batal permintaan uang "Cancel" vs "Cancel request" | Tombol tutup "Keep request", tombol destruktif "Cancel request" | Dua tombol berkata "Cancel" membingungkan |
| PO-16 | Baris "Savings and pockets" di Wealth (E2E-31) | Membuka detail rekening ORVYN Everyday (Q-F1) | Layar itu menampilkan saldo dan riwayat tabungan. Kantong tetap di beranda |
| PO-17 | Baris "Still need help?" di Bantuan bila Vyna dimatikan petugas | Baris disembunyikan | K3 tanpa nomor telepon; tombol ke fitur yang mati tidak boleh tampil (S1.37 AC3) |
| PO-18 | Tombol Freeze besar di bawah gambar kartu (saran benchmark di UI-24) | Tidak di 0.9.0. Freeze tetap sakelar (S1.24 AC1 tidak berubah), tapi statusnya pasti (Q-C6) dan terbaca TalkBack (Q-E5) | Requirement membatasi UI-24 ke "status freeze kartu yang pasti" |
| PO-19 | Catatan di layar minta uang (UI-35) | Catatan pindah ke langkah setelah nominal, seperti Transfer (S1.12 AC2). Aturan umum: tidak ada field teks di layar yang punya keypad nominal | Dua keyboard di satu layar adalah akar masalahnya |
| PO-20 | Judul riwayat kantong (UI-36) untuk transaksi lama | Judul dihitung saat dibaca, jadi transaksi lama juga tampil "Added from ORVYN Everyday" / "Moved to ORVYN Everyday". Journal Entry tidak diubah | Riwayat harus konsisten, dan Journal Entry tidak boleh diedit (ADR 0001) |
| PO-21 | Banner yang sudah ada saat kolom tanggal mulai dan urutan ditambah | Tanggal mulai = tanggal banner dibuat, urutan = urutan tampil sekarang | Tidak ada banner yang tiba-tiba hilang atau pindah |
| PO-22 | Password awal petugas baru (E2E-16) | Tampil sekali di halaman hasil, tidak di URL, tidak di log, tidak di Audit Entry. Muat ulang halaman tidak menampilkannya lagi | Password yang muncul dua kali lebih mudah bocor |
| PO-23 | Kapan operator pulsa terdeteksi (E2E-18) | Setelah 4 digit pertama diketik, tanpa menunggu Continue | Awalan operator Indonesia 4 digit |
| PO-24 | Bagian beranda yang datanya kosong atau belum dimuat (E2E-13) | Disembunyikan bersama judulnya. Bila ada data cache, cache yang tampil | "WEALTH" tanpa isi terlihat rusak |
| PO-25 | Nominal di QR-ku (S1.16 AC1) | Opsional, minimal Rp10,000 (minimum Transfer), karena hasilnya Internal Transfer | Nominal di bawah minimum akan ditolak saat dibayar |
| PO-26 | Menolak permintaan uang dari layar detail | Lewat dialog konfirmasi destruktif, sama dengan UI-16 | Decline tidak bisa dibatalkan |
| PO-27 | Kapan file story lama di `docs/stories/` diubah | Saat 0.9.0 dirilis. Sampai itu, perubahan AC tertulis di bagian 8 PRD ini | File story menggambarkan yang sudah dibangun |

## 5. Lingkup

**Masuk:** semua temuan di `requirement.md` kelompok A–G, yaitu semua E2E-01..E2E-39, F3, F4, F5, dan UI-01..UI-45. UI-15 sesuai K1, UI-30 sesuai K3, E2E-22 sesuai K4 (AC diubah, tidak dibangun), E2E-20 sesuai K5, E2E-02 sesuai K6. Pembaruan `design-system/MASTER.md` oleh CX (token, chip, tipografi, transisi).

**Tidak masuk:**
- UI-46 tablet dan landscape.
- S1.22 halaman biller di admin (keterbatasan yang sudah diterima).
- Tombol Freeze besar di bawah kartu (PO-18).
- Perubahan aturan uang: limit, biaya, minimum produk, kurs, bunga tetap.
- Menjual emas dalam gram dengan cara baru (jual tetap seperti 0.8.1).
- Mengubah data diri nasabah dari admin dan "View as customer" yang lebih lengkap (dicatat QA di S1.33, bukan temuan bernomor).
- Hal yang tidak bisa diuji di HP pemilik (kamera QRIS sungguhan, widget di launcher, ketuk push dari panel notifikasi, suara Vyna, TalkBack asli bila pemilik tidak menyalakannya): tetap dibangun bila tersentuh, dan diverifikasi dengan test otomatis atau pohon aksesibilitas (`uiautomator dump`).

## 6. Tabel jejak: temuan → story

Prioritas: **P0** = blocker atau major (atau dinaikkan PO), **P1** = minor atau polish.

| Temuan | Severity | Kelompok | Story |
|---|---|---|---|
| E2E-01 | blocker | A | Q-A1 (Create PIN, Unlock), Q-A2 (nominal), Q-A3 (Done di sukses) |
| E2E-02 | major | C | Q-C1 (PIN), Q-C2 (password) |
| E2E-03 | major | C | Q-C3 |
| E2E-04 | major | D | Q-D1 |
| E2E-05 | major | D | Q-D2 (NIK), Q-D3 (PIN lemah) |
| E2E-06 | major | D | Q-D3 |
| E2E-07 | major | E | Q-E5 |
| E2E-08 | major | B | Q-B7 |
| E2E-09 | major | B | Q-B1 (aksi offline), Q-B2 (sukses pencairan deposito) |
| E2E-10 | major | A | Q-A4 (saldo), Q-A5 (aksi cepat, sapaan), Q-A6 (baris, tombol passkey) |
| E2E-11 | major | F | Q-F1 |
| E2E-12 | major | F | Q-F2 |
| E2E-13 | minor | B | Q-B7 |
| E2E-14 | minor | F | Q-F3 |
| E2E-15 | minor | C | Q-C7 |
| E2E-16 | minor | C | Q-C8 |
| E2E-17 | minor | F | Q-F4 |
| E2E-18 | minor | F | Q-F5 |
| E2E-19 | minor | F | Q-F6 |
| E2E-20 | minor | F | Q-F7 |
| E2E-21 | minor | B | Q-B8 |
| E2E-22 | minor | F (K4) | Q-F8 AC6 (AC S2.19 diubah, tidak dibangun) |
| E2E-23 | minor | G | Q-G4 (format), Q-G5 (copy), Q-B9 (pesan terkunci) |
| E2E-24 | minor | G | Q-G3 |
| E2E-25 | minor | G | Q-C2 (layar yang sama dengan E2E-02) |
| E2E-26 | minor | G | Q-G6 |
| E2E-27 | minor | G | Q-G6 |
| E2E-28 | minor | B | Q-B9 |
| E2E-29 | minor | D | Q-D5 |
| E2E-30 | minor | G | Q-G7 |
| E2E-31 | minor | E | Q-E8 |
| E2E-32 | minor | E | Q-E7 |
| E2E-33 | minor | G | Q-G1 |
| E2E-34 | minor | F | Q-F8 |
| E2E-35 | minor | C | Q-C1 |
| E2E-36 = F3 | minor (P0, PO-1) | E | Q-E4 |
| E2E-37 = F5 | minor | B | Q-B4 |
| E2E-38 | minor | B | Q-B3 |
| E2E-39 | minor | C | Q-C4 |
| F4 | minor (bagian dari E2E-01) | A | Q-A2 |
| UI-01 | blocker | A | Q-A1 |
| UI-02 | blocker | A | Q-A2 (nominal), Q-A3 (sukses) |
| UI-03 | major | A | Q-A4 (a), Q-A5 (b, c, d), Q-A6 (e, f) |
| UI-04 | major | B | Q-B1 (aksi), Q-B3 (muat data), Q-B4 (review transfer), Q-B6 (cache gagal diperbarui) |
| UI-05 | major | B | Q-B3 |
| UI-06 | major | B | Q-B5 |
| UI-07 | major | B | Q-B6 |
| UI-08 | major | F | Q-F2 |
| UI-09 | major | F | Q-F1 |
| UI-10 | major | E | Q-E1 |
| UI-11 | major | E | Q-E2 |
| UI-12 | major | E | Q-E2 |
| UI-13 | major | E | Q-E3 |
| UI-14 | major | C | Q-C4 |
| UI-15 | major | G (K1) | Q-G1 |
| UI-16 | major | C | Q-C5 |
| UI-17 | major | D | Q-D2 |
| UI-18 | major | D | Q-D4 |
| UI-19 | major | E | Q-E6 |
| UI-20 | major | G | Q-G2 |
| UI-21 | minor | E | Q-E1 |
| UI-22 | minor | E | Q-E9 |
| UI-23 | minor | E | Q-E8 |
| UI-24 | minor | C | Q-C6 |
| UI-25 | minor | E | Q-E10 |
| UI-26 | minor | G | Q-G8 |
| UI-27 | minor | G | Q-G8 |
| UI-28 | minor | E | Q-E11 |
| UI-29 | minor | E | Q-E6 |
| UI-30 | minor | G (K3) | Q-G3 |
| UI-31 | minor | E | Q-E7 |
| UI-32 | minor | E | Q-E7 |
| UI-33 | minor | D | Q-D6 |
| UI-34 | minor | F | Q-F3 |
| UI-35 | minor | G | Q-G9 |
| UI-36 | minor | G | Q-G10 |
| UI-37 | minor | B | Q-B10 |
| UI-38 | minor | G | Q-G11 |
| UI-39 | polish | E | Q-E12 |
| UI-40 | polish | E | Q-E12 |
| UI-41 | polish | E | Q-E13 |
| UI-42 | polish | E | Q-E8 |
| UI-43 | polish | E | Q-E13 |
| UI-44 | polish | E | Q-B8 (maintenance), Q-E11 (kategori kosong) |
| UI-45 | polish | E | Q-C1, Q-C2 (layar Ganti PIN dan password) |
| UI-46 | polish | ditunda | tidak ada (bagian 5) |

Prioritas per story ada di `stories.md`. Ringkasnya: semua A, dan sebagian besar B, C, D, E yang menutup blocker atau major adalah P0. Kelompok F P0 hanya Q-F1 dan Q-F2. Kelompok G P0 hanya Q-G1 dan Q-G2.

## 7. Solusi

Copy final bahasa Inggris yang sudah ada di `ui-audit.md` dipakai apa adanya. Copy yang belum ada ditulis CX di `design.md` folder ini. Story menyebut "sesuai `design.md`" untuk copy itu.

### 7.1 Font besar (A)

- **Layar PIN (PinPad, semua tempat PIN dimasukkan atau dibuat):** bagian atas (judul, kalimat, titik, pesan) bisa digulir dan boleh menyusut. Keypad selalu menempel di bawah. Di ukuran font 1,5× ke atas, judul jadi satu baris lebih kecil, kalimat penjelas disembunyikan dari tampilan tapi tetap dibaca TalkBack, dan angka keypad tidak ikut membesar di atas 1,5×. Slot pesan error membungkus ke baris berikutnya, tidak terpotong.
- **Layar nominal (semua layar dengan keypad nominal):** nominal dan Quick Amount di area yang bisa digulir. Keypad dan tombol utama menempel di bawah.
- **Layar sukses:** isi bisa digulir, "Done" dan tombol sekunder menempel di bawah di atas navigasi sistem.
- **Nominal dan saldo:** mengecil otomatis supaya muat satu baris. Bila tetap tidak muat, baru membungkus di koma ribuan, tidak pernah di tengah kelompok angka.
- **Beranda dan komponen lain:** aksi cepat jadi grid dua baris di font besar, label boleh dua baris tanpa memotong kata. Sapaan membungkus per kata. Bottom nav, banner, kartu kantong, ListRow (nominal pindah ke baris sendiri di font besar), dan OrvynButton (tinggi minimal, bukan tetap) tahan 200%.

### 7.2 Tidak ada kegagalan yang diam (B)

- Setiap aksi yang gagal menampilkan pesan. Offline: "You're offline. Check your connection and try again." Selain itu pesan dari server. Tampilan tidak berubah seolah berhasil.
- Setiap layar yang memuat data punya tiga keadaan: skeleton, isi, dan error dengan "Try again". Pesan bawaan: "Couldn't load this. Check your connection and try again."
- Review transfer memuat ulang rincian saat dicoba lagi setelah gagal (F5).
- PinPad punya keadaan "sedang diproses": titik tetap terisi, keypad redup dan nonaktif, teks status tetap di tempat ("Checking your PIN…", "Sending Rp500,000…", "Paying Rp25,000…"). Getar dan goyang hanya untuk PIN salah. Error lain kembali ke layar periksa dengan pesan.
- Pencairan deposito lebih awal punya layar sukses.
- Beranda offline: banner di bawah hero dengan ikon dan hari/jam data, aksi cepat redup tapi menjelaskan bila diketuk. Bila pembaruan gagal karena hal lain, banner "Couldn't refresh…" dengan "Try again".
- Tarik untuk refresh di beranda memperbarui semua bagian: saldo, Total Assets, banner, kartu insight.
- Maintenance mengganti layar saat aktif (PO-8). Layar maintenance satu judul, satu kalimat dari server, dan "Try again".
- Layar terkunci punya "Try again" yang memeriksa server setelah petugas membuka kunci.
- E-statement: satu PDF yang gagal hanya memunculkan snackbar, daftar bulan tetap.

### 7.3 Keamanan dan admin (C)

- **Ganti PIN:** PIN lama diperiksa server di langkah pertama. Salah dihitung ke penghitung yang sama dengan layar Unlock dan pembayaran, dengan pesan "N tries left". Salah ke-5 mengunci (PO-5). Layar sukses dengan ikon centang: "Your PIN is changed" / "Use your new PIN from now on."
- **Ganti password:** salah dihitung ke penghitung yang sama. Pesan password lama salah tampil di bawah field password lama. Layar sukses yang setara (copy CX).
- **Reset demo:** menghapus passkeys dan semua data lain yang terkait nasabah, lalu berhasil. Test server memastikan setiap tabel yang mengarah ke nasabah kosong setelah reset.
- **PIN pad:** layar tidak bisa di-screenshot atau direkam selama PIN pad tampil.
- **Aksi destruktif:** hapus penerima tersimpan, hapus anggaran, dan tutup FX Account memakai dialog konfirmasi (copy di `ui-audit.md` UI-16). Tombol destruktif di sheet dipisah dari tombol utama.
- **Sakelar kartu:** snackbar baru muncul setelah server berhasil. Bila gagal, sakelar kembali ke posisi semula dengan pesan.
- **Admin:** server menolak penolakan pendaftaran tanpa alasan. Password awal petugas tidak lewat URL (PO-22).

### 7.4 Daftar dan masuk (D)

- **Sign in:** field password dan pesan error selalu terlihat walau keyboard terbuka. Tombol "Next" di keyboard pindah ke password.
- **About you:** Continue menggulir ke field pertama yang salah dan memberi ringkasan di atas tombol. NIK ganda diperiksa saat Continue (PO-2).
- **Create PIN:** PIN lemah ditolak langsung setelah digit ke-6 (PO-3). PIN kedua yang tidak cocok kembali ke langkah pertama dengan pesan "Those PINs didn't match. Start again."
- **Kode OTP cadangan:** bila notifikasi mati, kode tetap terlihat di layar "Enter the code", tanpa pindah otomatis.
- **Selfie:** punya panah kembali ke langkah KTP.
- **Forgot PIN?:** di layar Unlock (PO-6).

### 7.5 Sistem visual dan aksesibilitas (E)

- Chip pilihan satu gaya di dua tema, dengan ikon centang pada yang dipilih. Dua pilihan yang saling eksklusif memakai segmented button. Target sentuh minimal 48dp.
- Token `textTertiary` terang menjadi #616D86. Semua peran tipografi Material 3 memakai Plus Jakarta Sans. Keterangan baris memakai 14sp.
- Transisi: maju geser dari kanan 250ms, mundur sebaliknya, langkah di dalam alur ikut beranimasi, predictive back aktif.
- Ketukan ganda tidak terbawa ke layar berikutnya (F3).
- Semua sakelar membacakan status hidup/mati.
- Vyna: jawaban streaming tidak dibacakan berulang, markdown sederhana dirender, ikon pembaca suara jelas, disclaimer baru.
- Label TalkBack: saldo tersembunyi, ikon bagikan e-statement, baris inbox. Inbox membedakan belum dibaca dengan tebal huruf.
- Baris yang terlihat bisa diketuk selalu membuka sesuatu (pembayaran kartu terakhir, baris aset Wealth).
- Status permintaan uang memakai StatusChip, ikon tagihan berbeda per jenis, warna grafik Insights lolos 3:1, skeleton sesuai MASTER, tidak ada strip status bar, kartu satu gaya, nama panjang di gambar bukti dibungkus.
- CX memperbarui `design-system/MASTER.md` untuk token, chip, tipografi, dan transisi.

### 7.6 Story lama yang dibangun sekarang (F)

- **Detail rekening (S1.9):** kartu ORVYN Everyday di beranda membuka layar detail dengan nomor, saldo, tanggal dibuka, "Share account details", "Copy number", dan riwayat rekening (PO-9).
- **Share receipt dan tanggal di layar sukses** (PO-10).
- **Filter riwayat:** "Date" dan "Type" (PO-11), tarik untuk refresh, "Couldn't load more. Try again".
- **QR-ku dengan nominal opsional** (PO-25).
- **Pulsa:** nomor nasabah sendiri terisi sebagai pilihan pertama (diambil dari data Customer di server, bukan dari SIM), operator tampil saat mengetik (PO-23).
- **Emas dalam gram** (PO-12). Detail Fund menampilkan unit dan detail Gold Savings menampilkan gram.
- **Money Request (K5):** kartu inbox membuka detail permintaan dengan "Pay" dan "Decline". "Pay" lewat layar periksa lalu PIN.
- **Admin:** filter jenis transaksi lengkap, banner punya tanggal mulai, urutan, dan bisa diedit. Tidak ada sakelar Widget (K4).

### 7.7 Copy dan detail (G)

- **Banner beranda (K1):** pindah ke bawah transaksi terakhir. Putar otomatis berhenti bila TalkBack aktif. Ketuk pil aksi langsung menjalankan aksinya. Ketukan membuka banner yang sedang terlihat.
- **Pemindai QRIS:** tombol senter, pesan error dengan ikon yang dibacakan TalkBack, deskripsi kamera tanpa detail teknis.
- **Bantuan dan Privasi (K3):** FAQ dengan chevron dan status terbuka/tertutup, baris "Still need help?" dengan "Ask Vyna" (PO-17). Halaman Privacy menyebut kamera untuk pendaftaran dan mikrofon Vyna.
- **Format:** periode tagihan "Oct 2026", tanda minus asli (U+2212), "Rp" tidak pernah jadi "RP", kurs JPY per 100 unit (PO-13).
- **Copy:** baris "Arrives" RTGS, tombol valas (PO-14), subjudul Sign in, dialog batal permintaan (PO-15), "Welcome back, {nama depan}" selalu dengan nama.
- **Pesan kosong dan minimum:** pencarian penerima tanpa hasil, minimum e-wallet di label tombol.
- **Back dari layar periksa yang disiapkan Vyna** kembali ke Vyna dalam satu tekan.
- **Valas dan grafik Fund:** kolom "You sell at" / "You buy at", chip "1 month / 6 months / 1 year", perubahan persen dan nilai tertinggi/terendah.
- **Catatan minta uang** pindah dari layar keypad (PO-19).
- **Judul riwayat kantong** (PO-20).
- **Widget:** "Show balance", "As of 14:05 · hides in 10 seconds", "Balance hidden".

## 8. Perubahan AC story lama

Teks di bawah menggantikan AC lama saat 0.9.0 dirilis (PO-27). AC lain di story itu tidak berubah.

| Story | AC | Lama | Baru |
|---|---|---|---|
| S1.1 | AC4 | Nomor HP atau NIK yang sudah terdaftar ditolak dengan pesan yang jelas. | Nomor HP atau NIK yang sudah terdaftar ditolak dengan pesan yang jelas. NIK diperiksa saat Continue di langkah About you, dan server memeriksanya lagi saat pendaftaran dikirim. |
| S1.3 | AC2 | PIN 6 digit, dimasukkan dua kali, tidak boleh urutan atau angka sama. | Sama, ditambah: PIN lemah ditolak langsung di langkah pertama sebelum diulang, dan PIN kedua yang tidak cocok kembali ke langkah pertama dengan pesan "Those PINs didn't match. Start again." |
| S1.4 | AC4 | Penolakan wajib menyertakan alasan, yang dikirim ke pendaftar. | Sama, dan server menolak penolakan tanpa alasan (bukan hanya formulir admin). |
| S1.6 | AC2 | PIN salah 5 kali mengunci nasabah, dan sisa percobaan ditampilkan. | PIN atau password salah 5 kali, dihitung bersama di layar Unlock, pembayaran, Ganti PIN, dan Ganti password, mengunci nasabah. Sisa percobaan ditampilkan di setiap tempat itu (K6). |
| S1.6 | AC6 (baru) | – | Layar Unlock punya "Forgot PIN?" yang mengarah ke masuk dengan password lalu membuat PIN baru, tanpa melupakan HP. |
| S1.7 | AC4 | Saat offline, data terakhir tetap tampil dengan keterangan waktu pembaruan, dan aksi transaksi dinonaktifkan. | Saat offline, banner di bawah hero menyebut hari dan jam data. Aksi transaksi tampil redup dan, bila diketuk, menjelaskan bahwa nasabah sedang offline. |
| S1.8 | AC5 (baru) | – | Banner tampil di bawah transaksi terakhir. Putar otomatis berhenti bila TalkBack aktif. Ketuk pil aksi langsung menjalankan aksinya (K1). |
| S1.10 | AC3 | Filter untuk rentang tanggal, uang masuk/keluar, dan jenis transaksi. | Sama, dengan rincian: Date (This month, Last month, Last 3 months, Pick dates) dan Type (Transfers, QRIS, Bills, Cards, Pockets, Wealth). |
| S1.30 | AC1 | Mengganti PIN membutuhkan PIN lama. Mengganti password membutuhkan password lama. | Sama, ditambah: PIN lama diperiksa sebelum PIN baru diketik, dan setiap PIN atau password lama yang salah dihitung seperti S1.6 AC2. |
| S1.31 | AC2 | Halaman Privasi menjelaskan isi PRD 7.4 dengan bahasa sederhana. | Sama, dan menyebut semua baris PRD 7.4, termasuk kamera saat pendaftaran dan mikrofon Vyna. |
| S1.31 | AC4 (baru) | – | Setiap pertanyaan FAQ menunjukkan bisa dibuka (chevron, status terbuka/tertutup untuk TalkBack). Di bawah FAQ ada "Still need help?" dengan "Ask Vyna" yang membuka Vyna. Tidak ada nomor telepon (K3). |
| S1.35 | AC1 | Difilter berdasarkan nasabah, jenis, tanggal, dan nominal. | Sama, dan pilihan jenis mencakup setiap jenis transaksi yang ada di buku besar, termasuk kantong, valas, deposito, dan investasi. |
| S1.36 | AC1 | Banner punya gambar, judul dan teks, halaman detail, jadwal tayang, dan urutan. | Sama, dengan jadwal tayang berarti tanggal mulai dan tanggal berakhir, dan banner yang sudah ada bisa diedit. |
| S1.37 | AC4 | Mode maintenance menampilkan layar pemberitahuan di aplikasi dan menolak transaksi. | Sama, dan aplikasi yang sedang terbuka berganti ke layar maintenance dalam 60 detik atau pada jawaban server berikutnya, mana yang lebih dulu. |
| S1.38 | AC4 | Reset demo mengembalikan semua data ke kondisi awal, setelah "RESET" dan password. | Sama, termasuk passkeys dan semua data lain yang terkait nasabah. |
| S2.6 | AC2 | Penerima mendapat push dan kartu permintaan di kotak masuk, dengan tombol "Pay" dan "Decline". | Penerima mendapat push dan kartu permintaan di kotak masuk. Kartu itu membuka detail permintaan yang punya tombol "Pay" dan "Decline" (K5). |
| S2.6 | AC3 | "Pay" membuka layar periksa yang sudah terisi. Pembayaran tetap memakai PIN. | Tidak berubah teksnya, sekarang dibangun: "Pay" dari detail permintaan dan dari layar Requests selalu lewat layar periksa sebelum PIN. |
| S2.18 | AC1 | Widget berisi tombol "Scan QRIS" dan "Balance". | Widget berisi tombol "Scan QRIS" dan "Show balance". Saldo tersembunyi secara default dan tertulis "Balance hidden". |
| S2.19 | AC3 | Settings admin punya sakelar untuk kantong, jadwal transfer, minta uang, insight, Vyna, e-statement, dan widget. | Settings admin punya sakelar untuk kantong, jadwal transfer, minta uang, insight, Vyna, dan e-statement. Widget tidak punya sakelar, karena widget hanya menampilkan saldo yang tersimpan di HP dan tidak memanggil server (K4). |
| S3.4 | AC3 | Cair awal dengan PIN, bunga hangus dan dijelaskan sebelum PIN. | Sama, ditambah: setelah berhasil, layar sukses menampilkan nominal yang masuk ke tabungan. |
| S3.9 | AC4 | Setiap jenis aset bisa diketuk untuk membuka layarnya. | Tidak berubah teksnya, sekarang termasuk "Savings and pockets" (membuka detail rekening). |

S1.9, S1.16 AC1, S1.20 AC1–2, S3.6 AC5, dan S3.8 AC2 tidak berubah teksnya. AC itu baru dibangun di 0.9.0. Catatan PROGRESS yang menandai S1.9–S1.11 ✅ diperbaiki orkestrator saat rilis.

## 9. Perubahan admin

| Halaman | Perubahan | Story |
|---|---|---|
| Registrations | Server menolak penolakan tanpa alasan (termasuk alasan yang hanya berisi spasi), pendaftaran tetap pending | Q-C7 |
| Officers | Password awal petugas baru tampil sekali di halaman hasil, tidak di URL | Q-C8 |
| System → Reset demo | Berhasil walau ada passkey | Q-C3 |
| Transactions | Filter jenis mencakup semua jenis transaksi | Q-F8 |
| Content → Banners | Tanggal mulai, urutan, dan edit banner yang sudah ada. Setiap edit tercatat sebagai Audit Entry | Q-F8 |
| Settings | Tidak ada sakelar Widget (tidak ada perubahan kode, AC S2.19 diubah) | Q-F8 |

## 10. Dampak buku besar (ADR 0001, ADR 0003)

**Tidak ada Journal Entry jenis baru, dan tidak ada Posting yang diubah.** Server tetap menjadi penjaga terakhir untuk semua aturan uang.

| Perubahan | Dampak |
|---|---|
| Layar sukses pencairan deposito lebih awal (Q-B2) | Hanya tampilan. Journal Entry pencairan sama dengan 0.8.1: pokok dari Time Deposit ke Savings Account, bunga hangus sehingga tidak ada Posting bunga. Nominal di layar sukses sama dengan nominal Journal Entry itu |
| Beli emas dalam gram (Q-F6) | Journal Entry sama dengan beli dalam Rupiah: Rupiah dari Savings Account ke Investment Clearing. Rupiah = gram × Sell Price per gram, dibulatkan ke atas ke rupiah (aturan server yang sudah ada). Minimum 0,01 g **dan** Rp50,000 tetap berlaku. Jumlah gram di Holding berubah dalam transaksi database yang sama (ADR 0003). Nominal Rupiah yang tampil sebelum PIN sama persis dengan nominal Journal Entry |
| Bayar Money Request lewat layar periksa (Q-F7) | Journal Entry Internal Transfer yang sama dengan 0.8.1. Hanya urutan layar yang berubah |
| QR-ku dengan nominal (Q-F4) | Tidak ada Journal Entry sampai pembayar membayar. Lalu Internal Transfer biasa |
| Share receipt, tanggal, detail rekening, filter riwayat, Total Assets ikut refresh | Hanya membaca. Total Assets tetap tampilan, bukan angka tersimpan (ADR 0003) |
| Judul riwayat kantong (Q-G10) | Dihitung saat dibaca. Journal Entry lama tidak diedit |
| Ketukan ganda (Q-E4) dan PIN sedang diproses (Q-B5) | Tidak ada perubahan. Idempotency key tetap mencegah kirim dua kali |
| Reset demo menghapus passkeys (Q-C3) | Reset sudah menghapus semua Journal Entry dan Posting nasabah dan mengembalikan saldo House Account ke nol. Yang berubah hanya: reset tidak lagi gagal. Setelah reset, pengecekan buku besar lolos |
| Batas percobaan Ganti PIN/password (Q-C1, Q-C2) | Tidak memindahkan uang. Nasabah yang terkunci tidak bisa membayar sampai dibuka petugas, sama seperti sekarang |
| Maintenance (Q-B8) | Transaksi tetap ditolak server seperti 0.8.1 |

QA memeriksa buku besar dev (`checkLedger`) di awal, setelah setiap transaksi uji, dan di akhir regresi.

## 11. Dampak privasi (ADR 0002)

**Tidak ada izin Android baru dan tidak ada data perangkat baru yang dikirim ke server.** Pemeriksaan izin saat build tetap berlaku.

| Perubahan | Data yang terlibat |
|---|---|
| Endpoint cek NIK (Q-D2) | Dikirim: NIK yang diketik calon nasabah dan token pendaftaran dari langkah OTP. Diterima: hanya "tersedia" atau "sudah terdaftar", tanpa nama atau data orang lain. Dibatasi 10 cek per pendaftaran. NIK tidak ditulis ke log server (PO-2) |
| Nomor sendiri di pulsa (Q-F5) | Nomor HP diambil dari data Customer di server (yang diketik saat mendaftar), **bukan** dari SIM atau HP. Tidak ada `READ_PHONE_STATE` |
| Senter QRIS (Q-G2) | `CameraControl.enableTorch` memakai izin kamera yang sudah ada, hanya selama pemindai terbuka |
| Layar PIN tidak bisa direkam (Q-C4) | `FLAG_SECURE` adalah setelan jendela, bukan izin |
| Putar otomatis berhenti saat TalkBack aktif, tata letak font besar | Aplikasi membaca setelan aksesibilitas dan ukuran font di HP hanya untuk tampilan. Nilainya tidak pernah dikirim ke server |
| Share receipt, Share account details | Teks atau gambar dibuat di HP dan dikirim lewat menu bagikan Android ke aplikasi yang dipilih nasabah. Gambar tidak disimpan ke galeri |
| Reset demo menghapus passkeys (Q-C3) | Server menghapus kunci publik passkey. Passkey di pengelola kata sandi HP tetap ada tapi tidak bisa dipakai lagi. Masuk dengan passkey itu menampilkan pesan yang jelas (copy CX) |
| Bantuan tanpa nomor telepon (K3) | Tidak ada dialer, tidak ada nomor yang bisa sampai ke orang sungguhan |
| Halaman Privacy (Q-G3) | Isinya diperbaiki supaya sesuai PRD 7.4 (kamera pendaftaran, mikrofon Vyna) |
| Forgot PIN (Q-D6) | Hanya nomor HP dan password yang diketik nasabah |

## 12. Risiko dan asumsi

| Risiko atau asumsi | Penanganan |
|---|---|
| Komponen bersama (PinPad, layar nominal, layar sukses, OrvynButton, Chip, ListRow, tipografi) dipakai di puluhan layar. Satu perubahan bisa merusak layar lain | QA menjalankan regresi penuh, dan render 200% mencakup daftar layar di definisi selesai `stories.md` |
| Perlindungan ketukan ganda (Q-E4) bisa menelan ketukan yang sah, misalnya mengetik PIN cepat | Q-E4 AC5 menguji mengetik 6 digit PIN secepat mungkin |
| K6 membuat nasabah lebih cepat terkunci (penghitung dipakai bersama) | Sisa percobaan selalu tampil, dan "Forgot PIN?" memberi jalan sebelum terkunci |
| Endpoint cek NIK bisa dipakai menebak NIK nasabah | PO-2: perlu token yang lolos OTP, jawaban ya/tidak, 10 cek per pendaftaran, tanpa log NIK |
| Server 0.9.0 dipasang saat penguji masih memakai APK 0.8.1 | Server harus tetap melayani 0.8.1: endpoint lama tetap ada dengan perilaku yang sama, ditambah hitungan percobaan. QA menjalankan smoke 0.8.1 terhadap server 0.9.0 |
| Kolom baru di tabel banner butuh migrasi | Migrasi mengisi banner lama (PO-21). Diuji di dev sebelum produksi |
| HP pemilik: Android 12, tanpa TalkBack asli, kamera dan notifikasi dibatasi aturan HP, server lokal menjawab di bawah 1 detik | AC yang tidak bisa diuji di HP diverifikasi lewat `uiautomator dump`, test Compose atau Robolectric, dan test server. Ditandai di story |
| Cakupannya besar (62 story) | P0 dikerjakan dan di-QA dulu (bagian 13) |
| Asumsi: copy di `ui-audit.md` sudah final dan lolos humanizer | CX menyalin ke `design.md` dan hanya menulis copy yang belum ada |
| Asumsi: `failed_attempts` yang sama dipakai untuk password masuk, PIN Unlock, dan PIN pembayaran (dicek di `server/src`) | Q-C1 dan Q-C2 memakai kolom itu, tidak menambah penghitung baru |

## 13. Rencana rilis

- Versi **0.9.0**, versionCode **12** (K7). Aplikasi, server, dan admin berubah. Ada satu migrasi database (banner).
- **Tahap 1, P0:** semua story P0 dari A–G. IT menyerahkan tahap ini ke QA lebih dulu, supaya blocker dan major diverifikasi lebih awal.
- **Tahap 2, P1:** semua story P1.
- **QA akhir:** regresi penuh pada build gabungan, termasuk render 200% dan smoke APK 0.8.1 terhadap server 0.9.0. Target: 0 blocker, 0 major, putusan READY FOR RELEASE.
- **Rilis:** hanya orkestrator, dan hanya setelah QA READY **dan tim bisnis setuju** (requirement, CLAUDE.md langkah 7). Urutan: migrasi dan deploy server, lalu APK dan `releases/orvyn-0.9.0.txt` (daftar izin sama dengan 0.8.1), cek link unduhan, perbarui `docs/PROGRESS.md`. PO memperbarui `docs/stories/` sesuai bagian 8.
- Tidak ada migrasi data di HP. Nasabah cukup memasang APK baru. Passkey yang dibuat sebelum Reset demo tidak berlaku lagi setelah reset.

## 14. Catatan CX yang diputuskan orkestrator (17 Okt)

Delapan catatan di `design.md` bagian 15 diterima apa adanya dan berlaku sebagai AC:
1. Q-A1 AC4: judul satu baris, kecuali nama yang tidak muat pada 18sp; judul lalu membungkus, tidak pernah dipotong.
2. Q-A5 AC1: grid aksi cepat tanpa memotong kata; dua baris di 411dp, boleh tiga baris (2+2+1) di 360dp.
3. Label bottom nav berhenti membesar di 1,5× (keputusan desain, AC3 tetap: label terbaca utuh).
4. Pencairan deposito lebih awal mendapat layar periksa sebelum PIN (design.md 6.2).
5. Q-F3: pembatalan (reversal) ikut Type transaksi yang dibatalkannya; inflow, adjustment, dan opening hanya tampil tanpa filter Type (inflow juga di "Money in").
6. Q-D6: setelah Forgot PIN, snackbar "Your PIN is changed" di beranda.
7. Q-B9: IT memilih cara mengecek status kunci tanpa PIN, asalkan tidak membocorkan apa pun selain status kunci perangkat yang sudah terdaftar.
8. Q-B5 AC4: semua jalur PIN pembayaran di server mengirim sisa percobaan, termasuk kartu.
