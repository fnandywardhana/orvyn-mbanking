# Laporan IT: Quality pass 0.9.0

IT, 17 Okt 2026. Sumber: `prd.md` (termasuk PO-1..PO-27 dan bagian 14), `stories.md` (Q-A1..Q-G11), `design.md`, `design-system/MASTER.md`. Tidak ada yang di-push, tidak ada build release, tidak ada deploy, HP pemilik tidak dipakai, produksi (`orvyn-server-1`, `orvyn-db-1`) tidak disentuh.

## 1. Ringkasan

Semua 62 story dibangun (P0 dan P1). Server: 110/110 test lolos (92 lama + 18 baru). Android: 92/92 unit test lolos (28 lama + 48 render font 2,0 + 16 test perilaku), `assembleDebug` menghasilkan **0.9.0 (versionCode 12)**. Daftar izin APK **sama persis** dengan 0.8.1. Buku besar dev Balanced setelah migrasi 026 diterapkan di DB dev.

## 2. Commit (semua di `main`, belum di-push)

| Commit | Isi |
|---|---|
| `206b570` | Server P0: penghitung percobaan bersama (K6), cek PIN lama, `/auth/status`, cek NIK, jendela 10 menit Forgot PIN, Reset demo dengan passkey, penolakan tanpa alasan, data waktu di respons pembayaran, migrasi 026 |
| `2cb6c33` | Komponen bersama app (design.md 4), transisi, layar masuk (Sign in, Create PIN, Unlock, terkunci, Forgot PIN), maintenance, alur Transfer, detail rekening |
| `dd1e246` | Beranda, QRIS (termasuk senter, QR-ku dengan nominal), Bills (pulsa, ikon, periode) |
| `8e5840b` | Kartu, bayar tagihan kartu, deposito (cair awal lewat periksa dan sukses), valas, investasi (emas dalam gram, grafik Fund) |
| `11cb15f` | Profil (Ganti PIN, Ganti password, Bantuan, Privacy), passkey, e-statement, pendaftaran |
| `58cf432` | Permintaan uang, kantong, Insights, transfer terjadwal, inbox, Wealth |
| `5821f42` | Filter riwayat, Vyna, gambar bukti, widget, versi 0.9.0 (12) |
| `2c33197` | Server P1: password awal petugas sekali tampil, filter jenis admin, banner (tanggal mulai, urutan, edit), judul riwayat kantong, label periode tagihan, harga emas per gram |
| `7f3a7b5` | Test render font 2,0 permanen, test perilaku, nominal tetap terlihat di layar keypad |

## 3. Peta story → yang dibangun

Singkatan file: `C/` = `android/app/src/main/java/id/orvyn/app/ui/components/`, `UI/` = `.../ui/`, `S/` = `server/src/`.

### A. Font besar
| Story | Dibangun | File | Commit |
|---|---|---|---|
| Q-A1 | `PinScreen`: area atas bisa digulir dan rata bawah, keypad menempel, judul satu baris di font ≥1,5 (mengecil ke 18sp lalu membungkus), kalimat penjelas hanya di label TalkBack judul, slot status membungkus, angka keypad berhenti di 1,5×, tombol 56dp di layar pendek. Dipakai di semua layar PIN | `C/Screens.kt`, `C/Fields.kt` | 2cb6c33 |
| Q-A2 | `AmountScreen`: nominal dan Quick Amount digulir, keypad dan tombol menempel; dipakai di Transfer, QRIS, tagihan kartu, minta uang, split, kantong, deposito, Fund/Bond/Gold, valas, e-wallet, QR-ku. Label tombol boleh 2 baris | `C/Screens.kt` dan semua alur | 2cb6c33..58cf432 |
| Q-A3 | `SuccessScreen`: isi digulir, aksi menempel di atas navigasi | `C/Success.kt` | 2cb6c33 |
| Q-A4 | `AmountText`: mengecil sampai 24sp, lalu membungkus hanya setelah koma (U+200B), ukuran dari nilai akhir, TalkBack membaca tanpa U+200B. Hero, kartu rekening, nominal, sukses, valas, detail rekening | `C/Patterns.kt`, `UI/home/HomeScreen.kt` | 2cb6c33, dd1e246 |
| Q-A5 | Sapaan baris sendiri, aksi cepat grid 112dp, bottom nav label 1,5× dengan QRIS dalam satu kolom, banner dan kartu kantong melebar | `UI/home/HomeScreen.kt`, `UI/App.kt`, `UI/pockets/Pockets.kt` | dd1e246, 2cb6c33 |
| Q-A6 | `ListRow(stack=true)`: nominal pindah ke baris sendiri di ≥1,5, maks 45% di bawahnya; `OrvynButton` `heightIn(min=52)`, label membungkus | `C/Display.kt`, `C/Buttons.kt` | 2cb6c33 |

### B. Tidak ada kegagalan yang diam
| Story | Dibangun | File | Commit |
|---|---|---|---|
| Q-B1 | Semua aksi gagal memberi pesan: jadwal (snackbar, tampilan baru berubah setelah server setuju), kategori (kotak error di sheet), simpan/hapus penerima (sheet/snackbar), instruksi deposito (segmented kembali + kotak error), tutup valas, hapus passkey (snackbar); tagihan kartu dan cair awal tetap di layar periksa dengan kotak error | banyak layar | 2cb6c33..58cf432 |
| Q-B2 | Cair awal: layar periksa "Withdraw early?" → PIN "Withdrawing …" → sukses "Withdrawn" dengan nominal dari server (= Journal Entry) | `UI/wealth/Deposits.kt`, `S/deposits.ts`, `S/api.ts` | 8e5840b, 206b570 |
| Q-B3 | `rememberLoad` + `LoadContent`: skeleton setelah 300ms, error setelah 15 detik, "Try again" yang memuat ulang di tempat; dipasang di semua layar di AC1 (Passkeys tidak lagi "No passkeys yet." saat gagal) | `C/Patterns.kt` + layar | semua commit app |
| Q-B4 | Review Transfer: skeleton "Loading the details…", gagal → "Couldn't load the fee and arrival time." + Try again dan tombol "Details didn't load"; kembali dari PIN dengan error → kotak error, tombol memuat ulang rincian dulu; kunci idempotensi sama sampai berhasil | `UI/transfer/TransferFlow.kt` | 2cb6c33 |
| Q-B5 | PIN sibuk: titik penuh berdenyut, keypad 38% nonaktif, teks per layar (design 4.6), Back diabaikan; goyang/REJECT hanya untuk PIN ditolak; error lain kembali ke layar periksa. Server: semua jalur PIN (termasuk kartu) mengirim sisa percobaan | `C/Fields.kt`, `C/Screens.kt`, `S/transfers.ts`, `S/cards.ts` | 2cb6c33, 206b570 |
| Q-B6 | Banner offline/gagal diperbarui di bawah aksi cepat, aksi cepat 38% dengan snackbar dan label "…, unavailable while offline" | `UI/home/HomeScreen.kt`, `C/Patterns.kt` | dd1e246 |
| Q-B7 | Tarik untuk refresh dan kembali dari latar belakang memuat saldo, Total assets, banner, kartu insight, config sekaligus; bagian tanpa data dan tanpa cache disembunyikan bersama eyebrow | `UI/home/HomeScreen.kt` | dd1e246 |
| Q-B8 | Overlay maintenance (satu judul, kalimat server, Try again, "Still updating…"), dipicu jawaban server `MAINTENANCE`, kembali ke depan, refresh, dan cek `/config` tiap 60 detik | `UI/App.kt`, `data/Api.kt` | 2cb6c33 |
| Q-B9 | Layar terkunci baru dengan "Try again" lewat `POST /auth/status`; "It's still locked…"; terbuka → PIN pad dengan info; cek juga saat kembali ke depan; pesan netral (PO-7) | `UI/auth/AuthScreens.kt`, `S/customers.ts`, `S/api.ts` | 2cb6c33, 206b570 |
| Q-B10 | PDF gagal: daftar tetap, snackbar "Couldn't make your {bulan} statement." dengan Try again | `UI/profile/Statements.kt` | 11cb15f |

### C. Keamanan dan admin
| Story | Dibangun | File | Commit |
|---|---|---|---|
| Q-C1 | `POST /me/pin/check` dihitung ke `failed_attempts`; Ganti PIN 3 langkah dengan pesan "That's not your current PIN. N tries left.", lemah/tidak cocok, layar sukses; `/me/pin` 0.8.1 juga menghitung | `S/api.ts`, `S/transfers.ts`, `UI/profile/ProfileScreens.kt` | 206b570, 11cb15f |
| Q-C2 | `/me/password` menghitung dan mengunci di ke-5; pesan di field yang benar; layar sukses | sama | 206b570, 11cb15f |
| Q-C3 | Reset demo menghapus `passkeys`; test mengisi setiap tabel ber-FK ke `customers` (dicari dari `pg_constraint`) dan gagal bila ada tabel baru tanpa isi; pesan passkey lama dari design | `S/admin-system.ts`, `S/passkeys.ts` | 206b570 |
| Q-C4 | `FLAG_SECURE` berhitung (`SecureWindow`) selama PIN pad tampil; detail kartu tetap aman setelah PIN pad hilang | `C/Patterns.kt`, `UI/cards/CardsScreen.kt` | 2cb6c33, 8e5840b |
| Q-C5 | Dialog konfirmasi untuk hapus penerima, hapus anggaran, tutup valas; tombol destruktif terisi di dialog, teks destruktif 24dp di bawah tombol utama di sheet | `C/Overlays.kt`, layar terkait | 2cb6c33.. |
| Q-C6 | Sakelar kartu pindah langsung + spinner, snackbar setelah server setuju, kembali + pesan bila gagal | `UI/cards/CardsScreen.kt` | 8e5840b |
| Q-C7 | Server menolak alasan kosong/spasi, admin menampilkan `err-box` dan `aria-invalid` | `S/registrations.ts`, `S/admin-registrations.ts`, `S/admin.ts` | 206b570 |
| Q-C8 | Password awal disimpan di memori server per sesi petugas, tampil sekali, URL `/admin/officers` tanpa query, tidak di log/audit | `S/admin-system.ts` | 2c33197 |

### D. Daftar dan masuk
| Story | Dibangun | File | Commit |
|---|---|---|---|
| Q-D1 | Sign in satu kolom bergulir dengan `imePadding`, Next/Done, error di bawah password dan digulir ke sana; subjudul baru | `UI/auth/AuthScreens.kt`, `C/Fields.kt` | 2cb6c33 |
| Q-D2 | About you: gulir dan fokus ke jawaban pertama yang salah, ringkasan di atas tombol, "Pick one.", cek NIK ke server (`POST /register/nik`, token OTP, 10 cek, tanpa log NIK), "Start again" saat terlalu banyak cek | `UI/register/RegisterFlow.kt`, `S/registrations.ts` | 11cb15f, 206b570 |
| Q-D3 | PIN lemah ditolak setelah digit ke-6 (aturan sama dengan server), tidak cocok kembali ke langkah 1 dengan pesan; di pendaftaran, masuk pertama, Ganti PIN, Forgot PIN | `UI/Format.kt`, layar PIN | 2cb6c33, 11cb15f |
| Q-D4 | Tanpa pindah otomatis; kotak info lonceng dengan kode di "Enter the code"; kalimat baru | `UI/register/RegisterFlow.kt` | 11cb15f |
| Q-D5 | Selfie punya panah kembali dan Back sistem ke langkah KTP | sama | 11cb15f |
| Q-D6 | "Forgot PIN?" → sheet → Sign in mode lupa (HP tetap terdaftar) → "Choose a new PIN" → beranda + snackbar; server hanya menerima PIN baru tanpa PIN lama dalam 10 menit setelah masuk dengan password di sesi itu | `UI/App.kt`, `UI/auth/AuthScreens.kt`, `S/customers.ts` | 2cb6c33, 206b570 |

### E. Sistem visual dan aksesibilitas
| Story | Dibangun | File | Commit |
|---|---|---|---|
| Q-E1 | Chip satu gaya dengan centang, role radio/checkbox; segmented untuk Send now/Schedule, Buy/Sell, Renew/Pay out, Rupiah/Grams; chip 48dp | `C/Fields.kt`, `C/Patterns.kt` | 2cb6c33.. |
| Q-E2 | 15 peran tipografi Plus Jakarta Sans, `textTertiary` #616D86, semua `bodySmall` di keterangan jadi `bodyMedium` | `UI/theme/Theme.kt`, banyak layar | 2cb6c33, 5821f42 |
| Q-E3 | Transisi 250/175ms dengan kurva MASTER, pop sebaliknya, langkah dalam alur (`FlowSteps`), tab crossfade 150ms, sukses fade, animasi mati = langsung, `enableOnBackInvokedCallback` | `C/Patterns.kt`, `UI/App.kt`, `AndroidManifest.xml` | 2cb6c33 |
| Q-E4 | `InputGuard` 250ms di setiap layar dan langkah, tombol/baris/chip sekali tembak 600ms, keypad tanpa penahan | `C/Patterns.kt`, `C/Buttons.kt` | 2cb6c33 |
| Q-E5 | `SwitchRow` (seluruh baris `toggleable`, role Switch) untuk kartu, sidik jari, Vyna | `C/Patterns.kt` | 2cb6c33.. |
| Q-E6 | Vyna: tanpa live region saat streaming, jawaban selesai diumumkan sekali, markdown sederhana, sakelar pembaca suara dengan ikon coret, thinking diam saat animasi mati, disclaimer baru, chip 48dp | `UI/vyna/Vyna.kt` | 5821f42 |
| Q-E7 | "Balance hidden", label bagikan e-statement per bulan, inbox: tebal 700/600, role Button hanya untuk baris bertautan | `UI/inbox/InboxScreen.kt`, dll. | 58cf432, 11cb15f |
| Q-E8 | Pembayaran kartu terakhir membuka transaksi; baris Wealth bisa diketuk dengan chevron, "Savings and pockets" → detail rekening, label persen sama, segmen ≥6dp, Grow your money hanya yang belum dimiliki, "Not counted in your total" | `UI/wealth/Wealth.kt`, `UI/cards/CardsScreen.kt` | 58cf432, 8e5840b |
| Q-E9 | StatusChip Waiting/To pay/Paid/Declined/Cancelled/Expired dengan ikon | `C/Display.kt`, `UI/requests/Requests.kt` | 58cf432 |
| Q-E10 | Ikon PLN/BPJS/pulsa/e-wallet | `UI/bills/BillsFlow.kt` | dd1e246 |
| Q-E11 | `chart.barMuted`, kategori kosong "No {kategori} spending this month." | `UI/insights/Insights.kt` | 58cf432 |
| Q-E12 | Skeleton `surfaceVariant` 1,2 detik, tampil setelah 300ms, satu "Loading"; hero skeleton 200×40dp; strip status bar diganti latar halaman dengan ikon gelap di tema terang | `C/Display.kt`, `UI/home/HomeScreen.kt` | 2cb6c33, dd1e246 |
| Q-E13 | Kartu lewat `OrvynCard`, `IconTile` radius 10, nama panjang di gambar bukti maks 2 baris + elipsis | `UI/Receipt.kt`, dll. | 5821f42 |

### F. Story lama
| Story | Dibangun | File | Commit |
|---|---|---|---|
| Q-F1 | Layar `account`: pemilik, nomor grup 4, saldo (ikut sembunyi), "Opened on", Share/Copy, riwayat, banner offline, error + Try again | `UI/home/AccountScreen.kt`, `S/api.ts` (`openedOn`) | 2cb6c33 |
| Q-F2 | Baris "Date" dari waktu server di semua layar sukses; "Share receipt" (gambar sama dengan riwayat) di Transfer, QRIS, Bill, bayar Money Request | `C/Success.kt`, `UI/Receipt.kt` | 2cb6c33.. |
| Q-F3 | Filter Date (sheet + DateRangePicker), Type (6 jenis), Money in/out, Clear filters, tarik untuk refresh, baris "Couldn't load more. Try again", kosong "Nothing matches"; server `kinds=`, reversal ikut jenisnya | `UI/history/HistoryScreen.kt`, `S/api.ts` | 5821f42 |
| Q-F4 | QR-ku: Add/Change/Remove amount, minimum Rp10,000, label TalkBack dengan nominal | `UI/qris/QrisFlow.kt` | dd1e246 |
| Q-F5 | "My number" dari data server, operator dari 4 digit, awalan tak dikenal menonaktifkan Continue | `UI/bills/BillsFlow.kt`, `S/api.ts` (`phone`) | dd1e246 |
| Q-F6 | Segmented Rupiah/Grams, keypad desimal, tanpa Quick Amount, label minimum, "You hold" unit/gram; server dihitung dengan bilangan bulat | `UI/wealth/Invest.kt`, `S/investments.ts` | 8e5840b, 2c33197 |
| Q-F7 | Detail permintaan, Pay → periksa → PIN → sukses + Share receipt, Decline dengan dialog; server mengirim nomor rekening peminta | `UI/requests/Requests.kt`, `S/requests.ts` | 58cf432 |
| Q-F8 | Dropdown Kind lengkap ber-`optgroup`, banner Show from/Position/edit + audit `banner.edit`, migrasi PO-21, Settings tanpa sakelar Widget | `S/admin-transactions.ts`, `S/admin-content.ts`, migrasi 026 | 2c33197, 206b570 |

### G. Copy dan detail
| Story | Dibangun | File | Commit |
|---|---|---|---|
| Q-G1 | Banner di bawah Recent, putar otomatis berhenti saat TalkBack/animasi mati/sentuhan, pil aksi target terpisah 48dp, ketuk membuka halaman yang disentuh | `UI/home/HomeScreen.kt` | dd1e246 |
| Q-G2 | Tombol senter (`enableTorch`, mati saat ditutup, tidak tampil tanpa flash), chip error putih dengan ikon dan live region, deskripsi kamera baru | `UI/qris/QrisFlow.kt` | dd1e246 |
| Q-G3 | FAQ dengan chevron dan Expanded/Collapsed, kartu "Still need help?"/"Ask Vyna" (tersembunyi saat Vyna mati), Privacy dengan baris Camera baru dan Microphone | `UI/profile/ProfileScreens.kt` | 11cb15f |
| Q-G4 | "Oct 2026" (app dan label server), minus U+2212, total investasi di baris sendiri, kurs < Rp1,000 per 100 unit | Bills, Invest, Fx, `S/bills.ts` | dd1e246, 8e5840b, 2c33197 |
| Q-G5 | RTGS "Within the hour", tombol valas "Buy USD 100.00", subjudul Sign in, dialog batal permintaan, "Welcome back, {nama}" disimpan di HP | berbagai | 206b570, 2cb6c33, 8e5840b, 58cf432 |
| Q-G6 | Pencarian penerima kosong `No matches for "{q}"` + New recipient; e-wallet "Minimum Rp10,000" | Transfer, Bills | 2cb6c33, dd1e246 |
| Q-G7 | Transfer dari Vyna dibuka di layar periksa, Back sekali kembali ke Vyna | `UI/transfer/TransferFlow.kt` | 2cb6c33 |
| Q-G8 | Kolom "You sell at"/"You buy at", chip "1 month/6 months/1 year", baris perubahan, label tertinggi/terendah, ringkasan TalkBack | Fx, Invest | 8e5840b |
| Q-G9 | Catatan minta uang pindah ke "Check the request"; split sudah memenuhi | `UI/requests/Requests.kt` | 58cf432 |
| Q-G10 | Judul dihitung saat dibaca: "Added from / Moved to ORVYN Everyday" | `S/api.ts` | 2c33197 |
| Q-G11 | "Show balance", "As of {jam} · hides in 10 seconds", "Balance hidden" | `widget/BalanceWidget.kt` | 5821f42 |

## 4. Test baru

**Server** (`npm test`: **110/110 lolos**)
- `server/test/quality-pass.test.ts` (10): Q-C1 cek PIN lama dan hitungan bersama Unlock, kunci di ke-5; Q-C1 AC6 `/me/pin` 0.8.1; Q-C2 password; Q-B5 PIN kartu; Q-B9 status kunci; Q-D6 jendela 10 menit dan HP tetap terdaftar; Q-D2 cek NIK (token, tersedia/terdaftar, cek ke-11, NIK tidak di log, cek ulang saat kirim); Q-C7 penolakan tanpa alasan; Q-F2/Q-B2 waktu server dan nominal pencairan = Journal Entry; Q-C3 Reset demo dengan passkey, semua tabel ber-FK ke `customers` kosong, petugas dan audit tetap, buku besar OK.
- `server/test/quality-pass-admin.test.ts` (8): Q-C8 password awal; PO-21 migrasi banner; Q-F8 banner (tanggal mulai, urutan, edit, audit, tanggal terbalik); Q-F8 AC1 setiap kind punya label dan filter; Q-G10 judul kantong termasuk entri lama, memo tidak berubah; Q-F3 filter multi-jenis dengan reversal; Q-F6 AC4 0,05 g = Rp75,000 persis; Q-F4 AC4 QR-ku dengan nominal.
- `server/test/passkeys.test.ts` disesuaikan dengan pesan passkey baru.

**Android** (`./gradlew testDebugUnitTest assembleDebug`: **92/92 lolos**)
- `FontScaleTest` (48 render, permanen): Unlock, Create PIN, PIN transaksi, nominal Transfer, nominal deposito, sukses Transfer, sukses terjadwal, beranda Everyday dan Prestige, Sign in, baris riwayat, detail rekening; font 2,0 di 360×640dp dan 411×869dp, terang dan gelap. Gagal bila tombol keypad atau tombol utama terpotong. Render ke `android/app/build/font-scale/` dengan `-Proborazzi.test.record=true`.
- `QualityPassTest` (16): aturan PIN lemah, gram dan harga emas, kurs JPY, periode, minus, operator, filter tanggal, tebal Vyna, bungkus nominal hanya setelah koma, PIN sibuk dan `FLAG_SECURE`, ketik PIN cepat, judul PIN font besar untuk TalkBack, ketukan ganda, penahan input, animasi mati, SwitchRow, chip terpilih, kode OTP di layar, live region Vyna dan disclaimer.
- `WidgetTest` diperbarui untuk copy widget baru. Screenshot katalog (`android/app/screenshots/`) direkam ulang.

## 5. Izin APK (aapt2 35.0.0, debug 0.9.0)

`INTERNET`, `CAMERA`, `POST_NOTIFICATIONS`, `RECORD_AUDIO`, `USE_BIOMETRIC`, `USE_FINGERPRINT`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `com.google.android.c2dm.permission.RECEIVE`, `id.orvyn.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. `diff` dengan `releases/orvyn-0.8.1.apk`: **identik**. Pemeriksaan izin saat build tetap aktif.

## 6. Kompatibilitas dengan APK 0.8.1

Semua perubahan API menambah field atau endpoint; tidak ada field yang dihapus. Perilaku yang berubah untuk 0.8.1 adalah yang diminta PRD: PIN lama salah di `/me/pin` dan PIN salah di detail/bayar kartu sekarang dihitung (K6), pesan terkunci netral, dan `/pin` menolak mengganti PIN yang sudah ada di luar 10 menit setelah masuk dengan password (0.8.1 hanya memanggil `/pin` saat belum ada PIN, jadi tidak terpengaruh). Smoke 0.8.1 di HP tetap tugas QA.

## 7. Keputusan yang saya ambil

1. **Layar periksa tagihan kartu (Q-B1 AC2)** adalah layar pilihan nominal yang sudah punya tombol "Pay {nominal}". Kegagalan setelah PIN kembali ke sana dengan kotak error dan pilihan tetap tersimpan.
2. **Migrasi 026** berisi tiga hal: kolom `registrations.nik_checks` (batas 10 cek harus bertahan restart), `customer_sessions.password_at` (jendela 10 menit), dan penomoran ulang banner. PRD menyebut "satu migrasi (banner)"; tetap satu file.
3. **Forgot PIN:** masuk dengan password di HP yang sama mengirim device token; server mempertahankan Registered Device dan tidak mengirim pesan "Signed in on a new phone". Masuk dengan passkey juga membuka jendela 10 menit (setara password, S3.12 AC5).
4. **`/pin` sekarang menolak mengganti PIN yang sudah ada** di luar jendela itu. Sebelumnya sesi mana pun bisa mengganti PIN tanpa PIN lama; ini menutup celah itu sesuai PO-6.
5. **Pesan kunci server** untuk semua jalur PIN disamakan menjadi "Your account is locked. Ask the ORVYN team to unlock it." (PO-7, design 13). App 0.9.0 memakai copy layar terkunci 6.9.
6. **Status kunci tanpa PIN (catatan 7 bagian 14):** `POST /auth/status` dengan device token; hanya menjawab `locked` dan nama depan untuk HP yang terdaftar, 401 untuk token lain.
7. **Password awal petugas** disimpan di memori server per sesi petugas sampai halaman berikutnya. Bila server restart di antara dua permintaan, password hilang dan petugas cukup mereset dari daftar (ditandai `ponytail:` di kode).
8. **Bug ditemukan dan diperbaiki:** beli emas 0,05 g dengan harga Rp1,500,000/g menghasilkan Rp75,001 di server (pembulatan float). Sekarang dihitung dengan bilangan bulat dan app memakai BigDecimal, jadi angka sebelum PIN = Journal Entry.
9. **Tanggal di layar sukses:** dari server untuk Transfer, terjadwal (`createdAt`), QRIS, Bill, kartu, Money Request, kantong, deposito, valas, investasi, minta uang. Untuk "Requests sent" (split) memakai jam HP karena endpoint split tidak mengirim waktu.
10. **Snackbar** satu untuk seluruh app, 76dp di atas tepi bawah (di atas tombol utama dan bottom nav), bukan dihitung per layar.
11. **Pick dates** memakai `DatePickerDialog` Material berisi `DateRangePicker` (judul "Pick dates", Save/Cancel), bukan dialog layar penuh. "Last 3 months" = hari ini dikurangi 3 bulan.
12. **Daftar Pembuatan PIN pendaftaran** tidak punya teks sibuk saat mengirim, mengikuti design 4.6.
13. **Tidak ada sakelar fitur baru** di `features`: quality pass tidak menambah fitur yang bisa dimatikan; sakelar Widget tidak ditambahkan (K4).

## 8. Yang belum atau perlu dicek QA

- Uji di HP (Galaxy Note10+), TalkBack asli, `uiautomator dump`, `adb shell screencap` saat PIN pad, smoke APK 0.8.1 terhadap server 0.9.0: tugas QA, tidak saya lakukan.
- Q-A1 AC5: perbandingan tampilan font 1,0 dengan 0.8.1 perlu dilihat CX/QA (footer Unlock bertambah "Forgot PIN?", sesuai design 5.1).
- Q-E3 AC2: split bill dan kuis risiko belum memakai `FlowSteps` (berganti tanpa animasi arah); alur lain sudah.
- Q-E12 AC2: layar yang memakai `LoadContent` mengumumkan "Loading" sekali; beranda dan detail rekening memakai skeleton sendiri dengan satu label di wadahnya, belum diverifikasi dengan TalkBack.
- Design 6.6 "banner hilang dengan fade 150ms" dan design 4.8 "fokus TalkBack ke headline saat sukses tampil" tidak diimplementasikan khusus (headline diumumkan lewat live region).
- Server dev dijalankan sebentar di port 8790 untuk menerapkan migrasi 026 ke DB dev (berhasil, buku besar dev `ok: true`), lalu dimatikan.
- `docs/features/2026-10-16-quality-pass/test-plan.md` milik QA tidak saya commit.

## 9. Perbaikan QA putaran 1

IT, 17 Okt 2026. Sumber: `qa-report.md` bagian 7, `design.md` (4.7, 4.11, 4.12, 9.6, 11.1, bagian 17 dan perubahan 4.8, 5, 13), `design-system/MASTER.md` (Money colors, Text field, Success screen). Tanpa HP, tanpa produksi, tanpa push, tanpa build release.

### 9.1 Peta temuan → perbaikan

| Temuan | Perbaikan | File |
|---|---|---|
| Q-01-F1 (major) | `accountDetail` menggulir ke `Share account details` dan ke `Copy number` sebelum memeriksa keduanya, jadi hasilnya tidak lagi bergantung pada banner offline (sudah muncul atau belum). Test render juga tidak pernah menyentuh dev server di mesin ini: `Api.url` diarahkan ke port tertutup (dev server yang menyala menjawab 401 dan mengubah tampilan). Percobaan menunggu banner muncul sudah dicoba dan ditolak: di Robolectric pemuatan kadang tidak selesai dalam 20 detik, jadi justru menjadi sumber flaky baru | `FontScaleTest.kt`, `data/Api.kt` |
| Q-01-F2 | Snackbar berada tepat di atas area aksi menempel yang paling tinggi di layar (4.11). Area menempel ditandai `Modifier.snackAnchor()`: keypad dan tombol di `AmountScreen`, aksi di `SuccessScreen` (`Save as a recipient`, `Share receipt`, `Done`), dan bottom nav. Layar tanpa tanda tetap memakai posisi lama, 76dp di atas tepi bawah | `components/Patterns.kt`, `Screens.kt`, `Success.kt`, `App.kt` |
| Q-01-F3 | Pengumuman TalkBack dan `Read replies aloud` memakai `spoken()`: teks yang sama dengan tampilan, tanpa `- `, `* `, dan `**`. Nomor daftar (`1. `) tetap dibacakan | `vyna/Vyna.kt` |
| Q-01-F4 | `Try again` di baris rincian review Transfer: setelah rincian berhasil dimuat, kotak error lama hilang (4.12 "Hilang: saat mencoba lagi") | `transfer/TransferFlow.kt` |
| Q-01-F6 | Tombol layar nominal mengikuti 4.7: Fund, Bond, dan Gold mode Rupiah `Enter an amount` (0), `Minimum {minBuy}` (beli di bawah minimum), lalu `Buy/Sell {Rp…}`; buka deposito `Enter an amount`, `Minimum Rp1,000,000`, lalu `Next` | `wealth/Invest.kt`, `wealth/Deposits.kt` |
| Q-01-F8 | `Balance after` disembunyikan bila saldo kurang, di bayar permintaan uang dan di review Transfer. Kotak `You're {Rp…} short.` sudah menyebut kekurangannya. Akar masalahnya `Money.format` memakai nilai absolut; fungsi itu tidak diubah karena banyak pemanggil sengaja mengirim nominal negatif | `requests/Requests.kt`, `transfer/TransferFlow.kt` |
| Q-01-F9 | Label TalkBack banner `bannerLabel()`: tanpa titik kedua bila judul sudah diakhiri `.`, `!`, atau `?` (`Scan. Pay. Done. Pay at any…`, `Flying soon? Switch on…`) | `home/HomeScreen.kt` |
| Q-01-F10 | `AmountText` dengan `textAlign` mengisi lebar penuh, jadi `USD 100.00` rata tengah seperti layar nominal lain. Tidak ada perubahan tampilan di pemanggil lain (sukses, QRIS nominal tetap) | `components/Patterns.kt` |
| Q-01-F5 (17.1) | `Save as a recipient`, lalu `Saved to your recipients` dengan ikon `ph_check` 16dp putih, live region polite. Tombol putih ber-role Button | `transfer/TransferFlow.kt`, `components/Success.kt` |
| Q-01-F7 (17.2) | Server: judul pembayaran kartu di sisi kartu kredit dihitung saat dibaca, `Payment from your savings` (entri lama ikut, memo tidak berubah); `/cards` mengirim `accountId`. App: baris `RECENT CARD PAYMENTS` membuka `tx/{id}?account={accountId}`, jadi judul, tanda `+`, warna, dan gambar bukti di detail sama dengan barisnya. Sisi tabungan tetap `ORVYN Voyage bill` / `−` | `server/src/api.ts`, `server/src/cards.ts`, `data/Api.kt`, `cards/CardsScreen.kt`, `App.kt` |
| Q-01-F11 (17.3) | `Phone number` Done = `Send code` bila aktif, selain itu tutup keyboard; `6-digit code` Done = `Confirm` bila 6 digit; `About you`: `Full name` (huruf besar tiap kata, tanpa koreksi otomatis) → `NIK` → `Date of birth` → `Address` dengan Next, `Address` (huruf besar awal kalimat) Done menutup keyboard, melepas fokus, dan menggulir `Work` ke layar tanpa menjalankan `Continue`; `Password` Done = `Continue` bila cukup kuat. Next tidak memvalidasi. Varian `OrvynTextField` untuk `TextFieldValue` sekarang menerima `imeAction`/`onImeAction`. Field tanpa `onImeAction` kini memakai aksi bawaan Android (Next pindah, Done menutup keyboard); sebelumnya tombol itu tidak berbuat apa-apa | `register/RegisterFlow.kt`, `components/Fields.kt` |
| MASTER Text field | Selain pendaftaran: `Name` kantong → `Target` dengan Next. Sign in dan Change password sudah mengikuti aturan sejak 0.9.0 | `pockets/Pockets.kt` |

### 9.2 Test

- Server `npm test`: **111/111 lolos** (110 + 1 baru: Q-01-F7, pembayaran kartu sama di daftar kartu dan detail dari sisi kartu, sisi tabungan tetap, buku besar OK).
- Android `testDebugUnitTest`: **97/97 lolos** (92 + 5 baru di `QualityPassTest`: teks Vyna yang diumumkan tanpa simbol, label banner, tombol buka deposito `Enter an amount`/`Minimum Rp1,000,000`, snackbar di atas aksi layar sukses, aksi keyboard Next/Done). `FontScaleTest` disesuaikan: deposito `Enter an amount`, sukses Transfer `Save as a recipient`, detail rekening deterministik.
- Q-01-F1: `./gradlew testDebugUnitTest --rerun-tasks` dijalankan **4 kali berturut-turut** setelah perbaikan F1 (96/96 setiap kali) dan **3 kali berturut-turut** lagi setelah semua perbaikan (97/97 setiap kali), semuanya tanpa gagal. `assembleDebug` lolos.
- Izin APK debug: identik dengan `releases/orvyn-0.8.1.apk`.

### 9.3 Commit

| Commit | Isi |
|---|---|
| `bc43b15` | Perbaikan app F2, F3, F4, F6, F8, F9, F10 + test |
| `2b3952a` | F1: test font 2,0 detail rekening deterministik |
| `88e242e` | Bagian 17: F5, F7 (server + app), F11, aksi keyboard kantong |

### 9.4 Catatan dan yang belum

- **Q-01-F6 dan design 5.2:** tabel 5.2 menulis tombol nonaktif deposito dan Fund/Bond/Gold sebagai "0.8.1", sedangkan 4.7 (dan QA) meminta `Enter an amount`/`Minimum …`. Saya mengikuti 4.7 sesuai tugas orkestrator. Bond dengan nominal bukan kelipatan Rp1,000,000 tetap `Buy {Rp…}` nonaktif (kalimat `Bonds are bought in steps of Rp1,000,000.` di atasnya); design tidak menentukan label lain.
- **Q-01-F8:** QA memberi dua pilihan (tanda minus atau sembunyikan). Saya menyembunyikan baris karena kotak error sudah menyebut kekurangannya. CX bisa memilih sebaliknya.
- **Sejenis, tidak diubah:** `Your share` di split bill memakai `Money.format` juga, jadi bila bagian teman melebihi tagihan angkanya tampil positif (merah, dengan kalimat `Your friends' shares add up to more than the bill.`). Di luar temuan; CX/PO bisa memutuskan.
- **Snackbar:** layar dengan tombol menempel yang bukan `AmountScreen`/`SuccessScreen` (misalnya layar periksa) tetap memakai posisi 76dp, cukup untuk satu tombol 52dp. Bila QA menemukan layar dengan dua tombol menempel yang tertutup, cukup tambahkan `Modifier.snackAnchor()` di area itu.
- **Aksi keyboard di HP** (keyboard fisik, `input keyevent 66`) dan TalkBack untuk `Saved to your recipients` belum dicek di HP: tugas QA.
