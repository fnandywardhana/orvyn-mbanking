# Laporan QA: hotfix 0.9.1

QA, 18 Okt 2026 sekitar 02.45 sampai 03.35 WIB (17 Okt malam UTC). Build: `main` e523ae4 (commit IT `1c1394d..e523ae4`), debug **0.9.1 (versionCode 13)** di Galaxy Note10+ lewat `adb reverse tcp:8790`, dev server lokal port 8790, DB dev `orvyn-dev-db-1`, admin dev (lewat curl). Rencana uji: `test-plan.md` di folder ini. Screenshot di `qa-shots/` (status bar dipotong; `-light`/`-dark` = tema ORVYN). Render font 200% di `qa-shots/r200/`. Produksi tidak disentuh.

## Ringkasan dan putusan

Kelima temuan Q-02-F1..F5 **tertutup** di HP. Server 118/118, Android 106/106 di dua run `--rerun-tasks`, izin APK identik dengan 0.9.0, buku besar dev **Balanced** di setiap titik (1142 → 1152, sepuluh aksi uang = sepuluh Journal Entry, tidak ada `kind` baru, **0 Posting bernilai Rp0**). APK 0.9.0 tetap jalan terhadap server 0.9.1.

Ada 3 temuan baru, tidak ada blocker atau major:

| Severity | Jumlah | Temuan dan pemilik |
|---|---|---|
| Blocker | 0 | |
| Major | 0 | |
| Minor | 2 | H91-F2 (IT), H91-F3 (IT) |
| Polish | 1 | H91-F1 (IT) |

**Putusan: NOT READY.** Alasannya bukan uang: dua kriteria yang ditulis untuk hotfix ini belum terpenuhi sepenuhnya. H91-1 AC4 menyuruh nasabah "Go back and pick fewer friends", tapi teman yang ditambahkan lewat nomor rekening hilang dari `Who's in?` setelah kembali, jadi pesan baru itu bisa membawa nasabah ke jalan buntu (H91-F2). Di font 200% pada layar 360dp, teks baru `Needs at least Rp10,000` patah di tengah nominal, padahal aturan P0 0.9.0 (Q-A4) melarang itu, dan tata letak 200% di `design.md` 2.3 belum dibuat (H91-F3). Keduanya perbaikan kecil di `Requests.kt`. Uji ulang cukup H91-1 AC4, render 200% `Check the shares`, dan regresi split singkat. Tim bisnis minta "tidak ada defect", dan rilis 0.9.1 berjalan otomatis begitu QA READY, jadi saya tidak meloloskan dua celah AC ini.

## 1. Otomatis dan build

| ID | Cek | Hasil |
|---|---|---|
| A1 | `cd server && npm test` | **118/118 lolos** |
| A2 | `./gradlew testDebugUnitTest --rerun-tasks` dua kali berturut-turut | Run 1 dan run 2 BUILD SUCCESSFUL, 31 task dieksekusi tiap run, **106/106 lolos**, 0 gagal, 0 skip (termasuk `Hotfix091Test` 9 test) |
| A3 | `assembleDebug`, `aapt2 dump badging` | BUILD SUCCESSFUL, `versionCode='13' versionName='0.9.1'` |
| A4 | `aapt2 dump permissions` debug 0.9.1 vs `releases/orvyn-0.9.0.apk` | **Identik** (10 izin, `diff` kosong) |
| A5 | Copy baru `design.md` 2 dan 3 di kode | Semua ditemukan: `Minimum {min} a share`, `Needs at least`, `This bill is too small for … Go back and pick fewer friends.`, `The smallest share is …`, tiga pesan server, subjudul detail kategori, `card ending`, `Change share` |
| A6 | Rahasia di `1c1394d^..e523ae4` | Tidak ada `.env`, `secrets/`, `*.jks`, `signing.properties`, password, atau kunci |
| A7 | Buku besar awal | `checkLedger` OK, 1142 Journal Entry, admin `✓ Balanced`, 0 Posting Rp0 |
| C0 | Debug 0.9.0 dari `3534aeb` lewat `git worktree` | BUILD SUCCESSFUL, `versionCode='12' versionName='0.9.0'` |
| R200 | Render sementara Robolectric font 2,0, 360dp dan 411dp, terang dan gelap: detail kategori dan `Check the shares` (file test dihapus setelah dipakai) | Detail kategori lolos. `Check the shares` lihat H91-F3 |

## 2. Hasil per story

### H91-1 Setiap permintaan uang bisa dibayar

| AC | Hasil | Bukti |
|---|---|---|
| 1 | **Lolos.** Ask Sinta: Rp0 `Enter an amount`, Rp5 dan Rp9,999 `Minimum Rp10,000` nonaktif tanpa error inline, Rp10,000 `Continue`. Split total: Rp0 / Rp9,999 / Rp15,000 → `Enter an amount` / `Minimum Rp10,000` / `Next`. Chip 50k/100k/250k/500k tampil | `h1-ask-9999-minimum-dark.png` |
| 2 | **Lolos.** Sheet `Sinta's share` + `The smallest share is Rp10,000.`; Rp0 `Enter an amount`, Rp5,000 `Minimum Rp10,000`, Rp10,000 `Save` | `h1-share-sheet-5000-dark.png` |
| 3 | **Lolos** (font setelan pemilik). Total Rp15,000, 1 teman: baris Rp7,500 dengan ikon + `Needs at least Rp10,000`, tombol `Minimum Rp10,000 a share`; diubah ke Rp10,000 → tanda hilang, `Send 1 request` aktif, `Your share` Rp5,000. Label node baris: `Sinta Maharani, Rp7,500, Needs at least Rp10,000`. Di font 200% lihat H91-F3 | `h1-split-share-7500-flag-dark.png`, `h1-split-share-10000-ok-dark.png` |
| 4 | **Sebagian.** Total Rp15,000, 2 teman: kalimat `This bill is too small for 2 friends at Rp10,000 each. Go back and pick fewer friends.` dan tombol nonaktif. Panah kembali → `Who's in?` dengan `Next: 2 friends`, tapi teman yang ditambahkan lewat `Find` tidak tampil lagi (H91-F2) | `h1-split-too-small-2-friends-dark.png`, `h91-f2-whos-in-hidden-friend-dark.png` |
| 5 | **Lolos.** API: permintaan Rp9,999 → `Ask for at least Rp10,000.`, split total Rp9,999 → `The smallest bill you can split is Rp10,000.`, bagian Rp9,999 → `The smallest share is Rp10,000.`, semua `BAD_AMOUNT`, jumlah `money_requests` tetap 20. APK 0.9.0: minta Rp5,000 → kotak error `Ask for at least Rp10,000.`, tanpa permintaan baru. `Split this bill` tidak tampil di transaksi kartu Rp5,000 (Hendra) dan tampil di Rp98,000 dan Rp240,000 (Raka) | `c3-apk090-ask-5000-error.png`, `h1-8-no-split-under-minimum-light.png` |
| 6 | **Lolos.** Raka minta Rp10,000 (HP) → Sinta bayar di HP (layar periksa, PIN) → `Paid`, satu `internal_transfer` Rp10,000, status `paid` (1146). Minimum dinaikkan setelah dibuat: test server lolos | |

Kasus tepi: ketuk ganda `Pay Rp10,000` → satu Journal Entry. Offline di `Check the request` + Done → kotak `You're offline. Check your connection and try again.`, tanpa permintaan; online + Done → terkirim satu. Permintaan lama dev Rp5,000 dibayar Sinta di HP: `Check the details` Rp5,000, PIN → `Paid`, satu `internal_transfer` Rp5,000 (1148), sesuai H-9 (`h1-e6-old-5000-review-light.png`). Fitur `requests` dimatikan di admin → aksi `Request` hilang dari beranda dan `Split this bill` hilang dari detail transaksi; dinyalakan lagi.

### H91-2 Detail kategori Insights cocok dengan totalnya

| AC | Hasil | Bukti |
|---|---|---|
| 1 | **Lolos.** Raka `Shopping` Rp2,110,000: 6 baris kartu ORVYN Cashback + 1 baris QRIS, jumlah Rp2,110,000. Lewat API keenam kategori Raka cocok persis dengan totalnya (bills, shopping, transfer, food, other, transport) | `h2-shopping-detail-dark.png` |
| 2 | **Lolos.** Subjudul `This month, from ORVYN Everyday and your cards.`; baris kartu ikon kartu dan `ORVYN Cashback ·••• 8014 · Tue, 13 Oct, 14:19`; label node `ORVYN Cashback card ending 8014 · …`. Baris tabungan sama dengan 0.9.0. Terang dan gelap | `h2-shopping-detail-light.png` |
| 3 | **Lolos.** Terbaru di atas, sumber tercampur. Baris kartu membuka detail yang sama persis dengan dari Cards (judul, `Type`, `Category`, `Reference`, tanpa baris `Account`); baris tabungan membuka detail dengan `Account` | `h2-card-row-detail-dark.png` |
| 4 | **Lolos.** Hendra: transaksi kartu Rp5,000 (simulasi admin) diubah ke `Transport` → Insights `Transport` Rp5,000 → detail berisi baris kartu itu, bukan keadaan kosong | `h2-4-card-only-category-light.png` |
| 5 | **Lolos.** Offline → `Couldn't load these` / `You're offline. Check your connection and try again.` / `Try again`, tanpa daftar; online + `Try again` → daftar lengkap. Nasabah tanpa kartu kredit: dicek di kode (filter `spendWhere` yang sama dengan total) dan test server, tidak di HP | `h2-category-offline-light.png` |
| 6 | **Lolos (R200).** 360dp dan 411dp, terang dan gelap, merchant 36 huruf dan nominal Rp150,850,000: semua terbaca utuh, nama kartu membungkus | `r200/category-*.png` |

Catatan uji: menghapus `adb reverse` tidak memutus koneksi HTTP yang masih hidup, jadi percobaan offline pertama masih memuat daftar. Setelah koneksi lama habis (±8 detik) error state muncul seperti seharusnya. Ini perilaku alat uji, bukan aplikasi (sama dengan catatan putaran sebelumnya).

### H91-3 Deposito dengan bunga Rp0

| AC | Hasil |
|---|---|
| 1 | **Lolos.** Hendra buka deposito Rp1,000,000 `Pay out` (PIN salah dulu: `Wrong PIN. 4 tries left.`, tanpa Journal Entry) → admin `Mature now` → redirect `Matured. Interest after tax: Rp0.` (bukan HTTP 500). Tabungan Hendra +Rp1,000,000, deposito tertutup, inbox `Deposit paid out` / `… including Rp0 interest after tax.` |
| 2 | **Lolos.** Satu `deposit_close` (1150) dengan dua Posting: deposit −1,000,000, savings +1,000,000. 0 Posting Rp0, `checkLedger` OK |
| 3 | **Lolos (SRV).** |
| 4 | **Lolos.** Deposito `Renew` Rp1,000,000 → `Mature now` → `Matured. Interest after tax: Rp0.`, tanpa Journal Entry (tetap 1151), inbox `Deposit renewed` |
| 5, 6 | **Lolos (SRV).** Dicek juga di kode: `runMaturities` menangkap kegagalan per deposito dan menulis log, setiap deposito satu transaksi |

### H91-4 Done di keyboard

| AC | Hasil |
|---|---|
| 1 | **Lolos.** New recipient BCA: 5 digit + Enter → keyboard tertutup, layar tetap; 10 digit + Enter → `Check account` berjalan, layar `How much?` dengan nama penerima |
| 2 | **Lolos.** PLN token: 6 digit + Enter → keyboard tertutup; 12 digit + Enter → `Continue` (nama, daya, pilihan nominal). GoPay: 5 digit → tertutup; 12 digit → `Continue` |
| 3 | **Lolos.** `ORVYN account number` 5 digit → tertutup, 10 digit → `Find` (Ask: langsung ke `Ask Sinta`; Split: teman ditambahkan). `Nickname` + Enter → tersimpan, sheet tertutup (dikembalikan kosong setelahnya). Change password: Next di `Current password` → fokus ke `New password`; Enter dengan `New password` kosong → hanya menutup keyboard; Enter dengan isi → `save()` berjalan (password lemah, ditolak `Use at least 8 characters, with both letters and numbers.`, password tidak berubah, `failed_attempts` 0) |
| 4 | **Lolos.** Note review Transfer + Enter → PIN pad `To send Rp10,000 …`, tanpa Journal Entry sampai PIN benar. `Check the request` + Enter → `Request sent`. `What for` + Enter dengan tombol nonaktif → hanya menutup keyboard, tanpa permintaan; dengan tombol aktif → `Requests sent` |
| 5 | **Lolos.** History, cari penerima, Find a bank, Help: Enter → keyboard tertutup, hasil tetap, layar tidak berganti |
| 6 | **Lolos.** Sign in: Next ke password, Done masuk (setiap sign-in sesi ini). Vyna: Enter membuat baris baru (`baris\ndua`), tidak mengirim (0 pesan Vyna dipakai). Pendaftaran dan kantong tidak disentuh IT (kode), test 0.9.0 tetap lolos |

### H91-5 Snackbar beranda

| AC | Hasil | Bukti |
|---|---|---|
| 1 | **Lolos.** Beranda offline → `Transfer`: tepi bawah snackbar y=1999px, tepi atas tombol QRIS y=2032px → 32px = **12,2dp** (420dpi), gelap dan terang. QRIS, bayangan, label, dan item lain tidak tertutup | `h5-snackbar-home-offline-dark.png`, `h5-snackbar-home-offline-light.png` |
| 2 | **Lolos.** Selama snackbar tampil, tab `History` diketuk dan terbuka | |
| 3 | **Lolos (R).** `Hotfix091Test` 12dp pada font 1× dan 2× | |
| 4 | **Lolos (KODE).** Satu `BottomBar` dipakai semua tab, anchor di tombol QRIS | |
| 5 | **Lolos.** Sukses BI-FAST → offline → `Save as a recipient`: snackbar di atas tombol itu, `Share receipt` dan `Done` tidak tertutup | `h5-snackbar-success-offline-dark.png` |

## 3. Uji ulang Q-02-F1..F5

| Temuan | Hasil |
|---|---|
| Q-02-F1 | **Tertutup** (H4-1, H4-2: New recipient BCA dan PLN meter, langkah asli) |
| Q-02-F2 | **Tertutup** (aplikasi tidak bisa membuat bagian Rp5,000; server menolak Rp9,999; permintaan Rp10,000 terbayar) |
| Q-02-F3 | **Tertutup** (`Shopping` Rp2,110,000 = isi detail) |
| Q-02-F4 | **Tertutup** (`Mature now` `Pay out` bunga Rp0 berhasil, dua Posting) |
| Q-02-F5 | **Tertutup** (12,2dp di atas tombol QRIS, terang dan gelap) |

## 4. Regresi alur uang di HP dan buku besar

| Alur | Hasil | Journal Entry |
|---|---|---|
| Unlock | Lolos: buka ulang → `Welcome back, Raka`, PIN salah `Wrong PIN. 4 tries left.`, PIN benar → beranda | |
| Transfer sampai sukses | Lolos: BI-FAST Rp10,000 ke penerima BCA lewat New recipient, PIN salah lalu benar, `Sent` dengan `Date`, `Note`, `Reference` | 1143 `external_transfer` |
| Bayar tagihan | Lolos: PLN token Rp20,000, ketuk ganda `Pay Rp22,500` → PIN pad sekali → `Paid` dengan token | 1144 `bill_payment` |
| Simulasi kartu (admin, untuk H91-2 AC4) | `Approved: Rp5,000 at Apotek QA Sehat.` | 1145 `card_spend` |
| Minta uang + bayar | Lolos (H91-1 AC6) | 1146 `internal_transfer` |
| Split + bayar | Lolos: split Rp15,000, bagian Sinta Rp10,000, Sinta bayar di HP | 1147 `internal_transfer` |
| Permintaan lama Rp5,000 | Lolos (H-9) | 1148 `internal_transfer` |
| Deposito buka + `Mature now` `Pay out` bunga Rp0 | Lolos (H91-3 AC1–2) | 1149 `deposit_open`, 1150 `deposit_close` |
| Deposito `Renew` bunga Rp0 | Lolos (H91-3 AC4) | 1151 `deposit_open`, tanpa entry saat jatuh tempo |
| Insights detail kategori | Lolos (H91-2), hanya baca | |
| APK 0.9.0: transfer | Lolos | 1152 `internal_transfer` |

`checkLedger` OK setelah setiap aksi; akhir **1152**, admin `✓ Balanced`, `select count(*) from postings where amount = 0` = 0. Tidak ada `kind` baru.

## 5. Kompatibilitas: debug 0.9.0 (3534aeb) terhadap server 0.9.1

| Langkah | Hasil |
|---|---|
| Masuk | Lolos (Sign in + Next/Done) |
| Transfer Rp10,000 ke Sinta | Lolos, `Sent`, 1152 |
| Minta uang Rp5,000 | Kotak error `Ask for at least Rp10,000.`, tanpa permintaan (`c3-apk090-ask-5000-error.png`) |
| Minta uang Rp10,000 | `Request sent` |
| Insights → `Shopping` | Tidak crash, daftar lama `This month, from ORVYN Everyday.` hanya tabungan, seperti 0.9.0 (`c5-apk090-shopping-detail.png`) |

## 6. Temuan

### H91-F2 (minor, IT): teman yang ditambahkan lewat nomor rekening hilang dari `Who's in?` setelah kembali

**Langkah:** Requests → `Split a bill` → Rp15,000 → `Who's in?` → ketuk Sinta (dari `PEOPLE ON ORVYN`) → ketik nomor rekening ORVYN Hendra → `Find` → `Next: 2 friends` → `Check the shares` menampilkan `This bill is too small for 2 friends at Rp10,000 each. Go back and pick fewer friends.` → panah kembali.
**Diharapkan:** H91-1 AC4: `Who's in?` dengan kedua teman tetap tercentang, supaya nasabah bisa melepas satu.
**Aktual:** hanya Sinta yang tampil (tercentang). Hendra tidak ada di daftar, tapi tetap terhitung (`Next: 2 friends`), jadi nasabah tidak bisa melihat atau melepasnya. Jalan keluarnya hanya mengetik nomor yang sama lagi dan `Find` (yang melepas tanpa terlihat) atau mundur ke total. Penyebab: `PeoplePicker` di `Requests.kt` mengisi `known` hanya dari penerima tersimpan/terakhir, sedangkan teman hasil `Find` hanya ditambahkan ke `known` lokal yang hilang saat layar dibuat ulang; `initial` tidak ikut ditampilkan. Sudah ada di 0.9.0 (dicek dengan APK 0.9.0, perilaku sama), tapi baru bermasalah sekarang karena 0.9.1 menyuruh nasabah kembali dan mengurangi teman. Saran: tampilkan setiap orang di `initial`/`chosen` yang belum ada di daftar.
**Bukti:** `qa-shots/h91-f2-whos-in-hidden-friend-dark.png`

### H91-F3 (minor, IT): `Check the shares` di font 200% belum mengikuti design 2.3, nominal di `Needs at least Rp10,000` patah

**Langkah:** render font 2,0 di 360×640dp (terang dan gelap) `SplitFlow(15_000)` → pilih Sinta → `Next: 1 friend`.
**Diharapkan:** `design.md` 2.3 Font 200%: baris kedua membungkus di bawah nama, nominal turun ke bawah subjudul seperti ListRow besar (MASTER List row). Aturan P0 0.9.0 (Q-A4): nominal tidak patah di tengah angka.
**Aktual:** baris tetap tiga kolom (avatar, nama, nominal di kanan). Kolom tengah sempit sekali, sehingga `Needs at least Rp10,000` terbagi empat baris dengan nominal patah `Rp10,0` / `00`, nama `Sinta Maharani` patah `Maharan` / `i`, dan inisial avatar terpotong (`S`, bukan `SM`). Di 411dp nominal tidak patah, tapi tata letak tetap tidak bertumpuk dan nama 29 huruf patah di tengah kata. Detail kategori (H91-2 AC6) di render yang sama tidak bermasalah. Di HP pemilik (font setelan pemilik) baris tampil normal.
**Bukti:** `qa-shots/r200/shares-w360dp-x2.0-light.png`, `shares-w360dp-x2.0-dark.png`, `shares-w411dp-x2.0-*.png`

### H91-F1 (polish, IT): baris `Type` di detail transaksi kartu dibacakan dengan titik dan bullet

**Langkah:** Insights → `Shopping` → baris kartu (atau Cards → baris pembayaran kartu) → detail transaksi. `uiautomator dump`.
**Diharapkan:** `design.md` 3.4: nomor kartu bertopeng di setiap tempat dibacakan `{kartu} card ending {4 digit}`.
**Aktual:** baris daftar dan inbox sudah `card ending`, tapi nilai `Type` di detail transaksi `ORVYN Cashback ·••• 8014` tidak punya label, sehingga TalkBack membacakan titik dan bullet. Sama dengan 0.9.0. Perbaikan satu baris: pakai `spokenCard` di nilai baris detail.
**Bukti:** `qa-shots/h91-f1-card-detail-type-dark.png`

### Catatan (bukan temuan hotfix ini)

- Sukses transfer ke penerima yang sudah tersimpan, bila dicari lewat `New recipient`, tetap menawarkan `Save as a recipient` (`TransferFlow.kt`: `saved = to.id != null`, lookup tidak membawa `id`). Sudah ada sebelum 0.9.1, tidak diuji apakah menyimpan dua kali membuat data ganda. Untuk backlog.
- Masuk lewat API sebagai nasabah yang sedang masuk di HP mengeluarkan sesi HP (`Signed in on a new phone`). Ini perilaku satu perangkat yang disengaja; dicatat karena sempat terjadi sekali di sesi ini (Hendra).
- Pemisah ` · ` di subjudul baris (misalnya `card ending 8014 · Tue, 13 Oct`) tetap ada di label, sama dengan baris tabungan sejak 0.9.0.

## 7. Yang tidak teruji dan alasannya

- Font 200% dan TalkBack asli di HP (setelan HP dilarang): diganti R200 dan `uiautomator dump`.
- H91-3 AC3, AC5, AC6 dan H91-1 AC6 bagian minimum naik: hanya test server (keadaannya tidak bisa dibuat aman di dev tanpa mengubah kode atau setting).
- H91-2 AC5 nasabah tanpa kartu kredit di HP: tidak ada nasabah dev tanpa kartu dengan belanja bulan ini yang password-nya diketahui; dicek di kode dan test server.
- Done di sheet `Schedule` pada review Transfer: hanya jalur `Send now` yang diuji (keduanya memakai fungsi `confirm()` yang sama).
- Tagihan PLN, BPJS, pulsa: Done diuji di PLN token dan GoPay; field lain memakai kode yang sama (`BillsFlow.EnterNumber`).

## 8. Kondisi HP dan lingkungan di akhir sesi

- **Awal:** ORVYN release 0.9.0 di depan (kemungkinan sedang dipakai pemilik). Release di-uninstall dan debug 0.9.1 dipasang (sesi ORVYN pemilik ikut keluar, praktik yang diterima). Penjaga `mCurrentFocus` dijalankan sebelum setiap ketukan, teks, swipe, dan keyevent. Tidak ada jendela sistem atau aplikasi lain yang disentuh, tidak ada menu bagikan yang dibuka, kamera tidak dipakai, Back tidak pernah dikirim di beranda (semua kembali lewat panah `Back` di layar atau skrip yang menolak Back di beranda), tombol `Call`/`Message` RM Hendra tidak disentuh. Setelan HP tidak diubah; offline hanya lewat `adb reverse --remove`; tema terang/gelap lewat setelan Theme ORVYN. `am force-stop` dipakai sekali untuk membuka ulang ORVYN sendiri (uji Unlock).
- **Akhir:** debug di-uninstall, `releases/orvyn.apk` **0.9.0 (versionCode 12)** terpasang lagi, launcher di depan, `adb reverse --remove-all` (daftar kosong), `/data/local/tmp/orvyn_ui.xml` dihapus.
- Dev server dimatikan dengan `kill` PID node-nya sendiri (bukan `pkill`), port 8790 tidak lagi mendengarkan. Proxy lambat tidak dipakai. Worktree 0.9.0 dihapus (`git worktree list` hanya `main`). File test sementara `QaH91FontTest.kt` dihapus.
- Produksi: `orvyn-server-1` waktu mulai tetap `2026-10-17T16:01:00Z`, `RestartCount=0`, `running`. Tidak ada perintah ke produksi selain `docker inspect` baca saja.
- Setelan dev: semua fitur menyala (`requests` dimatikan sebentar lalu dinyalakan lagi), maintenance off. Vyna 0 pesan.
- Data uji baru di dev: transfer BI-FAST Rp10,000 Raka ke BCA `5550004040`, PLN token Rp20,000, permintaan Raka→Sinta Rp10,000 (dibayar), split Rp15,000 bagian Sinta Rp10,000 (dibayar), permintaan lama Rp5,000 dibayar, permintaan Rp10,000 dari APK 0.9.0 (terbuka), transfer Raka→Sinta Rp10,000 (APK 0.9.0), transaksi kartu Voyage Hendra Rp5,000 `Apotek QA Sehat` dengan kategori `Transport`, deposito Hendra Rp1,000,000 `Pay out` dicairkan, deposito Hendra Rp1,000,000 `Renew` masih terbuka. Tema ORVYN di HP debug sempat diubah ke `Light` (hilang bersama uninstall). Buku besar akhir OK, 1152 Journal Entry.
- `git status`: hanya `test-plan.md`, `qa-report.md`, dan `qa-shots/` di folder ini. Tidak ada commit.

---

# Putaran 2 (18 Okt 2026 dini hari WIB)

Build: `main` 1256846 (perbaikan IT `72c499b`), debug **0.9.1 (versionCode 13)** yang di-build setelah commit perbaikan, di Galaxy Note10+ lewat `adb reverse tcp:8790`, dev server lokal (PID sendiri), DB dev. Render 200% di `qa-shots/r200-r2/` (file test sementara, dihapus setelah dipakai). Pembanding "sebelum perbaikan" dirender dengan test yang sama di worktree `e523ae4` (dihapus).

## R2.0 Ringkasan dan putusan

Ketiga temuan putaran 1 **tertutup**. Server 118/118, Android `--rerun-tasks` 111/111, izin identik dengan 0.9.0, buku besar Balanced (1152 → 1153, satu split dibayar = satu `internal_transfer`, 0 Posting Rp0).

Tetapi perbaikan H91-F3 (avatar tumbuh dengan font di semua layar) membuat **regresi baru di daftar Requests** pada font 200%: kolom teks menyempit dan kata biasa patah di tengah (`Pratam` / `a`, `aske` / `d`, `Hend` / `ra`). Ini jenis cacat yang sama dengan yang membuat H91-F3 ditolak di putaran 1, dan sebelum perbaikan baris yang sama di 360dp tidak patah.

| Severity | Jumlah | Temuan dan pemilik |
|---|---|---|
| Blocker | 0 | |
| Major | 0 | |
| Minor | 1 | H91-F4 (IT) |

**Putusan putaran 2: NOT READY.** Perbaikannya kecil dan di satu tempat (`RequestRow` di `Requests.kt`). Uji ulang cukup render 200% Requests, detail permintaan, dan daftar penerima.

## R2.1 Otomatis dan build

| Cek | Hasil |
|---|---|
| `cd server && npm test` | **118/118 lolos** |
| `./gradlew --rerun-tasks testDebugUnitTest` (sekali) | BUILD SUCCESSFUL, **111/111 lolos**, 0 gagal, 0 skip (termasuk `FontScaleTest.checkShares` dan test H91-F2 baru) |
| `assembleDebug`, badging | APK 20:40:12 UTC (setelah commit perbaikan 20:37:56), `versionCode='13' versionName='0.9.1'` |
| Izin vs `releases/orvyn-0.9.0.apk` | **Identik** (`diff` kosong) |
| Rahasia di `c1d303b..1256846` | Tidak ada file rahasia |

## R2.2 Uji ulang temuan putaran 1

| Temuan | Hasil | Bukti |
|---|---|---|
| H91-F2 | **Tertutup.** Langkah sama di HP: split Rp15,000 → Sinta dari daftar + Hendra lewat nomor rekening dan Enter → `Next: 2 friends` → `This bill is too small for 2 friends at Rp10,000 each. Go back and pick fewer friends.` → panah kembali: Hendra dan Sinta tampil, keduanya `checked=true`. Ketuk Hendra → `Next: 1 friend` (H91-1 AC4 lolos) | `r2-h91-f2-whos-in-both-friends-dark.png` |
| H91-F3 | **Tertutup.** Render 200%, 360dp dan 411dp, terang dan gelap, dua teman (bagian Rp8,333): baris bertumpuk (nama, `Needs at least Rp10,000`, lalu nominal di barisnya sendiri), `Rp10,000` utuh, inisial `SM`/`HW` utuh di avatar 60dp. Satu kata yang lebih lebar dari kolom (`Kusumawardhani`, 14 huruf di 360dp) tetap terbagi; itu batas lebar, bukan tata letak | `r200-r2/shares-*.png` |
| H91-F1 | **Tertutup.** `uiautomator dump` detail transaksi kartu Raka: `Type` `ORVYN Cashback ·••• 8014` dengan label `ORVYN Cashback card ending 8014` | `r2-h91-f1-card-detail.png` |

## R2.3 Regresi split di HP dan buku besar

Raka: split Rp15,000 dengan Sinta, sheet bagian `Minimum Rp10,000` → Rp10,000 `Save` → `Your share` Rp5,000 → ketuk ganda `Send 1 request` → `Requests sent`, tepat satu permintaan baru (20 → 21). Sinta membayar lewat API dengan PIN → satu `internal_transfer` (1153), status `paid`, `checkLedger` OK, 0 Posting Rp0.

## R2.4 Avatar yang tumbuh dengan font (render 200%, 360dp dan 411dp, terang dan gelap)

| Layar | Hasil |
|---|---|
| Daftar penerima (Send money) | Lolos: avatar 60dp, nama, bank dan nomor membungkus rapi, ikon edit di tempat |
| `Who's in?` | Lolos: `SM`/`HW` utuh, nama dan nomor rekening utuh |
| `Check the shares` | Lolos (R2.2) |
| Detail permintaan (`Money request`) | Lolos: avatar dan judul `You asked …` membungkus per kata; satu kata 14 huruf terbagi di 360dp (batas lebar) |
| Layar nominal Transfer (`RecipientHeader`) | Sama dengan sebelum perbaikan: di 360×640 kepala penerima tergulir ke atas, keypad dan `Continue` utuh. Tidak ada perubahan karena avatar |
| Kartu RM | Tidak memakai `InitialsAvatar` (monogram 52dp sendiri), tidak berubah; `AP` utuh |
| **Daftar Requests** | **Regresi, H91-F4** |

## R2.5 Temuan baru

### H91-F4 (minor, IT): daftar Requests di font 200% mematahkan kata di tengah setelah avatar membesar

**Langkah:** render 200% `RequestsScreen` dengan permintaan `Raka Pratama asks you` Rp10,000 dan `You asked Hendra Wijaya Kusumawardhani` Rp150,000,000.
**Diharapkan:** seperti kriteria yang IT pakai di `FontScaleTest.checkShares`: nama tidak patah di tengah kata, baris bertumpuk di font besar (MASTER List row).
**Aktual:** `RequestRow` tetap tiga kolom (avatar, teks, kolom nominal + chip status). Avatar sekarang 60dp, jadi kolom teks lebih sempit:
- 360dp, nominal Rp10,000: `Raka` / `Pratam` / `a asks` / `you` (sebelum perbaikan: `Raka` / `Pratama` / `asks you`, utuh).
- 411dp, nominal Rp150,000,000: `You` / `aske` / `d` / `Hend` / `ra` / `Wijay` / `a` … (sebelum perbaikan baris ini juga sudah sempit, `Kusum` / `award` / `hani`, dan inisial terpotong `H`).
Di HP pemilik (font setelan pemilik) daftar tampil normal. Saran: `RequestRow` bertumpuk saat `isLargeFont()` seperti `ShareRow` (nominal dan chip di bawah teks), dan tambahkan Requests ke `FontScaleTest`.
**Bukti:** `qa-shots/r200-r2/requests-w360dp-light.png`, `requests-w411dp-dark.png`; pembanding sebelum perbaikan `before-fix-requests-w360dp-light.png`, `before-fix-requests-w411dp-dark.png`

### Catatan (sudah ada sebelum 0.9.1, bukan karena avatar)

Di 200% 360dp beberapa tombol berdampingan mematahkan kata: `New recipien` / `t`, `Schedul` / `ed` (Send money), `Messa` / `ge` (kartu RM). Untuk backlog font besar.

## R2.6 Yang tidak teruji

- Font 200% dan TalkBack asli di HP (setelan HP): diganti render dan `uiautomator dump`.
- Pembayaran split di HP sebagai Sinta: dibayar lewat API (alur bayar di HP sudah diuji putaran 1, tidak berubah).

## R2.7 Kondisi HP dan lingkungan di akhir sesi

- Awal: launcher di depan dengan release 0.9.0. Release di-uninstall, debug 0.9.1 dipasang, Raka masuk. Penjaga `mCurrentFocus` sebelum setiap input; tidak ada aplikasi lain atau jendela sistem yang disentuh, Back tidak dikirim di beranda, kamera tidak dipakai, setelan HP tidak diubah.
- Akhir: debug di-uninstall, `releases/orvyn.apk` **0.9.0** terpasang lagi, launcher di depan, `adb reverse --remove-all` (kosong), file dump di HP dihapus.
- Dev server dimatikan dengan `kill` PID node-nya (port 8790 bebas). Tidak ada proxy. Worktree `e523ae4` dihapus, file test sementara dan folder render di `build/` dihapus.
- Produksi: `orvyn-server-1` waktu mulai tetap `2026-10-17T16:01:00Z`, `RestartCount=0`.
- Setelan dev: semua fitur menyala, maintenance off. Data baru: split Raka Rp15,000 (bagian Sinta Rp10,000, dibayar). Buku besar akhir OK, 1153.
- Tidak ada commit. `git status`: hanya file QA di folder ini.
