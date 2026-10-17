# PRD: Hotfix 0.9.1, sisa temuan QA quality pass

Sumber: `requirement.md` di folder ini (keputusan orkestrator yang didelegasikan tim bisnis) dan temuan Q-02-F1..F5 di `docs/features/2026-10-16-quality-pass/qa-report.md` (Putaran 2, bagian R2.5). Story: `stories.md` (H91-1 sampai H91-5). Istilah dari `CONTEXT.md`. Desain dan copy: `design.md` di folder ini (ditulis CX, untuk F2 dan F3).

**Pertanyaan terbuka:** tidak ada. Keputusan di `requirement.md` sudah final. Detail yang tidak diatur di sana saya putuskan sebagai PO di bagian 4. Dua catatan CX di `design.md` bagian 7 sudah diputuskan orkestrator (H-9, H-10).

## 1. Ringkasan

0.9.0 sudah dirilis dengan putusan QA READY FOR RELEASE, tapi QA putaran 2 meninggalkan 3 temuan minor dan 2 polish. Tidak ada yang membuat uang salah. Tim bisnis minta tidak ada defect yang tersisa, jadi 0.9.1 menutup kelimanya: satu minimum untuk minta uang dan split bill, detail kategori Insights yang cocok dengan totalnya, pencairan deposito dengan bunga Rp0 yang tidak lagi gagal, aksi Done di keyboard, dan snackbar beranda yang tidak menutupi tombol QRIS. Tidak ada fitur baru.

## 2. Masalah dan untuk siapa

| Temuan | Siapa yang kena | Masalah |
|---|---|---|
| Q-02-F2 (minor) | Nasabah yang meminta uang atau membagi tagihan, dan teman yang membayar | Money Request dan bagian Split Bill Rp1,000 sampai Rp9,999 bisa dibuat, tapi pembayarannya ditolak karena minimum Transfer Rp10,000. Permintaan itu tidak pernah bisa dibayar |
| Q-02-F3 (minor) | Nasabah yang punya Credit Card | Total kategori di Insights termasuk belanja kartu kredit, tapi detail kategori hanya menampilkan transaksi tabungan. Angkanya tidak cocok, dan kategori yang belanjanya hanya dari kartu menampilkan nominal di Insights tapi "No … spending this month." di detailnya |
| Q-02-F4 (minor) | Petugas yang menjalankan "Mature now", dan nasabah yang depositonya jatuh tempo | Pencairan (Pay out) dengan bunga Rp0 gagal dengan HTTP 500, karena server menulis Posting bernilai Rp0. Di job jatuh tempo otomatis, satu deposito yang gagal juga menahan deposito lain yang jatuh tempo di menit yang sama |
| Q-02-F1 (polish) | Nasabah yang mengetik di field teks | Tombol Done atau Enter di keyboard tidak menjalankan tombol utama di luar pendaftaran, Sign in, dan kantong (misalnya New recipient, nomor meter PLN) |
| Q-02-F5 (polish) | Semua nasabah di beranda | Snackbar di beranda menutupi sedikit bagian atas tombol QRIS yang menonjol di bottom nav |

## 3. Tujuan dan cara mengukurnya

1. **Kelima temuan tertutup.** Diukur: setiap kriteria penerimaan H91-1 sampai H91-5 lolos QA di Galaxy Note10+ atau, bila ditandai, di test otomatis.
2. **Tidak ada regresi.** Diukur: semua test server dan aplikasi yang ada tetap lolos, regresi P0 singkat di HP lolos, dan QA memberi putusan READY FOR RELEASE dengan 0 blocker dan 0 major.
3. **Uang tetap benar.** Diukur: buku besar dev Balanced di awal, setelah setiap transaksi uji, dan di akhir QA. Tidak ada `kind` Journal Entry baru, dan tidak ada Posting bernilai Rp0.
4. **Privasi tetap.** Diukur: daftar izin APK 0.9.1 sama persis dengan 0.9.0.

## 4. Keputusan PO

Keputusan di `requirement.md` tidak dibuka lagi. Keputusan di bawah hanya mengisi detail yang tidak diatur di sana.

| # | Pilihan | Keputusan PO | Alasan |
|---|---|---|---|
| H-1 | Bagian pembuat Split Bill | Boleh Rp0 atau berapa pun, seperti sekarang. Bagian pembuat tidak menjadi permintaan | Tidak ada yang perlu membayarnya |
| H-2 | Satu sumber minimum | Server memakai satu nilai untuk minimum Transfer, Money Request, bagian Split Bill, dan total Split Bill. Aplikasi membaca nilai itu dari server (misalnya lewat `/config`, cara pastinya diserahkan ke IT), dengan cadangan nilai terakhir yang diketahui lalu Rp10,000. Bila nilai di server berubah, semuanya ikut | Requirement: "setting yang sama". Dua angka yang terpisah adalah akar Q-02-F2 |
| H-3 | Field catatan di layar periksa (Transfer, Ask for money, Split) | Done di field catatan menjalankan tombol utama layar itu bila aktif, sama dengan mengetuknya. Di Transfer itu berarti PIN pad terbuka. Uang tetap tidak bergerak tanpa PIN | Requirement: aturan MASTER di semua field. Catatan adalah field terakhir dan tidak ada pertanyaan di bawahnya |
| H-4 | Field pencarian (Find a bank, cari penerima, cari riwayat, cari FAQ) | Done atau Search hanya menutup keyboard. Hasil tetap tampil | Pencarian tidak punya tombol utama. Hasilnya sudah muncul saat mengetik |
| H-5 | Kotak ketik Vyna | Tidak diubah. Kotak itu bisa beberapa baris dan punya tombol kirim sendiri | Bukan field formulir. Enter untuk baris baru tetap berguna |
| H-6 | Snackbar di tab lain yang punya bottom nav | Ikut aturan yang sama dengan beranda, karena memakai komponen yang sama | Satu perbaikan di komponen bersama, bukan di satu layar |
| H-7 | Deposito yang tetap gagal di job jatuh tempo | Deposito itu tetap terbuka, kegagalannya ditulis ke log server, dan dicoba lagi di putaran job berikutnya (setiap menit) | Tidak ada uang yang bergerak setengah jalan, dan deposito lain tetap diproses |
| H-8 | Pesan inbox "Deposit paid out" saat bunga Rp0 | Tidak diubah ("… including Rp0 interest after tax.") | Isinya benar. Mengubah copy di luar cakupan hotfix |
| H-9 | Minimum naik setelah permintaan dibuat (catatan CX 7.1, **diputuskan orkestrator**) | Minimum hanya diperiksa saat permintaan atau split **dibuat**. Membayar Money Request yang sudah terbuka **tidak** memeriksa ulang minimum Transfer, jadi permintaan itu tetap bisa dibayar dengan nominal aslinya. Teks server usulan CX untuk pembayar tidak dipakai | Tidak ada permintaan yang macet setelah diterima server |
| H-10 | Minimum total Split Bill (catatan CX 7.2, **diputuskan orkestrator**) | Total minimal `{minimum}`, satu angka yang sama, bukan 2 × `{minimum}`. Bagian teman di bawah minimum ditandai "Needs at least {minimum}" dan nasabah mengubahnya | Hanya ada satu angka minimum yang perlu diingat nasabah |
| H-11 | `Split this bill` di detail transaksi | Hanya tampil bila nominal transaksi minimal `{minimum}` (`design.md` 2.6) | Transaksi yang lebih kecil tidak bisa dibagi dengan bagian yang sah |

## 5. Lingkup

**Masuk:**
- Q-02-F2: minimum Money Request, bagian Split Bill, dan total Split Bill sama dengan minimum Transfer, di server dan aplikasi, dengan penjelasan di tombol, di baris bagian, dan di sheet ubah bagian. Pembayaran permintaan yang sudah terbuka tidak memeriksa ulang minimum.
- Q-02-F3: detail kategori Insights menampilkan transaksi tabungan dan kartu kredit, dengan penanda sumbernya.
- Q-02-F4: pencairan dan perpanjangan deposito dengan bunga Rp0 tidak menulis Posting Rp0, dan job jatuh tempo memproses setiap deposito secara terpisah.
- Q-02-F1: aksi keyboard sesuai aturan Text field di `design-system/MASTER.md` di semua field formulir.
- Q-02-F5: snackbar di atas tombol QRIS.

**Tidak masuk:**
- Migrasi data. Di produksi tidak ada Money Request terbuka di bawah Rp10,000 (`requirement.md`). Di dev ada satu permintaan terbuka Rp5,000 dari data uji QA. Karena pembayaran tidak memeriksa ulang minimum (H-9), permintaan itu menjadi bisa dibayar. Itu diterima, dan permintaan baru di bawah minimum tidak bisa dibuat lagi.
- Mengubah minimum Transfer, QRIS, e-wallet, atau minimum produk lain.
- Mengganti kategori transaksi kartu kredit dari detail Insights (S2.8 AC3 tetap hanya untuk transaksi tabungan).
- Kotak ketik Vyna (H-5).
- Copy pesan inbox deposito (H-8) dan copy admin "Mature now".
- Perubahan tampilan admin.

## 6. Solusi

Copy final bahasa Inggris untuk F2 dan F3 ditulis CX di `design.md`. Story menyebut "sesuai `design.md`" untuk copy itu. F1, F4, dan F5 tidak membutuhkan copy baru.

### 6.1 Satu minimum untuk minta uang dan split bill (H91-1, Q-02-F2)

Di bawah, `{minimum}` adalah minimum Transfer dari server (sekarang **Rp10,000**), seperti di `design.md`.

- Minimum baru: Money Request, setiap bagian Split Bill, dan total Split Bill minimal `{minimum}` (H-2, H-10). Sebelumnya Rp1,000 untuk permintaan dan bagian, dan Rp2,000 untuk total split di aplikasi.
- **Server** menolak pembuatan permintaan atau split di bawah minimum dengan `BAD_AMOUNT`. Tidak ada permintaan yang dibuat dan tidak ada inbox yang terkirim. Pesannya (`design.md` 2.5): permintaan "Ask for at least {minimum}.", total split "The smallest bill you can split is {minimum}.", bagian split "The smallest share is {minimum}." Karena server yang menolak, APK 0.9.0 dan 0.8.1 juga tidak bisa membuat permintaan di bawah minimum, dan menampilkan pesan itu di kotak error yang sudah ada.
- **Aplikasi** menjelaskan minimum sebelum nasabah mencoba (`design.md` 2.2 sampai 2.4):
  - Layar nominal Ask for money dan layar total Split a bill: di bawah minimum tombol nonaktif "Minimum {minimum}". Tidak ada error inline. Quick Amount di bawah minimum tidak ditampilkan (dengan Rp10,000, keempat chip bawaan tetap tampil).
  - Sheet ubah bagian teman: kalimat "The smallest share is {minimum}." selalu tampil di bawah judul. Di bawah minimum tombol nonaktif "Minimum {minimum}".
  - Layar "Check the shares": bagian di bawah minimum ditandai di barisnya dengan ikon dan "Needs at least {minimum}", dan tombol kirim nonaktif "Minimum {minimum} a share". Pembagian rata tetap dibulatkan ke bawah, dan aplikasi tidak menaikkan bagian sendiri. Bila total lebih kecil dari jumlah teman × minimum, muncul "This bill is too small for {n} friends at {minimum} each. Go back and pick fewer friends."
  - Detail transaksi: `Split this bill` hanya tampil untuk transaksi minimal `{minimum}` (H-11).
  - Bila server menolak karena minimumnya naik di tengah alur, pesan server tampil di kotak error, dan aplikasi memakai minimum baru (`design.md` 2.5).
- **Pembayaran** Money Request tidak berubah: Internal Transfer lewat layar periksa dan PIN. Pembayaran tidak memeriksa ulang minimum Transfer (H-9), jadi permintaan yang sudah terbuka selalu bisa dibayar dengan nominal aslinya, walau minimum di server naik setelahnya.

### 6.2 Detail kategori Insights ikut kartu kredit (H91-2, Q-02-F3)

- Detail kategori (dari Insights → ketuk kategori) menampilkan semua transaksi bulan ini di kategori itu: dari Savings Account **dan** dari Credit Card nasabah. Dengan begitu jumlah baris di detail sama dengan nominal kategori di Insights.
- Aturan isi detail sama persis dengan aturan total di baris kategori (bulan ini WIB, uang keluar, jenis belanja, kategori pilihan nasabah lebih dulu, tanpa transaksi yang sudah di-Reversal). Server mengirim daftar gabungan dalam satu jawaban, jadi tidak pernah ada daftar setengah (`design.md` 3).
- Subjudul layar: "This month, from ORVYN Everyday and your cards." Baris kartu memakai ikon kartu dan subjudul baris yang diawali nama kartu dan 4 digit terakhir (misalnya "ORVYN Voyage ·••• 4821 · Thu, 15 Oct, 20:12"). Baris tabungan tidak berubah. TalkBack membaca kartu sebagai "{nama kartu} card ending {4 digit}".
- Urutan dari yang terbaru, campur kedua sumber. Mengetuk baris tabungan membuka detail transaksi seperti sekarang. Mengetuk baris kartu membuka detail yang sama dengan dari layar Cards.
- Keadaan kosong ("No {kategori} spending this month.", Q-E11 AC3) hanya tampil bila tidak ada transaksi dari kedua sumber.
- Offline atau gagal memuat: error state dengan "Try again". Tidak ada daftar dari cache, karena daftar tanpa kartu akan kembali tidak cocok dengan total.
- Nasabah tanpa Credit Card melihat transaksi tabungan saja, dengan subjudul baru.

### 6.3 Deposito dengan bunga Rp0 (H91-3, Q-02-F4)

- **Pay out** dengan bunga Rp0: satu Journal Entry `deposit_close` dengan dua Posting, pokok keluar dari Time Deposit dan masuk ke Savings Account. Tidak ada Posting beban bunga atau pajak. Deposito tertutup, nasabah mendapat inbox "Deposit paid out", dan admin menampilkan "Matured. Interest after tax: Rp0."
- **Aturan umum:** server tidak pernah menulis Posting bernilai Rp0. Bila bunga kotor ada tapi pajaknya dibulatkan ke Rp0, Posting pajak tidak ditulis. Jalur Automatic Rollover sudah benar dan tetap.
- **Job jatuh tempo** (setiap menit) memproses setiap deposito dalam transaksi database sendiri. Bila satu gagal, deposito itu di-rollback, tetap terbuka, kegagalannya ditulis di log server, dan deposito berikutnya tetap diproses (H-7).
- Kasus yang sama dari admin: suku bunga deposito 0% di Settings menghasilkan bunga Rp0, dan jatuh temponya mengikuti aturan di atas.

### 6.4 Done dan Enter di keyboard (H91-4, Q-02-F1)

Aturan Text field di `design-system/MASTER.md` berlaku di semua field formulir:
- Next di setiap field kecuali yang terakhir memindahkan fokus ke field berikutnya.
- Done di field terakhir menjalankan tombol utama layar atau sheet bila tombolnya aktif. Bila tombolnya nonaktif, Done hanya menutup keyboard. Enter di keyboard fisik sama dengan Done.

Field yang tercakup (yang belum punya aksi keyboard di 0.9.0):

| Layar | Field | Done menjalankan |
|---|---|---|
| Transfer → New recipient | Account number | "Check account" |
| Sheet simpan atau ubah penerima | Nickname | Tombol simpan sheet |
| Review Transfer | Note (optional) | Tombol "Send …" / "Schedule …" (H-3) |
| Pay → PLN token, tagihan PLN, BPJS, pulsa, e-wallet | Nomor pelanggan, meter, HP, atau e-wallet | "Continue" |
| Ask for money / Split → pilih teman | ORVYN account number | "Find" |
| Ask for money → Check the request | Note (optional) | "Ask for …" (H-3) |
| Split → Check the shares | What for (optional) | Tombol kirim permintaan (H-3) |
| Profile → Change password | Current password → New password | Next ke New password, lalu "Save password" |
| Field pencarian (Find a bank, cari penerima, cari riwayat, cari FAQ) | Search | Hanya menutup keyboard (H-4) |

Daftar lengkap per field ada di `design.md` bagian 4. Done tidak pernah melewati PIN: bila tombolnya membuka PIN pad, Done juga hanya membuka PIN pad. Pendaftaran, Sign in, dan kantong sudah benar di 0.9.0 dan tidak boleh berubah.

### 6.5 Snackbar beranda (H91-5, Q-02-F5)

- Di beranda, snackbar berada 12dp di atas tepi atas tombol QRIS yang menonjol, bukan di atas tepi bottom nav (`design.md` 5). Tidak ada bagian tombol QRIS, bayangannya, labelnya, atau item bottom nav lain yang tertutup.
- Berlaku untuk semua tab yang punya bottom nav (H-6). Layar tanpa bottom nav tetap seperti 0.9.0 (snackbar di atas area tombol yang menempel).

## 7. Perubahan admin

Tidak ada perubahan tampilan admin. "Mature now" di halaman nasabah sekarang berhasil untuk deposito dengan bunga Rp0 (sebelumnya HTTP 500). Settings tetap menerima suku bunga deposito 0%.

## 8. Dampak buku besar (ADR 0001, ADR 0003)

**Tidak ada `kind` Journal Entry baru.** Server tetap penjaga terakhir untuk semua aturan uang.

| Perubahan | Dampak |
|---|---|
| Pay out deposito dengan bunga Rp0 (H91-3) | Satu Journal Entry `deposit_close` dengan **dua** Posting: Time Deposit −pokok, Savings Account +pokok. Tanpa Posting beban bunga (Interest Expense) dan pajak (Tax Payable) bernilai Rp0. Jumlah Posting tetap nol |
| Pay out dengan bunga lebih dari Rp0 | Tidak berubah: satu `deposit_close` dengan empat Posting (Time Deposit −pokok, Interest Expense −bunga kotor, Tax Payable +pajak, Savings Account +pokok + bunga bersih). Posting yang nilainya Rp0 (misalnya pajak yang dibulatkan ke Rp0) tidak ditulis |
| Automatic Rollover dengan bunga Rp0 | Tidak berubah: tidak ada Journal Entry, deposito diperpanjang dengan pokok yang sama |
| Job jatuh tempo per deposito (H91-3) | Setiap deposito satu transaksi database. Deposito yang gagal di-rollback seluruhnya: tidak ada Journal Entry setengah jadi, saldo dan status deposito tetap |
| Minimum Money Request dan Split Bill (H91-1) | Tidak ada Journal Entry saat permintaan dibuat (seperti sekarang). Pembayaran tetap satu Internal Transfer dari pembayar ke peminta. Yang berubah: setiap permintaan yang dibuat bisa dibayar, dan pembayaran tidak memeriksa ulang minimum Transfer (H-9). Aturan lain pembayaran tetap: saldo cukup, Daily Limit, PIN |
| Detail Insights (H91-2) | Hanya membaca. Tidak ada saldo atau Posting yang berubah |
| Keyboard dan snackbar (H91-4, H91-5) | Tidak ada. Done di review Transfer hanya membuka PIN pad, sama dengan mengetuk tombol. Idempotency key tetap mencegah kirim dua kali |

QA memeriksa buku besar dev (`checkLedger`) di awal, setelah setiap transaksi uji, dan di akhir.

## 9. Dampak privasi (ADR 0002)

**Tidak ada.** Tidak ada izin Android baru, tidak ada data perangkat baru, dan tidak ada data baru yang dikirim ke server. Detail Insights hanya membaca transaksi kartu nasabah sendiri yang sudah ada di server dan sudah tampil di layar Cards.

## 10. Risiko dan asumsi

| Risiko atau asumsi | Penanganan |
|---|---|
| APK 0.9.0 dan 0.8.1 masih mengizinkan nominal Rp1,000–Rp9,999 di layar minta uang dan split | Server menolak dengan pesan yang jelas, dan aplikasi lama menampilkannya di kotak error yang ada. Diuji di H91-1 AC5 |
| Melewatkan cek minimum saat membayar Money Request (H-9) membuka jalan Internal Transfer di bawah Rp10,000 | Hanya untuk permintaan yang lolos minimum saat dibuat. Permintaan lama di bawah Rp10,000 tidak ada di produksi. Di dev, permintaan uji Rp5,000 menjadi bisa dibayar, dan itu diterima. Transfer biasa tetap memeriksa minimum |
| Detail Insights versi lama (APK 0.9.0) tidak boleh rusak saat server berubah | IT menjaga jawaban endpoint lama tetap sama. Diuji di definisi selesai (smoke APK 0.9.0) |
| Done di field catatan bisa terketuk tanpa sengaja dan mengirim Money Request (Ask for money tidak memakai PIN) | Sama dengan mengetuk tombolnya. Permintaan bisa dibatalkan pemintanya (S2.6 AC4). Transfer tetap butuh PIN (H-3) |
| Deposito yang gagal terus akan dicoba setiap menit dan menulis log setiap menit | Diterima untuk demo. Log menunjukkan deposito mana yang bermasalah |
| Komponen snackbar dan text field dipakai di banyak layar | Regresi: snackbar di layar sukses (Q-01-F2) dan aksi keyboard di pendaftaran, Sign in, dan kantong (Q-01-F11) diuji ulang |
| Asumsi: Insights sudah menghitung belanja kartu kredit per kategori di server (total 0.9.0 sudah termasuk kartu, QA R2.5) | Detail memakai aturan kategori yang sama, sehingga jumlahnya cocok |
| Asumsi: di produksi tidak ada Money Request terbuka di bawah Rp10,000 (`requirement.md`) | Tidak ada migrasi |

## 11. Perubahan AC story lama

Teks di bawah menggantikan AC lama di `docs/stories/` saat 0.9.1 dirilis, dengan tanda "(diubah 0.9.1)". PO yang menerapkannya. AC lain tidak berubah.

| Story | AC | Lama | Baru |
|---|---|---|---|
| S2.6 | AC1 | Nasabah memasukkan nomor rekening ORVYN atau memilih penerima tersimpan, nominal, dan catatan singkat. | Sama, dan nominal minimal sama dengan minimum Transfer (sekarang Rp10,000). Server menolak permintaan di bawah minimum saat dibuat. Permintaan yang sudah terbuka tetap bisa dibayar dengan nominal aslinya walau minimum naik. |
| S2.7 | AC2 | Pembagian default sama rata dan dibulatkan ke rupiah. Sisanya ditanggung pembuat. Nominal per orang bisa diubah. | Sama, dan total serta setiap bagian teman minimal sama dengan minimum Transfer (sekarang Rp10,000). Bagian di bawah minimum ditandai dan harus diubah sebelum dikirim. |
| S2.9 | AC3 | Ketukan pada kategori membuka riwayat yang sudah difilter ke kategori itu. | Ketukan pada kategori membuka transaksi bulan ini di kategori itu, dari tabungan dan kartu kredit, dengan penanda sumbernya. Jumlahnya sama dengan nominal kategori. |
| S3.4 | AC4 | Petugas bisa menjalankan "Mature now" untuk demo, tanpa menunggu berbulan-bulan. | Sama, juga untuk deposito dengan bunga Rp0. Satu deposito yang gagal jatuh tempo tidak menahan deposito lain. |

## 12. Rencana rilis

- Versi **0.9.1**, versionCode **13**. Server dan aplikasi Android berubah. Admin tidak berubah tampilannya. Tidak ada migrasi database.
- Server 0.9.1 tetap melayani APK 0.9.0 dan 0.8.1.
- Urutan: CX (`design.md` untuk F2 dan F3, bersamaan dengan PRD ini), IT, QA. H91-3 (server saja) bisa dikerjakan IT sebelum `design.md` selesai.
- Setelah QA menyatakan READY FOR RELEASE, orkestrator langsung merilis tanpa menunggu persetujuan tambahan (tim bisnis, 17 Okt: "klo udah ready mah rilis aja"): deploy server, build release, `releases/orvyn-0.9.1.txt` (daftar izin sama dengan 0.9.0), perbarui APK dan link unduhan, cek link, perbarui `docs/PROGRESS.md`. PO menerapkan bagian 11 ke `docs/stories/`.
- Nasabah cukup memasang APK baru. Tidak ada data di HP yang berubah.
