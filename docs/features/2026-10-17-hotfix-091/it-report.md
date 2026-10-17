# Laporan IT: Hotfix 0.9.1

Penulis: IT, 17 Okt 2026. Sumber: `requirement.md`, `prd.md` (termasuk H-1..H-11), `stories.md` (H91-1..H91-5), `design.md`, `design-system/MASTER.md`. Versi **0.9.1 (versionCode 13)**. Tidak ada migrasi database, tidak ada `kind` Journal Entry baru, tidak ada izin Android baru.

## Commit

| Commit | Isi |
|---|---|
| `1c1394d` | H91-3 server: bunga Rp0 tanpa Posting nol, job jatuh tempo per deposito |
| `e582c1e` | H91-1 server: satu minimum (`MINIMUM.transfer`), pesan server, `/config.minTransfer`, bayar permintaan tanpa cek ulang |
| `878ba57` | H91-2 server: `GET /insights/categories/:key/transactions` |
| `6d5658c` | H91-1 aplikasi (plus `Transaction.accountId` dan `Api.categoryTransactions` untuk H91-2) |
| `1f9a58c` | H91-2 aplikasi: detail kategori gabungan, label TalkBack `card ending` |
| `d2ab162` | H91-4: aksi Done/Search di semua field |
| `ead4a07` | H91-5: snackbar di atas tombol QRIS yang menonjol |
| `5fb463d` | Versi 0.9.1, versionCode 13 |

## Peta story → yang dibangun

### H91-1 Setiap permintaan uang bisa dibayar (Q-02-F2)

| AC | Yang dibangun | File |
|---|---|---|
| 1 | Layar nominal Ask dan total Split: `Enter an amount` / `Minimum {minimum}` / `Continue` atau `Next`, tanpa error inline. Quick Amount hanya `>= {minimum}` (`quickAmountsFrom`) | `android/.../ui/requests/Requests.kt` (`belowMinimum`) |
| 2 | Sheet ubah bagian: `The smallest share is {minimum}.` di bawah judul, tombol `Minimum {minimum}` / `Save` | `Requests.kt` |
| 3 | Baris teman di bawah minimum: ikon + `Needs at least {minimum}`, tombol `Minimum {minimum} a share`. TalkBack: satu node, role Button, `onClickLabel` `Change share`, label `{nama}, {Rp…}[, Needs at least {minimum}]` | `Requests.kt` (`sendSharesButton`) |
| 4 | `This bill is too small for {n} friends at {minimum} each. Go back and pick fewer friends.` (live region polite). Kalimat 0.9.0 untuk bagian berlebih ikut mendapat ikon. Panah kembali tetap ke `Who's in?` dengan pilihan tercentang (tidak diubah) | `Requests.kt` (`tooSmallForFriends`, `Warning`) |
| 5 | Server menolak `BAD_AMOUNT` dengan tiga pesan `design.md` 2.5, tanpa permintaan dan tanpa inbox. `Split this bill` hanya untuk transaksi `>= {minimum}` (H-11). Penolakan `BAD_AMOUNT` di aplikasi memuat ulang minimum | `server/src/requests.ts`, `android/.../ui/history/HistoryScreen.kt` |
| 6 | Bayar permintaan memakai `sendTransfer(..., anyAmount: true)`: satu Internal Transfer, tanpa cek ulang minimum (H-9) | `server/src/requests.ts`, `server/src/transfers.ts` |

**Satu minimum (H-2):** `MINIMUM.transfer` (Rp10,000) di `server/src/transfers.ts` dipakai Transfer, Scheduled Transfer, `prepare_transfer` Vyna, Money Request, serta total dan bagian Split. Nilainya dikirim lewat `GET /config` sebagai `minTransfer`. Aplikasi menyimpannya di `AppConfig.minTransfer` (terakhir dimuat, awalnya Rp10,000). Layar nominal Transfer juga memakai nilai ini. Test bisa mengubahnya (`MINIMUM.transfer = 15_000`).

### H91-2 Detail kategori Insights cocok dengan totalnya (Q-02-F3)

| AC | Yang dibangun | File |
|---|---|---|
| 1, 4 | Endpoint baru membaca tabungan dan kartu kredit dengan filter yang sama persis dengan total Insights (`spendWhere`, dipakai `spending()` juga): bulan ini WIB, uang keluar, `SPEND_KINDS`, kategori pilihan nasabah lebih dulu, tanpa yang di-Reversal | `server/src/insights.ts`, `server/src/api.ts` |
| 2 | Subjudul `This month, from ORVYN Everyday and your cards.` Baris kartu memakai `channel` (`ORVYN Voyage ·••• 4821`) dan ikon `card_spend`. TalkBack: `spokenCard` membacakan `… card ending 4821` di semua ListRow (History, Cards, detail) dan di Inbox | `android/.../ui/insights/Insights.kt`, `ui/components/Display.kt`, `ui/inbox/InboxScreen.kt` |
| 3 | Urut terbaru, campur kedua sumber. Ketuk baris membuka `tx/{id}?account={accountId}`, jadi baris kartu membuka sisi kartu seperti dari Cards | `Insights.kt`, `ui/App.kt` |
| 5 | Offline/gagal: error state `Couldn't load these` + pesan + `Try again`, tanpa cache. Nasabah tanpa kartu: hanya tabungan | `Insights.kt` (`LoadContent` yang ada) |
| 6 | Baris memakai `TransactionRow`/`ListRow` yang sudah membungkus di 200%. Uji HP oleh QA | |

`/accounts/:id/transactions` tidak diubah, jadi APK 0.9.0 tetap mendapat daftar lamanya (diuji).

### H91-3 Deposito dengan bunga Rp0 (Q-02-F4)

| AC | Yang dibangun | File |
|---|---|---|
| 1, 2 | `matureDeposit` membuang Posting Rp0 (`nonZero`). Pay out bunga Rp0: satu `deposit_close` dengan dua Posting. Admin "Mature now" menampilkan `Matured. Interest after tax: Rp0.` (kode admin tidak berubah) | `server/src/deposits.ts` |
| 3 | Bunga > 0 tetap empat Posting. Pajak yang dibulatkan ke Rp0 tidak ditulis (juga di jalur rollover) | `deposits.ts` |
| 4 | Renew dengan bunga Rp0: tanpa Journal Entry, inbox `Deposit renewed` (tetap) | `deposits.ts` |
| 5 | `runMaturities` menangkap kegagalan per deposito, menulis `deposit {id} failed to mature` ke log, lalu lanjut. Setiap deposito tetap satu `sql.begin` sendiri, jadi yang gagal di-rollback dan dicoba lagi di putaran berikutnya (H-7). Urutan `maturity_date, account_id` | `deposits.ts` |
| 6 | Suku bunga 0% lewat job: dua Posting | `deposits.ts` |

### H91-4 Done di keyboard (Q-02-F1)

Helper baru di `ui/components/Fields.kt`: `doneAction(enabled) { … }` (aktif: jalankan tombol, nonaktif: tutup keyboard) dan `closeKeyboard()` untuk aksi Search.

| AC | Field → aksi | File |
|---|---|---|
| 1 | New recipient `Account number` → `Check account` bila ≥ 10 digit | `ui/transfer/TransferFlow.kt` |
| 2 | Pay: nomor PLN, BPJS, pulsa, e-wallet → `Continue` bila aktif | `ui/bills/BillsFlow.kt` |
| 3 | `ORVYN account number` → `Find` bila 10 digit; `Nickname` → `Save`; `New password` → `Save password` bila aktif (Next sudah ada di 0.9.0) | `Requests.kt`, `TransferFlow.kt`, `ui/profile/ProfileScreens.kt` |
| 4 | Catatan review Transfer → tombol `Send …`/`Schedule …` (membuka PIN pad, H-3); `Check the request` → `Ask for …`; `Check the shares` → kirim bila aktif | `TransferFlow.kt`, `Requests.kt` |
| 5 | Search (tutup keyboard saja): Find a bank, cari penerima, cari History, cari Help | `TransferFlow.kt`, `HistoryScreen.kt`, `ProfileScreens.kt` |
| 6 | Pendaftaran, Sign in, Pockets, Vyna tidak disentuh. Test 0.9.0 (Q-01-F11) tetap lolos | |

### H91-5 Snackbar tidak menutupi QRIS (Q-02-F5)

| AC | Yang dibangun | File |
|---|---|---|
| 1, 3, 4 | Tombol QRIS menjadi anchor snackbar dengan posisinya setelah `raise(20.dp)` (`snackAnchor(unclipped = true)` memakai `positionInWindow`, bukan bounds yang terpotong bar). Satu komponen `BottomBar` dipakai semua tab, jadi semua tab ikut (H-6) | `ui/components/Patterns.kt`, `ui/App.kt` |
| 2 | Snackbar tidak mengambil sentuhan di luar dirinya (tidak berubah) | |
| 5 | Anchor lain tetap `boundsInWindow`; test 0.9.0 snackbar layar sukses (Q-01-F2) tetap lolos | |

## Test

**Server** (`cd server && npm test`): **118 lolos, 0 gagal.** Baru:
- `test/deposits.test.ts`: H91-3 AC1-2 (Pay out Rp0, dua Posting, inbox, tidak ada Posting 0), AC3 (pajak Rp0 tidak ditulis, pay out dan rollover), AC4 (renew Rp0 tanpa Journal Entry), AC5-6 (job: deposito pertama digagalkan dengan trigger test, deposito kedua tetap cair, yang gagal tetap terbuka dan masuk log, putaran berikutnya berhasil; suku 0%).
- `test/requests.test.ts`: H91-1 AC5 (tiga pesan, tanpa permintaan, tanpa inbox, Rp10,000 diterima), AC6 (satu Internal Transfer Rp10,000; minimum naik ke Rp15,000: buat ditolak dengan `Ask for at least Rp15,000.`, `/config.minTransfer` = 15000, permintaan lama tetap terbayar). Test lama "twenty requests a day" sekarang memakai Rp10,000.
- `test/insights.test.ts`: H91-2 (tabungan + kartu, terbaru di atas, jumlah sama dengan total Insights, transaksi di-Reversal tidak ikut, kategori hanya kartu tampil, kategori kosong kosong, `accountId` per sisi, endpoint lama tetap hanya tabungan).

**Android** (`./gradlew --rerun-tasks testDebugUnitTest assembleDebug`, dijalankan **dua kali**): keduanya **106 test lolos, 0 gagal**, APK debug ter-build. Baru di `app/src/test/java/id/orvyn/app/Hotfix091Test.kt` (9 test): label minimum dan Quick Amount, tombol kirim bagian, kalimat terlalu kecil, `spokenCard`, detail kategori dengan server tiruan (`ServerSocket`, tanpa library baru): baris kartu dan tabungan, label `card ending`, ketuk kartu membuka `accountId` kartu, offline dengan `Try again`, Done aktif/nonaktif dan Search, snackbar 12dp di atas QRIS pada font 1× dan 2×. Test snackbar sudah dicek gagal tanpa perbaikan (−2dp dan −7dp).

## Izin dan versi

`aapt2 dump permissions` APK debug 0.9.1 **sama persis** dengan `releases/orvyn-0.9.0.apk`: ACCESS_NETWORK_STATE, CAMERA, INTERNET, POST_NOTIFICATIONS, RECORD_AUDIO, USE_BIOMETRIC, USE_FINGERPRINT, WAKE_LOCK, c2dm RECEIVE, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. `versionCode='13' versionName='0.9.1'`.

## Kompatibilitas APK lama

- `/config` hanya mendapat field baru `minTransfer`; aplikasi lama mengabaikannya.
- `/accounts/:id/transactions` tidak berubah (detail kategori 0.9.0 tetap hanya tabungan, seperti sebelumnya).
- APK 0.9.0 dan 0.8.1 yang mencoba minta uang atau split di bawah Rp10,000 menerima pesan server baru di kotak error yang sudah ada.

## Keputusan IT

1. **Minimum sebagai konstanta server** (`MINIMUM.transfer`), bukan setting baru: sesuai arahan "tidak perlu field admin baru" dan "tidak ada migrasi". Ditandai `ponytail:` dengan jalur upgrade ke `settings`.
2. **"Nilai terakhir yang diketahui"** di aplikasi berarti yang terakhir dimuat selama aplikasi berjalan (`AppConfig.minTransfer`). `/config` dimuat saat mulai dan setiap menit, jadi tidak disimpan ke `DeviceStore`.
3. **Detail kategori tanpa paging**: satu bulan satu kategori dikirim dalam satu jawaban (`design.md` 3.2 mengizinkan). Ditandai `ponytail:`.
4. **Pesan Scheduled Transfer dan Vyna** ikut memakai minimum yang sama (teksnya `The smallest transfer is {minimum}.`, sama dengan 0.9.0 selama minimum Rp10,000).
5. **Fitur di `features`**: tidak ada fitur baru. Perubahan berada di balik switch yang sudah ada (`requests`, `insights`).
6. **Tidak dicakup** (di luar lingkup PRD): minimum QR terima (`Show QR for`, tetap Rp10,000 tetap di kode), Quick Amount di layar Transfer (semua chip ≥ Rp50,000).

## Yang belum dikerjakan

- Uji di HP (200%, terang/gelap, TalkBack, smoke APK 0.9.0) diserahkan ke QA. Saya tidak menyentuh HP, produksi, `releases/`, atau build release, dan tidak menjalankan dev server.

## Perbaikan QA putaran 1

Sumber: `qa-report.md` (NOT READY, 3 temuan IT). Commit `72c499b`.

| Temuan | Perbaikan | File | Test |
|---|---|---|---|
| H91-F2 (minor) | `PeoplePicker`: teman di `initial` yang bukan penerima tersimpan/terakhir (hasil `Find`) ditaruh di atas daftar saat layar dibuat ulang, jadi setiap teman yang terhitung di `Next: n friends` terlihat dan bisa dilepas (H91-1 AC4) | `ui/requests/Requests.kt` | `Hotfix091Test`: split Rp15,000, Sinta + Hendra lewat `Find`, kembali dari `Check the shares`, Hendra tampil dan dilepas → `Next: 1 friend`. Dicek gagal tanpa perbaikan |
| H91-F3 (minor) | Baris bagian dipindah ke `ShareRow`. Di font besar (≥ 1,5) bertumpuk sesuai `design.md` 2.3: nama, `Needs at least {minimum}`, lalu nominal di barisnya sendiri. `InitialsAvatar` tumbuh dengan skala font (40dp × maks(1, skala × 0,75), 60dp di 200%) sehingga inisial tidak terpotong; tanpa batas skala font, karena MASTER hanya membatasi keypad dan bottom nav. Perubahan avatar berlaku di semua tempat yang memakainya | `Requests.kt`, `ui/components/Success.kt` | `FontScaleTest.checkShares` (360dp dan 411dp, terang dan gelap, 200%): `Rp10,000` utuh dalam satu baris (Q-A4), nama tidak patah di tengah kata (termasuk nama 29 huruf), inisial satu baris, nominal di bawah nama |
| H91-F1 (polish) | Nilai baris di detail transaksi (`Type` dan lainnya) yang berisi nomor kartu bertopeng dibacakan `{kartu} card ending {4 digit}` lewat `spokenCard` | `ui/history/HistoryScreen.kt` | `spokenCard` sudah diuji di `Hotfix091Test` |

**Hasil:** `npm test` 118/118 lolos. `./gradlew --rerun-tasks testDebugUnitTest assembleDebug` dua kali: keduanya 111/111 lolos, APK debug ter-build. Izin APK tetap sama persis dengan `releases/orvyn-0.9.0.apk`. Tanpa HP, produksi, rilis, atau dev server.
