# QA report: Hotfix 0.8.1, tanggal lahir dan Quick Amount

QA, 16 Okt 2026. Build yang diuji: commit `9345d50` (aplikasi) dan `c61d397` (MASTER.md), debug 0.8.1 (versionCode 11), dev server lokal di port 8790. HP: Galaxy Note10+ dengan Samsung Keyboard. Rencana uji: `test-plan.md` di folder ini. Screenshot ada di `qa-shots/` (status bar sudah dipotong). Render ukuran huruf 200% ada di `qa-shots/robo/`.

## Ringkasan

Kedua bug produksi sudah beres. Tanggal lahir bisa diisi dengan keyboard angka Samsung tanpa "/", dan pendaftaran lolos sampai terkirim. Admin menampilkan `1990-08-17`. Setiap Quick Amount di layar deposito, Fund, dan Bond diterima aturan produknya. Layar lain tetap 50k sampai 500k. Buku besar "Balanced" setelah setiap transaksi.

Ada empat temuan:
- 1 minor yang berasal dari hotfix ini: kursor bergeser satu tempat setelah angka ditolak (sudah diperkirakan IT).
- 1 major dan 1 minor yang **sudah ada sejak sebelum 0.8.1**: aplikasi crash saat offline ketika mengirim data, dan ketukan ganda pada tombol bawah ikut terbawa ke layar berikutnya.
- 1 minor tampilan yang sudah ada dan di luar lingkup (keypad terpotong di layar 360dp dengan huruf 200%).

Tidak satu pun dari temuan ini yang timbul atau memburuk karena 0.8.1.

## 1. Test otomatis

| Cek | Hasil |
|---|---|
| `cd server && npm test` | **92/92 lolos** |
| `./gradlew testDebugUnitTest` (dijalankan ulang dengan `--rerun`) | **26/26 lolos** (QrTest 1, ScreenshotTest 12, FormatTest 3, WidgetTest 2, HotfixTest 8) |
| `./gradlew assembleDebug` | Lolos, APK debug 0.8.1 terbentuk |
| Versi | `versionName "0.8.1"`, `versionCode 11` |
| Izin Android | `AndroidManifest.xml` tidak berubah sejak 0.8.0. Tidak ada izin baru |
| Server | `server/` tidak berubah sejak 0.8.0 |
| Rahasia di commit | Tidak ada. Commit hanya menyentuh 7 file aplikasi dan MASTER.md |
| Pengecekan buku besar (`checkLedger`, dev) | OK sebelum dan sesudah setiap transaksi uji: deposito Rp1.000.000 (Raka), deposito Rp50.000.000 (Hendra), transfer Rp50.000 (Raka ke Sinta), Fund Rp100.000, Bond SR-D01 Rp1.000.000, pindah kantong Rp50.000 bolak-balik |
| Ukuran huruf 200% (test Robolectric sementara, tidak di-commit, sudah dihapus dari repo) | **40/40 lolos**: deposito, Fund, Bond, Transfer, dan field tanggal lahir, di 360dp dan 411dp, skala 1.0 dan 2.0, terang dan gelap. Setiap chip berada di dalam layar, tinggi dan lebarnya minimal 48dp, label TalkBack berupa nominal penuh, hanya chip yang benar yang terpilih. Salinan test: `qa-shots/robo/QaFontScaleTest.kt.txt` |

## 2. Yang lolos

### H1 Mengetik tanggal lahir

| AC | Hasil | Bukti |
|---|---|---|
| 1 | Lolos. Field membuka keyboard angka Samsung (tanpa tombol "/"). Mengetik `17081990` lewat tombol keyboard menghasilkan `17/08/1990` | `qa-shots/04-dob-keyboard.png` |
| 2 | Lolos. Isi field per tombol: `1`, `17/`, `17/0`, `17/08/`, `17/08/1`, ... `17/08/1990`. Angka ke-9 dan tombol `.` `,` di akhir diabaikan | dump UI |
| 3 | Lolos. Delapan kali tekan hapus: `17/08/199`, `17/08/19`, `17/08/1`, `17/08/`, `17/0`, `17/`, `1`, kosong | `qa-shots/06-empty-after-8.png` |
| 4 | Lolos. Kursor dipindah ke setelah `08`, hapus 2 kali menjadi `17/19/90`, ketik `09` menjadi `17/09/1990` | `qa-shots/cursor-pair.png` |
| 5 | Lolos. Ditempel lewat menu "Paste" ORVYN: `17/08/1990`, `17081990`, `17-08-1990`, `17.08.1990` semuanya menjadi `17/08/1990`. `1708199012345` menjadi `17/08/1990`. `input text "17/08/1990"` (meniru keyboard yang punya "/") juga menjadi `17/08/1990`. Huruf yang masuk dibuang | `qa-shots/13-pasted-slashes.png` |
| 6 | Lolos. Helper `DD/MM/YYYY` tampil saat field kosong, sedang diisi, dan lengkap | dump UI |

### H2 Tanggal lahir diperiksa dan tersimpan

| AC | Hasil | Bukti |
|---|---|---|
| 1 | Lolos. `31/02/1990`, `17/08/199`, dan field kosong menampilkan `Use the format DD/MM/YYYY.` lalu tetap di langkah ini. `29/02/2000` diterima | `qa-shots/22-h2-format-error.png` |
| 2 | Lolos. `16/10/2016` dan `17/10/2009` (kurang satu hari dari 17 tahun) menampilkan `You need to be at least 17 to open an account.`. `16/10/2009` (tepat 17 tahun, tanggal HP 16 Okt 2026) lolos | `qa-shots/24-age-error.png` |
| 3 | Lolos. Data lengkap membuka "Photo of your KTP" (Step 4 of 8) | |
| 4 | Lolos. Tombol kembali di header menampilkan tanggal tersimpan. ORVYN lalu di-force-stop dan dibuka lagi: draft dilanjutkan di "Photo of your KTP", kembali ke "About you" menampilkan `17/08/1990`, dan Continue langsung jalan tanpa mengetik ulang | `qa-shots/26-resumed-about.png` |
| 5 | Lolos. Pendaftaran `081290010001` dikirim dengan `17/08/1990`. Di admin dev, Applications menampilkan "Date of birth 1990-08-17". Ekspresi admin yang sama dijalankan terhadap DB dev dengan TZ UTC, Asia/Jakarta, America/Los_Angeles, dan Pacific/Kiritimati: selalu `1990-08-17` | admin dev |

### H3 Quick Amount deposito

| AC | Hasil | Bukti |
|---|---|---|
| 1, 2 | Lolos. Tepat `1M`, `5M`, `10M`, `50M`, urutan itu, tidak ada chip di bawah Rp1.000.000 | `qa-shots/31-deposit-1M-dark.png`, `qa-shots/36-deposit-10M-light.png` |
| 3 | Lolos. Setiap chip mengisi `Rp1,000,000`, `Rp5,000,000`, `Rp10,000,000`, `Rp50,000,000` dan Next aktif. Bila satu digit dihapus dari `Rp1,000,000` menjadi `Rp100,000`, tidak ada chip terpilih dan Next nonaktif. Deposito Rp1.000.000 (Raka) dan Rp50.000.000 (Hendra) berhasil sampai bukti, dan buku besar OK | `qa-shots/34-h3-deposit-done.png` |
| 4 | Lolos. Label `1M` sampai `50M`, bukan `1000k` | |
| 5 | Lolos (Robolectric, sesuai jalur pengganti yang disetujui). Pada skala 2.0 di 360dp, chip turun menjadi 3 + 1 dan rata tengah. Pada 411dp keempatnya muat satu baris. Tidak ada yang terpotong, di tema terang dan gelap | `qa-shots/robo/open-a-deposit-w360dp-x2.0-light.png` |
| 6 | Sebagian. Pohon aksesibilitas di HP: label tiap chip `Rp1,000,000` sampai `Rp50,000,000`, dan chip terpilih memiliki status terpilih. Ucapan TalkBack asli tidak didengar karena TalkBack tidak dinyalakan (aturan HP). Lihat catatan C1 | `uiautomator dump` |

### H4 Quick Amount Fund dan Bond

| AC | Hasil |
|---|---|
| 1 | Lolos. Keempat Fund (Arunika, Samudra, Garuda, Cahaya) punya "Smallest purchase" Rp100,000, dengan chip `100k` `250k` `500k` `1M`. Setiap chip mengisi nominal yang sama dan `Buy Rp…` aktif. Pembelian Arunika Rp100.000 berhasil dan buku besar OK (`qa-shots/43-fund-100k-dark.png`) |
| 2 | Lolos. ORI-D01 dan SR-D01 memakai chip `1M` `2M` `5M` `10M`, dan Buy aktif untuk semuanya. Pembelian SR-D01 Rp1.000.000 berhasil dengan satu Holding (bukan dua) dan buku besar OK (`qa-shots/45-bond-1M-dark.png`) |
| 3 | Lolos. Cahaya Ekuitas (di atas profil Moderate Hendra): Buy nonaktif untuk keempat chip sebelum pernyataan dicentang, lalu aktif setelah dicentang (`qa-shots/44-fund-above-profile-ack.png`) |
| 4 | Lolos. Beli dan jual Gold Savings memakai `50k` sampai `500k`, dan semuanya terisi persis. Jual Arunika (Holding Rp100.000): chip `250k`, `500k`, `1M` mengisi Rp100.000. Jual Cahaya (Holding Rp9,9 juta) mengisi nilai chip |
| 5 | Lolos. Label `1M` dan seterusnya, terang dan gelap (`qa-shots/light-pair.png`). Skala 2.0 lewat Robolectric (`qa-shots/robo/buy-fund-*`, `buy-bond-*`) |

### H5 Layar nominal lain

| AC | Hasil |
|---|---|
| 1, 2 | Lolos di HP. Transfer, top up e-wallet (GoPay), kantong (tambah dan ambil), minta uang, split bill (total dan bagian teman), "Another amount" kartu kredit, dan anggaran semuanya menampilkan `50k` `100k` `250k` `500k`. Setiap chip mengisi nominal yang sama persis. Tidak ada chip di bawah minimum layar. **QRIS tidak diuji di HP** (masuknya hanya lewat kamera atau galeri). Dari kode: `QrisFlow.kt` tidak berubah dan memakai nilai bawaan |
| 3 | Lolos untuk kantong. Kantong berisi Rp50.000, chip `100k`/`250k`/`500k` menampilkan `You have Rp50,000 to move.` dan tombol nonaktif (`qa-shots/47-pocket-message.png`). "That's more than you owe" dan pesan saldo Transfer tidak bisa dipicu dengan chip, karena tagihan Hendra Rp41 juta dan saldo Raka Rp13 juta. `CardPayFlow.kt` dan `TransferFlow.kt` tidak berubah di commit ini |
| 4 | Sebagian. Transfer Rp50.000 memakai chip sampai bukti, dan buku besar OK (`qa-shots/38-h5-transfer-receipt.png`). QRIS tidak bisa diuji di HP (lihat AC1) |

### Edge case

| Kasus | Hasil |
|---|---|
| Ketukan ganda pada keyboard tanggal | Lolos. `17` lalu dua kali `0` cepat menjadi `17/00/` (`qa-shots/51-e1-double-zero.png`) |
| Tanggal aneh | `00/00/1990` dan `17/13/1990` ditolak. `17/08/0000` dan `17/08/1800` diterima, sama seperti 0.8.0 dan di luar lingkup PRD |
| Offline saat isi tanggal | Lolos. Langkah "About you" berjalan tanpa server. Lihat temuan F2 untuk saat kirim |
| Draft tersimpan | Lolos untuk draft yang dibuat 0.8.1 (H2 AC4). Draft dari 0.8.0 tidak bisa diuji karena debug dan release ditandatangani berbeda. Format draft (ISO `birthDate`) tidak berubah, dan `dobDigits` membacanya |
| Uang kurang | Lolos. Deposito Rp50.000.000 dengan saldo Rp13 juta ditolak dengan "You don't have enough in ORVYN Everyday for this deposit.", tidak ada deposito dibuat, buku besar OK (`qa-shots/32-e7-not-enough.png`) |
| PIN salah | Lolos. "Wrong PIN. 4 tries left.", tidak ada uang bergerak, PIN benar sesudahnya berhasil (`qa-shots/33-e8-wrong-pin.png`) |
| Ketukan ganda pada chip | Lolos. Dua ketukan cepat pada `2M` menghasilkan `Rp2,000,000` (bukan dobel). `1M` lalu `5M` cepat menghasilkan `Rp5,000,000` dan hanya `5M` terpilih. Pada deposito, `50M` dua kali tetap `Rp50,000,000` |
| Ketukan ganda pada tombol lanjut | Lihat temuan F3 |
| Fitur dimatikan | Lolos untuk `deposits`. Setelah aplikasi dibuka ulang, "Open" di baris Time deposits dan pintu masuknya hilang, tanpa crash. Fitur dinyalakan lagi (`qa-shots/42-e10-deposits-off.png`). `invest` dan `register` tidak dicoba (kode fitur tidak berubah) |
| Terang dan gelap | Lolos. Tema aplikasi Light dan Dark dipakai di deposito, Transfer, minta uang, anggaran, Fund, dan Bond. Chip terpilih dan tidak terpilih jelas berbeda |
| Ukuran huruf terbesar | Lolos lewat Robolectric (lihat bagian 1). Tidak diuji di HP |

## 3. Temuan

### F1 (minor, IT): kursor bergeser satu tempat setelah angka atau tanda ditolak di tengah field tanggal

Ini perilaku yang sudah diperkirakan IT, dan terkonfirmasi di Samsung Keyboard.

**Langkah:**
1. Pendaftaran, "About you", isi `17081990` sehingga tampil `17/08/1990`.
2. Pindahkan kursor ke setelah `17/` (di HP: 6 kali panah kiri dari akhir).
3. Ketik `3`. Angka ditolak, teks tetap `17/08/1990` (benar).
4. Tekan hapus satu kali.

**Diharapkan:** kursor tetap di posisi semula, jadi hapus menghilangkan `7` (`10/81/990`).
**Aktual:** kursor sudah maju satu tempat, jadi hapus menghilangkan `0` (`17/91/990`).

Hal yang sama terjadi pada isi 7 angka dengan tombol `.` di tengah: `17/08/199` lalu `.` lalu hapus menjadi `17/81/99`. Teks tidak pernah salah, hanya posisi kursor setelah input ditolak. Ini hanya terjadi bila pengguna mengetik di tengah field yang sudah penuh, atau mengetik tanda baca. Penyebabnya: `dobInput` mengembalikan teks lama, sementara `OrvynTextField` berbasis String tetap memakai posisi kursor baru. Screenshot: `qa-shots/cursor-pair.png` (kursor sebelum langkah 3).

### F2 (major, IT, sudah ada sebelum 0.8.1): aplikasi crash saat offline ketika mengirim data

**Langkah:**
1. Pendaftaran dengan nomor baru sampai lewat "About you" (`081290010002`).
2. Putuskan koneksi ke server (di uji ini: `adb reverse --remove tcp:8790`).
3. Lanjutkan: lewati foto, buat password, buat PIN, lalu ulangi PIN (aplikasi mengirim pendaftaran).

**Diharapkan:** pesan "You're offline. Check your connection and try again." dan tetap di layar PIN.
**Aktual:** ORVYN tertutup paksa (`FATAL EXCEPTION: java.net.ConnectException ... at id.orvyn.app.data.Api$call$2.invokeSuspend(Api.kt:152)`).

Untungnya draft tidak hilang. Setelah koneksi kembali, pendaftaran bisa dikirim ulang dan berhasil.

**Penyebab:** di `android/app/src/main/java/id/orvyn/app/data/Api.kt`, isi permintaan ditulis di dalam blok `apply` sebelum `try`:

```kotlin
if (body != null) { ...; outputStream.use { it.write(body.toString().toByteArray()) } }   // di luar try
try { val status = conn.responseCode ... } catch (e: IOException) { throw ApiException("offline", ...) }
```

Karena itu, setiap panggilan POST yang membawa data (masuk, transfer, bayar, beli, pendaftaran) akan crash bila tidak ada internet, bukan menampilkan pesan offline. `Api.kt` tidak disentuh 0.8.1 (terakhir berubah di b13da52), jadi 0.8.0 di produksi punya bug yang sama. Ini **bukan regresi**. Perbaikannya kecil: memindahkan penulisan body ke dalam `try`. Screenshot tidak diambil karena dialog crash sistem tidak boleh difoto (aturan HP).

### F3 (minor, IT, sudah ada sebelum 0.8.1): ketukan ganda pada tombol bawah terbawa ke layar berikutnya

**Langkah A:**
1. Hendra, beli SR-D01, ketuk `1M`.
2. Ketuk "Buy Rp1,000,000" dua kali cepat.

**Diharapkan:** layar PIN terbuka tanpa digit terisi.
**Aktual:** ketukan kedua mengenai tombol `0` di PIN pad. Satu digit terisi, dan setelah PIN benar diketik hasilnya "Wrong PIN. 4 tries left." Satu jatah PIN terpakai. Screenshot: `qa-shots/46-double-tap-pin.png`.

**Langkah B:**
1. "Open a deposit", ketuk `50M`.
2. Ketuk Next dua kali cepat.

**Aktual:** layar tenor terlewati dan langsung ke "Check the details" dengan tenor bawaan 3 bulan.

Tidak ada transaksi ganda karena layar review dan PIN tetap menahan. Masalahnya hanya PIN terpakai dan pilihan tenor terlewati. Tombol bawah di semua layar alur ada di posisi yang sama, jadi ini bukan akibat hotfix.

### F4 (minor, PO untuk backlog, sudah ada sebelum 0.8.1 dan di luar lingkup): keypad nominal terpotong di layar 360dp dengan huruf 200%

Di render Robolectric 360×740dp dengan skala 2.0, baris bawah keypad (`000`, `0`, hapus) terpotong oleh tombol Next. Contohnya `qa-shots/robo/open-a-deposit-w360dp-x2.0-light.png` dan `buy-fund-w360dp-x2.0-dark.png`. Chip tidak terpotong, jadi kriteria H3 AC5 dan H4 AC5 tetap lolos. `design.md` 2.3 sudah menyebut keadaan ini sebagai kondisi 0.8.0 di luar lingkup hotfix. Di Note10+ (411dp) tidak terjadi. Dicatat supaya masuk backlog aksesibilitas.

## 4. Catatan (bukan temuan)

- **C1 TalkBack chip:** Compose mengekspos chip sebagai node yang bisa dicentang (`checkable=true`, `checked=true` pada chip terpilih), dengan label nominal penuh di node anak. Biasanya TalkBack membaca label dan "Selected". Kemungkinan petunjuknya berbunyi "double-tap to toggle", bukan "double-tap to activate" seperti contoh di `design.md` 2.5. Belum terverifikasi tanpa TalkBack asli. Bila tim bisnis ingin kepastian, pemilik HP bisa menyalakan TalkBack sendiri sebentar.
- **Data uji di dev server:** pendaftaran tertunda `081290010001` (QA Dob Satu) dan `081290010002` (QA Dob Dua). Raka punya deposito Rp1.000.000 dan transfer Rp50.000 ke Sinta. Hendra punya deposito Rp50.000.000, Arunika Rp100.000, SR-D01 Rp1.000.000, dan kantong "QA empty" (Rp0). Buku besar dev OK. Semua fitur di admin dev dinyalakan kembali.
- **Kode OTP** dibaca dari notifikasi ORVYN lewat `dumpsys notification`, disaring hanya ke teks "ORVYN code". Panel notifikasi tidak dibuka.
- **Status HP setelah uji:** release 0.8.0 dari `releases/orvyn.apk` sudah dipasang lagi (sesi ORVYN pemilik ikut keluar, sesuai praktik yang disetujui). `adb reverse` sudah dihapus. Dev server yang saya jalankan sudah dihentikan. Saat ini **dialog Google Play Protect** (muncul karena pemasangan ulang APK) masih terbuka di HP. Dialog itu bukan dibuka ORVYN, jadi tidak saya sentuh, dan pemilik perlu menutupnya. Clipboard HP berisi teks uji `1708199012345`.
- Test font-scale sementara sudah dihapus dari `android/app/src/test/`. `git status` hanya berisi `CONTEXT.md` (sudah berubah sebelum sesi ini) dan `docs/features/`.

## 5. Putusan

Semua kriteria penerimaan H1–H5 lolos. Yang tidak bisa diuji di HP:
- QRIS: masuknya hanya lewat kamera atau galeri.
- TalkBack asli: tidak dinyalakan karena aturan HP.

Keduanya ditutup lewat review kode dan pohon aksesibilitas. F1 minor dan tidak membuat data salah. F2 dan F3 sudah ada di 0.8.0 produksi dan tidak memburuk di 0.8.1. 0.8.1 justru membuka kembali pendaftaran yang sekarang macet di produksi. Saran untuk orkestrator: F2 (crash offline) ditugaskan ke IT sebagai perbaikan berikutnya, atau dimasukkan ke 0.8.1 bila tim bisnis setuju dengan tambahan satu putaran QA singkat.

**READY FOR RELEASE** (putaran 1, lihat putaran 2 di bawah)

---

## Putaran 2: uji ulang F1 dan F2 (commit `b3501b6`)

16 Okt 2026, sekitar 17.00 UTC. Build debug 0.8.1 (versionCode 11) dari `b3501b6`, dev server lokal di port 8790, Galaxy Note10+. Offline disimulasikan dengan `adb reverse --remove tcp:8790`. Pengaturan HP tidak disentuh.

### Test otomatis

| Cek | Hasil |
|---|---|
| `npm test` | 92/92 lolos |
| `./gradlew testDebugUnitTest assembleDebug --rerun-tasks` | 28/28 lolos, APK debug terbentuk |
| Buku besar dev | OK di awal dan akhir putaran |

### F1, kursor setelah input ditolak: **SELESAI**

| Langkah | Putaran 1 | Putaran 2 |
|---|---|---|
| `17/08/1990`, kursor setelah `17/`, ketik `3`, lalu hapus | `17/91/990` (kursor maju satu) | `10/81/990`, sesuai harapan |
| `17/08/199`, kursor setelah `17/`, ketik `.`, lalu hapus | `17/81/99` | `10/81/99`, sesuai harapan |

Smoke H1 di Samsung Keyboard tetap lolos:
- Ketik per angka: `1`, `17/`, `17/0`, `17/08/` sampai `17/08/1990`. Angka ke-9 diabaikan.
- Delapan kali hapus sampai kosong.
- Edit di tengah: hapus `08` lalu ketik `09` menjadi `17/09/1990`.
- Tempel `17.08.1990` lewat menu Paste ORVYN menjadi `17/08/1990`, dan kursor ada di akhir (satu hapus menghilangkan `0` terakhir).

### F2, crash saat offline: **SELESAI**

| Tempat | Offline | Online lagi | Bukti |
|---|---|---|---|
| Masuk (Raka) | "You're offline. Check your connection and try again." di bawah field, tanpa crash | Ketuk Sign in lagi, masuk ke beranda | `qa-shots/r2-01-signin-offline.png` |
| Transfer, langkah PIN (Raka ke Sinta Rp50.000 lewat chip) | Pesan offline di layar "Check the details", tanpa crash. Journal entry tetap 753, saldo tidak berubah | Lihat F5 di bawah. Setelah kembali ke nominal lalu Continue, transfer berhasil sekali: journal entry 754, saldo turun Rp50.000 sekali, buku besar OK | `qa-shots/r2-02-transfer-offline.png` |
| Kirim pendaftaran (nomor `081290010004`) | Pesan offline di layar "Enter it again", tanpa crash | Ulangi PIN, pendaftaran terkirim satu kali dengan tanggal lahir `1990-08-17` | `qa-shots/r2-05-signup-offline.png` |

Crash log HP (`logcat -b crash`) kosong selama putaran ini.

### Smoke

Masuk, buka deposito Rp1.000.000 lewat chip `1M` sampai "Deposit opened" (`qa-shots/r2-04-deposit-done.png`), dan transfer Rp50.000 (di atas): semuanya lolos. Buku besar OK.

### Temuan baru

#### F5 (minor, IT, sudah ada sebelum 0.8.1): layar review transfer tidak memuat ulang setelah koneksi kembali

**Langkah:**
1. Transfer ke Sinta, ketuk `50k`, Continue, "Send Rp50,000".
2. Putuskan koneksi, lalu masukkan PIN.
3. Pesan offline tampil di layar "Check the details" (benar).
4. Sambungkan lagi, lalu ketuk "Send Rp50,000".

**Diharapkan:** opsi transfer dimuat ulang dan tombol bisa dipakai, atau ada cara mencoba lagi di layar itu.
**Aktual:** kotak rincian tetap "Loading" dan tombol Send nonaktif. Nasabah harus kembali ke layar nominal lalu Continue lagi.

Penyebabnya: `Review` di `TransferFlow.kt` memuat `transferOptions` sekali saja (`LaunchedEffect(Unit)`) dan diam-diam mengabaikan kegagalan. File ini tidak berubah di 0.8.1. Tidak ada uang yang bergerak dua kali. Screenshot: `qa-shots/r2-03-review-after-online.png`.

### Status temuan

| Temuan | Status |
|---|---|
| F1 kursor tanggal lahir | Selesai di `b3501b6` |
| F2 crash offline | Selesai di `b3501b6` |
| F3 ketukan ganda terbawa ke layar berikutnya | Terbuka, minor, sudah ada sebelum 0.8.1 |
| F4 keypad terpotong di 360dp 200% | Terbuka, minor, di luar lingkup (backlog) |
| F5 review transfer tidak memuat ulang | Baru, minor, sudah ada sebelum 0.8.1 |

### Akhir putaran

Release 0.8.0 dari `releases/orvyn.apk` sudah dipasang lagi di HP. `adb reverse` sudah dihapus dan dev server sudah dihentikan.

Dialog Google Play Protect dari pemasangan APK masih terbuka di HP. Dialog itu bukan dibuka ORVYN, jadi tidak disentuh, dan pemilik perlu menutupnya.

Data uji baru di dev:
- Pendaftaran tertunda `081290010004` (QA Dob Empat).
- Deposito Rp1.000.000 untuk Raka.
- Transfer Rp50.000 dari Raka ke Sinta.

## Putusan akhir 0.8.1

Semua kriteria H1–H5 lolos, dan F1 serta F2 sudah selesai. Temuan yang tersisa (F3, F4, F5) semuanya minor, sudah ada sebelum 0.8.1, dan tidak memindahkan uang secara salah.

**READY FOR RELEASE**
