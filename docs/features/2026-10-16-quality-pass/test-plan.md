# Rencana uji: quality pass 0.9.0

QA, 17 Okt 2026. Ditulis sebelum build IT selesai: HP dan server belum dipakai. Sumber: `requirement.md`, `prd.md` (termasuk bagian 4 keputusan PO-1..PO-27 dan bagian 14 catatan CX yang jadi AC), `stories.md` (62 story Q-A1..Q-G11 dan definisi selesai), `design.md` (copy final), `e2e-report.md` dan `test-plan-e2e.md` (regresi 0.8.1), `ui-audit.md`, dan F3/F4/F5 dari `docs/features/2026-10-16-hotfix-dob-pills/qa-report.md`. Hasil uji nanti ditulis di `qa-report.md`.

**Target (PRD bagian 3):** setiap AC lolos, 0 blocker dan 0 major, buku besar Balanced, izin APK sama dengan 0.8.1, server 0.9.0 tetap melayani APK 0.8.1. Putusan: READY FOR RELEASE atau NOT READY.

**Format skenario.** Setiap story punya satu kepala skenario (tujuan, kondisi awal, peran) lalu satu baris per AC: langkah, hasil yang diharapkan, dan cara verifikasi. Copy dalam `kode` harus sama persis dengan `design.md`, termasuk tanda baca dan `…`. Baris "Tepi" adalah edge case tambahan di luar AC.

---

## 0. Lingkungan, data, dan cara verifikasi

### 0.1 Lingkungan

| Bagian | Nilai |
|---|---|
| Build | `main` setelah IT selesai (commit dicatat di laporan), debug **0.9.0 versionCode 12** |
| Server | Dev server lokal port 8790 (perintah di memori proyek `mbanking-project-brief`, rahasia dibaca dari `.env` tanpa ditampilkan), DB dev di container `orvyn-dev-db-1` |
| Admin | Admin dev `http://127.0.0.1:8790/admin`, Super Admin `admin@orvyn.test` (password di memori proyek, tidak ditulis di dokumen ini) |
| HP | Galaxy Note10+ pemilik, Android 12, kira-kira 411×869dp, font sesuai setelan pemilik (tidak diubah), `adb reverse tcp:8790 tcp:8790` |
| Produksi | Tidak disentuh sama sekali |

### 0.2 Data uji

| Siapa | Nomor | Catatan |
|---|---|---|
| Raka Pratama (Everyday) | `081211112222` | Pengirim utama. Password dan PIN di memori proyek |
| Sinta Maharani | `081233334444` | Lawan transaksi, sebagian lewat API |
| Hendra (Prestige) | `081255556666` | Prestige, RTGS, valas, deposito |
| QA EndToEnd | `081290020001` | Dari putaran E2E (password dan PIN di `e2e-report.md` bagian 5) |
| Pendaftaran baru putaran ini | `081290020003`, `081290020004` | Untuk Q-D1..Q-D6 dan Q-B7 AC1 (nasabah Rp0) |
| NIK ganda | `3173010101900004` | NIK pendaftaran lain dari putaran E2E |

### 0.3 Kode cara verifikasi

| Kode | Arti |
|---|---|
| **HP** | Diuji tangan di HP dengan build debug, mengikuti aturan HP (bagian 1) |
| **R200** | Render Robolectric `fontScale 2.0` di **360×640dp dan 411×869dp, terang dan gelap** (4 render per layar). Cek posisi (`bounds`) dan gambar. Disimpan di `qa-shots/r090/` |
| **R** | Test Compose/Robolectric lain: semantics TalkBack, animasi mati, notifikasi mati, server lambat, font 1,0 |
| **UIA** | `adb shell uiautomator dump` di HP: label, `checkable`, `checked`, `clickable`, `bounds`. Pengganti TalkBack asli |
| **API** | Panggilan HTTP langsung ke dev server (curl), termasuk sebagai Sinta |
| **SRV** | Test di `cd server && npm test` (nama test dicatat di laporan) |
| **ADM** | Admin web dev di browser QA (bukan HP) |
| **DB** | Query baca ke DB dev (`docker exec orvyn-dev-db-1 psql ...`) |
| **KODE** | Review kode atau manifest |
| **LAMBAT** | Proxy lambat di host: skrip kecil di scratchpad yang meneruskan port 8791 ke 8790 dengan jeda 3 sampai 20 detik, dan `adb reverse tcp:8790 tcp:8791`. Tidak menyentuh setelan HP |

**[bukan HP]** menandai AC yang tidak boleh atau tidak bisa diuji di HP pemilik. Alasannya selalu salah satu ini: perlu mengubah setelan HP (font, animasi, TalkBack, notifikasi), perlu kamera sungguhan, perlu launcher atau panel notifikasi, atau perlu passkey sungguhan di akun Google pemilik. Penggantinya disebut di baris itu.

**Font 200% hanya lewat R200.** `adb shell settings put system font_scale` adalah setelan HP, jadi dilarang. Di HP hanya font setelan pemilik.

### 0.4 Cek buku besar

Dilakukan di awal, **setelah setiap uji yang memindahkan uang**, setelah Reset demo, dan di akhir:
1. Dashboard admin dev: kartu Ledger menyatakan OK (memanggil `checkLedger`: jumlah posting per mata uang 0, saldo setiap rekening sama dengan jumlah postingnya).
2. Jumlah `journal_entries` sebelum dan sesudah (DB). Satu aksi = tepat satu Journal Entry baru dengan `kind` yang diharapkan. Tidak ada `kind` baru di luar daftar 0.8.1 (PRD bagian 10).
3. Saldo yang tampil di aplikasi sama dengan saldo di admin.

---

## 1. Aturan HP (wajib, berlaku setiap putaran)

HP adalah Galaxy Note10+ pribadi pemilik. Aturan dari `.claude/agents/qa.md`, memori `phone-safety`, dan pelajaran insiden di `e2e-report.md` bagian 5.

1. **Penjaga sebelum setiap input.** Sebelum setiap `input tap`, `input text`, `input swipe`, atau `keyevent`, jalankan `adb shell dumpsys window | grep mCurrentFocus` dan pastikan isinya `id.orvyn.app`. Cek dan input dikirim dalam satu panggilan skrip pembantu yang berhenti total bila cek gagal. Bila gagal: berhenti, jangan kirim input "perbaikan", catat di laporan.
2. **Back tidak pernah di beranda ORVYN.** Back di beranda menutup aplikasi ke launcher. Ini terjadi dua kali di putaran E2E (15 dan 17 Okt), justru saat beranda sedang tergulir dan terlihat seperti layar lain, dan setelah layar sukses yang langsung kembali ke beranda. Maka:
   - Sebelum **setiap** Back, ambil `uiautomator dump` dan pastikan layar sekarang bukan beranda. Beranda dikenali dari tab `Home` yang terpilih di bottom nav atau teks `Total balance`. Bila ragu, anggap beranda.
   - Setelah layar sukses (`Done`, atau Back yang sama dengan Done), **anggap sudah di beranda**. Jangan kirim Back lagi.
   - Lebih baik ketuk panah kembali di layar (node dengan label `Back`) daripada `keyevent 4`.
   - Tidak pernah dua Back berturut-turut tanpa dump baru di antaranya.
   - Untuk kembali ke atas beranda: gulir (swipe ke bawah), bukan Back.
3. **Bila ORVYN tertutup ke launcher:** hentikan semua input. Buka lagi hanya dengan `adb shell am start -n id.orvyn.app/.MainActivity` (tanpa mengetuk launcher). Catat kejadiannya di laporan.
4. **Ketuk hanya koordinat dari dump terbaru** dari layar yang sama. Koordinat harus berada di dalam jendela ORVYN, tidak di status bar (insiden: satu ketukan di y=0). Tidak ada koordinat hafalan.
5. **Sheet sistem hanya bila dibuka ORVYN**, dan hanya ditutup:
   - Menu bagikan Android (Share receipt, Share account details, e-statement): lihat saja, tutup dengan satu Back, tidak pernah memilih aplikasi.
   - Dialog izin kamera: tolak.
   - Sheet passkey Google: tidak membuat passkey (DECISIONS-PENDING #6), tutup dengan satu Back.
   - Dialog yang bukan dibuka uji ini (misalnya Google Play Protect, crash dari versi lama): tidak disentuh, dilaporkan.
6. **Kamera:** tidak pernah memotret. Izin kamera ditolak, langkah KTP dan selfie memakai `Skip this step`. Pemindai QRIS diuji hanya sampai pesan izin kamera.
7. **Tidak mengubah setelan HP apa pun:** ukuran font, skala animasi, TalkBack, notifikasi aplikasi, kunci layar, Wi-Fi, mode pesawat, tema sistem, kecerahan. Offline hanya lewat `adb reverse --remove tcp:8790`. Tema terang/gelap memakai setelan Theme di dalam ORVYN.
8. **Tidak membuka atau menyentuh aplikasi lain**, panel notifikasi, atau Recents. Push dicek dari inbox ORVYN dan dari `dumpsys notification` (baca saja).
9. **Ke latar belakang dan kembali** (Q-B7 AC2, Q-B9 AC5, maintenance): satu `keyevent KEYCODE_HOME` setelah penjaga lolos, lalu kembali dengan `am start`. Tidak ada input apa pun selama launcher di depan.
10. **Screenshot hanya saat ORVYN di depan**, dan status bar dipotong sebelum disimpan ke `qa-shots/`. Screenshot hitam dari `FLAG_SECURE` (Q-C4) adalah hasil yang diharapkan, bukan masalah.
11. **Biaya Vyna:** paling banyak 8 pesan per putaran.
12. **Akhir sesi:** uninstall debug, pasang lagi `releases/orvyn.apk`, `adb reverse --remove-all`, hentikan dev server, kembalikan setelan dev (semua fitur menyala, maintenance off, batas Vyna 30, banner uji dihapus), file test sementara dihapus dan `git status` hanya berisi folder fitur ini. Catat bahwa sesi ORVYN pemilik ikut keluar karena uninstall.

---

## 2. Otomatis dan pemeriksaan build

| ID | Skenario | Diharapkan | Cara |
|---|---|---|---|
| A1 | `cd server && npm test` | Semua lolos, termasuk test baru di definisi selesai poin 1: cek NIK, batas percobaan Ganti PIN/password, Reset demo dengan passkey, penolakan tanpa alasan, password awal petugas, banner (mulai, urutan, edit), filter jenis admin, judul riwayat kantong. Jumlah test dicatat (0.8.1: 92) | SRV |
| A2 | `cd android && ./gradlew testDebugUnitTest assembleDebug --rerun-tasks` | Semua lolos, APK debug terbentuk, `checkDebugPermissions` lolos | R |
| A3 | `aapt2 dump badging` APK debug | `versionName='0.9.0'`, `versionCode='12'` | KODE |
| A4 | Izin: daftar `uses-permission` APK 0.9.0 dibandingkan dengan `releases/orvyn-0.8.1.apk` (`aapt2 dump permissions`) | Selisih kosong. Tidak ada `READ_PHONE_STATE`, `FLASHLIGHT`, lokasi, kontak, penyimpanan (bagian 10) | KODE |
| A5 | R200 untuk daftar layar definisi selesai poin 3: Unlock, Create PIN, PIN transaksi, nominal Transfer, nominal deposito, sukses Transfer, sukses terjadwal, beranda Everyday dan Prestige, Sign in, riwayat, detail rekening. Ditambah layar yang disebut AC (Q-A2 AC1, Q-A3 AC4, Q-B6, Q-D6, Q-E9, Q-F4) | Tidak ada teks terpotong, tidak ada tombol di luar layar, semua node keypad punya `bounds` > 0. Bila IT belum membuat test ini, QA memakai test sementara berbasis `qa-shots/robo/QaE2eFontTest.kt.txt` lalu menghapusnya | R200 |
| A6 | Copy: kumpulkan semua teks dalam `kode` di `design.md` bagian 4 sampai 13, cari di `android/app/src/main` dan `server/src` | Setiap copy ditemukan persis. Yang tidak ditemukan dicek manual (bisa berupa template `{nilai}`) | KODE |
| A7 | `design-system/MASTER.md` | Memuat perubahan design.md 3.1: `textTertiary #616D86`, 15 peran tipe, chip dan segmented button, transisi 250ms, PIN pad sibuk dan aman, success screen dengan Date dan Share receipt, larangan field teks di layar keypad, contoh label bahasa Inggris (definisi selesai poin 7) | KODE |
| A8 | Buku besar awal | OK, jumlah Journal Entry dicatat | 0.4 |
| A9 | Tidak ada rahasia di commit IT | `git diff` 0.8.1..HEAD tanpa `.env`, `secrets/`, `*.jks`, `signing.properties`, password | KODE |

---

## 3. Skenario per story

### A. Font besar

#### Q-A1 Layar PIN tahan font besar (P0)
**Tujuan:** semua layar PIN bisa dipakai di font 2,0 di layar 360×640dp. **Kondisi awal:** fixture layar PIN di Robolectric. **Peran:** nasabah dengan font besar.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Render Create PIN (pendaftaran, masuk pertama), Unlock, PIN Transfer | Empat baris keypad termasuk `0` dan `Delete` tampil utuh, `bounds` > 0 dan di dalam layar | R200 |
| 2 | Di 360×640dp font 2,0, ketuk `2 0 4 0 6 0` di Create PIN, lalu satu `Delete` | Titik terisi 6, lalu satu titik hilang. PIN `204060` bisa dibuat | R |
| 3 | Unlock 360dp font 2,0 dengan PIN salah sisa 2 | `Wrong PIN. 2 tries left.` utuh (boleh 2 baris), area atas menggulir ke slot itu sendiri, `Forgot PIN?`, `Not you? Sign in with a password`, dan `Demo · ORVYN Bank is a fictional bank` bisa dicapai dengan menggulir. Keypad tetap di bawah | R200 |
| 4 | Font 1,5 dan 2,0 | Judul `titleLarge` satu baris (kecuali nama yang tidak muat di 18sp: membungkus, tidak elipsis, PRD 14.1), kalimat penjelas tidak digambar, tapi label judul berisi `"{judul}. {kalimat}"` | R (semantics) |
| 5 | Font 1,0 di 411dp | Sama dengan 0.8.1. Perbedaan satu-satunya: baris `Forgot PIN?` di footer Unlock (disetujui CX, design 5.1). Screenshot test lama cocok | R, HP |
| Tepi | Unlock dengan nama 30 huruf di font 2,0; layar pendek (tombol keypad 56dp, tetap ≥ 48dp) | Judul membungkus 2 baris, keypad utuh | R200 |

#### Q-A2 Layar nominal tahan font besar (P0)
**Tujuan:** keypad nominal dan tombol utama selalu bisa diketuk. **Kondisi awal:** fixture layar nominal. **Peran:** nasabah dengan font besar.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Render nominal Transfer, QRIS diketik, tagihan kartu `Another amount`, minta uang, kantong, buka deposito, beli Fund, valas beli/jual (juga e-wallet, split, QR-ku, Gold Grams) | Baris `000` (`.` di Gold Grams), `0`, `Delete`, dan tombol utama utuh dan bisa diketuk | R200 |
| 2 | 360×640dp font 2,0: ketik `5 0 0 0 0`, ketuk `Continue` | Pindah ke langkah berikutnya | R |
| 3 | Gulir area atas | Nominal dan Quick Amount ikut tergulir, keypad dan tombol diam di tempat | R200 |
| 4 | Quick Amount di font 2,0 | Chip membungkus rata tengah dan terbaca utuh (tidak mundur dari 0.8.1) | R200 |
| 5 | Tombol `Send Rp500,000` / `Pay Rp100,000` di font 2,0 | Label utuh, boleh 2 baris | R200 |
| Tepi | Tombol `000` dibaca `Three zeros`; nominal kosong `Rp0` warna `textTertiary` dan tombol `Enter an amount` | Sesuai design 4.7 dan 5.2 | R, UIA |

#### Q-A3 Layar sukses tahan font besar (P0)
**Tujuan:** `Done` selalu bisa diketuk. **Kondisi awal:** fixture layar sukses. **Peran:** nasabah dengan font besar.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Render sukses Transfer dan sukses terjadwal (6 baris: Date, To, Method, Note, First send, Repeats) | `Done` utuh di bawah, di atas navigasi sistem | R200 |
| 2 | Gulir rincian | Sampai baris terakhir terlihat, `Done` tetap menempel | R200 |
| 3 | Sukses Transfer ke penerima baru | `Save {Sinta} as a recipient` dan `Share receipt` terlihat dan bisa diketuk, boleh 2 baris | R200 |
| 4 | Sukses QRIS, Bill Payment, tagihan kartu, bayar Money Request, buka deposito, beli investasi, cair awal deposito | Sama dengan AC1–3 | R200, HP (font pemilik) |
| Tepi | Back sistem di layar sukses | Sama dengan `Done`, tidak kembali ke PIN. **Di HP: setelah ini anggap sudah di beranda** (aturan 1.2) | HP |

#### Q-A4 Nominal tidak patah di tengah angka (P0)
**Tujuan:** uang selalu terbaca utuh. **Kondisi awal:** fixture beranda Prestige saldo `Rp834,912,398`. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Render beranda Prestige | Saldo satu baris (mengecil sampai 24sp) atau `Rp834,912,` / `398`. Tidak ada baris berisi sebagian kelompok angka | R200 |
| 2 | Render nominal di layar nominal dan layar sukses dengan nominal 9 digit | Aturan sama, minimal 24sp sebelum membungkus. TalkBack membaca nominal asli tanpa U+200B | R200, R |
| 3 | Font 1,0 | Saldo hero 34sp, sama dengan 0.8.1 | R, HP |
| 4 | Animasi hitung naik saldo | Ukuran tidak berubah selama animasi, berakhir di angka tepat | R, HP |
| Tepi | Saldo tersembunyi `Rp••••••••` di font 2,0 | Tidak patah, dibaca `Balance hidden` | R200, UIA |

#### Q-A5 Beranda dan navigasi tahan font besar (P0)
**Tujuan:** label beranda tidak terpotong di tengah kata. **Kondisi awal:** fixture beranda Everyday dan Prestige. **Peran:** nasabah dengan font besar.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Render beranda font 1,5 dan 2,0 | Aksi cepat jadi grid, tidak ada kata terpotong (`Transfer`, `Request`, `Top up`). 411dp dua baris (3+2), 360dp boleh tiga baris (2+2+1) (PRD 14.2) | R200 |
| 2 | Sapaan `Good morning, {nama}` | Membungkus per kata di baris sendiri | R200 |
| 3 | Bottom nav | Label `QRIS` tidak bertabrakan dengan tombol QRIS, semua label utuh. Label berhenti membesar di 1,5× (PRD 14.3) | R200 |
| 4 | Banner dan kartu kantong dengan nama 24 huruf | Judul dan nama utuh, boleh 2 sampai 3 baris | R200 |
| 5 | Font 1,0 | Sama dengan 0.8.1 kecuali urutan bagian (Q-G1) dan banner status | R, HP |

#### Q-A6 Baris daftar dan tombol tahan font besar (P0)
**Tujuan:** nama penerima dan label tombol terbaca utuh. **Kondisi awal:** fixture riwayat dan Sign in. **Peran:** nasabah dengan font besar.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Render riwayat font 1,5 dan 2,0 | Nominal di baris sendiri di bawah judul, `Sinta` / `Maharani` utuh per kata | R200 |
| 2 | Font 1,0 dan 1,3 | Nominal di kanan seperti 0.8.1, paling lebar 45% | R, HP |
| 3 | Sign in font 2,0 | `Sign in with a passkey` utuh, boleh 2 baris, tombol bertambah tinggi | R200 |
| 4 | Semua layar di A5 | Tidak ada OrvynButton yang memotong label | R200 |
| Tepi | TalkBack baris transaksi | Satu node `"{judul}, {subjudul}, minus Rp50,000"` | UIA |

### B. Tidak ada kegagalan yang diam

#### Q-B1 Aksi yang gagal selalu memberi tahu (P0)
**Tujuan:** tidak ada aksi yang gagal tanpa pesan. **Kondisi awal:** Raka masuk, punya jadwal, penerima tersimpan, deposito, FX Account kosong; Hendra punya kartu kredit. Offline lewat `adb reverse --remove tcp:8790`. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Offline, lakukan satu per satu: Resume jadwal, Pause jadwal, Delete jadwal, ganti kategori, simpan penerima (sheet dan layar sukses), hapus penerima tersimpan, ubah instruksi jatuh tempo deposito, tutup FX Account, hapus passkey | Setiap aksi: `You're offline. Check your connection and try again.` di tempat menurut design 6.1 (snackbar atau kotak error di sheet). Tampilan kembali seperti sebelum aksi (status, kategori, daftar, segmented) | HP, UIA |
| 2 | Hendra bayar tagihan kartu, offline sebelum PIN, masukkan PIN | Tetap di layar periksa tagihan, kotak error dengan ikon di atas `Pay {Rp100,000}`. Tidak kembali ke pilih nominal | HP |
| 3 | Cair awal deposito offline setelah PIN | Tetap di `Withdraw early?` dengan kotak error, deposito masih di daftar | HP |
| 4 | Server menolak (nama kantong kosong atau terlalu panjang lewat API; nominal pindah kantong > saldo) | Pesan server di bawah field terkait. Tanpa field: snackbar | HP, API |
| 5 | Online lagi, ulang setiap aksi | Berhasil sekali. Untuk aksi uang: satu Journal Entry, buku besar OK | HP, 0.4 |
| Tepi | Hapus passkey offline: tidak ada passkey sungguhan di HP | Diuji dengan baris passkey palsu di DB dev bila layar Passkeys bisa memuatnya, atau **[bukan HP]** R | R, DB |

#### Q-B2 Layar sukses pencairan deposito lebih awal (P0)
**Tujuan:** nasabah melihat konfirmasi setelah cair awal. **Kondisi awal:** Hendra punya deposito Rp1,000,000 (dibuka di Q-E4 atau regresi). **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Deposits → deposito → `Withdraw early` → periksa (`Withdraw early?`, `You get the principal back. The interest is lost.`, baris Deposit, Matures, Back to ORVYN Everyday, Interest you lose) → `Withdraw Rp1,000,000` → PIN benar | Sukses: `Withdrawn`, `Rp1,000,000`, `back in ORVYN Everyday`, baris `Date`, `From`, `Interest` → `Not paid, because you withdrew before {tanggal}`, `Reference` | HP |
| 2 | Bandingkan dengan admin | Nominal sukses = nominal Journal Entry `deposit_close` = kenaikan saldo tabungan | HP, ADM |
| 3 | `Done` | Daftar Deposits, deposito itu tidak ada | HP |
| 4 | Di layar sukses, Back (setelah dump membuktikan bukan beranda) | Sama dengan Done, tidak kembali ke PIN | HP |
| 5 | Admin dan DB | Posting pokok Time Deposit → Savings Account, tanpa posting bunga. Buku besar OK | ADM, DB, 0.4 |
| Tepi | PIN salah sekali; teks sibuk `Withdrawing Rp1,000,000…` | `Wrong PIN. 4 tries left.` dengan goyang. Teks sibuk lewat LAMBAT | HP, LAMBAT |

#### Q-B3 Layar gagal memuat punya "Try again" (P0)
**Tujuan:** setiap layar data bisa dicoba lagi tanpa keluar. **Kondisi awal:** Raka masuk, data sudah pernah dimuat lalu cache dibersihkan sejauh perlu. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Offline, buka: Cards, Inbox, History, Requests, Wealth, valas, deposito, investasi, e-statement, Help, Insights, kantong, Scheduled, opsi transfer (review), pilih penerima, Bills, QRIS nominal, Passkeys | MessageState error dengan judul dari tabel design 6.3 (misalnya `Couldn't load your cards`), isi `You're offline. Check your connection and try again.`, tombol `Try again`. Layar yang punya cache: cache + banner | HP, UIA |
| 2 | Online, ketuk `Try again` | Isi tampil di layar yang sama, tombol sempat sibuk | HP |
| 3 | LAMBAT 20 detik | Skeleton paling lama 15 detik, lalu `Couldn't load this. Check your connection and try again.` dengan `Try again` | LAMBAT, R |
| 4 | Passkeys offline | Error `Couldn't load your passkeys`, tidak pernah `No passkeys yet.` | HP |
| 5 | Review Transfer BI-FAST offline | Inline `Couldn't load the fee and arrival time.` + `Try again`, tombol `Details didn't load` | HP |
| Tepi | Detail transaksi dan detail permintaan offline | `Couldn't load this transaction` / `Couldn't load this request` + `Try again` | HP |

#### Q-B4 Layar periksa Transfer pulih setelah offline (P0, F5)
**Tujuan:** review Transfer bisa dipakai lagi setelah koneksi kembali. **Kondisi awal:** Raka masuk, saldo cukup. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Transfer BI-FAST BCA Rp100,000 → review → offline → `Send Rp100,000` → PIN | Kembali ke review dengan kotak error offline, tanpa goyang PIN | HP |
| 2 | Online, ketuk `Send Rp100,000` | Rincian dimuat ulang (tombol sibuk), PIN diminta, transfer terkirim sekali | HP |
| 3 | Buka review saat offline | Error inline + `Try again`, tombol nonaktif `Details didn't load` | HP |
| 4 | DB dan admin | Satu Journal Entry `external_transfer` (fee Rp2,500 ke Fee Income), kunci idempotensi sama. Buku besar OK | DB, 0.4 |

#### Q-B5 PIN pad memberi tahu saat diproses (P0)
**Tujuan:** nasabah tahu PIN sedang diproses dan bisa membedakan PIN salah dengan error lain. **Kondisi awal:** Raka masuk; LAMBAT 3 detik untuk AC1–2 di HP. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Unlock, Transfer Rp500,000, bayar tagihan Rp25,000 dengan LAMBAT | Titik tetap terisi, keypad redup 38% dan nonaktif, teks di slot tetap: `Checking your PIN…`, `Sending Rp500,000…`, `Paying Rp25,000…`. Tata letak tidak bergeser. Teks per layar lain sesuai tabel design 4.6 | LAMBAT, HP, R |
| 2 | Selama sibuk, ketuk angka | Tidak ada digit bertambah. Back diabaikan (jangan uji Back di Unlock bila itu bisa menutup aplikasi; cukup R) | LAMBAT, R |
| 3 | Semantics slot status | Live region polite, satu pengumuman | R **[bukan HP]**: TalkBack asli |
| 4 | PIN salah di Transfer, QRIS (API), tagihan, **kartu** (detail kartu dan bayar tagihan kartu) | Goyang + getar REJECT, `Wrong PIN. {N} tries left.`. Server kartu juga mengirim sisa percobaan (PRD 14.8) | HP, API, SRV |
| 5 | Tagihan dengan saldo kurang (nasabah saldo kecil), Transfer di atas Daily Limit | Tanpa goyang dan getar. Kembali ke periksa dengan kotak error, misalnya `You're Rp120,000 short.` / `That's over your daily limit. You can still send Rp4,500,000 today.` | HP, R |
| 6 | Animasi mati | Titik diam terisi | R **[bukan HP]**: setelan animasi |

#### Q-B6 Beranda offline yang jujur (P0)
**Tujuan:** nasabah tahu saldo adalah data lama. **Kondisi awal:** Raka masuk, beranda pernah dimuat. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Offline, tarik beranda | Banner `warningContainer` dengan ikon wifi tercoret tepat di bawah kartu aksi cepat (posisi pertama di bawah hero, design 6.6): `You're offline. These balances are from {Today, 21:40}.` lalu `Sending money works again once you're back online.`. Hari `Today`/`Yesterday`/tanggal (fungsi `dayLabel`, cek R untuk Yesterday) | HP, R |
| 2 | Ketuk aksi `Transfer` | Snackbar `You're offline. Try again when you're connected.`. Alpha 38%. Label `Transfer, unavailable while offline`, tidak "disabled" | HP, UIA |
| 3 | Gagal bukan offline: proxy LAMBAT diatur menjawab 500 (server yang dihentikan terhitung offline, jadi tidak dipakai) | `Couldn't refresh. Showing balances from {hari}, {jam}.` + `Try again` | LAMBAT |
| 4 | Online, tarik | Banner hilang, aksi cepat 100% | HP |
| 5 | Render banner | Utuh di font 2,0, terang dan gelap | R200 |
| Tepi | Offline tanpa cache (data aplikasi baru) | Skeleton saldo, MessageState `Couldn't load your accounts` + `Try again` | R |

#### Q-B7 Tarik untuk refresh memperbarui seluruh beranda (P0)
**Tujuan:** tidak ada dua angka yang bertentangan. **Kondisi awal:** nasabah baru `081290020003` saldo Rp0 (dari Q-D). **Peran:** nasabah dan petugas.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Admin: Simulated Inflow Rp5,000,000 → tarik beranda | Total balance dan `Total assets` sama-sama naik Rp5,000,000, sama dengan layar Wealth. Buku besar OK | HP, ADM, 0.4 |
| 2 | Inflow lagi Rp1,000,000 → HOME → `am start` (aturan 1.9) | Total assets ikut naik tanpa tarik | HP |
| 3 | Admin: banner baru aktif → tarik beranda | Banner baru tampil tanpa buka ulang | HP, ADM |
| 4 | Offline → online → tarik | Banner dan kartu insight tampil lagi | HP |
| 5 | Offline pertama kali tanpa cache Wealth | Bagian Wealth beserta eyebrow `WEALTH` tidak tampil (PO-24) | HP, R |

#### Q-B8 Maintenance langsung mengganti layar (P1)
**Tujuan:** nasabah langsung tahu ORVYN sedang diperbarui. **Kondisi awal:** Raka di beranda. **Peran:** nasabah dan petugas.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Admin: maintenance on. HP diam | Dalam 60 detik layar maintenance tampil (catat waktunya) | HP, ADM |
| 2 | Maintenance off, buka alur Transfer sampai nominal, maintenance on, ketuk `Continue` | Layar maintenance langsung, bukan pesan inline | HP |
| 3 | Isi layar | Hanya `ORVYN is being updated`, satu kalimat server (kosong: `Your money is safe. Try again in a few minutes.`), tombol `Try again`. Tidak ada `Back soon` | HP, UIA |
| 4 | `Try again` saat masih on, lalu off dan `Try again` | `Still updating. Try again in a few minutes.`, lalu Unlock atau beranda | HP |
| 5 | API transfer saat maintenance | Ditolak server seperti 0.8.1 | API |
| Tepi | `Try again` saat offline | `You're offline. Check your connection and try again.` | HP |

#### Q-B9 Layar terkunci bisa dicoba lagi (P1)
**Tujuan:** nasabah yang dibuka petugas bisa lanjut tanpa menutup paksa aplikasi. **Kondisi awal:** QA EndToEnd terkunci (5 PIN salah) dan nasabah lain dikunci manual oleh petugas. **Peran:** nasabah dan petugas.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Layar terkunci | Ikon gembok, `Your account is locked`, `Ask the ORVYN team to unlock it. Once they have, tap Try again.`, tombol `Try again`. `Forgot PIN?` tersembunyi | HP, UIA |
| 2 | Admin unlock, ketuk `Try again` | PIN pad dengan `Your account is unlocked. Enter your PIN.`, 5 percobaan | HP, ADM |
| 3 | `Try again` saat masih terkunci | `It's still locked. Ask the ORVYN team to unlock it.` | HP |
| 4 | Nasabah dikunci petugas (bukan PIN salah) | Pesan sama dan tidak menyebut "too many wrong tries" (PO-7). Server `SIGN_IN_ERRORS.locked` = `Your account is locked. Ask the ORVYN team to unlock it.` | HP, API |
| 5 | Terkunci → HOME → admin unlock → `am start` | PIN pad langsung tampil | HP |
| Tepi | Cek status kunci tanpa PIN hanya membocorkan status kunci perangkat terdaftar (PRD 14.7) | Endpoint menolak tanpa token perangkat, tidak mengembalikan data lain | API, KODE |

#### Q-B10 E-statement: satu PDF gagal tidak menghapus daftar (P1)
**Tujuan:** daftar bulan tetap saat PDF gagal. **Kondisi awal:** Raka di E-statements dengan daftar dimuat. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Offline, ketuk September 2026 | Spinner di baris, daftar tetap, snackbar `Couldn't make your September 2026 statement.` dengan `Try again` | HP |
| 2 | Online, `Try again` | PDF dibuat dan menu bagikan terbuka (tutup dengan satu Back, aturan 1.5) | HP |
| 3 | Daftar gagal dimuat | `Couldn't load your statements` + `Try again` (Q-B3) | HP |

### C. Keamanan dan admin

#### Q-C1 Ganti PIN memeriksa PIN lama dulu dan menghitung percobaan (P0)
**Tujuan:** tebakan PIN dibatasi di Change PIN. **Kondisi awal:** QA EndToEnd masuk, `failed_attempts` 0 (DB). **Peran:** nasabah dan petugas.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Profile → `Change PIN` → `Enter your current PIN` → PIN salah | Langsung setelah digit ke-6: `That's not your current PIN. 4 tries left.`, goyang. Belum ada langkah PIN baru | HP |
| 2 | Salah 2 kali di Change PIN, keluar (tunggu kunci otomatis atau buka ulang), PIN salah di Unlock | `Wrong PIN. 2 tries left.` (hitungan bersama: 2 + 1 = 3 salah). DB `failed_attempts` naik setiap kali | HP, DB |
| 3 | Lanjutkan sampai salah ke-5 di Change PIN | Layar terkunci (Q-B9), admin menampilkan locked, hanya petugas bisa membuka. Sisa 1: `That's not your current PIN. 1 try left.` | HP, ADM |
| 4 | Setelah unlock, PIN lama benar | `failed_attempts` 0, `Choose a new PIN`, aturan Q-D3 berlaku (`123456` ditolak, tidak cocok kembali ke langkah 2 dengan `Those PINs didn't match. Start again.`) | HP, DB |
| 5 | PIN baru dua kali | `Saving your new PIN…`, sukses ikon centang hijau `Your PIN is changed` / `Use your new PIN from now on.` / `Done`. Unlock berikutnya dengan PIN baru | HP |
| 6 | `POST /api/me/pin` gaya 0.8.1 dengan PIN lama salah | `failed_attempts` naik, pesan dengan sisa percobaan | SRV, API |
| Tepi | Offline di langkah 1 | `You're offline. Check your connection and try again.` tanpa goyang, tidak dihitung (DB tetap) | HP, DB |
| Tepi | Back di langkah 2 atau 3 | Kembali ke langkah 1, PIN yang diketik kosong | HP |

#### Q-C2 Ganti password menghitung percobaan (P0)
**Tujuan:** tebakan password dibatasi, pesan di field yang benar. **Kondisi awal:** Raka masuk, `failed_attempts` 0. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Profile → `Change password` → password lama salah → `Save password` | Di bawah `Current password`: `That's not your current password. 4 tries left.`, field fokus | HP, UIA |
| 2 | Hitungan bersama Q-C1; salah ke-5 | Layar terkunci | HP, SRV |
| 3 | Password lama benar, baru `abc` | `failed_attempts` 0, di bawah `New password`: `Use at least 8 characters, with both letters and numbers.` | HP, DB |
| 4 | Password baru kuat | Sukses `Your password is changed` / `Use your new password the next time you sign in on a phone.` / `Done`. Sign in berikutnya dengan password baru berhasil (lewat API agar tidak harus melupakan HP) | HP, API |
| 5 | Test server | `POST /api/me/password` salah menaikkan hitungan dan mengunci di ke-5 | SRV |
| Tepi | Offline | Kotak error di atas tombol, tidak dihitung | HP, DB |
| Tepi | Aksi keyboard | `Current password` = Next, `New password` = Done yang menyimpan | HP |

#### Q-C3 Reset demo berhasil walau ada passkey (P0)
**Tujuan:** Reset demo tidak pernah gagal karena tabel yang terkait nasabah. **Kondisi awal:** dijalankan **paling akhir** di putaran (menghapus data uji). Sebelumnya `pg_dump` DB dev ke scratchpad. Satu baris passkey dibuat lewat test server atau insert DB (tidak membuat passkey sungguhan di HP). **Peran:** Super Admin.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | System → Reset demo, `RESET` + password | `Demo reset. {N} customers removed. Create new testers from Customers.`, tidak ada 500 | ADM |
| 2 | DB | `passkeys` kosong, semua tabel nasabah kosong | DB |
| 3 | Akun petugas dan audit log | Tetap ada. Dashboard: Ledger OK | ADM, 0.4 |
| 4 | Test server | Satu baris di setiap tabel dengan FK ke `customers`, reset, semua kosong. Test membaca daftar FK dari skema (tabel baru ikut tertangkap) | SRV, KODE |
| 5 | Masuk dengan passkey yang dihapus | Di bawah tombol passkey: `This passkey doesn't work anymore. Sign in with your phone number and password.`, tanpa crash | SRV, R **[bukan HP]**: butuh passkey sungguhan |
| Tepi | Konfirmasi salah | `Not reset: type RESET and your own password.`, data tidak berubah | ADM |
| Penutup | Pulihkan dump DB dev, cek buku besar | Data uji kembali, Ledger OK, dump di scratchpad dihapus | DB |

#### Q-C4 PIN pad tidak bisa direkam layar (P0)
**Tujuan:** PIN tidak bocor lewat rekaman. **Kondisi awal:** Raka masuk. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | `adb shell screencap` di Unlock, Create PIN (masuk pertama, Forgot PIN), PIN Transfer, Change PIN, detail kartu | Gambar hitam atau gagal | HP |
| 2 | screencap di layar sukses setelah PIN | Gambar normal | HP |
| 3 | Detail kartu tampil | Tetap diblokir | HP |
| 4 | Izin | Tidak ada izin baru (A4) | KODE |
| Tepi | Thumbnail Recents hitam | **[bukan HP]**: Recents tidak dibuka (aturan 1.8). KODE: `FLAG_SECURE` dipasang di `DisposableEffect` PinPad | KODE |

#### Q-C5 Aksi destruktif selalu dikonfirmasi (P0)
**Tujuan:** satu salah ketuk tidak menghapus apa pun. **Kondisi awal:** Raka punya penerima tersimpan, anggaran Food and drinks; Hendra punya FX Account USD kosong. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Sheet penerima → `Remove from saved` → `Cancel`, lalu ulang → `Remove` | Dialog `Remove {nama} from saved?` / `You can save them again after your next transfer.`. Cancel tidak menghapus, Remove menghapus | HP |
| 2 | `Remove budget` | `Remove your Food and drinks budget?` / `Reminders for this category stop.` / `Remove` | HP |
| 3 | FX Account USD kosong → `Close` | `Close your USD account?` / `It's empty, so nothing moves. You can open it again when you buy USD.` / `Close account` | HP |
| 4 | Sheet | Tombol destruktif teks merah dengan ikon tong sampah, di bawah tombol utama dengan jarak ≥ 24dp (bounds) | UIA |
| 5 | Dialog terang dan gelap | Tombol konfirmasi isi `error` + label `onError`, fokus awal di judul | HP (tema ORVYN), R |
| Tepi | Cancel request (Q-G5 AC4) dan Decline (Q-F7 AC3) memakai pola yang sama | Lihat story itu | HP |

#### Q-C6 Status Freeze dan sakelar kartu yang pasti (P1)
**Tujuan:** nasabah yakin kartu benar-benar dibekukan. **Kondisi awal:** Raka di Cards, kartu aktif. **Peran:** nasabah dan petugas.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | LAMBAT 3 detik, ketuk Freeze | Sakelar langsung pindah, spinner kecil, baris nonaktif | LAMBAT, HP |
| 2 | Server menjawab | Baru lalu snackbar `Card frozen` dan kartu abu-abu. Simulasi belanja admin ditolak + push declined | HP, ADM |
| 3 | Offline, ketuk Freeze | Sakelar kembali ke posisi semula, snackbar offline | HP |
| 4 | Online payments, Overseas, Contactless | `Online payments are off`/`on`, `Overseas payments are off`/`on`, `Contactless is off`/`on`, perilaku sama | HP |

#### Q-C7 Server menolak penolakan tanpa alasan (P1)
**Tujuan:** setiap penolakan punya alasan. **Kondisi awal:** satu pendaftaran pending (`081290020004`). **Peran:** petugas.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | `POST /admin/applications/:id/reject` dengan `reason=` (sesi petugas, curl) | Ditolak, status tetap pending (DB) | API, DB, SRV |
| 2 | `reason=%20%20%20` | Ditolak | API, SRV |
| 3 | Lewat halaman admin (hapus `required` di devtools) | `err-box` `Write a reason first. The applicant sees it in the app.`, field fokus dengan `aria-invalid="true"` | ADM |
| 4 | Tolak dengan alasan | `Rejected. The applicant sees your reason in the app.`, pendaftar menerima alasan (push + layar status di HP) | ADM, HP |

#### Q-C8 Password awal petugas tidak lewat URL (P1)
**Tujuan:** password awal tidak tersimpan di riwayat atau log. **Kondisi awal:** Super Admin di Officers. **Peran:** Super Admin.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Tambah petugas (curl `-i` dan browser) | Header `Location` dan URL hasil `/admin/officers` tanpa query, tanpa password | API, ADM |
| 2 | Halaman hasil, lalu muat ulang | Kotak `Officer added` dengan `Starting password: …`, `Copy password`, `This is the only time it's shown. {nama} must change it at first sign-in.` Setelah muat ulang kotak hilang | ADM |
| 3 | Log server dan Audit | Password tidak ada di log stdout dev server dan di Audit Entry | KODE, ADM, DB |
| 4 | Masuk sebagai petugas baru | Berhasil dan wajib ganti password | ADM |
| Tepi | Email sudah ada | `That email already has an officer account.` | ADM |

### D. Daftar dan masuk

#### Q-D1 Sign in: password dan pesan terlihat (P0)
**Tujuan:** nasabah bisa masuk tanpa menebak. **Kondisi awal:** HP di layar Sign in (lewat "Not you?" atau instal baru; nasabah Raka). **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Ketik nomor dengan keyboard terbuka | Field password terlihat penuh dan bisa diketuk (bounds di atas keyboard) | HP, UIA |
| 2 | Aksi keyboard di nomor | Next memindah fokus ke password. Done di password menjalankan Sign in | HP |
| 3 | Password salah | `That phone number and password don't match.` terlihat di atas keyboard tanpa menutup keyboard | HP |
| 4 | Font 2,0 360dp tanpa keyboard | Password dan `Sign in` bisa dicapai dengan menggulir | R200 |
| 5 | Offline | `You're offline. Check your connection and try again.` terlihat dengan cara yang sama | HP |
| Tepi | Kalimat layar | `Use your phone number and ORVYN password.` (Q-G5 AC3) | HP |

#### Q-D2 About you: kesalahan terlihat, NIK ganda diperiksa (P0)
**Tujuan:** calon nasabah tahu field mana salah dan NIK sudah terdaftar sebelum lanjut. **Kondisi awal:** pendaftaran `081290020003` sudah lolos OTP. **Peran:** calon nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | NIK 15 digit, gulir ke bawah, `Continue` | Layar menggulir ke NIK, field fokus, ringkasan `1 answer needs a fix. It's marked above.`. Dua kesalahan: `2 answers need a fix. They're marked above.` | HP, UIA, R (TalkBack) |
| 2 | Kelompok pilihan kosong | `Pick one.` di bawah judul kelompok. `Pick one answer for each question.` tidak ada | HP |
| 3 | NIK `3173010101900004` | Sibuk (`Checking your NIK`), lalu `This NIK is already registered with ORVYN.` di bawah NIK, tidak lanjut. Juga NIK milik Customer aktif | HP |
| 4 | NIK baru | Lanjut. Offline: kotak error offline, tidak lanjut | HP |
| 5 | Endpoint cek NIK | Tanpa token OTP ditolak; jawaban hanya tersedia/terdaftar tanpa data lain; cek ke-11 ditolak dengan `Too many NIK checks for this sign-up. Start again from your phone number.` dan `Start again`; NIK tidak muncul di log server | SRV, API, KODE |
| 6 | NIK didaftarkan pihak lain (API) di antara cek dan kirim | Server menolak saat pendaftaran dikirim | SRV, API |

#### Q-D3 Create PIN: lemah ditolak langsung, tidak cocok diberi tahu (P0)
**Tujuan:** calon nasabah tahu PIN bermasalah saat itu juga. **Kondisi awal:** pendaftaran `081290020003` di langkah Create PIN. **Peran:** calon nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | `123456`, `111111`, `654321` | Langsung setelah digit ke-6: `Choose 6 digits that aren't all the same or in a row.`, goyang, titik kosong, tetap di `Create your PIN` | HP |
| 2 | `482915` lalu `482916` | Kembali ke `Create your PIN` dengan `Those PINs didn't match. Start again.` terlihat (E2E-06) | HP, UIA |
| 3 | `482915` dua kali | Pendaftaran terkirim, `We're checking your details` | HP |
| 4 | Masuk pertama (nasabah dari persona), Change PIN langkah 2, Forgot PIN | Aturan sama | HP |
| 5 | Pesan di font 2,0 360dp | Utuh (Q-A1) | R200 |

#### Q-D4 Kode OTP terlihat bila notifikasi mati (P0)
**Tujuan:** calon nasabah tanpa notifikasi tetap bisa lanjut. **Kondisi awal:** fixture. **Peran:** calon nasabah. Seluruhnya **[bukan HP]**: notifikasi HP pemilik tidak boleh dimatikan.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Notifikasi mati, `Send code` | Tidak ada pindah otomatis | R |
| 2 | Layar `Enter the code` | Kotak info ikon lonceng `Notifications are off, so here's your code: {123456}`, dibaca per angka | R |
| 3 | Kalimat | `Enter the 6-digit code for {0812 3456 7890}. Step 2 of 8.`, tanpa "as a notification" | R, HP (keadaan notifikasi menyala) |
| 4 | Notifikasi menyala | Kode lewat notifikasi, kotak info tidak tampil | HP (`dumpsys notification`, baca saja) |
| 5 | Kedua keadaan | Test Compose/Robolectric ada dan lolos | R |

#### Q-D5 Langkah selfie punya panah kembali (P1)
**Tujuan:** calon nasabah bisa mengulang langkah KTP. **Kondisi awal:** pendaftaran di langkah KTP, izin kamera ditolak. **Peran:** calon nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | KTP `Skip this step` → selfie | Panah kembali di top bar, posisi sama dengan KTP, label `Back`, 48dp | HP, UIA |
| 2 | Ketuk panah; ulang dan pakai Back sistem (bukan beranda, aman) | Kembali ke `Photo of your KTP` | HP |
| 3 | Kembali maju | Data About you dan langkah sebelumnya tetap | HP |

#### Q-D6 "Forgot PIN?" di Unlock (P1)
**Tujuan:** nasabah lupa PIN tidak harus melupakan HP. **Kondisi awal:** Raka di Unlock. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Unlock | `Forgot PIN?` di atas `Not you? Sign in with a password`, keduanya terlihat di font 2,0 | HP, R200 |
| 2 | Ketuk | Sheet `Forgot your PIN?` / `Sign in with your phone number and password, then choose a new PIN.` / `Sign in with password` / `Not now` | HP |
| 3 | Sign in (kalimat `Sign in with your password, then choose a new PIN.`) → `Choose a new PIN` dua kali | `Saving your PIN…`, beranda dengan snackbar `Your PIN is changed` (PRD 14.6). Unlock berikutnya dengan PIN baru. DB: perangkat tetap Registered Device | HP, DB |
| 4 | Server | PIN baru tanpa PIN lama diterima ≤ 10 menit setelah masuk password di sesi itu, ditolak setelahnya dengan `That took too long. Sign in with your password again.` | SRV |
| 5 | Password salah di mode ini; nasabah terkunci | Salah dihitung (DB). Terkunci: layar terkunci | HP, DB |
| Tepi | Back dari Sign in dan dari Create PIN (bukan beranda) | Kembali ke Unlock, PIN lama tetap berlaku | HP |

### E. Sistem visual dan aksesibilitas

#### Q-E1 Chip pilihan yang jelas di dua tema (P0)
**Tujuan:** pilihan aktif selalu jelas. **Kondisi awal:** Raka masuk, tema ORVYN terang lalu gelap. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Pengulangan jadwal, Quick Amount, pilihan pendaftaran, mata uang, Vyna, rentang Fund, di dua tema | Tidak dipilih `surface` + garis 1dp `outlineStrong` + teks `text`; dipilih `primary` + `onPrimary` + centang 16dp | HP, R |
| 2 | Review Transfer, valas, sheet deposito, beli emas | Segmented `Send now`/`Schedule`, `Buy`/`Sell`, `Renew`/`Pay out`, `Rupiah`/`Grams` | HP |
| 3 | Dump | Status terpilih terbaca (`selected`/`checked`), misalnya `Schedule, selected` | UIA, R |
| 4 | Bounds filter riwayat, pilihan pendaftaran, saran Vyna | Tinggi ≥ 48dp (≥ 126px di HP) | UIA |
| 5 | MASTER | Aturan chip dan segmented baru | KODE |

#### Q-E2 Satu jenis huruf dan kontras AA (P0)
**Tujuan:** semua teks Plus Jakarta Sans dan terbaca. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | `theme/Theme.kt` | `textTertiary` terang `#616D86`, gelap tetap `#7C879E` | KODE |
| 2 | 15 peran tipografi; screenshot Wealth, Insights, investasi, deposito, valas, Vyna, kantong, kartu RM, pemilih tanggal | Semua peran Plus Jakarta Sans + `tnum`, tidak ada Roboto (bandingkan bentuk `a`, `g`, `1`) | KODE, HP |
| 3 | Valas `JPY 1,000` | Huruf sama dengan nominal Rupiah | HP |
| 4 | Keterangan baris | 14sp (`bodyMedium`), `bodySmall` hanya caption grafik (grep) | KODE |
| 5 | MASTER §1 | Tabel kontras dengan `#616D86` | KODE |

#### Q-E3 Transisi layar yang memberi arah (P0)
**Tujuan:** perpindahan layar menunjukkan maju/mundur. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Buka layar dan kembali (rekam layar ORVYN sebentar dengan `screenrecord` hanya saat ORVYN di depan, atau amati) | Geser dari kanan + fade 250ms, kembali sebaliknya, tidak ada fade 700ms | HP, KODE |
| 2 | Langkah penerima → nominal → periksa → PIN | Berganti dengan arah yang sama | HP |
| 3 | Animator 0 | Langsung tanpa animasi | R **[bukan HP]**: setelan animasi |
| 4 | Manifest dan Back di setiap alur | `android:enableOnBackInvokedCallback="true"`; Back mundur satu langkah (aturan 1.2) | KODE, HP |
| 5 | Durasi dan kurva | Sesuai MASTER §4 dan design 4.3 | KODE |

#### Q-E4 Ketukan ganda tidak terbawa (P0, PO-1, F3)
**Tujuan:** satu ketukan ganda tidak memakai jatah PIN atau melewati langkah. **Kondisi awal:** Raka dan Hendra masuk, saldo cukup. Ketukan ganda: dua `input tap` dalam satu perintah shell (jeda < 100ms), penjaga dicek sebelum perintah. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Raka → Sinta Rp50,000 → ketuk ganda `Send Rp50,000` | PIN pad `0 of 6 digits entered` (UIA). Setelah PIN: satu transfer, buku besar OK | HP, UIA, 0.4 |
| 2 | Hendra beli Bond → ketuk ganda `Buy Rp1,000,000` | PIN pad 0 digit. Setelah PIN: satu `investment`, buku besar OK | HP, UIA, 0.4 |
| 3 | Buka deposito → ketuk ganda `Next` | Layar tenor tampil, tidak terlewat. Lanjutkan buka Rp1,000,000 (dipakai Q-B2), buku besar OK | HP, 0.4 |
| 4 | `Continue` nominal Transfer dan `Next` lain | Satu langkah saja | HP, R |
| 5 | Ketik 6 digit PIN secepat mungkin (6 tap dalam satu perintah) | 6 digit terisi | HP |

#### Q-E5 Semua sakelar membacakan statusnya (P0)
**Tujuan:** pengguna TalkBack tahu posisi sakelar. **Peran:** pengguna TalkBack.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Dump Cards | Freeze card, Online, Overseas, Contactless: `checkable=true`, `checked` sesuai | UIA |
| 2 | Bacaan | `Freeze card, Stops every payment until you unfreeze it, switch, on` | R **[bukan HP]**: TalkBack asli |
| 3 | Sakelar lain (biometrik bila tampil, Read replies aloud, Auto-save kantong) | Sama | UIA |
| 4 | Ketuk teks judul baris, lalu ketuk sakelarnya | Masing-masing mengubah sekali | HP |

#### Q-E6 Vyna untuk TalkBack, jawaban rapi (P0)
**Tujuan:** Vyna bisa dipakai pengguna TalkBack. **Kondisi awal:** Raka di Vyna (hemat pesan, aturan 1.11). **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Streaming | Gelembung tanpa live region, `Vyna is thinking…` sekali | R |
| 2 | Selesai | Jawaban diumumkan sekali | R |
| 3 | Tanya "ringkas rekening saya dalam daftar" | Bullet `•` dengan indentasi, `**tebal**` jadi tebal, tanpa simbol mentah | HP, R |
| 4 | Ikon suara | Speaker / speaker tercoret, `Read replies aloud, switch, on/off` | UIA |
| 5 | Animasi mati | Titik diam + `Vyna is thinking…` | R **[bukan HP]** |
| 6 | Disclaimer | `Vyna can get things wrong. Check the details before you send money.` | HP |

#### Q-E7 Label TalkBack dan belum dibaca di inbox (P1)
**Kondisi awal:** Raka punya pesan inbox baru (kirim pesan dari admin). **Peran:** pengguna TalkBack.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Sembunyikan saldo; dump beranda, kartu rekening, kantong, detail rekening | `Balance hidden` | UIA |
| 2 | Dump E-statements | `Share September 2026 statement` | UIA |
| 3 | Dump Inbox | Baris informasi tanpa "disabled" (`enabled` tidak false, tanpa role); baris bertautan `clickable` role Button dengan `Unread` di depan bila belum dibaca | UIA, R |
| 4 | Inbox | Belum dibaca tebal 700 + titik; sudah dibaca 600 `textSecondary` | HP, R |

#### Q-E8 Baris yang terlihat bisa diketuk selalu membuka sesuatu (P1)
**Kondisi awal:** Hendra (punya kartu kredit, aset Wealth, emas kecil). **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Cards → pembayaran kartu terakhir | Detail transaksi | HP |
| 2 | Wealth | Seluruh baris aset bisa diketuk dengan chevron, tidak ada `Open`, `Savings and pockets` membuka detail ORVYN Everyday | HP |
| 3 | Persen aset | Teks dan label sama: `Less than 1% of assets` / `{12}% of assets` | UIA |
| 4 | Grafik komposisi | Segmen aset 1% ≥ 6dp | R, HP |
| 5 | `Grow your money`, baris kartu kredit | Hanya produk yang belum dimiliki (hilang bila semua dimiliki); `Not counted in your total` | HP |

#### Q-E9 Status permintaan uang dengan ikon dan kata (P1)
**Kondisi awal:** Raka punya permintaan Waiting, To pay, Paid, Declined, Cancelled, Expired (buat lewat API/admin). **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Daftar dan detail Requests | StatusChip: `Waiting` (jam, abu-abu), `To pay` (warning), `Paid` (success), `Declined`, `Cancelled`, `Expired` | HP |
| 2 | Setiap chip | Ikon + kata | HP, UIA |
| 3 | Split bill | Chip sama per orang | HP |
| 4 | Terang, gelap, R200 | Terbaca | HP, R200 |

#### Q-E10 Ikon tagihan per jenis (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Bills | Pulsa HP, BPJS P3K, e-wallet dompet, PLN petir | HP |
| 2 | Drawable | Phosphor Regular vector, ukuran sama | KODE |
| 3 | Dua tema | Benar | HP |

#### Q-E11 Grafik Insights dan kategori kosong (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Insights dua tema | Batang lain `#7088C4` / `#55689C`, terpilih `primary` | HP, KODE |
| 2 | Ringkasan TalkBack grafik | Tetap ada | UIA |
| 3 | Kategori tanpa pengeluaran | `No {Food and drinks} spending this month.` tanpa kartu kredit | HP |

#### Q-E12 Skeleton tenang, tanpa strip status bar (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | LAMBAT 2 detik, buka Cards; tanpa LAMBAT | Skeleton `surfaceVariant`, denyut 1,2 detik; jawaban < 300ms tanpa kedipan skeleton | LAMBAT, R |
| 2 | Dump saat skeleton | Satu node `Loading` per layar | UIA |
| 3 | Beranda memuat | Blok skeleton 200×40dp, bukan `Rp—` | LAMBAT, HP |
| 4 | Tema terang, gulir hero hilang | Tidak ada strip biru tua, ikon status bar gelap (screenshot ini boleh memuat status bar hanya untuk analisis, tidak dibagikan) | HP |

#### Q-E13 Kartu satu gaya, nama panjang di bukti (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Scheduled, Requests, Insights, Pockets | Garis `outlineVariant`, radius 14, tile 10 | HP, KODE |
| 2 | Bukti dengan nama 40 huruf (penerima dummy dari admin) | Maksimal 2 baris lalu `…` | R, HP (cache aplikasi lewat `run-as`) |
| 3 | Bukti lain | Tidak berubah | R |

### F. Story lama yang dibangun sekarang

#### Q-F1 Detail rekening (S1.9) (P0)
**Tujuan:** nasabah bisa membagikan nomor rekening dengan rapi. **Kondisi awal:** Raka di beranda. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Ketuk kartu ORVYN Everyday | Layar `ORVYN Everyday`: `Account holder`, `Account number` (grup 4), `Balance`, `Opened on {3 Mar 2024}`, `HISTORY` dengan riwayat rekening itu | HP |
| 2 | `Share account details` | Menu bagikan dengan teks `{nama} · ORVYN Bank · {nomor grup 4}` (cek teks dari intent di `dumpsys activity` atau R; tutup dengan satu Back) | HP, R |
| 3 | `Copy number` | `Account number copied`, isi clipboard tanpa spasi (R, karena clipboard HP tidak dibaca) | HP, R |
| 4 | Sembunyikan saldo di beranda | Detail: `Rp••••••••`, `Balance hidden`, tombol mata `Show balance` | HP, UIA |
| 5 | Offline | Banner `You're offline. These details are from {…}.`, Share dan Copy tetap bekerja | HP |
| 6 | R200; LAMBAT; error | Utuh di 200%; skeleton; `Couldn't load your account` + `Try again` | R200, LAMBAT, HP |
| Tepi | Ikon salin di kartu beranda | Tetap target terpisah, tidak membuka detail | HP |

#### Q-F2 "Share receipt" dan tanggal di layar sukses (P0)
**Tujuan:** bukti bisa dibagikan dari layar sukses. **Kondisi awal:** Raka dan Sinta. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Sukses Transfer kirim sekarang, Bill Payment, bayar Money Request; QRIS | `Share receipt` bergaris di atas `Done`. QRIS: **[bukan HP]** layar sukses QRIS tidak bisa dicapai tanpa scan, jadi R | HP, R |
| 2 | Ketuk `Share receipt` | Menu bagikan dengan gambar bukti sama dengan bukti dari riwayat; file di cache aplikasi (`run-as id.orvyn.app ls cache`), tidak di galeri (galeri HP tidak dibuka) | HP, KODE |
| 3 | Baris pertama rincian | `Date` → `16 Oct 2026, 19:27 WIB`, sama dengan detail transaksi (bukan jam HP) | HP |
| 4 | Transfer ke penerima baru | Urutan: `Save {nama} as a recipient`, `Share receipt`, `Done` | HP |
| 5 | Sukses terjadwal, kantong, valas, deposito, investasi, tagihan kartu | Tidak ada `Share receipt`, `Date` ada | HP |
| Tepi | Pembuatan gambar gagal | `Couldn't make the receipt. Try again.` | R |

#### Q-F3 Filter riwayat Date dan Type (S1.10 AC3) (P1)
**Kondisi awal:** Raka dengan transaksi beragam di beberapa bulan. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | `Date` | Sheet `Date`: `Any time`, `This month`, `Last month`, `Last 3 months`, `Pick dates`. Pemilih rentang: akhir tidak bisa sebelum mulai dan tidak setelah hari ini. Chip `{3 Oct} to {16 Oct}` | HP |
| 2 | `Type` | `Transfers`, `QRIS`, `Bills`, `Cards`, `Pockets`, `Wealth`, `Show results`/`Clear`. Hasil hanya jenis itu; reversal ikut jenis yang dibatalkannya, inflow/adjustment/opening hanya tanpa filter Type (inflow di `Money in`) (PRD 14.5). Chip `QRIS` atau `{2} types` | HP, API (cocokkan dengan DB) |
| 3 | Gabung Date + Type + `Money out` + cari "Kopi" | Hasil irisan, `Clear filters` menghapus. Filter tetap setelah kembali dari detail. `All` tidak ada | HP |
| 4 | Tarik ke bawah | Memuat ulang dengan filter sama | HP |
| 5 | Gulir ke halaman berikutnya, offline | Baris `Couldn't load more. Try again`; online + ketuk memuat | HP |
| 6 | Filter tanpa hasil | `Nothing matches` / `Try other dates or types, or clear the filters.` / `Clear filters` | HP |
| Tepi | Dump chip | `Date filter, Last month, selected`, tinggi ≥ 48dp | UIA |

#### Q-F4 Nominal opsional di QR-ku (S1.16 AC1) (P1)
**Kondisi awal:** Raka di My QR. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | `Add an amount` → Rp50,000 → `Show QR for Rp50,000` | QR berubah, `Rp50,000` di bawah, `Change amount` dan `Remove amount` | HP |
| 2 | Rp5,000 | Tombol nonaktif `Minimum Rp10,000` | HP |
| 3 | `Remove amount` | QR tanpa nominal | HP |
| 4 | Decode QR (screenshot QR dari layar ORVYN, decode di host) | Memuat nomor rekening dan nominal. Scan oleh nasabah lain membuka Internal Transfer terisi: **[bukan HP]** test otomatis + API | HP, R, API |
| 5 | Kecerahan | Naik selama QR tampil, kembali setelahnya (`dumpsys window` atribut `screenBrightness`, baca saja) | HP |

#### Q-F5 Pulsa: nomor sendiri dan operator saat mengetik (S1.20) (P1)
**Kondisi awal:** Raka di Bills → Phone. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Layar nomor | Baris `My number` / `{0812 1111 2222} · {operator}` dari data Customer; ketuk mengisi dan cek | HP |
| 2 | Ketik `0812` | `Operator: Telkomsel` sebelum Continue | HP |
| 3 | Ketik awalan tak dikenal (misalnya `0899` bila tak ada di tabel) | `We don't know this operator. Check the first 4 digits.`, Continue nonaktif | HP |
| 4 | Izin | Tidak membaca SIM, tidak ada izin baru (A4); sumber nomor dari API (KODE) | KODE |

#### Q-F6 Emas dalam gram, unit di detail investasi (P1)
**Kondisi awal:** Hendra, harga emas di dev. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Beli Gold Savings | Segmented `Rupiah`/`Grams`, Rupiah sama dengan 0.8.1 termasuk Quick Amount | HP |
| 2 | Grams: ketik `0.05`, coba digit ketiga | `0.05 g`, digit ketiga diabaikan, tanpa Quick Amount, `You pay {Rp…}` = gram × harga dibulatkan ke atas | HP |
| 3 | `0.00`/`0.009`; gram dengan Rupiah < Rp50,000 | `Minimum 0.01 g`; `Minimum Rp50,000` | HP |
| 4 | Beli 0,05 g dengan PIN | Sibuk `Buying 0.05 g…`, sukses `Bought` / `0.05 g` / `for {Rp…}`. Journal Entry = Rupiah sebelum PIN, Holding +0,05 g, buku besar OK | HP, DB, 0.4 |
| 5 | Detail Fund dan Gold | `You hold` → `{…} units` dan `{…} g` | HP |
| Tepi | Ganti mode | Nominal dikosongkan | HP |

#### Q-F7 Bayar permintaan lewat layar periksa (S2.6, K5) (P1)
**Kondisi awal:** Sinta (API) membuat permintaan Rp50,000 ke Raka. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Inbox → kartu `Sinta Maharani asks you for Rp50,000` | Detail `Money request`: peminta, nominal, catatan, `Sent`, status, `Pay Rp50,000`, `Decline` | HP |
| 2 | `Pay Rp50,000` (dari detail, lalu dari Requests untuk permintaan kedua) | `Check the details`: penerima, Amount, `Free`, Note, Total, Balance after → PIN → sukses dengan `Share receipt` | HP |
| 3 | Permintaan ketiga → `Decline` | Dialog `Decline this request?` / `Sinta gets a message that you declined.` / `Decline` / `Keep request`; Sinta menerima push (API inbox Sinta) | HP, API |
| 4 | Detail yang Paid/Declined/Cancelled/Expired | Status tanpa Pay dan Decline | HP |
| 5 | Ketuk ganda `Pay` | Satu `internal_transfer`, buku besar OK | HP, 0.4 |
| Tepi | Saldo kurang | `You're {Rp20,000} short.`, tombol nonaktif | HP (nasabah saldo kecil) |

#### Q-F8 Admin: filter jenis, banner, Settings (P1)
**Peran:** petugas. Seluruhnya ADM, dengan HP untuk AC2 dan AC4.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Transactions → `Kind` | Semua label design 12.4 dalam optgroup. Setiap pilihan hanya jenis itu. Test server: setiap `kind` di `journal_entries` punya label | ADM, SRV |
| 2 | Banner `Show from` besok | Status `Scheduled`, tidak tampil di HP hari ini; ubah `Show from` ke hari ini → tampil setelah tarik | ADM, HP |
| 3 | Edit banner (semua field) | `Banner saved.`, Audit Entry `banner.edit` dengan sebelum/sesudah. `Show until` < `Show from` → `Show until can't be before Show from.` | ADM |
| 4 | Dua banner, tukar `Position` | Urutan di HP mengikuti | ADM, HP |
| 5 | Migrasi | Banner lama: `Show from` = tanggal dibuat, `Position` = urutan lama (DB sebelum/sesudah migrasi) | DB |
| 6 | Settings | Tidak ada sakelar Widget; sakelar lain bekerja (lihat 4.5) | ADM |

### G. Copy dan detail

#### Q-G1 Banner promo di bawah transaksi terakhir (K1) (P0)
**Kondisi awal:** Raka, minimal 3 banner aktif. **Peran:** nasabah.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Gulir beranda | Hero → aksi cepat → (banner status) → insight → `ACCOUNTS` → `WEALTH` → RM → `POCKETS` → `RECENT` → banner | HP, UIA |
| 2 | TalkBack aktif | Tidak berputar otomatis, kembali berputar setelah mati | R **[bukan HP]**: TalkBack |
| 3 | Ketuk pil `Scan to pay`; ketuk bagian lain | Pil langsung membuka pemindai (izin ditolak); bagian lain membuka detail banner itu | HP |
| 4 | Ketuk 10 kali, termasuk tepat saat berganti (ketuk 6 detik setelah sentuhan terakhir) | Selalu banner yang terlihat saat disentuh | HP |
| 5 | Semua banner dihapus/berakhir di admin | Bagian hilang tanpa ruang kosong | HP, ADM |

#### Q-G2 Pemindai QRIS dengan senter (P0)
Seluruhnya **[bukan HP]** (aturan kamera): R dan KODE. Di HP hanya pesan izin kamera ditolak.

| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Test Compose pemindai | Tombol senter 48dp kanan atas, Regular/Fill, `Turn on the flashlight`/`Turn off the flashlight` | R |
| 2 | Kode | `CameraControl.enableTorch`, senter mati saat ditutup/latar belakang, tidak ada izin baru (A4) | KODE |
| 3 | Tanpa senter | Tombol tidak tampil | R |
| 4 | Pesan error | Chip putih, ikon `#C62828`, teks `#0B1020`, live region polite | R |
| 5 | Deskripsi kamera | `Camera. Point it at a QRIS code.`, tanpa "attempt" | R |
| 6 | HP: izin ditolak | Pesan kamera mati tampil, tidak crash | HP |

#### Q-G3 Bantuan "Ask Vyna" dan Privacy lengkap (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Help, buka dan tutup FAQ | Chevron berputar 180°, `Expanded`/`Collapsed` | HP, UIA |
| 2 | Bawah daftar (juga pencarian kosong) | `Still need help?` / `Vyna can answer questions about your money and how ORVYN works.` / `Ask Vyna` membuka Vyna | HP |
| 3 | Admin: Vyna off | Kartu tidak tampil | HP, ADM |
| 4 | Help | Tidak ada nomor telepon, tidak ada intent dialer (KODE grep `ACTION_DIAL`/`tel:`) | HP, KODE |
| 5 | Privacy | Baris `Camera` dan `Microphone` persis design 11.3, baris lain tetap, semua baris PRD 7.4 tercakup | HP, KODE |

#### Q-G4 Format angka, tanggal, kurs (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Cek tagihan PLN dan BPJS | `Bill for Oct 2026` | HP |
| 2 | Investasi turun, nominal negatif | `−1.30%` U+2212 (cek karakter di dump) | UIA |
| 3 | Investments | Eyebrow `YOUR INVESTMENTS`, baris `Total` → `Rp25,971,710`. Tidak ada `RP` di mana pun (grep dump di semua layar yang diuji) | HP, UIA |
| 4 | Kurs JPY | `100 JPY = Rp10,802.50`, tabel `100 JPY`, catatan `Rupiah for one unit. JPY is per 100 yen.` Hitungan beli JPY sama dengan 0.8.1 | HP, API |

#### Q-G5 Copy yang tepat (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Hendra review RTGS | `Arrives` → `Within the hour` | HP |
| 2 | Valas | `Buy USD 100.00` / `Sell USD 100.00`, langsung PIN; `Not enough USD` | HP |
| 3 | Sign in (nasabah persona dan nasabah daftar sendiri) | `Use your phone number and ORVYN password.` | HP |
| 4 | Batalkan permintaan | `Cancel this request?` / `{Sinta} won't be able to pay it anymore.` / `Cancel request` / `Keep request` | HP |
| 5 | Unlock setelah keluar otomatis 5 menit dan setelah buka ulang | Selalu `Welcome back, {nama depan}` | HP |

#### Q-G6 Pencarian kosong dan minimum (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Cari penerima `zzz` | `No matches for "zzz"` / `Check the spelling, or send to a new recipient.` / `New recipient` | HP |
| 2 | GoPay Rp5,000 | Tombol nonaktif `Minimum Rp10,000` | HP |
| 3 | Rp10,000 | `Pay {Rp11,000}` aktif | HP |

#### Q-G7 Back dari periksa Vyna kembali ke Vyna (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Vyna "kirim 50 ribu ke Sinta" → `Review` → satu Back (dump: bukan beranda) | Kembali ke percakapan Vyna | HP |
| 2 | Cek | Tidak ada Journal Entry baru, percakapan utuh | HP, DB |
| 3 | Tagihan dan isi kantong dari Vyna | Sama | HP |

#### Q-G8 Kurs dan grafik Fund (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Tabel kurs | `You sell at` (Buy Rate), `You buy at` (Sell Rate), cocok dengan admin | HP, ADM |
| 2 | Grafik Fund | `1 month`, `6 months`, `1 year` | HP |
| 3 | Perubahan | `+4.10% in 6 months` hijau panah naik; turun panah turun warna teks; tetap `No change in …` | HP, R |
| 4 | Tepi kanan | Label tertinggi dan terendah (hanya tertinggi bila < 16dp) | HP, R |

#### Q-G9 Tidak ada field teks di layar keypad (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Raka minta uang ke Sinta | Nominal tanpa field catatan → `Check the request` dengan `Note (optional)` → `Ask for Rp50,000` | HP |
| 2 | Semua layar 4.7, split bill | Tidak ada field teks bersama keypad | HP, KODE |
| 3 | Sinta melihat | Catatan di detail permintaan (API) | API |

#### Q-G10 Judul riwayat kantong (P1)
| AC | Langkah | Diharapkan | Cara |
|---|---|---|---|
| 1 | Isi dan ambil dari kantong | `Added from ORVYN Everyday` / `Moved to ORVYN Everyday`, tabung otomatis `Auto-save · {jam}` | HP |
| 2 | Kantong "QA Trip" (transaksi sebelum 0.9.0) | Judul baru juga | HP |
| 3 | Admin dan DB | Journal Entry lama tidak berubah (bandingkan dengan dump sebelum uji), buku besar OK | DB, 0.4 |

#### Q-G11 Copy widget (P1)
Seluruhnya **[bukan HP]** (butuh launcher): `WidgetTest`.

| AC | Diharapkan | Cara |
|---|---|---|
| 1 | `Show balance` | R |
| 2 | `As of {14:05} · hides in 10 seconds` dengan jam saldo tersimpan | R |
| 3 | `Balance hidden` | R |
| 4 | WidgetTest ada dan lolos | R |

---

## 4. Edge case lintas fitur

| ID | Kasus | Skenario | Diharapkan | Cara |
|---|---|---|---|---|
| X1 | PIN salah | Unlock, Transfer, tagihan, kartu, deposito, valas, investasi, Change PIN, Change password (hitungan bersama, K6) | `Wrong PIN. {N} tries left.` / `1 try left.`, ke-5 terkunci di mana pun, admin locked | HP, API, DB |
| X2 | Uang kurang | Transfer, QRIS (API), tagihan, bayar permintaan, beli investasi/emas, pindah kantong | Pesan per design (`You're Rp…short.`), tanpa goyang PIN, tidak ada Journal Entry | HP, API, DB |
| X3 | Ketukan ganda | Q-E4 + `Pay` permintaan, `Ask for`, `Save` penerima, `Try again`, `Withdraw` | Satu aksi, satu Journal Entry | HP, 0.4 |
| X4 | Offline | Q-B1, Q-B3, Q-B4, Q-B6, Q-B10, Q-C6, Q-D1, Q-D2, Q-F1, Q-F3 | Selalu ada pesan, tidak ada crash | HP |
| X5 | Fitur dimatikan | Admin Settings: matikan Pockets, Scheduled, Requests, Insights, Vyna, E-statements (dan passkey bila ada sakelarnya), lalu nyalakan | Bagian hilang di beranda dan menu; `Still need help?` hilang saat Vyna off; deep link ke fitur mati tidak crash; kembali normal | HP, ADM |
| X6 | Terang dan gelap | Setiap layar baru/berubah (detail rekening, detail permintaan, periksa permintaan, periksa pencairan, layar terkunci, maintenance, filter sheet, chip) di kedua tema ORVYN | Token sesuai design 3.3, tidak ada teks tak terbaca | HP, R200 |
| X7 | Font besar | Bagian 3.A dan A5 | Semua R200 lolos | R200 |
| X8 | Server lambat | LAMBAT 3 dan 20 detik di PIN, Cards, beranda, review Transfer | Keadaan sibuk dan error 15 detik | LAMBAT |
| X9 | Maintenance di tengah PIN | Maintenance on, masukkan PIN Transfer | Layar maintenance, tidak ada Journal Entry | HP, DB |
| X10 | Keluar otomatis 5 menit di tengah alur | Diam 5 menit di review | Unlock dengan `Welcome back, {nama}`, tidak ada uang bergerak | HP |

---

## 5. Temuan lama → skenario uji ulang

| Temuan | Severity lama | Skenario uji ulang | Selesai bila |
|---|---|---|---|
| E2E-01 | blocker | Q-A1 AC1–3, Q-A2 AC1–2, Q-A3 AC1–2 (360×640 font 2,0) | Create PIN, nominal, Done, Unlock utuh di keempat render |
| E2E-02 | major | Q-C1 AC1–3, AC6; Q-C2 AC1–2, AC5; X1 | Hitungan bersama, kunci di ke-5 |
| E2E-03 | major | Q-C3 AC1–4 | Reset berhasil dengan passkey, Ledger OK |
| E2E-04 | major | Q-D1 AC1–5 | Password dan pesan terlihat dengan keyboard |
| E2E-05 | major | Q-D2 AC3–6, Q-D3 AC1 | NIK dan PIN lemah ditolak di langkahnya |
| E2E-06 | major | Q-D3 AC2 | `Those PINs didn't match. Start again.` terlihat |
| E2E-07 | major | Q-E5 AC1–4 | `checkable`/`checked` di semua sakelar |
| E2E-08 | major | Q-B7 AC1–2 | Total assets ikut refresh |
| E2E-09 | major | Q-B1 AC1–3, AC5; Q-B2 | Semua aksi offline berpesan; sukses pencairan |
| E2E-10 | major | Q-A4 AC1, Q-A5 AC1–2, Q-A6 AC1, AC3 | Tidak ada kata/angka patah |
| E2E-11 | major | Q-F1 | Detail rekening dengan Share dan Copy |
| E2E-12 | major | Q-F2 | Share receipt dan Date |
| E2E-13 | minor | Q-B7 AC3–5 | Banner, insight, bagian kosong benar |
| E2E-14 | minor | Q-F3 | Filter Date dan Type |
| E2E-15 | minor | Q-C7 | Server menolak alasan kosong |
| E2E-16 | minor | Q-C8 | Tanpa password di URL/log |
| E2E-17 | minor | Q-F4 | Nominal opsional QR-ku |
| E2E-18 | minor | Q-F5 | `My number`, operator setelah 4 digit |
| E2E-19 | minor | Q-F6 | Beli gram, unit dan gram di detail |
| E2E-20 | minor | Q-F7 | Detail dengan Pay/Decline, review sebelum PIN |
| E2E-21 | minor | Q-B8 | Maintenance ≤ 60 detik dan pada jawaban server |
| E2E-22 | minor | Q-F8 AC6 (K4: AC S2.19 diubah) | Tidak ada sakelar Widget, sesuai AC baru |
| E2E-23 | minor | Q-G4 AC1–4, Q-G5 AC1–5, Q-B9 AC4 | Semua sub-temuan (periode, RTGS, RP, minus, JPY, tombol valas, subjudul Sign in, dialog batal, pesan terkunci, Welcome back) |
| E2E-24 | minor | Q-G3 AC5 | Privacy lengkap |
| E2E-25 | minor | Q-C2 AC1 | Error di field Current password |
| E2E-26 | minor | Q-G6 AC1 | `No matches for "zzz"` |
| E2E-27 | minor | Q-G6 AC2–3 | `Minimum Rp10,000` |
| E2E-28 | minor | Q-B9 AC1–3, AC5 | `Try again` di layar terkunci |
| E2E-29 | minor | Q-D5 | Panah kembali di selfie |
| E2E-30 | minor | Q-G7 | Satu Back ke Vyna |
| E2E-31 | minor | Q-E8 AC2–3 | `Savings and pockets` bisa diketuk, label persen sama |
| E2E-32 | minor | Q-E7 AC1–3 | Label e-statement, inbox, saldo tersembunyi |
| E2E-33 | minor | Q-G1 AC3–4 | Ketukan membuka banner yang terlihat |
| E2E-34 | minor | Q-F8 AC1–5 | Filter jenis lengkap, banner mulai/urutan/edit |
| E2E-35 | minor | Q-C1 AC1 | PIN lama diperiksa dulu |
| E2E-36 = F3 | minor (P0) | Q-E4 AC1–5 | PIN pad 0 digit, tenor tidak terlewat |
| E2E-37 = F5 | minor | Q-B4 AC1–4 | Review pulih setelah online |
| E2E-38 | minor | Q-B3 AC1–2 | `Try again` di semua layar muat |
| E2E-39 | minor | Q-C4 AC1–2 | screencap hitam di PIN pad |
| F3 | minor | = E2E-36 | |
| F4 | minor | Q-A2 AC1–3 (360dp font 2,0, semua layar nominal) | Keypad dan tombol utuh |
| F5 | minor | = E2E-37 | |

UI-01..UI-45 tertutup lewat story di PRD bagian 6. UI-46 ditunda dan tidak diuji. Temuan "dikonfirmasi" di `e2e-report.md` bagian 4 yang dulu tidak bisa diuji (UI-11, UI-13, UI-18, UI-19, UI-20) sekarang punya cara di Q-E2, Q-E3, Q-D4, Q-E6, Q-G2.

---

## 6. Regresi alur uang yang tidak berubah

Setiap baris: catat jumlah Journal Entry sebelum, jalankan, cek bagian 0.4. Diharapkan sama dengan 0.8.1 (`e2e-report.md` bagian 2), dengan layar baru 0.9.0 (Date, PIN sibuk, Share receipt).

| ID | Alur | Langkah | Diharapkan (Journal Entry) | Cara |
|---|---|---|---|---|
| G1 | Transfer ORVYN | Raka → Sinta Rp50,000 + catatan; rekening sendiri dan tak dikenal ditolak | `internal_transfer` satu; push dua pihak | HP |
| G2 | Transfer bank lain | Raka BI-FAST BCA Rp100,000; 100 juta Everyday ditolak dengan sisa limit | `external_transfer`: External Clearing + Fee Income Rp2,500 | HP |
| G3 | RTGS | Hendra Rp150 juta RTGS | Fee Rp25,000 | HP |
| G4 | QRIS | Sinta lewat API: scan QR merchant, PIN salah lalu benar, ulang kunci idempoten, nominal sangat besar | `qris_payment` ke Merchant Settlement sekali; limit ditolak | API |
| G5 | Tagihan | Token PLN 20k (token 4-4-4-4-4), tagihan PLN (lalu "already paid"), BPJS, pulsa, GoPay | `bill_payment` per transaksi, biaya e-wallet Rp1,000 | HP |
| G6 | Kartu | Hendra bayar tagihan kartu 100k; simulasi belanja admin (aktif, freeze, limit) | `card_payment`, `card_spend` / ditolak | HP, ADM |
| G7 | Kantong | Isi sampai target (ketuk ganda), ambil, round-up dari belanja kartu Rp23,000 | `pocket_move`; Auto-save Rp7,000 | HP, ADM |
| G8 | Jadwal | Buat bulanan (PIN sekali), `runScheduledTransfers` dua kali dengan waktu simulasi | Terkirim sekali | HP, SRV/skrip |
| G9 | Minta uang dan split | Raka minta Sinta, Sinta bayar (API); split 100k dengan Sinta dan QA | `internal_transfer` per pembayaran, pembulatan ke pembuat | HP, API |
| G10 | Valas | Hendra beli JPY 1,000, jual semua, tutup rekening (dialog) | `fx_exchange` dengan FX Position | HP |
| G11 | Deposito | Buka 1 juta, Mature now (payout dan rollover) di admin, cair awal (Q-B2) | `deposit_open`, `deposit_interest`, `deposit_close`, bunga bersih sesuai hitungan | HP, ADM |
| G12 | Reksa dana, SBN, emas | Beli Fund 100k, jual 50k, ORI 1 juta (kelipatan), emas 50k Rupiah | `investment`; kupon `bond_coupon` bila dijalankan | HP |
| G13 | Vyna | Saldo, "kirim 50 ribu ke Sinta" (Review → PIN), topik luar, batas harian | Angka benar, satu `internal_transfer` setelah PIN, topik luar ditolak | HP |
| G14 | Admin | Inflow, adjustment (tanpa alasan ditolak, minus ditolak), reversal (sekali, minus ditolak) | `inflow`, `adjustment`, `reversal` | ADM |
| G15 | Akhir regresi | Bagian 0.4 | Ledger OK, jumlah Journal Entry = awal + jumlah aksi | ADM, DB |

---

## 7. Kompatibilitas: APK 0.8.1 terhadap server 0.9.0 (smoke)

**Kondisi awal:** debug 0.8.1 dibangun dari commit `4f75da2` di `git worktree` di scratchpad (APK rilis 0.8.1 bicara ke produksi, jadi tidak dipakai). Uninstall debug 0.9.0, pasang debug 0.8.1. Server tetap 0.9.0. Dijalankan setelah regresi 0.9.0.

| ID | Skenario | Diharapkan | Cara |
|---|---|---|---|
| K1 | Masuk (Sign in, Create PIN masuk pertama, Unlock) | Berhasil seperti 0.8.1 | HP |
| K2 | Transfer ORVYN Rp10,000 dengan PIN | Berhasil sekali, buku besar OK | HP, 0.4 |
| K3 | Bayar tagihan PLN | Berhasil, periode tampil apa adanya dari server (tidak crash bila server mengirim `Oct 2026`) | HP |
| K4 | Ganti PIN gaya lama, PIN lama salah sekali lalu benar | Pesan `That's not your current PIN. {N} tries left.` tampil di layar lama, `failed_attempts` naik lalu kembali 0 (Q-C1 AC6) | HP, DB |
| K5 | PIN salah 5 kali, lalu unlock petugas | Pesan terkunci server baru tampil di 0.8.1, unlock memulihkan | HP, ADM |
| K6 | Beranda, riwayat (judul kantong baru), banner dengan posisi | Tampil tanpa crash | HP |
| K7 | Maintenance on | 0.8.1 menampilkan perilaku lamanya (pesan atau layar saat buka ulang), tidak crash | HP |
| K8 | Penutup | Uninstall debug 0.8.1, `git worktree remove` | KODE |

---

## 8. Izin APK

| ID | Skenario | Diharapkan |
|---|---|---|
| P1 | `checkDebugPermissions` (A2) dan `checkReleasePermissions` bila release dibangun orkestrator | Lolos |
| P2 | `aapt2 dump permissions` 0.9.0 debug vs `releases/orvyn-0.8.1.apk` | Sama persis: INTERNET, CAMERA, POST_NOTIFICATIONS, USE_BIOMETRIC, USE_FINGERPRINT, RECORD_AUDIO, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, c2dm RECEIVE, WAKE_LOCK, ACCESS_NETWORK_STATE |
| P3 | Grep kode baru: `READ_PHONE_STATE`, `TelephonyManager`, `getLine1Number`, `FLASHLIGHT`, `ContactsContract`, lokasi | Tidak ada (Q-F5 AC4, Q-G2 AC2) |
| P4 | Data baru yang dikirim ke server (PRD bagian 11) | Hanya NIK yang diketik (cek NIK), tidak ada fontScale, status TalkBack, atau data HP di payload (KODE pada klien API) |

---

## 9. Urutan putaran

1. Bagian 2 (otomatis, buku besar awal, izin, copy).
2. **Tahap 1, P0:** A, B (P0), C1–C5, D1–D4, E1–E6, F1–F2, G1–G2, lalu edge case X1–X4 dan X8 yang menyentuh P0.
3. **Tahap 2, P1:** sisanya, X5–X10.
4. Regresi bagian 6, lalu kompatibilitas bagian 7.
5. Q-C3 (Reset demo) paling akhir, dengan dump dan pemulihan DB dev.
6. Buku besar akhir, penutupan sesi HP (aturan 1.12), `qa-report.md` dengan putusan.
