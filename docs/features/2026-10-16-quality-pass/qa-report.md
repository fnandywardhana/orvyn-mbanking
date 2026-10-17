# Laporan QA putaran 1: quality pass 0.9.0

QA, 17 Okt 2026 (sekitar 16.30 sampai 18.00 WIB). Build: `main` 5dcf178 (kode IT 206b570..de1fd5f), debug **0.9.0 (versionCode 12)** di Galaxy Note10+ lewat `adb reverse tcp:8790`, dev server lokal port 8790, DB dev `orvyn-dev-db-1`, admin dev. Rencana: `test-plan.md`. Screenshot di `qa-shots-0.9.0/` (status bar dipotong; nama file `-light`/`-dark` menyebut tema ORVYN saat itu). Produksi tidak disentuh.

## Ringkasan dan putusan

Aplikasi 0.9.0 jauh lebih baik dari 0.8.1. Semua 39 temuan E2E dan F3/F4/F5 yang diuji ulang tertutup: kegagalan offline sekarang selalu berpesan, ganti PIN dan password menghitung percobaan bersama, Reset demo berhasil dengan passkey, ketukan ganda tidak terbawa ke PIN pad, review Transfer pulih setelah offline, layar terkunci punya `Try again`, maintenance tampil dalam 37 detik. Buku besar dev **Balanced** setelah setiap transaksi uji (1117 → 1133 Journal Entry), dan setiap aksi uang membuat tepat satu Journal Entry. Izin APK sama persis dengan 0.8.1. APK 0.8.1 tetap berjalan normal terhadap server 0.9.0.

Satu hal menahan rilis: perintah build wajib (`./gradlew testDebugUnitTest assembleDebug`) **merah**. Test render font 2,0 untuk detail rekening gagal tidak menentu (Q-01-F1, major). Layarnya sendiri benar; test-nya yang salah, dan perbaikannya kecil. Sisanya 4 minor dan 6 polish, tidak ada yang membuat uang salah.

| Severity | Jumlah | Pemilik |
|---|---|---|
| Blocker | 0 | |
| Major | 1 | IT (Q-01-F1) |
| Minor | 4 | IT (Q-01-F2, F3, F6, F8) |
| Polish | 6 | IT (Q-01-F4, F9, F10), CX (Q-01-F5, F7, F11) |

**Putusan: NOT READY** (karena Q-01-F1; target PRD bagian 3 adalah 0 blocker dan 0 major). Setelah F1 diperbaiki dan build hijau, putaran 2 cukup: build ulang, `./gradlew testDebugUnitTest` tiga kali, dan cek ulang temuan minor yang dikerjakan.

## 1. Otomatis dan build

| ID | Cek | Hasil |
|---|---|---|
| A1 | `cd server && npm test` | **110/110 lolos** (92 lama + 18 baru) |
| A2 | `./gradlew testDebugUnitTest assembleDebug --rerun-tasks` | **GAGAL 90/92**: `FontScaleTest.accountDetail` 360dp terang dan gelap. Diulang `--tests FontScaleTest` dua kali: 2 gagal, lalu 1 gagal (flaky). `packageDebug` tetap menghasilkan APK. Q-01-F1 |
| A3 | `aapt2 dump badging` | `versionName='0.9.0'`, `versionCode='12'` |
| A4 / P2 | Izin 0.9.0 debug vs `releases/orvyn-0.8.1.apk` (`aapt2 dump permissions`, diff) | **Identik**: INTERNET, CAMERA, POST_NOTIFICATIONS, RECORD_AUDIO, USE_BIOMETRIC, USE_FINGERPRINT, ACCESS_NETWORK_STATE, WAKE_LOCK, c2dm RECEIVE, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION |
| P3 | Grep `READ_PHONE_STATE`, `TelephonyManager`, `getLine1Number`, `FLASHLIGHT`, `ContactsContract`, lokasi di `android/app/src/main` | Tidak ada. `My number` datang dari API (`/home` → `customer.phone`) |
| A5 | Render font 2,0 (IT `FontScaleTest`, 48 render) | 46 lolos, 2 flaky (F1). QA memeriksa gambar Unlock, Create PIN, nominal Transfer, sukses terjadwal, beranda Prestige 360dp, Sign in, baris riwayat, detail rekening: keypad, `0`, `Delete`, tombol utama, `Done` utuh; saldo `Rp834,912,398` satu baris; aksi cepat jadi grid 2 kolom tanpa kata patah; `Sign in with a passkey` tidak terpotong |
| A6 | Copy: 273 teks bertanda kode di design.md bagian 4–13 dicari di `android/app/src/main` dan `server/src` | Semua ditemukan, kecuali teks "sebelum" yang memang dihapus (`Back soon`, `Bill for 2026-10`, `Wrong PIN. Try again.`, dll.), templat `{nilai}` (`Minimum Rp50,000`, `You're Rp120,000 short.`), dan bacaan TalkBack. Tidak ada copy yang menyimpang |
| A7 | `design-system/MASTER.md` | Memuat `#616D86`, segmented, transisi 250ms, PIN pad aman, `Share receipt`, aturan keypad |
| A8 | Buku besar awal | OK, 1117 Journal Entry |
| A9 | Rahasia di commit 4f75da2..HEAD | Tidak ada `.env`, `secrets/`, `*.jks`, `signing.properties`, kunci API, atau email pribadi. Nomor HP di diff hanya nomor uji fiktif |

## 2. Hasil per story

**Lolos** = semua AC yang diuji lolos; catatan menyebut AC yang hanya lewat test otomatis (R/SRV) atau kode. **Sebagian** = sebagian AC tidak teruji (alasan disebut), tidak ada AC yang gagal. Rincian langkah dan copy yang terlihat ada di Lampiran A.

### A. Font besar
| Story | Hasil | Catatan |
|---|---|---|
| Q-A1 | Lolos | R200 Unlock/Create PIN/PIN Transfer utuh di 360×640. Di HP (font pemilik) Unlock sama dengan 0.8.1 plus `Forgot PIN?`. Judul font besar dan label TalkBack: QualityPassTest |
| Q-A2 | Lolos | R200 nominal Transfer dan deposito: area atas tergulir, keypad dan tombol menempel, `Send Rp150,000,000` 2 baris |
| Q-A3 | Lolos | R200 sukses Transfer dan terjadwal: `Done` menempel. Di HP sukses Transfer, QRIS (API), tagihan, kartu, permintaan, deposito, valas, emas tampil benar |
| Q-A4 | Sebagian | Nominal 9 digit utuh di render dan HP. AC4 (animasi hitung naik) tidak diamati |
| Q-A5 | Lolos | R200 beranda Everyday/Prestige |
| Q-A6 | Lolos | R200 riwayat (nominal di baris sendiri), Sign in. Di HP nominal riwayat punya label `minus Rp50,000` |

### B. Tidak ada kegagalan yang diam
| Story | Hasil | Catatan |
|---|---|---|
| Q-B1 | Sebagian | Offline berpesan dan tampilan kembali: Pause/Delete jadwal, simpan penerima, hapus penerima, instruksi deposito, bayar tagihan kartu (tetap di layar periksa), cair awal (tetap di `Withdraw early?`). Tidak diuji: ganti kategori, tutup valas, hapus passkey offline, AC4 (pesan server di field) |
| Q-B2 | Lolos | Sukses `Withdrawn` = Journal Entry `deposit_close`, tanpa bunga, Back = Done |
| Q-B3 | Lolos | 7 layar offline dengan judul per layar + `Try again`; proxy lambat 20 detik → skeleton lalu error ±15 detik. Layar valas/deposito/investasi/Insights/kantong offline tidak dibuka satu per satu |
| Q-B4 | Lolos | F5 tertutup; satu `external_transfer`. Polish Q-01-F4 |
| Q-B5 | Lolos | Sibuk `Withdrawing Rp1,000,000…` / `Buying 0.05 g…`, keypad nonaktif; PIN salah `Wrong PIN. 4 tries left.` (Unlock, API QRIS, kartu); saldo kurang kembali ke layar periksa tanpa PIN salah. AC3, AC6 lewat QualityPassTest |
| Q-B6 | Sebagian | AC1, AC2, AC4 lolos. AC3 (server 500, `Couldn't refresh…`) tidak diuji. AC5 lewat render |
| Q-B7 | Lolos | E2E-08 tertutup (AC1, AC2), banner baru tampil setelah tarik (AC3). AC5 (Wealth tanpa cache) tidak diuji |
| Q-B8 | Lolos | 37 detik, satu judul, `Still updating…`, offline, maintenance pada PIN tanpa Journal Entry |
| Q-B9 | Lolos | Pesan netral, `It's still locked…`, `Your account is unlocked. Enter your PIN.`, kembali dari latar belakang |
| Q-B10 | Lolos | Offline → daftar tetap, `Couldn't make your September 2026 statement.` + `Try again` (±10 detik); online `Try again` → menu bagikan |

### C. Keamanan dan admin
| Story | Hasil | Catatan |
|---|---|---|
| Q-C1 | Lolos | Cek PIN lama dulu, hitungan bersama dengan Unlock, kunci di ke-5, sukses `Your PIN is changed`. AC6 lewat API dan APK 0.8.1 (K4) |
| Q-C2 | Lolos | Pesan di field yang benar, hitungan bersama (API: PIN + password), sukses |
| Q-C3 | Lolos | Reset dengan 1 passkey: `Demo reset. 33 customers removed. …`, semua tabel ber-FK ke `customers` kosong (dicek dari `pg_constraint`), petugas dan audit tetap, Ledger OK; konfirmasi salah ditolak. AC5 lewat SRV. DB dev dipulihkan dari dump |
| Q-C4 | Lolos | `screencap` 0 byte di Unlock, PIN Transfer, Change PIN, Create PIN, PIN kartu dan detail kartu; normal di sukses |
| Q-C5 | Lolos | Dialog hapus penerima dan batal permintaan diuji di HP. Hapus anggaran dan tutup valas lewat kode (copy sama persis) |
| Q-C6 | Lolos | Sakelar pindah + spinner, snackbar setelah server, kembali saat offline. Simulasi belanja admin saat frozen tidak diulang |
| Q-C7 | Lolos | API: alasan kosong dan spasi → 400, `err-box` `Write a reason first. …`, `aria-invalid="true"`, status tetap pending |
| Q-C8 | Lolos | `Location: /admin/officers` tanpa query, kotak `Officer added` sekali saja, password tidak di log server dan Audit |

### D. Daftar dan masuk
| Story | Hasil | Catatan |
|---|---|---|
| Q-D1 | Lolos | Field password dan error terlihat di atas keyboard, Next/Done benar; AC4 render |
| Q-D2 | Lolos | Gulir + fokus ke NIK, ringkasan, `Pick one.`, NIK ganda, offline; API: token, 10 cek, cek ke-11 ditolak, NIK tidak di log |
| Q-D3 | Lolos | PIN lemah langsung ditolak, `Those PINs didn't match. Start again.` terlihat (E2E-06), di pendaftaran, Forgot PIN, Change PIN |
| Q-D4 | Lolos | Tanpa pindah otomatis, kalimat baru. Kotak kode saat notifikasi mati lewat QualityPassTest (notifikasi HP tidak diubah) |
| Q-D5 | Lolos | Panah kembali di selfie, data About you tetap |
| Q-D6 | Lolos | Forgot PIN lengkap, Registered Device sama; jendela 10 menit lewat SRV |

### E. Sistem visual dan aksesibilitas
| Story | Hasil | Catatan |
|---|---|---|
| Q-E1 | Lolos | Chip dan segmented bercentang di dua tema, `checked` terbaca, filter chip 48dp |
| Q-E2 | Lolos | `textTertiary #616D86`, 15 peran (Theme.kt), `USD 100.00` dan grafik memakai Plus Jakarta Sans |
| Q-E3 | Sebagian | Kode: 250/175ms, tab crossfade 150ms, `enableOnBackInvokedCallback`; Back mundur satu langkah di semua alur yang dicoba. Animasi tidak direkam. IT sendiri mencatat split bill dan kuis risiko belum memakai `FlowSteps` (bagian 8 laporan IT) |
| Q-E4 | Lolos | F3 tertutup: PIN pad 0 digit, tenor tidak terlewat, `Ask for` dan `Pay` permintaan sekali |
| Q-E5 | Lolos | Sakelar kartu, `Read replies aloud` `checkable`. AC2 (bacaan TalkBack asli) lewat QualityPassTest |
| Q-E6 | Sebagian | Markdown rapi di layar, disclaimer, sakelar suara. Teks yang diumumkan TalkBack masih mentah: Q-01-F3 |
| Q-E7 | Lolos | `Balance hidden`, `Share September 2026 statement`, inbox `Unread, …` tanpa node nonaktif |
| Q-E8 | Lolos | Pembayaran kartu membuka detail (polish Q-01-F7), baris Wealth, persen sama, `Not counted in your total` |
| Q-E9 | Lolos | `To pay`, `Waiting`, `Paid`, `Declined`, `Cancelled` dengan ikon. Chip split tidak dicek |
| Q-E10 | Lolos | Ikon petir, plus, HP, dompet. `qa-shots-0.9.0/qe10-bills-icons-dark.png` |
| Q-E11 | Tidak teruji | Insights tidak dibuka di HP putaran ini |
| Q-E12 | Lolos | Satu `Loading` saat skeleton. AC3 (hero 200×40dp) dan AC4 (status bar terang) tidak dicek |
| Q-E13 | Tidak teruji | Nama 40 huruf di bukti tidak disiapkan; bukti normal dicek (Q-F2) |

### F. Story lama
| Story | Hasil | Catatan |
|---|---|---|
| Q-F1 | Lolos | Detail rekening lengkap, Share, Copy, saldo tersembunyi. Offline varian banner terlihat di render IT |
| Q-F2 | Lolos | `Date` = waktu server, `Share receipt` di Transfer/permintaan, gambar di cache aplikasi; tidak ada di deposito, valas, emas, kartu |
| Q-F3 | Lolos | Date, Type, Money in/out, Clear filters, `Nothing matches`, pemilih rentang. AC4–5 (tarik dan halaman berikutnya offline) tidak diuji |
| Q-F4 | Lolos | Tambah/ubah/hapus nominal, minimum Rp10,000. Decode QR lewat SRV |
| Q-F5 | Lolos | `My number` dari server, operator saat mengetik, awalan tak dikenal |
| Q-F6 | Lolos | Grams lengkap, Journal Entry = Rupiah sebelum PIN, `You hold` unit/gram. Minor Q-01-F6 di mode Rupiah |
| Q-F7 | Lolos | Detail, periksa, PIN, sukses dengan `Share receipt`, Decline dengan dialog, push ke Sinta. Minor Q-01-F8 |
| Q-F8 | Lolos | Kind lengkap ber-optgroup, banner terjadwal/edit/audit/tanggal terbalik, tanpa sakelar Widget |

### G. Copy dan detail
| Story | Hasil | Catatan |
|---|---|---|
| Q-G1 | Lolos | Urutan bagian, pil `Scan to pay` membuka pemindai, ketukan membuka banner yang terlihat. AC2 (TalkBack) lewat kode, AC5 tidak diuji. Polish Q-01-F9 |
| Q-G2 | Lolos | Izin kamera ditolak → pesan, tanpa crash. Senter lewat kode/R (aturan kamera) |
| Q-G3 | Lolos | `Still need help?`/`Ask Vyna`, hilang saat Vyna mati, Privacy Camera/Microphone |
| Q-G4 | Lolos | `Bill for Oct 2026`, `−2.34%`, `YOUR INVESTMENTS` + `Total`, `100 JPY = Rp10,802.50` |
| Q-G5 | Lolos | RTGS `Within the hour`, `Buy USD 100.00`, subjudul Sign in (persona dan daftar sendiri), dialog batal, `Welcome back, Raka` setelah buka ulang. Setelah keluar otomatis 5 menit tidak diuji |
| Q-G6 | Sebagian | `No matches for "zzz"` lolos. E-wallet `Minimum Rp10,000` hanya dicek di kode (`BillsFlow.kt`) |
| Q-G7 | Lolos | Satu Back dari periksa kembali ke Vyna, tanpa Journal Entry. Tagihan/kantong dari Vyna tidak dicoba |
| Q-G8 | Lolos | `You sell at`/`You buy at`, `1 month`/`6 months`/`1 year`, perubahan, label tertinggi/terendah |
| Q-G9 | Lolos | Catatan di `Check the request` |
| Q-G10 | Lolos | `Added from ORVYN Everyday` (kantong baru); kantong lama dan memo tidak berubah lewat SRV |
| Q-G11 | Lolos | `WidgetTest` (butuh launcher, tidak di HP) |

## 3. Uji ulang temuan E2E 0.8.1

| Temuan | Hasil | Bukti |
|---|---|---|
| E2E-01 (blocker) | Tertutup | Render 360×640 font 2,0: Create PIN `0`/`Delete`, nominal, `Done`, Unlock utuh (Q-A1..A3) |
| E2E-02 | Tertutup | Q-C1, Q-C2, API `/me/pin/check`, `/me/pin`, `/me/password`, kartu |
| E2E-03 | Tertutup | Q-C3 |
| E2E-04 | Tertutup | Q-D1 |
| E2E-05 | Tertutup | Q-D2 (NIK), Q-D3 (PIN lemah) |
| E2E-06 | Tertutup | Q-D3 |
| E2E-07 | Tertutup | Q-E5 |
| E2E-08 | Tertutup | Q-B7 |
| E2E-09 | Tertutup | Q-B1 (jadwal, simpan/hapus penerima, kartu, deposito), Q-B2. Ganti kategori offline tidak diulang |
| E2E-10 | Tertutup | Render beranda, riwayat, Sign in |
| E2E-11 | Tertutup | Q-F1 |
| E2E-12 | Tertutup | Q-F2 |
| E2E-13 | Tertutup | Banner baru muncul setelah tarik; setelah offline → online banner dan insight tetap ada |
| E2E-14 | Tertutup | Q-F3 |
| E2E-15 | Tertutup | Q-C7 |
| E2E-16 | Tertutup | Q-C8 |
| E2E-17 | Tertutup | Q-F4 |
| E2E-18 | Tertutup | Q-F5 |
| E2E-19 | Tertutup | Q-F6 |
| E2E-20 | Tertutup | Q-F7 |
| E2E-21 | Tertutup | Q-B8 |
| E2E-22 | Tertutup | AC diubah (K4); Settings tanpa Widget |
| E2E-23 | Tertutup | Periode, RTGS, `RP`, minus, JPY, tombol valas, subjudul Sign in, dialog batal, pesan terkunci, `Welcome back, {nama}` setelah buka ulang |
| E2E-24 | Tertutup | Q-G3 |
| E2E-25 | Tertutup | Q-C2 |
| E2E-26 | Tertutup | Q-G6 |
| E2E-27 | Tertutup (kode) | `Minimum ${q.minAmount}` di `BillsFlow.kt`; tidak dicoba di HP |
| E2E-28 | Tertutup | Q-B9 |
| E2E-29 | Tertutup | Q-D5 |
| E2E-30 | Tertutup | Q-G7 |
| E2E-31 | Tertutup | Label persen sama; semua baris Wealth bisa diketuk (deposito, valas, emas dicoba). `Savings and pockets` tidak diketuk |
| E2E-32 | Tertutup | Q-E7 |
| E2E-33 | Tertutup | Q-G1 |
| E2E-34 | Tertutup | Q-F8 |
| E2E-35 | Tertutup | Q-C1 |
| E2E-36 = F3 | Tertutup | Q-E4 |
| E2E-37 = F5 | Tertutup | Q-B4 |
| E2E-38 | Tertutup | Q-B3 |
| E2E-39 | Tertutup | Q-C4 |
| F4 | Tertutup | Render nominal Transfer dan deposito 360dp font 2,0 |

## 4. Regresi alur uang dan buku besar

Setiap aksi dicek dengan `checkLedger` dan jumlah Journal Entry. Semua OK, satu aksi = satu Journal Entry, tidak ada `kind` baru.

| ID | Alur | Hasil | JE |
|---|---|---|---|
| G1 | Transfer ORVYN Raka → Sinta Rp10,000 (0.8.1) dan Rp50,000 (0.9.0, ketuk ganda) | Lolos, push masuk inbox | 1118, 1120 |
| G2 | BI-FAST BCA Rp100,000 setelah offline | Lolos, fee Rp2,500 ke house | 1121 |
| G3 | RTGS Hendra Rp150,000,000 | Lolos, fee Rp25,000 | 1127 |
| G4 | QRIS lewat API (Sinta): PIN salah, idempotensi, limit, reversal | Lolos | 1131, 1132 |
| G5 | Tagihan PLN Rp1,342,200 (0.8.1); PLN dengan saldo Rp0 ditolak | Lolos, tanpa JE saat ditolak | 1119 |
| G6 | Bayar tagihan kartu Voyage Rp100,000 | Lolos | 1130 |
| G7 | Kantong baru + isi Rp20,000 (API) | Lolos | 1133 |
| G9 | Bayar permintaan uang Rp50,000 | Lolos | 1126 |
| G10 | Beli USD 100 | Lolos, IDR dan USD seimbang | 1128 |
| G11 | Cair awal deposito Rp1,000,000; buka deposito Rp1,000,000 | Lolos | 1122, 1123 |
| G12 | Beli emas 0,05 g | Lolos | 1129 |
| G14 | Inflow admin Rp5,000,000 dan Rp1,000,000; reversal QRIS (sekali, kedua ditolak) | Lolos | 1124, 1125, 1132 |
| G15 | Akhir regresi | Ledger OK, 1133 = 1117 + 16 aksi | |

Tidak diulang di putaran ini (dicakup test server 110/110): token PLN, BPJS, pulsa, e-wallet (G5 sebagian), jadwal berjalan (G8), split (G9 sebagian), Mature now (G11 sebagian), jual reksa dana dan SBN (G12 sebagian), Vyna batas harian (G13).

## 5. Kompatibilitas: debug 0.8.1 (4f75da2, worktree) terhadap server 0.9.0

| ID | Hasil | Catatan |
|---|---|---|
| K1 | Lolos | Sign in Raka di 0.8.1 langsung ke beranda. Field password masih tertutup keyboard (perilaku lama 0.8.1) |
| K2 | Lolos | Transfer ORVYN Rp10,000, satu JE (1118), Ledger OK |
| K3 | Lolos | PLN Rp1,342,200: review 0.8.1 menampilkan `Bill for 2026-10` (server tetap mengirim periode mentah), sukses `PLN bill · Oct 2026` (label server baru), tidak crash |
| K4 | Lolos | Ganti PIN gaya lama, PIN lama salah: `That's not your current PIN. 4 tries left.` di layar 0.8.1, `failed_attempts` 1, lalu benar → 0. `qa-shots-0.9.0/k4-081-change-pin-tries-left.png` |
| K5 | Lolos | 5 PIN salah → `Your account is locked. Ask the ORVYN team to unlock it.`; unlock petugas + buka ulang → PIN diterima |
| K6 | Lolos | Beranda dengan banner berposisi, kartu insight, riwayat |
| K7 | Lolos | Maintenance: app terbuka tetap normal (perilaku lama), buka ulang → layar lama `Back soon`, tidak crash |
| Upgrade | Lolos | `adb install -r` 0.9.0 di atas 0.8.1: data tetap, `Welcome back, Raka` + `Forgot PIN?` |
| K8 | Selesai | Worktree dihapus, debug 0.8.1 terganti |

## 6. Izin APK

Lihat A4 dan P3. `checkDebugPermissions` lolos di dalam build. Data yang dikirim ke server: hanya tambahan NIK yang diketik (`/register/nik`); tidak ada data perangkat baru.

## 7. Temuan

### Q-01-F1 (major, IT): test render font 2,0 `accountDetail` gagal tidak menentu, perintah build wajib merah
**Langkah:** `cd android && ./gradlew testDebugUnitTest assembleDebug --rerun-tasks`, lalu `./gradlew testDebugUnitTest --tests 'id.orvyn.app.FontScaleTest' --rerun-tasks` dua kali.
**Diharapkan:** 92/92 lolos (laporan IT bagian 4), Gerbang 3 "test lolos".
**Aktual:** `FontScaleTest > accountDetail[dark=false|true w360dp-h640dp-xxhdpi]` gagal 2, 2, lalu 1 kali: `account w360dp-light: Share is cut (Rect.fromLTRB(0.0, 0.0, 0.0, 0.0) …)`. Robolectric tidak punya server, jadi detail rekening gagal memuat dan banner `You're offline. These details are from Today, …` muncul di atas kartu; apakah banner sudah tampil dalam 1 detik bergantung waktu. Banner mendorong `Share account details` ke luar layar 360×640. Layarnya sesuai design 10.1 (daftar bergulir satu kesatuan); test-nya memeriksa `Share` tanpa `performScrollTo()`, padahal baris berikutnya melakukannya untuk `Copy number`.
**Bukti:** `android/app/build/font-scale/account-w360dp-light.png` (render dari putaran QA). Kode: `android/app/src/test/java/id/orvyn/app/FontScaleTest.kt` baris 185–191.

### Q-01-F2 (minor, IT): snackbar menutupi tombol sekunder yang menempel dan baris bawah keypad
**Langkah:** sukses Transfer ke penerima baru → offline → ketuk `Save Sinta as a recipient`.
**Diharapkan:** design 4.11: snackbar "tepat di atas area aksi yang menempel… tidak pernah menutupi tombol utama".
**Aktual:** snackbar ditaruh tetap 76dp di atas tepi bawah (keputusan IT 7.10), jadi menutupi `Share receipt` seluruhnya dan separuh `Save Sinta as a recipient` selama 4 detik. `Done` tidak tertutup. Di layar nominal dengan keypad, posisi tetap yang sama (y≈1920 di Note10+) jatuh di atas baris `000 0 Delete` (y≈1946); snackbar di layar keypad tidak dipicu di HP, jadi ini dari posisi, bukan pengamatan.
**Bukti:** `qa-shots-0.9.0/qb1-save-recipient-offline.png`

### Q-01-F3 (minor, IT): Vyna mengumumkan jawaban ke TalkBack (dan pembaca suara) dengan simbol markdown mentah
**Langkah:** Vyna, tanya "Ringkas rekening saya dalam daftar bullet dengan angka tebal", lalu `uiautomator dump`.
**Diharapkan:** Q-E6 AC2–3: jawaban diumumkan sekali, tanpa simbol mentah.
**Aktual:** layar rapi (bullet `•`, tebal), tetapi node pengumuman `vyna-announce` berisi `- Rekening tabungan … **Rp16,528,466**\n- …`. `Vyna.kt` baris 152–154: `announce = reply.text` dan `voice.speak(reply.text)` memakai teks mentah, jadi TalkBack dan `Read replies aloud` bisa membaca bintang dan tanda hubung.
**Bukti:** `qa-shots-0.9.0/qe6-vyna-markdown.png` (tampilan), dump UI (node `content-desc` di 1,1967).

### Q-01-F4 (polish, IT): kotak error offline tetap di review Transfer setelah rincian berhasil dimuat
**Langkah:** BI-FAST Rp100,000 → review → offline → `Send` → PIN → online → `Try again` di baris rincian.
**Diharapkan:** setelah rincian siap dan tombol aktif, tidak ada pesan offline lama.
**Aktual:** rincian dan `Send Rp100,000` kembali, tetapi kotak `You're offline. Check your connection and try again.` masih tampil sampai dikirim. Transfer tetap berhasil sekali.
**Bukti:** `qa-shots-0.9.0/qb4-review-online-stale-error.png`

### Q-01-F5 (polish, CX): `Save PT as a recipient` untuk penerima badan usaha
**Langkah:** Hendra kirim RTGS ke `PT Griya Properti Utama` → layar sukses.
**Diharapkan:** label yang masuk akal untuk nama usaha.
**Aktual:** templat `Save {nama depan} as a recipient` mengambil kata pertama, jadi `Save PT as a recipient`. CX perlu menentukan aturan (misalnya nama lengkap bila bukan orang, atau `Save as a recipient`).

### Q-01-F6 (minor, IT): label tombol nominal tidak mengikuti design 4.7 di beli investasi (mode Rupiah) dan buka deposito
**Langkah:** Gold Savings → `Buy` → mode `Rupiah`: kosong, lalu Rp10,000. Deposito → `Open a deposit` kosong.
**Diharapkan:** design 4.7: kosong → tombol nonaktif `Enter an amount`; di bawah minimum → `Minimum {Rp50,000}`.
**Aktual:** emas mode Rupiah: `Buy Rp0` dan `Buy Rp10,000` (nonaktif, tanpa alasan); deposito: `Next` nonaktif. Mode Grams sudah benar (`Enter an amount`, `Minimum 0.01 g`). Fund/Bond tidak dicek.

### Q-01-F7 (polish, CX): tanda nominal pembayaran kartu berbeda antara daftar Cards dan detail
**Langkah:** Cards → kartu Voyage → `RECENT CARD PAYMENTS` → ketuk pembayaran Rp100,000.
**Aktual:** daftar: `Your savings`, `+Rp100,000` (sisi kartu); detail: `ORVYN Voyage bill`, `−Rp100,000` (sisi tabungan). Benar secara buku besar, tetapi angka yang sama tampil dengan tanda berlawanan. CX tentukan sisi yang ditampilkan.

### Q-01-F8 (minor, IT): `Balance after` kehilangan tanda minus saat saldo kurang
**Langkah:** Sinta (API) minta Rp50,000 ke nasabah bersaldo Rp0 → Requests → `Pay Rp50,000`.
**Diharapkan:** `Balance after` tidak menampilkan angka positif yang salah (misalnya `−Rp50,000`, atau baris disembunyikan bila kurang).
**Aktual:** `Balance after` → `Rp50,000` di samping kotak `You're Rp50,000 short.`; tombol nonaktif, jadi uang tidak bergerak. `Requests.kt:254` (`Money.format(o.balance - q.amount)`), pola sama di `TransferFlow.kt:400`.
**Bukti:** `qa-shots-0.9.0/q01-f8-balance-after-short.png`

### Q-01-F9 (polish, IT): label TalkBack banner dengan tanda baca ganda
**Aktual:** `Scan. Pay. Done.. Pay at any QRIS sticker with your camera.`, `Flying soon?. Switch on overseas payments before you go.` (judul + ". " + isi, walau judul sudah berakhir tanda baca).

### Q-01-F10 (polish, IT): nominal valas rata kiri, layar nominal lain rata tengah
**Langkah:** Foreign currency → `Buy foreign currency` → USD 100.
**Aktual:** `USD 100.00` rata kiri, sedangkan `You pay …` dan semua layar nominal Rupiah (Transfer, deposito, emas) rata tengah (design 4.7 satu pola). `qa-shots-0.9.0/qg5-fx-buy-usd.png`

### Q-01-F11 (polish, CX): pendaftaran `About you`: Enter di keyboard tidak pindah ke field berikutnya
**Langkah:** pendaftaran → `About you` → ketik nama → Enter.
**Aktual:** fokus tetap di `Full name`; teks berikutnya masuk ke field yang sama. Q-D1 hanya mengatur Sign in, jadi ini belum punya aturan; CX tentukan aksi keyboard (Next … Done) untuk form pendaftaran.

### Catatan (bukan temuan)
- Satu dari empat ketukan banner tepat saat carousel berganti tidak membuka apa pun (tidak membuka banner yang salah, sesuai AC4).
- Dengan Pockets dimatikan, `Total balance` tetap menghitung saldo kantong yang bagiannya tersembunyi (Rp639,659,016 vs ORVYN Everyday Rp544,659,016). Perilaku sama dengan 0.8.1, di luar lingkup quality pass; PO bisa memutuskan apakah perlu dijelaskan.
- Pendaftaran yang diulang dari langkah nomor HP meninggalkan satu baris `registrations` berstatus `draft` untuk nomor yang sama.

## 8. Yang tidak teruji dan alasannya

- **Butuh mengubah setelan HP (dilarang):** font 2,0 di HP (diganti render), TalkBack asli, animasi mati, notifikasi mati (Q-D4 AC2), tema sistem. Diganti QualityPassTest/FontScaleTest dan `uiautomator dump`.
- **Kamera dan passkey sungguhan:** senter Q-G2, foto KTP/selfie, passkey (Q-C3 AC5, hapus passkey offline).
- **Launcher dan panel notifikasi:** widget (Q-G11, `WidgetTest`), thumbnail Recents (Q-C4).
- **Waktu:** keluar otomatis 5 menit (X10, Q-G5 AC5), Insights (Q-E11), bukti nama 40 huruf (Q-E13), animasi hitung saldo (Q-A4 AC4), server 500 di beranda (Q-B6 AC3), tarik/halaman berikutnya di riwayat (Q-F3 AC4–5), ganti kategori/tutup valas offline, tolak pendaftaran dengan alasan di HP (Q-C7 AC4), masuk sebagai petugas baru (Q-C8 AC4), semua banner dihapus (Q-G1 AC5).
- **Regresi:** token PLN, BPJS, pulsa, e-wallet, jadwal berjalan, split, Mature now, jual reksa dana/SBN, batas Vyna (dicakup test server).

## 9. Kondisi HP dan lingkungan di akhir sesi

- Saat sesi dimulai launcher Samsung di depan (layar menyala, tidak terkunci) dan tetap sama selama ±1 jam uji otomatis; sesi HP dimulai dengan `adb install` dan `am start`, tanpa input ke aplikasi lain. Tidak ada dialog Play Protect yang terlihat.
- Satu ketukan terkirim ke koordinat 0,0 saat ORVYN di depan (label bottom nav punya bounds 0 di dump). Tidak ada efek: ORVYN tetap di depan dan panel notifikasi tidak terbuka. Helper ketuk lalu diperketat (menolak y < 120 dan koordinat kosong).
- Back tidak pernah dikirim di beranda (helper menolak dua kali saat beranda terdeteksi). Kamera tidak pernah aktif: izin ditolak di dialog yang dibuka ORVYN. Menu bagikan ditutup dengan satu Back tanpa memilih aplikasi. Setelan HP tidak diubah.
- **Akhir:** debug di-uninstall, `releases/orvyn.apk` **0.8.1 (versionCode 11)** terpasang lagi (sesi ORVYN pemilik ikut keluar, notifikasi ORVYN dari uji hilang bersama uninstall). `adb reverse` dihapus semua. File dump UI di HP (`/data/local/tmp/orvyn_ui.xml`, dan `/sdcard/orvyn_ui.xml` sisa putaran E2E) dihapus. Launcher di depan.
- Dev server dan proxy lambat sudah dimatikan; produksi tidak disentuh. DB dev dipulihkan dari dump sebelum Reset demo (Ledger OK, 1133 JE), dump dihapus. Semua fitur menyala, maintenance off dengan pesan semula, banner uji dihapus, petugas uji `qa.r1.*` dinonaktifkan. PIN Raka dikembalikan ke PIN di memori proyek. Vyna dipakai 2 pesan.
- Data uji baru di dev: nasabah `QA Rondesatu` (nomor uji 0812-9002-0003, Everyday, saldo Rp0), kantong `QA R1 pocket` milik Sinta, beberapa permintaan uang dan transaksi uji untuk Raka, Sinta, Hendra.
- `git status`: hanya `qa-report.md` dan `qa-shots-0.9.0/` baru. Tidak ada commit.

## Lampiran A. Catatan uji langsung di HP

Raka (Everyday) dulu dengan tema ORVYN `Match my phone` (gelap), lalu `Light`; kemudian Hendra (Prestige) dan nasabah daftar baru. "Proxy lambat" = skrip di scratchpad yang meneruskan port 8791 ke 8790 dengan jeda, lewat `adb reverse tcp:8790 tcp:8791`.

- **Q-C4 AC1–2:** `screencap` di Unlock dan PIN Transfer menghasilkan file 0 byte (diblokir); di layar sukses gambar normal (752 KB). Lolos.
- **Q-B5 AC4 (Unlock):** `Wrong PIN. 4 tries left.` Lolos.
- **Q-G1 AC1:** urutan beranda Hero → aksi cepat → kartu insight → ACCOUNTS → WEALTH → POCKETS → RECENT → banner. Lolos. AC3–4: 3 dari 4 ketukan membuka detail banner yang sedang terlihat; satu ketukan (saat banner berganti) tidak membuka apa pun (tidak membuka banner yang salah). `qa-shots-0.9.0/home-top-dark.png`
- **Q-B6 AC1–2:** offline + tarik → banner `You're offline. These balances are from Today, 16:46.` / `Sending money works again once you're back online.` di bawah aksi cepat; aksi cepat redup, label `Transfer, unavailable while offline`, ketuk → snackbar `You're offline. Try again when you're connected.`. Tarikan pertama tidak memicu refresh, tarikan kedua ya. `qa-shots-0.9.0/qb6-home-offline-dark.png`
- **Q-B3 AC1–2:** offline: Cards `Couldn't load your cards`, Inbox `Couldn't load your inbox`, History `Couldn't load your history`, Passkeys `Couldn't load your passkeys` (bukan "No passkeys yet."), Help `Couldn't load help`, E-statements `Couldn't load your statements`, penerima `Couldn't load your recipients`, semua dengan `You're offline. Check your connection and try again.` + `Try again`. Online + `Try again` di Cards dan penerima memuat di tempat. `qa-shots-0.9.0/qb3-cards-offline.png`
- **Q-E5 AC1:** Freeze card, Online payments, Overseas payments, Contactless: `checkable=true`, `checked` sesuai.
- **Q-C6 AC1–3:** dengan proxy lambat 3 detik: sakelar langsung pindah, spinner, baris lain nonaktif (`qa-shots-0.9.0/qc6-freeze-pending.png`); snackbar `Card unfrozen` setelah server menjawab. Offline: sakelar Online payments kembali + snackbar offline. Lolos.
- **Q-B1 AC1 (sebagian):** jadwal Pause offline dan Delete offline (setelah dialog `Delete this schedule?`): snackbar offline, tampilan tetap (`qa-shots-0.9.0/qb1-schedule-pause-offline.png`). Simpan penerima dari layar sukses offline: snackbar offline, tombol tetap; online → `Sinta is in your saved recipients`.
- **Q-E4 AC1, AC4 (F3/E2E-36):** ketuk ganda `Continue` → satu langkah ke `Check the details`; ketuk ganda `Send Rp50,000` → PIN pad `0 of 6 digits entered`. Setelah PIN satu `internal_transfer` (1119 → 1120), buku besar OK. Lolos.
- **Q-E1 AC3:** segmented `Send now` `checkable=true checked=true`, `Schedule` `checked=false`.
- **Q-F2 AC2–4:** sukses Transfer: `Date` `17 Oct 2026, 16:52 WIB` = `created_at` Journal Entry (16:52:04 WIB); urutan `Save Sinta as a recipient`, `Share receipt`, `Done`. `Share receipt` membuka menu bagikan Android (ditutup dengan satu Back), gambar di `cache/receipts/` aplikasi, isinya sama dengan bukti riwayat. `qa-shots-0.9.0/qf2-transfer-success.png`
- **Q-C5 AC1, AC4–5:** `Remove from saved` di sheet (teks merah + ikon tong sampah, jauh di bawah `Save`) → dialog `Remove Sinta Maharani from saved?` / `You can save them again after your next transfer.` / `Cancel` / `Remove` (tombol terisi merah). Cancel tidak menghapus, Remove menghapus (DB 0 baris). Offline: kotak error di sheet. `qa-shots-0.9.0/qc5-remove-recipient-dialog.png`
- **Q-G6 AC1:** `No matches for "zzz"` / `Check the spelling, or send to a new recipient.` / `New recipient`. Lolos.
- **Q-B4 AC1–4 (F5/E2E-37):** BI-FAST BCA Rp100,000, offline sebelum PIN → kembali ke review tanpa goyang, rincian dimuat ulang dan gagal: `Couldn't load the fee and arrival time.` + `Try again`, tombol `Details didn't load`, kotak error offline. Online + `Try again` → rincian kembali, `Send Rp100,000` → PIN → terkirim. Satu `external_transfer` (savings −102,500, kliring 100,000, fee 2,500), buku besar OK (1121). Catatan polish Q-01-F4. `qa-shots-0.9.0/qb4-review-offline-after-pin.png`
- **Q-B1 AC1 (instruksi deposito), AC3:** ubah ke `Pay out` offline → segmented kembali ke `Renew` + kotak error di sheet. Cair awal offline setelah PIN → tetap di `Withdraw early?` dengan kotak error offline.
- **Q-B2 AC1–5, Q-B5 AC1 (deposito):** dengan proxy lambat 4 detik: PIN pad `6 of 6 digits entered`, `Withdrawing Rp1,000,000…`, 12 tombol nonaktif, Back nonaktif. Sukses `Withdrawn` / `Rp1,000,000` / `back in ORVYN Everyday`, `Date`, `From` `3-month deposit`, `Interest` `Not paid, because you withdrew before 16 Jan 2027`, `Reference`. Journal Entry `deposit_close` deposit −1,000,000 / savings +1,000,000 (tanpa bunga), buku besar OK (1122). Back sistem di sukses = Done → daftar Deposits tanpa deposito itu. `qa-shots-0.9.0/qb2-withdrawn.png`
- **Q-E4 AC3:** ketuk ganda `Next` di buka deposito → layar `For how long?` tampil (tenor tidak terlewat). Buka deposito Rp1,000,000 3 bulan → `Deposit opened` dengan `Date`, tanpa `Share receipt` (Q-F2 AC5). 1123, OK.
- **Q-E1 AC1, AC3:** Quick Amount terpilih: latar primary + centang (`qa-shots-0.9.0/qe1-quick-amount-after-tap.png`), node `checkable=true checked=true`.
- **Q-B7 AC1–2 (E2E-08):** inflow admin Rp5,000,000 → tarik beranda: Total balance Rp15,528,466 dan Total assets Rp17,528,466 naik bersama. Inflow Rp1,000,000 → HOME → `am start`: keduanya naik lagi tanpa tarik. Buku besar OK (1125).
- **Q-B8 AC1–5, X9:** maintenance on dengan HP diam → layar maintenance setelah 37 detik. Isi hanya `ORVYN is being updated`, kalimat server, `Try again` (tanpa `Back soon`). `Try again` saat masih on → `Still updating. Try again in a few minutes.`; offline → `You're offline. Check your connection and try again.`; off → beranda. Di tengah Transfer: nominal → review (opsi tidak ditolak server) → `Send` → PIN → langsung layar maintenance, tanpa Journal Entry (tetap 1125). `qa-shots-0.9.0/qb8-maintenance.png`
- **Q-B9 AC1–5:** dikunci petugas → tarik → layar terkunci: `Your account is locked` / `Ask the ORVYN team to unlock it. Once they have, tap Try again.` / `Try again`, tanpa `Forgot PIN?` dan tanpa "too many wrong tries". `Try again` masih terkunci → `It's still locked. Ask the ORVYN team to unlock it.`; setelah unlock → PIN pad `Your account is unlocked. Enter your PIN.`. Terkunci → HOME → unlock → `am start` → PIN pad langsung. `qa-shots-0.9.0/qb9-locked.png`
- **Q-D6 AC1–3, AC5; Q-D1 AC1–3; Q-D3 AC4:** `Forgot PIN?` → sheet `Forgot your PIN?` / `Sign in with your phone number and password, then choose a new PIN.` / `Sign in with password` / `Not now` → Sign in dengan kalimat `Sign in with your password, then choose a new PIN.`. Dengan keyboard terbuka field password dan `Sign in` terlihat di atas keyboard; aksi keyboard Next pindah ke password, Done menjalankan Sign in; password salah → `That phone number and password don't match.` terlihat tanpa menutup keyboard, `failed_attempts` 1. Password benar → `Choose a new PIN`: `123456` → `Choose 6 digits that aren't all the same or in a row.`; tidak cocok → kembali ke `Choose a new PIN` dengan `Those PINs didn't match. Start again.`; cocok → beranda + snackbar `Your PIN is changed`. DB: tetap satu Registered Device yang sama, `failed_attempts` 0.
- **Q-C1 AC1–5:** `Enter your current PIN` / `We check it before you choose a new one.`; PIN salah → `That's not your current PIN. 4 tries left.` lalu `3 tries left.` (DB naik); Unlock berikutnya `Wrong PIN. 2 tries left.` (hitungan bersama). Salah ke-5 di Change PIN (`1 try left.` sebelumnya) → layar terkunci. Setelah unlock: PIN lama benar → `Choose a new PIN` / `Avoid 123456 or your birthday.`, `111111` ditolak, PIN baru dua kali → `Your PIN is changed` / `Use your new PIN from now on.` / `Done`.
- **Q-C2 AC1, AC3–4, aksi keyboard:** password lama salah → di bawah `Current password`: `That's not your current password. 4 tries left.`; lama benar + baru `abc` → di bawah `New password`: `Use at least 8 characters, with both letters and numbers.`, `failed_attempts` 0; baru kuat → `Your password is changed` / `Use your new password the next time you sign in on a phone.` / `Done`. Next/Done keyboard bekerja.
- **Q-F1 AC1, AC3–4:** kartu ORVYN Everyday → `ORVYN Everyday`: `Account holder` Raka Pratama, `Account number` `4068 2436 24` (TalkBack per angka), `Balance`, `Opened on 14 Oct 2026`, `Share account details`, `Copy number`, `HISTORY` dengan riwayat rekening. `Copy number` → `Account number copied` (kode menyalin tanpa spasi). Share membuka menu bagikan (ditutup satu Back), teks dari kode `{nama} · ORVYN Bank · {nomor grup 4}`. Sembunyikan saldo: `Rp••••••••`, node `Balance, Balance hidden`, tombol `Show balance`; beranda ikut `Balance hidden` (Q-E7 AC1). `qa-shots-0.9.0/qf1-account-detail.png`
- **Q-F3 AC1–3, AC6, Tepi:** sheet `Date` (`Any time`, `This month`, `Last month`, `Last 3 months`, `Pick dates`), `Last month` → chip `Date filter, Last month` `checked=true`, tinggi 126px (48dp). `Type` (`Transfers`, `QRIS`, `Bills`, `Cards`, `Pockets`, `Wealth`, `Show results`, `Clear`) → `Type filter, QRIS`, hanya QRIS. + `Money in` → `Nothing matches` / `Try other dates or types, or clear the filters.` / `Clear filters` (`qa-shots-0.9.0/qf3-history-nothing-matches.png`). `Pick dates`: tanggal setelah hari ini nonaktif, `Save` nonaktif sampai rentang dipilih, 3–16 Okt → chip `3 Oct to 16 Oct`. `All` tidak ada. API `kinds=qris_payment,bill_payment` hanya mengembalikan dua jenis itu.
- **Q-G2 AC6:** `Allow camera` → dialog izin Android (dibuka ORVYN) → `Don't allow` → `Camera is off for ORVYN` / `Turn it on in Settings, or pick a QR picture from your gallery.`, tidak crash. Kamera tidak pernah aktif.
- **Q-F4 AC1–3:** `Add an amount` → Rp5,000 → tombol nonaktif `Minimum Rp10,000`; Rp50,000 → `Show QR for Rp50,000` → QR berlabel `QR code for Raka Pratama, Rp50,000`, `Rp50,000`, `Change amount`, `Remove amount`; `Remove amount` → QR tanpa nominal. AC4 (decode) hanya lewat test server (tidak ada dekoder QR di host).
- **Q-F5 AC1–3:** `My number` / `0812 1111 2222 · Telkomsel` dari data server, ketuk → langsung ke pilihan produk dengan nomor itu. `0812` → `Operator: Telkomsel` sebelum Continue; `0800…` → `We don't know this operator. Check the first 4 digits.`, Continue nonaktif.
- **Q-G3 AC2, AC5:** Help (tema terang) → `Still need help?` / `Vyna can answer questions about your money and how ORVYN works.` / `Ask Vyna` membuka Vyna. Privacy `Camera` dan `Microphone` sama persis dengan design 11.3 (KODE). `Expanded`/`Collapsed` ada di kode sebagai `stateDescription` (uiautomator Android 12 tidak menampilkan stateDescription). `qa-shots-0.9.0/qg3-help-light.png`
- **Q-E6 AC3–4, AC6:** jawaban berbentuk daftar `•` dengan indentasi, tanpa `**` di layar (`qa-shots-0.9.0/qe6-vyna-markdown.png`); `Read replies aloud` `checkable=true`; disclaimer persis. Temuan Q-01-F3 untuk teks yang diumumkan TalkBack.
- **Q-G7 AC1–2:** "kirim 50 ribu ke Sinta" → kartu `Transfer to Sinta Maharani` → `Review` → `Check the details` → satu Back → kembali ke percakapan Vyna, tidak ada Journal Entry baru (1125). Vyna dipakai 2 pesan.
- **Q-F7 AC1–5, X3:** Sinta (API) membuat 3 permintaan. Inbox: baris `Unread, Sinta Maharani asks you for Rp50,000, Today, 17:18` (tidak ada node nonaktif, Q-E7 AC3) → detail `Money request` (peminta, `To pay`, nominal, From, Note, `Sent` `17 Oct 2026, 17:18 WIB`, Status, `Pay Rp50,000`, `Decline`). Ketuk ganda `Pay` → `Check the details` (Amount, Fee `Free`, Note, Total, Balance after); ketuk ganda `Pay Rp50,000` → PIN pad `0 of 6 digits entered` → `Paid` dengan `Date`, `Share receipt`, `Done`. Satu Journal Entry (1126), buku besar OK. `Decline` → dialog `Decline this request?` / `Sinta gets a message that you declined.` / `Keep request` / `Decline` → status `Declined`, inbox Sinta (API) `Request declined`. `qa-shots-0.9.0/qe7-inbox-light.png`
- **Q-E9 AC1–2:** daftar Requests: `To pay` (ikon tangan, kuning), `Declined` (ikon silang), `Paid` (centang hijau), `Cancelled` (ikon larangan), `Waiting`. `qa-shots-0.9.0/qe9-requests-status-light.png`
- **Q-G9 AC1, Q-G5 AC4:** minta uang: layar nominal tanpa field catatan → `Check the request` (`Ask`, `Amount`, `Open for 7 days`, `Note (optional)`) → ketuk ganda `Ask for Rp50,000` → satu permintaan di DB dengan catatan, `Request sent` dengan `Date`. Batalkan: dialog `Cancel this request?` / `Sinta won't be able to pay it anymore.` / `Cancel request` / `Keep request` → `Cancelled`. `qa-shots-0.9.0/qg5-cancel-request-dialog-light.png`
- **Q-C4 AC1, AC3 (detail kartu):** PIN pad `Show card details` dan detail kartu yang tampil: `screencap` 0 byte.
- **Hendra (Prestige), Q-G5 AC1, G3:** review Rp150,000,000 ke Mandiri: pilihan BI-FAST `In seconds` Rp2,500 / RTGS `Within the hour` Rp25,000 (radio `checked`); RTGS → `Arrives` `Within the hour`. Terkirim, Method `RTGS · fee Rp25,000`, satu `external_transfer` (−150,025,000 / 150,000,000 / 25,000), 1127 OK. Lihat Q-01-F5 untuk `Save PT as a recipient`.
- **Q-E8 AC2–3, AC5:** Wealth Hendra: semua baris aset bisa diketuk; `Less than 1% of assets` untuk deposito Rp1,000,000 dan emas; label grafik sama dengan teks; `OWED` → `Credit cards` / `Not counted in your total`. Raka (Everyday): `Grow your money` hanya `Foreign currency` dan `Investments` (yang belum dimiliki).
- **Q-G8 AC1, Q-G4 AC4:** tabel kurs `Currency` / `You sell at` / `You buy at`, baris `100 JPY` `Rp10,627.50` / `Rp10,802.50`, catatan `Rupiah for one unit. JPY is per 100 yen.`. **Q-G5 AC2, G10:** `Buy USD` dengan segmented `Buy`/`Sell` dan chip mata uang bercentang, `USD 100.00` / `You pay Rp1,636,000 at 1 USD = Rp16,360`, tombol `Buy USD 100.00` → langsung PIN `To buy USD 100.00 for Rp1,636,000.` → `Bought` dengan Date, Rate. Satu `fx_exchange` IDR dan USD seimbang, 1128 OK. `qa-shots-0.9.0/qg5-fx-buy-usd.png`
- **Q-G4 AC2–3:** Investments: eyebrow `YOUR INVESTMENTS`, baris `Total` `Rp26,068,827`, tidak ada `RP`. Detail fund: `−2.34% in 1 month` (U+2212) dengan panah turun.
- **Q-F6 AC1–5:** segmented `Rupiah`/`Grams` (Rupiah dengan Quick Amount dan `About 0.0051 g at Rp1,962,000 a gram`); Grams: keypad `.` (`Decimal point`), tanpa Quick Amount, `0.05 g` dan digit ketiga diabaikan, `You pay Rp98,100` / `Rp1,962,000 a gram`, tombol `Buy 0.05 g for Rp98,100`. Proxy lambat: `To buy 0.05 g of gold for Rp98,100.`, sibuk `Buying 0.05 g…`; sukses `Bought` / `0.05 g` / `for Rp98,100`, Date, Price. Journal Entry `investment` −98,100 = Rupiah sebelum PIN; holding 2.5738 → 2.6238 g. 1129 OK. Fund: `You hold` → `8,100.4278 units`. Lihat Q-01-F6 untuk label tombol Rupiah.
- **Q-G8 AC2–4:** chip `1 month`/`6 months`/`1 year`, label tertinggi dan terendah di tepi kanan grafik, ringkasan TalkBack grafik ada. `qa-shots-0.9.0/qg8-fund-chart-light.png`
- **Q-B1 AC2, G6:** bayar tagihan kartu Voyage `Another amount` Rp100,000, offline sebelum PIN → tetap di layar pilih nominal dengan kotak error offline di atas `Pay Rp100,000`, pilihan tetap. Online → `Paid` / `toward your ORVYN Voyage bill`, Date, `Still owed Rp41,274,000`. Satu `card_payment`, 1130 OK. `qa-shots-0.9.0/qb1-cardpay-offline.png`
- **Q-E8 AC1:** pembayaran kartu terakhir di Cards membuka detail transaksi. Lihat Q-01-F7 (tanda +/− berbeda).
- **X5:** matikan Pockets, Requests, Insights, Vyna, FX, Deposits, Investments → tarik beranda: aksi `Request`, ikon Vyna, kartu insight, `WEALTH`, `POCKETS` hilang tanpa ruang kosong; Help tanpa `Still need help?` (Q-G3 AC3). Nyalakan lagi → kembali. Semua fitur menyala lagi di DB.
- **Pendaftaran baru (nomor uji 0812-9002-0003), Q-D4 AC1, AC3–4:** `Send code` → `Enter the code` / `Enter the 6-digit code for 0812 9002 0003. Step 2 of 8.`, tanpa pindah otomatis, tanpa kotak info (notifikasi menyala, kode dibaca dari `dumpsys notification`, baca saja).
- **Q-D2 AC1–4:** NIK 15 digit + tiga kelompok kosong → layar menggulir ke NIK, fokus, `NIK has 16 digits. You entered 15.`, ringkasan `4 answers need a fix. They're marked above.`; tiap kelompok `Pick one.`; setelah memilih → `1 answer needs a fix. It's marked above.`. NIK `3173010101900004` → `This NIK is already registered with ORVYN.`. NIK baru offline → kotak `You're offline. Check your connection and try again.`, tidak lanjut; online → langkah KTP. Catatan metode: tepat setelah `adb reverse --remove` satu permintaan masih lewat koneksi lama yang terbuka; setelah 20 detik offline perilakunya benar. `qa-shots-0.9.0/qd2-about-you-errors.png`
- **Q-D5 AC1–3:** KTP `Skip this step` → selfie punya panah `Back`; panah → `Photo of your KTP`, lagi → `About you` dengan data tetap.
- **Q-D3 AC1–3:** `654321` dan `111111` → `Choose 6 digits that aren't all the same or in a row.`; `482915` lalu `482916` → `Create your PIN` dengan `Those PINs didn't match. Start again.`; cocok → `We're checking your details`, DB pending.
- **Approve → HP:** layar berubah sendiri ke `Your account is ready`; Sign in memakai `Use your phone number and ORVYN password.` (Q-G5 AC3 untuk nasabah daftar sendiri). Beranda Rp0: WEALTH Rp0, POCKETS kosong, `No transactions yet`.
- **Q-B7 AC3, Q-F8 AC2–3:** banner admin `Show from` besok → `Scheduled … · Position 1`, tidak tampil di HP. Edit `Show from` hari ini → `Banner saved.`, Audit `banner.edit` dengan sebelum/sesudah; tanggal terbalik → `Show until can't be before Show from.`. Tarik beranda → banner baru ikut berputar di posisi kedua (setelah banner lama dengan Position 1). Banner uji dihapus lagi.
- **Q-F8 AC1, AC6:** dropdown `Kind` dengan optgroup Transfers, Payments, Cards, Pockets, Wealth, Admin dan label lengkap; filter `fx_exchange` hanya valas. Settings tanpa sakelar Widget.
- **Q-B5 AC5, Q-G4 AC1:** nasabah Rp0 bayar PLN (`Bill for Oct 2026`) → PIN → kembali ke layar periksa dengan kotak `You don't have enough money for this payment and its fee.` (pesan server), tanpa Journal Entry. **Q-F7 Tepi:** permintaan Rp50,000 ke nasabah Rp0 → `You're Rp50,000 short.`, tombol nonaktif (lihat Q-01-F8).
- **Q-B3 AC3, Q-E12 AC2:** proxy lambat 20 detik di Cards: satu node `Loading` (skeleton), setelah ±15 detik `Couldn't load your cards` / `Couldn't load this. Check your connection and try again.` / `Try again`.
- **Regresi lewat API (Sinta) dan admin:** QRIS merchant `Kopi Senja Cipete` Rp38,000: PIN salah → `Wrong PIN. 4 tries left.`; bayar dua kali dengan kunci idempotensi sama → satu transaksi (id sama); Rp900 juta → `OVER_LIMIT` dengan sisa limit. Reversal admin tanpa `REVERSE` ditolak, dengan `REVERSE` → `Reversed…`, kedua kali → `Not done: this transaction was already reversed.`; filter `kinds=qris_payment` memuat reversal-nya (PRD 14.5). Kantong baru + isi Rp20,000 → riwayat kantong `Added from ORVYN Everyday`. Buku besar OK setelah tiap langkah (1133).
- **Q-B10 AC1–2:** E-statements Raka offline → ketuk September 2026 → daftar tetap, snackbar `Couldn't make your September 2026 statement.` + `Try again` (terlihat ±10 detik); online + `Try again` → menu bagikan Android terbuka (ditutup satu Back). `qa-shots-0.9.0/qb10-statement-offline.png`
- **Q-G1 AC3:** pil `Scan to pay` di banner membuka `Scan to pay` (izin kamera sudah ditolak).
- **Q-C3:** lihat bagian 2. `pg_dump` sebelum reset, passkey palsu disisipkan, `RESET` salah → `Not reset: type RESET and your own password.` (33 nasabah tetap), reset → `Demo reset. 33 customers removed. Create new testers from Customers.`, 0 nasabah/passkey/JE, 4 petugas, audit 79 → 80, dashboard `Ledger ✓ Balanced`. Dump dipulihkan.
